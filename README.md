# Bigmath-FFM

Cross-platform high-performance big integer & decimal library for Java FFM (backed by GMP/MPFR), part of [ModularMCLib](https://github.com/ModularMCLib).

## Features

- **BigInt** — arbitrary-precision integer arithmetic (GMP)
- **BigDeci** — arbitrary-precision decimal floating-point (MPFR)
- **Int128** — 128-bit signed integer (no external dependency)
- **BigNumberFormat** — Native number formatting with `DecimalFormat` patterns, locales, compact units, and scientific fallback
- **FFM native bridge** — Java 23+ Foreign Function & Memory API
- **Scoped Kotlin expressions** — use `+`, `-`, `*`, `/` without leaking BigInt/BigDeci intermediates

## Supported Platforms

Bigmath-FFM 0.2.0 supports exactly these release classifiers:

| OS      | Architectures | Classifiers |
|---------|---------------|-------------|
| Linux   | x86-64, aarch64 | `linux-x86-64`, `linux-aarch64` |
| macOS   | x86-64, aarch64 | `macos-x86-64`, `macos-aarch64` |
| Windows | x86-64, aarch64 | `windows-x86-64`, `windows-aarch64` |

Release artifacts include GMP, MPFR, and the architecture-matched runtime
libraries needed on a minimal host. Android, 32-bit targets, RISC-V, and other
OS/architecture combinations fail before Native loading.

Linux glibc and macOS deployment baselines follow the current GitHub-hosted
runner toolchains. They are not frozen and can move when runner images change.

## Quick Start

```java
import com.modularmc.bigmath.BigInt;
import com.modularmc.bigmath.BigDeci;
import com.modularmc.bigmath.BigNumberFormat;

try (BigInt a = BigInt.fromString("12345678901234567890", 10);
     BigInt b = BigInt.fromLong(42);
     BigInt sum = a.add(b);
     BigDeci pi = BigDeci.fromString("3.141592653589793", 128);
     BigDeci area = pi.multiply(pi)) {
    System.out.println(sum); // 12345678901234567932
    System.out.println(BigNumberFormat.readable().format(sum));
    System.out.println(area);
}
```

### Number formatting

`BigNumberFormat` is immutable and thread-safe. It compiles the selected
`DecimalFormat` pattern and locale once; actual numeric conversion, scaling,
rounding, grouping, digit localization, and output assembly run in Native code.
When no locale is supplied, the formatter captures the current FORMAT locale at
build time.

```java
import java.util.Locale;

long value = 1_234_567L;
BigNumberFormat grouped = BigNumberFormat.ofPattern("#,##0.00", Locale.US);
BigNumberFormat power = BigNumberFormat.builder("#,##0.#")
        .locale(Locale.US)
        .compactUnits(true)
        .unit("W")
        .build();

String exact = grouped.format(value);
String readable = power.format(value);
String scientific = BigNumberFormat.scientific().format(value);
```

The public overloads accept `BigInt`, `BigDeci`, `Int128`, `long`, `double`,
`BigInteger`, and `BigDecimal`. `readable()` uses 1000-based compact suffixes,
while `scientific()` uses the localized `0.00E00` pattern.

The 0.2.0 migration from the removed instance formatting methods is:

| Previous behavior | Replacement |
|---|---|
| Grouped integer output | `BigNumberFormat.ofPattern("#,##0").format(value)` |
| Readable compact units | `BigNumberFormat.readable().format(value)` |
| Scientific output | `BigNumberFormat.scientific().format(value)` |

### Runtime configuration and diagnostics

Runtime options are configured only through Java, before the first Native-backed
value is initialized. There are no environment-variable or system-property
overrides for cache, device, calibration, or backend policy.

```java
import com.modularmc.bigmath.BigmathRuntime;
import com.modularmc.bigmath.RuntimeDiagnostics;
import com.modularmc.bigmath.RuntimeOptions;

BigmathRuntime.configure(RuntimeOptions.builder()
        .productCacheEnabled(true)
        .cpuProductCacheBytes(64L * 1024 * 1024)
        .automaticCudaDevice()
        .cudaBackend(RuntimeOptions.CudaBackend.AUTO)
        .build());

RuntimeDiagnostics diagnostics = BigmathRuntime.initializeAsync().join();
System.out.println(diagnostics.cuda().statusMessage());
```

The default AUTO policy keeps candidate operations on CPU until calibration is
ready. Calibration profiles are invalidated by Native build, ABI, CPU, GPU UUID,
driver/runtime, GMP, and MPFR identity and are stored with an OS-standard cache
path, process lock, and validated same-directory replacement. Device workspaces
share a bounded per-device budget. NTT is selected only for calibrated transform sizes where it
is correct and consistently at least 10% faster than cuFFT.

### Kotlin expressions

BigInt and BigDeci operators are available only inside their calculation
scopes. The returned value is detached; all other native-backed intermediates
are closed automatically.

```kotlin
val integerResult = bigIntExpression { a + b * c }
val decimalResult = bigDeciExpression { x / y + z }
```

The caller remains responsible for closing `integerResult` and
`decimalResult`. Int128 operators remain available globally because Int128 does
not allocate a native handle per result.

See [Migrating to Bigmath-FFM 0.2.0](docs/migration-0.2.0.md) for the complete
breaking-change and replacement guide.

## Building

### Prerequisites

- Java 23+
- CMake 3.21+
- GMP 6.x + MPFR 4.x for a full BigInt/BigDeci build
- CUDA Toolkit with `nvcc` for experimental CUDA builds on Linux x86-64 or Windows x86-64

### Gradle

```bash
./gradlew assemble
```

`assemble` builds the current host's full Native library through CMake. The
release pipeline instead runs `releaseJar` against the six downloaded remote
artifacts and does not rebuild Native code in the packaging job.

The normal Gradle build requires GMP/MPFR and fails when they are unavailable.
The explicit Int128-only developer mode is a direct CMake configuration:

```bash
cmake -S src/main/cpp -B build/native -DUSE_GMP=OFF
cmake --build build/native --target install
```

`USE_GMP=OFF` is not a release configuration and does not provide BigInt or
BigDeci capability.

For repository development, `native/` is an ignored staging area for artifacts
downloaded from remote CI. Local verification should compile Java/Kotlin and use
those downloaded binaries; project development does not require a local Native
build.

### Experimental CUDA

CUDA-enabled development artifacts are produced separately for Linux x86-64 and
Windows x86-64. The standard six-platform release JAR remains usable without a
CUDA runtime. CUDA builds use cuFFT plus an exact dual-prime NTT path, bounded
workspace pools, calibrated dispatch, GPU-resident odd/even modular
exponentiation, and CPU fallback before any partial result is exposed.

```bash
cmake --preset cuda-linux-release
cmake --build build/native --target install
```

```powershell
cmake --preset cuda-windows-msvc-release
cmake --build build/native --target install
```

Pass `-PbigmathCudaArch=<arch>` when a remote or direct Gradle Native build needs
an explicit architecture list. Runtime backend selection is performed only with
`RuntimeOptions`; legacy environment switches such as `BIGMATH_CUDA_NTT` are not
supported.

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

Release JARs contain exactly six native classifiers: `linux-x86-64`,
`linux-aarch64`, `macos-x86-64`, `macos-aarch64`, `windows-x86-64`, and
`windows-aarch64`.

Windows classifiers include the complete architecture-matched non-system DLL
closure needed on a minimal host. This includes the Microsoft VC++ runtime and,
when required by GMP, MPFR, or another bundled dependency, the GNU libstdc++,
libgcc, and MinGW-w64 winpthreads runtimes together with their notices.

Advanced users can point the loader at a replacement library by:

- setting `-Dbigmath.native.path=/absolute/path/to/library`
- replacing files in `native/<classifier>/`
- using the normal platform library search path

The replacement must match the Java bindings shipped in the same release.
Bigmath-FFM does not promise compatibility for an external C ABI or for Native
libraries taken from another release/build.

Third-party component notices and source pointers are documented in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and packaged into
`META-INF/THIRD_PARTY_NOTICES.md` in built resources.
