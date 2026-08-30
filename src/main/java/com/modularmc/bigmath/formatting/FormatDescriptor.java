package com.modularmc.bigmath.formatting;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/** Compiled, immutable wire descriptor consumed by the Native renderer. */
public final class FormatDescriptor {

	private final byte[] bytes;
	private final Arena arena;
	private final MemorySegment segment;

	FormatDescriptor(byte[] bytes) {
		this.bytes = bytes;
		this.arena = Arena.ofAuto();
		this.segment = arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes).asReadOnly();
	}

	public MemorySegment segment() {
		return segment;
	}

	public int size() {
		return bytes.length;
	}

	byte[] bytes() {
		return bytes;
	}
}
