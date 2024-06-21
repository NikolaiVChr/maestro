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

	// inclusive:
	public int startBar = 0;
	public int endBar = 0;
	public Note fromPitch = Note.C0;
	public Note toPitch = Note.MAX;
	
	@Override
	public int compareTo(PartSection that) {
		if (that == null) throw new NullPointerException();
		return this.startBar - that.startBar;
	}
}
