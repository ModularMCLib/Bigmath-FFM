package com.modularmc.bigmath;

import lombok.Getter;

import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Singleton bridge to the native {@code bigmath_ffm} shared library via Java FFM API.
 * <p>
 * Auto-detects the host OS and CPU architecture to locate and load the correct
 * platform-native library from the bundled resources. Provides {@link #downcall}
 * for linking native symbols and {@link #invoke} for calling them safely.
 * <p>
 * The native library path can be overridden via system property
 * {@code bigmath.native.path} (absolute file path) or the platform classifier
 * via {@code bigmath.native.classifier} (e.g. {@code linux-x86-64}).
 */
@Getter
public final class BigmathFFM {

	public static final Logger LOGGER = Logger.getLogger(BigmathFFM.class.getName());

	private static final Os CURRENT_OS = detectOs();
	private static final Arch CURRENT_ARCH = detectArch();
	private static final BigmathFFM INSTANCE = new BigmathFFM();

	private final Arena arena = Arena.ofAuto();
	private final Linker linker = Linker.nativeLinker();
	private final SymbolLookup lookup;
	private final Map<DowncallKey, MethodHandle> downcallCache = new ConcurrentHashMap<>();

	private BigmathFFM() {
		this.lookup = loadLibrary();
	}

	private record DowncallKey(String name, FunctionDescriptor descriptor) {}

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

	/**
	 * Returns the platform classifier string for the current host,
	 * e.g. {@code windows-x86-64} or {@code android-arm64-v8a}.
	 */
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

	private static void preloadWindowsDependencies(Path nativeDir, String libName) {
		if (CURRENT_OS != Os.WINDOWS || nativeDir == null || !Files.isDirectory(nativeDir)) {
			return;
		}

		try (Stream<Path> files = Files.list(nativeDir)) {
			List<Path> dependencies = files
				.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".dll"))
				.filter(path -> !path.getFileName().toString().equalsIgnoreCase(libName))
				.sorted(Comparator
					.comparingInt(BigmathFFM::windowsDependencyPriority)
					.thenComparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
				.toList();

			for (Path dependency : dependencies) {
				String absolutePath = dependency.toAbsolutePath().toString();
				LOGGER.info(() -> "Preloading Windows dependency: " + absolutePath);
				System.load(absolutePath);
			}
		} catch (IOException e) {
			LOGGER.warning(() -> "Failed to enumerate Windows native dependencies in " + nativeDir + ": " + e.getMessage());
		}
	}

	private static int windowsDependencyPriority(Path path) {
		String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
		if (fileName.equals("libwinpthread-1.dll")) return 0;
		if (fileName.startsWith("libgcc")) return 1;
		if (fileName.startsWith("libstdc++")) return 2;
		if (fileName.contains("gmp")) return 3;
		if (fileName.contains("mpfr")) return 4;
		return 10;
	}

	/**
	 * Loads the platform-native shared library. Resolution order:
	 * <ol>
	 *   <li>{@code bigmath.native.path} system property (absolute file path)</li>
	 *   <li>Bundled resource at {@code native/<classifier>/<libname>}</li>
	 *   <li>{@link System#loadLibrary} fallback</li>
	 * </ol>
	 *
	 * @return a {@link SymbolLookup} over the loaded library
	 * @throws UnsatisfiedLinkError if the library cannot be found or loaded
	 */
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
			Path explicitLibPath = Path.of(explicitPath).toAbsolutePath();
			preloadWindowsDependencies(explicitLibPath.getParent(), explicitLibPath.getFileName().toString());
			System.load(explicitLibPath.toString());
			LOGGER.info(() -> "Loaded from explicit path: " + explicitPath);
			return SymbolLookup.loaderLookup();
		}

		Path nativeDir = Path.of("native", classifier);
		Path nativePath = nativeDir.resolve(libName);
		Path absolutePath = nativePath.toAbsolutePath();

		LOGGER.info(() -> "Checking: " + absolutePath + " (exists: " + Files.exists(nativePath) + ")");
		if (Files.exists(nativePath)) {
			preloadWindowsDependencies(nativeDir, libName);
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

	/**
	 * Returns the singleton instance (lazy-evaluated at class init time).
	 */
	public static BigmathFFM getInstance() {
		return INSTANCE;
	}

	/**
	 * Returns the native linker for the current platform.
	 */
	public Linker linker() {
		return linker;
	}

	/**
	 * Looks up a native function by name and creates a downcall method handle
	 * with the given function descriptor.
	 *
	 * @param name       the C symbol name
	 * @param descriptor the FFM function descriptor
	 * @return a {@link MethodHandle} bound to the native function
	 * @throws UnsatisfiedLinkError if the symbol is not found
	 */
	public MethodHandle downcall(String name, FunctionDescriptor descriptor) {
		return downcallCache.computeIfAbsent(new DowncallKey(name, descriptor), key ->
			lookup.find(key.name())
				.map(addr -> linker.downcallHandle(addr, key.descriptor()))
				.orElseThrow(() -> new UnsatisfiedLinkError("Symbol not found: " + key.name()))
		);
	}

	/**
	 * Invokes a method handle with the given arguments, wrapping checked
	 * exceptions in {@link RuntimeException}.
	 *
	 * @param handle the method handle to invoke
	 * @param args   the arguments to pass
	 * @return the return value of the native call
	 */
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
