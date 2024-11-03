package com.digero.maestro.abc;

import com.digero.common.midi.Note;

public class PartSection implements Comparable<PartSection> {
	public int octaveStep = 0;
	public int volumeStep = 0;
	public int fade = 0;
	public boolean resetVelocities = false;
	public boolean silence = false;
	public int dialogLine = -1;
	public Boolean[] doubling = { false, false, false, false };
	public String name = "noname";

	// inclusive:
	public float startBar = 0;
	public float endBar = 0;// exclusive
	public long startTick = -1L;
	public long endTick = -1L;
	public Note fromPitch = Note.C0;
	public Note toPitch = Note.MAX;
	
	@Override
	public int compareTo(PartSection that) {
		if (that == null) throw new NullPointerException();
		float result = this.startBar - that.startBar;
		if (result > 0) return 1;
		if (result < 0) return -1;
		return 0;
	}
}
