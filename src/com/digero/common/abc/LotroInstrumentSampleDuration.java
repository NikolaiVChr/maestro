package com.digero.common.abc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class LotroInstrumentSampleDuration {
	private static final Logger log = Logger.getLogger("file");
	private static LotroInstrumentSampleDuration instance = null;
	private static Map<String, Map<Integer, Long>> db = null;
	
	/**
	 * Get duration of particular lotro instrument sample.
	 * 
	 * @param friendlyName Name of instrument
	 * @param note Note id
	 * @return duration in seconds
	 * @throws IOException 
	 */
	public static Long getDura(String friendlyName, int note) throws IOException {
		if (db == null) {
			parse();
		}
		Long dura = db.get(friendlyName).get(note);
		return dura;
	}
	
	public static LotroInstrumentSampleDuration getInstance() throws IOException {
		if (instance != null) {
			return instance;
		}

		instance = new LotroInstrumentSampleDuration();

		parse();

		return instance;
	}
	
	private static void parse() throws IOException {
		String fileName = "noteDurations.txt";
		db = new HashMap<>(); 
		InputStream in = getInstance().getClass().getResourceAsStream(fileName);
		if (in == null) {
			log.severe(fileName + " not readable.");
			return;
		}
		BufferedReader theFileReader = new BufferedReader(new InputStreamReader(in));			
		readLines(fileName, theFileReader);
		theFileReader.close();
	}

	private static void readLines(String fileName, BufferedReader theFileReader) throws IOException {
		String line = theFileReader.readLine();
		while (line != null) {
			if (line.isEmpty()) {
				line = theFileReader.readLine();
				continue;
			}
			String[] splits = line.split(",");
			if (splits.length != 3) {
				// Something is wrong in the tab formatting of one of the files
				log.severe("Wrong number of entries in " + fileName + ": "+splits.length);
				throw new IOException("Wrong number of entries in " + fileName + ": " + splits.length);
			}
			
			String instr = splits[0].trim();
			int note = Integer.parseInt(splits[1].trim());
			long dura = Long.parseLong(splits[2].trim());
            Map<Integer, Long> instrMap = db.computeIfAbsent(instr, k -> new HashMap<>());
            instrMap.put(note, dura);
			if (instr.equals(LotroInstrument.BASIC_FIDDLE.friendlyName) && note > 42) {
				// Student fiddle need the basic fiddle notes also above 42.
                Map<Integer, Long> instrMap2 = db.computeIfAbsent(LotroInstrument.STUDENT_FIDDLE.friendlyName, k -> new HashMap<>());
                instrMap2.put(note, dura);
			}
			line = theFileReader.readLine();
		}
	}
}
