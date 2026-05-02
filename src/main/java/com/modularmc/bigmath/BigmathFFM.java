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
 * Singleton bridge between the Java API and the native {@code bigmath_ffm}
 * shared library.
 * <p>
 * This type centralizes all Foreign Function and Memory API bootstrap work for
 * the project:
 * <ul>
 *   <li>detecting the host operating system and CPU architecture</li>
 *   <li>mapping that host into the library classifier used by packaged native resources</li>
 *   <li>loading the correct shared library and any required platform-specific dependencies</li>
 *   <li>resolving native symbols into cached downcall {@link MethodHandle method handles}</li>
 * </ul>
 * <p>
 * The library resolution flow is intentionally predictable so that local
 * development, CI, unpacked snapshot artifacts, and embedded runtime loading
 * all follow the same contract. Callers normally access the singleton via
 * {@link #getInstance()} and then obtain pre-linked native entry points through
 * {@link #downcall(String, FunctionDescriptor)}.
 * <p>
 * Two system properties can be used to override the default lookup behavior:
 * <ul>
 *   <li>{@code bigmath.native.path}: absolute path to a specific native library file</li>
 *   <li>{@code bigmath.native.classifier}: explicit classifier such as {@code linux-x86-64}</li>
 * </ul>
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
	 * Returns the packaged native classifier for the current runtime.
	 * <p>
	 * The classifier is the directory name used under {@code native/} inside the
	 * published JAR and is also the default convention for local unpacked test
	 * layouts. The value is derived from the detected operating system and
	 * architecture pair and therefore stays aligned with native artifact
	 * packaging.
	 * <p>
	 * Typical results include:
	 * <ul>
	 *   <li>{@code windows-x86-64}</li>
	 *   <li>{@code linux-aarch64}</li>
	 *   <li>{@code android-arm64-v8a}</li>
	 * </ul>
	 *
	 * @return the native classifier for the current process
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
	 * Returns the singleton native bridge instance.
	 * <p>
	 * The instance is created during class initialization so native loading and
	 * symbol lookup infrastructure are established once and then reused across
	 * all wrapper types such as {@link BigInt}, {@link BigDeci}, and
	 * {@link Int128}.
	 *
	 * @return the shared {@code BigmathFFM} instance
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
	 * Resolves a native symbol and returns a cached downcall handle for it.
	 * <p>
	 * Repeated lookups of the same symbol and {@link FunctionDescriptor}
	 * combination reuse the previously linked handle, which keeps higher-level
	 * numeric wrappers from paying repeated linker setup costs on hot paths.
	 *
	 * @param name the exported C symbol name
	 * @param descriptor the exact FFM function descriptor expected by the symbol
	 * @return a cached {@link MethodHandle} bound to the requested native symbol
	 * @throws UnsatisfiedLinkError if the symbol cannot be resolved from the loaded library
	 */
	public MethodHandle downcall(String name, FunctionDescriptor descriptor) {
		return downcallCache.computeIfAbsent(new DowncallKey(name, descriptor), key ->
			lookup.find(key.name())
				.map(addr -> linker.downcallHandle(addr, key.descriptor()))
				.orElseThrow(() -> new UnsatisfiedLinkError("Symbol not found: " + key.name()))
		);
	}

	/**
	 * Invokes a linked native method handle using varargs.
	 * <p>
	 * This helper keeps call sites concise in places where a strongly typed
	 * {@code invokeExact} signature would add a large amount of local ceremony.
	 * Runtime exceptions and errors are preserved as-is, while checked
	 * throwables from the reflective invocation path are wrapped in a
	 * {@link RuntimeException}.
	 *
	 * @param handle the linked native method handle to invoke
	 * @param args the arguments to forward to the native call
	 * @return the value returned by the target method handle
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
