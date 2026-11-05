# Mac → TV screen mirroring

The Android receiver uses UxPlay's shared iPhone/macOS protocol core. No separate Mac application is required to send a desktop through macOS's native Screen Mirroring control. This Android integration has not demonstrated Mac mirroring or synchronized audio.

## First test

Install the APK from the extracted Downloads folder:

```bash
cd "$HOME/Downloads/CinemaAir-experimental"
TV_LAUNCHER_DIR="$HOME/Downloads" bash scripts/install-tv.sh 192.168.15.3:5555
```

Substitute the current TV address if it changed. This installs/updates only Cinema Air and opens its TV activity. The explicit log destination also works with the originally downloaded helper version.

1. Select **Start receiving** on TV. If a decoder error appears, report its exact text before attempting a sender connection.
2. On macOS versions with Control Center, choose **Control Center → Screen Mirroring → Cinema Air**. On other versions, use the native AirPlay display control available in Displays preferences/menu bar.
3. Enter a PIN if the TV displays one. Do not share PINs, pairing keys or private screen contents.
4. Select built-in-display mirroring if offered a separate-display option. Confirm actual desktop frames appear before testing audio.
5. Play a short unprotected video. Record audible TV audio and synchronization separately from successful video reception.
6. Stop through the Mac's Screen Mirroring control. Select **Start receiving** on TV again before a reconnect or an iPhone test. Test one sender at a time.

Audio-only Sound output selection is not a substitute for screen mirroring. ALAC/PCM playback and AirPlay 2 multiroom are unsupported. Mac extended-display behavior and protected streaming content are not claimed.

## Compatibility risks

Upstream UxPlay documents macOS mirror-mode AAC audio separately from audio-only ALAC. That is evidence of the shared protocol core's intended use, not a validation of Android playback or every macOS version.

This port advertises only implemented AAC formats. Published receiver notes associate some macOS pairing behavior with PCM/ALAC feature flags. Those flags remain disabled because their playback paths are absent. Whether a given macOS sender accepts the narrower capability set is unknown. If Mac discovery or negotiation fails, additional codec integration may be necessary; do not enable unsupported feature bits merely to make discovery appear successful.

## Separate Mac validation record — unrun

Track discovery, PIN/negotiation, actual desktop frames, audio and synchronization individually. Then test resolution/aspect ratio, unprotected full-screen video, disconnect/reconnect, Back/Home, network interruption and a competing sender. Measure TV CPU/RAM, frame drops and latency before making performance claims. Keep Mac results distinct from iPhone results.

Initially report only the failed stage and exact app error in chat. Device-specific diagnostics and sender OS versions do not need to be included in public source or release bundles.
