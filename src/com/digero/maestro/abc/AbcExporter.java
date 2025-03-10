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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeSet;

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
import com.digero.maestro.abc.QuantizedTimingInfo.TimingInfoEvent;
import com.digero.maestro.midi.AbcNoteEvent;
import com.digero.maestro.midi.BentAbcNoteEvent;
import com.digero.maestro.midi.BentMidiNoteEvent;
import com.digero.maestro.midi.Chord;
import com.digero.maestro.midi.MidiNoteEvent;
import com.digero.maestro.midi.TrackInfo;
import com.sun.media.sound.MidiUtils;

public class AbcExporter {
	private boolean organic = true;
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
	
	// the tempo changes might not be shared evenly among the parts, so this is really only for making abc more readable
	private boolean exportTempos = false;
	
	// Some midis have zero duration notes that should played (this is for organic only)
	private boolean deleteEmptyNotes = false;

	public int stereoPan = 100;// zero is mono, 100 is very wide.
	private int firstBarNumber;

	private int lastChannelUsedInPreview = -1;

	public AbcExporter(List<AbcPart> parts, QuantizedTimingInfo timingInfo, KeySignature keySignature,
			AbcMetadataSource metadata, boolean skipSilenceAtStart) throws AbcConversionException {
		this.parts = parts;
		this.qtm = timingInfo;
		this.metadata = metadata;
		setKeySignature(keySignature);
		
		this.skipSilenceAtStart = skipSilenceAtStart;// getSongStartEndTick needs this so we needed to pass it
		// We use this from AbcSong when getting micros
		Pair<Long, Long> startEndTick = getSongStartEndTick(false, true, false);
		exportStartTick = startEndTick.first;
		exportEndTick = startEndTick.second;
	}

