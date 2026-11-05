# Physical-TV validation and recovery

No cloud access to the user's home LAN or iPhone is configured. Physical results must be collected on the Mac/TV; never expose ADB or receiver ports to the Internet.

## Temporary ADB setup

1. On the TV, check its current local IP. Keep the Mac and TV on the trusted home LAN.
2. If debugging is off, enable Developer options through Settings → Device Preferences → About → Build (press repeatedly), then enable the vendor's USB/network debugging option temporarily. Names vary with firmware. This is the only settings change needed for installation. Do not change launcher, power, GPU, HDMI or other developer performance settings. If the firmware has no network-debugging option, do not guess shell commands or expose a port; report the available options.
3. Connect with `$HOME/Downloads/platform-tools/adb connect <current-private-IP>:5555`; authorize the Mac fingerprint on the TV. Use `bash scripts/inspect-tv.sh <IP>:5555` from the extracted package. It checks the supplied model/API and writes read-only property/display/codec/memory diagnostics under `~/Downloads/logs`.
4. Confirm the ABI list includes `armeabi-v7a` before trying the candidate APK. Share that list first. Some firmware does not expose a `media.codec` dumpsys service. Its absence is a diagnostic limitation, not proof of a decoder failure; the app separately checks MediaCodecList for the chosen AVC mode and AAC-ELD before advertising. An actual decode test is still necessary.
5. Read the experimental limitations, then use `bash scripts/install-tv.sh <IP>:5555`. It installs only `com.gabriel.cinemaair`, not a launcher. No app data is cleared. Existing Cinema Home, TV Detox, original launcher state and essential streaming apps are untouched.

## Required test record (all physical items currently unrun)

Record iPhone **16e / exact iOS 27 build**, TV model/firmware/API/ABIs, network type, codec names/profiles and display mode. Then record each item separately:

- Start/stop reception ten times, including Back and Home, without sender connections; check no discovery/socket/lock leak.
- iPhone Control Center discovers Cinema Air; record discovery separately from connection success.
- PIN/connection negotiation completes, including cached pairing/rejected sender behavior. Do not share PINs, keys or pairing records.
- Real iPhone home-screen frames appear. No test bitmap, synthetic frame or discovery-only result counts.
- Play unprotected video with a visible synchronization clap or frame counter; verify audible audio, lip sync and drift over at least ten minutes. Record approximate end-to-end delay using an external camera if possible; no in-app recording is needed.
- Rotate portrait ↔ landscape repeatedly; verify aspect ratio, cropping and decoder recovery.
- Disconnect from the iPhone, select Start receiving again on TV, then reconnect. Video-only teardown now leaves audio and the listener running; test both a video-to-audio transition and a full sender disconnect. Stop/Start reception before switching sender identity.
- Test a second sender during an active session; inspect that it does not corrupt the first session. No multiroom support is claimed.
- Interrupt/reconnect Wi-Fi or Ethernet, destroy/recreate the activity, background/foreground, and kill/restart the process. The app should stop and release resources, requiring explicit Start again.
- Back returns to receiver controls; Home returns to Cinema Home. `scripts/stop-tv.sh` force-stops only Cinema Air and explicitly launches `com.gabriel.cinemahome/.MainActivity`.
- Measure idle/active memory (`adb -s <TV> shell dumpsys meminfo com.gabriel.cinemaair`) and CPU (`adb -s <TV> shell dumpsys cpuinfo`); gather frame drops/latency through device instrumentation before claiming performance. Diagnostics now count submitted video buffers, known late-buffer drops, audio frames and input retries. These are not full display-drop or end-to-end latency measurements.
- Verify HDMI inputs, tuner, remote, Bluetooth, audio/display, screensaver and normal YouTube/Netflix/Apple TV/Prime Video remain functional. Protected video may refuse mirroring; that is not a request to bypass DRM.

## Actual diagnosed failure modes

- Cloud Git initially could not reach the proxy under the shell sandbox. The authorized network execution path succeeded; no Git token or TLS bypass was needed.
- Initial native host integration test needed explicit libm linking; fixed. Server-start success returns 1 in this upstream, not 0; the test was corrected to its documented implementation outcome.
- The inherited mirror parser lacked early SPS/PPS bounds checks; Cinema Air now rejects malformed/oversized AVC configuration before field reads. This does not establish exhaustive fuzz coverage.
- Missing AAC-ELD or hardware AVC decoder: the app reports an error before advertisement. Share decoder diagnostics; do not assume a software video fallback exists.
- App not discovered: confirm same subnet, disable router client isolation only if appropriate for your own network, check multicast support and wired/Wi-Fi routing. Do not open public ports. Upstream's new internal mDNS backend is experimental and may need Android-specific fixes.
- Session immediately errors: preserve only the on-screen error and non-secret diagnostics; iOS 27 negotiation, timestamps or vendor codec behavior may require source changes. No interoperability success is implied.
- APK signature mismatch: keep the installed app; locate the original key. No helper automatically uninstalls or clears data.

## Cleanup and undo

Stop reception with Back/Home or `scripts/stop-tv.sh`. Rebooting the TV does not start the receiver. To remove **only Cinema Air**, deliberately run:

```bash
"$HOME/Downloads/platform-tools/adb" -s 192.168.15.3:5555 uninstall com.gabriel.cinemaair
```

This explicit removal deletes Cinema Air's app-private pairing identity/data. It does not remove Cinema Home or other apps. Substitute the current TV address. The install script never runs this command.

After testing, turn off the TV's USB/network/wireless debugging option again and disconnect the Mac with `adb disconnect <IP>:5555`. If your firmware offers revoking debugging authorizations, use it if desired. Verify a fresh ADB connection is refused. Turning off ADB does not prevent normal LAN receiver operation; the Mac is not needed after installation. Existing default launcher, original disabled launcher state, idle timeouts, screensaver, animations, input and power settings are never changed by these helpers.

## Version 0.2 regression checks

Install over the previous APK with the same signing identity. Keep a baseline test in 720p, then compare Auto/1080p if available. Test ordinary desktop mirroring, start/stop local music, switch between mirroring and audio-only routing, mute/change sender volume, and pause/resume audio. A video stream ending must not stop ongoing audio. Verify Diagnostics reports version 0.2 and read its persistent failure code if a stop recurs. Use `scripts/diagnose-tv.sh` for a local app-tag-only capture; it never uploads logs. Screen mirroring, audio-only reception, audio synchronization and protected-content policy are separate outcomes. No DRM bypass is implemented.
