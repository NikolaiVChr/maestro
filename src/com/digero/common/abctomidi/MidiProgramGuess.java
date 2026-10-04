package com.digero.common.abctomidi;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.digero.common.abc.AbcText;
import com.digero.common.midi.MidiConstants;
import com.digero.common.midi.MidiInstrument;
import com.digero.common.abctomidi.RhythmTempo.TuneType;

/**
 * The General MIDI program (the sound) of a part of standard ABC (Params.standardPitch), from clues in its header.
 * Standard ABC names no Lotro instrument: the user picks those in Maestro, which then doesn't match an instrument to
 * the program. The program only makes the part sound like what it was written for. Lotro files keep the program of
 * their Lotro instrument.
 * <p>
 * Clues, strongest first; a part's own clue wins over one of the file header's (before X:):
 * <ol>
 * <li>ABC 2.1's %%MIDI voice [ID] instrument=N [bank=B] (11.2): the voice's program, N counted from 1 (GM
 * instrument=110 is Bag Pipe, program 109). With an ID, the voice with that ID (a part's V:); without, the part it is
 * in. Only bank 1 (the default, General MIDI): another bank keeps the guess. mute is ignored: the part keeps its notes,
 * Maestro can mute it.</li>
 * <li>abc2midi's %%MIDI directives (not ABC 2.1; also as I:MIDI ..., older files I:MIDI= ...): program N sets the
 * part's program; program C N the program of channel C; channel C the part's channel (else 1, as abc2midi's first
 * voice). N is 0 to 127, C 1 to 16. Channel 10 is the drums: the notes are General MIDI percussion (C,, bass drum,
 * D,, snare), program 0 the standard kit unless another is set.</li>
 * <li>BarFly's V:ID Program C N (not ABC 2.1; melodeon.net's big file): the voice's program, N counted from 0 (as
 * abc2midi), C its channel, ignored. Weaker than every %%MIDI directive.</li>
 * <li>K:HP or K:Hp (Highland pipes): Bag Pipe</li>
 * <li>an instrument's name: in V: name= or nm= ("Violin"), in G: ("flute"), and in a T: after "for" ("Air for the
 * harp"). Elsewhere in a title a name is no clue: "The Flute Player" is a tune. (A name in square brackets, "[Flute]",
 * makes it a Lotro file: AbcToMidi.isMadeForLotro.) The first name wins.</li>
 * <li>bowing marks in the part's music (hasBowing): u and v (up-bow, down-bow, ABC 2.1 4.14), !upbow! !downbow!:
 * Violin</li>
 * <li>the tune's type in R:, by what usually plays it in sessions (RHYTHMS): reel, strathspey, polska ... Violin; jig,
 * slide, air ... Flute; hornpipe, polka, waltz, march ... Accordion; hymn ... Church Organ</li>
 * <li>without R:, a tune type in the title, the same way ("Butchers Hornpipe", "Miss McLeod's Reel"): tune books
 * often name the type only there (or have their R: commented out, %R:)</li>
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



	/**
	 * The tune's type in R: (ABC 2.1: the rhythm, e.g. reel, jig, hornpipe; its names in RhythmTempo) and the program of
	 * an instrument that plays that kind of tune, so a collection of dance tunes doesn't all sound the same. The
	 * weakest clue: a guess by what's usual in sessions. A type not here (song, Balkan dances) is no clue.
	 */
	private static final Map<TuneType, Integer> RHYTHMS = new EnumMap<>(TuneType.class);
	static {
		rhythms(MidiInstrument.VIOLIN, TuneType.REEL, TuneType.STRATHSPEY, TuneType.SCHOTTISCHE, TuneType.SET_DANCE,
				TuneType.POLSKA, TuneType.GANGLAT);
		rhythms(MidiInstrument.FLUTE, TuneType.JIG, TuneType.TREBLE_JIG, TuneType.SLIP_JIG, TuneType.SLIDE,
				TuneType.AIR, TuneType.SLOW_AIR);
		rhythms(MidiInstrument.ACCORDION, TuneType.HORNPIPE, TuneType.BARN_DANCE, TuneType.THREE_TWO, TuneType.POLKA,
				TuneType.MAZURKA, TuneType.WALTZ, TuneType.FIVE_TIME_WALTZ, TuneType.MARCH);
		rhythms(MidiInstrument.CHURCH_ORGAN, TuneType.HYMN);
	}

	private static void rhythms(MidiInstrument instrument, TuneType... types) {
		for (TuneType type : types)
			RHYTHMS.put(type, instrument.id());
	}

	/** A %%MIDI directive that sets a program or a channel; bassprog may go on with octave=N. */
	private static final Pattern MIDI_DIRECTIVE = Pattern
			.compile("(?i)MIDI(?:\\s*=)?\\s+(program|channel|bassprog|chordprog)\\s+(\\d+)(?:\\s+(\\d+))?\\b");
	/** ABC 2.1's %%MIDI voice [ID] [instrument=N [bank=B]] [mute] (11.2); the words after voice in group 1. */
	private static final Pattern MIDI_VOICE = Pattern.compile("(?i)MIDI(?:\\s*=)?\\s+voice\\b(.*)");
	/** BarFly's program in a V: line, V:1 Program 1 71: a channel (ignored), then the program counted from 0. */
	private static final Pattern VOICE_PROGRAM = Pattern.compile("(?i)\\bprogram\\s+(\\d+)(?:\\s+(\\d+))?\\b");
	private static final Pattern VOICE_NAME = Pattern.compile("(?i)\\b(?:name|nm)\\s*=\\s*(?:\"([^\"]*)\"|(\\S+))");
	private static final Pattern FOR = Pattern.compile("(?i)\\bfor\\s+(?:(?:the|a|an|two|three|2|3|solo)\\s+)?");
	/** A field (T:, w: ...) or a field going on (+:, ABC 2.1 3.2): no music. */
	private static final Pattern FIELD = Pattern.compile("[A-Za-z+]:");
	/** A bowing mark by name. */
	private static final Pattern BOWING_NAME = Pattern.compile("[!+](?:up|down)bow[!+]");
	/** What in a line of music isn't notes: quoted text, decorations by name, inline fields, a comment. */
	private static final Pattern NOT_NOTES = Pattern.compile("\"[^\"]*\"|![^!]*!|\\+[^+]*\\+|\\[[A-Za-z]:[^\\]]*]|%.*");

	/**
	 * Bowing marks in a part's music, from its line from to the next X: (or to an empty line, which ends the tune,
	 * endAtEmptyLine): u or v (up-bow, down-bow: in music a lower-case u or v is nothing else), !upbow! or !downbow!.
	 * Not in fields (lyrics, w:), comments, quoted text or other decorations.
	 */
	static boolean hasBowing(List<String> lines, int from, boolean endAtEmptyLine) {
		for (int i = from; i < lines.size(); i++) {
			String line = lines.get(i);
			if (line.startsWith("X:") || (endAtEmptyLine && line.isBlank()))
				break;
			if (line.startsWith("%") || FIELD.matcher(line).lookingAt())
				continue;
			if (BOWING_NAME.matcher(line).find())
				return true;
			String notes = NOT_NOTES.matcher(line).replaceAll("");
			if (notes.indexOf('u') >= 0 || notes.indexOf('v') >= 0)
				return true;
		}
		return false;
	}

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
		return firstMatch(NAMES, text, atStart);
	}

	/** The program of the first tune type with a program in an R: field's value (decoded, AbcText), or null. */
	static Integer programOfRhythm(String text) {
		TuneType type = RhythmTempo.typeOf(text, RHYTHMS.keySet());
		return (type == null) ? null : RHYTHMS.get(type);
	}

	private static Integer firstMatch(List<Name> names, String text, boolean atStart) {
		Integer program = null;
		int start = Integer.MAX_VALUE;
		int length = 0;
		for (Name name : names) {
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
		private Clue voiceProgram = new Clue(); // %%MIDI voice instrument=N, without an ID
		private Map<String, Integer> voicePrograms = new HashMap<>(); // %%MIDI voice ID instrument=N
		private final Set<String> ownVoiceIds = new HashSet<>(); // The IDs of voicePrograms the part set itself
		private String voiceId; // The part's voice: the ID of the V: in its header
		private Clue barFlyProgram = new Clue(); // V:1 Program C N (BarFly)
		private boolean highlandPipes; // K:HP
		private Clue named = new Clue(); // The first instrument name
		private boolean bowed; // Bowing marks in the part's music (hasBowing)
		private Clue rhythm = new Clue(); // The first tune type in R:
		private Clue titleRhythm = new Clue(); // The first tune type in a title

		/** The clues a part starts from: this file header's, which the part's own clues replace. */
		Clues forPart() {
			Clues part = new Clues();
			part.program = program.inherit();
			part.channel = channel.inherit();
			part.channelPrograms = new HashMap<>(channelPrograms);
			part.bassProgram = bassProgram.inherit();
			part.chordProgram = chordProgram.inherit();
			part.voiceProgram = voiceProgram.inherit();
			part.voicePrograms = new HashMap<>(voicePrograms);
			part.barFlyProgram = barFlyProgram.inherit();
			part.highlandPipes = highlandPipes;
			part.named = named.inherit();
			part.rhythm = rhythm.inherit();
			part.titleRhythm = titleRhythm.inherit();
			return part;
		}

		/**
		 * A %%MIDI directive (without %%) or an I: field's value: "MIDI program 73", "MIDI program 2 73", "MIDI channel
		 * 10", "MIDI bassprog 32". Others change nothing here.
		 */
		void midiDirective(String directive) {
			Matcher voice = MIDI_VOICE.matcher(directive.trim());
			if (voice.lookingAt()) {
				midiVoice(voice.group(1));
				return;
			}
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

		/** The words of %%MIDI voice after voice: [ID] [instrument=N [bank=B]] [mute], up to a % comment. */
		private void midiVoice(String words) {
			int comment = words.indexOf('%');
			String id = null;
			Integer instrument = null;
			int bank = 1;
			for (String word : ((comment < 0) ? words : words.substring(0, comment)).trim().split("\\s+")) {
				String lower = word.toLowerCase(Locale.ROOT);
				try {
					if (lower.startsWith("instrument="))
						instrument = Integer.valueOf(lower.substring("instrument=".length()));
					else if (lower.startsWith("bank="))
						bank = Integer.parseInt(lower.substring("bank=".length()));
					else if (!word.isEmpty() && !lower.equals("mute") && !word.contains("=") && id == null)
						id = word;
				} catch (NumberFormatException e) {
					return; // Not a number: not this directive
				}
			}
			if (instrument == null || instrument < 1 || instrument > 128 || bank != 1)
				return;
			if (id == null) {
				voiceProgram.set(instrument - 1);
			} else {
				voicePrograms.put(id, instrument - 1);
				ownVoiceIds.add(id);
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

		/** A V: field's value: the part's voice ID, the name in name= or nm=, and BarFly's Program C N. */
		void voice(String voice) {
			String[] words = voice.trim().split("\\s+", 2);
			if (!words[0].isEmpty() && !words[0].contains("="))
				voiceId = words[0];
			Matcher barFly = VOICE_PROGRAM.matcher(voice);
			if (barFly.find()) {
				int value = Integer.parseInt(barFly.group(barFly.group(2) != null ? 2 : 1));
				if (value <= 127)
					barFlyProgram.set(value);
			}
			Matcher m = VOICE_NAME.matcher(voice);
			while (m.find())
				named.setFirst(programOfName(m.group(1) != null ? m.group(1) : m.group(2)));
		}

		/** Bowing marks in the part's music (hasBowing): written for a fiddle. */
		void bowing(boolean bowed) {
			this.bowed = bowed;
		}

		/** A G: field's value (ABC 2.1: the group, e.g. flute or fiddle). */
		void group(String group) {
			named.setFirst(programOfName(group));
		}

		/** An R: field's value (the tune's type: reel, jig ...), its ABC escapes decoded (sl\"angpolska). */
		void rhythm(String value) {
			rhythm.setFirst(programOfRhythm(AbcText.decode(value)));
		}

		/** A T: field's value: a name after "for" is a clue, and a tune type (weaker than R:). */
		void title(String title) {
			Matcher forName = FOR.matcher(title);
			while (forName.find())
				named.setFirst(programOfName(title.substring(forName.end()), true));
			titleRhythm.setFirst(programOfRhythm(AbcText.decode(title)));
		}

		/** The part's channel as abc2midi counts, 1 to 16: its %%MIDI channel, else 1. */
		private int channel() {
			return (channel.value != null) ? channel.value : 1;
		}

		/** The part is drums: %%MIDI channel 10. Its notes are General MIDI percussion. */
		boolean isDrums() {
			return channel() == MidiConstants.DRUM_CHANNEL+1;
		}

		/** K:HP or K:Hp (Highland pipes) in the part's header (or the file header's). */
		boolean isHighlandPipes() {
			return highlandPipes;
		}

		/**
		 * The program the file sets for the part, or null. The part's own %%MIDI voice (without an ID, then by the
		 * part's voice ID), then its own %%MIDI program N; then the same from the file header (BUG1011); then %%MIDI
		 * program C N for the part's channel; then BarFly's V: Program C N. Not one guessed from K:, names or R:.
		 */
		Integer explicitProgram() {
			if (voiceProgram.value != null && !voiceProgram.inherited)
				return voiceProgram.value;
			Integer ofVoice = (voiceId != null) ? voicePrograms.get(voiceId) : null;
			if (ofVoice != null && ownVoiceIds.contains(voiceId))
				return ofVoice;
			if (program.value != null && !program.inherited)
				return program.value;
			if (voiceProgram.value != null)
				return voiceProgram.value;
			if (ofVoice != null)
				return ofVoice;
			if (program.value != null)
				return program.value;
			Integer ofChannel = channelPrograms.get(channel());
			return (ofChannel != null) ? ofChannel : barFlyProgram.value;
		}

		/** The program these clues give. */
		int program() {
			Integer explicit = explicitProgram();
			if (explicit != null)
				return explicit;
			if (isDrums())
				return 0; // The standard kit
			if (highlandPipes)
				return MidiInstrument.BAG_PIPE.id();
			if (named.value != null)
				return named.value;
			if (bowed)
				return MidiInstrument.VIOLIN.id();
			if (rhythm.value != null)
				return rhythm.value;
			if (titleRhythm.value != null)
				return titleRhythm.value;
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