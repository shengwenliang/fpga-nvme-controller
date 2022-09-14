/* NVMeBenchmark.cpp
 * A simple NVMe benchmark program.
 * Used with NVMeBenchmarkTop module.
 */
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <string>
#include <time.h>
#include <sys/ioctl.h>
#include <QDMAController.h>
#include <fstream>
#include <iomanip>

using namespace std;

#define SSD_ADMIN_SQ_PHYS_BASE(ssd_id) ((queue_phys_base)+0x2000*(ssd_id))
#define SSD_ADMIN_CQ_PHYS_BASE(ssd_id) ((queue_phys_base)+0x2000*(ssd_id)+0x1000)
#define SSD_ADMIN_SQ_VIRT_BASE(ssd_id) ((huge_virt_base)+0x2000*(ssd_id))
#define SSD_ADMIN_CQ_VIRT_BASE(ssd_id) ((huge_virt_base)+0x2000*(ssd_id)+0x1000)

// Change this as you like. Zero's based.
#define ADMIN_QUEUE_DEPTH 0x1f

// Functions for the CPU to create admin and I/O queues.
uint16_t command_id[32];
uint32_t admin_sq_tl[32], admin_cq_hd[32];
uint64_t ssd_virt_base[32];
uint64_t queue_phys_base, smart_phys_base;
uint64_t huge_virt_base;

void insert_admin_sq(int ssd_id, uint32_t command[])
{
    // Calculate the starting address of command.
    uint32_t *command_base = (uint32_t *)(SSD_ADMIN_SQ_VIRT_BASE(ssd_id) + (64 * admin_sq_tl[ssd_id]));

    // Fill in the command.
    for (int i=0; i<16; i++)
    {
        command_base[i] = command[i];
    }

    // Ring the doorbell.
    command_id[ssd_id]++;
    admin_sq_tl[ssd_id] = (admin_sq_tl[ssd_id] + 1) & ADMIN_QUEUE_DEPTH;
    uint32_t *nvme_sq0tdbl_pt = (uint32_t *)(ssd_virt_base[ssd_id] + 0x1000);
    *nvme_sq0tdbl_pt = admin_sq_tl[ssd_id];
    return;
}

int wait_for_next_cqe(int ssd_id)
{
    // Calculate the starting address of command.
    uint32_t *command_base = (uint32_t *)(SSD_ADMIN_CQ_VIRT_BASE(ssd_id) + (16 * (admin_cq_hd[ssd_id] & ADMIN_QUEUE_DEPTH)));

    int unexpected_phase = ((admin_cq_hd[ssd_id] >> 7) & 0x1);
    // fprintf(stdout, "command base: %08lx, unexpected phase: %x, sq tail: %x\n", (uint64_t) command_base, unexpected_phase, admin_sq_tl[0]);
    
    int current_phase = unexpected_phase;
    while (current_phase == unexpected_phase)
    {
        current_phase = command_base[3];
        current_phase = ((current_phase >> 16) & 0x1);
    }
    int status = command_base[3];
    status = (status >> 17);

    // Ring the doorbell.
    admin_cq_hd[ssd_id] = (admin_cq_hd[ssd_id] + 1) & ((ADMIN_QUEUE_DEPTH << 2) + 1);
    uint32_t *nvme_cq0hdbl_pt = (uint32_t *)(ssd_virt_base[ssd_id] + 0x1004);
    *nvme_cq0hdbl_pt = admin_cq_hd[ssd_id];
    // fprintf(stdout, "sq tail: %02x, cq head: %02x.\n", admin_sq_tl[0], admin_cq_hd[0]);

    return status;
}

