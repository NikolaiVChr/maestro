package com.digero.common.abctomidi;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sound.midi.*;

import com.digero.common.abc.*;
import com.digero.common.i18n.UIText;
import com.digero.common.midi.MidiConstants;
import com.digero.common.midi.MidiFactory;
import com.digero.common.midi.MidiUtils;
import com.digero.common.midi.Note;
import com.digero.common.midi.PanGenerator;
import com.digero.common.midi.SequencerWrapper;
import com.digero.common.util.*;
import com.digero.maestro.abc.AbcExporter.ExportTrackInfo;

public class AbcToMidi {
	private static final Logger log = Logger.getLogger("import.abc");

	/** This is a static-only class */
	private AbcToMidi() {
	}

	public static class Params {
		public List<FileAndData> filesData;

		/**
		 * If true then,
		 * pitch: abc notes will be transposed in the midi to match lotros instruments.
		 * last note: will be shortened to lotro sample length
		 * velocity: will use lotro dynamics for the midi volume
		 * cowbells: randomized pitch instead of only 1 note (without it: 1 note, or with standardPitch the written pitch)
		 */
		public boolean useLotroInstruments = true;
		public Map<Integer, LotroInstrument> instrumentOverrideMap = null;
		public boolean enableLotroErrors = false;
		public int stereo = 100;
		public boolean generateRegions = false;
		public AbcInfo abcInfo = null;
		public WarningHandler warningHandler;
		public boolean expandRepeats = false;
		/**
		 * Read Q: as ABC 2.1 does: its note length is the beat (Q:3/8=120 in 6/8 is 120 dotted quarters a minute),
		 * Q:120 and Q:C=120 count unit note lengths (L:, 10.1: L:1/8 Q:120 is 120 eighths a minute), and without Q: a
		 * 6/8 9/8 12/8 tune gets 120 dotted quarters. Lotro takes the meter's denominator as the beat whatever Q: says,
		 * so this is off by default, for existing projects and the ABC Player (it has no effect with Lotro errors on).
		 * Maestro's own %%Q: counts the meter's beats either way.
		 */
		public boolean specTempo = false;
		/**
		 * Play each note at its ABC 2.1 pitch (C is middle C), and don't take the instrument from T:. For standard ABC,
		 * like folk tunes, where T: is the song's title. Off for ABC made for Lotro, where the octave depends on the
		 * instrument named in the title (see {@link #isMadeForLotro(List)}).
		 */
		public boolean standardPitch = false;
		/**
		 * Play the chord symbols ("G", "Am", "D7") as an accompaniment: for each part with chord symbols, a bass track
		 * (Basic Theorbo, the chord's root or /bass note on the first beat of each bar and at each chord) and a chords
		 * track (Lute of Ages, the chord on the other beats). Maestro; Lotro plays no chords, so never with Lotro errors.
		 */
		public boolean chordAccompaniment = false;
		/**
		 * Play what ABC 2.1 (2011) says where Lotro plays it otherwise, and take the ABC 2.1 syntax Lotro doesn't
		 * know. For standard ABC in Maestro, set like standardPitch; off for files made for Lotro, existing projects and
		 * the ABC Player (it has no effect with Lotro errors on). Done: a broken rhythm with a chord (c>[ce], [ce]>d), a
		 * chord's length (its first note's), a unison (the longer note), and an accidental in every octave
		 * (I:propagate-accidentals, default pitch).
		 * The rest is still to do: see AbcToMidiBehaviourTest.Standard2011, whose tests of it are @Disabled.
		 */
		public boolean standard2011 = false;

		public Params(File file) throws IOException {
			this.filesData = new ArrayList<>();
			this.filesData.add(new FileAndData(file, readLines(file)));
		}

		/**
		 * ABC kept as text (in a Maestro project), and the file it came from: its name is used in messages, and it
		 * needn't exist any more.
		 */
		public Params(String data, File sourceFile) {
			this.filesData = new ArrayList<>();
			this.filesData.add(new FileAndData(sourceFile,
					withoutByteOrderMark(new ArrayList<>(Arrays.asList(data.split("\\r\\n|\\r|\\n", -1))))));
		}

		public Params(List<FileAndData> filesData) {
			this.filesData = filesData;
		}
	}

	private static final Pattern INFO_PATTERN = Pattern.compile("^([A-Z]):\\s*(.*)\\s*$");
	private static final int INFO_TYPE = 1;
	private static final int INFO_VALUE = 2;

	/**
	 * Information fields about the tune (ABC 2.1, 3.1) with their labels, written as MidiText header lines ("@I") in
	 * track 0, see writeInfoLines.
	 */
	private static final Map<Character, String> INFO_LABELS = Map.ofEntries(Map.entry('A', "Area"),
			Map.entry('B', "Book"), Map.entry('C', "Composer"), Map.entry('D', "Discography"), Map.entry('F', "File"),
			Map.entry('G', "Group"), Map.entry('H', "History"), Map.entry('N', "Notes"), Map.entry('O', "Origin"),
			Map.entry('R', "Rhythm"), Map.entry('S', "Source"), Map.entry('Z', "Transcription"));
	/** N: lines that are Maestro's own (AbcInfo): the instrument setup and genre/mood. */
	private static final Pattern MAESTRO_NOTE_PATTERN = Pattern.compile("(?i)ts\\s.*|genre:.*|mood:.*");

	private static final Pattern XINFO_PATTERN = Pattern.compile("^\\s*%%([A-Za-z\\-]+)((:?)|\\s)\\s*(.*)\\s*$");
	private static final int XINFO_FIELD = 1;
	private static final int XINFO_COLON = 3;
	private static final int XINFO_VALUE = 4;

	private static final Pattern NOTE_PATTERN = Pattern.compile("(_{1,2}|=|\\^{1,2})?" + "([xzA-Ga-g])"
			+ "(,{1,5}|'{1,5})?" + "(\\d+)?" + "(//?\\d*)?" + "(>{1,3}|<{1,3})?" + "(-)?");
	private static final int NOTE_ACCIDENTAL = 1;
	private static final int NOTE_LETTER = 2;
	private static final int NOTE_OCTAVE = 3;
	private static final int NOTE_LEN_NUMER = 4;
	private static final int NOTE_LEN_DENOM = 5;
	private static final int NOTE_BROKEN_RHYTHM = 6;
	private static final int NOTE_TIE = 7;

	private static final Pattern CHORD_LENGTH_PATTERN = Pattern.compile("(\\d+)?(//?\\d*)?");
	private static final int CHORD_LEN_NUMER = 1;
	private static final int CHORD_LEN_DENOM = 2;

	/**
	 * Maps a note name (a, b, c, etc.) to the number of semitones it is above the beginning of the octave (c)
	 */
	private static final int[] CHR_NOTE_DELTA = { 9, 11, 0, 2, 4, 5, 7 };

	// Lots of prime factors for divisibility goodness
	static final long DEFAULT_NOTE_TICKS = (2 * 2 * 2 * 2 * 2 * 2) * (3 * 3) * 5;

	/**
	 * The share of all notes and rests that must have triplet timing before the song is guessed to use triplets
	 * (AbcInfo.hasTriplets, which makes Maestro export with a grid that suits triplets and swing, but not regular
	 * notes). %%swing-rhythm overrides the guess.
	 */
	static final double TRIPLET_GUESS_MIN_SHARE = 0.05;// 5%. 100% includes all the rests.

	/**
	 * How long the shortest grace note in a group lasts. ABC 2.1 (4.12) leaves the length to the program. In folk music
	 * grace notes are as short as possible (cuts, taps, pipe gracenotes); this is a little over Lotro's shortest note
	 * ({@link AbcConstants#SHORTEST_NOTE_SECONDS}), so a Maestro transcription for Lotro can keep them.
	 */
	public static final double GRACE_NOTE_SECONDS = 0.065;

	/**
	 * How many Dynamics steps an accent (L, !accent!, !>!, !emphasis!) plays louder than the notes around it, at most
	 * ffff. ABC 2.1 (4.14) leaves the amount to the program; MuseScore 4 plays an accent 2 dynamic levels up (mf as ff).
	 * Only with Params.standard2011: Lotro plays an accent like any note.
	 */
	public static final int ACCENT_DYNAMICS_STEPS = 2;

	/**
	 * How many Dynamics steps louder the note at the start of each beat group plays, in a meter of beat groups (M:2+2+3/8,
	 * 7/8 as 2+2+3, TuneInfo.getBeatGroups), so the bar is heard as its groups. Only with Params.standard2011; meters of
	 * equal beats get none. An accented note (ACCENT_DYNAMICS_STEPS) stays as loud as its accent.
	 */
	public static final int BEAT_GROUP_ACCENT_STEPS = 1;

	/**
	 * How much of its written length a staccato note (.c) sounds; the next note starts on time. ABC 2.1 (4.14) leaves the
	 * amount to the program; MuseScore 3 and 4 play half. Only with Params.standard2011.
	 */
	public static final double STACCATO_LENGTH = 0.5;

	/**
	 * Ornaments that are played, by their decoration name (ABC 2.1, 4.14); T, M, P and ~ are the short forms. ABC leaves
	 * how to play them to the program: here in steps of GRACE_NOTE_SECONDS, see ornamentNotes.
	 */
	private static final Map<String, String> ORNAMENTS = Map.of("trill", "trill", "roll", "roll", "lowermordent",
			"lowermordent", "mordent", "lowermordent", "uppermordent", "uppermordent", "pralltriller", "uppermordent",
			"turn", "turn", "invertedturn", "invertedturn");

	/** The dynamics marks !pppp! to !ffff! (ABC 2.1, 4.14), by name: the same volumes as +pppp+ to +ffff+. */
	private static final Set<String> DYNAMICS_NAMES = Arrays.stream(Dynamics.values()).map(Enum::name)
			.collect(java.util.stream.Collectors.toUnmodifiableSet());

	/** The accent's decoration names (ABC 2.1, 4.14); L is its short form. */
	private static final Set<String> ACCENT_NAMES = Set.of("accent", ">", "emphasis");

	/** The volume of an accented note: ACCENT_DYNAMICS_STEPS louder than the dynamics, at most the loudest. */
	private static Dynamics accented(Dynamics dynamics) {
		return louder(dynamics, ACCENT_DYNAMICS_STEPS);
	}

	/** The dynamics steps louder, at most the loudest. */
	private static Dynamics louder(Dynamics dynamics, int steps) {
		Dynamics[] all = Dynamics.values();
		return all[Math.min(dynamics.ordinal() + steps, all.length - 1)];
	}

	public static List<String> readLines(File inputFile) throws IOException {
		// Note: ABC files are technically ISO-8859-1 by standard, but often UTF-8 in practice.
		// Java 18+ defaults to UTF-8. To be safe given the international user base:
		try {
			// 1. Try UTF-8 first.
			// This works for:
			// - Files created on Linux
			// - Files created on Java 18+
			// - Files explicitly saved as UTF-8
			// If the file contains invalid UTF-8 byte sequences (like a legacy Windows file might),
			// this throws MalformedInputException.
			return withoutByteOrderMark(new ArrayList<>(Files.readAllLines(inputFile.toPath(), StandardCharsets.UTF_8)));
		} catch (MalformedInputException e) {
			// 2. Fallback: Windows-1252 ("ANSI")
			// This covers the vast majority of legacy Windows files (Windows 7/10/11 with Java 8/11/17).
			// It is a superset of ISO-8859-1, so it correctly handles standard Western characters
			// Plus Windows specific chars like smart quotes and euro signs.
			// Decoded leniently: a byte Windows-1252 leaves undefined (0x81 0x8D 0x8F 0x90 0x9D, e.g. a DOS file's ü,
			// 0x81 in code page 437) becomes U+FFFD instead of failing the whole file ("Input length = 1").
			byte[] bytes = Files.readAllBytes(inputFile.toPath());
			return withoutByteOrderMark(
					new ArrayList<>(new String(bytes, Charset.forName("windows-1252")).lines().toList()));
		}
	}

	/**
	 * The lines without a byte order mark at the start of the first (ABC 2.1, 2.1: software should ignore it). Left in,
	 * "﻿X:1" is no X: line and "﻿%%song-title" no directive. A UTF-8 BOM in a file read as Windows-1252 (its
	 * rest isn't UTF-8) is "ï»¿".
	 */
	static List<String> withoutByteOrderMark(List<String> lines) {
		if (!lines.isEmpty()) {
			String first = lines.get(0);
			if (first.startsWith("﻿"))
				lines.set(0, first.substring(1));
			else if (first.startsWith("ï»¿"))
				lines.set(0, first.substring(3));
		}
		return lines;
	}

	/** A Lotro instrument's full name, e.g. "Basic Lute" or "Lute of Ages" (spaces may be _ or missing). */
	private static final Pattern INSTRUMENT_FULL_NAME_PATTERN;
	static {
		StringJoiner names = new StringJoiner("|", "\\b(?:", ")\\b");
		for (LotroInstrument instrument : LotroInstrument.values()) {
			StringJoiner words = new StringJoiner("[\\s_]*");
			for (String word : instrument.friendlyName.split(" "))
				words.add(Pattern.quote(word));
			names.add(words.toString());
		}
		INSTRUMENT_FULL_NAME_PATTERN = Pattern.compile(names.toString(), Pattern.CASE_INSENSITIVE);
	}

	/** Text in square brackets, e.g. the [flute] in BruTE's titles. */
	private static final Pattern BRACKETED_PATTERN = Pattern.compile("\\[([^\\[\\]]*)\\]");

	/** A chord symbol: "G", "Am", "D7", "F#m7b5", "Bbmaj7", "C/E", "Gsus4" (not an annotation like "^text"). */
	private static final Pattern CHORD_SYMBOL_PATTERN = Pattern.compile(
			"\"[A-G][#b]?(?:m|min|maj|dim|aug|sus|add|[0-9]|[+\\-()])*(?:/[A-G][#b]?)?\"");

	/**
	 * Whether the files were made for Lotro (by Maestro, BruTE, ABC Tools or by hand), with each part's octaves written
	 * for the instrument in its title, or are standard ABC (Params.standardPitch). A wrong answer puts every part an
	 * octave off, so without a sign either way the answer is null, and the caller asks the user.
	 * <ol>
	 * <li>FALSE, standard ABC, for a file with more X: than a song made for Lotro has parts (LOTRO_MAX_PARTS): a tune
	 * book (Essen's shanxi.abc, 802 songs, has %%abc-creator hum2abc).
	 * <li>Else TRUE, made for Lotro, if any of: an extended field of Maestro and the ABC Player (%%song-title,
	 * %%part-name, %%made-for, %%abc-creator naming Maestro, ABC Tools or BruTE ...; not %%abc-version, nor another
	 * tool's %%abc-creator); BruTE ("% Produced with Bruzo's Transcoding Environment", "Z: Transcribed with
	 * BruTE"); LotRO MIDI Player, Maestro's predecessor ("Z: Transcribed using LotRO MIDI Player: ..."); a Lotro
	 * instrument's full name in T: (Basic Lute, Lute of Ages), or just an instrument's name in square brackets ([flute],
	 * [Lute]).
	 * <li>Else null, unsure, with LotRO MIDI Player's comment "% Transpose: -12" but not its Z: line (a player changed
	 * it, maybe the notes too): no sign of standard ABC counts then.
	 * <li>Else FALSE, standard ABC, if any of: chord symbols ("Am"), voices (two V: in a tune), the background fields
	 * of tune collections (B: D: F: H: O: R: S:), a note Lotro can't play (below C, or above c'), or ABC that Lotro
	 * refuses or plays otherwise (tested in Lotro): a Q: note length that isn't the meter's beat (Q:3/8=120 in 6/8,
	 * B15), text in Q: (B20), words after the key in K: or an empty K: (B23, B38, B49), M:none (B21), +: (B22), an L:
	 * after the notes that changes the unit note length (B40), and in the notes :|: :|] (B2, B4), !decorations! (B56,
	 * B57), +decorations+ other than volumes (B12), grace notes (B55), T H L M O P S u v (B17, B50), y (B58), Z (B16),
	 * $ (B36), ` (B37), [|] (B18), inline fields (B7-B10), a length or a tie after a chord (B11, B65), and a broken
	 * rhythm next to a chord (B5, B6).
	 * <li>Else null: nothing tells.
	 * </ol>
	 * Not signs: an instrument word elsewhere in a title ("Bass Reeves", "(fiddle tune)", "[Bass line]"), as folk
	 * titles have them; +p+ volume marks, which are ABC too (ABC 2.0's decorations, ABC 2.1 with I:decoration +); what
	 * Lotro plays as ABC 2.1 says (|: :| :: :||: [1 [2, Q:1/4=120 in 4/4, K:D mix), or plays plain (~ and .); ending
	 * lists [1,3 (Lotro plays nothing of the part, but gives no error, B28); and the limits of note lengths, which
	 * files made for Lotro by hand can break.
	 * <p>
	 * Free text is no sign: in a file with X:, the lines before the first X: and after a blank line (ABC 2.1, 2.2:
	 * a blank line ends the tune) that aren't fields, until the next X:, e.g. a tune book's notes and copyright.
	 */
	public static Boolean isMadeForLotro(List<FileAndData> filesData) {
		boolean standardSign = false;
		boolean lmpComment = false; // LotRO MIDI Player's "% Transpose:": unsure, not standard ABC either
		for (FileAndData fileAndData : filesData) {
			if (fileAndData.lines.stream().filter(l -> l.trim().startsWith("X:")).count() > LOTRO_MAX_PARTS)
				return Boolean.FALSE;
		}
		for (FileAndData fileAndData : filesData) {
			String fileMeter = null; // The file header's M: (before the first X:), which every tune starts from
			String meter = null; // The M: that applies (null: 4/4)
			String fileUnitLength = null; // The file header's L:, without spaces
			String unitLength = null; // The L: that applies (null: the default)
			Set<String> voices = new HashSet<>(); // The tune's voice IDs (V:)
			List<String> tempos = new ArrayList<>(); // Q: values with a note length, checked against the meter
			boolean inTune = false; // After an X:
			boolean inBody = false; // The tune's notes have started
			boolean hasX = fileAndData.lines.stream().anyMatch(l -> l.trim().startsWith("X:"));
			boolean freeText = hasX; // Lines that aren't fields are free text: before the first X:, after a blank line
			for (String line : fileAndData.lines) {
				String trimmed = line.trim();
				String lower = trimmed.toLowerCase(Locale.ROOT);
				if (trimmed.isEmpty()) {
					freeText = hasX;
				} else if (lower.startsWith("%%")) {
					Matcher xInfo = XINFO_PATTERN.matcher(line);
					AbcField field = xInfo.matches()
							? AbcField.fromString(xInfo.group(XINFO_FIELD) + xInfo.group(XINFO_COLON))
							: null;
					if (field != null && isLotroField(field, xInfo.group(XINFO_VALUE)))
						return true;
				} else if (lower.startsWith("%")) {
					if (lower.contains("bruzo"))
						return true;
					lmpComment |= LMP_TRANSPOSE_PATTERN.matcher(trimmed).lookingAt();
				} else if (trimmed.startsWith("+:")) {
					standardSign = true; // A field continued on the next line (B22, B51)
				} else if (INFO_PATTERN.matcher(trimmed).matches()) {
					if (lower.startsWith("z:") && (lower.contains("brute") || lower.contains("lotro midi player")))
						return true;
					if (lower.startsWith("t:") && isLotroTitle(trimmed.substring(2)))
						return true;
					// The background fields of tune collections (book, discography, file, history, origin, rhythm,
					// source), which Lotro/BruTE tools don't write (C: N: Z: they do)
					if ("bdfhors".indexOf(lower.charAt(0)) >= 0)
						standardSign = true;
					String value = stripComment(trimmed.substring(2)).trim();
					switch (trimmed.charAt(0)) {
						case 'X' -> {
							standardSign |= tempoNotLotros(tempos, meter);
							meter = fileMeter;
							unitLength = fileUnitLength;
							voices.clear();
							inTune = true;
							inBody = false;
							freeText = false;
						}
						case 'M' -> {
							standardSign |= value.equalsIgnoreCase("none");
							meter = value;
							if (!inTune)
								fileMeter = value;
						}
						case 'Q' -> {
							if (value.indexOf('"') >= 0)
								standardSign = true; // A tempo word (B20, B32)
							else if (value.indexOf('=') >= 0)
								tempos.add(value);
						}
						case 'K' -> standardSign |= !KEY_LOTRO_PLAYS_PATTERN.matcher(value).matches();
						case 'L' -> {
							// Lotro ignores an L: after the notes (B40): a sign if it changes the unit note length
							String length = value.replace(" ", "");
							standardSign |= inBody && !length.equals(unitLength);
							unitLength = length;
							if (!inTune)
								fileUnitLength = length;
						}
						case 'V' -> {
							// Voices: two or more in a tune (hand-made files for Lotro may have a lone V:1)
							voices.add(value.split("\\s+")[0]);
							standardSign |= voices.size() > 1;
						}
						default -> {
						}
					}
				} else if (!freeText && !LOWER_CASE_FIELD_PATTERN.matcher(trimmed).lookingAt()) {
					// A line of notes (not w: s: r: ...)
					standardSign |= tempoNotLotros(tempos, meter);
					inBody = true;
					if (CHORD_SYMBOL_PATTERN.matcher(stripComment(line)).find() || hasNoteOutsideLotroRange(line)
							|| hasLotroRefused(line))
						standardSign = true;
				}
			}
			standardSign |= tempoNotLotros(tempos, meter);
		}
		return (standardSign && !lmpComment) ? Boolean.FALSE : null;
	}

	/** LotRO MIDI Player's comment "%  Transpose: -12", also kept when the player changed the Z: line. */
	private static final Pattern LMP_TRANSPOSE_PATTERN = Pattern.compile("%\\s*Transpose\\s*:\\s*-?\\d");


	/** The most parts a song made for Lotro has (Maestro's limit): a file with more X: is a tune book. */
	private static final int LOTRO_MAX_PARTS = 24;

	/** A tool made for Lotro, in %%abc-creator (Maestro writes "%%abc-creator Maestro v2.5.0"). */
	private static final Pattern LOTRO_CREATOR_PATTERN = Pattern.compile("(?i)maestro|abc ?tools|abc ?player|brute|bruzo");

	/**
	 * Whether an extended field marks a file made for Lotro: all do, but %%abc-version, and %%abc-creator unless it names
	 * a Lotro tool. Other tools write those too (hum2abc: %%abc-version 2.0, %%abc-creator hum2abc beta).
	 */
	private static boolean isLotroField(AbcField field, String value) {
		return switch (field) {
			case ABC_VERSION -> false;
			case ABC_CREATOR -> LOTRO_CREATOR_PATTERN.matcher(value).find();
			default -> true;
		};
	}

	/** A key that Lotro plays: the key and its mode, nothing more (K:G, K:Am, K:D mix, K: C maj; B33). */
	private static final Pattern KEY_LOTRO_PLAYS_PATTERN = Pattern.compile("(?i)[A-G][#b]?\\s*(?:m|min|minor|maj|major"
			+ "|ion|ionian|aeo|aeolian|mix|mixolydian|dor|dorian|phr|phrygian|lyd|lydian|loc|locrian)?");

	/** w: s: r: m: ... : a line that isn't notes. */
	private static final Pattern LOWER_CASE_FIELD_PATTERN = Pattern.compile("[a-z]:");

	/**
	 * In the notes, outside quoted text: what Lotro refuses or plays otherwise (tested in Lotro). See isMadeForLotro.
	 */
	private static final Pattern LOTRO_REFUSES_PATTERN = Pattern.compile(":\\|[:\\]]" // :|: :|] (B2, B4)
			+ "|![^!]*!" // !trill! !f! (B56, B57)
			+ "|\\+[^+\\s]*\\+" // +trill+ (B12; the volumes are taken out first)
			+ "|\\{" // Grace notes (B55)
			+ "|[THLMOPSuvyZ$`]" // Decorations (B17, B50), y (B58), Z (B16), $ (B36), ` (B37)
			+ "|\\[\\|\\]" // [|] (B18)
			+ "|\\[[A-Za-z]:" // An inline field (B7-B10)
			+ "|\\][0-9/]" // A length after a chord (B11)
			+ "|\\]-" // A tie after a chord (B65)
			+ "|\\][<>]|[<>]\\["); // A broken rhythm next to a chord (B5, B6)

	/** Something in the notes that Lotro refuses or plays otherwise (LOTRO_REFUSES_PATTERN). */
	private static boolean hasLotroRefused(String musicLine) {
		String notes = QUOTED_PATTERN.matcher(stripComment(musicLine)).replaceAll(" ");
		return LOTRO_REFUSES_PATTERN.matcher(VOLUME_PATTERN.matcher(notes).replaceAll(" ")).find();
	}

	/** Quoted text in the notes: a chord symbol or an annotation. */
	private static final Pattern QUOTED_PATTERN = Pattern.compile("\"[^\"]*\"");
	/** A volume that Lotro plays: +pppp+ to +ffff+. */
	private static final Pattern VOLUME_PATTERN = Pattern.compile("(?i)\\+(?:pppp|ppp|pp|p|mp|mf|f|ff|fff|ffff)\\+");

