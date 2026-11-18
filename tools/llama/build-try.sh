#!/bin/sh
# Builds "try", a small program that runs KoLib's engine on this computer, against a llama.cpp built here.
# usage: build-try.sh <llama.cpp source> <llama.cpp build dir> <output>
set -e
L="$1"; H="$2"; OUT="$3"
HERE="$(cd "$(dirname "$0")" && pwd)"
S="$HERE/../../app/src/main/cpp"
clang++ -std=c++17 -O2 -DKL_LOG_STDERR -I"$S" -I"$L/include" -I"$L/common" -I"$L/ggml/include" -I"$L/vendor" \
  "$S/kl_llm.cpp" "$HERE/try.cpp" -o "$OUT" \
  "$H/common/libllama-common.a" "$H/common/libllama-common-base.a" "$H/src/libllama.a" \
  "$H/ggml/src/libggml.a" "$H/ggml/src/libggml-cpu.a" "$H/ggml/src/libggml-base.a" \
  "$H/vendor/cpp-httplib/libcpp-httplib.a" "$H/vendor/hash/libvendor-hash.a" -lpthread -ldl
