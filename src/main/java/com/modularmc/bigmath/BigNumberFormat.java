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
import java.text.DecimalFormat;
import java.util.Currency;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Immutable, thread-safe number formatter compiled from {@link DecimalFormat}
 * pattern and locale semantics.
 * <p>
 * Construction validates and compiles the Java pattern into a read-only descriptor. Numeric
 * conversion, scaling, rounding, grouping, digit localization, and final string assembly are
 * performed by the Native library. A formatter captures its locale when it is built and does not
 * follow later changes to the default FORMAT locale.
 * <p>
 * A {@link BigInt} or {@link BigDeci} passed to {@code format} must remain open and must not be
 * mutated concurrently with the call. The formatter itself can be shared by multiple threads.
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

	/**
	 * Creates the localized readable preset {@code #,##0.#} with 1000-based compact units,
	 * {@link RoundingMode#HALF_EVEN}, and a localized {@code 0.00E00} overflow fallback.
	 *
	 * @return a formatter bound to the current default FORMAT locale
	 */
	public static BigNumberFormat readable() {
		return builder("#,##0.#")
				.compactUnits(true)
				.roundingMode(RoundingMode.HALF_EVEN)
				.scientificOverflowPattern("0.00E00")
				.build();
	}

	/**
	 * Creates the localized {@code 0.00E00} scientific preset.
	 *
	 * @return a formatter bound to the current default FORMAT locale
	 */
	public static BigNumberFormat scientific() {
		return builder("0.00E00")
				.roundingMode(RoundingMode.HALF_EVEN)
				.build();
	}

	/**
	 * Compiles a non-localized DecimalFormat pattern using the current default FORMAT locale.
	 *
	 * @param pattern the DecimalFormat pattern
	 * @return the compiled formatter
	 * @throws NullPointerException if {@code pattern} is {@code null}
	 * @throws IllegalArgumentException if the pattern is invalid
	 */
	public static BigNumberFormat ofPattern(String pattern) {
		return builder(pattern).build();
	}

	/**
	 * Compiles a non-localized DecimalFormat pattern using an explicit locale.
	 *
	 * @param pattern the DecimalFormat pattern
	 * @param locale the locale whose symbols are captured
	 * @return the compiled formatter
	 * @throws NullPointerException if an argument is {@code null}
	 * @throws IllegalArgumentException if the pattern is invalid
	 */
	public static BigNumberFormat ofPattern(String pattern, Locale locale) {
		return builder(pattern).locale(locale).build();
	}

	/**
	 * Compiles a localized DecimalFormat pattern using an explicit locale.
	 *
	 * @param pattern the localized DecimalFormat pattern
	 * @param locale the locale used to interpret the localized pattern and symbols
	 * @return the compiled formatter
	 * @throws NullPointerException if an argument is {@code null}
	 * @throws IllegalArgumentException if the pattern is invalid
	 */
	public static BigNumberFormat ofLocalizedPattern(String pattern, Locale locale) {
		return localizedBuilder(pattern).locale(locale).build();
	}

	/**
	 * Starts a builder for a non-localized DecimalFormat pattern.
	 *
	 * @param pattern the DecimalFormat pattern
	 * @return a new builder
	 * @throws NullPointerException if {@code pattern} is {@code null}
	 */
	public static Builder builder(String pattern) {
		return new Builder(pattern, false);
	}

	/**
	 * Starts a builder for a localized DecimalFormat pattern.
	 *
	 * @param pattern the localized pattern
	 * @return a new builder
	 * @throws NullPointerException if {@code pattern} is {@code null}
	 */
	public static Builder localizedBuilder(String pattern) {
		return new Builder(pattern, true);
	}

	/**
	 * Formats an open Native-backed integer and caches eligible results by handle ID and mutation
	 * version.
	 *
	 * @param value the value to format
	 * @return the localized formatted string
	 * @throws NullPointerException if {@code value} is {@code null}
	 * @throws IllegalStateException if the value is closed or its Native backend is unavailable
	 * @throws ArithmeticException if rounding is required with {@link RoundingMode#UNNECESSARY}
	 */
	public String format(BigInt value) {
		Objects.requireNonNull(value, "value");
		HandleKey key = new HandleKey(1, value.nativeId(), value.nativeVersion());
		return resultCache.getOrCompute(key, () ->
				NativeNumberRenderer.formatBigInt(descriptor, value.nativePtr()));
	}

	/**
	 * Formats an open Native-backed decimal and caches eligible results by handle ID and mutation
	 * version. NaN, infinities, and signed zero retain their specified formatting semantics.
	 *
	 * @param value the value to format
	 * @return the localized formatted string
	 * @throws NullPointerException if {@code value} is {@code null}
	 * @throws IllegalStateException if the value is closed or its Native backend is unavailable
	 * @throws ArithmeticException if rounding is required with {@link RoundingMode#UNNECESSARY}
	 */
	public String format(BigDeci value) {
		Objects.requireNonNull(value, "value");
		HandleKey key = new HandleKey(2, value.nativeId(), value.nativeVersion());
		return resultCache.getOrCompute(key, () ->
				NativeNumberRenderer.formatBigDeci(descriptor, value.nativePtr()));
	}

	/**
	 * Formats a signed 128-bit value and caches eligible results by its two words.
	 *
	 * @param value the value to format
	 * @return the localized formatted string
	 * @throws NullPointerException if {@code value} is {@code null}
	 * @throws ArithmeticException if rounding is required with {@link RoundingMode#UNNECESSARY}
	 */
	public String format(Int128 value) {
		Objects.requireNonNull(value, "value");
		Int128Key key = new Int128Key(value.lo(), value.hi());
		return resultCache.getOrCompute(key, () ->
				NativeNumberRenderer.formatInt128(descriptor, value.lo(), value.hi()));
	}

	/**
	 * Formats a signed 64-bit integer.
	 *
	 * @param value the value to format
	 * @return the localized formatted string
	 * @throws ArithmeticException if rounding is required with {@link RoundingMode#UNNECESSARY}
	 */
	public String format(long value) {
		return NativeNumberRenderer.formatLong(descriptor, value);
	}

	/**
	 * Formats the exact IEEE-754 value, preserving NaN, infinities, and negative zero.
	 *
	 * @param value the value to format
	 * @return the localized formatted string
	 * @throws ArithmeticException if rounding is required with {@link RoundingMode#UNNECESSARY}
	 */
	public String format(double value) {
		return NativeNumberRenderer.formatDouble(descriptor, value);
	}

	/**
	 * Formats an arbitrary-precision JDK integer through the portable Native decimal engine.
	 *
	 * @param value the value to format
	 * @return the localized formatted string
	 * @throws NullPointerException if {@code value} is {@code null}
	 * @throws ArithmeticException if rounding is required with {@link RoundingMode#UNNECESSARY}
	 */
	public String format(BigInteger value) {
		Objects.requireNonNull(value, "value");
		return NativeNumberRenderer.formatDecimal(descriptor, value.toByteArray(), 0);
	}

	/**
	 * Formats a JDK decimal from its signed unscaled bytes and scale through the portable Native
	 * decimal engine.
	 *
	 * @param value the value to format
	 * @return the localized formatted string
	 * @throws NullPointerException if {@code value} is {@code null}
	 * @throws ArithmeticException if rounding is required with {@link RoundingMode#UNNECESSARY}
	 */
	public String format(BigDecimal value) {
		Objects.requireNonNull(value, "value");
		return NativeNumberRenderer.formatDecimal(
				descriptor,
				value.unscaledValue().toByteArray(),
				(long) value.scale()
		);
	}

	/** Builds an immutable formatter while capturing all pattern, locale, and cache options. */
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

		/**
		 * Selects the locale used to interpret localized patterns and capture output symbols.
		 *
		 * @param locale the formatter locale
		 * @return this builder
		 * @throws NullPointerException if {@code locale} is {@code null}
		 */
		public Builder locale(Locale locale) {
			this.locale = Objects.requireNonNull(locale, "locale");
			return this;
		}

		/**
		 * Overrides the locale's currency for currency signs in the pattern.
		 *
		 * @param currency the selected currency
		 * @return this builder
		 * @throws NullPointerException if {@code currency} is {@code null}
		 */
		public Builder currency(Currency currency) {
			this.currency = Objects.requireNonNull(currency, "currency");
			return this;
		}

		/**
		 * Selects the decimal rounding policy used by Native rendering.
		 *
		 * @param roundingMode the rounding policy
		 * @return this builder
		 * @throws NullPointerException if {@code roundingMode} is {@code null}
		 */
		public Builder roundingMode(RoundingMode roundingMode) {
			this.roundingMode = Objects.requireNonNull(roundingMode, "roundingMode");
			return this;
		}

		/**
		 * Enables or disables 1000-based compact suffixes.
		 *
		 * @param enabled whether compact units are enabled
		 * @return this builder
		 */
		public Builder compactUnits(boolean enabled) {
			this.compactUnits = enabled;
			return this;
		}

		/**
		 * Treats nonzero magnitudes below 1000 as milli input and divides larger input by 1000 before
		 * applying compact-unit selection.
		 *
		 * @param enabled whether milli-input semantics are enabled
		 * @return this builder
		 */
		public Builder milliInput(boolean enabled) {
			this.milliInput = enabled;
			return this;
		}

		/**
		 * Appends an application unit after any compact suffix and before the pattern suffix.
		 *
		 * @param unit the unit text, which may be empty
		 * @return this builder
		 * @throws NullPointerException if {@code unit} is {@code null}
		 */
		public Builder unit(String unit) {
			this.unit = Objects.requireNonNull(unit, "unit");
			return this;
		}

		/**
		 * Selects the complete DecimalFormat pattern used when a compact value exceeds the final
		 * suffix. The overflow pattern has its own affixes and multiplier while inheriting locale,
		 * currency, rounding mode, and application unit.
		 *
		 * @param pattern the non-localized overflow pattern
		 * @return this builder
		 * @throws NullPointerException if {@code pattern} is {@code null}
		 */
		public Builder scientificOverflowPattern(String pattern) {
			this.scientificOverflowPattern = Objects.requireNonNull(pattern, "pattern");
			return this;
		}

		/**
		 * Configures the per-formatter result cache. A zero entry or byte limit disables caching.
		 * The cache applies only to BigInt, BigDeci, and Int128 values; outputs larger than 4 KiB are
		 * not cached.
		 *
		 * @param entries maximum cached results
		 * @param bytes maximum UTF-8 result bytes
		 * @return this builder
		 * @throws IllegalArgumentException if either limit is negative
		 */
		public Builder resultCacheLimits(int entries, long bytes) {
			if (entries < 0 || bytes < 0) {
				throw new IllegalArgumentException("Cache limits must be non-negative");
			}
			this.cacheEntries = entries;
			this.cacheBytes = bytes;
			return this;
		}

		/**
		 * Validates and compiles the configured pattern and captures the selected locale. If no locale
		 * was supplied, this method captures {@link Locale#getDefault(Locale.Category)} for the FORMAT
		 * category.
		 *
		 * @return the immutable formatter
		 * @throws IllegalArgumentException if either the primary or overflow pattern is invalid
		 */
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
