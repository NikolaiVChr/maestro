package com.digero.abcplayer;

import java.io.OutputStream;
import java.util.Map;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import com.digero.common.midi.SynthesizerFactory;
import com.sun.media.sound.AudioSynthesizer;

public class MidiToWav {
	/**
	 * Render sequence using selected or default soundbank into wave audio file.
	 */
	public static void render(Sequence sequence, OutputStream out, long startTick) throws MidiUnavailableException {
		try {
			// Find available AudioSynthesizer.
			AudioSynthesizer synth = SynthesizerFactory.findAudioSynthesizer();
			if (synth == null) {
				throw new MidiUnavailableException("Failed to find appropriate synthesizer");
			}

			// Open AudioStream from AudioSynthesizer.
			boolean opened = synth.isOpen();
			if (opened)
				synth.close();

			Map<String, Object> synthInfo = SynthesizerFactory.setupSynthesizerPropertyInfo();
			
			AudioInputStream stream = synth.openStream(null, synthInfo);
			SynthesizerFactory.initAudioSynthesizer(synth);

			// Play Sequence into AudioSynthesizer Receiver.
			double total = send(sequence, synth.getReceiver(), startTick);

			// Calculate how long the WAVE file needs to be.
			long len = (long) (stream.getFormat().getFrameRate() * (total + 1));
			stream = new AudioInputStream(stream, stream.getFormat(), len);

			// Write WAVE file to disk.
			AudioSystem.write(stream, AudioFileFormat.Type.WAVE, out);

			// We are finished, close synthesizer.
			synth.close();

			if (opened)
				synth.open();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Send entire MIDI Sequence into Receiver using timestamps.
	 */
	public static double send(Sequence seq, Receiver recv, long startTick) {
		float divtype = seq.getDivisionType();
		assert (seq.getDivisionType() == Sequence.PPQ);
		Track[] tracks = seq.getTracks();
		int[] trackspos = new int[tracks.length];
		int mpq = 500000;
		int seqres = seq.getResolution();
		long lasttick = 0;
		long curtime = 0;
		while (true) {
			MidiEvent selevent = null;
			int seltrack = -1;
			for (int i = 0; i < tracks.length; i++) {
				int trackpos = trackspos[i];
				Track track = tracks[i];
				if (trackpos < track.size()) {
					MidiEvent event = track.get(trackpos);
					if (selevent == null || event.getTick() < selevent.getTick()) {
						selevent = event;
						seltrack = i;
					}
				}
			}
			if (seltrack == -1)
				break;
			trackspos[seltrack]++;
			long tick = Math.max(0L, selevent.getTick()-startTick);
			curtime = calculateCurTime(divtype, mpq, seqres, lasttick, curtime, tick);
			lasttick = tick;
			MidiMessage msg = selevent.getMessage();
			if (msg instanceof MetaMessage) {
				mpq = calculateMpq(divtype, mpq, msg);
			} else {
				if (recv != null)
					recv.send(msg, curtime);
			}
		}
		return curtime / 1000000.0;
	}

	private static int calculateMpq(float divtype, int mpq, MidiMessage msg) {
		if (divtype == Sequence.PPQ && (((MetaMessage) msg).getType() == 0x51)) {
			byte[] data = ((MetaMessage) msg).getData();
			return ((data[0] & 0xff) << 16) | ((data[1] & 0xff) << 8) | (data[2] & 0xff);
		}
		return mpq;
	}

	private static long calculateCurTime(float divtype, int mpq, int seqres, long lasttick, long curtime, long tick) {
		if (divtype == Sequence.PPQ)
			curtime += ((tick - lasttick) * mpq) / seqres;
		else
			curtime = (long) ((tick * 1000000.0 * divtype) / seqres);
		return curtime;
	}

}
