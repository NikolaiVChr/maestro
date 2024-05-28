package com.digero.maestro.midi;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiFileFormat;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
import javax.sound.midi.Track;

import com.digero.common.abctomidi.AbcInfo;
import com.digero.common.abctomidi.AbcToMidi;
import com.digero.common.midi.MidiConstants;
import com.digero.common.midi.ExtensionMidiInstrument;
import com.digero.common.midi.KeySignature;
import com.digero.common.midi.MidiFactory;
import com.digero.common.midi.MidiStandard;
import com.digero.common.midi.MidiUtils;
import com.digero.common.midi.TimeSignature;
import com.digero.common.util.Pair;
import com.digero.common.util.ParseException;
import com.digero.maestro.abc.AbcConversionException;
import com.digero.maestro.abc.AbcExporter;
import com.digero.maestro.abc.AbcExporter.ExportTrackInfo;
import com.digero.maestro.abc.AbcMetadataSource;
import com.digero.maestro.view.MiscSettings;

/**
 * Container for a MIDI sequence. If necessary, converts type 0 MIDI files to type 1.
 */
public class SequenceInfo implements MidiConstants {
	private final Sequence sequence;
	private final SequenceDataCache sequenceCache;
	private final String fileName;
	private String title;
	private String composer;
	public MidiStandard standard = MidiStandard.GM;
	public boolean hasPorts = false;
	public int midiType = -1;// -1 = abc, 0 = type 0, 1 = type 1, 2 = type 2
	private boolean[] rolandDrumChannels = new boolean[CHANNEL_COUNT_ABC];// Which of the channels GS designates as
																			// drums
	private boolean[] yamahaDrumChannels = new boolean[CHANNEL_COUNT_ABC];// Which of the channels XG designates as
																			// drums
	private ArrayList<TreeMap<Long, Boolean>> yamahaDrumSwitches = null;// Which channel/tick XG switches to drums
																		// outside of designated drum channels
	private ArrayList<TreeMap<Long, Boolean>> mmaDrumSwitches = null;// Which channel/tick GM2 switches to drums outside
																		// of designated drum channels
	private int primaryTempoMPQ;
	private final List<TrackInfo> trackInfoList;
	private TreeMap<Integer, Integer> portMap = new TreeMap<>();
	public boolean keep;
	int zeroNotes = 0;
	public static List<ExportTrackInfo> lastTrackInfos = null;

	/**
	 * Create instance of this class while creating MIDI sequence from abc file.
	 * 
	 * @param params
	 * @param miscSettings
	 * @return instance of SequenceInfo
	 * @throws InvalidMidiDataException
	 * @throws ParseException
	 */
	public static SequenceInfo fromAbc(AbcToMidi.Params params, MiscSettings miscSettings, boolean oldVelocities)
			throws InvalidMidiDataException, ParseException {
		if (params.abcInfo == null)
			params.abcInfo = new AbcInfo();
		SequenceInfo sequenceInfo = new SequenceInfo(params.filesData.get(0).file.getName(), AbcToMidi.convert(params),
				-1, miscSettings, oldVelocities);
		sequenceInfo.title = params.abcInfo.getTitle();
		sequenceInfo.composer = params.abcInfo.getComposer();
		sequenceInfo.primaryTempoMPQ = (int) Math.round(MidiUtils.convertTempo(params.abcInfo.getPrimaryTempoBPM()));
		return sequenceInfo;
	}

	/**
	 * Create instance of this class while creating sequence from MIDI file
	 * 
	 * @param midiFile
	 * @param miscSettings
	 * @return
	 * @throws InvalidMidiDataException
	 * @throws IOException
	 * @throws ParseException
	 */
	public static SequenceInfo fromMidi(File midiFile, MiscSettings miscSettings, boolean oldVelocities)
			throws InvalidMidiDataException, IOException, ParseException {
		MidiFileFormat midiFileFormat = MidiSystem.getMidiFileFormat(midiFile);
		return new SequenceInfo(midiFile.getName(), ConvertPPQ.convert(MidiSystem.getSequence(midiFile)),
				midiFileFormat.getType(), miscSettings, oldVelocities);
	}

	/**
	 * Create instance of this class while creating preview MIDI file
	 * 
	 * @param abcExporter
	 * @param useLotroInstruments
	 * @return
	 * @throws InvalidMidiDataException
	 * @throws AbcConversionException
	 */
	public static SequenceInfo fromAbcParts(AbcExporter abcExporter, boolean useLotroInstruments, boolean oldVelocities)
			throws InvalidMidiDataException, AbcConversionException {
		return new SequenceInfo(abcExporter, useLotroInstruments);
	}

