package com.digero.common.midi;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.digero.common.util.Pair;

/**
 * Threadsafe
 */
public class MidiDrumExtended {
	private static final Logger log = Logger.getLogger("import.midi");
	
	private static MidiDrumExtended instance = new MidiDrumExtended();
	private final HashMap<String, String> map = new HashMap<>();

	private MidiDrumExtended() {

        parse("kitSounds.txt");
		
		/*
		for (int i =27;i< 87;i++) {
			System.out.println(i+" "+MidiDrum.fromId(i).toString());
		}
		*/
    }

    public static MidiDrumExtended getInstance() {
		return instance;
	}

	public String fromId(int drumId, String kit, MidiStandard standard) {
		String key = String.format("%s:%s%03d", standard, kit, drumId);
		String hit = map.get(key);
		if (hit == null) {
			key = String.format("%s:%s%03d", standard, MidiInstrument.STANDARD_DRUM_KIT, drumId);
			hit = map.get(key);
			if (hit != null) {
				return hit;
			}
		} else {
			return hit;
		}
		return MidiDrum.fromId(drumId).toString();
	}
	
	private void parse(String fileName) {
		try {
			InputStream in = instance.getClass().getResourceAsStream(fileName);
			if (in == null) {
				System.err.println(fileName + " not readable.");
				return;
			}
			BufferedReader theFileReader = new BufferedReader(new InputStreamReader(in));
			String line = theFileReader.readLine();
			readLines(fileName, theFileReader, line);
			theFileReader.close();
		} catch (FileNotFoundException e) {
			log.log(Level.SEVERE, fileName + " not readable.", e);
		} catch (IOException e) {
			log.log(Level.SEVERE, fileName + " line failed to read.", e);
		}
	}

	private void readLines(String fileName, BufferedReader theFileReader, String line) throws IOException {
		MidiStandard std = null;
		String patch = null;
		while (line != null) {
			if (line.isEmpty() || line.startsWith("#")) {
				line = theFileReader.readLine();
				continue;
			}
			if (line.startsWith("=")) {
				String[] splits = line.split("=");
				if (splits.length != 3) {
					// Something is wrong in the tab formatting of one of the files
					log.severe("Wrong number of = in " + fileName + ":");
					int l = 0;
					for (String a : splits) {
						System.err.println(l + ": " + a);
						l++;
					}
					break;
				}
				if (splits[1].equals(MidiStandard.GS.toString())) std = MidiStandard.GS;
				else if (splits[1].equals(MidiStandard.XG.toString())) std = MidiStandard.XG;
				else if (splits[1].equals(MidiStandard.GM2.toString())) std = MidiStandard.GM2;
				else break;
				patch = splits[2];		
			} else if (std != null && patch != null) {
				Pair<Integer, String> hit = getHit(line.trim());
				if (hit != null) {
					addHit(std, patch, hit.first, hit.second);
				}
			}
			line = theFileReader.readLine();
		}
	}	

	private Pair<Integer, String> getHit(String input) {
        Pattern pattern = Pattern.compile("^(\\d+)\\s+(.+)$");
        Matcher matcher = pattern.matcher(input);

        if (matcher.matches()) {
        	try {
	            Integer number = Integer.parseInt(matcher.group(1));
	            String text = matcher.group(2);
	            return new Pair<>(number, text);
        	} catch (NumberFormatException e) {
        		return null;
        	}            
        } else {
        	return null;
        }
    }

	private void addHit(MidiStandard std, String patch, int first, String second) {
		String key = String.format("%s:%s%03d", std, patch, first);
		map.put(key, second);
	}	
}
