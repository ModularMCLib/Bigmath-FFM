#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# cuda-bench.sh — fetch the prebuilt CUDA-enabled native library from CI and
# benchmark the GPU multiply path against GMP on this machine's NVIDIA GPU.
#
# No local CUDA/MSVC build toolchain is required: the .dll/.so is downloaded
# from the latest successful "Native Build" workflow run (which compiles with
# BIGMATH_ENABLE_CUDA=true). At runtime the library probes the local GPU and
# uses it automatically.
#
# Usage:
#   scripts/cuda-bench.sh                       # auto: latest run on current branch
#   scripts/cuda-bench.sh --run <run-id>        # a specific Native Build run
#   scripts/cuda-bench.sh --sizes 80000 160000  # custom digit sizes for the sweep
#   scripts/cuda-bench.sh --skip-download        # reuse build/native/lib as-is
#
# Requires: gh (authenticated), a JDK 23+ on PATH, and NVIDIA driver + CUDA
# runtime DLLs reachable (e.g. CUDA toolkit bin/x64 on PATH for cudart/cufft).
# ---------------------------------------------------------------------------
set -euo pipefail
cd "$(dirname "$0")/.."

RUN_ID=""; SIZES=""; SKIP_DL=0
while [ $# -gt 0 ]; do
  case "$1" in
    --run) RUN_ID="$2"; shift 2;;
    --sizes) shift; while [ $# -gt 0 ] && [[ "$1" =~ ^[0-9]+$ ]]; do SIZES="$SIZES $1"; shift; done;;
    --skip-download) SKIP_DL=1; shift;;
    *) echo "unknown arg: $1" >&2; exit 2;;
  esac
done

case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) CLASSIFIER="windows-x86-64"; LIB="bigmath_ffm.dll"; SEP=';';;
  Linux)                CLASSIFIER="linux-x86-64";   LIB="libbigmath_ffm.so"; SEP=':';;
  *) echo "CUDA artifacts are only published for linux-x86-64 / windows-x86-64" >&2; exit 1;;
esac
LIBDIR="build/native/lib"

if [ "$SKIP_DL" = 0 ]; then
  echo "== Locating latest successful Native Build run"
  if [ -z "$RUN_ID" ]; then
    BRANCH="$(git rev-parse --abbrev-ref HEAD)"
    RUN_ID="$(gh run list --workflow=native-build.yml --branch "$BRANCH" --status success \
                --limit 1 --json databaseId -q '.[0].databaseId' 2>/dev/null || true)"
    [ -n "$RUN_ID" ] || RUN_ID="$(gh run list --workflow=native-build.yml --status success \
                --limit 1 --json databaseId -q '.[0].databaseId')"
  fi
  echo "   run=$RUN_ID  artifact=native-libs-$CLASSIFIER"
  rm -rf "$LIBDIR"; mkdir -p "$LIBDIR"
  gh run download "$RUN_ID" -n "native-libs-$CLASSIFIER" -D "$LIBDIR"
fi
[ -f "$LIBDIR/$LIB" ] || { echo "native lib missing: $LIBDIR/$LIB" >&2; exit 1; }
echo "   staged: $LIBDIR/$LIB"

echo "== Compiling main + CUDA harnesses"
./gradlew -q classes
mkdir -p build/cudacheck
javac --release 23 -cp "build/classes/java/main" -d build/cudacheck \
      src/jmh/java/com/modularmc/bigmath/CudaCheck.java \
      src/jmh/java/com/modularmc/bigmath/CudaSweep.java \
      src/jmh/java/com/modularmc/bigmath/CudaVerify.java

CP="build/classes/java/main${SEP}build/cudacheck"
JAVA_OPTS=(--enable-native-access=ALL-UNNAMED -Dbigmath.native.path="$PWD/$LIBDIR/$LIB")

echo "== Verifying GPU execution (CudaCheck)"
java "${JAVA_OPTS[@]}" -cp "$CP" com.modularmc.bigmath.CudaCheck 2>/dev/null | grep -E 'cuda|device|status|multiplyCount|CUDA'

# Correctness gate: bit-exact vs GMP. set -e aborts the run on any mismatch, so a
# kernel rewrite cannot pass benchmarking while producing wrong products.
echo "== Correctness [cuFFT FP64 path] vs GMP"
java "${JAVA_OPTS[@]}" -cp "$CP" com.modularmc.bigmath.CudaVerify 2>/dev/null
echo "== Correctness [integer-NTT path] vs GMP  (BIGMATH_CUDA_NTT=1)"
BIGMATH_CUDA_NTT=1 java "${JAVA_OPTS[@]}" -cp "$CP" com.modularmc.bigmath.CudaVerify 2>/dev/null

echo "== Sweep [cuFFT FP64 path]"
java "${JAVA_OPTS[@]}" -cp "$CP" com.modularmc.bigmath.CudaSweep $SIZES

echo "== Sweep [integer-NTT path]  (BIGMATH_CUDA_NTT=1)"
echo "   (identical to the cuFFT row above if the loaded lib predates the int-NTT kernel)"
BIGMATH_CUDA_NTT=1 java "${JAVA_OPTS[@]}" -cp "$CP" com.modularmc.bigmath.CudaSweep $SIZES
