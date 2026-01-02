package com.digero.maestro.abc;

import java.util.logging.Logger;

import com.digero.common.abc.AbcConstants;
import com.digero.common.midi.MidiUtils;
import com.digero.common.midi.TimeSignature;

public class TimingInfo {
	private static final Logger log = Logger.getLogger("export.timing");
	
	public static final long ONE_SECOND_MICROS = 1000000;
	public static final long ONE_MINUTE_MICROS = 60 * ONE_SECOND_MICROS;
	public static final long LONGEST_NOTE_MICROS = ONE_MINUTE_MICROS / 12;// reduced to 5s from 6s due to some samples
																			// are shorter than 6s.
	public static final int MAX_TEMPO_BPM = 10000;//(int) (ONE_MINUTE_MICROS / AbcConstants.getShortestNoteMicros(125));// Maximum 1000 bpm
																									// (In which case
																									// 'beat' here is
																									// 60ms)
	public static final int MIN_TEMPO_BPM = 5;/*(int) ((ONE_MINUTE_MICROS + (ONE_MINUTE_MICROS / 10) / 2)
			/ (ONE_MINUTE_MICROS / 10)); // Round up (1m
											// 3s)/6s = 10.5
											// -> 10  */

	private final int resolutionPPQ;

    private final int tempoMPQ;//MPQ for this event (source time, not abc export time)
	private final int newTempo;//newMainBPM
	private final int origTempo;//origMainBPM

	private final TimeSignature meter;

	private final int defaultDivisor;
	private final int minNoteDivisor;
	private final long minNoteLengthTicks;
	private final long maxNoteLengthTicks;
	private final boolean useTripletTiming;
	private final boolean organic;

    /**
     * A single tempo change.
     * This is the class that computes and keeps the grid for the following tempo section
     * for legacy and mix timings.
     *
     * It also computes the tempo BPM written in mix and legacy every 10 lines or so.
     *
     * TODO:
     *   Rename this to ABCTempoChange  (although it can also mean grid change in mix timings)
     *   Rename SequenceDataCache.TempoEvent to SequenceDataCache.MidiTempoEvent
     *   Rename QuantizedTimingInfo.TimingInfoEvent to QuantizedTimingInfo.AbcTempoEvent
     */
	TimingInfo(int tempoMPQ, int resolutionPPQ, int newTempo, int origTempo, TimeSignature meter, boolean useTripletTiming,
			boolean organic) throws AbcConversionException {

        // Compute the export ABC tempo
		double exportTempoMPQ = (double) tempoMPQ *origTempo/newTempo;

        // For backwards compat we still do this outside the condition.
        // For organic its only use is to determine if min and max
        // is exceeded.
        exportTempoMPQ = roundTempoMPQ(exportTempoMPQ);

		if (!organic) {
            // Round it to a whole-number BPM. By not doing this for organic outputs,
            // we basically allow its tempo changes to have floating point BPM.
            // So if the default main tempo is 60, and the user sets it to 61. And later is
            // a tempo change to 90, organic will effectively use 91.5 BPM instead of 92.

            // Adjust the tempoMPQ by however much we just rounded the export tempo
            tempoMPQ = (int) Math.round(exportTempoMPQ * newTempo/origTempo);
        }

		this.tempoMPQ = tempoMPQ;
		this.resolutionPPQ = resolutionPPQ;
		this.newTempo = newTempo;
		this.origTempo = origTempo;
		this.meter = meter;
		this.useTripletTiming = useTripletTiming;
		this.organic = organic;


        long minimalNoteMicros = AbcConstants.getShortestNoteMicros(newTempo);

		final int exportTempoBPM = (int) Math.round(MidiUtils.convertTempo(exportTempoMPQ));

		if (exportTempoBPM > MAX_TEMPO_BPM || exportTempoBPM < MIN_TEMPO_BPM) {
			throw new AbcConversionException("Tempo " + exportTempoBPM + " is out of range. Must be between "
					+ MIN_TEMPO_BPM + " and " + MAX_TEMPO_BPM + ".");
		}

		// From http://abcnotation.com/abc2mtex/abc.txt:
		// The default note length can be calculated by computing the meter as
		// a decimal; if it is less than 0.75 the default is a sixteenth note,
		// otherwise it is an eighth note. For example, 2/4 = 0.5, so the
		// default note length is a sixteenth note, while 4/4 = 1.0 or
		// 6/8 = 0.75, so the default is an eighth note.
		assert ((meter.numerator / (double) meter.denominator < 0.75) ? 16 : 8) * 4 % meter.denominator == 0;
		this.defaultDivisor = ((meter.numerator / (double) meter.denominator < 0.75) ? 16 : 8) * 4 / meter.denominator;

		if (organic) {
			this.minNoteLengthTicks = 1;
			this.minNoteDivisor = 1;
			this.maxNoteLengthTicks = 1;
			return;
		}

        final long SHORTEST_NOTE_TICKS = (long) Math.ceil((minimalNoteMicros * resolutionPPQ) / exportTempoMPQ);
        final long LONGEST_NOTE_TICKS = (long) Math.floor((LONGEST_NOTE_MICROS * resolutionPPQ) / exportTempoMPQ);
		
		// Calculate min note length
		{
			int minNoteDivisor = defaultDivisor;
			if (useTripletTiming)
				minNoteDivisor *= 3;
			
			int nu = 4 * resolutionPPQ;
			int de = minNoteDivisor;
			
			assert nu%de == 0 : nu+"/"+de+" default="+defaultDivisor;
			long minNoteTicks = nu/de;

			while (minNoteTicks < SHORTEST_NOTE_TICKS && minNoteDivisor % 2 == 0) {
				minNoteTicks *= 2;
				minNoteDivisor /= 2;
			}

			assert minNoteDivisor > 0;

			while (minNoteTicks >= SHORTEST_NOTE_TICKS * 2 && minNoteTicks % 2 == 0) {
				minNoteTicks /= 2;
				minNoteDivisor *= 2;
			}

			if (meter.denominator > minNoteDivisor) {
				if (minNoteDivisor == 0) throw new AbcConversionException("The tempo is too high."); 
				int maximumDenominator = (1 << TimeSignature.floorLog2(minNoteDivisor));
				throw new AbcConversionException(
						"The denominator of the meter must be maximum " + maximumDenominator + " at this tempo. Either reduce the tempo or reduce the denominator.");
			}

			this.minNoteLengthTicks = minNoteTicks;
			this.minNoteDivisor = minNoteDivisor;
			this.maxNoteLengthTicks = minNoteTicks * (LONGEST_NOTE_TICKS / minNoteTicks);
		}
	}
	
