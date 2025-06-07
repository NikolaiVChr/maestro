package com.digero.maestro.abc;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.TreeMap;
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
import com.digero.common.util.Triple;
import com.digero.common.util.Util;
import com.digero.maestro.MaestroMain;
import com.digero.maestro.abc.QuantizedTimingInfo.TimingInfoEvent;
import com.digero.maestro.midi.AbcNoteEvent;
import com.digero.maestro.midi.BentAbcNoteEvent;
import com.digero.maestro.midi.BentMidiNoteEvent;
import com.digero.maestro.midi.Chord;
import com.digero.maestro.midi.MidiNoteEvent;
import com.digero.maestro.midi.TrackInfo;
import com.digero.maestro.view.ProjectFrame;

public class AbcExporter {
	private boolean organic = false;
	private boolean organic2 = false;
	private static final int MAX_RAID = 24; // Max number of parts that in any case can be played in lotro

	private final List<AbcPart> parts;
	private final AbcMetadataSource metadata;
	private QuantizedTimingInfo qtm;
	private KeySignature keySignature;

	private boolean skipSilenceAtStart;
	private boolean deleteMinimalNotes;
	private boolean useRestsInChords;
	// private boolean showPruned;
	private long exportStartTick;
	private long exportEndTick;
	
	// the tempo changes might not be shared evenly among the parts, so this is really only for making abc more readable
	private boolean exportTempos = false;
	
	// Some midis have zero duration notes that should played (this is for organic only)
	private boolean deleteEmptyNotes = false;
	
	private boolean useRestToShortenChords = true;// Only organic timings use this

	public int stereoPan = 100;// zero is mono, 100 is very wide.
	private int firstBarNumber;

	private int lastChannelUsedInPreview = -1;

	public AbcExporter(List<AbcPart> parts, QuantizedTimingInfo timingInfo, KeySignature keySignature,
			AbcMetadataSource metadata, boolean skipSilenceAtStart, boolean organic) throws AbcConversionException {
		this.parts = parts;
		this.qtm = timingInfo;
		this.metadata = metadata;
		setKeySignature(keySignature);
		this.organic = organic;// getSongStartEndTick needs this so we needed to pass it
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
			

			PanGenerator panner = new PanGenerator();
			
			lastChannelUsedInPreview = -1;			
			long lastEnd = 0L;
			for (AbcPart part : parts) {
				
				if (part.getEnabledTrackCount() > 0) {
					int pan = (parts.size() > 1) ? panner.get(part.getInstrument(), part.getTitle(), stereoPan)
							: PanGenerator.CENTER;
					
					ExportTrackInfo inf = exportPartToPreview(part, sequence, pan,
							useLotroInstruments, chordsMade);
					infoList.add(inf);
					lastEnd = Math.max(lastEnd, inf.endOfTrack);
					// System.out.println(part.getTitle()+" assigned to channel "+inf.channel+" on track
					// "+inf.trackNumber);
				}
			}
			addMidiTempoEvents(track0, lastEnd);
			track0.add(MidiFactory.createEndOfTrackEvent(lastEnd));
			
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

		Triple<Integer, Integer, Long> trackNumber = exportPartToMidi(part, sequence, chords, pan, useLotroInstruments);
		/*
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
		*/

		return new ExportTrackInfo(trackNumber.first, part, null /* noteEvents */, trackNumber.second,
				part.getInstrument().midi.id(), trackNumber.third);
	}

	private Triple<Integer, Integer, Long> exportPartToMidi(AbcPart part, Sequence out, List<Chord> chords, int pan,
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
		if (part.delay != 0) {
			// Make delay on instrument be audible in preview
			delayMicros = qtm.multiplyByExportTempoFactor(part.delay * 1000L);
		}
		long lastEnd = 0L;
		for (Chord chord : chords) {
			Dynamics dynamics = chord.calcDynamics(part.getAbcSong().dynamicsMethod);
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

					if (endTick <= ne.getStartTick()) {
						// This note has been turned off
						onIter.remove();
						if (organic) {
							long off = qtm.microsToTickOrganic(qtm.tickToMicrosOrganic(endTick) + delayMicros);
							lastEnd = Math.max(off, lastEnd);
							track.add(MidiFactory.createNoteOffEvent(on.note.id + noteDelta, channel, off));
						} else {
							long off = qtm.microsToTick(qtm.tickToMicros(endTick) + delayMicros);
							lastEnd = Math.max(off, lastEnd);
							track.add(MidiFactory.createNoteOffEvent(on.note.id + noteDelta, channel, off));
						}
					}
				}

				long endTick = ne.getTieEnd().getEndTick();

				// Lengthen to match the note lengths used in the game
				if (useLotroInstruments) {
					boolean sustainable = part.getInstrument().isSustainable(ne.note.id);
					double extraSeconds = 0.0d;
					if(sustainable) {
						// This is better match lotro linear power decay, since our midi playback is linear dB decay instead.
						extraSeconds = AbcConstants.SUSTAINED_NOTE_HOLD_SECONDS;
					} else if (part.getInstrument() == LotroInstrument.STUDENT_FIDDLE) {
						// This is to not stop fx noise before it has played out
						extraSeconds = AbcConstants.STUDENT_FX_MIN_SECONDS;
					} else {
						// This is to not stop plucked/drum note before it has played out
						extraSeconds = AbcConstants.NON_SUSTAINED_NOTE_HOLD_SECONDS;
					}
					if (organic) {
						endTick = qtm.microsToTickOrganic(qtm.tickToMicrosOrganic(endTick)
								+ qtm.multiplyByExportTempoFactor((long)(extraSeconds * TimingInfo.ONE_SECOND_MICROS)));
					} else {
						endTick = qtm.microsToTick(qtm.tickToMicros(endTick)
							+ qtm.multiplyByExportTempoFactor((long)(extraSeconds * TimingInfo.ONE_SECOND_MICROS)));
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
				long off = qtm.microsToTickOrganic(qtm.tickToMicrosOrganic(on.getEndTick()) + delayMicros);
				lastEnd = Math.max(off, lastEnd);
				track.add(MidiFactory.createNoteOffEvent(on.note.id + noteDelta, channel, off));
			} else {
				long off = qtm.microsToTick(qtm.tickToMicros(on.getEndTick()) + delayMicros);
				lastEnd = Math.max(off, lastEnd);
				track.add(MidiFactory.createNoteOffEvent(on.note.id + noteDelta, channel, off));
			}
		}

		track.add(MidiFactory.createEndOfTrackEvent(lastEnd));

		return new Triple<>(trackNumber, channel, lastEnd);
	}

