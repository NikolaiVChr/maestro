package com.digero.maestro.midi;

import com.digero.common.midi.ITempoCache;
import com.digero.common.midi.Note;

public class FakeNoteEvent extends NoteEvent {

	public FakeNoteEvent(Note note, long startTick, long endTick, ITempoCache tempoCache) {
		super(note, 127, startTick, endTick, tempoCache);
	}

}
