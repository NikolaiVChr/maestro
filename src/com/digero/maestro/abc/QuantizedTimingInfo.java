package com.digero.maestro.abc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.midi.IBarNumberCache;
import com.digero.common.midi.ITempoCache;
import com.digero.common.midi.TimeSignature;
import com.digero.common.util.Util;
import com.digero.maestro.midi.BentMidiNoteEvent;
import com.digero.maestro.midi.MidiNoteEvent;
import com.digero.maestro.midi.SequenceDataCache;
import com.digero.maestro.midi.SequenceDataCache.TempoEvent;
import com.digero.maestro.midi.SequenceInfo;
import com.digero.common.midi.MidiConstants;
import com.digero.common.midi.MidiUtils;

public class QuantizedTimingInfo implements ITempoCache, IBarNumberCache {
	// Tick => TimingInfoEvent
	private final NavigableMap<Long, TimingInfoEvent> timingInfoByTick = new TreeMap<>();
	private final NavigableMap<Long, TimingInfoEvent> timingInfoByTickOrganic = new TreeMap<>();
	private final HashMap<AbcPart, NavigableMap<Long, TimingInfoEvent>> oddTimingInfoByTick = new HashMap<>();

	private NavigableSet<Long> barStartTicks = null;
	private Long[] barStartTickByBar = null;
	private final long songLengthTicks;
	private final int tickResolution;

	private final int primaryTempoMPQ;
	//private final float exportTempoFactor;
	private int newTempo;
	private int origTempo;
	private final TimeSignature meter;
	private final boolean tripletTiming;
	private final boolean oddsAndEnds;
	private final int oddsAndEndsVersion;
	private String pctStr = "";
	private final boolean organic;
	public static final int COMBINE_PRIORITY_MULTIPLIER = 4;// Do not change this number without exposing the int in UI.
															// Since old projects will have 4 saved in msx.