int nvme_set_num_of_qp(int ssd_id, uint16_t queue_count)
{
    uint16_t queue_count_zerobased = (queue_count - 1);
    uint32_t command[16];
    // Now fill in each dw of command.
    // DW 0: bit 31-16 cmd_id, bit 15-10 rsvd, bit 9-8 fuse, bit 7-0 opcode.
    command[0] = (command_id[ssd_id] << 16) + (0x09 << 0);
    // DW 1: bit 31-0 namespace, all 1's in this case.
    command[1] = 0xffffffff;
    // DW 2-9 rsvd.
    for (int i=2; i<=9; i++)
    {
        command[i] = 0;
    }
    // DW 10: bit 31 save, bit 30-8 rsvd, bit 7-0 feature ID.
    command[10] = (0x07 << 0);
    // DW 11: bit 31-16 number of CQ zerobased, bit 15-0 number of SQ zerobased.
    command[11] = (queue_count_zerobased << 16) + (queue_count_zerobased << 0);
    // DW 12-15 rsvd.
    for (int i=12; i<=15; i++)
    {
        command[i] = 0;
    }
    // for (int i=0; i<16; i++)
    // {
    //     fprintf(stdout, "DW %2d: %08x\n", i, command[i]);
    // }
    insert_admin_sq(ssd_id, command);
    return wait_for_next_cqe(ssd_id);
}

int nvme_create_cq(int ssd_id, uint16_t cq_id, uint16_t cq_depth, uint64_t cq_addr)
{
    uint16_t cq_depth_zerobased = cq_depth - 1;
    uint32_t command[16];
    // Now fill in each dw of command.
    // DW 0: bit 31-16 cmd_id, bit 15-10 rsvd, bit 9-8 fuse, bit 7-0 opcode.
    command[0] = (command_id[ssd_id] << 16) + (0x05 << 0);
    // DW 1-5 rsvd.
    for (int i=1; i<=5; i++)
    {
        command[i] = 0;
    }
    // DW 6-7: bit 63-0 PRP1
    command[6] = (uint32_t)(cq_addr & 0xffffffff);
    command[7] = (uint32_t)(cq_addr >> 32);
    // DW 8-9 rsvd.
    command[8] = 0;
    command[9] = 0;
    // DW 10: bit 31-16 queue depth, bit 15-0 queue id
    command[10] = (cq_depth_zerobased << 16) + (cq_id << 0);
    // DW 11: Bit 31-16 interrupt vector, bit 15-2 esvd, bit 1 int enable, bit 0 phys cont
    command[11] = 1;
    // DW 12-15 rsvd
    for (int i=12; i<=15; i++)
    {
        command[i] = 0;
    }
    // for (int i=0; i<16; i++)
    // {
    //     fprintf(stdout, "DW %2d: %08x\n", i, command[i]);
    // }
    insert_admin_sq(ssd_id, command);
    return wait_for_next_cqe(ssd_id);
}

int nvme_create_sq(int ssd_id, uint16_t sq_id, uint16_t cq_id, uint16_t sq_depth, uint64_t sq_addr)
{
    uint16_t sq_depth_zerobased = sq_depth - 1;
    uint32_t command[16];
    // Now fill in each dw of command.
    // DW 0: bit 31-16 cmd_id, bit 15-10 rsvd, bit 9-8 fuse, bit 7-0 opcode.
    command[0] = (command_id[ssd_id] << 16) + (0x01 << 0);
    // DW 1-5 rsvd.
    for (int i=1; i<=5; i++)
    {
        command[i] = 0;
    }
    // DW 6-7: bit 63-0 PRP1
    command[6] = (uint32_t)(sq_addr & 0xffffffff);
    command[7] = (uint32_t)(sq_addr >> 32);
    // DW 8-9 rsvd.
    command[8] = 0;
    command[9] = 0;
    // DW 10: bit 31-16 queue depth, bit 15-0 queue id
    command[10] = (sq_depth_zerobased << 16) + (sq_id << 0);
    // DW 11: Bit 31-16 cq_id, bit 15-2 esvd, bit 1 int enable, bit 0 phys cont
    command[11] = (cq_id << 16) + (0x1 << 0);
    // DW 12-15 rsvd
    for (int i=12; i<=15; i++)
    {
        command[i] = 0;
    }    
    insert_admin_sq(ssd_id, command);
    return wait_for_next_cqe(ssd_id);
}

