package com.digero.common.util;

import java.util.Objects;

public class Pair<T1, T2> {
	public T1 first;
	public T2 second;

	public Pair() {
		first = null;
		second = null;
	}

	public Pair(T1 first, T2 second) {
		this.first = first;
		this.second = second;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Pair<?, ?> that))
			return false;

        return (Objects.equals(this.first, that.first)) && (Objects.equals(this.second, that.second));
	}

	@Override
	public int hashCode() {
		int hash = (first == null) ? 0 : first.hashCode();
		if (second != null)
			hash ^= Integer.rotateLeft(second.hashCode(), Integer.SIZE / 2);
		return hash;
	}
}
