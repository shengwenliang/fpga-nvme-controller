package nvme

import chisel3._
import chisel3.util._
import common._
import common.storage._
import common.axi._
import qdma._

class NVMeBandwidthBenchmarkTop extends RawModule{
	val qdma_pin		= IO(new QDMAPin(PCIE_WIDTH=8))
	val led 			= IO(Output(UInt(1.W)))

	led := 0.U

	// Global parameters
	val DATA_BUFFER_SHIFT	= 27 // Upper 128 MiB is used for data buffer.
	val SSD_NUM				= 1
	val QUEUE_NUM			= 2
	val QUEUE_DEPTH			= 64
	val QDMA_INTERFACE		= "SAXIB"

	val qdma = Module(new QDMA(
		VIVADO_VERSION	= "202101",
		PCIE_WIDTH		= 8,
		SLAVE_BRIDGE	= (QDMA_INTERFACE == "SAXIB"),
		TLB_TYPE		= new BypassTLB,	// TLB is not used here.
		BRIDGE_BAR_SCALE= "Megabytes",
		BRIDGE_BAR_SIZE = 256
	))
	qdma.getTCL()

	val user_clk 	= qdma.io.pcie_clk
	val user_rstn 	= qdma.io.pcie_arstn

	ToZero(qdma.io.reg_status)
	qdma.io.pin <> qdma_pin

	// TODO: Notify Carl Zhang move AXI clock convertor into QDMA module :/
	qdma.io.user_clk	:= user_clk
	qdma.io.user_arstn	:= user_rstn
	qdma.io.soft_rstn	:= 1.U

	Collector.connect_to_status_reg(qdma.io.reg_status, 400)