int get_smart_info(int ssd_id)
{
    uint32_t command[16];
    // Now fill in each dw of command.
    // DW 0: bit 31-16 cmd_id, bit 15-10 rsvd, bit 9-8 fuse, bit 7-0 opcode.
    command[0] = (command_id[ssd_id] << 16) + (0x02 << 0);
    // DW 1: bit 31-0 namespace
    command[1] = 0xffffffff;
    // DW 2-5 rsvd.
    for (int i=2; i<=5; i++)
    {
        command[i] = 0;
    }
    // DW 6-7: bit 63-0 PRP1
    command[6] = (uint32_t)(smart_phys_base & 0xffffffff);
    command[7] = (uint32_t)(smart_phys_base >> 32);
    // DW 8-9: bit 63-0 PRP2, rsvd in this case.
    command[8] = 0;
    command[9] = 0;
    // DW 10: bit 31-16 num of dwords lower, bit 15 retain async event, 
    // bit 14-8 rsvd, dw 7-0 log id
    command[10] = (0x400 << 16) + (0x0 << 15) + 0x02;
    // DW 11: bit 31-16 rsvd, bit 15-0 num of dwords upper.
    command[11] = 0x0;
    // DW 12-13: bit 63-0 log page offset. 0 in this case.
    command[12] = 0x0;
    command[13] = 0x0;
    // DW 14: bit 31-0 UUID. 0 in this case.
    command[14] = 0x0;
    // DW 15 rsvd.
    command[15] = 0x0;
    insert_admin_sq(ssd_id, command);
    return wait_for_next_cqe(ssd_id);
}

int get_error_log(int ssd_id)
{
    uint32_t command[16];
    // Now fill in each dw of command.
    // DW 0: bit 31-16 cmd_id, bit 15-10 rsvd, bit 9-8 fuse, bit 7-0 opcode.
    command[0] = (command_id[ssd_id] << 16) + (0x02 << 0);
    // DW 1: bit 31-0 namespace
    command[1] = 0xffffffff;
    // DW 2-5 rsvd.
    for (int i=2; i<=5; i++)
    {
        command[i] = 0;
    }
    // DW 6-7: bit 63-0 PRP1
    command[6] = (uint32_t)(smart_phys_base & 0xffffffff);
    command[7] = (uint32_t)(smart_phys_base >> 32);
    // DW 8-9: bit 63-0 PRP2, rsvd in this case.
    command[8] = 0;
    command[9] = 0;
    // DW 10: bit 31-16 num of dwords lower, bit 15 retain async event, 
    // bit 14-8 rsvd, dw 7-0 log id
    command[10] = (0x400 << 16) + (0x0 << 15) + 0x01;
    // DW 11: bit 31-16 rsvd, bit 15-0 num of dwords upper.
    command[11] = 0x0;
    // DW 12-13: bit 63-0 log page offset. 0 in this case.
    command[12] = 0x0;
    command[13] = 0x0;
    // DW 14: bit 31-0 UUID. 0 in this case.
    command[14] = 0x0;
    // DW 15 rsvd.
    command[15] = 0x0;
    insert_admin_sq(ssd_id, command);
    return wait_for_next_cqe(ssd_id);
}

int get_temperature_info(int ssd_id)
{
    uint32_t command[16];
    // Now fill in each dw of command.
    // DW 0: bit 31-16 cmd_id, bit 15-10 rsvd, bit 9-8 fuse, bit 7-0 opcode.
    command[0] = (command_id[ssd_id] << 16) + (0x02 << 0);
    // DW 1: bit 31-0 namespace
    command[1] = 0xffffffff;
    // DW 2-5 rsvd.
    for (int i=2; i<=5; i++)
    {
        command[i] = 0;
    }
    // DW 6-7: bit 63-0 PRP1
    command[6] = (uint32_t)(smart_phys_base & 0xffffffff);
    command[7] = (uint32_t)(smart_phys_base >> 32);
    // DW 8-9: bit 63-0 PRP2, rsvd in this case.
    command[8] = 0;
    command[9] = 0;
    // DW 10: bit 31-16 num of dwords lower, bit 15 retain async event, 
    // bit 14-8 rsvd, dw 7-0 log id
    command[10] = (0x1 << 16) + (0x0 << 15) + 0x02;
    // DW 11: bit 31-16 rsvd, bit 15-0 num of dwords upper.
    command[11] = 0x0;
    // DW 12-13: bit 63-0 log page offset. 200 (0xc8) in this case.
    command[12] = 0xc8;
    command[13] = 0x0;
    // DW 14: bit 31-0 UUID. 0 in this case.
    command[14] = 0x0;
    // DW 15 rsvd.
    command[15] = 0x0;
    insert_admin_sq(ssd_id, command);
    return wait_for_next_cqe(ssd_id);
}

