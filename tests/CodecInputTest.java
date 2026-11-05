package com.gabriel.cinemaair;
public class CodecInputTest {
    static void check(boolean value) { if(!value) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        long[] time={0};int[] polls={0},services={0};
        int index=CodecInput.await(()->++polls[0]<7?-1:2,()->services[0]++,()->time[0]+=2,()->time[0],()->true,250);
        check(index==2 && services[0]==6); // A temporary wait beyond 10ms must not end playback.
        time[0]=0;
        try { CodecInput.await(()->-1,()->services[0]++,()->time[0]+=2,()->time[0],()->true,20);throw new AssertionError(); }
        catch(CodecInput.Stalled expected) { check(time[0]<=22); }
        try { CodecInput.await(()->-1,()->{},()->{},()->0,()->false,20);throw new AssertionError(); }
        catch(InterruptedException expected) {}
        System.out.println("PASS: transient decoder backpressure, bounded sustained stall, stop cancellation");
    }
}
