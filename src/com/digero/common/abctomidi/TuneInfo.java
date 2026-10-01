package com.digero.common.abctomidi;

import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.digero.common.abc.AbcText;
import com.digero.common.abc.Dynamics;
import com.digero.common.abc.LotroInstrument;
import com.digero.common.i18n.UIText;
import com.digero.common.midi.KeyMode;
import com.digero.common.midi.KeySignature;
import com.digero.common.midi.TimeSignature;

public class TuneInfo {
	private int partNumber;
	private String title;
	private boolean titleIsFromExtendedInfo;
	private KeySignature key;
	// K:G ^c, K:D exp _b (ABC 2.1, 3.1.14): an accidental per letter (c d e f g a b) that replaces the key's; null: the
	// key's. Only with standard2011.
	private Integer[] keyAccidentals = new Integer[7];
	// Semitones added to every note, from K:. Separate, as a K: changes only what it names (ABC 2.1, 4.6)
	private int clefShift; // A clef with +8 or -8
	private int transposeShift; // transpose= or t=
	private int octaveShift; // octave=, in semitones
	private long ppqn;
	private int primaryTempoBPM; // In beats of the meter's denominator (M:6/8: eighths), like the MIDI's quarter notes
	// Q: as written (ABC 2.1, 3.1.8): the beat as a fraction of a whole note, and the beats per minute. The beat is 0
	// for Q:120 (the meter's denominator, as in LotRO), UNIT_NOTE_BEAT for Q:120 and Q:C=120 when the Q: note length
	// counts (ABC 2.1, 10.1: unit note lengths, L:), FELT_BEAT for a tempo word and DEFAULT_BEAT without Q:.
	private double tempoBeat;
	private int tempoBeatsPerMinute = 120;
	private boolean standardTempo; // The Q: note length counts, as in ABC 2.1 (LotRO errors off); LotRO ignores it
	private boolean standardPitch; // C is middle C, whatever the instrument (ABC 2.1); LotRO's octave depends on it
	private boolean standard2011; // Params.standard2011 (and LotRO errors off): ABC 2.1 where LotRO plays otherwise
	private boolean tempoGiven; // A Q: so far
	private boolean allPartsTempoFixed; // The first part's header has ended: its tempo is the song's
	private final NavigableMap<Long, Integer> curPartTempoMap = new TreeMap<>(); // Tick -> BPM
	private final NavigableMap<Long, Integer> allPartsTempoMap = new TreeMap<>(); // Tick -> BPM
	private LotroInstrument instrument;
	private boolean instrumentSet;
	private boolean instrumentSetHard = false;
	private Dynamics dynamics;
	private boolean compoundMeter;
	private int meterNumerator;
	private int meterDenominator;
	// The meter's beat groups, in its denominator's notes, when the numerator is written as a sum (M:2+2+3/8, ABC 2.1,
	// 3.1.6); else null
	private int[] beatGroups;
    private int noteDivisorNum;
    private int noteDivisorDenom;
    private int tickFactor = 16;

	// Tested in LotRO: every part starts from the K:, M: and L: of the file header (before the first X:), not from
	// the previous part. And as in ABC 2.1, an M: in a header without an L: in that header gives the default length.
	private boolean inFileHeader;
	private KeySignature fileKey;
	private Integer[] fileKeyAccidentals = new Integer[7];
	private int fileClefShift;
	private int fileTransposeShift;
	private int fileOctaveShift;
	private int fileMeterNumerator;
	private int fileMeterDenominator;
	private int[] fileBeatGroups;
	private int fileNoteDivisorNum;
	private int fileNoteDivisorDenom;
	private boolean noteDivisorSetInHeader; // An L: in the current header (the file's or the part's)
	// I:linebreak and I:decoration (ABC 2.1): every part starts from the file header's
	private AbcInstructions instructions = new AbcInstructions();
	private AbcInstructions fileInstructions = new AbcInstructions();
	// The tempo of the tune's type in R: (RhythmTempo), as a Q: value, for a song without Q:; every part starts from
	// the file header's
	private String rhythmTempo;
	private String fileRhythmTempo;