int main(int argc, char *argv[])
{

    // Set QDMA regs
    // Initialize NVMe device.
    init(0xb1, 256*1024*1024);     // FPGA device ID is 0000:1c:00.0
    // Make queue 0 active
    uint32_t pfch_tag;
    writeConfig(0x1408/4, 0);
    if (readConfig(0x1408/4) != 0) {
        fprintf(stderr, "ERROR: Cannot read FPGA BARs.");
        exit(1);
    }
    pfch_tag = readConfig(0x140c/4);
    writeReg(210, pfch_tag);
    fprintf(stdout, "Prefetch tag: %d\n", pfch_tag);

    // Physical addresses of several BARs.
    uint64_t nvme_base[4], bypass_base;
    
    FILE *fp;

    // Open FPGA card. Assume BAR 4 of 0000:37:00.0

    fp = fopen("/sys/bus/pci/devices/0000:b1:00.0/resource", "rb");
    if (fp == NULL)
    {
        fprintf(stderr, "ERROR: Cannot open fpga device.\n");
        exit(1);
    }
    fseek(fp, 228, SEEK_SET); // 57 * BAR

    fscanf(fp, "0x%lx", &bypass_base);
    fclose(fp);

    if (bypass_base == 0)
    {
        fprintf(stderr, "ERROR: Invalid PCI address for FPGA card.\n");
        exit(1);
    }
    else
    {
        fprintf(stdout, "BAR 2 of FPGA device is %lx.\n", bypass_base);
    }

    writeReg(32, 0);
    // Read FPGA configures
    uint32_t ssd_low_bit, ssd_num, queue_low_bit, queue_depth, queue_num, ram_type_bit;
    ssd_low_bit     = readReg(576);
    ssd_num         = readReg(577);
    queue_depth     = readReg(578);
    queue_low_bit   = readReg(579);
    queue_num       = readReg(580);
    ram_type_bit    = readReg(581);
    fprintf(stdout,
        "SSD_LOW_BIT:   %u\nSSD_COUNT:     %u\n"
        "QUEUE_DEPTH:   %u\nQUEUE_LOW_BIT: %u\n" 
        "QUEUE_NUM:     %u\nRAM_TYPE_BIT:  %u\n",
        ssd_low_bit, ssd_num,
        queue_depth, queue_low_bit,
        queue_num, ram_type_bit
    );
    if (ssd_low_bit == 0xffffffff)
    {
        fprintf(stderr, "ERROR: Invalid FPGA config info. \n");
        exit(1);
    }

    int multi_ssd = 0;

    if (ssd_num > 1) {
        multi_ssd = 1;
    }

    // Open SSD device, now I just assume it is BAR 0 of target
    string pci_id[8] = {"e3", "36", "38", "39", "3a", "3b", "3c", "3d"};

    uint64_t device_low_addr = 0, device_high_addr = 0;

    for (int i=0; i<ssd_num; i++)
    {
        string fname = "/sys/bus/pci/devices/0000:" + pci_id[i] + ":00.0/resource";
        const char *fname_cstr = fname.c_str();
        fp = fopen(fname_cstr, "rb");
        if (fp == NULL)
        {
            fprintf(stderr, "ERROR: Cannot open nvme device.\n");
            exit(1);
        }

        fscanf(fp, "0x%lx", nvme_base+i);
        fclose(fp);

        if (nvme_base[i] == 0)
        {
            fprintf(stderr, "ERROR: Invalid PCI address for SSD, %d.\n", i);
            exit(1);
        }
        else
        {
            fprintf(stdout, "BAR 0 of nvme device is %lx.\n", nvme_base[i]);
        }
    }
    
    // Allocate memory space for admin queues.
	void* huge_base;
	struct huge_mem hm;
    int fd, hfd;
    int size = 4*1024*1024;
	if ((fd = open("/dev/rc4ml_dev",O_RDWR)) == -1) {
		printf("[ERROR] on open /dev/rc4ml_dev, maybe you need to add 'sudo', or insmod\n");
		exit(1);
   	}
	if ((hfd = open("/media/huge/abc", O_CREAT | O_RDWR | O_SYNC, 0755)) == -1) {
		printf("[ERROR] on open /media/huge/abc, maybe you need to add 'sudo'\n");
		exit(1);
   	}
	huge_base = mmap(0, size, PROT_READ | PROT_WRITE, MAP_SHARED, hfd, 0);
	printf("huge device mapped at vaddr:%p\n", huge_base);
	hm.vaddr = (unsigned long)huge_base;
	hm.size = size;
	if(ioctl(fd, HUGE_MAPPING_SET, &hm) == -1){
		printf("IOCTL SET failed.\n");
	}
	struct huge_mapping map;
	map.nhpages = size/(2*1024*1024);
	map.phy_addr = (unsigned long*) calloc(map.nhpages, sizeof(unsigned long*));
	if (ioctl(fd, HUGE_MAPPING_GET, &map) == -1) {
    	printf("IOCTL GET failed.\n");
   	}
    
    huge_virt_base = (uint64_t) huge_base;
    queue_phys_base = (uint64_t)map.phy_addr[0];
    fprintf(stdout, "Queue base is: %lx\n", queue_phys_base);
    smart_phys_base = (uint64_t)map.phy_addr[1];
    fprintf(stdout, "Smart base is: %lx\n", smart_phys_base);

    // Write the address of bypass buffer.
    uint8_t addr_buf[8];
    uint64_t *addr_full = (uint64_t *) addr_buf;
    uint32_t *addr_upper = (uint32_t *)(addr_buf + 4), *addr_lower = (uint32_t *)addr_buf;

    *addr_full = bypass_base;
    addr_upper = (uint32_t *)(addr_buf + 4);
    addr_lower = (uint32_t *)addr_buf;
    writeReg(37, *addr_upper);
    writeReg(36, *addr_lower);

    int ssd_id;
    for (ssd_id=0; ssd_id<ssd_num; ssd_id++)
    {
        fprintf(stdout, "Start to configure SSD %u...\n", ssd_id);
    
        // In this version CPU is in charge of admin queues.
        // Open SSD. Theoratically, I should write an driver for our "SSDs", 
        // but here for simplicity, I just write memory directly...
        // Sorry for such a hmmmmm...
        
        int fd;
        if ((fd = open("/dev/mem", O_RDWR | O_SYNC)) == -1) {
            printf("[ERROR] on open /dev/mem, maybe you need to add 'sudo'.\n");
            exit(1);
        }
        // Allocate virtual address for the SSD
        ssd_virt_base[ssd_id] = (uint64_t) mmap(0, 8192, PROT_READ | PROT_WRITE, MAP_SHARED, fd, nvme_base[ssd_id]);

        // Configure SSD.
        writeReg(40, ssd_id);
        // Write SSD address.
        *addr_full = nvme_base[ssd_id];
        addr_upper = (uint32_t *)(addr_buf + 4);
        addr_lower = (uint32_t *)addr_buf;
        writeReg(38, *addr_lower);
        writeReg(39, *addr_upper);
        // Write the namespace to FPGA.
        writeReg(33, 1);

        // Activate the FPGA module.
        writeReg(32, 0);
        writeReg(32, 1);

        // Read necessary controller registers from SSD.

        // Capabilities. This program requires NVMe SSD to have following features:
        // - 4KB page support;
        // - 4B doorbell stride;
        // - Subsystem reset;
        // - at least 32 queue entries;
        // - Only one namespace with NSID set to 1.
        uint64_t nvme_ctl_cap = *((uint64_t *) ssd_virt_base[ssd_id]);
        uint64_t nvme_ctl_mpsmin = (nvme_ctl_cap >> 48) & 0xf;
        if (nvme_ctl_mpsmin > 0)
        {
            fprintf(stderr, "ERROR: The nvme device doesn't support 4KB page.\n");
            exit(1);
        }
        uint64_t nvme_ctl_dstrd = (nvme_ctl_cap >> 32) & 0xf;
        if (nvme_ctl_dstrd > 0)
        {
            fprintf(stderr, "ERROR: The nvme device doesn't support 4B doorbell stride.\n");
            exit(1);
        }
        uint64_t nvme_ctl_mqes = nvme_ctl_cap & 0xffff;
        if (nvme_ctl_mqes < 32)
        {
            fprintf(stderr, "ERROR: The nvme device doesn't support 32 queue entries.\n");
            exit(1);
        }

        // Reset the controller.
        uint32_t *nvme_cc_pt = (uint32_t *)(ssd_virt_base[ssd_id] + 0x14);
        *nvme_cc_pt = 0x460000; // Do not enable now.
        fprintf(stdout, "CC set to %08x.\n", *nvme_cc_pt);

        // Wait the controller to be completely reset.
        // Otherwise it will get stuck :(
        uint32_t *nvme_csts_pt = (uint32_t *)(ssd_virt_base[ssd_id] + 0x1c);
        while (*nvme_csts_pt != 0);
        fprintf(stdout, "System reset done. Current CSTS is %08x.\n", *nvme_csts_pt);

        // Set admin queue size to 32.
        uint32_t *nvme_aqa_pt = (uint32_t *)(ssd_virt_base[ssd_id] + 0x24);
        *nvme_aqa_pt = (ADMIN_QUEUE_DEPTH << 16) + ADMIN_QUEUE_DEPTH;
        fprintf(stdout, "AQA set to %08x.\n", *nvme_aqa_pt);

        // Set admin SQ base address.
        uint64_t *nvme_asq_pt = (uint64_t *)(ssd_virt_base[ssd_id] + 0x28);
        *nvme_asq_pt = SSD_ADMIN_SQ_PHYS_BASE(ssd_id);
        fprintf(stdout, "ASQ set to %016lx.\n", *nvme_asq_pt);

        // Set admin CQ base address.
        uint64_t *nvme_acq_pt = (uint64_t *)(ssd_virt_base[ssd_id] + 0x30);
        *nvme_acq_pt = SSD_ADMIN_CQ_PHYS_BASE(ssd_id);
        fprintf(stdout, "ACQ set to %016lx.\n", *nvme_acq_pt);

        // Enable the controller.
        *nvme_cc_pt = 0x460001;
        fprintf(stdout, "CC set to %08x.\n", *nvme_cc_pt);

        // Wait for the system to be started.
        while (*nvme_csts_pt == 0);
        fprintf(stdout, "System started. Current CSTS is %08x.\n", *nvme_csts_pt);

        // Reset queue pointers.
        admin_sq_tl[ssd_id] = 0;
        admin_cq_hd[ssd_id] = 0;

        uint32_t *nvme_cq_base = (uint32_t *)(SSD_ADMIN_CQ_VIRT_BASE(ssd_id));
        // Now clear the admin CQ buffer.
        for (int i=0; i<128; i++)
        {
            nvme_cq_base[i] = 0x0;
        }

        // Initialize SSD queues. First set feature.
        int cmd_ret = nvme_set_num_of_qp(ssd_id, queue_num);
        if (cmd_ret != 0)
        {
            fprintf(stdout, "ERROR: Set number of queue pair returned 0x%x\n", cmd_ret);
            exit(1);
        }
        
        for (int qid=1; qid<=queue_num; qid++)
        {
            // Calculate the address of each CQ.
            uint64_t cq_addr = bypass_base + ((qid-1) << queue_low_bit) + (ssd_id << ssd_low_bit) + (0x1 << ram_type_bit);
            // Create CQ now.
            cmd_ret = nvme_create_cq(ssd_id, qid, queue_depth, cq_addr);
            if (cmd_ret != 0)
            {
                fprintf(stdout, "ERROR: Create CQ %d returned 0x%x\n", qid, cmd_ret);
                exit(1);
            }

            uint64_t sq_addr = bypass_base + ((qid-1) << queue_low_bit) + (ssd_id << ssd_low_bit) + (0x0 << ram_type_bit);
            cmd_ret = nvme_create_sq(ssd_id, qid, qid, queue_depth, sq_addr);
            if (cmd_ret != 0)
            {
                fprintf(stdout, "ERROR: Create SQ %d returned 0x%x\n", qid, cmd_ret);
                exit(1);
            }
        }

        fprintf(stdout, "SSD %d queue initialization done.\n", ssd_id);

        // Try to get SMART page.
        cmd_ret = get_smart_info(ssd_id);
        if (cmd_ret != 0)
        {
            fprintf(stdout, "ERROR: Get smart page returned 0x%x\n", cmd_ret);
            exit(1);
        }

        uint8_t *smart_array = (uint8_t *)(huge_virt_base + 0x200000);

        // Get critical warnings.
        uint8_t smart_critical = smart_array[0];
        if (smart_critical != 0x00)
        {
            fprintf(stdout, "WARNING: SSD %d reported critical warning 0x%02x\n", ssd_id, smart_critical);
        }

        // Get temperature.
        // uint16_t smart_temp_comp;
        // smart_temp_comp = ((smart_array[2] << 8) + smart_array[1]) - 273;
        // fprintf(stdout, "Current temperature: %d\n", smart_temp_comp);

        // fprintf(stdout, "Creating I/O SQ/CQ...\n");
        // writeReg(128, 0);
        // writeReg(128, 1);
        // writeReg(128, 0);

        // // Wait for FPGA board to finish basic settings.
        // while (readReg(672) == 0);
        // if (readReg(673) != 0)
        // {
        //     fprintf(stderr, "ERROR: NVMe queue initialization failed.\n");
        //     fprintf(stdout, "status code: %08x\n", readReg(673));
        //     exit(1);
        // }

        // fprintf(stdout, "NVMe queue initialization done.\n");
    }

    char *zero_buffer = NULL;
    posix_memalign((void **)&zero_buffer, 64 /*alignment */ , 64);

    for (int i=0; i<64; i++)
    {
        zero_buffer[i] = 0;
    }

    int stop_benchmark = 0;

    while (!stop_benchmark)
    {
        // Benchmarking
        int mode, num_lb, benchmark_time;
        int benchmark_stuck = 0;

        fprintf(stdout, "Enter mode. +1 for write, +2 for random, +4 for mixed, +1024 for record: ");
        fscanf(stdin, "%d", &mode);
        fprintf(stdout, "Enter number of logical blocks (512 B) for each cmd: ");
        fscanf(stdin, "%d", &num_lb);
        fprintf(stdout, "Enter time in seconds: ");
        fscanf(stdin, "%d", &benchmark_time);

        // Set parameters
        writeReg(161, mode & 0x3);              
        writeReg(162, num_lb-1);                
        writeReg(163, benchmark_time*3906250);  // Time. 3,906,250 = 1s
        if (mode >= 1024)
        {
            writeReg(165, 1);
        }
        else
        {
            writeReg(165, 0);
        }
        fprintf(stdout, "Start benchmark...\n");

        writeReg(160, 0);
        writeReg(160, 1);
        sleep(benchmark_time);
        writeReg(160, 0);

        int diff_time = 0;

        while (readReg(704) == 0)
        {
            sleep(1);
            diff_time += 1;
            
            if (diff_time > 3)
            {
                
                // For debugging queues
                uint32_t *bypass_entry_buffer;
                posix_memalign((void **)&bypass_entry_buffer, 64 /*alignment */ , 64);
                fprintf(stderr, "ERROR: Benchmark stuck, now print information of SQE and CQE: \n");
                for (ssd_id=0; ssd_id<ssd_num; ssd_id++)
                {
                    // Submission queue 1
                    for (int i=0; i<queue_depth; i++)
                    {
                        fprintf(stdout, "%x\n", (0<<(ram_type_bit-6))+(ssd_id<<(ssd_low_bit-6))+i);
                        readBridge((0<<(ram_type_bit-6))+(ssd_id<<(ssd_low_bit-6))+i, (uint64_t *)bypass_entry_buffer);
                        fprintf(stdout, "content of SSD %d SQ %02x:\n", ssd_id, i);
                        for (int j=0; j<16; j++)
                        {
                            fprintf(stdout, "DW%2d: %08x\n", j, bypass_entry_buffer[j]);
                        }
                    }
                    // Completion queue 1
                    for (int i=0; i<(queue_depth/4); i++)
                    {
                        fprintf(stdout, "%x\n", (1<<(ram_type_bit-6))+(ssd_id<<(ssd_low_bit-6))+i);
                        readBridge((1<<(ram_type_bit-6))+(ssd_id<<(ssd_low_bit-6))+i, (uint64_t *)bypass_entry_buffer);
                        for (int j=0; j<4; j++)
                        {
                            fprintf(stdout, "content of SSD %d CQ %02x:\n", ssd_id, i*4+j);
                            for (int k=0; k<4; k++)
                            {
                                fprintf(stdout, "DW%2d: %08x\n", k, bypass_entry_buffer[j*4+k]);
                            }
                        }
                    }
                }
                benchmark_stuck = 1;
                break;
            }
        };
        if (benchmark_stuck)
        {
            break;
        }

        writeReg(165, 0);
        int successful_op = readReg(705);
        int failed_op = readReg(706);
        int first_latency = readReg(707);
        uint64_t total_cycles;
        uint32_t *total_cycles_lower = (uint32_t *) &total_cycles;
        uint32_t *total_cycles_upper = total_cycles_lower + 1;
        *total_cycles_lower = readReg(708);
        *total_cycles_upper = readReg(709);
        double total_time = total_cycles/250000000.0;
        uint64_t total_latency;
        uint32_t *total_latency_lower = (uint32_t *) &total_latency;
        uint32_t *total_latency_upper = total_latency_lower + 1;
        *total_latency_lower = readReg(730);
        *total_latency_upper = readReg(731);
        double average_latency = total_latency / (250.0 * successful_op);
        fprintf(stdout, "Benchmark time: %lf s\n", total_time);

        fprintf(stdout, "Success: %d\nFailed: %d\n",
            successful_op, failed_op);
        fprintf(stdout, "Speed from IOPS: %lf MB/s",
            (num_lb*0.5*successful_op/1024.0) / total_time);
        if (ssd_num > 1) {
            uint32_t ssd_io[ssd_num];
            ssd_io[0] = readReg(712);
            fprintf(stdout, " (%.2lf",
                (num_lb*0.5*ssd_io[0]/1024.0) / total_time);
            for (int i=1; i<ssd_num; i++)
            {
                ssd_io[i] = readReg(712+i);
                fprintf(stdout, " + %.2lf",
                    (num_lb*0.5*ssd_io[i]/1024.0) / total_time);
            }
            fprintf(stdout, ")");
        }
        fprintf(stdout, "\n");
        fprintf(stdout, "Average latency: %lf us\n", average_latency);

        if (mode >= 1024)
        {
            // Get bandwidth curve
            fprintf(stdout, "Time (s),Read bandwidth (MB/s),Write bandwidth (MB/s)\n");
            uint32_t read_bw, write_bw;
            double bw_time = 0;
            writeReg(166, 0);
            writeReg(166, 1);
            writeReg(166, 0);
            write_bw = 0;
            read_bw = 0;
            while ((read_bw != 0xffffffff) && (write_bw != 0xffffffff))
            {
                fprintf(stdout, "%.1lf,%.2lf,%.2lf\n",
                    bw_time, read_bw*640.0/(1024*1024), write_bw*640.0/(1024*1024));
                bw_time += 0.1;
                writeReg(166, 0);
                writeReg(166, 1);
                writeReg(166, 0);
                read_bw = readReg(729);
                write_bw = readReg(728);
            }
        }

        // Find failed entries.
        for (ssd_id=0; ssd_id<ssd_num; ssd_id++)
        {
            for (int qid=0; qid<queue_num; qid++)
            {
                for (int i=0; i<(queue_depth/4); i++)
                {
                    readBridge(
                        (1<<(ram_type_bit-6))+(qid << (queue_low_bit-6))+(ssd_id<<(ssd_low_bit-6))+i,
                        (uint64_t *)bypass_entry_buffer);
                    for (int j=0; j<4; j++)
                    {
                        if ((bypass_entry_buffer[j*4+3] & 0xfffe0000) != 0)
                        {
                            fprintf(stdout, "Failed entry. Position: SSD %d queue %d entry %d\n", ssd_id, qid, i*4+j);
                            for (int k=0; k<4; k++)
                            {
                                fprintf(stdout, "DW%2d: %08x\n", k, bypass_entry_buffer[j*4+k]);
                            }
                        }
                    }
                }
            }
        }

        fprintf(stdout, "done.\n");

        fprintf(stdout, "0 for continue benchmark: ");
        fscanf(stdin, "%d", &stop_benchmark);

    }
}