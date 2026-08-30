# Migrating to Bigmath-FFM 0.2.0

Bigmath-FFM 0.2.0 is a breaking Java, Kotlin, Native binding, and platform
release. Java callers should rebuild against 0.2.0 and deploy the Java classes
and Native artifacts from the same build together.

## Supported platforms

The supported classifiers are exactly:

- `linux-x86-64`
- `linux-aarch64`
- `macos-x86-64`
- `macos-aarch64`
- `windows-x86-64`
- `windows-aarch64`

Android and all other OS/architecture combinations are unsupported and now fail
before Native loading. Release artifacts provide full GMP/MPFR capability. The
explicit `USE_GMP=OFF` CMake mode remains an Int128-only developer build and is
not a release artifact.

## Number formatting

The instance `toFormattedString(...)` methods on `BigInt`, `BigDeci`, and
`Int128` were removed. Create an immutable formatter and reuse it:

```java
BigNumberFormat grouped = BigNumberFormat.ofPattern("#,##0");
String text = grouped.format(value);
```

Migration mapping:

| Previous behavior | 0.2.0 replacement |
|---|---|
| Grouped integer output | `BigNumberFormat.ofPattern("#,##0").format(value)` |
| Compact unit output | `BigNumberFormat.readable().format(value)` |
| Scientific output | `BigNumberFormat.scientific().format(value)` |

`ofPattern(...)` follows ordinary `DecimalFormat` pattern and locale behavior;
it does not add compact suffixes. Use `compactUnits(true)` only when compact
1000-based suffixes are wanted. A formatter captures the FORMAT locale when it
is built and does not follow later default-locale changes.

## Runtime configuration

Cache, CUDA device/backend, workspace, and calibration options are configured
only through Java. Configure before the first `BigInt` or `BigDeci` use:

```java
BigmathRuntime.configure(RuntimeOptions.builder()
        .productCacheEnabled(true)
        .cpuProductCacheBytes(64L * 1024 * 1024)
        .gpuWorkspaceFraction(0.25)
        .gpuWorkspaceMaxBytes(512L * 1024 * 1024)
        .automaticCudaDevice()
        .cudaBackend(RuntimeOptions.CudaBackend.AUTO)
        .build());

RuntimeDiagnostics diagnostics = BigmathRuntime.initializeAsync().join();
```

Calling `configure` after Native initialization has started throws
`IllegalStateException`. Runtime environment variables and system properties no
longer override these policies. `bigmath.native.path` remains a loader location,
not a runtime tuning option.

## Kotlin expressions

Global BigInt and BigDeci arithmetic operators were removed because every
intermediate operator result owns a Native handle. Use scoped expressions:

```kotlin
val integerResult = bigIntExpression { a + b * c }
val decimalResult = bigDeciExpression { x / y + z }
```

The lambda result is detached and returned to the caller; other intermediates
are closed automatically. The caller must close the returned value. Int128
operators remain global because Int128 values do not own Native handles.

Java code can use `NativeCalculationScope` directly when a calculation creates
several temporary BigInt or BigDeci values. Ordinary allocating methods and
`set`/`*Into` remain available; continue closing every owned value.

## BigInteger and BigDecimal interop

Interop no longer transfers decimal strings. `BigInteger` uses two's-complement
bytes, while `BigDecimal` uses unscaled two's-complement bytes plus scale. No
source change is required for callers using the public conversion methods, but
Java and Native components from older releases must not be mixed.

## Native artifacts and ABI

Native handles now carry an ID and mutation version and FFM constructors and
arithmetic exports return handles directly. Old Native libraries are rejected
by the ABI handshake. Bigmath-FFM does not promise an external C ABI or
cross-release Native compatibility.

`bigmath.native.path` can still select an explicit library, but that library and
its same-directory dependencies must come from a build compatible with the
current Java bindings. Windows minimal-host packages retain the Microsoft VC++
runtime plus any required GNU libstdc++, libgcc, and MinGW-w64 winpthreads DLLs
and notices.

## Repository build workflow

The repository `native/` directory is ignored and is used to stage remote CI
artifacts for local Java/public-API verification. Release packaging aggregates
the six remote artifacts with Gradle and does not build Native code in the
packaging job. Local Native builds are not required for normal project
development.
