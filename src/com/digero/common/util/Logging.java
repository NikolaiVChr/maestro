package com.digero.common.util;

import java.io.File;
import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.XMLFormatter;

public class Logging {
	public static void configure(String app) throws IOException {
		LogManager.getLogManager().reset();
		
		Logger root = Logger.getLogger("");
		root.setLevel(Level.CONFIG);
		
		ConsoleHandler console = new ConsoleHandler();
		console.setLevel(Level.WARNING);
		console.setFormatter(new SimpleFormatter());
		root.addHandler(console);
		
		File home = Util.getDocumentsDir();
		String logFolder = "Maestro-logs";
		if (home != null && new File(home, logFolder).exists() && new File(home, logFolder).isDirectory()) {
			String pattern = new File(home, logFolder+"/"+app+"-%u-%g.log").toString();
			// rotate at 1 MB, keep 5 files
			FileHandler fileHandler = new FileHandler(pattern, 1024*1024, 5, false);
			fileHandler.setLevel(Level.CONFIG);
			fileHandler.setFormatter(new SimpleFormatter());// XMLFormatter
			root.addHandler(fileHandler);
			//important that we dont write windows username to log:
			root.config("Starting logging to files. "+"Documents/"+logFolder+"/"+app+"-%u-%g.log");
		} else if (home != null) {
			root.severe("Logging to file disabled as folder dont exist: "+(new File(home, logFolder).toString()));
		}
		
		Logger.getLogger("import").setLevel(Level.INFO);
		//Logger.getLogger("import.midi").setLevel(Level.INFO);
		//Logger.getLogger("import.abc").setLevel(Level.INFO);
		Logger.getLogger("export.preview").setLevel(Level.WARNING);
		Logger.getLogger("export.abc").setLevel(Level.WARNING);
		Logger.getLogger("export.timing").setLevel(Level.OFF);
		Logger.getLogger("export.audio").setLevel(Level.INFO);
		Logger.getLogger("export.notes").setLevel(Level.SEVERE);
		Logger.getLogger("export.midi").setLevel(Level.WARNING);
		Logger.getLogger("view").setLevel(Level.INFO);
		Logger.getLogger("playback").setLevel(Level.WARNING);
		Logger.getLogger("util").setLevel(Level.WARNING);
		Logger.getLogger("file").setLevel(Level.WARNING);
		
		// disable java internal loggings
		Logger.getLogger("sun").setLevel(Level.OFF);
		Logger.getLogger("jdk").setLevel(Level.OFF);
		Logger.getLogger("java").setLevel(Level.OFF);
		
		root.config("Logging initialized");
	}
	/*
	 * OFF
	 * SEVERE
	 * WARNING
	 * INFO
	 * CONFIG
	 * FINE
	 * FINER
	 * FINEST
	 * ALL
	 */
}
