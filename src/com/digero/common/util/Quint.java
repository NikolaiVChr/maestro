package com.digero.common.util;

import java.util.Objects;

public class Quint<T1, T2, T3, T4, T5> {
	public T1 first;
	public T2 second;
	public T3 third;
    public T4 fourth;
	public T5 fifth;

	public Quint(T1 first, T2 second, T3 third, T4 fourth, T5 fifth) {
		this.first = first;
		this.second = second;
		this.third = third;
        this.fourth = fourth;
		this.fifth = fifth;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Quint<?, ?, ?, ?, ?> that))
			return false;

        return Objects.equals(this.first, that.first) && Objects.equals(this.second, that.second)
				&& Objects.equals(this.third, that.third) && Objects.equals(this.fourth, that.fourth)
				&& Objects.equals(this.fifth, that.fifth);
	}

	@Override
	public int hashCode() {
		return Objects.hash(first, second, third, fourth, fifth);
	}

	@Override
	public String toString() {
		return "(" + first + ", " + second + ", " + third + ", " + fourth + ", " + fifth + ")";
	}
}