	private SequenceInfo(String fileName, Sequence sequence, int type, MiscSettings miscSettings, boolean oldVelocities)
			throws InvalidMidiDataException, ParseException {
		this.fileName = fileName;
		this.sequence = sequence;
		this.midiType = type;
		// System.err.println("MIDI Type = "+type);

		determineStandard(sequence, fileName);

		// Since the drum track separation is only applicable to type 1 midi sequences,
		// do it before we convert this sequence to type 1, to avoid doing unnecessary work
		// Aifel: changed order so that XG drums in middle of a track from a type 0 gets separated out
		boolean wasType0 = convertToType1(sequence);

		separateDrumTracks(sequence);

		fixupTrackLength(sequence);

		Track[] tracks = sequence.getTracks();
		if (tracks.length == 0) {
			throw new InvalidMidiDataException("The MIDI file doesn't have any tracks");
		}

		sequenceCache = new SequenceDataCache(sequence, standard, rolandDrumChannels, yamahaDrumSwitches,
				yamahaDrumChannels, mmaDrumSwitches, portMap);
		hasPorts = sequenceCache.hasPorts;
		primaryTempoMPQ = sequenceCache.getPrimaryTempoMPQ();

		List<TrackInfo> trackInfoList = new ArrayList<>(tracks.length);
		for (int i = 0; i < tracks.length; i++) {
			trackInfoList.add(new TrackInfo(this, tracks[i], i, sequenceCache, sequenceCache.isXGDrumsTrack(i),
					sequenceCache.isGSDrumsTrack(i), wasType0, sequenceCache.isDrumsTrack(i),
					sequenceCache.isGM2DrumsTrack(i), portMap, miscSettings, oldVelocities));
		}

		composer = "";
		/*
		 * if (trackInfoList.get(0).hasName()) { title = trackInfoList.get(0).getName(); }
		 */
		title = fileName;
		int dot = title.lastIndexOf('.');
		if (dot > 0)
			title = title.substring(0, dot);
		title = title.replace('_', ' ');
		title = title.replace("�", "-");// replace long dash with normal ascii dash

		String[] array = title.split("-", 2);
		if (array.length > 1 && array[0].length() > 0 && array[1].length() > 0) {
			composer = array[0].trim();
			title = array[1].trim();
		}

		this.trackInfoList = Collections.unmodifiableList(trackInfoList);
		if (!getTimeSignature().equals(sequenceCache.getTimeSignature())) {
			// If see this output then..
			System.out.println("Time signature does not match between SequenceInfo (" + getTimeSignature()
					+ ") and SequenceDataCache (" + sequenceCache.getTimeSignature() + ").");
		}
	}

	/**
	 * This constructor ignores most of the data, as preview is only used for playback
	 * 
	 * @param abcExporter
	 * @param useLotroInstruments
	 * @throws InvalidMidiDataException
	 * @throws AbcConversionException
	 */
	private SequenceInfo(AbcExporter abcExporter, boolean useLotroInstruments)
			throws InvalidMidiDataException, AbcConversionException {
		AbcMetadataSource metadata = abcExporter.getMetadataSource();
		this.fileName = metadata.getSongTitle() + ".abc";
		this.composer = metadata.getComposer();
		this.title = metadata.getSongTitle();

		Pair<List<ExportTrackInfo>, Sequence> result = abcExporter.exportToPreview(useLotroInstruments);

		sequence = result.second;
		lastTrackInfos = result.first;
		standard = MidiStandard.PREVIEW;
		sequenceCache = new SequenceDataCache(sequence, standard, null, null, null, null, portMap);
		primaryTempoMPQ = sequenceCache.getPrimaryTempoMPQ();

		this.trackInfoList = null;
	}

	public String getFileName() {
		return fileName;
	}

	public Sequence getSequence() {
		return sequence;
	}

	public String getTitle() {
		return title;
	}

	public String getComposer() {
		return composer;
	}

	public int getTrackCount() {
		return trackInfoList.size();
	}

	public TrackInfo getTrackInfo(int track) {
		return trackInfoList.get(track);
	}

	public List<TrackInfo> getTrackList() {
		return trackInfoList;
	}

	public int getPrimaryTempoMPQ() {
		return primaryTempoMPQ;
	}

	public int getPrimaryTempoBPM() {
		return (int) Math.round(MidiUtils.convertTempo(getPrimaryTempoMPQ()));
	}

	public boolean hasTempoChanges() {
		return sequenceCache.getTempoEvents().size() > 1;
	}

	public KeySignature getKeySignature() {
		for (TrackInfo track : trackInfoList) {
			if (track.getKeySignature() != null)
				return track.getKeySignature();
		}
		return KeySignature.C_MAJOR;
	}

	public TimeSignature getTimeSignature() {
		for (TrackInfo track : trackInfoList) {
			if (track.getTimeSignature() != null)
				return track.getTimeSignature();
		}
		return sequenceCache.getTimeSignature();
	}

	public SequenceDataCache getDataCache() {
		return sequenceCache;
	}

	@Override
	public String toString() {
		return getTitle();
	}

	public long calcFirstNoteTick() {
		long firstNoteTick = Long.MAX_VALUE;
		for (Track t : sequence.getTracks()) {
			for (int j = 0; j < t.size(); j++) {
				MidiEvent evt = t.get(j);
				MidiMessage msg = evt.getMessage();
				if (msg instanceof ShortMessage) {
					ShortMessage m = (ShortMessage) msg;
					if (m.getCommand() == ShortMessage.NOTE_ON) {
						if (evt.getTick() < firstNoteTick) {
							firstNoteTick = evt.getTick();
						}
						break;
					}
				}
			}
		}
		if (firstNoteTick == Long.MAX_VALUE)
			return 0;
		return firstNoteTick;
	}

	public long calcLastNoteTick() {
		long lastNoteTick = 0;
		for (Track t : sequence.getTracks()) {
			for (int j = t.size() - 1; j >= 0; j--) {
				MidiEvent evt = t.get(j);
				MidiMessage msg = evt.getMessage();
				if (msg instanceof ShortMessage) {
					ShortMessage m = (ShortMessage) msg;
					if (m.getCommand() == ShortMessage.NOTE_OFF) {
						if (evt.getTick() > lastNoteTick) {
							lastNoteTick = evt.getTick();
						}
						break;
					}
				}
			}
		}

		return lastNoteTick;
	}

