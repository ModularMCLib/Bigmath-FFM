package net.modularmclib.bigmath.ffm;

import lombok.Getter;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

@Getter
public final class BigmathFFM {

	private static final BigmathFFM INSTANCE = new BigmathFFM();

	private final Arena arena = Arena.ofAuto();
	private final SymbolLookup lookup;

	private BigmathFFM() {
		this.lookup = SymbolLookup.libraryLookup(nativeLibraryPath(), arena);
	}

	private static String nativeLibraryPath() {
		String classifier = System.getProperty("bigmath.native.classifier");
		if (classifier != null) {
			return "native/" + classifier + "/" + nativeLibName();
		}
		String path = System.getProperty("bigmath.native.path");
		if (path != null) {
			return path;
		}
		return "native/" + osClassifier() + "/" + nativeLibName();
	}

	private static String nativeLibName() {
		String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("win")) return "bigmath_ffm.dll";
		if (os.contains("mac")) return "libbigmath_ffm.dylib";
		return "libbigmath_ffm.so";
	}

	private static String osClassifier() {
		String os = System.getProperty("os.name").toLowerCase();
		String arch = System.getProperty("os.arch").toLowerCase();
		String osPart = os.contains("win") ? "windows" : os.contains("mac") ? "macos" : "linux";
		String archPart = arch.contains("amd64") || arch.contains("x86_64") ? "x86-64" : "aarch64";
		return osPart + "-" + archPart;
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
