# Cloud validation record — version 0.2-experimental

These are executed cloud checks, not a physical TV/sender certification. User device diagnostics and sender OS-version reports are not included in this public record.

| Check | Outcome |
|---|---|
| Native RTSP parser limits | Passed: fragmented requests, truncated body, malformed length, integer overflow, cumulative allocation limit, AVC configuration bounds |
| Real local RTSP server integration | Passed: three start/OPTIONS/binary-plist info/disconnect/stop cycles; PCM/ALAC/AAC format masks; 720p/1080p @30 requests; reject unsupported 4K profile |
| Real ALAC decoder | Passed: synthetic 352-frame stereo packet decoded to bit-exact original PCM; flush/reuse; malformed/truncated input and input/output budgets |
| ASan/UBSan/leak checks | All three native tests passed with halt-on-error; FFmpeg host audio decoder and wrapper instrumented |
| Decoder input retry regression | Passed: transient backpressure lasting beyond the old 10 ms cutoff, output service while waiting, bounded sustained stall and stop cancellation |
| Mac helper tests | Seven passed using a fake ADB: install scope, destination with spaces, wrong model, unauthorized target, bad checksum, install exit status, stop/Home, optional missing codec-service diagnostics |
| Android release assembly/lint | Passed during development; final artifact verification is recorded in BUILD-INFO.json and below |
| Physical 1080p decoding / sustained video / ALAC/PCM output | Unrun by the cloud agent |
| Apple Music interoperability / audio-video sync / drift | Unrun by the cloud agent |
| iPhone interoperability / orientation | Unrun by the cloud agent |
| Android lifecycle, Surface recreation and vendor resource release | Code changes built; device regression test still required |
| TV CPU/RAM, actual display drops and end-to-end latency | Unmeasured by the cloud agent |

Sanitizer configure initially failed because LeakSanitizer cannot run under the shell ptrace sandbox. Building and testing in the authorized execution path without ptrace preserved all sanitizer checks. The final native runs used address/leak checking and UBSan halt-on-error; no errors were reported in the exercised paths. The ALAC test validates codec output, not encrypted AirPlay transport or physical audio hardware.

The retry test uses the same platform-independent CodecInput class as Android playback and a deterministic fake clock/source. It does not simulate a complete Android decoder. Fake-ADB tests run on Linux Bash, not on a physical Mac/TV. No zero-test run is counted as validation.

Version 0.2 fixes source-level failure paths: immediate teardown after one input wait; overly tight startup audio buffering; video teardown stopping continuing audio; shared dimensions applied to older queued frames; unnecessary square decoder reservations; disappearing error information. These are not a claim that a particular reported session failure has been conclusively diagnosed or that all playback errors are fixed.

To finish validation, follow TV-TESTING.md and MAC-AIRPLAY.md. Record video, audio, synchronization, resolution and reconnect separately. If a failure recurs, use Diagnostics / last error and local app-only event capture rather than uploading broad system logs.

## Final artifact verification

Final release assembly and lintRelease passed (0 errors, 7 warnings; no warning disabled). APK alignment, ZIP integrity, ELF32 ARM packaging and Android API 28 signature verification passed. The certificate SHA-256 matches the original release; versionCode is 2. This APK can update the existing installation without uninstalling. Enabled FFmpeg decoder configuration was checked: ALAC only. APK SHA-256: `a270b44726df546190b53600c3d40e3fe14db8e34ca482debb6c830c93fc704e`.
