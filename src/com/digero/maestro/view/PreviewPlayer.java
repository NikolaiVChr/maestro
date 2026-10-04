package com.digero.maestro.view;

import com.digero.common.midi.MidiConstants;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;

/**
 * Plays a tune for the tunebook dialog's preview. Play, then pause: play goes on from where it paused. Another tune
 * stops the one playing, and plays from its beginning. At the end of a tune, play starts it again. The sequencer is
 * opened at the first play, and closed by close.
 */
final class PreviewPlayer {
	/** Opens the sequencer to play with. */
	interface SequencerSource {
		Sequencer open() throws MidiUnavailableException;
	}

	/** Java's own sequencer, playing on the default synthesizer. */
	static Sequencer openDefault() throws MidiUnavailableException {
		Sequencer sequencer = MidiSystem.getSequencer();
		sequencer.open();
		return sequencer;
	}

	private final SequencerSource source;
	private Sequencer sequencer; // Null until the first play, and after close
	private Sequence tune; // What play plays, or null
	private boolean loaded; // tune is the sequencer's sequence
	private Runnable onEnd = () -> {
	};

	PreviewPlayer(SequencerSource source) {
		this.source = source;
	}

	/** The tune to play from now on (null for none): the one playing stops. */
	void setTune(Sequence tune) {
		if (isPlaying())
			sequencer.stop();
		this.tune = tune;
		loaded = false;
	}

	/** Called when a tune has played to its end (on the sequencer's thread). */
	void setOnEnd(Runnable onEnd) {
		this.onEnd = onEnd;
	}

	boolean canPlay() {
		return tune != null;
	}

	boolean isPlaying() {
		return sequencer != null && sequencer.isRunning();
	}

	/** Pauses the tune if it's playing; else plays it, from where it paused, or from its beginning. */
	void playOrPause() throws MidiUnavailableException, InvalidMidiDataException {
		if (isPlaying()) {
			sequencer.stop();
			return;
		}
		if (tune == null)
			return;
		if (sequencer == null) {
			sequencer = source.open();
			sequencer.addMetaEventListener((MetaMessage message) -> {
				if (message.getType() == MidiConstants.META_END_OF_TRACK)
					onEnd.run();
			});
		}
		if (!loaded) {
			sequencer.setSequence(tune);
			sequencer.setTickPosition(0);
			loaded = true;
		} else if (sequencer.getTickPosition() >= tune.getTickLength()) {
			sequencer.setTickPosition(0); // Played to its end: again
		}
		sequencer.start();
	}

	/** Stops, and lets the sequencer go. */
	void close() {
		if (sequencer != null) {
			sequencer.stop();
			sequencer.close();
			sequencer = null;
		}
		loaded = false;
	}

	/** For tests: the sequencer, or null. */
	Sequencer sequencer() {
		return sequencer;
	}
}