	/**
	 * Determine which MIDI variant we are dealing with.
	 * 
	 * Also figure out which channels GS, GM2 or XG has marked as drum channels. And if there are switches to drums in
	 * middle of some tracks.
	 * 
	 * @param seq
	 * @param fileName
	 */
	private void determineStandard(Sequence seq, String fileName) {

		if (fileName.endsWith(".abc") || fileName.endsWith(".ABC") || fileName.endsWith(".txt")
				|| fileName.endsWith(".TXT") || fileName.endsWith(".Abc") || fileName.endsWith(".Txt")) {
			standard = MidiStandard.ABC;
			return;
		}

		// sysex GM reset: F0 7E dv 09 01 F7 (dv = device ID)
		// sysex GM2 reset: F0 7E dv 09 03 F7 (dv = device ID)
		// sysex Yamaha XG: F0 43 dv md 00 00 7E 00 F7 (dv = device ID, md = model id)
		// sysex Roland GS: F0 41 dv 42 12 40 00 7F 00 sm F7 (dv = device ID, sm = checksum)

		// sysex GS switch channel to/from drums:
		// [ F0 41 dv 42 12 40 1x 15 mm sm F7 ]
		// x : 1 - 9 => 0 - 8 channel / 0 => 9 channel / A - F => 10 - 15 channel
		// mm : 0 => normal part / 1,2 => set to drum track
		// sm: checksum
		// dv: device ID

		// sysex XG switch channel to/from drums:
		// F0,43,dv,md,08,ch,07,xx,F7 (dv = device ID, md = model id, ch = channel, xx = drum mode)

		// sysex XG drum part protect mode:
		// F0 43 dv md 00 00 07 pp F7 (dv = device ID, md = model id, pp = 0 is off, 1 is on)
		// If ON then only MSB 126/127 on chan #10. (unless by sysex bank change). XG Reset is counted as protect ON.
		// Ignoring this sysex as I have tested 130,000 midi files and none of them had this, so its super rare.

		// sysex XG MSB bank change:
		// F0 43 dv md 08 nn 01 bb F7 (dv = device ID, md = model id, bb = MSB, nn = 0=non-chan#10 7F=chan#10) [However
		// the real nn is just channel
		// number]

		// sysex XG LSB bank change:
		// F0 43 dv md 08 nn 02 bb F7 (dv = device ID, md = model id, bb = LSB, nn = default 0)

		// sysex XG program change:
		// F0 43 dv md 08 nn 03 pp F7 (dv = device ID, md = model id, pp = patch, nn = default 0)

		standard = MidiStandard.GM;

		for (int i = 0; i < CHANNEL_COUNT_ABC; i++) {
			rolandDrumChannels[i] = false;
		}
		rolandDrumChannels[DRUM_CHANNEL] = true;

		for (int i = 0; i < CHANNEL_COUNT_ABC; i++) {
			yamahaDrumChannels[i] = false;
		}
		yamahaDrumChannels[DRUM_CHANNEL] = true;

		Track[] tracks = seq.getTracks();
		long lastResetTick = -10000;
		TreeMap<Long, PatchEntry> bankAndPatchTrack = new TreeMap<>();// Maps cannot have duplicate entries, so using a
																		// PatchEntry class to store.

		// System.err.println("\nDetermineStandard:");

		/*
		 * 
		 * Iterate and find all Resets and assignments to rhythm channels.
		 * 
		 * 
		 */
		for (Track track : tracks) {
			for (int j = 0; j < track.size(); j++) {
				MidiEvent evt = track.get(j);
				MidiMessage msg = evt.getMessage();
				if (msg instanceof SysexMessage) {
					SysexMessage sysex = (SysexMessage) msg;
					byte[] message = sysex.getMessage();

					/*
					 * StringBuilder sb = new StringBuilder(); for (byte b : message) { sb.append(String.format("%02X ",
					 * b)); } System.err.println("SYSEX on track "+i+": "+sb.toString());
					 */

					// the "& 0xFF" is to convert to unsigned int from signed byte.
					if (message.length == 9 && (message[0] & 0xFF) == 0xF0 && (message[1] & 0xFF) == 0x43
							&& (message[4] & 0xFF) == 0x00 && (message[5] & 0xFF) == 0x00 && (message[6] & 0xFF) == 0x7E
							&& (message[7] & 0xFF) == 0x00 && (message[8] & 0xFF) == 0xF7) {
						if (MidiStandard.GM != standard && MidiStandard.XG != standard) {
							System.err
									.println(fileName + ": MIDI XG Reset in a " + standard + " file. This is unusual!");
						}
						if (evt.getTick() > lastResetTick) {
							lastResetTick = evt.getTick();
							standard = MidiStandard.XG;
						} else if (MidiStandard.GS == standard && evt.getTick() == lastResetTick) {
							System.err.println(
									"They are at same tick. Statistically bigger chance its a GS, so not switching to XG.");
						} else if (MidiStandard.GM2 == standard && evt.getTick() == lastResetTick) {
							System.err.println(
									"They are at same tick. Statistically bigger chance its a XG, so switching to that.");
							lastResetTick = evt.getTick();
							standard = MidiStandard.XG;
						}
						ExtensionMidiInstrument.getInstance();
						// System.err.println("Yamaha XG Reset, tick "+evt.getTick());
					} else if (message.length == 11 && (message[0] & 0xFF) == 0xF0 && (message[1] & 0xFF) == 0x41
							&& (message[3] & 0xFF) == 0x42 && (message[4] & 0xFF) == 0x12 && (message[5] & 0xFF) == 0x40
							&& (message[6] & 0xFF) == 0x00 && (message[7] & 0xFF) == 0x7F && (message[8] & 0xFF) == 0x00
							&& (message[10] & 0xFF) == 0xF7) {
						if (MidiStandard.GM != standard && MidiStandard.GS != standard) {
							System.err
									.println(fileName + ": MIDI GS Reset in a " + standard + " file. This is unusual!");
						}
						if (evt.getTick() >= lastResetTick) {
							lastResetTick = evt.getTick();
							standard = MidiStandard.GS;
						}
						ExtensionMidiInstrument.getInstance();
						// System.err.println("Roland GS Reset, tick "+evt.getTick());
					} else if (message.length == 6 && (message[0] & 0xFF) == 0xF0 && (message[1] & 0xFF) == 0x7E
							&& (message[3] & 0xFF) == 0x09 && (message[4] & 0xFF) == 0x03
							&& (message[5] & 0xFF) == 0xF7) {
						if (MidiStandard.GM != standard && MidiStandard.GM2 != standard) {
							System.err.println(
									fileName + ": MIDI GM2 Reset in a " + standard + " file. This is unusual!");
						}
						if (evt.getTick() > lastResetTick) {
							lastResetTick = evt.getTick();
							standard = MidiStandard.GM2;
						} else if (evt.getTick() == lastResetTick && MidiStandard.GM != standard) {
							System.err.println(
									"They are at same tick. Statistically bigger chance its not a GM2, so not switching standard.");
						}
						ExtensionMidiInstrument.getInstance();
						// System.err.println("MIDI GM2 Reset, tick "+evt.getTick());
					} else if (message.length == 11 && (message[0] & 0xFF) == 0xF0 && (message[1] & 0xFF) == 0x41
							&& (message[3] & 0xFF) == 0x42 && (message[4] & 0xFF) == 0x12 && (message[5] & 0xFF) == 0x40
							&& (message[7] & 0xFF) == 0x15 && (message[10] & 0xFF) == 0xF7) {
						boolean toDrums = message[8] == 1 || message[8] == 2;
						int channel = -1;
						if (message[6] == 16) {
							channel = DRUM_CHANNEL;
						} else if (message[6] > 25 && message[6] < 32) {
							channel = message[6] - 16;
						} else if (message[6] > 16 && message[6] < 26) {
							channel = message[6] - 17;
						}
						if (channel != -1 && channel < 16) {
							if (toDrums) {
								// System.err.println("Roland GS sets channel "+(channel+1)+" to drums.");
							} else {
								// System.err.println("Roland GS unsets channel "+(channel+1)+" to drums.");
							}
							rolandDrumChannels[channel] = toDrums;
						}
					} else if (message.length == 9 && (message[0] & 0xFF) == 0xF0 && (message[1] & 0xFF) == 0x43
							&& (message[4] & 0xFF) == 0x08 && (message[6] & 0xFF) == 0x07
							&& (message[8] & 0xFF) == 0xF7) {
						String type = "Normal";
						if (message[5] < 16) {
							// From Tyros 1 data doc: part10=0x02, other parts=0x00. Korg EX-20 say this is channel.
							// TODO: Drum Setup Reset sysex.
							// Sure looks like Korg has it correct, at least for pre Tyros XG standard.
							if (message[7] == 0) {
								type = "Normal";
								yamahaDrumChannels[message[5]] = false;
							} else if (message[7] == 1) {
								type = "Drums";
								yamahaDrumChannels[message[5]] = true;
							} else if (message[7] > 1 && message[7] <= 5) {
								type = "Drums Setup " + (message[7] - 1);
								yamahaDrumChannels[message[5]] = true;
							} else {
								type = "Invalid setup: " + message[7];
							}
							// System.err.println("Yamaha XG setting channel #"+message[5]+" to "+type);
						}
					} else if (message.length == 9 && (message[0] & 0xFF) == 0xF0 && (message[1] & 0xFF) == 0x43
							&& (message[4] & 0xFF) == 0x00 && (message[5] & 0xFF) == 0x00 && (message[6] & 0xFF) == 0x07
							&& (message[8] & 0xFF) == 0xF7) {

						System.err.println(
								fileName + ": Yamaha XG Drum Part Protect mode " + (message[7] == 0 ? "OFF" : "ON"));
					} else if (message.length == 9 && (message[0] & 0xFF) == 0xF0 && (message[1] & 0xFF) == 0x43
							&& (message[4] & 0xFF) == 0x08 && (message[8] & 0xFF) == 0xF7) {
						// XG bank/patch change
						PatchEntry entry = null;
						entry = bankAndPatchTrack.get(evt.getTick());
						if (entry == null) {
							entry = new PatchEntry();
							entry.sysex.add(evt);
							bankAndPatchTrack.put(evt.getTick(), entry);
						} else {
							entry.sysex.add(evt);
						}
					}
				} else if (msg instanceof ShortMessage) {
					ShortMessage m = (ShortMessage) msg;
					int cmd = m.getCommand();

					if (cmd == ShortMessage.PROGRAM_CHANGE) {
						PatchEntry entry = null;
						entry = bankAndPatchTrack.get(evt.getTick());
						if (entry == null) {
							entry = new PatchEntry();
							entry.patch.add(evt);
							bankAndPatchTrack.put(evt.getTick(), entry);
						} else {
							entry.patch.add(evt);
						}
					} else if (cmd == ShortMessage.CONTROL_CHANGE) {
						PatchEntry entry = null;
						entry = bankAndPatchTrack.get(evt.getTick());
						if (entry == null) {
							entry = new PatchEntry();
							entry.bank.add(evt);
							bankAndPatchTrack.put(evt.getTick(), entry);
						} else {
							entry.bank.add(evt);
						}
					}
				}
			}
		}
		yamahaDrumSwitches = new ArrayList<>();
		for (int i = 0; i < CHANNEL_COUNT_ABC; i++) {
			yamahaDrumSwitches.add(new TreeMap<>());
		}
		mmaDrumSwitches = new ArrayList<>();
		for (int i = 0; i < CHANNEL_COUNT_ABC; i++) {
			mmaDrumSwitches.add(new TreeMap<>());
		}

		/**
		 * yamahaBankAndPatchChanges & mmaBankAndPatchChanges:
		 * 
		 * 0 = chromatic voice 1 = Has switched to drums, but patch not selected yet. 2 = drum
		 */
		Integer[] yamahaBankAndPatchChanges = new Integer[CHANNEL_COUNT_ABC];
		Integer[] mmaBankAndPatchChanges = new Integer[CHANNEL_COUNT_ABC];

		for (int i = 0; i < CHANNEL_COUNT_ABC; i++) {
			if (yamahaDrumChannels[i]) {
				yamahaBankAndPatchChanges[i] = 2;
			} else {
				yamahaBankAndPatchChanges[i] = 0;
			}
			if (i == DRUM_CHANNEL) {
				mmaBankAndPatchChanges[i] = 2;
			} else {
				mmaBankAndPatchChanges[i] = 0;
			}
		}

		/*
		 * Iterate again, but this time in order of ticks no matter which track the events come from. This time we find
		 * where there is changes from rhythm to chromatic voices and the other way around. We need that for determining
		 * how to seperate drum tracks and which tracks to mark as drum tracks.
		 * 
		 */
		for (PatchEntry entry : bankAndPatchTrack.values()) {
			List<MidiEvent> masterList = new ArrayList<>();

			// The order here is important, patch must be last, since not all MIDI files adhere to standard of certain
			// time separation between these
			// events:
			// Not sure if sysex bank/patch change have higher priority than Control Change events. But giving it lowest
			// priority for now.
			masterList.addAll(entry.sysex);
			masterList.addAll(entry.bank);
			masterList.addAll(entry.patch);

			for (MidiEvent evt : masterList) {
				MidiMessage msg = evt.getMessage();
				if (msg instanceof SysexMessage) {
					SysexMessage sysex = (SysexMessage) msg;
					byte[] message = sysex.getMessage();
					// we already know that this sysex is a XG bank/patch change, so no need for if statement.
					String bank = message[6] == 1 ? "MSB"
							: (message[6] == 2 ? "LSB" : (message[6] == 3 ? "Patch" : ""));
					if (!"".equals(bank) && message[5] < 16 && message[5] > -1 && message[7] < 128 && message[7] > -1) {
						// System.err.println(fileName+": Yamaha XG Sysex "+bank+" set to "+message[7]+" for channel
						// "+message[5]);
						int ch = message[5];
						if ("MSB".equals(bank)) {
							if (message[7] == 126 || message[7] == 127) {// 64 is chromatic effects, so not testing for
																			// that.
								yamahaBankAndPatchChanges[ch] = 1;
							} else {
								yamahaBankAndPatchChanges[ch] = 0;
							}
						} else if ("Patch".equals(bank)) {
							if (yamahaBankAndPatchChanges[ch] > 0) {
								yamahaBankAndPatchChanges[ch] = 2;
								yamahaDrumSwitches.get(ch).put(evt.getTick(), true);
								// System.err.println(" XG drums in channel "+(ch+1));
							} else if (yamahaBankAndPatchChanges[ch] == 0) {
								yamahaDrumSwitches.get(ch).put(evt.getTick(), false);
								// System.err.println(" channel "+(ch+1)+" changed voice in track "+i);
							}
						}
					}
				} else if (msg instanceof ShortMessage) {
					ShortMessage m = (ShortMessage) msg;
					int cmd = m.getCommand();
					int ch = m.getChannel();

					if (cmd == ShortMessage.PROGRAM_CHANGE) {
						if (yamahaBankAndPatchChanges[ch] > 0) {
							yamahaBankAndPatchChanges[ch] = 2;
							yamahaDrumSwitches.get(ch).put(evt.getTick(), true);
							// if (ch == 9) System.err.println("XG channel "+ch+" changed to drum kit "+m.getData1()+"
							// at tick "+evt.getTick());
						} else if (yamahaBankAndPatchChanges[ch] == 0) {
							yamahaDrumSwitches.get(ch).put(evt.getTick(), false);
							// if (ch == 9) System.err.println("XG channel "+ch+" changed to voice "+m.getData1()+" at
							// tick "+evt.getTick());
						}
						if (mmaBankAndPatchChanges[ch] > 0) {
							mmaBankAndPatchChanges[ch] = 2;
							mmaDrumSwitches.get(ch).put(evt.getTick(), true);
							// System.err.println(" GM2 channel "+ch+" changed kit at tick "+evt.getTick());
						} else if (mmaBankAndPatchChanges[ch] == 0) {
							mmaDrumSwitches.get(ch).put(evt.getTick(), false);
							// System.err.println(" GM2 channel "+ch+" changed voice at tick "+evt.getTick());
						}
					} else if (cmd == ShortMessage.CONTROL_CHANGE) {
						switch (m.getData1()) {
						case BANK_SELECT_MSB:
							if (m.getData2() == 127 || m.getData2() == 126) {// 64 is chromatic effects, so not testing
																				// for that.
								yamahaBankAndPatchChanges[ch] = 1;
							} else {
								yamahaBankAndPatchChanges[ch] = 0;
								// if (ch == 9) System.err.println(" channel "+ch+" changed to voice in track "+i+" to
								// MSB "+m.getData2()+" at tick
								// "+evt.getTick());
							}
							if (m.getData2() == 120) {
								mmaBankAndPatchChanges[ch] = 1;
							} else {
								mmaBankAndPatchChanges[ch] = 0;
							}
							// System.err.println("Channel "+ch+" bank select MSB "+m.getData2()+" at tick
							// "+evt.getTick());
							break;
						case BANK_SELECT_LSB:
							// System.err.println("Bank select LSB "+m.getData2());
							break;
						}
					}
				}
			}
		}
		for (int i = 0; i < CHANNEL_COUNT_ABC; i++) {
			yamahaDrumSwitches.get(i).put(-1L, yamahaDrumChannels[i]);
			if (i == DRUM_CHANNEL) {
				mmaDrumSwitches.get(i).put(-1L, true);
			} else if (i != DRUM_CHANNEL) {
				mmaDrumSwitches.get(i).put(-1L, false);
			}
		}
	}

