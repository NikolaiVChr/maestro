package com.digero.common.util;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Logging {
	
	static {
        try {
        	/*
            // replace default System.out with one that writes UTF-8
            System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8)); 
            System.out.println("Console UTF-8 test: ✓ Привет мир – こんにちは世界");
            */
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
	public static void configure(String app) throws IOException {
		LogManager.getLogManager().reset();
		
		Logger root = Logger.getLogger("");
		root.setLevel(Level.CONFIG);// root.setLevel(Level.ALL);// to be able to set level on individual children
		
		ConsoleHandler console = new ConsoleHandler();
		console.setLevel(Level.WARNING);
		console.setFormatter(new SimpleFormatter());
		root.addHandler(console);
		
		File home = SoundFontDownloader.getCommonDataDirectory();
		String logDirName = "logs";
		if (home != null) {
			File logDir = new File(home, logDirName);
			logDir.mkdirs();
			if (logDir.isDirectory()) {
				/*
					When it starts, old files (where app is no longer running), will have the last number incremented,
					so Maestro-0-0.log becomes Maestro-0-1.log etc.
					It will then start logging to: Maestro-0-0.log
					When the file gets larger than 1 MB, it will copy it to: Maestro-0-1.log (and copy increment),
					and continue writing on Maestro-0-0.log
					etc.. to .4

					If two Maestro are running at the same time, the second will be called:
					Maestro-1-0.log
					etc etc.
				 */
				String pattern = new File(logDir, app + "-%u-%g.log").toString();
				// rotate at 1 MB, keep 5 files
				FileHandler fileHandler = new FileHandler(pattern, 1024 * 1024, 5, false);
				try {
					// default encoding to console, UTF-8 to files
					fileHandler.setEncoding(StandardCharsets.UTF_8.name());
				} catch (UnsupportedEncodingException ignored) {
					System.out.println("Encoding UTF-8 not supported for writing file logs");
				}
				fileHandler.setLevel(Level.CONFIG);
				fileHandler.setFormatter(new SimpleFormatter());// XMLFormatter
				root.addHandler(fileHandler);
				//important that we dont write windows username to log in case user is asked to send to us:
				String safePath = logDir.getAbsolutePath().replace(System.getProperty("user.name"), "[user]");
				root.config("Logging to file: " + safePath);
			} else {
				root.warning("Logging to file disabled as folder don't exist: " + (new File(home, logDirName).toString()));
			}
		} else {
			root.warning("Logging to file disabled as common Maestro folder don't exist.");
		}

		/*
		// Since root is CONFIG, these will inherit CONFIG unless set.
        // Since FileHandler is also CONFIG, can now not go lower than CONFIG unless changing filehandler or console.

		Logger.getLogger("import").setLevel(Level.WARNING);
		Logger.getLogger("import.abc").setLevel(Level.WARNING);
		Logger.getLogger("import.midi").setLevel(Level.WARNING);
		Logger.getLogger("import.midi.text").setLevel(Level.WARNING);
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
		*/

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