    public TuneInfo() {
		partNumber = 0;
		title = "";
		titleIsFromExtendedInfo = false;
		key = KeySignature.C_MAJOR;
		meterNumerator = 4;
		meterDenominator = 4;
		primaryTempoBPM = 120;
		instrument = LotroInstrument.DEFAULT_INSTRUMENT;
		instrumentSet = false;
		dynamics = Dynamics.mf;
		compoundMeter = false;
        noteDivisorNum = -1;
        noteDivisorDenom = 1;
        calcPPQN();
	}

	/** A new file starts: its header (before its first X:) starts from the defaults. */
	public void newFile() {
		inFileHeader = true;
		key = KeySignature.C_MAJOR;
		keyAccidentals = new Integer[7];
		clefShift = 0;
		transposeShift = 0;
		octaveShift = 0;
		meterNumerator = 4;
		meterDenominator = 4;
		beatGroups = null;
		compoundMeter = false;
		noteDivisorNum = -1;
		noteDivisorDenom = 1;
		noteDivisorSetInHeader = false;
		instructions = new AbcInstructions();
		rhythmTempo = null;
		calcPPQN();
	}

	public void newPart(int partNumber) {
		this.partNumber = partNumber;
		instrument = LotroInstrument.DEFAULT_INSTRUMENT;
		instrumentSet = false;
		dynamics = Dynamics.mf;
		title = "";
		titleIsFromExtendedInfo = false;
		curPartTempoMap.clear();
		if (inFileHeader) {
			// The first X: of the file ends its header
			inFileHeader = false;
			fileKey = key;
			fileKeyAccidentals = keyAccidentals;
			fileClefShift = clefShift;
			fileTransposeShift = transposeShift;
			fileOctaveShift = octaveShift;
			fileMeterNumerator = meterNumerator;
			fileMeterDenominator = meterDenominator;
			fileBeatGroups = beatGroups;
			fileNoteDivisorNum = noteDivisorNum;
			fileNoteDivisorDenom = noteDivisorDenom;
			fileInstructions = instructions;
			fileRhythmTempo = rhythmTempo;
		}
		key = fileKey;
		keyAccidentals = fileKeyAccidentals.clone();
		clefShift = fileClefShift;
		transposeShift = fileTransposeShift;
		octaveShift = fileOctaveShift;
		meterNumerator = fileMeterNumerator;
		meterDenominator = fileMeterDenominator;
		beatGroups = fileBeatGroups;
		compoundMeter = isCompound(meterNumerator, beatGroups);
		noteDivisorNum = fileNoteDivisorNum;
		noteDivisorDenom = fileNoteDivisorDenom;
		noteDivisorSetInHeader = false;
		instructions = fileInstructions.copy();
		rhythmTempo = fileRhythmTempo;
		calcPPQN();
	}

	/** An R: field's value (ABC 2.1, 3.1.7): the tune's type sets the tempo of a song without Q: (endHeader). */
	public void setRhythm(String rhythm) {
		String tempo = RhythmTempo.of(AbcText.decode(rhythm));
		if (tempo != null)
			rhythmTempo = tempo;
	}

	/**
	 * An I: field's value (on its own line or inline): keeps I:linebreak and I:decoration, for this part from here on (or,
	 * in the file header, for every part).
	 *
	 * @return Whether it was one of those
	 */
	public boolean applyInstruction(String instruction) {
		return instructions.apply(instruction);
	}

	/** The I:linebreak and I:decoration instructions that apply here. */
	public AbcInstructions getInstructions() {
		return instructions;
	}

	/** Params.standard2011, and LotRO errors off. */
	public void setStandard2011(boolean standard2011) {
		this.standard2011 = standard2011;
	}

	/**
	 * How far a written accidental reaches, to the end of the bar: with standard2011, as I:propagate-accidentals says
	 * (ABC 2.1, 11.3; default: every octave). Else as LotRO plays it (tested, B66): the same note in the same octave.
	 */
	public AbcInstructions.AccidentalScope getAccidentalScope() {
		return standard2011 ? instructions.getPropagateAccidentals() : AbcInstructions.AccidentalScope.OCTAVE;
	}

