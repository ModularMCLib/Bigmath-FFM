package com.modularmc.bigmath;

import org.jspecify.annotations.NullMarked;

import java.util.Objects;

/** Immutable snapshot of Native runtime state. No user numeric values are recorded. */
@NullMarked
public record RuntimeDiagnostics(
		int nativeAbiVersion,
		String nativeBuildId,
		long nativeCapabilities,
		CudaDiagnostics cuda,
		ProductCacheDiagnostics productCache,
		long cpuFallbackCount
) {

	public RuntimeDiagnostics {
		Objects.requireNonNull(nativeBuildId, "nativeBuildId");
		Objects.requireNonNull(cuda, "cuda");
		Objects.requireNonNull(productCache, "productCache");
	}

	public enum CalibrationStatus {
		NOT_STARTED,
		RUNNING,
		READY,
		FAILED,
		UNAVAILABLE;

		static CalibrationStatus fromNative(int value) {
			return value >= 0 && value < values().length ? values()[value] : FAILED;
		}
	}

	public record CudaDiagnostics(
			int deviceCount,
			int selectedDevice,
			String deviceName,
			String statusMessage,
			CalibrationStatus calibrationStatus,
			RuntimeOptions.CudaBackend activeBackend,
			boolean nttEnabled,
			long balancedThresholdBits,
			long squareThresholdBits,
			long workspaceBudgetBytes,
			long workspaceInUseBytes,
			int workspaceCapacity,
			int workspaceInUse
	) {
		public CudaDiagnostics {
			Objects.requireNonNull(deviceName, "deviceName");
			Objects.requireNonNull(statusMessage, "statusMessage");
			Objects.requireNonNull(calibrationStatus, "calibrationStatus");
			Objects.requireNonNull(activeBackend, "activeBackend");
		}
	}

	public record ProductCacheDiagnostics(
			long hits,
			long misses,
			long admissions,
			long evictions,
			long bytes
	) {
	}
}
