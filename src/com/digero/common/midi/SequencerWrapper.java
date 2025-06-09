package com.digero.common.midi;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

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
	public static final int UPDATE_FREQUENCY_MILLIS = 50;
	public static final long UPDATE_FREQUENCY_MICROS = UPDATE_FREQUENCY_MILLIS * 1000;

	protected Sequencer sequencer;
	protected Receiver receiver;
	private Transmitter transmitter;
	private List<Transceiver> transceivers = new ArrayList<>();
	private long dragTick;
	private boolean isDragging;
	private TempoCacheSlow tempoCache = new TempoCacheSlow();
	private boolean[] trackActiveCache = null;

	private Timer updateTimer = new Timer(UPDATE_FREQUENCY_MILLIS, new TimerActionListener());
	private long lastUpdateTick = -1;
	private boolean lastRunning = false;
	private TempoCacheSlow cache = null;
	private long hoursPlus = 0L;
	private float tempoFactor = 1.f;
	protected long realDuraTicks = Long.MAX_VALUE;
	
	public static boolean onlyFirstTrackTempos = true;
	
	// For AbcPlayer, we should send the tempo factor to the sequence.
	// For Maestro, the tempo factor is factored into midi tempo messages
	// when the sequence is refreshed, so it shouldn't be sent to the sequence.
	private boolean useSequenceTempoFactor = false;

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
			lastTransmitter = transceivers.get(transceivers.size() - 1);

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
				ShortMessage msg = new ShortMessage();
				msg.setMessage(ShortMessage.SYSTEM_RESET);
				receiver.send(msg, -1);
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
				for (int i = 0; i < CHANNEL_COUNT_ABC; i++) {
					msg.setMessage(ShortMessage.PROGRAM_CHANGE, i, 0, 0);
					receiver.send(msg, -1);
					msg.setMessage(ShortMessage.CONTROL_CHANGE, i, RESET_ALL_CONTROLLERS, 0);
					receiver.send(msg, -1);
				}
				msg.setMessage(ShortMessage.SYSTEM_RESET);
				receiver.send(msg, -1);
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
		if (tick != getTickPosition()) {
			sequencer.setTickPosition(tick);
			lastUpdateTick = sequencer.getTickPosition();
			fireChangeEvent(SequencerProperty.POSITION);
		}
	}

	public long getPosition() {
		if (getSequence() == null)
			return 0L;
		// if (hoursPlus > 0 && getSequence() != null) {
		long tick = sequencer.getTickPosition();
		return tick2microsecondSlow(getSequence(), tick);
		// }
		// return sequencer.getMicrosecondPosition();
	}

	public void setPosition(long position) {
		if (position == 0) {
			// Sun's RealtimeSequencer isn't entirely reliable when calling
			// setMicrosecondPosition(0). Instead call setTickPosition(0),
			// which has the same effect and isn't so buggy.
			setTickPosition(0);
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

		return tick2microsecondSlow(sequence, tick);
	}

	/**
	 * 
	 * @return sequence duration in microseconds
	 */
	public long getLength() {
		// long l = sequencer.getMicrosecondLength();
		// if (l < 0) {
		long l = checkForSuperLongDuration(0);
		// }
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
		// index in ticks/tempos at the snapshot
		int snapshotIndex = 0;
		// microsecond at the snapshot
		long snapshotMicro = 0;

		int currTempo; // MPQ, used as return value for microsecond2tick

		private boolean firstTempoIsFake = false;

		public TempoCacheSlow() {
			// just some defaults, to prevents weird stuff
			ticks = new long[1];
			tempos = new int[1];
			tempos[0] = MidiUtils.DEFAULT_TEMPO_MPQ;
			snapshotIndex = 0;
			snapshotMicro = 0;
		}

		public TempoCacheSlow(Sequence seq) {
			this();
			refresh(seq);
		}

		public synchronized void refresh(Sequence seq) {
			ArrayList<MidiEvent> list = new ArrayList<>();
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
							list.add(ev);
						}
					}
				} else {
					// tempo events only occur in track 0
					for(Track track : tracks) {
						int c = track.size();
						for (int i = 0; i < c; i++) {
							MidiEvent ev = track.get(i);
							MidiMessage msg = ev.getMessage();
							if (MidiUtils.isMetaTempo(msg) && MidiUtils.getTempoMPQ(msg) != 0) {
								// found a valid tempo event. Add it to the list
								list.add(ev);
							}
						}
					}
				}
			}
			int size = list.size() + 1;
			firstTempoIsFake = true;
			if ((size > 1) && (list.get(0).getTick() == 0)) {
				// do not need to add an initial tempo event at the beginning
				size--;
				firstTempoIsFake = false;
			}
			ticks = new long[size];
			tempos = new int[size];
			int e = 0;
			if (firstTempoIsFake) {
				// add tempo 120 at beginning
				ticks[0] = 0;
				tempos[0] = MidiUtils.DEFAULT_TEMPO_MPQ;
				e++;
			}
			for (int i = 0; i < list.size(); i++, e++) {
				MidiEvent evt = list.get(i);
				ticks[e] = evt.getTick();
				tempos[e] = MidiUtils.getTempoMPQ(evt.getMessage());
			}
			snapshotIndex = 0;
			snapshotMicro = 0;
		}

		public int getCurrTempoMPQ() {
			return currTempo;
		}

		float getTempoMPQAt(long tick) {
			return getTempoMPQAt(tick, -1.0f);
		}

		synchronized float getTempoMPQAt(long tick, float startTempoMPQ) {
			for (int i = 0; i < ticks.length; i++) {
				if (ticks[i] > tick) {
					if (i > 0)
						i--;
					if (startTempoMPQ > 0 && i == 0 && firstTempoIsFake) {
						return startTempoMPQ;
					}
					return (float) tempos[i];
				}
			}
			return tempos[tempos.length - 1];
		}
	}

	/**
	 * Given a tick, convert to microsecond
	 */
	private long tick2microsecondSlow(Sequence seq, long tick) {
		if (seq.getDivisionType() != Sequence.PPQ) {
			double seconds = ((double) tick / (double) (seq.getDivisionType() * seq.getResolution()));
			// System.out.println("Divisiontype != PPQ");
			return (long) (1000000 * seconds);
		}

		boolean firstTime = false;
		if (cache == null) {
			cache = new TempoCacheSlow(seq);
			firstTime = true;
		}

		int resolution = seq.getResolution();

		long[] ticks = cache.ticks;
		int[] tempos = cache.tempos; // in MPQ
		int cacheCount = tempos.length;

		// optimization to not always go through entire list of tempo events
		int snapshotIndex = cache.snapshotIndex;
		long snapshotMicro = cache.snapshotMicro;

		// walk through all tempo changes and add time for the respective blocks
		long us = 0; // microsecond

		if (firstTime || snapshotIndex <= 0 || snapshotIndex >= cacheCount || ticks[snapshotIndex] > tick) {
			snapshotMicro = 0;
			snapshotIndex = 0;
		}
		if (cacheCount > 0) {
			// this implementation needs a tempo event at tick 0!
			int i = snapshotIndex + 1;
			while (i < cacheCount && ticks[i] <= tick) {
				long usPlus = MidiUtils.ticks2microsec(ticks[i] - ticks[i - 1], tempos[i - 1], resolution);
				snapshotMicro += usPlus;
				snapshotIndex = i;
				i++;
			}
			us = snapshotMicro
					+ MidiUtils.ticks2microsec(tick - ticks[snapshotIndex], tempos[snapshotIndex], resolution);
		}
		cache.snapshotIndex = snapshotIndex;
		cache.snapshotMicro = snapshotMicro;
		return us;
	}

	private long checkForSuperLongDuration(long l) {
		Sequence sequ = sequencer.getSequence();
		if (sequ != null) {
			l = tick2microsecondSlow(sequ, Math.min(realDuraTicks, sequencer.getTickLength()));
			long hours = l / 3600000000L;
			if (hoursPlus == 0L && hours > 0) {
				hoursPlus = hours;
			}
		}
		return l;
		/*
		 * this also works, but the above way is better if (hoursPlus > 0) { return -l+3600000000L*hoursPlus; } Sequence
		 * seq = sequencer.getSequence(); if (seq != null) { Track[] tracks = seq.getTracks(); long lastTick = 0; for
		 * (Track track : tracks) { if (track.ticks() > lastTick) { lastTick = track.ticks(); } } if (lastTick > 0L) {
		 * //System.out.println("lastTick="+lastTick+" us="+tick2microsecondSlow(seq, lastTick)); long hours =
		 * tick2microsecondSlow(seq, lastTick)/3600000000L;
		 * System.out.println("This midi is over "+hours+" hours long. But do not worry :)"); l = -l+3600000000L*hours;
		 * hoursPlus = hours; } } return l;
		 */
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
	 * If dragging, returns the drag position. Otherwise returns the song position.
	 */
	public long getThumbPosition() {
		return isDragging() ? getDragPosition() : getPosition();
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
