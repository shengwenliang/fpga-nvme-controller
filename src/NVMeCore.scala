package nvme

import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

import common._
import common.storage._
import common.connection._
import qdma._
import math.{pow, max}

class Doorbell extends Bundle {
    val addr    = Input(UInt(64.W))
    val value   = Input(UInt(32.W))
}

// Core part of NVMe host controller.
// Handle queue and doorbell request from/to SSDs.
class NVMeCore (
    SSD_NUM             : Int = 1,
    QUEUE_NUM           : Int = 4,
    QUEUE_DEPTH         : Int = 256,
    MAX_SQ_INTERVAL     : Int = 30,
    QDMA_INTERFACE      : String = "DMA"
) extends Module {
    val io = IO(new Bundle{
        // SSD input commands.
        val ssdCmd      = Flipped(Vec(SSD_NUM, Vec(QUEUE_NUM, Decoupled(UInt(512.W)))))

        val control     = Flipped(new NVMeCoreControl)
        val status      = new NVMeCoreStatus

        val ramIO       = Flipped(new NVMeRamIO)

        val sAxib       = if (QDMA_INTERFACE == "SAXIB") {Some(new AXIB_SLAVE)} else None

		val c2hCmd		= if (QDMA_INTERFACE == "DMA") {Some(Decoupled(new C2H_CMD))} else {None}
		val c2hData	    = if (QDMA_INTERFACE == "DMA") {Some(Decoupled(new C2H_DATA))} else {None}
    })

    assert(SSD_NUM > 0, "At least one SSD is required.")
    assert(QUEUE_NUM > 0, "At least one queue is required.")
    assert(QUEUE_DEPTH >= 4, "Queue depth should be at least 4.")
    assert(Set("DMA", "SAXIB") contains QDMA_INTERFACE, "Invalid QDMA interface.")
    assert(pow(2, log2Ceil(QUEUE_DEPTH)).toInt == QUEUE_DEPTH, "Queue depth must be exponential of 2.")

    val QUEUE_MAX_ID        = QUEUE_NUM - 1

    /* Basically below is how we handle queues using BAR space.
     * For SQE, address is splited into: {1'd0, ssd_id, queue_id, padding, entry_id, 6'd0};
     * For CQE, address is splited into: {1'd1, ssd_id, queue_id, padding, 2'd0, entry_id, 4'd0};
     * Padding is used to ensure starting address of a queue is 4-KiB aligned.
     * Length of padding is max(0, 6-bits(entry_id)).
     */ 

    val ENTRY_BIT_LEN       = log2Ceil(QUEUE_DEPTH)
    val ENTRY_LOW_BIT_SQ    = 6
    val ENTRY_HIGH_BIT_SQ   = 6 + ENTRY_BIT_LEN - 1

    val ENTRY_LOW_BIT_CQ    = 4
    val ENTRY_HIGH_BIT_CQ   = 4 + ENTRY_BIT_LEN - 1

    val QUEUE_BIT_LEN_RAW   = log2Ceil(QUEUE_NUM)
    val QUEUE_BIT_LEN       = max(0, QUEUE_BIT_LEN_RAW)
    val QUEUE_LOW_BIT       = max(12, ENTRY_HIGH_BIT_SQ+1)
    val QUEUE_HIGH_BIT      = QUEUE_LOW_BIT + QUEUE_BIT_LEN_RAW - 1

    val SSD_BIT_LEN_RAW     = log2Ceil(SSD_NUM)
    val SSD_BIT_LEN         = max(0, SSD_BIT_LEN_RAW)
    val SSD_LOW_BIT         = QUEUE_HIGH_BIT + 1
    val SSD_HIGH_BIT        = SSD_LOW_BIT + SSD_BIT_LEN_RAW - 1

    val RAM_TYPE_BIT        = SSD_HIGH_BIT + 1

    // SQ & CQ RAMs

    val sqRam   = Seq.fill(SSD_NUM)(XRam(
        UInt(512.W),
        QUEUE_NUM * QUEUE_DEPTH,
        latency = 1,
        use_musk = 0
    ))

    // SQ data structures
    val sqTail  = RegInit(VecInit(Seq.fill(SSD_NUM)(VecInit(Seq.fill(QUEUE_NUM)(0.U(ENTRY_BIT_LEN.W))))))
    val sqHead  = RegInit(VecInit(Seq.fill(SSD_NUM)(VecInit(Seq.fill(QUEUE_NUM)(0.U(ENTRY_BIT_LEN.W))))))
    
    val cqRam   = Seq.fill(SSD_NUM)(XRam(
        UInt(512.W),
        QUEUE_NUM * QUEUE_DEPTH / 4,
        latency = 1,
        use_musk = 1
    ))

    // CQ data structures. CQ head can be divided into 2 parts,
    // One is phase, used to know whether CQE has been updated,
    // another is head counter, used to send doorbell.
    val cqHeadExt   = RegInit(VecInit(Seq.fill(SSD_NUM)(VecInit(Seq.fill(QUEUE_NUM)(0.U((ENTRY_BIT_LEN+1).W))))))
    val cqPhase     = Wire(Vec(SSD_NUM, Vec(QUEUE_NUM, UInt(1.W))))
    val cqHead      = Wire(Vec(SSD_NUM, Vec(QUEUE_NUM, UInt(ENTRY_BIT_LEN.W))))
    for (ssdId <- 0 until SSD_NUM) {
        for (queueId <- 0 until QUEUE_NUM) {
            cqHead(ssdId)(queueId)  := cqHeadExt(ssdId)(queueId)(ENTRY_BIT_LEN-1, 0)
            cqPhase(ssdId)(queueId) := cqHeadExt(ssdId)(queueId)(ENTRY_BIT_LEN).asUInt
        }
    }
    val cqHeadChanged   = Wire(Vec(SSD_NUM, Vec(QUEUE_NUM, Valid(Bool()))))

    // SSD BAR physical address, for sending doorbells.

    val ssdBarAddr      = RegInit(VecInit(Seq.fill(SSD_NUM)(0.U(64.W))))

    // Set up SSD physical address.
    when (io.control.ssdSetup.valid) {
        ssdBarAddr(io.control.ssdSetup.bits.ssdId) := io.control.ssdSetup.bits.ssdBarAddr
    }

    // Running counters, getting to know whether all commands has been completed.
    // Only when enable is 0 and running is 0, this module is truly stopped.

    val commandStart = RegInit(VecInit(Seq.fill(SSD_NUM)(VecInit(Seq.fill(QUEUE_NUM)(0.U(32.W))))))
    val commandEnd   = RegInit(VecInit(Seq.fill(SSD_NUM)(VecInit(Seq.fill(QUEUE_NUM)(0.U(32.W))))))

    val queueRunning = Wire(Vec(SSD_NUM, Vec(QUEUE_NUM, Bool())))
    for (ssdId <- 0 until SSD_NUM) {
        for (queueId <- 0 until QUEUE_NUM) {
            queueRunning(ssdId)(queueId) := (commandStart(ssdId)(queueId) =/= commandEnd(ssdId)(queueId))
        }
    }
    io.status.running := queueRunning.asTypeOf(UInt((SSD_NUM*QUEUE_NUM).W)).orR

    // Parameters used to notify software
    io.status.params.ssdNum     := SSD_NUM.U
    io.status.params.ssdLowBit  := SSD_LOW_BIT.U
    io.status.params.queueLowBit:= QUEUE_LOW_BIT.U
    io.status.params.queueDepth := QUEUE_DEPTH.U
    io.status.params.queueNum   := QUEUE_NUM.U
    io.status.params.ramTypeBit := RAM_TYPE_BIT.U

    // Statistical information
    val statExecTime    = RegInit(UInt(64.W), 0.U)
    val statSuccOp      = RegInit(UInt(32.W), 0.U)
    val statFailedOp    = RegInit(UInt(32.W), 0.U)
    val statLatency     = RegInit(UInt(64.W), 0.U)

    io.status.stat.executeTime  := statExecTime
    io.status.stat.successfulOp := statSuccOp
    io.status.stat.failedOp     := statFailedOp
    io.status.stat.totalLatency := statLatency

    val moduleRunning   = io.status.running || io.control.enable
    val moduleStart     = moduleRunning && ~RegNext(moduleRunning)

    when (moduleStart) {
        // Clear counters at start
        statExecTime    := 0.U
        statSuccOp      := 0.U
        statFailedOp    := 0.U
        statLatency     := 0.U
    }.elsewhen (moduleRunning) {
        statExecTime    := statExecTime + 1.U
    }

    // Main logic.

    val dbReq = Wire(Vec(SSD_NUM, Vec(QUEUE_NUM, Decoupled(new Doorbell))))

    for (ssdId <- 0 until SSD_NUM) {

        // Part 1: Add command from user to SQ.

        // Each queue has a command FIFO.
        val cmdInputFifo    = XQueue(QUEUE_NUM)(UInt(512.W), 64)
        val cmdInputFifoOut = Wire(Vec(QUEUE_NUM, Decoupled(UInt(512.W))))

        // Write command to command RAM. 
        // Since all queues share one RAM, we use a reg to indicate which queue to write.
        val sqAllocPt     = RegInit(UInt(QUEUE_BIT_LEN.W), 0.U)
        val queueWriteRdy = Wire(Vec(QUEUE_NUM, Bool()))

        for (queueId <- 0 until QUEUE_NUM) {
            val cmdInputFifoIn      = Wire(Decoupled(UInt(512.W)))
            val cmdInputFifoSlice   = RegSlice(2)(cmdInputFifo(queueId).io.out)
            cmdInputFifo(queueId).io.in     <> RegSlice(2)(cmdInputFifoIn)
            io.ssdCmd(ssdId)(queueId).ready := io.control.enable && cmdInputFifoIn.ready
            cmdInputFifoIn.valid            := io.control.enable && io.ssdCmd(ssdId)(queueId).valid
            cmdInputFifoIn.bits             := io.ssdCmd(ssdId)(queueId).bits
            cmdInputFifoOut(queueId).valid  := cmdInputFifoSlice.valid
            cmdInputFifoOut(queueId).bits   := cmdInputFifoSlice.bits
            cmdInputFifoSlice.ready         := cmdInputFifoOut(queueId).ready
            cmdInputFifoOut(queueId).ready  := queueWriteRdy(queueId) && (sqAllocPt === queueId.U)

            when (io.ssdCmd(ssdId)(queueId).fire) {
                commandStart(ssdId)(queueId) := commandStart(ssdId)(queueId) + 1.U
            }
        }

        when (sqAllocPt === QUEUE_MAX_ID.U) {
            sqAllocPt := 0.U
        }.otherwise {
            sqAllocPt := sqAllocPt + 1.U
        }

        sqRam(ssdId).io.addr_a     := Cat(sqAllocPt, sqTail(ssdId)(sqAllocPt))
        sqRam(ssdId).io.data_in_a  := cmdInputFifoOut(sqAllocPt).bits
        sqRam(ssdId).io.wr_en_a    := cmdInputFifoOut(sqAllocPt).fire

        // Part 2: Get to know whether CQ has been changed.

        val cqDetectPt  = RegInit(UInt(QUEUE_BIT_LEN.W), 0.U)

        when (cqDetectPt === QUEUE_MAX_ID.U) {
            cqDetectPt := 0.U
        }.otherwise {
            cqDetectPt := cqDetectPt + 1.U
        }

        if (ENTRY_BIT_LEN <= 4) {
            cqRam(ssdId).io.addr_b     := cqDetectPt
        } else {
            cqRam(ssdId).io.addr_b     := Cat(cqDetectPt, cqHead(ssdId)(cqDetectPt)(ENTRY_BIT_LEN-1, 2))
        }
        for (queueId <- 0 until QUEUE_NUM) {
            cqHeadChanged(ssdId)(queueId).valid := (
                queueId.U(QUEUE_BIT_LEN.W) === RegNext(cqDetectPt)
            )
            cqHeadChanged(ssdId)(queueId).bits := 0.U
            switch (cqHead(ssdId)(queueId)(1, 0)) {
                is (0.U(2.W)) {
                    cqHeadChanged(ssdId)(queueId).bits := (
                        cqRam(ssdId).io.data_out_b(112+128*0) =/= RegNext(cqPhase(ssdId)(queueId))
                    )
                }
                is (1.U(2.W)) {
                    cqHeadChanged(ssdId)(queueId).bits := (
                        cqRam(ssdId).io.data_out_b(112+128*1) =/= RegNext(cqPhase(ssdId)(queueId))
                    )
                }
                is (2.U(2.W)) {
                    cqHeadChanged(ssdId)(queueId).bits := (
                        cqRam(ssdId).io.data_out_b(112+128*2) =/= RegNext(cqPhase(ssdId)(queueId))
                    )
                }
                is (3.U(2.W)) {
                    cqHeadChanged(ssdId)(queueId).bits := (
                        cqRam(ssdId).io.data_out_b(112+128*3) =/= RegNext(cqPhase(ssdId)(queueId))
                    )
                }
            }
        }
 
        // Part 3: Queue pair handle logic.

        // Basically a complex state machine.
        object QpState extends ChiselEnum {
            val sQpSqWait1, sQpSqWait2, sQpSqIns, sQpSqDb,
                sQpCqWait1, sQpCqWait2, sQpCqRefresh1, sQpCqRefresh2,
                sQpCqDb, sQpLoop = Value
        }

        import QpState._

        for (queueId <- 0 until QUEUE_NUM) {
            val qpSt      = RegInit(QpState(), sQpSqWait1)
            val sqWaitCnt = RegInit(UInt(32.W), 0.U)
            val newCqCome = cqHeadChanged(ssdId)(queueId).bits && cqHeadChanged(ssdId)(queueId).valid

            switch (qpSt) {
                is (sQpSqWait1) { // Wait for new command
                    when (cmdInputFifoOut(queueId).fire) { // A new command comes.
                        qpSt := sQpSqIns
                    }.otherwise {
                        qpSt := sQpSqWait1
                    }
                }
                is (sQpSqIns) { // Insert the command
                    when (sqTail(ssdId)(queueId) + 2.U === cqHead(ssdId)(queueId)) {
                    // when (sqTail(ssdId)(queueId) + 2.U === sqHead(ssdId)(queueId)) {
                        qpSt := sQpSqDb // SQ is full, directly ring doorbell.
                    }.otherwise {
                        qpSt := sQpSqWait2
                    }
                }
                is (sQpSqWait2) { // Wait for more command to reduce doorbell signals
                    when (cmdInputFifoOut(queueId).fire) { // A new command comes.
                        qpSt := sQpSqIns
                    }.elsewhen (sqWaitCnt >= MAX_SQ_INTERVAL.U) { // No command comes for a while, ring doorbell.
                        qpSt := sQpSqDb
                    }.otherwise {
                        qpSt := sQpSqWait2
                    }
                }
                is (sQpSqDb) { // Ring SQ doorbell
                    when (dbReq(ssdId)(queueId).fire) { // Doorbell accepted
                        qpSt := sQpCqWait1
                    }.otherwise {
                        qpSt := sQpSqDb
                    }
                }
                is (sQpCqWait1) { // Wait for new CQE
                    when (newCqCome) { // We have a first CQE
                        qpSt := sQpCqRefresh1
                    }.otherwise {
                        qpSt := sQpCqWait1
                    }
                }
                is (sQpCqWait2) { // Check if more CQE has come in a row
                    when (newCqCome) { // We have more CQEs
                        qpSt := sQpCqRefresh1
                    }.elsewhen (cqHeadChanged(ssdId)(queueId).valid) { // No more CQEs, ring doorbell.
                        qpSt := sQpCqDb
                    }.otherwise { // This CQ is not checked yet
                        qpSt := sQpCqWait2
                    }
                }
                is (sQpCqRefresh1) { // Wait for more CQEs 1
                    qpSt := sQpCqRefresh2
                }
                is (sQpCqRefresh2) { // Wait for more CQEs 2
                    qpSt := sQpCqWait2
                }
                is (sQpCqDb) { // Ring CQ doorbell
                    when (dbReq(ssdId)(queueId).fire) { // Doorbell accepted
                        qpSt := sQpLoop
                    }.otherwise {
                        qpSt := sQpCqDb
                    }
                }
                is (sQpLoop) { // Prepare for next round
                    when (sqTail(ssdId)(queueId) + 1.U === sqHead(ssdId)(queueId)) {
                        // In case SQ head not moved, skip the SQWAIT phases.
                        qpSt := sQpCqWait1
                    }.otherwise {
                        qpSt := sQpSqWait1
                    }
                }
            } // switch (qpSt)

            // New command requests
            queueWriteRdy(queueId) := (qpSt === sQpSqWait1) || (qpSt === sQpSqWait2)
            
            // Doorbell requests
            dbReq(ssdId)(queueId).valid         := (qpSt === sQpSqDb) || (qpSt === sQpCqDb)
            dbReq(ssdId)(queueId).bits.addr     := Mux(qpSt === sQpSqDb,
                ssdBarAddr(ssdId) + 0x1008.U(64.W) + Cat(queueId.U(QUEUE_BIT_LEN.W), 0.U(3.W)),
                ssdBarAddr(ssdId) + 0x100c.U(64.W) + Cat(queueId.U(QUEUE_BIT_LEN.W), 0.U(3.W)),
            )
            dbReq(ssdId)(queueId).bits.value    := Mux(qpSt === sQpSqDb,
                sqTail(ssdId)(queueId),
                cqHead(ssdId)(queueId)
            )

            // Update SQ tail and SQ wait timer
            when (qpSt === sQpSqIns) {
                sqTail(ssdId)(queueId) := sqTail(ssdId)(queueId) + 1.U
                sqWaitCnt := 0.U
            }.elsewhen (qpSt === sQpSqWait2) {
                sqWaitCnt := sqWaitCnt + 1.U
            }

            // Update CQ tail
            when (qpSt === sQpCqRefresh1) {
                cqHeadExt(ssdId)(queueId) := cqHeadExt(ssdId)(queueId) + 1.U
            }
        } // for (queueId <- 0 until QUEUE_NUM)
    } // for (ssdId <- 0 until SSD_NUM)

    // Part 4: Collect and send doorbells.

    val dbAbt = Module(new RRArbiter(new Doorbell, SSD_NUM*QUEUE_NUM))

    for (ssdId <- 0 until SSD_NUM) {
        for (queueId <- 0 until QUEUE_NUM) {
            dbAbt.io.in(ssdId*QUEUE_NUM + queueId) <> RegSlice(2)(dbReq(ssdId)(queueId))
        }
    }

    val dbFifo = XQueue(new Doorbell, 64)

    dbFifo.io.in <> RegSlice(2)(dbAbt.io.out)

    // Another state machine.
    object DbState extends ChiselEnum {
        val sDbWait, sDbPutDesc, sDbPutData = Value
    }

    import DbState._

    val dbSt    = RegInit(DbState(), sDbWait)
    val dbAddr  = RegInit(UInt(64.W), 0.U)
    val dbValue = RegInit(UInt(32.W), 0.U)
    
    switch (dbSt) {
        is (sDbWait) {
            when (dbFifo.io.out.fire) {
                dbSt := sDbPutDesc
            }.otherwise {
                dbSt := sDbWait
            }
        }
        is (sDbPutDesc) {
            if (QDMA_INTERFACE == "SAXIB") {
                when (io.sAxib.get.aw.fire) {
                    dbSt := sDbPutData
                }.otherwise {
                    dbSt := sDbPutDesc
                }
            } else {
                when (io.c2hCmd.get.fire) {
                    dbSt := sDbPutData
                }.otherwise {
                    dbSt := sDbPutDesc
                }
            }
        }
        is (sDbPutData) {
            if (QDMA_INTERFACE == "SAXIB") {
                when (io.sAxib.get.w.fire) {
                    dbSt := sDbWait
                }.otherwise {
                    dbSt := sDbPutData
                }
            } else {
                when (io.c2hData.get.fire) {
                    dbSt := sDbWait
                }.otherwise {
                    dbSt := sDbPutData
                }
            }
        }
    }

    dbFifo.io.out.ready := (dbSt === sDbWait)

    when (dbFifo.io.out.fire) {
        dbAddr  := dbFifo.io.out.bits.addr
        dbValue := dbFifo.io.out.bits.value
    }

    if (QDMA_INTERFACE == "SAXIB") {
        ToZero(io.sAxib.get.aw.bits)
        ToZero(io.sAxib.get.ar.bits)
        ToZero(io.sAxib.get.w.bits)

        when (dbSt === sDbPutDesc) {
            io.sAxib.get.aw.bits.addr   := Cat(dbAddr(63, 6), 0.U(6.W))
            io.sAxib.get.aw.bits.size   := 2.U(3.W)
            io.sAxib.get.aw.valid       := 1.U
        }.otherwise {
            io.sAxib.get.aw.bits.addr   := 0.U
            io.sAxib.get.aw.bits.size   := 0.U
            io.sAxib.get.aw.valid       := 0.U
        }
        io.sAxib.get.aw.bits.burst    := 1.U

        when (dbSt === sDbPutData) {
            io.sAxib.get.w.bits.data    := ShiftData512(Cat(0.U(480.W), dbValue), dbAddr(5, 0))
            io.sAxib.get.w.bits.strb    := ShiftStrb64("hf".U(64.W), dbAddr(5, 0))
            io.sAxib.get.w.bits.last    := 1.U
            io.sAxib.get.w.valid        := 1.U
        }.otherwise {
            io.sAxib.get.w.bits.data    := 0.U
            io.sAxib.get.w.bits.strb    := 0.U
            io.sAxib.get.w.bits.last    := 0.U
            io.sAxib.get.w.valid        := 0.U
        }
        io.sAxib.get.ar.bits.size       := 6.U
        io.sAxib.get.ar.bits.burst      := 1.U
        io.sAxib.get.ar.valid           := 0.U
        io.sAxib.get.r.ready            := 1.U
        io.sAxib.get.b.ready            := 1.U
    }

    if (QDMA_INTERFACE == "DMA") {
        ToZero(io.c2hCmd.get.bits)
        when (dbSt === sDbPutDesc) {
            io.c2hCmd.get.bits.addr := dbAddr
            io.c2hCmd.get.bits.len  := 4.U(16.W)
            io.c2hCmd.get.valid     := 1.U
        }.otherwise {
            io.c2hCmd.get.bits.addr := 0.U
            io.c2hCmd.get.bits.len  := 0.U
            io.c2hCmd.get.valid     := 0.U
        }

        ToZero(io.c2hData.get.bits)
        when (dbSt === sDbPutData) {
            io.c2hData.get.bits.data        := Cat(0.U(480.W), dbValue)
            io.c2hData.get.bits.ctrl_len    := 4.U(16.W)
            io.c2hData.get.bits.mty         := 60.U(6.W)
            io.c2hData.get.bits.last        := 1.U
            io.c2hData.get.valid            := 1.U
        }.otherwise {
            io.c2hData.get.bits.data        := 0.U
            io.c2hData.get.bits.ctrl_len    := 0.U
            io.c2hData.get.bits.mty         := 0.U
            io.c2hData.get.bits.last        := 0.U
            io.c2hData.get.valid            := 0.U
        }
    }

    // Part 5 : QDMA read/write SQ/CQ RAM.

    for (ssdId <- 0 until SSD_NUM) {

        // SQ RAM

        if (QUEUE_HIGH_BIT >= QUEUE_LOW_BIT) { // More than 1 queue
            sqRam(ssdId).io.addr_b  := Cat(
                io.ramIO.readAddr(QUEUE_HIGH_BIT, QUEUE_LOW_BIT),
                io.ramIO.readAddr(ENTRY_HIGH_BIT_SQ, ENTRY_LOW_BIT_SQ)
            )
        } else { // Only 1 queue.
            sqRam(ssdId).io.addr_b  := io.ramIO.readAddr(ENTRY_HIGH_BIT_SQ, ENTRY_LOW_BIT_SQ)
        }

        // CQ RAM

        if (SSD_HIGH_BIT >= SSD_LOW_BIT) { // More than 1 SSD.
            cqRam(ssdId).io.wr_en_a := (
                io.ramIO.writeAddr(63, RAM_TYPE_BIT+1) === 0.U
                && io.ramIO.writeAddr(RAM_TYPE_BIT) === 1.U
                && io.ramIO.writeAddr(SSD_HIGH_BIT, SSD_LOW_BIT) === ssdId.U
                && io.ramIO.writeAddr(QUEUE_LOW_BIT-1, ENTRY_HIGH_BIT_CQ+1) === 0.U
                && io.ramIO.writeMask =/= 0.U
            )
        } else { // Only 1 SSD.
            cqRam(ssdId).io.wr_en_a := (
                io.ramIO.writeAddr(63, RAM_TYPE_BIT+1) === 0.U
                && io.ramIO.writeAddr(RAM_TYPE_BIT) === 1.U
                && io.ramIO.writeAddr(QUEUE_LOW_BIT-1, ENTRY_HIGH_BIT_CQ+1) === 0.U
                && io.ramIO.writeMask =/= 0.U
            )
        }

        if (QUEUE_HIGH_BIT >= QUEUE_LOW_BIT && ENTRY_HIGH_BIT_CQ >= 6) { // More than 1 queue and 4 entries
            cqRam(ssdId).io.addr_a := Mux(cqRam(ssdId).io.wr_en_a,
                Cat(
                    io.ramIO.writeAddr(QUEUE_HIGH_BIT, QUEUE_LOW_BIT),
                    io.ramIO.writeAddr(ENTRY_HIGH_BIT_CQ, 6)
                ),
                Cat(
                    io.ramIO.readAddr(QUEUE_HIGH_BIT, QUEUE_LOW_BIT),
                    io.ramIO.readAddr(ENTRY_HIGH_BIT_CQ, 6)
                ),
            )
        } else if (QUEUE_HIGH_BIT < QUEUE_LOW_BIT && ENTRY_HIGH_BIT_CQ >= 6) {
            cqRam(ssdId).io.addr_a := Mux(cqRam(ssdId).io.wr_en_a, 
                io.ramIO.writeAddr(ENTRY_HIGH_BIT_CQ, 6),
                io.ramIO.readAddr(ENTRY_HIGH_BIT_CQ, 6),
            )
        } else if (QUEUE_HIGH_BIT >= QUEUE_LOW_BIT && ENTRY_HIGH_BIT_CQ < 6) {
            cqRam(ssdId).io.addr_a := Mux(cqRam(ssdId).io.wr_en_a, 
                io.ramIO.writeAddr(QUEUE_HIGH_BIT, QUEUE_LOW_BIT),
                io.ramIO.readAddr(QUEUE_HIGH_BIT, QUEUE_LOW_BIT)
            )
        } else {
            cqRam(ssdId).io.addr_a  := 0.U
        }
        cqRam(ssdId).io.musk_a.get  := io.ramIO.writeMask
        cqRam(ssdId).io.data_in_a   := io.ramIO.writeData
    }

    val nextReadAddr = RegNext(io.ramIO.readAddr)

    io.ramIO.readData := 0.U

    when (nextReadAddr(63, RAM_TYPE_BIT+1) === 0.U) {
        if (SSD_HIGH_BIT >= SSD_LOW_BIT) { // More than 1 SSD
            for (ssdId <- 0 until SSD_NUM) {
                when (ssdId.U === nextReadAddr(SSD_HIGH_BIT, SSD_LOW_BIT)) {
                    io.ramIO.readData := Mux(nextReadAddr(RAM_TYPE_BIT) === 0.U,
                        sqRam(ssdId).io.data_out_b,
                        cqRam(ssdId).io.data_out_a
                    )
                }
            }
        } else {
            io.ramIO.readData := Mux(nextReadAddr(RAM_TYPE_BIT) === 0.U,
                sqRam(0).io.data_out_b,
                cqRam(0).io.data_out_a
            )
        }
    }

    // Update SQ head from CQE.

    when (
        io.ramIO.writeAddr(63, RAM_TYPE_BIT+1) === 0.U
        && io.ramIO.writeAddr(RAM_TYPE_BIT) === 1.U
        && io.ramIO.writeAddr(QUEUE_LOW_BIT-1, ENTRY_HIGH_BIT_CQ+1) === 0.U
    ) {
        val chosenSsd   = if (SSD_HIGH_BIT >= SSD_LOW_BIT) {io.ramIO.writeAddr(SSD_HIGH_BIT, SSD_LOW_BIT)} else 0.U
        val chosenQp    = if (QUEUE_HIGH_BIT >= QUEUE_LOW_BIT) {io.ramIO.writeAddr(QUEUE_HIGH_BIT, QUEUE_LOW_BIT)} else 0.U
        when (io.ramIO.writeMask === "h000000000000ffff".U) {
            sqHead(chosenSsd)(chosenQp)      := io.ramIO.writeData(79, 64)
            commandEnd(chosenSsd)(chosenQp)  := commandEnd(chosenSsd)(chosenQp) + 1.U
        }.elsewhen (io.ramIO.writeMask === "h00000000ffff0000".U) {
            sqHead(chosenSsd)(chosenQp)      := io.ramIO.writeData(207, 192)
            commandEnd(chosenSsd)(chosenQp)  := commandEnd(chosenSsd)(chosenQp) + 1.U
        }.elsewhen (io.ramIO.writeMask === "h0000ffff00000000".U) {
            sqHead(chosenSsd)(chosenQp)      := io.ramIO.writeData(335, 320)
            commandEnd(chosenSsd)(chosenQp)  := commandEnd(chosenSsd)(chosenQp) + 1.U
        }.elsewhen (io.ramIO.writeMask === "hffff000000000000".U) {
            sqHead(chosenSsd)(chosenQp)      := io.ramIO.writeData(463, 448)
            commandEnd(chosenSsd)(chosenQp)  := commandEnd(chosenSsd)(chosenQp) + 1.U
        }
    }

    // Statistical counters

    for (ssdId <- 0 until SSD_NUM) {
        for (queueId <- 0 until QUEUE_NUM) {
            when (io.ssdCmd(ssdId)(queueId).fire) {
                statLatency := statLatency - statExecTime
            }
        }
    }

    when (
        io.ramIO.writeAddr(63, RAM_TYPE_BIT+1) === 0.U
        && io.ramIO.writeAddr(RAM_TYPE_BIT) === 1.U
        && io.ramIO.writeAddr(QUEUE_LOW_BIT-1, ENTRY_HIGH_BIT_CQ+1) === 0.U
    ) {
        when (io.ramIO.writeMask === "h000000000000ffff".U) {
            statLatency := statLatency + statExecTime
            when (io.ramIO.writeData(122, 113) === 0.U) {
                statSuccOp   := statSuccOp + 1.U
            }.otherwise (
                statFailedOp := statFailedOp + 1.U
            )
        }.elsewhen (io.ramIO.writeMask === "h00000000ffff0000".U) {
            statLatency := statLatency + statExecTime
            when (io.ramIO.writeData(250, 241) === 0.U) {
                statSuccOp   := statSuccOp + 1.U
            }.otherwise (
                statFailedOp := statFailedOp + 1.U
            )
        }.elsewhen (io.ramIO.writeMask === "h0000ffff00000000".U) {
            statLatency := statLatency + statExecTime
            when (io.ramIO.writeData(378, 369) === 0.U) {
                statSuccOp   := statSuccOp + 1.U
            }.otherwise (
                statFailedOp := statFailedOp + 1.U
            )
        }.elsewhen (io.ramIO.writeMask === "hffff000000000000".U) {
            statLatency := statLatency + statExecTime
            when (io.ramIO.writeData(506, 497) === 0.U) {
                statSuccOp   := statSuccOp + 1.U
            }.otherwise (
                statFailedOp := statFailedOp + 1.U
            )
        }
    }

}

object ShiftData512 {
    def apply (value : UInt, offset : UInt) = {
        assert(value.getWidth == 512)
        assert(offset.getWidth == 6)
        value << Cat(offset, 0.U(3.W))
    }
}

object ShiftStrb64 {
    def apply (value : UInt, offset : UInt) = {
        assert(value.getWidth == 64)
        assert(offset.getWidth == 6)
        value << offset
    }
}