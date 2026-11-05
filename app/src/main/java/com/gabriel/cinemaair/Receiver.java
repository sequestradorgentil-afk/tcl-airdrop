// SPDX-License-Identifier: GPL-3.0-or-later
package com.gabriel.cinemaair;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.view.Surface;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class Receiver {
    static { System.loadLibrary("cinemaair"); }
    interface Listener { void state(String text); void picture(int width,int height); }
    private final Context context;
    private final Listener listener;
    final Diagnostics diagnostics;
    private final ExecutorService control=Executors.newSingleThreadExecutor();
    private WifiManager.MulticastLock multicast;
    private volatile Playback playback;
    private Playback unclosed;
    private volatile boolean active;
    private volatile long epoch;
    private android.net.ConnectivityManager.NetworkCallback networkCallback;
    private boolean networkRegistered;
    Receiver(Context context,Listener listener) { this.context=context;this.listener=listener;diagnostics=new Diagnostics(context); }
    private native String nativeStart(String keyFile,byte[] identity,int width,int height);
    private native void nativeStop();
    void start(Surface surface,int width,int height) {
        if(control.isShutdown()) return;
        control.execute(()->{
            if(active) return;
            if(unclosed!=null) {
                if(!unclosed.terminated()) { listener.state("Error: decoder still releasing. Stop and reopen Cinema Air if it does not recover.");return; }
                unclosed=null;
            }
            if(!surface.isValid()) { listener.state("Error: video surface is unavailable");return; }
            long generation=++epoch;
            try {
                playback=new Playback(surface,listener,(code,text)->{ if(epoch==generation) fail(code,text); },diagnostics,width,height);
                WifiManager wifi=(WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if(wifi!=null) { multicast=wifi.createMulticastLock("cinemaair-discovery");multicast.setReferenceCounted(false);multicast.acquire(); }
                byte[] id=new byte[6];new SecureRandom().nextBytes(id);id[0]=(byte)((id[0]&0xfc)|2);
                android.content.SharedPreferences prefs=context.getSharedPreferences("identity",0);
                String saved=prefs.getString("id",null);
                if(saved==null) prefs.edit().putString("id",android.util.Base64.encodeToString(id,2)).apply();
                else id=android.util.Base64.decode(saved,2);
                active=true;
                android.net.ConnectivityManager connectivity=(android.net.ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
                networkCallback=new android.net.ConnectivityManager.NetworkCallback() {
                    private android.net.Network current;
                    @Override public void onAvailable(android.net.Network network) {
                        if(current!=null && !current.equals(network)) fail("E_NETWORK","Network changed; start receiving again");
                        current=network;
                    }
                    @Override public void onLost(android.net.Network network) { fail("E_NETWORK","Network lost; reconnect and start receiving again"); }
                };
                connectivity.registerDefaultNetworkCallback(networkCallback);networkRegistered=true;
                String error=nativeStart(new java.io.File(context.getFilesDir(),"pairing.key").getAbsolutePath(),id,width,height);
                if(error!=null) throw new IllegalStateException(error);
                diagnostics.event("START "+width+"x"+height+" fps=30");
                listener.state("Ready · Cinema Air · "+width+"×"+height+" / 30 fps requested");
            } catch(Exception e) {
                release();diagnostics.failure("E_START",e.getClass().getSimpleName()+" · "+e.getMessage());
                listener.state("Error: "+e.getMessage());
            }
        });
    }
    void stop() {
        active=false;
        if(!control.isShutdown()) control.execute(()->{ release();diagnostics.event("STOP");listener.state("Stopped"); });
    }
    void destroy() { if(!control.isShutdown()) { control.execute(this::release);control.shutdown(); } }
    private void release() {
        active=false;
        if(networkRegistered) {
            ((android.net.ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE)).unregisterNetworkCallback(networkCallback);
            networkRegistered=false;
        }
        networkCallback=null;nativeStop();
        Playback p=playback;playback=null;
        if(p!=null) {
            p.close();
            if(!p.terminated()) { unclosed=p;diagnostics.failure("E_CLOSE_TIMEOUT","Codec worker has not released within two seconds"); }
        }
        if(multicast!=null && multicast.isHeld()) multicast.release();
        multicast=null;
    }
    @SuppressWarnings("unused") public void onPacket(int kind,byte[] data,long timeNs) {
        Playback p=playback;
        if(active && p!=null && !p.offer(kind,data,timeNs)) fail("E_QUEUE","Compressed media exceeded its bounded queue");
    }
    @SuppressWarnings("unused") public void onVolume(float db) { Playback p=playback;if(p!=null) p.volume(db); }
    @SuppressWarnings("unused") public void onSize(int width,int height) {
        Playback p=playback;if(p!=null) p.dimensions(width,height);
        listener.picture(width,height);
    }
    @SuppressWarnings("unused") public void onEvent(String text) {
        if(!active) return;
        Playback p=playback;
        if(text.equals("Video ended") || text.equals("Video interrupted")) {
            diagnostics.event(text.equals("Video ended")?"VIDEO_ENDED":"VIDEO_INTERRUPTED");
            if(p!=null) p.endVideo(); // A video stream ending must not tear down continuing audio.
        } else if(text.equals("Audio flush")) {
            if(p!=null) p.flushAudio();
        } else if(text.startsWith("Error:")) fail("E_PROTOCOL",text);
        else listener.state(text); // Pairing PIN/sender display text is never sent to diagnostic logs.
    }
    private synchronized void fail(String code,String detail) {
        if(!active) return;
        active=false;diagnostics.failure(code,detail);
        if(!control.isShutdown()) control.execute(()->{ release();listener.state("Error: "+code+" · "+detail); });
    }
}
