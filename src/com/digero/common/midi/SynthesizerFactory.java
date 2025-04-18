package com.digero.common.midi;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiDevice.Info;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;

//import com.digero.common.midi.synth.LotroSoftSynthesizer;
import com.sun.media.sound.AudioSynthesizer;

public class SynthesizerFactory {
	private static Soundbank lotroSoundbank = null;
	private static File soundFontFile = new File("LotroInstruments.sf2");

	public static void setSoundFontLocation(File soundFontFile) {
		if (SynthesizerFactory.soundFontFile != soundFontFile) {
			SynthesizerFactory.soundFontFile = soundFontFile;
			lotroSoundbank = null;
		}
	}

	public static Synthesizer getLotroSynthesizer()
			throws MidiUnavailableException, InvalidMidiDataException, IOException {
		Synthesizer synth = MidiSystem.getSynthesizer();
		// Synthesizer synth = new LotroSoftSynthesizer();
		if (synth != null)
			initLotroSynthesizer(synth);
		return synth;
	}

	public static AudioSynthesizer getLotroAudioSynthesizer()
			throws MidiUnavailableException, InvalidMidiDataException, IOException {
		AudioSynthesizer synth = findAudioSynthesizer();
		if (synth != null)
			initAudioSynthesizer(synth);
		return synth;
	}

	@SuppressWarnings("restriction")
	public static void initLotroSynthesizer(Synthesizer synth)
			throws MidiUnavailableException, InvalidMidiDataException, IOException {
		Map<String, Object> synthInfo = new HashMap<>();
		synthInfo.put("midi channels", MidiConstants.CHANNEL_COUNT_ABC);// default is 16
		synthInfo.put("reverb", false);// default is true
		synthInfo.put("chorus", false);// default is true
		synthInfo.put("max polyphony", 128);// default is 64
		synthInfo.put("auto gain control", true);// default is true. Set to false it can give pops when skipping in
													// song, especially for abc player.
		synthInfo.put("latency", 250000L);// 12000 microseconds is default. But that low with 24 parts will give pops
											// and clicks in playback in abc
											// player.
		// synthInfo.put("jitter correction", true);//Not sure what this does or what the default value is.
		synthInfo.put("large mode", false);// Default false. Not sure what it does. If enabled it seems to use lazy
											// loading of soundfont samples.
		((com.sun.media.sound.SoftSynthesizer) synth).open(null, synthInfo);
		// ((LotroSoftSynthesizer)synth).open(null, synthInfo);
		synth.unloadAllInstruments(getLotroSoundbank());
		synth.loadAllInstruments(getLotroSoundbank());
	}

	@SuppressWarnings("restriction")
	public static void initAudioSynthesizer(Synthesizer synth)
			throws MidiUnavailableException, InvalidMidiDataException, IOException {
		Map<String, Object> synthInfo = new HashMap<>();
		synthInfo.put("midi channels", MidiConstants.CHANNEL_COUNT_ABC);// default is 16
		synthInfo.put("reverb", false);// default is true
		synthInfo.put("chorus", false);// default is true
		synthInfo.put("max polyphony", 128);// default is 64
		synthInfo.put("latency", 200000L);// 120000 microseconds is default, notice factor 10 from SoftSynth.
		// synthInfo.put("jitter correction", true);//Not sure what this does.
		synthInfo.put("large mode", false);// Default false. Not sure what it does.
		// ((com.sun.media.sound.SoftSynthesizer)synth).open(null, synthInfo);
		((AudioSynthesizer) synth).open(null, synthInfo);
		synth.unloadAllInstruments(getLotroSoundbank());
		synth.loadAllInstruments(getLotroSoundbank());
	}

	public static Soundbank getLotroSoundbank() throws InvalidMidiDataException, IOException {
		if (lotroSoundbank == null) {
			if (!soundFontFile.exists()) {
				String folder = ".";
				try {
					// Find the path to the jar file we are executing in
					folder = new File(
							SynthesizerFactory.class.getProtectionDomain().getCodeSource().getLocation().toURI())
							.getParent();
				} catch (URISyntaxException e) {
					e.printStackTrace();
				}
				soundFontFile = new File(folder, "LotroInstruments.sf2");
			}
			try {
				lotroSoundbank = MidiSystem.getSoundbank(soundFontFile);
			} catch (NullPointerException npe) {
				// JARSoundbankReader throws a NullPointerException if the file doesn't exist
				StackTraceElement trace = npe.getStackTrace()[0];
				if (trace.getClassName().equals("com.sun.media.sound.JARSoundbankReader")
						&& trace.getMethodName().equals("isZIP")) {
					throw new IOException("Soundbank file not found");
				} else {
					throw npe;
				}
			}
		}
		/*		
		try {
			Synthesizer synth2 = MidiSystem.getSynthesizer();
			Soundbank sf3Bank = MidiSystem.getSoundbank(new File("test.sf3"));
			boolean supported = synth2.isSoundbankSupported(sf3Bank);
			System.out.println("SF3 Supported: " + supported);
		} catch (MidiUnavailableException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		*/
		return lotroSoundbank;
	}

	/**
	 * Find available AudioSynthesizer
	 */
	public static AudioSynthesizer findAudioSynthesizer() throws MidiUnavailableException {
		// First check if default synthesizer is AudioSynthesizer.
		Synthesizer synth = MidiSystem.getSynthesizer();
		if (synth instanceof AudioSynthesizer)
			return (AudioSynthesizer) synth;

		// If default synhtesizer is not AudioSynthesizer, check others.
		for (Info info : MidiSystem.getMidiDeviceInfo()) {
			MidiDevice dev = MidiSystem.getMidiDevice(info);
			if (dev instanceof AudioSynthesizer)
				return (AudioSynthesizer) dev;
		}

		// No AudioSynthesizer was found, return null.
		return null;
	}
}
