# Cinema Air — experimental Android TV receiver

**Experimental receiver; sustained mirroring and audio interoperability need physical validation.** This is a first native Android port of UxPlay's protocol core, with real network/session/encryption code, MediaCodec video/AAC, FFmpeg ALAC decoding, and AudioTrack output. No iPhone-to-TV session has been tested by this project. Do not treat APK assembly, discovery, or the native RTSP tests as proof of screen mirroring or synchronized audio.

Package `com.gabriel.cinemaair` installs alongside Cinema Home. No boot receiver, launcher replacement, background reception, cloud relay, account login, session recording, or paid service. Requires Android 9 or later. Current candidate APK contains **armeabi-v7a only**, pending the actual TV ABI report. The installer refuses a model/API/ABI mismatch.

## Mac destination

Download and extract `CinemaAir-experimental.zip` under **`~/Downloads`**. The version 0.2 extracted directory is `CinemaAir-0.2-experimental`. All helper logs default to `~/Downloads/logs`. Set `TV_LAUNCHER_DIR` only to intentionally choose another log destination.

```bash
mkdir -p "$HOME/Downloads"
cd "$HOME/Downloads/CinemaAir-0.2-experimental"
bash scripts/inspect-tv.sh 192.168.15.3:5555
# Read the experimental limitations before installing:
bash scripts/install-tv.sh 192.168.15.3:5555
```

Use the current TV IP in Settings → Network; the old address may have changed. These scripts require `$HOME/Downloads/platform-tools/adb`. Inspection is read-only. Installation verifies the bundled APK SHA-256, checks BeyondTV2/API 28/armeabi-v7a, installs or updates only this package, and opens its activity. It never uninstalls to resolve signature conflicts. Choose **Start receiving** using the remote. On the iPhone: same LAN → Control Center → Screen Mirroring → Cinema Air. A PIN may be displayed by the upstream pairing flow. Use only a trusted home network; access control is unverified.

## Mac → TV screen mirroring

The same experimental APK is intended to receive Mac and iPhone screen mirroring. On macOS versions with Control Center, start reception on the TV, then choose **Control Center → Screen Mirroring → Cinema Air**. Use built-in-display mirroring if offered a separate-display option. No commercial sender app is required.

Mac discovery, pairing, desktop frames and audio synchronization are **untested** in this port. Legacy stereo 44.1 kHz / 16-bit PCM and ALAC audio-only reception now has a code path; physical interoperability remains unverified. AirPlay 2 multiroom remains unsupported. Read [Mac testing and limitations](docs/MAC-AIRPLAY.md), and test only one sender at a time.

Back stops a session. Home stops reception. To stop from the Mac and explicitly return to Cinema Home:

```bash
bash scripts/stop-tv.sh 192.168.15.3:5555
```

See [compatibility and outstanding work](docs/COMPATIBILITY.md), [TV validation and recovery](docs/TV-TESTING.md), [build instructions](docs/BUILD.md), [research and dependencies](docs/RESEARCH.md), and [test record](docs/TEST-RESULTS.md). The physical test checklist is required before calling this app finished.

## Version 0.2: quality, audio and stop diagnostics

Auto quality now requests **1080p/30** when the display and vendor H.264 decoder report support, otherwise 720p/30. The remote-friendly Quality button retains a 720p fallback. This is the receiver's current maximum; 4K/60 fps and the panel's absolute maximum are not claimed. The sender may negotiate a different actual mode.

Normal codec backpressure is retried for up to 250 ms while both output paths are drained. Video stream teardown no longer stops continuing audio. ALAC and PCM support add the corresponding protocol capabilities only for the implemented 44.1 kHz/stereo/16-bit formats. The app contains no content-title filter or protected-streaming toggle; removing the old warning text does not alter Apple's DRM.

If a session still ends, open **Diagnostics / last error** on the TV and report the error code. Counters include video buffers submitted for rendering, late drops, decoded audio frames and input retries; they do not measure end-to-end latency. Optional local Mac capture:

```bash
bash scripts/diagnose-tv.sh 192.168.15.3:5555
```

This captures only the app's fixed-code/numeric `CinemaAir` log tag and its memory report under `~/Downloads/logs`. No pairing PINs, keys, sender names or screen contents are sent to that tag. Review local diagnostics before sharing; they are never automatically uploaded.
