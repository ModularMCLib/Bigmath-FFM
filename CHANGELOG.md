# Changelog

## [Unreleased]

### Added
- Initial project structure with Gradle version catalog and modular scripts
- BigInt wrapper for GMP `mpz_t` via Java FFM API
- BigDeci wrapper for MPFR `mpfr_t` via Java FFM API
- Int128 type with native C++ implementation
- Locale-aware formatting for BigInt, BigDeci, Int128
- Immutable `BigNumberFormat` API with `DecimalFormat` patterns, locale symbols, compact units, scientific fallback, and all supported numeric inputs
- Native formatting pipeline shared by BigInt, BigDeci, Int128, primitive, and JDK big-number inputs
- Multi-platform CMake native library build
- Tiered multiplication algorithms: schoolbook → Karatsuba → NTT/FFT
- Binary GCD, fast exponentiation by squaring, product tree factorial
- GitHub Actions CI with PR label-driven workflows
- vcpkg manifest for Windows dependency management
- Six-platform release artifacts with one JAR shared by GitHub Releases and Maven

### Changed
- Number formatting now compiles Java pattern and locale metadata once and performs numeric conversion, scaling, rounding, grouping, localization, and output assembly in Native code
- Formatter result caching uses Native handle ID/version keys for BigInt and BigDeci and word keys for Int128

### Removed
- Removed `BigInt`, `BigDeci`, and `Int128` `toFormattedString(...)`; use `BigNumberFormat.ofPattern(...)`, `readable()`, or `scientific()`
