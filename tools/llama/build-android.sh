#!/bin/sh
# Builds KoLib's on-device language model libraries (llama.cpp and KoLib's layer over it) for Android and puts
# them in app/src/main/jniLibs, where the app's build picks them up. Run it once after cloning (and again after
# third_party/llama.cpp or app/src/main/cpp changes):
#
#   git submodule update --init --depth 1
#   ANDROID_NDK=/path/to/android-sdk/ndk/29.0.14206865 tools/llama/build-android.sh
#
# Needs CMake 3.22+ and Ninja. Without these libraries the app still builds; it just can't run models on the device.
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$HERE/../.."
: "${ANDROID_NDK:?Set ANDROID_NDK to the NDK folder}"
BUILD="${KL_LLM_BUILD:-$ROOT/build/llm}"
for ABI in ${KL_LLM_ABIS:-arm64-v8a x86_64}; do
  cmake -S "$ROOT/app/src/main/cpp" -B "$BUILD/$ABI" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ABI" -DANDROID_PLATFORM=android-29 -DCMAKE_BUILD_TYPE=Release
  ninja -C "$BUILD/$ABI" ${KL_LLM_JOBS:+-j$KL_LLM_JOBS}
  OUT="$ROOT/app/src/main/jniLibs/$ABI"
  mkdir -p "$OUT"
  rm -f "$OUT"/libkolib-llm.so "$OUT"/libllama*.so "$OUT"/libggml*.so
  find "$BUILD/$ABI" -name '*.so' -exec cp {} "$OUT/" \;
  # Processors no phone, tablet or Chromebook has (servers'): left out, to keep the app small.
  for V in cannonlake cascadelake cooperlake icelake ivybridge piledriver sapphirerapids skylakex zen4; do rm -f "$OUT/libggml-cpu-$V.so"; done
  "$ANDROID_NDK"/toolchains/llvm/prebuilt/*/bin/llvm-strip --strip-unneeded "$OUT"/libkolib-llm.so "$OUT"/libllama*.so "$OUT"/libggml*.so
done
ls -la "$ROOT"/app/src/main/jniLibs/*/
