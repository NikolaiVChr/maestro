package com.digero.common.abctomidi;

import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;

import com.digero.common.abc.Dynamics;
import com.digero.common.abc.LotroInstrument;
import com.digero.common.midi.KeySignature;
import com.digero.common.midi.TimeSignature;

class TuneInfo {
	private int partNumber;
	private String title;
	private boolean titleIsFromExtendedInfo;
	private KeySignature key;
	private long ppqn;
	private int primaryTempoBPM;
	private final NavigableMap<Long, Integer> curPartTempoMap = new TreeMap<>(); // Tick -> BPM
	private final NavigableMap<Long, Integer> allPartsTempoMap = new TreeMap<>(); // Tick -> BPM
	private LotroInstrument instrument;
	private boolean instrumentSet;
	private boolean instrumentSetHard = false;
	private Dynamics dynamics;
	private boolean compoundMeter;
	private int meterNumerator;
	private int meterDenominator;
    private int noteDivisorNum;
    private int noteDivisorDenom;
    private int tickFactor = 16;

	// Tested in LotRO: every part starts from the K:, M: and L: of the file header (before the first X:), not from
	// the previous part. And as in ABC 2.1, an M: in a header without an L: in that header gives the default length.
	private boolean inFileHeader;
	private KeySignature fileKey;
	private int fileMeterNumerator;
	private int fileMeterDenominator;
	private int fileNoteDivisorNum;
	private int fileNoteDivisorDenom;
	private boolean noteDivisorSetInHeader; // An L: in the current header (the file's or the part's)

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
		meterNumerator = 4;
		meterDenominator = 4;
		compoundMeter = false;
		noteDivisorNum = -1;
		noteDivisorDenom = 1;
		noteDivisorSetInHeader = false;
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
			fileMeterNumerator = meterNumerator;
			fileMeterDenominator = meterDenominator;
			fileNoteDivisorNum = noteDivisorNum;
			fileNoteDivisorDenom = noteDivisorDenom;
		}
		key = fileKey;
		meterNumerator = fileMeterNumerator;
		meterDenominator = fileMeterDenominator;
		compoundMeter = (meterNumerator % 3) == 0;
		noteDivisorNum = fileNoteDivisorNum;
		noteDivisorDenom = fileNoteDivisorDenom;
		noteDivisorSetInHeader = false;
		calcPPQN();
	}

	public void setTitle(String title, boolean fromExtendedInfo) {
		if (fromExtendedInfo || !titleIsFromExtendedInfo) {
			this.title = title;
			titleIsFromExtendedInfo = fromExtendedInfo;
		}
	}

	public void setKey(String str) {
		this.key = new KeySignature(str);
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
				throw new IllegalArgumentException(
						"The string: \"" + str + "\" is not a valid time signature (expected format: 4/4)");
			}
			meterNumerator = Integer.parseInt(parts[0]);
			meterDenominator = Integer.parseInt(parts[1]);
		}
		if (inHeader && !noteDivisorSetInHeader) {
			noteDivisorNum = -1;
			noteDivisorDenom = 1;
		}
		calcPPQN();
		this.compoundMeter = (meterNumerator % 3) == 0;
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

	private int parseTempo(String str) {
		// "Allegro" 1/4=120 or 1/4=120 "Allegro": the text goes; without a tempo, a tempo word sets it (else it stays)
		int quote = str.indexOf('"');
		if (quote >= 0) {
			int close = str.indexOf('"', quote + 1);
			String text = str.substring(quote + 1, close < 0 ? str.length() : close).trim().toLowerCase(Locale.ROOT);
			str = (str.substring(0, quote) + " " + (close < 0 ? "" : str.substring(close + 1))).trim();
			if (str.isEmpty()) {
				for (String word : text.split("[^\\p{L}]+")) {
					Integer bpm = TEMPO_WORDS.get(word);
					if (bpm != null)
						return bpm;
				}
				return primaryTempoBPM;
			}
		}
		try {
			// Apparently LotRO ignores the tempo note length (e.g. Q: 1/4=120)
			String[] parts = str.split("=");
			int bpm;
			if (parts.length == 1) {
				bpm = Integer.parseInt(parts[0]);
			} else if (parts.length == 2) {
				bpm = Integer.parseInt(parts[1]);
			} else {
				throw new IllegalArgumentException("Unable to read tempo");
			}

			if (bpm < 1 || bpm > 10000)
				throw new IllegalArgumentException("Tempo \"" + bpm + "\" is out of range (expected 1-10000)");

			return bpm;
		} catch (NumberFormatException nfe) {
			throw new IllegalArgumentException("Unable to read tempo");
		}
	}

	public void setPrimaryTempoBPM(String str) {
		this.primaryTempoBPM = parseTempo(str);
		if (!allPartsTempoMap.containsKey(0L))
			allPartsTempoMap.put(0L, this.primaryTempoBPM);
		if (!curPartTempoMap.containsKey(0L))
			curPartTempoMap.put(0L, this.primaryTempoBPM);
	}

	public void addTempoEvent(long tick, String str) {
		allPartsTempoMap.put(tick, parseTempo(str));
		curPartTempoMap.put(tick, parseTempo(str));
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
			throw new IllegalArgumentException(
					"\"" + str + "\" is not a valid note length" + " (example of valid note length: 1/4)");
		}
		int numerator = Integer.parseInt(parts[0]);
		int denominator = Integer.parseInt(parts[1]);

		if (numerator < 1) {
			throw new IllegalArgumentException(
					"The numerator of the note length must be positive" + " (example of valid note length: 3/8)");
		}
		if (denominator < 1) {
			throw new IllegalArgumentException(
					"The denominator of the note length must be positive" + " (example of valid note length: 3/8)");
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