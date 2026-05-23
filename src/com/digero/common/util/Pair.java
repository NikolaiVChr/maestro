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

        return Objects.equals(this.first, that.first) && Objects.equals(this.second, that.second);
	}

	@Override
	public int hashCode() {
		return Objects.hash(first, second);
	}

	@Override
	public String toString() {
		return "(" + first + ", " + second + ")";
	}
}
