package net.modularmclib.bigmath.ffm;

import lombok.Getter;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

@Getter
public final class BigmathFFM {

	public static final Logger LOGGER = Logger.getLogger(BigmathFFM.class.getName());

	private static final Os CURRENT_OS = detectOs();
	private static final Arch CURRENT_ARCH = detectArch();
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
		String libName = platformLibName();

		String finalClassifier = classifier;
		LOGGER.info(() -> "OS: " + CURRENT_OS + ", Arch: " + CURRENT_ARCH + ", Classifier: " + finalClassifier + ", Lib: " + libName);

		String explicitPath = System.getProperty("bigmath.native.path");
		if (explicitPath != null) {
			LOGGER.info(() -> "Trying explicit path: " + explicitPath);
			System.load(explicitPath);
			LOGGER.info(() -> "Loaded from explicit path: " + explicitPath);
			return SymbolLookup.loaderLookup();
		}

		Path nativePath = Path.of("native", classifier, libName);
		Path absolutePath = nativePath.toAbsolutePath();

		LOGGER.info(() -> "Checking: " + absolutePath + " (exists: " + Files.exists(nativePath) + ")");
		if (Files.exists(nativePath)) {
			System.load(absolutePath.toString());
			LOGGER.info(() -> "Loaded from: " + absolutePath);
			return SymbolLookup.loaderLookup();
		}

		LOGGER.info(() -> "Trying System.loadLibrary(\"bigmath_ffm\")");
		try {
			System.loadLibrary("bigmath_ffm");
			LOGGER.info(() -> "Loaded via System.loadLibrary");
			return SymbolLookup.loaderLookup();
		} catch (UnsatisfiedLinkError e) {
			LOGGER.warning(() -> "System.loadLibrary failed: " + e.getMessage());
		}

		String finalClassifier1 = classifier;
		LOGGER.severe(() -> "Failed to load " + libName + " for " + finalClassifier1 +
			". Tried: " + absolutePath + " and java.library.path=" + System.getProperty("java.library.path"));
		throw new UnsatisfiedLinkError(
			"Failed to load " + libName + " for " + classifier + ". " +
			"Tried: " + absolutePath + " and java.library.path"
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
			return handle.invokeWithArguments(args);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}
}
