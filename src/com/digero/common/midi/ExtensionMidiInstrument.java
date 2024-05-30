package com.digero.common.midi;

import static java.lang.Integer.parseInt;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

public class ExtensionMidiInstrument {

	public static final String TRACK_NAME_DRUM_GM = "Drums";
	public static final String TRACK_NAME_DRUM_GS = "GS Drums";
	public static final String TRACK_NAME_DRUM_XG = "XG Drums";
	public static final String TRACK_NAME_DRUM_GM2 = "GM2 Drums";

	private static ExtensionMidiInstrument instance = null;
	private static HashMap<String, String> mapxg = new HashMap<>();
	private static HashMap<String, String> mapgs = new HashMap<>();
	private static HashMap<String, String> mapgm2 = new HashMap<>();

	public static ExtensionMidiInstrument getInstance() {
		if (instance != null) {
			return instance;
		}

		instance = new ExtensionMidiInstrument();

		parse(MidiStandard.XG, (byte) 0, "xg.txt", true, false);
		parse(MidiStandard.GS, (byte) 0, "gs.txt", true, true);
		parse(MidiStandard.GS, (byte) 120, "gsKits.txt", false, false);
		parse(MidiStandard.GM2, (byte) 121, "gm2.txt", true, false);
		parse(MidiStandard.GM2, (byte) 120, "gm2-120.txt", false, false);
		parse(MidiStandard.XG, (byte) 127, "xg127.txt", false, false);
		parse(MidiStandard.XG, (byte) 126, "xg126.txt", false, false);
		parse(MidiStandard.XG, (byte) 64, "xg64.txt", false, false);

		/*
		 * GM voices: 129 GS voices: 1170 XG voices: 1011 GM2 voices: 136 Total : 2446
		 */

		/*
		 * System.out.println("GM  voices: 129"); System.out.println("GS  voices: "+(mapgs.size()-129));
		 * System.out.println("XG  voices: "+(mapxg.size()-129));
		 * System.out.println("GM2 voices: "+(mapgm2.size()-129));
		 * System.out.println("Total     : "+(mapgm2.size()-129+mapxg.size()-129+mapgs. size()-129+129));
		 */

		return instance;
	}

	/**
	 * Determine name of voice.
	 * 
	 * @param extension
	 * @param MSB
	 * @param LSB
	 * @param patch
	 * @param rhythmChannel rhythmic non-chromatic channel.
	 * @return string with instrument name
	 */
	public String fromId(MidiStandard extension, byte MSB, byte LSB, byte patch, boolean drumKit,
			boolean rhythmChannel) {
		/*
		 * 
		 * Abbreviations that are not expanded: KSP: Keyboard Stereo Panning (in GS/GM2 language this is called 'Wide')
		 * 
		 */

		// GS does not have Dulcimer on patch 15 MSB 0 like GM but a Santur, so we are
		// careful to fetch its actual name.
		boolean santur = extension == MidiStandard.GS && MSB == 0 && patch == 15 && !rhythmChannel;

		if (extension == MidiStandard.GS && rhythmChannel) {
			// Bank 120 is forced on drum channels in GS.
			MSB = 120;
		}
		if (extension == MidiStandard.GS) {
			// LSB is used to switch between different synth voice set in GS. Since only
			// have 1 synth file, just pipe all into LSB 0.
			// LSB 1 = SC-55, 2 = SC-88, 3 = SC-88Pro, 4 = SC-8850
			LSB = 0;
		}
		if (!drumKit && (extension == MidiStandard.GM || (MSB == 0 && LSB == 0 && !santur))) {
			return MidiInstrument.fromId(patch).name;
		} else if (MSB == 121 && LSB == 0 && extension == MidiStandard.GM2) {
			// LSB 0 on MSB 121 is same as GM midi standard.
			return MidiInstrument.fromId(patch).name;
		}
		if (MSB == 127 && extension == MidiStandard.XG) {
			// As per XG specs, LSB is ignored if MSB is 0x7F.
			// Note: I wonder why this is not done for 0x7E also..
			LSB = 0;
		}

		String instrName = determineInstrumentName(extension, MSB, LSB, patch);
		if (instrName == null && !drumKit) {
			return MidiInstrument.fromId(patch).name;
		} else if (instrName == null) {
			return MidiInstrument.STANDARD_DRUM_KIT;
		}
		return instrName;
	}

