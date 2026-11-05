# Receiver stack research — 2026-11-05

Official repositories/docs were fetched and inspected directly, including README, native APIs, renderer code, CMake files and licenses. These are upstream statements, not device-validation results.

* [FDH2/UxPlay](https://github.com/FDH2/UxPlay), commit `d4b7373cb6107b2d8d6c5838991c19d39e2b96ae`: current development describes 1.74 **experimental**. Maintained Unix/macOS/Windows receiver with H.264 mirroring, AAC mirroring audio, ALAC audio-only, optional HEVC and selected YouTube HLS. New internal mDNS removes the external Avahi dependency. Its GStreamer/X11/desktop executable cannot simply run as an Android app. Selected **protocol core only**; desktop renderers are not included. Android JNI, lifecycle, Surface, codecs and audio output are new integration work. Modern iOS and Android portability remain to be proven.
* [antimof/UxPlay](https://github.com/antimof/UxPlay), inspected commit `fd88c0c465df492e03461980bedf115a40bc835a`: older upstream location; current README points to FDH2. Not selected as a second protocol dependency.
* [FD-/RPiPlay](https://github.com/FD-/RPiPlay): upstream README describes iOS 9+ mirroring, Pi OpenMAX hardware video and FDK-AAC audio, with GStreamer desktop alternative. Pi-specific OpenMAX/ilclient and Linux discovery are unsuitable for direct Android execution. Not linked into Cinema Air.
* [jiangban/AirplayServer](https://github.com/jiangban/AirplayServer): preserved Android ancestor attributed by UxPlay to dsafa22. Contains JNI, MediaCodec/AudioTrack and bundled historic crypto/AAC implementations. Useful Android integration evidence, but not a maintained modern-iOS compatibility guarantee. Its old APK and binaries are **not** reused or distributed.

## Included code and licenses

| Component | Pin / source | License / obligations |
|---|---|---|
| UxPlay native core and internal mDNS | commit above; copied `lib/` with original headers | Combined program GPLv3; individual inherited files LGPL-2.1-or-later; preserve notices, provide corresponding source and build scripts |
| PlayFair (inside UxPlay) | inherited `lib/playfair` from EstebanKubata and contributors | GPL; root UxPlay distribution GPLv3; ordinary AirPlay session negotiation only, no protected-content bypass |
| llhttp (inside UxPlay) | upstream vendored files with original headers | MIT; notice retained in `vendor/uxplay/lib/llhttp/LICENSE-MIT` |
| libplist + libcnary | 2.7.0, `cf5897a71ea412ea2aeb1e2f6b5ea74d4fabfd8c` | LGPL-2.1-or-later; COPYING/COPYING.LESSER and source included; combined distribution GPLv3 |
| OpenSSL libcrypto | 3.5.9, `45e844fa2a14ec92d146bd8f5778ac130b6625fb` | Apache-2.0 compatible with GPLv3; license retained, exact source included in delivery source archive |
| New Java/JNI/build/test/helper code | this repository | GPL-3.0-or-later |
| Android framework MediaCodec, AudioTrack, networking/Wi-Fi APIs | supplied by device | Platform APIs; no Android framework binary bundled |

No FFmpeg, FDK-AAC, GStreamer, commercial receiver binary or codec downloaded from an unverified APK is included. AAC decoding depends on Android's decoder implementation. AAC-ELD is checked before advertising. OpenSSL is compiled with NDK, rather than linking Android's private platform OpenSSL/BoringSSL ABI. TLS/package/checksum verification was not disabled for dependency acquisition.

Local modifications to UxPlay: total RTSP request budget, negative/oversized mirror length rejection, AVC configuration boundary validation, reject HEVC and nonfinite/out-of-range dimensions, repeated dimension callback for orientation, safe cleanup after initialization failure, narrowed audio-codec/metadata TXT declarations. Original copyright/license headers are retained. libplist is built using a minimal CMake source list equivalent to its upstream C library target, with Android-supported feature definitions; the upstream command-line tools and C++ wrapper are not linked.

GPL corresponding source, license texts and build scripts accompany the APK in the downloadable ZIP. Preserve them when redistributing; modifications to this combined application must remain available under the applicable GPL terms. The project is user-owned source, not proprietary ownership of these upstream libraries. Codec patents and Apple certification are not granted by open-source copyright licenses.

Audio-format and feature-bit meanings were additionally cross-checked against [openairplay/airplay2-receiver](https://github.com/openairplay/airplay2-receiver), commit `6c343d3679ddb561c61566985acaaf587d0a3bd3`, `ap2/connections/audio.py` and `ap2/bitflags.py`. Its Python implementation is not linked or bundled. `/info` is narrowed to the published 44.1 kHz stereo AAC-LC/AAC-ELD format values, and PCM/ALAC feature bits are cleared. This does not establish iOS 27 compatibility.
