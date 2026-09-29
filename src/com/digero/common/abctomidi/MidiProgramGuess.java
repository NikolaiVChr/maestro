package com.digero.common.abctomidi;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.digero.common.midi.MidiInstrument;

/**
 * The General MIDI program (the sound) of a part of standard ABC (Params.standardPitch), from clues in its header.
 * Standard ABC names no Lotro instrument: the user picks those in Maestro, which then doesn't match an instrument to
 * the program. The program only makes the part sound like what it was written for. Lotro files keep the program of
 * their Lotro instrument.
 * <p>
 * Clues, strongest first; a part's own clue wins over one of the file header's (before X:):
 * <ol>
 * <li>abc2midi's %%MIDI directives (not ABC 2.1; also as I:MIDI ..., older files I:MIDI= ...): program N sets the
 * part's program; program C N the program of channel C; channel C the part's channel (else 1, as abc2midi's first
 * voice). N is 0 to 127, C 1 to 16. Channel 10 is the drums: the notes are General MIDI percussion (C,, bass drum,
 * D,, snare), program 0 the standard kit unless another is set.</li>
 * <li>K:HP or K:Hp (Highland pipes): Bag Pipe</li>
 * <li>an instrument's name: in V: name= or nm= ("Violin"), in G: ("flute"), and in a T: after "for" ("Air for the
 * harp"). Elsewhere in a title a name is no clue: "The Flute Player" is a tune. (A name in square brackets, "[Flute]",
 * makes it a Lotro file: AbcToMidi.isMadeForLotro.) The first name wins.</li>
 * <li>else Nylon Guitar, the program of Lute of Ages that every part had before</li>
 * </ol>
 * The chord accompaniment (Params.chordAccompaniment) takes %%MIDI bassprog N and chordprog N, else Acoustic Bass and
 * Nylon Guitar. Changing these rules changes only the sound of the source in Maestro, not what it exports.
 */
final class MidiProgramGuess {
	private MidiProgramGuess() {
	}

	/** The program without clues. */
	static final int DEFAULT_PROGRAM = MidiInstrument.NYLON_GUITAR.id();
	/** The programs of the chord accompaniment's bass and chords without %%MIDI bassprog or chordprog. */
	static final int DEFAULT_BASS_PROGRAM = MidiInstrument.ACOUSTIC_BASS.id();
	static final int DEFAULT_CHORD_PROGRAM = MidiInstrument.NYLON_GUITAR.id();
	/** abc2midi's (and General MIDI's) channel of the drums, counted from 1. */
	static final int DRUM_CHANNEL = 10;

