package nvme

import chisel3._
import chisel3.util._
import common._
import common.storage._
import common.axi._
import common.ToZero

class NVMeRamIO extends Bundle{
    val readEnable  = Output(UInt(1.W))
    val readAddr    = Output(UInt(64.W))
    val readData    = Input(UInt(512.W))
    val writeMask   = Output(UInt(64.W))
    val writeAddr   = Output(UInt(64.W))
    val writeData   = Output(UInt(512.W))
}

class SSDSetup extends Bundle {
    val ssdId           = Output(UInt(32.W))
    val ssdBarAddr      = Output(UInt(64.W))
}

class NVMeCoreControl extends Bundle {
    val enable          = Output(Bool())
    val ssdSetup        = Valid(new SSDSetup)
}

class NVMeParameters extends Bundle {
    val ssdNum          = Output(UInt(32.W))
    val queueNum        = Output(UInt(32.W))
    val queueLowBit     = Output(UInt(32.W))
    val ssdLowBit       = Output(UInt(32.W))
    val queueDepth      = Output(UInt(32.W))
    val ramTypeBit      = Output(UInt(32.W))
}

class NVMeStat extends Bundle {
    val executeTime     = Output(UInt(64.W))
    val successfulOp    = Output(UInt(32.W))
    val failedOp        = Output(UInt(32.W))
    val totalLatency    = Output(UInt(64.W))
}

class NVMeCoreStatus extends Bundle {
    val running         = Output(Bool())
    val params          = new NVMeParameters
    val stat            = new NVMeStat
}