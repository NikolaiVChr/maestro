package com.digero.common.util;

import static java.awt.Frame.MAXIMIZED_BOTH;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.digero.common.abc.AbcConstants;
import com.digero.maestro.MaestroMain;
import com.digero.maestro.view.ProjectFrame;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Range;
import sun.awt.shell.ShellFolder;

public final class Util {
	public static final @NonNls String MSX_FILE_EXTENSION_NO_DOT = "msx";
	public static final @NonNls String ABC_FILE_EXTENSION_NO_DOT = "abc";
	public static final @NonNls String ABCP_FILE_EXTENSION_NO_DOT = "abcp";
	public static final @NonNls String TXT_FILE_EXTENSION_NO_DOT = "txt";
	public static final @NonNls String MID_FILE_EXTENSION_NO_DOT = "mid";
	public static final @NonNls String KAR_FILE_EXTENSION_NO_DOT = "kar";
	public static final @NonNls String MIDI_FILE_EXTENSION_NO_DOT = "midi";
	public static final @NonNls String THEME_FILE_EXTENSION_NO_DOT = "maestrotheme.xml";
	public static final @NonNls String OPTIONS_BACKUP_FILE_EXTENSION_NO_DOT = "msbk";
	public static final @NonNls String PARTS_CONFIG_FILE_EXTENSION_NO_DOT = "partsconfig.txt";
	public static final String MSX_FILE_EXTENSION = "." + MSX_FILE_EXTENSION_NO_DOT;
	public static final String ABC_FILE_EXTENSION = "." + ABC_FILE_EXTENSION_NO_DOT;
	public static final String ABCP_FILE_EXTENSION = "." + ABCP_FILE_EXTENSION_NO_DOT;
	public static final String TXT_FILE_EXTENSION = "." + TXT_FILE_EXTENSION_NO_DOT;
	public static final String MID_FILE_EXTENSION = "." + MID_FILE_EXTENSION_NO_DOT;
	public static final String MIDI_FILE_EXTENSION = "." + MIDI_FILE_EXTENSION_NO_DOT;
	public static final String KAR_FILE_EXTENSION = "." + KAR_FILE_EXTENSION_NO_DOT;
	public static final String THEME_FILE_EXTENSION = "." + THEME_FILE_EXTENSION_NO_DOT;
	public static final String OPTIONS_BACKUP_FILE_EXTENSION = "." + OPTIONS_BACKUP_FILE_EXTENSION_NO_DOT;
	public static final String PARTS_CONFIG_FILE_EXTENSION = "." + PARTS_CONFIG_FILE_EXTENSION_NO_DOT;
	

	private Util() {
		// Can't instantiate class
	}

	public static Color grayscale(Color orig) {
		float[] hsb = Color.RGBtoHSB(orig.getRed(), orig.getGreen(), orig.getBlue(), null);
		return Color.getHSBColor(0.0f, 0.0f, hsb[2]);
	}

	public static final String ELLIPSIS = "...";

	/**
	 * Truncate string and append an ellipsis "..." if exceed a certain pixel width
	 *
     */
	@SuppressWarnings("deprecation") //
	public static String ellipsis(String text, float maxWidth, Font font) {
		FontMetrics metrics = Toolkit.getDefaultToolkit().getFontMetrics(font);

		float width = metrics.stringWidth(text);
		if (width < maxWidth)
			return text;

		final boolean trimToWordBoundary = false;
		Pattern prevWord = Pattern.compile("\\w*\\W*$");
		Matcher matcher = prevWord.matcher(text);

		int len = 0;
		int seg = text.length();
		String fit = "";

		// find the longest string that fits into
		// the control boundaries using bisection method
		while (seg > 1) {
			seg -= seg / 2;

			int left = len + seg;
			int right = text.length();

			if (left > right)
				continue;

			if (trimToWordBoundary) {
				// trim at a word boundary using regular expressions
				matcher.region(0, left);
				if (matcher.find())
					left = matcher.start();
			}

			// build and measure a candidate string with ellipsis
			String tst = text.substring(0, left) + ELLIPSIS;

			width = metrics.stringWidth(tst);

			// candidate string fits into boundaries, try a longer string
			// stop when seg <= 1
			if (width <= maxWidth) {
				len += seg;
				fit = tst;
			}
		}

		// string can't fit
		if (len == 0)
			return ELLIPSIS;

		return fit;
	}
	
