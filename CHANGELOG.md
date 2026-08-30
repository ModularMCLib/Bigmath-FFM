# Changelog

## [0.2.0] - 2026-08-31

### Added

- Immutable, thread-safe `BigNumberFormat` with `DecimalFormat` patterns,
  localized patterns and symbols, currency/percent/per-mille affixes, compact
  units, milli input, application units, scientific fallback, all Java rounding
  modes, and overloads for Bigmath, primitive, and JDK big-number inputs.
- Native descriptor-driven formatting pipeline and bounded per-formatter result
  cache keyed by Native handle ID/version or Int128 words.
- `BigmathRuntime`, `RuntimeOptions`, and immutable `RuntimeDiagnostics` for
  Java-only configuration, asynchronous initialization, capability reporting,
  product-cache metrics, calibration state, thresholds, workspace use, and CPU
  fallback counts.
- Thread-confined `NativeCalculationScope` and scoped Kotlin BigInt/BigDeci
  expression DSLs that automatically release intermediate Native handles.
- Native handle IDs and mutation versions, direct handle-returning FFM calls,
  byte-based `BigInteger`/`BigDecimal` interop, and explicit no-GMP capability
  errors.
- Adaptive process-wide product cache with canonical handle/version/backend keys,
  two-hit admission, a 16-result/64 MiB byte-LRU budget, and diagnostics.
- Bounded per-device CUDA workspace pools with explicit streams, caller-managed
  cuFFT work areas, pinned transfers, asynchronous copies, calibrated CPU/cuFFT/
  NTT dispatch, and persistent hardware-specific calibration profiles.
- GPU-resident sliding-window modular exponentiation using Montgomery reduction
  for odd moduli and Barrett reduction for even moduli, with atomic CPU fallback.
- Six release classifiers: Linux, macOS, and Windows on x86-64 and aarch64. Full
  release artifacts include GMP/MPFR and architecture-matched runtime notices.

### Changed

- This is a breaking Java, Kotlin, Native binding, and platform-support release.
- Number formatting now compiles pattern/locale metadata in Java once; all actual
  conversion, scaling, rounding, grouping, digit localization, and assembly run
  in Native code.
- BigInt parsing and `doubleValue()` no longer require decimal Native round trips;
  full-width Int128 division and portable MSVC multiplication were corrected and
  optimized.
- BigDeci GPU multiplication reuses significand scratch and returns directly to
  MPFR when the calibrated CUDA attempt is unavailable or fails.
- Runtime backend/device/cache/calibration policy is configured exclusively via
  Java API. Calibration completes before AUTO dispatch enables a candidate GPU
  path and NTT remains bounded by its valid `2^23` transform order.
- `native/` is an ignored staging area for remote artifacts. Release packaging
  aggregates those six artifacts with Gradle and does not build Native code in
  the packaging job.
- Windows release artifacts use MSVC and retain the architecture-matched
  Microsoft and GNU/MinGW runtime closure required on minimal hosts, including
  libstdc++, libgcc, and winpthreads notices.

### Removed

- Removed `BigInt`, `BigDeci`, and `Int128` `toFormattedString(...)`; use
  `BigNumberFormat.ofPattern(...)`, `readable()`, or `scientific()`.
- Removed global Kotlin BigInt/BigDeci operators; use `bigIntExpression` and
  `bigDeciExpression`. Int128 operators remain global.
- Removed Android and all unknown/implicit platform fallbacks. Unsupported hosts
  now fail before attempting to load Native code.
- Removed old formatting descriptors/exports/caches and unused custom Karatsuba,
  binary GCD, product-tree factorial, and generic convolution implementations.
- Removed runtime CUDA environment switches and load-time CUDA probing.

### Fixed

- Corrected CUDA dispatch so both operand sizes participate in the cost decision.
- Corrected NTT root-order bounds, shared workspace accounting, and pool
  saturation fallback.
- Corrected JAR Native extraction, same-directory dependency staging, and cleanup
  behavior for packaged resources.
- Corrected Windows portable low-128 multiplication and added architecture-specific
  MSVC paths where supported.
