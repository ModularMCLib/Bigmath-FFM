package com.modularmc.bigmath;

import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
public final class BigmathFFM {

	public static final Logger LOGGER = Logger.getLogger(BigmathFFM.class.getName());
	static final Set<String> SUPPORTED_NATIVE_CLASSIFIERS = Set.of(
		"linux-x86-64",
		"linux-aarch64",
		"macos-x86-64",
		"macos-aarch64",
		"windows-x86-64",
		"windows-aarch64"
	);

	static final Os CURRENT_OS = detectOs();
	static final Arch CURRENT_ARCH = detectArch();
	static final BigmathFFM INSTANCE = new BigmathFFM();

	final Arena arena = Arena.ofAuto();
	final Linker linker = Linker.nativeLinker();
	final SymbolLookup lookup;
	final Map<DowncallKey, MethodHandle> downcallCache = new ConcurrentHashMap<>();
	final MethodHandle cudaAvailableHandle;
	final MethodHandle cudaDeviceCountHandle;
	final MethodHandle cudaProbeCountHandle;
	final MethodHandle cudaMultiplyCountHandle;
	final MethodHandle cudaDeviceNameHandle;
	final MethodHandle cudaStatusMessageHandle;
	static final String NATIVE_RESOURCE_ROOT = "native";

	BigmathFFM() {
		this.lookup = loadLibrary();
		this.cudaAvailableHandle = optionalDowncall("bigmath_cuda_available", FunctionDescriptors.CUDA_INT);
		this.cudaDeviceCountHandle = optionalDowncall("bigmath_cuda_device_count", FunctionDescriptors.CUDA_INT);
		this.cudaProbeCountHandle = optionalDowncall("bigmath_cuda_probe_count", FunctionDescriptors.CUDA_INT);
		this.cudaMultiplyCountHandle = optionalDowncall("bigmath_cuda_multiply_count", FunctionDescriptors.CUDA_INT);
		this.cudaDeviceNameHandle = optionalDowncall("bigmath_cuda_device_name", FunctionDescriptors.CUDA_STRING);
		this.cudaStatusMessageHandle = optionalDowncall("bigmath_cuda_status_message", FunctionDescriptors.CUDA_STRING);
	}

	record DowncallKey(String name, FunctionDescriptor descriptor) {}

	enum Os {
		LINUX, MACOS, WINDOWS
	}

	enum Arch {
		X86_64, AARCH64
	}

	static Os detectOs() {
		String rawName = System.getProperty("os.name", "");
		String name = rawName.toLowerCase(Locale.ROOT);
		String vendor = System.getProperty("java.vendor", "").toLowerCase(Locale.ROOT);
		String runtimeName = System.getProperty("java.runtime.name", "").toLowerCase(Locale.ROOT);
		String vmName = System.getProperty("java.vm.name", "").toLowerCase(Locale.ROOT);
		if (name.contains("android") || vendor.contains("android") || runtimeName.contains("android") ||
			vmName.contains("android") || vmName.equals("dalvik")) {
			throw new UnsupportedOperationException("Android is not a supported Bigmath FFM runtime platform");
		}
		if (name.contains("mac") || name.contains("darwin")) return Os.MACOS;
		if (name.contains("win")) return Os.WINDOWS;
		if (name.contains("linux")) return Os.LINUX;
		throw new UnsupportedOperationException("Unsupported Bigmath FFM operating system: " + rawName);
	}