	public void setTitle(String title, boolean fromExtendedInfo) {
		if (fromExtendedInfo || !titleIsFromExtendedInfo) {
			this.title = title;
			titleIsFromExtendedInfo = fromExtendedInfo;
		}
	}

	/** Words after the key in K: that aren't supported (an unknown word, explicit accidentals). */
	public static class KeyWordException extends IllegalArgumentException {
		public final String word; // From the first unsupported word to the end

		KeyWordException(String message, String word) {
			super(message);
			this.word = word;
		}
	}

	/** Clef words in K: (ABC 2.1, 4.6), e.g. bass, clef=treble-8, alto1. */
	private static final Pattern CLEF_PATTERN = Pattern.compile("(clef=)?(treble|alto|tenor|bass|perc|none)\\d?([+-]8)?");
	/** Mode words after the key: C maj, D mix, E dor ... (the first three letters count). */

	/**
	 * K: key [mode] [clef and transposition] (ABC 2.1, 3.1.14 and 4.6). The clef and middle= only change how the
	 * music is printed; transpose=, octave= and a clef with +8 or -8 change what is played (getTranspose). K:none and
	 * an empty K: have no key signature; K:HP and K:Hp are the highland pipes (F#, C#, G natural: like D). A K: with
	 * only a clef or transposition (K:bass, [K:octave=-1]) keeps the key, and each of clef, transpose= and octave=
	 * stays until a K: names it again.
	 * <p>
	 * Explicit accidentals (ABC 2.1, 3.1.14), only with standard2011: K:G ^c is G major with C sharp added (or changed,
	 * K:Dm =b); K:D exp _b has only the accidentals listed. Each is for its letter in every octave. Without
	 * standard2011 they're an error (LotRO refuses them, B23). A K: with a key starts from the key's accidentals again.
	 *
	 * @return What LotRO doesn't take: the words after the key and its mode, or none/HP/Hp; "" if nothing
	 */
	public String setKey(String str) {
		String[] words = str.trim().isEmpty() ? new String[0] : str.trim().split("\\s+");
		List<String> notForLotro = new ArrayList<>();
		String keyText = null;
		boolean explicitOnly = false; // exp
		Map<Integer, Integer> listed = new LinkedHashMap<>(); // Letter index (c d e f g a b) -> semitones
		for (int w = 0; w < words.length; w++) {
			String word = words[w];
			String lower = word.toLowerCase(Locale.ROOT);
			if (w == 0 && (lower.equals("none") || word.equals("HP") || word.equals("Hp"))) {
				keyText = lower.equals("none") ? "C" : "D";
				notForLotro.add(word);
			} else if (w == 0 && !lower.contains("=") && !CLEF_PATTERN.matcher(lower).matches()) {
				keyText = word;
			} else if (w == 1 && keyText != null && notForLotro.isEmpty() && KeyMode.parseMode(word) != null) {
				keyText += " " + word; // D mix
			} else if (lower.equals("exp") || lower.matches("[_^=].*")) {
				Matcher accidental = KEY_ACCIDENTAL_PATTERN.matcher(lower);
				if (!standard2011 || !(lower.equals("exp") || accidental.matches())) {
					throw new KeyWordException(UIText.get("common.abctomidi.key.explicit.accidentals", str),
							String.join(" ", Arrays.copyOfRange(words, w, words.length)));
				}
				if (lower.equals("exp")) {
					explicitOnly = true;
				} else {
					String sign = accidental.group(1);
					int semitones = sign.equals("=") ? 0 : sign.charAt(0) == '^' ? sign.length() : -sign.length();
					listed.put(LETTERS.indexOf(accidental.group(2)), semitones);
				}
			} else {
				notForLotro.add(word);
				Matcher clef = CLEF_PATTERN.matcher(lower);
				if (clef.matches()) {
					clefShift = (clef.group(3) == null) ? 0 : clef.group(3).startsWith("+") ? 12 : -12;
				} else if (lower.startsWith("transpose=") || lower.startsWith("t=")) {
					transposeShift = Integer.parseInt(lower.substring(lower.indexOf('=') + 1));
				} else if (lower.startsWith("octave=")) {
					octaveShift = 12 * Integer.parseInt(lower.substring(lower.indexOf('=') + 1));
				} else if (!lower.matches("(middle|m|stafflines|staffscale|style|cue|name|subname|sname|nm|snm)=.*")) {
					throw new KeyWordException(UIText.get("common.keysignature.invalid", str),
							String.join(" ", Arrays.copyOfRange(words, w, words.length)));
				}
			}
		}
		if (keyText != null)
			this.key = new KeySignature(keyText);
		else if (words.length == 0)
			this.key = KeySignature.C_MAJOR; // An empty K:
		if (keyText != null || words.length == 0 || explicitOnly || !listed.isEmpty()) {
			// A new key (or new accidentals) starts from the key's own; a K: with only a clef keeps them
			if (keyText != null || words.length == 0)
				keyAccidentals = new Integer[7];
			else
				keyAccidentals = keyAccidentals.clone();
			if (explicitOnly)
				Arrays.fill(keyAccidentals, 0);
			for (Map.Entry<Integer, Integer> entry : listed.entrySet())
				keyAccidentals[entry.getKey()] = entry.getValue();
		}
		return String.join(" ", notForLotro);
	}

