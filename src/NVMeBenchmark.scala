package nvme

import chisel3._
import chisel3.util._
import common._
import common.storage._
import common.axi._
import math.max

// Runs a simple NVMe benchmark.

class NVMeBenchmark (
    SSD_NUM             : Int,
    QUEUE_NUM           : Int,
    DATA_BUFFER_SHIFT   : Int
) extends Module {
    val io = IO(new Bundle {
        // Interfaces
        val prpInput        = Flipped(new NVMeRamIO)
        val ssdCmd          = Vec(SSD_NUM, Vec(QUEUE_NUM, Decoupled(UInt(512.W))))
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
        val ctlRdBlkSize    = Input(UInt(32.W))
        val ctlWrBlkSize    = Input(UInt(32.W))
        val ctlRdBlkAhead   = Input(UInt(32.W))
        // Status
        val statSsdIo       = Output(Vec(SSD_NUM, UInt(32.W)))
    })

    assert (QUEUE_NUM >= 2, "At least 2 queues must be used for benchmarking")

    // Control and status signals.

    val ctlTimeTarget   = RegInit(UInt(38.W), 0.U)
    val ctlNumNlb       = RegInit(UInt(16.W), 0.U)
    val ctlMaxParallel  = RegInit(UInt(32.W), 0.U)
    val ctlModeWrite    = RegInit(UInt(1.W), 0.U)
    val ctlModeRandom   = RegInit(UInt(1.W), 0.U)
    val ctlModeMixed    = RegInit(UInt(1.W), 0.U)
    val ctlRdBlkSize    = RegInit(UInt(32.W), 0.U)
    val ctlWrBlkSize    = RegInit(UInt(32.W), 0.U)
    val ctlRdBlkAhead   = RegInit(UInt(32.W), 0.U)

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
        ctlRdBlkSize    := io.ctlRdBlkSize
        ctlWrBlkSize    := io.ctlWrBlkSize
        ctlRdBlkAhead   := io.ctlRdBlkAhead
        for (ssdId <- 0 until SSD_NUM) {
            statSsdIo(ssdId)    := 0.U
        }
    }

    // In this example, data transfer is between FPGA and SSDs.
    // I give each queue a fixed buffer.

    val SSD_BIT_LOW     = DATA_BUFFER_SHIFT - log2Ceil(SSD_NUM)
    val QUEUE_BIT_LOW   = SSD_BIT_LOW - 1
    val PRP_ADDR_MSB    = DATA_BUFFER_SHIFT - 10

    // Generate commands

    for (ssdId <- 0 until SSD_NUM) {
        val cmdRdCnt    = RegInit(UInt(32.W), 0.U)
        val cmdWrCnt    = RegInit(UInt(32.W), 0.U)
        val cmdRdBlk    = RegInit(UInt(32.W), 0.U)
        val cmdWrBlk    = RegInit(UInt(32.W), 0.U)

        val cmdRdCond   = (cmdWrBlk + ctlRdBlkAhead =/= cmdRdBlk)
        val cmdWrCond   = (cmdWrBlk =/= cmdRdBlk)

        val cmdLba      = RegInit(VecInit(Seq.fill(2)(0.U(64.W))))
        val cmdPrp1     = Wire(Vec(2, UInt(64.W)))
        val cmdPrp2     = Wire(Vec(2, UInt(64.W)))
        val cmdNlb      = RegInit(VecInit(Seq.fill(2)(0.U(16.W))))
        val cmdId       = RegInit(VecInit(Seq.fill(2)(0.U(16.W))))

        cmdPrp1(0) := (io.ctlFpgaBar 
            + (1.U(64.W) << DATA_BUFFER_SHIFT) 
            + (ssdId.U(64.W) << SSD_BIT_LOW)
        )
        cmdPrp1(1) := (io.ctlFpgaBar 
            + (1.U(64.W) << DATA_BUFFER_SHIFT) 
            + (ssdId.U(64.W) << SSD_BIT_LOW)
            + (1.U(64.W) << QUEUE_BIT_LOW)
        )
        cmdPrp2(0) := Mux(
            cmdNlb(0) < 16.U,
            cmdPrp1(0) + 0x1000.U(64.W),
            (
                io.ctlFpgaBar 
                + (1.U(64.W) << DATA_BUFFER_SHIFT) 
                - (1.U(64.W) << (DATA_BUFFER_SHIFT-9))
                + (ssdId.U(64.W) << (SSD_BIT_LOW-9))
            )
        )
        cmdPrp2(1) := Mux(
            cmdNlb(0) < 16.U,
            cmdPrp2(0) + 0x1000.U(64.W),
            (
                io.ctlFpgaBar 
                + (1.U(64.W) << DATA_BUFFER_SHIFT) 
                - (1.U(64.W) << (DATA_BUFFER_SHIFT-9))
                + (ssdId.U(64.W) << (SSD_BIT_LOW-9))
                + (1.U(64.W) << (QUEUE_BIT_LOW-9))
            )
        )

        io.ssdCmd(ssdId)(0).valid   := (
            io.ctlRunning && RegNext(io.ctlRunning)
            && Mux(ctlModeMixed.asBool, cmdRdCond, ~ctlModeWrite.asBool)
        )
        io.ssdCmd(ssdId)(0).bits    := NVMeCommandSet.nvmRead(
            cmdId(0), cmdPrp1(0), cmdPrp2(0), cmdLba(0), cmdNlb(0)
        )
        io.ssdCmd(ssdId)(1).valid   := (
            io.ctlRunning && RegNext(io.ctlRunning)
            && Mux(ctlModeMixed.asBool, cmdWrCond, ctlModeWrite.asBool)
        )
        io.ssdCmd(ssdId)(1).bits    := NVMeCommandSet.nvmWrite(
            cmdId(1), cmdPrp1(1), cmdPrp2(1), cmdLba(1), cmdNlb(1)
        )
        for (queueId <- 2 until QUEUE_NUM) {
            io.ssdCmd(ssdId)(queueId).valid := 0.U
            io.ssdCmd(ssdId)(queueId).bits  := 0.U
        }

        when (io.ctlRunning && ~RegNext(io.ctlRunning)) {
            cmdId(0)    := 0.U
            cmdId(1)    := 1.U
            when (io.ctlModeRandom.asBool) {
                cmdLba(0)   := Cat("h92918".U(20.W), 0.U(10.W))
                cmdLba(1)   := Cat(1.U(1.W), "h92918".U(20.W), 0.U(10.W))
            }.otherwise {
                cmdLba(0)   := 0.U
                cmdLba(1)   := Cat(1.U(1.W), 0.U(30.W))
            }
            cmdNlb(0)   := io.ctlNumNlb
            cmdNlb(1)   := io.ctlNumNlb
            cmdRdCnt    := 0.U
            cmdWrCnt    := 0.U
            cmdRdBlk    := 0.U
            cmdWrBlk    := 0.U
        }.elsewhen (io.ctlRunning) {
            when (io.ssdCmd(ssdId)(0).fire) {
                when (ctlModeRandom.asBool) {
                    val nextRndPart = Wire(UInt(20.W))
                    nextRndPart := (cmdLba(0)(29, 10) << 5) + (cmdLba(0)(29, 10) >> 5)
                    cmdLba(0)   := Cat(nextRndPart, 0.U(10.W))
                }.otherwise {
                    val nextSeqPart = Wire(UInt(30.W))
                    nextSeqPart := cmdLba(0) + 1.U(30.W) + ctlNumNlb
                    cmdLba(0)   := nextSeqPart
                }

                when (cmdRdCnt + 1.U =/= ctlRdBlkSize) {
                    cmdRdCnt    := cmdRdCnt + 1.U
                }.otherwise {
                    cmdRdBlk    := cmdRdBlk + 1.U
                    cmdRdCnt    := 0.U
                }

                cmdId(0)    := cmdId(0) + 2.U
                statSsdIo(ssdId)    := statSsdIo(ssdId) + 1.U
            }

            when (io.ssdCmd(ssdId)(1).fire) {
                when (ctlModeRandom.asBool) {
                    val nextRndPart = Wire(UInt(20.W))
                    nextRndPart := (cmdLba(1)(29, 10) << 5) + (cmdLba(1)(29, 10) >> 5)
                    cmdLba(1)   := Cat(1.U(1.W), nextRndPart, 1.U(10.W))
                }.otherwise {
                    val nextSeqPart = Wire(UInt(30.W))
                    nextSeqPart := cmdLba(1) + 1.U(30.W) + ctlNumNlb
                    cmdLba(1)   := Cat(1.U(1.W), nextSeqPart)
                }

                when (cmdWrCnt + 1.U =/= ctlWrBlkSize) {
                    cmdWrCnt    := cmdWrCnt + 1.U
                }.otherwise {
                    cmdWrBlk    := cmdWrBlk + 1.U
                    cmdWrCnt    := 0.U
                }

                cmdId(1)    := cmdId(1) + 2.U
                statSsdIo(ssdId)    := statSsdIo(ssdId) + 1.U
            }
        } // elsewhen (io.ctlRunning)
    } // for (ssdId <- 0 until SSD_NUM)

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