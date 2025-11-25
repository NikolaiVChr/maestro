package com.digero.maestro;

import static java.awt.Frame.ICONIFIED;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.SwingUtilities;

import com.digero.common.util.AppInfo;
import com.digero.common.util.Logging;
import com.digero.common.util.Themer;
import com.digero.common.util.Util;
import com.digero.common.util.Version;
import com.digero.maestro.view.MiscSettings;
import com.digero.maestro.view.ProjectFrame;

//import org.boris.winrun4j.DDE;

public class MaestroMain {
	private static Logger log;
	public static final String APP_NAME = "Maestro";
	public static final String WIKI_URL = "https://maestro.miraheze.org/wiki/Main_Page";
	public static final String DOWNLOAD_URL = "https://drive.google.com/drive/folders/1CigT_AloFP34lZbIEvb4CsqGmBL8vodu";
	public static Version APP_VERSION = new Version(0, 0, 0);

	private static ProjectFrame mainWindow = null;

	private static ServerSocket serverSocket;

	public MaestroMain() {
		// ABC Tool calls this to initialize the version.
		// note that 'log' is not initialized in this method
		try {
			Properties props = new Properties();
			props.load(MaestroMain.class.getResourceAsStream("version.properties"));
			String versionString = props.getProperty("version.Maestro");
			if (versionString != null)
				APP_VERSION = Version.parseVersion(versionString);
		} catch (IOException ignored) {
		}
	}

