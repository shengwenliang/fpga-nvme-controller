package nvme

import chisel3._
import chisel3.util._

class NVMeInterface (
    SSD_MAX_ID          : Int = 0,
    BUFFER_DATA_SHIFT   : Int = 27,
    SSD_NUM_SHIFT       : Int = 2,
    QUEUE_DEPTH_SHIFT   : Int = 8,
    QUEUE_MAX_ID        : Int = 3,
    QUEUE_COUNT_SHIFT   : Int = 2,
    MAX_SQ_INTERVAL     : Int = 30
) extends Module {
    val io = IO(new Bundle{
		val h2cCmd		= Decoupled(new H2C_CMD)
		val h2cData	    = Flipped(Decoupled(new H2C_DATA))
		val c2hCmd		= Decoupled(new C2H_CMD)
		val c2hData	    = Decoupled(new C2H_DATA)

        val ssdCmd      = Flipped(Decoupled(Vec(SSD_MAX_ID+1, Vec(QUEUE_MAX_ID+1, new NVMeCommand))))

        val regControl  = Input(Vec(512,UInt(32.W)))
        val regStatus   = Output(Vec(512,UInt(32.W)))

        val bramReq     = Flipped(new NVMeBRAM)
    })

    val nvmeCore = Module(new NVMeBlackBox(
        SSD_MAX_ID          = SSD_MAX_ID,
        BUFFER_DATA_SHIFT   = BUFFER_DATA_SHIFT,
        SSD_NUM_SHIFT       = SSD_NUM_SHIFT,
        QUEUE_DEPTH_SHIFT   = QUEUE_DEPTH_SHIFT,
        QUEUE_MAX_ID        = QUEUE_MAX_ID,
        QUEUE_COUNT_SHIFT   = QUEUE_COUNT_SHIFT,
        MAX_SQ_INTERVAL     = MAX_SQ_INTERVAL
    ))

    nvmeCore.io.clk_core = clock
    nvmeCore.io.sys_reset = reset
}