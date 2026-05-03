## Third-Party Native Component Notices

Bigmath-FFM may redistribute platform-native shared libraries alongside the
project's own `bigmath_ffm` library. This file documents the third-party
components that can be bundled in `native/<classifier>/` directories and in
published artifacts.

### Replacement and Relinking

Bigmath-FFM loads native libraries from disk rather than embedding them into a
statically linked executable. End users can replace compatible shared libraries
by:

- setting `-Dbigmath.native.path=/absolute/path/to/library`
- replacing files under `native/<classifier>/`
- providing an alternative library via the normal platform loader search path

Relevant loading logic lives in:

- `src/main/java/com/modularmc/bigmath/BigmathFFM.java`
- `src/main/cpp/CMakeLists.txt`
- `vcpkg.json`

### Components

| Component             | Typical bundled files                           | License / terms                                                                                                                                      | Source / upstream                                            |
|-----------------------|-------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------|
| GNU MP (GMP)          | `gmp-10.dll`, `libgmp.so.10`, `libgmp.10.dylib` | Dual-licensed by upstream under LGPL-3.0-or-later or GPL-2.0-or-later. Bigmath-FFM relies on the LGPL option when redistributing the shared library. | https://gmplib.org/                                          |
| GNU MPFR              | `mpfr-6.dll`, `libmpfr.so.6`, `libmpfr.6.dylib` | LGPL-3.0-or-later                                                                                                                                    | https://www.mpfr.org/                                        |
| GNU libstdc++ runtime | `libstdc++-6.dll`                               | GPL-3.0-or-later with GCC Runtime Library Exception 3.1                                                                                              | https://gcc.gnu.org/onlinedocs/libstdc++/manual/license.html |
| GNU libgcc runtime    | `libgcc_s_*.dll`                                | GPL-3.0-or-later with GCC Runtime Library Exception 3.1                                                                                              | https://gcc.gnu.org/onlinedocs/libstdc++/manual/license.html |
| MinGW-w64 winpthreads | `libwinpthread-1.dll`                           | Upstream `COPYING.winpthreads` notice as shipped by MinGW-w64                                                                                        | https://www.mingw-w64.org/                                   |
| Microsoft VC++ runtime | `msvcp140.dll`, `vcruntime140*.dll`, `concrt140.dll` | Redistributable in unmodified form by licensed Visual Studio users under the Visual Studio 2022 distributable code terms                            | https://learn.microsoft.com/en-gb/visualstudio/releases/2022/redistribution |

### License Texts

- The project root `LICENSE` file contains the LGPL-3.0 text used by
  Bigmath-FFM and also applicable to redistributed GMP/MPFR binaries when the
  LGPL option is used.
- GCC runtime library terms are documented by upstream in the GCC Runtime
  Library Exception 3.1:
  https://www.gnu.org/licenses/gcc-exception-3.1.txt
- GNU GPL version 3 text for GCC runtime components:
  https://www.gnu.org/licenses/gpl-3.0.txt
- MinGW-w64 winpthreads licensing notice is available from the upstream project
  and is also shipped in typical MinGW-w64 toolchains as
  `licenses/winpthreads/COPYING.winpthreads`.
- If you redistribute Windows runtime DLLs built from a MinGW-w64 toolchain,
  ship the originating toolchain's `COPYING.RUNTIME` and
  `COPYING.winpthreads` files alongside your binary artifact.
- When native artifacts bundle third-party runtime libraries, Bigmath-FFM also
  stages a per-classifier `licenses/` directory next to those binaries so the
  unpacked package carries the corresponding notices with it.

### Corresponding Source

Bigmath-FFM builds its native bridge from source in this repository and links
against third-party libraries obtained from standard package managers or
toolchains. Corresponding source code for the third-party components listed
above is available from their upstream project sites:

- GMP source releases: https://gmplib.org/
- MPFR source releases: https://www.mpfr.org/
- GCC sources and runtime exception text: https://gcc.gnu.org/
- MinGW-w64 sources: https://www.mingw-w64.org/

This repository's native build configuration is sufficient to rebuild the
Bigmath-FFM native library against compatible versions of those shared
libraries.

### No Warranty

Third-party components are provided subject to their own license terms and
without additional warranty from Bigmath-FFM beyond what is stated by this
project and the respective upstream licensors.