	public QuantizedTimingInfo(SequenceInfo source, int newTempo, int origTempo, TimeSignature meter,
			boolean useTripletTiming, int abcSongBPM, AbcSong song, boolean oddsAndEnds, int mixVersion, boolean organic)
			throws AbcConversionException {

		double exportPrimaryTempoMPQ = TimingInfo.roundTempoMPQ(source.getPrimaryTempoMPQ()*origTempo/newTempo);
		this.primaryTempoMPQ = (int) Math.round(exportPrimaryTempoMPQ * newTempo/origTempo);
		this.newTempo = newTempo;
		this.origTempo = origTempo;
		this.meter = meter;
		this.organic = organic;
		this.tripletTiming = useTripletTiming;
		this.tickResolution = source.getDataCache().getTickResolution();
		this.songLengthTicks = source.getDataCache().getSongLengthTicks();

		final int resolution = source.getDataCache().getTickResolution();

		if (!organic) {
			TimingInfo defaultTiming = new TimingInfo(source.getPrimaryTempoMPQ(), resolution, newTempo, origTempo, meter,
					useTripletTiming, abcSongBPM, false);
			TimingInfo defaultOddTiming = new TimingInfo(source.getPrimaryTempoMPQ(), resolution, newTempo, origTempo, meter,
					!useTripletTiming, abcSongBPM, false);
			TimingInfoEvent defaultEvent = new TimingInfoEvent(0, 0, 0, defaultTiming, defaultOddTiming);
			timingInfoByTick.put(0L, defaultEvent);
		}
		
		TimingInfo defaultTimingOrg = new TimingInfo(source.getPrimaryTempoMPQ(), resolution, newTempo, origTempo, meter,
				false, abcSongBPM, true);
		TimingInfoEvent defaultEventOrg = new TimingInfoEvent(0, 0, 0, defaultTimingOrg, null);
		timingInfoByTickOrganic.put(0L, defaultEventOrg);
		
		// System.out.println("even"+defaultTiming.toString());
		// System.out.println("odd"+defaultOddTiming.toString());

		Collection<TimingInfoEvent> reversedEvents = timingInfoByTick.descendingMap().values();

		
		Collection<SequenceDataCache.TempoEvent> origTempos = source.getDataCache().getTempoEvents().values();

		/*
		 * Merge the tune editor tempo changes with midi tempos.
		 * Note that changes to tempo spinner is not applied at this stage, so the offsets and accelerandos
		 * from tune editor will also be subject to tempo spinner.
		 * This was an oversight, but for backward compat we keep it so for now. 
		 */
		NavigableMap<Long, Integer> changeTree = song.getTuneTempoChanges();
		ArrayList<SequenceDataCache.TempoEvent> combinedTempos = new ArrayList<>();

		for (SequenceDataCache.TempoEvent midiTempo : origTempos) {
			// Modify the orig midi tempos by tune editor amount
			long tick = midiTempo.tick;
			Entry<Long, Integer> midiEntry = changeTree.floorEntry(tick);
			if (midiEntry != null && midiEntry.getValue() != 0) {
				int newerTempo = (int) MidiUtils.convertTempo(
						Math.max(1.0d, MidiUtils.convertTempo(midiTempo.tempoMPQ) + midiEntry.getValue()));
				SequenceDataCache.TempoEvent te = source.getDataCache().getATempoEvent(newerTempo, midiTempo.tick,
						midiTempo.micros);
				combinedTempos.add(te);
			} else {
				combinedTempos.add(
						source.getDataCache().getATempoEvent(midiTempo.tempoMPQ, midiTempo.tick, midiTempo.micros));
			}
		}
		for (Entry<Long, Integer> tuneTempo : changeTree.entrySet()) {
			// Add in tune editor tempo changes where there was not a midi tempo change
			long tick = tuneTempo.getKey();
			SequenceDataCache.TempoEvent oldTempo = source.getDataCache().getTempoEvents().get(tick);
			if (oldTempo == null && tick < songLengthTicks) {
				Entry<Long, TempoEvent> prevTempo = source.getDataCache().getTempoEvents().floorEntry(tick);
				if (prevTempo != null) {
					int mpq = prevTempo.getValue().tempoMPQ;
					if (tuneTempo.getValue() != 0) {
						mpq = (int) MidiUtils
								.convertTempo(Math.max(1.0d, MidiUtils.convertTempo(mpq) + tuneTempo.getValue()));
					}
					SequenceDataCache.TempoEvent te = source.getDataCache().getATempoEvent(mpq, tick,
							prevTempo.getValue().micros);
					combinedTempos.add(te);
				} else {
					int mpq = MidiConstants.DEFAULT_TEMPO_MPQ;
					if (tuneTempo.getValue() != 0) {
						mpq = (int) MidiUtils
								.convertTempo(Math.max(1.0d, MidiUtils.convertTempo(mpq) + tuneTempo.getValue()));
					}
					SequenceDataCache.TempoEvent te = source.getDataCache().getATempoEvent(mpq, tick,
							SequenceDataCache.TempoEvent.DEFAULT_TEMPO.micros);
					combinedTempos.add(te);
				}
			}
		}
		Comparator<SequenceDataCache.TempoEvent> rator = (o1, o2) -> {
			if (o1.tick == o2.tick) {
				return 0;
			} else if (o1.tick > o2.tick) {
				return 1;
			}
			return -1;
		};
		combinedTempos.sort(rator);
		combinedTempos = calcNewMicros(combinedTempos);
		
		/*
		 * Go through the tempo events from the MIDI file and quantize them so each event starts at an integral multiple
		 * of the previous event's MinNoteLengthTicks. This ensures that we can split notes at each tempo change without
		 * creating a note that is shorter than MinNoteLengthTicks.
		 */
		LinkedList<SequenceDataCache.TempoEvent> linker = new LinkedList<>(combinedTempos);
		for (int index = 0; index < linker.size(); index++) {
			TempoEvent currMidiTempoEvent = linker.get(index);
			
			TimingInfo infoOrganic = new TimingInfo(currMidiTempoEvent.tempoMPQ, resolution, newTempo, origTempo, meter, false, abcSongBPM, true);
			TimingInfoEvent abcTempoEventOrganic = new TimingInfoEvent(currMidiTempoEvent.tick, currMidiTempoEvent.micros, 0, infoOrganic, null);
			timingInfoByTickOrganic.put(currMidiTempoEvent.tick, abcTempoEventOrganic);
			if (!organic) {
				long tick = currMidiTempoEvent.tick;
				long micros = 0L;
				double barNumber = 0;
				TimingInfo info = new TimingInfo(currMidiTempoEvent.tempoMPQ, resolution, newTempo, origTempo, meter,
						useTripletTiming, abcSongBPM, false);
				TimingInfo infoOdd = new TimingInfo(currMidiTempoEvent.tempoMPQ, resolution, newTempo, origTempo, meter,
						!useTripletTiming, abcSongBPM, false);
				
				//System.out.println("\nstarting "+info.getTempoBPM()+" tick="+sourceEvent.tick+"    min="+info.getMinNoteLengthTicks());
	
				
				// Iterate over the existing events in reverse order
				Iterator<TimingInfoEvent> reverseIterator = reversedEvents.iterator();
				inner:while (reverseIterator.hasNext()) {
					TimingInfoEvent prevMidiTempoEvent = reverseIterator.next();
					assert prevMidiTempoEvent.tick <= currMidiTempoEvent.tick;
					long gridUnitTicks = prevMidiTempoEvent.info.getMinNoteLengthTicks();
	
					// Quantize the tick length to the floor multiple of gridUnitTicks
					long lengthTicks = Util.floorGrid(tick - prevMidiTempoEvent.tick, gridUnitTicks);
	
					/*
					 * If the new event has a coarser timing grid than prev, then it's possible that the bar splits will not
					 * align to the grid. To avoid this, adjust the length so that the new event starts at a time that will
					 * allow the bar to land on the quantization grid.
					 * 
					 * Since Mix Timing do not depend on bars to be on the grid, Mix Timings happily skip this. 
					 */
					final double epsilon = TimingInfo.MIN_TEMPO_BPM / (2.0d * TimingInfo.MAX_TEMPO_BPM);//0.005				
					while (lengthTicks > 0L && !oddsAndEnds) {
						double barNumberTmp = prevMidiTempoEvent.barNumber + lengthTicks / ((double) prevMidiTempoEvent.info.getBarLengthTicks());
						double gridUnitsRemaining = ((Math.ceil(barNumberTmp) - barNumberTmp) * info.getBarLengthTicks())
								/ info.getMinNoteLengthTicks();
						
						if (Math.abs(gridUnitsRemaining - Math.round(gridUnitsRemaining)) <= epsilon)
							break; // Ok, the bar ends on the grid
	
						lengthTicks -= gridUnitTicks;
					}
	
					if (lengthTicks == 0L) {
						// The prev tempo event was quantized to zero-length; remove it
						if (oddsAndEnds || prevMidiTempoEvent.tick == 0L) {
							// Put the current event at prev events place, when we remove prev.
							tick = prevMidiTempoEvent.tick;
							// Be careful here. this line will make sure less events is removed,
							// as around places where grid goes from one size to another (like 24 to 48), removing
							// prev might make it not move, which will make it a target for next after
							// this and so on, which can cascade remove a whole string of events.
							// Also remember its floorGrid, not roundGrid.
						}
						//System.out.println(" GOTO inner. Removed old bpm "+prev.info.getTempoBPM()+" at tick="+prev.tick);
						reverseIterator.remove();
						continue inner;
					}
					assert lengthTicks >= gridUnitTicks;
					tick = prevMidiTempoEvent.tick + lengthTicks;
					micros = prevMidiTempoEvent.micros + MidiUtils.ticks2microsec(lengthTicks, prevMidiTempoEvent.info.getTempoMPQ(), resolution);
					barNumber = prevMidiTempoEvent.barNumber + lengthTicks / ((double) prevMidiTempoEvent.info.getBarLengthTicks());
					//System.out.println(lengthTicks+" GO ON. Adding bpm "+info.getTempoBPM()+" at tick="+tick+" next tick is="+(tick+info.getMinNoteLengthTicks())+" prev was "+prev.info.getTempoBPM()+" at prevtick="+prev.tick);
					break;
				}
	
				TimingInfoEvent abcTempoEvent = new TimingInfoEvent(tick, micros, barNumber, info, infoOdd);
				timingInfoByTick.put(tick, abcTempoEvent);
			}
		}
		long lastTick = -9999L;
		long lastMin = 0L;
		for (TimingInfoEvent tempo : timingInfoByTick.values()) {
			if (lastTick == -9999L) {
				lastTick = tempo.tick;
				lastMin = tempo.info.getMinNoteLengthTicks();
				continue;
			}
			long distance = tempo.tick - lastTick;
			assert distance % lastMin == 0L : "ASSERT FAIL: "+distance+" "+lastMin;
			assert distance > 0L : "ASSERT FAIL2: "+distance;
			lastTick = tempo.tick;
			lastMin = tempo.info.getMinNoteLengthTicks();
		}
		/*
		long lastM = 0;
		for (long i=0;i<5000000;i++) {
			TimingInfoEvent e = getTimingEventForTickOrganic(i);
			assert (long) (e.micros + MidiUtils.ticks2microsec(i - e.tick, e.info.getTempoMPQ(), e.info.getResolutionPPQ())) >= lastM:i;
			lastM=(long) (e.micros + MidiUtils.ticks2microsec(i - e.tick, e.info.getTempoMPQ(), e.info.getResolutionPPQ()));
		}
		System.out.println("PASS");
		*/
		
		
		int parts = song.getParts().size();
		this.oddsAndEnds = oddsAndEnds;
		this.oddsAndEndsVersion = mixVersion;
		if (!oddsAndEnds && !organic) {
			if (useTripletTiming) {
				pctStr  = "Mix Timing is off:\n 100% of song is swing/triplet timing.\n";
			} else {
				pctStr  = "Mix Timing is off:\n 0% of song is swing/triplet timing.\n";
			}
			return;
		}

		int startWeight = 12;// Note starts get high weight.
		int endingWeight = 4;// Note ends get medium weight. This must be divisable by endingSustainedWeightFactor
		int endingSustainedWeightFactor = 2;// Non-sustained notes, gets ending weight divided by this.
		int bendWeight = 1;// Pitch bends get the least weight.
		boolean noDrumEndingScore = false;// If drums note's endings should not get any weight at all.

		if (mixVersion == 1) { // currently the version is hardcoded to 2
			startWeight = 2;
			endingWeight = 1;
			endingSustainedWeightFactor = 1;
			bendWeight = 2;
			noDrumEndingScore = true;
		}

		// default means timing is to laid out like the tripletcheckbox selected grid.
		// odd means timing is to laid out the opposite of tripletcheckbox selected
		// grid.
		// System.err.println(" Odds And Ends:");
		int tracks = song.getSequenceInfo().getTrackCount();
		TimingInfoEvent[] timings = timingInfoByTick.values().toArray(new TimingInfoEvent[0]);
		
		// variables for collecting stats:
		Long[] partSwing = new Long[parts];
		Long[] partEven = new Long[parts];
		Long[] partNeutral = new Long[parts];
		Integer[] partSixgridsCount = new Integer[parts];
		pctStr  = "Mix Timing:\n";
				
		for (int part = 0; part < parts; part++) {
			// calculate for all parts
			AbcPart abcPart = song.getParts().get(part);
			
			partEven[part] = 0L;
			partSwing[part] = 0L;
			partNeutral[part] = 0L;
			partSixgridsCount[part] = 0;
			
			TreeMap<Long, TimingInfoEvent> partMap = new TreeMap<>();
			oddTimingInfoByTick.put(abcPart, partMap);

			// Lets us build an array of all notes in this part
			// Combine-priorities means some notes might be added several times.
			ArrayList<MidiNoteEvent> eventList = new ArrayList<>();
			for (int t = 0; t < tracks; t++) {
				if (abcPart.isTrackEnabled(t)) {
					int scoreMultiplier = (song.isPriorityActive() && abcPart.getEnabledTrackCount() > 1
							&& abcPart.isTrackPriority(t)) ? COMBINE_PRIORITY_MULTIPLIER : 1;
					if (abcPart.sectionsModified.get(t) == null && abcPart.nonSection.get(t) == null) {
						eventList.addAll(abcPart.getTrackEvents(t));
						for (MidiNoteEvent note : abcPart.getTrackEvents(t)) {
							note.combinePrioritiesScoreMultiplier = scoreMultiplier;
						}
					} else {
						for (MidiNoteEvent note : abcPart.getTrackEvents(t)) {
							if (abcPart.getAudible(t, note.getStartTick()) && abcPart.shouldPlay(note, t)) {
								note.combinePrioritiesScoreMultiplier = scoreMultiplier;
								eventList.add(note);
							}
						}
					}
				}
			}

			for (int j = 0; j < timings.length; j++) {
				// calculate for all tempochanges
				TimingInfoEvent tempoChange = timings[j];
				TimingInfoEvent nextTempoChange = null;
				if (j + 1 < timings.length) {
					nextTempoChange = timings[j + 1];
				}
				partMap.put(tempoChange.tick, tempoChange);
				
				// Now calculate duration of sixGrid sections. They will always end and start on
				// quantized grid for both odd and even timing
				// I call it sixGrid due to durations of 3 and 2 will always coincide each 6th
				// duration.
				// Its really just LCM (Least Common Multiple)
				long sixTicks = 0;
				boolean evenShortest = tempoChange.info.getMinNoteLengthTicks() < tempoChange.infoOdd
						.getMinNoteLengthTicks();
				int loopCount = 1;
				long longest = 0;
				long shortest = 0;
				if (evenShortest) {
					shortest = tempoChange.info.getMinNoteLengthTicks();
					longest = tempoChange.infoOdd.getMinNoteLengthTicks();
				} else {
					shortest = tempoChange.infoOdd.getMinNoteLengthTicks();
					longest = tempoChange.info.getMinNoteLengthTicks();
				}
				sixTicks = longest;
				while (sixTicks % shortest != 0 && loopCount < shortest) {
					sixTicks += longest;
					loopCount++;
				}

				assert sixTicks % tempoChange.info.getMinNoteLengthTicks() == 0;
				assert sixTicks % tempoChange.infoOdd.getMinNoteLengthTicks() == 0;

				// Max possible number of sixGrid before song ending +1
				int maxSixths = (int) ((this.songLengthTicks - tempoChange.tick + sixTicks) / sixTicks);

				ArrayList<Integer> sixGridsOdds = new ArrayList<>(maxSixths);
				for (int k = 0; k < maxSixths; k++) {
					sixGridsOdds.add(null);
				}

				int highest = -1;
				for (MidiNoteEvent ne : eventList) {
					int endingWeightFinal = endingWeight;
					if (!abcPart.getInstrument().sustainable) {
						endingWeightFinal /= endingSustainedWeightFactor;
					}

					int sixGridStart = -1;
					if (ne.getStartTick() > tempoChange.tick
							&& (nextTempoChange == null || ne.getStartTick() < nextTempoChange.tick)) {

						// Note starting scores
						// The note starts after current tempo change and either is last tempochange or
						// note starts before next tempo change
						long q = tempoChange.tick + Util.roundGrid(ne.getStartTick() - tempoChange.tick,
								tempoChange.info.getMinNoteLengthTicks());
						long qOdd = tempoChange.tick + Util.roundGrid(ne.getStartTick() - tempoChange.tick,
								tempoChange.infoOdd.getMinNoteLengthTicks());
						int odd = (int) (Math.abs(ne.getStartTick() - q) - Math.abs(ne.getStartTick() - qOdd));
						// determine which sixGrid we are in

						sixGridStart = (int) ((ne.getStartTick() - tempoChange.tick) / sixTicks);

						if (sixGridStart >= maxSixths)
							continue;
						if (sixGridStart > highest)
							highest = sixGridStart;

						// Add a point to this sixGrid odd vs. default list.
						int oddScoreStarts = odd * startWeight * ne.combinePrioritiesScoreMultiplier;
						if (sixGridsOdds.get(sixGridStart) != null) {
							sixGridsOdds.set(sixGridStart, sixGridsOdds.get(sixGridStart) + oddScoreStarts);
						} else {
							sixGridsOdds.set(sixGridStart, oddScoreStarts);
						}
					}

					if (ne instanceof BentMidiNoteEvent) {
						// bent notes scores (the bent notes which range is less than 1 octave (or as the setting is set to)
						BentMidiNoteEvent be = (BentMidiNoteEvent) ne;
						
						for (Entry<Long, Integer> bend : be.bends.entrySet()) {
							long tick = bend.getKey();
							if (tick == be.getStartTick())
								continue;
							if (tick > tempoChange.tick && (nextTempoChange == null || tick < nextTempoChange.tick)) {
								// The note starts after current tempo change and either is last tempochange or
								// note starts before next tempo change
								long q = tempoChange.tick + Util.roundGrid(tick - tempoChange.tick,
										tempoChange.info.getMinNoteLengthTicks());
								long qOdd = tempoChange.tick + Util.roundGrid(tick - tempoChange.tick,
										tempoChange.infoOdd.getMinNoteLengthTicks());
								int odd = (int) (Math.abs(tick - q) - Math.abs(tick - qOdd));

								// determine which sixGrid we are in
								int sixGrid = (int) ((tick - tempoChange.tick) / sixTicks);

								if (sixGrid >= maxSixths)
									continue;
								if (sixGrid > highest)
									highest = sixGrid;

								// Add a point to this sixGrid odd vs. default list.
								int oddScoreBends = odd * bendWeight * be.combinePrioritiesScoreMultiplier;
								if (sixGridsOdds.get(sixGrid) != null) {
									sixGridsOdds.set(sixGrid, sixGridsOdds.get(sixGrid) + oddScoreBends);
								} else {
									sixGridsOdds.set(sixGrid, oddScoreBends);
								}
							}
						}
					}

					if ((!noDrumEndingScore || !abcPart.getInstrument().equals(LotroInstrument.BASIC_DRUM))
							&& ne.getEndTick() > tempoChange.tick
							&& (nextTempoChange == null || ne.getEndTick() < nextTempoChange.tick)) {
						// Note ending scores
						// The note ends after current tempo change and either is last tempochange or
						// note ends before next tempo change
						long q = tempoChange.tick + Util.roundGrid(ne.getEndTick() - tempoChange.tick,
								tempoChange.info.getMinNoteLengthTicks());
						long qOdd = tempoChange.tick + Util.roundGrid(ne.getEndTick() - tempoChange.tick,
								tempoChange.infoOdd.getMinNoteLengthTicks());
						int odd = (int) (Math.abs(ne.getEndTick() - q) - Math.abs(ne.getEndTick() - qOdd));
						// determine which sixGrid we are in
						int sixGrid = (int) ((ne.getEndTick() - tempoChange.tick) / sixTicks);

						if (sixGrid >= maxSixths)
							continue;
						if (sixGrid > highest)
							highest = sixGrid;

						// Add points to this sixGrid odd vs. default list.
						int oddScoreEnds = odd * endingWeightFinal * ne.combinePrioritiesScoreMultiplier;
						if (sixGridsOdds.get(sixGrid) != null) {
							sixGridsOdds.set(sixGrid, sixGridsOdds.get(sixGrid) + oddScoreEnds);
						} else {
							sixGridsOdds.set(sixGrid, oddScoreEnds);
						}
						/*
						if (sixGridStart != -1) {
							// We populate all sixGridsOdds inbetween start and end with non-null values
							// this is needed for stats, so it does not count that duration as silence.
							for (int w = sixGridStart+1; w < sixGrid; w++) {
								if (sixGridsOdds.get(w) == null) {
									sixGridsOdds.set(w, 0);
								}
							}
						}
						*/
					}
				}
				boolean prevOdd = false;
				for (int i = 0; i <= highest; i++) {
					long tck = sixTicks * i; // how many ticks since last tempochange
					long micros = tempoChange.micros // how many micros absolute
							+ MidiUtils.ticks2microsec(tck, tempoChange.info.getTempoMPQ(), resolution);
					tck += tempoChange.tick;// absolute tick
					assert (nextTempoChange == null || tck < nextTempoChange.tick);
					
					long sixGridMicro = MidiUtils.ticks2microsec( // micro of single sixGrid
							sixTicks, tempoChange.info.getTempoMPQ(), tempoChange.info.getResolutionPPQ());

					
					if (sixGridsOdds.get(i) != null && sixGridsOdds.get(i) > 0
							&& (nextTempoChange == null || tck <= nextTempoChange.tick - sixTicks)) {
						
						if (useTripletTiming) {
							partEven[part] += sixGridMicro;
						} else if (!useTripletTiming) {
							partSwing[part] += sixGridMicro;
						}
						
						if (!prevOdd) {
							TimingInfoEvent newTempoChange = new TimingInfoEvent(tck, micros, tempoChange.barNumber,
									tempoChange.infoOdd, null);
							partMap.remove(tck);
							partMap.put(tck, newTempoChange);
						}
						prevOdd = true;
					} else {
						
						
						if (nextTempoChange != null && tck > nextTempoChange.tick - sixTicks && tck < nextTempoChange.tick) {
							// There is not room for an entire sixGrid, so we find how many tick there is room for.
							sixGridMicro = MidiUtils.ticks2microsec(
									nextTempoChange.tick - tck, tempoChange.info.getTempoMPQ(), tempoChange.info.getResolutionPPQ());
						} else if (nextTempoChange != null && tck >= nextTempoChange.tick) {
							// should never come here
							sixGridMicro = 0;
						}
						
						if (sixGridsOdds.get(i) != null) {
							// if it is null there is no note start/stops, so we do not count that
							if (sixGridsOdds.get(i) == 0) {
								partNeutral[part] += sixGridMicro;
							} else if (!useTripletTiming) {
								partEven[part] += sixGridMicro;
							} else if (useTripletTiming) {
								partSwing[part] += sixGridMicro;
							}
						}
						
						if (prevOdd) {
							TimingInfoEvent newTempoChange = new TimingInfoEvent(tck, micros, tempoChange.barNumber,
									tempoChange.info, null);
							partMap.putIfAbsent(tck, newTempoChange);
						}
						prevOdd = false;
					}
				}
				if (prevOdd) {
					int i = highest + 1;
					// Make sure tempo section ends with a default tempoevent.
					long tck = sixTicks * i;
					long micros = tempoChange.micros
							+ MidiUtils.ticks2microsec(tck, tempoChange.info.getTempoMPQ(), resolution);
					tck += tempoChange.tick;
					if (nextTempoChange == null || tck < nextTempoChange.tick) {
						TimingInfoEvent newTempoChange = new TimingInfoEvent(tck, micros, tempoChange.barNumber,
								tempoChange.info, null);
						partMap.put(tck, newTempoChange);
						
						if (nextTempoChange != null) {
							// highest might not be last before next tempochange, it might be last notestart or noteend
							// so we use math.min
							long sixGridMicro = Math.min(MidiUtils.ticks2microsec(sixTicks, tempoChange.info.getTempoMPQ(), resolution), nextTempoChange.micros - micros);
							if (!useTripletTiming) {
								partEven[part] += sixGridMicro;
							} else if (useTripletTiming) {
								partSwing[part] += sixGridMicro;
							}
						}
					}
				}
				for (Integer score : sixGridsOdds) {
					if (score != null) partSixgridsCount[part]++; 
				}
			}
			
			lastTick = -9999L;
			lastMin = 0L;
			for (TimingInfoEvent tempo : partMap.values()) {
				if (lastTick == -9999L) {
					lastTick = tempo.tick;
					lastMin = tempo.info.getMinNoteLengthTicks();
					continue;
				}
				long distance = tempo.tick - lastTick;
				assert distance % lastMin == 0L : "ASSERT FAIL3: "+distance+" "+lastMin;
				assert distance > 0L : "ASSERT FAIL4: "+distance;
				lastTick = tempo.tick;
				lastMin = tempo.info.getMinNoteLengthTicks();
			}
		}
		
		for (int v = 0; v < parts; v++) {
			pctStr  += "\nPart #"+song.getParts().get(v).getPartNumber()+" has "+partSixgridsCount[v]+" segments with note start/endings:\n";
			if (partNeutral[v]+partEven[v]+partSwing[v] > 0) {
				int pct = (int)((1000*partSwing[v]/(double)(partNeutral[v]+partEven[v]+partSwing[v]) )/10.0d );
				pctStr += pct+"% of those is swing/triplet timing.\n";
				pct = (int)((1000*partEven[v]/(double)(partNeutral[v]+partEven[v]+partSwing[v]) )/10.0d );
				pctStr += pct+"% of those is regular timing.\n";
				pct = (int)((1000*partNeutral[v]/(double)(partNeutral[v]+partEven[v]+partSwing[v]) )/10.0d );
				pctStr += pct+"% of those it didn't matter either way, so choosing "+(useTripletTiming?"swing.\n":"regular.\n");
			} else {
				pctStr += "no further stats to show.\n";
			}
		}
		pctStr += "\nNote that each segment is divided into smaller cells. A cell is minimum 60 ms\n";
		long totalSwing = 0;
		long totalEven = 0;
		long totalNeutral = 0;
		for (long swing : partSwing) {
			totalSwing += swing;
		}
		for (long even : partEven) {
			totalEven += even;
		}
		for (long neutral : partNeutral) {
			totalNeutral += neutral;
		}
		if (totalNeutral+totalEven+totalSwing > 0) {
			long totalNeutralSwing = useTripletTiming?totalNeutral:0L;
			int pct = (int)((1000*(totalSwing+totalNeutralSwing)/(double)(totalNeutral+totalEven+totalSwing) )/10.0d );
			pctStr  += "\n"+pct+"% in total is swing/triplet timing.\n";
			pct = (int)((1000*totalSwing/(double)(totalNeutral+totalEven+totalSwing) )/10.0d );
			if (pct > 50 && !useTripletTiming) {
				pctStr += "\nNote: Recommended to enable swing timing also.\n";
			} else if (pct < 40 && useTripletTiming) {
				pctStr += "\nNote: Recommended to disable swing timing.\n";
			}
		} else {
			pctStr += "No total stats to show.\n";
		}
	}

