# Compatibility and limits

Status: experimental, unverified port; not Apple certified. Apple provides no public Android AirPlay receiver SDK. This is a port/wrapper around open-source protocol code, not an original clean-room AirPlay implementation.

| Capability | Code path | Validation |
|---|---|---|
| Local `_airplay._tcp` / `_raop._tcp` discovery | UxPlay internal mDNS backend, Android multicast lock | TV/iPhone discovery untested |
| Pairing and normal session encryption | UxPlay/ShairPlay/PlayFair/OpenSSL | Not interoperability or security tested |
| Screen mirroring | Native decrypted H.264 Annex B → JNI → vendor MediaCodec Surface | Not demonstrated |
| Mirroring audio | AAC-ELD/AAC-LC → Android MediaCodec; ALAC → minimal FFmpeg; PCM → stereo 16-bit AudioTrack | Not demonstrated; TV AAC-ELD availability unknown |
| A/V timing | Shared NTP-derived monotonic deadlines, 150 ms playback budget, AudioTrack queued-frame estimate | Sync/latency/drift unmeasured |
| Legacy standalone audio | PCM or ALAC, stereo 44.1 kHz / 16-bit | Decoder unit-tested; physical reception unverified |
| Individual-app media/HLS | Disabled, explicit error for attempted HLS | No support claimed |
| AirPlay 2 multiroom / PTP / buffered audio | Disabled | No support |
| HEVC/AV1 / DRM bypass | Disabled / absent | No support |

The user's sender is **iPhone 16e, iOS 27**, as reported. Upstream README descriptions of legacy iOS support (including older explicit iOS 17 text) do not establish iOS 27 compatibility. No sender version has been tested with Cinema Air.

Target supplied: TCL BeyondTV2, Android 9/API 28, firmware V8-R851T02-LF1V660.019318, 2 GiB RAM. These values are user reports, not cloud observations. CPU ABI, decoder profiles, display modes and free memory are still unverified. The shipped 32-bit ARM binary is a candidate, **not a verified TV-ABI build**. Do not install if `armeabi-v7a` is absent from the device's supported ABI list. An ARM64-only target needs another build.

Auto requests 1920×1080 at 30 fps when display modes and MediaCodec VideoCapabilities report support; otherwise it requests 1280×720 at 30 fps. The 720p option remains available. These are sender requests, not proof of actual negotiated resolution. 1080p has passed native profile/metadata tests, not a physical TV performance test. The current application maximum is 1080p/30; this does not establish the panel's absolute maximum. Decoder output cropping and fit-center Surface layout handle aspect ratio; SPS dimension changes reconfigure playback. Orientation changes remain device-untested.

Start/stop is explicit; no background/boot receiving. Video-only teardown releases video without stopping continuing audio or the listener. Network loss/fatal decoder errors still require Start receiving again. Automatic seamless reconnect is **not validated**. Audio-only idle output releases after three seconds without data; reception remains armed until Stop/Back/Home. The native admission callback rejects a different device identifier during an active reception interval, but multi-connection/multi-sender behavior remains untested. Reported sender identity is not a trustworthy authentication mechanism.

PIN setup uses the upstream flow. Cached pairing follows upstream's default without a persistent client allowlist. PIN enforcement, stale registrations, rejected senders and key persistence require physical testing; **do not rely on this port as a hardened access-control boundary**. No secrets or private screen frames are intentionally logged; no recording is enabled.

Compressed Java queues are bounded to 48 packets / 8 MiB; video payload and decoder input budgets are 2 MiB, HTTP/RTSP requests 1 MiB. Decoded audio is bounded to 64 blocks / 256 KiB, each block at most 16 KiB; ALAC encoded input is at most 64 KiB and output at most 352 stereo frames. FFmpeg allocation size is capped at 4 MiB per allocation. Temporary decoder input waits are retried for up to 250 ms while draining both outputs. Oversized packets terminate the session. Native SPS/PPS and signed lengths are checked before parsing. These checks are not an exhaustive audit or complete malformed-media fuzz suite. Inherited native C still needs further security review.

Audio scheduling is an initial implementation. Hardware audio-output latency, clock drift, lost packets, late frames, interruption recovery and vendor decoder behavior need TV measurements. No smoothness, low-latency, performance or protected-content compatibility claim is made. Netflix/Apple TV/Prime Video may show black video or refuse mirroring; use the existing native streaming apps for protected playback.

Permissions: INTERNET for AirPlay's LAN sockets; ACCESS_NETWORK_STATE to stop after network loss; CHANGE_WIFI_MULTICAST_STATE for local mDNS reception. Native AirPlay uses protocol-required plain control traffic and encrypted session payloads, not a TLS relay. No storage, location, microphone, camera, accessibility, boot, settings-write, or foreground-service permission. Keep-screen-on is set only after video or audio output begins and cleared on stop/background/error. There is no Wi-Fi awake lock. Existing screensaver and idle settings are untouched.

Diagnostics persist the last known failure locally and emit only fixed error codes and numeric counters to the app-specific log tag. Video Surface geometry is established before decoding; source dimensions travel with each queued packet. No square 1920×1920 decoder reservation is made for a smaller stream. The audio-only mode remains foreground-only, so leaving the app stops reception. Active audio keeps this app’s window awake; three seconds of audio-only idle releases output and clears that window flag, allowing normal idle/screensaver behavior without changing global settings. PCM/ALAC at 48 kHz, 24-bit or other channel layouts is not advertised or implemented.
