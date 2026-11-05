// SPDX-License-Identifier: GPL-3.0-or-later
package com.gabriel.cinemaair;

/** Retry normal MediaCodec backpressure while servicing both output paths. */
final class CodecInput {
    interface Poll { int get() throws Exception; }
    interface Work { void run() throws Exception; }
    interface Clock { long now(); }
    interface Live { boolean get(); }
    static final class Stalled extends Exception {}
    static int await(Poll poll,Work service,Work pause,Clock clock,Live live,long budget) throws Exception {
        long start=clock.now();
        while(live.get()) {
            int index=poll.get();
            if(index>=0) return index;
            service.run();
            if(clock.now()-start>=budget) throw new Stalled();
            pause.run();
        }
        throw new InterruptedException();
    }
}
