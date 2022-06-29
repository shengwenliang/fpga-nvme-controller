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

class NVMeControl extends Bundle {
    val init_start      = Input(UInt(1.W))
    val init_nsid       = Input(UInt(32.W))
    val init_dma_addr   = Input(UInt(64.W))
    val init_byp_addr   = Input(UInt(64.W))
    val init_ssd_addr   = Input(UInt(64.W))
    val init_ssdid      = Input(UInt(32.W))
    val p2pdma_read     = Input(UInt(1.W))
    val p2pdma_write    = Input(UInt(1.W))
    val p2pdma_cmd_addr = Input(UInt(64.W))
    val p2pdma_cmd_len  = Input(UInt(16.W))
    val p2pdma_c2h_data = Input(UInt(512.W))
    val ssd_init        = Input(UInt(1.W))
    val exec_start      = Input(UInt(1.W))
    val exec_time       = Input(UInt(32.W))
    val band_tr_en      = Input(UInt(1.W))
    val band_tr_read    = Input(UInt(1.W))
}

object Reg2NVMeControl {
    def apply(controlReg : Vec[UInt]) : NVMeControl = {
        val target = Wire(new NVMeControl)
        val source = Cat(
            controlReg(32)(0), 
            controlReg(33), 
            Cat(controlReg(35), controlReg(34)),
            Cat(controlReg(37), controlReg(36)),
            Cat(controlReg(39), controlReg(38)),
            controlReg(40), 
            controlReg(64)(0), 
            controlReg(65)(0), 
            Cat(controlReg(67), controlReg(66)),
            controlReg(68)(15, 0), 
            Cat(controlReg(84), controlReg(83), controlReg(82), controlReg(81), controlReg(80), controlReg(79), controlReg(78), controlReg(77), controlReg(76), controlReg(75), controlReg(74), controlReg(73), controlReg(72), controlReg(71), controlReg(70), controlReg(69)),
            controlReg(128)(0), 
            controlReg(160)(0), 
            controlReg(163), 
            controlReg(165)(0), 
            controlReg(166)(0), 
        )
        target := source.asTypeOf(new NVMeControl)
        target
    }
}

class NVMeStatus extends Bundle {
    val p2pdma_h2c_data = Output(UInt(512.W))
    val p2pdma_h2c_done = Output(UInt(1.W))
    val p2pdma_c2h_done = Output(UInt(1.W))
    val nvme_init_done  = Output(UInt(1.W))
    val nvme_exec_done  = Output(UInt(1.W))
    val stat_op_succ    = Output(UInt(32.W))
    val stat_op_fail    = Output(UInt(32.W))
    val stat_exec_time  = Output(UInt(64.W))
    val stat_io_ssd0    = Output(UInt(32.W))
    val stat_io_ssd1    = Output(UInt(32.W))
    val stat_io_ssd2    = Output(UInt(32.W))
    val stat_io_ssd3    = Output(UInt(32.W))
    val band_tr_rd      = Output(UInt(32.W))
    val band_tr_wr      = Output(UInt(32.W))
}

object NVMeStatus2Reg {
    def apply(source : NVMeStatus, statusReg : Vec[UInt])= {
        var i = 0;
        for (i <- 0 until 16) {
            statusReg(96+i) := source.p2pdma_h2c_data(i*32+31, i*32)
        }
        for (i <- 0 until 2) {
            statusReg(196+i) := source.stat_exec_time(i*32+31, i*32)
        }
        statusReg(128) := source.p2pdma_h2c_done
        statusReg(129) := source.p2pdma_c2h_done
        statusReg(160) := source.nvme_init_done
        statusReg(192) := source.nvme_exec_done
        statusReg(193) := source.stat_op_succ
        statusReg(194) := source.stat_op_fail
        statusReg(200) := source.stat_io_ssd0
        statusReg(201) := source.stat_io_ssd1
        statusReg(202) := source.stat_io_ssd2
        statusReg(203) := source.stat_io_ssd3
        statusReg(216) := source.band_tr_rd
        statusReg(217) := source.band_tr_wr
    }
}