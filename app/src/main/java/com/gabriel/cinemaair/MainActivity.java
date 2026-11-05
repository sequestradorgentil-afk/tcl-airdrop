// SPDX-License-Identifier: GPL-3.0-or-later
package com.gabriel.cinemaair;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

public final class MainActivity extends Activity implements SurfaceHolder.Callback,Receiver.Listener {
    private Receiver receiver;
    private SurfaceView video;
    private FrameLayout root;
    private ScrollView overlay;
    private TextView status;
    private Button toggle,quality;
    private boolean started,visible,can1080,prefer720;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        // Stable window geometry before creating the decoder Surface; do not resize navigation on first frame.
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);
        video=new SurfaceView(this);video.getHolder().addCallback(this);root.addView(video,new FrameLayout.LayoutParams(-1,-1));
        overlay=new ScrollView(this);overlay.setFillViewport(true);
        overlay.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff182d48,0xff0a1020}));
        root.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(48),dp(24),dp(48),dp(24));overlay.addView(panel,new FrameLayout.LayoutParams(-1,-2));
        TextView brand=text("CINEMA  /  AIR",17);brand.setTextColor(0xff8fc8ff);panel.addView(brand);
        TextView title=text("Your screen. On the big screen.",28);title.setTypeface(null,Typeface.BOLD);panel.addView(title);
        status=text("Stopped",19);panel.addView(status);
        panel.addView(text("Mac or iPhone · Same home network\nControl Center → Screen Mirroring → Cinema Air",17));
        receiver=new Receiver(this,this);
        can1080=Quality.fullHd(getWindowManager().getDefaultDisplay());
        prefer720=getPreferences(0).getBoolean("prefer720",false);
        toggle=button("Start receiving");panel.addView(toggle,buttonParams());
        toggle.setOnClickListener(v->{
            if(started) stop();
            else if(video.getHolder().getSurface().isValid()) {
                started=true;toggle.setText("Stop receiving");quality.setEnabled(false);
                status.setText("Starting local receiver…");boolean hd=can1080 && !prefer720;
                receiver.start(video.getHolder().getSurface(),hd?1920:1280,hd?1080:720);
            } else status.setText("Error: video surface is unavailable");
        });
        quality=button(qualityLabel());quality.setEnabled(can1080);panel.addView(quality,buttonParams());
        quality.setOnClickListener(v->{ prefer720=!prefer720;getPreferences(0).edit().putBoolean("prefer720",prefer720).apply();quality.setText(qualityLabel()); });
        Button diagnostics=button("Diagnostics / last error");panel.addView(diagnostics,buttonParams());
        diagnostics.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Cinema Air diagnostics").setMessage(receiver.diagnostics.summary()).setPositiveButton("Close",null).show());
        panel.addView(text("Auto selects the highest available mode up to 1080p / 30 fps.\n720p remains available. Sender negotiation may differ.\nBack stops reception. Home stops the app’s receiver.",14));
        toggle.requestFocus();
    }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(410),dp(60));p.topMargin=dp(8);return p;
    }
    private String qualityLabel() { return prefer720?"Quality: 720p / 30 fps":"Quality: Auto · "+(can1080?"1080p":"720p")+" / 30 fps"; }
    private Button button(String label) {
        Button b=new Button(this);b.setText(label);b.setTextSize(16);b.setAllCaps(false);
        b.setOnFocusChangeListener((v,focused)->{
            GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(dp(14));bg.setColor(focused?0xffbedfff:0xff33465f);
            bg.setStroke(dp(focused?3:1),focused?Color.WHITE:0xff60748a);b.setBackground(bg);b.setTextColor(focused?0xff0a1020:Color.WHITE);
        });return b;
    }
    private TextView text(String value,int size) { TextView v=new TextView(this);v.setText(value);v.setTextColor(Color.WHITE);v.setTextSize(size);v.setPadding(0,dp(5),0,dp(5));return v; }
    private void stop() {
        started=false;if(receiver!=null) receiver.stop();
        if(toggle!=null) toggle.setText("Start receiving");
        if(quality!=null) quality.setEnabled(can1080);
        if(overlay!=null) overlay.setVisibility(View.VISIBLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    @Override public void state(String text) {
        runOnUiThread(()->{
            if(!visible && !text.startsWith("Stopped")) return;
            status.setText(text);
            if(text.startsWith("Receiving video")) { overlay.setVisibility(View.GONE);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); }
            else {
                overlay.setVisibility(View.VISIBLE);
                if(text.startsWith("Receiving audio")) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                if(text.startsWith("Error") || text.startsWith("Stopped")) {
                    started=false;toggle.setText("Start receiving");quality.setEnabled(can1080);
                }
            }
        });
    }
    @Override public void picture(int w,int h) {
        if(w<=0 || h<=0) return;
        runOnUiThread(()->root.post(()->{
            int rw=root.getWidth(),rh=root.getHeight();float scale=Math.min((float)rw/w,(float)rh/h);
            video.setLayoutParams(new FrameLayout.LayoutParams(Math.round(w*scale),Math.round(h*scale),Gravity.CENTER));
        }));
    }
    @Override public void onBackPressed() { if(started) stop();else super.onBackPressed(); }
    @Override protected void onStart() { super.onStart();visible=true; }
    @Override protected void onStop() { visible=false;stop();super.onStop(); }
    @Override protected void onDestroy() { receiver.destroy();super.onDestroy(); }
    @Override public void surfaceCreated(SurfaceHolder holder) {}
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int w,int h) {}
    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        if(started) receiver.diagnostics.failure("E_SURFACE","Video Surface was destroyed during reception");
        stop();
    }
}
