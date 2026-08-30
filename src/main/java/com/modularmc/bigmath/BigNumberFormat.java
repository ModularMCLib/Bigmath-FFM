package com.modularmc.bigmath;

import com.modularmc.bigmath.formatting.DecimalPatternCompiler;
import com.modularmc.bigmath.formatting.FormatDescriptor;
import com.modularmc.bigmath.formatting.NativeNumberRenderer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Currency;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Immutable, thread-safe Native number formatter compiled from DecimalFormat
 * pattern and locale semantics.
 */
@NullMarked
public final class BigNumberFormat {

	private static final int DEFAULT_CACHE_ENTRIES = 128;
	private static final long DEFAULT_CACHE_BYTES = 1024L * 1024L;
	private static final int MAX_CACHEABLE_UTF8_BYTES = 4096;

	private final FormatDescriptor descriptor;
	private final ResultCache resultCache;

	private BigNumberFormat(FormatDescriptor descriptor, int cacheEntries, long cacheBytes) {
		this.descriptor = descriptor;
		this.resultCache = new ResultCache(cacheEntries, cacheBytes);
	}

	public static BigNumberFormat readable() {
		return builder("#,##0.#")
				.compactUnits(true)
				.roundingMode(RoundingMode.HALF_EVEN)
				.scientificOverflowPattern("0.00E00")
				.build();
	}

	public static BigNumberFormat scientific() {
		return builder("0.00E00")
				.roundingMode(RoundingMode.HALF_EVEN)
				.build();
	}

	public static BigNumberFormat ofPattern(String pattern) {
		return builder(pattern).build();
	}

	public static BigNumberFormat ofPattern(String pattern, Locale locale) {
		return builder(pattern).locale(locale).build();
	}

	public static BigNumberFormat ofLocalizedPattern(String pattern, Locale locale) {
		return localizedBuilder(pattern).locale(locale).build();
	}

	public static Builder builder(String pattern) {
		return new Builder(pattern, false);
	}

	public static Builder localizedBuilder(String pattern) {
		return new Builder(pattern, true);
	}

	public String format(BigInt value) {
		Objects.requireNonNull(value, "value");
		HandleKey key = new HandleKey(1, value.nativeId(), value.nativeVersion());
		return resultCache.getOrCompute(key, () ->
				NativeNumberRenderer.formatBigInt(descriptor, value.nativePtr()));
	}

	public String format(BigDeci value) {
		Objects.requireNonNull(value, "value");
		HandleKey key = new HandleKey(2, value.nativeId(), value.nativeVersion());
		return resultCache.getOrCompute(key, () ->
				NativeNumberRenderer.formatBigDeci(descriptor, value.nativePtr()));
	}

	public String format(Int128 value) {
		Objects.requireNonNull(value, "value");
		Int128Key key = new Int128Key(value.lo(), value.hi());
		return resultCache.getOrCompute(key, () ->
				NativeNumberRenderer.formatInt128(descriptor, value.lo(), value.hi()));
	}

	public String format(long value) {
		return NativeNumberRenderer.formatLong(descriptor, value);
	}

	public String format(double value) {
		return NativeNumberRenderer.formatDouble(descriptor, value);
	}

	public String format(BigInteger value) {
		Objects.requireNonNull(value, "value");
		return NativeNumberRenderer.formatDecimal(descriptor, value.toByteArray(), 0);
	}

	public String format(BigDecimal value) {
		Objects.requireNonNull(value, "value");
		return NativeNumberRenderer.formatDecimal(
				descriptor,
				value.unscaledValue().toByteArray(),
				(long) value.scale()
		);
	}

	public static final class Builder {

		private final String pattern;
		private final boolean localizedPattern;
		private @Nullable Locale locale;
		private @Nullable Currency currency;
		private RoundingMode roundingMode = RoundingMode.HALF_EVEN;
		private boolean compactUnits;
		private boolean milliInput;
		private String unit = "";
		private @Nullable String scientificOverflowPattern = "0.00E00";
		private int cacheEntries = DEFAULT_CACHE_ENTRIES;
		private long cacheBytes = DEFAULT_CACHE_BYTES;

