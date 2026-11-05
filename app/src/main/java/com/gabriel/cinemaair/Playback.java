// SPDX-License-Identifier: GPL-3.0-or-later
package com.gabriel.cinemaair;

import android.media.*;
import android.os.Process;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded media queues; one worker owns every codec and AudioTrack operation. */
final class Playback implements AutoCloseable {
    interface Failure { void fail(String code,String detail); }
    private static final int MAX_PACKET=2*1024*1024, MAX_BYTES=8*1024*1024;
    private static final int MAX_PCM_BYTES=256*1024;
    private final ArrayBlockingQueue<Packet> queue=new ArrayBlockingQueue<>(48);
    private final Surface surface;
    private final Receiver.Listener listener;
    private final Failure error;
    private final Diagnostics diagnostics;
    private final Thread worker;
    private volatile boolean alive=true;
    private volatile float gain=1;
    private float appliedGain=-1;
    private static final class Dimensions {
        final int w,h; Dimensions(int w,int h) { this.w=w;this.h=h; }
    }
    private volatile Dimensions dimensions;
    private final AtomicLong videoEpoch=new AtomicLong(),audioEpoch=new AtomicLong();
    private long codecEpoch,trackEpoch;
    private int codecWidth,codecHeight,outputWidth,outputHeight,audioKind,queuedBytes,pcmBytes;
    private MediaCodec video,audio;
    private AudioTrack track;
    private boolean rendered,reportedAudio;
    private long writtenFrames,framesSubmitted,framesDropped,audioFrames,inputWaits,lastReport,lastAudio;
    private final MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
    private final ArrayDeque<Pcm> pcm=new ArrayDeque<>();
    private final AudioTimestamp audioTimestamp=new AudioTimestamp();
    private static final class Pcm {
        final byte[] data; final long time; int offset;
        Pcm(byte[] data,long time) { this.data=data;this.time=time; }
    }
    private static final class Packet {
        final int kind; final byte[] data; final long time,vEpoch,aEpoch;final Dimensions size;
        Packet(int kind,byte[] data,long time,Dimensions size,long vEpoch,long aEpoch) {
            this.kind=kind;this.data=data;this.time=time;this.size=size;this.vEpoch=vEpoch;this.aEpoch=aEpoch;
        }
    }
    private static final class Fault extends Exception {
        final String code;Fault(String code,String text) { super(text);this.code=code; }
    }
    Playback(Surface surface,Receiver.Listener listener,Failure error,Diagnostics diagnostics,int width,int height) {
        this.surface=surface;this.listener=listener;this.error=error;this.diagnostics=diagnostics;
        dimensions=new Dimensions(width,height);
        MediaFormat required=MediaFormat.createAudioFormat("audio/mp4a-latm",44100,2);
        required.setInteger(MediaFormat.KEY_AAC_PROFILE,39);
        if(new MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(required)==null)
            throw new IllegalStateException("AAC-ELD decoder unavailable; reception was not advertised");
        if(Quality.decoder(width,height)==null) throw new IllegalStateException("Vendor AVC decoder does not support selected resolution at 30 fps");
        worker=new Thread(this::run,"cinemaair-codecs");worker.start();
    }
    synchronized boolean offer(int kind,byte[] data,long time) {
        if(!alive || data.length>MAX_PACKET || queuedBytes>MAX_BYTES-data.length) return false;
        if(!queue.offer(new Packet(kind,data,time,dimensions,videoEpoch.get(),audioEpoch.get()))) return false;
        queuedBytes+=data.length;return true;
    }
    void dimensions(int width,int height) { dimensions=new Dimensions(width,height); }
    void volume(float db) {
        if(Float.isNaN(db) || Float.isInfinite(db)) return;
        gain=db<=-144?0:(float)Math.pow(10,Math.min(0,db)/20.0);
    }
    void endVideo() { videoEpoch.incrementAndGet(); }
    void flushAudio() { audioEpoch.incrementAndGet(); }
    private void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        try {
            while(alive) {
                applyResets();
                Packet p=queue.poll(video==null && track==null?50:5,TimeUnit.MILLISECONDS);
                if(p!=null) {
                    synchronized(this) { queuedBytes-=p.data.length; }
                    if((p.kind==0 && p.vEpoch!=videoEpoch.get()) || (p.kind!=0 && p.aEpoch!=audioEpoch.get())) continue;
                    if(p.kind==1) { preparePcm();enqueuePcm(p.data,p.time); }
                    else {
                        MediaCodec codec=prepare(p.kind,p.size);
                        int index=CodecInput.await(()->codec.dequeueInputBuffer(0),()->{
                            inputWaits++;service();
                        },()->Thread.sleep(2),System::nanoTime,()->alive,250000000L);
                        if(!alive) break;
                        ByteBuffer input=codec.getInputBuffer(index);
                        if(input==null || input.capacity()<p.data.length) throw new Fault("E_FRAME_SIZE","Compressed frame exceeds decoder input capacity");
                        input.clear();input.put(p.data);
                        codec.queueInputBuffer(index,0,p.data.length,p.time/1000,0);
                    }
                }
                service();
                if(!rendered && track!=null && pcm.isEmpty() && System.nanoTime()-lastAudio>3000000000L) {
                    closeAudio();listener.state("Ready · audio idle; reception remains available");
                }
                if(System.nanoTime()-lastReport>2000000000L) report();
            }
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
          catch(CodecInput.Stalled e) { if(alive) error.fail("E_INPUT_STALL","No decoder input buffer for 250 ms despite draining output"); }
          catch(Exception e) { if(alive) error.fail(e instanceof Fault?((Fault)e).code:"E_CODEC",e.getClass().getSimpleName()+" · "+e.getMessage()); }
        finally { report();release(); }
    }
    private void applyResets() {
        if(codecEpoch!=videoEpoch.get()) {
            closeVideo();codecEpoch=videoEpoch.get();rendered=false;
            listener.state(track!=null?"Receiving audio · video stream ended":"Ready · listening for a sender");
        }
        if(trackEpoch!=audioEpoch.get()) {
            if(audio!=null) audio.flush();
            pcm.clear();pcmBytes=0;
            if(track!=null) { track.pause();track.flush();track.play(); }
            writtenFrames=0;trackEpoch=audioEpoch.get();
        }
    }
    private void service() throws Exception {
        if(video!=null) drain(video,true);
        if(audio!=null) drain(audio,false);
        pumpAudio();
    }
    private MediaCodec prepare(int kind,Dimensions size) throws Exception {
        if(kind==0) {
            if(video!=null && (codecWidth!=size.w || codecHeight!=size.h)) closeVideo();
            if(video==null) {
                MediaFormat f=MediaFormat.createVideoFormat("video/avc",size.w,size.h);
                f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,MAX_PACKET);
                String name=Quality.decoder(size.w,size.h);
                if(name==null) throw new Fault("E_VIDEO_FORMAT","Vendor H.264 decoder cannot handle "+size.w+"×"+size.h+" at 30 fps");
                video=MediaCodec.createByCodecName(name);video.configure(f,surface,null,0);video.start();
                codecWidth=size.w;codecHeight=size.h;
            }
            return video;
        }
        if(kind!=8 && kind!=4) throw new Fault("E_AUDIO_FORMAT","Unsupported negotiated audio codec");
        if(audioKind!=kind) closeAudio();
        if(audio==null) {
            MediaFormat f=MediaFormat.createAudioFormat("audio/mp4a-latm",44100,2);
            f.setInteger(MediaFormat.KEY_AAC_PROFILE,kind==8?39:2);
            f.setByteBuffer("csd-0",ByteBuffer.wrap(kind==8?new byte[]{(byte)0xf8,(byte)0xe8,0x50,0}:new byte[]{0x12,0x10}));
            String name=new MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(f);
            if(name==null) throw new Fault("E_AUDIO_DECODER","AAC decoder unavailable for negotiated profile");
            audio=MediaCodec.createByCodecName(name);audio.configure(f,null,null,0);audio.start();audioKind=kind;
        }
        return audio;
    }
    private void preparePcm() throws Exception {
        if(audioKind!=1) closeAudio();
        if(track==null) createTrack(44100,2);
        audioKind=1;
    }
    private void createTrack(int rate,int channels) throws Exception {
        closeTrack();
        if(rate!=44100 || channels!=2) throw new Fault("E_PCM_FORMAT","Only stereo 44.1 kHz PCM is negotiated");
        int min=AudioTrack.getMinBufferSize(rate,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT);
        if(min<=0) throw new Fault("E_AUDIO_OUTPUT","Audio output buffer query failed");
        track=new AudioTrack(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build(),
            new AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build(),
            Math.max(min,rate*4/10),AudioTrack.MODE_STREAM,AudioManager.AUDIO_SESSION_ID_GENERATE);
        if(track.getState()!=AudioTrack.STATE_INITIALIZED) throw new Fault("E_AUDIO_OUTPUT","Audio output initialization failed");
        track.setVolume(gain);appliedGain=gain;track.play();writtenFrames=0;
    }
    private void enqueuePcm(byte[] data,long time) throws Exception {
        if(data.length==0 || data.length%4!=0 || data.length>16384) throw new Fault("E_PCM_FORMAT","Invalid stereo PCM block length");
        if(pcm.size()>=64 || pcmBytes>MAX_PCM_BYTES-data.length) throw new Fault("E_PCM_QUEUE","Decoded audio exceeded bounded startup buffer");
        pcm.addLast(new Pcm(data,time));pcmBytes+=data.length;audioFrames+=data.length/4;lastAudio=System.nanoTime();
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
                    outputWidth=w;outputHeight=h;listener.picture(w,h);
                } else {
                    if(f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING)!=AudioFormat.ENCODING_PCM_16BIT)
                        throw new Fault("E_PCM_FORMAT","Unsupported decoder PCM encoding");
                    createTrack(f.getInteger(MediaFormat.KEY_SAMPLE_RATE),f.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
                }
            } else if(index>=0) {
                if(picture) {
                    long deadline=info.presentationTimeUs*1000,delay=deadline-System.nanoTime();
                    if(delay>500000000L) { codec.releaseOutputBuffer(index,false);throw new Fault("E_VIDEO_CLOCK","Video timestamp is more than 500 ms ahead"); }
                    if(delay < -100000000L) { codec.releaseOutputBuffer(index,false);framesDropped++; }
                    else {
                        codec.releaseOutputBuffer(index,Math.max(System.nanoTime(),deadline));framesSubmitted++;
                        if(!rendered) { rendered=true;listener.state("Receiving video · "+codecWidth+"×"+codecHeight); }
                    }
                } else {
                    try {
                        if(info.size>0 && track!=null) {
                            ByteBuffer out=codec.getOutputBuffer(index);out.position(info.offset);out.limit(info.offset+info.size);
                            if(info.size>16384) throw new Fault("E_PCM_FORMAT","Decoded PCM block exceeds budget");
                            byte[] samples=new byte[info.size];out.get(samples);enqueuePcm(samples,info.presentationTimeUs*1000);
                        }
                    } finally { codec.releaseOutputBuffer(index,false); }
                }
            } else break;
        }
    }
    private void pumpAudio() throws Exception {
        if(track==null) return;
        if(appliedGain!=gain) { track.setVolume(gain);appliedGain=gain; }
        for(int n=0;n<4 && !pcm.isEmpty();n++) {
            Pcm p=pcm.peekFirst();long now=System.nanoTime();
            long played=Integer.toUnsignedLong(track.getPlaybackHeadPosition());
            long next=now+Math.max(0,writtenFrames-played)*1000000000L/44100;
            if(track.getTimestamp(audioTimestamp) && audioTimestamp.framePosition<=writtenFrames)
                next=Math.max(now,audioTimestamp.nanoTime+(writtenFrames-audioTimestamp.framePosition)*1000000000L/44100);
            long wait=p.time+(long)p.offset*1000000000L/(4*44100)-next;
            if(wait>500000000L || wait < -250000000L) throw new Fault("E_AUDIO_CLOCK","Audio timestamp outside bounded playback window");
            if(wait>0) return;
            int count=track.write(p.data,p.offset,p.data.length-p.offset,AudioTrack.WRITE_NON_BLOCKING);
            if(count<0) throw new Fault("E_AUDIO_WRITE","AudioTrack write failed: "+count);
            if(count==0) return;
            p.offset+=count;pcmBytes-=count;writtenFrames+=count/4;
            if(!rendered && !reportedAudio) { listener.state("Receiving audio · stereo 44.1 kHz");reportedAudio=true; }
            if(p.offset==p.data.length) pcm.removeFirst();
        }
    }
    private void report() {
        diagnostics.stats("videoSubmitted="+framesSubmitted+" lateDrops="+framesDropped+" audioFrames="+audioFrames+
            " inputRetries="+inputWaits+" pcmBytes="+pcmBytes+" output="+outputWidth+"x"+outputHeight);
        lastReport=System.nanoTime();
    }
    private void closeVideo() {
        if(video!=null) { try { video.stop(); } finally { video.release();video=null; } }
    }
    private void closeTrack() {
        pcm.clear();pcmBytes=0;reportedAudio=false;
        if(track!=null) { try { track.pause();track.flush(); } finally { track.release();track=null; } }
    }
    private void closeAudio() {
        if(audio!=null) { try { audio.stop(); } finally { audio.release();audio=null; } }
        closeTrack();audioKind=0;
    }
    private void release() {
        try { closeVideo(); } catch(Exception ignored) {}
        try { closeAudio(); } catch(Exception ignored) {}
        queue.clear();
    }
    @Override public void close() {
        alive=false;worker.interrupt();
        try { worker.join(2000); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
    }
    boolean terminated() { return !worker.isAlive(); }
}
