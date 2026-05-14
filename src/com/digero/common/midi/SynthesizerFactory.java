package com.digero.common.midi;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiDevice.Info;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;
import javax.sound.sampled.AudioFormat;

//import com.digero.common.midi.synth.LotroSoftSynthesizer;
import com.sun.media.sound.AudioSynthesizer;
import com.sun.media.sound.AudioSynthesizerPropertyInfo;

import static com.digero.common.util.SoundFontDownloader.getCommonDataDirectory;

public class SynthesizerFactory {
	private static final Logger log = Logger.getLogger("playback.abc");
	private static Soundbank lotroSoundbank = null;
	private static File soundFontFile = new File("LotroInstruments.sf2");
	private static Soundbank customSoundfont = null;
    public static final long PLAYBACK_LATENCY_MICROS = 250000L;

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
		else
			log.severe("Failed to make lotro synth");
		return synth;
	}

	public static AudioSynthesizer getLotroAudioSynthesizer()
			throws MidiUnavailableException, InvalidMidiDataException, IOException {
		AudioSynthesizer synth = findAudioSynthesizer();
		if (synth != null)
			initAudioSynthesizer(synth);
		else
			log.severe("Failed to make wav synth");
		return synth;
	}

	private static com.sun.media.sound.SoftSynthesizer customMidisynth = null;
	public static Synthesizer getCustomMIDIAudioSynthesizer() {
		// The soundfont file should be named AppData/Local/MaestroCommon/midi.sf2
		try {
			if (customMidisynth != null) return customMidisynth;
			Soundbank bank = getCustomSoundbank();
			if (bank == null) return null;
			customMidisynth = findMIDISynthesizer();
			if (customMidisynth != null) {
				customMidisynth.open(null, setupSynthesizerPropertyInfo());
				customMidisynth.unloadAllInstruments(bank);
				customMidisynth.loadAllInstruments(bank);
			}
		} catch (Throwable t) {
			log.log(Level.WARNING, "Failed to create synth for custom midi SF2 soundbank", t);
		}
		return customMidisynth;
	}

	@SuppressWarnings("HardCodedStringLiteral")
	public static Map<String, Object> setupSynthesizerPropertyInfo() {
		Map<String, Object> synthInfo = new HashMap<>();
		synthInfo.put("midi channels", MidiConstants.CHANNEL_COUNT_ABC);// default is 16
		synthInfo.put("reverb", false);// default is true
		synthInfo.put("chorus", false);// default is true
		synthInfo.put("light reverb", false);// default is true
		synthInfo.put("device id", 0);// default is 0
		synthInfo.put("load default soundbank", false);// default is true
		synthInfo.put("max polyphony", 128);// default is 64
		synthInfo.put("control rate", 147f); // default is 147f
		synthInfo.put("interpolation", "point");// default is linear. Options: "point", "linear", "linear1", "linear2", "cubic", "lanczos", "sinc".
		synthInfo.put("auto gain control", true);// default is true. Set to false it can give pops when skipping in
													// song, especially for abc player.
		synthInfo.put("latency", PLAYBACK_LATENCY_MICROS);// 12000 microseconds is default. But that low with 24 parts will give pops
										  // and clicks in playback in abc player.
		synthInfo.put("jitter correction", true);//default is true. Use seperate thread with nanotime to make playback of messages more timewise accurate. Is also cause of why maestro playback gets delayed after OS sleep or hibernation.
		synthInfo.put("large mode", false);// Default false. If enabled it seems to use lazy
											// loading of soundfont samples.
		synthInfo.put("format", new AudioFormat(44100, 16, 2, true, false));// use mono samples in memory
		return synthInfo;
	}

	@SuppressWarnings("HardCodedStringLiteral")
	public static Map<String, Object> setupExternalSynthesizerPropertyInfo() {
		Map<String, Object> synthInfo = new HashMap<>();
		synthInfo.put("reverb", false);// default is true
		synthInfo.put("chorus", false);// default is true
		synthInfo.put("light reverb", false);// default is true
		return synthInfo;
	}

	/**
	 * This is used for ABC preview in both Maestro and AbcPlayer
	 *
     */
	@SuppressWarnings("restriction")
	public static void initLotroSynthesizer(Synthesizer synth)
			throws MidiUnavailableException, InvalidMidiDataException, IOException {

		((com.sun.media.sound.SoftSynthesizer) synth).open(null, setupSynthesizerPropertyInfo());
		// ((LotroSoftSynthesizer)synth).open(null, synthInfo);
		//synth.unloadAllInstruments(getLotroSoundbank()); // not needed, as we only make it once
		synth.loadAllInstruments(getLotroSoundbank());
		
		//uncomment this to check default values
		//outputProperties(synth);
	}

	private static void outputProperties(Synthesizer synth) {
		Map<String, Object> synthInfo = new HashMap<>();
		AudioSynthesizerPropertyInfo[] infos = ((com.sun.media.sound.SoftSynthesizer) synth).getPropertyInfo(synthInfo);
		for (AudioSynthesizerPropertyInfo inf : infos) {
			System.out.println("\n"+inf.name);
			System.out.println(inf.description);
			System.out.println(inf.value);
		}
	}

	/**
	 * This is used for exporting wav and mp3 audio files.
	 *
     */
	@SuppressWarnings("restriction")
	public static void initAudioSynthesizer(Synthesizer synth)
			throws MidiUnavailableException, InvalidMidiDataException, IOException {
		((AudioSynthesizer) synth).open(null, setupSynthesizerPropertyInfo());
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
					log.log(java.util.logging.Level.SEVERE, "Failed to find soundfont", e);
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
					log.log(Level.SEVERE, "Failed to find soundfont", npe);
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

	public static final String customMidiSoundfontFilename = "midi.sf2";
	public static Soundbank getCustomSoundbank() throws InvalidMidiDataException, IOException {
		if (customSoundfont == null) {
			try {
				File dataDir = getCommonDataDirectory();
				if (dataDir != null) {
					File sf2File = new File(dataDir, customMidiSoundfontFilename);
					if (sf2File.exists()) {
						customSoundfont = MidiSystem.getSoundbank(sf2File);
					}
				}
			} catch (Throwable t) {
				log.log(Level.WARNING, "Failed to load custom midi SF2 soundbank", t);
			}
		}
		return customSoundfont;
	}

	public static com.sun.media.sound.SoftSynthesizer findMIDISynthesizer() throws MidiUnavailableException {
		// First check if default synthesizer is AudioSynthesizer.
		Synthesizer synth = MidiSystem.getSynthesizer();
		if (synth instanceof com.sun.media.sound.SoftSynthesizer && synth != LotroSequencerWrapper.getLotroSynth())
			return (com.sun.media.sound.SoftSynthesizer) synth;

		// If default synthesizer is not SoftSynthesizer, check others.
		for (Info info : MidiSystem.getMidiDeviceInfo()) {
			MidiDevice dev = MidiSystem.getMidiDevice(info);
			if (dev instanceof com.sun.media.sound.SoftSynthesizer && dev != LotroSequencerWrapper.getLotroSynth())
				return (com.sun.media.sound.SoftSynthesizer) dev;
		}

		log.severe("No custom MIDI audio synth found");
		return null;
	}

	/**
	 * Find available AudioSynthesizer
	 */
	public static AudioSynthesizer findAudioSynthesizer() throws MidiUnavailableException {
		// First check if default synthesizer is AudioSynthesizer.
		Synthesizer synth = MidiSystem.getSynthesizer();
		if (synth instanceof AudioSynthesizer)
			return (AudioSynthesizer) synth;

		// If default synthesizer is not AudioSynthesizer, check others.
		for (Info info : MidiSystem.getMidiDeviceInfo()) {
			MidiDevice dev = MidiSystem.getMidiDevice(info);
			if (dev instanceof AudioSynthesizer)
				return (AudioSynthesizer) dev;
		}

		// No AudioSynthesizer was found, return null.
		log.severe("No audio synth found");
		return null;
	}

	public static boolean userOwnSoundFontExist() {
		// this is for lotro soundfont
        if (soundFontFile != null && soundFontFile.exists()) return true;
		if (!soundFontFile.exists()) {
			log.log(Level.INFO, "Soundfont file not found, trying jar location.");
			String folder = ".";
			try {
				// Find the path to the jar file we are executing in
				folder = new File(
						SynthesizerFactory.class.getProtectionDomain().getCodeSource().getLocation().toURI())
						.getParent();
			} catch (URISyntaxException e) {
				log.log(java.util.logging.Level.SEVERE, "Failed to find soundfont", e);
			}
			soundFontFile = new File(folder, "LotroInstruments.sf2");
		}
		if (soundFontFile != null && soundFontFile.exists()) return true;
		return false;
    }

	public static void setSoundbank(File sf2) {
		soundFontFile = sf2;
	}
}