	/**
	 * Rounds the given MPQ tempo so it corresponds to a whole-number of beats per minute.
	 */
	public static double roundTempoMPQ(double tempoMPQ) {
		return MidiUtils.convertTempo(Math.round(MidiUtils.convertTempo(tempoMPQ)));
	}

	public int getTempoMPQ() {
		return tempoMPQ;
	}

	public int getTempoBPM() {
		return (int) Math.round(MidiUtils.convertTempo(tempoMPQ));
	}

	public int getResolutionPPQ() {
		return resolutionPPQ;
	}

    /**
     * Get abc export tempo for this event.
     * Used to make preview midi.
     */
	public int getExportTempoMPQ() {
		return tempoMPQ * origTempo/newTempo;
	}

	public int getExportTempoBPM() {
		return (int) Math.round(MidiUtils.convertTempo((double) tempoMPQ * origTempo/newTempo));
	}

	public TimeSignature getMeter() {
		return meter;
	}

	public int getDefaultDivisor() {
		return defaultDivisor;
	}

	public int getMinNoteDivisor() {
		return minNoteDivisor;
	}

	public long getMinNoteLengthTicks() {
		return minNoteLengthTicks;
	}

	public long getMaxNoteLengthTicks() {
		return maxNoteLengthTicks;
	}

	/*
	 * Used by ABC exporter and ABC preview bar label. 
	 * UI does not use this to draw bar lines. And not by section and tune editor to edit song.
	 * 
	 * Makes no guarantee for this duration to fit on this TimingInfo's quantization. (due to the division)
	 * 
	 */
	public long getBarLengthTicks() {
		if (organic) {
			return 4L * resolutionPPQ * meter.numerator / meter.denominator;
		} else {
			// for some songs this gives wrong result, but for most it works:
			return minNoteDivisor * minNoteLengthTicks * meter.numerator / meter.denominator;
		}
	}

	public boolean isUseTripletTiming() {
		return useTripletTiming;
	}

    @Override
	@SuppressWarnings("HardCodedStringLiteral")
    public String toString() {
        String str = "  TimingInfo:\n";
        str += "meter "+meter.toString() + "\n";
        str += "resolutionPPQ "+resolutionPPQ + "\n";
        str += "exportTempoFactor "+(newTempo/(float)origTempo) + "\n";
        str += "tempoMPQ "+tempoMPQ + " (source)\n";
        if (!organic) {
            str += "defaultDivisor " + defaultDivisor + "\n";
            str += "minNoteDivisor " + minNoteDivisor + "\n";
            str += "swing "+useTripletTiming + "\n";
            str += "minNoteLengthTicks "+minNoteLengthTicks + "\n";
            str += "tempoBPM "+MidiUtils.convertTempo(roundTempoMPQ((double) tempoMPQ *origTempo/newTempo));
            str += "minDuration "+ (MidiUtils.ticks2microsec(minNoteLengthTicks, tempoMPQ, resolutionPPQ)) *origTempo/ (newTempo*1000L) + " ms\n";
        } else {
            str += "tempoBPM "+MidiUtils.convertTempo((double) tempoMPQ *origTempo/newTempo);
        }
        return str;
    }
}
