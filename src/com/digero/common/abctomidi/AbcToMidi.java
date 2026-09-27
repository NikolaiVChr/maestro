package com.digero.common.abctomidi;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sound.midi.*;

import com.digero.common.abc.*;
import com.digero.common.midi.MidiConstants;
import com.digero.common.midi.MidiFactory;
import com.digero.common.midi.MidiUtils;
import com.digero.common.midi.Note;
import com.digero.common.midi.PanGenerator;
import com.digero.common.midi.SequencerWrapper;
import com.digero.common.util.LotroFileParseException;
import com.digero.common.util.FileParseException;
import com.digero.common.util.Triple;
import com.digero.common.util.WarningHandler;
import com.digero.maestro.abc.AbcExporter.ExportTrackInfo;

public class AbcToMidi {
	private static final Logger log = Logger.getLogger("import.abc");

	/** This is a static-only class */
	private AbcToMidi() {
	}

	public static class Params {
		public List<FileAndData> filesData;

		public boolean useLotroInstruments = true;
		public Map<Integer, LotroInstrument> instrumentOverrideMap = null;
		public boolean enableLotroErrors = false;
		public int stereo = 100;
		public boolean generateRegions = false;
		public AbcInfo abcInfo = null;
		public WarningHandler warningHandler;
		public boolean expandRepeats = false;

		public Params(File file) throws IOException {
			this.filesData = new ArrayList<>();
			this.filesData.add(new FileAndData(file, readLines(file)));
		}

		public Params(List<FileAndData> filesData) {
			this.filesData = filesData;
		}
	}

