// SPDX-License-Identifier: GPL-3.0-or-later
package com.gabriel.cinemaair;
import android.media.*;
import android.view.Display;
final class Quality {
    static String decoder(int width,int height) {
        for(MediaCodecInfo info:new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if(info.isEncoder()) continue;
            String name=info.getName();
            if(name.startsWith("OMX.google.") || name.startsWith("c2.android.") || name.startsWith("c2.google.")) continue;
            try {
                MediaCodecInfo.VideoCapabilities caps=info.getCapabilitiesForType("video/avc").getVideoCapabilities();
                if(caps!=null && caps.areSizeAndRateSupported(width,height,30)) return name;
            } catch(RuntimeException ignored) {}
        }
        return null;
    }
    static boolean fullHd(Display display) {
        if(decoder(1920,1080)==null) return false;
        for(Display.Mode mode:display.getSupportedModes())
            if(Math.max(mode.getPhysicalWidth(),mode.getPhysicalHeight())>=1920 &&
               Math.min(mode.getPhysicalWidth(),mode.getPhysicalHeight())>=1080) return true;
        return false;
    }
}
