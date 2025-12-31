package com.aifel.abctools;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileSystemView;

import com.aifel.abctools.AbcTools.AbcFileFilter;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.midi.SynthesizerFactory;
import com.digero.common.util.Util;
import com.digero.common.view.UIText;

public class MergeTool {
	private static final Logger log = Logger.getLogger("util");
	
	private static final Pattern INFO_PATTERN = Pattern.compile("^([A-Z]):\\s*(.*)\\s*$");
	private static final int INFO_TYPE = 1;// regex group
	private static final int INFO_VALUE = 2;// regex group

	private volatile AbcToolsView frame = null;
	
	@SuppressWarnings("unused")
	private AbcTools abcTools;

	JList<File> theList = null;
	private String lastExport = null;
	
	private Preferences mergePrefs;
	
	private final String DIR_MERGE_SOURCE = "dir_source";
	private final String DIR_MERGE_DEST   = "dir_destination";
	private File sourceFolder;
	private File destFolder;
	private ActionListener actionSource = getSourceActionListener();
	private ActionListener actionDest = getDestActionListener();
	private ActionListener actionJoin = getJoinActionListener();
	private ActionListener actionTest = getTestActionListener();
	
	private Thread playerThread;
	
	MergeTool(AbcToolsView frame, String myHome, AbcTools main, Preferences mergePrefs) {
		
		this.mergePrefs = mergePrefs;
		this.abcTools = main;
		this.frame = frame;
		
		// Setup folders from stored prefs if available:
		sourceFolder = new File(mergePrefs.get(DIR_MERGE_SOURCE, myHome));
		destFolder = new File(mergePrefs.get(DIR_MERGE_DEST, myHome));
		
		SwingUtilities.invokeLater(() -> {

			// Setup action listeners
			frame.getBtnDest().addActionListener(actionDest);
			frame.getBtnSource().addActionListener(actionSource);
			frame.getBtnJoin().addActionListener(actionJoin);
			frame.getBtnTest().addActionListener(actionTest);
			frame.getScrollPane().getVerticalScrollBar().setUnitIncrement(22);
			
	
			
			/*
			 * try { List<Image> icons = new ArrayList<>(); icons.add(ImageIO.read(new
			 * FileInputStream("abcmergetool.ico"))); frame.setIconImages(icons); } catch (Exception ex) { // Ignore
			 * ex.printStackTrace(); }
			 */
			refreshMerge();
		});
	}
	
	public void flushPrefs() {
		mergePrefs.put(DIR_MERGE_SOURCE, sourceFolder.getAbsolutePath());
		mergePrefs.put(DIR_MERGE_DEST, destFolder.getAbsolutePath());
	}
	
	private void refreshMerge() {
		Component c = getGui(sourceFolder.listFiles(new AbcFileFilter()), false);
		frame.setLblSourceText(UIText.get("abctools.source.0", sourceFolder.getAbsolutePath()));
		frame.setLblDestText(UIText.get("abctools.destination.0", destFolder.getAbsolutePath()));
		frame.getScrollPane().setViewportView(c);
		frame.setBtnJoinEnabled(c != null);
		refreshTest();
		// frame.pack();
		frame.repaint();
	}

	private void refreshTest() {
		frame.setBtnTestEnabled(lastExport != null);
	}

