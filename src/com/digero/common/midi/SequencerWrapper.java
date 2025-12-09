package com.digero.common.midi;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.logging.Logger;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import javax.sound.midi.Transmitter;
import javax.swing.Timer;

import com.digero.common.midi.SequencerEvent.SequencerProperty;
import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;
import com.digero.common.util.ListenerList;

public class SequencerWrapper implements MidiConstants, ITempoCache, IDiscardable {
    private static final Logger log = Logger.getLogger("playback");

	public static final int UPDATE_FREQUENCY_MILLIS = 50;
	public static final long UPDATE_FREQUENCY_MICROS = UPDATE_FREQUENCY_MILLIS * 1000L;

    protected static LotroSequencerWrapper abcSeq = null;// is set only by LotroSequencer
	protected Sequencer sequencer;
	protected Receiver receiver;
	private Transmitter transmitter;
	private List<Transceiver> transceivers = new ArrayList<>();
	private long dragTick;
	private boolean isDragging;
	private final TempoCacheSlow tempoCache = new TempoCacheSlow();
	private boolean[] trackActiveCache = null;

	private final Timer updateTimer = new Timer(UPDATE_FREQUENCY_MILLIS, new TimerActionListener());
	private long lastUpdateTick = -1;
	private boolean lastRunning = false;
	private TempoCacheSlow cache = null;
	
	@Deprecated
	private long hoursPlus = 0L;
	
	private float tempoFactor = 1.f;
	protected long realDuraTicks = Long.MAX_VALUE;
	
	public static boolean onlyFirstTrackTempos = true;
	
	// For AbcPlayer, we should send the tempo factor to the sequence.
	// For Maestro, the tempo factor is factored into midi tempo messages
	// when the sequence is refreshed, so it shouldn't be sent to the sequence.
	private boolean useSequenceTempoFactor = false;

    public static boolean isAbcPreview = true;// set to true so don't have to touch abc player.

	private ListenerList<SequencerEvent> listeners = null;

	public SequencerWrapper() throws MidiUnavailableException {
		sequencer = MidiSystem.getSequencer(false);
		sequencer.open();
		transmitter = sequencer.getTransmitter();// MIDI OUT CONNECTION
		receiver = createReceiver();
		transmitter.setReceiver(receiver);
	}

	public Receiver createReceiver() throws MidiUnavailableException {
		return MidiSystem.getReceiver();
	}

	@Override
	public void discard() {
		if (sequencer != null) {
			stop();
		}

		if (listeners != null)
			listeners.discard();

		if (updateTimer != null)
			updateTimer.stop();

		if (transceivers != null) {
			for (Transceiver t : transceivers)
				t.close();
			transceivers = null;
		}

		if (transmitter != null)
			transmitter.close();

		if (receiver != null)
			receiver.close();

		if (sequencer != null)
			sequencer.close();

		trackActiveCache = null;
		cache = null;
		hoursPlus = 0L;
	}

	public void addTransceiver(Transceiver transceiver) {
		Transmitter lastTransmitter = transmitter;
		if (!transceivers.isEmpty())
			lastTransmitter = transceivers.getLast();

		// Hook up the transceiver in the chain
		lastTransmitter.setReceiver(transceiver);
		transceiver.setReceiver(receiver);

		transceivers.add(transceiver);
	}
	
	protected void resetHardIfGone() {
		
	}

    private class TimerActionListener implements ActionListener {
		@Override
		public void actionPerformed(ActionEvent e) {
			if (sequencer != null && sequencer.isOpen()) {
				resetHardIfGone();
		
				long songTick = sequencer.getTickPosition();
				if (songTick >= getTickLength()) {
					// There's a bug in Sun's RealTimeSequencer, where there is a possible
					// deadlock when calling setMicrosecondPosition(0) exactly when the sequencer
					// hits the end of the sequence. It looks like it's safe to call
					// sequencer.setTickPosition(0).
					sequencer.stop();
					sequencer.setTickPosition(0);
					lastUpdateTick = songTick;
				} else {
					if (lastUpdateTick != songTick) {
						lastUpdateTick = songTick;
						fireChangeEvent(SequencerProperty.POSITION);
					}
					boolean running = sequencer.isRunning();
					if (lastRunning != running) {
						lastRunning = running;
						if (running)
							updateTimer.start();
						else {
							updateTimer.stop();
							fireChangeEvent(SequencerProperty.SONG_ENDED);
						}
						fireChangeEvent(SequencerProperty.IS_RUNNING);
					}
				}
			}
		}
	}

