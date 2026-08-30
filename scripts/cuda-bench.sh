#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# cuda-bench.sh — fetch the prebuilt CUDA-enabled native library from CI and
# benchmark the GPU multiply path against GMP on this machine's NVIDIA GPU.
#
# No local CUDA/MSVC build toolchain is required: the .dll/.so is downloaded
# from the dedicated x86-64 CUDA artifact in the latest successful "Native
# Build" workflow run. Runtime backend selection is configured through the
# Java API.
#
# Usage:
#   scripts/cuda-bench.sh                       # auto: latest run on current branch
#   scripts/cuda-bench.sh --run <run-id>        # a specific Native Build run
#   scripts/cuda-bench.sh --sizes 80000 160000  # custom digit sizes for the sweep
#   scripts/cuda-bench.sh --skip-download        # reuse native/<classifier>/ as-is
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
LIBDIR="native/$CLASSIFIER"

if [ "$SKIP_DL" = 0 ]; then
  echo "== Locating latest successful Native Build run"
  if [ -z "$RUN_ID" ]; then
    BRANCH="$(git rev-parse --abbrev-ref HEAD)"
    RUN_ID="$(gh run list --workflow=native-build.yml --branch "$BRANCH" --status success \
                --limit 1 --json databaseId -q '.[0].databaseId' 2>/dev/null || true)"
    [ -n "$RUN_ID" ] || RUN_ID="$(gh run list --workflow=native-build.yml --status success \
                --limit 1 --json databaseId -q '.[0].databaseId')"
  fi
  echo "   run=$RUN_ID  artifact=cuda-native-$CLASSIFIER"
  rm -rf "$LIBDIR"
  mkdir -p "$LIBDIR"
  gh run download "$RUN_ID" -n "cuda-native-$CLASSIFIER" -D "$LIBDIR"
fi
[ -f "$LIBDIR/$LIB" ] || { echo "native lib missing: $LIBDIR/$LIB" >&2; exit 1; }
echo "   staged: $LIBDIR/$LIB"

echo "== Compiling main + CUDA harnesses with Gradle"
./gradlew -q classes compileJmhJava

CP="build/classes/java/main${SEP}build/classes/java/jmh"
JAVA_OPTS=(--enable-native-access=ALL-UNNAMED -Dbigmath.native.path="$PWD/$LIBDIR/$LIB")

echo "== Runtime diagnostics (AUTO)"
java "${JAVA_OPTS[@]}" -cp "$CP" com.modularmc.bigmath.CudaCheck AUTO \
  | grep -E 'Backend|backend|calibration|device|status|cpuFallbacks'

# Existing correctness harnesses exercise only public APIs. set -e aborts on a
# mismatch before benchmark rows are produced.
echo "== Correctness [cuFFT FP64 path]"
java "${JAVA_OPTS[@]}" -cp "$CP" com.modularmc.bigmath.CudaVerify CUFFT $SIZES
echo "== Correctness [integer-NTT path]"
java "${JAVA_OPTS[@]}" -cp "$CP" com.modularmc.bigmath.CudaVerify NTT $SIZES

for backend in CPU CUFFT NTT AUTO; do
  echo "== Sweep [$backend]"
  java "${JAVA_OPTS[@]}" -cp "$CP" com.modularmc.bigmath.CudaSweep "$backend" $SIZES
done
