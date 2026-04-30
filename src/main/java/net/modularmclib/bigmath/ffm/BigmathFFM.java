package net.modularmclib.bigmath.ffm;

import lombok.Getter;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;

@Getter
public final class BigmathFFM {

	private static final BigmathFFM INSTANCE = new BigmathFFM();

	private final Arena arena = Arena.ofAuto();
	private final SymbolLookup lookup;

	private BigmathFFM() {
		this.lookup = loadLibrary();
	}

	private enum Os {
		LINUX, MACOS, WINDOWS, ANDROID
	}

	private enum Arch {
		X86_64, AARCH64, UNKNOWN
	}

	private static final Os CURRENT_OS = detectOs();
	private static final Arch CURRENT_ARCH = detectArch();

	private static Os detectOs() {
		String name = System.getProperty("os.name", "").toLowerCase();
		if (name.contains("win")) return Os.WINDOWS;
		if (name.contains("mac") || name.contains("darwin")) return Os.MACOS;
		if (name.contains("android") || "android".equalsIgnoreCase(System.getProperty("java.vendor", ""))) {
			return Os.ANDROID;
		}
		return Os.LINUX;
	}

	private static Arch detectArch() {
		String arch = System.getProperty("os.arch", "").toLowerCase();
		if (arch.contains("amd64") || arch.contains("x86_64") || arch.contains("x86-64")) return Arch.X86_64;
		if (arch.contains("aarch64") || arch.contains("arm64") || arch.contains("armv8")) return Arch.AARCH64;
		return Arch.UNKNOWN;
	}

	public static String platformClassifier() {
		StringBuilder sb = new StringBuilder();
		switch (CURRENT_OS) {
			case ANDROID -> sb.append("android");
			case LINUX -> sb.append("linux");
			case MACOS -> sb.append("macos");
			case WINDOWS -> sb.append("windows");
		}
		sb.append("-");
		switch (CURRENT_ARCH) {
			case X86_64 -> sb.append("x86-64");
			case AARCH64 -> {
				if (CURRENT_OS == Os.ANDROID) {
					sb.append("arm64-v8a");
				} else {
					sb.append("aarch64");
				}
			}
			case UNKNOWN -> sb.append("unknown");
		}
		return sb.toString();
	}

	private static String platformLibName() {
		return switch (CURRENT_OS) {
			case WINDOWS -> "bigmath_ffm.dll";
			case MACOS -> "libbigmath_ffm.dylib";
			default -> "libbigmath_ffm.so";
		};
	}

	private static SymbolLookup loadLibrary() {
		String classifier = System.getProperty("bigmath.native.classifier");
		if (classifier == null) {
			classifier = platformClassifier();
		}

		String explicitPath = System.getProperty("bigmath.native.path");
		if (explicitPath != null) {
			return SymbolLookup.libraryLookup(explicitPath, Arena.ofAuto());
		}

		String[] searchPaths = {
			"native/" + classifier + "/" + platformLibName(),
			platformLibName(),
		};

		for (String path : searchPaths) {
			try {
				Path p = Path.of(path);
				if (path.equals(platformLibName()) || Files.exists(p)) {
					return SymbolLookup.libraryLookup(p, Arena.ofAuto());
				}
			} catch (IllegalArgumentException e) {
				// library not found at this path, try next
			}
		}

		// Try System.loadLibrary as last resort (uses java.library.path)
		try {
			System.loadLibrary("bigmath_ffm");
			return SymbolLookup.libraryLookup(platformLibName(), Arena.ofAuto());
		} catch (UnsatisfiedLinkError e) {
			// give up
		}

		throw new UnsatisfiedLinkError(
			"Failed to load native library '" + platformLibName() + "' " +
			"for platform '" + platformClassifier() + "' " +
			"(os=" + CURRENT_OS + ", arch=" + CURRENT_ARCH + "). " +
			"Ensure the native library is built and placed in the correct path, " +
			"or set -Dbigmath.native.path=/full/path/to/" + platformLibName()
		);
	}

	public static BigmathFFM getInstance() {
		return INSTANCE;
	}

	public Linker linker() {
		return Linker.nativeLinker();
	}

	public MethodHandle downcall(String name, FunctionDescriptor descriptor) {
		return lookup.find(name)
				.map(addr -> linker().downcallHandle(addr, descriptor))
				.orElseThrow(() -> new UnsatisfiedLinkError("Symbol not found: " + name));
	}

	public static Object invoke(MethodHandle handle, Object... args) {
		try {
			return handle.invoke(args);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}
}
