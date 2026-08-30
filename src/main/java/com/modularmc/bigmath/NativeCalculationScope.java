package com.modularmc.bigmath;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns temporary native-backed values created during one calculation.
 * <p>
 * A scope is confined to the thread that opens it. Values passed to
 * {@link #own(BigInt)} or {@link #own(BigDeci)} are closed in reverse ownership
 * order when the scope closes, including when a surrounding try-with-resources
 * block exits because of an exception. {@code detach} removes a result from the
 * scope and transfers responsibility for closing it to the caller.
 */
public final class NativeCalculationScope implements AutoCloseable {

	private final Thread ownerThread;
	private final List<AutoCloseable> ownedValues = new ArrayList<>();
	private final Map<AutoCloseable, Boolean> ownedIdentities = new IdentityHashMap<>();
	private boolean closed;

	private NativeCalculationScope() {
		this.ownerThread = Thread.currentThread();
	}

	/**
	 * Opens a calculation scope confined to the current thread.
	 */
	public static NativeCalculationScope open() {
		return new NativeCalculationScope();
	}

	/**
	 * Transfers {@code value} into this scope unless it is a permanent constant.
	 */
	public BigInt own(BigInt value) {
		checkOpenThread();
		if (value.isClosed()) {
			throw new IllegalStateException("Cannot own a closed BigInt");
		}
		if (!value.isPermanent()) {
			track(value);
		}
		return value;
	}

	/**
	 * Transfers {@code value} into this scope unless it is a permanent constant.
	 */
	public BigDeci own(BigDeci value) {
		checkOpenThread();
		if (value.isClosed()) {
			throw new IllegalStateException("Cannot own a closed BigDeci");
		}
		if (!value.isPermanent()) {
			track(value);
		}
		return value;
	}

	/**
	 * Detaches {@code value}, transferring close responsibility to the caller.
	 */
	public BigInt detach(BigInt value) {
		checkOpenThread();
		untrack(value);
		return value;
	}

	/**
	 * Detaches {@code value}, transferring close responsibility to the caller.
	 */
	public BigDeci detach(BigDeci value) {
		checkOpenThread();
		untrack(value);
		return value;
	}

	@Override
	public void close() {
		checkOwnerThread();
		if (closed) {
			return;
		}
		closed = true;
		RuntimeException failure = null;
		for (int index = ownedValues.size() - 1; index >= 0; index--) {
			try {
				ownedValues.get(index).close();
			} catch (RuntimeException exception) {
				if (failure == null) {
					failure = exception;
				} else {
					failure.addSuppressed(exception);
				}
			} catch (Exception exception) {
				RuntimeException wrapped = new IllegalStateException("Failed to close scoped native value", exception);
				if (failure == null) {
					failure = wrapped;
				} else {
					failure.addSuppressed(wrapped);
				}
			}
		}
		ownedValues.clear();
		ownedIdentities.clear();
		if (failure != null) {
			throw failure;
		}
	}

	private void track(AutoCloseable value) {
		if (ownedIdentities.put(value, Boolean.TRUE) == null) {
			ownedValues.add(value);
		}
	}

	private void untrack(AutoCloseable value) {
		if (ownedIdentities.remove(value) != null) {
			for (int index = ownedValues.size() - 1; index >= 0; index--) {
				if (ownedValues.get(index) == value) {
					ownedValues.remove(index);
					break;
				}
			}
		}
	}

	private void checkOpenThread() {
		checkOwnerThread();
		if (closed) {
			throw new IllegalStateException("NativeCalculationScope is closed");
		}
	}

	private void checkOwnerThread() {
		if (Thread.currentThread() != ownerThread) {
			throw new IllegalStateException("NativeCalculationScope may only be used by its owner thread");
		}
	}
}
