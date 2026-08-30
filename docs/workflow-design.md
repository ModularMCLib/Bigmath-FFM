# CI/CD Workflow Design

## 六平台发布契约

发布物只包含下列 classifier，名称与运行时平台识别保持一一对应：

| Runner | Classifier | 主库 | 编译器 / 依赖 |
|---|---|---|---|
| `ubuntu-latest` | `linux-x86-64` | `libbigmath_ffm.so` | 系统 C/C++ 编译器，GMP/MPFR |
| `ubuntu-24.04-arm` | `linux-aarch64` | `libbigmath_ffm.so` | 系统 C/C++ 编译器，GMP/MPFR |
| `macos-15-intel` | `macos-x86-64` | `libbigmath_ffm.dylib` | Apple Clang，Homebrew GMP/MPFR |
| `macos-15` | `macos-aarch64` | `libbigmath_ffm.dylib` | Apple Clang，Homebrew GMP/MPFR |
| `windows-2022` | `windows-x86-64` | `bigmath_ffm.dll` | MSVC，`x64-windows` |
| `windows-11-arm` | `windows-aarch64` | `bigmath_ffm.dll` | MSVC，`arm64-windows` |

Windows 两个架构均使用 MSVC 和对应 vcpkg triplet；找不到目标架构 runtime
时必须失败，不得从另一架构目录回退。最终目录保留完整的非系统 DLL 依赖闭包，
包括 MSVC runtime，以及 GMP、MPFR 或其他依赖实际引用的 GNU libstdc++、libgcc 和
MinGW-w64 winpthreads runtime；不能仅根据主库编译器删除另一运行时家族。

## Native artifact 单一构建入口

`.github/workflows/native-artifacts.yml` 是六平台 Native artifact 的唯一完整构建入口。
Snapshot、PR 的 `ci:native` 检查和正式 Release 都以 reusable workflow 方式调用它。
矩阵中的每个平台只构建一次，然后上传一个 `native-<classifier>` artifact。

每个 artifact 包含：

```text
native/<classifier>/
├── 主库
├── GMP / MPFR / 平台 runtime 依赖
└── licenses/
```

## Gradle 聚合

`releaseJar` 是发布专用 Gradle 任务。它只依赖 Java 编译，并从下载到工作区的六个
artifact 聚合 JAR；它不依赖 `processResources`、`copyNativeLib`、`buildNative` 或
`cmakeConfigure`，也不构建 Kotlin module。因此 package job 不会在聚合 runner 上
再次触发本机 Native 构建。package job 下载六个平台产物后只运行该 Gradle 任务。

## GitHub Release 与 Maven

正式发布时，package job 只生成一次最终 JAR，并将 JAR 与 `release.sha256` 一起上传为
`release-jar` Actions artifact。GitHub Release job 和 Maven reusable workflow分别下载
同一个 artifact。Maven publication 通过
`releaseJarFile` 指向已下载的 JAR，不重新运行 `releaseJar` 或任何 Native task。

Snapshot 采用相同六平台 Gradle 聚合，但只发布到 `latest` pre-release。

## PR 标签

| 标签 | 作用 |
|---|---|
| `ci:native` | 运行完整六平台 Native artifact 矩阵 |
| `ci:perf` | 运行按需性能基准 |
| `ci:skip` | 跳过可选 CI |
| `ignore changelog` | 从 release notes 排除 PR |