	/** The letters of keyAccidentals, in order. */
	private static final String LETTERS = "cdefgab";
	/** An explicit accidental in K:, e.g. ^c, _B, =f, ^^f (lower case here). */
	private static final Pattern KEY_ACCIDENTAL_PATTERN = Pattern.compile("(\\^\\^|\\^|__|_|=)([a-g])");

	/**
	 * The key's accidental for a note without one, in semitones: an explicit accidental of K: (standard2011), else the
	 * key signature's.
	 *
	 * @param letter        The note's letter, either case
	 * @param naturalNoteId The note without accidental (as KeySignature.getDefaultAccidental takes it)
	 */
	public int getKeyAccidental(char letter, int naturalNoteId) {
		Integer explicit = keyAccidentals[LETTERS.indexOf(Character.toLowerCase(letter))];
		return (explicit != null) ? explicit : key.getDefaultAccidental(naturalNoteId).deltaNoteId;
	}

	/** Semitones to add to every note (K: transpose=, octave=, a clef with +8 or -8). */
	public int getTranspose() {
		return clefShift + transposeShift + octaveShift;
	}

	public void setNoteDivisor(String str) {
		parseNoteDivisor(str);
		noteDivisorSetInHeader = true;
		calcPPQN();
	}

    public int getLNum() {
        if (noteDivisorNum < 0) return 1;
        return noteDivisorNum;
    }

    public int getLDenom() {
        if (noteDivisorNum < 0) return (4 * meterNumerator / meterDenominator) < 3 ? 16 : 8;
        return noteDivisorDenom;
    }

	private void calcPPQN() {
        //if (getLDenom() <= 16)
        //    tickFactor = getLDenom();
        //else
            tickFactor = 16;//must be same for all parts
		this.ppqn = AbcToMidi.DEFAULT_NOTE_TICKS * tickFactor / this.meterDenominator;
        //System.out.println("Note divisor: 1/" + getLDenom() + " -> " + this.ppqn + " PPQ");
	}

    public int getTickFactor() {
        return tickFactor;
    }

	public float getWholeNoteTime() {
		if (this.noteDivisorNum > 0) {
			// long products: e.g. Q:1000 with L:1/2834674 overflowed int. Where the int products didn't overflow, the
			// float result is bit-identical (the LotRO float emulation depends on that).
			return ((long) this.meterDenominator * this.noteDivisorNum * 60.0f
					/ ((long) this.primaryTempoBPM * this.noteDivisorDenom));
		} else {
			float L = ((this.meterNumerator / (float)this.meterDenominator) < 0.75f ? 1f / 16 : 1f / 8);
			return (this.meterDenominator * L * 60.0f) / this.primaryTempoBPM;
		}
	}