	/**
	 * Recalculate all the microseconds in the ABC timing events. This will modify the TempoEvents, so make sure that
	 * you do not send the original midi tempo events to this method.
	 * 
	 * @param combinedTempos Sorted list of tempo events
	 */
	private ArrayList<SequenceDataCache.TempoEvent> calcNewMicros(ArrayList<TempoEvent> combinedTempos) {
		ArrayList<SequenceDataCache.TempoEvent> combinedTemposNew = new ArrayList<>();
		int lastTempo = MidiConstants.DEFAULT_TEMPO_MPQ;
		long lastTick = 0L;
		long lastMicros = 0L;
		if (!combinedTempos.isEmpty()) {
			TempoEvent first = combinedTempos.get(0);
			assert first.tick == 0L;
			if (first.tick < 0L) {
				// since the first is going to have negative micros from start
				// those micros should be calced from its own tempo
				lastTempo = first.tempoMPQ;
				assert false:"tempo tick before zero";
			}
		}
		//NavigableMap<Long, TempoEvent> et = new TreeMap<>();
		for (TempoEvent event : combinedTempos) {
			if (event.tick == 0L) {
				//event.micros = 0L;
				lastTick = 0L;
				lastMicros = 0L; 
				lastTempo = event.tempoMPQ;
				TempoEvent evt = new TempoEvent(event.tempoMPQ,event.tick,0L);
				combinedTemposNew.add(evt);
		//		et.put(0L, evt);
				continue;
			}
			long newMicros = lastMicros + MidiUtils.ticks2microsec(event.tick - lastTick, lastTempo, tickResolution);
			assert newMicros > lastMicros;
			TempoEvent evt = new TempoEvent(event.tempoMPQ,event.tick,newMicros);
			//event.micros = newMicros;
			assert event.tick > lastTick;
			assert newMicros > lastMicros;
			lastTick = event.tick;
			lastMicros = newMicros;
			lastTempo = event.tempoMPQ;
			combinedTemposNew.add(evt);
		//	et.put(event.tick, evt);
		}
		/*
		long lastM = 0L;
		for (long i=0L;i<5000000L;i++) {
			TempoEvent e = et.floorEntry(i).getValue();
			long plus = MidiUtils.ticks2microsec(i - e.tick, e.tempoMPQ, tickResolution);
			//if ((i>118000 && i<119000) || i>138200) System.out.println(i+": "+e.micros+" ("+(e.tick)+" tick) -> "+((long) (e.micros + plus))+" plus="+plus);
			assert (long) (e.micros + plus) >= lastM:i;
			lastM=(long) (e.micros + MidiUtils.ticks2microsec(i - e.tick, e.tempoMPQ, tickResolution));
		}
		System.out.println("PASS calc");
		*/
		return combinedTemposNew;
	}