	/**
	 * Separates the MIDI file to have one track per channel (Type 1).
	 * <p>
	 * If the MIDI is a Type 1, but with only 1 track, it will also be separated.
	 * 
	 */
	public boolean convertToType1(Sequence song) {
		if (song.getTracks().length == 1 && MidiStandard.ABC != standard) {
			Track track0 = song.getTracks()[0];
			Track[] tracks = new Track[CHANNEL_COUNT];

			MidiEvent endOfTrack = null;
			int j = track0.size() - 1;
			while (j >= 0) {
				MidiEvent evt = track0.get(j);
				if (evt.getMessage() instanceof MetaMessage) {
					if (((MetaMessage) evt.getMessage()).getType() == META_END_OF_TRACK) {
						endOfTrack = evt;
						break;
					}
				}
				j--;
			}
			if (endOfTrack == null) {
				// This midi has no EOT, which is in violation of midi standard, so we make one at last tick.
				endOfTrack = MidiFactory.createEndOfTrackEvent(track0.get(track0.size() - 1).getTick());
			}

			int trackNumber = 1;
			int i = 0;
			while (i < track0.size()) {
				MidiEvent evt = track0.get(i);
				if (evt.getMessage() instanceof ShortMessage) {
					int chan = ((ShortMessage) evt.getMessage()).getChannel();
					if (tracks[chan] == null) {
						tracks[chan] = song.createTrack();

						tracks[chan].add(endOfTrack);

						String trackName = "Track " + trackNumber;

						// No reason to call it drum channel now. Drum that are switched to in middle of melodic
						// channels is separated out anyway. If
						// not, then so what..
						// if (standard == "XG" && yamahaDrumSwitches.get(chan).floorEntry(evt.getTick()) != null &&
						// yamahaDrumSwitches.get(chan).floorEntry(evt.getTick()).getValue() == true) {
						// trackName = "XG Drums";
						// } else if (standard == "GM2" && mmaDrumSwitches.get(chan).floorEntry(evt.getTick()) != null
						// &&
						// mmaDrumSwitches.get(chan).floorEntry(evt.getTick()).getValue() == true) {
						// trackName = "GM2 Drums";
						// } else
						/*
						 * if (standard == "XG" && yamahaDrumChannels[chan] == true && chan != DRUM_CHANNEL) { trackName
						 * = "XG Drums"; } else if (standard == "GS" && chan != DRUM_CHANNEL && rolandDrumChannels[chan]
						 * == true) { trackName = "GS Drums"; } else if (chan == DRUM_CHANNEL &&
						 * (rolandDrumChannels[chan] || standard != "GS") && (yamahaDrumChannels[chan] || standard !=
						 * "XG")) { trackName = "Drums"; }
						 */
						trackNumber++;
						tracks[chan].add(MidiFactory.createTrackNameEvent(trackName));
					}
					tracks[chan].add(evt);
					if (track0.remove(evt))
						continue;
				}
				i++;
			}
			return true;
		}
		return false;
	}

