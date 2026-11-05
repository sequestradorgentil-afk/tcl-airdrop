# Cinema Air — experimental Android TV receiver

**Not a demonstrated working AirPlay receiver yet.** This is a first native Android port of UxPlay's protocol core, with real network/session/encryption code and a MediaCodec/AudioTrack playback path. No iPhone-to-TV session has been tested. Do not treat APK assembly, discovery, or the native RTSP tests as proof of screen mirroring or synchronized audio.

Package `com.gabriel.cinemaair` installs alongside Cinema Home. No boot receiver, launcher replacement, background reception, cloud relay, account login, session recording, or paid service. Requires Android 9 or later. Current candidate APK contains **armeabi-v7a only**, pending the actual TV ABI report. The installer refuses a model/API/ABI mismatch.

## Mac destination

Download and extract `CinemaAir-experimental.zip` under **`~/Documents/TV LAUNCHER`**. The extracted directory is `CinemaAir-experimental`. All helper logs default to `~/Documents/TV LAUNCHER/logs`. Set `TV_LAUNCHER_DIR` only to intentionally choose another log destination.

```bash
mkdir -p "$HOME/Documents/TV LAUNCHER"
cd "$HOME/Documents/TV LAUNCHER/CinemaAir-experimental"
bash scripts/inspect-tv.sh 192.168.15.3:5555
# Read the experimental limitations before installing:
bash scripts/install-tv.sh 192.168.15.3:5555
```

Use the current TV IP in Settings → Network; the old address may have changed. These scripts require `$HOME/Downloads/platform-tools/adb`. Inspection is read-only. Installation verifies the bundled APK SHA-256, checks BeyondTV2/API 28/armeabi-v7a, installs or updates only this package, and opens its activity. It never uninstalls to resolve signature conflicts. Choose **Start receiving** using the remote. On the iPhone: same LAN → Control Center → Screen Mirroring → Cinema Air. A PIN may be displayed by the upstream pairing flow. Use only a trusted home network; access control is unverified.

Back stops a session. Home stops reception. To stop from the Mac and explicitly return to Cinema Home:

```bash
bash scripts/stop-tv.sh 192.168.15.3:5555
```

See [compatibility and outstanding work](docs/COMPATIBILITY.md), [TV validation and recovery](docs/TV-TESTING.md), [build instructions](docs/BUILD.md), [research and dependencies](docs/RESEARCH.md), and [test record](docs/TEST-RESULTS.md). The physical test checklist is required before calling this app finished.
