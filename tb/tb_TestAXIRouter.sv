module testbench_TestAXIRouter(

    );

reg                 clock                         =0;
reg                 reset                         =0;
wire                io_axibIn_aw_ready            ;
reg                 io_axibIn_aw_valid            =0;
reg       [63:0]    io_axibIn_aw_bits_addr        =0;
reg       [1:0]     io_axibIn_aw_bits_burst       =0;
reg       [3:0]     io_axibIn_aw_bits_cache       =0;
reg       [3:0]     io_axibIn_aw_bits_id          =0;
reg       [7:0]     io_axibIn_aw_bits_len         =0;
reg                 io_axibIn_aw_bits_lock        =0;
reg       [2:0]     io_axibIn_aw_bits_prot        =0;
reg       [3:0]     io_axibIn_aw_bits_qos         =0;
reg       [3:0]     io_axibIn_aw_bits_region      =0;
reg       [2:0]     io_axibIn_aw_bits_size        =0;
wire                io_axibIn_ar_ready            ;
reg                 io_axibIn_ar_valid            =0;
reg       [63:0]    io_axibIn_ar_bits_addr        =0;
reg       [1:0]     io_axibIn_ar_bits_burst       =0;
reg       [3:0]     io_axibIn_ar_bits_cache       =0;
reg       [3:0]     io_axibIn_ar_bits_id          =0;
reg       [7:0]     io_axibIn_ar_bits_len         =0;
reg                 io_axibIn_ar_bits_lock        =0;
reg       [2:0]     io_axibIn_ar_bits_prot        =0;
reg       [3:0]     io_axibIn_ar_bits_qos         =0;
reg       [3:0]     io_axibIn_ar_bits_region      =0;
reg       [2:0]     io_axibIn_ar_bits_size        =0;
wire                io_axibIn_w_ready             ;
reg                 io_axibIn_w_valid             =0;
reg       [511:0]   io_axibIn_w_bits_data         =0;
reg                 io_axibIn_w_bits_last         =0;
reg       [63:0]    io_axibIn_w_bits_strb         =0;
reg                 io_axibIn_r_ready             =0;
wire                io_axibIn_r_valid             ;
wire      [511:0]   io_axibIn_r_bits_data         ;
wire                io_axibIn_r_bits_last         ;
wire      [1:0]     io_axibIn_r_bits_resp         ;
wire      [3:0]     io_axibIn_r_bits_id           ;
reg                 io_axibIn_b_ready             =0;
wire                io_axibIn_b_valid             ;
wire      [3:0]     io_axibIn_b_bits_id           ;
wire      [1:0]     io_axibIn_b_bits_resp         ;
wire                io_ramOut_0_readEnable        ;
wire      [63:0]    io_ramOut_0_readAddr          ;
reg       [511:0]   io_ramOut_0_readData          =0;
wire      [63:0]    io_ramOut_0_writeMask         ;
wire      [63:0]    io_ramOut_0_writeAddr         ;
wire      [511:0]   io_ramOut_0_writeData         ;
wire                io_ramOut_1_readEnable        ;
wire      [63:0]    io_ramOut_1_readAddr          ;
reg       [511:0]   io_ramOut_1_readData          =100;
wire      [63:0]    io_ramOut_1_writeMask         ;
wire      [63:0]    io_ramOut_1_writeAddr         ;
wire      [511:0]   io_ramOut_1_writeData         ;
wire                io_ramOut_2_readEnable        ;
wire      [63:0]    io_ramOut_2_readAddr          ;
reg       [511:0]   io_ramOut_2_readData          =200;
wire      [63:0]    io_ramOut_2_writeMask         ;
wire      [63:0]    io_ramOut_2_writeAddr         ;
wire      [511:0]   io_ramOut_2_writeData         ;

