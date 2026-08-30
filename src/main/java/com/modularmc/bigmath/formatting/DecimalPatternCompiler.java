package com.modularmc.bigmath.formatting;

import org.jspecify.annotations.Nullable;

import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Compiles DecimalFormat's public pattern contract into a pointer-free blob. */
public final class DecimalPatternCompiler {

	static final int MAGIC = 0x32464e42;
	static final int VERSION = 1;
	static final int FLAG_GROUPING = 1;
	static final int FLAG_DECIMAL_ALWAYS = 1 << 1;
	static final int FLAG_SCIENTIFIC = 1 << 2;
	static final int FLAG_COMPACT = 1 << 3;
	static final int FLAG_MILLI = 1 << 4;
	static final int STRING_COUNT = 12;
	static final int HEADER_SIZE = 56 + STRING_COUNT * 8;

	private DecimalPatternCompiler() {
	}

	public static FormatDescriptor compile(
			String pattern,
			boolean localizedPattern,
			Locale locale,
			@Nullable Currency currency,
			RoundingMode roundingMode,
			boolean compact,
			boolean milli,
			String unit,
			@Nullable String overflowPattern
	) {
		Objects.requireNonNull(pattern, "pattern");
		Objects.requireNonNull(locale, "locale");
		Objects.requireNonNull(roundingMode, "roundingMode");
		Objects.requireNonNull(unit, "unit");

		DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(locale);
		DecimalFormat format;
		if (localizedPattern) {
			format = new DecimalFormat();
			format.setDecimalFormatSymbols(symbols);
			format.applyLocalizedPattern(pattern);
		} else {
			format = new DecimalFormat(pattern, symbols);
		}
		if (currency != null) {
			format.setCurrency(currency);
		}
		format.setRoundingMode(roundingMode);
		String canonicalPattern = format.toPattern();
		boolean scientific = findUnquoted(canonicalPattern, 'E') >= 0;
		boolean monetary = findUnquoted(canonicalPattern, '¤') >= 0;
		int minimumExponentDigits = scientific ? minimumExponentDigits(canonicalPattern) : 0;

		int flags = 0;
		if (format.isGroupingUsed()) flags |= FLAG_GROUPING;
		if (format.isDecimalSeparatorAlwaysShown()) flags |= FLAG_DECIMAL_ALWAYS;
		if (scientific) flags |= FLAG_SCIENTIFIC;
		if (compact) flags |= FLAG_COMPACT;
		if (milli) flags |= FLAG_MILLI;

		List<byte[]> strings = new ArrayList<>(STRING_COUNT);
		strings.add(utf8(format.getPositivePrefix()));
		strings.add(utf8(format.getPositiveSuffix()));
		strings.add(utf8(format.getNegativePrefix()));
		strings.add(utf8(format.getNegativeSuffix()));
		strings.add(utf8(symbols.getNaN()));
		strings.add(utf8(symbols.getInfinity()));
		strings.add(utf8(Character.toString(
				monetary ? symbols.getMonetaryDecimalSeparator() : symbols.getDecimalSeparator()
		)));
		strings.add(utf8(Character.toString(
				monetary ? symbols.getMonetaryGroupingSeparator() : symbols.getGroupingSeparator()
		)));
		strings.add(utf8(symbols.getExponentSeparator()));
		strings.add(utf8(Character.toString(symbols.getMinusSign())));
		strings.add(utf8(unit));
		byte[] fallback = overflowPattern == null
				? new byte[0]
				: compile(
					overflowPattern,
					false,
					locale,
					currency,
					roundingMode,
					false,
					false,
					unit,
					null
				).bytes();
		strings.add(fallback);

		int totalSize = HEADER_SIZE;
		for (byte[] value : strings) {
			totalSize = Math.addExact(totalSize, value.length);
		}
		ByteBuffer blob = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN);
		blob.putInt(MAGIC);
		blob.putInt(VERSION);
		blob.putInt(totalSize);
		blob.putInt(flags);
		blob.putInt(roundingMode.ordinal());
		blob.putInt(format.getMultiplier());
		blob.putInt(format.getMinimumIntegerDigits());
		blob.putInt(format.getMaximumIntegerDigits());
		blob.putInt(format.getMinimumFractionDigits());
		blob.putInt(format.getMaximumFractionDigits());
		blob.putInt(format.getGroupingSize());
		blob.putInt(minimumExponentDigits);
		blob.putInt(symbols.getZeroDigit());
		blob.putInt(STRING_COUNT);

		int stringOffset = HEADER_SIZE;
		for (byte[] value : strings) {
			blob.putInt(stringOffset);
			blob.putInt(value.length);
			stringOffset += value.length;
		}
		for (byte[] value : strings) {
			blob.put(value);
		}
		return new FormatDescriptor(blob.array());
	}

	private static byte[] utf8(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}

	private static int minimumExponentDigits(String pattern) {
		int exponent = findUnquoted(pattern, 'E');
		int digits = 0;
		for (int index = exponent + 1; index < pattern.length() && pattern.charAt(index) == '0'; index++) {
			digits++;
		}
		return Math.max(1, digits);
	}

	private static int findUnquoted(String pattern, char needle) {
		boolean quoted = false;
		for (int index = 0; index < pattern.length(); index++) {
			char current = pattern.charAt(index);
			if (current == '\'') {
				if (index + 1 < pattern.length() && pattern.charAt(index + 1) == '\'') {
					index++;
				} else {
					quoted = !quoted;
				}
			} else if (!quoted && current == needle) {
				return index;
			}
		}
		return -1;
	}
}
