package com.digero.common.util;

import java.util.Objects;

public class Triple<T1, T2, T3> {
	public T1 first;
	public T2 second;
	public T3 third;

	public Triple(T1 first, T2 second, T3 third) {
		this.first = first;
		this.second = second;
		this.third = third;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Triple<?, ?, ?> that))
			return false;

        return (Objects.equals(this.first, that.first)) && (Objects.equals(this.second, that.second))
				&& (Objects.equals(this.third, that.third));
	}

	@Override
	public int hashCode() {
		int hash = (first == null) ? 0 : first.hashCode();
		if (second != null)
			hash ^= Integer.rotateLeft(second.hashCode(), Integer.SIZE / 2);
		if (third != null)
			hash ^= Integer.rotateLeft(third.hashCode(), Integer.SIZE / 2);
		return hash;
	}
}