	public void reset(boolean fullReset) {
		stop();
		setPosition(0);
		trackActiveCache = null;

		if (fullReset) {
			Sequence seqSave = sequencer.getSequence();
			try {
				sequencer.setSequence((Sequence) null);
			} catch (InvalidMidiDataException e) {
				// This won't happen
				throw new RuntimeException(e);
			}

			sequencer.close();
			transmitter.close();
			receiver.close();

			try {
				sequencer = MidiSystem.getSequencer(false);
				sequencer.open();
				transmitter = sequencer.getTransmitter();
				receiver = createReceiver();
			} catch (MidiUnavailableException e1) {
				throw new RuntimeException(e1);
			}

			try {
				sequencer.setSequence(seqSave);
			} catch (InvalidMidiDataException e) {
				// This won't happen
				throw new RuntimeException(e);
			}

			// Hook up the transmitter to the receiver through any transceivers that we have
			Transmitter prevTransmitter = transmitter;
			for (Transceiver transceiver : transceivers) {
				prevTransmitter.setReceiver(transceiver);
				prevTransmitter = transceiver;
			}
			prevTransmitter.setReceiver(receiver);

			try {
                if (seqSave != null) {
                    ShortMessage msg = new ShortMessage();
                    msg.setMessage(ShortMessage.SYSTEM_RESET);
                    receiver.send(msg, -1);
                }
			} catch (InvalidMidiDataException e) {
				e.printStackTrace();
			}
		} else {
			// Not a full reset
			boolean isOpen = sequencer.isOpen();
			try {
				if (!isOpen)
					sequencer.open();

				LotroShortMessage msg = new LotroShortMessage();
                if (sequencer.getSequence() != null) {
                    for (int i = 0; i < CHANNEL_COUNT_ABC; i++) {
                        msg.setMessage(ShortMessage.PROGRAM_CHANGE, i, 0, 0);
                        receiver.send(msg, -1);
                        msg.setMessage(ShortMessage.CONTROL_CHANGE, i, RESET_ALL_CONTROLLERS, 0);
                        receiver.send(msg, -1);
                    }
                    msg.setMessage(ShortMessage.SYSTEM_RESET);
                    receiver.send(msg, -1);
                }
			} catch (MidiUnavailableException e) {
				// Ignore
			} catch (InvalidMidiDataException e) {
				// Ignore
				e.printStackTrace();
			}

			if (!isOpen)
				sequencer.close();
		}
		cache = null;
		hoursPlus = 0L;
	}

	public long getTickPosition() {
		return sequencer.getTickPosition();
	}

	public void setTickPosition(long tick) {
        if (sequencer.getSequence() == null) {
            return;
        }
		if (tick != getTickPosition()) {
			sequencer.setTickPosition(tick);
			lastUpdateTick = sequencer.getTickPosition();
			fireChangeEvent(SequencerProperty.POSITION);
		}
	}

    /**
     * If the sequencer is at time zero
     * More fast than checking getPosition() == 0L
     */
    public boolean isAtStart() {
        return getTickPosition() == 0L;
    }

    /**
     * Get the playback position in microseconds.
     */
	public long getPosition() {
		if (getSequence() == null)
			return 0L;
		// if (hoursPlus > 0 && getSequence() != null) {
		long tick = sequencer.getTickPosition();
		return MidiUtils.tick2microsecond(getSequence(), tick, tempoCache);
		// }
		// return sequencer.getMicrosecondPosition();
	}

    /**
     * Song position in microseconds, taking into account the playback latency.
     *
     */
    public long getDelayedPosition() {
        long micros = getPosition();

        if (isAbcPreview && abcSeq != null && abcSeq.isRunning()) {
            /*
             * Subtract the playback latency
             * to compensate for the delay between
             * the MIDI clock and the sound being played.
             *
             * Stuff like this shows that LotroSequencerWrapper and NoteFilterSequencerWrapper
             * really should have been 1 class. The midi seqWrapper needs to know if the abc
             * sequencer is running in this method, so for now I have put abcSeq as a static member.
             *
             * And we cannot just get position from abcSeqWrapper where we use getPosition and getThumbPosition.
             * As the micros might not match with UI,
             * since UI is really displaying the midi sequence, not the abc which might have tune-editor
             * tempo changes or main tempo change.
             */
            micros -= SynthesizerFactory.PLAYBACK_LATENCY_MICROS;
            micros -= abcSeq.getCountInMicros();
            micros = Math.max(0L, micros);
        }

        return micros;
    }