	/**
	 * 
	 * @return source main tempo MPQ
	 */
	public int getPrimaryTempoMPQ() {
		return primaryTempoMPQ;
	}

	/**
	 * 
	 * @return source main tempo BPM
	 */
	public int getPrimaryTempoBPM() {
		return (int) Math.round(MidiUtils.convertTempo(getPrimaryTempoMPQ()));
	}

	/**
	 * 
	 * @return ABC export main tempo MPQ
	 */
	public int getPrimaryExportTempoMPQ() {
		return Math.round(primaryTempoMPQ*origTempo/newTempo);
	}

	/**
	 * 
	 * @return export ABC main tempo BPM
	 */
	public int getPrimaryExportTempoBPM() {
		// TODO: should we not just return newTempo?
		return (int) Math.round(MidiUtils.convertTempo((double) primaryTempoMPQ *origTempo/newTempo));
	}

	public float getExportTempoFactord() {
		return newTempo/(float)origTempo;
	}
	
	public long divideByExportTempoFactor(long number) {
		return number*origTempo/newTempo;
	}
	
	public long multiplyByExportTempoFactor(long number) {
		return number*newTempo/origTempo;
	}

	public TimeSignature getMeter() {
		return meter;
	}

	public boolean isTripletTiming() {
		return tripletTiming;
	}

