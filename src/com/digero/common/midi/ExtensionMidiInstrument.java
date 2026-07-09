package com.digero.common.midi;

import org.jetbrains.annotations.NonNls;

import static java.lang.Integer.parseInt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Threadsafe
 */
public class ExtensionMidiInstrument {
	private static final Logger log = Logger.getLogger("file");

	public static final String TRACK_NAME_DRUM_GM = "Drums";
	public static final String TRACK_NAME_DRUM_GS = "GS Drums";
	public static final String TRACK_NAME_DRUM_XG = "XG Drums";
	public static final String TRACK_NAME_DRUM_GM2 = "GM2 Drums";

	private static final ExtensionMidiInstrument instance = new ExtensionMidiInstrument();

	private final Map<MidiStandard, Map<String, String>> maps = new EnumMap<>(MidiStandard.class);

	private ExtensionMidiInstrument() {

		maps.put(MidiStandard.XG, new HashMap<>());
		maps.put(MidiStandard.GS, new HashMap<>());
		maps.put(MidiStandard.GM2, new HashMap<>());

        parse(MidiStandard.XG, (byte) 0, "xg.txt", true, false);
        parse(MidiStandard.GS, (byte) 0, "gs.txt", true, true);
        parse(MidiStandard.GS, (byte) 120, "gsKits.txt", false, false);
        parse(MidiStandard.GM2, (byte) 121, "gm2.txt", true, false);
        parse(MidiStandard.GM2, (byte) 120, "gm2-120.txt", false, false);
        parse(MidiStandard.XG, (byte) 127, "xg127.txt", false, false);
        parse(MidiStandard.XG, (byte) 126, "xg126.txt", false, false);
        parse(MidiStandard.XG, (byte) 64, "xg64.txt", false, false);

        /*
         * GM voices: 129, GS voices: 1170, XG voices: 1011, GM2 voices: 136, Total : 2446
         */

        /*
		System.out.println("GM  voices: 129"); System.out.println("GS  voices: "+(maps.get(MidiStandard.GS).size()-129));
		System.out.println("XG  voices: "+(maps.get(MidiStandard.XG).size()-129));
		System.out.println("GM2 voices: "+(maps.get(MidiStandard.GM2).size()-129));
		System.out.println("Total     : "+(maps.get(MidiStandard.GS).size()-129+maps.get(MidiStandard.XG).size()-129+maps.get(MidiStandard.GM2).size()-129+129));
        */
    }

    public static ExtensionMidiInstrument getInstance() {
		return instance;
	}

	/**
	 * Resolve a bank select MSB/LSB and program change into the name of the voice they select.
	 * <p>
	 * Both {@code MSB} and {@code LSB} may be rewritten internally before the lookup, per the
	 * quirks of each standard: GS forces its own rhythm-kit key onto rhythm parts and collapses
	 * LSB (which selects the SC-55/SC-88/etc. sound family) to 0, since only one family table is
	 * bundled; XG ignores LSB when MSB is 127.
	 *
	 * @param extension     the MIDI standard governing the lookup. Must not be
	 *                      {@link MidiStandard#PREVIEW} or {@link MidiStandard#ABC}.
	 *                      {@link MidiStandard#GM} always resolves through the plain GM patch map.
	 * @param MSB           bank select MSB (CC#0) in effect, 0-127
	 * @param LSB           bank select LSB (CC#32) in effect, 0-127
	 * @param patch         program change value in effect, 0-127
	 * @param drumKit       {@code true} if the caller expects a drum kit name. This is a statement
	 *                      about the caller's classification of the track, not about the channel:
	 *                      it suppresses the plain-GM shortcut and selects
	 *                      {@link MidiInstrument#STANDARD_DRUM_KIT} rather than a melodic patch name
	 *                      as the fallback when no table entry matches.
	 * @param rhythmChannel {@code true} if the synth is treating this channel as a rhythm part at
	 *                      this point in the song, as recorded from GS {@code Use For Rhythm Part}
	 *                      SysEx or XG part-mode messages. Consulted only for GS, where it forces
	 *                      the rhythm-kit MSB bank and suppresses the Santur special case. Independent
	 *                      of {@code drumKit}: XG can place a kit on a non-rhythm channel, and a GS
	 *                      rhythm channel can carry a track the caller did not classify as drums.
	 * @return the voice name, never {@code null}. Falls back to the GM patch name, or to
	 *         {@link MidiInstrument#STANDARD_DRUM_KIT} when {@code drumKit} is set, if the
	 *         standard's table has no entry for the given bank and patch.
	 */
	public String fromId(MidiStandard extension, byte MSB, byte LSB, byte patch, boolean drumKit,
			boolean rhythmChannel) {
		/*
		 * 
		 * Abbreviations that are not expanded:
		 * 	KSP	Keyboard Stereo Panning (in GS/GM2 terms this is called 'Wide')
		 * 		It means left and right side of keyboard/piano is panned heavily.
		 *
		 */
		
		assert extension != MidiStandard.PREVIEW && extension != MidiStandard.ABC : extension+" should not be used here";

		// GS does not have Dulcimer on patch 15 MSB 0 like GM, but a Santur, so we are
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
		Map<String, String> map = maps.get(extension);
		if (map == null) {
			return null;
		}
		return map.get(String.format("%03d%03d%03d", MSB, LSB, patch));
	}

