package com.digero.maestro.abc;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;

import com.digero.common.abc.AbcConstants;
import com.digero.common.abc.AbcField;
import com.digero.common.abc.Dynamics;
import com.digero.common.abc.LotroInstrument;
import com.digero.common.abc.StringCleaner;
import com.digero.common.midi.ITempoCache;
import com.digero.common.midi.KeySignature;
import com.digero.common.midi.MidiConstants;
import com.digero.common.midi.MidiFactory;
import com.digero.common.midi.Note;
import com.digero.common.midi.PanGenerator;
import com.digero.common.util.Pair;
import com.digero.common.util.Util;
import com.digero.maestro.MaestroMain;
import com.digero.maestro.midi.BentNoteEvent;
import com.digero.maestro.midi.Chord;
import com.digero.maestro.midi.NoteEvent;
import com.digero.maestro.midi.TrackInfo;

public class AbcExporter {
	// Max parts for MIDI preview
	private static final int MAX_PARTS = 24;// MidiConstants.CHANNEL_COUNT - 1; // Channel 0 is no longer reserved for
											// metadata, and Track 9 is reserved for
											// drums
	private static final int MAX_RAID = 24; // Max number of parts that in any case can be played in lotro

	private final List<AbcPart> parts;
	private final AbcMetadataSource metadata;
	private QuantizedTimingInfo qtm;
	private KeySignature keySignature;

	private boolean skipSilenceAtStart;
	private boolean deleteMinimalNotes;
	// private boolean showPruned;
	private long exportStartTick;
	private long exportEndTick;
	private int lastChannelUsedInPreview = -1;
	private static final long PRE_TICK = -1L;

	public int stereoPan = 100;// zero is mono, 100 is very wide.
	private int firstBarNumber;

	public AbcExporter(List<AbcPart> parts, QuantizedTimingInfo timingInfo, KeySignature keySignature,
			AbcMetadataSource metadata) throws AbcConversionException {
		this.parts = parts;
		this.qtm = timingInfo;
		this.metadata = metadata;
		setKeySignature(keySignature);
		
		// We use this from AbcSong when getting micros
		Pair<Long, Long> startEndTick = getSongStartEndTick(true, false, false);
		exportStartTick = startEndTick.first;
		exportEndTick = startEndTick.second;
	}

	public List<AbcPart> getParts() {
		return parts;
	}

	public QuantizedTimingInfo getTimingInfo() {
		return qtm;
	}

	public void setTimingInfo(QuantizedTimingInfo timingInfo) {
		this.qtm = timingInfo;
	}

	public KeySignature getKeySignature() {
		return keySignature;
	}

	public void setKeySignature(KeySignature keySignature) throws AbcConversionException {
		if (keySignature.sharpsFlats != 0)
			throw new AbcConversionException("Only C major and A minor are currently supported");

		this.keySignature = keySignature;
	}

	public boolean isSkipSilenceAtStart() {
		return skipSilenceAtStart;
	}

	public void setSkipSilenceAtStart(boolean skipSilenceAtStart) {
		this.skipSilenceAtStart = skipSilenceAtStart;
	}
	
	public boolean isDeleteMinimalNotes() {
		return deleteMinimalNotes;
	}

	public void setDeleteMinimalNotes(boolean deleteMinimalNotes) {
		this.deleteMinimalNotes = deleteMinimalNotes;
	}

	/*
	 * public boolean isShowPruned() { return showPruned; }
	 * 
	 * public void setShowPruned(boolean showPruned) { this.showPruned = showPruned; }
	 */

	public AbcMetadataSource getMetadataSource() {
		return metadata;
	}

	public long getExportStartTick() {
		return exportStartTick;
	}

	public long getExportEndTick() {
		return exportEndTick;
	}

	/**
	 * Does not account for tempo adjustment
	 * @return 
	 */
	public long getExportStartMicros() {
		return qtm.tickToMicros(getExportStartTick());
	}

	/**
	 * Does not account for tempo adjustment
	 * @return 
	 */
	public long getExportEndMicros() {
		return qtm.tickToMicros(getExportEndTick());
	}

	public static class ExportTrackInfo {
		public final int trackNumber;
		public final AbcPart part;
		public final List<NoteEvent> noteEvents;
		public final Integer channel;
		public final Integer patch;

		public ExportTrackInfo(int trackNumber, AbcPart part, List<NoteEvent> noteEvents, Integer channel, int patch) {
			this.trackNumber = trackNumber;
			this.part = part;
			this.noteEvents = noteEvents;
			this.channel = channel;
			this.patch = patch;
		}
	}

	public Pair<List<ExportTrackInfo>, Sequence> exportToPreview(boolean useLotroInstruments)
			throws AbcConversionException, InvalidMidiDataException {
		try {

			Pair<Long, Long> startEndTick = getSongStartEndTick(true /* lengthenToBar */,
					true /* accountForSustain */,false);
			exportStartTick = startEndTick.first;
			exportEndTick = startEndTick.second;

			Map<AbcPart, List<Chord>> chordsMade = new HashMap<>();// abcexported chords ready to be previewed
			Map<AbcPart, TreeMap<Long, Boolean>> toneMap = null;// Tree is when notes are active.
			List<Set<AbcPart>> shareChannelWithPatchChangesMap = null;// abcpart 1 can borrow channel from abcpart 2,
																		// but need to switch voice all the time.
			Map<AbcPart, Integer> shareChannelSameVoiceMap = null;// abcpart can use a certain channel (together with
																	// another abcpart), no need for voice switching.
			Set<AbcPart> assignedSharingPartsSwitchers = new HashSet<>();// Set of all parts that will share using
																			// switching.
			Set<AbcPart> assignedSharingPartsSameVoice = new HashSet<>();// Set of all parts that will share without
																			// using switching.
			List<ExportTrackInfo> infoList = new ArrayList<>();
			
			int partsCount = calculatePartsCount(parts);
			if (partsCount == 0) {
				// The point of this is to return a 'null' sequence. That prevents midi sequencer from restarting when changing
				// tempo spinner while no parts are enabled, due to setting a abc sequence.
				for (AbcPart part : parts) {
					try {
						PolyphonyHistogram.count(part, new ArrayList<>());
					} catch (IOException e) {
						throw new AbcConversionException("Failed to read instrument sample durations.", e);
					}
				}
				return new Pair<>(infoList, new Sequence(Sequence.PPQ, 96));
			}
			if (parts.size() > MAX_RAID) {
				throw new AbcConversionException("Songs with more than " + MAX_RAID + " parts can never be previewed.\n"
						+ "This song currently has " + parts.size() + " parts and failed to preview.");
			}
			exportForChords(chordsMade, exportStartTick, exportEndTick);// export the chords here early, as we possibly
																		// need to process them for sharing.
			if (partsCount > MAX_PARTS) {
				int target = partsCount - MAX_PARTS;// How many channels we need to free up.
				// System.out.println("\n\nPreview requested for more than 15 parts. Starting
				// combine algorithms.");
				Pair<Map<AbcPart, Integer>, Integer> shareResult = findSharableParts(target, chordsMade,
						assignedSharingPartsSwitchers);
				if (shareResult != null) {
					shareChannelSameVoiceMap = shareResult.first;
					assignedSharingPartsSameVoice = shareChannelSameVoiceMap.keySet();
					target = shareResult.second;
				}

				if (target > 0) {
					toneMap = findTones(chordsMade);
					Triplet<List<Set<AbcPart>>, Set<AbcPart>, Integer> switchResult = null;
					switchResult = findSharableChannelSwitchers(toneMap, target, assignedSharingPartsSameVoice);
					shareChannelWithPatchChangesMap = switchResult.first;
					assignedSharingPartsSwitchers = switchResult.second;
					target = switchResult.third;
				}
				if (target > 0) {
					// That didn't work, lets try the opposite order
					// System.out.println("\nThat did not free up enough channels, trying the
					// methods in opposite order.");
					target = partsCount - MAX_PARTS;// How many channels we need to free up.
					shareChannelSameVoiceMap = null;
					assignedSharingPartsSameVoice = new HashSet<>();
					Triplet<List<Set<AbcPart>>, Set<AbcPart>, Integer> switchResult = null;
					switchResult = findSharableChannelSwitchers(toneMap, target, assignedSharingPartsSameVoice);
					shareChannelWithPatchChangesMap = switchResult.first;
					assignedSharingPartsSwitchers = switchResult.second;
					target = switchResult.third;

					if (target > 0) {
						shareResult = findSharableParts(target, chordsMade, assignedSharingPartsSwitchers);
						if (shareResult != null) {
							shareChannelSameVoiceMap = shareResult.first;
							assignedSharingPartsSameVoice = shareChannelSameVoiceMap.keySet();
							target = shareResult.second;
						}
					}
				}
				if (target > 0) {
					throw new AbcConversionException("Songs with more than " + MAX_PARTS
							+ " parts can sometimes be previewed.\n" + "This song currently has " + partsCount
							+ " active parts and failed to preview though.");
				}
			}

			Set<Integer> assignedChannels = new HashSet<>();// channels that has been assigned one or two parts onto it.

			Sequence sequence = new Sequence(Sequence.PPQ, qtm.getMidiResolution());

			// Track 0: Title and meta info
			Track track0 = sequence.createTrack();
			track0.add(MidiFactory.createTrackNameEvent(metadata.getSongTitle()));
			addMidiTempoEvents(track0);

			PanGenerator panner = new PanGenerator();
			lastChannelUsedInPreview = -1;
			
			for (AbcPart part : assignedSharingPartsSameVoice) {
				// Do the parts that is sharing channel first, as they will use the lower
				// (already designated) numbered channels
				int pan = (parts.size() > 1) ? panner.get(part.getInstrument(), part.getTitle(), stereoPan)
						: PanGenerator.CENTER;
				int chan = shareChannelSameVoiceMap.get(part);
				ExportTrackInfo inf = exportPartToPreview(part, sequence, exportStartTick, exportEndTick, pan,
						useLotroInstruments, assignedChannels, chan, false, chordsMade);
				infoList.add(inf);
				// System.out.println(part.getTitle()+" assigned to share channel
				// "+inf.channel+" on track "+inf.trackNumber);
				assignedChannels.add(inf.channel);
			}
			if (shareChannelWithPatchChangesMap != null) {
				// Do the parts that is sharing channel with voice switching second, as they
				// will use the medium numbered channels
				for (Set<AbcPart> entry : shareChannelWithPatchChangesMap) {
					int chan = lastChannelUsedInPreview + 1;
					int pan = -100000;
					for (AbcPart part : entry) {
						if (pan == -100000) {
							pan = (parts.size() > 1) ? panner.get(part.getInstrument(), part.getTitle(), stereoPan)
									: PanGenerator.CENTER;
						}
						ExportTrackInfo inf = exportPartToPreview(part, sequence, exportStartTick, exportEndTick, pan,
								useLotroInstruments, assignedChannels, chan, true, chordsMade);
						infoList.add(inf);
						// System.out.println(part.getTitle()+" assigned to switch channel
						// "+inf.channel+" on track "+inf.trackNumber);
						assignedChannels.add(inf.channel);
					}
				}
			}
			for (AbcPart part : parts) {
				// Now do the rest of the parts that is not sharing channels at all. They will
				// use 1 channel each.
				if (part.getEnabledTrackCount() > 0 && !assignedSharingPartsSameVoice.contains(part)
						&& !assignedSharingPartsSwitchers.contains(part)) {
					int pan = (parts.size() > 1) ? panner.get(part.getInstrument(), part.getTitle(), stereoPan)
							: PanGenerator.CENTER;
					ExportTrackInfo inf = exportPartToPreview(part, sequence, exportStartTick, exportEndTick, pan,
							useLotroInstruments, assignedChannels, null, false, chordsMade);
					infoList.add(inf);
					assignedChannels.add(inf.channel);
					// System.out.println(part.getTitle()+" assigned to channel "+inf.channel+" on track
					// "+inf.trackNumber);
					/*
					 * if (exportStartTick > 0) { track0.add(MidiFactory.createNoteOnEventEx(40,inf.channel,100,0L));
					 * track0.add(MidiFactory.createNoteOffEventEx(40,inf.channel,0,100L)); }
					 */
				}
			}
			// System.out.println("Preview done");
			/*
			 * if (exportStartTick > 0) { track0.add(MidiFactory.createNoteOnEventEx(40,9,100,0L));
			 * track0.add(MidiFactory.createNoteOffEventEx(40,9,0,100L)); }
			 */

			return new Pair<>(infoList, sequence);
		} catch (RuntimeException e) {
			// Unpack the InvalidMidiDataException if it was the cause
			if (e.getCause() instanceof InvalidMidiDataException)
				throw (InvalidMidiDataException) e.getCause();

			throw e;
		}
	}

