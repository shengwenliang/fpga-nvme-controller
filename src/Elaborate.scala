package nvme
import chisel3._
import chisel3.util._
import common._
import common.storage._
import qdma._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import firrtl.options.TargetDirAnnotation

object elaborate extends App {
	println("Generating a %s class".format(args(0)))
	val stage	= new chisel3.stage.ChiselStage
	val arr		= Array("-X", "sverilog", "--full-stacktrace")
	val dir 	= TargetDirAnnotation("Verilog")

	args(0) match{
		case "NVMeBenchmarkTop" => stage.execute(arr,Seq(ChiselGeneratorAnnotation(() => new NVMeBenchmarkTop()),dir))
		case "TestAXIRouter" => stage.execute(arr,Seq(ChiselGeneratorAnnotation(() => new TestAXIRouter()),dir))
		case "BandwidthProbe" => stage.execute(arr,Seq(ChiselGeneratorAnnotation(() => new BandwidthProbe(100, 4096)),dir))
		case _ => println("Module match failed!")
	}
}