	/**
	 * Ensures that there are no tracks with both drums and notes.
	 */
	public void separateDrumTracks(Sequence song) {
		Track[] tracks = song.getTracks();

		if (MidiStandard.ABC == standard) {// || tracks.length <= 1
			return;
		}

		for (int i = 0; i < tracks.length; i++) {
			Track track = tracks[i];

			int GS = 0; // GS drum notes
			int XG = 0; // XG drum notes
			int GM2 = 0; // GM2 drum notes
			int drumsExt9 = 0;// Extension drum notes on default-drum-channel
			int drums = 0; // Drum notes (GM)
			int notes = 0; // Chromatic notes
			int notes9 = 0;// Chromatic notes on default-drum-channel
			int drumsExtX = 0;// Brand drum notes on non-default-drum-channel
			int notesx = 0;// Chromatic notes on non-default-drum-channel

			for (int j = 0; j < track.size(); j++) {
				MidiEvent evt = track.get(j);
				MidiMessage msg = evt.getMessage();
				if (msg instanceof ShortMessage) {
					ShortMessage m = (ShortMessage) msg;
					int chan = m.getChannel();
					if (m.getCommand() == ShortMessage.NOTE_ON) {
						if (chan == DRUM_CHANNEL && MidiStandard.GM == standard) {
							drums = 1;
						} else if (MidiStandard.GS == standard && rolandDrumChannels[chan]) {
							GS = 1;
							if (chan == DRUM_CHANNEL)
								drumsExt9 = 1;
							else
								drumsExtX = 1;
						} else if (MidiStandard.XG == standard
								&& yamahaDrumSwitches.get(chan).floorEntry(evt.getTick()) != null
								&& yamahaDrumSwitches.get(chan).floorEntry(evt.getTick()).getValue()) {
							XG = 1;
							if (chan == DRUM_CHANNEL)
								drumsExt9 = 1;
							else
								drumsExtX = 1;
						} else if (MidiStandard.GM2 == standard
								&& mmaDrumSwitches.get(chan).floorEntry(evt.getTick()) != null
								&& mmaDrumSwitches.get(chan).floorEntry(evt.getTick()).getValue()) {
							GM2 = 1;
							if (chan == DRUM_CHANNEL)
								drumsExt9 = 1;
							else
								drumsExtX = 1;
						} else {
							notes = 1;
							if (chan == DRUM_CHANNEL)
								notes9 = 1;
							else
								notesx = 1;
						}
					}
				}
			}
			/*
			 * I had to design this carefully in high degree to not mess up old Projects too much.
			 * 
			 * If channel 9 drums plus brand drums. Then old would have taken channel 9 and made new track. So in that
			 * case if no real melodic notes make brand drums stay and funnel channel 9 into new channel even if
			 * standard is not GM.
			 * 
			 * If notes+channel 9 drums+brand drums, make notes stay, funnel the others into own 2 tracks. channel 9
			 * drums before brand.
			 * 
			 * If notes on channel 9 and notes that are not channel 9, then separate them also, to keep backwards compat
			 * with old projects.
			 * 
			 */
			if (GS + XG + GM2 + notes + drums > 1 || (drumsExt9 + drumsExtX > 1) || (notes9 + notesx > 1)) {
				Track drumTrack = null;
				Track noteTrack = null;
				Track brandDrumTrack = null;
				if (notes == 1) {
					if (drums == 1) {
						drumTrack = song.createTrack();
						drumTrack.add(MidiFactory.createTrackNameEvent(ExtensionMidiInstrument.TRACK_NAME_DRUM_GM));
						// System.err.println("Notes! Create GM Drum track. From "+i);
					}
					if (notes9 + notesx > 1) {
						noteTrack = song.createTrack();
						noteTrack.add(MidiFactory.createTrackNameEvent("Track " + i + "+"));
						System.err.println("Notes! Create Note ch9 track. From " + i);
					}
					if (XG == 1 || GS == 1 || GM2 == 1) {
						brandDrumTrack = song.createTrack();
						if (XG == 1) {
							brandDrumTrack
									.add(MidiFactory.createTrackNameEvent(ExtensionMidiInstrument.TRACK_NAME_DRUM_XG));
						} else if (GS == 1) {
							brandDrumTrack
									.add(MidiFactory.createTrackNameEvent(ExtensionMidiInstrument.TRACK_NAME_DRUM_GS));
						} else if (GM2 == 1) {
							brandDrumTrack
									.add(MidiFactory.createTrackNameEvent(ExtensionMidiInstrument.TRACK_NAME_DRUM_GM2));
						}
						// System.err.println("Notes! Create EXT Drum track. From "+i);
					}
				} else {
					if (drums == 1) {
						drumTrack = song.createTrack();
						drumTrack.add(MidiFactory.createTrackNameEvent(ExtensionMidiInstrument.TRACK_NAME_DRUM_GM));
						// System.err.println("Create GM Drum track. From "+i);
					} else if (drumsExt9 == 1) {
						brandDrumTrack = song.createTrack();
						if (XG == 1) {
							brandDrumTrack
									.add(MidiFactory.createTrackNameEvent(ExtensionMidiInstrument.TRACK_NAME_DRUM_XG));
						} else if (GS == 1) {
							brandDrumTrack
									.add(MidiFactory.createTrackNameEvent(ExtensionMidiInstrument.TRACK_NAME_DRUM_GS));
						} else if (GM2 == 1) {
							brandDrumTrack
									.add(MidiFactory.createTrackNameEvent(ExtensionMidiInstrument.TRACK_NAME_DRUM_GM2));
						}
						// System.err.println("Create EXT Drum track. Channel 9. From "+i);
					}
				}
				// Mixed track:
				for (int j = 0; j < track.size(); j++) {
					MidiEvent evt = track.get(j);
					MidiMessage msg = evt.getMessage();
					if (msg instanceof ShortMessage) {
						ShortMessage smsg = (ShortMessage) msg;
						int chan = smsg.getChannel();
						if (drumTrack != null && drums == 1 && chan == DRUM_CHANNEL) {
							drumTrack.add(evt);
							if (track.remove(evt))
								j--;
						} else if (brandDrumTrack != null && GS == 1 && (notes == 1 || chan == DRUM_CHANNEL)
								&& rolandDrumChannels[chan]) {
							brandDrumTrack.add(evt);
							if (track.remove(evt))
								j--;
						} else if (brandDrumTrack != null && XG == 1 && (notes == 1 || chan == DRUM_CHANNEL)
								&& yamahaDrumSwitches.get(chan).floorEntry(evt.getTick()) != null
								&& yamahaDrumSwitches.get(chan).floorEntry(evt.getTick()).getValue()) {
							brandDrumTrack.add(evt);
							if (track.remove(evt))
								j--;
						} else if (brandDrumTrack != null && GM2 == 1 && (notes == 1 || chan == DRUM_CHANNEL)
								&& mmaDrumSwitches.get(chan).floorEntry(evt.getTick()) != null
								&& mmaDrumSwitches.get(chan).floorEntry(evt.getTick()).getValue()) {
							brandDrumTrack.add(evt);
							if (track.remove(evt))
								j--;
						} else if ((GS == 1 && rolandDrumChannels[chan])
								|| (XG == 1 && yamahaDrumSwitches.get(chan).floorEntry(evt.getTick()) != null
										&& yamahaDrumSwitches.get(chan).floorEntry(evt.getTick()).getValue())
								|| (GM2 == 1 && mmaDrumSwitches.get(chan).floorEntry(evt.getTick()) != null
										&& mmaDrumSwitches.get(chan).floorEntry(evt.getTick()).getValue())) {
							// These non-channel9 drum notes stay in the track.
							// This IF clause is mostly for peace of mind. The chromatic notes will never enter here as
							// 'notes' will be 1 when they
							// are there
							// and therefore no drum notes will reach last IF statement.
						} else if (noteTrack != null && chan == DRUM_CHANNEL) {
							noteTrack.add(evt);
							if (track.remove(evt))
								j--;
						}
					}
				}
			}
		}
	}

