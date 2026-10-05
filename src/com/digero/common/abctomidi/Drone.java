package com.digero.common.abctomidi;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.midi.MidiInstrument;
import com.digero.common.midi.Note;

/**
 * A part's bagpipe drone (Params.chordAccompaniment, standard ABC only): one or two held notes under the tune,
 * played by AbcToMidi as a track of their own after the part tracks. A drone is where the file asks for one:
 * <ul>
 * <li>abc2midi's %%MIDI droneon and droneoff (also as I:MIDI ...), from where they are, repeats played as often as
 * they are; %%MIDI drone n1 n2 n3 n4 n5 sets the program (counted from 0), the two pitches and their velocities (0
 * keeps one). Without it, the pipes' drone below.</li>
 * <li>without those directives, a part of Highland pipes (K:HP, K:Hp) or one the file sets to Bag Pipe (%%MIDI
 * program 109, %%MIDI voice instrument=110): the pipes' drones, the bass A2 and the tenor A3 (45, 57) under the
 * chanter's low A, on Bag Pipe, the whole part.</li>
 * </ul>
 * Per part, starting from the file header's (like MidiProgramGuess.Clues); voices are parts (VoiceSplitter), so
 * each voice has its own (two voices of pipes are two sets of pipes). A span sounds from its droneon (or the part's
 * start) to its droneoff (or the part's end), whatever the key; AbcToMidi moves its start away from the part's note
 * starts (DRONE_GAP_MILLIS).
 * <p>
 * IN_LOTRO_BAGPIPE_RANGE: the pitches are moved by whole octaves into what Lotro's bagpipe plays, so the bass drone
 * A2 sounds as A3 (one note with the tenor drone), and a player needn't transpose the drone part.
 */
final class Drone {
	/**
	 * True: every drone pitch is played in the range of Lotro's bagpipe (C3 to C6), moved by whole octaves, and
	 * %%MIDI droneon alone gives the pipes' drone. Lotro's bagpipe can't play the bass drone A2: players would move
	 * it up an octave, or worse, the whole drone part. False: the pitches as written, and abc2midi's default drone,
	 * Bassoon on A2 and A1 (45, 33).
	 */
	static final boolean IN_LOTRO_BAGPIPE_RANGE = true;
	/** The Highland pipes' drones: the bass A2 and the tenor A3, on Bag Pipe. */
	static final int PIPES_PITCH_1 = 45;
	static final int PIPES_PITCH_2 = 57;
	/** The drone of %%MIDI droneon without %%MIDI drone: the pipes', or abc2midi's (Bassoon on A2 and A1). */
	static final int DEFAULT_PROGRAM = IN_LOTRO_BAGPIPE_RANGE ? MidiInstrument.BAG_PIPE.id()
			: MidiInstrument.BASSOON.id();
	static final int DEFAULT_PITCH_1 = IN_LOTRO_BAGPIPE_RANGE ? PIPES_PITCH_1 : 45;
	static final int DEFAULT_PITCH_2 = IN_LOTRO_BAGPIPE_RANGE ? PIPES_PITCH_2 : 33;
	/** What Lotro's bagpipe plays, as MIDI pitches: its notes (C2 to C5 in Lotro's notation) an octave up. */
	private static final int LOTRO_BAGPIPE_LOWEST = Note.MIN_PLAYABLE.id + 12 * LotroInstrument.BASIC_BAGPIPE.octaveDelta;
	private static final int LOTRO_BAGPIPE_HIGHEST = Note.MAX_PLAYABLE.id + 12 * LotroInstrument.BASIC_BAGPIPE.octaveDelta;

	/** A stretch the drone sounds, in ticks. */
	record Span(long start, long end) {
	}

	private static final Pattern DIRECTIVE = Pattern
			.compile("(?i)MIDI(?:\\s*=)?\\s+(droneon|droneoff|drone)\\b([\\s\\d]*)");

