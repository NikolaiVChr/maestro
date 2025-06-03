package com.digero.common.midi;

import static java.lang.Integer.parseInt;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.digero.common.util.Pair;

public class MidiDrumExtended {
	
	private static MidiDrumExtended instance = null;
	private static HashMap<String, String> map = new HashMap<>();

	public static MidiDrumExtended getInstance() {
		if (instance != null) {
			return instance;
		}

		instance = new MidiDrumExtended();
		
		parse("kitSounds.txt");
		
		for (int i =27;i< 87;i++) {
			System.out.println(i+" "+MidiDrum.fromId(i).toString());
		}
		

		return instance;
	}

	public static String fromId(int drumId, String kit, MidiStandard standard) {
		String key = String.format("%s:%s%03d", standard, kit, drumId);
		String hit = map.get(key);
		if (hit == null) {
			key = String.format("%s:%s%03d", standard, MidiInstrument.STANDARD_DRUM_KIT.toString(), drumId);
			hit = map.get(key);
			if (hit != null) {
				System.out.println(hit);
				return hit;
			} else {
				System.out.println(standard+" inner hit == null for "+MidiInstrument.STANDARD_DRUM_KIT+" id="+drumId+" key = "+key);
			}
		} else {
			System.out.println(hit);
			return hit;
		}
		return MidiDrum.fromId(drumId).toString();
	}
	
	private static void parse(String fileName) {
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
			System.err.println(fileName + " not readable.");
			e.printStackTrace();
		} catch (IOException e) {
			System.err.println(fileName + " line failed to read.");
			e.printStackTrace();
		}
	}

	private static void readLines(String fileName, BufferedReader theFileReader, String line) throws IOException {
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
					System.err.println("\nWrong number of = in " + fileName + ":");
					int l = 0;
					for (String a : splits) {
						System.err.println(l + ": " + a);
						l++;
					}
					line = theFileReader.readLine();
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

	public static Pair<Integer, String> getHit(String input) {
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

	private static void addHit(MidiStandard std, String patch, int first, String second) {
		String key = String.format("%s:%s%03d", std, patch, first);
		System.out.println("add "+key+" -> "+second);
		map.put(key, second);
	}	
}