	public boolean isMixTiming() {
		return oddsAndEnds;
	}

	public int getMixVersion() {
		return oddsAndEndsVersion;
	}

	public TimingInfo getTimingInfo(long tick, AbcPart part) {
		return getTimingEventForTick(tick, part).info;
	}
	
	public TimingInfo getTimingInfoOrganic(long tick) {
		return getTimingEventForTickOrganic(tick).info;
	}

	public long quantize(long tick, AbcPart part) {
		TimingInfoEvent e = getTimingEventForTick(tick, part);
		long quan = e.tick + Util.roundGrid(tick - e.tick, e.info.getMinNoteLengthTicks());
		TimingInfoEvent e2 = getTimingEventForTick(quan, part);
		if (e2 != e) {
			// Was quantized into next tempo/mix-timing region, so we use that instead.
			long quan2 = e2.tick + Util.roundGrid(quan - e2.tick, e2.info.getMinNoteLengthTicks());
			if (quan2 != quan) System.out.println("Special quantization applied.");
			return quan2;
		}
		return quan;
	}
	
	public long quantizeUp(long tick) {
		// exportEndTick, not really needed for end, but lets do it for good measure.
		TimingInfoEvent e = getTimingEventForTick(tick);
		long quan  = e.tick + Util.ceilGrid(tick - e.tick, e.info.getMinNoteLengthTicks());
		if (!oddsAndEnds) return quan;
		long quan2 = e.tick + Util.ceilGrid(quan - e.tick, e.infoOdd.getMinNoteLengthTicks());
		int counter = 0;
		while (quan != quan2 && counter < 1000) {
			quan  += e.info.getMinNoteLengthTicks();
			quan2 = e.tick + Util.ceilGrid(quan - e.tick, e.infoOdd.getMinNoteLengthTicks());
			counter++;
		}
		return quan;
	}
	
