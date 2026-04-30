# Changelog

- No changes
## [Unreleased]

### Added
- Initial project structure with Gradle version catalog and modular scripts
- BigInt wrapper for GMP `mpz_t` via Java FFM API
- BigDecimal wrapper for MPFR `mpfr_t` via Java FFM API
- Int128 type with native C++ implementation
- Locale-aware formatting for BigInt, BigDecimal, Int128
- Multi-platform CMake native library build
- Tiered multiplication algorithms: schoolbook → Karatsuba → NTT/FFT
- Binary GCD, fast exponentiation by squaring, product tree factorial
- GitHub Actions CI with PR label-driven workflows
- vcpkg manifest for Windows dependency management
