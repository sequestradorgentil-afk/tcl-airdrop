# Mac → TV screen mirroring

The Android receiver uses UxPlay's shared iPhone/macOS protocol core. No separate Mac application is required to send a desktop through macOS's native Screen Mirroring control. This Android integration has not demonstrated Mac mirroring or synchronized audio.

## First test

Install the APK from the extracted Downloads folder:

```bash
cd "$HOME/Downloads/CinemaAir-0.2-experimental"
TV_LAUNCHER_DIR="$HOME/Downloads" bash scripts/install-tv.sh 192.168.15.3:5555
```

Substitute the current TV address if it changed. This installs/updates only Cinema Air and opens its TV activity. The explicit log destination also works with the originally downloaded helper version.

1. Select **Start receiving** on TV. If a decoder error appears, report its exact text before attempting a sender connection.
2. On macOS versions with Control Center, choose **Control Center → Screen Mirroring → Cinema Air**. On other versions, use the native AirPlay display control available in Displays preferences/menu bar.
3. Enter a PIN if the TV displays one. Do not share PINs, pairing keys or private screen contents.
4. Select built-in-display mirroring if offered a separate-display option. Confirm actual desktop frames appear before testing audio.
5. Play a short unprotected video. Record audible TV audio and synchronization separately from successful video reception.
6. Stop through the Mac's Screen Mirroring control. Select **Start receiving** on TV again before a reconnect or an iPhone test. Test one sender at a time.

Audio-only Sound output selection is not a substitute for screen mirroring. Legacy ALAC/PCM stereo 44.1 kHz / 16-bit playback now has an implementation; other rates/depths and AirPlay 2 multiroom remain unsupported. Mac extended-display behavior and protected streaming content are not claimed.

## Compatibility risks

Upstream UxPlay documents macOS mirror-mode AAC audio separately from audio-only ALAC. That is evidence of the shared protocol core's intended use, not a validation of Android playback or every macOS version.

Version 0.2 advertises PCM, ALAC, AAC-LC and AAC-ELD only for its implemented 44.1 kHz/stereo paths. ALAC/PCM are 16-bit. A minimal LGPL FFmpeg ALAC decoder was added; Android continues to decode AAC and H.264. Audio routing changes must be tested separately from mirroring. Video teardown must not end continuing audio, and normal temporary decoder input waits are retried rather than treated as an immediate fatal error. Those are source-level fixes, not evidence of physical Apple Music playback or A/V synchronization.

Auto quality requests up to 1080p/30 only when the display and H.264 decoder report support. Use 720p to compare stability if necessary. Read Diagnostics / last error after a stop; optional `scripts/diagnose-tv.sh` saves app-only diagnostic events locally. Content restrictions from the sender/service still apply; the app has no protected-streaming filter to turn off.

## Separate Mac validation record — unrun

Track discovery, PIN/negotiation, actual desktop frames, audio and synchronization individually. Then test resolution/aspect ratio, unprotected full-screen video, disconnect/reconnect, Back/Home, network interruption and a competing sender. Measure TV CPU/RAM, frame drops and latency before making performance claims. Keep Mac results distinct from iPhone results.

Initially report only the failed stage and exact app error in chat. Device-specific diagnostics and sender OS versions do not need to be included in public source or release bundles.
