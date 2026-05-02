# CI/CD Workflow Design

## Overview

```
PR 提交 → 构建 + 测试 → 合并 main → 全平台构建 → 打包 → GitHub Release
```

## 1. PR Workflow (`build-on-push.yml`)

```
pull_request
├── build (Linux x86_64)
│   ├── cmake build → native/linux-x86-64/libbigmath_ffm.so
│   ├── upload artifact: native-libs
│   │   └── path: native/linux-x86-64/libbigmath_ffm.so (精准单文件)
│   └── gradle assemble → upload JAR artifact
│
└── test (needs: build, Linux x86_64)
    ├── download artifact: native-libs
    ├── 还原到 native/linux-x86-64/libbigmath_ffm.so
    └── gradle test -PrunTests -x buildNative -x cmakeConfigure
```

**关键点：** artifact 上传时使用精准文件路径 `native/{classifier}/{libname}`，下载后直接放置到同名目录，与 `BigmathFFM.loadLibrary()` 的查找路径一致。

## 2. 主分支 Snapshot (`auto-build.yml`)

```
push to main
├── native-linux (矩阵)
│   ├── ubuntu-latest / linux-x86-64
│   └── ubuntu-24.04-arm / linux-aarch64
├── native-macos (矩阵)
│   ├── macos-14 / macos-x86-64
│   └── macos-latest / macos-aarch64
├── native-windows (矩阵)
│   └── windows-latest / windows-x86-64
│
└── package (needs: 上面全部)
    ├── download-artifact: pattern: native-*, merge-multiple
    │   └── 合并为 native/{linux,macos,windows}-{x86-64,aarch64}/{lib}
    ├── gradle assemble → JAR
    ├── 生成 changelog
    └── 发布 pre-release tag: latest
        ├── build/libs/*.jar
        └── native/**/*
```

**路径结构（合并后）：**
```
native/
  linux-x86-64/libbigmath_ffm.so
  linux-aarch64/libbigmath_ffm.so
  macos-x86-64/libbigmath_ffm.dylib
  macos-aarch64/libbigmath_ffm.dylib
  windows-x86-64/bigmath_ffm.dll
```

与 `BigmathFFM.platformClassifier()` 输出一一对应。

## 3. 手动触发原生全构建 (`native-build.yml`)

```
PR 打标签 ci:native → 触发
├── native-full (矩阵: 6 平台)
└── android-build (矩阵: arm64-v8a + x86_64)
```

仅当 PR 需要验证全平台原生兼容性时使用。

## 3.1 性能基准 (`performance-benchmarks.yml`)

```
PR 打标签 ci:perf → 触发
└── benchmark (ubuntu-latest)
    ├── 安装 GMP / MPFR / CMake
    ├── ./gradlew jmh
    └── 上传 build/reports/jmh/**
```

仅当 PR 需要查看 JMH 性能结果时使用，避免拖慢普通 PR 的默认检查。

## 4. 标签体系

| 标签 | 颜色 | 触发 |
|------|------|------|
| `ci:native` | `#0E8A16` | 全平台原生构建 |
| `ci:android` | `#3DDC84` | Android NDK 交叉编译 |
| `ci:perf` | `#1D76DB` | Linux JMH 性能测试 |
| `ci:skip` | `#CCCCCC` | 跳过 CI |

| 标签 | 用途 |
|------|------|
| `do not merge` | 阻止合并 |
| `Tests: Passed/Failed` | 自动标记测试结果 |
| `type:*` | PR 类型 (feature/bugfix/refactor/tests/build/ci/docs) |
| `ignore changelog` | 排除出 release notes |

## 5. Native Library Loading (`BigmathFFM.loadLibrary()`)

```java
// 加载顺序
1. -Dbigmath.native.path=/absolute/path → System.load() + loaderLookup()
2. native/{platformClassifier}/{libName} → Files.exists → System.load()
3. System.loadLibrary("bigmath_ffm") → java.library.path
```

路径示例：
- Linux x86_64: `native/linux-x86-64/libbigmath_ffm.so`
- macOS aarch64: `native/macos-aarch64/libbigmath_ffm.dylib`
- Windows x86_64: `native/windows-x86-64/bigmath_ffm.dll`
- Android aarch64: `native/android-arm64-v8a/libbigmath_ffm.so`

## 6. 发布版本管理

```
正式 Release (released)
├── 生成 changelog (mikepenz/release-changelog-builder)
└── publish.yml
    ├── build → Maven publish
    ├── upload JAR → GitHub Release
    └── bump-version-and-changelog
        ├── gradle.properties 版本号 +1
        ├── CHANGELOG.md 插入新版本
        └── 创建 PR "Post-release: bump version"

预发布 (prereleased)
└── build + upload JAR (无 Maven publish, 无 bump)

Snapshot (push to main)
└── auto-build.yml → pre-release tag: latest
```
