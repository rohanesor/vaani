# Vendored whisper.cpp

This directory contains the CPU-only source subset required by Vokie's Android build.

- Upstream: https://github.com/ggerganov/whisper.cpp
- Release: v1.7.6
- Commit: `a8d002cfd879315632a579e73f0148d06959de36`
- License: MIT (`LICENSE`)

GPU/RPC backend source trees, examples, tests, model binaries, and download scripts are intentionally excluded. Vokie's parent CMake file disables network/model-download features and builds a single JNI shared library around the static whisper/ggml CPU implementation.