    /**
     * Set the playback position in microseconds.
     */
	public void setPosition(long position) {
        if (sequencer.getSequence() == null) {
            return;
        }
		if (position == 0L) {
			// Sun's RealtimeSequencer isn't entirely reliable when calling
			// setMicrosecondPosition(0). Instead call setTickPosition(0),
			// which has the same effect and isn't so buggy.
			setTickPosition(0L);
		} else if (position != getPosition()) {
			sequencer.setTickPosition(microsToTick(position));
			lastUpdateTick = sequencer.getTickPosition();
			fireChangeEvent(SequencerProperty.POSITION);
		}
	}

	@Override
	public long microsToTick(long micros) {
		Sequence sequence = getSequence();
		if (sequence == null)
			return 0;

		return MidiUtils.microsecond2tick(sequence, micros, tempoCache);
	}

	@Override
	public long tickToMicros(long tick) {
		Sequence sequence = getSequence();
		if (sequence == null)
			return 0L;

		return MidiUtils.tick2microsecond(sequence, tick, tempoCache);
	}

	/**
	 * 
	 * @return sequence duration in microseconds
	 */
	public long getLength() {
		Sequence sequ = sequencer.getSequence();
		long l = 0L;
		if (sequ != null) {
			// this can handle midis that have hours-long duration.
			// Sequencer.getMicrosecondLength cannot.
			l = MidiUtils.tick2microsecond(sequ, Math.min(realDuraTicks, sequencer.getTickLength()), tempoCache);
		}
		return l;
	}

	/**
	 * Reimplemented from java.midi but using long instead of int that will make it overflow
	 *
	 * @author Nikolai
	 *
	 */
	public static final class TempoCacheSlow {
		long[] ticks;
		int[] tempos; // in MPQ
        long[] micros;

		public TempoCacheSlow() {
			// just some defaults to prevent weird stuff
			ticks = new long[1];
			tempos = new int[1];
			tempos[0] = MidiUtils.DEFAULT_TEMPO_MPQ;
            micros = new long[1];
		}

		public TempoCacheSlow(Sequence seq) {
			this();
			refresh(seq);
		}

		public void refresh(Sequence seq) {
            NavigableMap<Long, MidiEvent> list = new TreeMap<>();
			Track[] tracks = seq.getTracks();
			if (tracks.length > 0) {
				if (onlyFirstTrackTempos) {
					// tempo events only occur in track 0
					Track track = tracks[0];
					int c = track.size();
					for (int i = 0; i < c; i++) {
						MidiEvent ev = track.get(i);
						MidiMessage msg = ev.getMessage();
						if (MidiUtils.isMetaTempo(msg) && MidiUtils.getTempoMPQ(msg) != 0) {
							// found a valid tempo event. Add it to the list
							list.put(ev.getTick(), ev);
						}
					}
				} else {
					// tempo events occur in any track
					for(Track track : tracks) {
						int c = track.size();
						for (int i = 0; i < c; i++) {
							MidiEvent ev = track.get(i);
							MidiMessage msg = ev.getMessage();
							if (MidiUtils.isMetaTempo(msg) && MidiUtils.getTempoMPQ(msg) != 0) {
								// found a valid tempo event. Add it to the list
                                list.put(ev.getTick(), ev);
							}
						}
					}
				}
			}

			int size = list.size() + 1;
			boolean firstTempoIsFake = true;
			if (list.get(0L) != null) {
				// do not need to add an initial tempo event at the beginning
				size--;
				firstTempoIsFake = false;
			}
			ticks = new long[size];
			tempos = new int[size];
            micros = new long[size];
			int e = 0;
            long currentMicros = 0L;
			if (firstTempoIsFake) {
				// add tempo 120 at the beginning
				ticks[0] = 0L;
				tempos[0] = MidiUtils.DEFAULT_TEMPO_MPQ;
                micros[0] = 0L;
				e++;
			}
			for (MidiEvent evt : list.values()) {
				ticks[e] = evt.getTick();
				tempos[e] = MidiUtils.getTempoMPQ(evt.getMessage());
                if (e > 0) {
                    long deltaTick = ticks[e] - ticks[e-1];
                    currentMicros += MidiUtils.ticks2microsec(deltaTick, tempos[e-1], seq.getResolution());
                }
                micros[e] = currentMicros;
                e++;
			}
		}

