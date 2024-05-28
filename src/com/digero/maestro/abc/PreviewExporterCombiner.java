package com.digero.maestro.abc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.sound.midi.Sequence;

import java.util.Map.Entry;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.midi.MidiConstants;
import com.digero.common.midi.Note;
import com.digero.common.midi.PanGenerator;
import com.digero.common.util.Pair;
import com.digero.common.util.Triple;
import com.digero.maestro.abc.AbcExporter.ExportTrackInfo;
import com.digero.maestro.midi.AbcNoteEvent;
import com.digero.maestro.midi.Chord;

/**
 * This class is for compressing 15+ parts into 15 midi channels using a variety of methods.
 * Since recently Java has been tricked into playing 24 channels, and this class is just legacy code now.
 * 
 * Some day it will be gone..
 */
@Deprecated
public class PreviewExporterCombiner {
	private static final int MAX_PARTS = 24;// MidiConstants.CHANNEL_COUNT - 1;
	// Channel 0 is no longer reserved for
	// metadata, and Track 9 is reserved for drums
	
	private List<Set<AbcPart>> shareChannelWithPatchChangesMap = null;// abcpart 1 can borrow channel from abcpart 2,
	// but need to switch voice all the time.
	private Map<AbcPart, Integer> shareChannelSameVoiceMap = null;// abcpart can use a certain channel (together with
			// another abcpart), no need for voice switching.
	Set<AbcPart> assignedSharingPartsSwitchers = new HashSet<>();// Set of all parts that will share using
					// switching.
	Set<AbcPart> assignedSharingPartsSameVoice = new HashSet<>();// Set of all parts that will share without
					// using switching.
	Set<Integer> assignedChannels = new HashSet<>();// channels that has been assigned one or two parts onto it.
	public int lastChannelUsedInPreview = -1;
	private Map<AbcPart, List<Chord>> chordsMade;
	private Map<AbcPart, TreeMap<Long, Boolean>> toneMap = null;// Tree is when notes are active.
	private int partsCount;
	private List<AbcPart> parts;
	private boolean useLotroInstruments;
	private AbcExporter exporter;
	private static final long PRE_TICK = -1L;

	public PreviewExporterCombiner(Map<AbcPart, List<Chord>> chordsMade, int partsCount, List<AbcPart> parts, boolean useLotroInstruments, AbcExporter exporter) {
		this.chordsMade = chordsMade;
		this.partsCount = partsCount;
		this.parts = parts;
		this.useLotroInstruments = useLotroInstruments;
		this.exporter = exporter;
	}
	
	public void startCombine () throws AbcConversionException {
		
		if (partsCount > MAX_PARTS) {
			int target = partsCount - MAX_PARTS;// How many channels we need to free up.
			// System.out.println("\n\nPreview requested for more than 15 parts. Starting
			// combine algorithms.");
			Pair<Map<AbcPart, Integer>, Integer> shareResult = findSharableParts(target);
			if (shareResult != null) {
				shareChannelSameVoiceMap = shareResult.first;
				assignedSharingPartsSameVoice = shareChannelSameVoiceMap.keySet();
				target = shareResult.second;
			}

			if (target > 0) {
				toneMap = findTones();
				Triple<List<Set<AbcPart>>, Set<AbcPart>, Integer> switchResult = null;
				switchResult = findSharableChannelSwitchers(target, assignedSharingPartsSameVoice);
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
				Triple<List<Set<AbcPart>>, Set<AbcPart>, Integer> switchResult = null;
				switchResult = findSharableChannelSwitchers(target, assignedSharingPartsSameVoice);
				shareChannelWithPatchChangesMap = switchResult.first;
				assignedSharingPartsSwitchers = switchResult.second;
				target = switchResult.third;

				if (target > 0) {
					shareResult = findSharableParts(target);
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
	}
	
	public void endCombine (List<ExportTrackInfo> infoList, PanGenerator panner, Sequence sequence) throws AbcConversionException {
		for (AbcPart part : assignedSharingPartsSameVoice) {
			// Do the parts that is sharing channel first, as they will use the lower
			// (already designated) numbered channels
			int pan = (parts.size() > 1) ? panner.get(part.getInstrument(), part.getTitle(), exporter.stereoPan)
					: PanGenerator.CENTER;
			int chan = shareChannelSameVoiceMap.get(part);
			ExportTrackInfo inf = exporter.exportPartToPreview(part, sequence, pan,
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
						pan = (parts.size() > 1) ? panner.get(part.getInstrument(), part.getTitle(), exporter.stereoPan)
								: PanGenerator.CENTER;
					}
					ExportTrackInfo inf = exporter.exportPartToPreview(part, sequence, pan,
							useLotroInstruments, assignedChannels, chan, true, chordsMade);
					infoList.add(inf);
					// System.out.println(part.getTitle()+" assigned to switch channel
					// "+inf.channel+" on track "+inf.trackNumber);
					assignedChannels.add(inf.channel);
				}
			}
		}
	}
	
	/**
	 * Find parts that can share a channel by switching voice.
	 * 
	 * @param toneMap                     Map of when a part has active notes.
	 * @param target                      Number of pairs that needs to be found.
	 * @param assignedSharingPartsChannel Will not check the parts in this set.
	 * @return Map of pairs of parts that can share channels using this method.
	 */
	private Triple<List<Set<AbcPart>>, Set<AbcPart>, Integer> findSharableChannelSwitchers(int target, Set<AbcPart> assignedSharingPartsChannel) {
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
		return new Triple<>(shareChannelWithPatchChangesMap, assignedSharingPartsSwitchers, target);
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
	private Map<AbcPart, TreeMap<Long, Boolean>> findTones() {
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
	private Pair<Map<AbcPart, Integer>, Integer> findSharableParts(int target) throws AbcConversionException {
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
					for (List<Chord> compareUs : chordsSet) {
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
					AbcNoteEvent evtA = aChord.get(k);
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
						AbcNoteEvent evtB = bChord.get(l);
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
}