	/** Instrument names and their programs. At the same place in a text the longer match wins ("tenor sax"). */
	private static final List<Name> NAMES = List.of( //
			name("fiddles?|violins?", MidiInstrument.VIOLIN), //
			name("violas?", MidiInstrument.VIOLA), //
			name("cellos?|violoncellos?", MidiInstrument.CELLO), //
			name("double bass|contrabass|bass", MidiInstrument.ACOUSTIC_BASS), //
			name("(?:bag|small)?pipes?|uilleann(?: pipes)?|highland pipes", MidiInstrument.BAG_PIPE), //
			name("pan ?pipes|pan ?flutes?", MidiInstrument.PAN_FLUTE), //
			name("flutes?|flutos?", MidiInstrument.FLUTE), //
			name("piccolos?|fifes?", MidiInstrument.PICCOLO), //
			name("(?:tin |low |penny )?whistles?", MidiInstrument.WHISTLE), //
			name("recorders?", MidiInstrument.RECORDER), //
			name("ocarinas?", MidiInstrument.OCARINA), //
			name("accordions?|melodeons?|concertinas?|squeezebox", MidiInstrument.ACCORDION), //
			name("harmonicas?|mouth ?organ", MidiInstrument.HARMONICA), //
			name("banjos?", MidiInstrument.BANJO), //
			name("guitars?|lutes?", MidiInstrument.NYLON_GUITAR), //
			name("mandolins?|bouzoukis?|citterns?", MidiInstrument.STEEL_STRING_GUITAR), //
			name("harps?", MidiInstrument.ORCHESTRA_HARP), //
			name("pianos?|piano ?forte|keyboards?", MidiInstrument.PIANO), //
			name("harpsichords?", MidiInstrument.HARPSCHORD), //
			name("organs?", MidiInstrument.CHURCH_ORGAN), //
			name("(?:hammered )?dulcimers?", MidiInstrument.DULCIMER), //
			name("clarinets?", MidiInstrument.CLARINET), //
			name("oboes?", MidiInstrument.OBOE), //
			name("english horn|cor anglais", MidiInstrument.ENGLISH_HORN), //
			name("bassoons?", MidiInstrument.BASSOON), //
			name("(?:french )?horns?", MidiInstrument.FRENCH_HORN), //
			name("trumpets?|cornets?", MidiInstrument.TRUMPET), //
			name("trombones?", MidiInstrument.TROMBONE), //
			name("tubas?", MidiInstrument.TUBA), //
			name("soprano sax(?:ophone)?", MidiInstrument.SOPRANO_SAX), //
			name("(?:alto )?sax(?:ophone)?s?", MidiInstrument.ALTO_SAX), //
			name("tenor sax(?:ophone)?", MidiInstrument.TENOR_SAX), //
			name("baritone sax(?:ophone)?", MidiInstrument.BARI_SAX), //
			name("voices?|vocals?|singers?|soprano|alto|tenor|baritone|choir", MidiInstrument.CHOIR_AAHS), //
			name("sitars?", MidiInstrument.SITAR), //
			name("kotos?", MidiInstrument.KOTO), //
			name("kalimbas?|mbiras?", MidiInstrument.KALIMBA));

	/** A %%MIDI directive that sets a program or a channel; bassprog may go on with octave=N. */
	private static final Pattern MIDI_DIRECTIVE = Pattern
			.compile("(?i)MIDI(?:\\s*=)?\\s+(program|channel|bassprog|chordprog)\\s+(\\d+)(?:\\s+(\\d+))?\\b");
	private static final Pattern VOICE_NAME = Pattern.compile("(?i)\\b(?:name|nm)\\s*=\\s*(?:\"([^\"]*)\"|(\\S+))");
	private static final Pattern FOR = Pattern.compile("(?i)\\bfor\\s+(?:(?:the|a|an|two|three|2|3|solo)\\s+)?");

	private record Name(Pattern pattern, int program) {
	}

	private static Name name(String regex, MidiInstrument instrument) {
		return new Name(Pattern.compile("(?i)\\b(?:" + regex + ")\\b"), instrument.id());
	}

	/** The program of the first instrument name in the text, or null. */
	static Integer programOfName(String text) {
		return programOfName(text, false);
	}

	/** @param atStart The name must start the text */
	private static Integer programOfName(String text, boolean atStart) {
		Integer program = null;
		int start = Integer.MAX_VALUE;
		int length = 0;
		for (Name name : NAMES) {
			Matcher m = name.pattern.matcher(text);
			if (m.find() && (!atStart || m.start() == 0)
					&& (m.start() < start || (m.start() == start && m.end() - m.start() > length))) {
				program = name.program;
				start = m.start();
				length = m.end() - m.start();
			}
		}
		return program;
	}

	/** A clue's value: the part's own, or one it started with from the file header, which its own replaces. */
	private static final class Clue {
		private Integer value;
		private boolean inherited;

		Clue inherit() {
			Clue clue = new Clue();
			clue.value = value;
			clue.inherited = value != null;
			return clue;
		}

		/** A directive: the last one counts. */
		void set(int v) {
			value = v;
			inherited = false;
		}

		/** A name: the first one counts. */
		void setFirst(Integer v) {
			if (v != null && (value == null || inherited)) {
				value = v;
				inherited = false;
			}
		}
	}

	/** The clues of a part (or of a file header, which its parts start from). */
	static final class Clues {
		private Clue program = new Clue(); // %%MIDI program N
		private Clue channel = new Clue(); // %%MIDI channel C
		private Map<Integer, Integer> channelPrograms = new HashMap<>(); // %%MIDI program C N
		private Clue bassProgram = new Clue(); // %%MIDI bassprog N
		private Clue chordProgram = new Clue(); // %%MIDI chordprog N
		private boolean highlandPipes; // K:HP
		private Clue named = new Clue(); // The first instrument name

