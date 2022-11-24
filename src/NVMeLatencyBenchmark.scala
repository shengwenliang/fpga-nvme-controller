package nvme

import chisel3._
import chisel3.util._
import common._
import common.storage._
import common.axi._
import math.max

// Runs a simple NVMe benchmark.

class NVMeLatencyBenchmark (
    SSD_NUM             : Int,
    DATA_BUFFER_SHIFT   : Int
) extends Module {
    val io = IO(new Bundle {
        // Interfaces
        val prpInput        = Flipped(new NVMeRamIO)
        val ssdCmd          = Vec(SSD_NUM, Decoupled(UInt(512.W)))
        val ssdCmpt         = Vec(SSD_NUM, Flipped(Valid(new SSDCompletion)))
        
        // Control
        val ctlRunning      = Input(Bool())
        val ctlEnd          = Input(Bool())
        val ctlFpgaBar      = Input(UInt(64.W))
        val ctlTimeTarget   = Input(UInt(38.W))
        val ctlNumNlb       = Input(UInt(16.W))
        val ctlMaxParallel  = Input(UInt(32.W))
        val ctlModeWrite    = Input(UInt(1.W))
        val ctlModeRandom   = Input(UInt(1.W))
        val ctlModeMixed    = Input(UInt(1.W))
        val ctlReadLatency  = Flipped(Valid(UInt(13.W)))
        // Status
        val statSsdIo       = Output(Vec(SSD_NUM, UInt(32.W)))
        val statLatency     = Output(UInt(32.W))
    })

    // Control and status signals.

    val ctlTimeTarget   = RegInit(UInt(38.W), 0.U)
    val ctlNumNlb       = RegInit(UInt(16.W), 0.U)
    val ctlMaxParallel  = RegInit(UInt(32.W), 0.U)
    val ctlModeWrite    = RegInit(UInt(1.W), 0.U)
    val ctlModeRandom   = RegInit(UInt(1.W), 0.U)
    val ctlModeMixed    = RegInit(UInt(1.W), 0.U)

    val statSsdIo       = RegInit(VecInit(Seq.fill(SSD_NUM)(0.U(32.W))))
    for (ssdId <- 0 until SSD_NUM) {
        io.statSsdIo(ssdId) := statSsdIo(ssdId)
    }

    // Initiate signals 

    when (io.ctlRunning && ~RegNext(io.ctlRunning)) {
        ctlTimeTarget   := io.ctlTimeTarget
        ctlNumNlb       := io.ctlNumNlb
        ctlMaxParallel  := io.ctlMaxParallel
        ctlModeWrite    := io.ctlModeWrite
        ctlModeRandom   := io.ctlModeRandom
        ctlModeMixed    := io.ctlModeMixed
        for (ssdId <- 0 until SSD_NUM) {
            statSsdIo(ssdId)    := 0.U
        }
    }

    // In this example, data transfer is between FPGA and SSDs.
    // I give each queue a fixed buffer.

    val SSD_BIT_LOW     = DATA_BUFFER_SHIFT - log2Ceil(SSD_NUM)
    val QUEUE_BIT_LOW   = SSD_BIT_LOW - 1
    val PRP_ADDR_MSB    = DATA_BUFFER_SHIFT - 10

    // Global timer
    val gblTimer = RegInit(UInt(64.W), 0.U)
    gblTimer    := gblTimer + 1.U(64.W)

    val startTimeRam = XRam(SSD_NUM)(UInt(64.W), 256, latency=2)

    // Generate commands

    for (ssdId <- 0 until SSD_NUM) {
        val cmdOutStd   = RegInit(UInt(32.W), 0.U)

        val cmdRdCond   = (cmdOutStd =/= ctlMaxParallel)
        val cmdWrCond   = (cmdOutStd =/= ctlMaxParallel)

        val cmdLba      = RegInit(0.U(64.W))
        val cmdPrp1     = Wire(UInt(64.W))
        val cmdPrp2     = Wire(UInt(64.W))
        val cmdNlb      = RegInit(0.U(16.W))
        val cmdId       = RegInit(0.U(16.W))

        cmdPrp1 := (io.ctlFpgaBar 
            + (1.U(64.W) << DATA_BUFFER_SHIFT) 
            + (ssdId.U(64.W) << SSD_BIT_LOW)
        )
        cmdPrp2 := Mux(
            cmdNlb < 16.U,
            cmdPrp1 + 0x1000.U(64.W),
            (
                io.ctlFpgaBar 
                + (1.U(64.W) << DATA_BUFFER_SHIFT) 
                - (1.U(64.W) << (DATA_BUFFER_SHIFT-9))
                + (ssdId.U(64.W) << (SSD_BIT_LOW-9))
            )
        )

        io.ssdCmd(ssdId).valid   := Mux(ctlModeWrite.asBool,
            (   // Write
                io.ctlRunning && RegNext(io.ctlRunning)
                && cmdWrCond && ctlModeWrite.asBool
            ),
            (   // Read
                io.ctlRunning && RegNext(io.ctlRunning)
                && cmdRdCond && ~ctlModeWrite.asBool
            )
        )
        io.ssdCmd(ssdId).bits    := Mux(ctlModeWrite.asBool,
            NVMeCommandSet.nvmWrite(
                cmdId, cmdPrp1, cmdPrp2, cmdLba, cmdNlb
            ),
            NVMeCommandSet.nvmRead(
                cmdId, cmdPrp1, cmdPrp2, cmdLba, cmdNlb
            )
        )

        when (io.ctlRunning && ~RegNext(io.ctlRunning)) {
            cmdId       := 0.U
            when (io.ctlModeRandom.asBool) {
                cmdLba  := Cat("h92918".U(20.W), 0.U(10.W))
            }.otherwise {
                cmdLba  := 0.U
            }
            cmdNlb      := io.ctlNumNlb
            cmdOutStd   := 0.U
        }.elsewhen (io.ctlRunning) {
            when (io.ssdCmd(ssdId).fire) {
                when (ctlModeRandom.asBool) {
                    val nextRndPart = Wire(UInt(20.W))
                    nextRndPart := (cmdLba(29, 10) << 5) + (cmdLba(29, 10) >> 5)
                    cmdLba      := Cat(nextRndPart, 0.U(10.W))
                }.otherwise {
                    val nextSeqPart = Wire(UInt(30.W))
                    nextSeqPart := cmdLba + 1.U(30.W) + ctlNumNlb
                    cmdLba      := nextSeqPart
                }

                cmdId       := cmdId + 1.U
                statSsdIo(ssdId)    := statSsdIo(ssdId) + 1.U
                cmdOutStd   := cmdOutStd + 1.U
            }

            when (io.ssdCmpt(ssdId).valid) {
                cmdOutStd   := cmdOutStd - 1.U
            }
        } // elsewhen (io.ctlRunning)

        // Start time RAM. We track latency for each command.

        startTimeRam(ssdId).io.addr_a      := cmdId(7, 0)
        startTimeRam(ssdId).io.data_in_a   := gblTimer
        startTimeRam(ssdId).io.wr_en_a     := io.ssdCmd(ssdId).fire
        startTimeRam(ssdId).io.addr_b      := io.ssdCmpt(ssdId).bits.cmdId(7, 0)
        
    } // for (ssdId <- 0 until SSD_NUM)

    val cmptId  = Wire(UInt(max(1, log2Ceil(SSD_NUM)).W))
    
    cmptId  := 0.U
    for (ssdId <- 0 until SSD_NUM) {
        when (io.ssdCmpt(ssdId).valid) {
            cmptId := ssdId.U
        }
    }

    val latencyBits = Wire(UInt(64.W))
    latencyBits := 0.U
    for (ssdId <- 0 until SSD_NUM) {
        when (RegNext(RegNext(cmptId)) === ssdId.U) {
            latencyBits := gblTimer - startTimeRam(ssdId).io.data_out_b
        }
    }

    // Latency RAM. 

    val latencyValid = RegNext(RegNext(io.ssdCmpt(cmptId).valid))

    val latencyRam = XRam(UInt(32.W), 8192, latency=2, memory_type="ultra")
    latencyRam.io.addr_a    := Mux(io.ctlRunning, RegNext(RegNext(latencyRam.io.addr_b)) ,io.ctlReadLatency.bits)
    latencyRam.io.wr_en_a   := io.ctlRunning && latencyValid
    latencyRam.io.addr_b    := Mux(latencyBits(63, 16) === 0.U, latencyBits(15, 3), -1.S(13.W).asUInt)
    latencyRam.io.data_in_a := latencyRam.io.data_out_b + 1.U
    io.statLatency          := latencyRam.io.data_out_a

    // Generate PRP list

    io.prpInput.readData    := Cat(
        io.ctlFpgaBar(63, DATA_BUFFER_SHIFT+1), 1.U(1.W), io.prpInput.readAddr(PRP_ADDR_MSB, 6) + 1.U((PRP_ADDR_MSB-5).W), 0x0000.U(15.W),
        io.ctlFpgaBar(63, DATA_BUFFER_SHIFT+1), 1.U(1.W), io.prpInput.readAddr(PRP_ADDR_MSB, 6), 0x7000.U(15.W),
        io.ctlFpgaBar(63, DATA_BUFFER_SHIFT+1), 1.U(1.W), io.prpInput.readAddr(PRP_ADDR_MSB, 6), 0x6000.U(15.W),
        io.ctlFpgaBar(63, DATA_BUFFER_SHIFT+1), 1.U(1.W), io.prpInput.readAddr(PRP_ADDR_MSB, 6), 0x5000.U(15.W),
        io.ctlFpgaBar(63, DATA_BUFFER_SHIFT+1), 1.U(1.W), io.prpInput.readAddr(PRP_ADDR_MSB, 6), 0x4000.U(15.W),
        io.ctlFpgaBar(63, DATA_BUFFER_SHIFT+1), 1.U(1.W), io.prpInput.readAddr(PRP_ADDR_MSB, 6), 0x3000.U(15.W),
        io.ctlFpgaBar(63, DATA_BUFFER_SHIFT+1), 1.U(1.W), io.prpInput.readAddr(PRP_ADDR_MSB, 6), 0x2000.U(15.W),
        io.ctlFpgaBar(63, DATA_BUFFER_SHIFT+1), 1.U(1.W), io.prpInput.readAddr(PRP_ADDR_MSB, 6), 0x1000.U(15.W),
    )
}