	public void setMeter(String str, boolean inHeader) {
		str = str.trim();
		boolean wasCompound = isCompoundForTempo();
		int oldDenominator = meterDenominator;
		beatGroups = null;
		if (str.equals("C") || str.equalsIgnoreCase("none")) {
			// M:none is free meter (ABC 2.1, 3.1.6): no bars to keep, so the timing is that of 4/4 (default L:1/8)
			meterNumerator = 4;
			meterDenominator = 4;
		} else if (str.equals("C|")) {
			meterNumerator = 2;
			meterDenominator = 2;
		} else {
			String[] parts = str.split("[/:| ]");
			if (parts.length != 2) {
				throw new IllegalArgumentException(UIText.get("common.timesignature.invalid", str));
			}
			Matcher sum = METER_SUM.matcher(parts[0]);
			if (sum.matches()) {
				// M:2+2+3/8 and M:(2+2+3)/8: 7/8, played in beats of 2, 2 and 3 eighths
				String[] groups = sum.group(1).split("\\+");
				beatGroups = new int[groups.length];
				meterNumerator = 0;
				for (int g = 0; g < groups.length; g++) {
					beatGroups[g] = Integer.parseInt(groups[g]);
					meterNumerator += beatGroups[g];
				}
			} else {
				meterNumerator = Integer.parseInt(parts[0]);
			}
			meterDenominator = Integer.parseInt(parts[1]);
		}
		if (inHeader && !noteDivisorSetInHeader) {
			noteDivisorNum = -1;
			noteDivisorDenom = 1;
		}
		calcPPQN();
		this.compoundMeter = isCompound(meterNumerator, beatGroups);
		if (inHeader && (meterDenominator != oldDenominator || isCompoundForTempo() != wasCompound)) {
			// ABC 2.1 lets M: come after Q: in a header: work the tempo out again for this meter
			retempo();
		}
	}

	/** Works the tempo out again from the last Q:, after a header field that changes it (M: or L: after Q:). */
	private void retempo() {
		int bpm = toMeterBeats(tempoBeat, tempoBeatsPerMinute);
		if (bpm != primaryTempoBPM) {
			if (Integer.valueOf(primaryTempoBPM).equals(curPartTempoMap.get(0L)))
				curPartTempoMap.put(0L, bpm);
			if (!allPartsTempoFixed && Integer.valueOf(primaryTempoBPM).equals(allPartsTempoMap.get(0L)))
				allPartsTempoMap.put(0L, bpm);
			primaryTempoBPM = bpm;
		}
	}

	/** The bar's length as a fraction of a whole note: getBarNumerator() / getBarDenominator() (M:6/8 gives 6/8). */
	public int getBarNumerator() {
		return meterNumerator;
	}

	public int getBarDenominator() {
		return meterDenominator;
	}

	public TimeSignature getMeter() {
		try {
			return new TimeSignature(meterNumerator, meterDenominator);
		} catch (IllegalArgumentException e) {
			return TimeSignature.FOUR_FOUR;
		}
	}

	/**
	 * Tempo words (Q:"Allegro"), each at a typical beats per minute. ABC 2.1 (3.1.8) allows a text without a tempo but
	 * gives no values.
	 */
	private static final Map<String, Integer> TEMPO_WORDS = Map.ofEntries(Map.entry("larghissimo", 24),
			Map.entry("grave", 40), Map.entry("largo", 50), Map.entry("lento", 55), Map.entry("larghetto", 63),
			Map.entry("adagio", 70), Map.entry("adagietto", 75), Map.entry("andante", 90), Map.entry("andantino", 95),
			Map.entry("moderato", 110), Map.entry("allegretto", 115), Map.entry("allegro", 130),
			Map.entry("vivace", 165), Map.entry("presto", 180), Map.entry("prestissimo", 200));