	private int program = DEFAULT_PROGRAM;
	private int pitch1 = DEFAULT_PITCH_1;
	private int pitch2 = DEFAULT_PITCH_2;
	private int velocity1; // 0: AbcToMidi's default
	private int velocity2;
	private boolean directives; // A drone directive (the part's or the file header's): only they turn it on
	private Long onSince; // The tick the drone was turned on at, null while off
	private final List<Span> spans = new ArrayList<>();

	/** The drone a part starts with: the file header's settings, on from the start if it turned it on. */
	Drone forPart() {
		Drone part = new Drone();
		part.program = program;
		part.pitch1 = pitch1;
		part.pitch2 = pitch2;
		part.velocity1 = velocity1;
		part.velocity2 = velocity2;
		part.directives = directives;
		part.onSince = (onSince != null) ? 0L : null;
		return part;
	}

	/**
	 * A %%MIDI directive (without %%) or an I: field's value: "MIDI droneon", "MIDI droneoff", "MIDI drone 70 45 33 80
	 * 80". Others change nothing.
	 *
	 * @param tick Where it is in the part (0 in a header)
	 */
	void midiDirective(String directive, long tick) {
		Matcher m = DIRECTIVE.matcher(directive.trim());
		if (!m.lookingAt())
			return;
		directives = true;
		switch (m.group(1).toLowerCase(Locale.ROOT)) {
			case "droneon" -> {
				if (onSince == null)
					onSince = tick;
			}
			case "droneoff" -> end(tick);
			default -> {
				String[] values = m.group(2).trim().split("\\s+");
				for (int v = 0; v < values.length && v < 5; v++) {
					int value = values[v].isEmpty() ? 0 : MidiProgramGuess.midiNumber(values[v]);
					if (value <= 0 || value > 127)
						continue; // As abc2midi: 0 keeps the setting
					switch (v) {
						case 0 -> program = value;
						case 1 -> pitch1 = value;
						case 2 -> pitch2 = value;
						case 3 -> velocity1 = value;
						default -> velocity2 = value;
					}
				}
			}
		}
	}

	/**
	 * The part's header has ended: without drone directives, a part of Highland pipes, or one set to Bag Pipe, has the
	 * pipes' drone from its start.
	 */
	void endHeader(boolean highlandPipes, Integer explicitProgram) {
		if (directives)
			return;
		if (highlandPipes || Integer.valueOf(MidiInstrument.BAG_PIPE.id()).equals(explicitProgram)) {
			program = MidiInstrument.BAG_PIPE.id();
			pitch1 = PIPES_PITCH_1;
			pitch2 = PIPES_PITCH_2;
			onSince = 0L;
		}
	}

	/** Turns the drone off at the tick (the part's end, or droneoff). */
	void end(long tick) {
		if (onSince != null && tick > onSince)
			spans.add(new Span(onSince, tick));
		onSince = null;
	}

	List<Span> spans() {
		return spans;
	}

	int program() {
		return program;
	}

	/**
	 * The notes to play, each {pitch, velocity}: in the range of Lotro's bagpipe if IN_LOTRO_BAGPIPE_RANGE; a pitch
	 * that lands on the other one is played once.
	 *
	 * @param defaultVelocity The velocity where the file gives none
	 */
	List<int[]> notes(int defaultVelocity) {
		List<int[]> notes = new ArrayList<>();
		addNote(notes, pitch1, (velocity1 > 0) ? velocity1 : defaultVelocity);
		addNote(notes, pitch2, (velocity2 > 0) ? velocity2 : defaultVelocity);
		return notes;
	}

	private static void addNote(List<int[]> notes, int pitch, int velocity) {
		if (IN_LOTRO_BAGPIPE_RANGE) {
			while (pitch < LOTRO_BAGPIPE_LOWEST)
				pitch += 12;
			while (pitch > LOTRO_BAGPIPE_HIGHEST)
				pitch -= 12;
		}
		for (int[] note : notes) {
			if (note[0] == pitch)
				return;
		}
		notes.add(new int[] { pitch, velocity });
	}
}