        float getTempoMPQAt(long tick) {
            int index = Arrays.binarySearch(ticks, tick);

            // not found
            if (index < 0) {
                // The insertion point is the index of the first element > the key
                // We want the element before that
                index = -(index + 1) - 1;
            }

            if (index < 0) index = 0;
            if (index >= ticks.length) index = ticks.length - 1;

            return (float) tempos[index];
        }
	}

	@Deprecated
	private long checkForSuperLongDurationOld(long l) {
		// this also works, but the new way is better 
		if (hoursPlus > 0) {
			return -l+3600000000L*hoursPlus; 
		}
		Sequence seq = sequencer.getSequence(); 
		if (seq != null) { 
			Track[] tracks = seq.getTracks(); 
			long lastTick = 0; 
			for	(Track track : tracks) { 
				if (track.ticks() > lastTick) { 
					lastTick = track.ticks(); 
				}
			}
			if (lastTick > 0L) {
				//System.out.println("lastTick="+lastTick+" us="+tick2microsecondSlow(seq, lastTick));
				long hours = MidiUtils.tick2microsecond(seq, lastTick, tempoCache)/3600000000L;
				System.out.println("This midi is over "+hours+" hours long. But do not worry :)");
				l = -l+3600000000L*hours;
				hoursPlus = hours;
			}
		}
		return l;		
	}

	public long getTickLength() {
		return Math.min(realDuraTicks, sequencer.getTickLength());
	}

	public float getTempoFactor() {
		return tempoFactor;
	}

	public void setTempoFactor(float tempo) {
		if (tempo != getTempoFactor()) {
			this.tempoFactor = tempo;
			// As of 3.0.2 - no need to set factor on sequence in Maestro,
			// since tempo change events are scaled by the factor during preview refresh.
			// Still need to do it for AbcPlayer.
			if (isUseSequenceTempoFactor()) {
				sequencer.setTempoFactor(tempo);
			}
			fireChangeEvent(SequencerProperty.TEMPO);
		}
	}

	public boolean isRunning() {
		return sequencer.isRunning();
	}

	public void setRunning(boolean isRunning) {
        if (sequencer.getSequence() == null) {
            lastRunning = false;
            return;
        }
		if (isRunning != this.isRunning()) {
			if (isRunning) {
				sequencer.start();
				updateTimer.start();
			} else {
				sequencer.stop();
				updateTimer.stop();
			}
			lastRunning = isRunning;
			fireChangeEvent(SequencerProperty.IS_RUNNING);
		}
	}

	public void start() {
		setRunning(true);
	}

	public void stop() {
		setRunning(false);
	}

	public boolean getTrackMute(int track) {
		return sequencer.getTrackMute(track);
	}

	public void setTrackMute(int track, boolean mute) {
		if (mute != this.getTrackMute(track)) {
			trackActiveCache = null;
			sequencer.setTrackMute(track, mute);
			fireChangeEvent(SequencerProperty.TRACK_ACTIVE);
		}
	}

	public boolean getTrackSolo(int track) {
		return sequencer.getTrackSolo(track);
	}

	public void setTrackSolo(int track, boolean solo) {
		if (solo != this.getTrackSolo(track)) {
			trackActiveCache = null;
			sequencer.setTrackSolo(track, solo);
			fireChangeEvent(SequencerProperty.TRACK_ACTIVE);
		}
	}