	private String determineInstrumentName(MidiStandard extension, byte MSB, byte LSB, byte patch) {
		String instrName = null;

		if (extension == MidiStandard.XG) {
			instrName = mapxg.get(String.format("%03d%03d%03d", MSB, LSB, patch));
		} else if (extension == MidiStandard.GS) {
			instrName = mapgs.get(String.format("%03d%03d%03d", MSB, LSB, patch));
		} else if (extension == MidiStandard.GM2) {
			instrName = mapgm2.get(String.format("%03d%03d%03d", MSB, LSB, patch));
		}
		return instrName;
	}

	private static void parse(MidiStandard extension, byte theByte, String fileName, boolean firstColumnPatch,
			boolean theByteIsLSB) {
		try {
			InputStream in = instance.getClass().getResourceAsStream(fileName);
			if (in == null) {
				System.err.println(fileName + " not readable.");
				return;
			}
			BufferedReader theFileReader = new BufferedReader(new InputStreamReader(in));
			String line = theFileReader.readLine();
			int lastPatch = -1;
			int lookupByte = -1;
			String regex = "\t+";// one or more tabs
			readLines(extension, theByte, fileName, firstColumnPatch, theByteIsLSB, theFileReader, line, lastPatch,
					lookupByte, regex);
			theFileReader.close();
		} catch (FileNotFoundException e) {
			System.err.println(fileName + " not readable.");
			e.printStackTrace();
		} catch (IOException e) {
			System.err.println(fileName + " line failed to read.");
			e.printStackTrace();
		}
	}

	private static void readLines(MidiStandard extension, byte theByte, String fileName, boolean firstColumnPatch,
			boolean theByteIsLSB, BufferedReader theFileReader, String line, int lastPatch, int lookupByte,
			String regex) throws IOException {
		while (line != null) {
			if (line.isEmpty()) {
				line = theFileReader.readLine();
				continue;
			}
			if (line.startsWith("\t")) {
				String[] splits = line.split(regex);
				if (splits.length != 3) {
					// Something is wrong in the tab formatting of one of the files
					System.err.println("\nWrong number of tabs in " + fileName + ":");
					int l = 0;
					for (String a : splits) {
						System.err.println(l + ": " + a);
						l++;
					}
					line = theFileReader.readLine();
					continue;
				}
				String lookupString = splits[1].trim();
				lookupByte = parseInt(lookupString.trim());
				addInstruments(extension, theByte, firstColumnPatch, theByteIsLSB, lastPatch, lookupByte, splits);
			} else {
				String patchString = line.trim();
				lastPatch = Integer.parseInt(patchString);
			}
			line = theFileReader.readLine();
		}
	}

	private static void addInstruments(MidiStandard extension, byte theByte, boolean firstColumnPatch,
			boolean theByteIsLSB, int lastPatch, int lookupByte, String[] splits) {
		if (theByteIsLSB) {
			if (firstColumnPatch) {
				addInstrument(extension, (byte) lookupByte, theByte, (byte) lastPatch, splits[2].trim());
			} else {
				addInstrument(extension, (byte) lastPatch, theByte, (byte) lookupByte, splits[2].trim());
			}
		} else {
			if (firstColumnPatch) {
				addInstrument(extension, theByte, (byte) lookupByte, (byte) lastPatch, splits[2].trim());
			} else {
				addInstrument(extension, theByte, (byte) lastPatch, (byte) lookupByte, splits[2].trim());
			}
		}
	}

	private static void addInstrument(MidiStandard extension, byte MSB, byte LSB, byte patch, String name) {
		// System.err.println(" addInstrument "+name+" ("+MSB+", "+LSB+", "+patch+")");
		String key = String.format("%03d%03d%03d", MSB, LSB, patch);
		if (extension == MidiStandard.XG) {
			if (mapxg.get(key) != null)
				System.out.println("Warning duplicate entry for (" + MSB + ", " + LSB + ", " + patch + ") in XG map");
			mapxg.put(key, name);
		} else if (extension == MidiStandard.GS) {
			if (mapgs.get(key) != null)
				System.out.println("Warning duplicate entry for (" + MSB + ", " + LSB + ", " + patch + ") in GS map");
			mapgs.put(key, name);
		} else if (extension == MidiStandard.GM2) {
			if (mapgm2.get(key) != null)
				System.out.println("Warning duplicate entry for (" + MSB + ", " + LSB + ", " + patch + ") in GM2 map");
			mapgm2.put(key, name);
		}
	}
}
