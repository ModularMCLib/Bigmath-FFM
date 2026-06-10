# Bigmath-FFM

Cross-platform high-performance big integer & decimal library for Java FFM (backed by GMP/MPFR), part of [ModularMCLib](https://github.com/ModularMCLib).

## Features

- **BigInt** — arbitrary-precision integer arithmetic (GMP)
- **BigDecimal** — arbitrary-precision decimal floating-point (MPFR)
- **Int128** — 128-bit signed integer (no external dependency)
- **FFM native bridge** — Java 23+ Foreign Function & Memory API
- **Kotlin operator overloading** — use `+`, `-`, `*`, `/` with BigInt/BigDecimal

## Supported Platforms

| OS      | Architecture    | GMP/MPFR                             | Int128   |
|---------|-----------------|--------------------------------------|----------|
| Linux   | x86-64, aarch64 | `apt install libgmp-dev libmpfr-dev` | built-in |
| macOS   | x86-64, aarch64 | `brew install gmp mpfr`              | built-in |
| Windows | x86-64, aarch64 | vcpkg / MSYS2                        | built-in |
| Android | aarch64, x86-64 | stubs only                           | built-in |

## Quick Start

```java
import com.modularmc.bigmath.BigInt;
import com.modularmc.bigmath.BigDeci;

BigInt a = BigInt.fromString("12345678901234567890", 10);
BigInt b = BigInt.fromLong(42);
BigInt sum = a.add(b);
System.out.println(sum); // 12345678901234567932

BigDecimal pi = BigDecimal.fromString("3.141592653589793", 128);
BigDecimal area = pi.multiply(pi);
```

## Building

### Prerequisites

- Java 23+
- CMake 3.20+
- GMP 6.x + MPFR 4.x (optional; stubs built otherwise)
- CUDA Toolkit with `nvcc` for experimental CUDA builds on Linux x86-64 or Windows x86-64

### Gradle

```bash
./gradlew assemble
```

Native libraries are built automatically via CMake. Set `-DUSE_GMP=OFF` to skip GMP/MPFR.

### Experimental CUDA

This branch contains experimental CUDA acceleration for the BigInt multiplication path.
On Linux x86-64 and Windows x86-64, native configuration requires the CUDA toolkit and
links against CUDA runtime and cuFFT. Windows builds use MSVC and the `x64-windows`
vcpkg triplet.

```bash
cmake --preset cuda-linux-release
cmake --build build/native --target install
```

```powershell
cmake --preset cuda-windows-msvc-release
cmake --build build/native --target install
```

Set `BIGMATH_CUDA_ARCH` or pass `-PbigmathCudaArch=<arch>` to override the default
`native` CUDA architecture detection. At runtime the native library probes CUDA once
when it is loaded, caches the device status, and automatically falls back to the CPU
path when no usable CUDA device is available.

Gradle can also discover common local tool paths when they are not on `PATH`, including
CLion's bundled CMake/Ninja and the default Windows CUDA Toolkit install directory.
Use explicit properties when needed:

```powershell
.\gradlew.bat buildNative `
  -PcudaToolkitRoot="C:\Program Files\NVIDIA GPU Computing Toolkit\CUDA\v13.3" `
  -PcmakeExecutable="D:\JetBrains\CLion\bin\cmake\win\x64\bin\cmake.exe" `
  -PninjaExecutable="D:\JetBrains\CLion\bin\ninja\win\x64\ninja.exe"
```

Windows CUDA builds must run with MSVC `cl.exe` available and GMP/MPFR installed via
the vcpkg `x64-windows` triplet.

### CMake directly

```bash
cmake -S src/main/cpp -B build/native -DCMAKE_BUILD_TYPE=Release
cmake --build build/native --target install
```

## License

GNU LGPL 3.0 — see [LICENSE](LICENSE).

## Native Distribution Notes

Published artifacts may include platform-native shared libraries under
`native/<classifier>/`. Depending on platform, these can include GMP, MPFR, and
selected toolchain runtime libraries needed to load `bigmath_ffm`.

Users can replace compatible shared libraries by:

- setting `-Dbigmath.native.path=/absolute/path/to/library`
- replacing files in `native/<classifier>/`
- using the normal platform library search path

Third-party component notices and source pointers are documented in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and packaged into
`META-INF/THIRD_PARTY_NOTICES.md` in built resources.
