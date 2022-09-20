package nvme

import chisel3._
import chisel3.util._

object NVMeCommandSet {
    def nvmWrite (id : UInt, prp1 : UInt, prp2 : UInt, slba : UInt, nlb : UInt) = {

        // Make sure the lengths of input signals are correct.
        
        val idSig   = Wire(UInt(16.W))
        val prp1Sig = Wire(UInt(64.W))
        val prp2Sig = Wire(UInt(64.W))
        val slbaSig = Wire(UInt(64.W))
        val nlbSig  = Wire(UInt(16.W))

        idSig   := id
        prp1Sig := prp1
        prp2Sig := prp2
        slbaSig := slba
        nlbSig  := nlb

        // Generate the NVMe-format command

        Cat(
            // DW 14-15: 
            0.U(64.W),  // End to end protection, not used
            // DW 13: 
            0.U(24.W),  // Rsvd
            0.U(8.W),   // Dataset, not used generally
            // DW 12:
            1.U(1.W),   // Limited retry
            0.U(1.W),   // Forced unit access
            0.U(4.W),   // Protection information
            0.U(10.W),  // Rsvd
            nlbSig,     // Number of logical blocks, 0's based
            // DW 11-10:
            slbaSig,    // Starting LB address
            // DW 9-8:
            prp2Sig,       // PRP 2
            // DW 7-6
            prp1Sig,       // PRP 1
            // DW 5-4:
            0.U(64.W),  // Metadata ptr, not used here
            // DW 3-2:
            0.U(64.W),  // Rsvd
            // DW 1:
            1.U(32.W),  // Namespace, typically 1 for most cases
            // DW 0:
            idSig,         // Command ID
            0.U(2.W),   // Use PRP
            0.U(4.W),   // Rsvd
            0.U(2.W),   // Fuse command
            0x01.U(8.W) // Opcode
        )
    }

    def nvmRead (id : UInt, prp1 : UInt, prp2 : UInt, slba : UInt, nlb : UInt) = {

        // Make sure the lengths of input signals are correct.

        val idSig   = Wire(UInt(16.W))
        val prp1Sig = Wire(UInt(64.W))
        val prp2Sig = Wire(UInt(64.W))
        val slbaSig = Wire(UInt(64.W))
        val nlbSig  = Wire(UInt(16.W))

        idSig   := id
        prp1Sig := prp1
        prp2Sig := prp2
        slbaSig := slba
        nlbSig  := nlb

        // Generate the NVMe-format command

        Cat(
            // DW 14-15: 
            0.U(64.W),  // End to end protection, not used
            // DW 13: 
            0.U(24.W),  // Rsvd
            0.U(8.W),   // Dataset, not used generally
            // DW 12:
            1.U(1.W),   // Limited retry
            0.U(1.W),   // Forced unit access
            0.U(4.W),   // Protection information
            0.U(10.W),  // Rsvd
            nlbSig,     // Number of logical blocks, 0's based
            // DW 11-10:
            slbaSig,    // Starting LB address
            // DW 9-8:
            prp2Sig,       // PRP 2
            // DW 7-6
            prp1Sig,       // PRP 1
            // DW 5-4:
            0.U(64.W),  // Metadata ptr, not used here
            // DW 3-2:
            0.U(64.W),  // Rsvd
            // DW 1:
            1.U(32.W),  // Namespace, typically 1 for most cases
            // DW 0:
            idSig,         // Command ID
            0.U(2.W),   // Use PRP
            0.U(4.W),   // Rsvd
            0.U(2.W),   // Fuse command
            0x02.U(8.W) // Opcode
        )
    }
}