	public static void main(final String[] args) throws Exception {
        AppInfo.maestro = true;
        AppInfo.APP_NAME = APP_NAME;
		Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
			log.log(Level.SEVERE, throwable.toString(), throwable);
		    ProjectFrame.feed("ERROR: exception in thread " + thread.getName() + ": " + throwable+". Please notify the devs.", getFirstLines(throwable));
		    if (mainWindow != null) {		    	
		    	SwingUtilities.invokeLater(() -> {
		    		try {
		    			mainWindow.showFeed();
		    		} catch (Exception e) {
						e.printStackTrace();
					}
		    	});
		    }
		});

		try {
			Properties props = new Properties();
			props.load(MaestroMain.class.getResourceAsStream("version.properties"));
			String versionString = props.getProperty("version.Maestro");
			if (versionString != null)
				APP_VERSION = Version.parseVersion(versionString);
		} catch (IOException ignore) {
		}
		
		if (args != null) {
			for (String arg : args) {
				if (arg.equals("-jre-version")) {
					outputJRE();
				} else if (arg.equals("-app-version")) {
					System.out.println("App name: "+APP_NAME);
					System.out.println("App version: "+APP_VERSION);
					System.out.println("App authors: Digero, Aifel, Elamond and Karloman");
				}
			}
		}
		
		if (!openPort() && args != null && args.length > 0 && args[0].length() > 3) {
			sendArgsToPort(args);
			return;
		}

        Logging.configure(APP_NAME);//should be after "return" so that each instance that just sends a file to running maestro, dont create new log file.
        log = Logger.getLogger("");//must be after configure

		//System.setProperty("sun.sound.useNewAudioEngine", "true");
		
		SwingUtilities.invokeAndWait(() -> {
			try {
				MiscSettings misc = new MiscSettings(Preferences.userNodeForPackage(MaestroMain.class).node("miscSettings"), true);
				Themer.setLookAndFeel(misc.theme, misc.fontSize);
			} catch (Exception e) {
				// Reset theme to default if an error occurred setting look and feel
				Preferences preferences = Preferences.userNodeForPackage(MaestroMain.class);
				preferences.node("saveAndExportSettings").put("theme", "Default");
			}
		});
		
		mainWindow = new ProjectFrame();

		SwingUtilities.invokeAndWait(() -> {
			mainWindow.setVisible(true);
			mainWindow.getRootPane().requestFocus();
			openSongFromCommandLine(args);
		});
	}

	public static String getFirstLines(Throwable throwable) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		throwable.printStackTrace(pw);
		
		String[] lines = sw.toString().split("\n");
		StringBuilder sb = new StringBuilder();
		
		int limit = Math.min(lines.length, 20);
		for (int i = 0; i < limit; i++) {
			sb.append(lines[i]).append("\n");
		}
		return sb.toString();
	}

	public static void outputJRE() {
		System.err.println("Java Runtime Name: " + System.getProperty("java.runtime.name"));
		System.err.println("Java Runtime Version: " + System.getProperty("java.runtime.version"));
		System.err.println("Java Vendor: " + System.getProperty("java.vendor"));
	}

	public static void setMIDIFileResolved() {
		if (mainWindow == null)
			return;
		mainWindow.setMIDIFileResolved();
	}

	/** A new activation from WinRun4J 32bit (a.k.a. a file was opened) */
	public static void activate(final String[] args) {
		SwingUtilities.invokeLater(() -> openSongFromCommandLine(args));
	}

	/** A new activation from WinRun4J 64bit (a.k.a. a file was opened) */
	public static void activate(String arg0) {
		final String[] args = { arg0.substring(1, arg0.length() - 1) };
		SwingUtilities.invokeLater(() -> MaestroMain.openSongFromCommandLine(args));
	}

	public static void execute(String cmdLine) {
		openSongFromCommandLine(new String[] { cmdLine });
	}

	public static void openSongFromCommandLine(String[] args) {
		if (mainWindow == null) {
			return;
		}

		int state = mainWindow.getExtendedState();
		if ((state & ICONIFIED) != 0)
			mainWindow.setExtendedState(state & ~ICONIFIED);

		if (args.length > 0) {
			File file = new File(args[0]);
			if (file.exists())
				mainWindow.openFile(file);
		}
	}

    @Deprecated
	public static void onVolumeChanged() {
		if (mainWindow != null)
			mainWindow.onVolumeChanged();
	}

	private static boolean openPort() {

		try {
			serverSocket = new ServerSocket(8000 + APP_VERSION.getBuild());
			if (serverSocket.getLocalPort() != 8000 + APP_VERSION.getBuild()) {
				//log.fine("Port is "+serverSocket.getLocalPort());
				return false;
			}
		} catch (IOException e) {
			// e.printStackTrace();
			return false;
		}
		//log.finer("Made port");
		Thread waitForOtherMaestrosThread = new Thread(() -> {
            while (!serverSocket.isClosed()) {
				try (Socket socket = serverSocket.accept()) {
                    //log.finer("Accepted");

                    // If readLine() takes longer than this, it throws SocketTimeoutException
                    socket.setSoTimeout(2000);

                    try (BufferedReader in = new BufferedReader(
                            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_16))) {

                        String data = in.readLine();

                        if (data != null
                                && (Util.stringEndsWithIgnoreCase(data, Util.MID_FILE_EXTENSION)
                                        || Util.stringEndsWithIgnoreCase(data, Util.MIDI_FILE_EXTENSION)
                                        || Util.stringEndsWithIgnoreCase(data, Util.ABC_FILE_EXTENSION)
                                        || Util.stringEndsWithIgnoreCase(data, Util.TXT_FILE_EXTENSION)
                                        || Util.stringEndsWithIgnoreCase(data, Util.MSX_FILE_EXTENSION)
                                        || Util.stringEndsWithIgnoreCase(data, Util.KAR_FILE_EXTENSION))) {
                            //log.finer("Received "+data);
                            String[] dataArray = { data };
                            activate(dataArray);
                        } else {
                            //log.fine("Received nothing: "+data);
                        }
					}
                } catch (IOException e) {
                    //log.log(Level.FINE, "Error while waiting for another maestro process", e);
                    if (serverSocket.isClosed()) break;
                }
			}
		});
        //for debugging:
        waitForOtherMaestrosThread.setName("listen-for-maestros");
        //so java knows this thread can safely be deleted when stopping maestro:
        waitForOtherMaestrosThread.setDaemon(true);
        waitForOtherMaestrosThread.start();

		return true;
	}

	private static void sendArgsToPort(final String[] args) {
		if (args == null || args.length == 0 || args[0].length() < 3) {
			return;
		}
		try {
			Socket clientSocket = new Socket("localhost", 8000 + APP_VERSION.getBuild());
			OutputStreamWriter os = new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_16);// NTFS
																													// uses
																													// UTF16
																													// for
																													// filenames
			// for (String arg : args) {
			os.write(args[0]);
			os.close();// Must be here to flush to stream
			// Path path = Paths.get(args[0]);
			// System.out.println("Wrote "+args[0]+" to 8001 ("+Files.exists(path)+")");
			// }
			clientSocket.close();
		} catch (IOException e) {
			// e.printStackTrace();
		}
	}
}
