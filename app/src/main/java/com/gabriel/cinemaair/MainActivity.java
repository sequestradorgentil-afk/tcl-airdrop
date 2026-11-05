// SPDX-License-Identifier: GPL-3.0-or-later
package com.gabriel.cinemaair;

import android.app.Activity;
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
    private LinearLayout panel;
    private TextView status;
    private Button toggle;
    private boolean started,visible;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);
        video=new SurfaceView(this);video.getHolder().addCallback(this);
        root.addView(video,new FrameLayout.LayoutParams(-1,-1));
        panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(64,40,64,40);
        panel.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff182d48,0xff0a1020}));
        root.addView(panel,new FrameLayout.LayoutParams(-1,-1));
        TextView brand=text("CINEMA  /  AIR",17);brand.setTextColor(0xff8fc8ff);panel.addView(brand);
        TextView title=text("Your screen. On the big screen.",32);title.setTypeface(null,Typeface.BOLD);panel.addView(title);
        status=text("Stopped",20);status.setPadding(0,24,0,20);panel.addView(status);
        panel.addView(text("Experimental receiver · iPhone 16e / iOS 27 not yet verified\nSame home network → Control Center → Screen Mirroring → Cinema Air",18));
        toggle=new Button(this);toggle.setText("Start receiving");toggle.setAllCaps(false);
        LinearLayout.LayoutParams buttonParams=new LinearLayout.LayoutParams(330,72);buttonParams.topMargin=24;panel.addView(toggle,buttonParams);
        toggle.setOnFocusChangeListener((v,focused) -> {
            GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(20);bg.setColor(focused?0xffbedfff:0xff33465f);
            bg.setStroke(focused?4:1,focused?Color.WHITE:0xff60748a);v.setBackground(bg);toggle.setTextColor(focused?0xff0a1020:Color.WHITE);
        });
        toggle.setOnClickListener(v -> {
            if(started) stop();
            else if(video.getHolder().getSurface().isValid()) {
                started=true;toggle.setText("Stop receiving");status.setText("Connecting · starting local receiver…");receiver.start(video.getHolder().getSurface());
            } else status.setText("Error: video surface is unavailable");
        });
        panel.addView(text("Quality: 720p / 30 fps requested. Sender may negotiate differently.\nBack stops the session. Home stops reception. No background or boot reception.\nProtected streaming content may not mirror.",15));
        toggle.requestFocus();
        receiver=new Receiver(this,this);
    }
    private TextView text(String value,int size) { TextView v=new TextView(this);v.setText(value);v.setTextColor(Color.WHITE);v.setTextSize(size);v.setPadding(0,8,0,8);return v; }
    private void stop() {
        started=false;receiver.stop();toggle.setText("Start receiving");panel.setVisibility(View.VISIBLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    @Override public void state(String text) {
        runOnUiThread(() -> {
            if(!visible && !text.startsWith("Stopped")) return;
            status.setText(text);
            if(text.startsWith("Receiving")) {
                panel.setVisibility(View.GONE);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            } else {
                panel.setVisibility(View.VISIBLE);getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                if(text.startsWith("Error") || text.startsWith("Stopped")) { started=false;toggle.setText("Start receiving"); }
            }
        });
    }
    @Override public void picture(int w,int h) {
        if(w<=0 || h<=0) return;
        runOnUiThread(() -> root.post(() -> {
            int rw=root.getWidth(),rh=root.getHeight();float scale=Math.min((float)rw/w,(float)rh/h);
            FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(Math.round(w*scale),Math.round(h*scale),Gravity.CENTER);video.setLayoutParams(p);
        }));
    }
    @Override public void onBackPressed() { if(started) stop();else super.onBackPressed(); }
    @Override protected void onStart() { super.onStart();visible=true; }
    @Override protected void onStop() { visible=false;stop();super.onStop(); }
    @Override protected void onDestroy() { receiver.destroy();super.onDestroy(); }
    @Override public void surfaceCreated(SurfaceHolder holder) {}
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int w,int h) {}
    @Override public void surfaceDestroyed(SurfaceHolder holder) { stop(); }
}
