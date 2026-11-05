# Test record — 2026-11-05

Environment: managed Linux x86_64 cloud machine; no access to the user's home TV or iPhone. This record describes actual executed checks, not physical interoperability.

| Check | Outcome |
|---|---|
| Native protocol + JNI cross-compilation, NDK r27c / API 28 / armeabi-v7a | Passed |
| Android debug APK assembly + lintDebug | Passed (before final lifecycle/parser refinements) |
| Final signed release candidate | See delivery verification below; not device-installed |
| `bounded-http` native test | Passed: fragmented RTSP, interrupted/truncated body, malformed Content-Length, size_t overflow, cumulative 1 MiB request budget, AVC configuration truncation and oversized SPS/PPS |
| `rtsp-start-stop` native integration | Passed: three actual socket listen/OPTIONS/binary-plist info/disconnect/stop cycles, checking AAC-only format masks and no PCM/ALAC feature bits with a persisted generated pairing identity; not a sender pairing test |
| AddressSanitizer + UndefinedBehaviorSanitizer with leak detection | Both native tests passed; no reported sanitizer errors in those exercised paths |
| Mac helper behavioral tests | Six passed on Linux Bash using a fake ADB executable; install scope, path with spaces, wrong model, unauthorized device, altered checksum, failed-install exit status and stop/Home routing checked |
| TV ABI/decoder/display/memory inspection | Unrun; waiting for Mac diagnostics |
| Android 9 UI/lifecycle/device decoder tests | Unrun; no Android device/emulator configured, no `/dev/kvm` available for accelerated emulator |
| iPhone 16e / iOS 27 discovery, PIN, real frames, audio/sync, rotation | Unrun |
| Reconnection, second sender, network interruption on TV | Unrun |
| Actual TV CPU/RAM/frame drops/latency | Unmeasured |
| HDMI, streaming-app, remote/Bluetooth and screensaver regression checks | Unrun on TV |

No zero-test run is reported as validation. Host tests exercise native library behavior and parser limits; they do not test Java MediaCodec/AudioTrack, Android lifecycle, encrypted media transport with a real sender, or mDNS interoperability. The fake-ADB tests are helper validation, not real Mac/TV installation tests. Initial build/test failures (missing math link and incorrect expectation for upstream's successful start return value) were diagnosed and corrected before the passing native test runs.

Required next step: collect the TV ABI/codec diagnostics, then use the physical checklist in TV-TESTING.md. A real mirrored frame and audible synchronized audio are required before declaring receiver success. Negotiation or decoding failures on iOS 27 may require further source changes.

## Final delivery verification

Final release assembly and lintRelease passed (0 errors; 8 non-fatal lint warnings: hardcoded English UI strings, Chrome OS ABI coverage and modern backup configuration). No warning was disabled. APK alignment and APK Signature Scheme v3 verification for Android API 28 passed. ELF32 ARM native packaging and APK ZIP integrity passed. The linked crypto archive was built from pinned OpenSSL 3.5.9. Both native tests passed again under ASan/UBSan after capability refinements. Build/signing identity and hashes are in BUILD-INFO.json. No physical device success is implied.

APK SHA-256: `32c30ef2d51a3ece9646379cb13a0ac8493a0b2bb3cee1323ad2d4c964733c78`.
