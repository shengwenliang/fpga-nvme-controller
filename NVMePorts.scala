package nvme

import chisel3._
import chisel3.util._
import common._
import common.storage._
import common.axi._
import common.ToZero

class NVMeCommand extends Bundle{
    val op          = Output(UInt(1.W))
    val numLb       = Output(UInt(16.W))
    val ssdAddr     = Output(UInt(64.W))
    val memAddr     = Output(UInt(64.W))
}

class NVMeBRAM extends Bundle{
    val readEnable  = Output(UInt(1.W))
    val readAddr    = Output(UInt(64.W))
    val readData    = Input(UInt(512.W))
    val writeMask   = Output(UInt(64.W))
    val writeAddr   = Output(UInt(64.W))
    val writeData   = Output(UInt(512.W))
}