package com.digero.common.util;

import java.util.Objects;

public class Quad<T1, T2, T3, T4> {
	public T1 first;
	public T2 second;
	public T3 third;
    public T4 fourth;

	public Quad(T1 first, T2 second, T3 third, T4 fourth) {
		this.first = first;
		this.second = second;
		this.third = third;
        this.fourth = fourth;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Quad<?, ?, ?, ?> that))
			return false;

        return (Objects.equals(this.first, that.first)) && (Objects.equals(this.second, that.second))
				&& (Objects.equals(this.third, that.third) && Objects.equals(this.fourth, that.fourth));
	}

	@Override
	public int hashCode() {
		int hash = (first == null) ? 0 : first.hashCode();
		if (second != null)
			hash ^= Integer.rotateLeft(second.hashCode(), Integer.SIZE / 2);
		if (third != null)
			hash ^= Integer.rotateLeft(third.hashCode(), Integer.SIZE / 2);
        if (fourth != null)
            hash ^= Integer.rotateLeft(fourth.hashCode(), Integer.SIZE / 2);
		return hash;
	}
}
