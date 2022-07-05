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
        val ssdCmd      = Flipped(Vec(SSD_MAX_ID+1, Vec(QUEUE_MAX_ID+1, Decoupled(new NVMeCommand))))

        val regControl  = new NVMeControl
        val regStatus   = new NVMeStatus

        val bramReq     = Flipped(new NVMeBRAM)

        val s_axib      = new AXIB_SLAVE

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

    nvmeCore.io.axib_read_enable    := io.bramReq.readEnable
    nvmeCore.io.axib_read_addr      := io.bramReq.readAddr  
    io.bramReq.readData             := nvmeCore.io.axib_read_data
    nvmeCore.io.axib_write_mask     := io.bramReq.writeMask 
    nvmeCore.io.axib_write_addr     := io.bramReq.writeAddr 
    nvmeCore.io.axib_write_data     := io.bramReq.writeData 

    io.s_axib.qdma_init()

    nvmeCore.io.s_axib_awid			<> io.s_axib.aw.bits.id
	nvmeCore.io.s_axib_awaddr		<> io.s_axib.aw.bits.addr
	nvmeCore.io.s_axib_awlen		<> io.s_axib.aw.bits.len
	nvmeCore.io.s_axib_awsize		<> io.s_axib.aw.bits.size
	nvmeCore.io.s_axib_awuser		<> io.s_axib.aw.bits.user
	nvmeCore.io.s_axib_awburst		<> io.s_axib.aw.bits.burst
	nvmeCore.io.s_axib_awregion 	<> io.s_axib.aw.bits.region
	nvmeCore.io.s_axib_awvalid		<> io.s_axib.aw.valid
	nvmeCore.io.s_axib_awready		<> io.s_axib.aw.ready
	nvmeCore.io.s_axib_wdata		<> io.s_axib.w.bits.data
	nvmeCore.io.s_axib_wstrb		<> io.s_axib.w.bits.strb
	nvmeCore.io.s_axib_wlast		<> io.s_axib.w.bits.last
	nvmeCore.io.s_axib_wuser		<> io.s_axib.w.bits.user
	nvmeCore.io.s_axib_wvalid		<> io.s_axib.w.valid
	nvmeCore.io.s_axib_wready		<> io.s_axib.w.ready
	nvmeCore.io.s_axib_bid			<> io.s_axib.b.bits.id
	nvmeCore.io.s_axib_bresp		<> io.s_axib.b.bits.resp
	nvmeCore.io.s_axib_bvalid		<> io.s_axib.b.valid
	nvmeCore.io.s_axib_bready		<> io.s_axib.b.ready
	nvmeCore.io.s_axib_arid			<> io.s_axib.ar.bits.id
	nvmeCore.io.s_axib_araddr		<> io.s_axib.ar.bits.addr
	nvmeCore.io.s_axib_arlen		<> io.s_axib.ar.bits.len
	nvmeCore.io.s_axib_arsize		<> io.s_axib.ar.bits.size
	nvmeCore.io.s_axib_aruser		<> io.s_axib.ar.bits.user
	nvmeCore.io.s_axib_arburst		<> io.s_axib.ar.bits.burst
	nvmeCore.io.s_axib_arregion 	<> io.s_axib.ar.bits.region
	nvmeCore.io.s_axib_arvalid		<> io.s_axib.ar.valid
	nvmeCore.io.s_axib_arready		<> io.s_axib.ar.ready
	nvmeCore.io.s_axib_rid			<> io.s_axib.r.bits.id
	nvmeCore.io.s_axib_rdata		<> io.s_axib.r.bits.data
	nvmeCore.io.s_axib_ruser		<> io.s_axib.r.bits.user
	nvmeCore.io.s_axib_rresp		<> io.s_axib.r.bits.resp
	nvmeCore.io.s_axib_rlast		<> io.s_axib.r.bits.last
	nvmeCore.io.s_axib_rvalid		<> io.s_axib.r.valid
	nvmeCore.io.s_axib_rready		<> io.s_axib.r.ready
    
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