	/**
	 * Takes into account both muting and solo.
	 */
	public boolean isTrackActive(int track) {
		if (track < 0)
			return true;

		if (trackActiveCache != null && track < trackActiveCache.length)
			return trackActiveCache[track];

		Sequence song = sequencer.getSequence();
		if (song == null)
			return true;

		int trackCount = song.getTracks().length;
		if (track >= trackCount)
			return true;

		if (trackActiveCache == null || trackActiveCache.length != trackCount)
			trackActiveCache = new boolean[trackCount];

		boolean foundSoloPart = false;
		for (int i = 0; i < trackCount; i++) {
			if (sequencer.getTrackSolo(i)) {
				trackActiveCache[i] = true;
				if (!foundSoloPart) {
					foundSoloPart = true;
					for (int j = 0; j < i; j++)
						trackActiveCache[j] = false;
				}
			} else {
				trackActiveCache[i] = !foundSoloPart && !sequencer.getTrackMute(i);
			}
		}

		return trackActiveCache[track];
	}

	/**
	 * Overriden by NoteFilterSequencerWrapper. On SequencerWrapper for convienience.
	 */
	public boolean isNoteActive(int noteId) {
		return true;
	}

	/**
	 * If dragging, returns the drag position in micros. Otherwise returns the song micros position.
	 */
	public long getThumbPosition() {
		return isDragging() ? getDragPosition() : getDelayedPosition();
	}

	/**
	 * If dragging, returns the drag tick. Otherwise returns the song tick.
	 */
	public long getThumbTick() {
		return isDragging() ? getDragTick() : getTickPosition();
	}

	public long getDragPosition() {
		return tickToMicros(dragTick);
	}

	public long getDragTick() {
		return dragTick;
	}

	public void setDragTick(long dragTick) {
		if (this.dragTick != dragTick) {
			this.dragTick = dragTick;
			fireChangeEvent(SequencerProperty.DRAG_POSITION);
		}
	}

	public void setDragPosition(long dragPosition) {
		setDragTick(microsToTick(dragPosition));
	}

	public boolean isDragging() {
		return isDragging;
	}

	public void setDragging(boolean isDragging) {
		if (this.isDragging != isDragging) {
			this.isDragging = isDragging;
			fireChangeEvent(SequencerProperty.IS_DRAGGING);
		}
	}

	public void addChangeListener(Listener<SequencerEvent> l) {
		if (listeners == null)
			listeners = new ListenerList<>();

		listeners.add(l);
	}

	public void removeChangeListener(Listener<SequencerEvent> l) {
		if (listeners != null)
			listeners.remove(l);
	}

	protected void fireChangeEvent(SequencerProperty property) {
		if (listeners != null && listeners.size() > 0)
			listeners.fire(new SequencerEvent(this, property));
	}

	public void setSequence(Sequence sequence) throws InvalidMidiDataException {
		cache = null;
		hoursPlus = 0L;
		if (sequencer.getSequence() != sequence) {
			trackActiveCache = null;
			boolean preLoaded = isLoaded();
			sequencer.setSequence(sequence);

			if (sequence != null) {
				/*
				 * System.out.print(transceivers.size()+" transceivers will play song: "+sequence.hashCode()+"\n"); for
				 * (Transceiver t : transceivers) { System.out.println(t.toString()); }
				 */
				tempoCache.refresh(sequence);
			}
			if (preLoaded != isLoaded())
				fireChangeEvent(SequencerProperty.IS_LOADED);
			fireChangeEvent(SequencerProperty.LENGTH);
			fireChangeEvent(SequencerProperty.SEQUENCE);
		}
	}

	public void clearSequence() {
		cache = null;
		hoursPlus = 0L;
		try {
			setSequence(null);
			realDuraTicks = Long.MAX_VALUE;
		} catch (InvalidMidiDataException e) {
			// This shouldn't happen
			throw new RuntimeException(e);
		}
	}

	public boolean isLoaded() {
		return sequencer.getSequence() != null;
	}

	public Sequence getSequence() {
		return sequencer.getSequence();
	}

	public Transmitter getTransmitter() {
		return transmitter;
	}

	public Receiver getReceiver() {
		return receiver;
	}

	public void open() throws MidiUnavailableException {
		sequencer.open();
	}

	public void close() {
		sequencer.close();
	}

	public boolean isUseSequenceTempoFactor() {
		return useSequenceTempoFactor;
	}

	public void setUseSequenceTempoFactor(boolean useSequenceTempoFactor) {
		this.useSequenceTempoFactor = useSequenceTempoFactor;
	}
}
