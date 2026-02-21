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
	private static volatile Map<String, Map<Integer, Long>> db = null;

    public static long getSafeDuration(LotroInstrument instrument) {
        return switch (instrument) {
            // These have a safety buffer of around 0.5 seconds
            // to force them to be split before they fade out too much:
            case LONELY_MOUNTAIN_FIDDLE -> 7_600_000L;
            case BASIC_FIDDLE -> 6_500_000L;
            case STUDENT_FIDDLE -> 6_500_000L;
            case BASIC_BAGPIPE -> 6_000_000L;
            case BASIC_HORN -> 5_750_000L;
            case BARDIC_FIDDLE -> 5_500_000L;
            case BASIC_PIBGORN -> 5_500_000L;
            case BASIC_BASSOON -> 5_000_000L;
            case BASIC_CLARINET -> 5_000_000L;

            // These two are right on the edge of their minimum sample lengths:
            case LONELY_MOUNTAIN_BASSOON -> 5_000_000L;
            case BASIC_FLUTE -> 5_000_000L;
            default -> 7_500_000L;// for rests
        };
    }

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
			synchronized (LotroInstrumentSampleDuration.class) {
				if (db == null) {
					parse();
				}
			}
		}
        Map<Integer, Long> instr = db.get(friendlyName);
        if (instr == null) {
            return null;
        }
		if (friendlyName.equals(LotroInstrument.BASIC_COWBELL.friendlyName) || friendlyName.equals(LotroInstrument.MOOR_COWBELL.friendlyName)) {
			note = AbcConstants.COWBELL_NOTE_ID;// 71
		}
        return instr.get(note);
	}
	
	public static LotroInstrumentSampleDuration getInstance() throws IOException {
		if (instance == null) {
			synchronized (LotroInstrumentSampleDuration.class) {
				if (instance == null) {
					instance = new LotroInstrumentSampleDuration();
					if (db == null) parse();
				}
			}
		}
		return instance;
	}
	
	private static void parse() throws IOException {
		String fileName = "noteDurations.txt";
		Map<String, Map<Integer, Long>> tempDb = new HashMap<>();
		InputStream in = LotroInstrumentSampleDuration.class.getResourceAsStream(fileName);
		if (in == null) {
			log.severe(fileName + " not readable.");
			db = tempDb;// this makes us get the error logged only once
			return;
		}
		BufferedReader theFileReader = new BufferedReader(new InputStreamReader(in));			
		readLines(fileName, theFileReader, tempDb);
		theFileReader.close();
		db = tempDb;
	}

	private static void readLines(String fileName, BufferedReader theFileReader, Map<String, Map<Integer, Long>> tempDb) throws IOException {
		String line = theFileReader.readLine();
		while (line != null) {
			if (line.isBlank() || line.startsWith("#")) {
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
            Map<Integer, Long> instrMap = tempDb.computeIfAbsent(instr, k -> new HashMap<>());
            instrMap.put(note, dura);
			if (instr.equals(LotroInstrument.BASIC_FIDDLE.friendlyName) && note >= LotroInstrument.STUDENT_CHROMATIC_LOWEST.id) {
				// Student fiddle needs the basic fiddle notes also above 42.
                Map<Integer, Long> instrMap2 = tempDb.computeIfAbsent(LotroInstrument.STUDENT_FIDDLE.friendlyName, k -> new HashMap<>());
                instrMap2.put(note, dura);
			}
			line = theFileReader.readLine();
		}
	}
}
