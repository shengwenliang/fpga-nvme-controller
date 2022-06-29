package nvme

import chisel3._
import chisel3.util._

import qdma._

class NVMe (
    DEBUG               : Boolean = true,
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

        val ssdCmd      = Flipped(Vec(SSD_MAX_ID+1, Vec(QUEUE_MAX_ID+1, Decoupled(new NVMeCommand))))

        val regControl  = new NVMeControl
        val regStatus   = new NVMeStatus

        val bramReq     = Flipped(new NVMeBRAM)

        val pcie_hbm_write_transfer = if (DEBUG) {Some(Input(UInt(2.W)))} else None
    })

    val QUEUE_NUM = (SSD_MAX_ID+1) * (QUEUE_MAX_ID+1)

    val nvmeCore = Module(new NVMeBlackBox(
        SSD_MAX_ID          = SSD_MAX_ID,
        BUFFER_DATA_SHIFT   = BUFFER_DATA_SHIFT,
        SSD_NUM_SHIFT       = SSD_NUM_SHIFT,
        QUEUE_DEPTH_SHIFT   = QUEUE_DEPTH_SHIFT,
        QUEUE_MAX_ID        = QUEUE_MAX_ID,
        QUEUE_COUNT_SHIFT   = QUEUE_COUNT_SHIFT,
        MAX_SQ_INTERVAL     = MAX_SQ_INTERVAL
    ))

    nvmeCore.io.clk_core  := clock
    nvmeCore.io.sys_reset := reset

    io.regStatus.p2pdma_h2c_data    := nvmeCore.io.status_p2pdma_h2c_data
    io.regStatus.p2pdma_h2c_done    := nvmeCore.io.status_p2pdma_h2c_done
    io.regStatus.p2pdma_c2h_done    := nvmeCore.io.status_p2pdma_c2h_done
    io.regStatus.nvme_init_done     := nvmeCore.io.status_nvme_init_done 
    io.regStatus.nvme_exec_done     := nvmeCore.io.status_nvme_exec_done 
    io.regStatus.stat_op_succ       := nvmeCore.io.status_stat_op_succ   
    io.regStatus.stat_op_fail       := nvmeCore.io.status_stat_op_fail   
    io.regStatus.stat_exec_time     := nvmeCore.io.status_stat_exec_time 
    io.regStatus.stat_io_ssd0       := nvmeCore.io.status_stat_io_ssd0   
    io.regStatus.stat_io_ssd1       := nvmeCore.io.status_stat_io_ssd1   
    io.regStatus.stat_io_ssd2       := nvmeCore.io.status_stat_io_ssd2   
    io.regStatus.stat_io_ssd3       := nvmeCore.io.status_stat_io_ssd3   
    io.regStatus.band_tr_rd         := nvmeCore.io.status_band_tr_rd     
    io.regStatus.band_tr_wr         := nvmeCore.io.status_band_tr_wr     

    nvmeCore.io.control_init_start      := io.regControl.init_start     
    nvmeCore.io.control_init_nsid       := io.regControl.init_nsid      
    nvmeCore.io.control_init_dma_addr   := io.regControl.init_dma_addr  
    nvmeCore.io.control_init_byp_addr   := io.regControl.init_byp_addr  
    nvmeCore.io.control_init_ssd_addr   := io.regControl.init_ssd_addr  
    nvmeCore.io.control_init_ssdid      := io.regControl.init_ssdid     
    nvmeCore.io.control_p2pdma_read     := io.regControl.p2pdma_read    
    nvmeCore.io.control_p2pdma_write    := io.regControl.p2pdma_write   
    nvmeCore.io.control_p2pdma_cmd_addr := io.regControl.p2pdma_cmd_addr
    nvmeCore.io.control_p2pdma_cmd_len  := io.regControl.p2pdma_cmd_len 
    nvmeCore.io.control_p2pdma_c2h_data := io.regControl.p2pdma_c2h_data
    nvmeCore.io.control_ssd_init        := io.regControl.ssd_init       
    nvmeCore.io.control_exec_start      := io.regControl.exec_start     
    nvmeCore.io.control_exec_time       := io.regControl.exec_time      
    nvmeCore.io.control_band_tr_en      := io.regControl.band_tr_en     
    nvmeCore.io.control_band_tr_read    := io.regControl.band_tr_read   

    io.h2cCmd.bits.addr     := nvmeCore.io.dma_h2c_cmd_addr        
    io.h2cCmd.bits.len      := nvmeCore.io.dma_h2c_cmd_len         
    io.h2cCmd.bits.eop      := nvmeCore.io.dma_h2c_cmd_eop         
    io.h2cCmd.bits.sop      := nvmeCore.io.dma_h2c_cmd_sop         
    io.h2cCmd.bits.mrkr_req := nvmeCore.io.dma_h2c_cmd_mrkr_req    
    io.h2cCmd.bits.sdi      := nvmeCore.io.dma_h2c_cmd_sdi         
    io.h2cCmd.bits.qid      := nvmeCore.io.dma_h2c_cmd_qid         
    io.h2cCmd.bits.error    := nvmeCore.io.dma_h2c_cmd_error       
    io.h2cCmd.bits.func     := nvmeCore.io.dma_h2c_cmd_func        
    io.h2cCmd.bits.cidx     := nvmeCore.io.dma_h2c_cmd_cidx        
    io.h2cCmd.bits.port_id  := nvmeCore.io.dma_h2c_cmd_port_id     
    io.h2cCmd.bits.no_dma   := nvmeCore.io.dma_h2c_cmd_no_dma      
    io.h2cCmd.valid         := nvmeCore.io.dma_h2c_cmd_valid       
    nvmeCore.io.dma_h2c_cmd_ready   := io.h2cCmd.ready

    nvmeCore.io.dma_h2c_data_data               := io.h2cData.bits.data           
    nvmeCore.io.dma_h2c_data_tcrc               := io.h2cData.bits.tcrc           
    nvmeCore.io.dma_h2c_data_tuser_qid          := io.h2cData.bits.tuser_qid      
    nvmeCore.io.dma_h2c_data_tuser_port_id      := io.h2cData.bits.tuser_port_id  
    nvmeCore.io.dma_h2c_data_tuser_err          := io.h2cData.bits.tuser_err      
    nvmeCore.io.dma_h2c_data_tuser_mdata        := io.h2cData.bits.tuser_mdata    
    nvmeCore.io.dma_h2c_data_tuser_mty          := io.h2cData.bits.tuser_mty      
    nvmeCore.io.dma_h2c_data_tuser_zero_byte    := io.h2cData.bits.tuser_zero_byte
    nvmeCore.io.dma_h2c_data_last               := io.h2cData.bits.last           
    nvmeCore.io.dma_h2c_data_valid              := io.h2cData.valid          
    io.h2cData.ready    := nvmeCore.io.dma_h2c_data_ready

    io.c2hCmd.bits.addr     := nvmeCore.io.dma_c2h_cmd_addr    
    io.c2hCmd.bits.qid      := nvmeCore.io.dma_c2h_cmd_qid     
    io.c2hCmd.bits.error    := nvmeCore.io.dma_c2h_cmd_error   
    io.c2hCmd.bits.func     := nvmeCore.io.dma_c2h_cmd_func    
    io.c2hCmd.bits.port_id  := nvmeCore.io.dma_c2h_cmd_port_id 
    io.c2hCmd.bits.pfch_tag := nvmeCore.io.dma_c2h_cmd_pfch_tag
    io.c2hCmd.bits.len      := nvmeCore.io.dma_c2h_cmd_len     
    io.c2hCmd.valid         := nvmeCore.io.dma_c2h_cmd_valid   
    nvmeCore.io.dma_c2h_cmd_ready   := io.c2hCmd.ready

    io.c2hData.bits.data            := nvmeCore.io.dma_c2h_data_data         
    io.c2hData.bits.tcrc            := nvmeCore.io.dma_c2h_data_tcrc         
    io.c2hData.bits.ctrl_marker     := nvmeCore.io.dma_c2h_data_ctrl_marker  
    io.c2hData.bits.ctrl_ecc        := nvmeCore.io.dma_c2h_data_ctrl_ecc     
    io.c2hData.bits.ctrl_len        := nvmeCore.io.dma_c2h_data_ctrl_len     
    io.c2hData.bits.ctrl_port_id    := nvmeCore.io.dma_c2h_data_ctrl_port_id 
    io.c2hData.bits.ctrl_qid        := nvmeCore.io.dma_c2h_data_ctrl_qid     
    io.c2hData.bits.ctrl_has_cmpt   := nvmeCore.io.dma_c2h_data_ctrl_has_cmpt
    io.c2hData.bits.last            := nvmeCore.io.dma_c2h_data_last         
    io.c2hData.bits.mty             := nvmeCore.io.dma_c2h_data_mty          
    io.c2hData.valid                := nvmeCore.io.dma_c2h_data_valid        
    nvmeCore.io.dma_c2h_data_ready  := io.c2hData.ready      

    nvmeCore.io.axib_read_enable    := io.bramReq.readEnable
    nvmeCore.io.axib_read_addr      := io.bramReq.readAddr  
    io.bramReq.readData             := nvmeCore.io.axib_read_data
    nvmeCore.io.axib_write_mask     := io.bramReq.writeMask 
    nvmeCore.io.axib_write_addr     := io.bramReq.writeAddr 
    nvmeCore.io.axib_write_data     := io.bramReq.writeData 

    if (DEBUG) {
        nvmeCore.io.pcie_hbm_write_transfer := io.pcie_hbm_write_transfer.get
    } else {
        nvmeCore.io.pcie_hbm_write_transfer := 0.U
    }

    // Handle NVMe command, where I need to combine each wire in bundle to a vector.
    
    val ssdCmdOpVec     = Wire(Vec(QUEUE_NUM, UInt(1.W)))
    val ssdCmdNlbVec    = Wire(Vec(QUEUE_NUM, UInt(16.W)))
    val ssdCmdLbaVec    = Wire(Vec(QUEUE_NUM, UInt(32.W)))
    val ssdCmdOffsetVec = Wire(Vec(QUEUE_NUM, UInt(32.W)))
    val ssdCmdValidVec  = Wire(Vec(QUEUE_NUM, UInt(1.W)))
    val ssdCmdReadyVec  = Wire(Vec(QUEUE_NUM, UInt(1.W)))

    var i, j = 0

    for (i <- 0 to SSD_MAX_ID) {
        for (j <- 0 to QUEUE_MAX_ID) {
            val idx = i*(QUEUE_MAX_ID+1) + j
            ssdCmdOpVec(idx)        := io.ssdCmd(i)(j).bits.op
            ssdCmdNlbVec(idx)       := io.ssdCmd(i)(j).bits.numLb
            ssdCmdLbaVec(idx)       := io.ssdCmd(i)(j).bits.ssdAddr
            ssdCmdOffsetVec(idx)    := io.ssdCmd(i)(j).bits.memAddr
            ssdCmdValidVec(idx)     := io.ssdCmd(i)(j).valid
            io.ssdCmd(i)(j).ready   := ssdCmdReadyVec(idx)
        }
    }

    nvmeCore.io.ssd_cmd_op      := ssdCmdOpVec.asTypeOf(UInt(QUEUE_NUM.W))
    nvmeCore.io.ssd_cmd_nlb     := ssdCmdNlbVec.asTypeOf(UInt((QUEUE_NUM*16).W))
    nvmeCore.io.ssd_cmd_lba     := ssdCmdLbaVec.asTypeOf(UInt((QUEUE_NUM*32).W))
    nvmeCore.io.ssd_cmd_offset  := ssdCmdOffsetVec.asTypeOf(UInt((QUEUE_NUM*32).W))
    nvmeCore.io.ssd_cmd_valid   := ssdCmdValidVec.asTypeOf(UInt(QUEUE_NUM.W))
    ssdCmdReadyVec              := nvmeCore.io.ssd_cmd_ready.asTypeOf(Vec(QUEUE_NUM, UInt(1.W)))
}