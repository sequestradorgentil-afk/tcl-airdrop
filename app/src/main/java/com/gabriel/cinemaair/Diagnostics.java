// SPDX-License-Identifier: GPL-3.0-or-later
package com.gabriel.cinemaair;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
final class Diagnostics {
    private final SharedPreferences prefs;
    private final String version;
    private volatile String counters="No playback counters yet.";
    Diagnostics(Context c) {
        prefs=c.getSharedPreferences("diagnostics",0);
        String name;
        try { name=c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName; }
        catch(android.content.pm.PackageManager.NameNotFoundException e) { name="unknown"; }
        version=name;
    }
    void failure(String code,String detail) {
        // Store known error descriptions locally; log only fixed codes and numeric counters.
        prefs.edit().putString("failure",code+" · "+detail).apply();
        Log.i("CinemaAir","FAIL "+code+" "+counters);
    }
    void stats(String value) { counters=value; }
    void event(String code) { Log.i("CinemaAir",code); }
    String summary() { return "Cinema Air "+version+"\nLast recorded error: "+prefs.getString("failure","None recorded")+"\n\n"+counters; }
}