	static Arch detectArch() {
		String rawArch = System.getProperty("os.arch", "");
		String arch = rawArch.toLowerCase(Locale.ROOT);
		return switch (arch) {
			case "amd64", "x86_64", "x86-64" -> Arch.X86_64;
			case "aarch64", "arm64", "armv8", "armv8-a" -> Arch.AARCH64;
			default -> throw new UnsupportedOperationException("Unsupported Bigmath FFM architecture: " + rawArch);
		};
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
	 * Supported results are:
	 * <ul>
	 *   <li>{@code linux-x86-64} and {@code linux-aarch64}</li>
	 *   <li>{@code macos-x86-64} and {@code macos-aarch64}</li>
	 *   <li>{@code windows-x86-64} and {@code windows-aarch64}</li>
	 * </ul>
	 *
	 * @return the native classifier for the current process
	 */
	static String platformClassifier() {
		String os = switch (CURRENT_OS) {
			case LINUX -> "linux";
			case MACOS -> "macos";
			case WINDOWS -> "windows";
		};
		String arch = switch (CURRENT_ARCH) {
			case X86_64 -> "x86-64";
			case AARCH64 -> "aarch64";
		};
		return os + "-" + arch;
	}

	static String nativeClassifier() {
		String override = System.getProperty("bigmath.native.classifier");
		if (override == null) {
			return platformClassifier();
		}
		if (!SUPPORTED_NATIVE_CLASSIFIERS.contains(override)) {
			throw new IllegalArgumentException(
				"Unsupported bigmath.native.classifier: " + override + ". Supported classifiers: " +
					SUPPORTED_NATIVE_CLASSIFIERS
			);
		}
		return override;
	}

	static String platformLibName() {
		return switch (CURRENT_OS) {
			case WINDOWS -> "bigmath_ffm.dll";
			case MACOS -> "libbigmath_ffm.dylib";
			default -> "libbigmath_ffm.so";
		};
	}

	static void preloadWindowsDependencies(Path nativeDir, String libName) {
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

	static int windowsDependencyPriority(Path path) {
		String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
		if (fileName.equals("libwinpthread-1.dll")) return 0;
		if (fileName.startsWith("libgcc")) return 1;
		if (fileName.startsWith("libstdc++")) return 2;
		if (fileName.contains("gmp")) return 3;
		if (fileName.contains("mpfr")) return 4;
		return 10;
	}

	record ResourceDirectory(Path path, FileSystem ownedFileSystem) implements AutoCloseable {
		@Override
		public void close() throws IOException {
			if (ownedFileSystem != null) {
				ownedFileSystem.close();
			}
		}
	}

	/**
	 * Loads the platform-native shared library. Resolution order:
	 * <ol>
	 *   <li>{@code bigmath.native.path} system property (absolute file path)</li>
	 *   <li>File-system path at {@code native/<classifier>/<libname>}</li>
	 *   <li>Bundled resource at {@code native/<classifier>/<libname>}</li>
	 *   <li>{@link System#loadLibrary} fallback</li>
	 * </ol>
	 *
	 * @return a {@link SymbolLookup} over the loaded library
	 * @throws UnsatisfiedLinkError if the library cannot be found or loaded
	 */
	static SymbolLookup loadLibrary() {
		String classifier = nativeClassifier();
		String libName = platformLibName();

		LOGGER.info(() -> "OS: " + CURRENT_OS + ", Arch: " + CURRENT_ARCH + ", Classifier: " + classifier + ", Lib: " + libName);

		String explicitPath = System.getProperty("bigmath.native.path");
		if (explicitPath != null) {
			LOGGER.info(() -> "Trying explicit path: " + explicitPath);
			Path explicitLibPath = Path.of(explicitPath).toAbsolutePath();
			preloadWindowsDependencies(explicitLibPath.getParent(), explicitLibPath.getFileName().toString());
			System.load(explicitLibPath.toString());
			LOGGER.info(() -> "Loaded from explicit path: " + explicitPath);
			return SymbolLookup.loaderLookup();
		}

		Path nativeDir = Path.of(NATIVE_RESOURCE_ROOT, classifier);
		Path nativePath = nativeDir.resolve(libName);
		Path absolutePath = nativePath.toAbsolutePath();

		LOGGER.info(() -> "Checking: " + absolutePath + " (exists: " + Files.exists(nativePath) + ")");
		if (Files.exists(nativePath)) {
			preloadWindowsDependencies(nativeDir, libName);
			System.load(absolutePath.toString());
			LOGGER.info(() -> "Loaded from: " + absolutePath);
			return SymbolLookup.loaderLookup();
		}

		Path unpackedPath = unpackBundledNativeDirectory(classifier, libName);
		if (unpackedPath != null) {
			Path unpackedDir = unpackedPath.getParent();
			LOGGER.info(() -> "Loaded bundled native resource via Java FileSystem: " + unpackedPath);
			preloadWindowsDependencies(unpackedDir, libName);
			System.load(unpackedPath.toAbsolutePath().toString());
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

		LOGGER.severe(() -> "Failed to load " + libName + " for " + classifier +
			". Tried: " + absolutePath + " and java.library.path=" + System.getProperty("java.library.path"));
		throw new UnsatisfiedLinkError(
			"Failed to load " + libName + " for " + classifier + ". " +
			"Tried: " + absolutePath + " and java.library.path"
		);
	}

	static Path unpackBundledNativeDirectory(String classifier, String libName) {
		String resourceName = NATIVE_RESOURCE_ROOT + "/" + classifier;
		URL resource = BigmathFFM.class.getClassLoader().getResource(resourceName);
		if (resource == null) {
			LOGGER.info(() -> "Bundled native resource not found: " + resourceName);
			return null;
		}

		try (ResourceDirectory resourceDirectory = openResourceDirectory(resource.toURI())) {
			Path sourceDir = resourceDirectory.path();
			Path sourceLib = sourceDir.resolve(libName);
			if (!Files.isRegularFile(sourceLib)) {
				LOGGER.warning(() -> "Bundled native library resource not found: " + sourceLib);
				return null;
			}

			Path targetDir = Files.createTempDirectory("bigmath-ffm-" + classifier + "-");
			targetDir.toFile().deleteOnExit();
			copyBundledNativeDirectory(sourceDir, targetDir);
			return targetDir.resolve(libName);
		} catch (IOException | IllegalArgumentException | URISyntaxException e) {
			LOGGER.warning(() -> "Failed to unpack bundled native resources for " + classifier + ": " + e.getMessage());
			return null;
		}
	}

	static ResourceDirectory openResourceDirectory(URI resourceUri) throws IOException {
		if ("jar".equalsIgnoreCase(resourceUri.getScheme())) {
			String rawUri = resourceUri.toString();
			int separator = rawUri.indexOf('!');
			if (separator < 0) {
				throw new IOException("Invalid jar resource URI: " + resourceUri);
			}

			URI fileSystemUri = URI.create(rawUri.substring(0, separator));
			try {
				FileSystem fileSystem = FileSystems.newFileSystem(fileSystemUri, Map.of());
				return new ResourceDirectory(Path.of(resourceUri), fileSystem);
			} catch (FileSystemAlreadyExistsException ignored) {
				return new ResourceDirectory(Path.of(resourceUri), null);
			}
		}
		if ("file".equalsIgnoreCase(resourceUri.getScheme())) {
			return new ResourceDirectory(Path.of(resourceUri), null);
		}
		throw new IOException("Unsupported native resource URI scheme: " + resourceUri.getScheme());
	}

	static void copyBundledNativeDirectory(Path sourceDir, Path targetDir) throws IOException {
		try (Stream<Path> paths = Files.walk(sourceDir)) {
			for (Path source : paths.filter(Files::isRegularFile).toList()) {
				Path relativePath = sourceDir.relativize(source);
				Path target = targetDir.resolve(relativePath.toString());
				Path parent = target.getParent();
				if (parent != null) {
					Files.createDirectories(parent);
				}
				Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
				target.toFile().deleteOnExit();
			}
		}
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

	static boolean cudaAvailable() {
		if (getInstance().cudaAvailableHandle == null) {
			return false;
		}
		return getInstance().invokeCudaInt(getInstance().cudaAvailableHandle) != 0;
	}

	static int cudaDeviceCount() {
		if (getInstance().cudaDeviceCountHandle == null) {
			return 0;
		}
		return getInstance().invokeCudaInt(getInstance().cudaDeviceCountHandle);
	}

	static int cudaProbeCount() {
		if (getInstance().cudaProbeCountHandle == null) {
			return 1;
		}
		return getInstance().invokeCudaInt(getInstance().cudaProbeCountHandle);
	}

	static int cudaMultiplyCount() {
		if (getInstance().cudaMultiplyCountHandle == null) {
			return 0;
		}
		return getInstance().invokeCudaInt(getInstance().cudaMultiplyCountHandle);
	}

	static String cudaDeviceName() {
		if (getInstance().cudaDeviceNameHandle == null) {
			return "";
		}
		return getInstance().invokeCudaString(getInstance().cudaDeviceNameHandle);
	}

	static String cudaStatusMessage() {
		if (getInstance().cudaStatusMessageHandle == null) {
			return "CUDA diagnostics are not available in this native library";
		}
		return getInstance().invokeCudaString(getInstance().cudaStatusMessageHandle);
	}

	MethodHandle optionalDowncall(String name, FunctionDescriptor descriptor) {
		return lookup.find(name)
			.map(symbol -> linker.downcallHandle(symbol, descriptor))
			.orElse(null);
	}

	int invokeCudaInt(MethodHandle handle) {
		try {
			return (int) handle.invokeExact();
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	String invokeCudaString(MethodHandle handle) {
		try {
			MemorySegment value = (MemorySegment) handle.invokeExact();
			if (value.equals(MemorySegment.NULL)) {
				return "";
			}
			return value.reinterpret(Long.MAX_VALUE).getString(0);
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}
}