	public long quantizeDown(long tick) {
		// exportStartTick, this is a double check to ensure we are on both grids for startTick.
		TimingInfoEvent e = getTimingEventForTick(tick);
		long quan  = e.tick + Util.floorGrid(tick - e.tick, e.info.getMinNoteLengthTicks());
		if (!oddsAndEnds) return quan;
		long quan2 = e.tick + Util.floorGrid(quan - e.tick, e.infoOdd.getMinNoteLengthTicks());
		while (quan != quan2 && quan > 0L) {
			quan  -= e.info.getMinNoteLengthTicks();
			quan2 = e.tick + Util.floorGrid(quan - e.tick, e.infoOdd.getMinNoteLengthTicks());
		}
		return quan;
	}

	/**
	 * Microseconds to tick. Does not take export tempo change into consideration.
	 */
	@Override
	public long tickToMicros(long tick) {
		TimingInfoEvent e = getTimingEventForTick(tick);
		return e.micros + MidiUtils.ticks2microsec(tick - e.tick, e.info.getTempoMPQ(), e.info.getResolutionPPQ());
	}

	/**
	 * Tick to microseconds. Does not take export tempo change into consideration.
	 */
	@Override
	public long microsToTick(long micros) {
		TimingInfoEvent e = getTimingEventForMicros(micros);
		return e.tick + MidiUtils.microsec2ticks(micros - e.micros, e.info.getTempoMPQ(), e.info.getResolutionPPQ());
	}
	
	public long tickToMicrosOrganic(long tick) {
		TimingInfoEvent e = getTimingEventForTickOrganic(tick);
		return e.micros + MidiUtils.ticks2microsec(tick - e.tick, e.info.getTempoMPQ(), e.info.getResolutionPPQ());
	}

	public long microsToTickOrganic(long micros) {
		TimingInfoEvent e = getTimingEventForMicrosOrganic(micros);
		return e.tick + MidiUtils.microsec2ticks(micros - e.micros, e.info.getTempoMPQ(), e.info.getResolutionPPQ());
	}

