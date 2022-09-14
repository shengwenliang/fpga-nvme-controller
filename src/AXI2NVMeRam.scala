package nvme

import chisel3._
import chisel3.util._
import common._
import common.axi._
import common.storage._
import common.connection._

object AXI2NVMeRam {
    def apply(in : AXI) = {
        val inst = Module(new AXI2NVMeRam(
			in.ar.bits.addr.getWidth,
			in.r.bits.data.getWidth, 
			in.ar.bits.id.getWidth, 
			in.ar.bits.user.getWidth, 
			in.ar.bits.len.getWidth
        ))
        val out = Wire(new NVMeRamIO)
        inst.io.in  <> in
        inst.io.out <> out

        out
    }

    class AXI2NVMeRam(ADDR_WIDTH:Int, DATA_WIDTH:Int, ID_WIDTH:Int, USER_WIDTH:Int, LEN_WIDTH:Int) extends Module {
        val io = IO(new Bundle{
            val in  = Flipped(new AXI(ADDR_WIDTH, DATA_WIDTH, ID_WIDTH, USER_WIDTH, LEN_WIDTH))
            val out = new NVMeRamIO
        })

        // W channels

        val rWid        = RegInit(UInt(ID_WIDTH.W), 0.U)
        val rNextWrAddr = RegInit(UInt(ADDR_WIDTH.W), 0.U)

        val sWrReq :: sWrData :: Nil = Enum(2)
        val wrSt    = RegInit(sWrReq)
        
        val wrFirstBeat     = (wrSt === sWrReq) && io.in.aw.fire && io.in.w.fire
        val wrRemainBeat    = (wrSt === sWrData) && io.in.w.fire

        val backFifo    = XQueue(UInt(ID_WIDTH.W), 32)

        io.out.writeMask    := Mux(io.in.w.fire, io.in.w.bits.strb, 0.U)
        io.out.writeAddr    := Mux(wrFirstBeat, Cat(io.in.aw.bits.addr(63, 6), 0.U(6.W)), rNextWrAddr)
        io.out.writeData    := io.in.w.bits.data

        io.in.aw.ready      := (wrSt === sWrReq)
        io.in.w.ready       := backFifo.io.in.ready
        io.in.b.bits.id     := backFifo.io.out.bits
        io.in.b.bits.resp   := 0.U
        io.in.b.bits.user   := 0.U
        io.in.b.valid       := backFifo.io.out.valid
        backFifo.io.out.ready   := io.in.b.ready

        backFifo.io.in.valid    := io.in.w.fire && io.in.w.bits.last.asBool
        backFifo.io.in.bits     := rWid

        switch (wrSt) {
            is (sWrReq) {
                when (io.in.aw.fire) { // Received a request
                    when (io.in.w.fire && io.in.w.bits.last.asBool) { // 1-beat data, already handled.
                        wrSt := sWrReq
                    }.otherwise {
                        wrSt := sWrData
                    }
                }.otherwise {
                    wrSt := sWrReq
                }
            }
            is (sWrData) {
                when (io.in.w.fire && io.in.w.bits.last.asBool) { // Last beat ends
                    wrSt := sWrReq
                }.otherwise {
                    wrSt := sWrData
                }
            }
        }

        when (io.in.aw.fire) {
            rWid        := io.in.aw.bits.id
            rNextWrAddr   := Mux(
                wrFirstBeat, 
                Cat(io.in.aw.bits.addr(63, 6), 0.U(6.W)) + "h40".U,
                Cat(io.in.aw.bits.addr(63, 6), 0.U(6.W))
            )
        }.elsewhen (wrRemainBeat) {
            when (!io.in.w.bits.last.asBool){
                rNextWrAddr   := rNextWrAddr + "h40".U
            }
        }

        // R channels

        val rRid        = RegInit(UInt(ID_WIDTH.W), 0.U)
        val rLen        = RegInit(UInt(LEN_WIDTH.W), 0.U)
        val rNextRdAddr = RegInit(UInt(ADDR_WIDTH.W), 0.U)

        val sRdReq :: sRdData :: Nil = Enum(2)
        val rdSt    = RegInit(sRdReq)

        val rdFirstBeat     = (rdSt === sRdReq) && io.in.ar.fire
        val rdRemainBeat    = (rdSt === sRdData) && io.in.r.fire && (rLen =/= 0.U)

        io.out.readAddr     := Mux(rdFirstBeat, Cat(io.in.ar.bits.addr(63, 6), 0.U(6.W)), rNextRdAddr)
        io.out.readEnable   := rdFirstBeat || rdRemainBeat

        io.in.ar.ready      := (rdSt === sRdReq)
        io.in.r.valid       := (rdSt === sRdData)
        io.in.r.bits.id     := rRid
        io.in.r.bits.user   := 0.U
        io.in.r.bits.last   := (rdSt === sRdData && rLen === 0.U)
        io.in.r.bits.data   := io.out.readData
        io.in.r.bits.resp   := 0.U

        switch (rdSt) {
            is (sRdReq) {
                when (io.in.ar.fire) {
                    rdSt := sRdData
                }.otherwise {
                    rdSt := sRdReq
                }
            }
            is (sRdData) {
                when (io.in.r.fire && io.in.r.bits.last.asBool) {
                    rdSt := sRdReq
                }.otherwise {
                    rdSt := sRdData
                }
            }
        }

        when (rdFirstBeat) {
            rLen        := io.in.ar.bits.len
            rNextRdAddr := Cat(io.in.ar.bits.addr(63, 6), 0.U(6.W)) + "h40".U
            rRid        := io.in.ar.bits.id
        }.elsewhen(rdRemainBeat) {
            when (rLen =/= 1.U) {
                rNextRdAddr := rNextRdAddr + "h40".U
            }
            rLen        := rLen - 1.U
        }
    }
}