IN#(97)in_io_axibIn_aw(
        clock,
        reset,
        {io_axibIn_aw_bits_addr,io_axibIn_aw_bits_burst,io_axibIn_aw_bits_cache,io_axibIn_aw_bits_id,io_axibIn_aw_bits_len,io_axibIn_aw_bits_lock,io_axibIn_aw_bits_prot,io_axibIn_aw_bits_qos,io_axibIn_aw_bits_region,io_axibIn_aw_bits_size},
        io_axibIn_aw_valid,
        io_axibIn_aw_ready
);
// addr, burst, cache, id, len, lock, prot, qos, region, size
// 64'h0, 2'h0, 4'h0, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h0

IN#(97)in_io_axibIn_ar(
        clock,
        reset,
        {io_axibIn_ar_bits_addr,io_axibIn_ar_bits_burst,io_axibIn_ar_bits_cache,io_axibIn_ar_bits_id,io_axibIn_ar_bits_len,io_axibIn_ar_bits_lock,io_axibIn_ar_bits_prot,io_axibIn_ar_bits_qos,io_axibIn_ar_bits_region,io_axibIn_ar_bits_size},
        io_axibIn_ar_valid,
        io_axibIn_ar_ready
);
// addr, burst, cache, id, len, lock, prot, qos, region, size
// 64'h0, 2'h0, 4'h0, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h0

IN#(577)in_io_axibIn_w(
        clock,
        reset,
        {io_axibIn_w_bits_data,io_axibIn_w_bits_last,io_axibIn_w_bits_strb},
        io_axibIn_w_valid,
        io_axibIn_w_ready
);
// data, last, strb
// 512'h0, 1'h0, 64'h0

OUT#(519)out_io_axibIn_r(
        clock,
        reset,
        {io_axibIn_r_bits_data,io_axibIn_r_bits_last,io_axibIn_r_bits_resp,io_axibIn_r_bits_id},
        io_axibIn_r_valid,
        io_axibIn_r_ready
);
// data, last, resp, id
// 512'h0, 1'h0, 2'h0, 4'h0

OUT#(6)out_io_axibIn_b(
        clock,
        reset,
        {io_axibIn_b_bits_id,io_axibIn_b_bits_resp},
        io_axibIn_b_valid,
        io_axibIn_b_ready
);
// id, resp
// 4'h0, 2'h0


TestAXIRouter TestAXIRouter_inst(
        .*
);