	private static int calculatePartsCount(List<AbcPart> parts) {
		int partsCount = 0;// Number of parts that has assigned tracks to them.
		for (AbcPart p : parts) {
			if (p.getEnabledTrackCount() > 0) {
				partsCount++;
			}
		}
		return partsCount;
	}

	/**
	 * Find parts that can share a channel by switching voice.
	 * 
	 * @param toneMap                     Map of when a part has active notes.
	 * @param target                      Number of pairs that needs to be found.
	 * @param assignedSharingPartsChannel Will not check the parts in this set.
	 * @return Map of pairs of parts that can share channels using this method.
	 */
	private Triplet<List<Set<AbcPart>>, Set<AbcPart>, Integer> findSharableChannelSwitchers(
			Map<AbcPart, TreeMap<Long, Boolean>> toneMap, int target, Set<AbcPart> assignedSharingPartsChannel) {
		// System.out.println("Attempting to free "+target+" channels by finding pairs
		// that can share channel with voice switching.");
		List<Set<AbcPart>> shareChannelWithPatchChangesMap = new ArrayList<>();
		Set<AbcPart> assignedSharingPartsSwitchers = new HashSet<>();

		List<AbcPart> keySet = new ArrayList<>(toneMap.keySet());

		outer: for (int iterC = 0; iterC < keySet.size() - 1 && target > 0; iterC++) {
			AbcPart partC = keySet.get(iterC);
			if (assignedSharingPartsChannel.contains(partC)) {
				continue outer;
			}
			TreeMap<Long, Boolean> tonesCtree = toneMap.get(partC);
			if (tonesCtree != null && !assignedSharingPartsSwitchers.contains(partC)) {
				inner: for (int iterD = keySet.size() - 1; iterD > iterC; iterD--) {
					// iterate opposite direction than C since sparse tracks are often clumped, and
					// we really should try to get non sparse matched with sparse.
					AbcPart partD = keySet.get(iterD);
					if (assignedSharingPartsChannel.contains(partD)) {
						continue inner;
					}
					TreeMap<Long, Boolean> tonesDtree = toneMap.get(partD);
					if (tonesDtree != null && !assignedSharingPartsSwitchers.contains(partD)) {
						if (toneComparator(tonesCtree, tonesDtree)) {
							// We have a match
							// System.out.println("Found channel switch matches:\n "+partC.getTitle()+"\n
							// "+partD.getTitle());
							assignedSharingPartsSwitchers.add(partC);
							assignedSharingPartsSwitchers.add(partD);
							Set<AbcPart> sharePartSet = new HashSet<>();
							Set<TreeMap<Long, Boolean>> shareTreeSet = new HashSet<>();
							sharePartSet.add(partC);
							sharePartSet.add(partD);
							shareTreeSet.add(tonesCtree);
							shareTreeSet.add(tonesDtree);
							target--;
							core: for (int iterE = 0; iterE < keySet.size() && target > 0; iterE++) {
								AbcPart partE = keySet.get(iterE);
								if (assignedSharingPartsChannel.contains(partE)
										|| assignedSharingPartsSwitchers.contains(partE)) {
									continue core;
								}
								TreeMap<Long, Boolean> tonesEtree = toneMap.get(partE);
								if (tonesEtree != null) {
									boolean result = true;
									for (TreeMap<Long, Boolean> tree2 : shareTreeSet) {
										result = result && toneComparator(tonesEtree, tree2);
									}
									if (result) {
										// another match for same channel
										assignedSharingPartsSwitchers.add(partE);
										sharePartSet.add(partE);
										shareTreeSet.add(tonesEtree);
										target--;
										// System.out.println(" "+partE.getTitle());
									}
								}
							}
							shareChannelWithPatchChangesMap.add(sharePartSet);
							continue outer;
						}
					}
				}
			}
		}
		// System.out.println(" "+target+" more freed channels needed.");
		return new Triplet<>(shareChannelWithPatchChangesMap, assignedSharingPartsSwitchers, target);
	}

	private boolean toneComparator(TreeMap<Long, Boolean> tonesCtree, TreeMap<Long, Boolean> tonesDtree) {
		Set<Entry<Long, Boolean>> entriesC = tonesCtree.entrySet();
		for (Entry<Long, Boolean> entryC : entriesC) {
			if (entryC.getKey() != PRE_TICK && ((entryC.getValue() && tonesDtree.floorEntry(entryC.getKey()).getValue())
					|| (!entryC.getValue() && tonesDtree.floorEntry(entryC.getKey() - 1).getValue()))) {
				// part D has active notes at entryC tick. The -1 is to allow D to start right
				// where C ends a note.
				// System.out.println("part D has active notes at entryC tick
				// ("+entryC.getKey()+") "+(qtm.tickToMicros(entryC.getKey())/1000000d));
				// abort this pair
				return false;
			}
		}
		Set<Entry<Long, Boolean>> entriesD = tonesDtree.entrySet();
		for (Entry<Long, Boolean> entryD : entriesD) {
			if (entryD.getKey() != PRE_TICK && ((entryD.getValue() && tonesCtree.floorEntry(entryD.getKey()).getValue())
					|| (!entryD.getValue() && tonesCtree.floorEntry(entryD.getKey() - 1).getValue()))) {
				// part C has active notes at entryD tick. The -1 is to allow C to start right
				// where D ends a note.
				// System.out.println("part C has active notes at entryD tick
				// ("+entryD.getKey()+") "+(qtm.tickToMicros(entryD.getKey())/1000000d));
				// abort this pair
				return false;
			}
		}
		return true;
	}

	/**
	 * Build tick treemaps of when notes are active for each part.
	 * 
	 * @param chordsMade The preview chords for each part.
	 * @return TreeMaps of tick to active note.
	 */
	private Map<AbcPart, TreeMap<Long, Boolean>> findTones(Map<AbcPart, List<Chord>> chordsMade) {
		Map<AbcPart, TreeMap<Long, Boolean>> toneMap = new HashMap<>();
		for (Entry<AbcPart, List<Chord>> chordEntry : chordsMade.entrySet()) {
			if (chordEntry.getValue() == null) {
				// toneMap.put(chordEntry.getKey(), null);
			} else {
				TreeMap<Long, Boolean> tree = new TreeMap<>();
				tree.put(PRE_TICK, false);
				for (Chord chord : chordEntry.getValue()) {
					if (!chord.isRest()) {
						tree.put(chord.getStartTick(), true);
						tree.putIfAbsent(chord.getLongestEndTick(), false);
						// System.out.println(chord.isRest()+" "+chordEntry.getKey().getTitle()+":
						// ("+chord.getStartTick()+","+chord.getEndTick()+")
						// "+(qtm.tickToMicros(chord.getStartTick())/1000000d)+" to
						// "+(qtm.tickToMicros(chord.getEndTick())/1000000d));
					}
				}
				toneMap.put(chordEntry.getKey(), tree);
			}
		}
		return toneMap;
	}

