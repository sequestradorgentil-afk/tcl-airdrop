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
    interface Listener { void state(String text); void picture(int width, int height); }
    private final Context context;
    private final Listener listener;
    private final ExecutorService control = Executors.newSingleThreadExecutor();
    private WifiManager.MulticastLock multicast;
    private volatile Playback playback;
    private volatile boolean active;
    private android.net.ConnectivityManager.NetworkCallback networkCallback;
    private boolean networkRegistered;
    Receiver(Context context, Listener listener) { this.context=context; this.listener=listener; }
    private native String nativeStart(String keyFile, byte[] identity);
    private native void nativeStop();
    void start(Surface surface) {
        control.execute(() -> {
            if (active) return;
            if (!surface.isValid()) { listener.state("Error: surface was destroyed"); return; }
            try {
                playback = new Playback(surface, listener, this::fail);
                WifiManager wifi=(WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if(wifi!=null) {
                    multicast=wifi.createMulticastLock("cinemaair-discovery");
                    multicast.setReferenceCounted(false); multicast.acquire();
                }
                byte[] id=new byte[6]; new SecureRandom().nextBytes(id); id[0]=(byte)((id[0]&0xfc)|2);
                android.content.SharedPreferences prefs=context.getSharedPreferences("identity",0);
                String saved=prefs.getString("id",null);
                if (saved==null) prefs.edit().putString("id",android.util.Base64.encodeToString(id,2)).apply();
                else id=android.util.Base64.decode(saved,2);
                active=true;
                android.net.ConnectivityManager connectivity=(android.net.ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
                networkCallback=new android.net.ConnectivityManager.NetworkCallback() {
                    private android.net.Network current;
                    @Override public void onAvailable(android.net.Network network) {
                        if(current!=null && !current.equals(network)) fail("Network changed. Start receiving again.");
                        current=network;
                    }
                    @Override public void onLost(android.net.Network network) { fail("Network connection lost. Start receiving again after reconnecting."); }
                };
                connectivity.registerDefaultNetworkCallback(networkCallback);networkRegistered=true;
                String error=nativeStart(new java.io.File(context.getFilesDir(),"pairing.key").getAbsolutePath(),id);
                if (error!=null) throw new IllegalStateException(error);
                listener.state("Ready · Cinema Air · 720p / 30 fps requested");
            } catch (Exception e) { release(); listener.state("Error: "+e.getMessage()); }
        });
    }
    void stop() { if (control.isShutdown()) return; control.execute(() -> { release(); listener.state("Stopped"); }); }
    void destroy() { control.execute(this::release); control.shutdown(); }
    private void release() {
        active=false;
        if(networkRegistered) {
            ((android.net.ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE)).unregisterNetworkCallback(networkCallback);
            networkRegistered=false;
        }
        networkCallback=null;
        nativeStop();
        if (playback!=null) { playback.close(); playback=null; }
        if (multicast!=null && multicast.isHeld()) multicast.release();
        multicast=null;
    }
    // Called from joined native worker threads. Never decode on a network thread.
    @SuppressWarnings("unused") public void onPacket(int kind, byte[] data, long timeNs) {
        Playback p=playback;
        if (active && p!=null && !p.offer(kind,data,timeNs)) fail("Playback queue overflow; session stopped");
    }
    @SuppressWarnings("unused") public void onSize(int width,int height) {
        Playback p=playback; if (p!=null) p.dimensions(width,height);
        listener.picture(width,height);
    }
    @SuppressWarnings("unused") public void onEvent(String text) {
        if (!active) return;
        if (text.equals("Session ended")) {
            active=false;
            control.execute(() -> { release(); listener.state("Stopped · sender disconnected. Select Start receiving to reconnect."); });
        } else if (text.startsWith("Error:")) fail(text); else listener.state(text);
    }
    private void fail(String text) {
        if (!active) return;
        active=false;
        control.execute(() -> { release(); listener.state("Error: "+text); });
    }
}
