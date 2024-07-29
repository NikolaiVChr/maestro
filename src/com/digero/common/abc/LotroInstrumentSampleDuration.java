package com.digero.common.abc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class LotroInstrumentSampleDuration {
	
	private static LotroInstrumentSampleDuration instance = null;
	private static Map<String, Map<Integer, Double>> db = null;
	
	/**
	 * Get duration of particular lotro instrument sample.
	 * 
	 * @param friendlyName Name of instrument
	 * @param note Note id
	 * @return duration in seconds
	 * @throws IOException 
	 */
	public static Double getDura(String friendlyName, int note) throws IOException {
		if (db == null) {
			parse();
		}
		Double dura = db.get(friendlyName).get(note);
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
			System.err.println(fileName + " not readable.");
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
				System.err.println("\nWrong number of entries in " + fileName + ":");
				System.exit(1);
			}
			
			String instr = splits[0].trim();
			int note = Integer.parseInt(splits[1].trim());
			double dura = Double.parseDouble(splits[2].trim());
			Map<Integer, Double> instrMap = db.get(instr);
			if (instrMap == null) {
				instrMap = new HashMap<>();
				db.put(instr, instrMap);
			}
			instrMap.put(note, dura);
			if (instr.equals(LotroInstrument.BASIC_FIDDLE.friendlyName) && note > 42) {
				// Student fiddle need the basic fiddle notes also above 42.
				Map<Integer, Double> instrMap2 = db.get(LotroInstrument.STUDENT_FIDDLE.friendlyName);
				if (instrMap2 == null) {
					instrMap2 = new HashMap<>();
					db.put(LotroInstrument.STUDENT_FIDDLE.friendlyName, instrMap2);
				}
				instrMap2.put(note, dura);
			}
			line = theFileReader.readLine();
		}
	}
}