    /**
     *
     *
     */
	private static File getUserDocumentsPath() {
		String userHome = System.getProperty("user.home", "");
	    File docs = new File(userHome, "Documents");
	    if (docs.isDirectory()) {
	        return docs;
	    }
	    docs = new File(userHome, "My Documents");
	    if (docs.isDirectory()) {
	        return docs;
	    }
	    return new File(userHome);
	}
	
	private static File getUserOneDriveDocumentsPath() {
	    // Check if OneDrive is being used
	    String oneDrivePath = System.getenv("OneDrive");
	    if (oneDrivePath != null) {
	        File oneDriveDocs = new File(oneDrivePath, "Documents");
	        if (oneDriveDocs.isDirectory()) {
	            return oneDriveDocs;
	        }
	    }
	    return null;
	}

	@Deprecated
	public static File getUserMusicPath() {
		String userHome = System.getProperty("user.home", "");
		File music = new File(userHome + "/Music");
		if (music.isDirectory())
			return music;
		music = new File(userHome + "/My Documents/My Music");
		if (music.isDirectory())
			return music;

		return getUserDocumentsPath();
	}
	
	public static File getDocumentsDir() {
		File docs = getUserDocumentsPath();
		
		File onedriveDocs = getUserOneDriveDocumentsPath();
		if (onedriveDocs != null) {
			return onedriveDocs;
		}
		if (docs.isDirectory()) {
			return docs;
		}
		return null;
	}

	public static File getLotroMusicPath(boolean create) {
		// TODO: handle Linux if Music folder is lower-case
		final String lotroFolderName = "The Lord of the Rings Online";
		final String abcFolderName = "Music";
		File docs = getUserDocumentsPath();
		File lotro = new File(docs, lotroFolderName);
		if (lotro.isDirectory()) {
			File music = new File(lotro, abcFolderName);
			if (music.isDirectory() || create && music.mkdir())
				return music;

			return lotro;
		}
		
		File onedriveDocs = getUserOneDriveDocumentsPath();
		if (onedriveDocs != null) {
			lotro = new File(onedriveDocs, lotroFolderName);
			if (lotro.isDirectory()) {
				File music = new File(lotro, abcFolderName);
				if (music.isDirectory() || create && music.mkdir())
					return music;
	
				return lotro;
			}
		}
		
		return docs;
	}

    public static int clamp(int value, int min, int max) {
        return Math.clamp(value, min, max);
    }

    public static long clamp(long value, long min, long max) {
        return Math.clamp(value, min, max);
    }

    public static double clamp(double value, double min, double max) {
        return Math.clamp(value, min, max);
    }

    public static float clamp(float value, float min, float max) {
        return Math.clamp(value, min, max);
    }

	public static int valueOf(Integer val, int defaultIfNull) {
		return (val != null) ? val : defaultIfNull;
	}

	public static long valueOf(Long val, long defaultIfNull) {
		return (val != null) ? val : defaultIfNull;
	}

	public static float valueOf(Float val, float defaultIfNull) {
		return (val != null) ? val : defaultIfNull;
	}

	public static double valueOf(Double val, double defaultIfNull) {
		return (val != null) ? val : defaultIfNull;
	}

	/** Greatest Common Divisor */
	public static int gcd(int a, int b) {
		while (b != 0) {
			int t = b;
			b = a % b;
			a = t;
		}
		return a;
	}

	/** Greatest Common Divisor */
	public static long gcd(long a, long b) {
		while (b != 0) {
			long t = b;
			b = a % b;
			a = t;
		}
		return a;
	}

	/** Least Common Multiple */
	public static int lcm(int a, int b) {
		return (a / gcd(a, b)) * b;
	}

	/** Least Common Multiple for positive numbers */
	public static long lcm(@Range(from = 1, to = 100000000L) long a, @Range(from = 1, to = 100000000L) long b) {
		return (a / gcd(a, b)) * b;
	}

	/** Rounds value to the nearest multiple of grid */
	public static int roundGrid(int value, int grid) {
		return ((value + grid / 2) / grid) * grid;
	}

	/** Rounds value to the nearest multiple of grid */
	public static long roundGrid(long value, long grid) {
		return ((value + grid / 2) / grid) * grid;
	}
	
	public static long ceilGrid(long value, long grid) {
		long floor = floorGrid(value, grid);
		if (floor < value) return floor + grid;
		return floor;
	}

	public static int floorGrid(int value, int grid) {
		return (value / grid) * grid;
	}

	public static long floorGrid(long value, long grid) {
		return (value / grid) * grid;
	}

