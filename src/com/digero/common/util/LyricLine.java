package com.digero.common.util;

public record LyricLine(long tick, String text) {
    @Override
    public String toString() {
        return text;
    }
}