	public Pair<List<ExportTrackInfo>, Sequence> exportToPreview(boolean useLotroInstruments)
			throws AbcConversionException, InvalidMidiDataException {
		try {
			PolyphonyHistogram.clearAll();
			Pair<Long, Long> startEndTick = getSongStartEndTick(false, true, false);
			exportStartTick = startEndTick.first;
			exportEndTick = startEndTick.second;

			Map<AbcPart, List<Chord>> chordsMade = new HashMap<>();// abcexported chords ready to be previewed
			
			
			List<ExportTrackInfo> infoList = new ArrayList<>();
			
			int partsCount = calculatePartsCount(parts);
			if (partsCount == 0) {
				// The point of this is to return a 'null' sequence. That prevents midi sequencer from restarting when changing
				// tempo spinner while no parts are enabled, due to setting a abc sequence.
				for (AbcPart part : parts) {
					try {
						PolyphonyHistogram.count(part, new ArrayList<>(), organic, qtm);
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
			exportForPreviewChords(chordsMade);// export the chords here early, as we possibly
																		// need to process them for sharing.
			
			

			Sequence sequence = new Sequence(Sequence.PPQ, qtm.getMidiResolution());

			// Track 0: Title and meta info
			Track track0 = sequence.createTrack();
			track0.add(MidiFactory.createTrackNameEvent(metadata.getSongTitle()));
			addMidiTempoEvents(track0);

			PanGenerator panner = new PanGenerator();
			
			lastChannelUsedInPreview = -1;			
			
			for (AbcPart part : parts) {
				
				if (part.getEnabledTrackCount() > 0) {
					int pan = (parts.size() > 1) ? panner.get(part.getInstrument(), part.getTitle(), stereoPan)
							: PanGenerator.CENTER;
					
					ExportTrackInfo inf = exportPartToPreview(part, sequence, pan,
							useLotroInstruments, chordsMade);
					infoList.add(inf);
					// System.out.println(part.getTitle()+" assigned to channel "+inf.channel+" on track
					// "+inf.trackNumber);
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

	/**
	 * Build all the preview chords here.
	 * 
	 * @param chordsMade      the map of lists of chord that need to be filled.
	 * @param exportStartTick
	 * @param exportEndTick
	 * @throws AbcConversionException
	 */
	private void exportForPreviewChords(Map<AbcPart, List<Chord>> chordsMade)
			throws AbcConversionException {
		for (AbcPart part : parts) {
			if (part.getEnabledTrackCount() > 0) {
				if (organic) {
					List<Chord> chords = combineOrganic(part, true);
					chordsMade.put(part, chords);
				} else {
					List<Chord> chords = combineAndQuantize(part, true);
					chordsMade.put(part, chords);
				}
			} else {
				try {
					PolyphonyHistogram.count(part, new ArrayList<>(), organic, qtm);
				} catch (IOException e) {
					throw new AbcConversionException("Failed to read instrument sample durations.", e);
				}
				chordsMade.put(part, null);
			}
		}
	}

	ExportTrackInfo exportPartToPreview(AbcPart part, Sequence sequence,
			int pan, boolean useLotroInstruments, 
			Map<AbcPart, List<Chord>> chordsMade) throws AbcConversionException {
		List<Chord> chords = chordsMade.get(part);

		Pair<Integer, Integer> trackNumber = exportPartToMidi(part, sequence, chords, pan, useLotroInstruments);

		List<AbcNoteEvent> noteEvents = new ArrayList<>(chords.size());
		for (Chord chord : chords) {
			for (int i = 0; i < chord.size(); i++) {
				AbcNoteEvent ne = chord.get(i);
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
			boolean useLotroInstruments) {
		part.numberOfExportedNotes = 0;
		int trackNumber = out.getTracks().length;
		part.setPreviewSequenceTrackNumber(trackNumber);

		int channel = lastChannelUsedInPreview + 1;
		
		if (channel == MidiConstants.DRUM_CHANNEL) {
			channel++;
		}
		// System.out.println("Channel using "+channel);
		lastChannelUsedInPreview = Math.max(channel, lastChannelUsedInPreview);

		Track track = out.createTrack();

		track.add(MidiFactory.createTrackNameEvent(part.getTitle()));
		if (useLotroInstruments) {
			// Only change the channel voice once
			track.add(MidiFactory.createLotroChangeEvent(part.getInstrument().midi.id(), channel, 0));
			// System.out.println("Channel "+channel+" for "+part.getInstrument());
			track.add(MidiFactory.createChannelVolumeEvent(MidiConstants.MAX_VOLUME, channel, 1));
			track.add(MidiFactory.createReverbControlEvent(AbcConstants.MIDI_REVERB, channel, 1));
			track.add(MidiFactory.createChorusControlEvent(AbcConstants.MIDI_CHORUS, channel, 1));
		}
		track.add(MidiFactory.createPanEvent(pan, channel));


		List<AbcNoteEvent> notesOn = new ArrayList<>();

		int noteDelta = 0;
		if (!useLotroInstruments)
			noteDelta = part.getInstrument().octaveDelta * 12;

		long delayMicros = 0;
		if ((qtm.getPrimaryExportTempoBPM() >= 50 || organic) && part.delay != 0) {
			// Make delay on instrument be audible in preview
			delayMicros = (long) (part.delay * 1000 * qtm.getExportTempoFactor());
		}
		
		for (Chord chord : chords) {
			Dynamics dynamics = chord.calcDynamics();
			if (dynamics == null)
				dynamics = Dynamics.DEFAULT;
			for (int j = 0; j < chord.size(); j++) {
				AbcNoteEvent ne = chord.get(j);
				// Skip rests and notes that are the continuation of a tied note
				if (ne.note == Note.REST || ne.tiesFrom != null)
					continue;

				// Add note off events for any notes that have been turned off by this point
				Iterator<AbcNoteEvent> onIter = notesOn.iterator();
				while (onIter.hasNext()) {
					AbcNoteEvent on = onIter.next();

					// Shorten the note to end at the same time that the next one starts
					long endTick = on.getEndTick();
					if (on.note.id == ne.note.id && on.getEndTick() > ne.getStartTick())
						endTick = ne.getStartTick();

					if (endTick <= ne.getStartTick()) {//TODO: Review this closer, and check for compatibility with TrackInfo behavior of zero dura notes.
						// This note has been turned off
						onIter.remove();
						if (organic) {
							track.add(MidiFactory.createNoteOffEvent(on.note.id + noteDelta, channel, qtm.microsToTickOrganic(qtm.tickToMicrosOrganic(endTick) + delayMicros)));
						} else {
							track.add(MidiFactory.createNoteOffEvent(on.note.id + noteDelta, channel, qtm.microsToTick(qtm.tickToMicros(endTick) + delayMicros)));
						}
					}
				}

				long endTick = ne.getTieEnd().getEndTick();

				// Lengthen to match the note lengths used in the game
				if (useLotroInstruments) {
					boolean sustainable = part.getInstrument().isSustainable(ne.note.id);
					double extraSeconds = sustainable ? AbcConstants.SUSTAINED_NOTE_HOLD_SECONDS
							: AbcConstants.NON_SUSTAINED_NOTE_HOLD_SECONDS;
					if (organic) {
						endTick = qtm.microsToTickOrganic(qtm.tickToMicrosOrganic(endTick)
								+ (long) (extraSeconds * TimingInfo.ONE_SECOND_MICROS * qtm.getExportTempoFactor()));
					} else {
						endTick = qtm.microsToTick(qtm.tickToMicros(endTick)
							+ (long) (extraSeconds * TimingInfo.ONE_SECOND_MICROS * qtm.getExportTempoFactor()));
					}
				}
				
				if (endTick != ne.getEndTick()) {
					ne = new AbcNoteEvent(ne.note, ne.velocity, ne.getStartTick(), endTick, qtm, ne.origNote);
				}
				
				if (organic) {
					track.add(MidiFactory.createNoteOnEventEx(ne.note.id + noteDelta, channel,
							dynamics.getVol(useLotroInstruments), qtm.microsToTickOrganic(qtm.tickToMicrosOrganic(ne.getStartTick()) + delayMicros)));
				} else {
					track.add(MidiFactory.createNoteOnEventEx(ne.note.id + noteDelta, channel,
							dynamics.getVol(useLotroInstruments), qtm.microsToTick(qtm.tickToMicros(ne.getStartTick()) + delayMicros)));
				}
				notesOn.add(ne);
				part.numberOfExportedNotes++;
			}
		}

		for (AbcNoteEvent on : notesOn) {
			if (organic) {
				track.add(MidiFactory.createNoteOffEvent(on.note.id + noteDelta, channel, qtm.microsToTickOrganic(qtm.tickToMicrosOrganic(on.getEndTick()) + delayMicros)));
			} else {
				track.add(MidiFactory.createNoteOffEvent(on.note.id + noteDelta, channel, qtm.microsToTick(qtm.tickToMicros(on.getEndTick()) + delayMicros)));
			}
		}

		return new Pair<>(trackNumber, channel);
	}

	public void exportToAbc(OutputStream os, boolean delayEnabled) throws AbcConversionException {
		
		
		
		
		
		
		
		
		
		// accountForSustain is true so that songbooks wont stop their timer before last note has finished sounding.
		// lengthenToBar is false for opposite reason, so reporting the correct duration to songbooks.
		Pair<Long, Long> startEnd = getSongStartEndTick(false, true, false);
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
			if (!organic) {
				out.println(AbcField.SWING_RHYTHM + Boolean.toString(qtm.isTripletTiming()));
				out.println(AbcField.MIX_TIMINGS + Boolean.toString(qtm.isMixTiming()));
			} else {
				out.println(AbcField.SWING_RHYTHM + Boolean.toString(false));
				out.println(AbcField.MIX_TIMINGS + Boolean.toString(false));
			}
			out.println(AbcField.ORGANIC + Boolean.toString(organic));
			out.println(AbcField.SKIP_SILENCE_AT_START + Boolean.toString(skipSilenceAtStart));
			out.println(AbcField.DELETE_MINIMAL_NOTES + Boolean.toString(deleteMinimalNotes && !organic));
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
				if (organic) {
					exportPartToAbcOrganic(part, out, delayEnabled);
				} else {
					exportPartToAbc(part, out, delayEnabled);
				}
			}
		}
	}
	
	private void exportPartToAbcOrganic(AbcPart part, PrintStream out,
			boolean delayEnabled) throws AbcConversionException {
		
		exportPartTitleToAbc(part, out);
		
		// Keep track of which notes have been sharped or flatted so
		// we can naturalize them the next time they show up.
		boolean[] sharps = new boolean[Note.MAX_PLAYABLE.id + 1];
		boolean[] flats = new boolean[Note.MAX_PLAYABLE.id + 1];

		// Write out ABC notation
		long L = (qtm.getMeter().numerator / (double) qtm.getMeter().denominator) < 0.75d ? 16 : 8;
		long Q = qtm.getPrimaryExportTempoBPM();
		
		// One whole abc note is this many microseconds:
		int oneMicro = (int)(qtm.getMeter().denominator * TimingInfo.ONE_SECOND_MICROS * 60 / (Q * L));
		
		
		
		final int BAR_LENGTH = 120;
		final long songStartMicros = qtm.tickToMicrosABCOrganic(exportStartTick);
		int curExportTempoBPM = (int)Q;
		Dynamics curDyn = null;
		Dynamics initDyn = null;
		
		//System.out.println("Q: "+Q+" L: "+L+" one:"+oneMicro+" start: "+songStartMicros+" minimum: "+AbcConstants.getShortestNoteMicros((int)Q));
		
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
		
		if (delayEnabled) {
			out.println("z" + ((part.delay+100)*1000) + "/" + oneMicro + " | ");
		}
		
		List<Chord> chords = combineOrganic(part, false);
		int countChords = 0;
		long currentMicro = 0L;
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
			
			countChords++;

			if (countChords % 8 == 0) {
				// Print at every 8th chord
				if (bar.length() > 0) {
					addLineBreaks.run();
					out.print(bar);
					out.println(" |");
					bar.setLength(0);
				}
				long micros = (long) ((qtm.tickToMicrosABCOrganic(c.getStartTick()) - songStartMicros));
				out.println("%  (" + Util.formatDuration(micros) + ")");

				Arrays.fill(sharps, false);
				Arrays.fill(flats, false);
			}

						 
			// Is this the start of a new tempo?
			TimingInfo tm = qtm.getTimingInfoOrganic(c.getStartTick());
			if (exportTempos && curExportTempoBPM != tm.getExportTempoBPM()) {
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

			int chordMicro = (int)((qtm.tickToMicrosABCOrganic(c.getEndTick()) - qtm.tickToMicrosABCOrganic(c.getStartTick())));
			
			long cEndMicro = qtm.tickToMicrosABCOrganic(c.getEndTick()) - songStartMicros;
			if (currentMicro + chordMicro != cEndMicro) {
				long diff = (currentMicro + chordMicro) - cEndMicro;
				chordMicro -= (int)(diff);
			}
			if (chordMicro < AbcConstants.getShortestNoteMicros((int)Q)) {
				chordMicro = (int)AbcConstants.getShortestNoteMicros((int)Q);
			}
			if (chordMicro > AbcConstants.LONGEST_NOTE_MICROS) {
				//chordMicro = (int)AbcConstants.LONGEST_NOTE_MICROS - 10;
				//System.out.println(part.getTitle() +": chord reduced to "+chordMicro+" us  drone:"+(c.get(0).note.id <= AbcConstants.BAGPIPE_LAST_DRONE_NOTE_ID));
			}
			currentMicro += chordMicro;
			
			int numerator = chordMicro;
			int denominator = oneMicro;
			
			// Apply tempo
			if (exportTempos && curExportTempoBPM != Q) {
				numerator *= Q;
				denominator *= curExportTempoBPM;
			}

			// Reduce the fraction
			int gcd = Util.gcd(numerator, denominator);
			numerator /= gcd;
			denominator /= gcd;
						
			int notesWritten = 0;			
			for (int j = 0; j < c.size(); j++) {
				AbcNoteEvent evt = c.get(j);
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
			if (notesWritten == 0) {
				System.out.println("ZERO");
				currentMicro -= chordMicro;
			}

			bar.append(' ');
		}
		//System.out.println(part.getTitle()+" EXPORT: ends at "+Util.formatDurationM(currentMicro)+" - micro:"+currentMicro);

		addLineBreaks.run();
		out.print(bar);
		out.println(" |]");
		out.println();
	}

	private void exportPartTitleToAbc(AbcPart part, PrintStream out) {
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
		out.println("L: " + ((qtm.getMeter().numerator / (double) qtm.getMeter().denominator) < 0.75d ? "1/16" : "1/8"));
		out.println();
	}

	private void exportPartToAbc(AbcPart part, PrintStream out,
			boolean delayEnabled) throws AbcConversionException {
		List<Chord> chords = combineAndQuantize(part, false);

		exportPartTitleToAbc(part, out);

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
				AbcNoteEvent evt = c.get(j);
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
	private List<Chord> combineAndQuantize(AbcPart part, boolean preview) throws AbcConversionException {
		// Combine the events from the enabled tracks
		List<AbcNoteEvent> events = new ArrayList<>();
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
				List<MidiNoteEvent> listOfNotes = new ArrayList<>(part.getTrackEvents(t));

				if (specialDrumNotes) {
					List<MidiNoteEvent> extraList = new ArrayList<>();
					List<MidiNoteEvent> removeList = new ArrayList<>();
					for (MidiNoteEvent ne : listOfNotes) {
						Note possibleCombiNote = part.mapNote(t, ne.note.id, ne.getStartTick());
						if (possibleCombiNote != null && LotroCombiDrumInfo.noteIdIsXtraNote(possibleCombiNote.id)) {
							MidiNoteEvent extra1 = LotroCombiDrumInfo.getId1(ne, possibleCombiNote, ne.midiPan);
							MidiNoteEvent extra2 = LotroCombiDrumInfo.getId2(ne, possibleCombiNote, ne.midiPan);
							extraList.add(extra1);
							extraList.add(extra2);
							removeList.add(ne);
						} else if (possibleCombiNote != null && possibleCombiNote.id > LotroCombiDrumInfo.maxCombi.id) {
							// Just for safety, should never land here.
							System.err.println("// Just for safety, should never land here:+\n"+ne);
							removeList.add(ne);
						}
					}
					listOfNotes.removeAll(removeList);
					listOfNotes.addAll(extraList);
				}
				
				if (part.getInstrument().sustainable) {
					long lastTick = 0L;
					for (int curr = 0; curr < listOfNotes.size(); curr++) {
						MidiNoteEvent currNe = listOfNotes.get(curr);
						if (currNe.getEndTick() > lastTick) lastTick = currNe.getEndTick();
						if (!part.getSectionLegato(t, currNe.getStartTick()) || lastTick > currNe.getEndTick()) {
							currNe.setLegatoEndTick(part, null);
							continue;
						}
						long currEnd = currNe.getEndTick();
						long nextEnd = currEnd;
						long currEndMicro = qtm.tickToMicros(currEnd);
						// Now find where next note event starts
						for (int next = curr+1; next < listOfNotes.size(); next++) {
							MidiNoteEvent nextNe = listOfNotes.get(next);
							if (nextNe.getStartTick() <= nextEnd && nextNe.getEndTick() > currEnd) {
								break;
							}
							if (nextNe.getStartTick() > nextEnd) {
								nextEnd = nextNe.getStartTick();
								break;
							}
						}
						if (nextEnd > currEnd) {
							long nextEndMicro = qtm.tickToMicros(nextEnd);
							if (nextEndMicro - currEndMicro < 1000000) {
								currNe.setLegatoEndTick(part, nextEnd);
							} else {
								currNe.setLegatoEndTick(part, null);								
							}
						} else {
							currNe.setLegatoEndTick(part, null);
						}
					}
				} else {
					for (MidiNoteEvent ne : listOfNotes) {
						ne.setLegatoEndTick(part, null);
					}
				}

				for (MidiNoteEvent ne : listOfNotes) {
					// Skip notes that are outside of the play range.
					if (ne.getEndTick() <= exportStartTick || ne.getStartTick() >= exportEndTick) {
						//if (part.mapNoteEvent(t, ne) != null && part.shouldPlay(ne, t)) System.out.println(metadata.getSongTitle()+": Skipping note that are outside songs time range.\n"+ne);
						continue;
					}
					
					// reset pruned flag
					// ne.resetPruned(part);

					Note mappedNote = ne.note;

					if (!ne.alreadyMapped) {
						mappedNote = part.mapNoteEvent(t, ne);
					}
					
					if (mappedNote != null && part.shouldPlay(ne, t)) {
						if (!(ne instanceof BentMidiNoteEvent)) {
							assert mappedNote.id >= part.getInstrument().lowestPlayable.id : mappedNote;
							assert mappedNote.id <= part.getInstrument().highestPlayable.id : mappedNote;
						} else {
							//AbcNoteEvent newNE = createNoteEvent(ne, mappedNote, 60, 0, 1000, qtm);
							//assert ((BentAbcNoteEvent)newNE).getMinNote() >= part.getInstrument().lowestPlayable.id : ne.alreadyMapped+": "+newNE;
							//assert ((BentAbcNoteEvent)newNE).getMaxNote() <= part.getInstrument().highestPlayable.id : ne.alreadyMapped+": "+newNE;
						}
						// if (mappedNote.id > part.getInstrument().highestPlayable.id) {
						// part.mapNoteEvent2(t, ne);
						// }

						long startTick = Math.max(ne.getStartTick(), exportStartTick);
						long legatoEndTick = ne.getEndTick();
						if (ne.getLegatoEndTick(part) != null) {
							legatoEndTick = ne.getLegatoEndTick(part);
						}
						long endTick = Math.min(legatoEndTick, exportEndTick);
						ne.setLegatoEndTick(part, null);// clean up, so if a part is removed there is not references to it in midinoteevents.
						if (part.isStudentPart() && mappedNote.id < LotroInstrument.STUDENT_CHROMATIC_LOWEST.id) {
							long endTickMin = qtm.microsToTick(
									qtm.tickToMicros(startTick) + (long) (AbcConstants.STUDENT_FX_MIN_SECONDS
											* TimingInfo.ONE_SECOND_MICROS * qtm.getExportTempoFactor()));
							endTick = Math.max(endTick, endTickMin);// TODO: what if similar note comes right after, then it should not be extended!!!
						}

						int[] sva = part.getSectionVolumeAdjust(t, ne);
						int velocity = part.getSectionNoteVelocity(t, ne);
						velocity = (int) ((velocity + part.getTrackVolumeAdjust(t) + sva[0]) * 0.01f * (float) sva[1] * 0.01f * (float) sva[2]);

						AbcNoteEvent newNE = createNoteEvent(ne, mappedNote, velocity, startTick, endTick, qtm);
						
						/*
						 * if (preview) { // Only associate if doing preview newNE.origEvent = new
						 * ArrayList<NoteEvent>(); newNE.origEvent.add(ne); }
						 */
						events.add(newNE);

						Boolean[] doubling = part.getSectionDoubling(ne.getStartTick(), t);

						if (doubling[0] && ne.note.id - 24 > Note.MIN.id) {
							Note mappedNote2 = part.mapNoteEvent(t, ne, ne.note.id - 24);
							if (mappedNote2 != null) {
								AbcNoteEvent newNE2 = createNoteEvent(ne, mappedNote2, velocity, startTick, endTick, qtm);
								//newNE2.doubledNote = true;// prune these first
								events.add(newNE2);
							}
						}
						if (doubling[1] && ne.note.id - 12 > Note.MIN.id) {
							Note mappedNote2 = part.mapNoteEvent(t, ne, ne.note.id - 12);
							if (mappedNote2 != null) {
								AbcNoteEvent newNE2 = createNoteEvent(ne, mappedNote2, velocity, startTick, endTick, qtm);
								//newNE2.doubledNote = true;
								events.add(newNE2);
							}
						}
						if (doubling[2] && ne.note.id + 12 < Note.MAX.id) {
							Note mappedNote2 = part.mapNoteEvent(t, ne, ne.note.id + 12);
							if (mappedNote2 != null) {
								AbcNoteEvent newNE2 = createNoteEvent(ne, mappedNote2, velocity, startTick, endTick, qtm);
								//newNE2.doubledNote = true;
								events.add(newNE2);
							}
						}
						if (doubling[3] && ne.note.id + 24 < Note.MAX.id) {
							Note mappedNote2 = part.mapNoteEvent(t, ne, ne.note.id + 24);
							if (mappedNote2 != null) {
								AbcNoteEvent newNE2 = createNoteEvent(ne, mappedNote2, velocity, startTick, endTick, qtm);
								//newNE2.doubledNote = true;
								events.add(newNE2);
							}
						}
					} else {
						ne.setLegatoEndTick(part, null);// clean up, so if a part is removed there is not references to it in midinoteevents.
						//System.out.println("Final skipping \n"+ne+"\n"+(mappedNote != null)+" "+(part.shouldPlay(ne, t)));
					}
				}
			}
		}
		
		if (events.isEmpty() && preview) {
			try {
				PolyphonyHistogram.count(part, new ArrayList<>(), organic, qtm);
			} catch (IOException e) {
				throw new AbcConversionException("Failed to read instrument sample durations.", e);
			}
			return Collections.emptyList();
		}

		Collections.sort(events);
		
		if (part.conclusionFermata != 0) {
			long finalNoteTickEnd = 0L;
			List<AbcNoteEvent> conclusion = new ArrayList<>();
			for (int cc = 0; cc < events.size() ; cc++) {
				AbcNoteEvent ne = events.get(cc);
				if (ne.getEndTick() > finalNoteTickEnd) {
					finalNoteTickEnd = ne.getEndTick();
					conclusion.removeAll(conclusion);
					conclusion.add(ne);
				} else if (ne.getEndTick() == finalNoteTickEnd) {
					conclusion.add(ne);
				}
			}
			long fermataEndTick = qtm.quantize(qtm.microsToTickABC(part.conclusionFermata * 1000L + qtm.tickToMicrosABC(finalNoteTickEnd)), part);
			boolean sustain = false;
			for (int cc = 0; cc < conclusion.size() ; cc++) {
				AbcNoteEvent ne = conclusion.get(cc);
				if (part.getInstrument().isSustainable(ne.note.id)) {
					sustain = true;
					ne.setEndTick(fermataEndTick);
				}
			}
			if (fermataEndTick > exportEndTick && sustain) {
				// This is a hack, as at the time this runs
				// exportEndTick has already been used elsewhere.
				exportEndTick = fermataEndTick;
			}
		}

		// Quantize the events
		long lastEnding = 0;
		AbcNoteEvent lastEvent = null;
		List<AbcNoteEvent> extraEvents = new ArrayList<>();
		List<AbcNoteEvent> deleteEvents = new ArrayList<>();
		
		int removedToAvoidDissonance = 0;
		for (int cc = 0; cc < events.size() ; cc++) {
			AbcNoteEvent ne = events.get(cc);
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
						AbcNoteEvent neLeft = events.get(ccc);
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

			List<AbcNoteEvent> bentNotes = expandPitchBends(part, ne);
			
			if (bentNotes != null) {
				assert !bentNotes.contains(ne);
				deleteEvents.add(ne);
				for (AbcNoteEvent bent : bentNotes) {
					assert bent.note != Note.REST;
					if (bent.getEndTick() > lastEnding) {
						lastEnding = bent.getEndTick();
						lastEvent = bent;
					}
				}
				extraEvents.addAll(bentNotes);
			} else {
				if (ne.getEndTick() > lastEnding) {
					lastEnding = ne.getEndTick();
					lastEvent = ne;
				}
			}
		}
		
		part.numberOfRemovedNotesForSafety = removedToAvoidDissonance;

		events.addAll(extraEvents);// add all the pitchbend fractions to the main event list
		events.removeAll(deleteEvents);
		//System.out.println("Something removed: "+deleteEvents.size());
		//System.out.println("Something added: "+extraEvents.size());
		
		Collections.sort(events);
		
		if (events.size() == 0) {
			System.err.println("Export to preview/abc: "+metadata.getSongTitle()+" has a part with no exported notes.");
			return new ArrayList<>();
		}
		
		// Add initial rest if necessary
		
		if (events.get(0).getStartTick() > exportStartTick) {
			events.add(0, new AbcNoteEvent(Note.REST, Dynamics.DEFAULT.midiVol, exportStartTick,
					events.get(0).getStartTick(), qtm, null));
		}

		// Add a rest at the end if necessary
		if (exportEndTick < Long.MAX_VALUE) {

			if (lastEvent.getEndTick() < exportEndTick) {
				if (lastEvent.note == Note.REST) {
					lastEvent.setEndTick(exportEndTick);
				} else {
					events.add(new AbcNoteEvent(Note.REST, Dynamics.DEFAULT.midiVol, lastEvent.getEndTick(),
							exportEndTick, qtm, null));
				}
			}
		}

		// Remove duplicate notes
		removeDuplicateNotes(events, part.getInstrument());
		
		Collections.sort(events);// needed due to duplicate adding thirds

		breakLongNotes(part, events);

		List<Chord> chords = new ArrayList<>(events.size() / 2);
		List<AbcNoteEvent> tmpEvents = new ArrayList<>();
		
		
		
		// Combine notes that play at the same time into chords
		Chord curChord = new Chord(events.get(0));
		chords.add(curChord);
		for (int i = 1; i < events.size(); i++) {
			AbcNoteEvent ne = events.get(i);

			if (curChord.getStartTick() == ne.getStartTick()) {
				// This note starts at the same time as the rest of the notes in the chord
				assert !curChord.isRest();
				curChord.add(ne);
			} else {
				List<AbcNoteEvent> deadnotes = curChord.prune(part.getInstrument().sustainable,
						part.getInstrument() == LotroInstrument.BASIC_DRUM, part.getInstrument().isPercussion, part);
				removeNotes(events, deadnotes, part);
				if (!deadnotes.isEmpty()) {
					// One of the tiedTo notes that was pruned might be the events.get(i) note,
					// so we go one step back and re-process events.get(i)
					i--;
					continue;
				}

				// Create a new chord
				Chord nextChord = new Chord(ne);

				// The curChord has all the notes it will get. But before continuing,
				// normalize the chord so that all notes end at the same time and end
				// before the next chord starts.
				boolean reprocessCurrentNote = false;
				long targetEndTick = Math.min(nextChord.getStartTick(), curChord.getEndTick());

				for (int j = 0; j < curChord.size(); j++) {
					AbcNoteEvent jne = curChord.get(j);
					if (jne.getEndTick() > targetEndTick) {
						// This note extends past the end of the chord; break it into two tied notes
						AbcNoteEvent next = jne.splitWithTieAtTick(targetEndTick);

						int ins = Collections.binarySearch(events, next);
						if (ins < 0)
							ins = -ins - 1;
						assert (ins >= i);
						// If we're inserting before the current note, back up and process the added
						// note
						if (ins == i)
							reprocessCurrentNote = true;
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

				// Insert a rest between the chords if needed
				if (curChord.getEndTick() < nextChord.getStartTick()) {
					tmpEvents.clear();
					tmpEvents.add(new AbcNoteEvent(Note.REST, Dynamics.DEFAULT.midiVol, curChord.getEndTick(),
							nextChord.getStartTick(), qtm, null));
					breakLongNotes(part, tmpEvents);

					for (AbcNoteEvent restEvent : tmpEvents) {
						chords.add(new Chord(restEvent));
					}
				}

				chords.add(nextChord);
				assert !nextChord.hasRestAndNotes();
				assert !curChord.hasRestAndNotes();
				curChord = nextChord;
			}
		}

		boolean reprocessCurrentNote = true;

		while (reprocessCurrentNote) {
			// The last Chord has all the notes it will get. But before continuing,
			// normalize the chord so that all notes end at the same time and end
			// before the next chord starts.

			// Last chord needs to be pruned as that hasn't happened yet.
			List<AbcNoteEvent> deadnotes = curChord.prune(part.getInstrument().sustainable,
					part.getInstrument() == LotroInstrument.BASIC_DRUM, part.getInstrument().isPercussion, part);
			removeNotes(events, deadnotes, part);// we need to set the pruned flag for last chord too.
			curChord.recalcEndTick();
			long targetEndTick = curChord.getEndTick();

			reprocessCurrentNote = false;

			Chord nextChord = null;

			for (int j = 0; j < curChord.size(); j++) {
				AbcNoteEvent jne = curChord.get(j);
				if (jne.getEndTick() > targetEndTick) {
					// This note extends past the end of the chord; break it into two tied notes
					AbcNoteEvent next = jne.splitWithTieAtTick(targetEndTick);
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
		assert !curChord.hasRestAndNotes();
		
		if (preview) {
			try {
				PolyphonyHistogram.count(part, chords, organic, qtm);
			} catch (IOException e) {
				throw new AbcConversionException("Failed to read instrument sample durations.", e);
			}
		}
		
		return chords;
	}
	
	/**
	 * Combine the tracks into one, quantize the note lengths, separate into chords.
	 */
	private List<Chord> combineOrganic(AbcPart part, boolean preview) throws AbcConversionException {
		// Combine the events from the enabled tracks
		List<AbcNoteEvent> events = new ArrayList<>();
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
				List<MidiNoteEvent> listOfNotes = new ArrayList<>(part.getTrackEvents(t));

				if (specialDrumNotes) {
					List<MidiNoteEvent> extraList = new ArrayList<>();
					List<MidiNoteEvent> removeList = new ArrayList<>();
					for (MidiNoteEvent ne : listOfNotes) {
						Note possibleCombiNote = part.mapNote(t, ne.note.id, ne.getStartTick());
						if (possibleCombiNote != null && LotroCombiDrumInfo.noteIdIsXtraNote(possibleCombiNote.id)) {
							MidiNoteEvent extra1 = LotroCombiDrumInfo.getId1(ne, possibleCombiNote, ne.midiPan);
							MidiNoteEvent extra2 = LotroCombiDrumInfo.getId2(ne, possibleCombiNote, ne.midiPan);
							extraList.add(extra1);
							extraList.add(extra2);
							removeList.add(ne);
						} else if (possibleCombiNote != null && possibleCombiNote.id > LotroCombiDrumInfo.maxCombi.id) {
							// Just for safety, should never land here.
							System.err.println("// Just for safety, should never land here:+\n"+ne);
							removeList.add(ne);
						}
					}
					listOfNotes.removeAll(removeList);
					listOfNotes.addAll(extraList);
				}
				
				if (part.getInstrument().sustainable) {
					long lastTick = 0L;
					for (int curr = 0; curr < listOfNotes.size(); curr++) {
						MidiNoteEvent currNe = listOfNotes.get(curr);
						if (currNe.getEndTick() > lastTick) lastTick = currNe.getEndTick();
						if (!part.getSectionLegato(t, currNe.getStartTick()) || lastTick > currNe.getEndTick()) {
							currNe.setLegatoEndTick(part, null);
							continue;
						}
						long currEnd = currNe.getEndTick();
						long nextEnd = currEnd;
						long currEndMicro = qtm.tickToMicrosOrganic(currEnd);
						// Now find where next note event starts
						for (int next = curr+1; next < listOfNotes.size(); next++) {
							MidiNoteEvent nextNe = listOfNotes.get(next);
							if (nextNe.getStartTick() <= nextEnd && nextNe.getEndTick() > currEnd) {
								break;
							}
							if (nextNe.getStartTick() > nextEnd) {
								nextEnd = nextNe.getStartTick();
								break;
							}
						}
						if (nextEnd > currEnd) {
							long nextEndMicro = qtm.tickToMicrosOrganic(nextEnd);
							if (nextEndMicro - currEndMicro < AbcConstants.ONE_SECOND_MICROS) {
								currNe.setLegatoEndTick(part, nextEnd);
							} else {
								currNe.setLegatoEndTick(part, null);								
							}
						} else {
							currNe.setLegatoEndTick(part, null);
						}
					}
				} else {
					for (MidiNoteEvent ne : listOfNotes) {
						ne.setLegatoEndTick(part, null);
					}
				}

				for (MidiNoteEvent ne : listOfNotes) {
					// Skip notes that are outside of the play range.
					if (ne.getEndTick() <= exportStartTick || ne.getStartTick() >= exportEndTick) {
						//if (part.mapNoteEvent(t, ne) != null && part.shouldPlay(ne, t)) System.out.println(metadata.getSongTitle()+": Skipping note that are outside songs time range.\n"+ne);
						continue;
					}
					
					// reset pruned flag
					// ne.resetPruned(part);

					Note mappedNote = ne.note;

					if (!ne.alreadyMapped) {
						mappedNote = part.mapNoteEvent(t, ne);
					}
					
					if (mappedNote != null && part.shouldPlay(ne, t)) {
						if (!(ne instanceof BentMidiNoteEvent)) {
							assert mappedNote.id >= part.getInstrument().lowestPlayable.id : mappedNote;
							assert mappedNote.id <= part.getInstrument().highestPlayable.id : mappedNote;
						} else {
							//AbcNoteEvent newNE = createNoteEvent(ne, mappedNote, 60, 0, 1000, qtm);
							//assert ((BentAbcNoteEvent)newNE).getMinNote() >= part.getInstrument().lowestPlayable.id : ne.alreadyMapped+": "+newNE;
							//assert ((BentAbcNoteEvent)newNE).getMaxNote() <= part.getInstrument().highestPlayable.id : ne.alreadyMapped+": "+newNE;
						}
						// if (mappedNote.id > part.getInstrument().highestPlayable.id) {
						// part.mapNoteEvent2(t, ne);
						// }

						long startTick = Math.max(ne.getStartTick(), exportStartTick);
						long legatoEndTick = ne.getEndTick();
						if (ne.getLegatoEndTick(part) != null) {
							legatoEndTick = ne.getLegatoEndTick(part);
						}
						long endTick = legatoEndTick;//Math.min(legatoEndTick, exportEndTick);
						
						ne.setLegatoEndTick(part, null);// clean up, so if a part is removed there is not references to it in midinoteevents.
						if (part.isStudentPart() && mappedNote.id < LotroInstrument.STUDENT_CHROMATIC_LOWEST.id) {
							long endTickMin = qtm.microsToTickOrganic(
									qtm.tickToMicrosOrganic(startTick) + (long) (AbcConstants.STUDENT_FX_MIN_SECONDS
											* TimingInfo.ONE_SECOND_MICROS * qtm.getExportTempoFactor()));
							endTick = Math.max(endTick, endTickMin);
							// TODO: what if similar note comes right after, then it should not be extended!!!
							//       and shouldn't this only be done for preview?
							//       why is it even done? lotro plays it fine when its shorter than sample.
							//       histogram acounts for sample duration too, so thats not the reason.
							//       Perhaps its for preview so midi playback dont cut the note short.
							//       But then why not do it for all plucked?
						}

						int[] sva = part.getSectionVolumeAdjust(t, ne);
						int velocity = part.getSectionNoteVelocity(t, ne);
						velocity = (int) ((velocity + part.getTrackVolumeAdjust(t) + sva[0]) * 0.01f * (float) sva[1] * 0.01f * (float) sva[2]);

						AbcNoteEvent newNE = createNoteEvent(ne, mappedNote, velocity, startTick, endTick, qtm);
						
						/*
						 * if (preview) { // Only associate if doing preview newNE.origEvent = new
						 * ArrayList<NoteEvent>(); newNE.origEvent.add(ne); }
						 */
						events.add(newNE);

						Boolean[] doubling = part.getSectionDoubling(ne.getStartTick(), t);

						if (doubling[0] && ne.note.id - 24 > Note.MIN.id) {
							Note mappedNote2 = part.mapNoteEvent(t, ne, ne.note.id - 24);
							if (mappedNote2 != null) {
								AbcNoteEvent newNE2 = createNoteEvent(ne, mappedNote2, velocity, startTick, endTick, qtm);
								//newNE2.doubledNote = true;// prune these first
								events.add(newNE2);
							}
						}
						if (doubling[1] && ne.note.id - 12 > Note.MIN.id) {
							Note mappedNote2 = part.mapNoteEvent(t, ne, ne.note.id - 12);
							if (mappedNote2 != null) {
								AbcNoteEvent newNE2 = createNoteEvent(ne, mappedNote2, velocity, startTick, endTick, qtm);
								//newNE2.doubledNote = true;
								events.add(newNE2);
							}
						}
						if (doubling[2] && ne.note.id + 12 < Note.MAX.id) {
							Note mappedNote2 = part.mapNoteEvent(t, ne, ne.note.id + 12);
							if (mappedNote2 != null) {
								AbcNoteEvent newNE2 = createNoteEvent(ne, mappedNote2, velocity, startTick, endTick, qtm);
								//newNE2.doubledNote = true;
								events.add(newNE2);
							}
						}
						if (doubling[3] && ne.note.id + 24 < Note.MAX.id) {
							Note mappedNote2 = part.mapNoteEvent(t, ne, ne.note.id + 24);
							if (mappedNote2 != null) {
								AbcNoteEvent newNE2 = createNoteEvent(ne, mappedNote2, velocity, startTick, endTick, qtm);
								//newNE2.doubledNote = true;
								events.add(newNE2);
							}
						}
					} else {
						ne.setLegatoEndTick(part, null);// clean up, so if a part is removed there is not references to it in midinoteevents.
						//System.out.println("Final skipping \n"+ne+"\n"+(mappedNote != null)+" "+(part.shouldPlay(ne, t)));
					}
				}
			}
		}
		
		if (events.isEmpty() && preview) {
			try {
				PolyphonyHistogram.count(part, new ArrayList<>(), organic, qtm);
			} catch (IOException e) {
				throw new AbcConversionException("Failed to read instrument sample durations.", e);
			}
			return Collections.emptyList();
		}

		Collections.sort(events);
		
		if (part.conclusionFermata != 0) {
			long finalNoteTickEnd = 0L;
			List<AbcNoteEvent> conclusion = new ArrayList<>();
			for (int cc = 0; cc < events.size() ; cc++) {
				AbcNoteEvent ne = events.get(cc);
				if (ne.getEndTick() > finalNoteTickEnd) {
					finalNoteTickEnd = ne.getEndTick();
					conclusion.removeAll(conclusion);
					conclusion.add(ne);
				} else if (ne.getEndTick() == finalNoteTickEnd) {
					conclusion.add(ne);
				}
			}
			long fermataEndTick = qtm.microsToTickABCOrganic(part.conclusionFermata * 1000L + qtm.tickToMicrosABCOrganic(finalNoteTickEnd));
			boolean sustain = false;
			for (int cc = 0; cc < conclusion.size() ; cc++) {
				AbcNoteEvent ne = conclusion.get(cc);
				if (part.getInstrument().isSustainable(ne.note.id)) {
					sustain = true;
					ne.setEndTick(fermataEndTick);
				}
			}
			if (fermataEndTick > exportEndTick && sustain) {
				// This is a hack, as at the time this runs
				// exportEndTick has already been used elsewhere.
				exportEndTick = fermataEndTick;
			}
		}

		// Quantize the events
		long lastEnding = 0;
		AbcNoteEvent lastEvent = null;
		List<AbcNoteEvent> extraEvents = new ArrayList<>();
		List<AbcNoteEvent> deleteEvents = new ArrayList<>();
		
		int removedToAvoidDissonance = 0;
		for (int cc = 0; cc < events.size() ; cc++) {
			AbcNoteEvent ne = events.get(cc);
			assert ne.note != Note.REST : "Rest detected!";
			if (cc == events.size()-1) {
				//System.out.println(part.getTitle()+": pre ends at "+(ne.getEndMicros()));
			}
			long oldStart = ne.getStartTick();
			long oldEnd = ne.getEndTick();
			
			if (deleteEmptyNotes && oldStart == oldEnd) {
				deleteEvents.add(ne);
				continue;
			}
			

			List<AbcNoteEvent> bentNotes = expandPitchBendsOrganic(part, ne);
			
			if (bentNotes != null) {
				assert !bentNotes.contains(ne);
				deleteEvents.add(ne);
				for (AbcNoteEvent bent : bentNotes) {
					assert bent.note != Note.REST;
					if (bent.getEndTick() > lastEnding) {
						lastEnding = bent.getEndTick();
						lastEvent = bent;
					}
				}
				extraEvents.addAll(bentNotes);
			} else {
				if (ne.getEndTick() > lastEnding) {
					lastEnding = ne.getEndTick();
					lastEvent = ne;
				}
			}
		}
		
		part.numberOfRemovedNotesForSafety = removedToAvoidDissonance;

		events.addAll(extraEvents);// add all the pitchbend fractions to the main event list
		events.removeAll(deleteEvents);
		//System.out.println("Something removed: "+deleteEvents.size());
		//System.out.println("Something added: "+extraEvents.size());
		
		Collections.sort(events);
		
		if (events.size() == 0) {
			System.err.println("Export to preview/abc: "+metadata.getSongTitle()+" has a part with no exported notes.");
			return new ArrayList<>();
		}
		
		// Add initial rest if necessary
		
		if (events.get(0).getStartTick() > exportStartTick) {
			events.add(0, new AbcNoteEvent(Note.REST, Dynamics.DEFAULT.midiVol, exportStartTick,
					events.get(0).getStartTick(), qtm, null));
		}

		// Add a rest at the end if necessary
		if (false && exportEndTick < Long.MAX_VALUE) {

			if (lastEvent.getEndTick() < exportEndTick) {
				if (lastEvent.note == Note.REST) {
					lastEvent.setEndTick(exportEndTick);
				} else {
					events.add(new AbcNoteEvent(Note.REST, Dynamics.DEFAULT.midiVol, lastEvent.getEndTick(),
							exportEndTick, qtm, null));
				}
			}
		}

		// Remove duplicate notes
		removeDuplicateNotes(events, part.getInstrument());
		
		Collections.sort(events);// needed due to duplicate adding thirds

		breakLongNotesOrganic(part, events);

		List<Chord> chords = new ArrayList<>(events.size() / 2);
		List<AbcNoteEvent> tmpEvents = new ArrayList<>();

		long minimumMicros = AbcConstants.getShortestNoteMicros(qtm.getPrimaryExportTempoBPM());
		
		// Combine notes that play at the same time into chords
		Chord curChord = new Chord(events.get(0));
		chords.add(curChord);
		MAIN:for (int i = 1; i < events.size(); i++) {
			AbcNoteEvent ne = events.get(i);
			
			if (curChord.getStartTick() == ne.getStartTick()) {
				// This note starts at the same time as the rest of the notes in the chord
				assert !curChord.isRest();
				curChord.add(ne);
			} else {								
				// The curChord has all the notes it will get. But before continuing,
				// normalize the chord so that all notes end at the same time and end
				// before the next chord starts.
				//System.out.println("Processing chord");
				
				// remove zero duration notes if longer notes start at same time
				if (curChord.getLongestEndTick() > curChord.getStartTick()) {
					for (int j = 0; j < curChord.size(); j++) {
						AbcNoteEvent jne = curChord.get(j);
						if (jne.getEndTick() == jne.getStartTick()) {
							// this note is zero duration and others in the chord is not
							curChord.remove(jne);
							//System.out.println(part.getTitle()+" Removed zero dura note");
							if (jne.tiesFrom != null) {
								jne.tiesFrom.tiesTo = null;
							}
							if (jne.tiesTo != null) {
								jne.tiesTo.tiesFrom = null;
							}
							j = -1;//should be careful when removing item from something we are iterating over..
						}
					}
					// A removal will have changed the chord's duration
					curChord.recalcEndTick();
				}
				
				// We prune after removed shorter zero notes, so they dont take up slot from
				// 6 max notes.
				List<AbcNoteEvent> deadnotes = curChord.prune(part.getInstrument().sustainable,
						part.getInstrument() == LotroInstrument.BASIC_DRUM, part.getInstrument().isPercussion, part);
				removeNotes(events, deadnotes, part);
				if (!deadnotes.isEmpty()) {
					// One of the tiedTo notes that was pruned might be ne note,
					// so we go one step back and re-process events.get(i)
					i--;
					//System.out.println("LOOP: PRUNED");
					continue MAIN;
				}
				
				
				// Create a new chord
				Chord nextChord = new Chord(ne);

				
				// handle fast glissando				
				long microsTillNext = qtm.tickToMicrosABCOrganic(ne.getStartTick()) - qtm.tickToMicrosABCOrganic(curChord.getStartTick());
				long neMicros = qtm.tickToMicrosABCOrganic(ne.getEndTick()) - qtm.tickToMicrosABCOrganic(ne.getStartTick());
				AbcNoteEvent ne2 = null;
				for (int ii = i+1; ii < events.size(); ii++) {
					AbcNoteEvent over = events.get(ii);
					if (over.getStartTick() > ne.getStartTick()) {
						ne2 = over;
						break;
					}
				}				
				long microsTillNext2 = ne2 == null?0L:qtm.tickToMicrosABCOrganic(ne2.getStartTick()) - qtm.tickToMicrosABCOrganic(ne.getStartTick());
				long ne2Micros = ne2 == null?0L:qtm.tickToMicrosABCOrganic(ne2.getEndTick()) - qtm.tickToMicrosABCOrganic(ne2.getStartTick());
				if ((curChord.getEndTick() > ne.getStartTick() || (neMicros < minimumMicros && ne2Micros < minimumMicros))
						&& curChord.getEndTick() < ne.getEndTick()
						&& microsTillNext < minimumMicros
						&& neMicros < minimumMicros * 4L
						&& microsTillNext2 < minimumMicros
						&& curChord.getLongestEndTick() < qtm.microsToTickABCOrganic(qtm.tickToMicrosABCOrganic(curChord.getStartTick()) + minimumMicros * 4L)) {
				
					long curMinEnd = qtm.microsToTickABCOrganic(qtm.tickToMicrosABCOrganic(curChord.getStartTick()) + minimumMicros);
					curChord.setEndTickRetract(curMinEnd);
					curChord.setEndTickExpand(curMinEnd);
					
					System.out.println(part.getTitle()+" Removed glissando note 1");
					events.remove(ne);
					curChord.glissando = true;
					i--;
					// TODO: these ties should perhaps prevent it from being removed, TBD
					if (ne.tiesFrom != null) {
						ne.tiesFrom.tiesTo = null;
					}
					if (ne.tiesTo != null) {
						ne.tiesTo.tiesFrom = null;
					}
					continue MAIN;
				} else {
					//System.out.println((curChord.getEndTick() > ne.getStartTick())+" microsTillNext="+microsTillNext+" microsTillNext2="+microsTillNext2+" neMicros="+neMicros);
				}
								
				// remove very fast arpeggio				
				if (microsTillNext < minimumMicros && curChord.getEndTick() > ne.getStartTick() && !curChord.dontMove && curChord.early == null && !curChord.glissando) {
					// curr end before next start prevents handling grace notes, they will be deleted later if they too short
					for (AbcNoteEvent small : curChord.getNotes()) {
						if (small.tiesFrom != null || small.tiesTo != null) {
							// curr chord has already been cut up, skip it
							i--;
							curChord.dontMove = true;// to prevent infinite loop
							continue MAIN;
						}
					}
					for (AbcNoteEvent small : curChord.getNotes()) {
						// make sure next chord dont have any notes with same pitch as one from curChord

						for (int ii = i; ii < events.size(); ii++) {
							AbcNoteEvent next = events.get(ii);
							if (next.getStartTick() > ne.getEndTick()) {
								// no reason to check more notes
								break;
							}
							if (next.getStartTick() == ne.getStartTick()) {
								if (next.note == small.note) {
									// cancel
									i--;
									curChord.dontMove = true;// to prevent infinite loop
									//System.out.println(part.getTitle()+" Keep arpeggio (next chord has same note)");
									continue MAIN;
								}
							}
						}
					}
					
					// Its too complex to move current chord into next cords position, so we do the opposite:					
					System.out.println(part.getTitle()+" Turned arpeggio into block chord (early start)");
					ne.setStartTick(curChord.getStartTick());
					curChord.add(ne);
					curChord.recalcEndTick();
					continue MAIN;
				}
				
				
				long shortest = qtm.tickToMicrosABCOrganic(curChord.getEndTick()) - qtm.tickToMicrosABCOrganic(curChord.getStartTick());
				long space = qtm.tickToMicrosABCOrganic(ne.getStartTick()) - qtm.tickToMicrosABCOrganic(curChord.getStartTick());
				long minEndMicro = qtm.tickToMicrosABCOrganic(curChord.getStartTick()) + minimumMicros;
				long minEndTick = qtm.microsToTickABCOrganic(minEndMicro);
				if (shortest < minimumMicros && space >= minimumMicros && ne.getStartTick() >= minEndTick) {
					// one or more notes in curChord is to short, but they have room to expand
					curChord.setEndTickExpand(minEndTick);
					//System.out.println("Expanded");
				}
				
				
				// cut up curChord if some notes longer than others
				boolean reprocessCurrentNote = false;
				long targetEndTick = Math.min(nextChord.getStartTick(), curChord.getEndTick());
				for (int j = 0; j < curChord.size(); j++) {
					AbcNoteEvent jne = curChord.get(j);
					if (!curChord.glissando && jne.getEndTick() > targetEndTick) {
						// This note extends past the end of the chord; break it into two tied notes
						AbcNoteEvent next = jne.splitWithTieAtTick(targetEndTick);

						int ins = Collections.binarySearch(events, next);
						if (ins < 0)
							ins = -ins - 1;
						
						assert (ins >= i);
						// If we're inserting before the current note, back up and process the added
						// note
						if (ins == i)
							reprocessCurrentNote = true;
						assert next.note != Note.REST;
						events.add(ins, next);
					}
				}
				// The shorter notes will have changed the chord's duration
				curChord.recalcEndTick();

				if (reprocessCurrentNote) {
					i--;
					//System.out.println("LOOP: split");
					continue MAIN;
				}
				
				// Expand into gap to next chord if the gap is smaller than 0.06s
				if (curChord.getEndTick() < nextChord.getStartTick()) {
					long restMicros = qtm.tickToMicrosABCOrganic(nextChord.getStartTick()) - qtm.tickToMicrosABCOrganic(curChord.getEndTick());
					if (restMicros <= minimumMicros) {
						assert nextChord.getStartTick() > curChord.getEndTick(); 
						curChord.setEndTickExpand(nextChord.getStartTick());
						//System.out.println(part.getTitle()+ ": Bridged rest");
					}
				}
				
				if (curChord.early != null) {
					curChord.setEarlyStartTick();
				}
				
				// Handle curr chord if its shorter than 0.06s				
				if (qtm.tickToMicrosABCOrganic(curChord.getEndTick()) < minEndMicro) {
					// First try to make it longer
					curChord.setEndTickExpand(qtm.microsToTickABCOrganic(minEndMicro));
					if (curChord.getEndTick() > nextChord.getStartTick()) {
						if (!curChord.glissando) {
							// give up and schedule curr chord for deletion
							curChord.setEndTickRetract(curChord.getStartTick());
							System.out.println(part.getTitle()+" Removed short dura note");
						} else {
							// force room for curr chord
							if (ne2 != null && ne2.getStartTick() >= minEndTick && ne.getEndTick() > ne2.getStartTick() && ne.tiesFrom == null) {
								// delay start of next note, its likely not part of glissando
								ne.setStartTick(ne2.getStartTick());
								events.remove(ne);
								events.add(events.indexOf(ne2), ne);
								i--;
								System.out.println(part.getTitle()+" Delayed staggered note");
								continue MAIN;
							} else {
								// remove next note, it likely part of glissando
								events.remove(ne);
								i--;
								// TODO: these ties should perhaps prevent it from being removed, TBD
								if (ne.tiesFrom != null) {
									ne.tiesFrom.tiesTo = null;
								}
								if (ne.tiesTo != null) {
									ne.tiesTo.tiesFrom = null;
								}
								System.out.println(part.getTitle()+" Removed glissando note 2 ");
								continue MAIN;
							}
						}
					}
				}
				
				// Insert a rest between the chords if needed
				if (curChord.getEndTick() < nextChord.getStartTick()) {
					long restMicros = qtm.tickToMicrosABCOrganic(nextChord.getStartTick()) - qtm.tickToMicrosABCOrganic(curChord.getEndTick());
					if (restMicros >= minimumMicros) {
						// there is space to make a rest
						tmpEvents.clear();
						tmpEvents.add(new AbcNoteEvent(Note.REST, Dynamics.DEFAULT.midiVol, curChord.getEndTick(),
								nextChord.getStartTick(), qtm, null));
						breakLongNotesOrganic(part, tmpEvents);
	
						for (AbcNoteEvent restEvent : tmpEvents) {
							Chord restChord = new Chord(restEvent);
							chords.add(restChord);
						}
					} else {
						// If we reach this code, then curr has been scheduled for deletion.
						// Here we can either make next chord start sooner
						// or find the chord before curr and expand that.
						// For code simplicity we make next start sooner
						// this has the added benefit that if next chord is
						// too short too, it will be longer.
						nextChord.early = curChord.getEndTick();
						//System.out.println(part.getTitle()+ ": Early start");
					}
				}

				chords.add(nextChord);
				assert !nextChord.hasRestAndNotes();
				assert !curChord.hasRestAndNotes();
				curChord = nextChord;
			}
		}

		boolean reprocessCurrentNote = true;

		while (reprocessCurrentNote) {
			// The last Chord has all the notes it will get. But before continuing,
			// normalize the chord so that all notes end at the same time
			if (curChord.early != null) {
				curChord.setEarlyStartTick();
			}
			
			// remove zero duration notes if longer notes start at same time
			if (curChord.getLongestEndTick() > curChord.getStartTick()) {
				for (int j = 0; j < curChord.size(); j++) {
					AbcNoteEvent jne = curChord.get(j);
					if (jne.getEndTick() == jne.getStartTick()) {
						// this note is zero duration and others in the chord is not
						curChord.remove(jne);
						if (jne.tiesFrom != null) {
							jne.tiesFrom.tiesTo = null;
						}
						if (jne.tiesTo != null) {
							jne.tiesTo.tiesFrom = null;
						}
						j=-1;
					}
				}
				// The removal will have changed the chord's duration
				curChord.recalcEndTick();
			}
			
			// Last chord needs to be pruned as that hasn't happened yet.
			List<AbcNoteEvent> deadnotes = curChord.prune(part.getInstrument().sustainable,
					part.getInstrument() == LotroInstrument.BASIC_DRUM, part.getInstrument().isPercussion, part);
			removeNotes(events, deadnotes, part);// we need to set the pruned flag for last chord too.
			curChord.recalcEndTick();
			
			//System.out.println(part.getTitle()+" final note ends at "+Util.formatDurationM(qtm.tickToMicrosABCOrganic(curChord.getEndTick()-exportStartTick)));
			
			if (qtm.tickToMicrosABCOrganic(curChord.getEndTick()) < qtm.tickToMicrosABCOrganic(curChord.getStartTick()) + minimumMicros) {
				curChord.setEndTickExpand(qtm.microsToTickABCOrganic(qtm.tickToMicrosABCOrganic(curChord.getStartTick()) + minimumMicros));
			}
			
			long targetEndTick = curChord.getEndTick();
			reprocessCurrentNote = false;
			Chord nextChord = null;

			for (int j = 0; j < curChord.size(); j++) {
				AbcNoteEvent jne = curChord.get(j);
				if (jne.getEndTick() > targetEndTick) {
					// This note extends past the end of the chord; break it into two tied notes
					AbcNoteEvent next = jne.splitWithTieAtTick(targetEndTick);
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
		assert !curChord.hasRestAndNotes();
		
		List<Chord> trash = new ArrayList<>();
		for (int i = 0; i < chords.size(); i++) {
			Chord chord = chords.get(i);
			if (chord.getStartTick() == chord.getEndTick()) {
				for (int j = 0; j < chord.size(); j++) {
					AbcNoteEvent note = chord.get(j);
					AbcNoteEvent tieIn = note.tiesFrom;
					AbcNoteEvent tieOut = note.tiesTo;
					if (tieIn != null && tieOut != null) {
						tieIn.tiesTo = tieOut;
						tieOut.tiesFrom = tieIn;
					} else if (tieIn != null) {
						tieIn.tiesTo = null;
					} else if (tieOut != null) {
						tieOut.tiesFrom = null;
					}
				}
				trash.add(chord);
			}
		}
		chords.removeAll(trash);
		
		if (preview) {
			try {
				PolyphonyHistogram.count(part, chords, organic, qtm);
			} catch (IOException e) {
				throw new AbcConversionException("Failed to read instrument sample durations.", e);
			}
		}
		
		//Collections.sort(chords);
		
		return chords;
	}

	/**
	 * Remove duplicate notes that play at the same time (comes from combining tracks into same part)
	 * 
	 * @param events All the notes from all the combined tracks
	 * @param instrument 
	 */
	private void removeDuplicateNotes(List<AbcNoteEvent> events, LotroInstrument instrument) {
		// If prioritizeLongNotes is true, then notes that are subset of the other but lower or equal value
		// will just be deleted if sustained.
		// If false, then the 2 notes will become 2 or 3 unisons,
		// where the middle (subset) will have the volume of the loudest.
		// Some listening tests convinced me that false is the way to go.
		final boolean prioritizeUninteruptedLongNotes = false;
		
		List<AbcNoteEvent> notesOn = new ArrayList<>();
		List<AbcNoteEvent> thirds = new ArrayList<>();
		Iterator<AbcNoteEvent> neIter = events.iterator();
		dupLoop: while (neIter.hasNext()) {
			AbcNoteEvent ne = neIter.next();//second
			List<AbcNoteEvent> thirdsOn = new ArrayList<>();
			Iterator<AbcNoteEvent> onIter = notesOn.iterator();
			while (onIter.hasNext()) {
				AbcNoteEvent on = onIter.next();//first
				if (on.getEndTick() <= ne.getStartTick() && (on.getLengthTicks() > 0 || on.getStartTick() < ne.getStartTick())) {
					// First note has already been turned off
					onIter.remove();
				} else if (on.note.id == ne.note.id) {
					if (on.getStartTick() == ne.getStartTick()) {
						// If they start at the same time, remove the second event.
						
						// Lengthen the first one if it's shorter than the second one.
						if (on.getEndTick() <= ne.getEndTick()) {
							on.setEndTick(ne.getEndTick());
							if (ne.velocity > on.velocity) {
								on.velocity = ne.velocity;// due to this, NoteEvent.velocity is not final
							}
						}
						
						if (!instrument.isSustainable(on.note.id) && ne.velocity > on.velocity) {
							on.velocity = ne.velocity;// due to this, NoteEvent.velocity is not final
						}
						
						// Remove the duplicate second note
						neIter.remove();
						continue dupLoop;
					} else if (on.getStartTick() < ne.getStartTick()) {
						// Otherwise, if they don't start at the same time, but first started first:

						if (ne.getEndTick() <= on.getEndTick()) {
							// second is subset of first
							
							if (instrument.isSustainable(on.note.id)) {
															
								if (prioritizeUninteruptedLongNotes && Dynamics.fromMidiVelocity(ne.velocity).abcVol <= Dynamics.fromMidiVelocity(on.velocity).abcVol) {
									// remove second
									// we only do this if second has lower or equal volume
									neIter.remove();
									continue dupLoop;
								}
								// else we stop first, insert second, and add new third if needed (with firsts volume) after second to finish first.
								long thirdEnd = on.getEndTick(); 
								on.setEndTick(ne.getStartTick());
								onIter.remove();
								if (on.velocity > ne.velocity) {
									ne.velocity = on.velocity;
								}
								if (thirdEnd > ne.getEndTick()) {
									AbcNoteEvent third = new AbcNoteEvent(on.note, on.velocity, ne.getEndTick(), thirdEnd, qtm, on.origNote);
									thirds.add(third);
									thirdsOn.add(third);
								}
							} else {
								// keep both, so end first where second start	
								on.setEndTick(ne.getStartTick());
								onIter.remove();
							}
						} else if (ne.getEndTick() > on.getEndTick()) {
							// ne extend beyond on
							if (!instrument.isSustainable(on.note.id) || Dynamics.fromMidiVelocity(ne.velocity) != Dynamics.fromMidiVelocity(on.velocity)) {
								// we break first, and start second
								on.setEndTick(ne.getStartTick());
								onIter.remove();
							} else {
								// sustained and same abc volume
								// we extend first to cover both, and discard second
								on.setEndTick(ne.getEndTick());
								neIter.remove();
								continue dupLoop;
							}
						}
					} else {
						if (on.getStartTick() < ne.getEndTick()) {
							// Otherwise, if they don't start at the same time, but second started first, which means first was a third
							
							if (ne.getEndTick() > on.getEndTick()) {
								// extend first to match seconds end
								on.setEndTick(ne.getEndTick());
							}
							
							// since we know that there has been inserted a subset note where
							// second starts, we dont need to care about the start. Also we know that it
							// will process third before the subset, so we don't have to worry about subset being extended
							// as long as second is removed here. And its safe
							// to remove second as long as its sustained. If its not sustained we shorten it so it dont extend into the third.
							if (instrument.isSustainable(on.note.id)) {
								neIter.remove();
								continue dupLoop;
							} else if (ne.getEndTick() > on.getStartTick()) {
								// shorten second to end where first begin
								// second will then be processed against the subset later in the loop
								ne.setEndTick(on.getStartTick());
							}
						}
					}
				}
			}
			notesOn.addAll(thirdsOn);//must be before adding ne
			notesOn.add(ne);
		}
		events.addAll(thirds);
	}


	private void breakLongNotes(AbcPart part, List<AbcNoteEvent> events) {
		for (int i = 0; i < events.size(); i++) {
			AbcNoteEvent ne = events.get(i);
			
			// preview might have delay so its okay to not be quant
			assert qtm.quantize(ne.getEndTick(), part) == ne.getEndTick();
			assert qtm.quantize(ne.getStartTick(), part) == ne.getStartTick();
			
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
					AbcNoteEvent next = new AbcNoteEvent(ne.note, ne.velocity, maxNoteEndTick, ne.getEndTick(), qtm, ne.origNote);
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
				assert qtm.quantize(maxNoteEndTick, part) == maxNoteEndTick;
				ne.setEndTick(maxNoteEndTick);
			}

			// Tie notes across bar boundaries
			
			long targetEndTick = Math.min(ne.getEndTick(), qtm.quantize(qtm.tickToBarEndTick(ne.getStartTick()), part));
			long minEnding = ne.getStartTick() + tm.getMinNoteLengthTicks();
			if (targetEndTick < minEnding) {
				// Mix Timings can cause code to come here since its bar ends might not be quantized.
				targetEndTick = minEnding;
				assert qtm.quantize(minEnding, part) == minEnding; 
			}
			assert (targetEndTick >= minEnding);
			assert (ne.getEndTick() >= minEnding) : "1="+(qtm.quantize(ne.getEndTick(), part) == ne.getEndTick())+" 2="+(qtm.quantize(ne.getStartTick(), part) == ne.getStartTick());
			assert (targetEndTick <= ne.getEndTick());

			// Tie notes across tempo boundaries
			final QuantizedTimingInfo.TimingInfoEvent nextTempoEvent = qtm.getNextTimingEvent(ne.getStartTick(),
					part);
			if (nextTempoEvent != null && nextTempoEvent.tick < targetEndTick) {
				targetEndTick = nextTempoEvent.tick;
				assert (targetEndTick - ne.getStartTick() >= tm.getMinNoteLengthTicks());
				assert (ne.getEndTick() - targetEndTick >= nextTempoEvent.info.getMinNoteLengthTicks());
				assert targetEndTick == qtm.quantize(targetEndTick, part);
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
				assert targetEndTick == qtm.quantize(targetEndTick, part) : "targetEndTick not on grid";
				assert (ne.getEndTick() - targetEndTick >= qtm.getTimingInfo(targetEndTick, part).getMinNoteLengthTicks());
				assert (targetEndTick - ne.getStartTick() >= tm.getMinNoteLengthTicks());
				AbcNoteEvent next = ne.splitWithTieAtTick(targetEndTick);
				int ins = Collections.binarySearch(events, next);
				if (ins < 0)
					ins = -ins - 1;
				assert (ins > i);
				events.add(ins, next);
			}
		}
	}
	
	private void breakLongNotesOrganic(AbcPart part, List<AbcNoteEvent> events) {
		TreeSet<Long> points = new TreeSet<>();
		for (int i = 0; i < events.size(); i++) {
			AbcNoteEvent ne = events.get(i);
			points.add(ne.getStartTick());
			//points.add(ne.getEndTick()); // ends are more likely to be moved later, so we grab only starts
		}
		for (int i = 0; i < events.size(); i++) {
			AbcNoteEvent ne = events.get(i);
			
			long maxNoteEndTick = 
					qtm.microsToTickOrganic(
							qtm.tickToMicrosOrganic(ne.getStartTick()) + (long) (TimingInfo.LONGEST_NOTE_MICROS * qtm.getExportTempoFactor()));
			
			boolean drone = part.getInstrument() == LotroInstrument.BASIC_BAGPIPE
					&& ne.note.id <= AbcConstants.BAGPIPE_LAST_DRONE_NOTE_ID;
			
			// Make a hard break for notes that are longer than LotRO can play
			// Bagpipe notes up to B2 can sustain indefinitely; don't break them
			if (ne.getEndTick() > maxNoteEndTick && ne.note != Note.REST && !drone) {

				// If the note is a rest or sustainable, add another one after
				// this ends to keep it going...
				if (ne.note == Note.REST || part.getInstrument().isSustainable(ne.note.id)) {

					if (ne.note != Note.REST) {
						long start  = qtm.tickToMicrosABCOrganic(ne.getStartTick());
						long finale = qtm.tickToMicrosABCOrganic(ne.getEndTick());
						long cut    = qtm.tickToMicrosABCOrganic(maxNoteEndTick);
						if (finale - cut < AbcConstants.ONE_SECOND_MICROS) {
							// we dont want to cut it half a second before the end
							// so we cut 1 second earlier
							// TODO: This might time its cut different from the cuts in other parts at same time
							//       But not sure how to handle that, and it might in some cases be good thing. 
							cut -= AbcConstants.ONE_SECOND_MICROS;
							long maxNoteEndTick2 = qtm.microsToTickABCOrganic(cut);
							long bar1 = qtm.tickToBarStartTickOrganic(maxNoteEndTick2);
							long bar2 = qtm.tickToBarStartTickOrganic(maxNoteEndTick);
							
							if (Math.abs(maxNoteEndTick2 - bar1) < Math.abs(maxNoteEndTick2 - bar2)
									&& qtm.tickToMicrosABCOrganic(bar1)-start > AbcConstants.ONE_SECOND_MICROS) {
								// prev bar is closer than next from 4.0 sec
								// and prev bar is at least 1 sec from start
								maxNoteEndTick = bar1;
								//System.out.println(part.getTitle()+" bar1");
							} else if (qtm.tickToMicrosABCOrganic(bar2)-start > AbcConstants.ONE_SECOND_MICROS) {
								// prev bar from 5 secs 
								maxNoteEndTick = bar2;
								//System.out.println(part.getTitle()+" bar2");
							}
						} else {
							long bar3 = qtm.tickToBarStartTickOrganic(maxNoteEndTick);
							long slipMicros = qtm.tickToMicrosABCOrganic(maxNoteEndTick) - qtm.tickToMicrosABCOrganic(bar3);

							if (qtm.tickToBarEndTickOrganic(ne.getStartTick()) < bar3
									&& slipMicros < AbcConstants.ONE_SECOND_MICROS) {
								// minimum 1nd bar from start and maximum 1 sec from 5 secs
								maxNoteEndTick = bar3;
								//System.out.println(part.getTitle()+" bar3");
							} else {
								//System.out.println(part.getTitle()+" 5.0 sec");
							}
						}
					}
					
					AbcNoteEvent next = new AbcNoteEvent(ne.note, ne.velocity, maxNoteEndTick, ne.getEndTick(), qtm, ne.origNote);
					if (ne.note == Note.REST) {
						maxNoteEndTick = (ne.getEndTick() - ne.getStartTick())/2;
						next = new AbcNoteEvent(ne.note, ne.velocity, maxNoteEndTick, ne.getEndTick(), qtm, ne.origNote);
					}
					int ins = Collections.binarySearch(events, next);
					if (ins < 0)
						ins = -ins - 1;
					assert (ins > i): "REST="+(ne.note == Note.REST);
					events.add(ins, next);

					ne.continues = next.getLengthTicks();// needed for pruning
					
					if (ne.note == Note.REST) {
						// need to be after the assert
						i--;
					}
				}
				ne.setEndTick(maxNoteEndTick);
			}

			// drones should be tied instead of cut up:
			// Where this is tied can matter for other notes, so
			// find where other notes start or end and choose that place.
			long maxForDrones = qtm.microsToTickOrganic(
					qtm.tickToMicrosOrganic(ne.getStartTick()) + (long) ((TimingInfo.LONGEST_NOTE_MICROS-0.25*AbcConstants.ONE_SECOND_MICROS) * qtm.getExportTempoFactor())
					);
			long minForDrones = qtm.microsToTickOrganic(
					qtm.tickToMicrosOrganic(ne.getStartTick()) + (long) ((TimingInfo.LONGEST_NOTE_MICROS-AbcConstants.ONE_SECOND_MICROS) * qtm.getExportTempoFactor())
					);
			Long bestForDrones = points.floor(maxForDrones);
			if (bestForDrones != null && bestForDrones > minForDrones) maxForDrones = bestForDrones; 
				
			long targetEndTick = Math.min(ne.getEndTick(), maxForDrones);


			// If remaining bar is larger than 5s, then split rests earlier (and yes, have
			// seen this happen for 8s+ -aifel)
			if (ne.note == Note.REST && targetEndTick > qtm.microsToTickOrganic(qtm.tickToMicrosOrganic(ne.getStartTick())
					+ (long) (TimingInfo.LONGEST_NOTE_MICROS * qtm.getExportTempoFactor()))) {
				// Rest longer than 5s, split it at 4s:
				targetEndTick = 
						qtm.microsToTickOrganic(qtm.tickToMicrosOrganic(ne.getStartTick())
								+ (long) (0.5f * AbcConstants.LONGEST_NOTE_MICROS * qtm.getExportTempoFactor()));
			}

			if (ne.getEndTick() > targetEndTick) {
				
				AbcNoteEvent next = ne.splitWithTieAtTick(targetEndTick);
				int ins = Collections.binarySearch(events, next);
				if (ins < 0)
					ins = -ins - 1;
				assert (ins > i);
				events.add(ins, next);
			}
		}
	}

	private AbcNoteEvent createNoteEvent(MidiNoteEvent oldNe, Note mappednote, int velocity, long startTick, long endTick,
			ITempoCache tempos) {
		if (oldNe instanceof BentMidiNoteEvent) {
			BentAbcNoteEvent newNe = new BentAbcNoteEvent(mappednote, velocity, startTick, endTick, tempos, (BentMidiNoteEvent) oldNe);
			return newNe;
		} else {
			return new AbcNoteEvent(mappednote, velocity, startTick, endTick, tempos, oldNe);
		}
	}

	private void addMidiTempoEvents(Track track0) {
		NavigableMap<Long, TimingInfoEvent> timings = qtm.getTimingInfoByTick();
		if (organic) {
			timings = qtm.getTimingInfoByTickOrganic();
		}
		for (QuantizedTimingInfo.TimingInfoEvent event : timings.values()) {
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

	/**
	 * Split all BentNoteEvents into multiple quantized NoteEvents
	 * 
	 * @param part Abc Part
	 * @param ne   The note event to be processed
	 * @return List of multiple NoteEvents
	 */
	private List<AbcNoteEvent> expandPitchBends(AbcPart part, AbcNoteEvent ne) {
		// Handle pitch bend by subdividing tone into shorter quantized notes.
		// By the time this method is ran, start and end tick of the bent tone is already quantized.
		if (ne instanceof BentAbcNoteEvent) {
			BentAbcNoteEvent be = (BentAbcNoteEvent) ne;
			int noteID = be.note.id;
			assert be.note != Note.REST;
			int startPitch = noteID;
			List<AbcNoteEvent> benders = new ArrayList<>();
			AbcNoteEvent current = null;
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
					current = createBentSubNote(be, noteID, current, t, entry);
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
							current = createBentSubNote(be, noteID, current, t, entry);
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
							current = createBentSubNote(be, noteID, current, lastGridTick, entry);
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
	
	/**
	 * Split all BentNoteEvents into multiple quantized NoteEvents
	 * 
	 * @param part Abc Part
	 * @param ne   The note event to be processed
	 * @return List of multiple NoteEvents
	 */
	private List<AbcNoteEvent> expandPitchBendsOrganic(AbcPart part, AbcNoteEvent ne) {
		// Handle pitch bend by subdividing tone into shorter notes.
		if (ne instanceof BentAbcNoteEvent) {
			BentAbcNoteEvent be = (BentAbcNoteEvent) ne;
			int noteID = be.note.id;
			assert be.note != Note.REST;
			int startPitch = noteID;
			List<AbcNoteEvent> benders = new ArrayList<>();
			AbcNoteEvent current = null;
			long minimum = qtm.microsToTickABCOrganic((long)(qtm.getExportTempoFactor()*(double)AbcConstants.getShortestNoteMicros(qtm.getPrimaryExportTempoBPM())));
			Integer entry = null;
			for (long t = be.getStartTick(); t < be.getEndTick(); t = be.getNextBend(t+minimum, entry)) {
				entry = be.getBend(t);
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
					current = createBentSubNote(be, noteID, current, t, entry);
					if (current == null)
						return new ArrayList<>();
					benders.add(current);
				} else {
					if (current.note.id != noteID) {
						current = createBentSubNote(be, noteID, current, t, entry);
						if (current == null)
							return new ArrayList<>();
						benders.add(current);
					}
				}
			}

			return benders;
		} else {
			return null;
		}
	}

	private AbcNoteEvent createBentSubNote(BentAbcNoteEvent be, int noteID, AbcNoteEvent current, long tick, int bend) {
		if (current != null) {
			current.setEndTick(tick);
		}
		Note newNote = Note.fromId(noteID);
		if (newNote == null) {
			System.out.println("Note removed, pitch bend out of range");
			return null;
		}
		assert newNote != Note.REST;
		AbcNoteEvent sub = new AbcNoteEvent(newNote, be.velocity, tick, be.getEndTick(), be.getTempoCache(), be.origNote);
		sub.setOrigBend(bend);
		return sub;
	}

	/** Removes a note and breaks any ties the note has. */
	@Deprecated
	private void removeNote(List<AbcNoteEvent> events, int i) {
		AbcNoteEvent ne = events.remove(i);

		// If the note is tied from another (previous) note, break the incoming tie
		if (ne.tiesFrom != null) {
			ne.tiesFrom.tiesTo = null;
			ne.tiesFrom = null;
		}

		// Remove the remainder of the notes that this is tied to (if any)
		for (AbcNoteEvent neTie = ne.tiesTo; neTie != null; neTie = neTie.tiesTo) {
			events.remove(neTie);
		}
	}

	/**
	 * Run this after pruning to tidy up ties
	 * 
	 * @param events
	 * @param notes
	 * @param part
	 */
	private void removeNotes(List<AbcNoteEvent> events, List<AbcNoteEvent> notes, AbcPart part) {
		for (AbcNoteEvent ne : notes) {

			// If the note is tied from another (previous) note, break the incoming tie
			if (ne.tiesFrom != null) {
				ne.tiesFrom.tiesTo = null;
				ne.tiesFrom = null;
			} /*
				 * else if (ne.origEvent != null && showPruned) { for (NoteEvent neo : ne.origEvent) { neo.prune(part);
				 * } }
				 */

			// Remove the remainder of the notes that this is tied to (if any)
			for (AbcNoteEvent neTie = ne.tiesTo; neTie != null; neTie = neTie.tiesTo) {
				events.remove(neTie);
			}
			ne.tiesTo = null;
		}
	}

	/** Removes a note and breaks any ties the note has. */
	@Deprecated
	private void removeNote(List<AbcNoteEvent> events, AbcNoteEvent ne) {
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
		long startTick = skipSilenceAtStart ? Long.MAX_VALUE : 0L;
		long endTick = Long.MIN_VALUE;
		for (AbcPart part : parts) {
			if (skipSilenceAtStart) {
				long firstNoteStart = part.firstNoteStartTick();
				if (firstNoteStart < startTick) {
					startTick = firstNoteStart;
				}
			}

			long lastNoteEnd = part.lastNoteEndTick(accountForSustain, qtm.getExportTempoFactor());
			if (lastNoteEnd > endTick) {
				endTick = lastNoteEnd;
			}
		}

		if (startTick == Long.MAX_VALUE)
			startTick = 0L;
		if (endTick == Long.MIN_VALUE)
			endTick = 0L;
		
		if (organic) {
			startTick = Math.max(0L, qtm.microsToTickABCOrganic(qtm.tickToMicrosABCOrganic(startTick)-100000L));
			return new Pair<>(startTick, endTick);
		}

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
	
	private static int calculatePartsCount(List<AbcPart> parts) {
		int partsCount = 0;// Number of parts that has assigned tracks to them.
		for (AbcPart p : parts) {
			if (p.getEnabledTrackCount() > 0) {
				partsCount++;
			}
		}
		return partsCount;
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
		if (organic) {
			return qtm.tickToMicrosOrganic(getExportStartTick());
		} else {
			return qtm.tickToMicros(getExportStartTick());
		}
	}
	
	private long getSongLengthMicros() {
		return (long) ((getExportEndMicros() - getExportStartMicros())
				/ (double) qtm.getExportTempoFactor());
	}

	/**
	 * Does not account for tempo adjustment
	 * @return 
	 */
	public long getExportEndMicros() {
		if (organic) {
			return qtm.tickToMicrosOrganic(getExportEndTick());
		} else {
			return qtm.tickToMicros(getExportEndTick());
		}
	}

	public static class ExportTrackInfo {
		public final int trackNumber;
		public final AbcPart part;
		public final List<AbcNoteEvent> noteEvents;
		public final Integer channel;
		public final Integer patch;

		public ExportTrackInfo(int trackNumber, AbcPart part, List<AbcNoteEvent> noteEvents, Integer channel, int patch) {
			this.trackNumber = trackNumber;
			this.part = part;
			this.noteEvents = noteEvents;
			this.channel = channel;
			this.patch = patch;
		}
	}

	public void setOrganic(boolean org) {
		organic = org;
	}

	public boolean isOrganic() {		
		return organic;
	}
}
