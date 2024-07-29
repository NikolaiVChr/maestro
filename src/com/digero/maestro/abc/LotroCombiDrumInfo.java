package com.digero.maestro.abc;

import java.util.EnumMap;
import java.util.Map;

import com.digero.common.midi.Note;
import com.digero.maestro.midi.MidiNoteEvent;

public class LotroCombiDrumInfo {

	public static Note maxCombi = Note.As5;
	public static Note minCombi = Note.Cs5;
	public static Map<Note, Note> firstNotes = new EnumMap<>(Note.class);
	public static Map<Note, Note> secondNotes = new EnumMap<>(Note.class);
	public static int combiNoteCount = 0;

	static {
		// When adding here, also add to LotroDrumInfo

		// Added Rock Bass (Jersiel)
		firstNotes.put(Note.Cs5, Note.As3);
		secondNotes.put(Note.Cs5, Note.D3);

		// Added Rock Snare (Jersiel)
		firstNotes.put(Note.D5, Note.E3);
		secondNotes.put(Note.D5, Note.C5);

		// Added Crash Cymbal (Jersiel)
		firstNotes.put(Note.Ds5, Note.A3);
		secondNotes.put(Note.Ds5, Note.Cs2);

		// Added march snare 1: Slap 7 (c') + Rim Shot 1 (^D) (Jersiel)
		firstNotes.put(Note.E5, Note.C5);
		secondNotes.put(Note.E5, Note.Ds3);

		// Concert bass: Bass Open (^A) + Bass (^G) (Jersiel)
		firstNotes.put(Note.F5, Note.As3);
		secondNotes.put(Note.F5, Note.Gs3);

		// Metal Bass: Muted 2 (^c) + Bass Slap 2 (D) (Jersiel)
		firstNotes.put(Note.Fs5, Note.Cs4);
		secondNotes.put(Note.Fs5, Note.D3);

		// March snare 2: Slap 3 (E) + Rattle Short 3 (^G,) (Jersiel)
		firstNotes.put(Note.G5, Note.E3);
		secondNotes.put(Note.G5, Note.Gs2);

		// Added Xtra Bass March (Aifel)
		firstNotes.put(Note.Gs5, Note.C3);
		secondNotes.put(Note.Gs5, Note.Gs3);
		
		// Added Xtra Bass Dry (Aifel)
		firstNotes.put(Note.A5, Note.As3);
		secondNotes.put(Note.A5, Note.Cs3);
		
		// Added Xtra Bass Wet (Aifel)
		firstNotes.put(Note.As5, Note.C5);
		secondNotes.put(Note.As5, Note.Cs3);

		// Added Xtra Reverse Cymbal: Long Rattle (A) + Tambourine (^A,) (Aifel)
		// firstNotes.put(Note.A6, Note.A4);
		// secondNotes.put(Note.A6, Note.As2);

		combiNoteCount = firstNotes.size();

	}
	
	public static boolean noteIdIsXtraNote(int noteId) {
		return noteId >= minCombi.id && noteId <= maxCombi.id;
	}

	public static MidiNoteEvent getId1(MidiNoteEvent ne, Note extraNote, int pan) {
		Note n1 = firstNotes.get(extraNote);
		MidiNoteEvent newNote = new MidiNoteEvent(n1, ne.velocity, ne.getStartTick(), ne.getEndTick(), ne.getTempoCache(), pan);
		newNote.alreadyMapped = true;
		return newNote;
	}

	public static MidiNoteEvent getId2(MidiNoteEvent ne, Note extraNote, int pan) {
		Note n2 = secondNotes.get(extraNote);
		MidiNoteEvent newNote = new MidiNoteEvent(n2, ne.velocity, ne.getStartTick(), ne.getEndTick(), ne.getTempoCache(), pan);
		newNote.alreadyMapped = true;
		return newNote;
	}
}