	/** A tempo word's beat: a quarter, or a dotted quarter in 6/8 9/8 12/8 (per denominator). */
	private static final double FELT_BEAT = -1;
	/**
	 * The beat and tempo without Q: and without a known tune type in R: (RhythmTempo): a quarter in every meter,
	 * abc2midi's default (Q:1/4=120; ABC 2.1 gives none). In 6/8 that's 80 dotted quarters a minute.
	 */
	private static final double DEFAULT_BEAT = 1 / 4.0;
	private static final int DEFAULT_BEATS_PER_MINUTE = 120;
	/** The beat of Q:120 and Q:C=120 when the Q: note length counts: the unit note length, L: (ABC 2.1, 10.1). */
	private static final double UNIT_NOTE_BEAT = -2;

	/** Beats of the meter's denominator per minute: the tempo that is played (and written to the MIDI). */
	private int toMeterBeats(double beat, int beatsPerMinute) {
		if (!standardTempo || beat == 0)
			return beatsPerMinute;
		if (beat == FELT_BEAT)
			return isCompoundForTempo() ? 3 * beatsPerMinute : beatsPerMinute;
		if (beat == UNIT_NOTE_BEAT)
			beat = getLNum() / (double) getLDenom();
		return (int) Math.max(1, Math.round(beatsPerMinute * beat * meterDenominator));
	}

	/** 6/8 9/8 12/8 (and 6/4 ...): the felt beat is three of the denominator. 3/4 and 3/8 are not compound. */
	private boolean isCompoundForTempo() {
		return compoundMeter && meterNumerator > 3;
	}

	/** The numerator of a sum (ABC 2.1, 3.1.6): 2+2+3 or (2+2+3). */
	private static final Pattern METER_SUM = Pattern.compile("\\(?(\\d+(?:\\+\\d+)+)\\)?");

	/** Compound: the numerator a multiple of 3; with beat groups, all of 3 (3+3+2 isn't, 2+2+2+3 = 9 isn't). */
	private static boolean isCompound(int numerator, int[] groups) {
		if (groups == null)
			return numerator % 3 == 0;
		return groups.length > 1 && Arrays.stream(groups).allMatch(g -> g == 3);
	}

	/**
	 * The bar's beat groups, in notes of the meter's denominator, for a meter that isn't counted in equal beats: as
	 * written (M:2+2+3/8), or, for a numerator that neither 2 nor 3 divides (5, 7, 11, 13 ...), beats of 2 and a 3 at
	 * the end (7 = 2+2+3, as a rachenitsa; 5 = 2+3). Null for other meters, which are counted in equal beats.
	 */
	public int[] getBeatGroups() {
		if (beatGroups != null)
			return beatGroups.clone();
		int n = meterNumerator;
		if (n < 5 || n % 2 == 0 || n % 3 == 0)
			return null;
		int[] groups = new int[(n - 3) / 2 + 1];
		Arrays.fill(groups, 2);
		groups[groups.length - 1] = 3;
		return groups;
	}