initial begin
    reset <= 1;
    clock = 1;
    #100;
    reset <= 0;
    #20;
    out_io_axibIn_r.start();
    out_io_axibIn_b.start();
    #100;
    // Simple Write Request
    // addr, burst, cache, id, len, lock, prot, qos, region, size
    in_io_axibIn_aw.write({64'h8000000, 2'h0, 4'h0, 4'h2, 8'h3, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});    // CHannel 2
    in_io_axibIn_aw.write({64'h00b0000, 2'h0, 4'h0, 4'h3, 8'h3, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});    // CHannel 0
    in_io_axibIn_aw.write({64'h4000000, 2'h0, 4'h0, 4'h4, 8'h7, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});    // CHannel 1
    in_io_axibIn_aw.write({64'h80a0000, 2'h0, 4'h0, 4'h5, 8'h3, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});    // CHannel 2
    // Channel 2
    // data, last, strb
    in_io_axibIn_w.write({512'h0, 1'h0, 64'hffffffffffffffff});
    in_io_axibIn_w.write({512'h1, 1'h0, 64'hffffffffffffffff});
    in_io_axibIn_w.write({512'h2, 1'h0, 64'hffffffffffffffff});
    in_io_axibIn_w.write({512'h3, 1'h1, 64'hffffffffffffffff});
    // Channel 0
    in_io_axibIn_w.write({512'h0, 1'h0, 64'hffffffffffffffff});
    in_io_axibIn_w.write({512'h1, 1'h0, 64'hffffffffffffffff});
    #40;
    in_io_axibIn_w.write({512'h2, 1'h0, 64'hffffffffffffffff});
    in_io_axibIn_w.write({512'h3, 1'h1, 64'hffffffffffffffff});
    // Channel 1
    in_io_axibIn_w.write({512'h0, 1'h0, 64'hffffffffffffffff});
    in_io_axibIn_w.write({512'h1, 1'h0, 64'hffffffffffffffff});
    in_io_axibIn_w.write({512'h2, 1'h0, 64'hffffffffffffffff});
    in_io_axibIn_w.write({512'h3, 1'h0, 64'hffffffffffffffff});
    in_io_axibIn_w.write({512'h4, 1'h0, 64'hffffffffffffffff});
    in_io_axibIn_w.write({512'h5, 1'h0, 64'hffffffffffffffff});
    in_io_axibIn_w.write({512'h6, 1'h0, 64'hffffffffffffffff});
    #40;
    in_io_axibIn_w.write({512'h7, 1'h1, 64'hffffffffffffffff});
    // Channel 2
    in_io_axibIn_w.write({512'h4, 1'h0, 64'hffffffffffffffff});
    in_io_axibIn_w.write({512'h5, 1'h0, 64'hffffffffffffffff});
    in_io_axibIn_w.write({512'h6, 1'h0, 64'hffffffffffffffff});
    in_io_axibIn_w.write({512'h7, 1'h1, 64'hffffffffffffffff});
    #100;
    // Simple Read Request
    // addr, burst, cache, id, len, lock, prot, qos, region, size
    in_io_axibIn_ar.write({64'h8000000, 2'h0, 4'h0, 4'h2, 8'h3, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});    // CHannel 2
    in_io_axibIn_ar.write({64'h00b0000, 2'h0, 4'h0, 4'h3, 8'h3, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});    // CHannel 0
    in_io_axibIn_ar.write({64'h4000000, 2'h0, 4'h0, 4'h4, 8'h7, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});    // CHannel 1
    in_io_axibIn_ar.write({64'h80a0000, 2'h0, 4'h0, 4'h5, 8'h3, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});    // CHannel 2
    #28;
    out_io_axibIn_r.stop();
    #4;
    out_io_axibIn_r.start();
    #100;
    // Realistic Write Request
    // Sample 1
    in_io_axibIn_aw.write({64'h8000040, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 2
    in_io_axibIn_aw.write({64'h8000000, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 3
    in_io_axibIn_aw.write({64'h00000000080000c0, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 4
    in_io_axibIn_aw.write({64'h8000080, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 5
    in_io_axibIn_aw.write({64'h8000140, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 6
    in_io_axibIn_aw.write({64'h8000100, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 7
    in_io_axibIn_aw.write({64'h00000000080001c0, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 8
    in_io_axibIn_aw.write({64'h8000180, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 9
    in_io_axibIn_aw.write({64'h8000240, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 10
    in_io_axibIn_aw.write({64'h8000200, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 11
    in_io_axibIn_aw.write({64'h00000000080002c0, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 12
    in_io_axibIn_aw.write({64'h8000280, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 13
    in_io_axibIn_aw.write({64'h8000340, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 14
    in_io_axibIn_aw.write({64'h8000300, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 15
    in_io_axibIn_aw.write({64'h00000000080003c0, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 16
    in_io_axibIn_aw.write({64'h8000780, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 17
    in_io_axibIn_aw.write({64'h8000440, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 18
    in_io_axibIn_aw.write({64'h8000400, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 19
    in_io_axibIn_aw.write({64'h00000000080004c0, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 20
    in_io_axibIn_aw.write({64'h8000480, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 21
    in_io_axibIn_aw.write({64'h8000540, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 22
    in_io_axibIn_aw.write({64'h8000500, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 23
    in_io_axibIn_aw.write({64'h00000000080005c0, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 24
    in_io_axibIn_aw.write({64'h8000580, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 25
    in_io_axibIn_aw.write({64'h8000640, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 26
    in_io_axibIn_aw.write({64'h8000600, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 27
    in_io_axibIn_aw.write({64'h00000000080006c0, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 28
    in_io_axibIn_aw.write({64'h8000680, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 29
    in_io_axibIn_aw.write({64'h8000740, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 30
    in_io_axibIn_aw.write({64'h8000700, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 31
    in_io_axibIn_aw.write({64'h00000000080007c0, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 32
    in_io_axibIn_aw.write({64'h8000380, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 33
    in_io_axibIn_aw.write({64'h8000840, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 34
    in_io_axibIn_aw.write({64'h8000800, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 35
    in_io_axibIn_aw.write({64'h00000000080008c0, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 36
    in_io_axibIn_aw.write({64'h8000880, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 37
    in_io_axibIn_aw.write({64'h8000940, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 38
    in_io_axibIn_aw.write({64'h8000900, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 39
    in_io_axibIn_aw.write({64'h00000000080009c0, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 40
    in_io_axibIn_aw.write({64'h8000980, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 41
    in_io_axibIn_aw.write({64'h0000000008000a40, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 42
    in_io_axibIn_aw.write({64'h0000000008000a00, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 43
    in_io_axibIn_aw.write({64'h0000000008000ac0, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 44
    in_io_axibIn_aw.write({64'h0000000008000a80, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 45
    in_io_axibIn_aw.write({64'h0000000008000b40, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 46
    in_io_axibIn_aw.write({64'h0000000008000b00, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 47
    in_io_axibIn_aw.write({64'h0000000008000bc0, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 48
    in_io_axibIn_aw.write({64'h0000000008000f80, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 49
    in_io_axibIn_aw.write({64'h0000000008000c40, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 50
    in_io_axibIn_aw.write({64'h0000000008000c00, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 51
    in_io_axibIn_aw.write({64'h0000000008000cc0, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 52
    in_io_axibIn_aw.write({64'h0000000008000c80, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 53
    in_io_axibIn_aw.write({64'h0000000008000d40, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 54
    in_io_axibIn_aw.write({64'h0000000008000d00, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 55
    in_io_axibIn_aw.write({64'h0000000008000dc0, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 56
    in_io_axibIn_aw.write({64'h0000000008000d80, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 57
    in_io_axibIn_aw.write({64'h8E+043, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 58
    in_io_axibIn_aw.write({64'h8000, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 59
    in_io_axibIn_aw.write({64'h0000000008000ec0, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 60
    in_io_axibIn_aw.write({64'h8E+083, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 61
    in_io_axibIn_aw.write({64'h0000000008000f40, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffeffeffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 62
    in_io_axibIn_aw.write({64'h0000000008000f00, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'h0001002e0001003f0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000, 1'h1, 64'hffff000000000000});
    #8; // Sample 63
    in_io_axibIn_aw.write({64'h0000000008000fc0, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 64
    in_io_axibIn_aw.write({64'h0000000008000b80, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 65
    in_io_axibIn_aw.write({64'h2030, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 66
    in_io_axibIn_aw.write({64'h8000040, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 67
    in_io_axibIn_aw.write({64'h8000000, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 68
    in_io_axibIn_aw.write({64'h00000000080000c0, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 69
    in_io_axibIn_aw.write({64'h8000080, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 70
    in_io_axibIn_aw.write({64'h8000140, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 71
    in_io_axibIn_aw.write({64'h8000100, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #8; // Sample 72
    in_io_axibIn_aw.write({64'h00000000080001c0, 2'h1, 4'h3, 4'h0, 8'h0, 1'h0, 3'h0, 4'h0, 4'h0, 3'h6});
    #4;
    in_io_axibIn_w.write({512'hfffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffefffffffe, 1'h1, 64'hffffffffffffffff});
    #100;
    $stop();
end
always #2 clock=~clock;

always @(posedge clock) begin
    if (io_ramOut_0_readEnable) begin
        io_ramOut_0_readData <= io_ramOut_0_readData + 'd1;
    end
    if (io_ramOut_1_readEnable) begin
        io_ramOut_1_readData <= io_ramOut_1_readData + 'd1;
    end
    if (io_ramOut_2_readEnable) begin
        io_ramOut_2_readData <= io_ramOut_2_readData + 'd1;
    end
end

endmodule