package com.digero.maestro.abc;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;

import com.digero.common.abc.*;
import com.digero.common.midi.*;
import com.digero.common.util.Pair;
import com.digero.common.util.Quad;
import com.digero.common.util.Triple;
import com.digero.common.util.Util;
import com.digero.common.view.UIText;
import com.digero.maestro.MaestroMain;
import com.digero.maestro.abc.QuantizedTimingInfo.TimingInfoEvent;
import com.digero.maestro.midi.*;
import com.digero.maestro.view.CountIn;
import com.digero.maestro.view.GenericTrackInfo;
import com.digero.maestro.view.MiscSettings;
import com.digero.maestro.view.ProjectFrame;

@SuppressWarnings({"AssertWithSideEffects"})
public class AbcExporter {
	private static final Logger logNotes = Logger.getLogger("export.notes");//processing and fitting of notes to lotros abc format
	private static final Logger logAbc = Logger.getLogger("export.abc");//creation of abc
	private static final Logger logPreview = Logger.getLogger("export.preview");//creation of preview midi

    private boolean organic = false;
	private boolean organic2 = false;
    private boolean upgraded = false;
	private static final int MAX_RAID = 24; // Max number of parts that in any case can be played in lotro

    /*
        bouncingEnabled is only for multi-stage 2
        If two note starts are 45 to 60 ms apart (arpeggio), keep the arpeggio instead of forcing them into
        block chord as createGrid() would do. The new arpegio will be 60 ms instead, but thats barely noticable.
        However only do it if there is not another note start within first note + 120 ms.
     */
    private boolean bouncingEnabled = true;

	private final List<AbcPart> parts;
	private final AbcMetadataSource metadata;
	private QuantizedTimingInfo qtm;
	private KeySignature keySignature;

	private boolean skipSilenceAtStart;
	private boolean deleteMinimalNotes;
	private boolean useRestsInChords;
    public boolean reducedFilesize = true;
	// private boolean showPruned;
	private long exportStartTick;
	private long exportEndTick;
	
	// the tempo changes might not be shared evenly among the parts, so this is really only for making abc more readable
	private final boolean exportTempos = false;
	
	// Some midis have zero duration notes that should played (this is for organic only)
	private final boolean deleteEmptyNotes = false;
	
	public int stereoPan = 100;// zero is mono, 100 is very wide.
	private int firstBarNumber;

	private int lastChannelUsedInPreview = -1;
    private long startTickForCountIn = 0L;
    public MiscSettings dissonancePrefs = null;

    // reduced precision factor
    private final int milli2micro = 100;

    public AbcExporter(List<AbcPart> parts, QuantizedTimingInfo timingInfo, KeySignature keySignature,
			AbcMetadataSource metadata, boolean skipSilenceAtStart, boolean organic) throws AbcConversionException {
		this.parts = parts;
		this.qtm = timingInfo;
		this.metadata = metadata;
		setKeySignature(keySignature);
		this.organic = organic;// getSongStartEndTick needs this so we needed to pass it
		this.skipSilenceAtStart = skipSilenceAtStart;// getSongStartEndTick needs this so we needed to pass it
		// We use this from AbcSong when getting micros
		Pair<Long, Long> startEndTick = getSongStartEndTick(false, true);
		exportStartTick = startEndTick.first;
		exportEndTick = startEndTick.second;
	}

