# Rebuilding

Toolchain: JDK 21 (cloud build used Temurin 21.0.9+10), Gradle **8.11.1** with wrapper distribution SHA-256, Android Gradle plugin **8.9.2**, Android SDK platform **35**, build-tools **35.0.0**, NDK **27.2.12479018** (r27c), CMake **3.22.1**. Minimum Android API **28**, target API **35**. Java bytecode 11. Four build workers bound memory use. No runtime dependencies downloaded by the app.

Install a JDK 21 and Google's Android command-line tools through their official distributions (Android Studio is optional). Accept the SDK licenses, then:

```bash
export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"  # Mac; use your actual SDK location
sdkmanager 'platforms;android-35' 'build-tools;35.0.0' 'ndk;27.2.12479018' 'cmake;3.22.1'
cd /path/to/tcl-airdrop
bash scripts/build-crypto.sh armeabi-v7a
bash scripts/build-audio.sh armeabi-v7a
./gradlew --no-daemon assembleRelease lintRelease
```

The crypto script pins OpenSSL 3.5.9 to `45e844fa2a14ec92d146bd8f5778ac130b6625fb`, uses NDK's Android clang, four make workers, and installs to ignored `.local/openssl/armeabi-v7a`. It does not use Android private crypto libraries. Dependencies/build output are never written into tracked source directories. Native CMake consumes checked-in UxPlay and libplist source plus this static crypto library.

The ALAC decoder uses FFmpeg **8.1.3**, commit `1041abdc962f4cc4f394aa8de9dc5236c0c3b9e7`. `build-audio.sh` enables only the ALAC decoder and avcodec/avutil, disables programs, network, autodetection, other codecs, assembly and shared libraries, and uses four workers. No software video decoder, GPL-only or nonfree FFmpeg option is enabled. Its static output is installed to ignored `.local/audio/<ABI>`.

The delivered `CinemaAir-source.zip` also contains the exact FFmpeg source as `dependency-sources/ffmpeg-8.1.3.tar.gz`, plus the exact OpenSSL source as `dependency-sources/openssl-3.5.9.tar.gz` for corresponding-source completeness. The automated crypto script fetches the pinned official Git commit and expects Git metadata; the archive supplies complete source for inspection or manual offline builds. An offline automated bootstrap is not implemented. See `scripts/build-crypto.sh`.

Build output: `app/build/outputs/apk/release/app-release-unsigned.apk`. Sign with a **persistent private key outside this repository** using build-tools 35 `zipalign` and `apksigner`. Reuse the same key for future compatible updates. Never commit keystores/passwords or add them to a ZIP/GitHub. `apksigner verify --verbose --min-sdk-version 28 <apk>` must succeed before delivery. An unsigned build is reproducible; identical signed bytes require the same private key and inputs.

This cloud task's dedicated development key is retained under `/workspace/private-signing/cinemaair`, outside the checkout and all delivery archives. The password is in a permission-0600 file there; it is never printed. This is not a production signing service or proof of a secure off-machine backup. Preserve it privately before disposing of this environment. Locally generating a different key will not update the delivered installation; never silently uninstall to bypass that mismatch.

Only armeabi-v7a is enabled in `app/build.gradle`. To target another **verified** ABI, first build its crypto and audio variants with `scripts/build-crypto.sh` and `scripts/build-audio.sh`, then deliberately select that ABI in `abiFilters`. The script supports arm64-v8a and x86_64 too; those builds are **unrun**, not delivered. Do not infer supported TV ABIs from the CPU model.

## Host validation

Linux: C compiler, CMake 3.22+, OpenSSL 3 development headers and pthreads. On macOS an explicit OpenSSL prefix may be required. These are protocol/parser tests, not Android rendering or iPhone tests.

```bash
bash scripts/build-audio.sh host
cmake -S native -B .local/host -DCMAKE_BUILD_TYPE=Debug
cmake --build .local/host -j4
ctest --test-dir .local/host --output-on-failure
python3 -m unittest discover -s tests -p 'test_*.py' -v
```

The second C test opens a short-lived local RTSP socket and closes it. It does not advertise mDNS or contact an iPhone. Sanitizer validation used `-fsanitize=address,undefined -fno-omit-frame-pointer` for C and executable linking, with `ASAN_OPTIONS=detect_leaks=1`.

## Cloud environment

Use the existing `/workspace/tcl-airdrop` checkout; tasks are already isolated. Do not create a worktree unless explicitly requested. Prepared SDK/JDK/Gradle live under `/workspace/toolchains`; live processes are not required for later tasks. Build instructions do not depend on a running desktop Linux receiver. In this managed cloud, preserve the provided HTTP/HTTPS proxy and CA trust. Java builds used a copy of `/etc/ssl/certs/java/cacerts` as their trust store plus the session's proxy settings, with verification enabled. These machine-specific settings are not required on an ordinary Mac.

## Decoder backpressure regression and sanitizers

```bash
mkdir -p .local/java-tests
javac -d .local/java-tests app/src/main/java/com/gabriel/cinemaair/CodecInput.java tests/CodecInputTest.java
java -cp .local/java-tests com.gabriel.cinemaair.CodecInputTest
```

For complete audio-decoder sanitizer coverage, build `bash scripts/build-audio.sh host-sanitized`, configure CMake with `-DAUDIO_HOST_ABI=host-sanitized`, and set both C and C++ compiler flags plus executable linker flags to `-fsanitize=address,undefined -fno-omit-frame-pointer`. Run CTest with `ASAN_OPTIONS=detect_leaks=1`. LeakSanitizer probes require an execution environment without ptrace; the compiler probe fails under a ptrace sandbox. Do not disable sanitizers to make those tests pass.