	public void exportToAbc(OutputStream os, boolean delayEnabled) throws AbcConversionException {
				
		// accountForSustain is true so that songbooks wont stop their timer before last note has finished sounding.
		// lengthenToBar is false for opposite reason, so reporting the correct duration to songbooks.
		Pair<Long, Long> startEnd = getSongStartEndTick(false, true, false);
		exportStartTick = startEnd.first;
		exportEndTick = startEnd.second;
		
		try (PrintStream out = new PrintStream(os)) {
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
				out.println(AbcField.ORGANIC_MULTI_STAGE + Boolean.toString(organic && organic2));
				out.println(AbcField.SKIP_SILENCE_AT_START + Boolean.toString(skipSilenceAtStart));
				out.println(AbcField.DELETE_MINIMAL_NOTES + Boolean.toString(deleteMinimalNotes && !organic));
				out.println(AbcField.ABC_VERSION + "2.1");
				
				
				outputBadger(out);
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
	}

	private void outputBadger(PrintStream out) {
		String genre = StringCleaner.cleanForABC(metadata.getGenre()).toLowerCase().trim();
		String mood = StringCleaner.cleanForABC(metadata.getMood()).toLowerCase().trim();
		String outAll = metadata.getPartSetup();
		String badgerTitle = metadata.getBadgerTitle();
		if (genre.length() > 0 || mood.length() > 0 || outAll != null || badgerTitle != null) {
			out.println();
			if (badgerTitle != null) {
				out.println(badgerTitle);
			}
			if (genre.length() > 0) {
				out.println("N: Genre: " + genre);
			}
			if (mood.length() > 0) {
				out.println("N: Mood: " + mood);
			}
			if (outAll != null) {
				out.print(outAll);
			}
		}
	}
	
	private void exportPartToAbcOrganic(AbcPart part, PrintStream out,
			boolean delayEnabled) throws AbcConversionException {
		
		exportPartHeaderToAbc(part, out);
		
		// Keep track of which notes have been sharped or flatted so
		// we can naturalize them the next time they show up.
		boolean[] sharps = new boolean[Note.MAX_PLAYABLE.id + 1];
		boolean[] flats = new boolean[Note.MAX_PLAYABLE.id + 1];

		// Write out ABC notation
		long L = (qtm.getMeter().numerator / (double) qtm.getMeter().denominator) < 0.75d ? 16L : 8L;
		long Q = qtm.getPrimaryExportTempoBPM();
		
		// One whole abc note is this many microseconds:
		int oneMicro = (int)(qtm.getMeter().denominator * TimingInfo.ONE_SECOND_MICROS * 60L / (Q * L));
		
		
		
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
			// the 100 is so the delay is always larger than 60 ms, even if its 0 ms.
			int delayMicro = (part.delay+100)*1000;
			int oneMicro2 = oneMicro;
			
			// Reduce the fraction
			int gcd = Util.gcd(delayMicro, oneMicro2);
			delayMicro /= gcd;
			oneMicro2 /= gcd;
			
			out.println("z" + delayMicro + "/" + oneMicro2 + " | ");
		}
		
		List<Chord> chords = combineOrganic(part, false);
		
		for (Chord c : chords) {
			initDyn = c.calcDynamics(part.getAbcSong().dynamicsMethod);
			if (initDyn != null)
				break;
		}
		
		int countChords = 0;
		long currentMicro = 0L;
		for (Chord c : chords) {
			if (c.size() == 0) {
				assert false : part.getAbcSong().getTitle()+" "+part.getTitle()+ ": Chord has no notes!";
				continue;
			}

			//assert !c.hasRestAndNotes() || organic2;

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
			

			Dynamics newDyn = (initDyn != null) ? initDyn : c.calcDynamics(part.getAbcSong().dynamicsMethod);
			initDyn = null;
			if (newDyn != null && newDyn != curDyn) {
				bar.append('+').append(newDyn).append("+ ");
				curDyn = newDyn;
			}

			if (c.size() > 1) {
				bar.append('[');
			}

			int chordMicro;
			if (organic2) {
				chordMicro = (int)(c.getShortest().origEndABCMicros - c.getShortest().origStartABCMicros);
			} else {
				chordMicro = (int)((qtm.tickToMicrosABCOrganic(c.getEndTick()) - qtm.tickToMicrosABCOrganic(c.getStartTick())));
			}
			
			long cEndMicro;
			if (organic2) {
				cEndMicro = c.getShortest().origEndABCMicros - songStartMicros;
			} else {
				cEndMicro = qtm.tickToMicrosABCOrganic(c.getEndTick()) - songStartMicros;
			}
			
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
			
			
			int notesWritten = 0;			
			for (int j = 0; j < c.size(); j++) {
				AbcNoteEvent evt = c.get(j);
				if (evt.getLengthTicks() == 0) {
					assert false : "Zero-length note:"+(evt.note);
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
				
				// now we do the micro calc again in case the timing algorithm
				// has put notes/tests with uneven durations into the chord:
				int noteMicro;
				if (organic2) {
					noteMicro = (int)(evt.origEndABCMicros - evt.origStartABCMicros);
				} else {
					noteMicro = (int)((qtm.tickToMicrosABCOrganic(evt.getEndTick()) - qtm.tickToMicrosABCOrganic(evt.getStartTick())));
				}
				
				long nEndMicro;
				if (organic2) {
					nEndMicro = evt.origEndABCMicros - songStartMicros;
				} else {
					nEndMicro = qtm.tickToMicrosABCOrganic(evt.getEndTick()) - songStartMicros;
				}
				
				if (currentMicro + noteMicro != nEndMicro) {
					long diff = (currentMicro + noteMicro) - nEndMicro;
					noteMicro -= (int)(diff);
				}
				if (noteMicro < AbcConstants.getShortestNoteMicros((int)Q)) {
					noteMicro = (int)AbcConstants.getShortestNoteMicros((int)Q);
				}
				if (noteMicro > AbcConstants.LONGEST_NOTE_MICROS) {
					//noteMicro = (int)AbcConstants.LONGEST_NOTE_MICROS - 10;
					//System.out.println(part.getTitle() +": chord reduced to "+noteMicro+" us  drone:"+(c.get(0).note.id <= AbcConstants.BAGPIPE_LAST_DRONE_NOTE_ID));
				}
				
				int numerator = noteMicro;
				int denominator = oneMicro;
				
				// Apply tempo
				if (exportTempos && curExportTempoBPM != Q) {
					numerator *= Q;
					denominator *= curExportTempoBPM;
				}

				// Reduce the fraction
				int gcdNote = Util.gcd(numerator, denominator);
				numerator /= gcdNote;
				denominator /= gcdNote;

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
			
			if (notesWritten > 0) {
				currentMicro += chordMicro;
			} else {
				System.out.println("ZERO");
			}

			bar.append(' ');
		}
		//System.out.println(part.getTitle()+" EXPORT: ends at "+Util.formatDurationM(currentMicro)+" - micro:"+currentMicro);

		addLineBreaks.run();
		out.print(bar);
		out.println(" |]");
		out.println();
	}

	private void exportPartToAbc(AbcPart part, PrintStream out,
			boolean delayEnabled) throws AbcConversionException {
		List<Chord> chords = combineAndQuantize(part, false);

		exportPartHeaderToAbc(part, out);

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
			initDyn = c.calcDynamics(part.getAbcSong().dynamicsMethod);
			if (initDyn != null)
				break;
		}

		if (delayEnabled) {
			long L = (qtm.getMeter().numerator / (double) qtm.getMeter().denominator) < 0.75d ? 16L : 8L;
			
			// One whole abc note is this many microseconds:
			int oneMicro = (int)(qtm.getMeter().denominator * TimingInfo.ONE_SECOND_MICROS * 60L / (qtm.getPrimaryExportTempoBPM() * L));

			// the 100 is so the delay is always larger than 60 ms, even if its 0 ms.
			int delayMicro = (part.delay+100)*1000;
			
			// Reduce the fraction
			int gcd = Util.gcd(delayMicro, oneMicro);
			delayMicro /= gcd;
			oneMicro /= gcd;
			
			out.println("z" + delayMicro + "/" + oneMicro + " |");
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
					long micros = qtm.divideByExportTempoFactor(qtm.barNumberToMicrosecond(curBarNumber) - songStartMicros);
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

			Dynamics newDyn = (initDyn != null) ? initDyn : c.calcDynamics(part.getAbcSong().dynamicsMethod);
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

	private void exportPartHeaderToAbc(AbcPart part, PrintStream out) {
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

	/**
	 * Combine the tracks into one, quantize the note lengths, separate into chords.
	 */
	private List<Chord> combineAndQuantize(AbcPart part, boolean preview) throws AbcConversionException {
		// Combine the events from the enabled tracks
		List<AbcNoteEvent> events = new ArrayList<>();
		for (int t = 0; t < part.getTrackCount(); t++) {
			if (part.isTrackEnabled(t)) {
				List<MidiNoteEvent> listOfNotes = expandXtraDrumNotes(part, t);
				
				applyLegato(part, t, listOfNotes);

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

						int[] sva = part.getSectionVolumeAdjust(t, ne);
						int velocity = part.getSectionNoteVelocity(t, ne);
						velocity = (int) ((velocity + part.getTrackVolumeAdjust(t) + sva[0]) * 0.01f * (float) sva[1] * 0.01f * (float) sva[2]);

						AbcNoteEvent newNE = createNoteEvent(ne, mappedNote, velocity, startTick, endTick, qtm, !part.isChromatic(t));
						
						/*
						 * if (preview) { // Only associate if doing preview newNE.origEvent = new
						 * ArrayList<NoteEvent>(); newNE.origEvent.add(ne); }
						 */
						events.add(newNE);

						createDoublingNoteEvents(part, events, t, ne, startTick, endTick, velocity);
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
		
		applyFermata(part, events);

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
			if (!preview) ProjectFrame.feed("Note: Song has a part with no exported notes ("+part.getTitle()+")", null);
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

	private void applyFermata(AbcPart part, List<AbcNoteEvent> events) {
		if (part.conclusionFermata != 0) {
			long finalNoteTickEnd = 0L;
			List<AbcNoteEvent> conclusion = new ArrayList<>();
			for (int cc = 0; cc < events.size() ; cc++) {
				AbcNoteEvent ne = events.get(cc);
				if (ne.getEndTick() > finalNoteTickEnd) {
					finalNoteTickEnd = ne.getEndTick();
					List<AbcNoteEvent> conclusionRemove = new ArrayList<>();
					if (organic) {
						long concludeMicros = qtm.tickToMicrosABCOrganic(finalNoteTickEnd);
						for (AbcNoteEvent potential : conclusion) {
							if (qtm.tickToMicrosABCOrganic(potential.getEndTick()) + 5000L < concludeMicros) {
								conclusionRemove.add(potential);
							}
						}
					} else {
						long concludeMicros = qtm.tickToMicrosABC(finalNoteTickEnd, part);
						for (AbcNoteEvent potential : conclusion) {
							if (qtm.tickToMicrosABC(potential.getEndTick(), part) + 5000L < concludeMicros) {
								conclusionRemove.add(potential);
							}
						}
					}
					conclusion.removeAll(conclusionRemove);
					conclusion.add(ne);
				} else if (ne.getEndTick() == finalNoteTickEnd) {
					conclusion.add(ne);
				}
			}
			long fermataEndTick;
			if (organic) {
				fermataEndTick = qtm.microsToTickABCOrganic(part.conclusionFermata * 1000L + qtm.tickToMicrosABCOrganic(finalNoteTickEnd));
			} else {
				fermataEndTick = qtm.quantize(qtm.microsToTickABC(part.conclusionFermata * 1000L + qtm.tickToMicrosABC(finalNoteTickEnd)), part);
			}
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
	}

	private void createDoublingNoteEvents(AbcPart part, List<AbcNoteEvent> events, int trackNumber, MidiNoteEvent ne,
			long startTick, long endTick, int velocity) {
		Boolean[] doubling = part.getSectionDoubling(ne.getStartTick(), trackNumber);

		if (doubling[0] && ne.note.id - 24 > Note.MIN.id) {
			Note mappedNote2 = part.mapNoteEvent(trackNumber, ne, ne.note.id - 24);
			if (mappedNote2 != null) {
				AbcNoteEvent newNE2 = createNoteEvent(ne, mappedNote2, velocity, startTick, endTick, qtm, !part.isChromatic(trackNumber));
				//newNE2.doubledNote = true;// prune these first
				events.add(newNE2);
			}
		}
		if (doubling[1] && ne.note.id - 12 > Note.MIN.id) {
			Note mappedNote2 = part.mapNoteEvent(trackNumber, ne, ne.note.id - 12);
			if (mappedNote2 != null) {
				AbcNoteEvent newNE2 = createNoteEvent(ne, mappedNote2, velocity, startTick, endTick, qtm, !part.isChromatic(trackNumber));
				//newNE2.doubledNote = true;
				events.add(newNE2);
			}
		}
		if (doubling[2] && ne.note.id + 12 < Note.MAX.id) {
			Note mappedNote2 = part.mapNoteEvent(trackNumber, ne, ne.note.id + 12);
			if (mappedNote2 != null) {
				AbcNoteEvent newNE2 = createNoteEvent(ne, mappedNote2, velocity, startTick, endTick, qtm, !part.isChromatic(trackNumber));
				//newNE2.doubledNote = true;
				events.add(newNE2);
			}
		}
		if (doubling[3] && ne.note.id + 24 < Note.MAX.id) {
			Note mappedNote2 = part.mapNoteEvent(trackNumber, ne, ne.note.id + 24);
			if (mappedNote2 != null) {
				AbcNoteEvent newNE2 = createNoteEvent(ne, mappedNote2, velocity, startTick, endTick, qtm, !part.isChromatic(trackNumber));
				//newNE2.doubledNote = true;
				events.add(newNE2);
			}
		}
	}

	private void applyLegato(AbcPart part, int trackNumber, List<MidiNoteEvent> listOfNotes) {
		if (part.getInstrument().sustainable) {
			long lastTick = 0L;
			for (int curr = 0; curr < listOfNotes.size(); curr++) {
				MidiNoteEvent currNe = listOfNotes.get(curr);
				if (currNe.getEndTick() > lastTick) lastTick = currNe.getEndTick();
				if (!part.getSectionLegato(trackNumber, currNe.getStartTick()) || lastTick > currNe.getEndTick()) {
					currNe.setLegatoEndTick(part, null);
					continue;
				}
				long currEnd = currNe.getEndTick();
				long nextEnd = currEnd;
				long currEndMicro;
				if (organic) {
					currEndMicro = qtm.tickToMicrosOrganic(currEnd);
				} else {
					currEndMicro = qtm.tickToMicros(currEnd);
				}
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
					long nextEndMicro;
					if (organic) {
						nextEndMicro = qtm.tickToMicrosOrganic(nextEnd);
					} else {
						nextEndMicro = qtm.tickToMicros(nextEnd);
					}
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
	}

	private List<MidiNoteEvent> expandXtraDrumNotes(AbcPart part, int trackNumber) {
		boolean specialDrumNotes = false;
		if (part.getInstrument() == LotroInstrument.BASIC_DRUM) {
			TrackInfo tInfo = part.getAbcSong().getSequenceInfo().getTrackInfo(trackNumber);
			for (int inNo : tInfo.getNotesInUse()) {
				byte outNo = part.getDrumMap(trackNumber).get(inNo);
				if (outNo > part.getInstrument().highestPlayable.id) {
					specialDrumNotes = true;
					break;
				}
			}
		}
		List<MidiNoteEvent> listOfNotes = new ArrayList<>(part.getTrackEvents(trackNumber));

		if (specialDrumNotes) {
			List<MidiNoteEvent> extraList = new ArrayList<>();
			List<MidiNoteEvent> removeList = new ArrayList<>();
			for (MidiNoteEvent ne : listOfNotes) {
				Note possibleCombiNote = part.mapNote(trackNumber, ne.note.id, ne.getStartTick());
				if (possibleCombiNote != null && LotroCombiDrumInfo.noteIdIsXtraNote(possibleCombiNote.id)) {
					MidiNoteEvent extra1 = LotroCombiDrumInfo.getId1(ne, possibleCombiNote, ne.midiPan);
					MidiNoteEvent extra2 = LotroCombiDrumInfo.getId2(ne, possibleCombiNote, ne.midiPan);
					extraList.add(extra1);
					extraList.add(extra2);
					removeList.add(ne);
					// Notice that bent notes on chromatic tracks are treated as only 1 note here
				} else if (possibleCombiNote != null && possibleCombiNote.id > LotroCombiDrumInfo.maxCombi.id) {
					// Just for safety, should never land here.
					System.err.println("// Just for safety, should never land here:+\n"+ne);
					removeList.add(ne);
				}
			}
			listOfNotes.removeAll(removeList);
			listOfNotes.addAll(extraList);
		}
		return listOfNotes;
	}
	
	/**
	 * Combine the tracks into one, quantize the note lengths, separate into chords.
	 */
	private List<Chord> combineOrganic(AbcPart part, boolean preview) throws AbcConversionException {
		// Combine the events from the enabled tracks
		List<AbcNoteEvent> events = new ArrayList<>();
		for (int t = 0; t < part.getTrackCount(); t++) {
			if (part.isTrackEnabled(t)) {
				List<MidiNoteEvent> listOfNotes = expandXtraDrumNotes(part, t);
				
				applyLegato(part, t, listOfNotes);
				
				for (MidiNoteEvent ne : listOfNotes) {
					// Skip notes that are outside of the play range.
					if (ne.getEndTick() <= exportStartTick) {//  || ne.getStartTick() >= exportEndTick
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
						

						int[] sva = part.getSectionVolumeAdjust(t, ne);
						int velocity = part.getSectionNoteVelocity(t, ne);
						velocity = (int) ((velocity + part.getTrackVolumeAdjust(t) + sva[0]) * 0.01f * (float) sva[1] * 0.01f * (float) sva[2]);

						AbcNoteEvent newNE = createNoteEvent(ne, mappedNote, velocity, startTick, endTick, qtm, !part.isChromatic(t));
						
						/*
						 * if (preview) { // Only associate if doing preview newNE.origEvent = new
						 * ArrayList<NoteEvent>(); newNE.origEvent.add(ne); }
						 */
						events.add(newNE);

						createDoublingNoteEvents(part, events, t, ne, startTick, endTick, velocity);
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
		
		applyFermata(part, events);

		// subdivide bent notes
		long lastEnding = 0;
		AbcNoteEvent lastEvent = null;
		List<AbcNoteEvent> extraEvents = new ArrayList<>();
		List<AbcNoteEvent> deleteEvents = new ArrayList<>();
		
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

		events.addAll(extraEvents);// add all the pitchbend fractions to the main event list
		events.removeAll(deleteEvents);
		//System.out.println("Something removed: "+deleteEvents.size());
		//System.out.println("Something added: "+extraEvents.size());
		
		Collections.sort(events);
		
		if (events.size() == 0) {
			System.err.println("Export to preview/abc: "+metadata.getSongTitle()+" has a part with no exported notes.");
			if (!preview) ProjectFrame.feed("Note: Song has a part with no exported notes ("+part.getTitle()+")", null);
			return new ArrayList<>();
		}
		
		// Add initial rest if necessary
		
		if (events.get(0).getStartTick() > exportStartTick) {
			events.add(0, new AbcNoteEvent(Note.REST, Dynamics.DEFAULT.midiVol, exportStartTick,
					events.get(0).getStartTick(), qtm, null));
		}

		// Remove duplicate notes
		removeDuplicateNotes(events, part.getInstrument());
		
		Collections.sort(events);// needed due to removeDuplicateNotes adding thirds
		
		/*
		// Verify duplicates does not exist
		removeDuplicateNotesVerify(events, part.getInstrument());
		*/
		
		useRestToShortenChords = part.getInstrument().sustainable && useRestsInChords;
		
		List<Chord> chords = null;

		List<AbcNoteEvent> eventsCopy = new ArrayList<>();
		for (AbcNoteEvent n : events) {
			eventsCopy.add(n.copy());
		}
		if (organic2) chords = processOrganic2(part, events);
		else chords = processOrganic(part, events);
		if (useRestToShortenChords) {
			int max = 0;
			try {
				max = PolyphonyHistogram.maxPolyInPart(part, chords, organic, qtm);
			} catch (IOException e) {
				throw new AbcConversionException("Failed to read instrument sample durations.", e);
			}
			if (max == 6) {
				//System.out.println(" ---- "+part.getAbcSong().getTitle()+" ("+part.getTitle()+"): poly restore");
				useRestToShortenChords = false;
				if (organic2) chords = processOrganic2(part, eventsCopy);
				else  chords = processOrganic(part, eventsCopy);
				part.setMaxPoly(6);
			} else if (max > 6) {
				part.setMaxPoly(max);
			} else {
				//System.out.println(" pass "+part.getAbcSong().getTitle()+" ("+part.getTitle()+"): poly okay");
				part.setMaxPoly(6);
			}
		} else {
			//System.out.println(" pass "+part.getAbcSong().getTitle()+" ("+part.getTitle()+"): poly off");
			part.setMaxPoly(6);
		}
		
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
	 * process the notes using original organic principle
	 */
	private List<Chord> processOrganic(AbcPart part, List<AbcNoteEvent> events) {
		boolean assertionsEnabled = false;
		assert assertionsEnabled = true;
		
		breakLongNotesOrganic(part, events);

		List<Chord> chords = new ArrayList<>(events.size() / 2);
		List<AbcNoteEvent> tmpEvents = new ArrayList<>();

		long minimumMicros = AbcConstants.getShortestNoteMicros(qtm.getPrimaryExportTempoBPM());
		
		// Combine notes that play at the same time into chords
		
		final boolean removeGliss = false;
		Chord curChord = new Chord(events.get(0));
		Chord prevChord = null;
		Chord prevRestChord = null;
		debugOutput(3,part.getTitle()+ ": Adding to curChord, note i=0 ticks:"+events.get(0).getStartTick()+"-"+events.get(0).getEndTick()+" "+events.get(0).note);
		chords.add(curChord);
		MAIN:for (int i = 1; i < events.size(); i++) {
			AbcNoteEvent ne = events.get(i);
			if (ne.tiesFrom == ne) continue;// hack
			if (curChord.getStartTick() == ne.getStartTick()) {
				// This note starts at the same time as the rest of the notes in the chord
				assert !curChord.isRest();
				curChord.add(ne);
				debugOutput(3,part.getTitle()+ ": Adding to curChord note i="+i+" ticks:"+ne.getStartTick()+"-"+ne.getEndTick()+" "+ne.note);
			} else {								
				// The curChord has all the notes it will get.
				
				// Note that ne can be a rest from cut up initial rest
				
				debugOutput(2,"\n"+part.getTitle()+ ": Processing note i="+i+" ticks:"+ne.getStartTick()+"-"+ne.getEndTick()+" "+ne.note);
				
				// remove zero duration notes if longer notes start at same time in curr chord
				if (curChord.getLongestEndTick() > curChord.getStartTick()) {
					for (int j = 0; j < curChord.size(); j++) {
						AbcNoteEvent jne = curChord.get(j);
						if (jne.getEndTick() == jne.getStartTick()) {
							// this note is zero duration and others in the chord is not
							curChord.remove(jne);
							debugOutput(2,part.getTitle()+" Removed zero dura note ("+jne.note.abc+")");
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
				
				if (curChord.early != null) {
					//must be AFTER 'remove zero among longer'
					//is BEFORE pruning to save pruning twice
					curChord.setEarlyStartTick(useRestToShortenChords);
					if (prevChord != null) prevChord.recalcEndTick();
					debugOutput(3,part.getTitle()+ ": applying early start. curChord now start at "+curChord.getStartTick());
					i--;
					continue MAIN;
				}
				
				// We prune AFTER removed shorter zero notes, so they dont take up slot from
				// 6 max notes.
				List<AbcNoteEvent> deadnotes = curChord.prune(part.getInstrument().sustainable,
						part.getInstrument() == LotroInstrument.BASIC_DRUM, part.getInstrument().isPercussion, part);
				removeNotes(events, deadnotes, part);
				if (!deadnotes.isEmpty()) {
					// One of the tiedTo notes that was pruned might be ne note,
					// so we go one step back and re-process events.get(i)
					i--;
					debugOutput(3,part.getTitle()+ ": something was pruned");
					continue MAIN;
				}
				
				// Create a new chord
				Chord nextChord = new Chord(ne);
				debugOutput(2,part.getTitle()+ ": Create new chord "+ne.note.id);
				
				
				// we first identify the two next chords as they will look after being cut up:
				long curChordRoomMicros = qtm.tickToMicrosABCOrganic(ne.getStartTick()) - qtm.tickToMicrosABCOrganic(curChord.getStartTick());
				AbcNoteEvent ne1 = ne;
				AbcNoteEvent ne2 = null;
				long ne2End = Long.MAX_VALUE;
				long ne2Start = Long.MAX_VALUE;
				Chord nextChordTmp = new Chord(ne);
				for (int ii = i+1; ii < events.size(); ii++) {
					// find the shortest non-zero dura notes coming next
					// remember events are sorted not only by start tick, but also end tick
					AbcNoteEvent over = events.get(ii);
					if (ne2 != null && over.getStartTick() > ne2.getStartTick()) {
						break;
					}
					if (over.getStartTick() == ne.getStartTick() && (ne1.getLengthTicks() == 0L || (over.getEndTick() < ne1.getEndTick() && over.getLengthTicks() != 0L))) {
						// over is shorter than ne1 or ne1 is zero. over starts at same time as ne.
						if (ne1.getEndTick() > over.getEndTick()) {
							ne2Start = over.getEndTick();
							ne2End = ne1.getEndTick();
						}
						ne1 = over;
						nextChordTmp.add(over);
					} else if (over.getStartTick() == ne.getStartTick() && over.getLengthTicks() != 0L && ne1.getLengthTicks() != 0L && over.getLengthTicks() > ne1.getLengthTicks()) {
						// over is longer than ne1 and neither is zero. over starts at same time as ne.
						// this means over is going to be cut up, so ne2 will become ending of over.
						ne2Start = ne1.getEndTick();
						ne2End = over.getEndTick();
						nextChordTmp.add(over);
					} else if (over.getStartTick() == ne.getStartTick()) {
						nextChordTmp.add(over);
					}
					if (over.getStartTick() > ne.getStartTick() && (ne2 == null || ne2.getLengthTicks() == 0L)) {
						// over starts after ne.
						ne2 = over;
						if (ne2.getStartTick() < ne2Start) {
							ne2Start = over.getStartTick();
							ne2End = over.getEndTick();
						}
					}
				}
				int nextValue = calcValue(nextChordTmp, part.getInstrument().sustainable);
				// ne1 now represent the first chord, it might be longer than ne if ne is zero dura.
				if ((ne2 != null && ne2.getStartTick() > ne2Start) || (ne2 == null && ne2Start < Long.MAX_VALUE)) {
					ne2 = new AbcNoteEvent(Note.A0, 64, ne2Start, ne2End, qtm, ne1.origNote);
				}
				// ne2 now represent the second chord
				long ne1RoomMicros = ne2 == null?Long.MAX_VALUE:qtm.tickToMicrosABCOrganic(ne2.getStartTick()) - qtm.tickToMicrosABCOrganic(ne.getStartTick());
				long neMicros = qtm.tickToMicrosABCOrganic(ne.getEndTick()) - qtm.tickToMicrosABCOrganic(ne.getStartTick());
				long ne1Micros = qtm.tickToMicrosABCOrganic(ne1.getEndTick()) - qtm.tickToMicrosABCOrganic(ne1.getStartTick());
				long ne2Micros = ne2 == null?0L:qtm.tickToMicrosABCOrganic(ne2.getEndTick()) - qtm.tickToMicrosABCOrganic(ne2.getStartTick());
				
				// handle fast glissando
				boolean glissRemoved = deprecated1(part, events, minimumMicros, debug, removeGliss, curChord, ne,
						curChordRoomMicros, ne1RoomMicros, ne1Micros, ne2Micros);
				
				if (glissRemoved) {
					debugOutput(1,part.getTitle()+ ": deprecated 1st");
					i--;
					continue MAIN; 
				}
				
				// turn very fast arpeggio into block chord
				if (ne.tiesFrom == null && ne.note != Note.REST
						&& curChordRoomMicros < minimumMicros
						&& (curChord.getEndTick() > ne.getStartTick() || part.getInstrument().isPercussion)
						&& !curChord.dontMove1 && !curChord.glissando && !curChord.isRest()) {
					// curr end before next start prevents handling grace notes, they will be deleted later if they too short
					for (AbcNoteEvent small : curChord.getNotes()) {
						if (small.tiesFrom != null || small.tiesTo != null) {
							// curr chord has already been cut up, or broken up due to being long notes, skip it
							i--;
							curChord.dontMove1 = true;// to prevent infinite loop
							debugOutput(3,part.getTitle()+" Keep arpeggio (ties involved)");
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
									// TODO: serious think about what going on here and write detailed comments
									i--;
									curChord.dontMove1 = true;// to prevent infinite loop
									debugOutput(3,part.getTitle()+" Keep arpeggio (next chord has same note)");
									continue MAIN;
								}
							}
						}
					}
					
					// Its too complex to move current chord into next cords position, so we do the opposite:					
					debugOutput(1,part.getTitle()+" Turned arpeggio into block chord (early start)");
					ne.setStartTick(curChord.getStartTick());
					curChord.add(ne);// we note that this will later be pruned (again)
					curChord.arp += 1;
					curChord.recalcEndTick();
					continue MAIN;
				} else {
					debugOutput(4,"Not arp:\n");
					debugOutput(4," microsTillNext < minimumMicros "+(curChordRoomMicros < minimumMicros));
					debugOutput(4," overlap "+(curChord.getEndTick() > ne.getStartTick()));
				}
				
				
				long shortest = qtm.tickToMicrosABCOrganic(curChord.getEndTick()) - qtm.tickToMicrosABCOrganic(curChord.getStartTick());
				long space = qtm.tickToMicrosABCOrganic(ne.getStartTick()) - qtm.tickToMicrosABCOrganic(curChord.getStartTick());
				long minEndMicro = qtm.tickToMicrosABCOrganic(curChord.getStartTick()) + minimumMicros;
				long curMinEndTick = qtm.microsToTickABCOrganic(minEndMicro);
				if (shortest < minimumMicros && space >= minimumMicros && ne.getStartTick() >= curMinEndTick) {
					// one or more notes in curChord is too short, but they have room to expand
					curChord.setEndTickExpand(curMinEndTick);
					debugOutput(2,part.getTitle()+ ": Expanded");
				}
				
				
				// cut up curChord if some notes longer than others
				boolean reprocessCurrentNote = false;
				long curEndMicro = qtm.tickToMicrosABCOrganic(curChord.getEndTick());
				long curStartMicro = qtm.tickToMicrosABCOrganic(curChord.getStartTick());
				
				/*
				if (curEndMicro < curStartMicro) {
					qtm.tickToMicrosABCOrganic2(curChord.getStartTick(),curChord.getEndTick());
				}
				*/
				
				long targetEndTick = Math.min(nextChord.getStartTick(), curChord.getEndTick());
				long curMinEndFitTick = Math.min(curMinEndTick, targetEndTick);
				if (!curChord.glissando) {
					for (int j = 0; j < curChord.size(); j++) {
						AbcNoteEvent jne = curChord.get(j);
						//System.out.println(jne.note+" is on cutting table "+jne.getStartTick()+" - "+jne.getEndTick()+". targetEndTick="+targetEndTick+" curMinEndFitTick="+curMinEndFitTick);
						if (!part.getInstrument().sustainable) {
							// This might be a bit controversial
							// But here we fix the duration on the chord to minimum or shorter,
							// since instrument is not sustainable anyway.
							// Controversial due to you can't later experiment by putting
							// a sustained instrument on this part, it will be ruined for that purpose.
							// But this will make fitting it all together easier.
							jne.setEndTick(curMinEndFitTick);
							//System.out.println(jne.note+" curMinEndFitTick="+curMinEndFitTick);
						} else if (!useRestToShortenChords && jne.getEndTick() > targetEndTick) {						
							long noteEndMicro = qtm.tickToMicrosABCOrganic(jne.getEndTick());
							if (curChord.getEndTick() == targetEndTick && noteEndMicro-curEndMicro < minimumMicros/2 && jne.tiesTo == null) {
								// note ends approx same time as end of chord
								// we make it end same time as shortest note in chord,
								// chord might become slightly longer later.
								jne.setEndTick(curChord.getEndTick());
								debugOutput(2,part.getTitle()+ ": Fit note ending to chord ending");
							} else {
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
					}
				}
				// The shorter notes will have changed the chord's duration
				curChord.recalcEndTick();
				if (reprocessCurrentNote) {
					i--;
					debugOutput(2,part.getTitle()+ ": Chord was cut up, reprocessing..");
					continue MAIN;
				}
				
				// Insert a rest into current chord if need to shorten chord
				if (useRestToShortenChords && !curChord.hadRestAndNotes()
						&& curChord.getEndTick() > nextChord.getStartTick()) {// && curChord.getEndTick() > targetEndTick
					// The reason we only do this for sustainable is they benefit from this only,
					// and adding a rest do limit the same time starting notes to 5.
					// As long as the shortest is longer than next start we add a rest
					// This is due to pruning might result in longer chord later,
					// So we force a short chord by putting in a rest.
					// If there is notes same dura or shorter as the rest we insert,
					// and they don't get pruned, the rest itself will get pruned, to not bloat.
					tmpEvents.clear();
					tmpEvents.add(new AbcNoteEvent(Note.REST, Dynamics.DEFAULT.midiVol, curChord.getStartTick(),
							Math.max(curMinEndTick, nextChord.getStartTick()), qtm, null));
					breakLongNotesOrganic(part, tmpEvents);
					if (tmpEvents.size() > 0) {
						int ins = Collections.binarySearch(events, tmpEvents.get(0));
						if (ins < 0)
							ins = -ins - 1;
						
						assert (ins <= i);
						
						// back up and process again
						reprocessCurrentNote = true;
						curChord.add(tmpEvents.get(0));
						events.add(ins, tmpEvents.get(0));
						if (tmpEvents.size() > 1) {
							System.out.println(part.getAbcSong().getSongTitle()+": Rest needed to be broken up !!!!!!!!!!");
						}
						if (curChord.size() > 6) {
							// uncommon, less than 10 songs out of 1000 had this happen 
							//System.out.println(part.getAbcSong().getSongTitle()+": 6 note chord had rest added !!!!!!!!!!");
						}
					}
					debugOutput(3,part.getTitle()+ ": Inserted a rest into current chord to make it shorter newEndtick="+Math.max(curMinEndTick, nextChord.getStartTick()));
				}
				curChord.recalcEndTick();
				if (reprocessCurrentNote) {
					//i--;
					debugOutput(2,part.getTitle()+ ": curChord was shortened using rests, reprocessing..");
					continue MAIN;
				}
				
				// Expand into gap to next chord if the gap is smaller than 0.06s
				long oldCurEndMicro = qtm.tickToMicrosABCOrganic(curChord.getEndTick());
				if (curChord.getEndTick() < nextChord.getStartTick()) {
					long restMicros = qtm.tickToMicrosABCOrganic(nextChord.getStartTick()) - oldCurEndMicro;
					if (restMicros <= minimumMicros && curChord.expandedMicros == null) {
						assert nextChord.getStartTick() > curChord.getEndTick(); 
						curChord.setEndTickExpand(nextChord.getStartTick());//TODO: breakup elongated notes
						
						// later we might undo some of this, expandedMicros is how much we are allowed to undo.
						curChord.expandedMicros = Math.min((oldCurEndMicro-curStartMicro)-minimumMicros, restMicros);
						if (curChord.expandedMicros <= 0L) curChord.expandedMicros = null;
						
						debugOutput(2,part.getTitle()+ ": Bridged rest");
					}
				}
				
				// Handle curr chord if its shorter than 0.06s
				if (curChord.getEndTick() < curMinEndTick && !curChord.dontMove2) {
					long earlyCurrMicro = qtm.tickToMicrosABCOrganic(curChord.getEndTick()) - minimumMicros;
					long earlyCurrTick = qtm.microsToTickABCOrganic(earlyCurrMicro);
					debugOutput(1,part.getTitle()+": curChord too short. ends at "+curChord.getEndTick()+", ideal end at "+curMinEndTick);
					// test if we should early start curr chord
					if (!useRestToShortenChords && ne2 != null && ne1RoomMicros < minimumMicros
							&& curStartMicro - earlyCurrMicro < minimumMicros/2) {
						// Both curr and ne does not have enough room.
						// We need less than half of minimum though
						if (prevRestChord != null
								&& earlyCurrMicro - qtm.tickToMicrosABCOrganic(prevRestChord.getStartTick()) > minimumMicros) {
							// There is a rest before curr that can be expanded into
							curChord.early = earlyCurrTick;//TODO: breakup elongated notes
							curChord.dontMove2 = true;
							debugOutput(2,part.getTitle()+": Early start of 1st of two trills/gliss notes (rest). cur_early="+earlyCurrTick+" cur_start="+curChord.getStartTick()+" prev_end="+prevRestChord.getEndTick());
							prevRestChord.setEndTickRetract(earlyCurrTick);
							
							i--;							
							continue MAIN;
						} else if (prevRestChord == null && prevChord != null && prevChord.expandedMicros != null
								&& prevChord.expandedMicros > curStartMicro - earlyCurrMicro) {
							// There is a chord before curr that can be expanded into
							curChord.early = earlyCurrTick;//TODO: breakup elongated notes
							curChord.dontMove2 = true;
							// any ties will still hold as there will be no gap
							debugOutput(2,part.getTitle()+": Early start of 1st of two trills/gliss notes (chord). cur_early="+earlyCurrTick+" cur_start="+curChord.getStartTick()+" prev_end="+prevChord.getEndTick());
							prevChord.setEndTickRetract(earlyCurrTick);
							prevChord.expandedMicros = null;
							
							i--;							
							continue MAIN;
						}
					}
					
					// Else try to make it longer					
					if (nextChord.getStartTick() >= curMinEndTick) {
						curChord.setEndTickExpand(curMinEndTick);
						debugOutput(3,part.getTitle()+ ": trying to expand curChord to end at "+curMinEndTick);
					} else {
						// there was not room for a larger chord
						int curValue = calcValue(curChord, part.getInstrument().sustainable);
						long neMicroStart = qtm.tickToMicrosABCOrganic(ne.getStartTick());
						if (!curChord.glissando) {
							boolean isRattle = true;
							for (AbcNoteEvent n : curChord.getNotes()) {
								if (!isRattle(part,n)) {
									isRattle = false;
									break;
								}
							}
							if ((ne2 == null || ne1RoomMicros > minimumMicros*2) && ne1.getEndTick() > curMinEndTick
									&& (minEndMicro-neMicroStart < minimumMicros/2)) {//  || ne1Micros > minimumMicros*2
								// delay start of next chord up to 30 ms
								long oldStartTick = ne.getStartTick();
								for (int ii = i; ii < events.size(); ii++) {
									AbcNoteEvent over = events.get(ii);
									if (over.getStartTick() > oldStartTick) {
										break;
									}
									if (over.getStartTick() == oldStartTick) {
										// should be ok to do this even if tiesFrom is non-null
										// since the tiesFrom has been expanded to end here
										if (over.getLengthTicks() == 0L) {
											over.setEndTick(curMinEndTick);
										}
										over.setStartTick(curMinEndTick);
										
										// TODO: Delaying start of next
									}
								}
								
								//going back and forth between micros and ticks is not always 1:1, so we stop infinite loops by setting this
								curChord.dontMove2 = true;
								curChord.setEndTickExpand(curMinEndTick);
								
								i--;
								debugOutput(2,part.getTitle()+" Delayed sequential chord by "+ ((minEndMicro-neMicroStart)/1000)+" ms 1");
								continue MAIN;
							} else if (!isRattle && ne2 != null && (isRattle(part, ne) || (ne1RoomMicros < minimumMicros
									&& neMicros < minimumMicros))) {
								// Both curr and next chord does not have enough room or curChord is rattle(s)
								// ne is fairly short (or rattle) and will have to go
								// TODO: I have doubt about the ties. ne might even be tied to curr chord.
								//       And if its tiesTo is also there, removing it should instead
								//       tie curr chord to the one after ne, and expand curr chord to ne2.
								//       I also doubt if its smart at all. Maybe next chord has 4 notes
								//       and current has 1 etc. etc.
								//       Deleting a short note might not even allow curChord to exist anyway
								//       As the ne after ne might be longer and should not be deleted.
								events.remove(ne);
								
								// TODO: these ties should perhaps prevent it from being removed, TBD
								if (ne.tiesFrom != null) {
									ne.tiesFrom.tiesTo = null;
								}
								if (ne.tiesTo != null) {
									if (!part.getInstrument().sustainable) {
										// If non-sustained then should remove ne.tiesTo
										// we do this by a hack when setting from to itself
										// then we just skip the notes from being added.
										AbcNoteEvent tie = ne.tiesTo;
										while (tie != null) {
											tie.tiesFrom = tie;
											tie = tie.tiesTo;
										}
									}
									ne.tiesTo.tiesFrom = null;
								}
								// we don't use dontMove2 here, as we might want to get back in here with other ne.
								i--;
								
								debugOutput(2,part.getTitle()+": Deleted ne, is second of two trills/gliss notes, dura="+ (ne1Micros/1000L)+" ms");
								continue MAIN;
							} else if (curChord.arp > 1) {
								boolean doable = true;
								if (ne.note == Note.REST) doable = false;
								if (ne.tiesFrom != null) {
									doable = false;
								}
								for (AbcNoteEvent small : curChord.getNotes()) {									
									if (small.note == ne.note) {
										// next note cannot be added to block chord,
										// as one with same pitch is there already
										
										if (ne1Micros < minimumMicros*3L/2L || part.getInstrument().isPercussion) {
											// next chord will be short is short, we remove next note
											
											if (ne.tiesTo != null) {
												if (!part.getInstrument().sustainable) {
													// If non-sustained then should remove ne.tiesTo
													// we do this by a hack when setting from to itself
													// then we just skip the notes from being added.
													AbcNoteEvent tie = ne.tiesTo;
													while (tie != null) {
														tie.tiesFrom = tie;
														tie = tie.tiesTo;
													}
												}
												ne.tiesTo.tiesFrom = null;
											}
											if (ne.tiesFrom != null) {
												ne.tiesFrom.tiesTo = null;
											}
											events.remove(ne);
											i--;
											debugOutput(1,part.getTitle()+": Removed short dura note just after arpeggio");
											continue MAIN;
										}
										doable = false;
										break;
									}
									
								}
								if (doable) {
									ne.setStartTick(curChord.getStartTick());
									curChord.add(ne);// we note that this will later be pruned (again)
									curChord.arp += 1;
									curChord.recalcEndTick();
									debugOutput(1,part.getTitle()+": Included late arpeggio to block chord");
									continue MAIN;
								}
							} else if (useRestToShortenChords && curValue > nextValue) {
								// Curr chord has higher value than next chord
								// so its more than just a gracenote, we remove next instead.
								// TODO: Could investigate if could delay start of next.
								if (ne.tiesTo != null) {
									if (!part.getInstrument().sustainable) {
										// If non-sustained then should remove ne.tiesTo
										// we do this by a hack when setting from to itself
										// then we just skip the notes from being added.
										AbcNoteEvent tie = ne.tiesTo;
										while (tie != null) {
											tie.tiesFrom = tie;
											tie = tie.tiesTo;
										}
									}
									ne.tiesTo.tiesFrom = null;
								}
								if (ne.tiesFrom != null) {
									ne.tiesFrom.tiesTo = null;
								}
								events.remove(ne);
								i--;
								curChord.removeRests();// It might not need the rest anymore so we remove it. Might get re-added.
								curChord.recalcEndTick();
								debugOutput(1,part.getTitle()+": Removed low value next chord");
								//note that this will make next chord even lower value,
								//so rest of next chords notes will also be removed.
								continue MAIN;
							}
							// give up and schedule curr chord for deletion, it likely contains a grace note
							curChord.setEndTickRetract(curChord.getStartTick());
							curChord.delete = true;
							debugOutput(2,part.getTitle()+": Removed short dura chord with "+curChord.size()+" notes. "+curChord.getStartTick());
							
						} else {
							// deprecated
							debugOutput(1,part.getTitle()+ ": deprecated!!");
							curChord.setEndTickExpand(curMinEndTick);
							
							boolean reRun = deprecated2(part, events, minimumMicros, debug, curChord, i, ne, ne1, ne2,
									ne1RoomMicros, ne1Micros, minEndMicro, curMinEndTick, neMicroStart);
							
							if (reRun == true) {
								continue MAIN;
							}
						}
					}
				}
				
				
				//System.out.println(curChord.getEndTick()+" < "+nextChord.getStartTick());
				
				// Insert a rest between the cur and next if needed
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
							prevRestChord = restChord;//break long notes keep them sorted so this is last
						}
						debugOutput(3,part.getTitle()+ ": add rest: "+curChord.getEndTick()+" - "+nextChord.getStartTick());
					} else {
						if (curChord.delete) {
							// If we reach this code, then curr has been scheduled for deletion.
							// Here we can either make next chord start sooner
							// or find the chord before curr and expand that.
							Chord chordToExpand = null;
							boolean found = false;
							for(int k = chords.size()-1;k>=0;k--) {
								if (!chords.get(k).delete) {
									chordToExpand = chords.get(k);
									found = true;
									break;
								}
							}
							/*
							System.out.println("-1 "+chords.get(chords.size()-1).toStringDura());
							System.out.println("-2 "+chords.get(chords.size()-2).toStringDura());
							System.out.println("-3 "+chords.get(chords.size()-3).toStringDura());
							*/
							long expandCandidateMicros = found?(qtm.tickToMicrosABCOrganic(chordToExpand.getEndTick()) - qtm.tickToMicrosABCOrganic(chordToExpand.getStartTick())):0L; 
							if (!found || expandCandidateMicros > AbcConstants.LONGEST_NOTE_MICROS - AbcConstants.ONE_SECOND_MICROS/2 || ne1RoomMicros < minimumMicros) {
								// We make next start sooner, since curChord is first chord or prev is longer than 7.5s
								// this has the added benefit that if next chord is
								// too short too, it will be longer.
								nextChord.early = curChord.getEndTick();//TODO: breakup elongated notes
								debugOutput(2,part.getTitle()+ ": Early start A");
							} else if (found) {
								chordToExpand.setEndTickExpand(ne.getStartTick());//TODO: breakup elongated notes
								debugOutput(2,part.getTitle()+ ": Prev ("+chordToExpand.getStartTick()+") expanded to "+ne.getStartTick()+" isRest="+chordToExpand.isRest()+" isDeleted="+chordToExpand.delete);
								//curChord = chordToExpand;
							} else {
								nextChord.early = curChord.getEndTick();//TODO: breakup elongated notes
								debugOutput(2,part.getTitle()+ ": Early start B");
							}
							curChord = chordToExpand;
						} else {
							curChord.setEndTickExpand(ne.getStartTick());//TODO: breakup elongated notes
							debugOutput(2,part.getTitle()+ ": Chord expanded to fill gap");
						}
						prevRestChord = null;
						
					}
				} else {
					prevRestChord = null;
					
					if (useRestToShortenChords) {
						if (curChord.getEndTick() > nextChord.getStartTick()) {
							ne.setStartTick(curChord.getStartTick());
							curChord.removeRests();
							AbcNoteEvent same = null;
							for (AbcNoteEvent cn : curChord.getNotes()) {
								if (cn.note == ne.note) {
									same = cn;
								}
							}
							if (same != null) {
								// delete note in curChord that has same pitch as ne
								curChord.remove(same);
								if (same.tiesFrom != null && same.tiesTo == ne) {
									ne.tiesFrom = same.tiesFrom;
									same.tiesFrom.tiesTo = ne;
								} else {
									ne.tiesFrom = null;
									if (same.tiesFrom != null) same.tiesFrom.tiesTo = null;
								}
								same.tiesFrom = null;
								same.tiesTo = null;
							} else if (ne.tiesFrom != null) {
								ne.tiesFrom.setEndTick(ne.getStartTick());
								debugOutput(2,part.getTitle()+": Adjusting tiesFrom endTick while shuffling ne into curr");
							}
							debugOutput(1,part.getTitle()+": Shuffle ne into curr");
							i--;
							continue MAIN;
						}
					}
				}
				
				
				if (assertionsEnabled && curChord != null) {
					/*
					 * 
					 * DEBUG STUFF
					 * 
					 */
					for (AbcNoteEvent evt : curChord.getNotes()) {
						long mics = qtm.tickToMicrosABCOrganic(evt.getEndTick()) - qtm.tickToMicrosABCOrganic(evt.getStartTick());
						assert mics <= TimingInfo.LONGEST_NOTE_MICROS + 500L: evt.note+" micros="+mics;
					}					
					long endCur = curChord.getEndTick();
					curChord.recalcEndTick();
					assert endCur == curChord.getEndTick();
					
					//Chord realCurChord = chords.size()>0?chords.get(chords.size()-1):curChord;
					Chord realCurChord = null;
					boolean found = false;
					for(int k = chords.size()-1;k>=0;k--) {
						if (!chords.get(k).delete) {
							realCurChord = chords.get(k);
							found = true;
							break;
						}
					}
					endCur = found?realCurChord.getEndTick():nextChord.getStartTick();
					assert endCur == nextChord.getStartTick() || (nextChord.early != null && endCur == nextChord.early):endCur+" != "+nextChord.getStartTick()+" next_early="+nextChord.early;
					
					/*// debug code to investigate specific chord gaps
					if (!useRestToShortenChords && curChord.getStartTick() == 19952) {
						System.out.println("\nCh: "+curChord.toStringDura()+" useRest="+useRestToShortenChords);
						
						for (AbcNoteEvent n : curChord.getNotes()) {
							System.out.println(n.note+": "+n.getStartTick()+" - "+n.getEndTick());
						}
						if (chords.size()>1) {
							System.out.println("\npre: "+chords.get(chords.size()-2).toStringDura());
							for (AbcNoteEvent n : chords.get(chords.size()-2).getNotes()) {
								System.out.println(n.note+": "+n.getStartTick()+" - "+n.getEndTick());
							}
							if (chords.size()>2) {
								System.out.println("\npre-: "+chords.get(chords.size()-3).toStringDura());
								for (AbcNoteEvent n : chords.get(chords.size()-3).getNotes()) {
									System.out.println(n.note+": "+n.getStartTick()+" - "+n.getEndTick());
								}
							}
						}
						//assert chords.get(chords.size()-2).getEndTick() == curChord.getStartTick();
						//assert false:"stopped";
					}
					*/
				}
				
				assert curChord == null || useRestToShortenChords || curChord.isConform():part.getAbcSong().getTitle()+"( "+part.getTitle()+"): not conform 1.";
				assert prevChord == null || useRestToShortenChords || prevChord.isConform():part.getAbcSong().getTitle()+"( "+part.getTitle()+"): not conform 2.";
				
				chords.add(nextChord);
				assert !nextChord.hasRestAndNotes();
				assert curChord == null || !curChord.hasRestAndNotes() || useRestToShortenChords;
				prevChord = curChord;
				curChord = nextChord;
			}
		}

		boolean reprocessLastChord = true;

		while (reprocessLastChord) {
			
			debugOutput(3,"Last chord processing..");
			
			// The last Chord has all the notes it will get. But before continuing,
			// normalize the chord so that all notes end at the same time
			if (curChord.early != null) {
				curChord.setEarlyStartTick(useRestToShortenChords);
				if (prevChord != null) prevChord.recalcEndTick();
				debugOutput(1,"Last chord: early start");
			}
			
			
			// remove zero duration notes if longer notes start at same time
			if (curChord.getLongestEndTick() > curChord.getStartTick()) {
				for (int j = 0; j < curChord.size(); j++) {
					AbcNoteEvent jne = curChord.get(j);
					if (jne.getEndTick() == jne.getStartTick()) {
						// this note is zero duration and others in the chord is not
						curChord.remove(jne);
						debugOutput(2,"Last chord: remove a zero dura note");
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
				debugOutput(2,"Last chord: expand dura");
			}
			
			long targetEndTick = curChord.getEndTick();
			reprocessLastChord = false;
			
			Chord nextChord = null;
			if (!useRestToShortenChords) {
				long curEndMicro = qtm.tickToMicrosABCOrganic(curChord.getEndTick());
				for (int j = 0; j < curChord.size(); j++) {
					AbcNoteEvent jne = curChord.get(j);
					if (jne.getEndTick() > targetEndTick) {
						long noteEndMicro = qtm.tickToMicrosABCOrganic(jne.getEndTick());
						if (curChord.getEndTick() == targetEndTick && noteEndMicro-curEndMicro < minimumMicros/2 && jne.tiesTo == null) {
							// note ends approx same time as end of chord
							// we make it end same time as shortest note in chord,
							// chord might become slightly longer later.
							jne.setEndTick(curChord.getEndTick());
							debugOutput(2,part.getTitle()+ ": Fit note ending to last chord ending");
						} else {
							// This note extends past the end of the chord; break it into two tied notes
							debugOutput(3,"Last chord: cut up chord");
							AbcNoteEvent next = jne.splitWithTieAtTick(targetEndTick);
							if (nextChord == null) {
								nextChord = new Chord(next);
								chords.add(nextChord);
							} else {
								nextChord.add(next);
							}
						}
					}
				}
			}
			curChord.recalcEndTick();
			
			if (nextChord != null) {
				reprocessLastChord = true;
				curChord = nextChord;
				curChord.recalcEndTick();
			}
		}
		assert !curChord.hasRestAndNotes() || useRestToShortenChords;
		
		// delete all chords with zero duration, as there was no room for them
		List<Chord> trash = new ArrayList<>();
		int count = 0;
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
				if (chord.hasRestAndNotes()) count++;
			}
		}
		chords.removeAll(trash);
		/*
		if (count > 0) {
			System.out.println(part.getAbcSong().getSongTitle()+": deleting "+count+ " resting chords due to rest being too short !!!!!!");
		}
		*/
		if (useRestToShortenChords) {
			/*
			 * It can happen that a note that is longer than the chord
			 * is also present in next chord. And if there is a
			 * volume difference between the chord, lotro will
			 * silence entire part. So to prevent that, we shorten
			 * some notes to be same dura as the chord.
			 */
			List<AbcNoteEvent> notesOn = new ArrayList<>();
			Long lastEnd = null;
			for (Chord chord : chords) {
				
				if (lastEnd != null) assert chord.getStartTick() == lastEnd:"Gap between chords1. Start tick (second):"+chord.getStartTick();
				
				for (AbcNoteEvent curr : chord.getNotes()) {
					for (AbcNoteEvent pre : notesOn) {
						if (pre.note == curr.note) {
							assert curr.getEndTick() > pre.getEndTick();
							pre.setEndTick(curr.getStartTick());
							debugOutput(1,part.getTitle()+": normalizing note!1! tied="+(pre.tiesTo != null));
						}
					}
				}
				List longerNotes = new ArrayList<>();				
				for (AbcNoteEvent ne : chord.getNotes()) {
					if (ne.getEndTick() > chord.getEndTick()) {
						longerNotes.add(ne);
					}
				}
				List notesOff = new ArrayList();
				for (AbcNoteEvent ne : notesOn) {
					if (ne.getEndTick() <= chord.getEndTick()) {
						notesOff.add(ne);
					}
				}
				notesOn.removeAll(notesOff);
				notesOn.addAll(longerNotes);
				
				lastEnd = chord.getEndTick();
			}
		} else if (assertionsEnabled) {
			Chord preChord = null;
			for (Chord chord : chords) {
				assert chord.isConform():part.getAbcSong().getTitle()+"( "+part.getTitle()+"): not conform 3.";
				assert preChord==null || preChord.getEndTick() == chord.getStartTick():"Gap between chords2. Start tick (second):"+chord.getStartTick();
				preChord = chord;
			}
		}
		
		return chords;
	}
	
	private int calcValue(Chord c, boolean sustained) {
		// weakness: this favors curChord if starting tick of next
		//           chord is not exact aligned.
		int value = -1;
		value += c.sizeReal();
		if (sustained) {
			long start = qtm.tickToMicrosABCOrganic(c.getStartTick());
			long end = qtm.tickToMicrosABCOrganic(c.getLongestEndTick());
			long dura = end - start;
			value += dura/(AbcConstants.ONE_SECOND_MICROS/4L);
		}
		return value;
	}
	
	final int debug = 0;// 0=no debug 1=minimal debug 2=more debug 3=most debug 4=more than most
	
	private void debugOutput (int lvl, String text) {
		if (debug >= lvl) {
			System.out.println(text);
		}
	}
	
	private boolean isRattle(AbcPart part, AbcNoteEvent ne) {
		if (part.getInstrument() == LotroInstrument.BASIC_DRUM) {
			Note note = ne.note;
			if (note == Note.G3 || note == Note.A3 || note ==  Note.B3 || note ==  Note.C4 || note ==  Note.Fs2 || note ==  Note.Gs2) {
				return true;
			}
		}
		return false;
	}
	
	private boolean isDrone(AbcPart part, AbcNoteEvent ne) {
		if (part.getInstrument() == LotroInstrument.BASIC_BAGPIPE && ne.note != Note.REST) {
			if (ne.note.id <= AbcConstants.BAGPIPE_LAST_DRONE_NOTE_ID) {
				return true;
			}
		}
		return false;
	}	
	
	/**
	 * process the notes using new multi-stage and faster organic principle
	 * 
	 * This is much better and easier to read code. The single-stage is full of nested conditions.
	 * But the older single-stage might sound better, not sure.
	 * 
	 */
	private List<Chord> processOrganic2(AbcPart part, List<AbcNoteEvent> events) {
		// TODO: Last note if non-sustained will make song too long, both notes a reported time. Example: ABBA - Chiquita	
	
		final long minimumMicros = AbcConstants.getShortestNoteMicros(qtm.getPrimaryExportTempoBPM());
		
				
		NavigableSet<Long> grid = createGrid(events, minimumMicros, part);
		
		events = snapNotesToGrid(events, grid, minimumMicros);
		
		List<Chord> chords = chordifyOrganic(events, grid, part);
		
		return chords;
	}
	
	/**
	 * 
	 * Part of multi-stage organic path
	 * 
	 */
	NavigableSet<Long> createGrid(List<AbcNoteEvent> events, long minimumMicros, AbcPart part) {
		// create a non-uniform organic grid
		
		final int startWeightLongNote = 3;
		final int startWeightShortNote = 9;
		final long thresholdShortNoteMicros = minimumMicros*2;
		final int endWeight = 1;
		final boolean endsShouldHaveNoSwayOverStartOfCluster = false;//Fernando sounds better with this false :(
		final boolean endsShouldHaveNoSwayOverStartWeights = true;
		
		// types
		final int INITIAL = 0;
		final int START = 1;
		final int END = 2;
		final int GENERAL = 3;
		
		class GridLine {
		    long micros;
		    int type;
		    int weight;
		    long firstMicros;
		    long lastMicros;
		    
		    public GridLine(long time, int type, int weight) {
		        this.micros = time;
		        this.type = type;
		        this.weight = weight;
		    }
		}
		
		// create a weight map from all start and end micros
		NavigableMap<Long, List<GridLine>> microsWeights = new TreeMap<>();
		for (AbcNoteEvent note : events) {			
			note.origStartABCMicros = qtm.tickToMicrosABCOrganic(note.getStartTick());			
			note.origEndABCMicros = qtm.tickToMicrosABCOrganic(note.getEndTick());
			note.origEndABCMicros = Math.max(note.origEndABCMicros, note.origStartABCMicros + minimumMicros);
			note.origDurationMicros = note.origEndABCMicros - note.origStartABCMicros;
			int startWeight = note.origDurationMicros <= thresholdShortNoteMicros?startWeightShortNote:startWeightLongNote;
			if (!part.getInstrument().sustainable) {// must be after dura calc
				note.origEndABCMicros = note.origStartABCMicros + minimumMicros;
			}
			GridLine start = new GridLine(note.origStartABCMicros, START, startWeight);
			GridLine end = new GridLine(note.origEndABCMicros, END, endWeight);
			microsWeights.computeIfAbsent(note.origStartABCMicros, k -> new ArrayList<>()).add(start);
			microsWeights.computeIfAbsent(note.origEndABCMicros, k -> new ArrayList<>()).add(end);
		}
		
		// Now do some adjustments to note start and ends
		@SuppressWarnings("unchecked")
		List<GridLine>[] typeList = new List[] {};
		Long[] typeLong = {};		
		List<GridLine>[] vals = (List<GridLine>[]) microsWeights.values().toArray(typeList);
		Long[] keys = microsWeights.keySet().toArray(typeLong);
		GridLine prevStart = null;
		GridLine prevEnd = null;
		for (int i = 0; i < vals.length-2; i++) {
			List<GridLine> curr = vals[i];
			List<GridLine> next = vals[i+1];
			List<GridLine> nextnext = vals[i+2];
			GridLine currStart = null;// one (of maybe more) start gridline at curr pos
			GridLine currEnd = null;// one (of maybe more) end gridline at curr pos
			GridLine nextStart = null;// one (of maybe more) start gridline at next or nextnext pos
			GridLine nextEnd = null;// one (of maybe more) end gridline at curr pos
			for (GridLine test : curr) {
				if (test.type==START) {
					currStart = test;
				}
				if (test.type==END) {
					currEnd = test;
				}
			}
			for (GridLine test : next) {
				if (test.type==START) {
					nextStart = test;
				}
				if (test.type==END) {
					nextEnd = test;
				}
			}
			if (nextStart == null) {
				for (GridLine test : nextnext) {
					if (test.type==START) {
						nextStart = test;
					}
				}
			}
			if (nextStart != null && currStart != null// && nextEnd != null
					&& currStart.micros + minimumMicros > nextStart.micros
					&& ((prevStart != null && prevStart.micros + minimumMicros*2 < currStart.micros) || (prevEnd != null && prevEnd.micros + minimumMicros*2 < currStart.micros))
					//&& nextEnd.micros <= nextStart.micros // TODO: wont this always be true?
				) {
				// there is not room for curr to next, it does not overlap with next start, and prev gridlines is not closeby
				// we move curr earlier, so there is room
				List<GridLine> list = microsWeights.get(keys[i]);
				microsWeights.remove(keys[i]);
				long newMicros = nextStart.micros - minimumMicros;
				for (GridLine line : list) {
					line.micros = newMicros;
				}
				microsWeights.put(newMicros, list);
			}
			if (nextStart != null && currEnd != null && currStart == null && prevStart != null
					&& prevStart.micros + minimumMicros > currEnd.micros
					&& nextStart.micros >= currEnd.micros + minimumMicros * 2
					&& (nextEnd == null || nextEnd.micros >= currEnd.micros + minimumMicros * 2)) {
				// The reason we also check for nextStart not null is that nextnextnext might have a start closeby,
				// and since we dont check that we make sure next or nextnext has a start.
				// there is not room for prev to curr. There is no start at curr. Next is not close to curr.
				// we move curr later, so there is room
				List<GridLine> list = microsWeights.get(keys[i]);
				microsWeights.remove(keys[i]);
				long newMicros = prevStart.micros + minimumMicros;
				for (GridLine line : list) {
					line.micros = newMicros;
				}
				microsWeights.put(newMicros, list);
			}
			
			prevStart = currStart;
			prevEnd = currEnd;
		}
		
		List<GridLine> gridLines = new ArrayList<>();
	    for (Map.Entry<Long, List<GridLine>> entry : microsWeights.entrySet()) {
	        gridLines.addAll(entry.getValue());
	    }
		
		// collect the weights into clusters
	    List<List<GridLine>> clusters = new ArrayList<>();
	    List<GridLine> currentCluster = new ArrayList<>();
	    if (!gridLines.isEmpty()) {
	        currentCluster.add(gridLines.get(0));
	    }
	    for (int i = 1; i < gridLines.size(); i++) {
	        GridLine curr = gridLines.get(i);
	        GridLine last = currentCluster.get(0);
	        if (curr.micros - last.micros < minimumMicros) {//curr.type.equals(last.type) && (
	            currentCluster.add(curr);
	        } else {
	            clusters.add(new ArrayList<>(currentCluster));
	            currentCluster = new ArrayList<>();
	            currentCluster.add(curr);
	        }
	    }
	    if (!currentCluster.isEmpty()) {
	        clusters.add(currentCluster);
	    }
	    
	    Comparator<GridLine> gridLineComparator = new Comparator<GridLine>() {
	        @Override
	        public int compare(GridLine a, GridLine b) {
	            int cmp = Long.compare(a.micros, b.micros);
	            if (cmp == 0) {
	                return Integer.compare(a.type, b.type);
	            }
	            return cmp;
	        }
	    };
		
	    NavigableSet<GridLine> grid = new TreeSet<GridLine>(gridLineComparator);
	    
	    GridLine lastAverage = null;
	    // inside each cluster, calc a weighted average
	    boolean firstCluster = true;
	    for (List<GridLine> cluster : clusters) {
	        long weightedSum = 0;
	        int totalWeight = 0;
	        int type = END;
	        Long firstStartMicros = null;  
	        for (GridLine line : cluster) {
	            if (line.type == START) {
	            	type = START;
	            	firstStartMicros = line.micros;
	            	break;
	            }
	        }
	        for (GridLine line : cluster) {
	        	// If there is start present, ignore weight of ends:
	        	if (endsShouldHaveNoSwayOverStartWeights && type == START && line.type == END) continue;
	            weightedSum += line.micros * line.weight;
	            totalWeight += line.weight;
	        }
	        long micros = weightedSum / totalWeight;// average weighted micros
	        if (firstCluster && cluster.get(0).micros == 0L) {
	        	/*
	        	 * When first note start is close to zero so a rest was inserted (which might be too short),
	        	 * or a note starts at zero, we here make sure the weights don't move the zero gridline.
	        	 * This is mostly relevant for not removing initial silence.
	        	 */
	        	micros = 0L;
	        }
	        firstCluster = false;
	        GridLine currAverage = new GridLine(micros, type, totalWeight);
	        currAverage.firstMicros = cluster.get(0).micros;// micros of the first line in the cluster
	        if (endsShouldHaveNoSwayOverStartOfCluster && firstStartMicros != null) currAverage.firstMicros = firstStartMicros; 
	        currAverage.lastMicros =  cluster.get(cluster.size()-1).micros;// micros of the last line in the cluster
	        if (lastAverage != null) {
	        	// TODO: weakness of this is that last might be adjusted to later position when it was curr,
	        	//       and then adjusted earlier afterwards. But due to removing by weight later on,
	        	//       it is sorta okay.
	        	while (currAverage.micros - lastAverage.micros < minimumMicros) {
	        		// the averages of the two clusters are still too close to each other
	        		long needed = minimumMicros - (currAverage.micros - lastAverage.micros);
		        	if (currAverage.weight > lastAverage.weight && lastAverage.firstMicros < lastAverage.micros) {
		        		// curr more heavy than last and last is later than last first line
		        		// we move the last earlier as much we can without moving it earlier than its first line
		        		lastAverage.micros = Math.max(lastAverage.firstMicros, lastAverage.micros - needed);
		        	} else if (currAverage.weight <= lastAverage.weight && currAverage.lastMicros > currAverage.micros) {
		        		// curr is not heavier than last and curr is earlier than its last line
		        		// we move the curr later, but not later than its last line
		        		currAverage.micros = Math.min(currAverage.lastMicros, currAverage.micros + needed);
		        	} else if (lastAverage.firstMicros < lastAverage.micros) {
		        		// we move the last earlier as much we can without moving it earlier than its first line
		        		lastAverage.micros = Math.max(lastAverage.firstMicros, lastAverage.micros - needed);
		        	} else if (currAverage.lastMicros > currAverage.micros) {
		        		// we move the curr later, but not later than its last line
		        		currAverage.micros = Math.min(currAverage.lastMicros, currAverage.micros + needed);
		        	} else {
		        		// we exhausted all 4 options and give up on adjusting, the loop will never be infinite
		        		break;
		        	}
	        	}
	        }
	        grid.add(currAverage);
	        lastAverage = currAverage;
	    }
	    
	    if (grid.isEmpty()) return new TreeSet<Long>();
	    
	    
    
	    
	    Iterator<GridLine> gridIter = grid.iterator();
	    GridLine prev = gridIter.next();
	    
	    NavigableSet<GridLine> refinedGrid = new TreeSet<>(gridLineComparator);
	    refinedGrid.add(prev);
	    while (gridIter.hasNext()) {
	        GridLine curr = gridIter.next();
	        
	        // The grid segments might be larger than 5.0 seconds
		    // Cut it up
	        while (curr.micros - prev.micros > TimingInfo.LONGEST_NOTE_MICROS) {
	            long candidateTime = prev.micros + TimingInfo.LONGEST_NOTE_MICROS;
	            GridLine candidate = new GridLine(candidateTime, GENERAL, 0);
	            if (curr.micros - candidate.micros < minimumMicros) {
	                break;
	            } else {
	                refinedGrid.add(candidate);
	                prev = candidate;
	            }
	        }
	        
	        if (curr.micros - prev.micros < minimumMicros) {
	        	if (curr.weight > prev.weight && prev.micros != 0L) {
	        		refinedGrid.remove(prev);
	        		refinedGrid.add(curr);
	        		prev = curr;
	        	}
	        } else {
	        	refinedGrid.add(curr);
	        	prev = curr;
	        }
	        
	        /*
	        // Merge two gridlines if they too close to each other
	        if (curr.micros - prev.micros < minimumMicros*2/3) {
	            //long mergedMicros = (prev.micros + curr.micros) / 2; // simpler average merging
	            long mergedMicros = (prev.micros * prev.weight + curr.micros * curr.weight) / (prev.weight + curr.weight);
	            refinedGrid.pollLast();// remove last added gridline
	            GridLine merged = new GridLine(mergedMicros, curr.type, prev.weight + curr.weight);
	            refinedGrid.add(merged);
	            prev = merged;
	        } else {
	            refinedGrid.add(curr);
	            prev = curr;
	        }
	        */
	    }
	    
	    NavigableSet<Long> gridTimes = new TreeSet<Long>();
	    //Long lastLine = null;
	    //int lastType = INITIAL;
	    for (GridLine line : refinedGrid) {
	        gridTimes.add(line.micros);
	        /*
	        if (lastLine != null) {
	        	// TODO: comment out when system more solid
	        	assert line.micros >= lastLine+minimumMicros:part.getTitle()+": "+lastType+" "+(line.micros - lastLine)+" micros  "+line.type;
	        	assert line.micros <= lastLine+TimingInfo.LONGEST_NOTE_MICROS+70000:part.getTitle()+": "+lastType+" "+((line.micros - lastLine)/1000)+"ms "+line.type;
	        }
	        lastType = line.type;
	        lastLine = line.micros;
	        */
	    }
	    
	    return gridTimes;
	}
	
	/**
	 * 
	 * Part of multi-stage organic path
	 * 
	 */
	private long getMaxStartShiftMicros(long noteDuration, long minimumMicros) {
		// If start is needed to be moved more than this return value then note will be deleted
		long minimums = noteDuration/minimumMicros;
		if (minimums < 1L) return minimumMicros*3L/5L;// Very short note we wont move the start more than 36 ms
		if (minimums <= 2L) return minimumMicros*3L/4L;// Short note we also wont move the start more than 45 ms
		return minimumMicros;// Longer note we wont move the start more than 60 ms
	}
	
	/**
	 * 
	 * Part of multi-stage organic path
	 * 
	 */
	public List<AbcNoteEvent> snapNotesToGrid(List<AbcNoteEvent> notes, NavigableSet<Long> grid, long minimumMicros) {
				
		List<AbcNoteEvent> snappedNotes = new ArrayList<>(notes.size());
		
	    for (AbcNoteEvent note : notes) {
	    	
	        Long floor = grid.floor(note.origStartABCMicros);
	        Long ceiling = grid.ceiling(note.origStartABCMicros);
	        
	        long candidateStart;
	        if (floor == null && ceiling == null) {
	        	continue; // fallback: no grid available
	        } else if (floor == null) {
	            candidateStart = ceiling;
	        } else if (ceiling == null) {
	            candidateStart = floor;
	        } else {
	            if (Math.abs(note.origStartABCMicros - floor) <= Math.abs(note.origStartABCMicros - ceiling)) {
	                candidateStart = floor;
	            } else {
	                candidateStart = ceiling;
	            }
	        }
	        // Check that the shift does not exceed max relative to the original start.
	        if (Math.abs(candidateStart - note.origStartABCMicros) > getMaxStartShiftMicros(note.origDurationMicros, minimumMicros)) {
	            continue;
	        }
	        note.setStartTick(qtm.microsToTickABCOrganic(candidateStart));
	        note.origStartABCMicros = candidateStart;
	        
	        // Snap note end
	        // We want a grid line that is after start
	        floor = grid.floor(note.origEndABCMicros);
	        ceiling = grid.ceiling(note.origEndABCMicros);
	        Long candidateEnd;
	        if (ceiling != null && candidateStart == ceiling) {
	        	continue;
	        } else if (floor == null || floor == candidateStart) {
	        	candidateEnd = ceiling;
	        } else if (ceiling == null) {
	        	if (candidateStart < floor) {
	        		candidateEnd = floor;
	        	} else {
	        		candidateEnd = null;
	        	}
	        } else {
	        	if (candidateStart < floor && Math.abs(note.origEndABCMicros - floor) <= Math.abs(note.origEndABCMicros - ceiling)) {
	        		candidateEnd = floor;
	            } else {
	            	candidateEnd = ceiling;
	            }
	        }
	        
	        if (candidateEnd == null) {
	        	// ceiling == null and ( floor == null or taken by start )
	        	continue;
	        }
	        
	        //	Check that the shift does not exceed max relative to the original end.
	        if (Math.abs(candidateEnd - note.origEndABCMicros) > minimumMicros * 3L/2L) {//90 ms
	        	//System.out.println(parts.get(0).getAbcSong().getTitle()+": End grid was too far from note end:"+(Math.abs(candidateEnd - note.origEndABCMicros)/(double)minimumMicros));
	            continue;
	        }

	        note.setEndTick(qtm.microsToTickABCOrganic(candidateEnd));
	        note.origEndABCMicros = candidateEnd;
	        
	        if (note.origEndABCMicros - note.origStartABCMicros <= 0L || note.getEndTick() - note.getStartTick() <= 0L) {
	        	continue;
	        }
	        
	        snappedNotes.add(note);
	    }
	    return snappedNotes;
	}
	
	/**
	 * 
	 * Part of multi-stage organic path
	 * 
	 */
	public List<Chord> chordifyOrganic(List<AbcNoteEvent> events, NavigableSet<Long> grid, AbcPart part) {
  
	    List<Chord> chords = new ArrayList<>(events.size() / 2);
	    
	    if (events.size() == 0) return chords;
	    
	    TreeMap<Long,Long> gridTicks = new TreeMap<>();
		for (long micros : grid) {
			gridTicks.put(micros, qtm.microsToTickABCOrganic(micros));
		}
		
		List<AbcNoteEvent> eventSegments = new ArrayList<>(events.size());
		for (AbcNoteEvent ne : events) {
	    	List<AbcNoteEvent> segments = splitToGrid(ne, gridTicks, part);
	    	eventSegments.addAll(segments);
		}
		
		Collections.sort(eventSegments);
		
		boolean assertionsEnabled = false;
		assert assertionsEnabled = true;
		if (assertionsEnabled) {
			AbcNoteEvent last = null;
			for (AbcNoteEvent ne : eventSegments) {
				if (last != null) {
					assert ne.getStartTick() >= last.getStartTick();
					assert ne.getEndTick() >= last.getEndTick() || ne.getStartTick() > last.getStartTick();
				}
				last = ne;
			}
		}
		
		// Add rests between the notes
		List<AbcNoteEvent> rests = new ArrayList<>(events.size());
		List<AbcNoteEvent> restTrash = new ArrayList<>();
		List<AbcNoteEvent> potentialTrash = new ArrayList<>();
		long lastEndMicros = 0L;
		long lastEndTick = 0L;// prevChordsShortest ending
		AbcNoteEvent lastEndNote = null;
		AbcNoteEvent prevChordsShortest = null;
		long lastStartMicros = -1L;
		boolean firstLoop = true;
		for (int i = 0; i < eventSegments.size(); i++) {
			boolean progressing = false;
	    	AbcNoteEvent noteSegment = eventSegments.get(i);	    	
	    	if (!firstLoop) {
	    		if (noteSegment.origStartABCMicros > lastStartMicros) {
	    			// new chord
	    			// here we rely on the sorting to be shortest notes first. (beside sorting by start tick)
	    			boolean F = false;
	    			if (lastEndNote != null && noteSegment.origStartABCMicros > lastEndNote.origEndABCMicros) {
	    				// we dont need the short rest
	    				restTrash.addAll(potentialTrash);
	    				// insert rest from last note/bridge to current segment
		    			AbcNoteEvent rest = new AbcNoteEvent(Note.REST,64,lastEndNote.getEndTick(),noteSegment.getStartTick(),qtm,null);
		    			rest.origStartABCMicros = lastEndNote.origEndABCMicros;
		    			rest.origEndABCMicros = noteSegment.origStartABCMicros;
		    			
		    			List<AbcNoteEvent> segments = splitToGrid(rest, gridTicks, part);
		    	    	rests.addAll(segments);
	    			} else if (prevChordsShortest != lastEndNote && lastEndNote != null && noteSegment.origStartABCMicros == lastEndNote.origEndABCMicros) {
	    				// we don't need the short rest
	    				assert prevChordsShortest.getLengthTicks() <= lastEndNote.getLengthTicks();
	    				assert prevChordsShortest.origStartABCMicros == lastEndNote.origStartABCMicros;
	    				restTrash.addAll(potentialTrash);
	    			} else if (noteSegment.origStartABCMicros > lastEndMicros) {
	    				// insert rest from last rest to current segment
		    			AbcNoteEvent rest = new AbcNoteEvent(Note.REST,64,lastEndTick,noteSegment.getStartTick(),qtm,null);
		    			rest.origStartABCMicros = lastEndMicros;
		    			rest.origEndABCMicros = noteSegment.origStartABCMicros;
		    			
		    			List<AbcNoteEvent> segments = splitToGrid(rest, gridTicks, part);
		    	    	rests.addAll(segments);
		    	    	
		    	    	/*
		    	    	for (AbcNoteEvent evt : eventSegments) {
		    	    		assert rest.origStartABCMicros >= evt.origEndABCMicros || rest.origEndABCMicros <= evt.origStartABCMicros;
		    	    	}
		    	    	*/
		    		} else {
		    			assert noteSegment.origStartABCMicros == lastEndMicros;
		    		}
	    			prevChordsShortest = noteSegment;//The shortest note/rest in the new chord
	    			
	    			if (noteSegment.note != Note.REST) lastEndNote = noteSegment;
	    	    	else lastEndNote = null;
	    			
	    			potentialTrash = new ArrayList<>();
	    			if (prevChordsShortest.note == Note.REST) potentialTrash.add(prevChordsShortest);
	    			
	    	    	progressing = true;
	    		} else if (noteSegment.note != Note.REST && lastEndNote == null) {
	    			// second or later in this chord
	    			assert prevChordsShortest.getLengthTicks() <= noteSegment.getLengthTicks();
	    			lastEndNote = noteSegment;
	    		} else if (noteSegment.note == Note.REST) {
	    			// There might be more than 1 rest that starts at same time,
	    			// we will either remove all of them or let pruning do its work.
	    			potentialTrash.add(noteSegment);
	    		}
	    	}
	    	if (firstLoop || (progressing && noteSegment.origEndABCMicros > lastEndMicros)) {
	    		lastEndMicros = noteSegment.origEndABCMicros;
	    		lastEndTick = noteSegment.getEndTick();
	    		if (firstLoop) {
	    			prevChordsShortest = noteSegment;
	    			if (noteSegment.note != Note.REST) {
	    				lastEndNote = prevChordsShortest;
	    			} else {
	    				lastEndNote = null;
	    				potentialTrash.add(prevChordsShortest);
	    			}
	    		}
	    	}
	    	lastStartMicros = noteSegment.origStartABCMicros;
	    	firstLoop = false;
		}
		eventSegments.addAll(rests);
		eventSegments.removeAll(restTrash);
		//if (restTrash.size() > 0) System.out.println("Trashing rests:"+restTrash.size());
		
		Collections.sort(eventSegments);
		
	    Chord curChord = null;
	    for (int i = 0; i < eventSegments.size(); i++) {
	    	AbcNoteEvent noteSegment = eventSegments.get(i);
	    	if (curChord == null) {
	    		curChord = new Chord(noteSegment);
	    		chords.add(curChord);
	    	} else if (curChord.getStartTick() != noteSegment.getStartTick()) {
	    		assert curChord.getStartTick() < noteSegment.getStartTick();
	    		
	    		unmixRestAndNotes(part, eventSegments, curChord);
	    		
				List<AbcNoteEvent> deadnotes = curChord.prune(part.getInstrument().sustainable,
						part.getInstrument() == LotroInstrument.BASIC_DRUM, part.getInstrument().isPercussion, part);
				removeNotes(eventSegments, deadnotes, part);
				
				if (!deadnotes.isEmpty()) {
					// One of the tiedTo notes that was pruned might be noteSegment note,
					// so we go one step back and re-process
					i--;
					continue;
				}
				
	    		curChord = new Chord(noteSegment);
	    		chords.add(curChord);
	    	} else {
	    		curChord.add(noteSegment);
	    	}
	    }
	    if (curChord != null) {
	    	unmixRestAndNotes(part, eventSegments, curChord);
	    	List<AbcNoteEvent> deadnotes = curChord.prune(part.getInstrument().sustainable,
					part.getInstrument() == LotroInstrument.BASIC_DRUM, part.getInstrument().isPercussion, part);
			removeNotes(eventSegments, deadnotes, part);
		}
	    
	    if (useRestToShortenChords) {
			/*
			 * It can happen that a note that is longer than the chord
			 * is also present in next chord. And if there is a
			 * volume difference between the chord, lotro will
			 * silence entire part. So to prevent that, we shorten
			 * some notes to be same dura as the chord.
			 */
			List<AbcNoteEvent> prevNotes = new ArrayList<>();
			long prevShortest = -1L;
			Chord preChord = null;
			for (Chord chord : chords) {
				if (preChord != null) {
					for (AbcNoteEvent curr : chord.getNotes()) {
						for (AbcNoteEvent pre : prevNotes) {
							if (pre.note == curr.note) {
								pre.origEndABCMicros = prevShortest;
								assert curr.origEndABCMicros > pre.origEndABCMicros;
								//System.out.println(part.getAbcSong().getTitle()+ ": normalizing note!!!");
							}
						}
					}
				}
				prevNotes = new ArrayList<>();
				prevShortest = chord.getShortest().origEndABCMicros;
				for (AbcNoteEvent ne : chord.getNotes()) {
					if (ne.origEndABCMicros > prevShortest) {
						prevNotes.add(ne);
					}
				}
				preChord = chord;
			}
		}
	    
	    return chords;
	}

	/**
	 * 
	 * Part of multi-stage organic path
	 * 
	 */
	private void unmixRestAndNotes(AbcPart part, List<AbcNoteEvent> eventSegments, Chord curChord) {
		// make sure chord does not contain both rest and notes
		// Also make sure there is not duplicates in it
		List<AbcNoteEvent> tmp = new ArrayList<>(curChord.getNotes());
		boolean both = curChord.hasRestAndNotes();
		for (AbcNoteEvent note : tmp) {
			if (!useRestToShortenChords && both && note.note == Note.REST) {
				curChord.remove(note);
			}
			for (AbcNoteEvent note2 : tmp) {
				if (note != note2 && note.note == note2.note) {
					if (!curChord.getNotes().contains(note) || !curChord.getNotes().contains(note2)) {
						// one of them has already been removed
						continue;
					}
					List<AbcNoteEvent> firstList = new ArrayList<>();
					List<AbcNoteEvent> secondList = new ArrayList<>();
					firstList.add(note);
					secondList.add(note2);
					if (note.tiesTo != null && note2.tiesTo != null) {
						long first = note.getFullLengthTicks();
						long second = note2.getFullLengthTicks();
						if (first >= second) {
							curChord.remove(note2);
							removeNotes(eventSegments, secondList, part);
						} else {
							curChord.remove(note);
							removeNotes(eventSegments, firstList, part);
						}
					} else if (note.tiesTo != null) {
						curChord.remove(note2);
						removeNotes(eventSegments, secondList, part);
					} else if (note2.tiesTo != null) {
						curChord.remove(note);
						removeNotes(eventSegments, firstList, part);
					} else {
						curChord.remove(note2);
						removeNotes(eventSegments, secondList, part);
					}
				}
			}
		}
	}

	/**
	 * 
	 * Part of multi-stage organic path
	 * 
	 */
	private List<AbcNoteEvent> splitToGrid(AbcNoteEvent ne, TreeMap<Long,Long> gridTicks, AbcPart part) {
				
		List<AbcNoteEvent> segments = new ArrayList<>();
		segments.add(ne);
		
		Entry<Long, Long> ceil = gridTicks.ceilingEntry(ne.origStartABCMicros+1L);
		Long restartMicros = ne.origStartABCMicros;
		Long restartTick = ne.getStartTick();
		Long ceilMicros = ceil == null?null:ceil.getKey();
		Long ceilTick   = ceil == null?null:ceil.getValue();
		
		long endMicros = ne.origEndABCMicros;
		boolean drone = isDrone(part,ne);
		boolean rest = ne.note == Note.REST;
		long lastSplitTick = ne.getStartTick();
		long lastSplitMicros = ne.origStartABCMicros;
		while (ceil != null && ceilMicros < endMicros && ceilTick < ne.getEndTick()) {
			// As long as there is another ceiling within the note duration
			if (ne.getStartTick() < ceilTick) {//rounding error guard
				AbcNoteEvent ne2;
				long microsFullDura = ne.origEndABCMicros-ne.origStartABCMicros;
				boolean sustained = part.getInstrument().sustainable;
				//Entry<Long, Long> ceilNext = gridTicks.ceilingEntry(ceilMicros+1L);
				//Long ceilNextMicros = ceilNext == null?null:ceilNext.getKey();
				if (useRestToShortenChords && sustained
						&& !rest && microsFullDura < TimingInfo.LONGEST_NOTE_MICROS
						) {//&& ceilNextMicros != null && ceilNextMicros+60000 < ne.origEndABCMicros
					
					// insert rest to shorten chord and keep long note
					//
					// Note that this will potentially insert many rests into chords.
					// But prune will get rid of all but the shortest.
					
					AbcNoteEvent restShorter = new AbcNoteEvent(Note.REST, 4, ne.getStartTick(), ceilTick, qtm, null);
					restShorter.origStartABCMicros = ne.origStartABCMicros;
					restShorter.origEndABCMicros = ceilMicros;
					assert restShorter.origEndABCMicros - restShorter.origStartABCMicros < TimingInfo.LONGEST_NOTE_MICROS + 70000 : ((ne.origEndABCMicros - ne.origStartABCMicros)/1000) +" ms";
					segments.add(restShorter);
					break;
				} else if (!rest && (drone || (restartMicros + TimingInfo.LONGEST_NOTE_MICROS -1 > ceilMicros))) {
					
					// split and tie
					//
					// rests dont come in here, they need restart.
					// all drones go here.
					
					
					// TODO: comment out when system more solid
					//assert ne.origStartABCMicros < ceilMicros;
					//assert ne.getStartTick() < ceilTick:ne.getStartTick()+" < "+ceilTick;
					// assert ne.origEndABCMicros > ceilMicros:ne.origEndABCMicros+" > "+ceilMicros;
					// assert ne.getEndTick() > ceilTick:ne.getEndTick()+" > "+ceilTick;
					
					ne2 = ne.splitWithTieAtTick(ceilTick);
					ne2.origStartABCMicros = ceilMicros;
					ne2.origEndABCMicros = ne.origEndABCMicros;
					ne.origEndABCMicros = ceilMicros;
					segments.add(ne2);
				} else {
					
					// restart
					// 
					// all rests come in here, drones do not
					// 
					
					ne2 = new AbcNoteEvent(ne.note, ne.velocity, ceilTick, ne.getEndTick(), qtm, ne.origNote);
					ne2.origStartABCMicros = ceilMicros;
					ne2.origEndABCMicros = ne.origEndABCMicros;
					ne.origEndABCMicros = ceilMicros;
					ne.setEndTick(ceilTick);
					assert ne.origEndABCMicros - ne.origStartABCMicros < TimingInfo.LONGEST_NOTE_MICROS + 70000 : ((ne.origEndABCMicros - ne.origStartABCMicros)) +" us";
					segments.add(ne2);
					restartMicros = ceilMicros;
					restartTick = ceilTick;
				}
				// TODO: comment out when system more solid
				/*
				assert ne.origStartABCMicros < ne.origEndABCMicros;
				assert ne.origStartABCMicros + TimingInfo.LONGEST_NOTE_MICROS + 70000>= ne.origEndABCMicros;
				assert ne2.origStartABCMicros < ne2.origEndABCMicros;
				assert ne.getStartTick() < ne.getEndTick();
				assert ne2.getStartTick() < ne2.getEndTick();
				*/
				
				ne = ne2;
				lastSplitTick = ceilTick;
				lastSplitMicros = ceilMicros;
			} else {
				//System.out.println((gridTicks.floorKey(ceilMicros-1)/1000)+" < "+(ceilMicros/1000));
			}
			ceil = gridTicks.ceilingEntry(ceilMicros+1L);
			ceilMicros = ceil == null?null:ceil.getKey();
			ceilTick   = ceil == null?null:ceil.getValue();
		}
		boolean assertionsEnabled = false;
		assert assertionsEnabled = true;
		if (assertionsEnabled) {
			for (AbcNoteEvent segment : segments) {
				long microsDura = segment.origEndABCMicros-segment.origStartABCMicros;
				assert microsDura <= TimingInfo.LONGEST_NOTE_MICROS + 70000: segment.note+" vel="+segment.velocity+"  "+((ne.origEndABCMicros - ne.origStartABCMicros)/1000) +" ms";
			}
		}
		return segments;
	}
	
	private boolean deprecated1(AbcPart part, List<AbcNoteEvent> events, long minimumMicros, int debug,
			boolean removeGliss, Chord curChord, AbcNoteEvent ne, long microsTillNext, long microsTillNext2,
			long neMicros, long ne2Micros) {
		if (removeGliss) {
			if ((curChord.getEndTick() > ne.getStartTick() || (neMicros < minimumMicros && ne2Micros < minimumMicros))
					&& curChord.getEndTick() < ne.getEndTick()
					&& microsTillNext < minimumMicros
					&& neMicros < minimumMicros * 4L
					&& microsTillNext2 < minimumMicros
					&& curChord.getLongestEndTick() < qtm.microsToTickABCOrganic(qtm.tickToMicrosABCOrganic(curChord.getStartTick()) + minimumMicros * 4L)
					&& !curChord.glissando) {
			
				
				long curMinEnd = qtm.microsToTickABCOrganic(qtm.tickToMicrosABCOrganic(curChord.getStartTick()) + minimumMicros);
				curChord.setEndTickRetract(curMinEnd);
				curChord.setEndTickExpand(curMinEnd);
				
				debugOutput(1,part.getTitle()+" Removed glissando note 1");
				events.remove(ne);
				curChord.glissando = true;

				// TODO: these ties should perhaps prevent it from being removed, TBD
				if (ne.tiesFrom != null) {
					ne.tiesFrom.tiesTo = null;
				}
				if (ne.tiesTo != null) {
					if (!part.getInstrument().sustainable) {
						// If non-sustained then should remove ne.tiesTo
						// we do this by a hack when setting from to itself
						// then we just skip the notes from being added.
						AbcNoteEvent tie = ne.tiesTo;
						while (tie != null) {
							tie.tiesFrom = tie;
							tie = tie.tiesTo;
						}
					}
					ne.tiesTo.tiesFrom = null;
				}

				return true;
				
			} else {
				debugOutput(3,"Not gli: overlap="+(curChord.getEndTick() > ne.getStartTick())+" microsTillNext="+microsTillNext+" microsTillNext2="+microsTillNext2+" neMicros="+neMicros+" ne2Micros="+ne2Micros);
			}
		}
		return false;
	}

	private boolean deprecated2(AbcPart part, List<AbcNoteEvent> events, long minimumMicros, int debug, Chord curChord,
			int i, AbcNoteEvent ne, AbcNoteEvent ne1, AbcNoteEvent ne2, long microsTillNext2, long neMicros,
			long minEndMicro, long curMinEndTick, long neMicroStart) {

		// curr chord was earlier detected as part of glissando
		// force room for curr chord
		
		long oldNeStartTick = ne.getStartTick();
		// iterate to find if any next notes has tiesFrom and if ends after next after next starts
		boolean neTiesFrom = false;
		boolean neEndsAfterNe2 = true;// with minimum margin
		List<AbcNoteEvent> neChord = new ArrayList<>();
		for (int ii = i; ii < events.size(); ii++) {
			AbcNoteEvent over = events.get(ii);
			if (over.getStartTick() > oldNeStartTick) {
				break;
			}
			if (over.getStartTick() == oldNeStartTick) {
				// should be ok to do this even if tiesFrom is non-null
				// since the tiesFrom has been expanded to end here
				if (over.tiesFrom != null) {
					neTiesFrom = true;
				}
				if (ne2 != null && (over.getEndTick() <= ne2.getStartTick()
						|| qtm.tickToMicrosABCOrganic(over.getEndTick()) - qtm.tickToMicrosABCOrganic(ne2.getStartTick()) < minimumMicros)) {
					neEndsAfterNe2 = false;
				}
				neChord.add(over);
			}
		}
		if (ne2 != null && ne2.getStartTick() >= curMinEndTick
				&& microsTillNext2 > minimumMicros*2) {
			// delay start of next note, it has room to expand on its own later if needed

			// delay start of next chord minimum possible	
			for (AbcNoteEvent over : neChord) {
				// should be ok to do this even if tiesFrom is non-null
				// since the tiesFrom has been expanded to end here
				if (over.getLengthTicks() == 0L) {
					over.setEndTick(curMinEndTick);
				}
				over.setStartTick(curMinEndTick);
				
				// TODO: Delaying start
			}
			curChord.dontMove2 = true;
			i--;
			debugOutput(1,part.getTitle()+" Delayed short chord");
			return true;
		} else if (ne2 != null && ne2.getStartTick() >= curMinEndTick && neEndsAfterNe2
				&& !neTiesFrom && part.getInstrument().sustainable) {
			// Delay start of next note, parts of it are playing same time as the next after next,
			// so its okay to set its start time same as next after next.
			// Note if this happens it means ne2 start is not far into future else prev. condition would have triggered.
			
			// delay start of next chord till next after next
			for (AbcNoteEvent over : neChord) {
				if (over.getLengthTicks() == 0L) {
					over.setEndTick(ne2.getStartTick());
				}
				over.setStartTick(ne2.getStartTick());
				
				// TODO: Delaying start
			}								
			curChord.dontMove2 = true;
			//events.remove(ne);
			//events.add(events.indexOf(ne2), ne);
			i--;
			debugOutput(1,part.getTitle()+" Delayed staggered notes");
			return true;
		} else if ((ne2 == null || ne1.getEndTick() <= ne2.getStartTick()) && ne1.getEndTick() > curMinEndTick
				&& (minEndMicro-neMicroStart < minimumMicros/3 || neMicros > minimumMicros*2)) {
			// delay start of next chord, its likely not part of glissando after all (or anymore)
			// there is plenty of room till next after next starts
			for (AbcNoteEvent over : neChord) {
				// should be ok to do this even if tiesFrom is non-null
				// since the tiesFrom has been expanded to end here
				if (over.getLengthTicks() == 0L) {
					over.setEndTick(curMinEndTick);
				}
				over.setStartTick(curMinEndTick);
				
				// TODO: Delaying start
			}
			curChord.dontMove2 = true;
			i--;
			debugOutput(2,part.getTitle()+" Delayed sequential chord by "+ ((minEndMicro-neMicroStart)/1000)+" ms 2");
			return true;
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
			debugOutput(1,part.getTitle()+" Removed glissando note 2 ");
			return true;
		}
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
		// If false, then the 2 notes will become 2 or 3 notes,
		// where the middle (subset) will have the volume of the loudest.
		// Some listening tests convinced me that false is the way to go.
		final boolean prioritizeUninteruptedLongNotes = false;
		
		List<AbcNoteEvent> notesOn = new ArrayList<>();
		List<AbcNoteEvent> thirds = new ArrayList<>();
		List<AbcNoteEvent> trash = new ArrayList<>();
		Iterator<AbcNoteEvent> neIter = events.iterator();
		dupLoop: while (neIter.hasNext()) {
			AbcNoteEvent second = neIter.next();//second
			List<AbcNoteEvent> thirdsOn = new ArrayList<>();
			Iterator<AbcNoteEvent> onIter = notesOn.iterator();
			while (onIter.hasNext()) {
				AbcNoteEvent first = onIter.next();//first
				if (first.getEndTick() <= second.getStartTick() && (first.getLengthTicks() > 0 || first.getStartTick() < second.getStartTick())) {
					// First note has already been turned off
					onIter.remove();
				} else if (first.note.id == second.note.id) {
					if (first.getStartTick() == second.getStartTick()) {
						// If they start at the same time, remove the second event.
						
						if (second.getLengthTicks() == 0) {
							neIter.remove();
							continue dupLoop;
						} else if (first.getLengthTicks() == 0) {
							onIter.remove();
							trash.add(first);
						} else {
							
							// Lengthen the first one if it's shorter than the second one.
							if (first.getEndTick() <= second.getEndTick()) {
								first.setEndTick(second.getEndTick());
								if (second.velocity > first.velocity) {
									first.velocity = second.velocity;// due to this, NoteEvent.velocity is not final
								}
							}
							
							if (!instrument.isSustainable(first.note.id) && second.velocity > first.velocity) {
								first.velocity = second.velocity;// due to this, NoteEvent.velocity is not final
							}
							
							// Remove the duplicate second note
							neIter.remove();
							continue dupLoop;
						}
					} else if (first.getStartTick() < second.getStartTick()) {
						// Otherwise, if they don't start at the same time, but first started first:

						if (second.getEndTick() <= first.getEndTick()) {
							// second is subset of first
							if (second.getLengthTicks() == 0) {
								neIter.remove();
								continue dupLoop;
							} else if (instrument.isSustainable(first.note.id)) {
															
								if (prioritizeUninteruptedLongNotes && Dynamics.fromMidiVelocity(second.velocity).abcVol <= Dynamics.fromMidiVelocity(first.velocity).abcVol) {
									// remove second
									// we only do this if second has lower or equal volume
									neIter.remove();
									continue dupLoop;
								}
								// else we stop first, insert second, and add new third if needed (with firsts volume) after second to finish first.
								long thirdEnd = first.getEndTick(); 
								first.setEndTick(second.getStartTick());
								onIter.remove();
								if (first.velocity > second.velocity) {
									second.velocity = first.velocity;
								}
								if (thirdEnd > second.getEndTick()) {
									AbcNoteEvent third = new AbcNoteEvent(first.note, first.velocity, second.getEndTick(), thirdEnd, qtm, first.origNote);
									thirds.add(third);
									thirdsOn.add(third);
								}
							} else {
								// keep both, so end first where second start	
								first.setEndTick(second.getStartTick());
								onIter.remove();
							}
						} else if (second.getEndTick() > first.getEndTick()) {
							// second extend beyond first
							
							if (!instrument.isSustainable(first.note.id) || Dynamics.fromMidiVelocity(second.velocity) != Dynamics.fromMidiVelocity(first.velocity)) {
								// we break first, and start second
								first.setEndTick(second.getStartTick());
								onIter.remove();
							} else {
								// sustained and same abc volume
								// we extend first to cover both, and discard second
								first.setEndTick(second.getEndTick());
								neIter.remove();
								continue dupLoop;
							}
						}
					} else {
						if (first.getStartTick() < second.getEndTick()) {
							// Otherwise, if they don't start at the same time, but second started first, which means first was a third
							
							if (second.getLengthTicks() == 0) {
								neIter.remove();
								continue dupLoop;
							}
							
							if (second.getEndTick() > first.getEndTick()) {
								// extend first to match seconds end
								first.setEndTick(second.getEndTick());
							}
							
							// since we know that there has been inserted a subset note where
							// second starts, we dont need to care about the start. Also we know that it
							// will process third before the subset, so we don't have to worry about subset being extended
							// as long as second is removed here. And its safe
							// to remove second as long as its sustained. If its not sustained we shorten it so it dont extend into the third.
							if (instrument.isSustainable(first.note.id)) {
								neIter.remove();
								continue dupLoop;
							} else if (second.getEndTick() > first.getStartTick()) {
								// shorten second to end where first begin
								// second will then be processed against the subset later in the loop
								second.setEndTick(first.getStartTick());
							}
						}
					}
				}
			}
			notesOn.addAll(thirdsOn);//must be before adding ne
			notesOn.add(second);
		}
		events.addAll(thirds);
		events.removeAll(trash);
	}
	
	private void removeDuplicateNotesVerify(List<AbcNoteEvent> events, LotroInstrument instrument) {
		List<AbcNoteEvent> notesOn = new ArrayList<>();
		Iterator<AbcNoteEvent> neIter = events.iterator();
		while (neIter.hasNext()) {
			AbcNoteEvent ne = neIter.next();//second
			Iterator<AbcNoteEvent> onIter = notesOn.iterator();
			while (onIter.hasNext()) {
				AbcNoteEvent on = onIter.next();//first
				if (on.getEndTick() <= ne.getStartTick() && (on.getLengthTicks() > 0 || on.getStartTick() < ne.getStartTick())) {
					// First note has already been turned off
					onIter.remove();
				} else if (on.note.id == ne.note.id) {
					System.out.println("OOPSIE ");
					System.exit(0);
				}
			}
			notesOn.add(ne);
		}
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
							ne.getStartMicros() + qtm.multiplyByExportTempoFactor(TimingInfo.LONGEST_NOTE_MICROS)),
					part);
			
			// quantize:            tunedit + mixtimings 
			// microsToTick:        tunedit + mixtimings
			// getStartMicros:      tunedit + mixtimings
			// LONGEST_NOTE_MICROS: tunedit + mixtimings + tempoedit (hence why export tempo factor is applied onto it

			// Make a hard break for notes that are longer than LotRO can play
			// Bagpipe notes up to B2 can sustain indefinitely; don't break them
			if (ne.getEndTick() > maxNoteEndTick && ne.note != Note.REST
					&& !isDrone(part,ne)) {

				// Align with a bar boundary if it extends across 1 or more full bars.
				long endBarTick = qtm.tickToBarStartTick(maxNoteEndTick);

				long slipMicros = qtm.tickToMicrosABC(maxNoteEndTick) - qtm.tickToMicrosABC(endBarTick);

				if (qtm.tickToBarEndTick(ne.getStartTick()) < endBarTick
						&& slipMicros < AbcConstants.ONE_SECOND_MICROS) {
					// endBarTick is at least 2 barlines away from note start 
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
					+ qtm.multiplyByExportTempoFactor(TimingInfo.LONGEST_NOTE_MICROS))) {
				// Rest longer than 5s, split it at 4s:
				targetEndTick = qtm.quantize(
						qtm.microsToTick(qtm.tickToMicros(ne.getStartTick())
								+ qtm.multiplyByExportTempoFactor (AbcConstants.LONGEST_NOTE_MICROS/2)),
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
							qtm.tickToMicrosOrganic(ne.getStartTick()) + qtm.multiplyByExportTempoFactor(TimingInfo.LONGEST_NOTE_MICROS));
			
			boolean drone = isDrone(part,ne);
			
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
								// minimum 2nd bar from start and maximum 1 sec from 5 secs
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
					qtm.tickToMicrosOrganic(ne.getStartTick()) + qtm.multiplyByExportTempoFactor((TimingInfo.LONGEST_NOTE_MICROS-AbcConstants.ONE_SECOND_MICROS/4))
					);
			long minForDrones = qtm.microsToTickOrganic(
					qtm.tickToMicrosOrganic(ne.getStartTick()) + qtm.multiplyByExportTempoFactor((TimingInfo.LONGEST_NOTE_MICROS-AbcConstants.ONE_SECOND_MICROS))
					);
			Long bestForDrones = points.floor(maxForDrones);
			if (bestForDrones != null && bestForDrones > minForDrones) maxForDrones = bestForDrones; 
				
			long targetEndTick = Math.min(ne.getEndTick(), maxForDrones);


			// If remaining bar is larger than 5s, then split rests earlier (and yes, have
			// seen this happen for 8s+ -aifel)
			if (ne.note == Note.REST && targetEndTick > qtm.microsToTickOrganic(qtm.tickToMicrosOrganic(ne.getStartTick())
					+ qtm.multiplyByExportTempoFactor(TimingInfo.LONGEST_NOTE_MICROS))) {
				// Rest longer than 5s, split it at 4s:
				targetEndTick = 
						qtm.microsToTickOrganic(qtm.tickToMicrosOrganic(ne.getStartTick())
								+ qtm.multiplyByExportTempoFactor(AbcConstants.LONGEST_NOTE_MICROS/2));
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
			ITempoCache tempos, boolean ignoreBentNotes) {
		if (oldNe instanceof BentMidiNoteEvent && !ignoreBentNotes) {
			BentAbcNoteEvent newNe = new BentAbcNoteEvent(mappednote, velocity, startTick, endTick, tempos, (BentMidiNoteEvent) oldNe);
			return newNe;
		} else {
			return new AbcNoteEvent(mappednote, velocity, startTick, endTick, tempos, oldNe);
		}
	}

	private void addMidiTempoEvents(Track track0, long end) {
		NavigableMap<Long, TimingInfoEvent> timings = qtm.getTimingInfoByTick();
		if (organic) {
			timings = qtm.getTimingInfoByTickOrganic();
		}
		QuantizedTimingInfo.TimingInfoEvent event1L = timings.get(1L);
		for (QuantizedTimingInfo.TimingInfoEvent event : timings.values()) {
			if (event.tick > end)
				continue;

			track0.add(MidiFactory.createTempoEvent(event.info.getExportTempoMPQ(), event.tick));

			if (event.tick == 0L && event1L == null) {
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

			Integer entry = null;
			for (long t = be.getStartTick(); t < be.getEndTick();
					t = be.getNextBend(qtm.microsToTickABCOrganic(
							qtm.tickToMicrosABCOrganic(t) + AbcConstants.getShortestNoteMicros(qtm.getPrimaryExportTempoBPM())
							), entry)) {
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
			if (isDrone(part,ne)) {
				if (ne.tiesTo != null) {
					ne.tiesTo.tiesFrom = null;
				}
			} else {
				for (AbcNoteEvent neTie = ne.tiesTo; neTie != null; neTie = neTie.tiesTo) {
					events.remove(neTie);
				}
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

			long lastNoteEnd = part.lastNoteEndTick(accountForSustain, qtm, organic);
			if (lastNoteEnd > endTick) {
				endTick = lastNoteEnd;
			}
		}

		if (startTick == Long.MAX_VALUE)
			startTick = 0L;
		if (endTick == Long.MIN_VALUE)
			endTick = 0L;
		
		if (organic) {
			// TODO: Why do we start 100 ms before first note? ..I forgot why I made this
			//       Its not related to the 100 ms used in delay parts.
			startTick = Math.max(0L, qtm.microsToTickABCOrganic(qtm.tickToMicrosABCOrganic(startTick)-80000L));
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
	
	/**
	 * Returns the final song duration.
	 * Used to export duration in part names, file name and metadata.
	 * 
	 * @return
	 */
	private long getSongLengthMicros() {
		return qtm.divideByExportTempoFactor(getExportEndMicros() - getExportStartMicros());
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
		
		//not sure what this used to be used for
		//public final List<AbcNoteEvent> noteEvents;
		
		public final Integer channel;
		public final Integer patch;
		public final long endOfTrack;

		public ExportTrackInfo(int trackNumber, AbcPart part, List<AbcNoteEvent> noteEvents, Integer channel, int patch, long endOfTrack) {
			this.trackNumber = trackNumber;
			this.part = part;
			//this.noteEvents = noteEvents;
			this.channel = channel;
			this.patch = patch;
			this.endOfTrack = endOfTrack;
		}
	}

	public void setOrganic(boolean org) {
		organic = org;
	}

	public boolean isOrganic() {		
		return organic;
	}
	
	public void setOrganic2(boolean org) {
		organic2 = org;
	}

	public boolean isOrganic2() {		
		return organic2;
	}

	public boolean isUseRestsInChords() {
		return useRestsInChords;
	}

	public void setUseRestsInChords(boolean useRestsInChords) {
		this.useRestsInChords = useRestsInChords;
	}
}