	public Quad<List<ExportTrackInfo>, Sequence, PolyphonyHistogram, DissonanceDetector> exportToPreview(boolean useLotroInstruments)
			throws AbcConversionException, InvalidMidiDataException {
		try {
            PolyphonyHistogram histogram = new PolyphonyHistogram();
            DissonanceDetector dissonanceDetector = new DissonanceDetector(dissonancePrefs);
			Pair<Long, Long> startEndTick = getSongStartEndTick(false, true);
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
						histogram.count(part, new ArrayList<>(), organic, qtm);
					} catch (IOException e) {
						throw new AbcConversionException("Failed to read instrument sample durations.", e);
					}
				}
				return new Quad<>(infoList, new Sequence(Sequence.PPQ, 96), histogram, dissonanceDetector);
			}
			if (parts.size() > MAX_RAID) {
				throw new AbcConversionException("Songs with more than " + MAX_RAID + " parts can never be previewed.\n"
						+ "This song currently has " + parts.size() + " parts and failed to preview.");
			}
			exportForPreviewChords(chordsMade, histogram, dissonanceDetector);// export the chords here early, as we possibly
																		// need to process them for sharing.
			
			

			Sequence sequence = new Sequence(Sequence.PPQ, qtm.getMidiResolution());

			// Track 0: Title and meta info
			Track track0 = sequence.createTrack();
			track0.add(MidiFactory.createTrackNameEvent(metadata.getSongTitle()));

            AbcPart one = parts.isEmpty() ? null : parts.getFirst();
            CountIn countIn = null;
            if (one != null) {
                countIn = one.getAbcSong().getCountIn();
                if (countIn != null) {
                    long countInMicros;
                    if (countIn.pattern == CountIn.CountInPattern.OFF) {
                        countInMicros = 0L;
                    } else {
                        countInMicros = calculateCountInTotalMicrosABC(countIn, qtm);
                        long hitMicros = countInMicros / countIn.pattern.dynamics.length;
                        if (countInMicros > AbcConstants.LONGEST_COUNT_IN_MICROS) {
                            countInMicros = 0L;
                        } else if (hitMicros < AbcConstants.getShortestNoteMicros(qtm.getPrimaryExportTempoBPM())) {
                            countInMicros = 0L;
                        }
                    }
                    countIn.micros = countInMicros;
                }
            }

			PanGenerator panner = new PanGenerator();

            List<AbcPart> panSortedParts = new ArrayList<>(parts);
            panner.sortParts(panSortedParts, null);
            //System.out.println("\nDoing stereo spread");
			lastChannelUsedInPreview = -1;			
			long lastEnd = 0L;

            int minDelay = 0;
            for (AbcPart part : parts) {
                if (part.getDelay() != 0) {
                    if (part.getDelay() < minDelay) {
                        minDelay = part.getDelay();
                    }
                }
            }
			for (AbcPart part : panSortedParts) {
				
				if (part.getEnabledTrackCount() > 0 || (countIn != null && countIn.micros > 0L && countIn.part == part)) {
					int pan = panner.get(part.getInstrument(), stereoPan, part.getUserPan(), -1);
                    //System.out.println(part.getInstrument()+" -> "+(pan-64));
					ExportTrackInfo inf = exportPartToPreview(part, sequence, pan,
							useLotroInstruments, chordsMade, countIn, minDelay);
					infoList.add(inf);
					lastEnd = Math.max(lastEnd, inf.endOfTrack);
					logPreview.fine(part.getTitle()+" assigned to channel "+inf.channel+" on track "+inf.trackNumber);
				}
			}
			addMidiTempoEvents(track0, lastEnd);
			track0.add(MidiFactory.createEndOfTrackEvent(lastEnd));
			
			logPreview.fine("Preview done");
			/*
			 * if (exportStartTick > 0) { track0.add(MidiFactory.createNoteOnEventEx(40,9,100,0L));
			 * track0.add(MidiFactory.createNoteOffEventEx(40,9,0,100L)); }
			 */

            /*
            // debug output the preview sequence as midi
            try {
                java.io.File file = new java.io.File("debug_output.mid");
                int counter = 1;
                while (file.exists()) file = new java.io.File("debug_output-" + (counter++) + ".mid");
                javax.sound.midi.MidiSystem.write(sequence, 1, file);
                System.out.println("Dumped MIDI to: " + file.getAbsolutePath());
            } catch (IOException ignored) {}
            */
            /*
            if (organic) {
                // first notes/rest have been relying on getExportStartMicrosABC() to get its starttick.
                // so when doing preview we have to convert that back to tick to make sure get the
                // first start note included. That might move the tick due to rounding errors,
                // and thereby include first note/rest which start has also been converted to tick.
                //System.out.println("Export start tick for organic: " + exportStartTick +" -> "+qtm.microsToTickABCOrganic(exportStartTick)+" micros="+getExportStartMicrosABC());
                //exportStartTick = Math.max(0L,qtm.microsToTickABCOrganic(getExportStartMicrosABC()));
                //disabled for now, have clamped all note ON to be after exportstarttick instead.
            }
            */
			return new Quad<>(infoList, sequence, histogram, dissonanceDetector);
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
     * @param chordsMade         the map of lists of chord that need to be filled.
     * @param dissonanceDetector
     */
	private void exportForPreviewChords(Map<AbcPart, List<Chord>> chordsMade, PolyphonyHistogram histogram, DissonanceDetector dissonanceDetector)
			throws AbcConversionException {
        boolean useMicroAccuracy = useRestsInChords || !reducedFilesize;
        int[] quanFractions = minimumQuantifiedMicros(!useMicroAccuracy);
		for (AbcPart part : parts) {
			if (part.getEnabledTrackCount() > 0) {
				if (organic) {
					Pair<List<Chord>,Boolean> chords = combineOrganic(part, true, histogram, quanFractions);
					chordsMade.put(part, chords.first);
                    dissonanceDetector.submitPart(part, chords.first);
				} else {
					List<Chord> chords = combineAndQuantize(part, true, histogram);
					chordsMade.put(part, chords);
                    dissonanceDetector.submitPart(part, chords);
				}
			} else {
				try {
					histogram.count(part, new ArrayList<>(), organic, qtm);
				} catch (IOException e) {
					throw new AbcConversionException("Failed to read instrument sample durations.", e);
				}
				chordsMade.put(part, null);
			}
		}
	}

	ExportTrackInfo exportPartToPreview(AbcPart part, Sequence sequence,
                                        int pan, boolean useLotroInstruments,
                                        Map<AbcPart, List<Chord>> chordsMade, CountIn countIn, int minDelay) throws AbcConversionException {
		List<Chord> chords = chordsMade.get(part);

		Triple<Integer, Integer, Long> trackNumber = exportPartToMidi(part, sequence, chords, pan, useLotroInstruments, countIn, minDelay);

		List<AbcNoteEvent> noteEvents = new ArrayList<>(chords.size());
		
		for (Chord chord : chords) {
			for (int i = 0; i < chord.size(); i++) {
				AbcNoteEvent ne = chord.get(i);
				
				// Skip rests and notes that are the continuation of a tied note
				if (ne.note == Note.REST || ne.tiesFrom != null)
					continue;

				// Convert tied notes into a single note event
				if (ne.tiesTo != null) {
                    ne.endABCMicros = ne.getTieEnd().endABCMicros;//must be before setEndTick() due to an assert inside getTieEnd()
					ne.setEndTick(ne.getTieEnd().getEndTick());
					ne.tiesTo = null;
					// Not fixing up the ne.tiesTo.tiesFrom pointer since we that for the
					// (ne.tiesFrom != null) check above, and we otherwise don't care about
					// ne.tiesTo.
				}

                // Issue with this is that tune editor section bars will no longer match up with what gets edited
                // could maybe be a toggle to show delay or not
                //ne.startABCMicros += delayMicros;
                //ne.endABCMicros += delayMicros;

				noteEvents.add(ne);
			}
		}

		return new ExportTrackInfo(trackNumber.first, part.origPart, noteEvents, trackNumber.second,
				part.getInstrument().midi.id(), trackNumber.third, part.numberOfExportedNotes, part.numberOfRemovedNotesForSafety,
                part.getMaxPoly(), part.numberOfRemovedNotesFromFitting, part.numberOfRemovedNotesZeros, part.numberOfRemovedNotesFromPruning,
                part.getPanEvent());
	}

	private Triple<Integer, Integer, Long> exportPartToMidi(AbcPart part, Sequence out, List<Chord> chords, int pan,
                                                            boolean useLotroInstruments, CountIn countIn, int minDelay) {
        part.numberOfExportedNotes = 0;
        int trackNumber = out.getTracks().length;
        //part.setPreviewSequenceTrackNumber(trackNumber);//since part is here a copy for threaded reasons, we set this in projectFrame now.

        int channel = lastChannelUsedInPreview + 1;

        if (channel == MidiConstants.DRUM_CHANNEL) {
            channel++;
        }

        lastChannelUsedInPreview = Math.max(channel, lastChannelUsedInPreview);

        Track track = out.createTrack();

        track.add(MidiFactory.createTrackNameEvent(part.getTitle()));
        if (useLotroInstruments) {
            // Only change the channel voice once
            track.add(MidiFactory.createLotroChangeEvent(part.getInstrument().midi.id(), channel, 0));
            logPreview.fine("Channel " + channel + " for " + part.getInstrument());
            track.add(MidiFactory.createChannelVolumeEvent(MidiConstants.MAX_VOLUME, channel, 0));
            track.add(MidiFactory.createReverbControlEvent(AbcConstants.MIDI_REVERB, channel, 0));
            track.add(MidiFactory.createChorusControlEvent(AbcConstants.MIDI_CHORUS, channel, 0));
            track.add(MidiFactory.createExpressionEvent(MidiConstants.MAX_EXPRESSION, channel, 0));
            track.add(MidiFactory.createChannelVolumeEvent(MidiConstants.MAX_VOLUME, channel, 1));
            track.add(MidiFactory.createReverbControlEvent(AbcConstants.MIDI_REVERB, channel, 1));
            track.add(MidiFactory.createChorusControlEvent(AbcConstants.MIDI_CHORUS, channel, 1));
            track.add(MidiFactory.createExpressionEvent(MidiConstants.MAX_EXPRESSION, channel, 1));
        }
        part.setPanEvent(MidiFactory.createPanEvent(pan, channel));
        track.add(part.getPanEvent());

        long lastEnd = 0L;

        MidiEvent lastCountin = null;
        long countInMicros = 0L;//all track notes will be delayed by this
        if (countIn != null) {
            long minimumMicro = AbcConstants.getShortestNoteMicros(qtm.getPrimaryExportTempoBPM());
            countInMicros = calculateCountInTotalMicrosABC(countIn, qtm);
            if (countInMicros > AbcConstants.LONGEST_COUNT_IN_MICROS) {
                // 12 seconds is max
                countInMicros = 0;
                countIn = null;
                logPreview.warning("Count-in for preview: count-in longer than 12 seconds, cancelling count-in.");
            } else {
                logPreview.info("Count-in for preview: total count-in. micros = " + countInMicros + " bars = " + countIn.barCount);
                int hits = countIn.pattern.dynamics.length;
                long hitMicros = countInMicros / hits;
                if (countIn.part == part) {
                    if (hitMicros >= minimumMicro) {
                        long drumDelayMicros = 0;
                        long tick = exportStartTick;
                        if (part.getDelay() - minDelay != 0) {
                            drumDelayMicros = (part.getDelay() - minDelay) * 1000L;
                            tick = organic
                                    ? qtm.microsToTickABCOrganic(qtm.tickToMicrosABCOrganic(exportStartTick) + drumDelayMicros)
                                    : qtm.microsToTickABC(qtm.tickToMicrosABC(exportStartTick) + drumDelayMicros);
                        }
                        logPreview.info("Count-in for preview: hitMicros: " + hitMicros);
                        for (CountIn.CountInDynamics dyn : countIn.pattern.dynamics) {
                            Dynamics volume = dyn.dynamics;
                            track.add(MidiFactory.createNoteOnEventEx(countIn.hit.note.id, channel,
                                    volume.midiVol, tick));

                            logPreview.info("Count-in for preview: added a count-in hit: " + countIn.hit.name + " velocity = " + volume.midiVol);
                            if (organic) {
                                tick = qtm.microsToTickABCOrganic(qtm.tickToMicrosABCOrganic(tick) + hitMicros);
                            } else {
                                tick = qtm.microsToTickABC(qtm.tickToMicrosABC(tick) + hitMicros);
                            }
                            lastCountin = MidiFactory.createNoteOffEventEx(countIn.hit.note.id, channel,
                                    0, tick);
                            track.add(lastCountin);
                            lastEnd = tick;
                        }
                    } else {
                        countInMicros = 0L;
                        logPreview.warning("Count-in for preview: count-in hits shorter than 60 ms, cancelling count-in.");
                    }
                } else if (hitMicros < minimumMicro) {
                    countInMicros = 0L;
                    logPreview.warning("Count-in for preview: count-in hits shorter than 60 ms, cancelling count-in.");
                }
            }
        }
        boolean first = true;
        if (chords != null) {
            // chords can be null if no tracks are selected, but there is a count-in on this drum

            List<AbcNoteEvent> notesOn = new ArrayList<>();

            int noteDelta = 0;
            if (!useLotroInstruments)
                noteDelta = part.getInstrument().octaveDelta * 12;

            long delayMicros = 0;
            if (part.getDelay()-minDelay != 0) {
                // Make delay on instrument be audible in preview
                delayMicros = qtm.multiplyByExportTempoFactor((part.getDelay()-minDelay) * 1000L);
            }
            if (countInMicros > 0L) {
                delayMicros += qtm.multiplyByExportTempoFactor(countInMicros);
            }
            //logPreview.warning(part.getPartNumber()+" "+part.getInstrument()+": delayMicro "+delayMicros);

            for (Chord chord : chords) {
                Dynamics dynamics = chord.calcDynamics(part.getAbcSong().dynamicsMethod);
                if (dynamics == null)
                    dynamics = Dynamics.DEFAULT;
                for (int j = 0; j < chord.size(); j++) {
                    AbcNoteEvent ne = chord.get(j);

                    if (!organic) {
                        // Need these for ExportTrackInfo. Note that delay is not included.
                        ne.startABCMicros = qtm.tickToMicrosABC(ne.getStartTick(), part);
                        ne.endABCMicros = qtm.tickToMicrosABC(ne.getEndTick(), part);
                    }

                    // Skip rests and notes that are the continuation of a tied note
                    if (ne.note == Note.REST || ne.tiesFrom != null)
                        continue;

                    // Add note off events for any notes that have been turned off by this point
                    Iterator<AbcNoteEvent> onIter = notesOn.iterator();
                    while (onIter.hasNext()) {
                        AbcNoteEvent on = onIter.next();

                        // Shorten the note to end at the same time that the next one starts
                        long endTick = on.getEndTick();
                        if (on.note.id == ne.note.id && on.getEndTick() > ne.getStartTick()) {
                            // the note starting now, has an ongoing note with same pitch
                            // we stop the ongoing note here.
                            endTick = ne.getStartTick();
                        }

                        if (endTick <= ne.getStartTick()) {
                            // This note has been turned off
                            onIter.remove();
                            long off = endTick;
                            if (delayMicros > 0L) {
                                if (organic) {
                                    off = qtm.microsToTickOrganic(qtm.tickToMicrosOrganic(endTick) + delayMicros);
                                } else {
                                    off = qtm.microsToTick(qtm.tickToMicros(endTick) + delayMicros);
                                }
                            }
                            lastEnd = Math.max(off, lastEnd);
                            track.add(MidiFactory.createNoteOffEvent(on.note.id + noteDelta, channel, off));
                        }
                    }

                    long endTick = ne.getTieEnd().getEndTick();

                    // Match the note lengths used in lotro for non-sustained notes
                    if (useLotroInstruments) {
                        boolean sustainable = part.getInstrument().isSustainable(ne.note.id);

                        if (!sustainable) {
                            // This is to not stop plucked/drum note before it has played out
                            long micros = AbcConstants.getNonSustainedNoteHoldMicros(part.getInstrument());

                            if (organic) {
                                endTick = qtm.microsToTickOrganic(
                                          qtm.tickToMicrosOrganic(ne.getStartTick()) + qtm.multiplyByExportTempoFactor(micros)
                                            );
                            } else {
                                endTick = qtm.microsToTick(qtm.tickToMicros(ne.getStartTick())
                                        + qtm.multiplyByExportTempoFactor(micros));
                            }
                        }
                    }

                    if (endTick != ne.getEndTick()) {
                        ne = new AbcNoteEvent(ne.note, ne.velocity, ne.getStartTick(), endTick, qtm, ne.origNote);
                    }

                    long onTick;
                    if (organic) {
                        if (delayMicros > 0L) {
                            onTick = qtm.microsToTickOrganic(qtm.tickToMicrosOrganic(ne.getStartTick()) + delayMicros);
                        } else {
                            onTick = ne.getStartTick();
                        }
                    } else {
                        if (delayMicros > 0L) {
                            onTick = qtm.microsToTick(qtm.tickToMicros(ne.getStartTick()) + delayMicros);
                        } else {
                            onTick = ne.getStartTick();
                        }
                    }
                    /*
                     The math.max is due to process of notes assumed start of midi-exportstarttick converted to
                     abc-micros, and they final ON time was converted back to midi-tick
                     (if with delay then using midi-micros, not abc-micros even) and might not match exact with
                     midi-exportstarttick anymore. If they are before exportstarttick then ABC preview would not
                     play the first note(s).
                     */
                    onTick = Math.max(onTick, exportStartTick);
                    if (first && lastCountin != null && onTick < lastCountin.getTick()) {
                        // there is be rounding differences between last countin hit and first note.
                        // we fix it here:
                        lastCountin.setTick(onTick);
                    }
                    first = false;
                    track.add(MidiFactory.createNoteOnEventEx(ne.note.id + noteDelta, channel,
                            dynamics.getVol(useLotroInstruments), onTick));
                    notesOn.add(ne);
                    part.numberOfExportedNotes++;
                }
            }

            for (AbcNoteEvent on : notesOn) {
                long off = on.getEndTick();
                if (delayMicros > 0L) {
                    if (organic) {
                        off = qtm.microsToTickOrganic(qtm.tickToMicrosOrganic(on.getEndTick()) + delayMicros);
                    } else {
                        off = qtm.microsToTick(qtm.tickToMicros(on.getEndTick()) + delayMicros);
                    }
                }
                lastEnd = Math.max(off, lastEnd);
                track.add(MidiFactory.createNoteOffEvent(on.note.id + noteDelta, channel, off));
            }
        }

        track.add(MidiFactory.createEndOfTrackEvent(lastEnd));


		return new Triple<>(trackNumber, channel, lastEnd);
	}

	public void exportToAbc(OutputStream os, boolean delayEnabled, String appName, int minDelay) throws AbcConversionException {
				
		// accountForSustain is true so that songbooks wont stop their timer before last note has finished sounding.
		// lengthenToBar is false for opposite reason, so reporting the correct duration to songbooks.
		Pair<Long, Long> startEnd = getSongStartEndTick(false, true);
		exportStartTick = startEnd.first;
		exportEndTick = startEnd.second;

        try (PrintStream out = new PrintStream(os, false, StandardCharsets.UTF_8)) {
            // Lotro uses Windows-1252 code page to decipher ABC files, but its more safe
            // to rely on UTF-8 for export, especially for abc player,
            // it will just show as garbled chars in lotro
            // when playing. It will still work.
			if (!parts.isEmpty()) {
				out.println("%abc-2.1");
				out.println(AbcField.SONG_TITLE + StringCleaner.cleanForABC(metadata.getSongTitle()));
				if (!metadata.getComposer().isEmpty()) {
					out.println(AbcField.SONG_COMPOSER + StringCleaner.cleanForABC(metadata.getComposer()));
				}
				out.println(AbcField.SONG_DURATION + Util.formatDuration(getSongLengthMicros()));
				if (!metadata.getTranscriber().isEmpty()) {
					out.println(AbcField.SONG_TRANSCRIBER + StringCleaner.cleanForABC(metadata.getTranscriber()));
				}
				out.println(AbcField.ABC_CREATOR + appName + " v" + MaestroMain.APP_VERSION);
				out.println(AbcField.EXPORT_TIMESTAMP + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now()));
				if (!organic) {
					out.println(AbcField.SWING_RHYTHM + Boolean.toString(qtm.isTripletTiming()));
					out.println(AbcField.MIX_TIMINGS + Boolean.toString(qtm.isMixTiming()));
                    out.println(AbcField.REDUCED_FILE_SIZE + Boolean.toString(reducedFilesize));
				} else {
					out.println(AbcField.SWING_RHYTHM + Boolean.toString(false));
					out.println(AbcField.MIX_TIMINGS + Boolean.toString(false));
                    out.println(AbcField.REDUCED_FILE_SIZE + Boolean.toString(reducedFilesize && !useRestsInChords));
				}
				out.println(AbcField.ORGANIC + Boolean.toString(organic));
				out.println(AbcField.ORGANIC_MULTI_STAGE + Boolean.toString(organic && organic2));
                out.println(AbcField.ORGANIC_VERSION + Integer.toString((organic && organic2 && upgraded)?2:1));
				out.println(AbcField.ORGANIC_POLY_6_PLUS + Boolean.toString(organic && useRestsInChords));
				out.println(AbcField.SKIP_SILENCE_AT_START + Boolean.toString(skipSilenceAtStart));
				out.println(AbcField.DELETE_MINIMAL_NOTES + Boolean.toString(deleteMinimalNotes && !organic));
				out.println(AbcField.ABC_VERSION + "2.1");
				
				
				outputBadger(out);
			}
	        PolyphonyHistogram histogram = new PolyphonyHistogram();
            boolean useMicroAccuracy = useRestsInChords || !reducedFilesize;
            int[] quanFractions = minimumQuantifiedMicros(!useMicroAccuracy);

            AbcPart one = parts.isEmpty() ? null : parts.getFirst();
            CountIn countIn = null;
            long minMicros = AbcConstants.getShortestNoteMicros(qtm.getPrimaryExportTempoBPM());
            if (organic) minMicros = quanFractions[3];
            if (one != null) {
                countIn = one.getAbcSong().getCountIn();
                if (countIn != null) {
                    long countInMicros;
                    if (countIn.pattern.dynamics.length == 0) {
                        countInMicros = 0L;
                    } else {
                        countInMicros = calculateCountInTotalMicrosABC(countIn, qtm);
                        long hitMicros = countInMicros / countIn.pattern.dynamics.length;
                        if (countInMicros > AbcConstants.LONGEST_COUNT_IN_MICROS) {
                            countInMicros = 0L;
                        } else if (hitMicros < minMicros) {
                            countInMicros = 0L;
                        }
                    }
                    countIn.micros = countInMicros;
                }
            }

			for (AbcPart part : parts) {
				if (part.getEnabledTrackCount() > 0 || (part.getAbcSong().getCountIn() != null && part.getAbcSong().getCountIn().micros > 0L && part.getAbcSong().getCountIn().part == part)) {
					if (organic) {
						exportPartToAbcOrganic(part, out, delayEnabled, histogram, quanFractions, minDelay);
					} else {
						exportPartToAbc(part, out, delayEnabled, histogram, minDelay);
					}
				}
			}

            // PrintStream never throws on write failure; it sets an internal error
            // flag instead. checkError() flushes first, so this catches a failure
            // from any out.print/println above (disk full, broken pipe, etc.).
            if (out.checkError()) {
                throw new AbcConversionException(
                        "An I/O error occurred while writing the ABC file; the output may be incomplete.");
            }
		}
	}

	private void outputBadger(PrintStream out) {
		String genre = StringCleaner.cleanForABC(metadata.getGenre()).toLowerCase().trim();
		String mood = StringCleaner.cleanForABC(metadata.getMood()).toLowerCase().trim();
		String outAll = metadata.getPartSetup();
		String badgerTitle = metadata.getBadgerTitle();
		if (!genre.isEmpty() || !mood.isEmpty() || outAll != null || badgerTitle != null) {
			out.println();
			if (badgerTitle != null) {
				out.println(badgerTitle);
			}
			if (!genre.isEmpty()) {
				out.println("N: Genre: " + genre);
			}
			if (!mood.isEmpty()) {
				out.println("N: Mood: " + mood);
			}
			if (outAll != null) {
				out.print(outAll);
			}
		}
	}

	private void exportPartToAbcOrganic(AbcPart part, PrintStream out,
                                        boolean delayEnabled, PolyphonyHistogram histogram, int[] quanFractions, int minDelay) throws AbcConversionException {

        //long L = (qtm.getMeter().numerator / (double) qtm.getMeter().denominator) < 0.75d ? 16L : 8L;
        long Q = qtm.getPrimaryExportTempoBPM();

        // One whole abc note is this many microseconds
        // This we will use as denominator for full precision
        int oneMicro = (int)(qtm.getMeter().denominator * TimingInfo.ONE_SECOND_MICROS * 60L / Q);

        boolean useMicroAccuracy = useRestsInChords || !reducedFilesize;

		StringBuilder head = exportPartHeaderToAbc(part, quanFractions, useMicroAccuracy?1:(int)milliToMicro(1,oneMicro,quanFractions[4]));

        List<StringBuilder> builders = new ArrayList<>();
        builders.add(head);

		// Keep track of which notes have been sharped or flatted so
		// we can naturalize them the next time they show up.
		boolean[] sharps = new boolean[Note.MAX_PLAYABLE.id + 1];
		boolean[] flats = new boolean[Note.MAX_PLAYABLE.id + 1];

		// Write out ABC notation


        /*
         Trade-off between filesize and drift when many minimum notes
         in succesion can make the drift not ideal.
         At milli2micro at 100 the worst drift I saw from running 900 various
         songs through this with reduced accuracy was in a 19 minute song
         where the drift somewhere in one of the parts was almost 9 millisecs.
         10 ms is kinda the fastest a human expert musicians ear can pick up.
         But! GCD also reduces the fractions, and most often using 10 instead
         of 100 will result in same reduced fraction.
         And with 10 as value, the drift is so small that is not worth
         thinking about.
         For reference, the real reason for drift at all is that the note
         processing algorithms use 60000 or 60001 as minimum since that's what
         lotro accept. But for example for using 100, a 60000 minimum all of a
         sudden can be up to 60099. And then a lot of minimum notes after each
         other and the drift can accumulate until a longer note/rest saves the day
         by getting drift corrected and the timeline gets back on track.
         TODO: Could change all the note processing methods in this class
               to use a modified minimum, they will elongate, move and cut rests
               and also delete notes to make it all fit. But I think the 10
               compromise is rather nice. Basically no drift, and still reduced
               filesize with at least 2 digits per chord.
         PS. With 100, the 900 songs takes up 86.963.288 bytes (max 9 ms drift)
             With  10, they take up           98.252.641 bytes (max 0.6 ms drift)
             That was with multi-stage, poly6+ off. So a bit more than 10% larger.
             Single-stage, poly6+ off:
             85.898.803 bytes (0.9 ms drift) vs 96.873.151 bytes (less than 0.2 ms drift)
             The more drift in multi-stage comes from its fitting, it not as
             an aggressive fitting algorithm as single-stage.
             The code is more neat and maintainable in multistage though.
             Anyway, the drift is completely neglectable for both organics now.
        */

		
		// One whole abc note is this many milliseconds
		// This we will use as denominator for reduced precision
		double oneMilli = quanFractions[4]*quanFractions[1]/(double)quanFractions[0];

		long minimumMicro = quanFractions[2];

        //minimum numerator for reduced precision:
        int minimumMilli = quanFractions[8];//microToMilliCeil(minimumMicro, oneMicro, oneMilli);
        int maximumMilli = microToMilliFloor(AbcConstants.LONGEST_NOTE_MICROS, oneMicro, oneMilli);

        if (minimumMilli/(double)oneMilli < minimumMicro/(double)oneMicro) {
            // a bit complex, but the reducer can change the resultant
            // numbers so that the lotro calculated note time gets to be
            // 0.0599999 despite our calculation since we will divide
            // note and L denominator by either 100 or 10, sometimes.
            // In other words, strange bpm still haunts.
            //minimumMilli++;
            // disabled reducer for now, and therefore commented this code out,
            // and disabled the assert below.
            logAbc.info("minimumMicro=" + minimumMicro + ", minimumMilli++=" + minimumMilli);
        }
		//assert minimumMilli/(double)oneMilli >= minimumMicro/(double)oneMicro:"reduced min="+(minimumMilli/(double)oneMilli)+" min="+(minimumMicro/(double)oneMicro);

		final long songStartMicros = qtm.tickToMicrosABCOrganic(exportStartTick);

		Dynamics curDyn = null;
		Dynamics initDyn = null;
		
		
		
		final StringBuilder bar = new StringBuilder();
		
		Runnable addLineBreaks = () -> {
			// Trim end
			int length = bar.length();
			if (length == 0)
				return;

            while (length > 0 && Character.isWhitespace(bar.charAt(length - 1)))
                length--;
			bar.setLength(length);
		};

        long countInMicros = 0L;//all non-count-in track notes will be delayed by this
        CountIn countIn = null;
        if (part.getAbcSong().getCountIn() != null && part.getAbcSong().getCountIn().pattern != CountIn.CountInPattern.OFF) {
            countIn = part.getAbcSong().getCountIn();
            countInMicros = calculateCountInTotalMicrosABC(countIn, qtm);
            if (countInMicros > AbcConstants.LONGEST_COUNT_IN_MICROS) {
                countInMicros = 0;
                countIn = null;
                logAbc.warning("Count-in for ABC: count-in longer than 12 seconds, cancelling count-in.");
                ProjectFrame.feed(UIText.get("maestro.warning.count.in.cancelled.it.s.too.long"), UIText.get("maestro.reduce.to.at.under.12.seconds"));
            } else {
                logAbc.info("Count-in for ABC: total count-in. micros = " + countInMicros + " bars = " + countIn.barCount);
            }
        }
		StringBuilder delayed = new StringBuilder();
		if (delayEnabled || countIn != null) {

            long hitMicros = 0L;

            if (countIn != null && countIn.part == part) {
                int hits = countIn.pattern.dynamics.length;
                hitMicros = countInMicros / hits;
                countInMicros = 0L;
                if (hitMicros < minimumMicro) {
                    countIn = null;//cancel, since count-in is too short
                    logAbc.warning("Count-in for ABC: hitMicros shorter than 60 ms, cancelling count-in.");
                    ProjectFrame.feed(UIText.get("maestro.warning.count.in.cancelled.it.s.too.short"), UIText.get("maestro.expand.so.each.drum.hit.is.more.than.60.ms.apart"));
                } else if (!useMicroAccuracy && microToMilliCeil(hitMicros,oneMicro,oneMilli) < minimumMilli) {
                    countIn = null;//cancel, since count-in is too short
                    logAbc.warning("Count-in for ABC: hitMicros shorter than 60 ms, cancelling count-in.");
                    ProjectFrame.feed(UIText.get("maestro.warning.count.in.cancelled.it.s.too.short"), UIText.get("maestro.expand.so.each.drum.hit.is.more.than.60.ms.apart"));
                } else {
                    logAbc.info("Count-in for ABC: going forward.");
                }
            } else if (countIn != null) {
                int hits = countIn.pattern.dynamics.length;
                hitMicros = countInMicros / hits;
                if (hitMicros < minimumMicro) {
                    countInMicros = 0L;
                } else if (!useMicroAccuracy && microToMilliCeil(hitMicros,oneMicro,oneMilli) < minimumMilli) {
                    countInMicros = 0L;
                }
                logAbc.info("Count-in for ABC: not this part. hit="+hitMicros+" total="+countInMicros);
            }

			// the 100 is so the delay is always larger than 60 ms, even if its 0 ms.
			long delayMicro = (part.getDelay()+100L-minDelay)*1000L + countInMicros;
            //logAbc.warning(part.getPartNumber()+" "+part.getInstrument()+"delayMicro "+delayMicro+" = ("+part.getDelay()+"+100+"+(-minDelay)+")*1000+"+countInMicros);
            final long MAX_REST_MICROS = 7 * AbcConstants.ONE_SECOND_MICROS;
            long parts = (delayMicro + MAX_REST_MICROS - 1) / MAX_REST_MICROS;   // ceil division
            if (parts < 1) parts = 1;
            long base = delayMicro / parts;        // each piece ~equal, all well above the 60ms floor
            long remainder = delayMicro % parts;   // distribute the leftover micros

            for (int i = 0; i < parts; i++) {
                long rest = base + (i < remainder ? 1L : 0L);   // spread remainder 1 micro at a time
                if (useMicroAccuracy) delayed.append("z" + rest);
                else delayed.append("z" + microToMilliCeil(rest,oneMicro,oneMilli));
                delayed.append(" ");
            }
            delayed.append("| \n");
            if (countIn != null && countIn.part == part) {
                /*
                 Count-in on songs where the first note is delayed,
                 will ignore the delay. If it's the count-in drum itself that are delayed,
                 count-in will also be delayed.
                 */

                logAbc.info("Count-in for ABC: hitMicros: "+hitMicros);
                for (CountIn.CountInDynamics dyn : countIn.pattern.dynamics) {
                    Dynamics volume = dyn.dynamics;
                    bar.append('+').append(volume).append("+ ");
                    bar.append(countIn.hit.note.abc);
                    if (useMicroAccuracy) bar.append((int)hitMicros);
                    else bar.append(microToMilliCeil(hitMicros,oneMicro,oneMilli));

                    logAbc.info("Count-in for ABC: added a count-in hit: "+countIn.hit.name+" velocity = "+volume.midiVol);
                }
            }
		}
        builders.add(delayed);
		
		Pair<List<Chord>, Boolean> pair = combineOrganic(part, false, histogram, quanFractions);
		 
		List<Chord> chords = pair.first;

        // check that last notes is not tied. Could have impact on drone-bug if fails.
        //assert chords.isEmpty() || chords.getLast().getNotes().stream().allMatch(note -> note.tiesTo == null);
		
		if (useMicroAccuracy) {
            //logAbc.warning("ABC part organic export: using micro accuracy.");
			logAbc.info("ABC part organic export: Q="+Q+", L="+quanFractions[5]+"/"+quanFractions[6]+", 1 numerator=1 us, minimum="+(minimumMicro)+" μs.");
		} else {
            //logAbc.warning("ABC part organic export: using reduced accuracy.");
			logAbc.info("ABC part organic export: Q="+Q+", L="+quanFractions[5]+"/"+quanFractions[6]+", 1 numerator="+milli2micro+" μs, minimum="+quanFractions[2]+" μs.");
		}
		
		for (Chord c : chords) {
			initDyn = c.calcDynamics(part.getAbcSong().dynamicsMethod);
			if (initDyn != null)
				break;
		}
		
		int countChords = 0;
		long currentMicro = 0L;
		long currentMilli = 0L;
		long passingNoteEndMilli = 0L;
        long passingNoteEndMicro = 0L;
		long largestDriftMicros = 0L;
		for (Chord ch : chords) {
			if (ch.size() == 0) {
				assert false : part.getAbcSong().getTitle()+" "+part.getTitle()+ ": Chord has no notes!";
				continue;
			}
			ChordOrganic c = (ChordOrganic) ch;

			//assert !c.hasRestAndNotes() || organic2;

			/*
			 * if (c.hasRestAndNotes()) { c.removeRests(); }
			 */

			c.sort();
			
			countChords++;

			if (countChords % 8 == 0) {
				// Print at every 8th chord
				if (!bar.isEmpty()) {
                    addLineBreaks.run();
                    bar.append(" |\n");
                    if (!reducedFilesize) {
                        long micros = (qtm.tickToMicrosABCOrganic(c.getStartTick()) - songStartMicros);
                        bar.append(String.format(Locale.US, "%%  (%s) bar %.1f\n", Util.formatDuration(micros), part.getSequenceInfo().getDataCache().tickToBarNumberFloat(c.getStartTick())));
                    }
                }

				Arrays.fill(sharps, false);
				Arrays.fill(flats, false);
			}

						 
			// Is this the start of a new tempo?
			TimingInfo tm = qtm.getTimingInfoOrganic(c.getStartTick());

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
			chordMicro = (int)(c.getEndMicros() - c.getStartMicros());
			
			
			long cEndMicro;
			cEndMicro = c.getEndMicros() - songStartMicros;
			
			long cStartMicro;
			cStartMicro = c.getStartMicros() - songStartMicros;
			
			if ((cEndMicro-cStartMicro) < 59000) {
				// this might not be serious as the tick resolution can be very low,
				// like 10 ms per tick.
				logAbc.fine("combine output mismatch: "+qtm.tickToMicrosABCOrganic(c.getStartTick())+" -> "+qtm.tickToMicrosABCOrganic(c.getEndTick())+" ("+(cEndMicro-cStartMicro)+" μs)");
				//assert false;
			}
			
			//must not become shorter than the micros,
			//as then long notes can overlap proceeding note with same pitch.
			int chordMilli = microToMilliCeil(chordMicro, oneMicro, oneMilli);
			
			assert !c.isUneven() || useRestsInChords;
			
			if (useMicroAccuracy) {
				long diff = (currentMicro + chordMicro) - cEndMicro;
				chordMicro -= (int)diff;
				
				int minAdjust = 0;
				if (chordMicro < minimumMicro) {
					logAbc.finest("Increased chord from "+chordMicro+" to "+milliToMicro(minimumMilli, oneMicro, oneMilli)+" micros.");
					minAdjust = (int)minimumMicro - chordMicro;
					chordMicro = (int)minimumMicro;
				}
				
				if (chordMicro > AbcConstants.LONGEST_NOTE_MICROS) {
					// should never happen
					logAbc.severe(part.getTitle() +": chord is "+(chordMicro)+" μs, drone="+isDrone(part, c.get(0)));
					chordMicro = (int)(AbcConstants.LONGEST_NOTE_MICROS-1L);
				}
				
				long driftChordMicros = currentMicro - cStartMicro;

				if (Math.abs(driftChordMicros) > Math.abs(largestDriftMicros)) {
					largestDriftMicros = driftChordMicros;
					
					if (Math.abs(driftChordMicros) > 10000L) {
						long chordEndDiff = (currentMicro + chordMicro) - cEndMicro;
						logAbc.warning("chordStart-driftMicros="+driftChordMicros+". End adjustment was "+(-diff+minAdjust)
								+", ideal end adjustment would have been "+(-diff)+", chordEnd-driftMicros="+chordEndDiff+" μs. ("+part.getTitle()+")"
						        +"\nChord should be "+(cEndMicro-cStartMicro)+" but ended as "+chordMicro);
					}
				}
			} else {
                int oldChordMilli = chordMilli;
                long chordMilliInMicros = milliToMicro(chordMilli, oneMicro, oneMilli);
                long diffMicros = (currentMicro + chordMilliInMicros) - cEndMicro;
				if (diffMicros != 0) {
					// After a series of chords, we might start to drift due to conversions vs. keeping lotro limits.
					// When the drift magnitude gets larger than milliFactor, we adjust.
					
					// diff is how much the note ending will have drifted in micros

                    int diffInMillis = microToMilliRound(diffMicros, oneMicro, oneMilli);

					if (c.isUneven() || passingNoteEndMilli > currentMilli + chordMilli - diffInMillis) {
						// allow only positive adjustment
						// this is needed, but it makes poly 6+ drift too much,
						// so we use micro accuracy for poly 6+.
                        diffInMillis = Math.min(0, diffInMillis);
                        assert false:"At the time, this should not run";
					}
					
					// adjust the note duration by the drift
                    chordMilli -= diffInMillis;
				} else diffMicros = Long.MIN_VALUE;
				
				long minAdjust = 0L;
				if (chordMilli < minimumMilli) {
					logAbc.finest("Increased chord from "+chordMicro+" to "+(minimumMilli*oneMicro/(double)oneMilli)+" micros.");
					minAdjust = minimumMilli - chordMilli;
					chordMilli = minimumMilli;
				}
                if (chordMilli > maximumMilli) {
					// should never happen
                    logAbc.severe(part.getTitle() +": chord is "+milliToMicro(chordMilli, oneMicro, oneMilli)+" μs, drone="+isDrone(part, c.get(0)));
                    chordMilli = maximumMilli;
                }

                chordMilliInMicros = milliToMicro(chordMilli, oneMicro, oneMilli);
				
				chordMicro = (int)chordMilliInMicros;
				
				long chordStartDiff = currentMicro - cStartMicro;
                if (Math.abs(chordStartDiff) > Math.abs(largestDriftMicros) && diffMicros != Long.MIN_VALUE) {
                    if (Math.abs(chordStartDiff) > 10000L) {
                        // 10 ms is known to be what a human expert musicians ear can pick up.
                        long chordEndDiff = (currentMicro + chordMicro) - cEndMicro;
                        long adjustmentMicros = milliToMicro(chordMilli-oldChordMilli,oneMicro,oneMilli);//milliToMicro(microToMilliRound(-diff, oneMicro, oneMilli) + (int)minAdjust, oneMicro, oneMilli);
                        long idealAdjustment = -diffMicros;


                        logAbc.warning("\nHigh drift in "+part.getAbcSong().getTitle()
                                +"\nstartChord-driftMicros="+chordStartDiff+". End adjustment was "+adjustmentMicros
                                +", ideal end adjustment would have been "+idealAdjustment+", endChord-driftMicros="+chordEndDiff+" μs. ("+part.getTitle()+")"
                                +"\nChord dura should have been "+(cEndMicro-cStartMicro)+", but is now "+chordMicro);



					}
                    //System.out.println("Dura="+(cEndMicro-cStartMicro)+", diff="+(chordMicro-(cEndMicro-cStartMicro))+" startdrift="+chordStartDiff+" enddrift="+((currentMicro+chordMicro)-cEndMicro));
					largestDriftMicros = chordStartDiff;
				} else if (Math.abs((currentMicro+chordMicro)-cEndMicro) > 10L) {
                    //System.out.println("Dura="+(cEndMicro-cStartMicro)+", diff="+(chordMicro-(cEndMicro-cStartMicro))+" startdrift="+chordStartDiff+" enddrift="+((currentMicro+chordMicro)-cEndMicro));
                }
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
				noteMicro = (int)(evt.endABCMicros - evt.startABCMicros);
				
				long nEndMicro;
				nEndMicro = evt.endABCMicros - songStartMicros;

                assert nEndMicro >= cEndMicro;// if this fails, a bug is in one of the proceeding methods.
				
				int numerator;
				int denominator;
				
				if (useMicroAccuracy) {
					if (nEndMicro == cEndMicro) {
                        numerator = chordMicro;
                    } else if (nEndMicro < cEndMicro) {
                        numerator = chordMicro;
                        assert false:"corrupt chord";
					} else {
						long diff = (currentMicro + noteMicro) - nEndMicro;
						noteMicro -= (int) diff;
						
						if (noteMicro < minimumMicro) {
							logAbc.finest("Increased note from "+noteMicro+" to "+minimumMicro+" micros.");
							noteMicro = (int)minimumMicro;
						}
						
						if (noteMicro > AbcConstants.LONGEST_NOTE_MICROS) {
							// should never happen
							logAbc.severe(part.getTitle() +": note is "+(noteMicro)+" us, drone="+isDrone(part, c.get(0)));
							noteMicro = (int)(AbcConstants.LONGEST_NOTE_MICROS-1L);
						}
						
						numerator = noteMicro;
					}

                    if (useRestsInChords) {
                        if (currentMicro + numerator > passingNoteEndMicro){
                            passingNoteEndMicro = currentMicro + numerator;
                        }
                    }

					denominator = 1;
				} else {
					int noteMilli = microToMilliFloor(noteMicro, oneMicro, oneMilli);
					if (nEndMicro == cEndMicro || !useRestsInChords) {
						noteMilli = chordMilli;
					} else {
						
						
						// Careful. If adjusting eneven chord then chord might become shorter
						// which can cascade to later chord which has a note with same pitch
						// as a long note from this chord. If that same note then get to start
						// earlier, the long note might overlap it, making entire part silent.
						// Similar if this chord starts while there a long note from previous
						// chord still playing, a shortening might cascade in similar manner. 
                        long noteMilliInMicros = milliToMicro(noteMilli, oneMicro, oneMilli);
                        long diffMicros = (currentMicro + noteMilliInMicros) - nEndMicro;

                        noteMilli -= microToMilliRound(diffMicros,oneMicro,oneMilli);

						if (noteMilli < minimumMilli) {
							noteMilli = minimumMilli;
						}
						if (noteMilli < chordMilli) {
							noteMilli = chordMilli;
						}
                        if (noteMilli > maximumMilli) {
                            // should not happen
                            logAbc.severe(part.getTitle() +": note is "+milliToMicro(noteMilli, oneMicro, oneMilli)+" μs, drone="+isDrone(part, evt));
                            noteMilli = maximumMilli;
                        }
					}

                    if (useRestsInChords) {
                        if (currentMilli + noteMilli > passingNoteEndMilli){
                            passingNoteEndMilli = currentMilli + noteMilli;
                        }
                    }
					
					numerator = noteMilli;
					denominator = 1;
				}

				if (numerator == 1 && denominator == 2) {
					bar.append('/');
				} else if (numerator == 1 && denominator == 4) {
					bar.append("//");
				} else {
					if (numerator == 0) {
						logAbc.severe("Zero length Error: ticks=" + evt.getLengthTicks()
								 + " note=" + noteAbc);
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
				currentMilli += chordMilli;
			} else {
				logAbc.warning("Zero notes written in chord");
			}

			bar.append(' ');
		}

		//The tolerance is 10 ms. But should best be under 1 ms.
		logAbc.info("Largest drift was "+largestDriftMicros+" μs. ("+part.getTitle()+")");
		logAbc.fine(part.getTitle()+" EXPORT: ends at "+Util.formatDurationM(currentMicro)+" - micro:"+currentMicro);

		addLineBreaks.run();
        builders.add(bar);

        for (StringBuilder b : builders) {
            out.print(b);
        }
        if (part.getInstrument() == LotroInstrument.BASIC_BAGPIPE) {
            // Attempt to fix drone-bug (aka. horn bug)
            if (useMicroAccuracy) out.print(" x500000");
            else out.print(" x" + microToMilliCeil(500_000,oneMicro,oneMilli));
        }
		out.println(" |]");
		out.println();
	}

    /**
     * Converts a reduced precision numerator over oneMilli back to the micro duration, rounding to the nearest micro.
     */
    private long milliToMicro(int millis, int oneMicro, int oneMilli) {
        return (long) Math.round((millis / (double) oneMilli) * oneMicro);
    }

    /**
     * Converts a micro duration to reduced precision numerator, rounding up.
     * Ensure the numerator is not accidentally shortened.
     */
    private int microToMilliCeil(long micros, int oneMicro, int oneMilli) {
        return (int) Math.ceil((micros / (double) oneMicro) * oneMilli);
    }

    /**
     * Converts a micro duration to reduced precision numerator, rounding to the nearest reduced unit.
     * Used for calculating drift
     */
    private int microToMilliRound(long micros, int oneMicro, int oneMilli) {
        return (int) Math.round((micros / (double) oneMicro) * oneMilli);
    }

    /**
     * Converts a micro duration to reduced precision, rounding down.
     */
    private int microToMilliFloor(long micros, int oneMicro, int oneMilli) {
        return (int) Math.floor((micros / (double) oneMicro) * oneMilli);
    }

    /**
     * Converts a reduced precision numerator over oneMilli back to the micro duration, rounding to the nearest micro.
     */
    private long milliToMicro(int millis, int oneMicro, double oneMilli) {
        return (long) Math.round((millis / (double) oneMilli) * oneMicro);
    }

    /**
     * Converts a micro duration to reduced precision numerator, rounding up.
     * Ensure the numerator is not accidentally shortened.
     */
    private int microToMilliCeil(long micros, int oneMicro, double oneMilli) {
        return (int) Math.ceil((micros / (double) oneMicro) * oneMilli);
    }

    /**
     * Converts a micro duration to reduced precision numerator, rounding to the nearest reduced unit.
     * Used for calculating drift
     */
    private int microToMilliRound(long micros, int oneMicro, double oneMilli) {
        return (int) Math.round((micros / (double) oneMicro) * oneMilli);
    }

    /**
     * Converts a micro duration to reduced precision, rounding down.
     */
    private int microToMilliFloor(long micros, int oneMicro, double oneMilli) {
        return (int) Math.floor((micros / (double) oneMicro) * oneMilli);
    }

    /**
     * Minimum note/rest duration
     * The result will be in micros, so that if exporting with
     * reduced precision, it is the absolute lowest number that lotro
     * will accept given the denominator we plan to provide.
     *
     * We don't use this value all places, only for the fitting algorithms.
     * So that the fitting of too small numerators takes places where we do stuff
     * about it, and less so during output.
     */
    private int[] minimumQuantifiedMicros(boolean reduced) {
        int Q = qtm.getPrimaryExportTempoBPM();
        int M = qtm.getMeter().denominator;
        int idealMinimum = (int)AbcConstants.getShortestNoteMicros();
        int[] quanFractions;
        if (!reduced) {
            int oneMicro = (int) (M * TimingInfo.ONE_SECOND_MICROS * 60L / Q);

            float time = ((M*60.0f * idealMinimum)/((float)oneMicro*Q));
            if (time < 0.06f) {
                if (!AbcConstants.isStrangeBPM(Q)) {
                    //System.out.println(parts.getFirst().getAbcSong().getTitle()+": Ideal minimum is "+idealMinimum+" micros (not strange)");
                    //idealMinimum++;
                    // Of the 900 songs I tested, none came into this condition.
                } else {
                    // we now only use 60001 if its strange BPM and the float calc failed.
                    // that will allow more songs to use 60 ms, and so far it has
                    // worked, but more testing is needed.
                    idealMinimum++;
                    //throw new RuntimeException("skipping file");
                }
            } else if (AbcConstants.isStrangeBPM(Q)) {
                // despite it being a strange bpm, we allow 60000. Edit: nope, we don't.
                //System.out.println(parts.getFirst().getAbcSong().getTitle()+": Ideal minimum is "+idealMinimum+" micros (strange)");
                idealMinimum++;
                // tested it, and without this at least 1 song did not play. (ConcertViolinsLuteContinuo2nd-Vivaldi, 30 bpm) [and it didn't use regex reducer]
                //throw new RuntimeException("skipping file");
            } else {
                //throw new RuntimeException("skipping file");
            }
            quanFractions = new int[]{1,1,idealMinimum,Integer.toString(oneMicro).length()-1,oneMicro,1,oneMicro, 0, 0};
            logNotes.info("Not reducing file size. Fraction setup is L="+quanFractions[0]+"/"+quanFractions[1]+" micros="+quanFractions[2]+" digits="+quanFractions[3]+" denom="+quanFractions[4]+"| result L:"+quanFractions[5]+"/"+quanFractions[6]);
            return quanFractions;
        }
        idealMinimum = (int)AbcConstants.getShortestNoteMicros();
        quanFractions = new int[]{};//Lnum, Ldenom, micros, digits, denom (wont be used directly), final Lnum, final Ldenom, floatOk, minimumNumeratorForReduced
        boolean strange = AbcConstants.isStrangeBPM(Q);
        outer:for (int Ldenom = 1; Ldenom <= 99; Ldenom++) {
            for (int Lnum = 1; Lnum <= 999; Lnum++) {
                int [] suggest = suggestion(Lnum,Ldenom,strange,Q);

                if (suggest != null && (quanFractions.length == 0 || suggest[2] <= quanFractions[2]) && suggest[3] >= 4) {
                    // it has potential. Not too many significant digits. At least as good as previous best.
                    boolean good = staysWithinSixSignificantDigits(suggest[5],suggest[6]);
                    boolean prevGood = quanFractions.length != 0 && quanFractions[7] == 1;
                    if (quanFractions.length == 0
                                || suggest[2] < quanFractions[2]
                                || (Lnum+Ldenom < quanFractions[0]+quanFractions[1] && prevGood == good)
                                || (good && !prevGood)
                            ) {
                        // Either smaller numbers, or it fit into a C++ float. Or its result is closer to minimum.
                        // or its resultant 'grid' will be finer.
                        quanFractions = suggest;
                        quanFractions[7] = good?1:0;
                        /*
                        System.out.println(" Optimal fraction setup is L="+quanFractions[0]+"/"+quanFractions[1]+" Q="+Q
                                +" micros="+quanFractions[2]+" digits="+quanFractions[3]+" denom="+quanFractions[4]
                                +"| result L:"+quanFractions[5]+"/"+quanFractions[6]+" fits="+quanFractions[7]+" strange="+strange);
                        System.out.println("  prevGood="+prevGood);
                         */
                        if (!strange && suggest[2] == idealMinimum && good) break outer;
                    }
                }
            }
        }
        if (quanFractions.length == 0) {
            logNotes.info("No best fraction setup found.");
            int L = (qtm.getMeter().numerator / (double) qtm.getMeter().denominator) < 0.75d ? 16 : 8;
            quanFractions = suggestion(1,L,strange,Q);
            if (quanFractions == null) {
                // should very very rarely happen if ever at all
                reducedFilesize = false;//Metadata will still write reduced true out. But thats fine, it just didnt reduce anything.
                quanFractions = minimumQuantifiedMicros(false);
            }
        }
        //if (!(strange && quanFractions[2] == 60000)) throw new RuntimeException("skipping file");
        logNotes.info("Reduced file size. Optimal fraction setup is L="+quanFractions[0]+"/"+quanFractions[1]+" Q="+Q+" micros="+quanFractions[2]+" digits="+quanFractions[3]+" denom="+quanFractions[4]+"| result L:"+quanFractions[5]+"/"+quanFractions[6]+" fits="+quanFractions[7]+" strange="+strange);
        return quanFractions;
    }

    private int[] suggestion (int Lnum, int Ldenom, boolean strange, int Q) {
        long oneMicro = qtm.getMeter().denominator * TimingInfo.ONE_SECOND_MICROS * 60L / Q;
        if (oneMicro > Integer.MAX_VALUE) return null;
        int oneMilli = Math.ceilDiv((int) oneMicro, milli2micro);//milli2micro = 10,100 or 1000.
        if (oneMilli == 0) return null;
        double oneMilliEffect = oneMilli * Ldenom / (double)Lnum;
        //int minimumMilli = microToMilliCeil(60000L, (int)oneMicro, oneMilliEffect);
        //double fraction = qtm.getMeter().denominator * 60d * minimumMilli / (Q * oneMilliEffect);
        // account for lotro use of floats:
        double wholeNoteImpreciseSecs = (double)oneMicro / AbcConstants.ONE_SECOND_MICROS;
        double milliLImprecise = wholeNoteImpreciseSecs / oneMilliEffect;
        int minimumMilli = (int) Math.ceil(0.06d / milliLImprecise);
        double fraction = minimumMilli*milliLImprecise;
        if (fraction < 0.06d) {
            //int oneMilliNumerator = microToMilliCeil(1L, (int)oneMicro, oneMilliEffect);
            //result += oneMilliNumerator;
            minimumMilli++;
            fraction = minimumMilli*milliLImprecise;
        }
        int fittingMinimum = (int) Math.ceil((minimumMilli / oneMilliEffect) * oneMicro);
        if (fraction < 0.05999999999999d) {
            // less than 0.06 for a double
            // but larger than a c++ float 0.0599999
            logNotes.severe("Fraction error: "+fraction+" lnum="+Lnum+" ldenom="+Ldenom+" Q="+Q+" denominator="+qtm.getMeter().denominator+" oneMicro="+oneMicro+" oneMilli="+oneMilli+" minimumMilli="+minimumMilli+" fittingMinimumMicro="+fittingMinimum);
            assert false : "Fraction error: "+fraction;
            return null;
        }
        int gcd = Util.gcd(Lnum, Ldenom);
        Lnum /= gcd;
        Ldenom /= gcd;
        gcd = Util.gcd(Lnum, Ldenom*oneMilli);
        int finalLnum = Lnum / gcd;
        int finalLdenom = Ldenom * oneMilli / gcd;

        // Lnum, Ldenom: not used in the output.
        // fittingMinimum: used to constrain notes during note fitting, before outputting.
        // digit: approx number of digits used to divide the number we supply after each note,
        // to get a feel for how granular the final 'grid' can be. 4 or 5 required.
        // oneMilli: Lnum/(Ldenom*oneMilli) will be final L, if this result wins.
        // final Lnum/Ldenom: final L suggestion.
        // -1: will later be 1 if L can be stored in a c++ float
        // minimum allowed reduced numerator
        return new int[]{Lnum, Ldenom, fittingMinimum, Integer.toString(finalLdenom).length()-Integer.toString(finalLnum).length(),oneMilli,finalLnum,finalLdenom,-1, minimumMilli};
    }

    /**
     * In case lotro use a float point for representing L
     * We test if a float can handle it.
     */
    public boolean staysWithinSixSignificantDigits(int numerator, int denominator) {
        BigDecimal n = BigDecimal.valueOf(numerator);
        BigDecimal d = BigDecimal.valueOf(denominator);

        try {
            BigDecimal result = n.divide(d,9, RoundingMode.UNNECESSARY);
            return result.precision() <= 6;

        } catch (ArithmeticException e) {
            // infinite significant digits.
            return false;
        }
    }

	private void exportPartToAbc(AbcPart part, PrintStream out,
                                 boolean delayEnabled, PolyphonyHistogram histogram, int minDelay) throws AbcConversionException {
		List<Chord> chords = combineAndQuantize(part, false, histogram);

		StringBuilder outBuilder = exportPartHeaderToAbc(part, null, 0);
        out.print(outBuilder);

		// Keep track of which notes have been sharped or flatted so
		// we can naturalize them the next time they show up.
		boolean[] sharps = new boolean[Note.MAX_PLAYABLE.id + 1];
		boolean[] flats = new boolean[Note.MAX_PLAYABLE.id + 1];

		// Write out ABC notation
		final int BAR_LENGTH = 160;
		final long songStartMicros = qtm.tickToMicros(exportStartTick);
		final int primaryExportTempoBPM = qtm.getPrimaryExportTempoBPM();
        long minimumMicro = AbcConstants.getShortestNoteMicros(primaryExportTempoBPM);
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

            while (length > 0 && Character.isWhitespace(bar.charAt(length - 1)))
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

        long countInMicros = 0L;//all non-count-in track notes will be delayed by this
        CountIn countIn = null;
        if (part.getAbcSong().getCountIn() != null && part.getAbcSong().getCountIn().pattern != CountIn.CountInPattern.OFF) {
            countIn = part.getAbcSong().getCountIn();
            countInMicros = calculateCountInTotalMicrosABC(countIn, qtm);
            if (countInMicros > AbcConstants.LONGEST_COUNT_IN_MICROS) {
                countInMicros = 0;
                countIn = null;
                logAbc.warning("Count-in for ABC: count-in longer than 12 seconds, cancelling count-in.");
                ProjectFrame.feed(UIText.get("maestro.warning.count.in.cancelled.it.s.too.long"), UIText.get("maestro.reduce.to.at.under.12.seconds"));
            } else {
                logAbc.info("Count-in for ABC: total count-in. micros = " + countInMicros + " bars = " + countIn.barCount);
            }
        }

        long L = (qtm.getMeter().numerator / (double) qtm.getMeter().denominator) < 0.75d ? 16L : 8L;

        // One whole abc note is this many microseconds:
        int oneMicro = (int)(qtm.getMeter().denominator * TimingInfo.ONE_SECOND_MICROS * 60L / (qtm.getPrimaryExportTempoBPM() * L));

		if (delayEnabled || countIn != null) {

            long hitMicros = 0L;

            if (countIn != null && countIn.part == part) {
                int hits = countIn.pattern.dynamics.length;
                hitMicros = countInMicros / hits;
                countInMicros = 0L;
                if (hitMicros < minimumMicro) {
                    countIn = null;//cancel, since count-in is too short
                    logAbc.warning("Count-in for ABC: hitMicros shorter than 60 ms, cancelling count-in.");
                    ProjectFrame.feed(UIText.get("maestro.warning.count.in.cancelled.it.s.too.short"), UIText.get("maestro.expand.so.each.drum.hit.is.more.than.60.ms.apart"));
                }
            } else if (countIn != null) {
                int hits = countIn.pattern.dynamics.length;
                hitMicros = countInMicros / hits;
                if (hitMicros < minimumMicro) {
                    countInMicros = 0L;
                    countIn = null;
                }
            }
			


			// the 100 is so the delay is always larger than 60 ms, even if its 0 ms.
			long delayMicro = (part.getDelay()+100L-minDelay)*1000L + countInMicros;
            //logAbc.warning(part.getPartNumber()+" "+part.getInstrument()+"delayMicro "+delayMicro+" = ("+part.getDelay()+"+100+"+(-minDelay)+")*1000+"+countInMicros);
			// Reduce the fraction
			//int gcd = Util.gcd(delayMicro, oneMicro);
			//delayMicro /= gcd;
			//int oneMicro2 = oneMicro / gcd;

            final long MAX_REST_MICROS = 7 * AbcConstants.ONE_SECOND_MICROS;
            long parts = (delayMicro + MAX_REST_MICROS - 1) / MAX_REST_MICROS;   // ceil division
            if (parts < 1) parts = 1;
            long base = delayMicro / parts;        // each piece ~equal, all well above the 60ms floor
            long remainder = delayMicro % parts;   // distribute the leftover micros

            for (int i = 0; i < parts; i++) {
                long rest = base + (i < remainder ? 1L : 0L);   // spread remainder 1 micro at a time
                out.print("z" + rest + "/" + oneMicro);
                out.print(" ");
            }
            out.println("| ");

            if (countIn != null && countIn.part == part) {
                /*
                 Count-in on songs where the first note is delayed,
                 will ignore the delay and count-in to the first note as it were without delay.
                 If it's the count-in drum itself that are delayed,
                 count-in will also be delayed.
                 */

                logAbc.info("Count-in for ABC: hitMicros: "+hitMicros);
                for (CountIn.CountInDynamics dyn : countIn.pattern.dynamics) {
                    Dynamics volume = dyn.dynamics;
                    bar.append('+').append(volume).append("+ ");
                    bar.append(countIn.hit.note.abc);
                    bar.append(hitMicros).append("/").append(oneMicro).append(" |");

                    logAbc.info("Count-in for ABC: added a count-in hit: "+countIn.hit.name+" velocity = "+volume.midiVol);
                }
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
				if (!bar.isEmpty()) {
					addLineBreaks.run();
					out.print(bar);
					out.println(" |");
					bar.setLength(0);
				}

				curBarNumber = barNumber;

				int exportBarNumber = curBarNumber - firstBarNumber;
				if (!reducedFilesize && (exportBarNumber + 1) % 10 == 0) {
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
				if (!bar.isEmpty()) {
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
						logAbc.severe("Zero length Error: ticks=" + evt.getLengthTicks() + " micros="
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
        if (part.getInstrument() == LotroInstrument.BASIC_BAGPIPE) {
            // Attempt to fix drone-bug (aka. horn bug)
            out.print(" x500000/"+oneMicro);
        }
		out.println(" |]");
		out.println();
	}

    private long calculateCountInTotalMicrosABC(CountIn countIn, QuantizedTimingInfo qtm) {
        TimingInfoEvent info;
        if (organic) {
            info = qtm.getTimingEventForTickOrganic(startTickForCountIn);
        } else {
            info = qtm.getTimingEventForTick(startTickForCountIn);
        }
        long countInTicks = (long)(countIn.barCount*(double)info.info().getBarLengthTicks());//can overflow at extreme lengths, so casting to double
        long countInMicros = MidiUtils.ticks2microsec(countInTicks, info.info().getTempoMPQ(), info.info().getResolutionPPQ());
        countInMicros = qtm.divideByExportTempoFactor(countInMicros);
        return countInMicros;
    }

    private StringBuilder exportPartHeaderToAbc(AbcPart part, int[] quanFractions, int oneNoteIs) {
        StringBuilder out = new StringBuilder();
        out.append("\n");
		out.append("X: " + part.getPartNumber()).append("\n");
		if (metadata != null) {
			out.append("T: " + StringCleaner.cleanForABC(metadata.getPartName(part))).append("\n");
		} else {
			out.append("T: " + StringCleaner.cleanForABC(part.getTitle())).append("\n");
		}

		out.append(AbcField.PART_NAME + StringCleaner.cleanForABC(part.getTitle())).append("\n");

		// Since people might not use the instrument-name when they name a part,
		// we add this so can choose the right instrument in abcPlayer and maestro when
		// loading abc.
		out.append(AbcField.MADE_FOR + part.getInstrument().friendlyName.trim()).append("\n");
        if (part.getUserPan() != null) out.append(AbcField.USER_PAN + part.getUserPan().toString()).append("\n");
        else out.append(AbcField.USER_PAN + "auto").append("\n");

        /*
        if (organic) {
            out.append("%% 1 note is approx "+oneNoteIs+" μs").append("\n");
        }
        */

		if (metadata != null) {
            // We output these even with reduced file size enabled.
            // They are redundant as this info is in the main file header
            // Is really just needed when outputting each part to its own file.
            // But songbook indexers use them
			if (!metadata.getComposer().isEmpty())
				out.append("C: " + StringCleaner.cleanForABC(metadata.getComposer())).append("\n");

			if (!metadata.getTranscriber().isEmpty())
				out.append("Z: " + StringCleaner.cleanForABC(metadata.getTranscriber())).append("\n");
		}

		out.append("M: " + qtm.getMeter()).append("\n");
		out.append("Q: " + qtm.getPrimaryExportTempoBPM()).append("\n");
		out.append("K: " + keySignature).append("\n");
		if (organic) {
            out.append("L: " + quanFractions[5]+"/"+quanFractions[6]).append("\n");
        } else {
            out.append("L: " + ((qtm.getMeter().numerator / (double) qtm.getMeter().denominator) < 0.75d ? "1/16" : "1/8"));
            out.append("\n");
        }
		out.append("\n");
        return out;
	}

	/**
	 * Combine the tracks into one, quantize the note lengths, separate into chords.
	 */
	private List<Chord> combineAndQuantize(AbcPart part, boolean preview, PolyphonyHistogram histogram) throws AbcConversionException {
        part.numberOfRemovedNotesFromPruning = 0;
		// Combine the events from the enabled tracks
		List<AbcNoteEvent> events = new ArrayList<>();
		for (int t = 0; t < part.getTrackCount(); t++) {
			if (part.isTrackEnabled(t)) {
				List<MidiNoteEvent> listOfNotes = expandXtraDrumNotes(part, t);
				
				applyLegato(part, t, listOfNotes);

				for (MidiNoteEvent ne : listOfNotes) {
					// Skip notes that are outside the play range.
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
				histogram.count(part, new ArrayList<>(), organic, qtm);
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
		
		if (events.isEmpty()) {
			logNotes.warning("Export to preview/abc: "+metadata.getSongTitle()+" has a part with no exported notes.");
			if (!preview && !(part.getAbcSong().getCountIn() != null && part.getAbcSong().getCountIn().part == part)) {
                ProjectFrame.feed(UIText.get("maestro.note.song.has.a.part.with.no.exported.notes.0", part.getTitle()), null);
            }
			return new ArrayList<>();
		}
		
		// Add initial rest if necessary
		
		if (events.getFirst().getStartTick() > exportStartTick) {
			events.addFirst(new AbcNoteEvent(Note.REST, Dynamics.DEFAULT.midiVol, exportStartTick,
					events.getFirst().getStartTick(), qtm, null));
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
		Chord curChord = new Chord(events.getFirst());
		chords.add(curChord);
        int prunedNotes = 0;
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
                prunedNotes += deadnotes.size();
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
            prunedNotes += deadnotes.size();
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
				histogram.count(part, chords, organic, qtm);
			} catch (IOException e) {
				throw new AbcConversionException("Failed to read instrument sample durations.", e);
			}
		}
        part.numberOfRemovedNotesFromPruning = prunedNotes;
		return chords;
	}

	private void applyFermata(AbcPart part, List<AbcNoteEvent> events) {
		final long margin = 5000L;//5 ms
		if (part.conclusionFermata != 0) {
			long finalNoteTickEnd = 0L;
			List<AbcNoteEvent> conclusion = new ArrayList<>();
            for (AbcNoteEvent ne : events) {
                if (ne.getEndTick() > finalNoteTickEnd) {
                    finalNoteTickEnd = ne.getEndTick();
                    List<AbcNoteEvent> conclusionRemove = new ArrayList<>();
                    if (organic) {
                        long concludeMicros = qtm.tickToMicrosABCOrganic(finalNoteTickEnd);
                        for (AbcNoteEvent potential : conclusion) {
                            if (qtm.tickToMicrosABCOrganic(potential.getEndTick()) + margin < concludeMicros) {
                                conclusionRemove.add(potential);
                            }
                        }
                    } else {
                        long concludeMicros = qtm.tickToMicrosABC(finalNoteTickEnd, part);
                        for (AbcNoteEvent potential : conclusion) {
                            if (qtm.tickToMicrosABC(potential.getEndTick(), part) + margin < concludeMicros) {
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
            for (AbcNoteEvent ne : conclusion) {
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
		// if the silence between notes is smaller than 1 second (in orig midi tempo),
		// then extend note endings to bridge the gap.
		if (part.getInstrument().sustainable) {
			long lastTick = 0L;
			for (int curr = 0; curr < listOfNotes.size(); curr++) {
				MidiNoteEvent currNe = listOfNotes.get(curr);
				if (currNe.getEndTick() > lastTick) lastTick = currNe.getEndTick();
				if (!part.getSectionLegato(trackNumber, currNe.getStartTick()) || lastTick > currNe.getEndTick()) {
					currNe.setLegatoEndTick(part, null);
					continue;
				}
				long currEndTick = currNe.getEndTick();
				long nextEndTick = currEndTick;
				long currEndMicro;
				if (organic) {
					currEndMicro = qtm.tickToMicrosOrganic(currEndTick);
				} else {
					currEndMicro = qtm.tickToMicros(currEndTick);
				}
				// Now find where next note event starts
				for (int next = curr+1; next < listOfNotes.size(); next++) {
					MidiNoteEvent nextNe = listOfNotes.get(next);
					if (nextNe.getStartTick() <= nextEndTick && nextNe.getEndTick() > currEndTick) {
						break;
					}
					if (nextNe.getStartTick() > nextEndTick) {
						nextEndTick = nextNe.getStartTick();
						break;
					}
				}
				if (nextEndTick > currEndTick) {
					long nextEndMicro;
					if (organic) {
						nextEndMicro = qtm.tickToMicrosOrganic(nextEndTick);
					} else {
						nextEndMicro = qtm.tickToMicros(nextEndTick);
					}
					if (nextEndMicro - currEndMicro < AbcConstants.ONE_SECOND_MICROS) {
						currNe.setLegatoEndTick(part, nextEndTick);
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
        DrumNoteMap dm = part.getDrumMap(trackNumber);

        boolean specialDrumNotes = false;
        if (part.getInstrument() == LotroInstrument.BASIC_DRUM) {
            TrackInfo tInfo = part.getAbcSong().getSequenceInfo().getTrackInfo(trackNumber);
            for (int inNo : tInfo.getNotesInUse()) {
                byte outNo = dm.get(inNo);
                if (dm.isCombiNote(outNo)) {
                    specialDrumNotes = true;
                    break;
                }
            }
        }
        List<MidiNoteEvent> listOfNotes = new ArrayList<>(part.getTrackEvents(trackNumber));
        if (!specialDrumNotes) return listOfNotes;

        List<MidiNoteEvent> extraList = new ArrayList<>();
        List<MidiNoteEvent> removeList = new ArrayList<>();
        for (MidiNoteEvent ne : listOfNotes) {
            Note mapped = part.mapNote(trackNumber, ne.note.id, ne.getStartTick());
            if (mapped == null) continue;

            LotroCombiDrumInfo.CombiDrumHit c = dm.resolveCombi(mapped.id);
            if (c != null) {
                extraList.add(makeHit(ne, c.firstNote(),  ne.midiPan));
                extraList.add(makeHit(ne, c.secondNote(), ne.midiPan));
                removeList.add(ne);
                // Notice that bent notes on chromatic tracks are treated as only 1 note here
            }
        }
        listOfNotes.removeAll(removeList);
        listOfNotes.addAll(extraList);

		return listOfNotes;
	}

    private static MidiNoteEvent makeHit(MidiNoteEvent ne, Note n, int pan) {
        MidiNoteEvent e = new MidiNoteEvent(n, ne.velocity, ne.getStartTick(), ne.getEndTick(), ne.getTempoCache(), pan);
        e.alreadyMapped = true;
        return e;
    }
	
	/**
	 * Combine the tracks into one, separate into chords.
	 */
	private Pair<List<Chord>, Boolean> combineOrganic(AbcPart part, boolean preview, PolyphonyHistogram histogram, int quanFractions[]) throws AbcConversionException {
        part.numberOfRemovedNotesForSafety = 0;
		// Combine the events from the enabled tracks
		List<AbcNoteEvent> events = new ArrayList<>();
		for (int t = 0; t < part.getTrackCount(); t++) {
			if (part.isTrackEnabled(t)) {
				List<MidiNoteEvent> listOfNotes = expandXtraDrumNotes(part, t);
				
				applyLegato(part, t, listOfNotes);
				
				for (MidiNoteEvent ne : listOfNotes) {
					// Skip notes that are outside the play range.
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
				histogram.count(part, new ArrayList<>(), organic, qtm);
			} catch (IOException e) {
				throw new AbcConversionException("Failed to read instrument sample durations.", e);
			}
			return new Pair<>(Collections.emptyList(), false);
		}

		Collections.sort(events);
		
		applyFermata(part, events);

		// subdivide bent notes
		long lastEnding = 0;
		List<AbcNoteEvent> extraEvents = new ArrayList<>();
		List<AbcNoteEvent> deleteEvents = new ArrayList<>();
		
		for (int cc = 0; cc < events.size() ; cc++) {
			AbcNoteEvent ne = events.get(cc);
			assert ne.note != Note.REST : "Rest detected!";
			if (cc == events.size()-1) {
				logNotes.finer(part.getTitle()+": ends at micro "+qtm.tickToMicrosABCOrganic(ne.getEndTick())+" (before subdividing bends)");
			}
			long oldStart = ne.getStartTick();
			long oldEnd = ne.getEndTick();
			
			if (deleteEmptyNotes && oldStart == oldEnd) {
				deleteEvents.add(ne);
				continue;
			}
			

			List<AbcNoteEvent> bentNotes = expandPitchBendsOrganicImproved(ne);
			
			if (bentNotes != null) {
				assert !bentNotes.contains(ne);
				deleteEvents.add(ne);
				for (AbcNoteEvent bent : bentNotes) {
					assert bent.note != Note.REST;
					if (bent.getEndTick() > lastEnding) {
						lastEnding = bent.getEndTick();
					}
				}
				extraEvents.addAll(bentNotes);
			} else {
				if (ne.getEndTick() > lastEnding) {
					lastEnding = ne.getEndTick();
				}
			}
		}

		events.addAll(extraEvents);// add all the pitchbend fractions to the main event list
		events.removeAll(deleteEvents);
		//System.out.println("Something removed: "+deleteEvents.size());
		//System.out.println("Something added: "+extraEvents.size());
		
		Collections.sort(events);
		
		if (events.isEmpty()) {
			logNotes.warning("Export to preview/abc: "+metadata.getSongTitle()+" has a part with no exported notes.");
			if (!preview && !(part.getAbcSong().getCountIn() != null && part.getAbcSong().getCountIn().part == part)) {
                ProjectFrame.feed(UIText.get("maestro.note.song.has.a.part.with.no.exported.notes.0", part.getTitle()), null);
            }
			return new Pair<>(Collections.emptyList(), false);
		}
		
		// Add initial rest if necessary
		
		if (events.getFirst().getStartTick() > exportStartTick) {
			events.addFirst(new AbcNoteEvent(Note.REST, Dynamics.DEFAULT.midiVol, exportStartTick,
					events.getFirst().getStartTick(), qtm, null));
		}

		// Remove duplicate notes
		removeDuplicateNotes(events, part.getInstrument());
		
		Collections.sort(events);// needed due to removeDuplicateNotes adding thirds
		
		/*
		// Verify duplicates does not exist
		removeDuplicateNotesVerify(events, part.getInstrument());
		*/
		
		boolean useRestToShortenChords = part.getInstrument().sustainable && useRestsInChords;
		
		List<Chord> chords = null;

		List<AbcNoteEvent> eventsCopy = new ArrayList<>();
		for (AbcNoteEvent n : events) {
			eventsCopy.add(n.copy());
		}
		if (organic2) chords = processOrganic2(part, events, useRestToShortenChords, quanFractions);
		else chords = processOrganic(part, events, useRestToShortenChords, quanFractions);
		if (useRestToShortenChords) {
			int max = 0;
			try {
				max = histogram.maxPolyInPart(part, chords, organic, qtm);
			} catch (IOException e) {
				throw new AbcConversionException("Failed to read instrument sample durations.", e);
			}
			if (max == 6) {
				logNotes.info(" ---- "+part.getAbcSong().getTitle()+" ("+part.getTitle()+"): poly restore");
				useRestToShortenChords = false;
				if (organic2) chords = processOrganic2(part, eventsCopy, useRestToShortenChords, quanFractions);
				else  chords = processOrganic(part, eventsCopy, useRestToShortenChords, quanFractions);
				part.setMaxPoly(6);
			} else if (max > 6) {
				part.setMaxPoly(max);
			} else {
				logNotes.finer(" pass "+part.getAbcSong().getTitle()+" ("+part.getTitle()+"): poly okay");
				part.setMaxPoly(max);
			}
		} else {
			//System.out.println(" pass "+part.getAbcSong().getTitle()+" ("+part.getTitle()+"): poly off");
			part.setMaxPoly(6);
		}
		
		if (preview) {
			try {
				histogram.count(part, chords, organic, qtm);
			} catch (IOException e) {
				throw new AbcConversionException("Failed to read instrument sample durations.", e);
			}
		}
		
		//Collections.sort(chords);
		
		return new Pair<>(chords, useRestToShortenChords);
	}	

	/**
	 * process the notes using single-stage organic output
	 */
	private List<Chord> processOrganic(AbcPart part, List<AbcNoteEvent> events, boolean useRestToShortenChords, int[] quanFractions) {
		boolean assertionsEnabled = false;
		assert assertionsEnabled = true;

        final long LONGEST_NOTE_SOFT_BUFFER_MICROS = 70000L;
        final long softMaxDurationMicros = LotroInstrumentSampleDuration.getSafeDuration(part.getInstrument());

        part.numberOfRemovedNotesFromPruning = 0;
        part.numberOfRemovedNotesFromFitting = 0;
        part.numberOfRemovedNotesZeros = 0;

        for (AbcNoteEvent note : events) {
            note.startABCMicros = qtm.tickToMicrosABCOrganic(note.getStartTick());
            note.endABCMicros = qtm.tickToMicrosABCOrganic(note.getEndTick());
        }
        final long songStartMicros = getExportStartMicrosABC();

		breakLongNotesOrganic(part, events, softMaxDurationMicros);

		List<ChordOrganic> chords = new ArrayList<>(events.size() / 2);
		List<AbcNoteEvent> tmpEvents = new ArrayList<>();

		long minimumMicros = quanFractions[2];//often slightly above 60 ms
		
		// Combine notes that play at the same time into chords
		
		final boolean removeGliss = false;
		ChordOrganic curChord = new ChordOrganic(events.getFirst(), qtm);
		ChordOrganic prevChord = null;
		ChordOrganic prevRestChord = null;
		logNotes.finest(part.getTitle()+ ": Adding to curChord, note i=0 micros:"+Util.formatDurationM(events.getFirst().startABCMicros)+"-"+Util.formatDurationM(events.getFirst().endABCMicros)+" "+events.getFirst().note);
		chords.add(curChord);
		MAIN:for (int i = 1; i < events.size(); i++) {
			AbcNoteEvent ne = events.get(i);
			if (ne.tiesFrom == ne) continue;// hack
			if (curChord.getStartMicros() == ne.startABCMicros) {
				// This note starts at the same time as the rest of the notes in the chord
				assert !curChord.isRest();
				curChord.add(ne);
				logNotes.finest(part.getTitle()+ ": Adding to curChord note i="+i+" micros:"+Util.formatDurationM(ne.startABCMicros)+"-"+Util.formatDurationM(ne.endABCMicros)+" "+ne.note);
			} else {								
				// The curChord has all the notes it will get.
				
				// Note that ne can be a rest from cut up initial rest
				
				logNotes.finer(part.getTitle()+ ": Processing note i="+i+" micros:"+Util.formatDurationM(ne.startABCMicros)+"-"+Util.formatDurationM(ne.endABCMicros)+" "+ne.note);
				
				// remove zero duration notes if longer notes start at same time in curr chord
				if (curChord.getLongestEndMicros() > curChord.getStartMicros()) {
					for (int j = 0; j < curChord.size(); j++) {
						AbcNoteEvent jne = curChord.get(j);
						if (jne.endABCMicros == jne.startABCMicros) {
							// this note is zero duration and others in the chord is not
							curChord.remove(jne);
                            part.numberOfRemovedNotesZeros++;
							logNotes.finer(part.getTitle()+" Removed zero dura note ("+jne.note.abc+")");
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
					curChord.recalcEndMicros();
				}
				
				if (curChord.early != null) {
					//must be AFTER 'remove zero among longer'
					//is BEFORE pruning to save pruning twice
					curChord.setEarlyStartMicros(useRestToShortenChords);
					if (prevChord != null) prevChord.recalcEndMicros();
					logNotes.finer(part.getTitle()+ ": applying early start. curChord now start at "+Util.formatDurationM(curChord.getStartMicros()));
					i--;
					continue MAIN;
				}
				
				// We prune AFTER removed shorter zero notes, so they dont take up slot from
				// 6 max notes.
				List<AbcNoteEvent> deadnotes = curChord.prune(part.getInstrument().sustainable,
						part.getInstrument() == LotroInstrument.BASIC_DRUM, part.getInstrument().isPercussion,
						part, useRestToShortenChords);
				removeNotes(events, deadnotes, part);
                part.numberOfRemovedNotesFromPruning += deadnotes.size();

				if (!deadnotes.isEmpty()) {
					// One of the tiedTo notes that was pruned might be ne note,
					// so we go one step back and re-process events.get(i)
					i--;
					logNotes.finer(part.getTitle()+ ": something was pruned");
					continue MAIN;
				}
				
				// Create a new chord
				ChordOrganic nextChord = new ChordOrganic(ne, qtm);
				logNotes.finer(part.getTitle()+ ": Create new chord. "+ne.note);
				
				
				// we first identify the two next chords as they will look after being cut up:
				long curChordRoomMicros = ne.startABCMicros - curChord.getStartMicros();
				AbcNoteEvent ne1 = ne;
				AbcNoteEvent ne2 = null;
				long ne2End = Long.MAX_VALUE;
				long ne2Start = Long.MAX_VALUE;
				ChordOrganic nextChordTmp = new ChordOrganic(ne, qtm);
				for (int ii = i+1; ii < events.size(); ii++) {
					// find the shortest non-zero dura notes coming next
					// remember events are sorted not only by start tick, but also end tick
					AbcNoteEvent over = events.get(ii);
					if (ne2 != null && over.startABCMicros > ne2.startABCMicros) {
						break;
					}
					if (over.startABCMicros == ne.startABCMicros && (ne1.endABCMicros - ne1.startABCMicros == 0L || (over.endABCMicros < ne1.endABCMicros && over.endABCMicros - over.startABCMicros != 0L))) {
						// over is shorter than ne1 or ne1 is zero. over starts at same time as ne.
						if (ne1.endABCMicros > over.endABCMicros) {
							ne2Start = over.endABCMicros;
							ne2End = ne1.endABCMicros;
						}
						ne1 = over;
						nextChordTmp.add(over);
					} else if (over.startABCMicros == ne.startABCMicros && over.endABCMicros - over.startABCMicros != 0L && ne1.endABCMicros - ne1.startABCMicros != 0L && over.endABCMicros - over.startABCMicros > ne1.endABCMicros - ne1.startABCMicros) {
						// over is longer than ne1 and neither is zero. over starts at same time as ne.
						// this means over is going to be cut up, so ne2 will become ending of over.
						ne2Start = ne1.endABCMicros;
						ne2End = over.endABCMicros;
						nextChordTmp.add(over);
					} else if (over.startABCMicros == ne.startABCMicros) {
						nextChordTmp.add(over);
					}
					if (over.startABCMicros > ne.startABCMicros && (ne2 == null || ne2.endABCMicros - ne2.startABCMicros == 0L)) {
						// over starts after ne.
						ne2 = over;
						if (ne2.startABCMicros < ne2Start) {
							ne2Start = over.startABCMicros;
							ne2End = over.endABCMicros;
						}
					}
				}
				int nextValue = calcValue(nextChordTmp, part.getInstrument().sustainable);
				// ne1 now represent the first chord, it might be longer than ne if ne is zero dura.
				if ((ne2 != null && ne2.startABCMicros > ne2Start) || (ne2 == null && ne2Start < Long.MAX_VALUE)) {
					ne2 = new AbcNoteEvent(Note.A0, 64, qtm.microsToTickABCOrganic(ne2Start), qtm.microsToTickABCOrganic(ne2End), qtm, ne1.origNote);
					ne2.startABCMicros = ne2Start;
					ne2.endABCMicros = ne2End;
					ne2.setStartTick(qtm.microsToTickABCOrganic(ne2Start));
					ne2.setEndTick(qtm.microsToTickABCOrganic(ne2End));
				}
				// ne2 now represent the second chord
				long ne1RoomMicros = ne2 == null?Long.MAX_VALUE:ne2.startABCMicros - ne.startABCMicros;
				long neMicros = ne.endABCMicros - ne.startABCMicros;
				long ne1Micros = ne1.endABCMicros - ne1.startABCMicros;
				long ne2Micros = ne2 == null?0L:ne2.endABCMicros - ne2.startABCMicros;
				
				// handle fast glissando
				boolean glissRemoved = deprecated1(part, events, minimumMicros, removeGliss, curChord, ne,
						curChordRoomMicros, ne1RoomMicros, ne1Micros, ne2Micros);
				
				if (glissRemoved) {
					logNotes.severe(part.getTitle()+ ": deprecated 1st");
					i--;
					continue MAIN; 
				}
				
				// turn very fast arpeggio into block chord
				if (ne.note != Note.REST
						&& curChordRoomMicros < minimumMicros
						&& (curChord.getEndMicros() > ne.startABCMicros || part.getInstrument().isPercussion)
						&& !curChord.dontMove1 && !curChord.glissando && !curChord.isRest()) {
					// curr end before next start prevents handling grace notes, they will be deleted later if they too short
					for (AbcNoteEvent small : curChord.getNotes()) {
						if (small.tiesTo != null) {
							// curr chord has already been cut up, or broken up due to being long notes, skip it
							i--;
							curChord.dontMove1 = true;// to prevent infinite loop
							logNotes.finer(part.getTitle()+" Keep arpeggio (ties involved)");
							continue MAIN;
						}
					}
					List<AbcNoteEvent> removeFromCur = new ArrayList<>();
					boolean hasTieFrom = ne.tiesFrom != null;
					boolean foundTieFrom = false;
					for (AbcNoteEvent small : curChord.getNotes()) {
						// make sure next chord dont have any notes with same pitch as one from curChord
						/*
						for (int ii = i; ii < events.size(); ii++) {
							AbcNoteEvent next = events.get(ii);
							if (next.startABCMicros > ne.endABCMicros) {
								// no reason to check more notes
								break;
							}
							not sure why I did this for next notes, when
							we are going to come in here again anyway in next
							iteration, if more notes needs this done.
							*/
							AbcNoteEvent next = ne; 
							if (next.startABCMicros == ne.startABCMicros) {
								if (next.note == small.note) {
									logNotes.finer("Removing small note from curChord.");
									removeFromCur.add(small);
									if (next.tiesFrom == small) {
										foundTieFrom = true;
										next.tiesFrom = small.tiesFrom;
										next.tiesFrom.tiesTo = next;
									} else if (small.tiesFrom != null) {
										small.tiesFrom.tiesTo = null;
										small.tiesFrom = null;
									}
								}
							}
						//}
					}
					for(AbcNoteEvent small : removeFromCur) {
						curChord.remove(small);
						events.remove(small);
						i--;
					}
					if (useRestToShortenChords && hasTieFrom && !foundTieFrom) {
						// As we move ne into curChord, ne.tiesFrom is shortened so they don't overlap
						// ne.tiesFrom is not part of curChord
						ne.tiesFrom.endABCMicros = curChord.getStartMicros();
						ne.tiesFrom.setEndTick(qtm.microsToTickABCOrganic(curChord.getStartMicros()));
					}
					// Its too complex to move current chord into next cords position, so we do the opposite:					
					logNotes.finer(part.getTitle()+" Turned arpeggio into block chord (early start)");
					ne.startABCMicros = curChord.getStartMicros();
					ne.setStartTick(qtm.microsToTickABCOrganic(ne.startABCMicros));
					curChord.add(ne);// we note that this will later be pruned (again)
					curChord.arp += 1;
					curChord.recalcEndMicros();
					continue MAIN;
				} else {
					logNotes.finer("Not arp. curChord.dontMove1="+curChord.dontMove1+". curChord.isRest="+curChord.isRest()+", curChordRoomMicros<minimumMicros="+(curChordRoomMicros < minimumMicros)+", overlap="+(curChord.getEndMicros() > ne.startABCMicros));
				}
				
				long shortest = curChord.getEndMicros() - curChord.getStartMicros();
				long space = ne.startABCMicros - curChord.getStartMicros();
				long minEndMicros = curChord.getStartMicros() + minimumMicros;
				if (shortest < minimumMicros && space >= minimumMicros && ne.startABCMicros >= minEndMicros) {
					// one or more notes in curChord is too short, but they have room to expand
					curChord.setEndMicrosExpand(minEndMicros);
					logNotes.finer(part.getTitle()+ ": Expanded");
				}
				
				
				// cut up curChord if some notes longer than others
				boolean reprocessCurrentNote = false;
				long curEndMicro = curChord.getEndMicros();
				long curStartMicro = curChord.getStartMicros();
				long cutTarget = Math.min(curEndMicro, ne.startABCMicros);
				
				long curMinEndFitMicros = Math.min(minEndMicros, cutTarget);
				if (!curChord.glissando) {
					for (int j = 0; j < curChord.size(); j++) {
						AbcNoteEvent jne = curChord.get(j);
						logNotes.finer(jne.note+" is on cutting table "
									+Util.formatDurationM(jne.startABCMicros)+" - "+Util.formatDurationM(jne.endABCMicros)
									+". curEndMicros="+Util.formatDurationM(curEndMicro)+" cutTarget="+Util.formatDurationM(cutTarget)+" curMinEndFitMicros="+Util.formatDurationM(curMinEndFitMicros));
						if (!part.getInstrument().sustainable) {
							// This might be a bit controversial
							// But here we fix the duration on the chord to minimum or shorter,
							// since instrument is not sustainable anyway.
							// Controversial due to you can't later experiment by putting
							// a sustained instrument on this part, it will be ruined for that purpose.
							// But this will make fitting it all together easier.
							jne.endABCMicros = curMinEndFitMicros;
							jne.setEndTick(qtm.microsToTickABCOrganic(jne.endABCMicros));
							logNotes.finer(jne.note+" curMinEndFitMicros="+curMinEndFitMicros+" tiesTo="+(jne.tiesTo!=null));
							if (jne.tiesTo != null) {
								AbcNoteEvent tie = jne;
								while(tie.tiesTo != null) {
									events.remove(tie.tiesTo);
									if (tie.tiesTo == ne) {
										reprocessCurrentNote = true;
									}
									AbcNoteEvent old = tie;
									tie = tie.tiesTo;
									old.tiesTo = null;
								}
							}
						} else if (!useRestToShortenChords && jne.endABCMicros > cutTarget) {
							long noteEndMicro = jne.endABCMicros;
							if (noteEndMicro-cutTarget < minimumMicros/2 && jne.tiesTo == null) {
								// note ends approx same time as cutTarget
								// we make it end same time as cutTarget,
								// chord might become slightly longer later.
								jne.endABCMicros = cutTarget;
								jne.setEndTick(qtm.microsToTickABCOrganic(jne.endABCMicros));
								logNotes.finer(part.getTitle()+ ": Fit note ending to cut target. tiesTo="+(jne.tiesTo!=null));
							} else {
								// This note extends past the end of the chord; break it into two tied notes
								AbcNoteEvent next = jne.splitWithTieAtTick(qtm.microsToTickABCOrganic(cutTarget), cutTarget);
								
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
				curChord.recalcEndMicros();
				if (reprocessCurrentNote) {
					i--;
					logNotes.finest(part.getTitle()+ ": Chord was cut up, reprocessing..");
					continue MAIN;
				}
				
				// Insert a rest into current chord if need to shorten chord
				if (useRestToShortenChords && !curChord.hadRestAndNotes()
						&& curChord.getEndMicros() > nextChord.getStartMicros()) {// && curChord.getEndMicros() > targetEndMicros
					// The reason we only do this for sustainable is they benefit from this only,
					// and adding a rest do limit the same time starting notes to 5.
					// As long as the shortest is longer than next start we add a rest
					// This is due to pruning might result in longer chord later,
					// So we force a short chord by putting in a rest.
					// If there is notes same dura or shorter as the rest we insert,
					// and they don't get pruned, the rest itself will get pruned, to not bloat.
					tmpEvents.clear();
					long endMicro = Math.max(minEndMicros, nextChord.getStartMicros());
					AbcNoteEvent shortRest = new AbcNoteEvent(Note.REST, Dynamics.DEFAULT.midiVol,qtm.microsToTickABCOrganic(curChord.getStartMicros()),qtm.microsToTickABCOrganic(endMicro), qtm, null);
					shortRest.startABCMicros = curChord.getStartMicros();
					shortRest.endABCMicros = endMicro;
					tmpEvents.add(shortRest);
					breakLongNotesOrganic(part, tmpEvents, softMaxDurationMicros);
					if (!tmpEvents.isEmpty()) {
						// If rest needed to be broken up, we just keep the first segment
						// we wont get in here again due to condition for hadRestAndNotes()
						int ins = Collections.binarySearch(events, tmpEvents.getFirst());
						if (ins < 0)
							ins = -ins - 1;
						
						assert (ins <= i);
						
						// back up and process again
						reprocessCurrentNote = true;
						curChord.add(tmpEvents.getFirst());
						events.add(ins, tmpEvents.getFirst());

						if (curChord.size() > 6) {
							// uncommon, less than 10 songs out of 1000 had this happen 
							logNotes.finer(part.getAbcSong().getSongTitle()+": 6 note chord had rest added !!!!!!!!!!");
						}
					}
					logNotes.fine(part.getTitle()+ ": Inserted a rest into current chord to make it shorter newEndMicros="
								+ Util.formatDurationM(Math.max(minEndMicros, nextChord.getStartMicros())));
				}
				curChord.recalcEndMicros();
				if (reprocessCurrentNote) {
					//i--;
					logNotes.finest(part.getTitle()+ ": curChord was shortened using rests, reprocessing..");
					continue MAIN;
				}
				
				// Expand into gap to next chord if the gap is smaller than 0.06s
				long oldCurEndMicro = curChord.getEndMicros();
				if (curChord.getEndMicros() < nextChord.getStartMicros()) {
					long restMicros = nextChord.getStartMicros() - oldCurEndMicro;
					if (restMicros <= minimumMicros && curChord.expandedMicros == null) {
						curChord.setEndMicrosExpand(nextChord.getStartMicros());//TODO: breakup elongated notes
						
						// later we might undo some of this; expandedMicros is how much we are allowed to undo.
						curChord.expandedMicros = Math.min((oldCurEndMicro-curStartMicro)-minimumMicros, restMicros);
						if (curChord.expandedMicros <= 0L) curChord.expandedMicros = null;
						
						logNotes.finest(part.getTitle()+ ": Bridged rest");
					}
				}
				
				// Handle curr chord if its shorter than 0.06s
				if (curChord.getEndMicros() < minEndMicros && !curChord.dontMove2) {
					long earlyCurrMicro = curChord.getEndMicros() - minimumMicros;
					logNotes.finer(part.getTitle()+": curChord too short. ends at "+curChord.getEndMicros()+", ideal end at "+minEndMicros);
					// test if we should early start curr chord
					if (!useRestToShortenChords && ne2 != null && ne1RoomMicros < minimumMicros
							&& curStartMicro - earlyCurrMicro < minimumMicros/2) {
						// Both curr and ne does not have enough room.
						// We need less than half of minimum though
						if (prevRestChord != null
								&& earlyCurrMicro - prevRestChord.getStartMicros() > minimumMicros) {
							// There is a rest before curr that can be expanded into
							curChord.early = earlyCurrMicro;//TODO: breakup elongated notes
							curChord.dontMove2 = true;
							logNotes.finer(part.getTitle()+": Early start of 1st of two trills/gliss notes (rest). cur_early="
										+ Util.formatDurationM(earlyCurrMicro)+" cur_start="+Util.formatDurationM(curChord.getStartMicros())
										+ " prev_end="+Util.formatDurationM(prevRestChord.getEndMicros()));
							prevRestChord.setEndMicrosRetract(earlyCurrMicro);
							if (assertionsEnabled) assertSoftDura(prevRestChord, minimumMicros*4/5);
							
							i--;							
							continue MAIN;
						} else if (prevRestChord == null && prevChord != null && prevChord.expandedMicros != null
								&& prevChord.expandedMicros > curStartMicro - earlyCurrMicro) {
							// There is a chord before curr that can be expanded into
							curChord.early = earlyCurrMicro;//TODO: breakup elongated notes
							curChord.dontMove2 = true;
							// any ties will still hold as there will be no gap
							logNotes.finer(part.getTitle()+": Early start of 1st of two trills/gliss notes (chord). cur_early="
										+ Util.formatDurationM(earlyCurrMicro)+" cur_start="+Util.formatDurationM(curChord.getStartMicros())
										+ " prev_end="+Util.formatDurationM(prevChord.getEndMicros()));
							prevChord.setEndMicrosRetract(earlyCurrMicro);
							prevChord.expandedMicros = null;
							if (assertionsEnabled) assertSoftDura(prevChord, minimumMicros*4/5);
							i--;							
							continue MAIN;
						}
					}
					
					// Else try to make it longer					
					if (nextChord.getStartMicros() >= minEndMicros) {
						curChord.setEndMicrosExpand(minEndMicros);
						logNotes.finer(part.getTitle()+ ": trying to expand curChord to end at "+Util.formatDurationM(minEndMicros));
					} else {
						// there was not room for a larger chord
						int curValue = calcValue(curChord, part.getInstrument().sustainable);
						long neMicroStart = ne.startABCMicros;
						if (!curChord.glissando) {
							boolean isRattle = true;
							for (AbcNoteEvent n : curChord.getNotes()) {
								if (!isRattle(part,n)) {
									isRattle = false;
									break;
								}
							}
							if ((ne2 == null || ne1RoomMicros > minimumMicros*2) && ne1.endABCMicros > minEndMicros
									&& (minEndMicros-neMicroStart < minimumMicros/2)) {//  || ne1Micros > minimumMicros*2
								// delay start of next chord up to 30 ms
								long oldStartMicros = ne.startABCMicros;
								for (int ii = i; ii < events.size(); ii++) {
									AbcNoteEvent over = events.get(ii);
									if (over.startABCMicros > oldStartMicros) {
										break;
									}
									if (over.startABCMicros == oldStartMicros) {
										// should be ok to do this even if tiesFrom is non-null
										// since the tiesFrom has been expanded to end here
										if (over.endABCMicros-over.startABCMicros == 0L) {
											over.endABCMicros = minEndMicros;
											over.setEndTick(qtm.microsToTickABCOrganic(minEndMicros));
										}
										over.startABCMicros = minEndMicros;
										over.setStartTick(qtm.microsToTickABCOrganic(minEndMicros));
										
										// TODO: Delaying start of next
									}
								}
								
								//going back and forth between micros and ticks is not always 1:1, so we stop infinite loops by setting this
								curChord.dontMove2 = true;
								curChord.setEndMicrosExpand(minEndMicros);
								
								i--;
								logNotes.finer(part.getTitle()+" Delayed sequential chord by "+ ((minEndMicros-neMicroStart)/1000)+" ms 1");
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
                                part.numberOfRemovedNotesFromFitting++;
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
								
								logNotes.finer(part.getTitle()+": Deleted ne, is second of two trills/gliss notes, dura="+Util.formatDurationM(ne1Micros));
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
											// the next chord will be too short; we remove it
											
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
											logNotes.finer(part.getTitle()+": Removed short dura note just after arpeggio");
											continue MAIN;
										}
										doable = false;
										break;
									}
									
								}
								if (doable) {
									ne.startABCMicros = curChord.getStartMicros();
									ne.setStartTick(qtm.microsToTickABCOrganic(curChord.getStartMicros()));
									curChord.add(ne);// we note that this will later be pruned (again)
									curChord.arp += 1;
									curChord.recalcEndMicros();
									logNotes.finer(part.getTitle()+": Included late arpeggio to block chord");
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
								curChord.recalcEndMicros();
								logNotes.finer(part.getTitle()+": Removed low value next chord");
								//note that this will make next chord even lower value,
								//so rest of next chords notes will also be removed.
								continue MAIN;
							}
							// give up and schedule curr chord for deletion, it likely contains a grace note
							curChord.setEndMicrosRetract(curChord.getStartMicros());
							curChord.delete = true;
                            part.numberOfRemovedNotesFromFitting += curChord.sizeReal();
							logNotes.finer(part.getTitle()+": Removed short dura chord with "+curChord.size()+" notes. "+Util.formatDurationM(curChord.getStartMicros()));
							
						} else {
							// deprecated
							logNotes.severe(part.getTitle()+ ": deprecated!!");
							curChord.setEndMicrosExpand(minEndMicros);
							
							boolean reRun = deprecated2(part, events, minimumMicros, curChord, i, ne, ne1, ne2,
									ne1RoomMicros, ne1Micros, minEndMicros, minEndMicros, neMicroStart);
							
							if (reRun) {
								continue MAIN;
							}
						}
					}
				}
				assertSoftDura(curChord, minimumMicros*99/100);
				
				//System.out.println(curChord.getEndMicros()+" < "+nextChord.getStartMicros());
				
				// Insert a rest between the cur and next if needed
				if (curChord.getEndMicros() < nextChord.getStartMicros()) {
					long restMicros = nextChord.getStartMicros() - curChord.getEndMicros();
					if (restMicros >= minimumMicros) {
						// there is space to make a rest
						tmpEvents.clear();
						AbcNoteEvent rest = new AbcNoteEvent(Note.REST, Dynamics.DEFAULT.midiVol,qtm.microsToTickABCOrganic(curChord.getEndMicros()),
								qtm.microsToTickABCOrganic(nextChord.getStartMicros()), qtm, null);
						tmpEvents.add(rest);
						rest.startABCMicros = curChord.getEndMicros();
						rest.endABCMicros = nextChord.getStartMicros();
						breakLongNotesOrganic(part, tmpEvents, softMaxDurationMicros);
						
						for (AbcNoteEvent restEvent : tmpEvents) {
							ChordOrganic restChord = new ChordOrganic(restEvent, qtm);
							chords.add(restChord);
							prevRestChord = restChord;//break long notes keep them sorted so this is last
							assertSoftDura(restChord, minimumMicros*99/100);
						}
						logNotes.finest(part.getTitle()+ ": add rest: "+curChord.getEndMicros()+" - "+nextChord.getStartMicros());
					} else {
						if (curChord.delete) {
							// If we reach this code, then curr has been scheduled for deletion.
							// Here we can either make next chord start sooner
							// or find the chord before curr and expand that.
							ChordOrganic chordToExpand = null;
							boolean found = false;
							for(int k = chords.size()-1;k>=0;k--) {
								if (!chords.get(k).delete) {
									chordToExpand = chords.get(k);
									found = true;
									break;
								}
							}

							long expandCandidateMicros = found?(chordToExpand.getEndMicros() - chordToExpand.getStartMicros()):0L; 
							if (!found || expandCandidateMicros > AbcConstants.LONGEST_NOTE_MICROS - AbcConstants.ONE_SECOND_MICROS/2 || ne1RoomMicros < minimumMicros) {
								// We make next start sooner, since curChord is first chord or prev is longer than 7.5s
								// this has the added benefit that if next chord is
								// too short too, it will be longer.
								nextChord.early = curChord.getEndMicros();//TODO: breakup elongated notes
								logNotes.fine(part.getTitle()+ ": Early start A");
							} else if (found) {
								chordToExpand.setEndMicrosExpand(ne.startABCMicros);//TODO: breakup elongated notes
								logNotes.fine(part.getTitle()+ ": Prev ("+chordToExpand.getStartMicros()+") expanded to "+ne.startABCMicros+" isRest="+chordToExpand.isRest()+" isDeleted="+chordToExpand.delete);
								//curChord = chordToExpand;
							} else {
								nextChord.early = curChord.getEndMicros();//TODO: breakup elongated notes
								logNotes.fine(part.getTitle()+ ": Early start B");
							}
							curChord = chordToExpand;
						} else {
							curChord.setEndMicrosExpand(ne.startABCMicros);//TODO: breakup elongated notes
							logNotes.finest(part.getTitle()+ ": Chord expanded to fill gap");
						}
						prevRestChord = null;
						
					}
				} else {
					prevRestChord = null;
					
					if (useRestToShortenChords) {
						if (curChord.getEndMicros() > nextChord.getStartMicros()) {
							ne.startABCMicros = curChord.getStartMicros();
							ne.setStartTick(qtm.microsToTickABCOrganic(curChord.getStartMicros()));
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
                                part.numberOfRemovedNotesFromFitting++;
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
								ne.tiesFrom.endABCMicros = ne.startABCMicros;
								ne.tiesFrom.setEndTick(qtm.microsToTickABCOrganic(ne.startABCMicros));
								logNotes.finer(part.getTitle()+": Adjusting tiesFrom endMicros while shuffling ne into curr");
							}
							logNotes.fine(part.getTitle()+": Shuffle ne into curr");
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
						long mics = evt.endABCMicros - evt.startABCMicros;
						assert mics <= softMaxDurationMicros + LONGEST_NOTE_SOFT_BUFFER_MICROS: evt.note+" micros="+mics;
					}
					long endCur = curChord.getEndMicros();
					curChord.recalcEndMicros();
					assert endCur == curChord.getEndMicros():"endMicros was not synced with chord content in "+curChord.toStringDuraMicros();
					
					/*
					 * realCurChord is the last non-deleted chord in chords.
					 */
					ChordOrganic realCurChord = null;
					boolean found = false;
					for(int k = chords.size()-1; k >= 0; k--) {
						if (!chords.get(k).delete) {
							realCurChord = chords.get(k);
							found = true;
							break;
						}
					}
					endCur = found?realCurChord.getEndMicros():nextChord.getStartMicros();
					assert endCur == nextChord.getStartMicros() || (nextChord.early != null && endCur == nextChord.early):"chords not aligned. "+Util.formatDurationM(endCur)+" != "+Util.formatDurationM(nextChord.getStartMicros())+" next_early="+nextChord.early;
					
					/*
					// debug code to investigate specific chord gaps
					if (!useRestToShortenChords && curChord.getEndMicros() == 92927) {
						System.out.println("\nCh: "+curChord.toStringDura()+" useRest="+useRestToShortenChords+" delete="+curChord.delete);
						
						for (AbcNoteEvent n : curChord.getNotes()) {
							System.out.println(n.note+": "+n.startABCMicros+" - "+n.endABCMicros+" tiesFrom="+(n.tiesFrom != null));
						}
						if (chords.size()>1) {
							System.out.println("\npre: "+chords.get(chords.size()-2).toStringDura()+" delete="+curChord.delete);
							for (AbcNoteEvent n : chords.get(chords.size()-2).getNotes()) {
								System.out.println(n.note+": "+n.startABCMicros+" - "+n.endABCMicros+" tiesTo="+(n.tiesTo != null));
							}
							if (chords.size()>2) {
								System.out.println("\npre-: "+chords.get(chords.size()-3).toStringDura()+" delete="+curChord.delete);
								for (AbcNoteEvent n : chords.get(chords.size()-3).getNotes()) {
									System.out.println(n.note+": "+n.startABCMicros+" - "+n.endABCMicros);
								}
							}
						}
						//assert chords.get(chords.size()-2).endABCMicros == curChord.getStartMicros();
						//assert false:"stopped";
					}
					*/
					
					assertSoftDura(curChord, minimumMicros*99/100);
					if (prevChord != null) assertSoftDura(prevChord, minimumMicros*99/100); 
					if (prevRestChord != null) assertSoftDura(prevRestChord, minimumMicros*99/100);
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
		
		for (ChordOrganic chord : chords) {
			if (assertionsEnabled && chord != curChord) assertSoftDura(chord, minimumMicros*99/100);
		}

		boolean reprocessLastChord = true;

		while (reprocessLastChord) {
			
			logNotes.fine("Last chord processing..");
			
			// The last Chord has all the notes it will get. But before continuing,
			// normalize the chord so that all notes end at the same time
			if (curChord.early != null) {
				curChord.setEarlyStartMicros(useRestToShortenChords);
				if (prevChord != null) prevChord.recalcEndMicros();
				logNotes.fine("Last chord: early start");
			}
			
			
			// remove zero duration notes if longer notes start at same time
			if (curChord.getLongestEndMicros() > curChord.getStartMicros()) {
				for (int j = 0; j < curChord.size(); j++) {
					AbcNoteEvent jne = curChord.get(j);
					if (jne.endABCMicros == jne.startABCMicros) {
						// this note is zero duration and others in the chord is not
						curChord.remove(jne);
                        part.numberOfRemovedNotesZeros++;
						logNotes.finest("Last chord: remove a zero dura note");
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
				curChord.recalcEndMicros();
			}
			
			
			// Last chord needs to be pruned as that hasn't happened yet.
			List<AbcNoteEvent> deadnotes = curChord.prune(part.getInstrument().sustainable,
					part.getInstrument() == LotroInstrument.BASIC_DRUM, part.getInstrument().isPercussion, part);
			removeNotes(events, deadnotes, part);// we need to set the pruned flag for last chord too.
            part.numberOfRemovedNotesFromPruning += deadnotes.size();
			curChord.recalcEndMicros();
			
			logNotes.fine(part.getTitle()+" final note ends at "+Util.formatDurationM(curChord.getEndMicros()-qtm.tickToMicrosABCOrganic(exportStartTick)));
			
			if (curChord.getEndMicros() < curChord.getStartMicros() + minimumMicros) {
				curChord.setEndMicrosExpand(curChord.getStartMicros() + minimumMicros);
				logNotes.fine("Last chord: expand dura");
			}
			
			long targetEndMicros = curChord.getEndMicros();
			reprocessLastChord = false;
			
			ChordOrganic nextChord = null;
			if (!useRestToShortenChords) {
				long curEndMicro = curChord.getEndMicros();
				for (int j = 0; j < curChord.size(); j++) {
					AbcNoteEvent jne = curChord.get(j);
					if (jne.endABCMicros > targetEndMicros) {
						long noteEndMicro = jne.endABCMicros;
						if (noteEndMicro-curEndMicro < minimumMicros/2 && jne.tiesTo == null) {
							// note ends approx the same time as the end of chord
							// we make it end same time as the shortest note in chord,
							// chord might become slightly longer later.
							jne.endABCMicros = curChord.getEndMicros();
							jne.setEndTick(qtm.microsToTickABCOrganic(curChord.getEndMicros()));
							logNotes.finer(part.getTitle()+ ": Fit note ending to last chord ending");
						} else {
							// This note extends past the end of the chord; break it into two tied notes
							logNotes.finest("Last chord: cut up chord");
							AbcNoteEvent next = jne.splitWithTieAtTick(qtm.microsToTickABCOrganic(targetEndMicros), targetEndMicros);
							if (nextChord == null) {
								nextChord = new ChordOrganic(next, qtm);
								chords.add(nextChord);
							} else {
								nextChord.add(next);
							}
						}
					}
				}
			}
			curChord.recalcEndMicros();
			
			if (assertionsEnabled && curChord != null) {
				/*
				 * 
				 * DEBUG STUFF
				 * 
				 */
				for (AbcNoteEvent evt : curChord.getNotes()) {
					long mics = evt.endABCMicros - evt.startABCMicros;
					assert mics <= softMaxDurationMicros + LONGEST_NOTE_SOFT_BUFFER_MICROS: evt.note+" micros="+mics;
				}					
				long endCur = curChord.getEndMicros();
				curChord.recalcEndMicros();
				assert endCur == curChord.getEndMicros();
				
				/*
				// debug code to investigate specific chord gaps
				if (!useRestToShortenChords && curChord.getStartMicros() == 92859) {
					System.out.println("\nCh: "+curChord.toStringDura()+" useRest="+useRestToShortenChords+" delete="+curChord.delete);
					
					for (AbcNoteEvent n : curChord.getNotes()) {
						System.out.println(n.note+": "+n.startABCMicros+" - "+n.endABCMicros+" tiesFrom="+(n.tiesFrom != null));
					}
					if (chords.size()>1) {
						System.out.println("\npre: "+chords.get(chords.size()-2).toStringDura()+" delete="+curChord.delete);
						for (AbcNoteEvent n : chords.get(chords.size()-2).getNotes()) {
							System.out.println(n.note+": "+n.startABCMicros+" - "+n.endABCMicros+" tiesTo="+(n.tiesTo != null));
						}
						if (chords.size()>2) {
							System.out.println("\npre-: "+chords.get(chords.size()-3).toStringDura()+" delete="+curChord.delete);
							for (AbcNoteEvent n : chords.get(chords.size()-3).getNotes()) {
								System.out.println(n.note+": "+n.startABCMicros+" - "+n.endABCMicros);
							}
						}
					}
					//assert chords.get(chords.size()-2).endABCMicros == curChord.getStartMicros();
					//assert false:"stopped";
				}
				*/
			}
			if (assertionsEnabled) assertSoftDura(curChord, minimumMicros*99/100);

			if (nextChord != null) {
				reprocessLastChord = true;
				curChord = nextChord;
				curChord.recalcEndMicros();
			}
		}
		assert !curChord.hasRestAndNotes() || useRestToShortenChords;
		
		// delete all chords with zero duration, as there was no room for them
		List<ChordOrganic> trash = new ArrayList<>();
		int count = 0;
        for (ChordOrganic chord : chords) {
            if (chord.getStartMicros() == chord.getEndMicros()) {
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
            } else {
                if (assertionsEnabled) assertSoftDura(chord, minimumMicros * 99 / 100);
            }
        }
		chords.removeAll(trash);
		
		if (count > 0) {
			logNotes.fine(part.getAbcSong().getSongTitle()+": deleting "+count+ " resting chords due to rest being too short !!!!!!");
		}
		if (useRestToShortenChords) {
			/*
			 * It can happen that a note that is longer than the chord
			 * is also present in the next chord. And if there is a
			 * volume difference between the chords, lotro will
			 * silence the entire part. So to prevent that, we shorten
			 * some notes to be the same dura as the chord.
			 */
			List<AbcNoteEvent> notesOn = new ArrayList<>();
			Long lastEnd = null;
			for (ChordOrganic chord : chords) {

                chord.recalcEndMicros();

                assert lastEnd == null || chord.getStartMicros() == lastEnd :"Gap between chords1. Start micros (second):"+chord.getStartMicros();

                List<AbcNoteEvent> notesOff = new ArrayList<>();
                for (AbcNoteEvent ne : notesOn) {
                    if (ne.endABCMicros <= chord.getStartMicros()) {
                        notesOff.add(ne);
                    }
                }
                notesOn.removeAll(notesOff);

				for (AbcNoteEvent curr : chord.getNotes()) {
					for (AbcNoteEvent pre : notesOn) {
						if (pre.note == curr.note) {
							assert curr.endABCMicros > pre.endABCMicros;
							pre.endABCMicros = curr.startABCMicros;
							if (pre.tiesTo == null) {
								// I suspect lotro internally can
								// have rounding errors.
								// So we shorten a slight bit.
								//pre.endABCMicros--;//this can cause it to end before its chord
							}
							pre.setEndTick(qtm.microsToTickABCOrganic(curr.startABCMicros));
							logNotes.fine(part.getTitle()+": normalizing note!1! tied="+(pre.tiesTo != null));
						}
					}
				}
				List<AbcNoteEvent> longerNotes = new ArrayList<>();
				for (AbcNoteEvent ne : chord.getNotes()) {
					if (ne.endABCMicros > chord.getEndMicros()) {
                        assert ne.note != Note.REST;
						longerNotes.add(ne);
					}
				}
                notesOn.addAll(longerNotes);


				lastEnd = chord.getEndMicros();
			}
		} else if (assertionsEnabled) {
			ChordOrganic preChord = null;
			for (ChordOrganic chord : chords) {
				assert chord.isConform():part.getAbcSong().getTitle()+"( "+part.getTitle()+"): not conform 3.";
				assert chord.isLinked():part.getAbcSong().getTitle()+"( "+part.getTitle()+"): not linked. "+part.getTitle();
				assert preChord==null || preChord.getEndMicros() == chord.getStartMicros():"Gap between chords2. Start Micro (second):"+chord.getStartMicros();
				preChord = chord;
			}
		}
		if (assertionsEnabled) {
			for (ChordOrganic chord : chords) {
				assertSoftDura(chord, minimumMicros*99/100);
			}
		}
		if (!chords.isEmpty() && chords.getFirst().getNotes().getFirst().startABCMicros != songStartMicros) {
            // This is an extra safety check for the first chord being aligned perfectly with
            // initial silence start time, should normally not happen
            boolean fixable = true;
            if (chords.getFirst().getNotes().getFirst().startABCMicros > songStartMicros) {
                for (AbcNoteEvent evt : chords.getFirst().getNotes()) {
                    if (evt.endABCMicros - songStartMicros > AbcConstants.LONGEST_NOTE_MICROS) {
                        logNotes.severe(part.getAbcSong().getTitle()+": Song start micros: "+songStartMicros+" != "+chords.getFirst().getNotes().getFirst().startABCMicros+" (too long to fix)");
                        fixable = false;
                        break;
                    }
                }
            } else {
                for (AbcNoteEvent evt : chords.getFirst().getNotes()) {
                    if (evt.endABCMicros - songStartMicros < minimumMicros) {
                        logNotes.severe(part.getAbcSong().getTitle()+": Song start micros: "+songStartMicros+" != "+chords.getFirst().getNotes().getFirst().startABCMicros+" (too short to fix)");
                        fixable = false;
                        break;
                    }
                }
            }
            if (fixable) {
                chords.getFirst().setForceEarlyStartMicros(songStartMicros, exportStartTick);
                logNotes.warning(part.getAbcSong().getTitle()+": Fixed song start micros.");
            } else {
                assert false:"Please notify Aifel that this occurred, thanks.";
            }
        }
		List<Chord> returnList = new ArrayList<>(chords.size());
		returnList.addAll(chords);
		return returnList;
	}
	
	private void assertSoftDura(ChordOrganic chord, long minimum) {
		if (chord == null) return;
		chord.recalcEndMicros();
		long chordDura = qtm.tickToMicrosABCOrganic(chord.getEndTick())-qtm.tickToMicrosABCOrganic(chord.getStartTick());
		long maxEndTick = qtm.microsToTickABCOrganic(qtm.tickToMicrosABCOrganic(chord.getStartTick()) + minimum);
		long maxEndMicros = chord.getStartMicros() + minimum;
		long tickMicro = qtm.microsToTickABCOrganic(qtm.tickToMicrosABCOrganic(chord.getEndTick()) + 10000L)-chord.getEndTick();
		if (chordDura > 0L && chordDura < minimum) {
			// we dont assert due to tick resolution might be so coarse that its within margin
			logNotes.fine(chordDura+" < "+minimum+" dontMove2="+chord.dontMove2+" delete="+chord.delete
					+" endTick="+chord.getEndTick()+" maxEndTick="+maxEndTick+" 10ms="+tickMicro
					+" endMicros="+chord.getEndMicros()+" maxEndMicros="+maxEndMicros);
		}
	}
	
	private void assertNoteDuraOrganic1(AbcNoteEvent ne, long minimum) {
		if (ne == null) return;
		long neDura = ne.endABCMicros-ne.startABCMicros;
		assert neDura == 0L || neDura >= minimum:neDura+" < "+minimum;
	}

    /**
     * Assign musical importance value to a chord
     * Part of single-stage organic
     */
	private int calcValue(ChordOrganic c, boolean sustained) {
		// weakness: this favors curChord if starting tick of next
		//           chord is not exactly aligned.
		int value = -1;
		value += c.sizeReal();
        if (value == -1) value = -32;// this way rests gets assigned value only by duration
		if (sustained || value == -32) {
			long start = c.getStartMicros();
			long end = c.getLongestEndMicros();
			long dura = end - start;
			value += (int) (dura/(AbcConstants.ONE_SECOND_MICROS/4L));
		}
		return value;
	}
	
	private boolean isRattle(AbcPart part, AbcNoteEvent ne) {
		if (part.getInstrument() == LotroInstrument.BASIC_DRUM) {
			Note note = ne.note;
            return note == Note.G3 || note == Note.A3 || note == Note.B3 || note == Note.C4 || note == Note.Fs2 || note == Note.Gs2;
		}
		return false;
	}
	
	private boolean isDrone(AbcPart part, AbcNoteEvent ne) {
		if (part.getInstrument() == LotroInstrument.BASIC_BAGPIPE && ne.note != Note.REST) {
            return ne.note.id <= AbcConstants.BAGPIPE_LAST_DRONE_NOTE_ID;
		}
		return false;
	}	
	
	/**
	 * process the notes using new multi-stage organic principle
	 * 
	 * This is much better and easier to read code (used to be, before poly 6+ at least).
	 * The single-stage is full of nested conditions.
	 * 
	 */
	private List<Chord> processOrganic2(AbcPart part, List<AbcNoteEvent> events, boolean useRestToShortenChords, int[] quanFractions) {
	
		final long minimumMicros = quanFractions[2];

		NavigableSet<Long> grid = upgraded?createGridVersion3(events, minimumMicros, part, part.getAbcSong().getSequenceInfo().getDataCache().getBarLengthTicks()):createGrid(events, minimumMicros, part, useRestToShortenChords);

        if (upgraded) {
            events = snapNotesToGrid3(events, grid, minimumMicros, part);
            /*
            boolean sustained = part.getInstrument().sustainable;
            if (sustained) {
                events = snapNotesToGridSustained(events, grid, minimumMicros, part);
            } else {
                events = snapNotesToGridFixed(events, grid, minimumMicros, part);
            }
             */
        } else {
            events = snapNotesToGrid(events, grid, minimumMicros, part);
        }

        events = removeCollapsedDissonance(events, part);

		List<Chord> chords = chordifyOrganic(events, grid, part, useRestToShortenChords, minimumMicros);
		
		return chords;
	}
	
	/**
	 * 
	 * Part of multi-stage organic path
	 * 
	 */
	NavigableSet<Long> createGrid(List<AbcNoteEvent> events, long minimumMicros, AbcPart part, boolean useRestToShortenChords) {
		// create a non-uniform organic grid

		final int startWeightLongNote = 3;
		final int startWeightShortNote = 9;
		final long thresholdShortNoteMicros = minimumMicros*2;
		final int endWeight = 1;
		final boolean endsShouldHaveNoSwayOverStartOfCluster = false;//Fernando sounds better with this false :(
		final boolean endsShouldHaveNoSwayOverStartWeights = true;

        // when cutting up too long notes, this is the minimum buffer they are allowed to exceed max with.
        long maxSustainBuffer = minimumMicros * 2;
        long maxSustain = LotroInstrumentSampleDuration.getSafeDuration(part.getInstrument());
        long minPreferredSustain = 4L * TimingInfo.ONE_SECOND_MICROS;
        long minSustain = 2L * TimingInfo.ONE_SECOND_MICROS;
		
		// types
		final int INITIAL = 0;
		final int START = 1;
		final int END = 2;
		final int GENERAL = 3;
		
		class GridLine {
		    long micros;
		    final int type;
		    final int weight;
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
			note.startABCMicros = qtm.tickToMicrosABCOrganic(note.getStartTick());			
			note.endABCMicros = qtm.tickToMicrosABCOrganic(note.getEndTick());
			note.endABCMicros = Math.max(note.endABCMicros, note.startABCMicros + minimumMicros);
			long durationMicros = note.endABCMicros - note.startABCMicros;
			int startWeight = durationMicros <= thresholdShortNoteMicros?startWeightShortNote:startWeightLongNote;
			if (!part.getInstrument().sustainable) {// must be after dura calc
				note.endABCMicros = note.startABCMicros + minimumMicros;
			}
			GridLine start = new GridLine(note.startABCMicros, START, startWeight);
			GridLine end = new GridLine(note.endABCMicros, END, endWeight);
			microsWeights.computeIfAbsent(note.startABCMicros, k -> new ArrayList<>()).add(start);
			microsWeights.computeIfAbsent(note.endABCMicros, k -> new ArrayList<>()).add(end);
		}
		
		// Now do some adjustments to note start and ends
		if (!useRestToShortenChords) {
			@SuppressWarnings("unchecked")
			List<GridLine>[] typeList = new List[] {};
			Long[] typeLong = {};		
			List<GridLine>[] vals = microsWeights.values().toArray(typeList);
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
		}
		
		List<GridLine> gridLines = new ArrayList<>();
	    for (Map.Entry<Long, List<GridLine>> entry : microsWeights.entrySet()) {
	        gridLines.addAll(entry.getValue());
	    }
		
		// collect the weights into clusters
	    List<List<GridLine>> clusters = new ArrayList<>();
	    List<GridLine> currentCluster = new ArrayList<>();
	    if (!gridLines.isEmpty()) {
	        currentCluster.add(gridLines.getFirst());
	    }
	    for (int i = 1; i < gridLines.size(); i++) {
	        GridLine curr = gridLines.get(i);
	        GridLine last = currentCluster.getFirst();
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
	    
	    Comparator<GridLine> gridLineComparator = new Comparator<>() {
            @Override
            public int compare(GridLine a, GridLine b) {
                int cmp = Long.compare(a.micros, b.micros);
                if (cmp == 0) {
                    return Integer.compare(a.type, b.type);
                }
                return cmp;
            }
        };
		
	    NavigableSet<GridLine> grid = new TreeSet<>(gridLineComparator);
	    
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
	        if (firstCluster && cluster.getFirst().micros == getExportStartMicrosABC()) {
	        	/*
	        	 * Test for equality because the first note will not start sooner
	        	 * and an inserted initial rest will start exactly at that time.
	        	 *
	        	 * When the first note starts is close to zero so a rest was inserted (which might be too short),
	        	 * or a note starts at zero, we here make sure the weights don't move the zero gridline.
	        	 * This is mostly relevant for not removing initial silence.
	        	 */
	        	micros = cluster.getFirst().micros;
	        }
	        firstCluster = false;
	        GridLine currAverage = new GridLine(micros, type, totalWeight);
	        currAverage.firstMicros = cluster.getFirst().micros;// micros of the first line in the cluster
	        if (endsShouldHaveNoSwayOverStartOfCluster && firstStartMicros != null) currAverage.firstMicros = firstStartMicros; 
	        currAverage.lastMicros =  cluster.getLast().micros;// micros of the last line in the cluster
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
		        		// we exhausted all 4 options and now give up on adjusting, the loop will never be infinite
		        		break;
		        	}
	        	}
	        }
	        grid.add(currAverage);
	        lastAverage = currAverage;
	    }
	    
	    if (grid.isEmpty()) return new TreeSet<>();
	    
	    
        Iterator<GridLine> gridIter = grid.iterator();
	    GridLine prev = gridIter.next();
	    
	    NavigableSet<GridLine> refinedGrid = new TreeSet<>(gridLineComparator);
	    refinedGrid.add(prev);

        long barTicks = part.getAbcSong().getSequenceInfo().getDataCache().getBarLengthTicks();
	    while (gridIter.hasNext()) {
	        GridLine curr = gridIter.next();
	        
	        // The grid segments might be larger than 5.0 seconds
		    // Cut it up
	        while (curr.micros - prev.micros > maxSustain) {
                long candidateTime;
                if (curr.micros - prev.micros < maxSustain * 2 - 500) {
                    // this prevents restarting into short bursts
                    candidateTime = prev.micros + (curr.micros - prev.micros) / 2;

                    // min and max points to prevent any segment to be longer than 5 secs
                    long minMicros = curr.micros - maxSustain;
                    long maxMicros = prev.micros + maxSustain;

                    candidateTime = closestBarMicrosABC(barTicks, candidateTime,
                            Math.min(candidateTime - minMicros, TimingInfo.ONE_SECOND_MICROS),
                            Math.min(maxMicros - candidateTime, TimingInfo.ONE_SECOND_MICROS));
                } else {
                    candidateTime = closestBarMicrosABC(barTicks, prev.micros + maxSustain,
                            maxSustain-minPreferredSustain, 0L);
                }
	            GridLine candidate = new GridLine(candidateTime, GENERAL, 0);
	            if (curr.micros - candidate.micros < maxSustainBuffer) {
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
	    
	    boolean assertionsEnabled = false;
		assert assertionsEnabled = true;
	    
	    NavigableSet<Long> gridTimes = new TreeSet<>();
	    Long lastLine = null;
	    int lastType = INITIAL;
	    for (GridLine line : refinedGrid) {
	        gridTimes.add(line.micros);
	        
	        if (assertionsEnabled && lastLine != null) {
	        	// TODO: comment out when system more solid
	        	assert line.micros >= lastLine+minimumMicros:part.getTitle()+": "+lastType+" "+(line.micros - lastLine)+" micros  "+line.type;
	        	assert line.micros <= lastLine+maxSustain+maxSustainBuffer:part.getTitle()+": "+lastType+" "+((line.micros - lastLine)/1000)+"ms "+line.type;
	        }
	        lastType = line.type;
	        lastLine = line.micros;
	        
	    }
	    
	    return gridTimes;
	}

    /**
     *
     * Used by createGridVersion2() of multi-stage organic path
     *
     */
    private record GridPoint(long micros, boolean isBounce, int weight) implements Comparable<GridPoint> {
        @Override
        public int compareTo(GridPoint o) {
            return Long.compare(this.micros, o.micros);
        }
    }

    record Candidate(long micros, int type, int weight, AbcNoteEvent note) {}

    /**
     *
     * Used by createGridVersion3() of multi-stage 2 organic path
     *
     */
    final int TYPE_START = 1;
    final int TYPE_END = 2;
    class GridPoint3 implements Comparable<GridPoint3> {
        private long micros;
        private final int bounceDepth;
        private final int weight;

        // A GridPoint can simultaneously be the start of some notes and the end of others.
        final List<AbcNoteEvent> starts = new ArrayList<>();
        final List<AbcNoteEvent> ends = new ArrayList<>();

        public GridPoint3(long micros, int bounceDepth, int weight) {
            this.micros = micros;
            this.bounceDepth = bounceDepth;
            this.weight = weight;
        }

        public long micros() { return micros; }
        public int weight() { return weight; }
        public int bounceDepth() { return bounceDepth; }

        // Binds a candidate's notes to this grid point and immediately updates their times to this point
        public void mergeCandidate(Candidate3 c) {
            if (c.type == TYPE_START) {
                this.starts.addAll(c.notes);
                for (AbcNoteEvent note : c.notes) note.startABCMicros = this.micros;
            } else {
                this.ends.addAll(c.notes);
                for (AbcNoteEvent note : c.notes) note.endABCMicros = this.micros;
            }
        }

        // Merges another GridPoint into this one (e.g., when a stronger blocker overwrites a weaker one)
        public void absorb(GridPoint3 other) {
            this.starts.addAll(other.starts);
            this.ends.addAll(other.ends);
            for (AbcNoteEvent note : other.starts) note.startABCMicros = this.micros;
            for (AbcNoteEvent note : other.ends) note.endABCMicros = this.micros;
        }

        /*
         * Use carefully! Must remove from TreeSet before calling, and re-add after.
         */
        public void moveTo(long newMicros) {
            this.micros = newMicros;
            for (AbcNoteEvent note : starts) note.startABCMicros = newMicros;
            for (AbcNoteEvent note : ends) note.endABCMicros = newMicros;
        }

        @Override
        public int compareTo(GridPoint3 o) {
            return Long.compare(this.micros, o.micros);
        }
    }

    static class Candidate3 {
        long micros;
        final int type;   // TYPE_START or TYPE_END
        int weight = 0;

        // Instead of a single note, we hold all notes participating in this event
        final List<AbcNoteEvent> notes = new ArrayList<>();

        public Candidate3(long micros, int type) {
            this.micros = micros;
            this.type = type;
        }

        public void addNote(AbcNoteEvent note, int addedWeight) {
            this.notes.add(note);
            this.weight += addedWeight;
        }

        public int weight() { return weight; }
        public int type() { return type; }
        public long micros() { return micros; }
    }

    /**
     *
     * Part of organic multi-stage 2 path
     *
     */
    private NavigableSet<Long> createGridVersion3(List<AbcNoteEvent> events, long minimumMicros, AbcPart part, long barTicks) {

        final int WEIGHT_SOLO = 10;  // Fast notes
        final int WEIGHT_LONG = 10;  // Sustained notes
        final int WEIGHT_GRACE = 5;  // Ornaments
        final int WEIGHT_END = 1;    // Note endings

        final long GRACE_THRESHOLD = 50_000L; // 50ms
        final long SHORT_NOTE_THRESHOLD = minimumMicros * 3;

        // The window within which notes are considered part of the same group
        final long arpeggioWindow = 45_000L;
        final int MAX_BOUNCE_CHAIN = 2;

        // when cutting up too long notes, this is the minimum buffer they are allowed to exceed max with.
        long maxSustainBuffer = minimumMicros * 2;
        long maxSustain = LotroInstrumentSampleDuration.getSafeDuration(part.getInstrument());
        long minPreferredSustain = 4L * TimingInfo.ONE_SECOND_MICROS;
        long minSustain = 2L * TimingInfo.ONE_SECOND_MICROS;
        boolean sustained = part.getInstrument().sustainable;

        //System.err.println("createGridVersion3: maxSustainBuffer="+maxSustainBuffer+" maxSustain="+maxSustain+" minPreferredSustain="+minPreferredSustain+" minSustain="+minSustain+" sustained="+sustained);


        // Using maps first to sum weights of coincident events
        Map<Long, Candidate3> startCandidates = new HashMap<>();
        Map<Long, Candidate3> endCandidates = new HashMap<>();

        for (AbcNoteEvent note : events) {
            long rawStartMicros = qtm.tickToMicrosABCOrganic(note.getStartTick());
            long rawEndMicros = qtm.tickToMicrosABCOrganic(note.getEndTick());
            long rawDuration = rawEndMicros - rawStartMicros;

            // Lock in the immutable original times for future safety checks
            note.initStartABCMicros = rawStartMicros;
            note.initEndABCMicros = rawEndMicros;

            // Set the mutable times
            note.startABCMicros = rawStartMicros;
            note.endABCMicros = rawEndMicros;

            if (!sustained) {
                note.endABCMicros = Math.max(note.endABCMicros, note.startABCMicros + minimumMicros);
            }
            note.endABCMicros = Math.max(note.endABCMicros, note.startABCMicros + minimumMicros);

            // Determine start weight
            int sWeight;
            if (rawDuration < GRACE_THRESHOLD && !part.getInstrument().isPercussion) {
                sWeight = WEIGHT_GRACE;
            } else if (rawDuration <= SHORT_NOTE_THRESHOLD) {
                sWeight = WEIGHT_SOLO;
            } else {
                sWeight = WEIGHT_LONG;
            }

            // Bin into candidates (adds the note and accumulates the weight)
            startCandidates.computeIfAbsent(note.startABCMicros, t -> new Candidate3(t, TYPE_START))
                    .addNote(note, sWeight);

            endCandidates.computeIfAbsent(note.endABCMicros, t -> new Candidate3(t, TYPE_END))
                    .addNote(note, WEIGHT_END);
        }

        // Combine into a single list
        List<Candidate3> candidates = new ArrayList<>(startCandidates.size() + endCandidates.size());
        candidates.addAll(startCandidates.values());
        candidates.addAll(endCandidates.values());

        // Sort (Solo > Long > Grace > End)
        candidates.sort(Comparator
                .comparingInt(Candidate3::weight).reversed()
                .thenComparingInt(Candidate3::type)
                .thenComparingLong(Candidate3::micros));

        TreeSet<GridPoint3> grid = new TreeSet<>();
        final long firstMicros = getExportStartMicrosABC();
        grid.add(new GridPoint3(firstMicros, 0, Integer.MAX_VALUE));

        // The absolute last microsecond of the track
        long endOfTrack = candidates.getLast().micros;

        // Tracks the time of the last note that failed a bounce and was forced to crush
        long lastCrushedTime = -1L;

        for (int i = 0; i < candidates.size(); i++) {
            Candidate3 c = candidates.get(i);

            if (c.notes.isEmpty()) {
                // backward gracenote bounce might have removed all notes from c
                continue;
            }

            long time = c.micros;

            GridPoint3 searchKey = new GridPoint3(time, 0, 0);
            GridPoint3 floor = grid.floor(searchKey);
            GridPoint3 ceil = grid.ceiling(searchKey);

            boolean exactFloor = floor != null && time == floor.micros();
            boolean exactCeil = ceil != null && time == ceil.micros();
            boolean isTaken = exactFloor || exactCeil;

            boolean floorConflict = !exactFloor && (floor != null && Math.abs(time - floor.micros()) < minimumMicros);
            boolean ceilConflict = !exactCeil && (ceil != null && Math.abs(ceil.micros() - time) < minimumMicros);

            if (isTaken) {
                // Grid point already exists here.
                // We just strap these notes to the existing anchor.
                GridPoint3 exact = exactFloor ? floor : ceil;
                exact.mergeCandidate(c);

            } else if (!floorConflict && !ceilConflict) {
                // Create a new anchor and strap notes to it.
                GridPoint3 newPoint = new GridPoint3(time, 0, c.weight());
                newPoint.mergeCandidate(c);
                grid.add(newPoint);

            } else if (bouncingEnabled && c.type == TYPE_START) {
                // Conflicts (Bounces and block Chords)

                // The Group Collapse Check
                // Are we part of a fast group that just collapsed?
                boolean partOfCollapsedGroup = (lastCrushedTime != -1L) && (time - lastCrushedTime <= arpeggioWindow);

                if (partOfCollapsedGroup && floor != null) {
                    // The group is collapsing. Force this note to the floor immediately.
                    floor.mergeCandidate(c);
                    lastCrushedTime = time; // Update the time so the next note knows we're still collapsing
                    continue; // Skip all other bounce logic!
                }

                // The Leapfrog Trap Door
                // If 3rd note evaluates and sees that 2nd snowplowed past it,
                // 3rd must ride the snowplow, not crush backward into the past.
                if (ceilConflict && ceil.bounceDepth() > 0 && time < ceil.micros()) {
                    floor = ceil;
                    floorConflict = true;
                }

                if (c.weight >= WEIGHT_SOLO && floorConflict) {
                    // Forward bounce (solos/arpeggios)
                    boolean distanceOk = floor.micros() + minimumMicros * 3L / 4L < time;
                    boolean snowplowActive = floor.bounceDepth() > 0;
                    boolean underChainLimit = floor.bounceDepth() < MAX_BOUNCE_CHAIN;

                    boolean isOkToBounce = (snowplowActive || distanceOk) && underChainLimit;
                    long bounceTime = floor.micros() + minimumMicros;

                    // Look-Ahead Check
                    if (isOkToBounce) {
                        boolean chainSafe = isSnowplowPathClear(i, bounceTime, candidates, grid, minimumMicros, floor.bounceDepth() + 1, MAX_BOUNCE_CHAIN, firstMicros);
                        if (!chainSafe) {
                            isOkToBounce = false; // The future is blocked. Abort the bounce!
                        }
                    }

                    // Check if very last note can bounce without requiring its ending to go past end of track.
                    if (isOkToBounce) {
                        for (AbcNoteEvent note : c.notes) {
                            // If this note ends at the absolute edge of the track, and bouncing
                            // forward leaves it with zero/negative duration or an illegal micro-gap...
                            if (note.initEndABCMicros == endOfTrack && (note.initEndABCMicros - bounceTime < minimumMicros)) {
                                isOkToBounce = false; // Abort the bounce. Crush backward instead.
                                break;
                            }
                        }
                    }

                    if (isOkToBounce && isValidBounce3(bounceTime, time, minimumMicros, grid, c.weight, true, firstMicros)) {
                        applyBounce3(grid, bounceTime, c, minimumMicros, floor.bounceDepth() + 1);
                        lastCrushedTime = -1;
                    } else {
                        // Force into Block Chord: Snap to floor.
                        floor.mergeCandidate(c);
                        lastCrushedTime = time;
                    }
                } else if (c.weight == WEIGHT_GRACE && ceil != null && ceilConflict) {
                    // Backward bounce (grace notes)

                    boolean isOkToBounceBackward = ceil.bounceDepth() < MAX_BOUNCE_CHAIN;
                    long bounceTime = ceil.micros() - minimumMicros;

                    if (isOkToBounceBackward && isValidBounce3(bounceTime, time, minimumMicros, grid, c.weight, false, firstMicros)) {
                        applyBounce3(grid, bounceTime, c, minimumMicros, ceil.bounceDepth() + 1);

                        for (AbcNoteEvent note : c.notes) {
                            if (note.initEndABCMicros <= ceil.micros()) {
                                // No original overlap with main note
                                Candidate3 endCand = endCandidates.get(note.endABCMicros);

                                // Stop gracenote(s) from having their own end candidate
                                // put their endings into main notes candidate instead.
                                if (endCand != null) {
                                    endCand.notes.remove(note);
                                }
                                note.endABCMicros = ceil.micros(); // Snap end to main note start
                                ceil.ends.add(note);
                            }
                            // If note.initEndABCMicros > ceil.micros(), we leave it untouched
                            // to preserve the intentional overlap.
                        }

                        lastCrushedTime = -1;
                    } else {
                        // mark it for deletion by moving it to negative infinity.
                        for (AbcNoteEvent note : c.notes) {
                            note.startABCMicros = Long.MIN_VALUE;
                        }
                        if (logNotes.isLoggable(Level.FINEST)) {
                            logNotes.finest("Deleted grace note at " + Util.formatDurationM(time) + " (No space available)");
                        }
                        lastCrushedTime = time;
                    }
                } else {
                    GridPoint3 blocker = floorConflict ? floor : ceil;
                    if (blocker != null) blocker.mergeCandidate(c);
                    lastCrushedTime = time;
                }

            } else if (c.type == TYPE_END) {
                // Ending conflicts (Overwrites and fallbacks)

                GridPoint3 blocker = floorConflict ? floor : ceil;
                if (floorConflict && ceilConflict) {
                    // pick the closest blocker
                    blocker = (Math.abs(time - floor.micros()) < Math.abs(time - ceil.micros())) ? floor : ceil;
                }

                boolean added = false;
                if (blocker != null && blocker.weight() < c.weight()) {
                    // Overwrite weak blocker
                    grid.remove(blocker);
                    GridPoint3 newPoint = new GridPoint3(time, 0, c.weight());

                    // Drag all notes attached to the old blocker to the new time!
                    newPoint.absorb(blocker);
                    newPoint.mergeCandidate(c);

                    grid.add(newPoint);
                    added = true;
                }

                // Fallback for rejected end candidates
                if (!added && floorConflict && !ceilConflict) {
                    long safetyTime = floor.micros() + minimumMicros;

                    // Verify safetyTime doesn't conflict with ceiling
                    // (It effectively steals space from the gap)
                    boolean safetyConflict = (ceil != null && Math.abs(ceil.micros() - safetyTime) < minimumMicros);

                    // Also ensure we aren't adding a duplicate
                    boolean safetyExists = (ceil != null && ceil.micros() == safetyTime);

                    if (!safetyConflict && !safetyExists) {
                        // Add the safety line with low weight
                        GridPoint3 safetyPoint = new GridPoint3(safetyTime, 0, WEIGHT_END);
                        safetyPoint.mergeCandidate(c);
                        grid.add(safetyPoint);
                        added = true;
                    } else if (safetyExists) {
                        ceil.mergeCandidate(c);
                        added = true;
                    }
                }

                // Absolute last resort: just strap the end to the blocker so it doesn't fall off the grid
                if (!added && blocker != null) {
                    blocker.mergeCandidate(c);
                }
            }
        }

        NavigableSet<Long> finalGrid = new TreeSet<>();
        if (grid.isEmpty()) return finalGrid;

        Iterator<GridPoint3> it = grid.iterator();
        long prev = it.next().micros();
        finalGrid.add(prev);

        // ensure we don't have silence longer than sample lengths
        while (it.hasNext()) {
            GridPoint3 currPoint = it.next(); // Grab the actual object
            long curr = currPoint.micros();
            long diff = curr - prev;

            if (diff > maxSustain) {

                // The grid segments might be larger than sample lengths
                // Cut it up
                while (diff > maxSustain) {
                    long candidateTime;

                    // gap just slightly too large (5s to 9.9995s)
                    if (diff < maxSustain * 2L - 500L) {
                        long midpoint = prev + diff / 2L;

                        // limits
                        long lowerBound = curr - maxSustain;
                        long upperBound = prev + maxSustain;

                        // musical Limits (Segments must be >= 2s)
                        long minSegmentLen = minSustain;

                        long musicalLowerBound = prev + minSegmentLen;
                        long musicalUpperBound = curr - minSegmentLen;

                        // Intersect to find the safe zone
                        long safeMin = Math.max(lowerBound, musicalLowerBound);
                        long safeMax = Math.min(upperBound, musicalUpperBound);

                        if (safeMin <= midpoint && safeMax >= midpoint) {
                            // Search for a bar line within the safe zone
                            candidateTime = closestBarMicrosABC(barTicks, midpoint,
                                    midpoint - safeMin,
                                    safeMax - midpoint);
                        } else {
                            // Constraints are impossible
                            // Fallback to midpoint
                            candidateTime = midpoint;
                        }
                    } else {
                        // big gap (> 9.9995s). slice off sample duration chunks.
                        candidateTime = closestBarMicrosABC(barTicks, prev + maxSustain,
                                maxSustain-minPreferredSustain, 0L);
                    }

                    if (curr - candidateTime < maxSustainBuffer) {
                        // we allow to go maxSustainBuffer over LONGEST_NOTE_MICROS
                        break;
                    }

                    finalGrid.add(candidateTime);
                    assert candidateTime > prev;
                    prev = candidateTime;
                    diff = curr - prev;
                }

                finalGrid.add(curr);
                prev = curr;

            } else if (diff < minimumMicros) {
                // The gap is illegally small. We must drop 'curr'.
                // Rescue all notes bound to this point and snap them to the safe 'prev' anchor.
                for (AbcNoteEvent n : currPoint.starts) n.startABCMicros = prev;
                for (AbcNoteEvent n : currPoint.ends) n.endABCMicros = prev;

                // Note: If snapping an end backward crushes a note to 0 duration,
                // the recovery block in snapNotesToGrid3 will safely expand it later.
            } else {
                finalGrid.add(curr);
                prev = curr;
            }
        }

        boolean assertionsEnabled = false;
        assert assertionsEnabled = true;

        if (assertionsEnabled) {
            Long lastLine = null;
            for (Long line : finalGrid) {
                if (lastLine != null) {
                    assert line >= lastLine + minimumMicros : part.getTitle() + ": " + (line - lastLine) + " micros";
                    assert line <= lastLine + maxSustain + maxSustainBuffer : part.getTitle() + ": " + ((line - lastLine) / 1000) + "ms " + line;
                }
                lastLine = line;
            }
        }

        return finalGrid;
    }

    private void applyBounce3(TreeSet<GridPoint3> grid, long bounceTime, Candidate3 c, long minimumMicros, int newBounceDepth) {
        GridPoint3 bKey = new GridPoint3(bounceTime, newBounceDepth, 0);
        GridPoint3 bCeil = grid.ceiling(bKey);
        GridPoint3 bFloor = grid.floor(bKey);

        GridPoint3 blocker = null;

        // Thanks to the 60ms strict spacing rule, the origin of the bounce
        // is exactly 60ms away, meaning it fails the '< minimumMicros' check.
        // Mathematically, only one of these two statements can ever be true.
        if (bCeil != null && Math.abs(bCeil.micros() - bounceTime) < minimumMicros) {
            blocker = bCeil;
        } else if (bFloor != null && Math.abs(bounceTime - bFloor.micros()) < minimumMicros) {
            blocker = bFloor;
        }

        if (blocker != null) {
            if (blocker.weight() < c.weight()) {
                // We are stronger! Overwrite the blocker at the bounce site.
                grid.remove(blocker);
                GridPoint3 bp = new GridPoint3(bounceTime, newBounceDepth, c.weight());

                // Absorb notes attached to the weak blocker, drag them to bounceTime
                bp.absorb(blocker);
                bp.mergeCandidate(c);

                grid.add(bp);
                if (logNotes.isLoggable(Level.FINEST)) logNotes.finest("Overwriting weak grid line at bounce site: " + Util.formatDurationM(blocker.micros()));
            } else {
                // Blocker is stronger. Snap the bouncing notes to the blocker instead.
                blocker.mergeCandidate(c);
            }
        } else {
            // Free space at bounce destination
            GridPoint3 bp = new GridPoint3(bounceTime, newBounceDepth, c.weight());
            bp.mergeCandidate(c);
            grid.add(bp);

            if (logNotes.isLoggable(Level.FINEST)) {
                logNotes.finest("Bounced " + Util.formatDurationM(bounceTime));
            }
        }
    }

    private boolean isValidBounce3(long bounceTime, long originalTime, long minimumMicros, TreeSet<GridPoint3> grid, int weight, boolean forward, long exportStartTime) {

        if (bounceTime < exportStartTime) {
            return false;
        }

        boolean directionOk = forward ? (bounceTime >= originalTime) : (bounceTime <= originalTime);
        boolean reasonable = Math.abs(bounceTime - originalTime) < (3 * minimumMicros / 2);

        GridPoint3 key = new GridPoint3(bounceTime, 0, 0);
        GridPoint3 neighbor = forward ? grid.ceiling(key) : grid.floor(key);

        // A space is only naturally safe if it's empty, or if the neighbor is at least 60ms away.
        // We explicitly forbid landing exactly on a neighbor here.
        boolean spaceSafe = neighbor == null
                || Math.abs(neighbor.micros() - bounceTime) >= minimumMicros;

        // Can we overwrite a weak neighbor?
        int neighborWeight = (neighbor == null) ? 0 : neighbor.weight();
        boolean weightSafe = neighborWeight < weight;

        // To bounce, the direction and distance must be okay, AND we must either have
        // safe empty space, or be strong enough to crush the existing weak candidate.
        return directionOk && reasonable && (spaceSafe || weightSafe);
    }

    // Simulates the snowplow chain reaction. Returns false if a leapfrogged note
    // hits a wall, meaning the current bounce must be aborted.
    private boolean isSnowplowPathClear(int currentIndex, long proposedBounceTime, List<Candidate3> candidates, TreeSet<GridPoint3> grid, long minimumMicros, int nextDepth, int maxChain, long exportStartTime) {
        long simTarget = proposedBounceTime;
        int simDepth = nextDepth;

        // Look ahead at upcoming candidates
        for (int j = currentIndex + 1; j < candidates.size(); j++) {
            Candidate3 futureC = candidates.get(j);
            if (futureC.type != TYPE_START) continue;

            // If the future note is safely past our simulated target, the chain is clear.
            if (futureC.micros >= simTarget) return true;

            // futureC is trapped. It must bounce to the next slot.
            simDepth++;
            simTarget += minimumMicros;

            // Chain limit exceeded
            if (simDepth > maxChain) return false;

            // The forced destination is blocked by a heavy chord
            if (!isValidBounce3(simTarget, futureC.micros, minimumMicros, grid, futureC.weight, true, exportStartTime)) {
                return false;
            }
        }
        return true;
    }

    /**
     *
     * Part of organic multi-stage 2 path
     *
     */
    @Deprecated
    private NavigableSet<Long> createGridVersion2(List<AbcNoteEvent> events, long minimumMicros, AbcPart part, long barTicks) {

        final int WEIGHT_SOLO = 10;  // Fast notes
        final int WEIGHT_LONG = 10;  // Sustained notes
        final int WEIGHT_GRACE = 5;  // Ornaments
        final int WEIGHT_END = 1;    // Note endings

        final long GRACE_THRESHOLD = 50_000L; // 50ms
        final long SHORT_NOTE_THRESHOLD = minimumMicros * 3;

        // when cutting up too long notes, this is the minimum buffer they are allowed to exceed max with.
        long maxSustainBuffer = minimumMicros * 2;
        long maxSustain = LotroInstrumentSampleDuration.getSafeDuration(part.getInstrument());
        long minPreferredSustain = 4L * TimingInfo.ONE_SECOND_MICROS;
        long minSustain = 2L * TimingInfo.ONE_SECOND_MICROS;
        boolean sustained = part.getInstrument().sustainable;

        //System.err.println("createGridVersion2: maxSustainBuffer="+maxSustainBuffer+" maxSustain="+maxSustain+" minPreferredSustain="+minPreferredSustain+" minSustain="+minSustain+" sustained="+sustained);

        /*
            If two note starts are 30 to 60 ms apart (arpeggio), keep the arpeggio instead of forcing them into
            block chord as createGrid() would do. The new arpegio will be 60 ms instead, but thats barely noticable.
            However only do it if there is not another note start within first note + 120 ms.
         */
        final boolean bouncingEnabled = true;


        // Using maps first to sum weights of coincident events
        Map<Long, Integer> startWeightMap = new HashMap<>();
        Map<Long, Integer> endWeightMap = new HashMap<>();

        for (AbcNoteEvent note : events) {

            note.startABCMicros = qtm.tickToMicrosABCOrganic(note.getStartTick());
            long rawEndMicros = qtm.tickToMicrosABCOrganic(note.getEndTick());
            long rawDuration = rawEndMicros - note.startABCMicros;

            int sWeight;
            if (rawDuration < GRACE_THRESHOLD && !part.getInstrument().isPercussion) {
                sWeight = WEIGHT_GRACE;
            } else if (rawDuration <= SHORT_NOTE_THRESHOLD) {
                sWeight = WEIGHT_SOLO;
            } else {
                sWeight = WEIGHT_LONG;
            }

            note.endABCMicros = rawEndMicros;
            if (!sustained) {
                note.endABCMicros = Math.max(note.endABCMicros, note.startABCMicros + minimumMicros);
            }
            note.endABCMicros = Math.max(note.endABCMicros, note.startABCMicros + minimumMicros);

            startWeightMap.merge(note.startABCMicros, sWeight, Integer::sum);
            endWeightMap.merge(note.endABCMicros, WEIGHT_END, Integer::sum);
        }

        List<Candidate> candidates = new ArrayList<>();
        for (AbcNoteEvent note : events) {
            int w = startWeightMap.getOrDefault(note.startABCMicros, 0);
            candidates.add(new Candidate(note.startABCMicros, TYPE_START, w, note));
        }
        Set<Long> endTimes = new HashSet<>();
        for (AbcNoteEvent note : events) {
            endTimes.add(note.endABCMicros);
        }
        for (Long t : endTimes) {
            int w = endWeightMap.getOrDefault(t, 0);
            candidates.add(new Candidate(t, TYPE_END, w, null));
        }

        // 3. Sort (Solo > Long > Grace > End)
        candidates.sort(Comparator
                .comparingInt(Candidate::weight).reversed()
                .thenComparingInt(Candidate::type)
                .thenComparingLong(Candidate::micros));

        TreeSet<GridPoint> grid = new TreeSet<>();
        grid.add(new GridPoint(getExportStartMicrosABC(), false, Integer.MAX_VALUE));

        for (Candidate c : candidates) {
            long time = c.micros;

            GridPoint searchKey = new GridPoint(time, false, 0);
            GridPoint floor = grid.floor(searchKey);
            GridPoint ceil = grid.ceiling(searchKey);

            boolean floorConflict = (floor != null && Math.abs(time - floor.micros()) < minimumMicros);
            boolean ceilConflict = (ceil != null && Math.abs(ceil.micros() - time) < minimumMicros);
            boolean isTaken = (ceil != null && time == ceil.micros()) || (floor != null && time == floor.micros());

            if (!floorConflict && !ceilConflict && !isTaken) {
                grid.add(new GridPoint(time, false, c.weight()));
            } else if (bouncingEnabled && c.type == TYPE_START && !isTaken) {

                if (c.weight >= WEIGHT_SOLO && floor != null && floorConflict) {
                    // Forward bounce (solos/arpeggios)

                    // If the previous grid point was a bounce, we assume we are in a run/arpeggio chain
                    // and should continue bouncing to preserve separation, even if the gap is small.
                    boolean isOkToBounce = floor.isBounce() || floor.micros() + minimumMicros / 2 < time;
                    long bounceTime = floor.micros() + minimumMicros;
                    if (isOkToBounce && isValidBounce(bounceTime, time, minimumMicros, grid, c.weight, true)) {
                        applyBounce(grid, bounceTime, c, minimumMicros);
                    } else {
                        // Snap to floor (block Chord)
                        if (c.note() != null) {
                            long duration = c.note().endABCMicros - c.note().startABCMicros;
                            c.note().startABCMicros = floor.micros();
                            c.note().endABCMicros = floor.micros() + duration;
                        }
                    }
                } else if (c.weight == WEIGHT_GRACE && ceil != null && ceilConflict) {
                    // Backward bounce (grace notes)

                    long bounceTime = ceil.micros() - minimumMicros;

                    if (isValidBounce(bounceTime, time, minimumMicros, grid, c.weight, false)) {
                        applyBounce(grid, bounceTime, c, minimumMicros);
                    } else {
                        // mark it for deletion by moving it to negative infinity.
                        if (c.note() != null) {
                            c.note().startABCMicros = -Long.MAX_VALUE / 2;
                            if (logNotes.isLoggable(Level.FINEST)) {
                                logNotes.finest("Deleted grace note at " + Util.formatDurationM(time) + " (No space available)");
                            }
                        }
                    }
                }
            } else if (c.type == TYPE_END && !isTaken) {
                GridPoint blocker = null;
                if (floorConflict) blocker = floor;
                if (ceilConflict) blocker = ceil;

                if (floorConflict && ceilConflict) {
                    // pick the closest blocker
                    blocker = (Math.abs(time - floor.micros()) < Math.abs(time - ceil.micros())) ? floor : ceil;
                }

                boolean added = false;
                if (blocker.weight() < c.weight()) {
                    // Overwrite weak blocker (it's guaranteed to also be an end)
                    grid.remove(blocker);
                    grid.add(new GridPoint(time, false, c.weight()));
                    added = true;
                }

                // Fallback for rejected end candidates
                // If we couldn't place the end line due to a floor conflict (too close to start?),
                // and there is no ceiling nearby to snap to, we risk the note being deleted.
                // We insert a safety end at exactly minimumMicros after the floor.
                if (!added && floorConflict && !ceilConflict) {
                    long safetyTime = floor.micros() + minimumMicros;

                    // Verify safetyTime doesn't conflict with ceiling
                    // (It effectively steals space from the gap)
                    boolean safetyConflict = (ceil != null && Math.abs(ceil.micros() - safetyTime) < minimumMicros);

                    // Also ensure we aren't adding a duplicate
                    boolean safetyExists = (ceil != null && ceil.micros() == safetyTime);

                    if (!safetyConflict && !safetyExists) {
                        // Add the safety line with low weight (it's a fallback)
                        grid.add(new GridPoint(safetyTime, false, WEIGHT_END));
                    }
                }
            }
        }

        NavigableSet<Long> finalGrid = new TreeSet<>();
        if (grid.isEmpty()) return finalGrid;

        Iterator<GridPoint> it = grid.iterator();
        long prev = it.next().micros();
        finalGrid.add(prev);

        // ensure we don't have silence longer than 5s
        while (it.hasNext()) {
            long curr = it.next().micros();
            long diff = curr - prev;

            if (diff > maxSustain) {

                // The grid segments might be larger than 5.0 seconds
                // Cut it up
                while (diff > maxSustain) {
                    long candidateTime;

                    // gap just slightly too large (5s to 9.9995s)
                    if (diff < maxSustain * 2L - 500L) {
                        long midpoint = prev + diff / 2L;

                        // limits
                        long lowerBound = curr - maxSustain;
                        long upperBound = prev + maxSustain;

                        // musical Limits (Segments must be >= 2s)
                        long minSegmentLen = minSustain;

                        long musicalLowerBound = prev + minSegmentLen;
                        long musicalUpperBound = curr - minSegmentLen;

                        // Intersect to find the safe zone
                        long safeMin = Math.max(lowerBound, musicalLowerBound);
                        long safeMax = Math.min(upperBound, musicalUpperBound);

                        if (safeMin <= midpoint && safeMax >= midpoint) {
                            // Search for a bar line within the safe zone
                            candidateTime = closestBarMicrosABC(barTicks, midpoint,
                                    midpoint - safeMin,
                                    safeMax - midpoint);
                        } else {
                            // Constraints are impossible
                            // Fallback to midpoint
                            candidateTime = midpoint;
                        }
                    } else {
                        // big gap (> 9.9995s). slice off 5s chunks.
                        candidateTime = closestBarMicrosABC(barTicks, prev + maxSustain,
                                maxSustain-minPreferredSustain, 0L);
                    }

                    if (curr - candidateTime < maxSustainBuffer) {
                        // we allow to go maxSustainBuffer over LONGEST_NOTE_MICROS
                        break;
                    }

                    finalGrid.add(candidateTime);
                    assert candidateTime > prev;
                    prev = candidateTime;
                    diff = curr - prev;
                }

                finalGrid.add(curr);
                prev = curr;
            } else if (diff < minimumMicros) {
                // should normally not come in here
            } else {
                finalGrid.add(curr);
                prev = curr;
            }
        }

        boolean assertionsEnabled = false;
        assert assertionsEnabled = true;

        if (assertionsEnabled) {
            // TODO: comment out when system more solid
            Long lastLine = null;
            for (Long line : finalGrid) {
                if (lastLine != null) {
                    assert line >= lastLine + minimumMicros : part.getTitle() + ": " + (line - lastLine) + " micros";
                    assert line <= lastLine + maxSustain + maxSustainBuffer : part.getTitle() + ": " + ((line - lastLine) / 1000) + "ms " + line;
                }
                lastLine = line;
            }
        }

        return finalGrid;
    }

    private boolean isValidBounce(long bounceTime, long originalTime, long minimumMicros, TreeSet<GridPoint> grid, int weight, boolean forward) {
        boolean directionOk = forward ? (bounceTime >= originalTime) : (bounceTime <= originalTime);
        boolean reasonable = Math.abs(bounceTime - originalTime) < (3 * minimumMicros / 2);

        GridPoint key = new GridPoint(bounceTime, false, 0);
        GridPoint neighbor = forward ? grid.ceiling(key) : grid.floor(key);

        boolean spaceSafe = neighbor == null
                || Math.abs(neighbor.micros() - bounceTime) >= minimumMicros
                || neighbor.micros() == bounceTime;

        // Can we overwrite a weak neighbor?
        int neighborWeight = (neighbor == null) ? 0 : neighbor.weight();
        boolean weightSafe = neighborWeight < weight;

        return directionOk && reasonable && (spaceSafe || weightSafe);
    }

    private void applyBounce(TreeSet<GridPoint> grid, long time, Candidate c, long minimumMicros) {

        // Clean up Neighbors (Make space for the Start)

        GridPoint key = new GridPoint(time, false, 0);

        GridPoint ceil = grid.ceiling(key);
        if (ceil != null) {
            if (ceil.micros() == time) {
                if (ceil.weight() < c.weight) {
                    grid.remove(ceil);
                    if (logNotes.isLoggable(Level.FINEST)) {
                        logNotes.finest("Overwriting weak grid line at " + Util.formatDurationM(ceil.micros()));
                    }
                } else {
                    // Strong exact match: We cannot bounce "over" it.
                    // Instead, we snap to this existing line.
                    // We must still update the note and handle the end time here.
                    updateNoteAndGridEnd(grid, time, c, minimumMicros);
                    return;
                }
            } else if (Math.abs(ceil.micros() - time) < minimumMicros) {
                // Close neighbor: Check weight
                if (ceil.weight() < c.weight) {
                    grid.remove(ceil);
                    if (logNotes.isLoggable(Level.FINEST)) logNotes.finest("Overwriting weak grid line at " + Util.formatDurationM(ceil.micros()));
                } else {
                    // Neighbor is strong. Snap to it instead of creating new bounce.
                    updateNoteAndGridEnd(grid, ceil.micros(), c, minimumMicros);
                    return;
                }
            }
        }

        GridPoint floor = grid.floor(key);
        if (floor != null && Math.abs(time - floor.micros()) < minimumMicros && floor.micros() != time) {
            if (floor.weight() < c.weight) {
                grid.remove(floor);
            } else {
                // Neighbor is strong. Snap to it.
                updateNoteAndGridEnd(grid, floor.micros(), c, minimumMicros);
                return;
            }
        }

        grid.add(new GridPoint(time, true, c.weight));

        updateNoteAndGridEnd(grid, time, c, minimumMicros);

        if (logNotes.isLoggable(Level.FINEST)) {
            logNotes.finest("Bounced " + Util.formatDurationM(time));
        }
    }

    private void updateNoteAndGridEnd(TreeSet<GridPoint> grid, long time, Candidate c, long minimumMicros) {
        if (c.note == null) return;

        long duration = c.note().endABCMicros - c.note().startABCMicros;
        long originalStart = c.note().startABCMicros;

        // Update note
        c.note().startABCMicros = time;
        c.note().endABCMicros = time + duration;

        // Only add the New End if we moved FORWARD (Delay).
        // Forward bounce risks making the note too short if we don't move the end line.
        if (time > originalStart) {
            long newEnd = c.note().endABCMicros;
            GridPoint endKey = new GridPoint(newEnd, false, 0);

            // A. Check Ceiling (Future Neighbor) for the END
            // If a grid line exists shortly after our new end, snap to it.
            GridPoint ceil = grid.ceiling(endKey);
            if (ceil != null && Math.abs(ceil.micros() - newEnd) < minimumMicros) {
                c.note().endABCMicros = ceil.micros();
                return; // Snapped to existing. Done.
            }

            // B. Check Floor (Past Neighbor / Ghost End) for the END
            // If a grid line exists shortly before our new end, it's a conflict.
            GridPoint floor = grid.floor(endKey);
            if (floor != null && Math.abs(newEnd - floor.micros()) < minimumMicros) {
                if (floor.weight() < 2) {
                    grid.remove(floor); // Remove weak neighbor (e.g. the Ghost Old End)
                } else {
                    return; // Neighbor is strong. We can't add our end. Snap will handle it later.
                }
            }

            // C. Add the New End (Weight 2 to beat remaining Ghosts)
            grid.add(new GridPoint(newEnd, false, 2));
        }
    }

    /**
     * Part of multi-stage organic path
     *
     * @param barTicks bar tick duration
     * @param idealMicros origin point
     * @param maxDistanceDown max distance down in micros from origin point
     * @param maxDistanceUp max distance up in micros from origin point
     * @return nearest midi bar line in micros
     */
    private long closestBarMicrosABC(long barTicks, long idealMicros, long maxDistanceDown, long maxDistanceUp) {
        assert idealMicros > 0L && maxDistanceDown >= 0L && maxDistanceUp >= 0L && idealMicros - maxDistanceDown >= 0L
                :"idealMicros=" + idealMicros + ", maxDistanceDown=" + maxDistanceDown + ", maxDistanceUp=" + maxDistanceUp+" idealMicros-maxDistanceDown="+(idealMicros - maxDistanceDown);
        long tick = qtm.microsToTickABCOrganic(idealMicros);
        long down = (tick / barTicks) * barTicks;
        long up = down + barTicks;
        long middle = down + barTicks / 2;
        boolean downClosest = tick - down < up - tick;

        long upBarMicros = qtm.tickToMicrosABCOrganic(up);
        long downBarMicros = qtm.tickToMicrosABCOrganic(down);
        long middleBarMicros = qtm.tickToMicrosABCOrganic(middle);

        boolean upWithin = upBarMicros <= idealMicros + maxDistanceUp;
        boolean downWithin = downBarMicros >= idealMicros - maxDistanceDown;
        boolean middleWithin = (middleBarMicros >= idealMicros - maxDistanceDown) && (middleBarMicros <= idealMicros + maxDistanceUp);

        if (upWithin && downWithin) {
            //System.err.println("closestBarMicrosABC: &&");
            return downClosest ? downBarMicros : upBarMicros;
        } else if (upWithin) {
            //System.err.println("closestBarMicrosABC: upWithin");
            return upBarMicros;
        } else if (downWithin) {
            //System.err.println("closestBarMicrosABC: downWithin");
            return downBarMicros;
        } else if (middleWithin) {
            //System.err.println("closestBarMicrosABC: middleWithin");
            return middleBarMicros;
        }
        //System.err.println("closestBarMicrosABC: ideal");
        return idealMicros;
    }
	
	/**
	 * 
	 * Part of multi-stage organic path
	 * 
	 */
	private long getMaxStartShiftMicros(long noteDuration, long minimumMicros) {
		// If start is needed to be moved more than this return value then note will be deleted
		long minimums = noteDuration/minimumMicros;
		if (minimums < 1L) return 36L*1000L;// Very short note we wont move the start more than 36 ms
		if (minimums <= 2L) return 45L*1000L;// Short note we also wont move the start more than 45 ms
		return minimumMicros;// Longer note we wont move the start more than 60 ms
	}
	
	/**
	 * 
	 * Part of organic multi-stage 1 path
	 * 
	 */
	private List<AbcNoteEvent> snapNotesToGrid(List<AbcNoteEvent> notes, NavigableSet<Long> grid, long minimumMicros, AbcPart part) {
		List<AbcNoteEvent> snappedNotes = new ArrayList<>(notes.size());

        part.numberOfRemovedNotesFromFitting = 0;
		int gridDeletion = 0;
        /*
        for (Long step : grid) {
            if (step > notes.getLast().getEndMicros() + 1000000) break;
            System.out.println("Grid point "+step+" micros");
        }
        */
	    for (AbcNoteEvent note : notes) {
            //System.out.println("Note "+note.note.id+": " + note.startABCMicros + " to " + note.endABCMicros+" micros");

	        Long floor = grid.floor(note.startABCMicros);
	        Long ceiling = grid.ceiling(note.startABCMicros);
	        
	        long candidateStart;
	        if (floor == null && ceiling == null) {
	        	continue; // fallback: no grid available
	        } else if (floor == null) {
	        	if (logNotes.isLoggable(Level.FINER)) logNotes.finer("Start at ceiling (floor null) "+Util.formatDurationM(ceiling)+" for "+Util.formatDurationM(note.startABCMicros));
	            candidateStart = ceiling;
	        } else if (ceiling == null) {
                if (logNotes.isLoggable(Level.FINER)) logNotes.finer("Start at floor (ceil null) "+Util.formatDurationM(floor)+" for "+Util.formatDurationM(note.startABCMicros));
	            candidateStart = floor;
	        } else {
	            if (Math.abs(note.startABCMicros - floor) <= Math.abs(note.startABCMicros - ceiling)) {
	            	logNotes.finer("Start at floor "+Util.formatDurationM(floor)+" for ("+Util.formatDurationM(note.startABCMicros)+" - "+Util.formatDurationM(note.endABCMicros)+") ceiling="+Util.formatDurationM(ceiling));
	                candidateStart = floor;
	            } else {
	            	logNotes.finer("Start at ceiling "+Util.formatDurationM(ceiling)+" for ("+Util.formatDurationM(note.startABCMicros)+" - "+Util.formatDurationM(note.endABCMicros)+") floor="+Util.formatDurationM(floor));
	                candidateStart = ceiling;
	            }
	        }
	        // Check that the shift does not exceed max relative to the original start.
	        if (Math.abs(candidateStart - note.startABCMicros) > getMaxStartShiftMicros(note.endABCMicros-note.startABCMicros, minimumMicros)) {
                if (logNotes.isLoggable(Level.FINER)) logNotes.finer("dropping1 "+Util.formatDurationM(note.startABCMicros)+" - "+Util.formatDurationM(note.endABCMicros));
                gridDeletion++;
	            continue;
	        }
	        note.setStartTick(qtm.microsToTickABCOrganic(candidateStart));
	        note.startABCMicros = candidateStart;
	        
	        // Snap note end
	        // We want a grid line that is after start
	        floor = grid.floor(note.endABCMicros);
	        ceiling = grid.ceiling(note.endABCMicros);
	        Long candidateEnd;
	        if (ceiling != null && candidateStart == ceiling) {
                if (logNotes.isLoggable(Level.FINER)) logNotes.finer("dropping2 "+Util.formatDurationM(note.startABCMicros)+" - "+Util.formatDurationM(note.endABCMicros));
                gridDeletion++;
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
	        	if (candidateStart < floor && Math.abs(note.endABCMicros - floor) <= Math.abs(note.endABCMicros - ceiling)) {
	        		candidateEnd = floor;
	            } else {
	            	candidateEnd = ceiling;
	            }
	        }
	        
	        if (candidateEnd == null) {
	        	// ceiling == null and ( floor == null or taken by start )
                if (logNotes.isLoggable(Level.FINER)) logNotes.finer("dropping3 "+Util.formatDurationM(note.startABCMicros)+" - "+Util.formatDurationM(note.endABCMicros));
                gridDeletion++;
	        	continue;
	        }
	        
	        //	Check that the shift does not exceed max relative to the original end.
	        if (part.getInstrument().sustainable && Math.abs(candidateEnd - note.endABCMicros) > minimumMicros * 3L/2L) {//90 ms
	        	//System.out.println(parts.get(0).getAbcSong().getTitle()+": End grid was too far from note end:"+(Math.abs(candidateEnd - note.origEndABCMicros)/(double)minimumMicros));
                if (logNotes.isLoggable(Level.FINER)) logNotes.finer("dropping4 "+Util.formatDurationM(note.startABCMicros)+" - "+Util.formatDurationM(note.endABCMicros));
                gridDeletion++;
	            continue;
	        }

	        note.setEndTick(qtm.microsToTickABCOrganic(candidateEnd));
	        note.endABCMicros = candidateEnd;
	        
	        if (note.endABCMicros - note.startABCMicros <= 0L || note.getEndTick() - note.getStartTick() <= 0L) {
                if (logNotes.isLoggable(Level.FINER)) logNotes.finer("dropping5 "+Util.formatDurationM(note.startABCMicros)+" - "+Util.formatDurationM(note.endABCMicros));
                gridDeletion++;
	        	continue;
	        }
	        assert note.endABCMicros - note.startABCMicros > 0;
	        snappedNotes.add(note);
	    }
        part.numberOfRemovedNotesFromFitting = gridDeletion;

        /*
        for (AbcNoteEvent note : snappedNotes) {
            System.out.println("Snapped note " + note.note.id + ": " + note.startABCMicros + " to " + note.endABCMicros + " micros");
        }
        */
	    return snappedNotes;
	}

    /**
     *
     * Part of organic multi-stage 2 path
     *
     */
    private List<AbcNoteEvent> snapNotesToGrid3(List<AbcNoteEvent> notes, NavigableSet<Long> grid, long minimumMicros, AbcPart part) {
        List<AbcNoteEvent> snappedNotes = new ArrayList<>(notes.size());
        AbcNoteEvent[] lastNoteOfPitch = new AbcNoteEvent[129];
        int gridDeletion = 0;

        for (AbcNoteEvent note : notes) {
            // Notes condemned by the grid generator
            if (note.startABCMicros == Long.MIN_VALUE) {
                gridDeletion++;
                continue;
            }

            long candidateStart = note.startABCMicros;
            long candidateEnd = note.endABCMicros;
            long originalDuration = note.initEndABCMicros - note.initStartABCMicros;

            // Check that the shift does not exceed max relative to the original start.
            // Protects against events getting dragged across massive rests
            if (Math.abs(candidateStart - note.initStartABCMicros) > getMaxStartShiftMicros(originalDuration, minimumMicros)) {
                gridDeletion++;
                continue;
            }

            //	Check that the shift does not exceed max relative to the original end.
            if (part.getInstrument().sustainable && Math.abs(candidateEnd - note.initEndABCMicros) > minimumMicros * 3L / 2L) {
                gridDeletion++;
                continue;
            }

            // Duration recovery
            // If the start bounced forward past the end, we must grab the next safe grid point
            if (candidateEnd <= candidateStart) {
                Long nextGridPoint = grid.higher(candidateStart);
                if (nextGridPoint != null) {
                    candidateEnd = nextGridPoint;
                } else {
                    gridDeletion++;
                    continue; // Cannot recover.
                }
            }

            // Resolve same-pitch overlaps
            int pitch = note.note.id;
            if (pitch == -1) pitch = 128;
            AbcNoteEvent prevNote = lastNoteOfPitch[pitch];

            if (prevNote != null && prevNote.endABCMicros > candidateStart) {
                if (prevNote.startABCMicros >= candidateStart) {
                    // The new note completely eclipses the old one. Delete the old one.
                    snappedNotes.remove(prevNote);
                    gridDeletion++;
                } else {
                    // Truncate the previous note to the new note's start
                    prevNote.endABCMicros = candidateStart;
                    prevNote.setEndTick(Math.max(prevNote.getStartTick() + 1, qtm.microsToTickABCOrganic(candidateStart)));
                }
            }

            note.setStartTick(qtm.microsToTickABCOrganic(candidateStart));
            note.startABCMicros = candidateStart;
            note.setEndTick(qtm.microsToTickABCOrganic(candidateEnd));
            note.endABCMicros = candidateEnd;

            boolean assertionsEnabled = false;
            assert assertionsEnabled = true;

            if (assertionsEnabled) {
                assert grid.contains(note.startABCMicros) : "Start time " + note.startABCMicros + " is not on the grid!";
                assert grid.contains(note.endABCMicros) : "End time " + note.endABCMicros + " is not on the grid!";
                assert note.endABCMicros > note.startABCMicros : "Note duration was <= 0!";
                assert (note.endABCMicros - note.startABCMicros) >= minimumMicros : "Note duration " + (note.endABCMicros - note.startABCMicros) + " is shorter than minimumMicros!";

                if (prevNote != null && snappedNotes.contains(prevNote)) {
                    assert prevNote.endABCMicros <= note.startABCMicros : "Same-pitch overlap detected on pitch " + pitch;
                }
            }

            snappedNotes.add(note);
            lastNoteOfPitch[pitch] = note;
        }

        part.numberOfRemovedNotesFromFitting = gridDeletion;
        return snappedNotes;
    }

    /**
     *
     * Part of organic multi-stage 2 path for sustained instruments
     *
     */
    private List<AbcNoteEvent> snapNotesToGridSustained(List<AbcNoteEvent> notes, NavigableSet<Long> grid, long minimumMicros, AbcPart part) {
        List<AbcNoteEvent> snappedNotes = new ArrayList<>(notes.size());
        AbcNoteEvent[] lastNoteOfPitch = new AbcNoteEvent[129]; // Tracks the last note for each MIDI pitch
        int gridDeletion = 0;

        for (AbcNoteEvent note : notes) {
            long originalDuration = note.endABCMicros - note.startABCMicros;

            // Snap Start to nearest grid point
            Long floor = grid.floor(note.startABCMicros);
            Long ceiling = grid.ceiling(note.startABCMicros);
            long candidateStart = note.startABCMicros;

            if (floor != null && ceiling != null) {
                candidateStart = (note.startABCMicros - floor <= ceiling - note.startABCMicros) ? floor : ceiling;
            } else if (floor != null) {
                candidateStart = floor;
            } else if (ceiling != null) {
                candidateStart = ceiling;
            } else {
                continue; // No grid points exist at all
            }

            // Shield against snapping across massive gaps (like a 2-minute rest)
            if (Math.abs(candidateStart - note.startABCMicros) > getMaxStartShiftMicros(originalDuration, minimumMicros)) {
                gridDeletion++;
                continue;
            }

            // Snap end to nearest grid point
            long expectedEnd = note.endABCMicros;
            Long endFloor = grid.floor(expectedEnd);
            Long endCeiling = grid.ceiling(expectedEnd);
            long candidateEnd = expectedEnd;

            if (endFloor != null && endCeiling != null) {
                candidateEnd = (expectedEnd - endFloor <= endCeiling - expectedEnd) ? endFloor : endCeiling;
            } else if (endFloor != null) {
                candidateEnd = endFloor;
            } else if (endCeiling != null) {
                candidateEnd = endCeiling;
            }

            // Prevent the end of a note from dragging long
            if (Math.abs(candidateEnd - expectedEnd) > minimumMicros * 3L / 2L) {
                gridDeletion++;
                continue;
            }

            // Enforce valid duration on the grid
            if (candidateEnd <= candidateStart) {
                Long higher = grid.higher(candidateStart);
                Long lower = grid.lower(candidateStart);

                // Fetch prevNote early to protect it from backward expansion collision
                int pitch = note.note.id;
                if (pitch == -1) pitch = 128;
                AbcNoteEvent prevNote = lastNoteOfPitch[pitch];

                boolean canExpandBackward = (lower != null);
                if (canExpandBackward && prevNote != null && lower < prevNote.endABCMicros) {
                    canExpandBackward = false;
                }

                long distHigher = (higher != null) ? (higher - candidateStart) : Long.MAX_VALUE;
                long distLower = canExpandBackward ? (candidateStart - lower) : Long.MAX_VALUE;

                if (higher == null && !canExpandBackward) {
                    gridDeletion++;
                    continue;
                }

                long maxAcceptableDuration = Math.max(originalDuration * 5L/4L, minimumMicros * 2L);
                boolean higherIsTooLong = distHigher > maxAcceptableDuration;
                boolean lowerIsTooLong = distLower > maxAcceptableDuration;

                if (lowerIsTooLong) canExpandBackward = false;

                if (!canExpandBackward && higherIsTooLong) {
                    gridDeletion++;
                    continue; // Cannot expand safely in either direction. Drop the event.
                }

                // Expand into the adjacent grid interval that best matches original duration
                if (distHigher != Long.MAX_VALUE && !higherIsTooLong && (!canExpandBackward || Math.abs(distHigher - originalDuration) <= Math.abs(distLower - originalDuration))) {
                    candidateEnd = higher;
                } else if (canExpandBackward) {
                    long oldStart = candidateStart;
                    candidateStart = lower;
                    candidateEnd = oldStart;
                } else {
                    gridDeletion++;
                    continue; // Failsafe drop
                }
            }

            // Resolve same-pitch overlap
            int pitch = note.note.id;
            if (pitch == -1) pitch = 128;
            AbcNoteEvent prevNote = lastNoteOfPitch[pitch];

            if (prevNote != null && prevNote.endABCMicros > candidateStart) {
                if (prevNote.startABCMicros >= candidateStart) {
                    // The previous note is completely eclipsed by the new one on the grid.
                    snappedNotes.remove(prevNote);
                    gridDeletion++;
                } else {
                    // Truncate the previous note to end exactly when this new one begins.
                    prevNote.endABCMicros = candidateStart;
                    prevNote.setEndTick(qtm.microsToTickABCOrganic(candidateStart));
                    assert prevNote.endABCMicros - prevNote.startABCMicros > 0;
                }
            }

            note.setStartTick(qtm.microsToTickABCOrganic(candidateStart));
            note.startABCMicros = candidateStart;
            note.setEndTick(qtm.microsToTickABCOrganic(candidateEnd));
            note.endABCMicros = candidateEnd;

            assert note.endABCMicros - note.startABCMicros > 0;

            snappedNotes.add(note);
            lastNoteOfPitch[pitch] = note;
        }

        /*
        for (AbcNoteEvent note : snappedNotes) {
            System.out.println("sus_Snapped note " + note.note.id + ": " + note.startABCMicros + " to " + note.endABCMicros + " micros");
        }
        */

        part.numberOfRemovedNotesFromFitting = gridDeletion;
        return snappedNotes;
    }

    /**
     *
     * Part of organic multi-stage 2 path for plucked/percussive instruments
     *
     */
    private List<AbcNoteEvent> snapNotesToGridFixed(List<AbcNoteEvent> notes, NavigableSet<Long> grid, long minimumMicros, AbcPart part) {
        List<AbcNoteEvent> snappedNotes = new ArrayList<>(notes.size());
        AbcNoteEvent[] lastNoteOfPitch = new AbcNoteEvent[129];
        int gridDeletion = 0;

        // Short uniform duration for plucked/percussive instruments.

        for (AbcNoteEvent note : notes) {
            long originalDuration = note.endABCMicros - note.startABCMicros;
            // Snap Start (nearest-neighbor)
            Long floor = grid.floor(note.startABCMicros);
            Long ceiling = grid.ceiling(note.startABCMicros);
            long candidateStart = note.startABCMicros;

            if (floor != null && ceiling != null) {
                candidateStart = (note.startABCMicros - floor <= ceiling - note.startABCMicros) ? floor : ceiling;
            } else if (floor != null) {
                candidateStart = floor;
            } else if (ceiling != null) {
                candidateStart = ceiling;
            } else {
                continue; // No grid points exist at all
            }

            // Shield against snapping events across massive rests
            if (Math.abs(candidateStart - note.startABCMicros) > getMaxStartShiftMicros(originalDuration, minimumMicros)) {
                gridDeletion++;
                continue;
            }

            // Apply Duration (Snap end to the next available grid point)
            Long nextGridPoint = grid.higher(candidateStart);
            long candidateEnd;
            if (nextGridPoint != null) {
                candidateEnd = nextGridPoint;
            } else {
                // Fallback for the absolute last note on the grid
                candidateEnd = candidateStart + minimumMicros;
            }

            // Resolve overlaps
            int pitch = note.note.id;
            if (pitch == -1) pitch = 128;
            AbcNoteEvent prevNote = lastNoteOfPitch[pitch];

            if (prevNote != null && prevNote.endABCMicros > candidateStart) {
                if (prevNote.startABCMicros >= candidateStart) {
                    // The notes crossed paths or snapped to the exact same point.
                    // The new one entirely eclipses the previous note.
                    snappedNotes.remove(prevNote);
                    gridDeletion++;
                } else {
                    // Truncate previous note to end exactly when this new one begins
                    prevNote.endABCMicros = candidateStart;
                    prevNote.setEndTick(Math.max(prevNote.getStartTick() + 1, qtm.microsToTickABCOrganic(candidateStart)));
                    assert prevNote.endABCMicros - prevNote.startABCMicros > 0;
                }
            }

            note.setStartTick(qtm.microsToTickABCOrganic(candidateStart));
            note.startABCMicros = candidateStart;
            note.setEndTick(Math.max(note.getStartTick() + 1L, qtm.microsToTickABCOrganic(candidateEnd)));
            note.endABCMicros = candidateEnd;

            assert note.endABCMicros - note.startABCMicros > 0;

            snappedNotes.add(note);
            lastNoteOfPitch[pitch] = note;
        }

        /*
        for (AbcNoteEvent note : snappedNotes) {
            System.out.println("fix_Snapped note " + note.note.id + ": " + note.startABCMicros + " to " + note.endABCMicros + " micros");
        }
        */

        part.numberOfRemovedNotesFromFitting = gridDeletion;
        return snappedNotes;
    }

    /**
     * Filters out notes that have "collapsed" onto the same grid line
     * and create unwanted dissonance (e.g. chromatic slides crushed into chords).
     */
    private List<AbcNoteEvent> removeCollapsedDissonance(List<AbcNoteEvent> events, AbcPart part) {
        part.numberOfRemovedNotesForSafety = 0;

        if (part.getInstrument().isPercussion) return events;//drums and cowbells only

        // TODO: Handle if notes were zero duration in midi file. Its mostly drums this happens for, but not always.

        List<AbcNoteEvent> cleaned = new ArrayList<>();
        // Group notes by their new snapped start time
        Map<Long, List<AbcNoteEvent>> groups = new HashMap<>();

        for (AbcNoteEvent ne : events) {
            if (ne.note == Note.REST) {
                cleaned.add(ne);
                continue;
            }
            groups.computeIfAbsent(ne.getStartTick(), k -> new ArrayList<>()).add(ne);
        }

        for (List<AbcNoteEvent> cluster : groups.values()) {
            if (cluster.size() < 2) {
                cleaned.addAll(cluster);
                continue;
            }

            // Sort by original importance (length/velocity) so we drop the weak ones
            cluster.sort(Comparator.comparingLong((AbcNoteEvent e) -> e.endABCMicros - e.startABCMicros)
                    .thenComparingInt(AbcNoteEvent::getVelocity).reversed());

            List<AbcNoteEvent> survivors = new ArrayList<>();

            for (AbcNoteEvent candidate : cluster) {
                if (candidate.getOrigBend() != null || (candidate.origNote instanceof BentMidiNoteEvent) || candidate.origNote == null) {
                    survivors.add(candidate);
                    continue;
                }

                boolean keepCandidate = true;
                for (AbcNoteEvent survivor : survivors) {
                    if (survivor.getOrigBend() != null || survivor.origNote instanceof BentMidiNoteEvent || survivor.origNote == null) {
                        continue;
                    }

                    // Check if they were originally sequential
                    long overlapMicros = getOrigOverlap(candidate, survivor);

                    // If they overlapped significantly in the original, they are intended harmony/dissonance.
                    if (overlapMicros > 20_000L) {
                        continue; // Keep both, don't check for dissonance
                    }

                    // Check for dissonance
                    int interval = Math.abs(candidate.note.id - survivor.note.id);
                    if (interval <= 2 && interval > 0) { // Major 2nd or minor 2nd

                        long durCandidate = candidate.endABCMicros - candidate.startABCMicros;
                        long durSurvivor = survivor.endABCMicros - survivor.startABCMicros;

                        if (durCandidate > 100_000L && durSurvivor > 100_000L) {
                            continue; // Keep both
                        }

                        // They crashed into each other and sound bad.
                        // Since we sorted by velocity/importance, survivor is better.
                        // Drop candidate.
                        keepCandidate = false;
                        part.numberOfRemovedNotesForSafety++;
                        break;
                    }
                }
                if (keepCandidate) {
                    survivors.add(candidate);
                }
            }
            cleaned.addAll(survivors);
        }

        Collections.sort(cleaned);
        return cleaned;
    }

    private long getOrigOverlap(AbcNoteEvent candidate, AbcNoteEvent survivor) {
        long startC = candidate.origNote.getStartMicros();//relying on its datacache to be a SequenceDataCache,
        long endC   = candidate.origNote.getEndMicros();//  which it is for MidiNoteEvents.

        long startS = survivor.origNote.getStartMicros();
        long endS   = survivor.origNote.getEndMicros();

        long overlap = 0;
        if (startC < startS) {
            overlap = endC - startS; // Candidate started first
        } else {
            overlap = endS - startC; // Survivor started first
        }
        return overlap;
    }

    /**
	 * 
	 * Part of multi-stage organic path
	 * 
	 */
	public List<Chord> chordifyOrganic(List<AbcNoteEvent> events, NavigableSet<Long> grid, AbcPart part, boolean useRestToShortenChords, long minimumMicros) {
        part.numberOfRemovedNotesFromPruning = 0;

        // when cutting up too long notes, this is the minimum buffer they are allowed to exceed max with.
        long maxSustainBuffer = minimumMicros * 2;
        long maxSustain = LotroInstrumentSampleDuration.getSafeDuration(part.getInstrument());

		boolean assertionsEnabled = false;
		assert assertionsEnabled = true;
		
	    List<ChordOrganic> chords = new ArrayList<>(events.size() / 2);
	    
	    if (events.isEmpty()) return new ArrayList<>();
	    
	    TreeMap<Long,Long> gridTicks = new TreeMap<>();
		for (long micros : grid) {
			gridTicks.put(micros, qtm.microsToTickABCOrganic(micros));
		}
		
		List<AbcNoteEvent> eventSegments = new ArrayList<>(events.size());
		for (AbcNoteEvent ne : events) {
	    	List<AbcNoteEvent> segments = splitToGrid(ne, gridTicks, part, useRestToShortenChords, minimumMicros);
	    	eventSegments.addAll(segments);
		}
		
		Collections.sort(eventSegments);
		
		if (assertionsEnabled) {
			AbcNoteEvent last = null;
			for (AbcNoteEvent ne : eventSegments) {
				if (last != null) {
					assert ne.getStartTick() >= last.getStartTick();
					assert ne.getEndTick() >= last.getEndTick() || ne.getStartTick() > last.getStartTick();
					assert ne.startABCMicros >= last.startABCMicros;
					assert ne.endABCMicros >= last.endABCMicros || ne.startABCMicros > last.startABCMicros;
                    assert ne.endABCMicros - ne.startABCMicros <= maxSustain + maxSustainBuffer;
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
		AbcNoteEvent lastEndNote = null;// prev chords shortest note if not a rest else null
		AbcNoteEvent prevChordsShortest = null;// prev chords shortest note
		long lastStartMicros = -1L;
		boolean firstLoop = true;
        for (AbcNoteEvent noteSegment : eventSegments) {
            if (!firstLoop) {
                if (noteSegment.startABCMicros > lastStartMicros) {

                    // new chord
                    // here we rely on the sorting to be shortest notes first. (beside sorting by start tick)
                    if (lastEndNote != null && noteSegment.startABCMicros > lastEndNote.endABCMicros) {
                        assert lastStartMicros == lastEndNote.startABCMicros;
                        assert lastEndMicros <= lastEndNote.endABCMicros;
                        // prev chord had a note and that note end before this starts,
                        // so we dont need the short rest(s) in prev chord
                        restTrash.addAll(potentialTrash);
                        // insert rest from last note/bridge to current segment
                        AbcNoteEvent rest = new AbcNoteEvent(Note.REST, 64, lastEndNote.getEndTick(), noteSegment.getStartTick(), qtm, null);
                        rest.startABCMicros = lastEndNote.endABCMicros;
                        rest.endABCMicros = noteSegment.startABCMicros;
                        List<AbcNoteEvent> segments = splitToGrid(rest, gridTicks, part, useRestToShortenChords, minimumMicros);
                        rests.addAll(segments);
                    } else if (prevChordsShortest != lastEndNote && lastEndNote != null && noteSegment.startABCMicros == lastEndNote.endABCMicros) {
                        // prev chord had a shortest actual note and that note ends when this starts,
                        // so we don't need the short rest(s)
                        restTrash.addAll(potentialTrash);

                        assert prevChordsShortest.getLengthTicks() <= lastEndNote.getLengthTicks();
                        assert prevChordsShortest.startABCMicros == lastEndNote.startABCMicros;
                    } else if (noteSegment.startABCMicros > lastEndMicros) {
                        // insert rest from last short rest to current segment
                        AbcNoteEvent rest = new AbcNoteEvent(Note.REST, 64, lastEndTick, noteSegment.getStartTick(), qtm, null);
                        rest.startABCMicros = lastEndMicros;
                        rest.endABCMicros = noteSegment.startABCMicros;
                        List<AbcNoteEvent> segments = splitToGrid(rest, gridTicks, part, useRestToShortenChords, minimumMicros);
                        rests.addAll(segments);

                    } else {
                        assert noteSegment.startABCMicros == lastEndMicros;
                    }
                    prevChordsShortest = noteSegment;//The shortest note/rest in the new chord

                    if (noteSegment.note != Note.REST) lastEndNote = noteSegment;
                    else lastEndNote = null;

                    potentialTrash = new ArrayList<>();
                    if (noteSegment.note == Note.REST) potentialTrash.add(noteSegment);

                    if (noteSegment.endABCMicros <= lastEndMicros) {
                        logNotes.severe("Part "+part.getPartNumber() + ": noteSegment.endABCMicros > lastEndMicros = " + (noteSegment.endABCMicros > lastEndMicros) + " duraMicros="+(noteSegment.endABCMicros-noteSegment.startABCMicros));
                    }
                    lastEndMicros = noteSegment.endABCMicros;
                    lastEndTick = noteSegment.getEndTick();

                } else if (noteSegment.note != Note.REST && lastEndNote == null) {
                    // second or later in this chord
                    // is shortest actual note in this chord
                    assert prevChordsShortest.getLengthTicks() <= noteSegment.getLengthTicks();
                    assert prevChordsShortest.startABCMicros == noteSegment.startABCMicros;
                    assert prevChordsShortest.endABCMicros <= noteSegment.endABCMicros;
                    lastEndNote = noteSegment;
                } else if (noteSegment.note == Note.REST) {
                    // There might be more than 1 rest that starts at same time,
                    // we will either remove all of them or let pruning do its work.
                    potentialTrash.add(noteSegment);
                } else {
                    // not shortest note and not rest, we ignore it
                }
            } else {
                if (noteSegment.endABCMicros <= lastEndMicros) {
                    logNotes.severe("noteSegment.origEndABCMicros > lastEndMicros = " + (noteSegment.endABCMicros > lastEndMicros));
                }
                lastEndMicros = noteSegment.endABCMicros;
                lastEndTick = noteSegment.getEndTick();
                prevChordsShortest = noteSegment;
                if (noteSegment.note != Note.REST) {
                    lastEndNote = noteSegment;
                } else {
                    lastEndNote = null;
                    potentialTrash.add(noteSegment);
                }
            }
            lastStartMicros = noteSegment.startABCMicros;
            firstLoop = false;
        }
		eventSegments.addAll(rests);
		eventSegments.removeAll(restTrash);
		if (!restTrash.isEmpty()) logNotes.finest("Trashing rests:"+restTrash.size());
		
		Collections.sort(eventSegments);
		
		if (assertionsEnabled) {
			AbcNoteEvent prev = null;
			AbcNoteEvent prevShortest = null;
	    	for (AbcNoteEvent note : eventSegments) {
                assert prev == null || prev.startABCMicros <= note.startABCMicros
                    :note.note+": "+prev.startABCMicros+"-"+prev.endABCMicros+" "+note.note+": "+note.startABCMicros+"-"+note.endABCMicros+" compare="+prev.compareTo(note);
                assert prevShortest == null || prevShortest.endABCMicros == note.startABCMicros || prevShortest.startABCMicros == note.startABCMicros
                        :(note.startABCMicros-prevShortest.endABCMicros)+" gap detected between "+prevShortest.toStringMicros()+" and "+note.toStringMicros();
	    		if (prev == null || prev.startABCMicros < note.startABCMicros) {
	    			prevShortest = note;
	    		}
				prev = note;
	    	}
	    }
		
		//
		//  Now put the notes and rests into chords
		//
        int notesPruned = 0;
	    ChordOrganic curChord = null;
	    AbcNoteEvent prevNote = null;
	    int startI = 0;
	    for (int i = 0; i < eventSegments.size(); i++) {
	    	AbcNoteEvent noteSegment = eventSegments.get(i);
	    	if (curChord == null) {
	    		curChord = new ChordOrganic(noteSegment, qtm);
	    		chords.add(curChord);
	    	} else if (curChord.getShortest().startABCMicros != noteSegment.startABCMicros) {
	    		assert curChord.getShortest().startABCMicros < noteSegment.startABCMicros;
	    		assert prevNote == null || prevNote.endABCMicros == noteSegment.startABCMicros:prevNote.endABCMicros+" != "+noteSegment.startABCMicros;
	    		assert curChord.getShortest().endABCMicros == noteSegment.startABCMicros:curChord.getShortest().endABCMicros+" != "+noteSegment.startABCMicros;
	    		//assert prevNote == null || prevNote.getEndTick() == noteSegment.getStartTick():prevNote.getEndTick()+" != "+noteSegment.getStartTick();
	    		//assert curChord.getEndTick() == noteSegment.getStartTick():curChord.getEndTick()+" != "+noteSegment.getStartTick();
	    		
	    		boolean removedStuff = unmixRestAndNotes(part, eventSegments, curChord, useRestToShortenChords);
	    		
	    		if (removedStuff) {
					// One of the notes that was removed might be any in this chord,
					// so we go steps back and re-process
					i = startI-1;
					chords.remove(curChord);
					curChord = null;
					continue;
				}
	    			    		
				List<AbcNoteEvent> deadnotes = curChord.prune(part.getInstrument().sustainable,
						part.getInstrument() == LotroInstrument.BASIC_DRUM, part.getInstrument().isPercussion, part, useRestToShortenChords);
				breakTies(deadnotes);
				eventSegments.removeAll(deadnotes);
                notesPruned += deadnotes.size();
				
				if (!deadnotes.isEmpty()) {
					// we go steps back and re-process
					i = startI-1;
					chords.remove(curChord);
					curChord = null;					
					continue;
				}
				
	    		curChord = new ChordOrganic(noteSegment, qtm);
	    		chords.add(curChord);
	    		prevNote = noteSegment;// since they are also sorted by endTick, this is the shortest in the chord.
	    		startI = i;
	    	} else {
	    		curChord.add(noteSegment);
	    	}
	    }
	    if (curChord != null) {
	    	unmixRestAndNotes(part, eventSegments, curChord, useRestToShortenChords);
	    	List<AbcNoteEvent> deadnotes = curChord.prune(part.getInstrument().sustainable,
					part.getInstrument() == LotroInstrument.BASIC_DRUM, part.getInstrument().isPercussion, part);
	    	breakTies(deadnotes);
	    	eventSegments.removeAll(deadnotes);
            notesPruned += deadnotes.size();
		}

        part.numberOfRemovedNotesFromPruning = notesPruned;
	    
	    if (assertionsEnabled) {
	    	ChordOrganic prev = null;
	    	for (ChordOrganic chord : chords) {
	    		if (prev != null) {
	    			for (AbcNoteEvent ne : chord.getNotes()) {
	    				assert chord.getNotes().getFirst().startABCMicros == ne.startABCMicros;
	    				assert chord.getShortest().endABCMicros <= ne.endABCMicros;
	    			}
	    			assert prev.getShortest().endABCMicros == chord.getNotes().getFirst().startABCMicros:prev.getShortest().endABCMicros+" != "+chord.getNotes().getFirst().startABCMicros;
	    		}
	    		prev = chord;
	    	}
	    }
	    
	    if (useRestToShortenChords) {
			/*
			 * It can happen that a note that is longer than the chord
			 * is also present in next chord. And if there is a
			 * volume difference between the chord, lotro will
			 * silence entire part. So to prevent that, we shorten
			 * some notes.
			 */
	    	List<AbcNoteEvent> notesOn = new ArrayList<>();
			for (ChordOrganic chord : chords) {
				for (AbcNoteEvent curr : chord.getNotes()) {
					for (AbcNoteEvent pre : notesOn) {
						if (pre.note == curr.note) {
							pre.endABCMicros = curr.startABCMicros;
							if (pre.tiesTo == null) {
								// I suspect lotro internally can
								// have rounding errors.
								// So we shorten a slight bit.
                                //pre.endABCMicros--;//this can cause it to end before its chord
							}
							assert curr.endABCMicros > pre.endABCMicros;
							assert pre.note != Note.REST;
							logNotes.finer(part.getAbcSong().getTitle()+ ": normalizing note!2!");
						}
					}
				}

				List<AbcNoteEvent> longerNotes = new ArrayList<>();
				for (AbcNoteEvent ne : chord.getNotes()) {
					if (ne.endABCMicros > chord.getShortest().endABCMicros) {
						longerNotes.add(ne);
					}
				}
				List<AbcNoteEvent> notesOff = new ArrayList<>();
				for (AbcNoteEvent ne : notesOn) {
					if (ne.endABCMicros <= chord.getShortest().endABCMicros) {
						notesOff.add(ne);
					}
				}
				notesOn.removeAll(notesOff);
				notesOn.addAll(longerNotes);
			}
		}
	    
	    List<Chord> returnList = new ArrayList<>(chords.size());
		returnList.addAll(chords);	    
	    return returnList;
	}
	
	/**
	 * 
	 * Part of multi-stage organic path
	 * 
	 */
	private boolean unmixRestAndNotes(AbcPart part, List<AbcNoteEvent> eventSegments, Chord curChord, boolean useRestToShortenChords) {
		// make sure chord does not contain both rest and notes
		// Also make sure there are no duplicates in it
		List<AbcNoteEvent> tmp = new ArrayList<>(curChord.getNotes());
		boolean both = curChord.hasRestAndNotes();
		boolean removedStuff = false;
		for (AbcNoteEvent note : tmp) {
			if (!useRestToShortenChords && both && note.note == Note.REST) {
				logNotes.finer("Removing rest");
				curChord.remove(note);
				eventSegments.remove(note);
				removedStuff = true;
			}
			for (AbcNoteEvent note2 : tmp) {
				if (note != note2 && note.note == note2.note) {
					// chord contains 2 notes with same pitch
					if (!curChord.getNotes().contains(note) || !curChord.getNotes().contains(note2)) {
						// one of them has already been removed
						continue;
					}
					List<AbcNoteEvent> firstList = new ArrayList<>();
					List<AbcNoteEvent> secondList = new ArrayList<>();
					firstList.add(note);
					secondList.add(note2);

					long first = note.getTieEnd().getEndTick();
					long second = note2.getTieEnd().getEndTick();

                    boolean isRest = note.note == Note.REST;

                    if ((first >= second && !isRest) || (isRest && first < second)) {
                        // remove second
						logNotes.finer("Remove dupli-secnd "+Util.formatDurationM(note2.startABCMicros)+" - "+Util.formatDurationM(note2.endABCMicros));
						if (curChord.isShortest(note2) && !isRest) {
							AbcNoteEvent replacement = new AbcNoteEvent(Note.REST,64,note2.getStartTick(),note2.getEndTick(),qtm,null);
							replacement.startABCMicros = note2.startABCMicros;
							replacement.endABCMicros = note2.endABCMicros;
							curChord.add(replacement);
							int index = eventSegments.indexOf(note2);
							eventSegments.add(index, replacement);
						}
						curChord.remove(note2);
						breakTies(secondList);
						eventSegments.remove(note2);
					} else {
                        // remove first
						logNotes.finer("Remove dupli-first "+Util.formatDurationM(note.startABCMicros)+" - "+Util.formatDurationM(note.endABCMicros));
						if (curChord.isShortest(note) && !isRest) {
							AbcNoteEvent replacement = new AbcNoteEvent(Note.REST,64,note.getStartTick(),note.getEndTick(),qtm,null);
							replacement.startABCMicros = note.startABCMicros;
							replacement.endABCMicros = note.endABCMicros;
							curChord.add(replacement);
							int index = eventSegments.indexOf(note);
							eventSegments.add(index, replacement);
						}
						curChord.remove(note);
						breakTies(firstList);
						eventSegments.remove(note);
					}
                    part.numberOfRemovedNotesFromFitting++;
					removedStuff = true;
				}
			}
		}
		return removedStuff;
	}

    /**
     *
     * Part of multi-stage organic path
     *
     */
    private List<AbcNoteEvent> splitToGrid(AbcNoteEvent ne, TreeMap<Long,Long> gridTicks, AbcPart part, boolean useRestToShortenChords, long minimumMicros) {

        // when cutting up too long notes, this is the minimum buffer they are allowed to exceed max with.
        long maxSustainBuffer = minimumMicros * 2;
        long maxSustain = LotroInstrumentSampleDuration.getSafeDuration(part.getInstrument());
        boolean sustained = part.getInstrument().sustainable;

        List<AbcNoteEvent> segments = new ArrayList<>();
        segments.add(ne);

        Entry<Long, Long> ceil = gridTicks.ceilingEntry(ne.startABCMicros+1L);
        Long restartMicros = ne.startABCMicros;
        Long restartTick = ne.getStartTick();
        Long ceilMicros = ceil == null?null:ceil.getKey();
        Long ceilTick   = ceil == null?null:ceil.getValue();

        Entry<Long, Long> ceilFuture = ceil==null?null:gridTicks.ceilingEntry(ceilMicros+1L);
        Long ceilFutureMicros = ceilFuture == null?null:ceilFuture.getKey();
        Long ceilFutureTick   = ceilFuture == null?null:ceilFuture.getValue();

        long endMicros = ne.endABCMicros;
        boolean drone = isDrone(part,ne);
        boolean rest = ne.note == Note.REST;
        while (ceil != null && ceilMicros < endMicros) {
            // As long as there is another ceiling within the note duration
            AbcNoteEvent ne2;
            long microsFullDura = ne.endABCMicros-ne.startABCMicros;

            boolean canReachFuture = ceilFuture != null && ceilFutureMicros <= endMicros
                    && ceilFutureMicros - restartMicros <= maxSustain + maxSustainBuffer;

            if (useRestToShortenChords && sustained	&& !rest
                    && canReachFuture
                    ) {

                // insert rest to shorten chord and keep long note
                //
                // Note that this will potentially insert many rests into chords.
                // But prune will get rid of all but the shortest.

                AbcNoteEvent restShorter = new AbcNoteEvent(Note.REST, 4, ne.getStartTick(), ceilTick, qtm, null);
                restShorter.startABCMicros = ne.startABCMicros;
                restShorter.endABCMicros = ceilMicros;
                assert restShorter.endABCMicros - restShorter.startABCMicros <= maxSustain+maxSustainBuffer : ((ne.endABCMicros - ne.startABCMicros)/1000) +" ms";
                segments.add(restShorter);
                logNotes.fine("Add rest into chord starting at "+Util.formatDurationM(restShorter.startABCMicros));
                if (microsFullDura < maxSustain + maxSustainBuffer) {
                    break;
                } else {
                    ne2 = ne;
                }
            } else if (!rest && (drone || canReachFuture)) {

                // split and tie
                //
                // rests dont come in here, they need restart.
                // all drones go here.


                // TODO: comment out when system more solid
                assert ne.startABCMicros < ceilMicros;
                assert ne.getStartTick() < ceilTick:ne.getStartTick()+" < "+ceilTick;
                assert ne.endABCMicros > ceilMicros:ne.endABCMicros+" > "+ceilMicros;
                assert ne.getEndTick() > ceilTick:ne.getEndTick()+" > "+ceilTick;

                //System.err.println("TIE ceilMicros = "+Util.formatDurationM(ceilMicros));

                ne2 = ne.splitWithTieAtTick(ceilTick, ceilMicros);

                segments.add(ne2);
            } else {

                // restart
                //
                // all rests come in here, drones do not
                //

                ne2 = new AbcNoteEvent(ne.note, ne.velocity, ceilTick, ne.getEndTick(), qtm, ne.origNote);
                ne2.startABCMicros = ceilMicros;
                ne2.endABCMicros = ne.endABCMicros;
                ne.endABCMicros = ceilMicros;
                ne.setEndTick(ceilTick);

                //System.err.println("RST ceilMicros = "+Util.formatDurationM(ceilMicros));

                assert ne.endABCMicros - ne.startABCMicros < maxSustain+maxSustainBuffer : ((ne.endABCMicros - ne.startABCMicros)) +" us";
                segments.add(ne2);
                restartMicros = ceilMicros;
                restartTick = ceilTick;
            }

            // TODO: comment out when system more solid
            assert ne.startABCMicros < ne.endABCMicros;
            assert ne2.startABCMicros < ne2.endABCMicros;
            assert ne.getStartTick() < ne.getEndTick();
            assert ne2.getStartTick() < ne2.getEndTick();


            ne = ne2;

            ceil = gridTicks.ceilingEntry(ceilMicros+1L);
            ceilMicros = ceil == null?null:ceil.getKey();
            ceilTick   = ceil == null?null:ceil.getValue();
            ceilFuture = ceil==null?null:gridTicks.ceilingEntry(ceilMicros+1L);
            ceilFutureMicros = ceilFuture == null?null:ceilFuture.getKey();
            ceilFutureTick   = ceilFuture == null?null:ceilFuture.getValue();
        }

        boolean assertionsEnabled = false;
        assert assertionsEnabled = true;
        if (assertionsEnabled) {
            Collections.sort(segments);
            AbcNoteEvent prev = null;
            for (AbcNoteEvent segment : segments) {
                long microsDura = segment.endABCMicros-segment.startABCMicros;
                assert microsDura <= maxSustain+maxSustainBuffer: segment.note+" vel="+segment.velocity+"  "+((ne.endABCMicros - ne.startABCMicros)/1000) +" ms";
                if (prev != null) {

                }
                prev = segment;
            }
        }
        return segments;
    }

    @Deprecated
	private boolean deprecated1(AbcPart part, List<AbcNoteEvent> events, long minimumMicros,
			boolean removeGliss, ChordOrganic curChord, AbcNoteEvent ne, long microsTillNext, long microsTillNext2,
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
				
				logNotes.info(part.getTitle()+" Removed glissando note 1");
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
				logNotes.info("Not gli: overlap="+(curChord.getEndTick() > ne.getStartTick())+" microsTillNext="+microsTillNext+" microsTillNext2="+microsTillNext2+" neMicros="+neMicros+" ne2Micros="+ne2Micros);
			}
		}
		return false;
	}

    @Deprecated
	private boolean deprecated2(AbcPart part, List<AbcNoteEvent> events, long minimumMicros, ChordOrganic curChord,
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
			logNotes.info(part.getTitle()+" Delayed short chord");
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
			logNotes.info(part.getTitle()+" Delayed staggered notes");
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
			logNotes.info(part.getTitle()+" Delayed sequential chord by "+ ((minEndMicro-neMicroStart)/1000)+" ms 2");
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
			logNotes.info(part.getTitle()+" Removed glissando note 2 ");
			return true;
		}
	}

	/**
	 * Remove duplicate notes that play at the same time (comes from combining tracks into same part)
	 * 
	 * @param events All the notes from all the combined tracks
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

    @Deprecated
	private void removeDuplicateNotesVerify(List<AbcNoteEvent> events, LotroInstrument instrument) {
		List<AbcNoteEvent> notesOn = new ArrayList<>();
        //second
        for (AbcNoteEvent ne : events) {
            Iterator<AbcNoteEvent> onIter = notesOn.iterator();
            while (onIter.hasNext()) {
                AbcNoteEvent on = onIter.next();//first
                if (on.getEndTick() <= ne.getStartTick() && (on.getLengthTicks() > 0 || on.getStartTick() < ne.getStartTick())) {
                    // First note has already been turned off
                    onIter.remove();
                } else if (on.note.id == ne.note.id) {
                    logNotes.severe("OOPSIE ");
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
							qtm.tickToMicros(ne.getStartTick()) + qtm.multiplyByExportTempoFactor(TimingInfo.LONGEST_NOTE_MICROS)),
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
			assert (ne.getEndTick() >= minEnding) : "1="+(qtm.quantize(ne.getEndTick(), part) == ne.getEndTick())+" 2="+(qtm.quantize(ne.getStartTick(), part) == ne.getStartTick());
			assert (targetEndTick <= ne.getEndTick());

			// Tie notes across tempo boundaries
			final QuantizedTimingInfo.TimingInfoEvent nextTempoEvent = qtm.getNextTimingEvent(ne.getStartTick(),
					part);
			if (nextTempoEvent != null && nextTempoEvent.tick() < targetEndTick) {
				targetEndTick = nextTempoEvent.tick();
				assert (targetEndTick - ne.getStartTick() >= tm.getMinNoteLengthTicks());
				assert (ne.getEndTick() - targetEndTick >= nextTempoEvent.info().getMinNoteLengthTicks());
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
	
	/**
	 * Used by single-stage organic
	 *
     */
	private void breakLongNotesOrganic(AbcPart part, List<AbcNoteEvent> events, long softMaxDurationMicros) {
		TreeSet<Long> startPoints = new TreeSet<>();
        for (AbcNoteEvent ne : events) {
            startPoints.add(ne.startABCMicros);
            //startPoints.add(ne.endABCMicros); // ends are more likely to be moved later, so we grab only starts
        }
        long shortestMicros = AbcConstants.getShortestNoteMicros(qtm.getPrimaryExportTempoBPM());
		for (int i = 0; i < events.size(); i++) {
			AbcNoteEvent ne = events.get(i);

			long maxNoteEndMicros = ne.startABCMicros + softMaxDurationMicros;

            if (ne.endABCMicros <= maxNoteEndMicros) {
                continue;
            }

			boolean drone = isDrone(part,ne);
			boolean rest = ne.note == Note.REST;
			
			// Make a hard break for notes that are longer than LotRO can play
			// Bagpipe notes up to B2 can sustain indefinitely; don't break them
			if (!rest && !drone) {
				// restart note unless non-sustainable, then end it premature

				if (part.getInstrument().isSustainable(ne.note.id)) {

                    long startMicros  = ne.startABCMicros;
                    long finaleMicros = ne.endABCMicros;
                    long cutMicros    = maxNoteEndMicros;
                    if (finaleMicros - cutMicros < AbcConstants.ONE_SECOND_MICROS * 2) {
                        // we dont want to cut it less than 2 second before the end
                        // so we cut at 2 seconds before finaleMicros
                        // TODO: This might time its cut different from the cuts in other parts at same time
                        //       But not sure how to handle that, and it might in some cases be good thing.
                        cutMicros = finaleMicros - 2 * AbcConstants.ONE_SECOND_MICROS;
                        long maxNoteEndTick2 = qtm.microsToTickABCOrganic(cutMicros);
                        long bar1 = qtm.tickToBarStartTickOrganic(maxNoteEndTick2);
                        long bar2 = qtm.tickToBarEndTickOrganic(maxNoteEndTick2);

                        if (Math.abs(maxNoteEndTick2 - bar1) < Math.abs(maxNoteEndTick2 - bar2)
                                && qtm.tickToMicrosABCOrganic(bar1)-startMicros > AbcConstants.ONE_SECOND_MICROS * 2) {
                            // prev bar is closer than next from cut
                            // and prev bar is at least 2 sec from startMicros
                            maxNoteEndMicros = qtm.tickToMicrosABCOrganic(bar1);
                            logNotes.fine(part.getTitle()+" bar1 break");
                        } else if (qtm.tickToMicrosABCOrganic(bar2) < cutMicros && finaleMicros - qtm.tickToMicrosABCOrganic(bar2) > 14*AbcConstants.ONE_SECOND_MICROS/8) {
                            // next bar from cut and finaleMicros is at least 1.75 sec from finaleMicros
                            maxNoteEndMicros = qtm.tickToMicrosABCOrganic(bar2);
                            logNotes.fine(part.getTitle()+" bar2 break");
                        } else {
                            maxNoteEndMicros = cutMicros;
                            logNotes.fine(part.getTitle()+" break 2 sec before 5 sec max");
                        }
                    } else {
                        long bar3 = qtm.tickToBarStartTickOrganic(qtm.microsToTickABCOrganic(maxNoteEndMicros));
                        long slipMicros = maxNoteEndMicros - qtm.tickToMicrosABCOrganic(bar3);

                        if (slipMicros < AbcConstants.ONE_SECOND_MICROS * 2) {
                            // maximum 2 sec from 5 secs
                            maxNoteEndMicros = qtm.tickToMicrosABCOrganic(bar3);
                            logNotes.fine(part.getTitle()+" bar3 break");
                        } else {
                            logNotes.fine(part.getTitle()+" 5.0 sec break");
                        }
                    }

					AbcNoteEvent next = new AbcNoteEvent(ne.note, ne.velocity, qtm.microsToTickABCOrganic(maxNoteEndMicros), ne.getEndTick(), qtm, ne.origNote);
					next.startABCMicros = maxNoteEndMicros;
					next.endABCMicros = ne.endABCMicros;
					assertNoteDuraOrganic1(next, shortestMicros);
					
					int ins = Collections.binarySearch(events, next);
					if (ins < 0)
						ins = -ins - 1;
					assert (ins > i): "REST="+(rest);
					events.add(ins, next);

					ne.continues = next.getLengthTicks();// needed for pruning
				}
				ne.setEndTick(qtm.microsToTickABCOrganic(maxNoteEndMicros));
				ne.endABCMicros = maxNoteEndMicros;
				assertNoteDuraOrganic1(ne, shortestMicros);
			} else if (drone) {
                // drones should be tied instead of cut up
                // Where this is tied can matter for other notes, so
                // find where other notes start or end and choose that place.

                // maxTickForDrones is softMaxDurationMicros - 0.25s after start
                long maxMicrosForDrones =
                        ne.startABCMicros + softMaxDurationMicros - AbcConstants.ONE_SECOND_MICROS / 4;

                // minForDrones is softMaxDurationMicros - 1s after start
                long minMicrosForDrones =
                        ne.startABCMicros + softMaxDurationMicros - AbcConstants.ONE_SECOND_MICROS;

                logNotes.finer("note " + ne.startABCMicros + " - " + ne.endABCMicros + ", drone=" + drone + ", rest=" + rest);
                logNotes.finer("minMicrosForDrones = " + minMicrosForDrones);
                logNotes.finer("maxMicrosForDrones = " + maxMicrosForDrones);

                Long bestMicrosForDrones = startPoints.floor(maxMicrosForDrones);
                if (bestMicrosForDrones != null && bestMicrosForDrones > minMicrosForDrones) {
                    maxMicrosForDrones = bestMicrosForDrones;
                    logNotes.finer("maxMicrosForDrones = bestMicrosForDrones = " + bestMicrosForDrones);
                }

                long targetEndMicros = Math.min(ne.endABCMicros, maxMicrosForDrones);

                logNotes.finer("min(" + ne.endABCMicros + ", " + maxMicrosForDrones + ")");

                if (ne.endABCMicros > targetEndMicros) {
                    long targetEndTick = qtm.microsToTickABCOrganic(targetEndMicros);
                    AbcNoteEvent next = ne.splitWithTieAtTick(targetEndTick, targetEndMicros);

                    assertNoteDuraOrganic1(ne, shortestMicros);
                    assertNoteDuraOrganic1(next, shortestMicros);

                    int ins = Collections.binarySearch(events, next);
                    if (ins < 0)
                        ins = -ins - 1;
                    assert (ins > i);
                    events.add(ins, next);
                }
            } else if (rest) {
                // Rest longer than softMaxDurationMicros, split it at 4s:
                long targetEndMicros =
                            ne.startABCMicros + AbcConstants.LONGEST_NOTE_MICROS / 2;
                logNotes.finer("targetEndMicros=" + targetEndMicros);

                long targetEndTick = qtm.microsToTickABCOrganic(targetEndMicros);
                AbcNoteEvent next = new AbcNoteEvent(ne.note, ne.velocity, targetEndTick, ne.getEndTick(), qtm, ne.origNote);
                next.startABCMicros = targetEndMicros;
                next.endABCMicros = ne.endABCMicros;
                ne.setEndTick(targetEndTick);
                ne.endABCMicros = targetEndMicros;

                assertNoteDuraOrganic1(ne, shortestMicros);
                assertNoteDuraOrganic1(next, shortestMicros);

                int ins = Collections.binarySearch(events, next);
                if (ins < 0)
                    ins = -ins - 1;
                assert (ins > i);
                events.add(ins, next);
            }
            assert ne.endABCMicros - ne.startABCMicros <= softMaxDurationMicros:Util.formatDurationM(ne.endABCMicros - ne.startABCMicros)+" too long still. instr="+part.getInstrument()+" drone="+drone+" rest="+rest;
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

    /**
     * Add tempo events to midi meta track.
     */
	private void addMidiTempoEvents(Track track0, long end) {
		NavigableMap<Long, TimingInfoEvent> timings = qtm.getTimingInfoByTick();
		if (organic) {
			timings = qtm.getTimingInfoByTickOrganic();
		}
		QuantizedTimingInfo.TimingInfoEvent event1L = timings.get(1L);
		for (QuantizedTimingInfo.TimingInfoEvent event : timings.values()) {
			if (event.tick() > end)
				continue;

			track0.add(MidiFactory.createTempoEvent(event.info().getExportTempoMPQ(), event.tick()));

			if (event.tick() == 0L && event1L == null) {
				// The Java MIDI sequencer can sometimes miss a tempo event at tick 0
				// Add another tempo event at tick 1 to work around the bug
				track0.add(MidiFactory.createTempoEvent(event.info().getExportTempoMPQ(), 1));
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
        if (!(ne instanceof BentAbcNoteEvent be)) return null;
        assert be.note != Note.REST;

        int startPitch = be.note.id;

        // Collect transition points
        // Key: Tick (quantized), value: Pitch
        TreeMap<Long, Integer> splitPoints = new TreeMap<>();

        Integer initialBend = be.getBend(be.getStartTick());
        if (initialBend == null) initialBend = be.bends.firstEntry().getValue();
        splitPoints.put(be.getStartTick(), startPitch + initialBend);

        NavigableMap<Long, Integer> bends = be.bends.subMap(be.getStartTick(), false, be.getEndTick(), false);

        for (Map.Entry<Long, Integer> entry : bends.entrySet()) {
            long t = entry.getKey();
            int bend = entry.getValue();
            int noteID = startPitch + bend;

            long floorGrid = qtm.quantizeFloor(t, part);
            long gridLen = qtm.getGridSizeTicks(t, part);

            long targetTick;

            if (t == floorGrid) {
                // Exact match, split here
                targetTick = t;
            } else if (gridLen >= 3 && t < floorGrid + gridLen / 3L) {
                // Early bend -> Snap back to floor
                targetTick = floorGrid;
            } else {
                // Late bend -> Push forward to next grid
                targetTick = floorGrid + gridLen;
            }

            if (targetTick < be.getStartTick()) targetTick = be.getStartTick();
            if (targetTick >= be.getEndTick()) continue;

            // Add or overwrite the split point
            // If multiple bends map to the same grid line, the latest one wins
            splitPoints.put(targetTick, noteID);
        }

        // Build Segments from Map
        List<AbcNoteEvent> benders = new ArrayList<>();
        Map.Entry<Long, Integer> currentEntry = splitPoints.firstEntry();

        while (true) {
            long startTick = currentEntry.getKey();
            int pitch = currentEntry.getValue();

            // Find next transition
            Map.Entry<Long, Integer> nextEntry = splitPoints.higherEntry(startTick);
            long endTick = (nextEntry != null) ? nextEntry.getKey() : be.getEndTick();

            Note newNote = Note.fromId(pitch);
            if (newNote == null || newNote == Note.REST) {
                // Pitch out of range
                logNotes.warning("Dropping entire bent note as it was bent out of range. pitch="+pitch);
                return new ArrayList<>();
            } else {
                // Only create if length > 0 (TreeMap ensures start < nextStart)
                if (endTick > startTick) {
                    // Check for redundant splits (same pitch)
                    if (!benders.isEmpty() && benders.getLast().note.id == pitch && benders.getLast().getEndTick() == startTick) {
                        // Merge with previous
                        benders.getLast().setEndTick(endTick);
                    } else {
                        // New segment
                        AbcNoteEvent sub = new AbcNoteEvent(newNote, be.velocity, startTick, endTick, be.getTempoCache(), be.origNote);
                        sub.setOrigBend(pitch-startPitch);
                        benders.add(sub);
                    }
                }
            }

            if (nextEntry == null) break;
            currentEntry = nextEntry;
        }

        return benders;
    }
	
	/**
	 * Split all BentNoteEvents into multiple quantized NoteEvents
	 * 
	 * @param part Abc Part
	 * @param ne   The note event to be processed
	 * @return List of multiple NoteEvents
	 */
    @Deprecated
	private List<AbcNoteEvent> expandPitchBendsOrganic(AbcPart part, AbcNoteEvent ne) {
		// Handle pitch bend by subdividing tone into shorter notes.
		if (ne instanceof BentAbcNoteEvent be) {
            int noteID = be.note.id;
			assert be.note != Note.REST;
			int startPitch = noteID;
			List<AbcNoteEvent> benders = new ArrayList<>();
			AbcNoteEvent current = null;
            long minimumDura = AbcConstants.getShortestNoteMicros(qtm.getPrimaryExportTempoBPM());

			Integer bend = null;
			for (long tick = be.getStartTick(); tick < be.getEndTick();
					tick = be.getNextBend(qtm.microsToTickABCOrganicRoundUp(
							qtm.tickToMicrosABCOrganic(tick) + minimumDura*65L/60L), bend)
                    ) {
                // Faction 65/60 makes bends more detailed as they are much less susceptible to
                // micro/tick rounding inaccuracies.
				bend = be.getBend(tick);
                if (bend == null) {
                    // Since all bent notes have a bend at start tick,
                    // and that start tick might have been quantized to lower tick.
                    // Make sure we grab that initial value here.
                    // For organic this shouldn't happen, is a legacy/mix issue.
                    bend = be.bends.firstEntry().getValue();
                }
                noteID = startPitch + bend;
                if (current == null) {
					current = createBentSubNote(be, noteID, current, tick, bend);
					if (current == null)
						return new ArrayList<>();
					benders.add(current);
				} else {
					if (current.note.id != noteID) {
						current = createBentSubNote(be, noteID, current, tick, bend);
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

    /**
     * Split all BentNoteEvents into multiple NoteEvents
     *
     * @param ne   The note event to be processed
     * @return List of multiple NoteEvents
     */
    private List<AbcNoteEvent> expandPitchBendsOrganicImproved(AbcNoteEvent ne) {
        /*
            Stuff this method does
            ---
            Split a note with pitch bend into discrete semi-note sub-notes.
            Adhere to lotro's minimum duration with a buffer guard against tick/micros rounding
            Detect and ignore fast transient bends.
            Handle slides, they basically get sampled.
            Sudden pitch-bends (at the end of notes) will be smoothed out.
            This method has a unit-test
        */
        if (!(ne instanceof BentAbcNoteEvent be)) {
            return null;
        }
        assert be.note != Note.REST;

        int startPitch = be.note.id;
        List<AbcNoteEvent> benders = new ArrayList<>();
        AbcNoteEvent current = null;

        // minimum with safety margin to handle rounding jitter
        long safetyMarginMicros = 65_000L;// 65 ms is a fine buffer even if minimum is slightly larger than 60 ms
        long stabilityThreshold = safetyMarginMicros / 2;// Threshold for a "Stable" note
        long finaleMinimum = safetyMarginMicros;// if the note ends with a bend that is shorter than this, we disregard it.

        long tick = be.getStartTick();
        long endTick = be.getEndTick();
        long validPitchEndTick = qtm.microsToTickABCOrganic(
                qtm.tickToMicrosABCOrganic(endTick) - finaleMinimum);

        Integer bend = be.getBend(tick);
        if (bend == null) bend = be.bends.firstEntry().getValue();

        current = createBentSubNote(be, startPitch + bend, null, tick, bend);
        if (current == null) return new ArrayList<>();
        benders.add(current);

        while (tick < endTick) {
            /*
            This loop has a part A, B and C.

            A:
            Skip minimum 65 ms forward and check which bend dominates what we skipped over.
            If needed, create/change the current subnote to match.

            B:
            Determine when and what bend the skipped minimum should end on
            Also if the that ending from minimum region just continues with same bend,
            then extend nextTick as far as we can before bend changes,
            because once we change, we again have to skip minimum.

            C:
            Check to see if we can go even further. Tests 65 ms forward.
            Plus checks for short transients in that region that should be ignored.

             */


            // Part A: Calculate earliest split
            long safeSplitTick = qtm.microsToTickABCOrganicRoundUp(
                    qtm.tickToMicrosABCOrganic(tick) + safetyMarginMicros);

            if (safeSplitTick <= tick) safeSplitTick = tick + 1;//should never happen

            int dominantBend = getDominantBend(be, tick, safeSplitTick);

            // Determine the dominant bend inside the window we had to skip over
            if (dominantBend != bend) {
                // The pitch changes within the safety window.

                if (tick == current.getStartTick()) {
                    // happens between the current's start and end of this window: Overwrite
                    benders.remove(current);

                    // Check if we can merge with the previous note instead of creating new
                    if (!benders.isEmpty() && benders.getLast().note.id == startPitch + dominantBend
                            && benders.getLast().getEndTick() == tick) {
                        // Merge with previous
                        current = benders.getLast();
                        current.setEndTick(endTick);
                    } else {
                        current = createBentSubNote(be, startPitch + dominantBend, null, tick, dominantBend);
                        if (current == null) return new ArrayList<>();
                        benders.add(current);
                    }
                } else if (current.note.id != startPitch+dominantBend) {
                    // Happened only in this window: Split previous note here
                    current = createBentSubNote(be, startPitch+dominantBend, current, tick, dominantBend);
                    if (current == null) {
                        return new ArrayList<>();
                    }
                    benders.add(current);
                }
                bend = dominantBend;
            }

            // Part B: Determine when and what bend the window should end on
            long nextTick;
            int nextBend;

            if (safeSplitTick >= endTick) {
                nextBend = bend;
                nextTick = endTick;
            } else {
                // If we fixed a transient, dominant might differ from map data at tick.
                // And getNextBend might return safeSplitTick immediately.
                // Inside the condition below handle this by forcing extension.

                nextTick = be.getNextBend(safeSplitTick, bend);

                if (nextTick < endTick) {
                    Integer b = be.getBend(nextTick);
                    nextBend = (b != null) ? b : 0;// null should not happen
                } else {
                    nextBend = bend;
                }
            }

            // Safety checks...
            if (nextTick <= tick) nextTick = endTick;
            if (nextTick > endTick) nextTick = endTick;

            // finale check...
            if (nextTick > validPitchEndTick) {
                nextTick = endTick;
            }

            // Part B: Apply split
            current.setEndTick(nextTick);

            if (nextTick < endTick) {
                int candidateBend = nextBend;

                // stability scan
                // Check if the pitch at nextTick is transient
                // We scan the window [nextTick, nextTick + 65ms] for a stable plateau.
                long scanEnd = qtm.microsToTickABCOrganicRoundUp(
                        qtm.tickToMicrosABCOrganic(nextTick) + safetyMarginMicros);

                if (scanEnd > validPitchEndTick) scanEnd = endTick;
                if (scanEnd <= nextTick) scanEnd = nextTick; // Safety

                if (scanEnd > nextTick) {
                    long checkTick = nextTick;

                    // Loop through bends inside the safety window
                    while (checkTick < scanEnd) {
                        Integer currentVal = be.getBend(checkTick);
                        // Find when this value changes
                        long nextChange = be.getNextBend(checkTick + 1, currentVal);
                        long changeTick = Math.min(nextChange, scanEnd);

                        long durationMicros = qtm.tickToMicrosABCOrganic(changeTick) - qtm.tickToMicrosABCOrganic(checkTick);

                        if (durationMicros >= stabilityThreshold) {
                            // Found a stable plateau. Using this bend to override any preceding transient.
                            candidateBend = currentVal;
                            break;
                        }

                        checkTick = nextChange;
                        if (checkTick >= endTick) break;
                    }

                    // Logic:
                    // transient detected, we skip to candidateBend.
                    // slide, we stick with the original nextBend.
                }

                if (candidateBend != bend) {
                    current = createBentSubNote(be, startPitch+candidateBend, current, nextTick, candidateBend);
                    if (current == null) {
                        return new ArrayList<>();
                    }
                    benders.add(current);
                    bend = candidateBend;
                } else {
                    // bend matches current (merge).
                    // Update state for the next iteration.
                    bend = candidateBend;
                }
            }
            tick = nextTick;
        }

        return benders;
    }

    /**
     * Scans a time window to find the pitch bend that occupies the most time.
     * Used to filter out initial transients in short windows.
     */
    private int getDominantBend(BentAbcNoteEvent be, long start, long end) {
        // Ensure we don't try to scan past the end of the note.
        // If end (safeSplitTick) is beyond the note's end, getNextBend will
        // never return a value large enough to satisfy the loop condition,
        // and the while will continue forever.
        if (end > be.getEndTick()) end = be.getEndTick();

        if (start >= end) {
            Integer b = be.getBend(start);
            return b != null ? b : 0;
        }

        Map<Integer, Long> durationMap = new HashMap<>();
        long curr = start;
        Integer currentBend = be.getBend(start);
        if (currentBend == null) currentBend = be.bends.firstEntry().getValue();

        while (curr < end) {
            long nextChange = be.getNextBend(curr + 1, currentBend);
            long segmentEnd = Math.min(nextChange, end);

            assert segmentEnd > curr;

            long dur = qtm.tickToMicrosABCOrganic(segmentEnd) - qtm.tickToMicrosABCOrganic(curr);
            durationMap.merge(currentBend, dur, Long::sum);

            curr = segmentEnd;
            if (curr < end) {
                currentBend = be.getBend(curr);
                if (currentBend == null) currentBend = 0;
            }
        }

        int bestBend = currentBend;
        long maxDur = -1L;

        for (Map.Entry<Integer, Long> entry : durationMap.entrySet()) {
            if (entry.getValue() > maxDur) {
                maxDur = entry.getValue();
                bestBend = entry.getKey();
            }
        }
        return bestBend;
    }

	private AbcNoteEvent createBentSubNote(BentAbcNoteEvent be, int noteID, AbcNoteEvent current, long tick, int bend) {
		if (current != null) {
			current.setEndTick(tick);
		}
		Note newNote = Note.fromId(noteID);
		if (newNote == null || newNote == Note.REST) {
			System.out.println("Note removed, pitch bend out of range: "+noteID);
			return null;
		}
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
	
	/**
	 * Run this after when unmixing to tidy up ties
	 *
     */
	private void breakTies(List<AbcNoteEvent> notes) {
		for (AbcNoteEvent ne : notes) {

			// If the note is tied from another (previous) note, break the incoming tie
			if (ne.tiesFrom != null) {
				ne.tiesFrom.tiesTo = null;
				ne.tiesFrom = null;
			}
			
			if (ne.tiesTo != null) {
				ne.tiesTo.tiesFrom = null;
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
     */
	public Pair<Long, Long> getSongStartEndTick(boolean lengthenToBar, boolean accountForSustain) {
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

        startTickForCountIn = startTick;// the bar duration at this place is what we use for calculating count-in time.

		if (organic) {
			// TODO: We start 80 ms before first note to be sure preview plays first note.
			//       Its not related to the 100 ms used in delay parts.
            //       Multi-stage (organic2) needed this to not drift,
            //       because its grid was anchored at 0, not startTick, should be fixed now.
			//startTick = Math.max(0L, qtm.microsToTickABCOrganic(qtm.tickToMicrosABCOrganic(startTick)-80000L));
			return new Pair<>(startTick, endTick);
		}

		// Remove integral number of bars
		//long q = qtm.tickToBarStartTick(startTick);
		firstBarNumber = qtm.tickToBarNumber(startTick);
		long startTickFinal = qtm.quantizeDown(startTick);// this can produce a rest between count-in and first note. Want to be sure count-in is accurate: use organic.
		//logNotes.fine(metadata.getSongTitle()+": firstBar "+firstBarNumber+"  q="+startTick+" startTick="+startTick+" startTickfinal="+startTickFinal+"\n"+qtm.getTimingEventForTick(q)+"\n"+ qtm.getTimingEventForTick(q).info() +"\n"+ qtm.getTimingEventForTick(q).infoOdd());
        logNotes.fine(metadata.getSongTitle()+": firstBar "+firstBarNumber+" startTick="+startTick+" startTickfinal="+startTickFinal+"\n"+qtm.getTimingEventForTick(startTick)+"\n"+ qtm.getTimingEventForTick(startTick).info() +"\n"+ qtm.getTimingEventForTick(startTick).infoOdd());
		logNotes.fine("Bar 1 starts at "+qtm.barNumberToBarStartTick(0)+" "+(qtm.barNumberToMicrosecond(0)/1000000.0));
		logNotes.fine("Bar 2 starts at "+qtm.barNumberToBarStartTick(1)+" "+(qtm.barNumberToMicrosecond(1)/1000000.0));
		logNotes.fine("Bar 3 starts at "+qtm.barNumberToBarStartTick(2)+" "+(qtm.barNumberToMicrosecond(2)/1000000.0)+"\n\n\n\n\n\n");
		
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

    public void calcSongStartEndTicks() {
        Pair<Long, Long> limits = getSongStartEndTick(false, true);
        exportStartTick = limits.first;
        exportEndTick = limits.second;
    }

	public long getExportStartTick() {
		return exportStartTick;
	}

	public long getExportEndTick() {
		return exportEndTick;
	}

	/**
	 * Does not account for tempo adjustment
     */
	public long getExportStartMicros() {
		if (organic) {
			return qtm.tickToMicrosOrganic(getExportStartTick());
		} else {
			return qtm.tickToMicros(getExportStartTick());
		}
	}

    public long getExportStartMicrosABC() {
        if (organic) {
            return qtm.tickToMicrosABCOrganic(getExportStartTick());
        } else {
            return qtm.tickToMicrosABC(getExportStartTick());
        }
    }
	
	/**
	 * Returns the final song duration.
	 * Used to export duration in part names, file name and metadata.
	 *
     */
	private long getSongLengthMicros() {
		return qtm.divideByExportTempoFactor(getExportEndMicros() - getExportStartMicros());
	}

	/**
	 * Does not account for tempo adjustment
     */
	public long getExportEndMicros() {
        long countInMicros = 0L;
        AbcPart part = getParts().getFirst();
        if (part != null) {
            AbcSong song = part.getAbcSong();
            CountIn countIn = song.getCountIn();
            if (countIn != null) {
                // we add the total count-in duration to the song duration
                countInMicros = qtm.multiplyByExportTempoFactor(countIn.micros);
            }
        }
		if (organic) {
			return qtm.tickToMicrosOrganic(getExportEndTick()) + countInMicros;
		} else {
			return qtm.tickToMicros(getExportEndTick()) + countInMicros;
		}
	}

	public static class ExportTrackInfo implements GenericTrackInfo {
		public final int trackNumber;
		public final AbcPart part;
        public final int numberOfExportedNotes;
        public final int numberOfRemovedNotesForSafety;
        public final int numberOfRemovedNotesFromFitting;
        public final int numberOfRemovedNotesZeros;
        public final int numberOfRemovedNotesFromPruning;
		
		//not sure what this used to be used for
		public final List<AbcNoteEvent> noteEvents;
		
		public final Integer channel;
		public final Integer patch;
		public final long endOfTrack;
        public int maxPoly;
        public final MidiEvent panEvent;
        public SequenceInfo seqInfo = null;
        private final SortedSet<Integer> notesInUse = new TreeSet<>();

        public ExportTrackInfo(int trackNumber, AbcPart part, List<AbcNoteEvent> noteEvents, Integer channel, int patch, long endOfTrack,
                               int numberOfExportedNotes, int numberOfRemovedNotesForSafety, int maxPoly,
                               int numberOfRemovedNotesFromFitting, int numberOfRemovedNotesZeros, int numberOfRemovedNotesFromPruning,
                               MidiEvent panEvent) {
			this.trackNumber = trackNumber;
			this.part = part;
            this.numberOfExportedNotes = numberOfExportedNotes;
            this.numberOfRemovedNotesForSafety = numberOfRemovedNotesForSafety;
            this.noteEvents = noteEvents;
			this.channel = channel;
			this.patch = patch;
			this.endOfTrack = endOfTrack;
            this.maxPoly = maxPoly;
            this.numberOfRemovedNotesFromFitting = numberOfRemovedNotesFromFitting;
            this.numberOfRemovedNotesZeros = numberOfRemovedNotesZeros;
            this.numberOfRemovedNotesFromPruning = numberOfRemovedNotesFromPruning;
            this.panEvent = panEvent;
            if (noteEvents != null) {
                for (NoteEvent ne : noteEvents) {
                    notesInUse.add(ne.note.id);
                }
            }
		}

        @Override
        public int getTrackNumber() {
            // beware: this returns part number.
            return part.getPartNumber();
        }

        @Override
        public List<NoteEvent> getEvents() {
            return Collections.unmodifiableList(noteEvents);
        }

        @Override
        public boolean isDrumTrack() {
            if (part == null) return false;//TODO: for abcToMidi store instrument in case part is null
            return part.isDrumPart();
        }

        @Override
        public SortedSet<Integer> getNotesInUse() {
            return notesInUse;
        }

        @Override
        public int getMinVelocity() {
            return 0;
        }

        @Override
        public int getMaxVelocity() {
            return 127;
        }

        @Override
        public String getName() {
            if (part == null) return "Unset name";
            return part.getTitle();
        }

        @Override
        public String getInstrumentNames() {
            if (part == null) return "Unset instrument";//TODO: for abcToMidi store instrumentname in case part is null
            return part.getInstrument().getLocalFriendlyName();
        }

        @Override
        public int getInstrumentExCount() {
            return 0;
        }

        @Override
        public SequenceInfo getSequenceInfo() {
            return seqInfo;
        }

        @Override
        public String toString() {
            String str = "";
            if (part != null) str = part.getTitle()+": ";
            str += "Preview track number "+trackNumber;
            return str;
        }
	}

    /**
     * Master bool for organic enabled
     */
	public void setOrganic(boolean org) {
		organic = org;
	}

	public boolean isOrganic() {		
		return organic;
	}

    /**
     * Enable multi-stage
     * Only has meaning if organic is true
     */
	public void setOrganic2(boolean org) {
		organic2 = org;
	}

	public boolean isOrganic2() {		
		return organic2;
	}

    /**
     * Enable multi-stage 2 bouncing
     * Only has meaning if organic, upgraded and multistage is true
     * Default is true.
     */
    public void setBouncingEnabled(boolean bouncingEnabled) {
        this.bouncingEnabled = bouncingEnabled;
    }

    /**
     * Enable multi-stage 2
     * Only has meaning if organic and multistage is true
     */
    public boolean isUpgraded() {
        return upgraded;
    }

    public void setUpgraded(boolean upgraded) {
        this.upgraded = upgraded;
    }

	public boolean isUseRestsInChords() {
		return useRestsInChords;
	}

	public void setUseRestsInChords(boolean useRestsInChords) {
		this.useRestsInChords = useRestsInChords;
	}
}
