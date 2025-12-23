package com.digero.common.util;

public record LyricLine(long tick, String text, long endTick) {
    @Override
    public String toString() {
        return text;
    }
}