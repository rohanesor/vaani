#!/usr/bin/env bash
# Reproducible CTranslate2 4.8.2 CPU-only Android arm64 static-library build.
# Run from the repository root in a Windows/WSL Android SDK environment.
set -euo pipefail

CT2_TAG=v4.8.2
CT2_COMMIT=d44d2d069eb88c7b7804da864c10c201501cb4a9
NDK_VERSION=27.0.12077973
SDK_ROOT=${ANDROID_SDK_ROOT:-/mnt/c/Users/kille/AppData/Local/Android/Sdk}
CMAKE_BIN=${CMAKE_BIN:-"$SDK_ROOT/cmake/3.22.1/bin/cmake.exe"}
NINJA_WIN=${NINJA_WIN:-'C:\Users\kille\AppData\Local\Android\Sdk\cmake\3.22.1\bin\ninja.exe'}
NDK_WIN=${NDK_WIN:-'C:\Users\kille\AppData\Local\Android\Sdk\ndk\27.0.12077973'}
WORK=${1:-/tmp/ctranslate2-android}
SRC="$WORK/src"
BUILD="$WORK/build"

rm -rf "$WORK"
git clone --depth 1 --branch "$CT2_TAG" https://github.com/OpenNMT/CTranslate2.git "$SRC"
[ "$(git -C "$SRC" rev-parse HEAD)" = "$CT2_COMMIT" ]
git -C "$SRC" submodule update --init --recursive
# Android bionic does not expose pthread_setaffinity_np. CT2 uses it only for
# optional thread pinning, so retain its documented unsupported-platform path.
python3 - "$SRC/src/thread_pool.cc" <<'PY'
import sys
p=sys.argv[1]
s=open(p).read()
old='#if !defined(__linux__) || defined(_OPENMP)'
assert old in s
open(p,'w').write(s.replace(old, '#if !defined(__linux__) || defined(__ANDROID__) || defined(_OPENMP)', 1))
PY
"$CMAKE_BIN" -S "$(wslpath -w "$SRC")" -B "$(wslpath -w "$BUILD")" -G Ninja \
  "-DCMAKE_MAKE_PROGRAM=$NINJA_WIN" \
  "-DCMAKE_TOOLCHAIN_FILE=$NDK_WIN\\build\\cmake\\android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-24 -DCMAKE_BUILD_TYPE=Release -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
  -DBUILD_SHARED_LIBS=OFF -DBUILD_CLI=OFF -DBUILD_TESTS=OFF \
  -DWITH_MKL=OFF -DWITH_DNNL=OFF -DWITH_ACCELERATE=OFF -DWITH_OPENBLAS=OFF -DWITH_RUY=ON \
  -DWITH_CUDA=OFF -DWITH_CUDNN=OFF -DWITH_HIP=OFF -DOPENMP_RUNTIME=NONE -DENABLE_CPU_DISPATCH=OFF
"$CMAKE_BIN" --build "$(wslpath -w "$BUILD")" --target ctranslate2 -j2
printf 'Built: %s\n' "$BUILD/libctranslate2.a"