		/** The clues a part starts from: this file header's, which the part's own clues replace. */
		Clues forPart() {
			Clues part = new Clues();
			part.program = program.inherit();
			part.channel = channel.inherit();
			part.channelPrograms = new HashMap<>(channelPrograms);
			part.bassProgram = bassProgram.inherit();
			part.chordProgram = chordProgram.inherit();
			part.highlandPipes = highlandPipes;
			part.named = named.inherit();
			return part;
		}

		/**
		 * A %%MIDI directive (without %%) or an I: field's value: "MIDI program 73", "MIDI program 2 73", "MIDI channel
		 * 10", "MIDI bassprog 32". Others change nothing here.
		 */
		void midiDirective(String directive) {
			Matcher m = MIDI_DIRECTIVE.matcher(directive.trim());
			if (!m.lookingAt())
				return;
			String what = m.group(1).toLowerCase(Locale.ROOT);
			int first = Integer.parseInt(m.group(2));
			Integer second = (m.group(3) != null) ? Integer.valueOf(m.group(3)) : null;
			switch (what) {
				case "program" -> {
					if (second == null) {
						if (first <= 127)
							program.set(first);
					} else if (first >= 1 && first <= 16 && second <= 127) {
						channelPrograms.put(first, second);
					}
				}
				case "channel" -> {
					if (first >= 1 && first <= 16)
						channel.set(first);
				}
				case "bassprog" -> {
					if (first <= 127)
						bassProgram.set(first);
				}
				case "chordprog" -> {
					if (first <= 127)
						chordProgram.set(first);
				}
				default -> {
				}
			}
		}

		/** A K: field's value: the key may be HP or Hp (Highland pipes). */
		void key(String key) {
			String[] words = key.trim().split("\\s+");
			// A K: with only a clef or transposition keeps the key (and so its pipes)
			if (!words[0].isEmpty() && !words[0].contains("=") && !words[0].toLowerCase(Locale.ROOT).matches(
					"(?:treble|bass|alto|tenor|baritone|perc|none|clef)\\S*"))
				highlandPipes = words[0].equals("HP") || words[0].equals("Hp");
		}

		/** A V: field's value: the name in name= or nm=. */
		void voice(String voice) {
			Matcher m = VOICE_NAME.matcher(voice);
			while (m.find())
				named.setFirst(programOfName(m.group(1) != null ? m.group(1) : m.group(2)));
		}

		/** A G: field's value (ABC 2.1: the group, e.g. flute or fiddle). */
		void group(String group) {
			named.setFirst(programOfName(group));
		}

		/** A T: field's value: only a name after "for" is a clue. */
		void title(String title) {
			Matcher forName = FOR.matcher(title);
			while (forName.find())
				named.setFirst(programOfName(title.substring(forName.end()), true));
		}

		/** The part's channel as abc2midi counts, 1 to 16: its %%MIDI channel, else 1. */
		private int channel() {
			return (channel.value != null) ? channel.value : 1;
		}

		/** The part is drums: %%MIDI channel 10. Its notes are General MIDI percussion. */
		boolean isDrums() {
			return channel() == DRUM_CHANNEL;
		}

		/** The program these clues give. */
		int program() {
			if (program.value != null)
				return program.value;
			Integer ofChannel = channelPrograms.get(channel());
			if (ofChannel != null)
				return ofChannel;
			if (isDrums())
				return 0; // The standard kit
			if (highlandPipes)
				return MidiInstrument.BAG_PIPE.id();
			if (named.value != null)
				return named.value;
			return DEFAULT_PROGRAM;
		}

		/** The program of the chord accompaniment's bass. */
		int bassProgram() {
			return (bassProgram.value != null) ? bassProgram.value : DEFAULT_BASS_PROGRAM;
		}

		/** The program of the chord accompaniment's chords. */
		int chordProgram() {
			return (chordProgram.value != null) ? chordProgram.value : DEFAULT_CHORD_PROGRAM;
		}
	}
}