	private static final Pattern INFO_PATTERN = Pattern.compile("^([A-Z]):\\s*(.*)\\s*$");
	private static final int INFO_TYPE = 1;
	private static final int INFO_VALUE = 2;

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
			return Files.readAllLines(inputFile.toPath(), StandardCharsets.UTF_8);
		} catch (MalformedInputException e) {
			// 2. Fallback: Windows-1252 ("ANSI")
			// This covers the vast majority of legacy Windows files (Windows 7/10/11 with Java 8/11/17).
			// It is a superset of ISO-8859-1, so it correctly handles standard Western characters
			// Plus Windows specific chars like smart quotes and euro signs.
			return Files.readAllLines(inputFile.toPath(), Charset.forName("windows-1252"));
		}
	}

	public static Sequence convert(Params params) throws FileParseException {
		return convert(params.filesData, params.useLotroInstruments, params.instrumentOverrideMap, params.abcInfo,
				params.enableLotroErrors, params.stereo, params.generateRegions, params.expandRepeats, params.warningHandler);
	}

	private static Sequence convert(List<FileAndData> filesData, boolean useLotroInstruments,
									Map<Integer, LotroInstrument> instrumentOverrideMap, AbcInfo abcInfo, final boolean enableLotroErrors,
									final int stereo, final boolean generateRegions, final boolean expandRepeats, WarningHandler warningHandler) throws FileParseException {
		if (abcInfo == null)
			abcInfo = new AbcInfo();
		else
			abcInfo.reset();

		abcInfo.warningHandler = warningHandler;

		TuneInfo info = new TuneInfo();
		Sequence seq = null;
		Track track = null;

		int channel = 0;
		int trackNumber = 0;
		int trackIndex = 0;
		// Where the meter (and with it the PPQN) last changed, for the "must be the same" error
		int meterChangeLine = 0;
		int meterChangeColumn = 0;

		int partChordsNumber = 0;

		int guessNotes = 0; // All notes and rests, for the triplet guess
		int guessTripletNotes = 0; // Those with triplet timing

		int chordStartIndex = 0;
		double chordStartTick = 0;
		double chordEndTick = 0;
		long PPQN = 0;
		Map<Integer, AbcRegion> tiedRegions = new HashMap<>();

		Map<Integer, Integer> tiedNotes = new HashMap<>(); // noteId => (line << 16) | column
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
		Repeats repeats = new Repeats(expandRepeats);
		// Lyrics without timing (W:), each a lyric line in track 0. Before the part's notes they wait here for its track.
		List<String> pendingVerseLines = new ArrayList<>();
		long lastAttackTick = -1; // Where the part's last note started, for W: lines after the notes
		// The next W: line's tick at the earliest. Each line gets a tick of its own: on one tick MidiText sorts by text.
		long nextVerseTick = 0;
		// For +: after a W: line: its text, and its event once written (null while it waits in pendingVerseLines)
		String lastVerseText = "";
		MidiEvent lastVerseEvent = null;
		char lastField = 0; // The field on the line before (w for w:), for a +: line; 0 after a line of music
		int lastLyricLine = -1; // Line index of the last w: line, for a +: line after it

		int lineNumberForRegions = -1;
		abcInfo.abcTrackInfos = new ArrayList<>();
		for (FileAndData fileAndData : filesData) {
			track = null;
			info.newFile();
			String fileName = fileAndData.file.getName();
			abcInfo.addSourceFile(fileAndData.file);
			int lineNumber = 0;
			int partStartLine = 0;
			List<String> lines = fileAndData.lines;
			int firstLineForRegions = lineNumberForRegions + 1; // Region line numbers run on through all files
			int startColumn = 0; // Where the parsing of the line starts: mid-line when going back for a repeat
			lineLoop: for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
				String line = lines.get(lineIndex);
				lineNumberForRegions = firstLineForRegions + lineIndex;
				lineNumber = lineIndex + 1;

				// Handle extended info
				Matcher xInfoMatcher = XINFO_PATTERN.matcher(line);
				if (xInfoMatcher.matches()) {
					AbcField field = AbcField
							.fromString(xInfoMatcher.group(XINFO_FIELD) + xInfoMatcher.group(XINFO_COLON));

					if (field == AbcField.TEMPO) {
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
				// +: continues the field on the line before (ABC 2.1, 3.3), with a space between. Tested in LotRO: it
				// refuses the part.
				if (line.startsWith("+:")) {
					if (enableLotroErrors) {
						throw new LotroFileParseException("LotRO refuses a part with a +: field continuation; put the field "
								+ "on one line", fileName, lineNumber, 0);
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
					}
					// Other fields (T: N: H: ...) keep the text of their first line
					continue;
				}
				// Symbol lines (s:): decorations for the notes above, like w: for lyrics. LotRO plays on (tested).
				if (line.stripLeading().startsWith("s:"))
					continue;

				int chordSize = 0;

				Matcher infoMatcher = INFO_PATTERN.matcher(line);
				if (infoMatcher.matches()) {
					char type = Character.toUpperCase(infoMatcher.group(INFO_TYPE).charAt(0));
					String value = unescapePercent(infoMatcher.group(INFO_VALUE).trim());
					lastField = type;

					// A T: after the part's notes started is a section title (ABC 2.1). LotRO plays on (tested), and it
					// doesn't name the song or the part.
					if (type == 'T' && track != null)
						continue;

					abcInfo.setMetadata(type, value);

					try {
						switch (type) {
							case 'X':
								for (int lineAndColumn : tiedNotes.values()) {
									throw new FileParseException("Tied note does not connect to another note", fileName,
											lineAndColumn >>> 16, lineAndColumn & 0xFFFF);
								}

								if (track != null)
									singLyrics(track, lyricNotes, lyricLines, musicLines, lastAttackTick + 1);

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
									abcInfo.setPartEndLine(trackNumber, lineNumberForRegions - 1);

								info.newPart(Integer.parseInt(value));
								trackNumber++;
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
								break;
							case 'T':
								info.setTitle(value, false);
								abcInfo.setPartName(trackNumber, value, false);
								if (instrumentOverrideMap == null || !instrumentOverrideMap.containsKey(trackNumber)) {
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
								// notes they go on tick 0, else after the last note that started, so no track gets longer.
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
							case 'K':
								String notForLotro = info.setKey(value);
								if (enableLotroErrors && !notForLotro.isEmpty()) {
									throw new LotroFileParseException("LotRO refuses a part with \"" + notForLotro
											+ "\" in K:; write only the key, e.g. K:G or K:D mix", fileName, lineNumber,
											infoMatcher.start(INFO_VALUE));
								}
								break;
							case 'L':
								// The note length doesn't affect the PPQN, so it may differ between parts
								info.setNoteDivisor(value);
								break;
							case 'M':
								if (enableLotroErrors && value.equalsIgnoreCase("none")) {
									throw new LotroFileParseException("LotRO refuses a part with M:none; give a meter, e.g. M:4/4",
											fileName, lineNumber, infoMatcher.start(INFO_VALUE));
								}
								info.setMeter(value, track == null);
								meterChangeLine = lineNumber;
								meterChangeColumn = infoMatcher.start(INFO_VALUE);
								break;
							case 'Q': {
								if (enableLotroErrors && value.indexOf('"') >= 0) {
									throw new LotroFileParseException("LotRO refuses a part with text in Q: (" + value
											+ "); use only the tempo, e.g. Q:120", fileName, lineNumber,
											infoMatcher.start(INFO_VALUE));
								}
								int tempo = info.getPrimaryTempoBPM();
								info.setPrimaryTempoBPM(value);
								if (seq != null && (info.getPrimaryTempoBPM() != tempo)) {
									if (track != null) {
										throw new FileParseException("The tempo can't be changed with Q: in the middle of a part",
												fileName, lineNumber, infoMatcher.start(INFO_VALUE));
									}
									throw new FileParseException("All parts must have the same tempo (Q:" + info.getPrimaryTempoBPM()
											+ " here, Q:" + tempo + " in the earlier parts)", fileName, lineNumber,
											infoMatcher.start(INFO_VALUE));
								}
								break;
							}
						}
					} catch (IllegalArgumentException e) {
						// NumberFormatException's own message ("For input string: ...") doesn't say what's wrong
						String message = (e instanceof NumberFormatException)
								? "Invalid number in " + type + ": field: \"" + value + "\""
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
							abcInfo.setPartName(0, info.getTitle(), false);
							abcInfo.setTimeSignature(info.getMeter());
							abcInfo.setKeySignature(info.getKey());

							track = null;
						} catch (InvalidMidiDataException mde) {
							throw new FileParseException("Midi Error: " + mde.getMessage(), fileName);
						}
					}

					if (track == null) {
						trackIndex = seq.getTracks().length;
						channel = getTrackChannel(trackIndex);
						if (channel > MidiConstants.CHANNEL_COUNT_ABC - 1) {
							throw new FileParseException(
									"Too many parts (max = " + (MidiConstants.CHANNEL_COUNT_ABC - 1) + ")", fileName,
									partStartLine);
						}
						track = seq.createTrack();
						trackInstruments.put(trackIndex, info.getInstrument());
						track.add(MidiFactory.createLotroChangeEvent(info.getInstrument().midi.id(), channel, 0));
						abcInfo.abcTrackInfos.add(new ExportTrackInfo(0, null, null, channel, info.getInstrument().midi.id(),Long.MAX_VALUE, 0,0,0,0,0,0, null));
						if (useLotroInstruments) {
							track.add(MidiFactory.createChannelVolumeEvent(MidiConstants.MAX_VOLUME, channel, 1L));
							track.add(MidiFactory.createReverbControlEvent(AbcConstants.MIDI_REVERB, channel, 1L));
							track.add(MidiFactory.createChorusControlEvent(AbcConstants.MIDI_CHORUS, channel, 1L));
						}

						// The header is done: info has the part's instrument. Definitive means it came from %%made-for.
						abcInfo.setPartInstrument(trackNumber, info.getInstrument(), info.isInstrumentDefinitiveSet());

						// W: lines so far, before the first note
						for (String verseLine : pendingVerseLines)
							seq.getTracks()[0].add(MidiFactory.createTextMetaEvent(MidiConstants.META_LYRIC, "<" + verseLine,
									nextVerseTick++));
						pendingVerseLines.clear();
					}

					Matcher m = NOTE_PATTERN.matcher(line);
					lastField = 0;
					musicLines.add(lineIndex);
					repeats.musicLine(lineIndex);
					int i = startColumn;
					startColumn = 0;
					boolean inChord = false;
					Set<Integer> chordNoteIds = new HashSet<>(); // Pitches in the current chord; only the first of each sounds
					// Length multiplier from the suffix after the current chord's ']' (e.g. [ceg]3/4), applied to
					// every note in the chord. Stays 1/1 when the chord has no suffix or we're not in a chord.
					int chordLenNumerator = 1;
					int chordLenDenominator = 1;
					String chordLenStr = "";
					// Broken rhythm on the current chord, before it (c>[ce]) or after it ([ce]>d), applied to every note in
					// the chord as in ABC 2.1; the note after the chord gets its part when the chord ends. LotRO plays
					// neither like that (tested), so with LotRO errors they're errors.
					long chordBrokenNumerator = 1;
					long chordBrokenDenominator = 1;
					String chordBrokenStr = ""; // The > or < after the chord
					int nextBrokenNumerator = 1;
					int nextBrokenDenominator = 1;
					int chordCloseIndex = -1; // Index of the current chord's ']'; -1 if the chord is unclosed
					Tuplet tuplet = null;
					int brokenRhythmNumerator = 1; // The numerator of the note after the broken rhythm sign
					int brokenRhythmDenominator = 1; // The denominator of the note after the broken rhythm sign
					while (true) {
						boolean found = m.find(i);
						int parseEnd = found ? m.start() : line.length();
						// Parse anything that's not a note
						for (; i < parseEnd; i++) {
							char ch = line.charAt(i);
							if (Character.isWhitespace(ch)) {
								if (inChord) {
									throw new FileParseException("Unexpected whitespace inside a chord", fileName,
											lineNumber, i);
								}
								continue;
							}

							switch (ch) {
								case '[': // Chord start
									if (inChord) {
										throw new FileParseException("Unexpected '" + ch + "' inside a chord", fileName,
												lineNumber, i);
									}

									if (i + 1 < line.length() && Character.isDigit(line.charAt(i + 1))) {
										// [1 [2 ... : the start of a numbered ending. Tested in LotRO: it plays on, and plays
										// no repeats, so every ending plays once, one after the other
										int end = skipEndingNumber(line, i + 1);
										repeats.ending(checkEnding(line.substring(i + 1, end + 1), enableLotroErrors, fileName, lineNumber, i));
										i = end;
										break;
									}
									if (i + 2 < line.length() && Character.isLetter(line.charAt(i + 1)) && line.charAt(i + 2) == ':') {
										// [K:G] [L:1/16] [M:3/4] : an inline field (ABC 2.1, 3.1), the same as a field on a
										// line of its own. Tested in LotRO: it refuses the part.
										int close = line.indexOf(']', i + 3);
										if (close < 0) {
											throw new FileParseException("There is no matching ']'", fileName, lineNumber, i);
										}
										if (enableLotroErrors) {
											throw new LotroFileParseException("LotRO refuses a part with an inline field ("
													+ line.substring(i, close + 1) + "); put the field on a line of its own",
													fileName, lineNumber, i);
										}
										char field = Character.toUpperCase(line.charAt(i + 1));
										String value = line.substring(i + 3, close).trim();
										try {
											switch (field) {
												case 'K' -> info.setKey(value);
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
														throw new FileParseException(
																"The tempo can't be changed with Q: in the middle of a part", fileName,
																lineNumber, i + 3);
													}
												}
												default -> {
													// Other fields (P: V: I: r: ...) change nothing that is played
												}
											}
										} catch (IllegalArgumentException e) {
											String message = (e instanceof NumberFormatException)
													? "Invalid number in " + field + ": field: \"" + value + "\""
													: e.getMessage();
											throw new FileParseException(message, fileName, lineNumber, i + 3);
										}
										i = close;
										break;
									}
									if (line.startsWith("[|]", i)) {
										// [|] : an invisible bar line (ABC 2.1, 4.8). Tested in LotRO: it refuses the part.
										if (enableLotroErrors) {
											throw new LotroFileParseException("LotRO refuses a part with an invisible bar line [|]; "
													+ "use |", fileName, lineNumber, i);
										}
										lyricBar++;
										if (trackNumber == 1)
											abcInfo.addBar(Math.round(chordStartTick));
										accidentals.clear();
										i += 2;
										break;
									}
									if (i + 1 < line.length() && line.charAt(i + 1) == '|') {
										// [| : a thick-thin bar line
										lyricBar++;
										if (trackNumber == 1)
											abcInfo.addBar(Math.round(chordStartTick));
										accidentals.clear();
										i++;
										repeats.sectionEnd(lineIndex, i + 1);
										break;
									}

									chordBrokenNumerator = 1;
									chordBrokenDenominator = 1;
									chordBrokenStr = "";
									nextBrokenNumerator = 1;
									nextBrokenDenominator = 1;
									if (brokenRhythmDenominator != 1 || brokenRhythmNumerator != 1) {
										if (enableLotroErrors) {
											throw new LotroFileParseException("LotRO shortens only the first note of a chord after "
													+ "broken rhythm (c>[ce]), the others keep their length; write the lengths "
													+ "on the notes instead", fileName, lineNumber, i);
										}
										// c>[ce] : the chord gets the second part of the broken rhythm
										chordBrokenNumerator = brokenRhythmNumerator;
										chordBrokenDenominator = brokenRhythmDenominator;
										brokenRhythmNumerator = 1;
										brokenRhythmDenominator = 1;
									}

									chordSize = 0;
									inChord = true;
									chordStartIndex = i;
									chordNoteIds.clear();

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
											throw new FileParseException("Invalid chord length: " + chordLenMatcher.group(),
													fileName, lineNumber, chordCloseIndex + 1);
										}
										chordLenStr = chordLenMatcher.group();
										if (enableLotroErrors && !chordLenStr.isEmpty()) {
											throw new LotroFileParseException("LotRO doesn't support a duration after a chord ("
													+ chordLenStr + "); write the length on each note in the chord instead",
													fileName, lineNumber, chordCloseIndex + 1);
										}
										if (chordLenNumerator == 0 || chordLenDenominator == 0) {
											throw new FileParseException("Invalid chord length: " + chordLenStr, fileName,
													lineNumber, chordCloseIndex + 1);
										}
										// [ce]>d : broken rhythm after the chord
										int brokenStart = chordCloseIndex + 1 + chordLenStr.length();
										int brokenEnd = brokenStart;
										while (!enableLotroErrors && brokenEnd < line.length()
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
										throw new LotroFileParseException("Too many chords/notes/rests in "+info.getTitle()+". Max is 10000.",
												fileName, lineNumber, i);
									}
									break;

								case ']': // Chord end
									if (!inChord) {
										throw new FileParseException("Unexpected '" + ch + "'", fileName, lineNumber, i);
									}
									if (i != chordCloseIndex) {
										// For now this branch should never run.
										throw new FileParseException("Mismatched ']' in chord", fileName, lineNumber, i);
									}
									if (chordSize == 0) {
										throw new FileParseException("Empty chord", fileName, lineNumber, chordStartIndex);
									}
									inChord = false;

									if (tuplet != null && tuplet.r == 0) {
										// A tuplet that ended on this chord have now applied to all of its notes. Now the tuplet is done.
										tuplet = null;
									}

									int chordLenEnd = i + 1 + chordLenStr.length() + chordBrokenStr.length();
									if (generateRegions && !repeats.skipping) { // A skipped ending's chord isn't played
										abcInfo.addRegion(new AbcRegion(lineNumberForRegions, chordStartIndex, chordLenEnd,
												Math.round(chordStartTick), Math.round(chordEndTick), null, trackIndex));
									}

									// Skip the chord length suffix and broken rhythm; the for-loop's i++ lands on chordLenEnd
									i = chordLenEnd - 1;
									chordLenNumerator = 1;
									chordLenDenominator = 1;
									chordLenStr = "";
									// The note after [ce]> gets the rest of the broken rhythm
									brokenRhythmNumerator = nextBrokenNumerator;
									brokenRhythmDenominator = nextBrokenDenominator;
									chordBrokenNumerator = 1;
									chordBrokenDenominator = 1;
									chordBrokenStr = "";
									chordCloseIndex = -1;
									i = chordLenEnd - 1;

									chordStartTick = chordEndTick;
									log.finer("chordStartTick ]="+chordStartTick);
									break;

								case '|': // Bar line
									if (inChord) {
										throw new FileParseException("Unexpected '" + ch + "' inside a chord", fileName,
												lineNumber, i);
									}
									lyricBar++;

									if (trackNumber == 1)
										abcInfo.addBar(Math.round(chordStartTick));

									accidentals.clear();
									char afterBar = (i + 1 < line.length()) ? line.charAt(i + 1) : ' ';
									if (afterBar == '|') {
										repeats.sectionEnd(lineIndex, i + 2); // || : a double bar line
									}
									if (afterBar == ']' || afterBar == ':') {
										i++; // Skip |], |:
										if (afterBar == ']')
											repeats.sectionEnd(lineIndex, i + 1);
										else
											repeats.start(lineIndex, i + 1);
									} else if (trackNumber == 1) {
										abcInfo.addBar(Math.round(chordStartTick));
									}
									int endingEnd = skipEndingNumber(line, i + 1); // |1 |2 : a numbered ending
									if (endingEnd > i)
										repeats.ending(checkEnding(line.substring(i + 1, endingEnd + 1), enableLotroErrors, fileName,
												lineNumber, i + 1));
									i = endingEnd;
									break;

								case ':': // Beginning of repeat end bar line :| ::| :::::::|
									if (inChord) {
										throw new FileParseException("Unexpected '" + ch + "' inside a chord", fileName,
												lineNumber, i);
									}

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

									// After the whole sign: :| ::| :: and :||: (LotRO plays them, tested), and :|: :|] (LotRO
									// refuses them, tested: with LotRO errors they're errors)
									int signEnd;
									if (pipe >= 0) {
										signEnd = pipe + 1;
										if (enableLotroErrors) {
											// Only :| ::| ...
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
										throw new FileParseException("Expected to see '|' after parsing '" + ch + "'", fileName,
												lineNumber, i);
									}
									lyricBar++;
									if (trackNumber == 1)
										abcInfo.addBar(Math.round(chordStartTick));

									if (repeats.end(lines, lineIndex, i, signEnd)) {
										// Play the repeated section again: go back to its start
										lineIndex = repeats.jumpLine - 1;
										startColumn = repeats.jumpColumn;
										continue lineLoop;
									}
									i = signEnd - 1;
									int nextEndingEnd = skipEndingNumber(line, i + 1); // :|2 : a numbered ending
									if (nextEndingEnd > i)
										repeats.ending(checkEnding(line.substring(i + 1, nextEndingEnd + 1), enableLotroErrors,
												fileName, lineNumber, i + 1));
									i = nextEndingEnd;
									break;

								case '+': {
									int j = line.indexOf('+', i + 1);
									if (j < 0) {
										throw new FileParseException("There is no matching '+'", fileName, lineNumber, i);
									}
									String decoration = line.substring(i + 1, j);
									try {
										info.setDynamics(decoration);
									} catch (IllegalArgumentException iae) {
										// +trill+ +fermata+ ... : the ABC 2.0 form of !trill! (ABC 2.1, 4.14). Tested in LotRO: it
										// plays nothing of the part. Only notes (+ceg+, a chord in ABC 1.6) stay an error.
										if (enableLotroErrors) {
											throw new LotroFileParseException("LotRO plays nothing of a part with +" + decoration
													+ "+; only the volumes +pppp+ to +ffff+ work", fileName, lineNumber, i);
										}
										if (decoration.isEmpty() || decoration.matches("[_^=A-Ga-g,'0-9/]*"))
											throw new FileParseException("Unsupported +decoration+", fileName, lineNumber, i);
									}

									if (enableLotroErrors && inChord) {
										throw new LotroFileParseException("Can't include a +decoration+ inside a chord",
												fileName, lineNumber, i);
									}

									i = j;
									break;
								}

								case '"': {
									// "Am" chord symbol or "^text" annotation. LotRO plays on (tested); it plays no chords.
									int j = line.indexOf('"', i + 1);
									if (j < 0) {
										throw new FileParseException("There is no matching '\"'", fileName, lineNumber, i);
									}
									i = j;
									break;
								}

								case '!': {
									// !trill! !f! ... decorations. Tested in LotRO: it plays nothing of the part from the first
									// one on (not even after a +mf+ or on the next line). Without LotRO errors they're skipped.
									int j = line.indexOf('!', i + 1);
									if (j < 0) {
										throw new FileParseException("There is no matching '!'", fileName, lineNumber, i);
									}
									if (enableLotroErrors) {
										throw new LotroFileParseException("LotRO plays nothing of a part from a !decoration! on ("
												+ line.substring(i, j + 1) + "); use +f+ style for volume", fileName, lineNumber, i);
									}
									i = j;
									break;
								}

								case '{': {
									// {g} grace notes. LotRO plays on (tested); they are not played here.
									if (inChord) {
										throw new FileParseException("Unexpected '" + ch + "' inside a chord", fileName,
												lineNumber, i);
									}
									int j = line.indexOf('}', i + 1);
									if (j < 0) {
										throw new FileParseException("There is no matching '}'", fileName, lineNumber, i);
									}
									i = j;
									break;
								}

								case '~': // Roll
								case '.': // Staccato
									// Decorations. LotRO plays on (tested); they change nothing here.
									break;

								case '$': // Score line break (ABC 2.1, 4.1)
								case '`': // Back quote in a beam, e.g. A`B`c (ABC 2.1, 4.7)
									// Layout only, they change nothing that's played. Tested in LotRO: it plays the part up to
									// the sign, and nothing after it.
									if (enableLotroErrors) {
										throw new LotroFileParseException("LotRO stops playing the part at '" + ch
												+ "' (layout only); leave it out", fileName, lineNumber, i);
									}
									break;

								case 'Z': {
									// Z Z4 : a rest of 1 or 4 whole bars (ABC 2.1, 4.5). Tested in LotRO: it refuses the part.
									if (enableLotroErrors) {
										throw new LotroFileParseException("LotRO refuses a part with a multi-measure rest Z; "
												+ "write the rest out, e.g. z8 for a bar of 4/4 with L:1/8", fileName, lineNumber, i);
									}
									if (inChord) {
										throw new FileParseException("Unexpected '" + ch + "' inside a chord", fileName,
												lineNumber, i);
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
									// Decorations in short form (ABC 2.1, 4.14); they change nothing here. Tested in LotRO
									// (T H u v): it refuses the part.
									if (enableLotroErrors) {
										throw new LotroFileParseException("LotRO refuses a part with the decoration '" + ch
												+ "'; leave it out", fileName, lineNumber, i);
									}
									break;
								case 'y':
									// Spacer. Tested in LotRO: it plays nothing of the part.
									if (enableLotroErrors) {
										throw new LotroFileParseException("LotRO doesn't play a part with the spacer 'y'",
												fileName, lineNumber, i);
									}
									break;

								case '(':
									// Tuplet or slur start
									if (i + 1 < line.length() && Character.isDigit(line.charAt(i + 1))) {
										// If it has a digit following it, it's a tuplet
										if (tuplet != null) {
											throw new FileParseException("Unexpected '" + ch + "' before end of tuplet",
													fileName, lineNumber, i);
										}

										// The tuplet spec (p:q:r) runs to the first character that isn't a digit or ':',
										// which may be the end of the line ("Tuplet not finished" is reported there)
										int j = i + 1;
										while (j < line.length() && (line.charAt(j) == ':' || Character.isDigit(line.charAt(j))))
											j++;
										try {
											tuplet = new Tuplet(line.substring(i + 1, j), info.isCompoundMeter());
										} catch (IllegalArgumentException e) {
											throw new FileParseException("Invalid tuplet", fileName, lineNumber, i);
										}
										i = j - 1;
									} else {
										// Otherwise it's a slur, which LotRO conveniently ignores
										if (inChord) {
											throw new FileParseException("Unexpected '" + ch + "' inside a chord", fileName,
													lineNumber, i);
										}
									}
									break;

								case ')':
									// End of a slur, ignore
									if (inChord) {
										throw new FileParseException("Unexpected '" + ch + "' inside a chord", fileName,
												lineNumber, i);
									}
									break;

								case '\\':
									// Line continuation; LotRO treats every line on its own anyway, so it's ignored
									if (!line.substring(i + 1).isBlank()) {
										throw new FileParseException("Unexpected '\\' (only allowed at the end of a line)",
												fileName, lineNumber, i);
									}
									break;

								default:
									throw new FileParseException("Unknown/unexpected character '" + ch + "'", fileName,
											lineNumber, i);
							}
						}

						if (i >= line.length())
							break;

						// The matcher might find +f+, +ff+, or +fff+ and think it's a note
						if (i > m.start())
							continue;

						if (inChord)
							chordSize++;

						if (enableLotroErrors && inChord && chordSize > AbcConstants.MAX_CHORD_NOTES) {
							throw new LotroFileParseException("Too many notes in a chord", fileName, lineNumber, m.start());
						}

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
							throw new FileParseException("Invalid note length: "
									+ Objects.requireNonNullElse(m.group(NOTE_LEN_NUMER), "")
									+ Objects.requireNonNullElse(m.group(NOTE_LEN_DENOM), ""), fileName, lineNumber, m.start());
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
								throw new FileParseException("Invalid broken rhythm: " + brokenRhythm, fileName, lineNumber,
										m.start(NOTE_BROKEN_RHYTHM));
							}
							if (inChord) {
								throw new FileParseException("Can't have broken rhythm (< or >) within a chord", fileName,
										lineNumber, m.start(NOTE_BROKEN_RHYTHM));
							}
							if (m.group(NOTE_TIE) != null) {
								throw new FileParseException("Tied notes can't have broken rhythms (< or >)", fileName,
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
							numerator = multiplyLength(numerator, chordBrokenNumerator, fileName, lineNumber, m.start());
							denominator = multiplyLength(denominator, chordBrokenDenominator, fileName, lineNumber, m.start());
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
						// A chord is as long as its shortest note
						double chordEndTickBeforeThisNote = chordEndTick; // Restored if this note turns out to be ignored
						if (chordEndTick == chordStartTick || noteEndTick < chordEndTick) {
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
								throw new FileParseException("Unexpected accidental on a rest", fileName, lineNumber,
										m.start(NOTE_ACCIDENTAL));
							}
							if (!octaveStr.isEmpty()) {
								throw new FileParseException("Unexpected octave indicator on a rest", fileName, lineNumber,
										m.start(NOTE_OCTAVE));
							}

							float lengthSeconds = info.getWholeNoteTime() * (numerator_abc / (float) denominator_abc);

							throwExceptionsIfEnabled(enableLotroErrors, fileName, lineNumber, m, abcNoteL, noteLetter,
									lengthSeconds, lotroSeconds(info, numerator_abc, denominator_abc), info.getPrimaryTempoBPM());
							if (!inChord) partChordsNumber++;
							if (enableLotroErrors && partChordsNumber > 10_000) {
								throw new LotroFileParseException("Too many chords/notes/rests in "+info.getTitle()+". Max is 10000.",
										fileName, lineNumber, i);
							}
							if (generateRegions) {
								abcInfo.addRegion(new AbcRegion(lineNumberForRegions, m.start(), m.end(),
										Math.round(chordStartTick), Math.round(noteEndTick), Note.REST, trackIndex));
							}
						} else {
							int octave = Character.isUpperCase(noteLetter) ? 3 : 4;
							if (octaveStr.indexOf('\'') >= 0)
								octave += octaveStr.length();
							else if (octaveStr.indexOf(',') >= 0)
								octave -= octaveStr.length();

							int noteId;
							int lotroNoteId;

							lotroNoteId = noteId = (octave + 1) * 12
									+ CHR_NOTE_DELTA[Character.toLowerCase(noteLetter) - 'a'];
							if (!useLotroInstruments)
								noteId += 12 * info.getInstrument().octaveDelta;

							if (m.group(NOTE_ACCIDENTAL) != null) {
								if (m.group(NOTE_ACCIDENTAL).startsWith("_"))
									accidentals.put(noteId, -m.group(NOTE_ACCIDENTAL).length());
								else if (m.group(NOTE_ACCIDENTAL).startsWith("^"))
									accidentals.put(noteId, m.group(NOTE_ACCIDENTAL).length());
								else if (m.group(NOTE_ACCIDENTAL).equals("="))
									accidentals.put(noteId, 0);
							}

							int noteDelta;
							if (accidentals.containsKey(noteId)) {
								noteDelta = accidentals.get(noteId);
							} else {
								// Use the key signature to determine the accidental
								noteDelta = info.getKey().getDefaultAccidental(noteId).deltaNoteId;
							}
							lotroNoteId += noteDelta;
							noteId += noteDelta;
							// K: transpose= octave= or a clef with +8/-8 (never with LotRO errors: LotRO refuses them)
							lotroNoteId += info.getTranspose();
							noteId += info.getTranspose();

							if (enableLotroErrors && lotroNoteId < Note.MIN_PLAYABLE.id)
								throw new LotroFileParseException("Note is too low", fileName, lineNumber, m.start());
							else if (enableLotroErrors && lotroNoteId > Note.MAX_PLAYABLE.id)
								throw new LotroFileParseException("Note is too high", fileName, lineNumber, m.start());

							// Lotro plays only the first of the same note in a chord and ignores the later one completely,
							// also for the chord's length (tested in game, also for enharmonic spellings like [^c_d]).
							// Checked before the cowbell code, which gives all cowbell notes the same pitch.
							if (inChord && !chordNoteIds.add(lotroNoteId)) {
								chordEndTick = chordEndTickBeforeThisNote;
								i = m.end();// required, otherwise the loop will find the same note again and never end
								continue;
							}

							if (info.getInstrument() == LotroInstrument.BASIC_COWBELL
									|| info.getInstrument() == LotroInstrument.MOOR_COWBELL) {
								if (useLotroInstruments) {
									// Randomize the noteId unless it's part of a note tie
									if (m.group(NOTE_TIE) == null && !tiedNotes.containsKey(noteId)) {
										int min = info.getInstrument().lowestPlayable.id;
										int max = info.getInstrument().highestPlayable.id;
										lotroNoteId = noteId = min + (int) (Math.random() * (max - min));
									}
								} else {
									noteId = (info.getInstrument() == LotroInstrument.BASIC_COWBELL) ? 76 : 71;
									lotroNoteId = AbcConstants.COWBELL_NOTE_ID;
								}
							}

							// check for invalid overlapping notes
							Iterator<Triple<Integer, Double, String>> notesOnIter = notesOn.iterator();
							while (notesOnIter.hasNext()) {
								Triple<Integer, Double, String> soundingNote = notesOnIter.next();
								if (soundingNote.second <= chordStartTick) {
									notesOnIter.remove();
								}
							}
							// A note that continues a tie is not a new attack (tested in LotRO: it makes no sound), so it can't overlap
							boolean continuesTie = tiedNotes.containsKey(noteId);
							for (Triple<Integer,Double, String> soundingNote : notesOn) {
								if (!continuesTie && lotroNoteId == soundingNote.first && chordStartTick + 0.0001d < soundingNote.second && enableLotroErrors) {
									// Tested in LotRO: a note that starts again while it still sounds, with a different volume
									// than it started with, makes LotRO play nothing of the part. Without a volume change it plays.
									if (info.getDynamics() != attackDynamics.get(lotroNoteId)) {
										throw new LotroFileParseException("Note " + abcNoteAcc + noteLetter + octaveStr + abcNoteL
												+ " starts again while " + soundingNote.third + " still sounds, at another volume (+"
												+ info.getDynamics() + "+). LotRO then plays nothing of part " + info.getPartNumber(),
												fileName, lineNumber, m.start());
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
										Math.round(chordStartTick), Math.round(noteEndTick), Note.fromId(noteId),
										trackIndex);

								abcInfo.addRegion(region);

								AbcRegion tiesFrom = tiedRegions.get(noteId);
								if (tiesFrom != null) {
									region.setTiesFrom(tiesFrom);
									tiesFrom.setTiesTo(region);
								}

								if (m.group(NOTE_TIE) != null)
									tiedRegions.put(noteId, region);
								else
									tiedRegions.remove(noteId);
							}

							// A syllable goes here. Also on a tied note: in w: lyrics tied notes are separate notes (ABC 2.1, 5.1)
							if (!inChord || chordSize == 1)
								lyricNote(lyricNotes, lineIndex, m.start(), lyricBar).ticks.put(repeats.pass, Math.round(chordStartTick));

							if (!tiedNotes.containsKey(noteId)) {
								attackDynamics.put(lotroNoteId, info.getDynamics());
								lastAttackTick = Math.round(chordStartTick);
								lastAttackTick = Math.round(chordStartTick);
								if (info.getPpqn() != PPQN) {
									throw new FileParseException(
											"The meter denominator (the N in M:x/N) must be the same throughout the song",
											fileName, meterChangeLine, meterChangeColumn);
								}
								track.add(MidiFactory.createNoteOnEventEx(noteId, channel,
										info.getDynamics().getVol(useLotroInstruments), Math.round(chordStartTick)));
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
							if (m.group(NOTE_TIE) != null) {
								tiedNoteEndTicks.put(noteId, tieEndTick);
								tiedNoteStartTicks.put(noteId, tieStartTick);
							} else {
								tiedNoteEndTicks.remove(noteId);
								tiedNoteStartTicks.remove(noteId);
							}

							handleNoteTie(useLotroInstruments, enableLotroErrors, info, track, channel, PPQN, tiedNotes,
									noteOffEvents, fileName, lineNumber, m, numerator_abc, denominator_abc, abcNoteL,
									abcNoteAcc, curTempoBPM, tieStartTick, tieEndTick, noteLetter, octaveStr, noteId, lotroNoteId, info.getInstrument());
							if (!inChord) partChordsNumber++;
							if (enableLotroErrors && partChordsNumber > 10_000) {
								throw new LotroFileParseException("Too many chords/notes/rests in "+info.getTitle()+". Max is 10000.",
										fileName, lineNumber, i);
							}
						}

						if (!inChord) {
							chordStartTick = noteEndTick;
							log.finer("chordStartTick n="+chordStartTick);
						}
						i = m.end();
					}

					if (tuplet != null)
						throw new FileParseException("Tuplet not finished by end of line", fileName, lineNumber, i);

					if (inChord)
						throw new FileParseException("Chord not closed at end of line", fileName, lineNumber, i);

					if (brokenRhythmDenominator != 1 || brokenRhythmNumerator != 1)
						throw new FileParseException("Broken rhythm unfinished at end of line", fileName, lineNumber, i);
				}
			}

			// The file's last part ends here
			if (track != null)
				singLyrics(track, lyricNotes, lyricLines, musicLines, lastAttackTick + 1);
			lyricNotes.clear();
			lyricLines.clear();
			musicLines.clear();
			verseLineIndexes.clear();
			repeats.newPart();

			if (seq == null)
				throw new FileParseException("The file contains no notes", fileName, lineNumber);

			for (int lineAndColumn : tiedNotes.values()) {
				throw new FileParseException("Tied note does not connect to another note", fileName, lineAndColumn >>> 16,
						lineAndColumn & 0xFFFF);
			}
		}

		// Done here for all parts at once, when all tempo changes are known
		Track[] partTracks = seq.getTracks();
		for (int t = 1; t < partTracks.length; t++) {
			endTrack(partTracks[t], trackInstruments.get(t), useLotroInstruments, info.getAllPartsTempoMap(), PPQN,
					info.getPrimaryTempoBPM());
		}

		abcInfo.setPartEndLine(trackNumber, lineNumberForRegions);

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
			MidiEvent panEvent = MidiFactory.createPanEvent(panAmount, getTrackChannel(i));
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
	 * Records a tie, or ends the note: its note-off goes at its written end (for a tie, the end of the whole tied
	 * note). A plucked note that rings shorter than written and is last in track is cut later, in endTrack.
	 */
	private static void handleNoteTie(boolean useLotroInstruments, final boolean enableLotroErrors, TuneInfo info,
									  Track track, int channel, long PPQN, Map<Integer, Integer> tiedNotes, List<MidiEvent> noteOffEvents,
									  String fileName, int lineNumber, Matcher m, long numerator_abc, long denominator_abc, String abcNoteL,
									  String abcNoteAcc, int curTempoBPM, double noteStartTick, double noteEndTick, char noteLetter, String octaveStr, int noteId,
									  int lotroNoteId, LotroInstrument instrument) throws LotroFileParseException {

		if (m.group(NOTE_TIE) != null) {
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
	 * @param lengthSeconds Used for the 8 s maximum (float, as before; not verified against LotRO)
	 * @param lotroSeconds  LotRO's own calculation, used for the 60 ms minimum (verified in game)
	 */
	private static void throwExceptionsIfEnabled(final boolean enableLotroErrors, String fileName, int lineNumber,
												 Matcher m, String abcNoteL, char noteLetter, float lengthSeconds, double lotroSeconds, int bpm) throws LotroFileParseException {
		// Using double for lengthSeconds can result in rounding errors in 17 decimal
		// place.
		if (enableLotroErrors && lotroSeconds < AbcConstants.SHORTEST_NOTE_SECONDS) {
			throw new LotroFileParseException("Rest's duration is too short (" + formatSeconds(lotroSeconds)
					+ "s)(" + noteLetter + abcNoteL + ")", fileName, lineNumber, m.start());
            /*
		} else if (enableLotroErrors && AbcConstants.getShortestNoteMicros(bpm) > 60000L && ((float) lengthSeconds) == ((float) AbcConstants.SHORTEST_NOTE_SECONDS)) {
			throw new LotroParseException("Rest's duration is too short (" + String.format(Locale.US, "%.3f", lengthSeconds)
						+ "s)(" + noteLetter + " " + abcNoteL + ")", fileName, lineNumber, m.start());
            */
		} else if (enableLotroErrors && lengthSeconds > AbcConstants.LONGEST_NOTE_SECONDS) {
			throw new LotroFileParseException("Rest's duration is too long (" + String.format(Locale.US, "%.3f", lengthSeconds) + "s)("
					+ noteLetter + abcNoteL + ")", fileName, lineNumber, m.start());
		}
	}

	/**
	 * Very important: It should now fail when it really in abc is 0.06
	 * but inside lotro it is 0.599999
	 *
	 * @param lengthSeconds Used for the 8 s maximum (float, as before; not verified against LotRO)
	 * @param lotroSeconds  LotRO's own calculation, used for the 60 ms minimum (verified in game)
	 */
	private static void throwExceptionsIfEnabled(final boolean enableLotroErrors, String fileName, int lineNumber,
												 Matcher m, String abcNoteL, String abcNoteAcc, char noteLetter, String octaveStr, float lengthSeconds,
												 double lotroSeconds, boolean shouldAddGroup, int bpm) throws LotroFileParseException {
		// Using double for lengthSeconds can result in rounding errors in 17 decimal
		// place.
		if (enableLotroErrors && lotroSeconds < AbcConstants.SHORTEST_NOTE_SECONDS) {
			throw new LotroFileParseException(
					"Note's duration is too short (" + formatSeconds(lotroSeconds) + "s)(" + abcNoteAcc
							+ noteLetter + octaveStr + abcNoteL + addGroup(m, shouldAddGroup) + ")",
					fileName, lineNumber, m.start());
		} else if (enableLotroErrors && lengthSeconds > AbcConstants.LONGEST_NOTE_SECONDS) {
			throw new LotroFileParseException(
					"Note's duration is too long (" + String.format(Locale.US, "%.3f", lengthSeconds) + "s)(" + abcNoteAcc
							+ noteLetter + octaveStr + abcNoteL + addGroup(m, shouldAddGroup) + ")",
					fileName, lineNumber, m.start());
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

	/** LotRO's length of a note or rest with the written length n/d, see AbcConstants.lotroNoteSeconds. */
	private static double lotroSeconds(TuneInfo info, long n, long d) {
		return AbcConstants.lotroNoteSeconds(n, d, info.getLNum(), info.getLDenom(), info.getPrimaryTempoBPM(),
				info.getMeterDenominator());
	}

	/**
	 * Seconds for a message. Near the 60 ms limit all digits are shown, because there the difference is in the last
	 * digits (LotRO refuses 0.05999999999999999, which would otherwise print as 0.060).
	 */
	private static String formatSeconds(double seconds) {
		if (Math.abs(seconds - AbcConstants.SHORTEST_NOTE_SECONDS) < 0.0005)
			return Double.toString(seconds);
		return String.format(Locale.US, "%.3f", seconds);
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
	 * notes written since the previous run. The first line is verse 1, sung on every pass through the notes. The others
	 * are later verses: in ABC 2.1 (5.2) they're for the times the part is played again (P:), not for the repeats in
	 * it. They're written after the part's last note, as lines of text without timing, so Maestro still shows them.
	 *
	 * @param unsungTick Where the verses that aren't sung go: after the part's last note started
	 */
	private static void singLyrics(Track track, TreeMap<Long, LyricNote> lyricNotes, TreeMap<Integer, String> lyricLines,
								   TreeSet<Integer> musicLines, long unsungTick) {
		record Verse(long firstTick, List<long[]> slots, String text) {
		}
		List<Verse> verses = new ArrayList<>();
		TreeMap<Integer, List<String>> unsung = new TreeMap<>(); // verse number => its lines, in the order of the file
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
			for (int pass : passes) {
				List<long[]> slots = new ArrayList<>();
				long firstTick = Long.MAX_VALUE;
				for (LyricNote note : notes) {
					long tick = note.ticks.getOrDefault(pass, -1L); // -1: not played on this pass, its syllable is dropped
					slots.add(new long[] { tick, note.bar });
					if (tick >= 0)
						firstTick = Math.min(firstTick, tick);
				}
				verses.add(new Verse(firstTick, slots, texts.getFirst()));
			}
			for (int verse = 2; verse <= texts.size() && !passes.isEmpty(); verse++)
				unsung.computeIfAbsent(verse, v -> new ArrayList<>()).add(texts.get(verse - 1));
		}

		// In the order they're sung, so only the first one doesn't start a new line
		verses.sort(Comparator.comparingLong(Verse::firstTick));
		boolean newLine = false;
		for (Verse verse : verses)
			newLine |= addLyrics(track, verse.slots(), verse.text(), newLine);

		// Each line on a tick of its own, as on one tick MidiText sorts by text; / starts a new line (MidiText's
		// NEWLINE_NEW, in the text and in the timed lines alike)
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
	 * addLyrics), e.g. "1.~Je-sus, san-to no-me do Cris_to" gives "1. Jesus, santo nome do Cristo".
	 */
	private static String verseText(String text) {
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
			words.append(AbcText.decode(syllable.toString()));
			syllable.setLength(0);
			// After a space, * or | the word ends; - and _ join the syllables of a word
			if (c != '-' && c != '_' && !words.isEmpty() && words.charAt(words.length() - 1) != ' ')
				words.append(' ');
		}
		return words.toString().trim();
	}

	/**
	 * The numbers of an ending, checked for LotRO: tested in LotRO, it gives an error for part with an ending for
	 * several passes ([1,3 [1-2); [1 [2 play on.
	 */
	private static String checkEnding(String numbers, boolean enableLotroErrors, String fileName, int lineNumber,
									  int column) throws LotroFileParseException {
		if (enableLotroErrors && !numbers.chars().allMatch(Character::isDigit)) {
			throw new LotroFileParseException("LotRO plays nothing of a part with an ending for several passes ("
					+ numbers + "); write the ending out for each pass", fileName, lineNumber, column);
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
	 * ||, |] or [|. Without expandRepeats everything plays once, one after the other, as in LotRO.
	 */
	private static final class Repeats {
		final boolean expand;
		int startLine = -1; // Where a :| goes back to (line index); -1 until the part's first line of music
		int startColumn;
		int pass = 1; // 2 is the first time through the section again
		Set<Integer> ending; // The numbers of the ending the parser is in, null outside an ending
		boolean skipping; // The ending isn't played on this pass: its notes take no time
		final Set<Long> jumped = new HashSet<>(); // The :| that went back, as its source position and pass
		int jumpLine; // Where to go back to, after end() returned true
		int jumpColumn;

		Repeats(boolean expand) {
			this.expand = expand;
		}

		void newPart() {
			startLine = -1;
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
			}
		}

		/** |: at the column before this one. */
		void start(int lineIndex, int column) {
			startLine = lineIndex;
			startColumn = column;
			pass = 1;
			ending = null;
			skipping = false;
		}

		/** || |] [| : ends an ending, and a :| without |: after it goes back to here. */
		void sectionEnd(int lineIndex, int column) {
			start(lineIndex, column);
		}

		/** [1 |1 :|2 ... : an ending starts. */
		void ending(String numbers) {
			ending = parseEndingNumbers(numbers);
			skipping = expand && pass > 1 && !ending.contains(pass);
		}

		/**
		 * :| at the column, the whole sign ending before column after.
		 *
		 * @return Whether to go back to jumpLine and jumpColumn, to play the section again
		 */
		boolean end(List<String> lines, int lineIndex, int column, int after) {
			if (skipping) {
				// The end of an ending this pass doesn't play: go on after it
				skipping = false;
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
	/** An ending [1 |1 (also in :|2), and the signs that end a section: || |] [| |: :: */
	private static final Pattern ENDING_OR_SECTION_END_PATTERN = Pattern
			.compile("[\\[|](\\d+(?:[,-]\\d+)*)|\\|\\||\\|\\]|\\[\\||\\|:|::");

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

	/**
	 * Writes a w: line as lyric events, the way Maestro's MidiText reads karaoke (Tune1000): one syllable per note or
	 * chord from the first slot on, a space after the last syllable of a word, and a / before the first syllable when
	 * lyrics came before: a new line. (Before the very first lyrics it would give an empty line. A \r after the last
	 * syllable instead wouldn't end a W: line that comes before.)
	 * As in ABC 2.1: a space or - ends a syllable, _ holds the previous syllable over the next note, * skips a note,
	 * ~ is a space within a syllable, \- is a hyphen, and | goes on at the next bar. Syllables beyond the notes are
	 * dropped. Other escapes (\'e, \~n, &eacute;, ...) are decoded in each syllable, see AbcText.
	 *
	 * @param slots   {tick, bar} of each note or chord a syllable can go to; a tick below 0 drops its syllable (the
	 *                note isn't played on this pass)
	 * @param newLine Lyrics come before this line
	 * @return Whether any syllable was written
	 */
	private static boolean addLyrics(Track track, List<long[]> slots, String text, boolean newLine) {
		List<Long> ticks = new ArrayList<>();
		List<StringBuilder> syllables = new ArrayList<>();
		StringBuilder syllable = new StringBuilder();
		int slot = 0;
		boolean lastWritten = false; // The last syllable so far got a note
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
			// The syllable so far ends here; after a space, * or | it also ends its word
			boolean wordEnds = (c != '-' && c != '_');
			if (!syllable.isEmpty()) {
				lastWritten = slot < slots.size() && slots.get(slot)[0] >= 0;
				if (lastWritten) {
					ticks.add(slots.get(slot)[0]);
					syllables.add(new StringBuilder(AbcText.decode(syllable.toString())));
				}
				slot++;
				syllable.setLength(0);
			}
			if (wordEnds && !syllables.isEmpty() && syllables.getLast().charAt(syllables.getLast().length() - 1) != ' ')
				syllables.getLast().append(' ');
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
		for (int k = 0; k < syllables.size(); k++) {
			String s = ((k == 0 && newLine) ? "/" : "") + syllables.get(k);
			track.add(MidiFactory.createTextMetaEvent(MidiConstants.META_LYRIC, s, ticks.get(k))); // Lyric
		}
		return !syllables.isEmpty();
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
	 * A non-sustained LotRO instrument (e.g. lute) rings for its sample length from its attack, however long the note
	 * is written. So with LotRO instruments a plucked note's sound ends where its sample runs out, and the track ends
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
					if (start != null && !instrument.isSustainable(pitch)) { // With LotRO instruments the MIDI pitch is the LotRO note
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
			throw new FileParseException("The note length is too large to calculate with", fileName, lineNumber, column);
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

	private static int getTrackChannel(int trackNumber) {
		if (trackNumber < MidiConstants.DRUM_CHANNEL + 1)
			return trackNumber - 1;

		return trackNumber;
	}

	// Used for ABC Player playlist to read metadata only from ABC to populate playlist view
	public static AbcInfo parseAbcMetadata(List<FileAndData> abc) throws FileParseException {
		AbcInfo abcInfo = new AbcInfo();
		int trackNumber = 0;
		// Same instrument rules as convert(): %%made-for wins, then %%part-name, then the first T: that names one
		boolean instrumentSet = false;
		boolean inBody = false; // The current part's notes have started, so a T: is a section title (as in convert())
		String fileName = null;
		for (FileAndData fileAndData : abc) {
			fileName = fileAndData.file.getName();
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

					abcInfo.setMetadata(type, value);

					try {
						switch(type) {
							case 'X': // New part
								instrumentSet = false;
								inBody = false;
								trackNumber++;
								abcInfo.setPartNumber(trackNumber,  Integer.parseInt(value));
								abcInfo.setPartStartLine(trackNumber, lineNumber);
								break;
							case 'T':
								abcInfo.setPartName(trackNumber, value, false);
								if (!instrumentSet) {
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
			throw new FileParseException("Empty or invalid ABC files", fileName);
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
