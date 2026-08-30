package com.modularmc.bigmath;

import org.jspecify.annotations.NullMarked;

import java.time.Duration;
import java.util.Objects;

/** Immutable options captured when the Bigmath Native runtime is initialized. */
@NullMarked
public final class RuntimeOptions {

	static final int AUTO_DEVICE = -1;
	private static final long MEBIBYTE = 1024L * 1024L;

	private final boolean productCacheEnabled;
	private final long cpuProductCacheBytes;
	private final double gpuWorkspaceFraction;
	private final long gpuWorkspaceMaxBytes;
	private final Duration calibrationDuration;
	private final int cudaDevice;
	private final CudaBackend cudaBackend;

	private RuntimeOptions(Builder builder) {
		this.productCacheEnabled = builder.productCacheEnabled;
		this.cpuProductCacheBytes = builder.cpuProductCacheBytes;
		this.gpuWorkspaceFraction = builder.gpuWorkspaceFraction;
		this.gpuWorkspaceMaxBytes = builder.gpuWorkspaceMaxBytes;
		this.calibrationDuration = builder.calibrationDuration;
		this.cudaDevice = builder.cudaDevice;
		this.cudaBackend = builder.cudaBackend;
	}

	/** @return a builder initialized with the library defaults */
	public static Builder builder() {
		return new Builder();
	}

	/** @return whether the host product result cache is enabled */
	public boolean productCacheEnabled() {
		return productCacheEnabled;
	}

	/** @return maximum retained host product bytes */
	public long cpuProductCacheBytes() {
		return cpuProductCacheBytes;
	}

	/** @return fraction of free device memory available to the workspace pool */
	public double gpuWorkspaceFraction() {
		return gpuWorkspaceFraction;
	}

	/** @return absolute upper bound for the device workspace pool */
	public long gpuWorkspaceMaxBytes() {
		return gpuWorkspaceMaxBytes;
	}

	/** @return background calibration time budget */
	public Duration calibrationDuration() {
		return calibrationDuration;
	}

	/** @return {@code -1} for automatic selection, otherwise the configured CUDA device */
	public int cudaDevice() {
		return cudaDevice;
	}

	/** @return the requested CUDA backend policy */
	public CudaBackend cudaBackend() {
		return cudaBackend;
	}

	/** Runtime backend policy. Explicit CUDA modes are intended for diagnostics and benchmarks. */
	public enum CudaBackend {
		AUTO,
		CPU,
		CUFFT,
		NTT;

		static CudaBackend fromNative(int value) {
			return value >= 0 && value < values().length ? values()[value] : CPU;
		}
	}

	/** Builds validated runtime options. The builder is not thread-safe. */
	public static final class Builder {

		private boolean productCacheEnabled = true;
		private long cpuProductCacheBytes = 64L * MEBIBYTE;
		private double gpuWorkspaceFraction = 0.25;
		private long gpuWorkspaceMaxBytes = 512L * MEBIBYTE;
		private Duration calibrationDuration = Duration.ofSeconds(10);
		private int cudaDevice = AUTO_DEVICE;
		private CudaBackend cudaBackend = CudaBackend.AUTO;

		private Builder() {
		}

		/** Enables or disables host product result caching. */
		public Builder productCacheEnabled(boolean enabled) {
			this.productCacheEnabled = enabled;
			return this;
		}

		/** Sets the maximum host product result bytes retained by the cache. */
		public Builder cpuProductCacheBytes(long bytes) {
			if (bytes < 0) throw new IllegalArgumentException("CPU product cache bytes must be non-negative");
			this.cpuProductCacheBytes = bytes;
			return this;
		}

		/** Sets the fraction of free device memory available to pooled workspaces. */
		public Builder gpuWorkspaceFraction(double fraction) {
			if (!Double.isFinite(fraction) || fraction <= 0.0 || fraction > 1.0) {
				throw new IllegalArgumentException("GPU workspace fraction must be in (0, 1]");
			}
			this.gpuWorkspaceFraction = fraction;
			return this;
		}

		/** Sets the absolute device workspace pool limit. */
		public Builder gpuWorkspaceMaxBytes(long bytes) {
			if (bytes <= 0) throw new IllegalArgumentException("GPU workspace limit must be positive");
			this.gpuWorkspaceMaxBytes = bytes;
			return this;
		}

		/** Sets the background calibration time budget. */
		public Builder calibrationDuration(Duration duration) {
			Objects.requireNonNull(duration, "duration");
			if (duration.isZero() || duration.isNegative()) {
				throw new IllegalArgumentException("Calibration duration must be positive");
			}
			duration.toMillis();
			this.calibrationDuration = duration;
			return this;
		}

		/** Selects the automatically chosen CUDA device. */
		public Builder automaticCudaDevice() {
			this.cudaDevice = AUTO_DEVICE;
			return this;
		}

		/** Selects a concrete zero-based CUDA device. */
		public Builder cudaDevice(int device) {
			if (device < 0) throw new IllegalArgumentException("CUDA device index must be non-negative");
			this.cudaDevice = device;
			return this;
		}

		/** Selects automatic, CPU-only, cuFFT, or NTT dispatch. */
		public Builder cudaBackend(CudaBackend backend) {
			this.cudaBackend = Objects.requireNonNull(backend, "backend");
			return this;
		}

		/** @return validated immutable runtime options */
		public RuntimeOptions build() {
			return new RuntimeOptions(this);
		}
	}
}