	public static boolean openURL(String url, ProjectFrame mainWindow) {
		try {
			URI uriDownload = new URI(url);											
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
				Desktop.getDesktop().browse(uriDownload);
				return true;
			}
		} catch (Exception e) {
			ProjectFrame.feed(e.getMessage(), MaestroMain.getFirstLines(e));
		    if (mainWindow != null) {
		    	SwingUtilities.invokeLater(() -> {
		    		try {
		    			mainWindow.showFeed();
		    		} catch (Exception e2) {
						e.printStackTrace();
					}
		    	});
		    }
			e.printStackTrace();
		}
		return false;
	}

	public static File resolveShortcut(File file) {
		if (file.getName().toLowerCase().endsWith(".lnk")) {
			try {
				return ShellFolder.getShellFolder(file).getLinkLocation();
			} catch (Exception e) {
			}
		}
		return file;
	}

	public static void initWinBounds(final JFrame frame, final Preferences prefs, int defaultW, int defaultH) {
		Dimension mainScreen = Toolkit.getDefaultToolkit().getScreenSize();

		int width = prefs.getInt("width", defaultW);
		int height = prefs.getInt("height", defaultH);
		int x = prefs.getInt("x", (mainScreen.width - width) / 2);
		int y = prefs.getInt("y", (mainScreen.height - height) / 2);

		Rectangle onScreen = calculateOnScreen(width, height, x, y);

		if (onScreen == null) {
			x = (mainScreen.width - width) / 2;
			y = (mainScreen.height - height) / 2;
		} else {
			if (x < onScreen.x)
				x = onScreen.x;
			else if (x + width > onScreen.x + onScreen.width)
				x = onScreen.x + onScreen.width - width;

			if (y < onScreen.y)
				y = onScreen.y;
			else if (y + height > onScreen.y + onScreen.height)
				y = onScreen.y + onScreen.height - height;
		}

		frame.setBounds(x, y, width, height);

		int maximized = prefs.getInt("maximized", 0) & MAXIMIZED_BOTH;
		frame.setExtendedState((frame.getExtendedState() & ~MAXIMIZED_BOTH) | maximized);

		frame.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				if ((frame.getExtendedState() & MAXIMIZED_BOTH) == 0) {
					prefs.putInt("width", frame.getWidth());
					prefs.putInt("height", frame.getHeight());
				}
			}

			@Override
			public void componentMoved(ComponentEvent e) {
				if ((frame.getExtendedState() & MAXIMIZED_BOTH) == 0) {
					prefs.putInt("x", frame.getX());
					prefs.putInt("y", frame.getY());
				}
			}
		});

		frame.addWindowStateListener(e -> prefs.putInt("maximized", e.getNewState() & MAXIMIZED_BOTH));
	}

	/**
	 * Handle the case where the window was last saved on a screen that is no longer connected
	 *
     */
	private static Rectangle calculateOnScreen(int width, int height, int x, int y) {
		Rectangle windowRect = new Rectangle(x, y, width, height);
		Rectangle onScreen = null;
		int bestAreaOnscreen = 0;
		for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
			Rectangle monitorBounds = device.getDefaultConfiguration().getBounds();
			Rectangle monitorIntersection = monitorBounds.intersection(windowRect);
			if (!monitorIntersection.isEmpty()) {
				int areaOnscreen = monitorIntersection.width * monitorIntersection.height;
				if (areaOnscreen > bestAreaOnscreen) {
					bestAreaOnscreen = areaOnscreen;
					onScreen = monitorBounds;
				}
			}
		}
		return onScreen;
	}

	public static String formatDuration(long micros) {
		return formatDuration(micros, 0);
	}
	
	public static String formatDurationM(long micros) {
		return formatDurationM(micros, 0, ':');
	}

	public static String formatDuration(long micros, long maxMicros) {
		return formatDuration(micros, maxMicros, ':');
	}

	public static String formatDuration(long micros, long maxMicros, char separator) {
        maxMicros = Math.max(micros, maxMicros);

		StringBuilder s = new StringBuilder(5);

		int t = (int) Math.ceilDiv(micros, AbcConstants.ONE_SECOND_MICROS);

		int hr = t / (60 * 60);
		t %= 60 * 60;
		int min = t / 60;
		t %= 60;
		int sec = t;

		int tMax = (int) Math.ceilDiv(maxMicros, AbcConstants.ONE_SECOND_MICROS);
		int hrMax = tMax / (60 * 60);
		tMax %= 60 * 60;
		int minMax = tMax / 60;

		if (hrMax > 0) {
			s.append(hr).append(separator);
			if (min < 10) {
				s.append('0');
			}
		} else if (minMax >= 10 && min < 10) {
			s.append('0');
		}
		s.append(min).append(separator);
		if (sec < 10) {
			s.append('0');
		}
		s.append(sec);

		return s.toString();
	}
	
	public static String formatDurationM(long micros, long maxMicros, char separator) {
        maxMicros = Math.max(micros, maxMicros);

		StringBuilder s = new StringBuilder(5);

		long milli = micros / 1000L;
		
		int hr = (int)(milli / (60000L * 60000L));
		milli %= 60000L * 60000L;
		int min = (int)(milli / 60000L);
		milli %= 60000L;
		int sec = (int)(milli / 1000L);
		int ms = (int)(milli % 1000L);
		
		int tMax = (int) (maxMicros / (1000 * 1000));
		int hrMax = tMax / (60 * 60);
		tMax %= 60 * 60;
		int minMax = tMax / 60;

		if (hrMax > 0) {
			s.append(hr).append(separator);
			if (min < 10) {
				s.append('0');
			}
		} else if (minMax >= 10 && min < 10) {
			s.append('0');
		}
		s.append(min).append(separator);
		if (sec < 10) {
			s.append('0');
		}
		s.append(sec).append(separator);
		if (ms < 100) {
			s.append('0');
		}
		if (ms < 10) {
			s.append('0');
		}
		s.append(ms);

		return s.toString();
	}

	public static String emptyIfNull(String in) {
		return (in != null) ? in : "";
	}

	public static String quote(String in) {
		return "\"" + in.replace("\"", "\\\"") + "\"";
	}

	public static String htmlEscape(String in) {
		return in.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	public static boolean stringEndsWithIgnoreCase(String source, String suffix) {
		int beginIndex = source.length() - suffix.length();
		return (beginIndex >= 0) && source.substring(beginIndex).equalsIgnoreCase(suffix);
	}

	public static String fileNameWithoutExtension(File file) {
		if (file.isDirectory())
			return file.getName();

		return fileNameWithoutExtension(file.getName());
	}

	public static String fileNameWithoutExtension(String fileName) {
		int dot = fileName.lastIndexOf('.');
		if (dot > 0)
			fileName = fileName.substring(0, dot);
		return fileName;
	}
	
	public static float map(float value, float fromLow, float fromHigh, float toLow, float toHigh) {
        if (fromLow == fromHigh) {
            // TODO: consider throwing an exception instead.
            return toLow;
        }
		return toLow + (value - fromLow) * (toHigh - toLow) / (fromHigh - fromLow);
	}
	
	public static float map(long value, long leftMin, long leftMax, int rightMin, int rightMax) {

        if (leftMin == leftMax) {
            // TODO: consider throwing an exception instead.
            return (float) rightMin;
        }

		// Figure out how 'wide' each range is
		long leftSpan = leftMax - leftMin;
		int rightSpan = rightMax - rightMin;

		// Convert the left range into a 0-1 range (float)
		double valueScaled = (value - leftMin) / (double) leftSpan;

		// Convert the 0-1 range into a value in the right range.
		return (float)(rightMin + (valueScaled * rightSpan));
	}

    /**
     * Expensive
     */
    public static int mapBig(long value, long leftMin, long leftMax, int rightMin, int rightMax) {
        // since left range can span a rather big number, we use big decimal to make sure its division is done proper
        // double could do a fine job, but due to backwards compatibility won't change it.

        if (leftMin == leftMax) {
            // TODO: consider throwing an exception instead.
            return rightMin;
        }

        // Figure out how 'wide' each range is
        BigDecimal leftSpan = BigDecimal.valueOf(leftMax-leftMin);
        BigDecimal rightSpan = BigDecimal.valueOf(rightMax-rightMin);

        // Convert the left range into a 0-1 range (float)
        // The result will have 10 decimal places of precision
        BigDecimal valueScaled = BigDecimal.valueOf(value-leftMin).divide(leftSpan, 10, RoundingMode.HALF_UP);

        // Convert the 0-1 range into a value in the right range.
        return rightMin + valueScaled.multiply(rightSpan).intValue();
    }
	
	public boolean stringEquals(String str1, String str2) {
		if (str1 == null && str2 == null) return true;
		if (str1 == null || str2 == null) return false;
		return str1.equals(str2);
	}
}
