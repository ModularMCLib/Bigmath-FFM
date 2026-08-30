package com.modularmc.bigmath;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Process-wide configuration and diagnostics entry point for the Bigmath Native runtime. */
@NullMarked
public final class BigmathRuntime {

	private static final Object LOCK = new Object();
	private static RuntimeOptions options = RuntimeOptions.builder().build();
	private static boolean initializationStarted;
	private static @Nullable CompletableFuture<RuntimeDiagnostics> initialization;

	private BigmathRuntime() {
	}

	/**
	 * Replaces the runtime options used by the first Native initialization.
	 *
	 * @throws IllegalStateException if Native initialization has already started
	 */
	public static void configure(RuntimeOptions configuredOptions) {
		Objects.requireNonNull(configuredOptions, "configuredOptions");
		synchronized (LOCK) {
			if (initializationStarted) {
				throw new IllegalStateException("Bigmath Native runtime initialization has already started");
			}
			options = configuredOptions;
		}
	}

	/** Starts Native initialization on the common asynchronous executor. Calls are idempotent. */
	public static CompletableFuture<RuntimeDiagnostics> initializeAsync() {
		synchronized (LOCK) {
			if (initialization == null) {
				initializationStarted = true;
				initialization = CompletableFuture.supplyAsync(() -> {
					BigmathFFM.getInstance();
					return diagnostics();
				});
			}
			return initialization;
		}
	}

	/** Returns a current read-only snapshot, initializing the Native runtime if necessary. */
	public static RuntimeDiagnostics diagnostics() {
		return BigmathFFM.getInstance().runtimeDiagnostics();
	}

	static RuntimeOptions beginNativeInitialization() {
		synchronized (LOCK) {
			initializationStarted = true;
			return options;
		}
	}
}