	/**
	 * Microseconds to tick. Does take export tempo change into consideration. Returns micros in the ABC song.
	 */
	public long tickToMicrosABC(long tick) {
		if (newTempo == origTempo) return tickToMicros(tick);
		TimingInfoEvent e = getTimingEventForTick(tick);
		return (long) ((e.micros
				+ MidiUtils.ticks2microsec(tick - e.tick, e.info.getTempoMPQ(), e.info.getResolutionPPQ()))
				*origTempo/(long)newTempo);
	}
	
	public long tickToMicrosABCOrganic(long tick) {
		if (newTempo == origTempo) return tickToMicrosOrganic(tick); 
		TimingInfoEvent e = getTimingEventForTickOrganic(tick);
		return (long) ((e.micros
				+ MidiUtils.ticks2microsec(tick - e.tick, e.info.getTempoMPQ(), e.info.getResolutionPPQ()))
				*origTempo/(long)newTempo);
	}
	
	public void tickToMicrosABCOrganic2(long tick1, long tick2) {
		long lastM = 0;
		for (long i=0;i<5000000;i++) {
			TimingInfoEvent e = getTimingEventForTickOrganic(i);
			assert (long) (e.micros + MidiUtils.ticks2microsec(i - e.tick, e.info.getTempoMPQ(), e.info.getResolutionPPQ())) >= lastM:i;
			lastM = (long) (e.micros + MidiUtils.ticks2microsec(i - e.tick, e.info.getTempoMPQ(), e.info.getResolutionPPQ()));
		}
		System.out.println("PASS2");
		TimingInfoEvent e1 = getTimingEventForTickOrganic(tick1);
		TimingInfoEvent e2 = getTimingEventForTickOrganic(tick2);
		assert getTimingEventForTickOrganic(tick1).tick == getTimingEventForTickOrganic2(tick1).getKey();
		assert getTimingEventForTickOrganic(tick2).tick == getTimingEventForTickOrganic2(tick2).getKey(); 
		System.out.println("asking for tick "+tick1+" and "+tick2);
		System.out.println(e1);
		System.out.println(e2);
		System.out.println("bpm: "+MidiUtils.convertTempo(e1.info.getTempoMPQ())+" "+MidiUtils.convertTempo(e2.info.getTempoMPQ()));
		System.out.println("mpq="+e1.info.getTempoMPQ());
		assert tick1 < tick2;
		assert e1.tick < e2.tick;
		long e2Micros = e1.micros + MidiUtils.ticks2microsec(e2.tick - e1.tick, e1.info.getTempoMPQ(), e1.info.getResolutionPPQ());
		assert e2.micros == e2Micros:e2.micros +"!="+ e2Micros;
	}
	
	public long tickToMicros(long tick, AbcPart part) {
		TimingInfoEvent e = getTimingEventForTick(tick, part);
		return (long) ((e.micros
				+ MidiUtils.ticks2microsec(tick - e.tick, e.info.getTempoMPQ(), e.info.getResolutionPPQ())));
	}
	
	/**
	 * Microseconds to tick. Does take export tempo change into consideration. Returns micros in the ABC song.
	 * Used only by PolyphonyHistogram
	 */
	public long tickToMicrosABC(long tick, AbcPart part) {
		if (newTempo == origTempo) return tickToMicros(tick, part);
		TimingInfoEvent e = getTimingEventForTick(tick, part);
		return (long) ((e.micros
				+ MidiUtils.ticks2microsec(tick - e.tick, e.info.getTempoMPQ(), e.info.getResolutionPPQ()))
				*origTempo/(long)newTempo);
	}

	/**
	 * Tick to microseconds. Does take export tempo change into consideration. The micro is in the ABC song.
	 */
	public long microsToTickABC(long micros) {
		if (newTempo == origTempo) return microsToTick(micros); 
		micros = micros * newTempo/(long)origTempo;
		TimingInfoEvent e = getTimingEventForMicros(micros);
		return e.tick + MidiUtils.microsec2ticks(micros - e.micros, e.info.getTempoMPQ(), e.info.getResolutionPPQ());
	}
	
	public long microsToTickABCOrganic(long micros) {
		if (newTempo == origTempo) return microsToTickOrganic(micros);
		micros = micros * newTempo/(long)origTempo;
		TimingInfoEvent e = getTimingEventForMicrosOrganic(micros);
		return e.tick + MidiUtils.microsec2ticks(micros - e.micros, e.info.getTempoMPQ(), e.info.getResolutionPPQ());
	}
	
	@Override
	public int tickToBarNumber(long tick) {
		// zero based
		if (organic) return tickToBarNumberOrganic(tick);
		TimingInfoEvent e = getTimingEventForTick(tick);
		return (int) Math.floor(e.barNumber + (tick - e.tick) / ((double) e.info.getBarLengthTicks()));
	}
	
	public int tickToBarNumberOrganic(long tick) {
		TimingInfoEvent e = getTimingEventForTickOrganic(tick);
		return (int) (tick / e.info.getBarLengthTicks());
	}

	@Override
	public long getBarToTick(int bar) {
		assert false : "Use another method for this, this one should never be called";
		return 0L;
	}

	public long tickToBarStartTick(long tick) {
		if (barStartTicks == null)
			calcBarStarts();

		if (tick <= barStartTicks.last())
			return barStartTicks.floor(tick);

		return barNumberToBarStartTick(tickToBarNumber(tick));
	}

	public long tickToBarEndTick(long tick) {
		if (barStartTicks == null)
			calcBarStarts();

		Long endTick = barStartTicks.higher(tick);
		if (endTick != null)
			return endTick;

		return barNumberToBarEndTick(tickToBarNumber(tick));
	}
	
	public long tickToBarStartTickOrganic(long tick) {
		TimingInfoEvent e = timingInfoByTickOrganic.lastEntry().getValue();
		return (tick/e.info.getBarLengthTicks())*e.info.getBarLengthTicks();
	}

	public long tickToBarEndTickOrganic(long tick) {
		TimingInfoEvent e = timingInfoByTickOrganic.lastEntry().getValue();
		return (1 + tick/e.info.getBarLengthTicks())*e.info.getBarLengthTicks();
	}

	public long barNumberToBarStartTick(int barNumber) {
		if (barStartTickByBar == null)
			calcBarStarts();

		if (barNumber < barStartTickByBar.length)
			return barStartTickByBar[barNumber];

		TimingInfoEvent e = timingInfoByTick.lastEntry().getValue();
		return e.tick + Math.round((barNumber - e.barNumber) * e.info.getBarLengthTicks());
	}
	