	withClockAndReset(qdma.io.user_clk,!qdma.io.user_arstn) {
    	val nvmeCore = Module(new NVMeController(
			SSD_NUM 		= SSD_NUM, 
			QUEUE_NUM 		= QUEUE_NUM, 
			QUEUE_DEPTH 	= QUEUE_DEPTH,
			QDMA_INTERFACE 	= QDMA_INTERFACE
		))

		if (QDMA_INTERFACE == "DMA") {
			nvmeCore.io.c2hCmd.get  <> qdma.io.c2h_cmd
			nvmeCore.io.c2hData.get <> qdma.io.c2h_data
		} else if (QDMA_INTERFACE == "SAXIB") {
			qdma.io.s_axib.get	<> AXIRegSlice(3)(nvmeCore.io.sAxib.get)
			qdma.io.c2h_cmd		<> DontCare
			qdma.io.c2h_data	<> DontCare
		}

		val controlReg  = qdma.io.reg_control
		val statusReg   = qdma.io.reg_status
		
		statusReg(65) := nvmeCore.io.status.params.ssdNum
		statusReg(67) := nvmeCore.io.status.params.queueLowBit
		statusReg(64) := nvmeCore.io.status.params.ssdLowBit
		statusReg(66) := nvmeCore.io.status.params.queueDepth
		statusReg(68) := nvmeCore.io.status.params.queueNum 
		statusReg(69) := nvmeCore.io.status.params.ramTypeBit

		// In such scenario, whole BAR space of QDMA is often used by different modules.
		// Thus, we must split AXI Bridge into different ways.
		// The 2nd input of AXIRouter is to automatically decide data widths, it will NOT 
		// be connected to the router.

		// In this example design, AXIB space is splited into 3 ways: 
		// 0 - Lower space is used to store SQ / CQ RAMs
		// 1 - Middle space is used to store PRP lists
		// 2 - Upper space is used for data buffer.

		val axibRt = AXIRouter(3, qdma.io.axib)	
		axibRt.io.in	<> AXIRegSlice(2)(qdma.io.axib)
		axibRt.io.wrIdx	:= Mux(
			axibRt.io.in.aw.bits.addr(63, nvmeCore.RAM_TYPE_BIT+1) === 0.U,
			0.U, Mux(axibRt.io.in.aw.bits.addr(63, DATA_BUFFER_SHIFT) === 0.U, 1.U, 2.U)
		)
		axibRt.io.rdIdx	:= Mux(
			axibRt.io.in.ar.bits.addr(63, nvmeCore.RAM_TYPE_BIT+1) === 0.U,
			0.U, Mux(axibRt.io.in.ar.bits.addr(63, DATA_BUFFER_SHIFT) === 0.U, 1.U, 2.U)
		)

		nvmeCore.io.ramIO	<> AXI2NVMeRam(AXIRegSlice(2)(axibRt.io.out(0)))

		qdma.io.h2c_cmd			<> DontCare
		qdma.io.h2c_data		<> DontCare

		nvmeCore.io.control.enable						:= controlReg(160)
		nvmeCore.io.control.ssdSetup.valid				:= controlReg(32) & ~RegNext(controlReg(32))
		nvmeCore.io.control.ssdSetup.bits.ssdId			:= controlReg(40)
		nvmeCore.io.control.ssdSetup.bits.ssdBarAddr	:= Cat(controlReg(39), controlReg(38))

		// Benchmark module

		val benchmark = Module(new NVMeBandwidthBenchmark(
			SSD_NUM				= SSD_NUM,
			QUEUE_NUM			= QUEUE_NUM,
			DATA_BUFFER_SHIFT	= DATA_BUFFER_SHIFT
		))

		benchmark.io.ssdCmd		<> nvmeCore.io.ssdCmd
		benchmark.io.prpInput	<> AXI2NVMeRam(AXIRegSlice(2)(axibRt.io.out(1)))

		benchmark.io.ctlRunning		:= controlReg(160)
		benchmark.io.ctlEnd			:= statusReg(192) & ~RegNext(statusReg(192))
		benchmark.io.ctlFpgaBar		:= Cat(controlReg(37), controlReg(36))
		benchmark.io.ctlTimeTarget	:= Cat(controlReg(163), 0.U(6.W))
		benchmark.io.ctlNumNlb		:= controlReg(162)(15, 0)
		benchmark.io.ctlMaxParallel	:= controlReg(170)
		benchmark.io.ctlModeWrite	:= controlReg(161)(0)
		benchmark.io.ctlModeRandom	:= controlReg(161)(1)
		benchmark.io.ctlModeMixed	:= 0.U
		benchmark.io.ctlRdBlkSize	:= controlReg(167)
		benchmark.io.ctlWrBlkSize	:= controlReg(168)
		benchmark.io.ctlRdBlkAhead	:= controlReg(169)

		statusReg(192)	:= !(nvmeCore.io.status.running || nvmeCore.io.control.enable)
		statusReg(193) 	:= nvmeCore.io.status.stat.successfulOp
		statusReg(194) 	:= nvmeCore.io.status.stat.failedOp
		statusReg(196)	:= nvmeCore.io.status.stat.executeTime(31, 0)
		statusReg(197)	:= nvmeCore.io.status.stat.executeTime(63, 32)
		for (ssdId <- 0 until SSD_NUM) {
			statusReg(200+ssdId)	:= benchmark.io.statSsdIo(ssdId)
		}
		statusReg(218)	:= nvmeCore.io.status.stat.totalLatency(31, 0)
		statusReg(219)	:= nvmeCore.io.status.stat.totalLatency(63, 32)

		// Bandwidth probe
		val dataBufferIo = AXI2NVMeRam(AXIRegSlice(2)(axibRt.io.out(2)))
		dataBufferIo.readData	:= 0.U

		val readProbe = Module(new BandwidthProbe)
		readProbe.io.enable 		:= ~statusReg(192)(0) && controlReg(165)(0)
		readProbe.io.fire			:= dataBufferIo.readEnable
		readProbe.io.count.ready	:= (controlReg(166)(0) === 1.U && RegNext(controlReg(166)(0)) =/= 1.U)
		statusReg(216)	:= Mux(readProbe.io.count.valid, readProbe.io.count.bits, -1.S(32.W).asUInt)
		
		val writeProbe = Module(new BandwidthProbe)
		writeProbe.io.enable 		:= ~statusReg(192)(0) && controlReg(165)(0)
		writeProbe.io.fire			:= (dataBufferIo.writeMask =/= 0.U)
		writeProbe.io.count.ready	:= (controlReg(166)(0) === 1.U && RegNext(controlReg(166)(0)) =/= 1.U)
		statusReg(217)	:= Mux(writeProbe.io.count.valid, writeProbe.io.count.bits, -1.S(32.W).asUInt)

		// AXIB Debug
		val aw_cnt  = RegInit(UInt(32.W), 0.U)
		val w_cnt	= RegInit(UInt(32.W), 0.U)

		when (qdma.io.axib.aw.fire) {
			aw_cnt := aw_cnt + qdma.io.axib.aw.bits.len + 1.U
		}

		when (qdma.io.axib.w.fire) {
			w_cnt := w_cnt + 1.U
		}

		val diff_cnt = aw_cnt - w_cnt
		val diff_time = RegInit(UInt(32.W), 0.U)
		when (diff_cnt === 0.U) {
			diff_time := 0.U
		}.otherwise {
			diff_time := diff_time + 1.U
		}
	}
}

class BypassTLB extends Module with BaseTLB {
	io.h2c_in	<> io.h2c_out
	io.c2h_in	<> io.c2h_out
	io.tlb_miss_count	:= 0.U
	io.wr_tlb.ready		:= 1.U
}