module testbench_LatencyBucket(

    );

reg                 clock                         =0;
reg                 reset                         =0;
reg                 io_enable                     =0;
reg                 io_start                      =0;
reg                 io_end                        =0;
reg       [4:0]     io_bucketRdId                 =0;
wire      [31:0]    io_bucketValue                ;
reg                 io_resetBucket                =0;
wire                io_resetDone                  ;


LatencyBucket LatencyBucket_inst(
        .*
);


initial begin
        reset <= 1;
        clock = 1;
        #100;
        reset <= 0;
        io_enable <= 1;
        #6;
        io_start    <= 1;
        #2;
        io_start    <= 0;
        #2;
        io_end      <= 1;
        #1
        io_end      <= 0;
        #5;
        io_end      <= 1;
        #1;
        io_end      <= 0;
        io_start    <= 1;
        #1;
        io_start    <= 0;
        #10;
        io_end      <= 1;
        #1;
        io_end      <= 0;
        #10;
        io_enable   <= 0;
        io_bucketRdId   <= 0;
        while (io_bucketRdId < 31) begin
            #5;
            io_bucketRdId   <= io_bucketRdId + 'd1;
        end
        #5;
        io_resetBucket <= 1;
        #1;
        io_resetBucket <= 0;
        #50;
        $stop();
end
always #0.5 clock=~clock;

endmodule