	/**
	 * Build all the preview chords here.
	 * 
	 * @param chordsMade      the map of lists of chord that need to be filled.
	 * @param exportStartTick
	 * @param exportEndTick
	 * @throws AbcConversionException
	 */
	private void exportForChords(Map<AbcPart, List<Chord>> chordsMade, long exportStartTick, long exportEndTick)
			throws AbcConversionException {
		for (AbcPart part : parts) {
			if (part.getEnabledTrackCount() > 0) {
				List<Chord> chords = combineAndQuantize(part, false, exportStartTick, exportEndTick);
				chordsMade.put(part, chords);
			} else {
				try {
					PolyphonyHistogram.count(part, new ArrayList<>());
				} catch (IOException e) {
					throw new AbcConversionException("Failed to read instrument sample durations.", e);
				}
				chordsMade.put(part, null);
			}
		}
	}

	/**
	 * Use brute force to check which parts can share a preview midi channel. Two conditions for that to happen:
	 * <p>
	 * 1 - Must be the same lotro instrument 2 - Must not have any notes with same pitch playing at the same time.
	 * 
	 * @param target                        Number of pairs that need to be found.
	 * @param chordsMade
	 * @param assignedSharingPartsSwitchers
	 * @return
	 * @throws AbcConversionException
	 */
	private Pair<Map<AbcPart, Integer>, Integer> findSharableParts(int target, Map<AbcPart, List<Chord>> chordsMade,
			Set<AbcPart> assignedSharingPartsSwitchers) throws AbcConversionException {
		// System.out.println("Attempting to find parts that can share channel and
		// voice. Need to free "+target+" channels.");
		Map<AbcPart, Integer> shareMap = new HashMap<>();
		int channel = 0;// We create the midi tracks for this method first, so we start at channel 0
		// evaluate fiddles first as they are most likely to use only single notes.
		LotroInstrument[] orderToEvaluate = { LotroInstrument.LONELY_MOUNTAIN_FIDDLE, LotroInstrument.BASIC_FIDDLE,
				LotroInstrument.BARDIC_FIDDLE, LotroInstrument.SPRIGHTLY_FIDDLE, LotroInstrument.STUDENT_FIDDLE,
				LotroInstrument.BASIC_FLUTE, LotroInstrument.BASIC_CLARINET, LotroInstrument.BASIC_HORN,
				LotroInstrument.BASIC_BAGPIPE, LotroInstrument.LONELY_MOUNTAIN_BASSOON, LotroInstrument.BASIC_BASSOON,
				LotroInstrument.BRUSQUE_BASSOON, LotroInstrument.BASIC_PIBGORN, LotroInstrument.BASIC_THEORBO,
				LotroInstrument.BASIC_LUTE, LotroInstrument.LUTE_OF_AGES, LotroInstrument.BASIC_HARP,
				LotroInstrument.MISTY_MOUNTAIN_HARP, LotroInstrument.BASIC_DRUM, LotroInstrument.BASIC_COWBELL,
				LotroInstrument.MOOR_COWBELL, LotroInstrument.TRAVELLERS_TRUSTY_FIDDLE,
				LotroInstrument.STUDENT_FX_FIDDLE };

		for (LotroInstrument evalInstr : orderToEvaluate) {
			if (target < 1)
				break;
			List<AbcPart> partsToCompare = new ArrayList<>();
			for (AbcPart part : parts) {
				if (part.getInstrument().equals(evalInstr) && !assignedSharingPartsSwitchers.contains(part)) {
					partsToCompare.add(part);
				}
			}
			int iterBParts = 0;
			outer: for (int iterAParts = 0; iterAParts < partsToCompare.size() - 1 && target > 0; iterAParts++) {
				AbcPart partA = partsToCompare.get(iterAParts);
				if (shareMap.containsKey(partA)) {
					continue outer;
				}
				List<Chord> chordsA = chordsMade.get(partA);
				if (chordsA == null) {
					continue outer;
				}
				boolean matchFound = false;
				Set<List<Chord>> chordsSet = new HashSet<>();
				chordsSet.add(chordsA);
				inner: for (iterBParts = iterAParts + 1; iterBParts < partsToCompare.size()
						&& target > 0; iterBParts++) {
					AbcPart partB = partsToCompare.get(iterBParts);
					if (shareMap.containsKey(partB)) {
						continue inner;
					}
					List<Chord> chordsB = chordsMade.get(partB);
					if (chordsB == null) {
						continue inner;
					}
					boolean result = true;
					core: for (List<Chord> compareUs : chordsSet) {
						result = result && chordListComparator(chordsB, compareUs, false);
					}
					if (result) {
						shareMap.put(partB, channel);
						chordsSet.add(chordsB);
						if (!matchFound) {
							// System.out.println("Found match");
							// System.out.println(" "+partA.getTitle());
							shareMap.put(partA, channel);
							matchFound = true;
						}
						// System.out.println(" "+partB.getTitle());
						target--;
					}
				}
				if (matchFound) {
					channel++;
					if (channel == MidiConstants.DRUM_CHANNEL) {
						channel++;
					}
					if (channel > 15) {
						return null;
					}
				}
			}
		}
		// System.out.println(" "+target+" channels still need to be freed.");
		return new Pair<>(shareMap, target);
	}

	/**
	 * Use brute force to check for any notes with same pitch playing at the same time.
	 * 
	 * @return false if such notes were found, else true.
	 */
	private boolean chordListComparator(List<Chord> chordsA, List<Chord> chordsB, boolean test) {
		for (Chord aChord : chordsA) {
			if (aChord.isRest()) {
				continue;
			}
			long startAChord = aChord.getStartTick();// All notes in a chord starts at same time as the chord itself
			long endAChord = aChord.getLongestEndTick();

			for (Chord bChord : chordsB) {
				if (bChord.isRest()) {
					continue;
				}
				if (bChord.getStartTick() >= endAChord) {
					break;
				}
				if (bChord.getLongestEndTick() <= startAChord) {
					continue;
				}
				long startBChord = bChord.getStartTick();
				for (int k = 0; k < aChord.size(); k++) {
					// Iterate the aChord notes
					NoteEvent evtA = aChord.get(k);
					if (Note.REST == evtA.note) {
						continue;
					}
					int evtAId = evtA.note.id;
					long endANote = evtA.getEndTick();
					if (startBChord > endANote) {
						continue;
					}
					// long startANote = evt.getStartTick();
					for (int l = 0; l < bChord.size(); l++) {
						// Iterate the bChord notes
						NoteEvent evtB = bChord.get(l);
						int evtIdB = evtB.note.id;
						if (evtIdB != evtAId) {
							continue;
						}

						long endBNote = evtB.getEndTick();
						if (endBNote <= startAChord) {
							continue;
						}
						// long startBNote = evtB.getStartTick();
						if (startBChord >= endANote) {
							continue;
						}
						// The notes are same pitch and overlap
						if (test) {
							System.out.println(evtA.note + " and " + evtB.note + " do not match.");
							System.out.println(" at " + (evtA.getStartMicros() / 1000000) + " seconds.");
						}
						return false;
					}
				}
			}
		}
		return true;
	}

	private void addMidiTempoEvents(Track track0) {
		for (QuantizedTimingInfo.TimingInfoEvent event : qtm.getTimingInfoByTick().values()) {
			if (event.tick > exportEndTick)
				break;

			track0.add(MidiFactory.createTempoEvent(event.info.getExportTempoMPQ(), event.tick));

			if (event.tick == 0) {
				// The Java MIDI sequencer can sometimes miss a tempo event at tick 0
				// Add another tempo event at tick 1 to work around the bug
				track0.add(MidiFactory.createTempoEvent(event.info.getExportTempoMPQ(), 1));
			}
		}
	}

	private ExportTrackInfo exportPartToPreview(AbcPart part, Sequence sequence, long songStartTick, long songEndTick,
			int pan, boolean useLotroInstruments, Set<Integer> assignedChannels, Integer chan,
			boolean programChangeEveryChord, Map<AbcPart, List<Chord>> chordsMade) throws AbcConversionException {
		List<Chord> chords = chordsMade.get(part);

		Pair<Integer, Integer> trackNumber = exportPartToMidi(part, sequence, chords, pan, useLotroInstruments,
				assignedChannels, chan, programChangeEveryChord);

		List<NoteEvent> noteEvents = new ArrayList<>(chords.size());
		for (Chord chord : chords) {
			for (int i = 0; i < chord.size(); i++) {
				NoteEvent ne = chord.get(i);
				// Skip rests and notes that are the continuation of a tied note
				if (ne.note == Note.REST || ne.tiesFrom != null)
					continue;

				// Convert tied notes into a single note event
				if (ne.tiesTo != null) {
					ne.setEndTick(ne.getTieEnd().getEndTick());
					ne.tiesTo = null;
					// Not fixing up the ne.tiesTo.tiesFrom pointer since we that for the
					// (ne.tiesFrom != null) check above, and we otherwise don't care about
					// ne.tiesTo.
				}

				noteEvents.add(ne);
			}
		}

		return new ExportTrackInfo(trackNumber.first, part, noteEvents, trackNumber.second,
				part.getInstrument().midi.id());
	}