	private void parse(MidiStandard extension, byte theByte, @NonNls String fileName, boolean firstColumnPatch,
					   boolean theByteIsLSB) {
		try (InputStream in = getClass().getResourceAsStream(fileName)) {
			if (in == null) {
				log.severe(fileName + " not readable.");
				return;
			}
			try (InputStreamReader sReader = new InputStreamReader(in, StandardCharsets.UTF_8);
				 BufferedReader theFileReader = new BufferedReader(sReader)) {
				String line = theFileReader.readLine();
				int lastPatch = -1;
				int lookupByte = -1;
				String regex = "\t+";// one or more tabs

				readLines(extension, theByte, fileName, firstColumnPatch, theByteIsLSB, theFileReader, line, lastPatch,
				          lookupByte, regex);
			}
		} catch (IOException e) {
			log.log(Level.SEVERE, fileName + " line failed to read.", e);
		} catch (RuntimeException e) {
			log.log(Level.SEVERE, fileName + " failed to parse.", e);
		}
	}

	private void readLines(MidiStandard extension, byte theByte, String fileName, boolean firstColumnPatch,
			boolean theByteIsLSB, BufferedReader theFileReader, String line, int lastPatch, int lookupByte,
			String regex) throws IOException {
		while (line != null) {
			if (line.isBlank()) {
				line = theFileReader.readLine();
				continue;
			}
			if (line.startsWith("\t")) {
				if (lastPatch != -1) {
					String[] splits = line.split(regex);
					if (splits.length != 3) {
						// Something is wrong in the tab formatting of one of the files

						int l = 0;
						StringBuilder str = new StringBuilder();
						for (String a : splits) {
							str.append(l).append(": ").append(a).append("\n");
							l++;
						}
						log.severe("Wrong number of tabs in " + fileName + ":\n" + str);
						line = theFileReader.readLine();
						continue;
					}
					String lookupString = splits[1].trim();
					lookupByte = parseInt(lookupString.trim());
					addInstruments(extension, theByte, firstColumnPatch, theByteIsLSB, lastPatch, lookupByte, splits);
				} else {
					log.severe("Likely wrong number of tabs in " + fileName + ": " + line);
				}
			} else {
				String patchString = line.trim();
				lastPatch = Integer.parseInt(patchString);
			}
			line = theFileReader.readLine();
		}
	}

	private void addInstruments(MidiStandard extension, byte theByte, boolean firstColumnPatch,
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

	private void addInstrument(MidiStandard extension, byte MSB, byte LSB, byte patch, String name) {
		// log.fine(extension+" addInstrument "+name+" ("+MSB+", "+LSB+", "+patch+")");
		Map<String, String> map = maps.get(extension);
		if (map == null) {
			return;
		}
		String key = String.format("%03d%03d%03d", MSB, LSB, patch);
		String previous = map.put(key, name);
		if (previous != null) {
			log.warning("Duplicate entry for (" + MSB + ", " + LSB + ", " + patch + ") in " + extension + " map");
		}
	}
}