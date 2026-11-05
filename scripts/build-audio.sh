#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
abi=${1:-armeabi-v7a}
source_dir="$root/.local/ffmpeg-source"
if [ ! -d "$source_dir" ]; then
  git clone --depth 1 --branch n8.1.3 https://github.com/FFmpeg/FFmpeg.git "$source_dir"
  git -C "$source_dir" checkout --detach 1041abdc962f4cc4f394aa8de9dc5236c0c3b9e7
fi
[ "$(git -C "$source_dir" rev-parse HEAD)" = 1041abdc962f4cc4f394aa8de9dc5236c0c3b9e7 ]
options=(--disable-everything --disable-autodetect --disable-programs --disable-doc --disable-network --disable-asm --disable-debug --enable-small --enable-pic --enable-static --disable-shared --enable-decoder=alac --disable-avdevice --disable-avformat --disable-avfilter --disable-swscale --disable-swresample)
if [ "$abi" = host-sanitized ]; then
  options+=(--extra-cflags="-fsanitize=address,undefined -fno-omit-frame-pointer" --extra-ldflags="-fsanitize=address,undefined")
elif [ "$abi" != host ]; then
  : "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT}"
  case "$(uname -s)" in Linux) host=linux-x86_64;; Darwin) host=darwin-x86_64;; *) exit 2;; esac
  bin="$ANDROID_SDK_ROOT/ndk/27.2.12479018/toolchains/llvm/prebuilt/$host/bin"
  case "$abi" in
    armeabi-v7a) arch=arm; triple=armv7a-linux-androideabi; options+=(--cpu=armv7-a);;
    arm64-v8a) arch=aarch64; triple=aarch64-linux-android;;
    x86_64) arch=x86_64; triple=x86_64-linux-android;;
    *) echo 'Unsupported ABI' >&2; exit 2;;
  esac
  options+=(--target-os=android --arch="$arch" --enable-cross-compile --cc="$bin/${triple}28-clang" --cxx="$bin/${triple}28-clang++" --ar="$bin/llvm-ar" --ranlib="$bin/llvm-ranlib" --strip="$bin/llvm-strip")
fi
mkdir -p "$root/.local/audio-build/$abi"
cd "$root/.local/audio-build/$abi"
"$source_dir/configure" "${options[@]}" --prefix="$root/.local/audio/$abi"
make -j4
make install