	private Pair<Integer, Integer> exportPartToMidi(AbcPart part, Sequence out, List<Chord> chords, int pan,
			boolean useLotroInstruments, Set<Integer> assignedChannels, Integer chan, boolean programChangeEveryChord) {
		int trackNumber = out.getTracks().length;
		part.setPreviewSequenceTrackNumber(trackNumber);

		int channel = lastChannelUsedInPreview + 1;
		if (chan != null) {
			channel = chan;
			// System.out.println(" fixed: "+chan);
		} else if (channel == MidiConstants.DRUM_CHANNEL) {
			channel++;
		}
		// System.out.println("Channel using "+channel);
		lastChannelUsedInPreview = Math.max(channel, lastChannelUsedInPreview);

		Track track = out.createTrack();

		track.add(MidiFactory.createTrackNameEvent(part.getTitle()));
		if (useLotroInstruments && !assignedChannels.contains(channel)) {
			// Only change the channel voice once
			track.add(MidiFactory.createLotroChangeEvent(part.getInstrument().midi.id(), channel, 0));
			// System.out.println("Channel "+channel+" for "+part.getInstrument());
		}
		if (!assignedChannels.contains(channel)) {
			if (useLotroInstruments) {
				track.add(MidiFactory.createChannelVolumeEvent(MidiConstants.MAX_VOLUME, channel, 1));
				track.add(MidiFactory.createReverbControlEvent(AbcConstants.MIDI_REVERB, channel, 1));
				track.add(MidiFactory.createChorusControlEvent(AbcConstants.MIDI_CHORUS, channel, 1));
			}
			track.add(MidiFactory.createPanEvent(pan, channel));
		}

		List<NoteEvent> notesOn = new ArrayList<>();

		int noteDelta = 0;
		if (!useLotroInstruments)
			noteDelta = part.getInstrument().octaveDelta * 12;

		for (Chord chord : chords) {
			if (programChangeEveryChord && useLotroInstruments) {
				track.add(MidiFactory.createProgramChangeEvent(part.getInstrument().midi.id(), channel,
						chord.getStartTick()));
			}
			Dynamics dynamics = chord.calcDynamics();
			if (dynamics == null)
				dynamics = Dynamics.DEFAULT;
			for (int j = 0; j < chord.size(); j++) {
				NoteEvent ne = chord.get(j);
				// Skip rests and notes that are the continuation of a tied note
				if (ne.note == Note.REST || ne.tiesFrom != null)
					continue;

				// Add note off events for any notes that have been turned off by this point
				Iterator<NoteEvent> onIter = notesOn.iterator();
				while (onIter.hasNext()) {
					NoteEvent on = onIter.next();

					// Shorten the note to end at the same time that the next one starts
					long endTick = on.getEndTick();
					if (on.note.id == ne.note.id && on.getEndTick() > ne.getStartTick())
						endTick = ne.getStartTick();

					if (endTick <= ne.getStartTick()) {
						// This note has been turned off
						onIter.remove();
						track.add(MidiFactory.createNoteOffEvent(on.note.id + noteDelta, channel, endTick));
					}
				}

				long endTick = ne.getTieEnd().getEndTick();

				// Lengthen to match the note lengths used in the game
				if (useLotroInstruments) {
					boolean sustainable = part.getInstrument().isSustainable(ne.note.id);
					double extraSeconds = sustainable ? AbcConstants.SUSTAINED_NOTE_HOLD_SECONDS
							: AbcConstants.NON_SUSTAINED_NOTE_HOLD_SECONDS;

					endTick = qtm.microsToTick(qtm.tickToMicros(endTick)
							+ (int) (extraSeconds * TimingInfo.ONE_SECOND_MICROS * qtm.getExportTempoFactor()));
				}

				if (endTick != ne.getEndTick()) {
					int oPitch = ne.origPitch;
					ne = new NoteEvent(ne.note, ne.velocity, ne.getStartTick(), endTick, qtm);
					ne.origPitch = oPitch;
				}

				track.add(MidiFactory.createNoteOnEventEx(ne.note.id + noteDelta, channel,
						dynamics.getVol(useLotroInstruments), ne.getStartTick()));
				notesOn.add(ne);
			}
		}

		for (NoteEvent on : notesOn) {
			track.add(MidiFactory.createNoteOffEvent(on.note.id + noteDelta, channel, on.getEndTick()));
		}

		return new Pair<>(trackNumber, channel);
	}