	public long barNumberToBarStartTickOrganic(int barNumber) {
		TimingInfoEvent e = timingInfoByTickOrganic.lastEntry().getValue();
		return barNumber * e.info.getBarLengthTicks();
	}

	public long barNumberToBarEndTick(int barNumber) {
		return barNumberToBarStartTick(barNumber + 1);
	}
	
	public long barNumberToBarEndTickOrganic(int barNumber) {
		return barNumberToBarStartTickOrganic(barNumber + 1);
	}

	public long barNumberToMicrosecond(int barNumber) {
		return tickToMicros(barNumberToBarStartTick(barNumber));
	}

	public int getMidiResolution() {
		return tickResolution;
	}

	private void calcBarStarts() {
		barStartTicks = new TreeSet<>();
		barStartTicks.add(0L);
		TimingInfoEvent prev = null;
		for (TimingInfoEvent event : timingInfoByTick.values()) {
			if (prev != null) {
				// Calculate the start time for all bars that start between prev and event
				long barStart = prev.tick
						+ Math.round((Math.ceil(prev.barNumber) - prev.barNumber) * prev.info.getBarLengthTicks());
				while (barStart < event.tick) {
					barStartTicks.add(barStart);
					barStart += prev.info.getBarLengthTicks();
				}
			}
			prev = event;
		}

		// Calculate bar starts for all bars after the last tempo change
		long barStart = prev.tick
				+ Math.round((Math.ceil(prev.barNumber) - prev.barNumber) * prev.info.getBarLengthTicks());
		while (barStart <= songLengthTicks) {
			barStartTicks.add(barStart);
			barStart += prev.info.getBarLengthTicks();
		}
		barStartTicks.add(barStart);

		barStartTickByBar = barStartTicks.toArray(new Long[0]);
	}
	
	TimingInfoEvent getTimingEventForTick(long tick, AbcPart part) {
		if (oddsAndEnds)
			return oddTimingInfoByTick.get(part).floorEntry(tick).getValue();
		return getTimingEventForTick(tick);
	}

	TimingInfoEvent getTimingEventForTick(long tick) {
		if (timingInfoByTick.floorEntry(tick) == null) {
			System.err.println("ERROR: Asking for timing event at tick "+tick);
		}
		return timingInfoByTick.floorEntry(tick).getValue();
	}
	
	TimingInfoEvent getTimingEventForTickOrganic(long tick) {
		if (timingInfoByTickOrganic.floorEntry(tick) == null) {
			System.err.println("ERROR: Asking for timing event at tick "+tick);
		}
		return timingInfoByTickOrganic.floorEntry(tick).getValue();
	}
	
	Entry<Long, TimingInfoEvent> getTimingEventForTickOrganic2(long tick) {
		if (timingInfoByTickOrganic.floorEntry(tick) == null) {
			System.err.println("ERROR: Asking for timing event at tick "+tick);
		}
		return timingInfoByTickOrganic.floorEntry(tick);
	}

	TimingInfoEvent getTimingEventForMicros(long micros) {
		TimingInfoEvent retVal = timingInfoByTick.firstEntry().getValue();
		for (TimingInfoEvent event : timingInfoByTick.values()) {
			if (event.micros > micros)
				break;

			retVal = event;
		}
		return retVal;
	}
	
	TimingInfoEvent getTimingEventForMicrosOrganic(long micros) {
		TimingInfoEvent retVal = timingInfoByTickOrganic.firstEntry().getValue();
		for (TimingInfoEvent event : timingInfoByTickOrganic.values()) {
			if (event.micros > micros)
				break;

			retVal = event;
		}
		return retVal;
	}

	TimingInfoEvent getNextTimingEvent(long tick, AbcPart part) {
		if (oddsAndEnds) {
			Map.Entry<Long, TimingInfoEvent> entry = oddTimingInfoByTick.get(part).higherEntry(tick);
			return (entry == null) ? null : entry.getValue();
		} else {
			Map.Entry<Long, TimingInfoEvent> entry = timingInfoByTick.higherEntry(tick);
			return (entry == null) ? null : entry.getValue();
		}
	}
	
	TimingInfoEvent getNextTimingEventOrganic(long tick) {
		Map.Entry<Long, TimingInfoEvent> entry = timingInfoByTickOrganic.higherEntry(tick);
		return (entry == null) ? null : entry.getValue();
	}

	NavigableMap<Long, TimingInfoEvent> getTimingInfoByTick() {
		return timingInfoByTick;
	}
	
	NavigableMap<Long, TimingInfoEvent> getTimingInfoByTickOrganic() {
		return timingInfoByTickOrganic;
	}

	public static class TimingInfoEvent {
		public final long tick;
		public final long micros;
		public final double barNumber; // May start in the middle of a bar

		public final TimingInfo info;
		public final TimingInfo infoOdd;

		public TimingInfoEvent(long tick, long micros, double barNumber, TimingInfo info, TimingInfo infoOdd) {
			this.tick = tick;
			this.micros = micros;
			this.barNumber = barNumber;
			this.info = info;
			this.infoOdd = infoOdd;
		}
		
		@Override
		public String toString() {
			return "Tick="+tick+" micros="+micros+" bar="+barNumber+(info!=null&&infoOdd!=null?" Info":(infoOdd==null&&info!=null?" InfoOdd":(info!=null?" Dual":" Faulty")));			
		}
	}

	public long getGridSizeTicks(long tick, AbcPart part) {
		return getTimingInfo(tick, part).getMinNoteLengthTicks();
	}

	public String getStats() {
		String out = "";
		
		out += pctStr+"\n";
		
		return out;
	}

	public String getTempoStats() {
		String out = "";
		
		out += "Source contains "+getTimingInfoByTick().size()+" tempo sections.\n";
		
		return out;
	}

	/**
	 * Only used by tempopanel
	 * @param thumbTick
	 * @return
	 */
	public int getAbcTempoMPQForTick(long thumbTick) {
		TimingInfoEvent entry;
		if (organic) {
			entry = getTimingEventForTickOrganic(thumbTick);
		} else {
			entry = getTimingEventForTick(thumbTick);
		}
		int mpq = 0;
		if (entry != null) {
			mpq = entry.info.getTempoMPQ();
		}
		return mpq;
	}

	public boolean isOrganic() {
		return organic;
	}
}