	/**
	 * This method will move meta messages to the last non-meta message tick. It will also add any missing End Of Track
	 * messages
	 * 
	 * @param song
	 */
	@SuppressWarnings("unchecked") //
	public static void fixupTrackLength(Sequence song) {
//		System.out.println("Before: " + Util.formatDuration(song.getMicrosecondLength()));
//		TempoCache tempoCache = new TempoCache(song);
		Track[] tracks = song.getTracks();
		List<MidiEvent>[] suspectEvents = new List[tracks.length];
		long endTick = 0;

		for (int i = 0; i < tracks.length; i++) {
			Track track = tracks[i];
			for (int j = track.size() - 1; j >= 0; --j) {
				MidiEvent evt = track.get(j);
				if (MidiUtils.isMetaEndOfTrack(evt.getMessage())) {
					if (suspectEvents[i] == null)
						suspectEvents[i] = new ArrayList<>();
					suspectEvents[i].add(evt);
				} else if (evt.getTick() > endTick) {
					// Seems like some songs have extra meta messages way past the end
					if (evt.getMessage() instanceof MetaMessage) {
						if (suspectEvents[i] == null)
							suspectEvents[i] = new ArrayList<>();
						suspectEvents[i].add(0, evt);
					} else {
						endTick = evt.getTick();
						break;
					}
				}
			}
		}

		for (int i = 0; i < tracks.length; i++) {
			for (MidiEvent evt : suspectEvents[i]) {
				if (evt.getTick() > endTick) {
					tracks[i].remove(evt);
//					System.out.println("Moving event from "
//							+ Util.formatDuration(MidiUtils.tick2microsecond(song, evt.getTick(), tempoCache)) + " to "
//							+ Util.formatDuration(MidiUtils.tick2microsecond(song, endTick, tempoCache)));
					evt.setTick(endTick);
					tracks[i].add(evt);
				}
			}

			// insert any missing end of track events
			boolean okay = false;
			for (int e = tracks[i].size() - 1; e >= 0; e--) {
				MidiEvent evt = tracks[i].get(e);
				if (MidiUtils.isMetaEndOfTrack(evt.getMessage())) {
					okay = true;
					break;
				}
			}
			if (!okay) {
				long trackEndTick = 0L;
				if (tracks[i].size() > 0)
					trackEndTick = tracks[i].get(tracks[i].size() - 1).getTick();
				MidiEvent end = MidiFactory.createEndOfTrackEvent(trackEndTick + 1);
				tracks[i].add(end);
				System.out.println("Track " + i + " was missing an EndOfTrack. It was now inserted.");
			}
		}

//		System.out.println("Real song duration: "
//				+ Util.formatDuration(MidiUtils.tick2microsecond(song, endTick, tempoCache)));
//		System.out.println("After: " + Util.formatDuration(song.getMicrosecondLength()));
	}

	/**
	 * 
	 * @return the result from splitting tracks with multiple instruments into 1 track per instrument.
	 */
	public Sequence split() {
		TrackSplitter splitter = new TrackSplitter();
		Sequence sequence2 = null;
		try {
			sequence2 = splitter.split(sequence, sequenceCache, standard, rolandDrumChannels, yamahaDrumSwitches,
					yamahaDrumChannels, mmaDrumSwitches, portMap);
		} catch (InvalidMidiDataException e) {
			e.printStackTrace();
		}
		return sequence2;
	}

	private static class PatchEntry {
		public List<MidiEvent> bank = new ArrayList<>();
		public List<MidiEvent> patch = new ArrayList<>();
		public List<MidiEvent> sysex = new ArrayList<>();
	}

	public void setKeepZeroNotes(boolean keep) {
		this.keep = keep;
	}

	public int getNumberOfZeroNotes() {
		return zeroNotes ;
	}
}
