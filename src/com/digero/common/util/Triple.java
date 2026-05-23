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

        return Objects.equals(this.first, that.first) && Objects.equals(this.second, that.second)
				&& Objects.equals(this.third, that.third);
	}

	@Override
	public int hashCode() {
		return Objects.hash(first, second, third);
	}

	@Override
	public String toString() {
		return "(" + first + ", " + second + ", " + third + ")";
	}
}
