// SPDX-License-Identifier: GPL-3.0-or-later
package com.gabriel.cinemaair;

import android.media.*;
import android.os.Process;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Bounded compressed queues; all codec/track operations belong to one worker. */
final class Playback implements AutoCloseable {
    private static final int MAX_PACKET=1024*1024, MAX_BYTES=4*1024*1024;
    private final ArrayBlockingQueue<Packet> queue=new ArrayBlockingQueue<>(16);
    private final Surface surface;
    private final Receiver.Listener listener;
    private final Consumer<String> error;
    private final Thread worker;
    private volatile boolean alive=true;
    private volatile int width=1280,height=720;
    private int codecWidth,codecHeight, audioKind, queuedBytes;
    private MediaCodec video,audio;
    private AudioTrack track;
    private boolean rendered;
    private long writtenFrames;
    private int sampleRate=44100,channels=2;
    private final MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
    private final ArrayDeque<Pcm> pcm=new ArrayDeque<>();
    private final AudioTimestamp audioTimestamp=new AudioTimestamp();
    private static final class Pcm {
        final byte[] data; final long time; int offset;
        Pcm(byte[] data,long time) { this.data=data;this.time=time; }
    }
    private static final class Packet {
        final int kind; final byte[] data; final long time;
        Packet(int kind,byte[] data,long time) { this.kind=kind;this.data=data;this.time=time; }
    }
    Playback(Surface surface,Receiver.Listener listener,Consumer<String> error) {
        this.surface=surface;this.listener=listener;this.error=error;
        MediaFormat required=MediaFormat.createAudioFormat("audio/mp4a-latm",44100,2);
        required.setInteger(MediaFormat.KEY_AAC_PROFILE,39);
        if(new MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(required)==null)
            throw new IllegalStateException("AAC-ELD decoder unavailable; reception was not advertised");
        MediaFormat avc=MediaFormat.createVideoFormat("video/avc",1280,720);
        String decoder=new MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(avc);
        if(decoder==null || decoder.startsWith("OMX.google.") || decoder.startsWith("c2.android."))
            throw new IllegalStateException("Hardware H.264 decoder unavailable");
        worker=new Thread(this::run,"cinemaair-codecs");worker.start();
    }
    synchronized boolean offer(int kind,byte[] data,long time) {
        if (!alive) return false;
        if (data.length>MAX_PACKET || queuedBytes>MAX_BYTES-data.length) return false;
        if (!queue.offer(new Packet(kind,data,time))) return false;
        queuedBytes+=data.length;return true;
    }
    void dimensions(int width,int height) { this.width=width;this.height=height; }
    private void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        try {
            while(alive) {
                Packet p=queue.poll(5,TimeUnit.MILLISECONDS);
                if (p!=null) {
                    synchronized(this) { queuedBytes-=p.data.length; }
                    MediaCodec codec=prepare(p.kind);
                    drain(codec,p.kind==0);
                    int index=codec.dequeueInputBuffer(10000);
                    if(index<0) throw new IllegalStateException("Decoder stalled; reconnect sender");
                    ByteBuffer input=codec.getInputBuffer(index);
                    if(input==null || input.capacity()<p.data.length) throw new IllegalStateException("Compressed frame exceeds decoder capacity");
                    input.clear();input.put(p.data);
                    codec.queueInputBuffer(index,0,p.data.length,p.time/1000,0);
                }
                if(video!=null) drain(video,true);
                if(audio!=null) drain(audio,false);
                pumpAudio();
            }
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
          catch(Exception e) { if(alive) error.accept("Decoder: "+e.getClass().getSimpleName()+" · "+e.getMessage()); }
        finally { release(); }
    }
    private MediaCodec prepare(int kind) throws Exception {
        if(kind==0) {
            int w=width,h=height;
            if(video!=null && (codecWidth!=w || codecHeight!=h)) { video.stop();video.release();video=null; }
            if(video==null) {
                MediaFormat f=MediaFormat.createVideoFormat("video/avc",w,h);
                f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,MAX_PACKET);
                f.setInteger(MediaFormat.KEY_MAX_WIDTH,1920);f.setInteger(MediaFormat.KEY_MAX_HEIGHT,1920);
                MediaCodecList list=new MediaCodecList(MediaCodecList.REGULAR_CODECS);
                String name=list.findDecoderForFormat(f);
                if(name==null || name.startsWith("OMX.google.") || name.startsWith("c2.android."))
                    throw new IllegalStateException("No supported hardware H.264 decoder for "+w+"×"+h);
                video=MediaCodec.createByCodecName(name);
                video.configure(f,surface,null,0);video.start();codecWidth=w;codecHeight=h;
            }
            return video;
        }
        if(kind!=8 && kind!=4) throw new IllegalStateException("Unsupported negotiated audio codec");
        if(audio!=null && audioKind!=kind) { audio.stop();audio.release();audio=null;closeTrack(); }
        if(audio==null) {
            MediaFormat f=MediaFormat.createAudioFormat("audio/mp4a-latm",44100,2);
            f.setInteger(MediaFormat.KEY_AAC_PROFILE,kind==8?39:2);
            f.setByteBuffer("csd-0",ByteBuffer.wrap(kind==8?new byte[]{(byte)0xf8,(byte)0xe8,0x50,0}:new byte[]{0x12,0x10}));
            String name=new MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(f);
            if(name==null) throw new IllegalStateException("TV has no decoder for AAC profile "+(kind==8?"ELD":"LC"));
            audio=MediaCodec.createByCodecName(name);audio.configure(f,null,null,0);audio.start();audioKind=kind;
        }
        return audio;
    }
    private void drain(MediaCodec codec,boolean picture) throws Exception {
        for(int n=0;n<8 && alive;n++) {
            int index=codec.dequeueOutputBuffer(info,0);
            if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat f=codec.getOutputFormat();
                if(picture) {
                    int w=f.getInteger(MediaFormat.KEY_WIDTH),h=f.getInteger(MediaFormat.KEY_HEIGHT);
                    if(f.containsKey("crop-right")) w=f.getInteger("crop-right")-f.getInteger("crop-left")+1;
                    if(f.containsKey("crop-bottom")) h=f.getInteger("crop-bottom")-f.getInteger("crop-top")+1;
                    listener.picture(w,h);
                } else {
                    closeTrack();sampleRate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE);channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if(channels!=2 || (f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING)!=AudioFormat.ENCODING_PCM_16BIT))
                        throw new IllegalStateException("Unsupported decoded PCM format");
                    int min=AudioTrack.getMinBufferSize(sampleRate,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT);
                    track=new AudioTrack(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build(),
                        new AudioFormat.Builder().setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build(),
                        Math.max(min,sampleRate*channels*2/10),AudioTrack.MODE_STREAM,AudioManager.AUDIO_SESSION_ID_GENERATE);
                    if(track.getState()!=AudioTrack.STATE_INITIALIZED) throw new IllegalStateException("Audio output initialization failed");
                    track.play();writtenFrames=0;
                }
            } else if(index>=0) {
                if(picture) {
                    long deadline=info.presentationTimeUs*1000;
                    long delay=deadline-System.nanoTime();
                    if(delay>500000000L) { codec.releaseOutputBuffer(index,false);throw new IllegalStateException("Video timestamp discontinuity"); }
                    if(delay < -100000000L) codec.releaseOutputBuffer(index,false);
                    else {
                        codec.releaseOutputBuffer(index,Math.max(System.nanoTime(),deadline));
                        if(!rendered) { rendered=true;listener.state("Receiving · experimental synchronization"); }
                    }
                } else {
                    try {
                        if(info.size>0 && track!=null) {
                            if(pcm.size()>=16 || info.size>16384)
                                throw new IllegalStateException("Decoded audio queue exceeded its budget");
                            ByteBuffer out=codec.getOutputBuffer(index);
                            out.position(info.offset);out.limit(info.offset+info.size);
                            byte[] samples=new byte[info.size];out.get(samples);
                            pcm.addLast(new Pcm(samples,info.presentationTimeUs*1000));
                        }
                    } finally { codec.releaseOutputBuffer(index,false); }
                }
            } else break;
        }
    }
    private void pumpAudio() {
        // Never sleep on this shared codec worker: that would starve video input.
        if(track==null) return;
        for(int n=0;n<4 && !pcm.isEmpty();n++) {
            Pcm p=pcm.peekFirst();
            long now=System.nanoTime();
            long played=Integer.toUnsignedLong(track.getPlaybackHeadPosition());
            long next=now+Math.max(0,writtenFrames-played)*1000000000L/sampleRate;
            if(track.getTimestamp(audioTimestamp) && audioTimestamp.framePosition<=writtenFrames)
                next=Math.max(now,audioTimestamp.nanoTime+(writtenFrames-audioTimestamp.framePosition)*1000000000L/sampleRate);
            long deadline=p.time+(long)p.offset*1000000000L/(channels*2*sampleRate);
            long wait=deadline-next;
            if(wait>500000000L || wait < -250000000L)
                throw new IllegalStateException("Audio timestamp discontinuity; reconnect");
            if(wait>0) return;
            int count=track.write(p.data,p.offset,p.data.length-p.offset,AudioTrack.WRITE_NON_BLOCKING);
            if(count<0) throw new IllegalStateException("Audio output write failed: "+count);
            if(count==0) return;
            p.offset+=count;writtenFrames+=count/(channels*2);
            if(p.offset==p.data.length) pcm.removeFirst();
        }
    }
    private void closeTrack() {
        pcm.clear();
        if(track!=null) { track.pause();track.flush();track.release();track=null; }
    }
    private void release() {
        try { if(video!=null) { video.stop();video.release(); } } catch(Exception ignored) {} finally { video=null; }
        try { if(audio!=null) { audio.stop();audio.release(); } } catch(Exception ignored) {} finally { audio=null; }
        closeTrack();queue.clear();
    }
    @Override public void close() {
        alive=false;worker.interrupt();
        try { worker.join(2000); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
