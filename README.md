# Chisel NVMe Host Controller
NVMe host controller written in Chisel.

## Table of contents
- [Chisel NVMe Host Controller](#chisel-nvme-host-controller)
  - [Table of contents](#table-of-contents)
  - [How to Add this Module in Your Chisel Project:](#how-to-add-this-module-in-your-chisel-project)
  - [NVMeCore Module](#nvmecore-module)
    - [Parameter Description](#parameter-description)
    - [Port Description](#port-description)
      - [NVMeCoreControl](#nvmecorecontrol)
      - [NVMeCoreStatus](#nvmecorestatus)
  - [Other Modules and APIs](#other-modules-and-apis)
    - [AXI2NVMeRam](#axi2nvmeram)
    - [NVMe Command Builders](#nvme-command-builders)
    - [BandwidthProbe](#bandwidthprobe)
  - [Example Design](#example-design)

## How to Add this Module in Your Chisel Project:
[QDMA](https://github.com/carlzhang4/qdma) is this module's dependency. Before using this module, make sure QDMA module are installed.  
To install this module, use the command below:  
```bash
$ git submodule add git@github.com:JewdrunAleph/fpga-nvme-controller nvme
$ git clone git@github.com:JewdrunAleph/fpga-nvme-controller nvme
```

## NVMeCore Module
`NVMeCore` can offload I/O queue management of SSDs from CPU to FPGA. It takes NVMe commands as inputs, maintains I/O queues and rings doorbell signal via QDMA.

### Parameter Description
|Parameter          |Type   |Range          |Description    |
|:---               |:---   |:---           |:---           |
|SSD_NUM            |Int    |Larger than 0. |Number of SSDs used.   |
|QUEUE_NUM          |Int    |Larger than 0. |Number of I/O queues FPGA taken care of for each SSD.|
|QUEUE_DEPTH        |Int    |Larger than 4, exponential of 2.   |Queue depth of each queue I/O queue. Depth of each queue should be equal.|
|MAX_SQ_INTERVAL    |Int    |Larger than 0. |Time window for this module to wait new command in cycles. <br> To reduce doorbell traffic, when a new command is inserted, queue management module will not immediately ring the doorbell, instead, it will wait for a period to see whether new command has come.|
|QDMA_INTERFACE     |String |"DMA" or "SAXIB"   |Interface of QDMA used to ring doorbell. <br>You can choose either DMA C2H engine, or AXI Slave Bridge. |


### Port Description
|Port       |Type                       |Direction  |Description    |
|:---       |:---                       |:---       |:---           |
|ssdCmd     |Vec[Vec[DecoupledIO[UInt]]]|Input      |Command of each queue of each SSD. Each command should follow NVMe format.|
|control    |NVMeCoreControl            |Input      |Control signals for this module. Refer to [here](#NVMeCoreControl)|
|status     |NVMeCoreStatus             |Output     |Status signals for this module. Refer to [here](#NVMeCoreStatus)|
|ramIO      |NVMeRamIO                  |           |SQ/CQ RAM I/O request from the host. This port can be converted from an AXI slave interface, see [here](#AXI2NVMeRam).|
|sAxib      |Option[AXIB_SLAVE]         |Output     |Used when `QDMA_INTERFACE == "SAXIB"`. Connect to QDMA's AXI Slave Bridge|
|c2hCmd     |Option[DecoupledIO[C2H_CMD]]|Output    |Used when `QDMA_INTERFACE == "DMA"`. Connect to QDMA's C2H command port.|
|c2hData    |Option[DecoupledIO[C2H_DATA]]|Output   |Used when `QDMA_INTERFACE == "DMA"`. Connect to QDMA's C2H data port.|

#### NVMeCoreControl
NVMe control signals are listed here.
**enable**  
Only when this signal is high will this module work and accept new commands. When this signal is low, it still processes existing commands, but won't accept new commands anymore. Designed for benchmarking.
**ssdSetup**  
Initialize an SSD with data required by this module. It has two signals:  
- `ssdId`: Index of SSD to be initialized.  
- `ssdBarAddr`: **Physical** address of BAR 0 of this SSD. It should be got from the host.

#### NVMeCoreStatus
NVMe status signals are listed here. This interface includes signals either needed by host or helpful for benchmarking.  
- `running`: Whether this module is processing or accepting commands. When `enable` signal is set to low and this module finishes processing all existing commands, this signal will turn to low.  
- `params`: Parameters required by the host. With these parameters, host can create I/O queue for all SSDs and assign correct address for these queues.  
- `stat`: Statistical informations **since the module is enabled**, include:
  - `executeTime`: Total execution time.
  - `successfulOp`: Number of commands SSD processed successfully.
  - `failedOp`: Number of commands SSD failed to process by SSDs.
  - `totalLatency`: Total latency for all commands processed **in cycles**. To get average latency of each SSD, devide this over number of commands processed.

## Other Modules and APIs

### AXI2NVMeRam
For simplicity, NVMe core module takes `NVMeRamIO` as input, which is similar to simple dual port BRAM I/O signals. However, in real applications requests are from AXI interface. Therefore, this repo provides an `AXI2NVMeRam` module for interface conversion.

### NVMe Command Builders
`NVMeCommandSet` object provides a set of functions which helps to fill in NVMe-formatted commands with some simple and basic items. Check Util.scala for more details.

### BandwidthProbe
`BandwidthProbe` helps to record actual data transfer bandwidth of certain interface.

## Example Design
This repo provides an example benchmark design includes hardware design and corresponding software. Example design is tested on an Alveo U50 Card. For U280 board, please use your own xdc file.  
To test this design:  
1. Install [QDMA driver and LibQDMA](https://github.com/carlzhang4/qdma_improve).
2. Generate bitstream file, and program to FPGA.
3. Use Makefile to generate executable.
4. Reboot your computer and run.