		private Builder(String pattern, boolean localizedPattern) {
			this.pattern = Objects.requireNonNull(pattern, "pattern");
			this.localizedPattern = localizedPattern;
		}

		public Builder locale(Locale locale) {
			this.locale = Objects.requireNonNull(locale, "locale");
			return this;
		}

		public Builder currency(Currency currency) {
			this.currency = Objects.requireNonNull(currency, "currency");
			return this;
		}

		public Builder roundingMode(RoundingMode roundingMode) {
			this.roundingMode = Objects.requireNonNull(roundingMode, "roundingMode");
			return this;
		}

		public Builder compactUnits(boolean enabled) {
			this.compactUnits = enabled;
			return this;
		}

		public Builder milliInput(boolean enabled) {
			this.milliInput = enabled;
			return this;
		}

		public Builder unit(String unit) {
			this.unit = Objects.requireNonNull(unit, "unit");
			return this;
		}

		public Builder scientificOverflowPattern(String pattern) {
			this.scientificOverflowPattern = Objects.requireNonNull(pattern, "pattern");
			return this;
		}

		public Builder resultCacheLimits(int entries, long bytes) {
			if (entries < 0 || bytes < 0) {
				throw new IllegalArgumentException("Cache limits must be non-negative");
			}
			this.cacheEntries = entries;
			this.cacheBytes = bytes;
			return this;
		}

		public BigNumberFormat build() {
			Locale resolvedLocale = locale != null
					? locale
					: Locale.getDefault(Locale.Category.FORMAT);
			FormatDescriptor descriptor = DecimalPatternCompiler.compile(
					pattern,
					localizedPattern,
					resolvedLocale,
					currency,
					roundingMode,
					compactUnits,
					milliInput,
					unit,
					compactUnits ? scientificOverflowPattern : null
			);
			return new BigNumberFormat(descriptor, cacheEntries, cacheBytes);
		}
	}

	private record HandleKey(int type, long id, long version) {
	}

	private record Int128Key(long lo, long hi) {
	}

	private record CacheEntry(String value, int utf8Bytes) {
	}

	private static final class ResultCache {

		private final int maxEntries;
		private final long maxBytes;
		private final LinkedHashMap<Object, CacheEntry> entries = new LinkedHashMap<>(16, 0.75f, true);
		private long bytes;

		private ResultCache(int maxEntries, long maxBytes) {
			this.maxEntries = maxEntries;
			this.maxBytes = maxBytes;
		}

		private String getOrCompute(Object key, Supplier<String> formatter) {
			synchronized (this) {
				CacheEntry cached = entries.get(key);
				if (cached != null) {
					return cached.value();
				}
			}
			String value = formatter.get();
			if (maxEntries == 0 || maxBytes == 0) {
				return value;
			}
			int valueBytes = value.getBytes(StandardCharsets.UTF_8).length;
			if (valueBytes > MAX_CACHEABLE_UTF8_BYTES || valueBytes > maxBytes) {
				return value;
			}
			synchronized (this) {
				CacheEntry existing = entries.get(key);
				if (existing != null) {
					return existing.value();
				}
				entries.put(key, new CacheEntry(value, valueBytes));
				bytes += valueBytes;
				evict();
			}
			return value;
		}

		private void evict() {
			Iterator<Map.Entry<Object, CacheEntry>> iterator = entries.entrySet().iterator();
			while ((entries.size() > maxEntries || bytes > maxBytes) && iterator.hasNext()) {
				Map.Entry<Object, CacheEntry> eldest = iterator.next();
				bytes -= eldest.getValue().utf8Bytes();
				iterator.remove();
			}
		}
	}
}
