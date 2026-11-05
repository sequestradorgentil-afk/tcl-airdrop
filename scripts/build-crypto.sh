#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT to the Android SDK directory}"
abi=${1:-armeabi-v7a}
case "$abi" in
  armeabi-v7a) target=android-arm ;;
  arm64-v8a) target=android-arm64 ;;
  x86_64) target=android-x86_64 ;;
  *) echo 'Unsupported ABI' >&2; exit 2 ;;
esac
export ANDROID_NDK_ROOT="$ANDROID_SDK_ROOT/ndk/27.2.12479018"
case "$(uname -s)" in Linux) host=linux-x86_64;; Darwin) host=darwin-x86_64;; *) exit 2;; esac
export PATH="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/$host/bin:$PATH"
source_dir="$root/.local/openssl-source"
if [ ! -d "$source_dir" ]; then
  git clone https://github.com/openssl/openssl.git "$source_dir"
  git -C "$source_dir" checkout --detach 45e844fa2a14ec92d146bd8f5778ac130b6625fb
fi
[ "$(git -C "$source_dir" rev-parse HEAD)" = 45e844fa2a14ec92d146bd8f5778ac130b6625fb ]
mkdir -p "$root/.local/crypto-build/$abi"
cd "$root/.local/crypto-build/$abi"
perl "$source_dir/Configure" "$target" -D__ANDROID_API__=28 no-shared no-tests no-apps no-docs --prefix="$root/.local/openssl/$abi" --libdir=lib
make -j4 build_libs
make install_dev