	/**
	 * Q: (ABC 2.1, 3.1.8): [text] [beat[ beat...]=]bpm [text], e.g. Q:1/4=120, Q:3/8=120, Q:1/4 3/8=40, Q:"Allegro".
	 * Sets tempoBeat and tempoBeatsPerMinute.
	 *
	 * @param qField From a Q: field, where Q:120 and Q:C=120 count unit note lengths when the Q: note length counts
	 *               (ABC 2.1, 10.1); not Maestro's %%Q:, which counts the meter's beats
	 */
	private void parseTempo(String str, boolean qField) {
		// "Allegro" 1/4=120 or 1/4=120 "Allegro": the text goes; without a tempo, a tempo word sets it (else it stays)
		int quote = str.indexOf('"');
		if (quote >= 0) {
			int close = str.indexOf('"', quote + 1);
			String text = str.substring(quote + 1, close < 0 ? str.length() : close).trim().toLowerCase(Locale.ROOT);
			str = (str.substring(0, quote) + " " + (close < 0 ? "" : str.substring(close + 1))).trim();
			if (str.isEmpty()) {
				for (String word : text.split("[^\\p{L}]+")) {
					Integer bpm = TEMPO_WORDS.get(word);
					if (bpm != null) {
						tempoBeat = FELT_BEAT;
						tempoBeatsPerMinute = bpm;
						return;
					}
				}
				if (!tempoGiven)
					tempoBeat = FELT_BEAT; // An unknown word: the default tempo
				return;
			}
		}
		try {
			String[] parts = str.split("=");
			int bpm;
			double beat = 0;
			boolean unitNotes = qField && standardTempo;
			if (parts.length == 1) {
				bpm = Integer.parseInt(parts[0].trim());
				if (unitNotes)
					beat = UNIT_NOTE_BEAT; // Q:120
			} else if (parts.length == 2) {
				bpm = Integer.parseInt(parts[1].trim());
				beat = parseTempoBeat(parts[0]);
				if (unitNotes && parts[0].trim().equals("C"))
					beat = UNIT_NOTE_BEAT; // Q:C=120
			} else {
				throw new IllegalArgumentException(UIText.get("common.abctomidi.tempo.unreadable"));
			}

			if (bpm < 1 || bpm > 10000)
				throw new IllegalArgumentException(UIText.get("common.abctomidi.tempo.out.of.range", String.valueOf(bpm)));

			tempoBeat = beat;
			tempoBeatsPerMinute = bpm;
		} catch (NumberFormatException nfe) {
			throw new IllegalArgumentException(UIText.get("common.abctomidi.tempo.unreadable"));
		}
	}

	/**
	 * The beat of Q: as a fraction of a whole note: 1/4, 3/8, or several added up (1/4 3/8). 0 if it isn't note
	 * lengths (e.g. the old Q:C=120), which then counts as the meter's denominator, as before.
	 */
	private static double parseTempoBeat(String str) {
		double beat = 0;
		for (String length : str.trim().split("\\s+")) {
			String[] fraction = length.split("/");
			try {
				int numerator = Integer.parseInt(fraction[0]);
				int denominator = (fraction.length == 2) ? Integer.parseInt(fraction[1]) : 1;
				if (fraction.length > 2 || numerator < 1 || denominator < 1)
					return 0;
				beat += numerator / (double) denominator;
			} catch (NumberFormatException e) {
				return 0;
			}
		}
		return beat;
	}

	/** Notes play at their ABC 2.1 pitch and T: names no instrument (Params.standardPitch). */
	public void setStandardPitch(boolean standardPitch) {
		this.standardPitch = standardPitch;
	}

	public boolean isStandardPitch() {
		return standardPitch;
	}

	/** Q: follows ABC 2.1 (LotRO errors off): its note length is the beat. Else as in LotRO: the meter's denominator. */
	public void setStandardTempo(boolean standardTempo) {
		this.standardTempo = standardTempo;
	}

	/**
	 * The beat of the last Q:, as a fraction of a whole note; 0 for Q:120 (the meter's beat), negative for a tempo word
	 * (FELT_BEAT) or unit notes (UNIT_NOTE_BEAT).
	 */
	public double getTempoBeat() {
		return tempoBeat;
	}

	/** The beats per minute of the last Q:, as written. */
	public int getTempoBeatsPerMinute() {
		return tempoBeatsPerMinute;
	}

	/**
	 * A part's header ends (its first notes). Without any Q: so far, ABC 2.1 gives no tempo, when the Q: note length
	 * counts (as in LotRO otherwise: 120 of the meter's denominator): the first part's tune type in R: gives it
	 * (RhythmTempo), else abc2midi's, 120 quarters a minute in every meter. The song has one tempo: the other parts
	 * keep it. A Q:120 that counts unit notes takes the header's L:, also one after the Q:.
	 */
	public void endHeader() {
		if (!tempoGiven && standardTempo) {
			if (!allPartsTempoFixed) {
				if (rhythmTempo != null) {
					parseTempo(rhythmTempo, true);
				} else {
					tempoBeat = DEFAULT_BEAT;
					tempoBeatsPerMinute = DEFAULT_BEATS_PER_MINUTE;
				}
			}
			primaryTempoBPM = toMeterBeats(tempoBeat, tempoBeatsPerMinute);
			if (primaryTempoBPM != tempoBeatsPerMinute) {
				// The MIDI's default tempo is 120, so only another one needs a tempo event
				curPartTempoMap.putIfAbsent(0L, primaryTempoBPM);
				if (!allPartsTempoFixed)
					allPartsTempoMap.putIfAbsent(0L, primaryTempoBPM);
			}
		} else if (standardTempo && tempoBeat == UNIT_NOTE_BEAT) {
			// Q:120 counts unit notes: an L: after the Q: in the header counts too
			retempo();
		}
		allPartsTempoFixed = true;
	}

