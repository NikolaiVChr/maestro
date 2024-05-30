package com.digero.common.midi;

import java.io.IOException;
import java.util.List;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.VoiceStatus;

import com.digero.maestro.abc.AbcExporter.ExportTrackInfo;
import com.digero.maestro.midi.SequenceInfo;

public class LotroSequencerWrapper extends NoteFilterSequencerWrapper {
	private static Synthesizer lotroSynth;
	private static String loadLotroSynthError;
	private long startTick = 0L;

	static {
		try {
			lotroSynth = SynthesizerFactory.getLotroSynthesizer();
		} catch (InvalidMidiDataException | MidiUnavailableException | IOException e) {
			loadLotroSynthError = e.getMessage();
		}
	}
	
	public LotroSequencerWrapper() throws MidiUnavailableException {
		super();
	}

	/**
	 * Only affects channels larger than 16.
	 * Will stop all active notes. And then inject a patch change to each channel
	 * since java don't do that properly with 16+ channels.
	 * 
	 * @param doControllers Stop all midi controllers also
	 */
	public void injectPatchChanges(boolean doControllers) {
		List<ExportTrackInfo> infos = SequenceInfo.lastTrackInfos;
		if (infos != null && infos.size() > MidiConstants.CHANNEL_COUNT-1) {
			for (ExportTrackInfo info : infos) {
					receiver.send(MidiFactory.createAllNotesOff(info.channel), -1L);
					receiver.send(MidiFactory.createSustainOff(info.channel), -1L);// -1 means real-time
					if (doControllers) {
						// We only do this when stopping to play
						receiver.send(MidiFactory.createAllControllersOff(info.channel), -1L);
					}
					receiver.send(MidiFactory.createLotroChangeEvent(info.patch, info.channel, sequencer.getTickPosition())
							.getMessage(), -1L);
			}
		}
	}

	@Override
	public void setRunning(boolean isRunning) {
		boolean running = sequencer.isRunning();
		super.setRunning(isRunning);
		if (running || isRunning) injectPatchChanges(!isRunning);
	}

	@Override
	public void setPosition(long position) {
		super.setPosition(position);
		if (sequencer.isRunning()) injectPatchChanges(false);
	}

	@Override
	public void setTickPosition(long tick) {
		super.setTickPosition(tick);
		if (sequencer.isRunning()) injectPatchChanges(false);
	}

	@Override
	public void setTrackMute(int track, boolean mute) {
		super.setTrackMute(track, mute);
		if (sequencer.isRunning()) injectPatchChanges(false);
	}

	@Override
	public void setTrackSolo(int track, boolean solo) {
		super.setTrackSolo(track, solo);
		if (sequencer.isRunning()) injectPatchChanges(false);
	}

	public static String getLoadLotroSynthError() {
		return loadLotroSynthError;
	}

	public boolean isUsingLotroInstruments() {
		return lotroSynth != null;
	}

	@Override
	public Receiver createReceiver() throws MidiUnavailableException {
		return (lotroSynth != null) ? lotroSynth.getReceiver() : MidiSystem.getReceiver();
	}

	public static int getNoteCount() {
		if (lotroSynth == null)
			return 0;

		VoiceStatus[] voices = lotroSynth.getVoiceStatus();
		if (voices != null && voices.length != 0) {
			int notes = 0;
			for (VoiceStatus voice : voices) {
				if (voice.active) {
					notes++;
				}
			}
			return notes;
		}
		return 0;
	}

	public long getStartTick() {
		return startTick;
	}

	public void setStartTick(long startTick) {
		this.startTick = startTick;
	}
}
