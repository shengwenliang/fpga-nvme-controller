package nvme

import chisel3._
import chisel3.util._
import common._
import common.storage._
import common.axi._
import common.ToZero
import qdma._

class NVMeBlackBox (
    SSD_MAX_ID          : Int = 0,
    BUFFER_DATA_SHIFT   : Int = 27,
    SSD_NUM_SHIFT       : Int = 2,
    QUEUE_DEPTH_SHIFT   : Int = 8,
    QUEUE_MAX_ID        : Int = 3,
    QUEUE_COUNT_SHIFT   : Int = 2,
    MAX_SQ_INTERVAL     : Int = 30
) extends BlackBox(Map(
    "SSD_NUM"           -> SSD_MAX_ID,
    "BRIDGE_DATA_SHIFT" -> BUFFER_DATA_SHIFT,
    "SSD_NUM_SHIFT"     -> SSD_NUM_SHIFT,
    "QUEUE_DEPTH_SHIFT" -> QUEUE_DEPTH_SHIFT,
    "QUEUE_COUNT_SHIFT" -> QUEUE_COUNT_SHIFT,
    "MAX_SQ_INTERVAL"   -> MAX_SQ_INTERVAL,
    "USED_QUEUE_MAX_ID" -> QUEUE_MAX_ID
)) {
    val QUEUE_NUM = (SSD_MAX_ID+1) * (QUEUE_MAX_ID+1)

    val io = IO(new Bundle{
        val clk_core    = Input(Clock())
        val sys_reset   = Input(Bool())

        val status_p2pdma_h2c_data  = Output(UInt(512.W))   // Reg(111:96)
        val status_p2pdma_h2c_done  = Output(UInt(1.W))     // Reg(128)
        val status_p2pdma_c2h_done  = Output(UInt(1.W))     // Reg(129)
        val status_nvme_init_done   = Output(UInt(1.W))     // Reg(160)
        val status_nvme_exec_done   = Output(UInt(1.W))     // Reg(192)
        val status_stat_op_succ     = Output(UInt(32.W))    // Reg(193)
        val status_stat_op_fail     = Output(UInt(32.W))    // Reg(194)
        val status_stat_exec_time   = Output(UInt(64.W))    // Reg(197:196) 
        val status_stat_io_ssd0     = Output(UInt(32.W))    // Reg(200)
        val status_stat_io_ssd1     = Output(UInt(32.W))    // Reg(201)
        val status_stat_io_ssd2     = Output(UInt(32.W))    // Reg(202)
        val status_stat_io_ssd3     = Output(UInt(32.W))    // Reg(203)
        val status_band_tr_rd       = Output(UInt(32.W))    // Reg(216)
        val status_band_tr_wr       = Output(UInt(32.W))    // Reg(217)

        val control_init_start      = Input(UInt(1.W))      // Reg(32)
        val control_init_nsid       = Input(UInt(32.W))     // Reg(33)
        val control_init_dma_addr   = Input(UInt(64.W))     // Reg(35:34)
        val control_init_byp_addr   = Input(UInt(64.W))     // Reg(37:36)
        val control_init_ssd_addr   = Input(UInt(64.W))     // Reg(39:38)
        val control_init_ssdid      = Input(UInt(32.W))     // Reg(40)
        val control_p2pdma_read     = Input(UInt(1.W))      // Reg(64)
        val control_p2pdma_write    = Input(UInt(1.W))      // Reg(65)
        val control_p2pdma_cmd_addr = Input(UInt(64.W))     // Reg(67:66)
        val control_p2pdma_cmd_len  = Input(UInt(16.W))     // Reg(68)
        val control_p2pdma_c2h_data = Input(UInt(512.W))    // Reg(84:69)
        val control_ssd_init        = Input(UInt(1.W))      // Reg(128)
        val control_exec_start      = Input(UInt(1.W))      // Reg(160)
        val control_exec_time       = Input(UInt(32.W))     // Reg(163)
        val control_band_tr_en      = Input(UInt(1.W))      // Reg(165)
        val control_band_tr_read    = Input(UInt(1.W))      // Reg(166)

		val s_axib_awid		= Output(UInt(4.W))
		val s_axib_awaddr	= Output(UInt(64.W))
		val s_axib_awlen	= Output(UInt(8.W))
		val s_axib_awsize	= Output(UInt(3.W))
		val s_axib_awburst	= Output(UInt(2.W))
		val s_axib_awuser	= Output(UInt(12.W))
		val s_axib_awregion	= Output(UInt(4.W))
		val s_axib_awvalid	= Output(UInt(1.W))
		val s_axib_awready	= Input(UInt(1.W))
		val s_axib_wdata	= Output(UInt(512.W))
		val s_axib_wstrb	= Output(UInt(64.W))
		val s_axib_wlast	= Output(UInt(1.W))
		val s_axib_wuser	= Output(UInt(64.W))
		val s_axib_wvalid	= Output(UInt(1.W))
		val s_axib_wready	= Input(UInt(1.W))
		val s_axib_bid		= Input(UInt(4.W))
		val s_axib_bresp	= Input(UInt(2.W))
		val s_axib_bvalid	= Input(UInt(1.W))
		val s_axib_bready	= Output(UInt(1.W))
		val s_axib_arid		= Output(UInt(4.W))
		val s_axib_araddr	= Output(UInt(64.W))
		val s_axib_aruser	= Output(UInt(12.W))
		val s_axib_arlen	= Output(UInt(8.W))
		val s_axib_arsize	= Output(UInt(3.W))
		val s_axib_arburst	= Output(UInt(2.W))
		val s_axib_arregion	= Output(UInt(4.W))
		val s_axib_arvalid	= Output(UInt(1.W))
		val s_axib_arready	= Input(UInt(1.W))
		val s_axib_rid		= Input(UInt(4.W))
		val s_axib_rdata	= Input(UInt(512.W))
		val s_axib_rresp	= Input(UInt(2.W))
		val s_axib_ruser	= Input(UInt(64.W))
		val s_axib_rlast	= Input(UInt(1.W))
		val s_axib_rvalid	= Input(UInt(1.W))
		val s_axib_rready	= Output(UInt(1.W))

        val axib_read_enable    = Input(UInt(1.W))
        val axib_read_addr      = Input(UInt(64.W))
        val axib_read_data      = Output(UInt(512.W))
        val axib_write_mask     = Input(UInt(64.W))
        val axib_write_addr     = Input(UInt(64.W))
        val axib_write_data     = Input(UInt(512.W))

        val ssd_cmd_op      = Input(UInt(QUEUE_NUM.W))
        val ssd_cmd_nlb     = Input(UInt((QUEUE_NUM*16).W))
        val ssd_cmd_lba     = Input(UInt((QUEUE_NUM*32).W))
        val ssd_cmd_offset  = Input(UInt((QUEUE_NUM*32).W))
        val ssd_cmd_valid   = Input(UInt(QUEUE_NUM.W))
        val ssd_cmd_ready   = Output(UInt(QUEUE_NUM.W))

        val pcie_hbm_write_transfer = Input(UInt(2.W))
    })
}