	public void setPrimaryTempoBPM(String str) {
		parseTempo(str, true);
		tempoGiven = true;
		this.primaryTempoBPM = toMeterBeats(tempoBeat, tempoBeatsPerMinute);
		if (!allPartsTempoMap.containsKey(0L))
			allPartsTempoMap.put(0L, this.primaryTempoBPM);
		if (!curPartTempoMap.containsKey(0L))
			curPartTempoMap.put(0L, this.primaryTempoBPM);
	}

	public void addTempoEvent(long tick, String str) {
		// %%Q: (Maestro's tempo changes). The written Q: stays what getTempoBeat() and endHeader() see.
		double beat = tempoBeat;
		int beatsPerMinute = tempoBeatsPerMinute;
			parseTempo(str, false);
		int bpm = toMeterBeats(tempoBeat, tempoBeatsPerMinute);
		tempoBeat = beat;
		tempoBeatsPerMinute = beatsPerMinute;
		allPartsTempoMap.put(tick, bpm);
		curPartTempoMap.put(tick, bpm);
	}

	public int getCurrentTempoBPM(long tick) {
		Entry<Long, Integer> entry = curPartTempoMap.floorEntry(tick);
		if (entry == null)
			return getPrimaryTempoBPM();

		return entry.getValue();
	}

	public NavigableMap<Long, Integer> getAllPartsTempoMap() {
		return allPartsTempoMap;
	}

	private double parseNoteDivisor(String str) {
		String[] parts = str.trim().split("[/:| ]");
		if (parts.length != 2) {
			throw new IllegalArgumentException(UIText.get("common.abctomidi.length.invalid", str));
		}
		int numerator = Integer.parseInt(parts[0]);
		int denominator = Integer.parseInt(parts[1]);

		if (numerator < 1) {
			throw new IllegalArgumentException(UIText.get("common.abctomidi.length.numerator.positive"));
		}
		if (denominator < 1) {
			throw new IllegalArgumentException(UIText.get("common.abctomidi.length.denominator.positive"));
		}

        this.noteDivisorNum = numerator;
        this.noteDivisorDenom = denominator;

		return numerator / (double) denominator;
	}

	public void setInstrument(LotroInstrument instrument, boolean definitive) {
		this.instrument = instrument;
		this.instrumentSet = true;
		this.instrumentSetHard = definitive;
	}

	public boolean isInstrumentSet() {
		return instrumentSet;
	}

	public boolean isInstrumentDefinitiveSet() {
		return instrumentSet && instrumentSetHard;
	}

	public void setDynamics(String str) {
		dynamics = Dynamics.valueOf(str);
	}

	public int getPartNumber() {
		return partNumber;
	}

	public String getTitle() {
		return title;
	}

	public KeySignature getKey() {
		return key;
	}

	public long getPpqn() {
		return ppqn;
	}

	/** The N in M:x/N, exactly as written (LotRO uses it for note lengths). */
	public int getMeterDenominator() {
		return meterDenominator;
	}

	public boolean isCompoundMeter() {
		return compoundMeter;
	}

	public int getPrimaryTempoBPM() {
		return primaryTempoBPM;
	}

	public LotroInstrument getInstrument() {
		return instrument;
	}

	public Dynamics getDynamics() {
		return dynamics;
	}
}