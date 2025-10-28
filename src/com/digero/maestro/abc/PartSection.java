package com.digero.maestro.abc;

import com.digero.common.midi.Note;
import org.jetbrains.annotations.NotNull;

public class PartSection implements Comparable<PartSection> {
	public int octaveStep = 0;
	public int volumeStep = 0;
	public int fade = 0;
	public boolean resetVelocities = false;
	public boolean silence = false;
	public boolean legato = false;
	public int dialogLine = -1;
	public Boolean[] doubling = { false, false, false, false };

	// inclusive:
	public float startBar = 0;
	public float endBar = 0;// exclusive
	public long startTick = -1L;
	public long endTick = -1L;
	public Note fromPitch = AbcPart.minDefault;
	public Note toPitch = Note.MAX;

    public PartSection() {
    }

    PartSection(PartSection orig) {
        this.octaveStep = orig.octaveStep;
        this.volumeStep = orig.volumeStep;
        this.fade = orig.fade;
        this.resetVelocities = orig.resetVelocities;
        this.silence = orig.silence;
        this.legato = orig.legato;
        this.dialogLine = orig.dialogLine;
        this.doubling = orig.doubling.clone();
        this.startBar = orig.startBar;
        this.endBar = orig.endBar;
        this.startTick = orig.startTick;
        this.endTick = orig.endTick;
        this.fromPitch = orig.fromPitch;
        this.toPitch = orig.toPitch;
    }

	@Override
	public int compareTo(@NotNull PartSection that) {
		return Float.compare(this.startBar, that.startBar);
	}
}