	/**
	 * Whether a Q: with a note length has a beat other than the meter's (Q:3/8=120 in 6/8), which Lotro plays at another
	 * speed (B15), or several beats (Q:1/4 3/8=40). Clears the list.
	 *
	 * @param meter The M: that applies; null for the default 4/4
	 */
	private static boolean tempoNotLotros(List<String> tempos, String meter) {
		int denominator;
		if (meter == null || meter.equals("C"))
			denominator = 4;
		else if (meter.equals("C|"))
			denominator = 2;
		else {
			Matcher m = Pattern.compile("\\d+\\s*/\\s*(\\d+)").matcher(meter);
			denominator = m.matches() ? Integer.parseInt(m.group(1)) : -1;
		}
		boolean notLotros = false;
		for (String tempo : tempos) {
			String[] beats = tempo.substring(0, tempo.indexOf('=')).trim().split("\\s+");
			if (beats.length > 1) {
				notLotros = true;
				continue;
			}
			Matcher beat = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)").matcher(beats[0]);
			if (beat.matches() && denominator > 0
					&& (long) Integer.parseInt(beat.group(1)) * denominator != Integer.parseInt(beat.group(2)))
				notLotros = true;
		}
		tempos.clear();
		return notLotros;
	}

	/** The version line of ABC 2.1 (2.1): %abc-2.1 on the first line; %abc alone, or no line, is older. */
	private static final Pattern VERSION_PATTERN = Pattern.compile("^%abc-(\\d+)\\.(\\d+)");

	/** The file says it follows ABC 2.1 or later: strict (ABC 2.1, 12). */
	private static boolean isAbc21OrLater(List<String> lines) {
		if (lines.isEmpty())
			return false;
		Matcher m = VERSION_PATTERN.matcher(lines.get(0).stripLeading().replace("\uFEFF", ""));
		if (!m.find())
			return false;
		int major = Integer.parseInt(m.group(1));
		return major > 2 || (major == 2 && Integer.parseInt(m.group(2)) >= 1);
	}

	/**
	 * The loose reading of ! (ABC 2.1, 12): the ! at index starts a decoration if another ! follows before | [ ] : or
	 * the line's end; else it's a score line break. Spaces may come between (!D.C. al fine!, ! roll!): measured on The
	 * Session and Norbeck, a ! ... ! without a bar line between is a decoration, never a line break before music.
	 */
	private static boolean isBangDecoration(String line, int index) {
		for (int k = index + 1; k < line.length(); k++) {
			char c = line.charAt(k);
			if (c == '!')
				return true;
			if (c == '|' || c == '[' || c == ']' || c == ':')
				return false;
		}
		return false;
	}

	/** A line of a file header (ABC 2.1, 2.2.2): a field, a directive or comment (%), or empty. Else free text. */
	private static final Pattern FILE_HEADER_LINE_PATTERN = Pattern.compile("^([A-Za-z]:|%|\\s*$).*");

	/** What in a music line isn't notes: quoted text, !decorations!, +decorations+ and inline fields ([K:G]). */
	private static final Pattern NOT_NOTES_PATTERN = Pattern.compile("\"[^\"]*\"|![^!]*!|\\+[^+]*\\+|\\[[A-Za-z]:[^\\]]*\\]");

	/** A note with its accidental and octave marks. */
	private static final Pattern NOTE_OCTAVE_PATTERN = Pattern.compile("(\\^{1,2}|_{1,2}|=)?([A-Ga-g])(,+|'+)?");

	/** A note Lotro can't play: below C, or above c' (the range of its instruments' ABC). */
	private static boolean hasNoteOutsideLotroRange(String musicLine) {
		Matcher note = NOTE_OCTAVE_PATTERN.matcher(NOT_NOTES_PATTERN.matcher(stripComment(musicLine)).replaceAll(" "));
		while (note.find()) {
			String octave = note.group(3);
			if (octave == null)
				continue;
			char letter = note.group(2).charAt(0);
			boolean sharp = note.group(1) != null && note.group(1).startsWith("^");
			if (octave.startsWith("'")) {
				// c' is Lotro's highest note; anything above it, ^c' included
				if (octave.length() > 1 || letter != 'c' || sharp)
					return true;
			} else if (octave.length() > (Character.isUpperCase(letter) ? 1 : 2)) {
				return true; // Below C, (C,, or c,,,)
			}
		}
		return false;
	}

	private static boolean isLotroTitle(String title) {
		if (INSTRUMENT_FULL_NAME_PATTERN.matcher(title).find())
			return true;
		Matcher bracketed = BRACKETED_PATTERN.matcher(title);
		while (bracketed.find()) {
			// Only a name: the whole text in the brackets
			String text = bracketed.group(1).trim();
			Pair<LotroInstrument, MatchResult> match = LotroInstrument.matchInstrument(text);
			if (match != null && match.second.start() == 0 && match.second.end() == text.length())
				return true;
		}
		return false;
	}

	public static Sequence convert(Params params) throws FileParseException {
		// The line being parsed when it may be a field's text going on, the field's line and its letter
		int[] wrappedField = { -1, -1, 0 };
		try {
			return convert(params.filesData, params.useLotroInstruments, params.instrumentOverrideMap, params.abcInfo,
					params.enableLotroErrors, params.stereo, params.generateRegions, params.expandRepeats, params.specTempo,
					params.standardPitch, params.chordAccompaniment, params.standard2011, params.warningHandler,
					wrappedField);
		} catch (FileParseException e) {
			// A line in a tune's header that isn't music (Bruce Thomson's files: "Tradition" read as T, a trill, and r)
			if (wrappedField[0] >= 0 && e.getLine() == wrappedField[0]) {
				throw new FileParseException(UIText.get("common.abctomidi.field.wrapped", (char) wrappedField[2] + ":",
						String.valueOf(wrappedField[1])), e.getFileName(), e.getLine(), 0, wrappedField[1], 0);
			}
			throw e;
		}
	}

	/** A bare Q: (Q:100): its tempo, the unit note it counts in ABC 2.1 (L:) and the meter's beat it may have meant. */
	public record BareTempo(int bpm, String unitNote, String beat) {
	}

	private static final Pattern BARE_TEMPO = Pattern.compile("(?:C\\s*=\\s*)?(\\d+)");
	private static final Pattern METER_FRACTION = Pattern.compile("^\\(?([\\d+\\s]+)\\)?\\s*/\\s*(\\d+)");

	/**
	 * For Maestro's notice when a new project is made from standard ABC: the first tune's Q: when it is bare (Q:100,
	 * Q:C=100). ABC 2.1 (10.1, deprecated form) counts it in unit notes, L:, and so do we, but many files mean beats of
	 * the meter by it (O'Neill's metronome marks, FolkWiki; abcjs reads it so). The tempo can be changed in Maestro.
	 *
	 * @return null if the first tune's Q: has a note length, or its L: is the meter's beat (both readings are the same);
	 *         a bpm of -1 (and no notes) if it has no Q: at all, so its tempo is guessed (common.abctomidi.no.tempo)
	 */
	public static BareTempo bareTempo(List<FileAndData> filesData) {
		String tempo = null;
		String meter = null;
		String unitNote = null;
		for (String line : filesData.getFirst().lines) {
			line = stripComment(line).trim();
			if (line.startsWith("K:"))
				break; // The first tune's header ends (a file header has no K:)
			if (line.startsWith("Q:"))
				tempo = line.substring(2).trim();
			else if (line.startsWith("M:"))
				meter = line.substring(2).trim();
			else if (line.startsWith("L:"))
				unitNote = line.substring(2).replace(" ", "");
		}
		Matcher bare = (tempo == null) ? null : BARE_TEMPO.matcher(tempo);
		if (bare == null)
			return new BareTempo(-1, null, null); // No Q: at all: the tempo is guessed
		if (!bare.matches())
			return null;
		int numerator = 4;
		int denominator = 4;
		if ("C|".equals(meter)) {
			numerator = 2;
			denominator = 2;
		} else if (meter != null && !meter.equals("C")) {
			Matcher fraction = METER_FRACTION.matcher(meter);
			if (!fraction.find())
				return null; // M:none
			numerator = 0;
			for (String part : fraction.group(1).split("\\+"))
				numerator += Integer.parseInt(part.trim()); // 2+2+3/8
			denominator = Integer.parseInt(fraction.group(2));
		}
		if (unitNote == null)
			unitNote = (numerator / (double) denominator < 0.75) ? "1/16" : "1/8"; // ABC 2.1, 3.1.7
		String beat = "1/" + denominator;
		return unitNote.equals(beat) ? null : new BareTempo(Integer.parseInt(bare.group(1)), unitNote, beat);
	}

	/** @param wrappedField See convert(Params) */
	private static Sequence convert(List<FileAndData> filesData, boolean useLotroInstruments,
									Map<Integer, LotroInstrument> instrumentOverrideMap, AbcInfo abcInfo, final boolean enableLotroErrors,
									final int stereo, final boolean generateRegions, final boolean expandRepeats, boolean specTempo,
									boolean standardPitch, boolean chordAccompaniment, boolean standard2011,
									WarningHandler warningHandler, int[] wrappedField)
			throws FileParseException {
		if (abcInfo == null)
			abcInfo = new AbcInfo();
		else
			abcInfo.reset();

		abcInfo.warningHandler = warningHandler;

		TuneInfo info = new TuneInfo();
		info.setStandardTempo(specTempo && !enableLotroErrors);
		info.setStandardPitch(standardPitch && !enableLotroErrors);
		// Play ABC 2.1 where Lotro plays it otherwise (Params.standard2011); with Lotro errors, Lotro's reading
		final boolean abc21 = standard2011 && !enableLotroErrors;
		info.setStandard2011(abc21);
		Sequence seq = null;
		Track track = null;

		int channel = 0;
		boolean drumPart = false; // Standard ABC with %%MIDI channel 10: the notes are General MIDI percussion
		Set<Integer> drumTracks = new HashSet<>(); // Their track indexes, on the MIDI drum channel
		Map<Integer, int[]> accompanimentPrograms = new HashMap<>(); // part => {bass, chords} programs, standard ABC
		int trackNumber = 0;
		int trackIndex = 0;
		// Where the meter (and with it the PPQN) last changed, for the "must be the same" error
		int meterChangeLine = 0;
		int meterChangeColumn = 0;
		// Chord symbols and bar lines per part (trackNumber), for the accompaniment (Params.chordAccompaniment)
		boolean playChords = chordAccompaniment && !enableLotroErrors;
		boolean hymn = playChords && isHymn(filesData); // Full chords instead of bass and chord in turn
		// Bagpipe drones (Drone): standard ABC only, never a file made for Lotro
		boolean playDrones = playChords && standardPitch;
		Map<Integer, List<ChordSymbol>> chordSymbols = new TreeMap<>();
		Map<Integer, NavigableSet<Long>> partBarTicks = new HashMap<>();
		Map<Integer, Long> partEndTicks = new HashMap<>(); // Where each part's written notes end
		// The Q: of the header being read, checked when the header ends (ABC 2.1 lets M: come after it)
		String headerTempo = null;
		int headerTempoLine = 0;
		int headerTempoColumn = 0;

		int partChordsNumber = 0;

		int guessNotes = 0; // All notes and rests, for the triplet guess
		int guessTripletNotes = 0; // Those with triplet timing

		int chordStartIndex = 0;
		double chordStartTick = 0;
		double chordEndTick = 0;
		long PPQN = 0;
		Map<Integer, AbcRegion> tiedRegions = new HashMap<>();

		Map<Integer, Integer> tiedNotes = new HashMap<>(); // noteId => (line << 16) | column
		// ABC 2.1 (4.11), with standard2011: a tie joins a note to the next note (or chord), which must have its pitch.
		// The pitches tied when the last note or chord ended, and the pitches of the note or chord being read.
		Set<Integer> tiesToContinue = new HashSet<>();
		Set<Integer> eventPitches = new HashSet<>();
		// A repeat sign or an ending since the last note or chord: the next note played may be another one (|: c ... d- :|
		// goes back to c), so a tie that isn't continued there just ends
		boolean crossedRepeat = false;
		Map<Integer, Double> tiedNoteStartTicks = new HashMap<>(); // noteId => start of the tie's first note
		Map<Integer, Double> tiedNoteEndTicks = new HashMap<>(); // noteId => end of the tied note so far, see below
		Map<Integer, LotroInstrument> trackInstruments = new HashMap<>(); // trackIndex => instrument it plays, see endTrack
		Map<Integer, Integer> accidentals = new HashMap<>(); // noteId => deltaNoteId

		List<MidiEvent> noteOffEvents = new ArrayList<>();
		List<Triple<Integer, Double, String>> notesOn = new ArrayList<>();
		Map<Integer, Dynamics> attackDynamics = new HashMap<>(); // lotroNoteId => volume when it was last attacked
		// Lyrics (w:), sung when the part ends, see singLyrics. Keyed by source position, as the repeats play notes again.
		TreeMap<Long, LyricNote> lyricNotes = new TreeMap<>(); // position (see sourcePosition) => where it's played
		TreeMap<Integer, String> lyricLines = new TreeMap<>(); // line index => text of the w: line there
		TreeSet<Integer> musicLines = new TreeSet<>(); // Line indexes of the part's lines of music
		Set<Integer> verseLineIndexes = new HashSet<>(); // W: lines written, so a repeat doesn't write them again
		int lyricBar = 0; // Bars in the part so far, for | in a w: line
		Repeats repeats = new Repeats(expandRepeats, info);
		// Lyrics without timing (W:), each a lyric line in track 0. Before the part's notes they wait here for its track.
		List<String> pendingVerseLines = new ArrayList<>();
		long lastAttackTick = -1; // Where the part's last note started, for W: lines after the notes
		// Beat-group accents (BEAT_GROUP_ACCENT_STEPS): the notes started since the last bar line, accented when the bar
		// is known; where the part's last bar line was (null before its first, so the first bar can be a pickup)
		List<BarAttack> barAttacks = new ArrayList<>();
		Long lastBarTick = null;
		// The next W: line's tick at the earliest. Each line gets a tick of its own: on an equal tick MidiText's order of
		// lines is not defined.
		long nextVerseTick = 0;
		// For +: after a W: line: its text, and its event once written (null while it waits in pendingVerseLines)
		String lastVerseText = "";
		MidiEvent lastVerseEvent = null;
		char lastField = 0; // The field on the line before (w for w:), for a +: line; 0 after a line of music
		int lastFieldLine = -1; // The line of lastField
		// A broken rhythm at a line's end (e>), for the first note of the next line of notes: {numerator, denominator}
		// of that note, and the line and column of the >; {1, 1, -1, -1} if none
		int[] brokenCarried = { 1, 1, -1, -1 };
		int lastLyricLine = -1; // Line index of the last w: line, for a +: line after it
		// Information fields ("Composer: ..."), in the file's order; see writeInfoLines. Lines already read are kept by
		// their region line number, so a repeat going back doesn't read them again.
		List<String> infoLines = new ArrayList<>();
		Set<Integer> infoLineNumbers = new HashSet<>();
		int partTitles = 0; // T: lines in the part's header: the first names the part, the others are other titles
		// Clues to a part's MIDI program in standard ABC (MidiProgramGuess): the file header's, and the part's, which
		// starts from them. In the file header both are the same.
		MidiProgramGuess.Clues fileProgramClues = new MidiProgramGuess.Clues();
		MidiProgramGuess.Clues partProgramClues = fileProgramClues;
		// Bagpipe drones (Drone), the same way: the file header's and the part's; each part's that sounds, by part
		Drone fileDrone = new Drone();
		Drone partDrone = fileDrone;
		Map<Integer, Drone> drones = new TreeMap<>();

		int lineNumberForRegions = -1;
		// With voices (VoiceSplitter) the lines read aren't in the file's order: a part ends at the line read before
		// its next X:, and the next file's region lines start after the highest line read
		int previousLineForRegions = -1;
		int highestLineForRegions = -1;
		abcInfo.abcTrackInfos = new ArrayList<>();
		for (FileAndData fileAndData : filesData) {
			// The previous file's last part ends here
			if (track != null && playChords) {
				partEndTicks.put(trackNumber, Math.round(chordStartTick));
				endPartDrone(partDrone, trackNumber, Math.round(chordStartTick), drones);
			}
			if (track != null && abc21)
				accentGroupStarts(barAttacks, (lastBarTick != null) ? lastBarTick : 0, groupTicks(info), useLotroInstruments);
			// AbcToMidi.convert, start of each file
			track = null;
			info.newFile();
			partTitles = 0; // This file's header: its first T: is the file's title, not another one of the last part
			lastField = 0; // A +: at the file's start continues nothing of the file before
			fileProgramClues = new MidiProgramGuess.Clues();
			partProgramClues = fileProgramClues;
			fileDrone = new Drone();
			partDrone = fileDrone;
			// The file's name in messages; from an X: on with the tune's number and title (a tune of a songbook)
			String baseFileName = fileAndData.file.getName();
			String fileName = baseFileName;
			String tuneNumber = null; // The X: of the part being read
			String fileTitle = null; // The file header's first T:, the name of a part without a T: of its own
			abcInfo.addSourceFile(fileAndData.file);
			int lineNumber = 0;
			int partStartLine = 0;
			List<String> lines = fileAndData.lines;
			// ABC 2.1 (7), with standard2011: a tune's voices become parts that play together (VoiceSplitter). Lotro
			// plays them one after another (B42), so without the flag V: changes nothing. Messages give the lines of
			// the file.
			int[] sourceLineNumbers = null;
			if (abc21) {
				VoiceSplitter.Result voices = VoiceSplitter.split(lines);
				if (voices != null) {
					lines = voices.lines();
					sourceLineNumbers = voices.sourceLineNumbers();
				}
				PartOrder.Result ordered = PartOrder.apply(lines);
				if (ordered != null) {
					lines = ordered.lines();
					int[] orderedSources = ordered.sourceLineNumbers();
					if (sourceLineNumbers != null) {
						for (int k = 0; k < orderedSources.length; k++)
							orderedSources[k] = sourceLineNumbers[orderedSources[k] - 1];
					}
					sourceLineNumbers = orderedSources;
				}
			}
			int firstLineForRegions = highestLineForRegions + 1; // Region line numbers run on through all files
			int startColumn = 0; // Where the parsing of the line starts: mid-line when going back for a repeat
			// ABC 2.1 (2.2), with standard2011 in a file of X: tunes: free text before the first X: (besides the file
			// header's fields) and after a tune; an empty line ends the tune (2.2.1). Lotro plays on after an empty
			// line (B14), so without the flag every line is read.
			boolean skipFreeText = abc21 && lines.stream().anyMatch(l -> l.startsWith("X:"));
			// ABC 2.1 (12): a file without %abc-2.1 (or higher) on its first line is read loosely: a lone ! is a line break
			boolean looseBang = abc21 && !isAbc21OrLater(lines);
			boolean tuneSeen = false; // An X: so far in this file
			boolean inTune = false; // Between an X: and the empty line that ends its tune
			lineLoop: for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
				String line = lines.get(lineIndex);
				wrappedField[0] = -1; // Only a line being parsed may be a field's text going on
				lineNumber = (sourceLineNumbers != null) ? sourceLineNumbers[lineIndex] : lineIndex + 1;
				previousLineForRegions = lineNumberForRegions;
				lineNumberForRegions = firstLineForRegions + lineNumber - 1;
				highestLineForRegions = Math.max(highestLineForRegions, lineNumberForRegions);

				if (skipFreeText) {
					if (line.startsWith("X:")) {
						tuneSeen = true;
						inTune = true;
					} else if (inTune && line.isBlank()) {
						inTune = false;
					} else if (!inTune && (tuneSeen || !FILE_HEADER_LINE_PATTERN.matcher(line).matches())) {
						continue; // Free text
					}
				}

				// Handle extended info
				Matcher xInfoMatcher = XINFO_PATTERN.matcher(line);
				if (xInfoMatcher.matches()) {
					// %%MIDI program 73 (abc2midi): a clue to the part's MIDI program, in its header. %%MIDI droneon:
					// anywhere in the part.
					if (xInfoMatcher.group(XINFO_FIELD).equalsIgnoreCase("MIDI")) {
						if (track == null)
							partProgramClues.midiDirective("MIDI " + xInfoMatcher.group(XINFO_VALUE));
						partDrone.midiDirective("MIDI " + xInfoMatcher.group(XINFO_VALUE), Math.round(chordStartTick));
					}
					AbcField field = AbcField
							.fromString(xInfoMatcher.group(XINFO_FIELD) + xInfoMatcher.group(XINFO_COLON));

					if (field == null) {
						// %%linebreak, %%propagate-accidentals ...: the same as I: (ABC 2.1, 3.1.17), see AbcInstructions
						info.applyInstruction(xInfoMatcher.group(XINFO_FIELD) + " " + xInfoMatcher.group(XINFO_VALUE));
					} else if (field == AbcField.TEMPO) {
						try {
							info.addTempoEvent(Math.round(chordStartTick), xInfoMatcher.group(XINFO_VALUE).trim());
						} catch (IllegalArgumentException e) {
							// Apparently that wasn't actually a tempo change
						}
					} else if (field != null) {
						String value = xInfoMatcher.group(XINFO_VALUE).trim();

						abcInfo.setExtendedMetadata(field, value);

						if (field == AbcField.PART_NAME) {
							info.setTitle(value, true);
							abcInfo.setPartName(trackNumber, value, true);

							if (instrumentOverrideMap == null || !instrumentOverrideMap.containsKey(trackNumber)) {
								LotroInstrument instrument = LotroInstrument.findInstrumentName(value, null);
								if (!info.isInstrumentDefinitiveSet() && instrument != null)
									info.setInstrument(instrument, false);
							}
							if (abcInfo.getUserPan(trackNumber) == null) {
								Integer titlePan = null;
								String titleLower = value.toLowerCase();
								if (PanGenerator.leftRegex.matcher(titleLower).find())
									titlePan = 0+14;//The odd numbers are for backwards compat
								else if (PanGenerator.rightRegex.matcher(titleLower).find())
									titlePan = 127-13;//The odd numbers are for backwards compat
								else if (PanGenerator.centerRegex.matcher(titleLower).find())
									titlePan = 64;

								if (titlePan != null) abcInfo.setPartPan(trackNumber, titlePan);
							}
						} else if (field == AbcField.MADE_FOR) {
							if (instrumentOverrideMap == null || !instrumentOverrideMap.containsKey(trackNumber)) {
								LotroInstrument instrument = LotroInstrument.findInstrumentName(value, null);
								if (instrument != null)
									info.setInstrument(instrument, true);
							}
						} else if (field == AbcField.USER_PAN) {
							if ("auto".equalsIgnoreCase(value.trim())) {
								abcInfo.setPartPan(trackNumber, null);
							} else {
								try {
									int pan = Math.clamp(Integer.parseInt(value.trim()), 0, 127);
									abcInfo.setPartPan(trackNumber, pan);
								} catch (NumberFormatException nfe) {
									abcInfo.setPartPan(trackNumber, null);
								}
							}
						}
					}

					continue;
				}

				line = stripComment(line);
				if (line.isBlank())
					continue;

				// Lyrics under the notes (w:), sung when the part ends (singLyrics). More w: lines under the same notes
				// are later verses, sung when the repeats play the notes again. (W: lyrics after the tune are an info
				// field, with no timing.)
				if (line.stripLeading().startsWith("w:")) {
					lyricLines.put(lineIndex, line.stripLeading().substring(2));
					lastField = 'w';
					lastLyricLine = lineIndex;
					continue;
				}
				// +: continues the field on the line before (ABC 2.1, 3.3), with a space between. Tested in Lotro: it
				// refuses the part.
				if (line.startsWith("+:")) {
					if (enableLotroErrors) {
						throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.field.continuation"),
								fileName, lineNumber, 0);
					}
					String more = line.substring(2).trim();
					if (lastField == 'w') {
						// Only once: a repeat going back reads it again
						if (verseLineIndexes.add(lineIndex))
							lyricLines.put(lastLyricLine, lyricLines.get(lastLyricLine) + " " + more);
					} else if (lastField == 'W' && verseLineIndexes.add(lineIndex)) {
						lastVerseText = lastVerseText + " " + more;
						String joined = AbcText.decode(lastVerseText);
						if (lastVerseEvent == null && !pendingVerseLines.isEmpty()) {
							pendingVerseLines.set(pendingVerseLines.size() - 1, joined);
						} else if (lastVerseEvent != null) {
							seq.getTracks()[0].remove(lastVerseEvent);
							lastVerseEvent = MidiFactory.createTextMetaEvent(MidiConstants.META_LYRIC, "<" + joined,
									lastVerseEvent.getTick());
							seq.getTracks()[0].add(lastVerseEvent);
						}
					} else if (INFO_LABELS.containsKey(lastField) && !infoLines.isEmpty()
							&& infoLines.getLast().startsWith(INFO_LABELS.get(lastField) + ": ")
							&& infoLineNumbers.add(lineNumberForRegions)) {
						infoLines.set(infoLines.size() - 1, infoLines.getLast() + " " + AbcText.decode(more));
					}
					// Other fields (T: K: ...) keep the text of their first line
					continue;
				}
				// Symbol lines (s:): decorations for the notes above, like w: for lyrics. Lotro plays on (tested).
				if (line.stripLeading().startsWith("s:"))
					continue;

				// Remark lines (r:, ABC 2.1, 3.1): ignored anywhere, in the header or the tune. Lotro plays on (B68).
				if (line.stripLeading().startsWith("r:"))
					continue;

				// Macros (m:, ABC 2.1, 4.16): not expanded yet, so the music using them can't be played as written
				if (line.stripLeading().startsWith("m:"))
					throw new FileParseException(UIText.get("common.abctomidi.macros.not.supported"), fileName,
							lineNumber, 0);

				int chordSize = 0;

				Matcher infoMatcher = INFO_PATTERN.matcher(line);
				if (infoMatcher.matches()) {
					char type = Character.toUpperCase(infoMatcher.group(INFO_TYPE).charAt(0));
					String value = unescapePercent(infoMatcher.group(INFO_VALUE).trim());
					char previousField = lastField;
					lastField = type;
					lastFieldLine = lineNumber;

					// A T: after the part's notes started is a section title (ABC 2.1). Lotro plays on (tested), and it
					// doesn't name the song or the part.
					if (type == 'T' && track != null)
						continue;

					// The song's title comes from the first T: of each header (more T: lines are other titles of the tune)
					if (type != 'T' || partTitles == 0)
						abcInfo.setMetadata(type, value);

					// Information about the tune. H: lines in a row are one text (ABC 1.6: H: may go on over several
					// lines); other fields in a row are one each, e.g. two C: for two composers.
					String infoLabel = INFO_LABELS.get(type);
					if (infoLabel != null && infoLineNumbers.add(lineNumberForRegions)
							&& !(type == 'N' && MAESTRO_NOTE_PATTERN.matcher(value).matches())) {
						String text = AbcText.decode(value);
						if (type == 'H' && previousField == 'H' && !infoLines.isEmpty()
								&& infoLines.getLast().startsWith(infoLabel + ": "))
							infoLines.set(infoLines.size() - 1, infoLines.getLast() + " " + text);
						else
							infoLines.add(infoLabel + ": " + text);
					}

					// Clues to the part's MIDI program in its header (standard ABC, see MidiProgramGuess)
					if (track == null) {
						switch (type) {
							case 'I' -> partProgramClues.midiDirective(value);
							case 'G' -> partProgramClues.group(value);
							case 'V' -> partProgramClues.voice(value);
							case 'T' -> partProgramClues.title(value);
							case 'K' -> partProgramClues.key(value);
							case 'R' -> partProgramClues.rhythm(value);
							default -> {
							}
						}
					}

					// A voice's part (VoiceSplitter) is named by the voice; the song keeps the tune's title
					if (track == null && type == 'V' && abc21) {
						String voicePartName = VoiceSplitter.partName(value);
						if (voicePartName != null)
							abcInfo.setPartName(trackNumber, voicePartName, true);
					}

					try {
						switch (type) {
							case 'X':
								endUnconnectedTies(enableLotroErrors, tiedNotes, tiedNoteStartTicks, tiedNoteEndTicks,
										tiedRegions, tiesToContinue, track, channel, info.getDynamics().getVol(useLotroInstruments),
										noteOffEvents, fileName);
								if (brokenCarried[2] >= 0) {
									// e> at the end of the part before: no note of its own to go to
									throw new FileParseException(UIText.get("common.abctomidi.broken.unfinished"), fileName,
											brokenCarried[2], brokenCarried[3]);
								}

								if (track != null)
									singLyrics(track, lyricNotes, lyricLines, musicLines, sourceLineNumbers, lastAttackTick + 1);
								if (track != null && playChords) {
									partEndTicks.put(trackNumber, Math.round(chordStartTick));
									endPartDrone(partDrone, trackNumber, Math.round(chordStartTick), drones);
								}
								if (track != null && abc21)
									accentGroupStarts(barAttacks, (lastBarTick != null) ? lastBarTick : 0, groupTicks(info),
											useLotroInstruments);
								lastBarTick = null;

								accidentals.clear();
								noteOffEvents.clear();
								notesOn.clear();
								attackDynamics.clear();
								lyricNotes.clear();
								lyricLines.clear();
								musicLines.clear();
								verseLineIndexes.clear();
								lyricBar = 0;
								lastAttackTick = -1;
								repeats.newPart();

								if (trackNumber > 0)
									abcInfo.setPartEndLine(trackNumber, previousLineForRegions);


								if (value.isEmpty() && enableLotroErrors) {
									// Tested in Lotro (B81): it refuses the whole file, its other parts too
									throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.x.empty"), fileName,
											lineNumber, 0);
								}
								info.newPart(partNumber(value, info.getPartNumber()));
								tuneNumber = value;
								fileName = tuneFileName(baseFileName, tuneNumber, null);
								trackNumber++;
								// Named by its first T:, else by the file header's, else by the file
								abcInfo.setPartName(trackNumber, defaultPartName(fileTitle, baseFileName), false);
								partStartLine = lineNumber;
								// The part starts from the file header's meter, so a meter error in a part without M: points here
								meterChangeLine = lineNumber;
								meterChangeColumn = 0;
								chordStartTick = 0;
								chordEndTick = 0;
								abcInfo.setPartNumber(trackNumber, info.getPartNumber());
								abcInfo.setPartStartLine(trackNumber, lineNumberForRegions);
								track = null; // Will create a new track after the header is done
								if (instrumentOverrideMap != null && instrumentOverrideMap.containsKey(trackNumber)) {
									info.setInstrument(instrumentOverrideMap.get(trackNumber), false);
								}
								partChordsNumber = 0;
								partTitles = 0;
								partProgramClues = fileProgramClues.forPart();
								partDrone = fileDrone.forPart();
								break;
							case 'T':
								if (partTitles++ > 0) {
									// More T: lines in the header are other titles of the tune
									if (infoLineNumbers.add(lineNumberForRegions))
										infoLines.add("Also known as: " + AbcText.decode(value));
								} else {
									// The first T: names the part (in the file header: the file, and every part without a T:)
									if (tuneNumber != null)
										fileName = tuneFileName(baseFileName, tuneNumber, AbcText.decode(value));
									else if (fileTitle == null)
										fileTitle = value;
									info.setTitle(value, false);
									// In a later file's header, trackNumber is still the file before's last part
									if (tuneNumber != null || trackNumber == 0)
										abcInfo.setPartName(trackNumber, value, false);
								}
								// In standard ABC, T: is the song's title: it doesn't name an instrument
								if (!info.isStandardPitch()
										&& (instrumentOverrideMap == null || !instrumentOverrideMap.containsKey(trackNumber))) {
									if (!info.isInstrumentSet()) {
										LotroInstrument instrument = LotroInstrument.findInstrumentName(value, null);
										if (instrument != null)
											info.setInstrument(instrument, false);
									}
								}
								break;
							case 'W':
								// Lyrics without timing, e.g. all verses: each W: line is a lyric line of its own (MidiText's
								// LINE) in track 0, which Maestro shows with the sung lyrics (MidiText.setFromAbc). Before the
								// notes they go on tick 0, else after the last note that started, so no track gets longer.
								// One tick per line: on an equal tick MidiText's order of lines is not defined. (The header
								// lines, see writeInfoLines, take the ticks left free.)
								if (!verseLineIndexes.add(lineIndex)) {
									// Already written, before a repeat went back
								} else if (track == null) {
									pendingVerseLines.add(AbcText.decode(value));
									lastVerseEvent = null;
								} else {
									long tick = Math.max(nextVerseTick, lastAttackTick + 1);
									lastVerseEvent = MidiFactory.createTextMetaEvent(MidiConstants.META_LYRIC,
											"<" + AbcText.decode(value), tick);
									seq.getTracks()[0].add(lastVerseEvent);
									nextVerseTick = tick + 1;
								}
								lastVerseText = value;
								break;
							case 'I':
								// I:linebreak and I:decoration (ABC 2.1) are kept; I:MIDI droneon ... turns the drone on or
								// off; others (I:MIDI program ...) change nothing here
								info.applyInstruction(value);
								partDrone.midiDirective(value, Math.round(chordStartTick));
								break;
							case 'R':
								// The tune's type: the tempo of a song without Q: (standard ABC)
								info.setRhythm(value);
								break;
							case 'P':
								// A section of the tune starts (ABC 2.1, 3.1.9): a :| without |: after it goes back to
								// its start, not into the section before it (which PartOrder may have changed)
								if (abc21 && track != null)
									repeats.sectionEnd(lineIndex + 1, 0);
								break;
							case 'K':
								String notForLotro;
								try {
									notForLotro = info.setKey(value);
								} catch (TuneInfo.KeyWordException e) {
									// Tested in Lotro (B23, B38): K:G ^c, K:C exp ^f and K:C foo play nothing
									if (enableLotroErrors)
										notForLotro = e.word;
									else
										throw e;
								}
								if (enableLotroErrors && value.isBlank()) {
									// Tested in Lotro (B38): an empty K:, in the header or the tune, plays nothing
									throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.key.empty"),
											fileName, lineNumber, infoMatcher.start(INFO_VALUE));
								}
								if (enableLotroErrors && !notForLotro.isEmpty()) {
									throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.key.words",
											notForLotro), fileName, lineNumber, infoMatcher.start(INFO_VALUE));
								}
								break;
							case 'L':
								// Tested in Lotro (B40): an L: line after the part's first notes changes nothing, the notes
								// after it keep the header's L:. An error only if it would change the length: files made
								// for Lotro repeat the L: after a mid-song M: ("M:2/4", "L:1/8")
								int lengthNum = info.getLNum();
								int lengthDenom = info.getLDenom();
								// The note length doesn't affect the PPQN, so it may differ between parts
								info.setNoteDivisor(value);
								if (enableLotroErrors && track != null
										&& (long) info.getLNum() * lengthDenom != (long) lengthNum * info.getLDenom()) {
									throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.length.after.notes"),
											fileName, lineNumber, infoMatcher.start(INFO_VALUE));
								}
								break;
							case 'M':
								if (enableLotroErrors && value.equalsIgnoreCase("none")) {
									throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.meter.none"),
											fileName, lineNumber, infoMatcher.start(INFO_VALUE));
								}
								if (enableLotroErrors && value.contains("+")) {
									// ABC 2.1 (3.1.6): the numerator as a sum shows the beat groups; not tested in Lotro
									throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.meter.sum",
											value), fileName, lineNumber, infoMatcher.start(INFO_VALUE));
								}
								info.setMeter(value, track == null);
								meterChangeLine = lineNumber;
								meterChangeColumn = infoMatcher.start(INFO_VALUE);
								break;
							case 'Q': {
								if (enableLotroErrors && value.indexOf('"') >= 0) {
									throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.tempo.text",
											value), fileName, lineNumber, infoMatcher.start(INFO_VALUE));
								}
								int tempo = info.getPrimaryTempoBPM();
								info.setPrimaryTempoBPM(value);
								if (track != null) {
									if (info.getPrimaryTempoBPM() != tempo) {
										throw new FileParseException(UIText.get("common.abctomidi.tempo.change.mid.part"),
												fileName, lineNumber, infoMatcher.start(INFO_VALUE));
									}
								} else {
									// Checked when the header ends
									headerTempo = value;
									headerTempoLine = lineNumber;
									headerTempoColumn = infoMatcher.start(INFO_VALUE);
								}
								break;
							}
						}
					} catch (IllegalArgumentException e) {
						// NumberFormatException's own message ("For input string: ...") doesn't say what's wrong
						String message = (e instanceof NumberFormatException)
								? UIText.get("common.abctomidi.field.invalid.number", String.valueOf(type), value)
								: e.getMessage();
						throw new FileParseException(message, fileName, lineNumber, infoMatcher.start(INFO_VALUE));
					}
				} else {
					// The line contains notes

					if (trackNumber == 0) {
						// This ABC file doesn't have an "X:" line before notes. Tsk tsk.
						trackNumber = 1;
						if (instrumentOverrideMap != null && instrumentOverrideMap.containsKey(trackNumber)) {
							info.setInstrument(instrumentOverrideMap.get(trackNumber), false);
						}
					}

					// The part's first line of notes: its header has just ended. (Its track is made further down.)
					boolean headerEnded = (track == null);
					if (headerEnded) {
						// The tempo is known now, whatever the order of Q: and M:
						info.endHeader();
						double beat = info.getTempoBeat();
						int denominator = info.getBarDenominator();
						if (enableLotroErrors && headerTempo != null && beat > 0
								&& Math.abs(beat * denominator - 1) > 1e-9) {
							// Tested in Lotro (B15): the beat is the meter's denominator, whatever the note length
							long asLotro = Math.round(info.getTempoBeatsPerMinute() * beat * denominator);
							throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.tempo.note.length",
									headerTempo, String.valueOf(info.getTempoBeatsPerMinute()),
									String.valueOf(denominator), String.valueOf(asLotro)), fileName, headerTempoLine,
									headerTempoColumn);
						}
						if (seq != null && info.getPrimaryTempoBPM() != abcInfo.getPrimaryTempoBPM()) {
							throw new FileParseException(UIText.get("common.abctomidi.parts.same.tempo",
									String.valueOf(info.getPrimaryTempoBPM()),
									String.valueOf(abcInfo.getPrimaryTempoBPM())), fileName,
									(headerTempo != null) ? headerTempoLine : lineNumber,
									(headerTempo != null) ? headerTempoColumn : 0);
						}
						headerTempo = null;
					}

					if (seq == null) {
						try {
							PPQN = info.getPpqn();
							seq = new Sequence(Sequence.PPQ, (int) PPQN);

							abcInfo.setPrimaryTempoBPM(info.getPrimaryTempoBPM());

							// Create track 0, which will later be filled with the
							// tempo events and song metadata (title, etc.)
							trackIndex = 0;
							seq.createTrack();

							abcInfo.setPartNumber(0, 0);
							// The first part's name, as above: its first T:, else the file header's, else the file
							abcInfo.setPartName(0, info.getTitle().isEmpty() ? defaultPartName(fileTitle, baseFileName)
									: info.getTitle(), false);
							abcInfo.setTimeSignature(info.getMeter());
							abcInfo.setKeySignature(info.getKey());

							track = null;
						} catch (InvalidMidiDataException mde) {
							throw new FileParseException(UIText.get("common.abctomidi.midi.error", mde.getMessage()),
									fileName);
						}
					}

					if (track == null) {
						// The parts before it without notes get their empty tracks first
						while (partTrackCount(seq) < trackNumber - 1)
							addEmptyTrack(seq, abcInfo, partTrackCount(seq) + 1, trackInstruments, useLotroInstruments,
									fileName);
						trackIndex = seq.getTracks().length;
						channel = getTrackChannel(trackIndex);
						if (channel > MidiConstants.CHANNEL_COUNT_ABC - 1) {
							throw new FileParseException(UIText.get("common.abctomidi.too.many.parts",
									String.valueOf(MidiConstants.CHANNEL_COUNT_ABC - 1)), fileName, partStartLine);
						}
						track = seq.createTrack();
						trackInstruments.put(trackIndex, info.getInstrument());
						// Standard ABC names no Lotro instrument: its program only sounds like what the part was written
						// for, and %%MIDI channel 10 puts it on the drum channel. A Lotro instrument that was set (the
						// user's choice, %%made-for, %%part-name) gives its own program, as in Lotro files.
						int program = info.getInstrument().midi.id();
						drumPart = false;
						if (info.isStandardPitch() && !info.isInstrumentSet()) {
							program = partProgramClues.program();
							drumPart = partProgramClues.isDrums();
							accompanimentPrograms.put(trackNumber, new int[] { partProgramClues.bassProgram(),
									partProgramClues.chordProgram() });
						}
						if (drumPart) {
							channel = MidiConstants.DRUM_CHANNEL; // Free: getTrackChannel never gives it
							drumTracks.add(trackIndex);
						} else if (playChords) {
							// Highland pipes, or a part set to Bag Pipe: the pipes' drone (without drone directives)
							partDrone.endHeader(partProgramClues.isHighlandPipes(), partProgramClues.explicitProgram());
						}
						track.add(MidiFactory.createLotroChangeEvent(program, channel, 0));
						abcInfo.abcTrackInfos.add(new ExportTrackInfo(0, null, null, channel, program, Long.MAX_VALUE, 0,0,0,0,0,0, null));
						if (useLotroInstruments) {
							track.add(MidiFactory.createChannelVolumeEvent(MidiConstants.MAX_VOLUME, channel, 1L));
						}
						track.add(MidiFactory.createReverbControlEvent(AbcConstants.MIDI_REVERB, channel, 1L));
						track.add(MidiFactory.createChorusControlEvent(AbcConstants.MIDI_CHORUS, channel, 1L));

						// The header is done: info has the part's instrument. Definitive means it came from %%made-for.
						abcInfo.setPartInstrument(trackNumber, info.getInstrument(), info.isInstrumentDefinitiveSet());

						// W: lines so far, before the first note
						for (String verseLine : pendingVerseLines)
							seq.getTracks()[0].add(MidiFactory.createTextMetaEvent(MidiConstants.META_LYRIC, "<" + verseLine,
									nextVerseTick++));
						pendingVerseLines.clear();
					}

					// A line that may be a field's text going on (Bruce Thomson's files): the part's first line of notes,
					// right after a field, before the tune's K:. If it isn't music, convert(Params) says so.
					if (musicLines.isEmpty() && Character.isUpperCase(lastField) && lastField != 'K' && lastField != 'X'
							&& keyFollows(lines, lineIndex + 1)) {
						wrappedField[0] = lineNumber;
						wrappedField[1] = lastFieldLine;
						wrappedField[2] = lastField;
					}
					if (!enableLotroErrors)
						line = withSlipsFixed(line);
					Matcher m = NOTE_PATTERN.matcher(line);
					lastField = 0;
					musicLines.add(lineIndex);
					repeats.musicLine(lineIndex);
					int i = startColumn;
					startColumn = 0;
					boolean inChord = false;
					Set<Integer> chordNoteIds = new HashSet<>(); // Pitches in the current chord; only the first of each sounds
					int chordRests = 0; // Rests in the current chord; Lotro's limit counts them as one (B73)
					// Length multiplier from the suffix after the current chord's ']' (e.g. [ceg]3/4), applied to
					// every note in the chord. Stays 1/1 when the chord has no suffix or we're not in a chord.
					int chordLenNumerator = 1;
					int chordLenDenominator = 1;
					String chordLenStr = "";
					// A tie after the chord ([ce]- or [ce]2-): every note in it is tied (ABC 2.1, 4.11 and 4.17)
					boolean chordTied = false;
					// Broken rhythm on the current chord, before it (c>[ce]) or after it ([ce]>d), applied to every note in
					// the chord as in ABC 2.1; the note after the chord gets its part when the chord ends. Lotro plays
					// neither like that (tested), so with Lotro errors they're errors.
					long chordBrokenNumerator = 1;
					long chordBrokenDenominator = 1;
					boolean chordBrokenFirstNoteOnly = false; // c>[ce] as Lotro plays it: only c is shortened
					String chordBrokenStr = ""; // The > or < after the chord
					int nextBrokenNumerator = 1;
					int nextBrokenDenominator = 1;
					int chordCloseIndex = -1; // Index of the current chord's ']'; -1 if the chord is unclosed
					List<double[]> graceNotes = new ArrayList<>(); // {noteId, written length} of grace notes before the next note
					double attackOffset = 0; // The current note or chord starts after its grace notes
					String ornament = null; // The decoration before the next note, if it's one that is played (ORNAMENTS)
					// An accent or staccato before the next note or chord (only with standard2011)
					boolean accent = false;
					boolean staccato = false;
					Tuplet tuplet = null;
					// The numerator and denominator of the note after the broken rhythm sign; from the line before if
					// that ended with one (e>)
					int brokenRhythmNumerator = brokenCarried[0];
					int brokenRhythmDenominator = brokenCarried[1];
					brokenCarried = new int[] { 1, 1, -1, -1 };
					while (true) {
						boolean found = m.find(i);
						int parseEnd = found ? m.start() : line.length();
						// Parse anything that's not a note
						for (; i < parseEnd; i++) {
							char ch = line.charAt(i);
							if (Character.isWhitespace(ch)) {
								if (inChord) {
									throw new FileParseException(UIText.get("common.abctomidi.chord.whitespace"),
											fileName, lineNumber, i);
								}
								continue;
							}

							switch (ch) {
								case '[': // Chord start
									if (inChord) {
										throw new FileParseException(UIText.get("common.abctomidi.unexpected.in.chord",
												String.valueOf(ch)), fileName, lineNumber, i);
									}

									if (i + 1 < line.length() && Character.isDigit(line.charAt(i + 1))) {
										// [1 [2 ... : the start of a numbered ending. Tested in Lotro: it plays on, and plays
										// no repeats, so every ending plays once, one after the other
										int end = skipEndingNumber(line, i + 1);
										crossedRepeat = true;
										repeats.ending(checkEnding(line.substring(i + 1, end + 1), enableLotroErrors, fileName, lineNumber, i));
										i = end;
										break;
									}
									if (i + 2 < line.length() && Character.isLetter(line.charAt(i + 1)) && line.charAt(i + 2) == ':') {
										// [K:G] [L:1/16] [M:3/4] : an inline field (ABC 2.1, 3.1), the same as a field on a
										// line of its own. Tested in Lotro: it refuses the part.
										int close = line.indexOf(']', i + 3);
										if (close < 0) {
											throw new FileParseException(UIText.get("common.abctomidi.no.matching",
													"]"), fileName, lineNumber, i);
										}
										if (enableLotroErrors) {
											throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.inline.field",
													line.substring(i, close + 1)), fileName, lineNumber, i);
										}
										char field = Character.toUpperCase(line.charAt(i + 1));
										String value = line.substring(i + 3, close).trim();
										try {
											switch (field) {
												case 'K' -> info.setKey(value);
												case 'I' -> {
													info.applyInstruction(value);
													partDrone.midiDirective(value, Math.round(chordStartTick));
												}
												case 'L' -> info.setNoteDivisor(value);
												case 'M' -> {
													info.setMeter(value, false);
													meterChangeLine = lineNumber;
													meterChangeColumn = i + 3;
												}
												case 'Q' -> {
													int tempo = info.getPrimaryTempoBPM();
													info.setPrimaryTempoBPM(value);
													if (info.getPrimaryTempoBPM() != tempo) {
														throw new FileParseException(UIText.get("common.abctomidi.tempo.change.mid.part"),
																fileName, lineNumber, i + 3);
													}
												}
												default -> {
													// Other fields (P: V: r: ...) change nothing that is played
												}
											}
										} catch (IllegalArgumentException e) {
											String message = (e instanceof NumberFormatException)
													? UIText.get("common.abctomidi.field.invalid.number", String.valueOf(field), value)
													: e.getMessage();
											throw new FileParseException(message, fileName, lineNumber, i + 3);
										}
										i = close;
										break;
									}
									if (line.startsWith("[|]", i)) {
										// [|] : an invisible bar line (ABC 2.1, 4.8). Tested in Lotro: it refuses the part.
										if (enableLotroErrors) {
											throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.invisible.bar"),
													fileName, lineNumber, i);
										}
										if (playChords)
											partBarTicks.computeIfAbsent(trackNumber, k -> new TreeSet<>()).add(Math.round(chordStartTick));
										if (abc21)
											lastBarTick = barLine(barAttacks, lastBarTick, Math.round(chordStartTick), groupTicks(info),
													useLotroInstruments);
										lyricBar++;
										if (trackNumber == 1)
											abcInfo.addBar(Math.round(chordStartTick));
										accidentals.clear();
										i += 2;
										break;
									}
									if (i + 1 < line.length() && line.charAt(i + 1) == '|') {
										// [| : a thick-thin bar line; [|: also starts a repeat
										if (playChords)
											partBarTicks.computeIfAbsent(trackNumber, k -> new TreeSet<>()).add(Math.round(chordStartTick));
										if (abc21)
											lastBarTick = barLine(barAttacks, lastBarTick, Math.round(chordStartTick), groupTicks(info),
													useLotroInstruments);
										lyricBar++;
										if (trackNumber == 1)
											abcInfo.addBar(Math.round(chordStartTick));
										accidentals.clear();
										i++;
										if (!enableLotroErrors && i + 1 < line.length() && line.charAt(i + 1) == ':') {
											i++;
											crossedRepeat = true;
											repeats.start(lineIndex, i + 1);
										} else {
											repeats.sectionEnd(lineIndex, i + 1);
										}
										break;
									}

									chordBrokenNumerator = 1;
									chordBrokenDenominator = 1;
									chordBrokenFirstNoteOnly = false;
									chordBrokenStr = "";
									nextBrokenNumerator = 1;
									nextBrokenDenominator = 1;
									if (brokenRhythmDenominator != 1 || brokenRhythmNumerator != 1) {
										if (enableLotroErrors) {
											throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.broken.before.chord"),
													fileName, lineNumber, i);
										}
										// c>[ce] : the chord gets the second part of the broken rhythm. ABC 2.1 (4.4, 4.17): the
										// whole chord. Lotro (tested, B6 and B31): only the chord's first note, the others keep
										// their length, and the next note follows the shortened one (the chord's shortest).
										chordBrokenNumerator = brokenRhythmNumerator;
										chordBrokenDenominator = brokenRhythmDenominator;
										chordBrokenFirstNoteOnly = !abc21;
										brokenRhythmNumerator = 1;
										brokenRhythmDenominator = 1;
									}

									chordSize = 0;
									inChord = true;
									chordStartIndex = i;
									chordNoteIds.clear();
									chordRests = 0;

									// Look ahead past the matching ']' for a chord length suffix, because the notes inside
									// the chord are turned into MIDI events before we reach the ']'.
									chordLenNumerator = 1;
									chordLenDenominator = 1;
									chordLenStr = "";
									chordCloseIndex = line.indexOf(']', i + 1);
									if (chordCloseIndex >= 0) {
										Matcher chordLenMatcher = CHORD_LENGTH_PATTERN.matcher(line);
										chordLenMatcher.region(chordCloseIndex + 1, line.length());
										chordLenMatcher.lookingAt(); // Always succeeds; may be an empty match
										try {
											chordLenNumerator = parseLengthNumerator(chordLenMatcher.group(CHORD_LEN_NUMER));
											chordLenDenominator = parseLengthDenominator(chordLenMatcher.group(CHORD_LEN_DENOM));
										} catch (IllegalArgumentException e) {
											throw new FileParseException(UIText.get("common.abctomidi.chord.length.invalid",
													chordLenMatcher.group()), fileName, lineNumber,
													chordCloseIndex + 1);
										}
										chordLenStr = chordLenMatcher.group();
										if (enableLotroErrors && !chordLenStr.isEmpty()) {
											throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.chord.length",
													chordLenStr), fileName, lineNumber, chordCloseIndex + 1);
										}
										if (chordLenNumerator == 0 || chordLenDenominator == 0) {
											throw new FileParseException(UIText.get("common.abctomidi.chord.length.invalid",
													chordLenStr), fileName, lineNumber, chordCloseIndex + 1);
										}
										// [ce]- : a tie after the chord. Tested in Lotro (B65): it refuses the part.
										int tieAt = chordCloseIndex + 1 + chordLenStr.length();
										chordTied = tieAt < line.length() && line.charAt(tieAt) == '-';
										if (enableLotroErrors && chordTied) {
											throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.chord.tie"),
													fileName, lineNumber, tieAt);
										}
										// [ce]>d : broken rhythm after the chord. ABC 2.1 (4.4): like after a note. Lotro refuses
										// it (tested, B5), so without standard2011 it's an error.
										int brokenStart = tieAt + (chordTied ? 1 : 0);
										if (!abc21 && brokenStart < line.length()
												&& (line.charAt(brokenStart) == '>' || line.charAt(brokenStart) == '<')) {
											String message = UIText.get("common.abctomidi.lotro.broken.after.chord");
											if (enableLotroErrors)
												throw new LotroFileParseException(message, fileName, lineNumber, brokenStart);
											throw new FileParseException(message, fileName, lineNumber, brokenStart);
										}
										int brokenEnd = brokenStart;
										while (brokenEnd < line.length()
												&& (line.charAt(brokenEnd) == '>' || line.charAt(brokenEnd) == '<')
												&& line.charAt(brokenEnd) == line.charAt(brokenStart))
											brokenEnd++;
										if (brokenEnd > brokenStart) {
											chordBrokenStr = line.substring(brokenStart, brokenEnd);
											int factor = 1 << chordBrokenStr.length();
											if (chordBrokenStr.charAt(0) == '>') {
												chordBrokenNumerator *= 2 * factor - 1;
												chordBrokenDenominator *= factor;
												nextBrokenDenominator = factor;
											} else {
												chordBrokenDenominator *= factor;
												nextBrokenNumerator = 2 * factor - 1;
												nextBrokenDenominator = factor;
											}
										}
									}
									// If there's no ']' on this line, the "Chord not closed" check at the end of the line reports it

									partChordsNumber++;
									if (enableLotroErrors && partChordsNumber > 10_000) {
										throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.too.many.notes",
												info.getTitle()), fileName, lineNumber, i);
									}
									break;

								case ']': // Chord end
									if (!inChord) {
										throw new FileParseException(UIText.get("common.abctomidi.unexpected.char",
												String.valueOf(ch)), fileName, lineNumber, i);
									}
									if (i != chordCloseIndex) {
										// For now this branch should never run.
										throw new FileParseException(UIText.get("common.abctomidi.chord.mismatched.close"),
												fileName, lineNumber, i);
									}
									if (chordSize == 0) {
										throw new FileParseException(UIText.get("common.abctomidi.chord.empty"),
												fileName, lineNumber, chordStartIndex);
									}
									inChord = false;
									if (abc21 && !repeats.skipping) {
										checkTiesContinue(tiesToContinue, eventPitches, tiedNotes, tiedNoteStartTicks, tiedNoteEndTicks, tiedRegions,
												crossedRepeat, track, channel, info.getDynamics().getVol(useLotroInstruments), noteOffEvents, fileName);
										crossedRepeat = false;
									}
									// An accent or staccato before the chord was for all of its notes
									accent = false;
									staccato = false;

									if (tuplet != null && tuplet.r == 0) {
										// A tuplet that ended on this chord have now applied to all of its notes. Now the tuplet is done.
										tuplet = null;
									}

									int chordLenEnd = i + 1 + chordLenStr.length() + (chordTied ? 1 : 0) + chordBrokenStr.length();
									if (generateRegions && !repeats.skipping) { // A skipped ending's chord isn't played
										abcInfo.addRegion(new AbcRegion(lineNumberForRegions, chordStartIndex, chordLenEnd,
												Math.round(chordStartTick), Math.round(chordEndTick), null, trackIndex));
									}

									// Skip the chord length suffix and broken rhythm; the for-loop's i++ lands on chordLenEnd
									i = chordLenEnd - 1;
									chordLenNumerator = 1;
									chordLenDenominator = 1;
									chordLenStr = "";
									chordTied = false;
									// The note after [ce]> gets the rest of the broken rhythm
									brokenRhythmNumerator = nextBrokenNumerator;
									brokenRhythmDenominator = nextBrokenDenominator;
									chordBrokenNumerator = 1;
									chordBrokenDenominator = 1;
									chordBrokenStr = "";
									chordCloseIndex = -1;
									i = chordLenEnd - 1;

									chordStartTick = chordEndTick;
									attackOffset = 0;
									log.finer("chordStartTick ]="+chordStartTick);
									break;

								case '|': // Bar line
									if (inChord) {
										throw new FileParseException(UIText.get("common.abctomidi.unexpected.in.chord",
												String.valueOf(ch)), fileName, lineNumber, i);
									}
									if (playChords)
										partBarTicks.computeIfAbsent(trackNumber, k -> new TreeSet<>()).add(Math.round(chordStartTick));
									if (abc21)
										lastBarTick = barLine(barAttacks, lastBarTick, Math.round(chordStartTick), groupTicks(info),
												useLotroInstruments);
									lyricBar++;

									if (trackNumber == 1)
										abcInfo.addBar(Math.round(chordStartTick));

									accidentals.clear();
									char afterBar = (i + 1 < line.length()) ? line.charAt(i + 1) : ' ';
									if (afterBar == '|' && enableLotroErrors && i + 2 < line.length()
											&& Character.isDigit(line.charAt(i + 2))) {
										// ||1 : Lotro plays nothing of the part (tested, B77b)
										throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.ending.after.double.bar",
												line.substring(i, skipEndingNumber(line, i + 2) + 1)), fileName, lineNumber, i);
									}
									if (afterBar == '|' && !endingAt(line, i + 2)) {
										// || : a double bar line; not the bar line before an ending (||1, || [1)
										repeats.sectionEnd(lineIndex, i + 2);
									}
									if (afterBar == ']' || afterBar == ':') {
										i++; // Skip |], |:
										// |:: is read as |:, as ::| is read as :| (BUG1018; ABC 2.1 defines no third pass)
										while (afterBar == ':' && !enableLotroErrors && i + 1 < line.length()
												&& line.charAt(i + 1) == ':')
											i++;
										if (afterBar == ']')
											repeats.sectionEnd(lineIndex, i + 1);
										else {
											crossedRepeat = true;
											repeats.start(lineIndex, i + 1);
										}
									} else if (trackNumber == 1) {
										abcInfo.addBar(Math.round(chordStartTick));
									}
									int endingEnd = skipEndingNumber(line, i + 1); // |1 |2 : a numbered ending
									if (endingEnd > i) {
										crossedRepeat = true;
										repeats.ending(checkEnding(line.substring(i + 1, endingEnd + 1), enableLotroErrors, fileName,
												lineNumber, i + 1));
									}
									i = endingEnd;
									break;

								case ':': // Beginning of repeat end bar line :| ::| :::::::|
									if (inChord) {
										throw new FileParseException(UIText.get("common.abctomidi.unexpected.in.chord",
												String.valueOf(ch)), fileName, lineNumber, i);
									}
									if (playChords)
										partBarTicks.computeIfAbsent(trackNumber, k -> new TreeSet<>()).add(Math.round(chordStartTick));
									if (abc21)
										lastBarTick = barLine(barAttacks, lastBarTick, Math.round(chordStartTick), groupTicks(info),
												useLotroInstruments);

									int pipe = -1;
									for (int j = i + 1; j < parseEnd; j++) {
										if (line.charAt(j) == '|') {
											pipe = j; // Skip past :::::| (legal in lotro, so we should support it.. even though lotro doesn't support |::)
											break;
										}
									}
									int colons = 1;
									while (i + colons < line.length() && line.charAt(i + colons) == ':')
										colons++;

									// After the whole sign: :| ::| :: and :||: (Lotro plays them, tested), and :|: :|] (Lotro
									// refuses them, tested: with Lotro errors they're errors)
									int signEnd;
									if (pipe >= 0) {
										signEnd = pipe + 1;
										if (enableLotroErrors) {
											// Only :| ::| ... : Lotro plays nothing of a part with :|: or :|] (tested, B2, B4)
											if (signEnd < line.length() && line.charAt(signEnd) == ':') {
												throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.repeat.bar.colon"),
														fileName, lineNumber, i);
											}
											if (signEnd < line.length() && line.charAt(signEnd) == ']') {
												throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.repeat.bar.bracket"),
														fileName, lineNumber, i);
											}
										} else if (signEnd + 1 < line.length() && line.charAt(signEnd) == '|'
												&& line.charAt(signEnd + 1) == ':') {
											signEnd += 2; // :||: the end of one repeat and the start of the next
										} else if (signEnd < line.length()
												&& (line.charAt(signEnd) == ':' || line.charAt(signEnd) == ']')) {
											signEnd++; // :|: the same, or :|] the end of a section
										}
									} else if (colons >= 2) {
										signEnd = i + colons; // :: the end of one repeat and the start of the next
									} else {
										throw new FileParseException(UIText.get("common.abctomidi.bar.expected.after",
												String.valueOf(ch)), fileName, lineNumber, i);
									}
									lyricBar++;
									if (trackNumber == 1)
										abcInfo.addBar(Math.round(chordStartTick));
									// A repeat sign is a bar line: the bar's accidentals end here, also for the pass that
									// goes back. ABC 2.1, and Lotro (tested, B79), so in every reading
									accidentals.clear();

									crossedRepeat = true;
									if (repeats.end(lines, lineIndex, i, signEnd)) {
										// Play the repeated section again: go back to its start, read as it was read there
										if (repeats.startState != null)
											info.restore(repeats.startState);
										lineIndex = repeats.jumpLine - 1;
										startColumn = repeats.jumpColumn;
										continue lineLoop;
									}
									// :: :|: :||: also start the next repeat. Repeats.end only does that when no ending was
									// open; after |2 ... :: the next :| went back to the first |: and played nothing again.
									if (line.charAt(signEnd - 1) == ':')
										repeats.start(lineIndex, signEnd);
									i = signEnd - 1;
									int nextEndingEnd = skipEndingNumber(line, i + 1); // :|2 : a numbered ending
									if (nextEndingEnd > i) {
										crossedRepeat = true;
										repeats.ending(checkEnding(line.substring(i + 1, nextEndingEnd + 1), enableLotroErrors,
												fileName, lineNumber, i + 1));
									}
									i = nextEndingEnd;
									break;

								case '+': {
									int j = line.indexOf('+', i + 1);
									if (j < 0) {
										throw new FileParseException(UIText.get("common.abctomidi.no.matching", "+"),
												fileName, lineNumber, i);
									}
									String decoration = line.substring(i + 1, j);
									try {
										// Also in capitals: tested in Lotro (B74), +FF+ and +PP+ set the volume
										info.setDynamics(decoration.toLowerCase(Locale.ROOT));
									} catch (IllegalArgumentException iae) {
										// +trill+ +fermata+ ... : the ABC 2.0 form of !trill! (ABC 2.1, 4.14). Tested in Lotro: it
										// plays nothing of the part. Only notes (+ceg+, a chord in ABC 1.6) stay an error.
										if (enableLotroErrors) {
											throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.plus.decoration",
													decoration), fileName, lineNumber, i);
										}
										if (decoration.isEmpty() || decoration.matches("[_^=A-Ga-g,'0-9/]*"))
											throw new FileParseException(UIText.get("common.abctomidi.plus.decoration.unsupported"),
													fileName, lineNumber, i);
										if (!repeats.skipping && ORNAMENTS.containsKey(decoration))
											ornament = ORNAMENTS.get(decoration);
										else if (abc21 && ACCENT_NAMES.contains(decoration))
											accent = true; // +accent+, the ABC 2.0 form of !accent!
									}

									if (enableLotroErrors && inChord) {
										throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.plus.decoration.in.chord"),
												fileName, lineNumber, i);
									}

									i = j;
									break;
								}

								case '"': {
									// "Am" chord symbol or "^text" annotation. Lotro plays on (tested); it plays no chords.
									int j = line.indexOf('"', i + 1);
									if (j < 0) {
										throw new FileParseException(UIText.get("common.abctomidi.no.matching", "\""),
												fileName, lineNumber, i);
									}
									if (playChords && !inChord && !repeats.skipping && !drumPart) {
										// On the beat of the note that follows; text that isn't a chord name is skipped
										ChordSymbol chord = ChordSymbol.parse(line.substring(i + 1, j), Math.round(chordStartTick),
												beatTicks(info), beatsPerBar(info), groupTicks(info), info.getTranspose());
										if (chord != null)
											chordSymbols.computeIfAbsent(trackNumber, k -> new ArrayList<>()).add(chord);
									}
									i = j;
									break;
								}

								case '!': {
									// !trill! !f! ... decorations. Tested in Lotro: it plays nothing of the part from the first
									// one on (not even after a +mf+ or on the next line). Without Lotro errors they're skipped.
									// With standard2011 a ! can also be a score line break (layout only, skipped): after
									// I:linebreak ! (ABC 2.1, 6.1.1), or in a file older than ABC 2.1 when no ! follows before
									// | [ : a space or the line's end (the loose reading, ABC 2.1, 12).
									if (abc21 && (info.getInstructions().isLineBreakAtBang()
											|| (looseBang && !isBangDecoration(line, i)))) {
										break;
									}
									int j = line.indexOf('!', i + 1);
									if (j < 0) {
										throw new FileParseException(UIText.get("common.abctomidi.no.matching", "!"),
												fileName, lineNumber, i);
									}
									if (enableLotroErrors) {
										throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.bang.decoration",
												line.substring(i, j + 1)), fileName, lineNumber, i);
									}
									String decorationName = line.substring(i + 1, j);
									if (abc21 && DYNAMICS_NAMES.contains(decorationName)) {
										// ABC 2.1 (4.14): players "may be expected to implement the dynamics marks": !p! as
										// +p+. Lotro skips them, so only with standard2011.
										info.setDynamics(decorationName);
									} else if (abc21 && ACCENT_NAMES.contains(decorationName)) {
										accent = true; // ABC 2.1 (4.14): "the accent mark", as L
									} else if (!repeats.skipping && ORNAMENTS.containsKey(decorationName)) {
										ornament = ORNAMENTS.get(decorationName);
									}
									i = j;
									break;
								}

								case '{': {
									// {g} {/g} {a>b} grace notes (ABC 2.1, 4.12). Lotro plays on without them (tested), so with
									// Lotro errors they're not played. Else they're played before the next note, on the beat
									// (ABC leaves their length to the program: GRACE_NOTE_SECONDS). Checked in every mode, so a
									// mistake in the braces is an error even where they aren't played.
									if (inChord) {
										throw new FileParseException(UIText.get("common.abctomidi.unexpected.in.chord",
												String.valueOf(ch)), fileName, lineNumber, i);
									}
									int j = line.indexOf('}', i + 1);
									if (j < 0) {
										throw new FileParseException(UIText.get("common.abctomidi.no.matching", "}"),
												fileName, lineNumber, i);
									}
									int k = i + 1;
									if (k < j && line.charAt(k) == '/')
										k++; // {/g} acciaccatura: played the same
									List<double[]> group = new ArrayList<>(); // {noteId, written length}
									// A grace note's accidental isn't kept for the notes after it
									Map<Integer, Integer> graceAccidentals = new HashMap<>(accidentals);
									double nextFactor = 1; // A broken rhythm's factor for the next grace note
									Matcher grace = NOTE_PATTERN.matcher(line);
									while (k < j) {
										char c = line.charAt(k);
										if (c == ' ' || c == '\t') {
											k++;
											continue;
										}
										if (c == '(' || c == ')') {
											// A slur over the grace notes, {(B/c/B/^A/)} (Village Music Project): layout only.
											// ABC 2.1 (4.12) doesn't say; Lotro plays the part (tested in game, B76). Also a
											// tuplet, {(3Bcd}: grace notes are timed by the player anyway; Lotro plays it (B77d).
											k++;
											while (c == '(' && k < j && (Character.isDigit(line.charAt(k)) || line.charAt(k) == ':'))
												k++;
											continue;
										}
										grace.region(k, j);
										if (!grace.lookingAt()) {
											throw new FileParseException(UIText.get("common.abctomidi.grace.unexpected",
													String.valueOf(c)), fileName, lineNumber, k);
										}
										char letter = grace.group(NOTE_LETTER).charAt(0);
										if (letter == 'z' || letter == 'x') {
											throw new FileParseException(UIText.get("common.abctomidi.grace.rest",
													String.valueOf(letter)), fileName, lineNumber, k);
										}
										double weight;
										try {
											weight = nextFactor * parseLengthNumerator(grace.group(NOTE_LEN_NUMER))
													/ parseLengthDenominator(grace.group(NOTE_LEN_DENOM));
										} catch (IllegalArgumentException e) {
											throw new FileParseException(UIText.get("common.abctomidi.grace.length.invalid"),
													fileName, lineNumber, k);
										}
										nextFactor = 1;
										String broken = grace.group(NOTE_BROKEN_RHYTHM);
										if (broken != null) {
											// a>b : a 3/2 and b 1/2 of their lengths, as for notes
											double factor = 1 << broken.length();
											double longer = (2 * factor - 1) / factor;
											weight *= (broken.charAt(0) == '>') ? longer : 1 / factor;
											nextFactor = (broken.charAt(0) == '>') ? 1 / factor : longer;
										}
										// A tie (a grace note tied to the note) changes nothing here
										group.add(new double[] { notePitch(grace, info, graceAccidentals, useLotroInstruments)[0],
												weight });
										k = grace.end();
									}
									if (group.isEmpty()) {
										throw new FileParseException(UIText.get("common.abctomidi.grace.empty"),
												fileName, lineNumber, i);
									}
									if (nextFactor != 1) {
										throw new FileParseException(UIText.get("common.abctomidi.grace.broken.needs.note"),
												fileName, lineNumber, j);
									}
									// c{g}<d : without Lotro errors withSlipsFixed moved the < before the {. Lotro plays
									// nothing of the part (tested, B78b).
									if (enableLotroErrors && j + 1 < line.length()
											&& (line.charAt(j + 1) == '<' || line.charAt(j + 1) == '>')) {
										throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.grace.broken"),
												fileName, lineNumber, j + 1);
									}
									if (!enableLotroErrors && !repeats.skipping)
										graceNotes.addAll(group);
									i = j;
									break;
								}

								case '~': // Roll
									// Lotro plays on (tested) and plays the note plain, so with Lotro errors it changes nothing
									if (!enableLotroErrors && !repeats.skipping)
										ornament = "roll";
									break;
								case '.': { // Staccato
									// A decoration. Lotro plays on (tested) and plays the note as written, so only with
									// standard2011 it sounds shorter. Not the dotted bar line .| (ABC 2.1, 4.8), nor a dotted
									// slur .( and its optional .) (4.11). A dotted tie .- is made a tie by withSlipsFixed.
									char afterDot = (i + 1 < line.length()) ? line.charAt(i + 1) : ' ';
									if (abc21 && afterDot != '|' && afterDot != '(' && afterDot != ')')
										staccato = true;
									break;
								}

								case '$': // Score line break (ABC 2.1, 4.1)
								case '`': // Back quote in a beam, e.g. A`B`c (ABC 2.1, 4.7)
									// Layout only, they change nothing that's played. Tested in Lotro: it plays the part up to
									// the sign, and nothing after it.
									if (enableLotroErrors) {
										throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.layout.char",
												String.valueOf(ch)), fileName, lineNumber, i);
									}
									break;

								case 'X': // X X4 : the same, not printed (ABC 2.1, 4.5). Tested in Lotro (B80): it refuses it too
								case 'Z': {
									// Z Z4 : a rest of 1 or 4 whole bars (ABC 2.1, 4.5). Tested in Lotro: it refuses the part.
									if (enableLotroErrors) {
										throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.multi.measure.rest",
												String.valueOf(ch)), fileName, lineNumber, i);
									}
									if (inChord) {
										throw new FileParseException(UIText.get("common.abctomidi.unexpected.in.chord",
												String.valueOf(ch)), fileName, lineNumber, i);
									}
									int j = i + 1;
									while (j < line.length() && Character.isDigit(line.charAt(j)))
										j++;
									int bars = (j > i + 1) ? Integer.parseInt(line.substring(i + 1, j)) : 1;
									if (!repeats.skipping && bars > 0) {
										// Whole-note ticks times the bar's length, at the current tempo like a note
										double barTicks = (double) info.getTickFactor() * DEFAULT_NOTE_TICKS * info.getBarNumerator()
												* info.getCurrentTempoBPM(Math.round(chordStartTick))
												/ ((double) info.getBarDenominator() * info.getPrimaryTempoBPM());
										for (int bar = 1; bar < bars; bar++) {
											// The bar lines inside the rest; the one after it is written
											lyricBar++;
											if (trackNumber == 1)
												abcInfo.addBar(Math.round(chordStartTick + bar * barTicks));
										}
										if (generateRegions) {
											abcInfo.addRegion(new AbcRegion(lineNumberForRegions, i, j, Math.round(chordStartTick),
													Math.round(chordStartTick + bars * barTicks), Note.REST, trackIndex));
										}
										if (abc21) {
											checkTiesContinue(tiesToContinue, eventPitches, tiedNotes, tiedNoteStartTicks, tiedNoteEndTicks, tiedRegions,
													crossedRepeat, track, channel, info.getDynamics().getVol(useLotroInstruments), noteOffEvents, fileName);
											crossedRepeat = false;
										}
										chordStartTick += bars * barTicks;
										chordEndTick = chordStartTick;
									}
									i = j - 1;
									break;
								}

								case 'H': // Fermata
								case 'L': // Accent
								case 'M': // Lower mordent
								case 'O': // Coda
								case 'P': // Upper mordent
								case 'S': // Segno
								case 'T': // Trill
								case 'u': // Up-bow
								case 'v': // Down-bow
								case 'J': // Slide (abc 1.6 and BarFly, not ABC 2.1)
								case 'R': // Roll (abc 1.6 and BarFly, not ABC 2.1): played as ~
									// Decorations in short form (ABC 2.1, 4.14). T M P R are ornaments that are played, the others
									// change nothing here. Tested in Lotro (T H u v; J R in B77f): it refuses the part.
									if (enableLotroErrors) {
										throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.decoration.letter",
												String.valueOf(ch)), fileName, lineNumber, i);
									}
									if (!repeats.skipping && (ch == 'T' || ch == 'M' || ch == 'P' || ch == 'R'))
										ornament = switch (ch) {
											case 'T' -> "trill";
											case 'M' -> "lowermordent";
											case 'P' -> "uppermordent";
											default -> "roll";
										};
									if (abc21 && ch == 'L')
										accent = true;
									break;
								case 'y':
									// Spacer. Tested in Lotro (B58): it refuses the part.
									if (enableLotroErrors) {
										throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.spacer"),
												fileName, lineNumber, i);
									}
									break;

								case '(':
									// Tuplet or slur start
									if (i + 1 < line.length() && Character.isDigit(line.charAt(i + 1))) {
										// If it has a digit following it, it's a tuplet
										if (tuplet != null) {
											throw new FileParseException(UIText.get("common.abctomidi.tuplet.unexpected",
													String.valueOf(ch)), fileName, lineNumber, i);
										}

										// The tuplet spec (p:q:r) runs to the first character that isn't a digit or ':',
										// which may be the end of the line ("Tuplet not finished" is reported there)
										int j = i + 1;
										while (j < line.length() && (line.charAt(j) == ':' || Character.isDigit(line.charAt(j))))
											j++;
										// ABC 2.1 (4.13): compound is 6/8 9/8 12/8; the older reading also counts 3/4 and 3/8
										boolean compound = info.isCompoundMeter() && (!abc21 || info.getBarNumerator() > 3);
										try {
											tuplet = new Tuplet(line.substring(i + 1, j), compound);
										} catch (IllegalArgumentException e) {
											throw new FileParseException(UIText.get("common.abctomidi.tuplet.invalid"),
													fileName, lineNumber, i);
										}
										i = j - 1;
									} else {
										// Otherwise it's a slur, which Lotro conveniently ignores
										if (inChord) {
											throw new FileParseException(UIText.get("common.abctomidi.unexpected.in.chord",
													String.valueOf(ch)), fileName, lineNumber, i);
										}
									}
									break;

								case ')':
									// End of a slur, ignore
									if (inChord) {
										throw new FileParseException(UIText.get("common.abctomidi.unexpected.in.chord",
												String.valueOf(ch)), fileName, lineNumber, i);
									}
									// (c d)>e : Lotro plays nothing of the part (tested, B77c). Without Lotro errors
									// withSlipsFixed moved the > before the ).
									if (enableLotroErrors && i + 1 < line.length()
											&& (line.charAt(i + 1) == '>' || line.charAt(i + 1) == '<')) {
										throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.broken.after.slur"),
												fileName, lineNumber, i + 1);
									}
									break;

								case '-':
									// A tie apart from its note, c4 -c4 (without Lotro errors withSlipsFixed moved it to the
									// note): Lotro plays nothing of the part (tested, B30)
									if (enableLotroErrors && detachedTieNoteEnd(line, i) >= 0) {
										throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.tie.apart"),
												fileName, lineNumber, i);
									}
									throw new FileParseException(UIText.get("common.abctomidi.unknown.char",
											String.valueOf(ch)), fileName, lineNumber, i);

								case '“':
								case '”':
									// Typographic quotes, left only with Lotro errors (withSlipsFixed): Lotro plays nothing of
									// the part (tested, B77e)
									throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.typographic.quotes"),
											fileName, lineNumber, i);

								case '\\':
									// Line continuation; Lotro treats every line on its own anyway, so it's ignored. Also doubled,
									// \\ at the line's end (John Chambers' collections): Lotro plays it (tested, B77g).
									String afterBackslash = line.substring(i + 1);
									if (!afterBackslash.isBlank()
											&& !(afterBackslash.startsWith("\\") && afterBackslash.substring(1).isBlank())) {
										throw new FileParseException(UIText.get("common.abctomidi.backslash.not.at.end"),
												fileName, lineNumber, i);
									}
									break;

								case '&':
									// Voice overlay (ABC 2.1, 7.4): a second voice in the same bar. Not played yet (rare: 17
									// of The Session's 55,000 settings)
									throw new FileParseException(UIText.get("common.abctomidi.overlay.not.supported"),
											fileName, lineNumber, i);

								default:
									throw new FileParseException(UIText.get("common.abctomidi.unknown.char",
											String.valueOf(ch)), fileName, lineNumber, i);
							}
						}

						if (i >= line.length())
							break;

						// The matcher might find +f+, +ff+, or +fff+ and think it's a note
						if (i > m.start())
							continue;

						if (inChord)
							chordSize++;

						// Parse the note

						// fraction with broken rhythm, tuplet and tempo changes applied. long, because the products get
						// large with fine L: (e.g. L:1/2834674 with broken rhythm or a fast tempo), and every
						// multiplication is overflow-checked
						long numerator;
						long denominator;

						// actual fraction as written
						long numerator_abc;
						long denominator_abc;

						try {
							numerator = parseLengthNumerator(m.group(NOTE_LEN_NUMER));
							denominator = parseLengthDenominator(m.group(NOTE_LEN_DENOM));
						} catch (IllegalArgumentException e) {
							throw new FileParseException(UIText.get("common.abctomidi.note.length.invalid",
									Objects.requireNonNullElse(m.group(NOTE_LEN_NUMER),
									"") + Objects.requireNonNullElse(m.group(NOTE_LEN_DENOM), "")), fileName,
									lineNumber, m.start());
						}

						String abcNoteL = "";
						if (m.group(NOTE_LEN_NUMER) != null) {
							abcNoteL = m.group(NOTE_LEN_NUMER);
						}
						if (m.group(NOTE_LEN_DENOM) != null) {
							abcNoteL += m.group(NOTE_LEN_DENOM);
						}

						// Apply the chord's length suffix, e.g. [ceg]3/4 or [c2eg]3/4 (the latter gives c 6/4, e and g 3/4)
						if (inChord && !chordLenStr.isEmpty()) {
							numerator = multiplyLength(numerator, chordLenNumerator, fileName, lineNumber, m.start());
							denominator = multiplyLength(denominator, chordLenDenominator, fileName, lineNumber, m.start());
							abcNoteL += "*" + chordLenStr; // Shows the effective length in error messages, e.g. "c2*3/4"
						}

						String abcNoteAcc = "";
						if (m.group(NOTE_ACCIDENTAL) != null) {
							abcNoteAcc = m.group(NOTE_ACCIDENTAL);
						}

						numerator_abc = numerator;
						denominator_abc = denominator;

						String brokenRhythm = m.group(NOTE_BROKEN_RHYTHM);
						if (brokenRhythm != null) {
							if (brokenRhythmDenominator != 1 || brokenRhythmNumerator != 1) {
								throw new FileParseException(UIText.get("common.abctomidi.broken.invalid",
										brokenRhythm), fileName, lineNumber, m.start(NOTE_BROKEN_RHYTHM));
							}
							if (inChord) {
								throw new FileParseException(UIText.get("common.abctomidi.broken.in.chord"), fileName,
										lineNumber, m.start(NOTE_BROKEN_RHYTHM));
							}
							if (m.group(NOTE_TIE) != null) {
								throw new FileParseException(UIText.get("common.abctomidi.broken.tied"), fileName,
										lineNumber, m.start(NOTE_BROKEN_RHYTHM));
							}

							int factor = 1 << brokenRhythm.length();

							if (brokenRhythm.charAt(0) == '>') {
								numerator = multiplyLength(numerator, 2 * factor - 1, fileName, lineNumber, m.start());
								denominator = multiplyLength(denominator, factor, fileName, lineNumber, m.start());
								brokenRhythmDenominator = factor;
							} else {
								brokenRhythmNumerator = 2 * factor - 1;
								brokenRhythmDenominator = factor;
								denominator = multiplyLength(denominator, factor, fileName, lineNumber, m.start());
							}
						} else if (inChord) {
							// c>[ce] as Lotro plays it (chordBrokenFirstNoteOnly): the chord's other notes keep their length
							if (chordSize == 1 || !chordBrokenFirstNoteOnly) {
								numerator = multiplyLength(numerator, chordBrokenNumerator, fileName, lineNumber, m.start());
								denominator = multiplyLength(denominator, chordBrokenDenominator, fileName, lineNumber, m.start());
							}
						} else {
							numerator = multiplyLength(numerator, brokenRhythmNumerator, fileName, lineNumber, m.start());
							denominator = multiplyLength(denominator, brokenRhythmDenominator, fileName, lineNumber, m.start());
							brokenRhythmNumerator = 1;
							brokenRhythmDenominator = 1;
						}

						if (tuplet != null) {
							if (!inChord || chordSize == 1)
								tuplet.r--;
							numerator = multiplyLength(numerator, tuplet.q, fileName, lineNumber, m.start());
							denominator = multiplyLength(denominator, tuplet.p, fileName, lineNumber, m.start());
							if (tuplet.r == 0 && !inChord) {
								tuplet = null;
							}
						}

						if (repeats.skipping) {
							ornament = null;
							graceNotes.clear();
							if (!inChord) {
								accent = false;
								staccato = false;
							}
							// A note of an ending that this pass doesn't play: it takes no time. It keeps its place in the w:
							// lyrics, which are written for the notes as they stand.
							char letter = m.group(NOTE_LETTER).charAt(0);
							if (letter != 'z' && letter != 'x' && (!inChord || chordSize == 1))
								lyricNote(lyricNotes, lineIndex, m.start(), lyricBar);
							i = m.end();
							continue;
						}

						// Count the notes with triplet timing, for the guess at the end. Before the tempo scaling
						// below, which would hide the 3 when a tempo is a multiple of 3 (e.g. Q:120).
						guessNotes++;
						if ((denominator % 3 == 0) && (numerator % 3 != 0)) {
							guessTripletNotes++;
						}

						// Convert back to the original tempo
						int curTempoBPM = info.getCurrentTempoBPM(Math.round(chordStartTick));
						int primaryTempoBPM = info.getPrimaryTempoBPM();
						numerator = multiplyLength(numerator, curTempoBPM, fileName, lineNumber, m.start());
						denominator = multiplyLength(denominator, primaryTempoBPM, fileName, lineNumber, m.start());

						double noteEndTick = chordStartTick
								+ (double) info.getTickFactor() * DEFAULT_NOTE_TICKS * numerator * info.getLNum() / ((double) denominator * info.getLDenom());
						log.finer("noteEndTick="+noteEndTick);
						// A chord is as long as its shortest note, as Lotro plays it (tested, B31). ABC 2.1 (4.17, standard2011):
						// "the chord duration is that of the first note". Either way each note sounds for its own length.
						double chordEndTickBeforeThisNote = chordEndTick; // Restored if this note turns out to be ignored
						if (abc21 && inChord) {
							if (chordSize == 1)
								chordEndTick = noteEndTick;
						} else if (chordEndTick == chordStartTick || noteEndTick < chordEndTick) {
							chordEndTick = noteEndTick;
							log.finer("chordEndTick="+noteEndTick);
						} else {
							log.finer("skipping chordEndTick "+chordEndTick+" != "+chordStartTick);
						}

						char noteLetter = m.group(NOTE_LETTER).charAt(0);
						String octaveStr = m.group(NOTE_OCTAVE);
						if (octaveStr == null)
							octaveStr = "";
						if (noteLetter == 'z' || noteLetter == 'x') {
							if (m.group(NOTE_ACCIDENTAL) != null && !m.group(NOTE_ACCIDENTAL).isEmpty()) {
								throw new FileParseException(UIText.get("common.abctomidi.rest.accidental"), fileName,
										lineNumber, m.start(NOTE_ACCIDENTAL));
							}
							if (!octaveStr.isEmpty()) {
								throw new FileParseException(UIText.get("common.abctomidi.rest.octave"), fileName,
										lineNumber, m.start(NOTE_OCTAVE));
							}

							float lengthSeconds = info.getWholeNoteTime() * (numerator_abc / (float) denominator_abc);

							throwExceptionsIfEnabled(enableLotroErrors, fileName, lineNumber, m, abcNoteL, noteLetter,
									lengthSeconds, lotroSeconds(info, numerator_abc, denominator_abc), info.getPrimaryTempoBPM());
							if (inChord) {
								chordRests++;
								if (enableLotroErrors
										&& chordNoteIds.size() + Math.min(chordRests, 1) > AbcConstants.MAX_CHORD_NOTES) {
									throw new LotroFileParseException(
											UIText.get("common.abctomidi.lotro.chord.too.many.notes"), fileName, lineNumber,
											m.start());
								}
							}
							if (!inChord) partChordsNumber++;
							if (enableLotroErrors && partChordsNumber > 10_000) {
								throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.too.many.notes",
										info.getTitle()), fileName, lineNumber, i);
							}
							if (generateRegions) {
								abcInfo.addRegion(new AbcRegion(lineNumberForRegions, m.start(), m.end(),
										Math.round(chordStartTick), Math.round(noteEndTick), Note.REST, trackIndex));
							}
							graceNotes.clear(); // Grace notes before a rest aren't played
							ornament = null;
						} else {
							int[] pitch = notePitch(m, info, accidentals, useLotroInstruments);
							int noteId = pitch[0];
							eventPitches.add(noteId);
							int lotroNoteId = pitch[1];
							// Tied to the next note of its pitch: by its own - or by a tie after its chord
							boolean tied = m.group(NOTE_TIE) != null || (inChord && chordTied);

							if (enableLotroErrors && lotroNoteId < Note.MIN_PLAYABLE.id)
								throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.note.too.low"),
										fileName, lineNumber, m.start());
							else if (enableLotroErrors && lotroNoteId > Note.MAX_PLAYABLE.id)
								throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.note.too.high"),
										fileName, lineNumber, m.start());

							// Lotro plays only the first of the same note in a chord and ignores the later one completely,
							// also for the chord's length (tested in game, also for enharmonic spellings like [^c_d]).
							// Checked before the cowbell code, which gives all cowbell notes the same pitch.
							if (inChord && !chordNoteIds.add(lotroNoteId)) {
								chordEndTick = chordEndTickBeforeThisNote;
								// ABC 2.1 (4.17, standard2011): a unison, both notes sound. One MIDI channel can't sound a
								// pitch twice, so the longer one plays: the first note's note-off moves to this one's end.
								if (abc21 && !tied) {
									for (MidiEvent noteOff : noteOffEvents) {
										if (((ShortMessage) noteOff.getMessage()).getData1() == noteId
												&& noteOff.getTick() > chordStartTick && noteOff.getTick() < Math.round(noteEndTick)) {
											track.remove(noteOff);
											noteOff.setTick(Math.round(noteEndTick));
											track.add(noteOff);
											break;
										}
									}
								}
								i = m.end();// required, otherwise the loop will find the same note again and never end
								continue;
							}

							// Lotro's limit of 6 notes in a chord counts different notes, and rests as one: a doubled note,
							// which it ignores, doesn't count, nor a second rest (tested in game, B72, B73)
							if (enableLotroErrors && inChord
									&& chordNoteIds.size() + Math.min(chordRests, 1) > AbcConstants.MAX_CHORD_NOTES) {
								throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.chord.too.many.notes"),
										fileName, lineNumber, m.start());
							}

							if (info.getInstrument() == LotroInstrument.BASIC_COWBELL
									|| info.getInstrument() == LotroInstrument.MOOR_COWBELL) {
								if (useLotroInstruments) {
									// Randomize the noteId unless it's part of a note tie
									if (!tied && !tiedNotes.containsKey(noteId)) {
										int min = info.getInstrument().lowestPlayable.id;
										int max = info.getInstrument().highestPlayable.id;
										lotroNoteId = noteId = min + (int) (Math.random() * (max - min));
									}
								} else if (!info.isStandardPitch()) {
									// Lotro files and old projects: one pitch, as before (A37)
									noteId = (info.getInstrument() == LotroInstrument.BASIC_COWBELL) ? 76 : 71;
									lotroNoteId = AbcConstants.COWBELL_NOTE_ID;
								}
								// Else (standard ABC, ABC 2.1) every note sounds as written, like any other instrument
							}

							// Grace notes before this note or chord: on the beat, taking their time from the note, which starts
							// after them. The shortest one lasts GRACE_NOTE_SECONDS, the others by their written lengths; all
							// of them together at most half the note. Not before a tied note's continuation. Seconds are
							// turned into ticks at the tempo at the note: under a %%Q: change the ticks play at that tempo
							// (BUG1016).
							if (!graceNotes.isEmpty() && (!inChord || chordSize == 1) && !tiedNotes.containsKey(noteId)) {
								double totalWeight = 0;
								double shortestWeight = Double.MAX_VALUE;
								for (double[] grace : graceNotes) {
									totalWeight += grace[1];
									shortestWeight = Math.min(shortestWeight, grace[1]);
								}
								double ticksPerSecond = info.getCurrentTempoBPM(Math.round(chordStartTick)) * PPQN / 60.0;
								double graceTicks = Math.min(GRACE_NOTE_SECONDS * ticksPerSecond * totalWeight / shortestWeight,
										(noteEndTick - chordStartTick) / 2);
								double graceTick = chordStartTick;
								int volume = info.getDynamics().getVol(useLotroInstruments);
								for (double[] grace : graceNotes) {
									double graceEnd = graceTick + graceTicks * grace[1] / totalWeight;
									track.add(MidiFactory.createNoteOnEventEx((int) grace[0], channel, volume, Math.round(graceTick)));
									track.add(MidiFactory.createNoteOffEventEx((int) grace[0], channel, volume, Math.round(graceEnd)));
									graceTick = graceEnd;
								}
								attackOffset = graceTicks;
							}
							graceNotes.clear();

							// Where the note's sound starts, for its region and syllable: after its grace notes, before its
							// ornament
							double soundOffset = attackOffset;


							// An ornament: quick notes in steps of GRACE_NOTE_SECONDS, taking their time from the note, which
							// sounds after them (see ornamentNotes). Not on a chord, a tied note's continuation or a drum. In
							// ticks at the tempo at the note, as grace notes.
							if (ornament != null && !inChord && !tiedNotes.containsKey(noteId)
									&& !info.getInstrument().isPercussion && !drumPart) {
								double ticksPerSecond = info.getCurrentTempoBPM(Math.round(chordStartTick)) * PPQN / 60.0;
								Map<Integer, Integer> neighbourAccidentals = new HashMap<>(accidentals);
								int upper = neighbourPitch(m, 1, info, neighbourAccidentals, useLotroInstruments);
								int lower = neighbourPitch(m, -1, info, neighbourAccidentals, useLotroInstruments);
								double tick = chordStartTick + attackOffset;
								int volume = info.getDynamics().getVol(useLotroInstruments);
								for (double[] note : ornamentNotes(ornament, noteId, upper, lower, noteEndTick - tick,
										GRACE_NOTE_SECONDS * ticksPerSecond)) {
									track.add(MidiFactory.createNoteOnEventEx((int) note[0], channel, volume, Math.round(tick)));
									track.add(MidiFactory.createNoteOffEventEx((int) note[0], channel, volume,
											Math.round(tick + note[1])));
									tick += note[1];
								}
								attackOffset = tick - chordStartTick;
							}
							ornament = null;

							// check for invalid overlapping notes
							Iterator<Triple<Integer, Double, String>> notesOnIter = notesOn.iterator();
							while (notesOnIter.hasNext()) {
								Triple<Integer, Double, String> soundingNote = notesOnIter.next();
								if (soundingNote.second <= chordStartTick) {
									notesOnIter.remove();
								}
							}
							// A note that continues a tie is not a new attack (tested in Lotro: it makes no sound), so it can't overlap
							boolean continuesTie = tiedNotes.containsKey(noteId);
							for (Triple<Integer,Double, String> soundingNote : notesOn) {
								if (!continuesTie && lotroNoteId == soundingNote.first && chordStartTick + 0.0001d < soundingNote.second && enableLotroErrors) {
									// Tested in Lotro: a note that starts again while it still sounds, with a different volume
									// than it started with, makes Lotro play nothing of the part. Without a volume change it plays.
									if (info.getDynamics() != attackDynamics.get(lotroNoteId)) {
										throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.note.restart.volume",
												abcNoteAcc + noteLetter + octaveStr + abcNoteL,
												String.valueOf(soundingNote.third), String.valueOf(info.getDynamics()),
												String.valueOf(info.getPartNumber())), fileName, lineNumber, m.start());
									}
									// 0.0001 is for rounding errors
									double lengthSeconds = info.getWholeNoteTime() * (numerator_abc / (double) denominator_abc);// the overlapping note duration
									log.warning(fileName+": Overlapping note "+soundingNote.third+", lotro might not play part "
											+info.getPartNumber()+" correctly. Overlap ticks="+(soundingNote.second-chordStartTick)+" "+soundingNote.second+" - "+chordStartTick+" "+noteEndTick+ " "+lengthSeconds+"s");
								}
							}

							// Check for overlapping notes, and remove extra note off events
							Iterator<MidiEvent> noteOffIter = noteOffEvents.iterator();
							while (noteOffIter.hasNext()) {
								MidiEvent evt = noteOffIter.next();
								if (evt.getTick() <= chordStartTick) {
									noteOffIter.remove();
									continue;
								}

								int noteOffId = ((ShortMessage) evt.getMessage()).getData1();
								if (noteOffId == noteId) {
									track.remove(evt);
									evt.setTick(Math.round(chordStartTick));
									track.add(evt);
									noteOffIter.remove();
									break;
								}
							}

							if (generateRegions) {
								AbcRegion region = new AbcRegion(lineNumberForRegions, m.start(), m.end(),
										Math.round(chordStartTick + soundOffset), Math.round(noteEndTick), Note.fromId(noteId),
										trackIndex);

								abcInfo.addRegion(region);

								AbcRegion tiesFrom = tiedRegions.get(noteId);
								if (tiesFrom != null) {
									region.setTiesFrom(tiesFrom);
									tiesFrom.setTiesTo(region);
								}

								if (tied)
									tiedRegions.put(noteId, region);
								else
									tiedRegions.remove(noteId);
							}

							// A syllable goes here. Also on a tied note: in w: lyrics tied notes are separate notes (ABC 2.1, 5.1)
							if (!inChord || chordSize == 1)
								lyricNote(lyricNotes, lineIndex, m.start(), lyricBar).ticks.put(repeats.pass,
										Math.round(chordStartTick + soundOffset));

							if (!tiedNotes.containsKey(noteId)) {
								attackDynamics.put(lotroNoteId, info.getDynamics());
								lastAttackTick = Math.round(chordStartTick + attackOffset);
								if (info.getPpqn() != PPQN) {
									throw new FileParseException(UIText.get("common.abctomidi.meter.denominator.same"),
											fileName, meterChangeLine, meterChangeColumn);
								}
								Dynamics attack = accent ? accented(info.getDynamics()) : info.getDynamics();
								MidiEvent noteOn = MidiFactory.createNoteOnEventEx(noteId, channel,
										attack.getVol(useLotroInstruments), Math.round(chordStartTick + attackOffset));
								track.add(noteOn);
								// Where it is written (after grace notes it sounds later), for the beat-group accents
								if (abc21 && !accent && info.getBeatGroups() != null)
									barAttacks.add(new BarAttack(noteOn, Math.round(chordStartTick), attack));
							}

							notesOn.add(new Triple<>(lotroNoteId, noteEndTick, abcNoteAcc+noteLetter+octaveStr+abcNoteL));

							// Like Lotro (tested in game): a tied note joins the next note of the same pitch, wherever
							// that is, and sounds from the first note's start for the sum of their lengths. The later
							// note makes no sound of its own. For a tie to the directly following note, that is the
							// same as the later note's own end.
							// A non-sustained note (e.g. lute) rings for its sample length from its first attack, so a tied
							// one is measured from the tie's first note, not from the note that ends the tie.
							double tieEndTick = noteEndTick;
							double tieStartTick = chordStartTick;
							Double tiedSoFar = tiedNoteEndTicks.get(noteId);
							if (tiedSoFar != null) {
								tieEndTick = tiedSoFar + (noteEndTick - chordStartTick);
								tieStartTick = tiedNoteStartTicks.get(noteId);
							}
							if (tied) {
								tiedNoteEndTicks.put(noteId, tieEndTick);
								tiedNoteStartTicks.put(noteId, tieStartTick);
							} else {
								tiedNoteEndTicks.remove(noteId);
								tiedNoteStartTicks.remove(noteId);
								// A staccato note sounds STACCATO_LENGTH of what is left of it after its grace notes and
								// ornament (not a tied note). Measured from the written start, a trill ending after that
								// would put the note-off before the note-on: a hanging note.
								if (staccato && tiedSoFar == null) {
									double attackTick = chordStartTick + attackOffset;
									tieEndTick = attackTick + (noteEndTick - attackTick) * STACCATO_LENGTH;
								}
							}

							handleNoteTie(useLotroInstruments, enableLotroErrors, info, track, channel, PPQN, tiedNotes,
									noteOffEvents, fileName, lineNumber, m, tied, numerator_abc, denominator_abc, abcNoteL,
									abcNoteAcc, curTempoBPM, tieStartTick, tieEndTick, noteLetter, octaveStr, noteId, lotroNoteId, info.getInstrument());
							if (!inChord) partChordsNumber++;
							if (enableLotroErrors && partChordsNumber > 10_000) {
								throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.too.many.notes",
										info.getTitle()), fileName, lineNumber, i);
							}
						}

						if (!inChord) {
							if (abc21) {
								checkTiesContinue(tiesToContinue, eventPitches, tiedNotes, tiedNoteStartTicks, tiedNoteEndTicks, tiedRegions,
										crossedRepeat, track, channel, info.getDynamics().getVol(useLotroInstruments), noteOffEvents, fileName);
								crossedRepeat = false;
							}
							chordStartTick = noteEndTick;
							attackOffset = 0;
							accent = false;
							staccato = false;
							log.finer("chordStartTick n="+chordStartTick);
						}
						i = m.end();
					}

					if (tuplet != null)
						throw new FileParseException(UIText.get("common.abctomidi.tuplet.unfinished"), fileName,
								lineNumber, i);

					if (inChord)
						throw new FileParseException(UIText.get("common.abctomidi.chord.unclosed"), fileName,
								lineNumber, i);

					if (brokenRhythmDenominator != 1 || brokenRhythmNumerator != 1) {
						// e> at the line's end: its note comes on the next line of notes (Village Music Project; ABC 2.1
						// doesn't forbid it, abc2midi plays it). Lotro plays the part too (tested, B78c).
						brokenCarried = new int[] { brokenRhythmNumerator, brokenRhythmDenominator, lineNumber, i };
					}
				}
			}

			// The file's last part ends here
			if (track != null)
				singLyrics(track, lyricNotes, lyricLines, musicLines, sourceLineNumbers, lastAttackTick + 1);
			lyricNotes.clear();
			lyricLines.clear();
			musicLines.clear();
			verseLineIndexes.clear();
			repeats.newPart();

			if (seq == null)
				throw new FileParseException(UIText.get("common.abctomidi.no.notes"), fileName, lineNumber);

			endUnconnectedTies(enableLotroErrors, tiedNotes, tiedNoteStartTicks, tiedNoteEndTicks, tiedRegions,
					tiesToContinue, track, channel, info.getDynamics().getVol(useLotroInstruments), noteOffEvents, fileName);
			if (brokenCarried[2] >= 0) {
				throw new FileParseException(UIText.get("common.abctomidi.broken.unfinished"), fileName, brokenCarried[2],
						brokenCarried[3]);
			}
		}

		// The last parts without notes get their empty tracks too (see addEmptyTrack)
		while (partTrackCount(seq) < trackNumber)
			addEmptyTrack(seq, abcInfo, partTrackCount(seq) + 1, trackInstruments, useLotroInstruments,
					filesData.getLast().file.getName());

		// Done here for all parts at once, when all tempo changes are known
		Track[] partTracks = seq.getTracks();
		for (int t = 1; t < partTracks.length; t++) {
			endTrack(partTracks[t], trackInstruments.get(t), useLotroInstruments, info.getAllPartsTempoMap(), PPQN,
					info.getPrimaryTempoBPM());
		}

		abcInfo.setPartEndLine(trackNumber, lineNumberForRegions);

		// The accompaniment: new tracks after all parts, so the parts keep their track numbers
		if (track != null && playChords) {
			partEndTicks.put(trackNumber, Math.round(chordStartTick));
			endPartDrone(partDrone, trackNumber, Math.round(chordStartTick), drones);
		}
		if (track != null && abc21)
			accentGroupStarts(barAttacks, (lastBarTick != null) ? lastBarTick : 0, groupTicks(info), useLotroInstruments);
		for (Map.Entry<Integer, List<ChordSymbol>> part : chordSymbols.entrySet()) {
			// Standard ABC: %%MIDI bassprog and chordprog; Lotro files: the programs of Theorbo and Lute of Ages
			int[] programs = accompanimentPrograms.getOrDefault(part.getKey(), new int[] {
					LotroInstrument.BASIC_THEORBO.midi.id(), LotroInstrument.LUTE_OF_AGES.midi.id() });
			trackNumber = addAccompaniment(seq, abcInfo, part.getKey(), part.getValue(), hymn,
					partEndTicks.getOrDefault(part.getKey(), 0L),
					partBarTicks.getOrDefault(part.getKey(), new TreeSet<>()), trackNumber, useLotroInstruments,
					info.getAllPartsTempoMap(), PPQN, info.getPrimaryTempoBPM(), programs[0], programs[1]);
		}

		// The drones after the accompaniment, one per part that has one (two voices of pipes are two sets of pipes)
		if (playDrones) {
			for (Map.Entry<Integer, Drone> part : drones.entrySet()) {
				trackNumber = addDrone(seq, abcInfo, part.getKey(), part.getValue(), trackNumber, useLotroInstruments,
						info.getAllPartsTempoMap(), PPQN, info.getPrimaryTempoBPM());
			}
		}

		writeInfoLines(seq.getTracks()[0], AbcText.decode(abcInfo.getTitle()), infoLines);

		PanGenerator panner = new PanGenerator();

		Track[] tracks = seq.getTracks();

		// Add tempo events
		Long tick = null;
		for (Map.Entry<Long, Integer> tempoEvent : info.getAllPartsTempoMap().entrySet()) {
			tick = tempoEvent.getKey();
			int mpq = (int) MidiUtils.convertTempo(tempoEvent.getValue());
			tracks[0].add(MidiFactory.createTempoEvent(mpq, tick));
		}
		tracks[0].add(MidiFactory.createEndOfTrackEvent(Objects.requireNonNullElse(tick, 1L)));

		List<Object[]> panSortedParts = new ArrayList<>();
		for (int i = 1; i <= trackNumber; i++) {
			panSortedParts.add(new Object[]{i, abcInfo.getPartInstrument(i)});
		}
		panner.sortInstruments(panSortedParts);

		// Add name and pan events
		tracks[0].add(MidiFactory.createTrackNameEvent(abcInfo.getTitle()));
		for (Object[] obj : panSortedParts) {
			int i = (int) obj[0];
			tracks[i].add(MidiFactory.createTrackNameEvent(abcInfo.getPartName(i)));

			int panAmount = panner.get(abcInfo.getPartInstrument(i), stereo, abcInfo.getUserPan(i), -1);
			MidiEvent panEvent = MidiFactory.createPanEvent(panAmount,
					drumTracks.contains(i) ? MidiConstants.DRUM_CHANNEL : getTrackChannel(i));
			tracks[i].add(panEvent);
			abcInfo.setPanEvent(panEvent, i);
		}

		// Add time and key signature events
		tracks[0].add(MidiFactory.createTimeSignatureEvent(abcInfo.getTimeSignature(), 0));
		if (MidiFactory.isSupportedMidiKeyMode(abcInfo.getKeySignature().mode))
			tracks[0].add(MidiFactory.createKeySignatureEvent(abcInfo.getKeySignature(), 0));

		// The song uses triplets only if a real share of its notes has triplet timing: one triplet among thousands
		// of regular notes must not give the whole song a triplet grid
		if (guessNotes > 0 && guessTripletNotes >= guessNotes * TRIPLET_GUESS_MIN_SHARE)
			abcInfo.setHasTriplets(true);

		// The song's length, including the ring-out of plucked notes; needs the tempo events added above
		abcInfo.setSongLengthMicros(seq.getMicrosecondLength());

		return seq;
	}

	/**
	 * A note, chord or rest has been read (ABC 2.1, 4.11, with standard2011): each pitch tied before it must be in it, as
	 * a tie joins a note to the next note of its pitch. Then its own ties are the ones the next one must continue. (Lotro
	 * joins a tied note to the next note of its pitch wherever that is; standard2011 doesn't.) A tie the next one doesn't
	 * continue ends there, at the tied note's end: after a repeat sign or an ending the next note played can be another
	 * (|: c ... d- :| goes back to c), and elsewhere it ties nothing (a tie written as a slur, F-G; user, 2026-10-01).
	 */
	private static void checkTiesContinue(Set<Integer> tiesToContinue, Set<Integer> eventPitches,
										  Map<Integer, Integer> tiedNotes, Map<Integer, Double> tiedNoteStartTicks,
										  Map<Integer, Double> tiedNoteEndTicks, Map<Integer, AbcRegion> tiedRegions,
										  boolean crossedRepeat, Track track, int channel, int velocity,
										  List<MidiEvent> noteOffEvents, String fileName) throws FileParseException {
		for (int pitch : tiesToContinue) {
			Integer lineAndColumn = tiedNotes.get(pitch);
			if (eventPitches.contains(pitch) || lineAndColumn == null)
				continue;
			if (!crossedRepeat)
				log.warning(fileName + ": line " + (lineAndColumn >>> 16) + ": a tie to another pitch or a rest ties nothing");
			MidiEvent noteOff = MidiFactory.createNoteOffEventEx(pitch, channel, velocity,
					Math.round(tiedNoteEndTicks.get(pitch)));
			track.add(noteOff);
			noteOffEvents.add(noteOff);
			tiedNotes.remove(pitch);
			tiedNoteStartTicks.remove(pitch);
			tiedNoteEndTicks.remove(pitch);
			tiedRegions.remove(pitch);
		}
		tiesToContinue.clear();
		tiesToContinue.addAll(tiedNotes.keySet());
		eventPitches.clear();
	}

	/**
	 * The ties left at a part's end, with no note of their pitch after them (e6- | d4, c d- at the end): with Lotro
	 * errors an error at the tie; else the note just ends there (user, 2026-10-01).
	 */
	private static void endUnconnectedTies(boolean enableLotroErrors, Map<Integer, Integer> tiedNotes,
										   Map<Integer, Double> tiedNoteStartTicks, Map<Integer, Double> tiedNoteEndTicks,
										   Map<Integer, AbcRegion> tiedRegions, Set<Integer> tiesToContinue, Track track,
										   int channel, int velocity, List<MidiEvent> noteOffEvents, String fileName)
			throws FileParseException {
		for (Map.Entry<Integer, Integer> tie : tiedNotes.entrySet()) {
			int lineAndColumn = tie.getValue();
			if (enableLotroErrors) {
				throw new FileParseException(UIText.get("common.abctomidi.tie.not.connected"), fileName,
						lineAndColumn >>> 16, lineAndColumn & 0xFFFF);
			}
			log.warning(fileName + ": line " + (lineAndColumn >>> 16) + ": a tie with no note of its pitch after it ties nothing");
			MidiEvent noteOff = MidiFactory.createNoteOffEventEx(tie.getKey(), channel, velocity,
					Math.round(tiedNoteEndTicks.get(tie.getKey())));
			track.add(noteOff);
			noteOffEvents.add(noteOff);
		}
		tiedNotes.clear();
		tiedNoteStartTicks.clear();
		tiedNoteEndTicks.clear();
		tiedRegions.clear();
		tiesToContinue.clear();
	}

	/**
	 * Common slips in the notes whose meaning is sure, written the proper way, in the same length so the columns stay
	 * right: typographic quotes (a word processor's “G”) as "G"; a tie written apart from its note (c4 -c4,
	 * c4|-c4, c4 -|c4; the Nottingham Music Database) moved to the note; a broken rhythm after a slur's end ((c d)>e,
	 * Village Music Project) moved before the ). Also proper ABC that the parser reads only the other way round: a
	 * broken rhythm after grace notes, c{g}<d, moved before them, c<{g}d (ABC 2.1, 4.12: "A<{g}A and A{g}<A are legal
	 * and equivalent"); a dotted tie, C.-C or [CE].-[CE] (4.11), as a tie, C- C. Not with Lotro errors: Lotro plays
	 * nothing of such a part (tested, B30, B77, B78), and the parser says so.
	 */
	static String withSlipsFixed(String line) {
		StringBuilder notes = new StringBuilder(line.replace('“', '"').replace('”', '"'));
		boolean quoted = false;
		for (int i = 0; i < notes.length(); i++) {
			char c = notes.charAt(i);
			if (c == '"')
				quoted = !quoted;
			if (quoted)
				continue;
			if (c == '-') {
				int noteEnd = detachedTieNoteEnd(notes, i);
				if (noteEnd >= 0) {
					notes.deleteCharAt(i);
					notes.insert(noteEnd, '-');
				}
			} else if (c == '.' && i + 1 < notes.length() && notes.charAt(i + 1) == '-' && endsNoteOrChord(notes, i)) {
				// C.-C : the - goes to the note, the dot (layout only) becomes a space
				notes.setCharAt(i, '-');
				notes.setCharAt(i + 1, ' ');
			} else if (c == '{') {
				// c{g}<d : the < (or >> >) goes before the {
				int close = notes.indexOf("}", i);
				if (close < 0)
					break; // The parser says so
				int end = close + 1;
				while (end < notes.length() && (notes.charAt(end) == '>' || notes.charAt(end) == '<')
						&& notes.charAt(end) == notes.charAt(close + 1))
					end++;
				String broken = notes.substring(close + 1, end);
				notes.delete(close + 1, end);
				notes.insert(i, broken);
				i = close + broken.length();
			} else if (c == ')' && i + 1 < notes.length() && (notes.charAt(i + 1) == '>' || notes.charAt(i + 1) == '<')) {
				// The > (or >> <) goes before the )
				int end = i + 1;
				while (end < notes.length() && notes.charAt(end) == notes.charAt(i + 1))
					end++;
				notes.deleteCharAt(i);
				notes.insert(end - 1, ')');
				i = end - 1;
			}
		}
		return notes.toString();
	}

	/**
	 * Whether the - at dash is a tie written apart from its note: right after a note (c4 -c4, c4|-c4, c4 -|c4), with
	 * only spaces and bar lines between. Not after a rest, a chord or grace notes, nor at a line's start (not sure).
	 *
	 * @return The index after the note, where the - belongs, or -1
	 */
	private static int detachedTieNoteEnd(CharSequence notes, int dash) {
		int k = dash - 1;
		while (k >= 0 && (notes.charAt(k) == ' ' || notes.charAt(k) == '\t' || notes.charAt(k) == '|'))
			k--;
		if (k < 0 || k == dash - 1)
			return -1; // At the line's start, or right after the note (a tie as it should be)
		int noteEnd = k + 1;
		while (k >= 0 && (Character.isDigit(notes.charAt(k)) || notes.charAt(k) == '/'))
			k--; // The length
		while (k >= 0 && (notes.charAt(k) == ',' || notes.charAt(k) == '\''))
			k--; // The octave
		return (k >= 0 && "ABCDEFGabcdefg".indexOf(notes.charAt(k)) >= 0) ? noteEnd : -1;
	}

	/** Whether a note (c, ^c'2, C,3/2) or a chord (its ], maybe with a length) ends right before index end. */
	private static boolean endsNoteOrChord(CharSequence notes, int end) {
		int k = end - 1;
		while (k >= 0 && (Character.isDigit(notes.charAt(k)) || notes.charAt(k) == '/'))
			k--; // The length
		if (k >= 0 && notes.charAt(k) == ']')
			return true;
		while (k >= 0 && (notes.charAt(k) == ',' || notes.charAt(k) == '\''))
			k--; // The octave
		return k >= 0 && "ABCDEFGabcdefg".indexOf(notes.charAt(k)) >= 0;
	}

	/** Whether an ending's number comes at from, maybe after spaces and a [ (||1, || [1). */
	private static boolean endingAt(String line, int from) {
		int k = from;
		while (k < line.length() && line.charAt(k) == ' ')
			k++;
		if (k < line.length() && line.charAt(k) == '[')
			k++;
		return k < line.length() && Character.isDigit(line.charAt(k));
	}

	/** Whether a K: line comes later in the tune: before the next X:, from line index from on. */
	private static boolean keyFollows(List<String> lines, int from) {
		for (int l = from; l < lines.size(); l++) {
			String line = lines.get(l);
			if (line.startsWith("X:"))
				return false;
			if (line.startsWith("K:"))
				return true;
		}
		return false;
	}

	/**
	 * Records a tie, or ends the note: its note-off goes at its written end (for a tie, the end of the whole tied
	 * note). A plucked note that rings shorter than written and is last in track is cut later, in endTrack.
	 */
	private static void handleNoteTie(boolean useLotroInstruments, final boolean enableLotroErrors, TuneInfo info,
									  Track track, int channel, long PPQN, Map<Integer, Integer> tiedNotes, List<MidiEvent> noteOffEvents,
									  String fileName, int lineNumber, Matcher m, boolean tied, long numerator_abc, long denominator_abc, String abcNoteL,
									  String abcNoteAcc, int curTempoBPM, double noteStartTick, double noteEndTick, char noteLetter, String octaveStr, int noteId,
									  int lotroNoteId, LotroInstrument instrument) throws LotroFileParseException {

		if (tied) {
			float lengthSeconds = info.getWholeNoteTime() * (numerator_abc / (float) denominator_abc);

			throwExceptionsIfEnabled(enableLotroErrors, fileName, lineNumber, m, abcNoteL, abcNoteAcc, noteLetter,
					octaveStr, lengthSeconds, lotroSeconds(info, numerator_abc, denominator_abc), true, info.getPrimaryTempoBPM());
			int lineAndColumn = (lineNumber << 16) | m.start();
			tiedNotes.put(noteId, lineAndColumn);
		} else {
			float lengthSeconds = info.getWholeNoteTime() * (numerator_abc / (float) denominator_abc);

			throwExceptionsIfEnabled(enableLotroErrors, fileName, lineNumber, m, abcNoteL, abcNoteAcc, noteLetter,
					octaveStr, lengthSeconds, lotroSeconds(info, numerator_abc, denominator_abc), false, info.getPrimaryTempoBPM());

			MidiEvent noteOff = MidiFactory.createNoteOffEventEx(noteId, channel,
					info.getDynamics().getVol(useLotroInstruments), Math.round(noteEndTick));
			track.add(noteOff);
			noteOffEvents.add(noteOff);

			tiedNotes.remove(noteId);
		}
	}

	/**
	 * @param lengthSeconds Used for the 8 s maximum (float, as before; not verified against Lotro)
	 * @param lotroSeconds  Lotro's own calculation, used for the 60 ms minimum (verified in game)
	 */
	private static void throwExceptionsIfEnabled(final boolean enableLotroErrors, String fileName, int lineNumber,
												 Matcher m, String abcNoteL, char noteLetter, float lengthSeconds, double lotroSeconds, int bpm) throws LotroFileParseException {
		// Using double for lengthSeconds can result in rounding errors in 17 decimal
		// place.
		if (enableLotroErrors && lotroSeconds < AbcConstants.SHORTEST_NOTE_SECONDS) {
			throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.rest.too.short",
					formatSeconds(lotroSeconds), noteLetter + abcNoteL), fileName, lineNumber, m.start());
            /*
		} else if (enableLotroErrors && AbcConstants.getShortestNoteMicros(bpm) > 60000L && ((float) lengthSeconds) == ((float) AbcConstants.SHORTEST_NOTE_SECONDS)) {
			throw new LotroParseException("Rest's duration is too short (" + String.format(Locale.US, "%.3f", lengthSeconds)
						+ "s)(" + noteLetter + " " + abcNoteL + ")", fileName, lineNumber, m.start());
            */
		} else if (enableLotroErrors && lengthSeconds > AbcConstants.LONGEST_NOTE_SECONDS) {
			throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.rest.too.long",
					String.format(Locale.US, "%.3f", lengthSeconds), noteLetter + abcNoteL), fileName, lineNumber,
					m.start());
		}
	}

	/**
	 * Very important: It should now fail when it really in abc is 0.06
	 * but inside lotro it is 0.599999
	 *
	 * @param lengthSeconds Used for the 8 s maximum (float, as before; not verified against Lotro)
	 * @param lotroSeconds  Lotro's own calculation, used for the 60 ms minimum (verified in game)
	 */
	private static void throwExceptionsIfEnabled(final boolean enableLotroErrors, String fileName, int lineNumber,
												 Matcher m, String abcNoteL, String abcNoteAcc, char noteLetter, String octaveStr, float lengthSeconds,
												 double lotroSeconds, boolean shouldAddGroup, int bpm) throws LotroFileParseException {
		// Using double for lengthSeconds can result in rounding errors in 17 decimal
		// place.
		if (enableLotroErrors && lotroSeconds < AbcConstants.SHORTEST_NOTE_SECONDS) {
			throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.note.too.short",
					formatSeconds(lotroSeconds), abcNoteAcc + noteLetter + octaveStr + abcNoteL + addGroup(m,
					shouldAddGroup)), fileName, lineNumber, m.start());
		} else if (enableLotroErrors && lengthSeconds > AbcConstants.LONGEST_NOTE_SECONDS) {
			throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.note.too.long",
					String.format(Locale.US, "%.3f", lengthSeconds),
					abcNoteAcc + noteLetter + octaveStr + abcNoteL + addGroup(m, shouldAddGroup)), fileName, lineNumber,
					m.start());
		}
	}

	/**
	 * Removes a % comment. In ABC, % starts a comment anywhere on a line, including in information fields; \% is a
	 * literal percent sign and doesn't start a comment.
	 */
	private static String stripComment(String line) {
		for (int i = 0; i < line.length(); i++) {
			if (line.charAt(i) == '%' && (i == 0 || line.charAt(i - 1) != '\\')) {
				return line.substring(0, i);
			}
		}
		return line;
	}

	/** Turns \% (a literal percent sign in ABC) into %. */
	private static String unescapePercent(String value) {
		return value.replace("\\%", "%");
	}

	/** Lotro's length of a note or rest with the written length n/d, see AbcConstants.lotroNoteSeconds. */
	private static double lotroSeconds(TuneInfo info, long n, long d) {
		return AbcConstants.lotroNoteSeconds(n, d, info.getLNum(), info.getLDenom(), info.getPrimaryTempoBPM(),
				info.getMeterDenominator());
	}

	/**
	 * Seconds for a message. Near the 60 ms limit all digits are shown, because there the difference is in the last
	 * digits (Lotro refuses 0.05999999999999999, which would otherwise print as 0.060).
	 */
	private static String formatSeconds(double seconds) {
		if (Math.abs(seconds - AbcConstants.SHORTEST_NOTE_SECONDS) < 0.0005)
			return Double.toString(seconds);
		return String.format(Locale.US, "%.3f", seconds);
	}

	/**
	 * The pitch of the note in the matcher: {noteId, lotroNoteId}. noteId has the instrument's octave when Lotro
	 * instruments aren't used. A written accidental (^ _ =) is put in accidentals: in ABC it holds to the end of the bar,
	 * for the same note in the same octave (Lotro), in every octave or for this note only (info.getAccidentalScope).
	 */
	private static int[] notePitch(Matcher m, TuneInfo info, Map<Integer, Integer> accidentals,
								   boolean useLotroInstruments) {
		char noteLetter = m.group(NOTE_LETTER).charAt(0);
		String octaveStr = Objects.requireNonNullElse(m.group(NOTE_OCTAVE), "");
		int octave = Character.isUpperCase(noteLetter) ? 3 : 4;
		if (octaveStr.indexOf('\'') >= 0)
			octave += octaveStr.length();
		else if (octaveStr.indexOf(',') >= 0)
			octave -= octaveStr.length();

		// Lotro's ABC: written C is C3 (48), the note Lute of Ages (octaveDelta 0, Lotro's default instrument) sounds;
		// each instrument's octaveDelta is how far it sounds from that (measured in Lotro: lute C 132 Hz, flute C
		// 523 Hz). ABC 2.1 puts C an octave higher, at middle C (60): standardPitch adds that octave.
		int lotroNoteId = (octave + 1) * 12 + CHR_NOTE_DELTA[Character.toLowerCase(noteLetter) - 'a'];
		int noteId = lotroNoteId;
		if (info.isStandardPitch())
			noteId += 12; // ABC 2.1: C is middle C (MIDI 60); Lotro's ABC is an octave lower, before the instrument's shift
		else if (!useLotroInstruments)
			noteId += 12 * info.getInstrument().octaveDelta;

		// The key of accidentals: the note in its octave, or (propagate-accidentals pitch) the note in every octave,
		// negative so the two can't meet
		AbcInstructions.AccidentalScope scope = info.getAccidentalScope();
		int accidentalKey = (scope == AbcInstructions.AccidentalScope.PITCH) ? Math.floorMod(noteId, 12) - 12 : noteId;
		Integer written = null;
		String accidental = m.group(NOTE_ACCIDENTAL);
		if (accidental != null) {
			if (accidental.startsWith("_"))
				written = -accidental.length();
			else if (accidental.startsWith("^"))
				written = accidental.length();
			else if (accidental.equals("="))
				written = 0;
		}
		if (written != null && scope != AbcInstructions.AccidentalScope.NOT)
			accidentals.put(accidentalKey, written);

		int noteDelta;
		if (written != null) {
			noteDelta = written;
		} else if (accidentals.containsKey(accidentalKey)) {
			noteDelta = accidentals.get(accidentalKey);
		} else {
			// The key signature's accidental, or K:'s explicit one (K:G ^c, standard2011)
			noteDelta = info.getKeyAccidental(noteLetter, noteId);
		}
		// K: transpose= octave= or a clef with +8/-8 (never with Lotro errors: Lotro refuses them)
		return new int[] { noteId + noteDelta + info.getTranspose(), lotroNoteId + noteDelta + info.getTranspose() };
	}

	/**
	 * The pitch of the note a step above (1) or below (-1) the matched one in the scale: the next letter, with the key
	 * signature and the bar's accidentals (e.g. above B in K:F is c, above ^c in K:C is d).
	 */
	private static int neighbourPitch(Matcher m, int step, TuneInfo info, Map<Integer, Integer> accidentals,
									  boolean useLotroInstruments) {
		char letter = m.group(NOTE_LETTER).charAt(0);
		String octaveStr = Objects.requireNonNullElse(m.group(NOTE_OCTAVE), "");
		int octave = Character.isUpperCase(letter) ? 3 : 4;
		if (octaveStr.indexOf('\'') >= 0)
			octave += octaveStr.length();
		else if (octaveStr.indexOf(',') >= 0)
			octave -= octaveStr.length();
		int degree = octave * 7 + "CDEFGAB".indexOf(Character.toUpperCase(letter)) + step;
		int newOctave = Math.floorDiv(degree, 7);
		char newLetter = "CDEFGAB".charAt(Math.floorMod(degree, 7));
		String neighbour = (newOctave >= 4)
				? Character.toLowerCase(newLetter) + "'".repeat(newOctave - 4)
				: newLetter + ",".repeat(3 - newOctave);
		Matcher n = NOTE_PATTERN.matcher(neighbour);
		if (!n.matches())
			return notePitch(m, info, accidentals, useLotroInstruments)[0]; // Beyond ABC's octave marks: no neighbour
		return notePitch(n, info, accidentals, useLotroInstruments)[0];
	}

	/**
	 * The quick notes of an ornament, each {pitch, ticks}, played from the note's start; the note itself sounds after
	 * them for the rest of its length (at least one step). Folk style, starting on the note. An ornament that doesn't
	 * fit the note is left out.
	 * <ul>
	 * <li>trill: note, upper, note, upper ... for the whole note</li>
	 * <li>roll (~, Irish): the note in three parts, the second starting with a cut (upper), the third with a tap
	 * (lower)</li>
	 * <li>lowermordent (M, !mordent!): note, lower; uppermordent (P, !pralltriller!): note, upper</li>
	 * <li>turn: upper, note, lower; invertedturn: lower, note, upper</li>
	 * </ul>
	 *
	 * @param ticks The note's length, from where the ornament starts (after its grace notes)
	 * @param step  GRACE_NOTE_SECONDS in ticks
	 */
	static List<double[]> ornamentNotes(String ornament, int note, int upper, int lower, double ticks, double step) {
		List<double[]> notes = new ArrayList<>();
		int steps = (int) (ticks / step + 1e-9);
		switch (ornament) {
			case "trill" -> {
				// An even count, ending on the upper note: the note itself comes last
				int count = (steps - 1) / 2 * 2;
				for (int k = 0; k < count; k++)
					notes.add(new double[] { (k % 2 == 0) ? note : upper, step });
			}
			case "roll" -> {
				double third = ticks / 3;
				if (third >= 2 * step) {
					notes.add(new double[] { note, third });
					notes.add(new double[] { upper, step });
					notes.add(new double[] { note, third - step });
					notes.add(new double[] { lower, step });
				}
			}
			case "lowermordent", "uppermordent" -> {
				if (steps >= 3) {
					notes.add(new double[] { note, step });
					notes.add(new double[] { ornament.equals("lowermordent") ? lower : upper, step });
				}
			}
			case "turn", "invertedturn" -> {
				if (steps >= 4) {
					boolean turn = ornament.equals("turn");
					notes.add(new double[] { turn ? upper : lower, step });
					notes.add(new double[] { note, step });
					notes.add(new double[] { turn ? lower : upper, step });
				}
			}
			default -> {
			}
		}
		return notes;
	}

	/** A note's place in the source file, for lyricNotes: its line index and column. Sorts in the order of the file. */
	private static long sourcePosition(int lineIndex, int column) {
		return ((long) lineIndex << 32) | column;
	}

	/** A note that can get a syllable of the w: lyrics: its bar as written, and the tick it plays at on each pass. */
	private static final class LyricNote {
		final long bar;
		final Map<Integer, Long> ticks = new HashMap<>(); // pass => tick

		LyricNote(long bar) {
			this.bar = bar;
		}
	}

	/** The note at the position, added the first time it's reached (the bar is the one it's written in). */
	private static LyricNote lyricNote(TreeMap<Long, LyricNote> lyricNotes, int lineIndex, int column, long bar) {
		return lyricNotes.computeIfAbsent(sourcePosition(lineIndex, column), k -> new LyricNote(bar));
	}

	/**
	 * Writes a part's w: lyrics, when the part has ended. A run of w: lines (with no music between them) is sung to the
	 * notes written since the previous run. Its lines are the verses, sung one per time the notes are played: verse 1
	 * the first time, verse 2 the second (a repeat, or the section played again by a P: order, PartOrder's copy of the
	 * same lines), and so on; once the verses are used up, verse 1 again (a single w: line is sung every time). Verses
	 * left over are written after the part's last note, as lines of text without timing, so Maestro still shows them.
	 *
	 * @param sourceLineNumbers The file's line of each line index (PartOrder's copies of a section share them), or null
	 * @param unsungTick        Where the verses that aren't sung go: after the part's last note started
	 */
	private static void singLyrics(Track track, TreeMap<Long, LyricNote> lyricNotes, TreeMap<Integer, String> lyricLines,
								   TreeSet<Integer> musicLines, int[] sourceLineNumbers, long unsungTick) {
		record Event(long tick, String text, int verse, boolean detached) {
		}
		List<Event> events = new ArrayList<>();
		Set<Integer> goingOn = new HashSet<>(); // Verses ending with \ (ABC 2.1, 5.1): the next one goes on in their line
		int verseNumber = 0;
		TreeMap<Integer, List<String>> unsung = new TreeMap<>(); // verse number => its lines, in the order of the file
		// Per run of w: lines in the file (the file's line of its first line): its verses, and the times sung so far
		Map<Integer, List<String>> runTexts = new LinkedHashMap<>();
		Map<Integer, Integer> runSung = new HashMap<>();
		List<Integer> lines = new ArrayList<>(lyricLines.keySet());
		int notesFromLine = 0; // The notes of the next run of w: lines are written from this line on
		int k = 0;
		while (k < lines.size()) {
			int firstLine = lines.get(k);
			List<String> texts = new ArrayList<>();
			int lastLine = firstLine;
			do {
				lastLine = lines.get(k);
				texts.add(lyricLines.get(lastLine));
				k++;
			} while (k < lines.size() && musicLines.subSet(lastLine, lines.get(k)).isEmpty());

			Collection<LyricNote> notes = lyricNotes
					.subMap(sourcePosition(notesFromLine, 0), sourcePosition(firstLine, 0)).values();
			notesFromLine = lastLine + 1;
			Set<Integer> passes = new TreeSet<>();
			for (LyricNote note : notes)
				passes.addAll(note.ticks.keySet());
			int run = (sourceLineNumbers != null) ? sourceLineNumbers[firstLine] : firstLine;
			runTexts.putIfAbsent(run, texts);
			boolean[] detached = detachedNotes(new ArrayList<>(notes), new ArrayList<>(passes));
			for (int pass : passes) {
				int time = runSung.merge(run, 1, Integer::sum); // The how-many-th time these notes are sung
				int index = (time <= texts.size()) ? time - 1 : 0;
				String text = texts.get(index);
				List<long[]> slots = new ArrayList<>();
				for (LyricNote note : notes) // A tick of -1: not played on this pass, its syllable is dropped
					slots.add(new long[] { note.ticks.getOrDefault(pass, -1L), note.bar });
				Sung sung = sing(slots, withoutContinuation(text));
				List<Syllable> syllables = new ArrayList<>(sung.syllables());
				if (index > 0) {
					// A detached note after this verse's last syllable: the first verse's syllable for it, the one the
					// score shows under it (Canzonetta's Deh, which the second verse doesn't have)
					for (Syllable first : sing(slots, withoutContinuation(texts.get(0))).syllables()) {
						if (first.slot() >= sung.endSlot() && detached[first.slot()])
							syllables.add(first);
					}
				}
				int verse = verseNumber++;
				if (!withoutContinuation(text).equals(text))
					goingOn.add(verse);
				for (Syllable syllable : syllables)
					events.add(new Event(syllable.tick(), syllable.text(), verse, detached[syllable.slot()]));
			}
		}
		for (Map.Entry<Integer, List<String>> run : runTexts.entrySet()) {
			// The verses beyond the times the notes were played
			List<String> texts = run.getValue();
			int sung = runSung.getOrDefault(run.getKey(), 0);
			for (int verse = Math.max(2, sung + 1); verse <= texts.size() && sung > 0; verse++)
				unsung.computeIfAbsent(verse, v -> new ArrayList<>()).add(texts.get(verse - 1));
		}

		// In the order they're sung (a stable sort: a verse's own order on an equal tick). Each verse starts a new line
		// (/), but not the first one, not after a verse ending with \, and not after a detached syllable, which starts
		// the line of the phrase it begins; a detached syllable starts one
		events.sort(Comparator.comparingLong(Event::tick));
		Event previous = null;
		for (Event event : events) {
			boolean newLine = previous != null && event.verse() != previous.verse()
					&& (event.detached() || (!previous.detached() && !goingOn.contains(previous.verse())));
			track.add(MidiFactory.createTextMetaEvent(MidiConstants.META_LYRIC, (newLine ? "/" : "") + event.text(),
					event.tick()));
			previous = event;
		}

		// Each line on a tick of its own, as on an equal tick MidiText's order is not defined; / starts a new line
		// (MidiText's NEWLINE_NEW, in the text and in the timed lines alike)
		long tick = Math.max(0, unsungTick);
		for (List<String> verseLines : unsung.values()) {
			for (String line : verseLines) {
				String text = verseText(line);
				if (!text.isEmpty())
					track.add(MidiFactory.createTextMetaEvent(MidiConstants.META_LYRIC, "/" + text, tick++));
			}
		}
	}

	/**
	 * A w: line as plain text, for a verse that isn't sung: the syllables joined into words (the same rules as
	 * sing), e.g. "1.~Je-sus, san-to no-me do Cris_to" gives "1. Jesus, santo nome do Cristo".
	 */
	private static String verseText(String text) {
		text = withoutContinuation(text);
		StringBuilder words = new StringBuilder();
		StringBuilder syllable = new StringBuilder();
		for (int i = 0; i <= text.length(); i++) {
			char c = (i < text.length()) ? text.charAt(i) : ' ';
			if (c == '\\' && i + 1 < text.length()) {
				char next = text.charAt(++i);
				if (next == '-')
					syllable.append('-');
				else
					syllable.append(c).append(next);
				continue;
			}
			if (c == '~') {
				syllable.append(' ');
				continue;
			}
			if (!Character.isWhitespace(c) && c != '-' && c != '_' && c != '*' && c != '|') {
				syllable.append(c);
				continue;
			}
			// AbcToMidi.verseText, the end of the character loop
			words.append(AbcText.decode(syllable.toString()));
			syllable.setLength(0);
			// A - after a space joins the word again (syll-a -ble is "syllable")
			if (c == '-' && i > 0 && Character.isWhitespace(text.charAt(i - 1)) && !words.isEmpty()
					&& words.charAt(words.length() - 1) == ' ')
				words.setLength(words.length() - 1);
			// After a space, * or | the word ends; - and _ join the syllables of a word, and so does a space after a
			// held note's - (que - - sto is "questo")
			if (c != '-' && c != '_' && !(Character.isWhitespace(c) && followsHold(text, i)) && !words.isEmpty()
					&& words.charAt(words.length() - 1) != ' ')
				words.append(' ');
		}
		return words.toString().trim();
	}

	/**
	 * The numbers of an ending, checked for Lotro: tested in Lotro, it gives an error for part with an ending for
	 * several passes ([1,3 [1-2); [1 [2 play on.
	 */
	private static String checkEnding(String numbers, boolean enableLotroErrors, String fileName, int lineNumber,
									  int column) throws LotroFileParseException {
		if (enableLotroErrors && !numbers.chars().allMatch(Character::isDigit)) {
			throw new LotroFileParseException(UIText.get("common.abctomidi.lotro.ending.several", numbers), fileName,
					lineNumber, column);
		}
		return numbers;
	}

	/** The numbers of an ending: 1, 1,3 or 1-3 (ABC 2.1 also allows e.g. 1,3,5-7). */
	private static Set<Integer> parseEndingNumbers(String numbers) {
		Set<Integer> result = new HashSet<>();
		for (String range : numbers.split(",")) {
			String[] fromTo = range.split("-");
			int from = Integer.parseInt(fromTo[0]);
			int to = Integer.parseInt(fromTo[fromTo.length - 1]);
			for (int n = from; n <= to; n++)
				result.add(n);
		}
		return result;
	}

	/**
	 * Where the parser is in a part's repeats (ABC 2.1, 4.8 and 4.9), with Params.expandRepeats. A :| goes back to the
	 * |: before it; without one, to the part's start, or to the last double bar (|| |] [|) or :| before it. The endings
	 * [1 [2 [1,3 [2-4 (also |1 and :|2) play on the passes they're numbered for; an ending runs to the next ending, :|,
	 * ||, |] or [|. Without expandRepeats everything plays once, one after the other, as in Lotro.
	 */
	private static final class Repeats {
		final boolean expand;
		final TuneInfo info; // Read at every place a :| can go back to
		int startLine = -1; // Where a :| goes back to (line index); -1 until the part's first line of music
		int startColumn;
		TuneInfo.ReadState startState; // How the notes were read at startLine/startColumn; null without expand
		TuneInfo.ReadState skipState; // How the notes were read where the skipped ending starts: restored at its end
		Dynamics skipDynamics;
		int pass = 1; // 2 is the first time through the section again
		Set<Integer> ending; // The numbers of the ending the parser is in, null outside an ending
		boolean skipping; // The ending isn't played on this pass: its notes take no time
		final Set<Long> jumped = new HashSet<>(); // The :| that went back, as its source position and pass
		int jumpLine; // Where to go back to, after end() returned true
		int jumpColumn;

		Repeats(boolean expand, TuneInfo info) {
			this.expand = expand;
			this.info = info;
		}

		void newPart() {
			startLine = -1;
			startState = null;
			pass = 1;
			ending = null;
			skipping = false;
			jumped.clear();
		}

		/** A line of music: the part's first one is where a :| without |: goes back to. */
		void musicLine(int lineIndex) {
			if (startLine < 0) {
				startLine = lineIndex;
				startColumn = 0;
				markState();
			}
		}

		/** |: at the column before this one. */
		void start(int lineIndex, int column) {
			startLine = lineIndex;
			startColumn = column;
			pass = 1;
			ending = null;
			skip(false);
			markState();
		}

		/** The state a :| restores when it goes back to here. */
		private void markState() {
			if (expand)
				startState = info.readState();
		}

		/** || |] [| : ends an ending, and a :| without |: after it goes back to here. */
		void sectionEnd(int lineIndex, int column) {
			start(lineIndex, column);
		}

		/** [1 |1 :|2 ... : an ending starts. */
		void ending(String numbers) {
			ending = parseEndingNumbers(numbers);
			skip(expand && pass > 1 && !ending.contains(pass));
		}

		/**
		 * Starts or stops skipping an ending this pass doesn't play. What is written in it isn't read either (BUG1015):
		 * its K: M: L: I: and dynamics are undone at its end, so they don't reach the ending that is played.
		 */
		private void skip(boolean skip) {
			if (skip && !skipping) {
				skipState = info.readState();
				skipDynamics = info.getDynamics();
			} else if (!skip && skipping) {
				info.restore(skipState);
				info.setDynamics(skipDynamics.name());
			}
			skipping = skip;
		}

		/**
		 * :| at the column, the whole sign ending before column after.
		 *
		 * @return Whether to go back to jumpLine and jumpColumn, to play the section again
		 */
		boolean end(List<String> lines, int lineIndex, int column, int after) {
			if (skipping) {
				// The end of an ending this pass doesn't play: go on after it
				skip(false);
				ending = null;
				return false;
			}
			boolean again;
			if (!expand)
				again = false;
			else if (ending == null)
				again = (pass == 1);
			else // After an ending: again if another pass has an ending in this section
				again = ending.contains(pass + 1) || endingFollows(lines, startLine, startColumn, pass + 1);
			if (again && jumped.add((sourcePosition(lineIndex, column) << 8) | pass)) {
				pass++;
				ending = null;
				jumpLine = startLine;
				jumpColumn = startColumn;
				return true;
			}
			if (ending != null) {
				// The end of the ending for this pass. The endings after it are for other passes, and are skipped.
				ending = null;
				return false;
			}
			// Played often enough: a later :| without |: goes back to here
			start(lineIndex, after);
			return false;
		}
	}

	/** Quoted text "..." and decorations !...! +...+ , which may contain | and digits. */
	private static final Pattern NOT_A_BAR_PATTERN = Pattern.compile("\"[^\"]*\"|![^!]*!|\\+[^+]*\\+");

	/**
	 * An ending [1 |1 (also in :|2), and the signs that end a section: || |] [| |: :: ; not the || of :|| (a repeat
	 * end, as :|), which would hide the second ending after a first one closed by :||, nor a || right before an ending
	 * (||1, || [1), which is just the bar line before it
	 */
	private static final Pattern ENDING_OR_SECTION_END_PATTERN = Pattern
			.compile("[\\[|](\\d+(?:[,-]\\d+)*)|(?<!:)\\|\\|(?!\\s*\\[?\\d)|\\|]|\\[\\||\\|:|::");

	/**
	 * Whether an ending for the pass comes in the section that starts at the line and column: up to its end (|| |] [|),
	 * the next |: or ::, or the next X:.
	 */
	private static boolean endingFollows(List<String> lines, int lineIndex, int column, int pass) {
		for (int l = lineIndex; l < lines.size(); l++) {
			String line = stripComment(lines.get(l));
			if (XINFO_PATTERN.matcher(line).matches() || line.stripLeading().startsWith("w:") || line.stripLeading().startsWith("s:"))
				continue;
			Matcher info = INFO_PATTERN.matcher(line);
			if (info.matches()) {
				if (info.group(INFO_TYPE).equals("X"))
					return false;
				continue;
			}
			String music = NOT_A_BAR_PATTERN.matcher(line.substring(l == lineIndex ? Math.min(column, line.length()) : 0))
					.replaceAll(" ");
			Matcher m = ENDING_OR_SECTION_END_PATTERN.matcher(music);
			while (m.find()) {
				if (m.group(1) == null)
					return false; // The section ends
				if (parseEndingNumbers(m.group(1)).contains(pass))
					return true;
			}
		}
		return false;
	}

	/** A w: line without the \ that continues it on the next w: line (ABC 2.1, 5.1); a \\ at the end is kept. */
	private static String withoutContinuation(String text) {
		String trimmed = text.stripTrailing();
		int backslashes = 0;
		while (backslashes < trimmed.length() && trimmed.charAt(trimmed.length() - 1 - backslashes) == '\\')
			backslashes++;
		return (backslashes % 2 == 1) ? trimmed.substring(0, trimmed.length() - 1) : text;
	}

	/**
	 * Whether the last character before index i that isn't a space is a held note's -: one after a space or another -
	 * (ABC 2.1, 5.1), not a \- or the - between two syllables.
	 */
	private static boolean followsHold(String text, int i) {
		int k = Math.min(i, text.length()) - 1;
		while (k >= 0 && Character.isWhitespace(text.charAt(k)))
			k--;
		return k >= 1 && text.charAt(k) == '-' && (Character.isWhitespace(text.charAt(k - 1)) || text.charAt(k - 1) == '-');
	}

	/** A syllable that is only punctuation (o_. , ;): no syllable to sing on a note of its own. */
	private static final Pattern PUNCTUATION = Pattern.compile("[.,;:!?]+");

	/** A syllable, sung on the note at slot, at its tick on this pass. */
	private record Syllable(int slot, long tick, String text) {
	}

	/** A w: line's syllables on one pass, and the slot after its last one. */
	private record Sung(List<Syllable> syllables, int endSlot) {
	}

	/**
	 * The notes of a run of w: lines that are played after the run's next pass has started: Canzonetta's e4 after ::,
	 * which starts the next section, while the notes before it are repeated by |: :: . A detached note's syllable
	 * starts the line of the phrase after it, and on a pass whose verse has none for it, the first verse's is sung.
	 */
	private static boolean[] detachedNotes(List<LyricNote> notes, List<Integer> passes) {
		boolean[] detached = new boolean[notes.size()];
		for (int p = 0; p + 1 < passes.size(); p++) {
			long nextPassStart = Long.MAX_VALUE;
			for (LyricNote note : notes) {
				long tick = note.ticks.getOrDefault(passes.get(p + 1), -1L);
				if (tick >= 0)
					nextPassStart = Math.min(nextPassStart, tick);
			}
			for (int n = 0; n < notes.size(); n++) {
				if (notes.get(n).ticks.getOrDefault(passes.get(p), -1L) > nextPassStart)
					detached[n] = true;
			}
		}
		return detached;
	}

	/**
	 * A w: line's syllables, the way Maestro's MidiText reads karaoke (Tune1000): one syllable per note or chord from
	 * the first slot on, and a space after the last syllable of a word. singLyrics writes them, with a / where a line
	 * starts. (Before the very first lyrics a / would give an empty line. A \r after the last syllable instead
	 * wouldn't end a W: line that comes before.)
	 * As in ABC 2.1: a space or - ends a syllable, _ holds the previous syllable over the next note, * skips a note,
	 * ~ is a space within a syllable, \- is a hyphen, and | goes on at the next bar. Syllables beyond the notes are
	 * dropped. Other escapes (\'e, \~n, &eacute;, ...) are decoded in each syllable, see AbcText. A syllable whose
	 * note isn't played on this pass (a first ending, on the second pass) is sung on the next note that holds it (_ or
	 * a - after a space), if that note is played: Canzonetta's o_ over |1F2 ... |2F8. Punctuation alone (o_.) joins
	 * the syllable before.
	 *
	 * @param slots {tick, bar} of each note or chord a syllable can go to; a tick below 0 drops its syllable (the note
	 *              isn't played on this pass)
	 */
	private static Sung sing(List<long[]> slots, String text) {
		List<Integer> sungSlots = new ArrayList<>(); // The slot of each syllable
		List<StringBuilder> syllables = new ArrayList<>();
		StringBuilder syllable = new StringBuilder();
		int slot = 0;
		boolean lastWritten = false; // The last syllable so far got a note
		String heldOver = null; // The last syllable, when its note isn't played on this pass: a note holding it sings it
		for (int i = 0; i <= text.length(); i++) {
			char c = (i < text.length()) ? text.charAt(i) : ' ';
			if (c == '\\' && i + 1 < text.length()) {
				// \- is a hyphen in the syllable; other escapes (\'e, \~n, \\) are decoded with the syllable
				char next = text.charAt(++i);
				if (next == '-')
					syllable.append('-');
				else
					syllable.append(c).append(next);
				continue;
			}
			if (c == '~') {
				syllable.append(' ');
				continue;
			}
			if (!Character.isWhitespace(c) && c != '-' && c != '_' && c != '*' && c != '|') {
				syllable.append(c);
				continue;
			}
			// The syllable so far ends here; after a space, * or | it also ends its word, but not a space after a held
			// note's - (que - - sto is one word)
			boolean wordEnds = (c != '-' && c != '_') && !(Character.isWhitespace(c) && followsHold(text, i));
			if (!syllable.isEmpty() && PUNCTUATION.matcher(syllable).matches() && (heldOver != null
					|| (lastWritten && !syllables.isEmpty()))) {
				// Punctuation alone takes no note: it ends the syllable before (a held-over one too)
				if (heldOver != null) {
					heldOver += syllable;
				} else {
					StringBuilder last = syllables.getLast();
					last.setLength(last.toString().stripTrailing().length());
					last.append(syllable);
				}
				syllable.setLength(0);
			} else if (!syllable.isEmpty()) {
				String decoded = AbcText.decode(syllable.toString());
				lastWritten = slot < slots.size() && slots.get(slot)[0] >= 0;
				if (lastWritten) {
					sungSlots.add(slot);
					syllables.add(new StringBuilder(decoded));
				}
				heldOver = (!lastWritten && slot < slots.size()) ? decoded : null;
				slot++;
				syllable.setLength(0);
			} else if (c == '-' && i > 0 && (Character.isWhitespace(text.charAt(i - 1)) || text.charAt(i - 1) == '-')) {
				// A - after a space or another -: a syllable of its own, the word going on over this note (ABC 2.1, 5.1:
				// syll-a--ble and syll-a -ble are both four notes)
				if (singHeldOver(heldOver, slots, slot, sungSlots, syllables)) {
					heldOver = null;
					lastWritten = true;
				}
				slot++;
				if (!syllables.isEmpty()) {
					StringBuilder last = syllables.getLast();
					if (last.charAt(last.length() - 1) == ' ')
						last.setLength(last.length() - 1); // The space before it didn't end the word
				}
			}
			if (wordEnds && !syllables.isEmpty() && syllables.getLast().charAt(syllables.getLast().length() - 1) != ' ')
				syllables.getLast().append(' ');
			if (c == '_' && singHeldOver(heldOver, slots, slot, sungSlots, syllables)) {
				heldOver = null;
				lastWritten = true;
			}
			if (c == '_' || c == '*') {
				slot++; // The note is held by the previous syllable, or has none
			} else if (c == '|' && slot > 0) {
				long bar = slots.get(Math.min(slot, slots.size()) - 1)[1];
				while (slot < slots.size() && slots.get(slot)[1] <= bar)
					slot++;
			}
		}
		// A line that ends with - (Cris-) goes on in the next w: line. The last syllable keeps its hyphen, so MidiText
		// joins the next line's first syllable to it (tão) instead of starting a new line there.
		String trimmed = text.stripTrailing();
		if (lastWritten && trimmed.endsWith("-") && !trimmed.endsWith("\\-")) {
			StringBuilder last = syllables.getLast();
			last.setLength(last.toString().stripTrailing().length());
			last.append('-');
		}
		List<Syllable> sung = new ArrayList<>();
		for (int k = 0; k < syllables.size(); k++)
			sung.add(new Syllable(sungSlots.get(k), slots.get(sungSlots.get(k))[0], syllables.get(k).toString()));
		return new Sung(sung, slot);
	}

	/**
	 * Sings a syllable whose own note isn't played on this pass on the note at slot that holds it, if that one is.
	 *
	 * @return Whether it was sung
	 */
	private static boolean singHeldOver(String heldOver, List<long[]> slots, int slot, List<Integer> sungSlots,
										List<StringBuilder> syllables) {
		if (heldOver == null || slot >= slots.size() || slots.get(slot)[0] < 0)
			return false;
		sungSlots.add(slot);
		syllables.add(new StringBuilder(heldOver));
		return true;
	}

	/**
	 * Skips the number of a numbered ending (1, 1,3 or 1-2) that starts at index start, if there is one.
	 *
	 * @return The index of its last character, or start - 1 if there is none
	 */
	private static int skipEndingNumber(String line, int start) {
		int end = start;
		while (end < line.length() && Character.isDigit(line.charAt(end))) {
			end++;
			// 1,3 or 1-2: another number follows
			if (end + 1 < line.length() && (line.charAt(end) == ',' || line.charAt(end) == '-')
					&& Character.isDigit(line.charAt(end + 1)))
				end++;
		}
		return end - 1;
	}

	private static String addGroup(Matcher m, boolean shouldReturn) {
		if (shouldReturn) {
			return m.group(NOTE_TIE);
		}
		return "";
	}

	/**
	 * Ends a part's track where its last sound stops.
	 * <p>
	 * A non-sustained Lotro instrument (e.g. lute) rings for its sample length from its attack, however long the note
	 * is written. So with Lotro instruments a plucked note's sound ends where its sample runs out, and the track ends
	 * where the last sound ends. That keeps the sequence length equal to the song duration Maestro writes
	 * (%%song-duration). Only a plucked note written to last beyond that end is cut, to the end; every other note keeps
	 * its written length (none is lengthened: the soundfont's decay plays out a short note).
	 * Otherwise, and for sustained notes, the track ends at its last note-off.
	 * <p>
	 * A tied note is one note-on and one note-off here, so its sample is counted from its first attack.
	 *
	 * @param instrument The instrument the track plays (its program change)
	 * @param tempoMap   Tick -> BPM of the sequence's tempo events
	 */
	private static void endTrack(Track track, LotroInstrument instrument, boolean useLotroInstruments,
								 NavigableMap<Long, Integer> tempoMap, long ppqn, int defaultBpm) {
		if (track.size() > 0) {
			// JDK's Track keeps its end-of-track as the last event and only ever moves it later (not in the
			// Javadoc, so it's checked here). The cuts below can make the track end earlier, so an end-of-track is
			// taken out here and added again at the new end.
			MidiEvent eot = track.get(track.size() - 1);
			if (MidiUtils.isMetaEndOfTrack(eot.getMessage()))
				track.remove(eot);
		}

		// Where the track's sound stops. A plucked note rings until its sample runs out, however long it is written;
		// every other event, sustained note-offs included, counts at its own tick.
		boolean useSampleLengths = useLotroInstruments && instrument != null;
		boolean cowbell = instrument == LotroInstrument.BASIC_COWBELL || instrument == LotroInstrument.MOOR_COWBELL;
		Map<Integer, Long> sampleMicros = new HashMap<>(); // sample ID => length; one lookup per pitch
		Map<Integer, Deque<Long>> noteStarts = new HashMap<>(); // pitch => note-on ticks not yet ended
		List<MidiEvent> pluckedNoteOffs = new ArrayList<>();
		long end = 0;
		for (int i = 0; i < track.size(); i++) {
			MidiEvent event = track.get(i);
			long soundEnd = event.getTick();
			if (useSampleLengths && event.getMessage() instanceof ShortMessage sm) {
				int pitch = sm.getData1();
				if (sm.getCommand() == ShortMessage.NOTE_ON) {
					noteStarts.computeIfAbsent(pitch, k -> new ArrayDeque<>()).add(event.getTick());
				} else if (sm.getCommand() == ShortMessage.NOTE_OFF) {
					Deque<Long> starts = noteStarts.get(pitch);
					Long start = (starts == null) ? null : starts.poll();
					if (start != null && !instrument.isSustainable(pitch)) { // With Lotro instruments the MIDI pitch is the Lotro note
						int sampleId = cowbell ? AbcConstants.COWBELL_NOTE_ID : pitch;
						long micros = sampleMicros.computeIfAbsent(sampleId, id -> sampleMicros(instrument, id));
						soundEnd = ticksAfter(start, micros / 1_000_000.0, tempoMap, ppqn, defaultBpm);
						pluckedNoteOffs.add(event);
					}
				}
			}
			end = Math.max(end, soundEnd);
		}

		// Only a useSampleLengths note that is written to last beyond that is cut, to the end of the track. Notes that end
		// earlier keep their written length, even when that is longer than their sample.
		for (MidiEvent noteOff : pluckedNoteOffs) {
			if (noteOff.getTick() > end) {
				track.remove(noteOff);
				noteOff.setTick(end);
				track.add(noteOff);
			}
		}
		track.add(MidiFactory.createEndOfTrackEvent(end));
	}

	/** Length of an instrument's sample for a note, in microseconds. */
	private static long sampleMicros(LotroInstrument instrument, int sampleId) {
		// getDura returns null for a note the instrument has no sample for. That is checked here rather than caught as
		// an exception: the JVM may throw a NullPointerException without its message once the code is JIT-compiled
		// (OmitStackTraceInFastThrow), so the text of the warning would depend on how warm the JVM is.
		Long micros;
		try {
			micros = LotroInstrumentSampleDuration.getDura(instrument.friendlyName, sampleId);
		} catch (Exception e) {
			micros = null; // No sample table for the instrument; the message isn't used, for the same reason
		}
		if (micros == null) {
			log.warning("Unable to find duration for note " + sampleId + " in " + instrument.friendlyName);
			return AbcConstants.getNonSustainedNoteHoldMicros(instrument);
		}
		return micros;
	}

	/**
	 * The tick that lies the given number of seconds after startTick, following the tempo changes in between.
	 *
	 * @param tempoMap   Tick -> BPM, as used for the sequence's tempo events (60,000,000 / BPM microseconds per quarter)
	 * @param defaultBpm The tempo before the first entry (the sequence's default when there are none)
	 */
	static long ticksAfter(long startTick, double seconds, NavigableMap<Long, Integer> tempoMap, long ppqn,
						   int defaultBpm) {
		long tick = startTick;
		double remaining = seconds;
		while (true) {
			Map.Entry<Long, Integer> tempo = tempoMap.floorEntry(tick);
			double ticksPerSecond = (tempo == null ? defaultBpm : tempo.getValue()) / 60.0 * ppqn;
			Long nextChange = tempoMap.higherKey(tick);
			if (nextChange == null || (nextChange - tick) / ticksPerSecond >= remaining)
				return tick + Math.round(remaining * ticksPerSecond);
			remaining -= (nextChange - tick) / ticksPerSecond;
			tick = nextChange;
		}
	}

	/**
	 * Multiplies two parts of a note length. Overflow would silently give wrong (even negative) lengths, so it's
	 * reported instead.
	 */
	private static long multiplyLength(long a, long b, String fileName, int lineNumber, int column)
			throws FileParseException {
		try {
			return Math.multiplyExact(a, b);
		} catch (ArithmeticException e) {
			throw new FileParseException(UIText.get("common.abctomidi.note.length.too.large"), fileName, lineNumber,
					column);
		}
	}

	/**
	 * Parses the numerator part of an ABC note length ("3" in "c3/4"). A missing numerator means 1.
	 */
	private static int parseLengthNumerator(String numer) {
		if (numer == null)
			return 1;
		int value = Integer.parseInt(numer); // NumberFormatException is an IllegalArgumentException
		if (value < 1)
			throw new IllegalArgumentException("Length must be positive: " + numer);
		return value;
	}

	/**
	 * Parses the denominator part of an ABC note length ("/4" in "c3/4"). A missing denominator means 1, "/" means 2
	 * and "//" means 4.
	 */
	private static int parseLengthDenominator(String denom) {
		if (denom == null)
			return 1;
		else if (denom.equals("/"))
			return 2;
		else if (denom.equals("//"))
			return 4;
		else if (denom.startsWith("//"))
			throw new IllegalArgumentException("\"//\" can't be followed by a number: " + denom);
		int value = Integer.parseInt(denom.substring(1)); // NumberFormatException is an IllegalArgumentException
		if (value < 1)
			throw new IllegalArgumentException("Length must be positive: " + denom);
		return value;
	}

	/**
	 * @deprecated This doesn't work if changing between sustained and non-sustained instruments
	 */
	@Deprecated
	public static void updateInstrumentRealtime(SequencerWrapper sequencer, int trackIndex,
												LotroInstrument instrument) {
		Sequence sequence = sequencer.getSequence();
		if (sequence == null)
			return;

		Track[] tracks = sequencer.getSequence().getTracks();
		if (tracks == null || trackIndex < 0 || trackIndex >= tracks.length)
			return;

		Track track = tracks[trackIndex];

		// Try to find the existing program change event
		ShortMessage programChange = null;
		for (int j = 0; j < track.size(); j++) {
			MidiEvent evt = track.get(j);
			if (evt.getMessage() instanceof ShortMessage m) {
				if (m.getCommand() == ShortMessage.PROGRAM_CHANGE) {
					programChange = m;
					break;
				}
			}
		}

		if (programChange == null)
			return;

		// Update the program change event and resend it to the sequencer's receiver
		MidiFactory.modifyProgramChangeMessage(programChange, instrument.midi.id());

		Receiver receiver = sequencer.getReceiver();
		if (receiver != null) {
			receiver.send(programChange, -1);

			// Turn off any currently-playing notes
			ShortMessage noteOff = new ShortMessage();
			for (int i = MidiConstants.LOWEST_NOTE_ID; i <= MidiConstants.HIGHEST_NOTE_ID; i++) {
				try {
					noteOff.setMessage(ShortMessage.NOTE_OFF, programChange.getChannel(), i, 0);
					receiver.send(noteOff, -1);
				} catch (InvalidMidiDataException e) {
				}
			}
		}
	}

	/** "hymn" as a word anywhere in the files (T:Hymn, R:hymn, N:from a hymnal): its accompaniment is full chords. */
	private static final Pattern HYMN_PATTERN = Pattern.compile("\\bhymn", Pattern.CASE_INSENSITIVE);

	/**
	 * The song's title and the information fields, as MidiText's header lines in track 0, which Maestro shows above the
	 * lyrics ("Title: ...", "Info: Composer: ..."). They are lyric events, not text events: MidiText drops text events
	 * that look like chord names, like a tune called "G". Track 0 is enough: MidiText takes header lines from every
	 * track, only the lyrics from the winning one. The same line (C: in every part of a Lotro file) is written once.
	 * <p>
	 * Every text event in track 0 has a tick of its own, so MidiText's order of them never depends on how it sorts
	 * events on an equal tick: the header lines take the first ticks from 0 that the W: lines haven't taken.
	 */
	private static void writeInfoLines(Track track0, String title, List<String> infoLines) {
		Set<Long> usedTicks = new HashSet<>();
		for (int i = 0; i < track0.size(); i++) {
			if (track0.get(i).getMessage() instanceof MetaMessage meta && isMidiTextType(meta.getType()))
				usedTicks.add(track0.get(i).getTick());
		}
		List<String> lines = new ArrayList<>();
		if (!title.isBlank())
			lines.add("@T" + title);
		for (String line : new LinkedHashSet<>(infoLines)) {
			if (!line.equals("Also known as: " + title))
				lines.add("@I" + line);
		}
		long tick = 0;
		for (String line : lines) {
			while (usedTicks.contains(tick))
				tick++;
			track0.add(MidiFactory.createTextMetaEvent(MidiConstants.META_LYRIC, line, tick++));
		}
	}

	/** Meta event types that Maestro's MidiText reads as text: text, lyric, marker, cue point, M-Live. */
	private static boolean isMidiTextType(int type) {
		return type == MidiConstants.META_TEXT || type == MidiConstants.META_LYRIC || type == MidiConstants.META_MARKER
				|| type == MidiConstants.META_CUE_POINT || type == MidiConstants.META_M_LIVE;
	}

	private static boolean isHymn(List<FileAndData> filesData) {
		for (FileAndData fileAndData : filesData) {
			for (String line : fileAndData.lines) {
				if (HYMN_PATTERN.matcher(line).find())
					return true;
			}
		}
		return false;
	}

	/** Beats in a bar: 4 in 4/4 and 12/8, 3 in 3/4 and 9/8, 2 in 2/4, 2/2 and 6/8. */
	private static int beatsPerBar(TuneInfo info) {
		int numerator = info.getBarNumerator();
		return (numerator % 3 == 0 && numerator > 3) ? numerator / 3 : numerator;
	}

	/** The meter's beat in ticks: a quarter in 4/4, a half in 2/2, a dotted quarter in 6/8 9/8 12/8. */
	private static long beatTicks(TuneInfo info) {
		long beat = info.getTickFactor() * DEFAULT_NOTE_TICKS / info.getBarDenominator();
		int numerator = info.getBarNumerator();
		return (numerator % 3 == 0 && numerator > 3) ? 3 * beat : beat;
	}

	/** A note started in the bar so far: its note-on, where it is written, and its dynamics. */
	private record BarAttack(MidiEvent noteOn, long tick, Dynamics dynamics) {
	}

	/**
	 * A bar line at the tick: the bar's notes at the start of a beat group get their accent. The bar runs from the last
	 * bar line; the part's first bar ends here, so a first bar shorter than the meter (a pickup) is its end, and one as
	 * long or longer starts at the part's start.
	 *
	 * @return The bar line's tick, the next bar's start
	 */
	private static long barLine(List<BarAttack> attacks, Long lastBarTick, long tick, long[] groups,
								boolean useLotroInstruments) {
		long barStart = (lastBarTick != null) ? lastBarTick
				: (groups != null) ? Math.min(0, tick - Arrays.stream(groups).sum()) : 0;
		accentGroupStarts(attacks, barStart, groups, useLotroInstruments);
		return tick;
	}

	/**
	 * Plays the notes at the start of a beat group BEAT_GROUP_ACCENT_STEPS louder, counting the groups from barStart
	 * (and on, past the bar's end, for a bar longer than the meter); then forgets the notes.
	 */
	private static void accentGroupStarts(List<BarAttack> attacks, long barStart, long[] groups,
										  boolean useLotroInstruments) {
		if (groups != null) {
			for (BarAttack attack : attacks) {
				if (isGroupStart(groups, barStart, attack.tick())) {
					ShortMessage message = (ShortMessage) attack.noteOn().getMessage();
					try {
						message.setMessage(message.getCommand(), message.getChannel(), message.getData1(),
								louder(attack.dynamics(), BEAT_GROUP_ACCENT_STEPS).getVol(useLotroInstruments));
					} catch (InvalidMidiDataException e) {
						throw new IllegalStateException(e); // The same message, another velocity
					}
				}
			}
		}
		attacks.clear();
	}

	/** Whether the tick is where a beat group starts, counting the groups from barStart. */
	private static boolean isGroupStart(long[] groups, long barStart, long tick) {
		long offset = tick - barStart;
		long position = 0;
		for (int g = 0; position <= offset; g = (g + 1) % groups.length) {
			if (position == offset)
				return true;
			position += groups[g];
		}
		return false;
	}

	/** The bar's beat groups in ticks (M:2+2+3/8, 7/8: a quarter, a quarter, a dotted quarter), or null. */
	private static long[] groupTicks(TuneInfo info) {
		int[] groups = info.getBeatGroups();
		if (groups == null)
			return null;
		long unit = info.getTickFactor() * DEFAULT_NOTE_TICKS / info.getBarDenominator();
		return Arrays.stream(groups).mapToLong(g -> g * unit).toArray();
	}

	/**
	 * A chord symbol: where it is, the meter's beat and beats per bar, the bar's beat groups in ticks (null for equal
	 * beats), its notes (MIDI, from C3), its bass note and the chord's fifth for an alternating bass (MIDI, C2 to B2).
	 */
	record ChordSymbol(long tick, long beatTicks, int beatsPerBar, long[] groupTicks, int[] pitches, int bass,
					   int fifth) {
		/** Intervals above the root for each chord quality (ABC 2.1, 4.18 leaves the names to the program). */
		private static final Map<String, int[]> QUALITIES = new HashMap<>();
		static {
			quality(new int[] { 0, 4, 7 }, "", "M", "maj");
			quality(new int[] { 0, 3, 7 }, "m", "min", "-");
			quality(new int[] { 0, 4, 7, 10 }, "7", "11", "13");
			quality(new int[] { 0, 4, 7, 11 }, "maj7", "M7", "Maj7");
			quality(new int[] { 0, 3, 7, 10 }, "m7", "min7", "-7");
			quality(new int[] { 0, 3, 6 }, "dim", "o");
			quality(new int[] { 0, 3, 6, 9 }, "dim7", "o7");
			quality(new int[] { 0, 3, 6, 10 }, "m7b5");
			quality(new int[] { 0, 4, 8 }, "aug", "+");
			quality(new int[] { 0, 4, 8, 10 }, "aug7", "+7", "7#5");
			quality(new int[] { 0, 5, 7 }, "sus", "sus4");
			quality(new int[] { 0, 2, 7 }, "sus2");
			quality(new int[] { 0, 5, 7, 10 }, "7sus", "7sus4");
			quality(new int[] { 0, 4, 7, 9 }, "6");
			quality(new int[] { 0, 3, 7, 9 }, "m6");
			quality(new int[] { 0, 4, 7, 10, 14 }, "9");
			quality(new int[] { 0, 4, 7, 11, 14 }, "maj9");
			quality(new int[] { 0, 3, 7, 10, 14 }, "m9");
			quality(new int[] { 0, 4, 7, 14 }, "add9");
			quality(new int[] { 0, 7 }, "5");
		}

		private static void quality(int[] intervals, String... names) {
			for (String name : names)
				QUALITIES.put(name, intervals);
		}

		/**
		 * Root, accidental, quality, an optional /bass (either case, ABC 2.1, 4.18) and an alternate chord in
		 * parentheses, which is only printed: G, F#m, Bb7, Dm7b5, C/E, G/b, G(Em).
		 */
		private static final Pattern NAME = Pattern.compile("([A-G])([#b]?)([^/(]*)(?:/([A-Ga-g])([#b]?))?(?:\\(.*\\))?");

		/** The chord, or null if the text isn't a chord name (an annotation like "^text", "Fine", "a."). */
		static ChordSymbol parse(String text, long tick, long beatTicks, int beatsPerBar, long[] groupTicks,
								 int transpose) {
			Matcher m = NAME.matcher(text.trim());
			if (!m.matches())
				return null;
			int[] intervals = QUALITIES.get(m.group(3));
			if (intervals == null)
				return null;
			int root = pitchClass(m.group(1), m.group(2), transpose);
			int bass = (m.group(4) == null) ? root : pitchClass(m.group(4), m.group(5), transpose);
			int[] pitches = new int[intervals.length];
			int fifth = 7;
			for (int n = 0; n < intervals.length; n++) {
				pitches[n] = 48 + root + intervals[n]; // Root from C3 to B3
				if (intervals[n] >= 6 && intervals[n] <= 8)
					fifth = intervals[n]; // The chord's own fifth: diminished, perfect or augmented
			}
			// Bass notes from C2 to B2
			if (groupTicks != null) {
				// A beat is a group: the first is the quick chord's length
				beatTicks = groupTicks[0];
				beatsPerBar = groupTicks.length;
			}
			return new ChordSymbol(tick, beatTicks, beatsPerBar, groupTicks, pitches, 36 + bass,
					36 + (root + fifth) % 12);
		}

		private static int pitchClass(String letter, String accidental, int transpose) {
			int pitch = CHR_NOTE_DELTA[Character.toLowerCase(letter.charAt(0)) - 'a'];
			if (accidental.equals("#"))
				pitch++;
			else if (accidental.equals("b"))
				pitch--;
			return Math.floorMod(pitch + transpose, 12);
		}
	}

	/**
	 * Adds a part's accompaniment as two new tracks, a bass and a chords track. Each chord lasts until the next one
	 * (the last until the part's written end, partEnd). From the chord's start and from each bar line in it, the root
	 * in the bass, then by the bar's beats: 2 beats (2/4 6/8) chord; 3 beats (3/4 9/8) the chord once, held; 4 beats
	 * (4/4 12/8) chord, the fifth in the bass on beat 3, chord; beat groups (7/8 as 2+2+3, M:2+2+3/8) the chord on each
	 * group after the first, held to its end; else the chord on each beat. A chord that gets no beat of its own (it
	 * lasts a beat or less) is struck with its bass. A hymn: the bass and the chord together from the chord's start
	 * and each bar line (and beat 3 of 4), held.
	 *
	 * @return The new last track number (unchanged if there are no channels left)
	 */
	private static int addAccompaniment(Sequence seq, AbcInfo abcInfo, int part, List<ChordSymbol> chords, boolean hymn,
										long partEnd, NavigableSet<Long> bars, int trackNumber, boolean useLotroInstruments,
										NavigableMap<Long, Integer> tempoMap, long ppqn, int bpm, int bassProgram,
										int chordProgram) {
		if (getTrackChannel(trackNumber + 2) > MidiConstants.CHANNEL_COUNT_ABC - 1)
			return trackNumber; // No channels left for it
		Track bassTrack = accompanimentTrack(seq, abcInfo, part, ++trackNumber, LotroInstrument.BASIC_THEORBO, bassProgram,
				"Bass", useLotroInstruments);
		Track chordTrack = accompanimentTrack(seq, abcInfo, part, ++trackNumber, LotroInstrument.LUTE_OF_AGES, chordProgram,
				"Chords", useLotroInstruments);
		// With Lotro instruments the notes are in Lotro's notation, which the instrument's octave shift moves
		int bassShift = useLotroInstruments ? -12 * LotroInstrument.BASIC_THEORBO.octaveDelta : 0;
		int chordShift = useLotroInstruments ? -12 * LotroInstrument.LUTE_OF_AGES.octaveDelta : 0;
		int bassChannel = getTrackChannel(trackNumber - 1);
		int chordChannel = getTrackChannel(trackNumber);
		int bassVolume = Dynamics.mf.getVol(useLotroInstruments);
		int chordVolume = Dynamics.mp.getVol(useLotroInstruments);

		for (int c = 0; c < chords.size(); c++) {
			ChordSymbol chord = chords.get(c);
			long end = (c + 1 < chords.size()) ? chords.get(c + 1).tick() : Math.max(partEnd, chord.tick() + chord.beatTicks());
			long segmentStart = chord.tick();
			long firstBeatEnd = Math.min(chord.tick() + chord.beatTicks(), end);
			boolean chordStruck = false;
			while (segmentStart < end) {
				Long nextBar = bars.higher(segmentStart);
				long segmentEnd = (nextBar == null || nextBar >= end) ? end : nextBar;
				Long barStart = bars.floor(segmentStart);
				long bar = (barStart == null) ? 0 : barStart;
				firstBeatEnd = Math.min(firstBeatEnd, segmentEnd);
				if (hymn) {
					// Full chords: bass and chord together, held until the next strike
					long strike = segmentStart;
					for (long beat = segmentStart + chord.beatTicks(); beat < segmentEnd; beat += chord.beatTicks()) {
						if (chord.beatsPerBar() == 4 && (beat - bar) / chord.beatTicks() == 2) {
							addFullChord(bassTrack, bassChannel, bassVolume, bassShift, chordTrack, chordChannel,
									chordVolume, chordShift, chord, strike, beat);
							strike = beat;
						}
					}
					addFullChord(bassTrack, bassChannel, bassVolume, bassShift, chordTrack, chordChannel, chordVolume,
							chordShift, chord, strike, segmentEnd);
					chordStruck = true;
					segmentStart = segmentEnd;
					continue;
				}
				if (chord.groupTicks() != null) {
					// Beat groups: the root on the first, the chord on each one after it, held to its end
					for (long beat = segmentStart; beat < segmentEnd; ) {
						long beatEnd = Math.min(groupEnd(chord.groupTicks(), bar, beat), segmentEnd);
						if (beat == segmentStart) {
							addNote(bassTrack, bassChannel, chord.bass() + bassShift, bassVolume, beat, beatEnd);
						} else {
							for (int pitch : chord.pitches())
								addNote(chordTrack, chordChannel, pitch + chordShift, chordVolume, beat, beatEnd);
							chordStruck = true;
						}
						beat = beatEnd;
					}
					segmentStart = segmentEnd;
					continue;
				}
				for (long beat = segmentStart; beat < segmentEnd; beat += chord.beatTicks()) {
					long beatEnd = Math.min(beat + chord.beatTicks(), segmentEnd);
					long beatInBar = (beat - bar) / chord.beatTicks();
					if (beat == segmentStart) {
						addNote(bassTrack, bassChannel, chord.bass() + bassShift, bassVolume, beat, beatEnd);
					} else if (chord.beatsPerBar() == 4 && beatInBar == 2) {
						addNote(bassTrack, bassChannel, chord.fifth() + bassShift, bassVolume, beat, beatEnd);
					} else if (chord.beatsPerBar() == 3) {
						// One chord, held to the end of the bar (or the next chord)
						for (int pitch : chord.pitches())
							addNote(chordTrack, chordChannel, pitch + chordShift, chordVolume, beat, segmentEnd);
						chordStruck = true;
						break;
					} else {
						for (int pitch : chord.pitches())
							addNote(chordTrack, chordChannel, pitch + chordShift, chordVolume, beat, beatEnd);
						chordStruck = true;
					}
				}
				segmentStart = segmentEnd;
			}
			if (!chordStruck && end > chord.tick()) {
				// A quick chord, a beat or less: heard with its bass, not as the bass alone
				for (int pitch : chord.pitches())
					addNote(chordTrack, chordChannel, pitch + chordShift, chordVolume, chord.tick(), firstBeatEnd);
			}
		}
		endTrack(bassTrack, LotroInstrument.BASIC_THEORBO, useLotroInstruments, tempoMap, ppqn, bpm);
		endTrack(chordTrack, LotroInstrument.LUTE_OF_AGES, useLotroInstruments, tempoMap, ppqn, bpm);
		return trackNumber;
	}

	/** The name of a part without a T: of its own: the file header's first T:, else the file's name without extension. */
	private static String defaultPartName(String fileTitle, String fileName) {
		return (fileTitle != null) ? fileTitle : fileName.replaceFirst("\\.[^.]*$", "");
	}

	/**
	 * A part's number from its X: field. ABC 2.1 (3.1.1): "The X: field may be empty": then the number after the part
	 * before's (1 for the first).
	 */
	private static int partNumber(String value, int previous) {
		return value.isEmpty() ? previous + 1 : Integer.parseInt(value);
	}

	/**
	 * The file's name in a message about a tune in it: "book.abc (X:12 The Red Haired Girl)", or without a title yet
	 * "book.abc (X:12)". In a songbook the line alone doesn't say which tune.
	 */
	private static String tuneFileName(String fileName, String number, String title) {
		return (title == null || title.isBlank()) ? UIText.get("common.abctomidi.file.tune", fileName, number)
				: UIText.get("common.abctomidi.file.tune.title", fileName, number, title.trim());
	}

	/** How loud a drone is, unless %%MIDI drone gives its velocities. */
	static final Dynamics DRONE_DYNAMICS = Dynamics.pp;

	/**
	 * A drone starts more than this many milliseconds from every note start of its part: merged into the part's track
	 * (one bagpipe), notes that start together in Lotro share one velocity.
	 */
	static final int DRONE_GAP_MILLIS = 60;

	/** The part's drone ends at the tick (the part's end); one that sounded is kept for its track. */
	private static void endPartDrone(Drone drone, int part, long tick, Map<Integer, Drone> drones) {
		drone.end(tick);
		if (!drone.spans().isEmpty())
			drones.put(part, drone);
	}

	/**
	 * Adds a part's drone as a new track ("<part> - Drone", Basic Bagpipe): its notes held through each span.
	 *
	 * @return The new last track number (unchanged if there are no channels left)
	 */
	private static int addDrone(Sequence seq, AbcInfo abcInfo, int part, Drone drone, int trackNumber,
								boolean useLotroInstruments, NavigableMap<Long, Integer> tempoMap, long ppqn, int bpm) {
		if (getTrackChannel(trackNumber + 1) > MidiConstants.CHANNEL_COUNT_ABC - 1)
			return trackNumber; // No channels left for it
		Track droneTrack = accompanimentTrack(seq, abcInfo, part, ++trackNumber, LotroInstrument.BASIC_BAGPIPE,
				drone.program(), "Drone", useLotroInstruments);
		// With Lotro instruments the notes are in Lotro's notation, which the instrument's octave shift moves
		int shift = useLotroInstruments ? -12 * LotroInstrument.BASIC_BAGPIPE.octaveDelta : 0;
		int channel = getTrackChannel(trackNumber);
		int volume = DRONE_DYNAMICS.getVol(useLotroInstruments);
		NavigableSet<Long> noteStarts = noteStarts(seq.getTracks()[part]);
		for (Drone.Span span : drone.spans()) {
			long start = droneStart(span, noteStarts, tempoMap, ppqn, bpm);
			for (int[] note : drone.notes(volume))
				addNote(droneTrack, channel, note[0] + shift, note[1], start, span.end());
		}
		endTrack(droneTrack, LotroInstrument.BASIC_BAGPIPE, useLotroInstruments, tempoMap, ppqn, bpm);
		return trackNumber;
	}

	/** The ticks where the track's notes start. */
	private static NavigableSet<Long> noteStarts(Track track) {
		NavigableSet<Long> starts = new TreeSet<>();
		for (int i = 0; i < track.size(); i++) {
			if (track.get(i).getMessage() instanceof ShortMessage sm && sm.getCommand() == ShortMessage.NOTE_ON
					&& sm.getData2() > 0)
				starts.add(track.get(i).getTick());
		}
		return starts;
	}

	/**
	 * Where a drone span starts: at its start, or later, just more than DRONE_GAP_MILLIS after the note start that is
	 * too close to it (and again, as long as another one is). At its start after all if that leaves no time before its
	 * end.
	 */
	private static long droneStart(Drone.Span span, NavigableSet<Long> noteStarts, NavigableMap<Long, Integer> tempoMap,
								   long ppqn, int bpm) {
		long start = span.start();
		while (true) {
			long gap = droneGapTicks(start, tempoMap, ppqn, bpm);
			Long near = noteStarts.higher(start - gap);
			if (near == null || near >= start + gap)
				return start;
			start = near + droneGapTicks(near, tempoMap, ppqn, bpm);
			if (start >= span.end())
				return span.start();
		}
	}

	/** The fewest ticks that are more than DRONE_GAP_MILLIS at the tempo at the tick. */
	private static long droneGapTicks(long tick, NavigableMap<Long, Integer> tempoMap, long ppqn, int bpm) {
		Map.Entry<Long, Integer> tempo = tempoMap.floorEntry(tick);
		double ticksPerMilli = ((tempo != null) ? tempo.getValue() : bpm) * ppqn / 60000.0;
		return (long) Math.floor(DRONE_GAP_MILLIS * ticksPerMilli) + 1;
	}

	/** The parts that have a track so far (the tracks after track 0; the accompaniment comes after all parts). */
	private static int partTrackCount(Sequence seq) {
		return (seq == null) ? 0 : seq.getTracks().length - 1;
	}

	/**
	 * An empty track for a part without notes (a tune with only a header), so each part keeps the track of its number
	 * (Maestro hides an empty part). Its instrument is the default one: a part's instrument is set with its notes.
	 */
	private static void addEmptyTrack(Sequence seq, AbcInfo abcInfo, int part,
									  Map<Integer, LotroInstrument> trackInstruments, boolean useLotroInstruments,
									  String fileName) throws FileParseException {
		int channel = getTrackChannel(part);
		if (channel > MidiConstants.CHANNEL_COUNT_ABC - 1)
			throw new FileParseException(UIText.get("common.abctomidi.too.many.parts",
					String.valueOf(MidiConstants.CHANNEL_COUNT_ABC - 1)), fileName);
		Track track = seq.createTrack();
		LotroInstrument instrument = LotroInstrument.DEFAULT_INSTRUMENT;
		int program = instrument.midi.id();
		trackInstruments.put(part, instrument);
		track.add(MidiFactory.createLotroChangeEvent(program, channel, 0));
		abcInfo.abcTrackInfos.add(new ExportTrackInfo(0, null, null, channel, program, Long.MAX_VALUE, 0,0,0,0,0,0, null));
		if (useLotroInstruments) {
			track.add(MidiFactory.createChannelVolumeEvent(MidiConstants.MAX_VOLUME, channel, 1L));
		}
		track.add(MidiFactory.createReverbControlEvent(AbcConstants.MIDI_REVERB, channel, 1L));
		track.add(MidiFactory.createChorusControlEvent(AbcConstants.MIDI_CHORUS, channel, 1L));
		abcInfo.setPartInstrument(part, instrument, false);
	}

	/** Where the beat group that the tick is in ends, counting the groups from the bar's start (and on, past its end). */
	private static long groupEnd(long[] groupTicks, long barStart, long tick) {
		long end = barStart;
		for (int g = 0; ; g = (g + 1) % groupTicks.length) {
			end += groupTicks[g];
			if (end > tick)
				return end;
		}
	}

	private static Track accompanimentTrack(Sequence seq, AbcInfo abcInfo, int part, int index, LotroInstrument instrument,
											int program, String what, boolean useLotroInstruments) {
		Track track = seq.createTrack();
		int channel = getTrackChannel(index);
		track.add(MidiFactory.createLotroChangeEvent(program, channel, 0));
		abcInfo.abcTrackInfos.add(new ExportTrackInfo(0, null, null, channel, program, Long.MAX_VALUE, 0, 0, 0, 0, 0, 0,
				null));
		if (useLotroInstruments) {
			track.add(MidiFactory.createChannelVolumeEvent(MidiConstants.MAX_VOLUME, channel, 1L));
		}
		track.add(MidiFactory.createReverbControlEvent(AbcConstants.MIDI_REVERB, channel, 1L));
		track.add(MidiFactory.createChorusControlEvent(AbcConstants.MIDI_CHORUS, channel, 1L));
		// The part's name as shown; set as final, so the title all parts share isn't taken off it again
		String name = abcInfo.getPartName(part);
		abcInfo.setPartNumber(index, 0); // Maestro numbers it
		abcInfo.setPartName(index, name.isEmpty() ? what : name + " - " + what, true);
		abcInfo.setPartInstrument(index, instrument, false);
		abcInfo.setPartStartLine(index, abcInfo.getPartStartLine(part));
		abcInfo.setPartEndLine(index, abcInfo.getPartEndLine(part));
		return track;
	}

	/** A hymn's full chord: the root in the bass and the chord, together from start to end. */
	private static void addFullChord(Track bassTrack, int bassChannel, int bassVolume, int bassShift, Track chordTrack,
									 int chordChannel, int chordVolume, int chordShift, ChordSymbol chord, long start,
									 long end) {
		addNote(bassTrack, bassChannel, chord.bass() + bassShift, bassVolume, start, end);
		for (int pitch : chord.pitches())
			addNote(chordTrack, chordChannel, pitch + chordShift, chordVolume, start, end);
	}

	private static void addNote(Track track, int channel, int pitch, int volume, long start, long end) {
		track.add(MidiFactory.createNoteOnEventEx(pitch, channel, volume, start));
		track.add(MidiFactory.createNoteOffEventEx(pitch, channel, volume, end));
	}

	private static int getTrackChannel(int trackNumber) {
		if (trackNumber < MidiConstants.DRUM_CHANNEL + 1)
			return trackNumber - 1;

		return trackNumber;
	}

	// Used for ABC Player playlist to read metadata only from ABC to populate playlist view
	public static AbcInfo parseAbcMetadata(List<FileAndData> abc) throws FileParseException {
		AbcInfo abcInfo = new AbcInfo();
		int trackNumber = 0;
		int partNumber = 0; // The last X: number, for an empty X: (as in convert())
		// Same instrument rules as convert(): %%made-for wins, then %%part-name, then the first T: that names one
		boolean instrumentSet = false;
		boolean inBody = false; // The current part's notes have started, so a T: is a section title (as in convert())
		String fileName = null;
		for (FileAndData fileAndData : abc) {
			fileName = fileAndData.file.getName();
			String fileTitle = null; // As in convert(): the first T: names the part, else the file header's, else the file
			int partTitles = 0;
			boolean inFileHeader = true; // Before this file's first X:
			inBody = false; // The file before's last part ended with that file
			abcInfo.addSourceFile(fileAndData.file);
			int lineNumber = 0;
			int partStartLine = 0;

			for (String line : fileAndData.lines) {
				lineNumber++;

				Matcher xInfoMatcher = XINFO_PATTERN.matcher(line);
				if (xInfoMatcher.matches()) {
					AbcField field = AbcField.fromString(xInfoMatcher.group(XINFO_FIELD) + xInfoMatcher.group(XINFO_COLON));
					if (field == AbcField.TEMPO) {
						continue;
					} else if (field != null) {
						String value = xInfoMatcher.group(XINFO_VALUE).trim();
						abcInfo.setExtendedMetadata(field, value);
						if (field == AbcField.PART_NAME) {
							abcInfo.setPartName(trackNumber, value, true);
							LotroInstrument instrument = LotroInstrument.findInstrumentName(value, null);
							if (!abcInfo.getPartInstrumentFromMadeFor(trackNumber) && instrument != null) {
								abcInfo.setPartInstrument(trackNumber, instrument);
								instrumentSet = true;
							}
						} else if (field == AbcField.MADE_FOR) {
							LotroInstrument instrument = LotroInstrument.findInstrumentName(value, null);
							if (instrument != null) {
								abcInfo.setPartInstrument(trackNumber, instrument, true /*made for*/);
								instrumentSet = true;
							}
						}
					}
					continue;
				}

				// Same comment handling as convert(), so the playlist shows the same titles
				line = stripComment(line);

				Matcher infoMatcher = INFO_PATTERN.matcher(line);
				if (infoMatcher.matches()) {
					char type = Character.toUpperCase(infoMatcher.group(INFO_TYPE).charAt(0));
					String value = unescapePercent(infoMatcher.group(INFO_VALUE).trim());

					if (type == 'T' && inBody)
						continue;

					if (type != 'T' || partTitles == 0)
						abcInfo.setMetadata(type, value);

					try {
						switch(type) {
							case 'X': // New part
								instrumentSet = false;
								inBody = false;
								inFileHeader = false;
								trackNumber++;
								partTitles = 0;
								abcInfo.setPartName(trackNumber, defaultPartName(fileTitle, fileName), false);
								partNumber = partNumber(value, partNumber);
								abcInfo.setPartNumber(trackNumber, partNumber);
								abcInfo.setPartStartLine(trackNumber, lineNumber);
								break;
							case 'T':
								if (partTitles++ == 0) {
									if (inFileHeader && fileTitle == null)
										fileTitle = value;
									// In a later file's header, trackNumber is still the file before's last part
									if (!inFileHeader || trackNumber == 0)
										abcInfo.setPartName(trackNumber, value, false);
								}
								if (!instrumentSet && (!inFileHeader || trackNumber == 0)) {
									LotroInstrument instrument = LotroInstrument.findInstrumentName(value, null);
									if (instrument != null) {
										abcInfo.setPartInstrument(trackNumber, instrument);
										instrumentSet = true;
									}
								}
								break;
							default:
								break;
						}
					} catch (IllegalArgumentException e) {
						throw new FileParseException(e.getMessage(), fileName, lineNumber, infoMatcher.start(INFO_VALUE));
					}
				} else if (!line.isBlank() && !line.stripLeading().startsWith("w:")) {
					inBody = true; // A line of notes
				}
			}
		}

		if (abcInfo.isEmpty()) {
			throw new FileParseException(UIText.get("common.abctomidi.files.empty"), fileName);
		}

		return abcInfo;
	}

	// From http://abcnotation.com/abc2mtex/abc.txt:
	//
	// Duplets, triplets, quadruplets, etc.
	// ====================================
	// These can be simply coded with the notation (2ab for a duplet,
	// (3abc for a triplet or (4abcd for a quadruplet, etc., up to (9.
	// The musical meanings are:
	//
	// (2 2 notes in the time of 3
	// (3 3 notes in the time of 2
	// (4 4 notes in the time of 3
	// (5 5 notes in the time of n
	// (6 6 notes in the time of 2
	// (7 7 notes in the time of n
	// (8 8 notes in the time of 3
	// (9 9 notes in the time of n
	//
	// If the time signature is compound (3/8, 6/8, 9/8, 3/4, etc.) then
	// n is three, otherwise n is two.
	//
	// More general tuplets can be specified using the syntax (p:q:r
	// which means `put p notes into the time of q for the next r
	// notes'. If q is not given, it defaults as above. If r is not
	// given, it defaults to p. For example, (3:2:2 is equivalent to
	// (3::2 and (3:2:3 is equivalent to (3:2 , (3 or even (3:: . This
	// can be useful to include notes of different lengths within a
	// tuplet, for example (3:2:2G4c2 or (3:2:4G2A2Bc and also describes
	// more precisely how the simple syntax works in cases like (3D2E2F2
	// or even (3D3EF2. The number written over the tuplet is p.
	private static class Tuplet {
		public int p;
		public int q;
		public int r;

		public Tuplet(String str, boolean compoundMeter) {
			try {
				String[] parts = str.split(":");
				if (parts.length < 1 || parts.length > 3)
					throw new IllegalArgumentException();

				p = Integer.parseInt(parts[0]);

				if (p < 2 || p > 9)
					throw new IllegalArgumentException();

				if (parts.length >= 2 && !parts[1].isEmpty())
					q = Integer.parseInt(parts[1]);
				else if (p == 3 || p == 6)
					q = 2;
				else if (p == 2 || p == 4 || p == 8)
					q = 3;
				else if (p == 5 || p == 7 || p == 9)
					q = compoundMeter ? 3 : 2;
				else
					throw new IllegalArgumentException();

				if (parts.length >= 3)
					r = Integer.parseInt(parts[2]);
				else
					r = p;
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException(e);
			}
		}

		@Override
		public String toString() {
			return "("+p+":"+q+":"+r;
		}
	}

	/**
	 * Used by Unit test only
	 *
	 * @param test a pair of strings [input, expected]
	 * @param compound meter
	 * @return true if pass
	 */
	public static boolean testTuplet(String[] test, boolean compound) {
		Tuplet tuplet = new Tuplet(test[0], compound);
		boolean pass = tuplet.toString().equals(test[1]);
		if (!pass) {
			System.err.println("Input: " + test[0]);
			System.err.println("Actual: " + tuplet.toString());
			System.err.println("Expected: " + test[1]);
		}
		return pass;
	}
}
