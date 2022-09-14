package nvme

import chisel3._
import chisel3.util._
import common._
import common.storage._
import common.axi._
import qdma._

class TestAXIRouter extends Module {
    val io = IO(new Bundle {
        val axibIn  = Flipped(new AXIB)
        val ramOut  = Vec(3, new NVMeRamIO)
    })

    val axibRt = AXIRouter(3, io.axibIn)	
    axibRt.io.in    <> io.axibIn
    axibRt.io.wrIdx := Mux(
        axibRt.io.in.aw.bits.addr(27),
        2.U,
        Mux(axibRt.io.in.aw.bits.addr(26), 1.U, 0.U)
    )
    axibRt.io.rdIdx := Mux(
        axibRt.io.in.ar.bits.addr(27),
        2.U,
        Mux(axibRt.io.in.ar.bits.addr(26), 1.U, 0.U)
    )
    for (idx <- 0 until 3) {
        io.ramOut(idx)  <> AXI2NVMeRam(axibRt.io.out(idx))
    }
}