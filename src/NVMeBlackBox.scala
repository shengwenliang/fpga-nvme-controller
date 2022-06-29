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

        val dma_h2c_cmd_addr        = Output(UInt(64.W))    
        val dma_h2c_cmd_len         = Output(UInt(16.W))
        val dma_h2c_cmd_eop         = Output(UInt(1.W))
        val dma_h2c_cmd_sop         = Output(UInt(1.W))
        val dma_h2c_cmd_mrkr_req    = Output(UInt(1.W))        
        val dma_h2c_cmd_sdi         = Output(UInt(1.W))
        val dma_h2c_cmd_qid         = Output(UInt(11.W))
        val dma_h2c_cmd_error       = Output(UInt(1.W))    
        val dma_h2c_cmd_func        = Output(UInt(8.W))    
        val dma_h2c_cmd_cidx        = Output(UInt(16.W))    
        val dma_h2c_cmd_port_id     = Output(UInt(3.W))    
        val dma_h2c_cmd_no_dma      = Output(UInt(1.W))    
        val dma_h2c_cmd_valid       = Output(UInt(1.W))    
        val dma_h2c_cmd_ready       = Input(UInt(1.W))    

        val dma_h2c_data_data               = Input(UInt(512.W))
        val dma_h2c_data_tcrc               = Input(UInt(32.W))
        val dma_h2c_data_tuser_qid          = Input(UInt(11.W))
        val dma_h2c_data_tuser_port_id      = Input(UInt(3.W)) 
        val dma_h2c_data_tuser_err          = Input(UInt(1.W)) 
        val dma_h2c_data_tuser_mdata        = Input(UInt(32.W))        
        val dma_h2c_data_tuser_mty          = Input(UInt(6.W))    
        val dma_h2c_data_tuser_zero_byte    = Input(UInt(1.W))            
        val dma_h2c_data_valid              = Input(UInt(1.W))
        val dma_h2c_data_ready              = Output(UInt(1.W))
        val dma_h2c_data_last               = Input(UInt(1.W))

        val dma_c2h_cmd_addr        = Output(UInt(64.W))    
        val dma_c2h_cmd_qid         = Output(UInt(11.W))
        val dma_c2h_cmd_error       = Output(UInt(1.W))    
        val dma_c2h_cmd_func        = Output(UInt(8.W))    
        val dma_c2h_cmd_port_id     = Output(UInt(3.W))    
        val dma_c2h_cmd_pfch_tag    = Output(UInt(7.W))        
        val dma_c2h_cmd_valid       = Output(UInt(1.W))    
        val dma_c2h_cmd_ready       = Input(UInt(1.W))    
        val dma_c2h_cmd_len         = Output(UInt(16.W))

        val dma_c2h_data_data               = Output(UInt(512.W))
        val dma_c2h_data_tcrc               = Output(UInt(32.W))
        val dma_c2h_data_ctrl_marker        = Output(UInt(1.W))        
        val dma_c2h_data_ctrl_ecc           = Output(UInt(7.W))    
        val dma_c2h_data_ctrl_len           = Output(UInt(16.W))    
        val dma_c2h_data_ctrl_port_id       = Output(UInt(3.W))        
        val dma_c2h_data_ctrl_qid           = Output(UInt(11.W))    
        val dma_c2h_data_ctrl_has_cmpt      = Output(UInt(1.W))        
        val dma_c2h_data_valid              = Output(UInt(1.W))
        val dma_c2h_data_ready              = Input(UInt(1.W))
        val dma_c2h_data_last               = Output(UInt(1.W))
        val dma_c2h_data_mty                = Output(UInt(6.W))

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