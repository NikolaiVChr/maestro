package com.digero.common.midi;

public interface IBarNumberCache {
	int tickToBarNumber(long tick);
	float tickToBarNumberFloat(long tick);
	long getBarToTick(int bar);
}
