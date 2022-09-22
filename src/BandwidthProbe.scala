package nvme
import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum
import common.axi._
import common.storage._
import common._
import math.ceil

class BandwidthProbe (
    CYCLE       : Int = 25000000,
    DEPTH       : Int = 4096
) extends Module {
    val io = IO(new Bundle{
        val enable  = Input(Bool())
        val fire    = Input(Bool())
        val count   = Decoupled(Output(UInt(32.W)))
    })

    val time_cnt = RegInit(UInt(log2Ceil(CYCLE).W), 0.U)
    val record_valid = time_cnt === (CYCLE-1).U
    val band_cnt = RegInit(UInt(32.W), 0.U)

    when (~io.enable) {
        time_cnt := 0.U
        band_cnt := 0.U
    }.otherwise {
        when (record_valid) {
            time_cnt := 0.U
        }.otherwise {
            time_cnt := time_cnt + 1.U
        }

        when (record_valid) {
            when (io.fire) {
                band_cnt := 1.U
            }.otherwise {
                band_cnt := 0.U
            }
        }.elsewhen (io.fire) {
            band_cnt := band_cnt + 1.U
        }
    }

    val FIFO_CNT = ceil(DEPTH / 4096).toInt

    if (FIFO_CNT == 0) {
        val q = XQueue(UInt(32.W), DEPTH)
        q.io.out        <> io.count
        q.io.in.valid   := record_valid
        q.io.in.bits    := band_cnt
    } else {
        val q = XQueue(FIFO_CNT)(UInt(32.W), 4096)
        q(0).io.in.valid        := record_valid
        q(0).io.in.bits         := band_cnt
        q(FIFO_CNT-1).io.out    <> io.count
        for (i <- 1 until FIFO_CNT) {
            q(i-1).io.out       <> q(i).io.in
        }
    }

}