	private void join() throws IOException {
		List<File> theFiles = theList.getSelectedValuesList();
		if (theFiles != null && theFiles.size() > 1) {
			List<List<String>> oldContent = new ArrayList<>();
			for (File theFile : theFiles) {
				List<String> lines = Files.readAllLines(Paths.get(theFile.toURI()), StandardCharsets.UTF_8);
				oldContent.add(lines);
			}

			int numberOfParts = 0;
			for (List<String> lines : oldContent) {
				for (String line : lines) {
					Matcher infoMatcher = INFO_PATTERN.matcher(line);
					if (infoMatcher.matches()) {
						char type = Character.toUpperCase(infoMatcher.group(INFO_TYPE).charAt(0));
						if (type == 'X') {
							numberOfParts++;
						}
					}
				}
			}
			String badgerParts = getAllParts(numberOfParts);

			List<String> newContent = new ArrayList<>();
			int x = 1;
			int fileNo = 0;
			String Q = "";
			boolean mismatch = false;
			boolean meta = true;
			for (List<String> lines : oldContent) {
				LotroInstrument instr = null;
				for (String line : lines) {
					Matcher infoMatcher = INFO_PATTERN.matcher(line);
					boolean isX = false;
					if (infoMatcher.matches()) {
						char type = Character.toUpperCase(infoMatcher.group(INFO_TYPE).charAt(0));
						String value = infoMatcher.group(INFO_VALUE).trim();

						if (type == 'X') {
							if (meta)
								newContent.add(badgerParts);
							meta = false;
							newContent.add("X: " + x);
							newContent.add("%%Orig filename was " + theFiles.get(fileNo).getName());
							x++;
							isX = true;
						} else if (type == 'T') {
							instr = LotroInstrument.findInstrumentName(value, null);
							if (instr == null) {
								// instr was not found in the part name, lets check the filename
								instr = LotroInstrument.findInstrumentName(theFiles.get(fileNo).getName(), null);
								if (instr != null) {
									line += "[" + instr + "]";
								} else {
									instr = LotroInstrument
											.findInstrumentNameAggressively(theFiles.get(fileNo).getName(), null);
									if (instr != null)
										line += "[[" + instr + "]]";// Double [[ means there is significant chance it
																	// got the instrument wrong
									else if (theFiles.get(fileNo).getName().toLowerCase().contains("wind")) {
										line += "[Wind]";
									}
								}
							}
						} else if (type == 'Q') {
							if (!Q.isEmpty() && !Q.equals(value)) {
								mismatch = true;
							}
							Q = value;
						}
					}
					if (!isX && (!meta || x == 1)) {
						// X is not written here, and only header meta info from first file.
						newContent.add(line);
					}
				}
				fileNo++;
			}

			if (mismatch) {
				int misresult = JOptionPane.showConfirmDialog(frame,
						UIText.get("abctools.all.these.files.do.not.seem.to.belong"), UIText.get("abctools.tempo.mismatch"),
						JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

				switch (misresult) {
				case JOptionPane.YES_OPTION:
					break;
				case JOptionPane.NO_OPTION, JOptionPane.CANCEL_OPTION, JOptionPane.CLOSED_OPTION:
					frame.setTextFieldText(UIText.get("abctools.cancelled.merge"));
					lastExport = null;
					refreshTest();
					return;
                }
			}

			String n1 = theFiles.getFirst().getName();
			int dot = n1.lastIndexOf('.');
			if (dot > 0)
				n1 = n1.substring(0, dot);

			String n2 = theFiles.getLast().getName();
			dot = n2.lastIndexOf('.');
			if (dot > 0)
				n2 = n2.substring(0, dot);

			String newName = trimNonAbc(getLongestCommonSubstring(n1, n2));
			if (newName.isEmpty())
				newName = "mySong";
			newName += Util.ABC_FILE_EXTENSION;
			File newFile = new File(destFolder, newName);
			boolean success = false;
			if (newFile.exists()) {
				int result = JOptionPane.showConfirmDialog(frame,
						UIText.get("abctools.the.file.0.exist.already.do.you.want.to.overwrite.it", newFile.getAbsolutePath()),
						UIText.get("abctools.overwrite"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

				switch (result) {
				case JOptionPane.YES_OPTION:
					success = true;
					break;
				case JOptionPane.NO_OPTION:
					frame.setTextFieldText(UIText.get("abctools.cancelled.save"));
					lastExport = null;
					refreshTest();
					return;
				case JOptionPane.CANCEL_OPTION, JOptionPane.CLOSED_OPTION:
					frame.setTextFieldText(UIText.get("abctools.cancelled.save"));
					lastExport = null;
					refreshTest();
					return;
				}
			}
			
			if (!success)
				success = newFile.createNewFile();
			
			if (success) {
				FileWriter writer = new FileWriter(newFile);
	
				frame.setTextFieldText(
						UIText.get("abctools.writing.new.file.0.the.song.has.1.parts2", newFile.getAbsolutePath(), x - 1));
				StringBuilder info = new StringBuilder(UIText.get("abctools.writing.new.file.0.the.song.has.1.parts", newFile.getAbsolutePath(), x - 1));
				for (String line : newContent) {
					writer.write(line + System.lineSeparator());
					info.append(System.lineSeparator()).append(line);
				}
				writer.close();
				lastExport = newFile.getAbsolutePath();
				refreshTest();
				frame.setTextFieldText(info.toString());
				log.info("Created merged "+lastExport);
			} else {
				frame.setTextFieldText(UIText.get("abctools.failed.to.write.merged.file.0", newFile.getAbsolutePath()));
			}
		} else {
			frame.setTextFieldText(UIText.get("abctools.please.select.at.least.2.abc.files"));
			lastExport = null;
			refreshTest();
		}
	}

	private void test() throws IOException {
		if (lastExport == null || (playerThread != null && playerThread.isAlive()))
			return;
		
		playerThread = new Thread(() -> {
			String folder = ".";
			try {
				// Find the path to the jar file we are executing in
				folder = new File(
						SynthesizerFactory.class.getProtectionDomain().getCodeSource().getLocation().toURI())
						.getParent();
			} catch (URISyntaxException e) {
				e.printStackTrace();
			}
			File jarFile = new File(folder, "AbcPlayer.jar");
	        URL jarUrl;
			try {
				jarUrl = jarFile.toURI().toURL();
				ClassLoader parent = Thread.currentThread().getContextClassLoader();
				try (URLClassLoader cl = new URLClassLoader(new URL[]{jarUrl}, parent)) {
		            String mainClassName = "com.digero.abcplayer.AbcPlayer";
		            Class<?> mainClass = Class.forName(mainClassName, true, cl);
		
		            Method m = mainClass.getMethod("main", String[].class);
		            String[] childArgs = new String[]{lastExport.replace('\\', '/'), "--tools"};
		            m.invoke(null, (Object) childArgs);
		            log.info("Started playback of merged "+lastExport);
		        } catch (Exception e) {
					e.printStackTrace();
					log.warning("Failed to start abc player to test merged abc");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		playerThread.setDaemon(true);
		playerThread.start();
	}

	private static String trimNonAbc(String text) {
		// remove leading and trailing '-' '_' and trailing '('
		text = text.trim();
		if (text.isEmpty())
			return text;
		if (text.endsWith("-") || text.endsWith("_") || text.endsWith("(")) {
			text = text.substring(0, text.length() - 1);
		}
		if (text.startsWith("-") || text.startsWith("_")) {
			text = text.substring(1);
		}
		return text;
	}

	/**
	 * 
	 * @param x The number of parts
	 * @return string for badger chapter songbooks
	 */
	private String getAllParts(int x) {
		String str = "N: TS  ";
		StringBuilder str2 = new StringBuilder();

		for (int part = 1; part <= x; part++) {
			str2.append("  ").append(part);
		}
		str += x + ", ";
		return str + str2;
	}

	private JList<File> getGui(File[] all, boolean vertical) {
		if (all.length == 0)
			return null;
		theList = new JList<>(all);
		theList.setCellRenderer(new FileRenderer(!vertical));

		if (!vertical) {
			theList.setLayoutOrientation(javax.swing.JList.HORIZONTAL_WRAP);
			theList.setVisibleRowCount(-1);
		} else {
			theList.setVisibleRowCount(9);
		}
		return theList;
	}

	private String getLongestCommonSubstring(String str1, String str2) {
		int m = str1.length();
		int n = str2.length();

		int max = 0;

		int[][] dp = new int[m][n];
		int endIndex = -1;
		for (int i = 0; i < m; i++) {
			for (int j = 0; j < n; j++) {
				if (str1.charAt(i) == str2.charAt(j)) {

					// If first row or column
					if (i == 0 || j == 0) {
						dp[i][j] = 1;
					} else {
						// Add 1 to the diagonal value
						dp[i][j] = dp[i - 1][j - 1] + 1;
					}

					if (max < dp[i][j]) {
						max = dp[i][j];
						endIndex = i;
					}
				}

			}
		}
		// We want String upto endIndex, we are using endIndex+1 in substring.
		return str1.substring(endIndex - max + 1, endIndex + 1);
	}

	@SuppressWarnings("serial")
	static class FileRenderer extends DefaultListCellRenderer {

		private boolean pad;
		private Border padBorder = new EmptyBorder(3, 3, 3, 3);

		FileRenderer(boolean pad) {
			this.pad = pad;
		}

		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
				boolean cellHasFocus) {

			Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			JLabel l = (JLabel) c;
			File f = (File) value;
			l.setText(f.getName());
			l.setIcon(FileSystemView.getFileSystemView().getSystemIcon(f));
			if (pad) {
				l.setBorder(padBorder);
			}

			return l;
		}
	}

	private ActionListener getSourceActionListener() {
		return new ActionListener() {
			JFileChooser openFileChooser;

			@Override
			public void actionPerformed(ActionEvent e) {
				frame.getBtnSource().setEnabled(false);
				frame.getBtnDest().setEnabled(false);
				if (openFileChooser == null) {
					openFileChooser = new JFileChooser(sourceFolder);
					openFileChooser.setMultiSelectionEnabled(false);
					// openFileChooser.setFileFilter(new ExtensionFileFilter("ABC files", "abc", "txt"));
					openFileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
					openFileChooser.setDialogTitle(UIText.get("abctools.source.folder"));
				}

				int result = openFileChooser.showOpenDialog(frame);
				if (result == JFileChooser.APPROVE_OPTION) {
					sourceFolder = openFileChooser.getSelectedFile();
					refreshMerge();
				}
				frame.getBtnSource().setEnabled(true);
				frame.getBtnDest().setEnabled(true);
			}
		};
	}

	private ActionListener getDestActionListener() {
		return new ActionListener() {
			JFileChooser openFileChooser;

			@Override
			public void actionPerformed(ActionEvent e) {
				if (openFileChooser == null) {
					openFileChooser = new JFileChooser(destFolder);
					openFileChooser.setMultiSelectionEnabled(false);
					openFileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
					openFileChooser.setDialogTitle(UIText.get("abctools.destination.folder"));
				}

				int result = openFileChooser.showOpenDialog(frame);
				if (result == JFileChooser.APPROVE_OPTION) {
					destFolder = openFileChooser.getSelectedFile();
					refreshMerge();
				}
			}
		};
	}

	private ActionListener getJoinActionListener() {
		return e -> {
			try {
				join();
			} catch (IOException e1) {
				e1.printStackTrace();
				frame.setTextFieldText(UIText.get("abctools.an.error.occured.1", e1));
				lastExport = null;
				refreshTest();
			}
		};
	}

	private ActionListener getTestActionListener() {
		return e -> {
			try {
				test();
			} catch (IOException e1) {
				e1.printStackTrace();
				frame.setTextFieldText(UIText.get("abctools.an.error.occured.0", e1));
			}
		};
	}
}