	public void exportToAbc(OutputStream os, boolean delayEnabled) throws AbcConversionException {
		Pair<Long, Long> startEnd = getSongStartEndTick(true /* lengthenToBar */, false /* accountForSustain */,false);
		exportStartTick = startEnd.first;
		exportEndTick = startEnd.second;

		PrintStream out = new PrintStream(os);
		if (!parts.isEmpty()) {
			out.println("%abc-2.1");
			out.println(AbcField.SONG_TITLE + StringCleaner.cleanForABC(metadata.getSongTitle()));
			if (metadata.getComposer().length() > 0) {
				out.println(AbcField.SONG_COMPOSER + StringCleaner.cleanForABC(metadata.getComposer()));
			}
			out.println(AbcField.SONG_DURATION + Util.formatDuration(getSongLengthMicros()));
			if (metadata.getTranscriber().length() > 0) {
				out.println(AbcField.SONG_TRANSCRIBER + StringCleaner.cleanForABC(metadata.getTranscriber()));
			}
			out.println(AbcField.ABC_CREATOR + MaestroMain.APP_NAME + " v" + MaestroMain.APP_VERSION);
			out.println(AbcField.EXPORT_TIMESTAMP + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
			out.println(AbcField.SWING_RHYTHM + Boolean.toString(qtm.isTripletTiming()));
			out.println(AbcField.MIX_TIMINGS + Boolean.toString(qtm.isMixTiming()));
			out.println(AbcField.SKIP_SILENCE_AT_START + Boolean.toString(skipSilenceAtStart));
			out.println(AbcField.DELETE_MINIMAL_NOTES + Boolean.toString(deleteMinimalNotes));
			out.println(AbcField.ABC_VERSION + "2.1");
			String gnr = StringCleaner.cleanForABC(metadata.getGenre()).toLowerCase().trim();
			String mood = StringCleaner.cleanForABC(metadata.getMood()).toLowerCase().trim();
			String outAll = metadata.getAllParts();
			String badgerTitle = metadata.getBadgerTitle();
			if (gnr.length() > 0 || mood.length() > 0 || outAll != null || badgerTitle != null) {
				out.println();
				if (badgerTitle != null) {
					out.println(badgerTitle);
				}
				if (gnr.length() > 0) {
					out.println("N: Genre: " + gnr);
				}
				if (mood.length() > 0) {
					out.println("N: Mood: " + mood);
				}
				if (outAll != null) {
					out.println(outAll);
				}
			}
		}

		for (AbcPart part : parts) {
			if (part.getEnabledTrackCount() > 0) {
				exportPartToAbc(part, exportStartTick, exportEndTick, out, delayEnabled);
			}
		}
	}

	private long getSongLengthMicros() {
		return (long) ((getExportEndMicros() - getExportStartMicros())
				/ (double) qtm.getExportTempoFactor());
	}

	private void exportPartToAbc(AbcPart part, long songStartTick, long songEndTick, PrintStream out,
			boolean delayEnabled) throws AbcConversionException {
		List<Chord> chords = combineAndQuantize(part, true, songStartTick, songEndTick);

		out.println();
		out.println("X: " + part.getPartNumber());
		if (metadata != null)
			out.println("T: " + StringCleaner.cleanForABC(metadata.getPartName(part)));
		else
			out.println("T: " + part.getTitle().trim());

		out.println(AbcField.PART_NAME + StringCleaner.cleanForABC(part.getTitle()));

		// Since people might not use the instrument-name when they name a part,
		// we add this so can choose the right instrument in abcPlayer and maestro when
		// loading abc.
		out.println(AbcField.MADE_FOR + part.getInstrument().friendlyName.trim());

		if (metadata != null) {
			if (metadata.getComposer().length() > 0)
				out.println("C: " + StringCleaner.cleanForABC(metadata.getComposer()));

			if (metadata.getTranscriber().length() > 0)
				out.println("Z: " + StringCleaner.cleanForABC(metadata.getTranscriber()));
		}

		out.println("M: " + qtm.getMeter());
		out.println("Q: " + qtm.getPrimaryExportTempoBPM());
		out.println("K: " + keySignature);
		out.println("L: " + ((qtm.getMeter().numerator / (double) qtm.getMeter().denominator) < 0.75 ? "1/16" : "1/8"));
		out.println();

		// Keep track of which notes have been sharped or flatted so
		// we can naturalize them the next time they show up.
		boolean[] sharps = new boolean[Note.MAX_PLAYABLE.id + 1];
		boolean[] flats = new boolean[Note.MAX_PLAYABLE.id + 1];

		// Write out ABC notation
		final int BAR_LENGTH = 160;
		final long songStartMicros = qtm.tickToMicros(exportStartTick);
		final int primaryExportTempoBPM = qtm.getPrimaryExportTempoBPM();
		int curBarNumber = firstBarNumber;
		int curExportTempoBPM = primaryExportTempoBPM;
		Dynamics curDyn = null;
		Dynamics initDyn = null;

		final StringBuilder bar = new StringBuilder();

		Runnable addLineBreaks = () -> {
			// Trim end
			int length = bar.length();
			if (length == 0)
				return;

			while (Character.isWhitespace(bar.charAt(length - 1)))
				length--;
			bar.setLength(length);

			// Insert line breaks inside very long bars
			for (int i = BAR_LENGTH; i < bar.length(); i += BAR_LENGTH) {
				for (int j = 0; j < BAR_LENGTH - 1; j++, i--) {
					if (bar.charAt(i) == ' ') {
						bar.replace(i, i + 1, "\r\n\t");
						i += "\r\n\t".length() - 1;
						break;
					}
				}
			}
		};

		for (Chord c : chords) {
			initDyn = c.calcDynamics();
			if (initDyn != null)
				break;
		}

		if (delayEnabled && qtm.getPrimaryExportTempoBPM() >= 50) {
			// oneNote is duration in secs of z1
			double oneNote = 60 / (double) qtm.getPrimaryExportTempoBPM() * qtm.getMeter().denominator
					/ ((qtm.getMeter().numerator / (double) qtm.getMeter().denominator) < 0.75 ? 16d : 8d);
			// fractionFactor is number of z that the whole song is being start delayed
			// with.
			// it is always 1 or above. Above if oneNote is smaller than 60ms.
			int fractionFactor = (int) Math.ceil(Math.max(1d, 0.06d / oneNote));
			if (part.delay == 0) {
				out.println("z" + fractionFactor + " | ");
			} else {
				int numer = 10000 * fractionFactor;
				int denom = 10000;
				numer += (int) (numer * part.delay / (fractionFactor * oneNote * 1000));
				out.println("z" + numer + "/" + denom + " | ");
				// System.err.println("M: " + qtm.getMeter()+" Q: " +
				// qtm.getPrimaryExportTempoBPM()+ " L: " + ((qtm.getMeter().numerator/ (double)
				// qtm.getMeter().denominator)<0.75?"1/16":"1/8")+"\n oneNote is "+oneNote+"
				// delay is "+part.delay+"ms : "+"z"+numer+"/"+denom);
			}
		}
		
		for (Chord c : chords) {
			if (c.size() == 0) {
				assert false : "Chord has no notes!";
				continue;
			}

			assert !c.hasRestAndNotes();

			/*
			 * if (c.hasRestAndNotes()) { c.removeRests(); }
			 */

			c.sort();

			// Is this the start of a new bar?
			int barNumber = Math.max(qtm.tickToBarNumber(c.getStartTick()), firstBarNumber);
			assert barNumber >= curBarNumber : metadata.getSongTitle()+ ": Bar counting error. Part: "+part.getTitle()+" barNumber="+barNumber+" curBarNumber="+curBarNumber+" chordStartTick="+c.getStartTick();

			if (barNumber > curBarNumber) {
				// Print the previous bar
				if (bar.length() > 0) {
					addLineBreaks.run();
					out.print(bar);
					out.println(" |");
					bar.setLength(0);
				}

				curBarNumber = barNumber;

				int exportBarNumber = curBarNumber - firstBarNumber;
				if ((exportBarNumber + 1) % 10 == 0) {
					long micros = (long) ((qtm.barNumberToMicrosecond(curBarNumber) - songStartMicros)
							/ qtm.getExportTempoFactor());
					out.println("% Bar " + (exportBarNumber + 1) + " (" + Util.formatDuration(micros) + ")");
				}

				Arrays.fill(sharps, false);
				Arrays.fill(flats, false);
			}

			// Is this the start of a new tempo?
			TimingInfo tm = qtm.getTimingInfo(c.getStartTick(), part);
			if (curExportTempoBPM != tm.getExportTempoBPM()) {
				curExportTempoBPM = tm.getExportTempoBPM();

				// Print the partial bar
				if (bar.length() > 0) {
					addLineBreaks.run();
					out.println(bar);
					bar.setLength(0);
					bar.append("\t");
					out.print("\t");
				}

				out.println("%%Q: " + curExportTempoBPM);
			}

			Dynamics newDyn = (initDyn != null) ? initDyn : c.calcDynamics();
			initDyn = null;
			if (newDyn != null && newDyn != curDyn) {
				bar.append('+').append(newDyn).append("+ ");
				curDyn = newDyn;
			}

			if (c.size() > 1) {
				bar.append('[');
			}

			int notesWritten = 0;
			for (int j = 0; j < c.size(); j++) {
				NoteEvent evt = c.get(j);
				if (evt.getLengthTicks() == 0) {
					assert false : "Zero-length note";
					continue;
				}

				String noteAbc = evt.note.abc;
				if (evt.note != Note.REST) {
					if (evt.note.isSharp()) {
						if (sharps[evt.note.naturalId])
							noteAbc = Note.fromId(evt.note.naturalId).abc;
						else
							sharps[evt.note.naturalId] = true;
					} else if (evt.note.isFlat()) {
						if (flats[evt.note.naturalId])
							noteAbc = Note.fromId(evt.note.naturalId).abc;
						else
							flats[evt.note.naturalId] = true;
					} else if (sharps[evt.note.id] || flats[evt.note.id]) {
						sharps[evt.note.id] = false;
						flats[evt.note.id] = false;
						bar.append('=');
					}
				}

				bar.append(noteAbc);

				int numerator = (int) (evt.getLengthTicks() / tm.getMinNoteLengthTicks()) * tm.getDefaultDivisor();
				int denominator = tm.getMinNoteDivisor();

				// Apply tempo
				if (curExportTempoBPM != primaryExportTempoBPM) {
					numerator *= primaryExportTempoBPM;
					denominator *= curExportTempoBPM;
				}

				// Reduce the fraction
				int gcd = Util.gcd(numerator, denominator);
				numerator /= gcd;
				denominator /= gcd;

				if (numerator == 1 && denominator == 2) {
					bar.append('/');
				} else if (numerator == 1 && denominator == 4) {
					bar.append("//");
				} else {
					if (numerator == 0) {
						System.err.println("Zero length Error: ticks=" + evt.getLengthTicks() + " micros="
								+ evt.getLengthMicros() + " note=" + noteAbc);
					}
					if (numerator != 1)
						bar.append(numerator);
					if (denominator != 1)
						bar.append('/').append(denominator);
				}

				if (evt.tiesTo != null)
					bar.append('-');

				notesWritten++;
			}

			if (c.size() > 1) {
				if (notesWritten == 0) {
					// Remove the [
					bar.delete(bar.length() - 1, bar.length());
				} else {
					bar.append(']');
				}
			}

			bar.append(' ');
		}

		addLineBreaks.run();
		out.print(bar);
		out.println(" |]");
		out.println();
	}

	/**
	 * Combine the tracks into one, quantize the note lengths, separate into chords.
	 */
	private List<Chord> combineAndQuantize(AbcPart part, boolean addTies, final long songStartTick,
			final long songEndTick) throws AbcConversionException {
		// Combine the events from the enabled tracks
		List<NoteEvent> events = new ArrayList<>();
		for (int t = 0; t < part.getTrackCount(); t++) {
			if (part.isTrackEnabled(t)) {
				boolean specialDrumNotes = false;
				if (part.getInstrument() == LotroInstrument.BASIC_DRUM) {
					TrackInfo tInfo = part.getAbcSong().getSequenceInfo().getTrackInfo(t);
					for (int inNo : tInfo.getNotesInUse()) {
						byte outNo = part.getDrumMap(t).get(inNo);
						if (outNo > part.getInstrument().highestPlayable.id) {
							specialDrumNotes = true;
							break;
						}
					}
				}
				List<NoteEvent> listOfNotes = new ArrayList<>(part.getTrackEvents(t));

				if (specialDrumNotes) {
					List<NoteEvent> extraList = new ArrayList<>();
					List<NoteEvent> removeList = new ArrayList<>();
					for (NoteEvent ne : listOfNotes) {
						Note possibleCombiNote = part.mapNote(t, ne.note.id, ne.getStartTick());
						if (possibleCombiNote != null && possibleCombiNote.id > part.getInstrument().highestPlayable.id
								&& possibleCombiNote.id <= LotroCombiDrumInfo.maxCombi.id) {
							NoteEvent extra1 = LotroCombiDrumInfo.getId1(ne, possibleCombiNote);
							NoteEvent extra2 = LotroCombiDrumInfo.getId2(ne, possibleCombiNote);
							extra1.setMidiPan(ne.midiPan);
							extra2.setMidiPan(ne.midiPan);
							extraList.add(extra1);
							extraList.add(extra2);
							removeList.add(ne);
						} else if (possibleCombiNote != null && possibleCombiNote.id > LotroCombiDrumInfo.maxCombi.id) {
							// Just for safety, should never land here.
							System.out.println("// Just for safety, should never land here:+\n"+ne);
							removeList.add(ne);
						}
					}
					listOfNotes.removeAll(removeList);
					listOfNotes.addAll(extraList);
				}

				for (NoteEvent ne : listOfNotes) {
					// Skip notes that are outside of the play range.
					if (ne.getEndTick() <= exportStartTick || ne.getStartTick() >= exportEndTick) {
						//System.out.println(metadata.getSongTitle()+": Skipping notes that are outside of the play range!\n"+ne);
						// This is not an issue, they are not scheduled to be heard anyway.
						continue;
					}

					// reset pruned flag
					// ne.resetPruned(part);

					Note mappedNote = ne.note;

					if (!ne.alreadyMapped) {
						mappedNote = part.mapNoteEvent(t, ne);
					}

					if (mappedNote != null && part.shouldPlay(ne, t)) {
						if (!(ne instanceof BentNoteEvent)) {
							assert mappedNote.id >= part.getInstrument().lowestPlayable.id : mappedNote;
							assert mappedNote.id <= part.getInstrument().highestPlayable.id : mappedNote;
						}
						// if (mappedNote.id > part.getInstrument().highestPlayable.id) {
						// part.mapNoteEvent2(t, ne);
						// }

						long startTick = Math.max(ne.getStartTick(), exportStartTick);
						long endTick = Math.min(ne.getEndTick(), exportEndTick);
						if (part.isFXPart()) {
							long endTickMin = qtm.microsToTick(
									qtm.tickToMicros(startTick) + (long) (AbcConstants.STUDENT_FX_MIN_SECONDS
											* TimingInfo.ONE_SECOND_MICROS * qtm.getExportTempoFactor()));
							endTick = Math.max(endTick, endTickMin);
						}

						int[] sva = part.getSectionVolumeAdjust(t, ne);
						int velocity = part.getSectionNoteVelocity(t, ne);
						velocity = (int) ((velocity + part.getTrackVolumeAdjust(t) + sva[0]) * 0.01f * (float) sva[1] * 0.01f * (float) sva[2]);

						NoteEvent newNE = createNoteEvent(ne, mappedNote, velocity, startTick, endTick, qtm);
						if (!part.isPercussionPart()) {
							int origId = part.mapNoteFullOctaves(t, ne.note.id, ne.getStartTick());
							if (mappedNote.id != origId) {
								newNE.origPitch = origId;
							}
						}
						/*
						 * if (!addTies) { // Only associate if doing preview newNE.origEvent = new
						 * ArrayList<NoteEvent>(); newNE.origEvent.add(ne); }
						 */
						events.add(newNE);

						Boolean[] doubling = part.getSectionDoubling(ne.getStartTick(), t);

						if (doubling[0] && ne.note.id - 24 > Note.MIN.id) {
							Note mappedNote2 = part.mapNote(t, ne.note.id - 24, ne.getStartTick());
							NoteEvent newNE2 = createNoteEvent(ne, mappedNote2, velocity, startTick, endTick, qtm);
							newNE2.doubledNote = true;// prune these first
							events.add(newNE2);
						}
						if (doubling[1] && ne.note.id - 12 > Note.MIN.id) {
							Note mappedNote2 = part.mapNote(t, ne.note.id - 12, ne.getStartTick());
							NoteEvent newNE2 = createNoteEvent(ne, mappedNote2, velocity, startTick, endTick, qtm);
							newNE2.doubledNote = true;
							events.add(newNE2);
						}
						if (doubling[2] && ne.note.id + 12 < Note.MAX.id) {
							Note mappedNote2 = part.mapNote(t, ne.note.id + 12, ne.getStartTick());
							NoteEvent newNE2 = createNoteEvent(ne, mappedNote2, velocity, startTick, endTick, qtm);
							newNE2.doubledNote = true;
							events.add(newNE2);
						}
						if (doubling[3] && ne.note.id + 24 < Note.MAX.id) {
							Note mappedNote2 = part.mapNote(t, ne.note.id + 24, ne.getStartTick());
							NoteEvent newNE2 = createNoteEvent(ne, mappedNote2, velocity, startTick, endTick, qtm);
							newNE2.doubledNote = true;
							events.add(newNE2);
						}
					} else {
						//System.out.println("Final skipping \n"+ne+"\n"+(mappedNote != null)+" "+(part.shouldPlay(ne, t)));
					}
				}
			}
		}
		
		if (events.isEmpty() && !addTies) {
			try {
				PolyphonyHistogram.count(part, events);
			} catch (IOException e) {
				throw new AbcConversionException("Failed to read instrument sample durations.", e);
			}
			return Collections.emptyList();
		}

		Collections.sort(events);

		// Quantize the events
		long lastEnding = 0;
		NoteEvent lastEvent = null;
		List<NoteEvent> extraEvents = new ArrayList<>();
		List<NoteEvent> deleteEvents = new ArrayList<>();
		
		int removedToAvoidDissonance = 0;
		for (int cc = 0; cc < events.size() ; cc++) {
			NoteEvent ne = events.get(cc);
			if (addTies)
				assert ne.note != Note.REST : "Rest detected!";
			
			long oldStart = ne.getStartTick();
			long oldEnd = ne.getEndTick();
			long newStart = qtm.quantize(oldStart, part);
			long newEnding = qtm.quantize(oldEnd, part);
			
			ne.setStartTick(newStart);
			ne.setEndTick(newEnding);
			boolean deleted = false;
			// Make sure the note didn't get quantized to zero length
			if (ne.getLengthTicks() == 0) {
				if (ne.note == Note.REST) {
					deleteEvents.add(ne);
					continue;
				} else if (deleteMinimalNotes && !part.getInstrument().isPercussion) {
					
					long halfMin = qtm.getTimingInfo(newStart, part).getMinNoteLengthTicks()/2;
					for (int ccc = cc+1; ccc < events.size() && ccc < cc+40; ccc++) {
						NoteEvent neLeft = events.get(ccc);
						if (neLeft.getStartTick() >= newStart && neLeft.getStartTick() < newStart+halfMin && neLeft.getStartTick() >= oldEnd) {
							// So a note coming after our note, starts very soon after our note.
							// It did not overlap in the midi and they will most likely overlap after quantization to minimal note length.
							//
							// Just because they overlap where they should not does not guarantee dissonance, but its likely.
							//
							// TODO: In theory should check this across all parts also.
							deleteEvents.add(ne);
							removedToAvoidDissonance++;
							deleted = true;
							break;
						}
					}
					if (deleted) {
						continue;
					}
					ne.setLengthTicks(qtm.getTimingInfo(ne.getStartTick(), part).getMinNoteLengthTicks());
				} else {
					ne.setLengthTicks(qtm.getTimingInfo(ne.getStartTick(), part).getMinNoteLengthTicks());
				}
			}

			List<NoteEvent> bentNotes = quantizePitchBends(part, ne);
			
			if (bentNotes != null) {
				assert !bentNotes.contains(ne);
				deleteEvents.add(ne);
				for (NoteEvent bent : bentNotes) {
					assert bent.note != Note.REST;
					if (!addTies && qtm.getPrimaryExportTempoBPM() >= 50 && part.delay != 0) {
						// Make delay on instrument be audible in preview
						long delayMicros = (long) (part.delay * 1000 * qtm.getExportTempoFactor());
						bent.setEndTick(qtm.microsToTick(qtm.tickToMicros(bent.getEndTick()) + delayMicros));
						bent.setStartTick(qtm.microsToTick(qtm.tickToMicros(bent.getStartTick()) + delayMicros));
					}
					if (bent.getEndTick() > lastEnding) {
						lastEnding = bent.getEndTick();
						lastEvent = bent;
					}
				}
				extraEvents.addAll(bentNotes);
			} else {
				if (!addTies && qtm.getPrimaryExportTempoBPM() >= 50 && part.delay != 0) {
					// Make delay on instrument be audible in preview
					long delayMicros = (long) (part.delay * 1000 * qtm.getExportTempoFactor());
					ne.setEndTick(qtm.microsToTick(qtm.tickToMicros(ne.getEndTick()) + delayMicros));
					ne.setStartTick(qtm.microsToTick(qtm.tickToMicros(ne.getStartTick()) + delayMicros));
				}
				if (ne.getEndTick() > lastEnding) {
					lastEnding = ne.getEndTick();
					lastEvent = ne;
				}
			}
		}
		
		if (removedToAvoidDissonance > 0) {
			System.out.println("Removed "+removedToAvoidDissonance+"/"+events.size()+" notes to avoid overlap and dissonance. Part: "+part.getTitle());
		}

		events.addAll(extraEvents);// add all the pitchbend fractions to the main event list
		events.removeAll(deleteEvents);
		//System.out.println("Something removed: "+deleteEvents.size());
		//System.out.println("Something added: "+extraEvents.size());
		
		Collections.sort(events);
		
		if (events.size() == 0) {
			System.out.println("Export to preview/abc: "+metadata.getSongTitle()+" has a part with no exported notes.");
			return new ArrayList<>();
		}
		
		// Add initial rest if necessary
		
		if (events.get(0).getStartTick() > exportStartTick) {
			events.add(0, new NoteEvent(Note.REST, Dynamics.DEFAULT.midiVol, exportStartTick,
					events.get(0).getStartTick(), qtm));
		}

		// Add a rest at the end if necessary
		if (exportEndTick < Long.MAX_VALUE) {

			if (lastEvent.getEndTick() < exportEndTick) {
				if (lastEvent.note == Note.REST) {
					lastEvent.setEndTick(exportEndTick);
				} else {
					events.add(new NoteEvent(Note.REST, Dynamics.DEFAULT.midiVol, lastEvent.getEndTick(),
							exportEndTick, qtm));
				}
			}
		}

		// Remove duplicate notes
		List<NoteEvent> notesOn = new ArrayList<>();
		Iterator<NoteEvent> neIter = events.iterator();
		dupLoop: while (neIter.hasNext()) {
			NoteEvent ne = neIter.next();
			Iterator<NoteEvent> onIter = notesOn.iterator();
			while (onIter.hasNext()) {
				NoteEvent on = onIter.next();
				if (on.getEndTick() < ne.getStartTick()) {
					// This note has already been turned off
					onIter.remove();
				} else if (on.note.id == ne.note.id) {
					if (on.getStartTick() == ne.getStartTick()) {
						// If they start at the same time, remove the second event.
						// Lengthen the first one if it's shorter than the second one.
						if (on.getEndTick() < ne.getEndTick())
							on.setEndTick(ne.getEndTick());

						// Remove the duplicate note
						neIter.remove();
						/*
						 * if (ne.origEvent != null) { if (on.origEvent == null) { on.origEvent = new
						 * ArrayList<NoteEvent>(); } on.origEvent.addAll(ne.origEvent); }
						 */
						continue dupLoop;
					} else {
						// Otherwise, if they don't start at the same time:
						// 1. Lengthen the second note if necessary, so it doesn't end before
						// the first note would have ended.
						if (ne.getEndTick() < on.getEndTick())
							ne.setEndTick(on.getEndTick());

						// 2. Shorten the note that's currently on to end at the same time that
						// the next one starts.
						on.setEndTick(ne.getStartTick());
						onIter.remove();
					}
				}
			}
			notesOn.add(ne);
		}

		breakLongNotes(part, events, addTies);

		List<Chord> chords = new ArrayList<>(events.size() / 2);
		List<NoteEvent> tmpEvents = new ArrayList<>();
		
		if (!addTies) {
			try {
				PolyphonyHistogram.count(part, events);
			} catch (IOException e) {
				throw new AbcConversionException("Failed to read instrument sample durations.", e);
			}
		}
		
		// Combine notes that play at the same time into chords
		Chord curChord = new Chord(events.get(0));
		chords.add(curChord);
		for (int i = 1; i < events.size(); i++) {
			NoteEvent ne = events.get(i);

			if (curChord.getStartTick() == ne.getStartTick()) {
				// This note starts at the same time as the rest of the notes in the chord
				if (addTies)
					assert !curChord.isRest();
				curChord.addAlways(ne);
				// if (addTies) assert curChord.hasRestAndNotes() : "addTies is true!";
			} else {
				List<NoteEvent> deadnotes = curChord.prune(part.getInstrument().sustainable,
						part.getInstrument() == LotroInstrument.BASIC_DRUM);
				removeNotes(events, deadnotes, part);
				if (!deadnotes.isEmpty()) {
					// One of the tiedTo notes that was pruned might be the events.get(i) note,
					// so we go one step back and re-process events.get(i)
					i--;
					continue;
				}

				// Create a new chord
				Chord nextChord = new Chord(ne);

				if (addTies) {
					// The curChord has all the notes it will get. But before continuing,
					// normalize the chord so that all notes end at the same time and end
					// before the next chord starts.
					boolean reprocessCurrentNote = false;
					long targetEndTick = Math.min(nextChord.getStartTick(), curChord.getEndTick());

					for (int j = 0; j < curChord.size(); j++) {
						NoteEvent jne = curChord.get(j);
						if (jne.getEndTick() > targetEndTick) {
							// This note extends past the end of the chord; break it into two tied notes
							NoteEvent next = jne.splitWithTieAtTick(targetEndTick);

							int ins = Collections.binarySearch(events, next);
							if (ins < 0)
								ins = -ins - 1;
							assert (ins >= i);
							// If we're inserting before the current note, back up and process the added
							// note
							if (ins == i)
								reprocessCurrentNote = true;
							if (addTies)
								assert next.note != Note.REST;
							events.add(ins, next);
						}
					}

					// The shorter notes will have changed the chord's duration
					if (targetEndTick < curChord.getEndTick())
						curChord.recalcEndTick();

					if (reprocessCurrentNote) {
						i--;
						continue;
					}
				} else {
					// If we're not allowed to add ties, use the old method of shortening the
					// chord by inserting a short rest.

					// The next chord starts playing immediately after the *shortest* note (or rest)
					// in
					// the current chord is finished, so we may need to add a rest inside the chord
					// to
					// shorten it, or a rest after the chord to add a pause.

					// Check the chord length again, since removing a note might have changed its
					// length
					if (curChord.getEndTick() > nextChord.getStartTick()) {
						// If the chord is too long, add a short rest in the chord to shorten it
						curChord.addAlways(new NoteEvent(Note.REST, Dynamics.DEFAULT.midiVol, curChord.getStartTick(),
								nextChord.getStartTick(), qtm));
						// No pruning after a rest is added, as this is for preview and 6 notes plus a
						// rest should be allowed.
					}
				}

				// Insert a rest between the chords if needed
				if (curChord.getEndTick() < nextChord.getStartTick()) {
					tmpEvents.clear();
					tmpEvents.add(new NoteEvent(Note.REST, Dynamics.DEFAULT.midiVol, curChord.getEndTick(),
							nextChord.getStartTick(), qtm));
					breakLongNotes(part, tmpEvents, addTies);

					for (NoteEvent restEvent : tmpEvents) {
						chords.add(new Chord(restEvent));
					}
				}

				chords.add(nextChord);
				if (addTies)
					assert !nextChord.hasRestAndNotes();
				if (addTies)
					assert !curChord.hasRestAndNotes();
				curChord = nextChord;
			}
		}

		boolean reprocessCurrentNote = true;
		if (addTies) {
			while (reprocessCurrentNote) {
				// The last Chord has all the notes it will get. But before continuing,
				// normalize the chord so that all notes end at the same time and end
				// before the next chord starts.

				// Last chord needs to be pruned as that hasn't happened yet.
				List<NoteEvent> deadnotes = curChord.prune(part.getInstrument().sustainable,
						part.getInstrument() == LotroInstrument.BASIC_DRUM);
				removeNotes(events, deadnotes, part);// we need to set the pruned flag for last chord too.
				curChord.recalcEndTick();
				long targetEndTick = curChord.getEndTick();

				reprocessCurrentNote = false;

				Chord nextChord = null;

				for (int j = 0; j < curChord.size(); j++) {
					NoteEvent jne = curChord.get(j);
					if (jne.getEndTick() > targetEndTick) {
						// This note extends past the end of the chord; break it into two tied notes
						NoteEvent next = jne.splitWithTieAtTick(targetEndTick);
						if (nextChord == null) {
							nextChord = new Chord(next);
							chords.add(nextChord);
						} else {
							nextChord.add(next);
						}
					}
				}
				curChord.recalcEndTick();
				if (nextChord != null) {
					reprocessCurrentNote = true;
					curChord = nextChord;
					curChord.recalcEndTick();
				}
			}
		} else {
			// Last chord needs to be pruned as that hasn't happened yet.
			List<NoteEvent> deadnotes = curChord.prune(part.getInstrument().sustainable,
					part.getInstrument() == LotroInstrument.BASIC_DRUM);
			removeNotes(events, deadnotes, part);// we need to set the pruned flag for last chord too.
			curChord.recalcEndTick();
		}
		if (addTies)
			assert !curChord.hasRestAndNotes();
		return chords;
	}

	/*
	private void verifySort(List<NoteEvent> events) {
		long c = -1000000L;
		for (NoteEvent ne : events) {
			//System.out.println(ne.getStartTick());
			if (ne.getStartTick() < c) assert 1==0;
			c = ne.getStartTick();
		}		
	}
	
	private void verifySortChords(List<Chord> chords) {
		long c = -1000000L;
		for (Chord ne : chords) {
			//System.out.println("C "+ne.getStartTick());
			if (ne.getStartTick() < c) assert 1==0;
			c = ne.getStartTick();
		}		
	}
	*/

	private NoteEvent createNoteEvent(NoteEvent oldNe, Note note, int velocity, long startTick, long endTick,
			ITempoCache tempos) {
		if (oldNe instanceof BentNoteEvent) {
			BentNoteEvent newNe = new BentNoteEvent(note, velocity, startTick, endTick, tempos);
			newNe.setBends(((BentNoteEvent) oldNe).bends);
			return newNe;
		} else {
			return new NoteEvent(note, velocity, startTick, endTick, tempos);
		}
	}

	/**
	 * Split all BentNoteEvents into multiple quantized NoteEvents
	 * 
	 * @param part Abc Part
	 * @param ne   The note event to be processed
	 * @return List of multiple NoteEvents
	 */
	private List<NoteEvent> quantizePitchBends(AbcPart part, NoteEvent ne) {
		// Handle pitch bend by subdividing tone into shorter quantized notes.
		// By the time this method is ran, start and end tick of the bent tone is already quantized.
		if (ne instanceof BentNoteEvent) {
			BentNoteEvent be = (BentNoteEvent) ne;
			int noteID = be.note.id;
			assert be.note != Note.REST;
			int startPitch = noteID;
			List<NoteEvent> benders = new ArrayList<>();
			NoteEvent current = null;
			boolean changeAtLastGrid = true;
			long lastGridTick = 0L;
			for (long t = be.getStartTick(); t < be.getEndTick(); t++) {
				Integer entry = be.getBend(t);
				if (entry != null) {
					noteID = startPitch + entry;
				} else {
					// Since all bent notes have a bend at start tick,
					// and that start tick might have been quantized to lower tick.
					// Make sure we grab that initial value here.
					entry = be.bends.firstEntry().getValue();
					noteID = startPitch + entry;
				}
				if (current == null) {
					current = createBentSubNote(be, noteID, current, t);
					if (current == null)
						return new ArrayList<>();
					benders.add(current);
					lastGridTick = t;
					changeAtLastGrid = true;
				} else {
					long qTick = qtm.quantize(t, part);
					if (t == qTick) {
						// this tick is on the grid
						if (current.note.id != noteID) {
							current = createBentSubNote(be, noteID, current, t);
							if (current == null)
								return new ArrayList<>();
							benders.add(current);
							changeAtLastGrid = true;
						} else {
							changeAtLastGrid = false;
						}
						lastGridTick = t;
					} else if (!changeAtLastGrid && entry != null && current.note.id != noteID) {
						long grid = qtm.getGridSizeTicks(t, part);
						if (grid >= 3 && t < lastGridTick + grid / 3L) {
							/*
							 * We have a pitch change, and we are less than a 3rd of a gridlength from last gridpoint.
							 * Last grid point there was no pitch changes. So we round this pitch change back to last
							 * gridpoint.
							 */
							current = createBentSubNote(be, noteID, current, lastGridTick);
							if (current == null)
								return new ArrayList<>();
							benders.add(current);
							changeAtLastGrid = true;
						}
					}
				}
			}
			//double dura = be.getLengthMicros() / 1000.0d;
			//System.out.println(dura+" Note split into "+benders.size()+" bends");
			//if (be.getStartTick() != benders.get(0).getStartTick() || be.getEndTick() != benders.get(benders.size()-1).getEndTick()) {
			//	System.out.println("\nNote split wrongly "+be.getStartTick()+" to "+be.getEndTick());
			//	System.out.println("        == "+benders.get(0).getStartTick()+" to "+benders.get(benders.size()-1).getEndTick());
			//}
			//if (benders.size() == 0) {
			//	System.out.println(" empty benders");
			//}
			return benders;
		} else {
			return null;
		}
	}

	private NoteEvent createBentSubNote(BentNoteEvent be, int noteID, NoteEvent current, long tick) {
		if (current != null) {
			current.setEndTick(tick);
		}
		Note newNote = Note.fromId(noteID);
		if (newNote == null) {
			System.out.println("Note removed, pitch bend out of range");
			return null;
		}
		assert newNote != Note.REST;
		NoteEvent sub = new NoteEvent(newNote, be.velocity, tick, be.getEndTick(), be.getTempoCache());
		sub.setMidiPan(be.midiPan);
		return sub;
	}

	private void breakLongNotes(AbcPart part, List<NoteEvent> events, boolean addTies) {
		for (int i = 0; i < events.size(); i++) {
			NoteEvent ne = events.get(i);
			TimingInfo tm = qtm.getTimingInfo(ne.getStartTick(), part);

			long maxNoteEndTick = qtm.quantize(
					qtm.microsToTick(
							ne.getStartMicros() + (long) (TimingInfo.LONGEST_NOTE_MICROS * qtm.getExportTempoFactor())),
					part);
			
			// quantize:            tunedit + mixtimings 
			// microsToTick:        tunedit + mixtimings
			// getStartMicros:      tunedit + mixtimings
			// LONGEST_NOTE_MICROS: tunedit + mixtimings + tempoedit (hence why export tempo factor is applied onto it

			// Make a hard break for notes that are longer than LotRO can play
			// Bagpipe notes up to B2 can sustain indefinitely; don't break them
			if (ne.getEndTick() > maxNoteEndTick && ne.note != Note.REST
					&& !(part.getInstrument() == LotroInstrument.BASIC_BAGPIPE
							&& ne.note.id <= AbcConstants.BAGPIPE_LAST_DRONE_NOTE_ID)) {

				// Align with a bar boundary if it extends across 1 or more full bars.
				long endBarTick = qtm.tickToBarStartTick(maxNoteEndTick);

				long slipMicros = qtm.tickToMicrosABC(maxNoteEndTick) - qtm.tickToMicrosABC(endBarTick);

				if (qtm.tickToBarEndTick(ne.getStartTick()) < endBarTick
						&& slipMicros < AbcConstants.ONE_SECOND_MICROS) {
					maxNoteEndTick = qtm.quantize(endBarTick, part);
					assert ne.getEndTick() > maxNoteEndTick;
				}

				// If the note is a rest or sustainable, add another one after
				// this ends to keep it going...
				if (ne.note == Note.REST || part.getInstrument().isSustainable(ne.note.id)) {
					// TODO: When making DP-CIT this assert kicks in at 51 BPM. But why..
					//assert (ne.getEndTick() - maxNoteEndTick >= qtm.getTimingInfo(maxNoteEndTick, part)
					//		.getMinNoteLengthTicks());
					NoteEvent next = new NoteEvent(ne.note, ne.velocity, maxNoteEndTick, ne.getEndTick(), qtm);
					next.origPitch = ne.origPitch;
					int ins = Collections.binarySearch(events, next);
					if (ins < 0)
						ins = -ins - 1;
					assert (ins > i);
					events.add(ins, next);

					/*
					 * If the final note is less than a full bar length, just tie it to the original note rather than
					 * creating a hard break. We don't want the last piece of a long sustained note to be a short blast.
					 * LOTRO won't complain about a note being too long if it's part of a tie.
					 */
					TimingInfo tmNext = qtm.getTimingInfo(next.getStartTick(), part);
					if (next.getLengthTicks() < tmNext.getBarLengthTicks() && ne.note != Note.REST) {
						next.tiesFrom = ne;
						ne.tiesTo = next;
					}
					ne.continues = next.getLengthTicks();// needed for pruning
				}

				ne.setEndTick(maxNoteEndTick);
			}

			if (addTies) {
				// Tie notes across bar boundaries
				long targetEndTick = Math.min(ne.getEndTick(),
						qtm.quantize(qtm.tickToBarEndTick(ne.getStartTick()), part));
				if (targetEndTick < ne.getStartTick() + tm.getMinNoteLengthTicks()) {
					// Mix Timings can cause code to come here.
					targetEndTick = ne.getStartTick() + tm.getMinNoteLengthTicks();
				}
				assert (targetEndTick <= ne.getEndTick());
				assert (targetEndTick >= ne.getStartTick() + tm.getMinNoteLengthTicks());

				// Tie notes across tempo boundaries
				final QuantizedTimingInfo.TimingInfoEvent nextTempoEvent = qtm.getNextTimingEvent(ne.getStartTick(),
						part);
				if (nextTempoEvent != null && nextTempoEvent.tick < targetEndTick) {
					targetEndTick = nextTempoEvent.tick;
					assert (targetEndTick - ne.getStartTick() >= tm.getMinNoteLengthTicks());
					assert (ne.getEndTick() - targetEndTick >= nextTempoEvent.info.getMinNoteLengthTicks());
				}

				// If remaining bar is larger than 5s, then split rests earlier (and yes, have
				// seen this happen for 8s+ -aifel)
				if (ne.note == Note.REST && targetEndTick > qtm.microsToTick(qtm.tickToMicros(ne.getStartTick())
						+ (long) (TimingInfo.LONGEST_NOTE_MICROS * qtm.getExportTempoFactor()))) {
					// Rest longer than 5s, split it at 4s:
					targetEndTick = qtm.quantize(
							qtm.microsToTick(qtm.tickToMicros(ne.getStartTick())
									+ (long) (0.5f * AbcConstants.LONGEST_NOTE_MICROS * qtm.getExportTempoFactor())),
							part);
				}

				/*
				 * Make sure that quarter notes start on quarter-note boundaries within the bar, and that eighth notes
				 * start on eight-note boundaries, and so on. Add a tie at the boundary if they start past the boundary.
				 */
				if (!qtm.isMixTiming()) {// This is only to prettify output, we omit this from Mix Timing since bars
											// follow default timing, and notes might be in odd timing.
					long barStartTick = qtm.tickToBarStartTick(ne.getStartTick());
					long gridTicks = tm.getMinNoteLengthTicks();
					long wholeNoteTicks = tm.getBarLengthTicks() * tm.getMeter().denominator / tm.getMeter().numerator;

					// Try unit note lengths of whole, then half, quarter, eighth, sixteenth, etc.
					for (long unitNoteTicks = wholeNoteTicks; unitNoteTicks > gridTicks * 2; unitNoteTicks /= 2) {
						// Check if this note starts on the current unit-note grid
						final long startTickInsideBar = ne.getStartTick() - barStartTick;
						if (Util.floorGrid(startTickInsideBar, unitNoteTicks) == startTickInsideBar) {
							// Ok, this note starts on this unit grid, now make sure it ends on the next
							// unit grid. If it ends before the next unit grid, keep halving the length.
							if (targetEndTick >= ne.getStartTick() + unitNoteTicks) {
								// Exception: dotted notes (1.5x the unit grid) are ok
								if (targetEndTick != ne.getStartTick() + (unitNoteTicks * 3 / 2))
									targetEndTick = ne.getStartTick() + unitNoteTicks;

								break;
							}
						}
					}
				}

				if (ne.getEndTick() > targetEndTick) {
					assert (ne.getEndTick() - targetEndTick >= qtm.getTimingInfo(targetEndTick, part)
							.getMinNoteLengthTicks());
					assert (targetEndTick - ne.getStartTick() >= tm.getMinNoteLengthTicks());
					NoteEvent next = ne.splitWithTieAtTick(targetEndTick);
					int ins = Collections.binarySearch(events, next);
					if (ins < 0)
						ins = -ins - 1;
					assert (ins > i);
					events.add(ins, next);
				}
			}
			assert ((part.delay > 0 && !addTies) || ne.getLengthTicks() >= tm.getMinNoteLengthTicks());
		}
	}

	/** Removes a note and breaks any ties the note has. */
	private void removeNote(List<NoteEvent> events, int i) {
		NoteEvent ne = events.remove(i);

		// If the note is tied from another (previous) note, break the incoming tie
		if (ne.tiesFrom != null) {
			ne.tiesFrom.tiesTo = null;
			ne.tiesFrom = null;
		}

		// Remove the remainder of the notes that this is tied to (if any)
		for (NoteEvent neTie = ne.tiesTo; neTie != null; neTie = neTie.tiesTo) {
			events.remove(neTie);
		}
	}

	private void removeNotes(List<NoteEvent> events, List<NoteEvent> notes, AbcPart part) {
		for (NoteEvent ne : notes) {

			// If the note is tied from another (previous) note, break the incoming tie
			if (ne.tiesFrom != null) {
				ne.tiesFrom.tiesTo = null;
				ne.tiesFrom = null;
			} /*
				 * else if (ne.origEvent != null && showPruned) { for (NoteEvent neo : ne.origEvent) { neo.prune(part);
				 * } }
				 */

			// Remove the remainder of the notes that this is tied to (if any)
			for (NoteEvent neTie = ne.tiesTo; neTie != null; neTie = neTie.tiesTo) {
				events.remove(neTie);
			}
			ne.tiesTo = null;
		}
	}

	/** Removes a note and breaks any ties the note has. */
	private void removeNote(List<NoteEvent> events, NoteEvent ne) {
		removeNote(events, events.indexOf(ne));
	}

	/**
	 * 
	 * @param lengthenToBar lengthen ending to bar
	 * @param accountForSustain lengthen to allow preview midi playback to decay
	 * @return
	 */
	public Pair<Long, Long> getSongStartEndTick(boolean lengthenToBar, boolean accountForSustain, boolean debug) {
		// Remove silent bars before the song starts
		long startTick = skipSilenceAtStart ? Long.MAX_VALUE : 0;
		long endTick = Long.MIN_VALUE;
		for (AbcPart part : parts) {
			if (skipSilenceAtStart) {
				long firstNoteStart = part.firstNoteStartTick();
				if (firstNoteStart < startTick) {
					startTick = firstNoteStart;
				}
			}

			long lastNoteEnd = part.lastNoteEndTick(accountForSustain);
			if (lastNoteEnd > endTick) {
				endTick = lastNoteEnd;
			}
		}

		if (startTick == Long.MAX_VALUE)
			startTick = 0;
		if (endTick == Long.MIN_VALUE)
			endTick = 0;				

		// Remove integral number of bars
		long q = qtm.tickToBarStartTick(startTick);
		firstBarNumber = qtm.tickToBarNumber(q);
		long startTickFinal = qtm.quantizeDown(q);
		if (debug) {
			System.out.println(metadata.getSongTitle()+": firstBar "+firstBarNumber+"  q="+q+" startTick="+startTick+" startTickfinal="+startTickFinal+"\n"+qtm.getTimingEventForTick(q)+"\n"+qtm.getTimingEventForTick(q).info+"\n"+qtm.getTimingEventForTick(q).infoOdd);
			System.out.println("Bar 1 starts at "+qtm.barNumberToBarStartTick(0)+" "+(qtm.barNumberToMicrosecond(0)/1000000.0));
			System.out.println("Bar 2 starts at "+qtm.barNumberToBarStartTick(1)+" "+(qtm.barNumberToMicrosecond(1)/1000000.0));
			System.out.println("Bar 3 starts at "+qtm.barNumberToBarStartTick(2)+" "+(qtm.barNumberToMicrosecond(2)/1000000.0)+"\n\n\n\n\n\n");
		}
		
		if (lengthenToBar) {
			// Lengthen to an integral number of bars
			endTick = qtm.quantizeUp(qtm.tickToBarEndTick(endTick));
		} else {
			endTick = qtm.quantizeUp(endTick);
		}
		return new Pair<>(startTickFinal, endTick);
	}

	private static class Triplet<T, U, V> {

		public final T first;
		public final U second;
		public final V third;

		public Triplet(T first, U second, V third) {
			this.first = first;
			this.second = second;
			this.third = third;
		}

		/*
		 * public T getFirst() { return first; } public U getSecond() { return second; } public V getThird() { return
		 * third; }
		 */
	}
}
