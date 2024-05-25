package com.aifel.abctools;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import static java.nio.file.FileVisitResult.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.filechooser.FileSystemView;
import javax.xml.transform.TransformerException;

import javax.swing.border.EmptyBorder;
import javax.swing.border.Border;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.abc.StringCleaner;
import com.digero.maestro.MaestroMain;
import com.digero.maestro.abc.AbcSong;
import com.digero.maestro.abc.ExportFilenameTemplate;
import com.digero.maestro.abc.PartAutoNumberer;
import com.digero.maestro.abc.PartNameTemplate;
import com.digero.maestro.util.FileResolver;
import com.digero.maestro.util.XmlUtil;
import com.digero.maestro.view.InstrNameSettings;
import com.digero.maestro.view.MiscSettings;
import com.digero.maestro.view.SaveAndExportSettings;

public class AbcTools {

	private Preferences toolsPrefs = Preferences.userNodeForPackage(AbcTools.class);
	private Preferences mergePrefs;
	private Preferences autoPrefs;
// @formatter:off
	private final String DIR_MERGE_SOURCE = "dir_source";
	private final String DIR_MERGE_DEST   = "dir_destination";
	private final String DIR_AUTO_SOURCE  = "dir_source";
	private final String DIR_AUTO_MIDI    = "dir_midi";
	private final String DIR_AUTO_DEST    = "dir_destination";
// @formatter:on
	private File sourceFolder;
	private File destFolder;
	private File sourceFolderAuto;
	private File destFolderAuto;
	private File midiFolderAuto;
	private ActionListener actionSource = getSourceActionListener();
	private ActionListener actionDest = getDestActionListener();
	private ActionListener actionJoin = getJoinActionListener();
	private ActionListener actionTest = getTestActionListener();

	private Preferences prefs = Preferences.userNodeForPackage(MaestroMain.class);

	private static final Pattern INFO_PATTERN = Pattern.compile("^([A-Z]):\\s*(.*)\\s*$");
	private static final int INFO_TYPE = 1;
	private static final int INFO_VALUE = 2;

	private static MultiMergerView frame = null;
	private static Timer swingUpdateTimer;

	JList<File> theList = null;
	private String lastExport = null;
	private PartAutoNumberer partAutoNumberer;
	private PartNameTemplate partNameTemplate;
	private ExportFilenameTemplate exportFilenameTemplate;
	private InstrNameSettings instrNameSettings;
	private SaveAndExportSettings saveSettings;
	private MiscSettings miscSettings;
	private volatile double progressFactor = 1;
	private volatile int exportCount = 0;
	private volatile int totalExportCount = 0;
	private volatile int result = 0;
	private volatile String textAuto = "";
	private Boolean updatedField = false;// Also used as Mutex
	private volatile int progressInt;
	private static AbcTools instance = null;

	public static void main(String[] args) {
		try {
			UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
				| UnsupportedLookAndFeelException e) {
			e.printStackTrace();
		}
		EventQueue.invokeLater(() -> {
			try {

				frame = new MultiMergerView();
				instance = new AbcTools();
				frame.setVisible(true);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		swingUpdateTimer = new Timer();
		swingUpdateTimer.scheduleAtFixedRate(new TimerTask() {
			  @Override
			  public void run() {
				  instance.updateProgress();
				  instance.updateField();
			  }
			}, 250L, 250L);
	}

	AbcTools() {
		// Setup folders from stored prefs if available:
		String myHome = System.getProperty("user.home");
		mergePrefs = toolsPrefs.node("mergeTool");
		sourceFolder = new File(mergePrefs.get(DIR_MERGE_SOURCE, myHome));
		destFolder = new File(mergePrefs.get(DIR_MERGE_DEST, myHome));
		autoPrefs = toolsPrefs.node("autoExport");
		sourceFolderAuto = new File(autoPrefs.get(DIR_AUTO_SOURCE, myHome));
		midiFolderAuto = new File(autoPrefs.get(DIR_AUTO_MIDI, myHome));
		destFolderAuto = new File(autoPrefs.get(DIR_AUTO_DEST, myHome));
		if (!sourceFolder.exists())
			sourceFolder = new File(myHome);
		if (!destFolder.exists())
			destFolder = new File(myHome);
		if (!sourceFolderAuto.exists())
			sourceFolderAuto = new File(myHome);
		if (!midiFolderAuto.exists())
			midiFolderAuto = new File(myHome);
		if (!destFolderAuto.exists())
			destFolderAuto = new File(myHome);

		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				writePrefs();
			}
		});

		// Setup Maestro version number:
		new MaestroMain();
		frame.setTitle("ABC Tools v"+MaestroMain.APP_VERSION.toString());

		// Setup action listeners
		frame.getBtnDest().addActionListener(actionDest);
		frame.getBtnSource().addActionListener(actionSource);
		frame.getBtnJoin().addActionListener(actionJoin);
		frame.getBtnTest().addActionListener(actionTest);
		frame.getScrollPane().getVerticalScrollBar().setUnitIncrement(22);
		frame.getBtnStartExport().addActionListener(getStartExportActionListener());
		frame.getBtnDestAuto().addActionListener(getDestAutoActionListener());
		frame.getBtnMIDI().addActionListener(getMIDIAutoActionListener());
		frame.getBtnSourceAuto().addActionListener(getSourceAutoActionListener());

		frame.getTxtAutoExport()
				.setText("<html>Start with selecting source, midi and dest folders."
						+ "<br>Destination folder must be empty!"
						+ "<br>MIDI folder is optional. It is used when midi cannot be found,"
						+ " then it looks in that folder before asking for location."
						+ "<br>When exporting it will use your Maestro settings for filename, partname etc etc."
						+ "<br>Close Maestro while this app runs.</html>");
		/*
		 * try { List<Image> icons = new ArrayList<>(); icons.add(ImageIO.read(new
		 * FileInputStream("abcmergetool.ico"))); frame.setIconImages(icons); } catch (Exception ex) { // Ignore
		 * ex.printStackTrace(); }
		 */
		refresh();
		refreshAuto();
	}

	protected void writePrefs() {
		mergePrefs.put(DIR_MERGE_SOURCE, sourceFolder.getAbsolutePath());
		mergePrefs.put(DIR_MERGE_DEST, destFolder.getAbsolutePath());
		autoPrefs.put(DIR_AUTO_SOURCE, sourceFolderAuto.getAbsolutePath());
		autoPrefs.put(DIR_AUTO_MIDI, midiFolderAuto.getAbsolutePath());
		autoPrefs.put(DIR_AUTO_DEST, destFolderAuto.getAbsolutePath());

		try {
			toolsPrefs.flush();
		} catch (BackingStoreException e) {
			e.printStackTrace();
		}
	}

	private void refresh() {
		Component c = getGui(sourceFolder.listFiles(new AbcFileFilter()), false);
		frame.setLblSourceText("Source: " + sourceFolder.getAbsolutePath());
		frame.setLblDestText("Destination: " + destFolder.getAbsolutePath());
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
								} else if (instr == null) {
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
							if (!"".equals(Q) && !Q.equals(value)) {
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
						"All these files do not seem to belong to same song. Continue with merge?", "Tempo mismatch",
						JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

				switch (misresult) {
				case JOptionPane.YES_OPTION:
					break;
				case JOptionPane.NO_OPTION:
					frame.setTextFieldText("Cancelled merge.");
					lastExport = null;
					refreshTest();
					return;
				case JOptionPane.CANCEL_OPTION:
					frame.setTextFieldText("Cancelled merge.");
					lastExport = null;
					refreshTest();
					return;
				}
			}

			String n1 = theFiles.get(0).getName();
			int dot = n1.lastIndexOf('.');
			if (dot > 0)
				n1 = n1.substring(0, dot);

			String n2 = theFiles.get(theFiles.size() - 1).getName();
			dot = n2.lastIndexOf('.');
			if (dot > 0)
				n2 = n2.substring(0, dot);

			String newName = trimNonAbc(getLongestCommonSubstring(n1, n2));
			if (newName.length() == 0)
				newName = "mySong";
			newName += ".abc";
			File newFile = new File(destFolder, newName);
			if (newFile.exists()) {
				int result = JOptionPane.showConfirmDialog(frame,
						"The file " + newFile.getAbsolutePath() + " exist already. Do you want to overwrite it?",
						"Overwrite", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

				switch (result) {
				case JOptionPane.YES_OPTION:
					break;
				case JOptionPane.NO_OPTION:
					frame.setTextFieldText("Cancelled save.");
					lastExport = null;
					refreshTest();
					return;
				case JOptionPane.CANCEL_OPTION:
					frame.setTextFieldText("Cancelled save.");
					lastExport = null;
					refreshTest();
					return;
				}
			}
			newFile.createNewFile();
			FileWriter writer = new FileWriter(newFile);

			frame.setTextFieldText(
					"Writing new file:\n " + newFile.getAbsolutePath() + "\n\n The song has " + (x - 1) + " parts.");
			StringBuilder info = new StringBuilder("Writing new file:\n " + newFile.getAbsolutePath()
					+ "\n\n The song has " + (x - 1) + " parts.\n\n");
			for (String line : newContent) {
				writer.write(line + System.lineSeparator());
				info.append(System.lineSeparator()).append(line);
			}
			writer.close();
			lastExport = newFile.getAbsolutePath();
			refreshTest();
			frame.setTextFieldText(info.toString());
		} else {
			frame.setTextFieldText("Please select at least 2 abc files..");
			lastExport = null;
			refreshTest();
		}
	}

	private void test() throws IOException {
		if (lastExport == null)
			return;
		String cmd = "javaw.exe -d64 -classpath . -jar AbcPlayer.jar " + lastExport.replace('\\', '/');
		System.out.println(cmd);
		Runtime.getRuntime().exec(cmd);
	}

	private static String trimNonAbc(String text) {
		// remove leading and trailing '-' '_' and trailing '('
		text = text.trim();
		if (text.length() == 0)
			return text;
		if (text.endsWith("-") || text.endsWith("_") || text.endsWith("(")) {
			text = text.substring(0, text.length() - 1);
		}
		if (text.startsWith("-") || text.startsWith("_")) {
			text = text.substring(1, text.length());
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

	static class AbcFileFilter implements FileFilter {

		public boolean accept(File file) {
			String name = file.getName().toLowerCase();
			return name.endsWith(".abc") || name.endsWith(".txt");
		}
	}

	static class MsxFileFilter implements FileFilter {

		public boolean accept(File file) {
			String name = file.getName().toLowerCase();
			return name.endsWith(".msx");
		}
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
				if (openFileChooser == null) {
					openFileChooser = new JFileChooser(sourceFolder);
					openFileChooser.setMultiSelectionEnabled(false);
					// openFileChooser.setFileFilter(new ExtensionFileFilter("ABC files", "abc", "txt"));
					openFileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
					openFileChooser.setDialogTitle("Source folder");
				}

				int result = openFileChooser.showOpenDialog(frame);
				if (result == JFileChooser.APPROVE_OPTION) {
					sourceFolder = openFileChooser.getSelectedFile();
					refresh();
				}
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
					openFileChooser.setDialogTitle("Destination folder");
				}

				int result = openFileChooser.showOpenDialog(frame);
				if (result == JFileChooser.APPROVE_OPTION) {
					destFolder = openFileChooser.getSelectedFile();
					refresh();
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
				frame.setTextFieldText("An error occured:\n\n" + e1);
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
				frame.setTextFieldText("An error occured:\n\n" + e1);
			}
		};
	}

	private ActionListener getStartExportActionListener() {
		return e -> {
			(new Thread(() -> {
				try {
					autoExport();
				} catch (Exception ioe) {
					ioe.printStackTrace();
					setProgress(0);
					appendToField("<br><font color='red'>"+ioe.toString()+"</font>");
					SwingUtilities.invokeLater(() -> {
						frame.getBtnStartExport().setEnabled(true);
						frame.setForceMixTimingEnabled(true);
						frame.setBtnDestAutoEnabled(true);
						frame.setBtnMIDIEnabled(true);
						frame.setBtnSourceAutoEnabled(true);
						frame.setSaveMSXEnabled(true);
						frame.setTabsEnabled(true);
						frame.setRecursiveCheckBoxEnabled(true);
					});
				}
			})).start();
		};
	}

	private void refreshAuto() {
		frame.setLblSourceAutoText("Source: " + sourceFolderAuto.getAbsolutePath());
		frame.setLblDestAutoText("Destination: " + destFolderAuto.getAbsolutePath());
		frame.setLblMidiAutoText("MIDIs: " + midiFolderAuto.getAbsolutePath());
		frame.repaint();
	}

	private void autoExport() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			refreshAuto();
			frame.getBtnStartExport().setEnabled(false);
			frame.setForceMixTimingEnabled(false);
			frame.setBtnDestAutoEnabled(false);
			frame.setBtnMIDIEnabled(false);
			frame.setBtnSourceAutoEnabled(false);
			frame.setSaveMSXEnabled(false);
			frame.setTabsEnabled(false);
			frame.setRecursiveCheckBoxEnabled(false);
		});
		// Test if dest is empty
		if (destFolderAuto.listFiles().length != 0) {
			SwingUtilities.invokeLater(() -> {
				frame.getTxtAutoExport()
						.setText("<html>Start with selecting source, midi and dest folders.<br>"
								+ "<font color='red'>Destination folder must be empty!</font>"
								+ "<br>MIDI folder is optional. It is used when midi cannot be found,"
								+ " then it looks in that folder before asking for location."
								+ "<br>When exporting it will use your Maestro settings for filename, partname etc etc."
								+ "<br>Close Maestro while this app runs.</html>");
				frame.getBtnStartExport().setEnabled(true);
				frame.setForceMixTimingEnabled(true);
				frame.setBtnDestAutoEnabled(true);
				frame.setBtnMIDIEnabled(true);
				frame.setBtnSourceAutoEnabled(true);
				frame.setSaveMSXEnabled(true);
				frame.setTabsEnabled(true);
				frame.setRecursiveCheckBoxEnabled(true);
			});
			setProgress(0);
			return;
		}
		textAuto = "";
		appendToField("Keep Maestro closed while this app runs.<br><br>Exporting in progress");

		partAutoNumberer = new PartAutoNumberer(prefs.node("partAutoNumberer"));
		partNameTemplate = new PartNameTemplate(prefs.node("partNameTemplate"));
		exportFilenameTemplate = new ExportFilenameTemplate(prefs.node("exportFilenameTemplate"));
		instrNameSettings = new InstrNameSettings(prefs.node("instrNameSettings"));
		saveSettings = new SaveAndExportSettings(prefs.node("saveAndExportSettings"));
		miscSettings = new MiscSettings(prefs.node("miscSettings"), true);

		setProgress(0);
		if (!frame.getRecursiveCheckBoxSelected()) {
			File[] projects = sourceFolderAuto.listFiles(new MsxFileFilter());
			
			appendToField("<br>Found " + projects.length + " project files.<br>");
			
			progressFactor = 1000.0d / projects.length;
			exportCount = 0;
			
			for (File project : projects) {
				exportProject(project);
				exportCount++;
				setProgress((int) (exportCount * progressFactor));
			}
		} else {		
			totalExportCount = 0;
			
			Files.walkFileTree(sourceFolderAuto.toPath(), new CountFiles());
			appendToField("<br>Found " + totalExportCount + " project files.<br>");
			
			progressFactor = 1000.0d / totalExportCount;
			exportCount = 0;
			
			Files.walkFileTree(sourceFolderAuto.toPath(), new ProcessFiles());
		}
		setProgress(1000);

		appendToField("<br><br>Exports finished.");
		SwingUtilities.invokeLater(() -> {
			frame.getBtnStartExport().setEnabled(true);
			frame.setForceMixTimingEnabled(true);
			frame.setBtnDestAutoEnabled(true);
			frame.setBtnMIDIEnabled(true);
			frame.setBtnSourceAutoEnabled(true);
			frame.setSaveMSXEnabled(true);
			frame.setTabsEnabled(true);
			frame.setRecursiveCheckBoxEnabled(true);
		});
	}
	
	public class CountFiles extends SimpleFileVisitor<Path> {
		
		MsxFileFilter f = new MsxFileFilter();
		
	    @Override
	    public FileVisitResult visitFile(Path file, BasicFileAttributes attr) {
	        if (attr.isRegularFile() && f.accept(file.toFile())) {
	        	totalExportCount++;
	        }
	        return CONTINUE;
	    }
	
	    // Print each directory visited.
	    @Override
	    public FileVisitResult postVisitDirectory(Path dir,
	                                          IOException exc) {
	        //System.out.format("Finished directory: %s%n", dir);
	        return CONTINUE;
	    }
	
	    // If there is some error accessing
	    // the file, let the user know.
	    // If you don't override this method
	    // and an error occurs, an IOException 
	    // is thrown.
	    @Override
	    public FileVisitResult visitFileFailed(Path file,
	                                       IOException exc) {
	        System.err.println(exc);
	        return CONTINUE;
	    }
	}
	
	public class ProcessFiles extends SimpleFileVisitor<Path> {

		MsxFileFilter f = new MsxFileFilter();
		
	    @Override
	    public FileVisitResult visitFile(Path file, BasicFileAttributes attr) {
	    	if (f.accept(file.toFile())) {
		        if (attr.isSymbolicLink()) {
		            System.out.format("Ignoring symbolic link: %s ", file);
		        } else if (attr.isRegularFile()) {
		        	try {
						exportProject(file.toFile());
					} catch (Exception e) {
						appendToField("<br><font color='red'>"+e.toString()+"</font>");
						//e.printStackTrace();
					}
					exportCount++;
					setProgress((int) (exportCount * progressFactor));
		        } else {
		            System.out.format("Ignoring: %s ", file);
		        }
	    	}
	        return CONTINUE;
	    }
	
	    // Print each directory visited.
	    @Override
	    public FileVisitResult postVisitDirectory(Path dir,
	                                          IOException exc) {
	        System.out.format("Finished directory: %s%n", dir);
	        return CONTINUE;
	    }
	
	    // If there is some error accessing
	    // the file, let the user know.
	    // If you don't override this method
	    // and an error occurs, an IOException 
	    // is thrown.
	    @Override
	    public FileVisitResult visitFileFailed(Path file,
	                                       IOException exc) {
	        System.err.println(exc);
	        return CONTINUE;
	    }
	}

	private void setProgress(final int progress) {
		progressInt = progress;
	}

	private void appendToField(String txt) {
		synchronized(updatedField) {
			updatedField = true;
			textAuto += txt;
		}
	}
	
	private void updateProgress() {
		SwingUtilities.invokeLater(() -> {
			frame.setProgressBarValue(progressInt);
		});
	}

	private void updateField() {
		synchronized(updatedField) {
			if (updatedField) {
				SwingUtilities.invokeLater(() -> {
					synchronized(updatedField) {
						frame.getTxtAutoExport().setText("<html>" + textAuto + "</html>");
						updatedField = false;
					}
				});
			}
		}
	}

	private volatile boolean projectModified = false;

	private void exportProject(File project) throws Exception {
		appendToField("<br>Exporting " + project.getName());

		projectModified = false;
		AbcSong abcSong = new AbcSong(project, partAutoNumberer, partNameTemplate, exportFilenameTemplate,
				instrNameSettings, openFileResolver, miscSettings);

		if (frame.getForceMixTimingSelected()) {
			abcSong.setMixTiming(true);
		}

		abcSong.setSkipSilenceAtStart(saveSettings.skipSilenceAtStart);
		abcSong.setDeleteMinimalNotes(saveSettings.deleteMinimalNotes);
		abcSong.setAllOut(miscSettings.showBadger && miscSettings.allBadger);
		abcSong.setBadger(miscSettings.showBadger);
		StringCleaner.cleanABC = saveSettings.convertABCStringsToBasicAscii;

		File exportFile = abcSong.getExportFile();
		String fileName = "mySong.abc";

		// Always regenerate setting from pattern export is highest precedent
		if (exportFilenameTemplate.shouldRegenerateFilename()) {
			fileName = exportFilenameTemplate.formatName();
		} else if (exportFile != null) // else use abc filename if exists already
		{
			fileName = exportFile.getName();
		} else if (abcSong.getSaveFile() != null) // else use msx filename if exists already
		{
			fileName = abcSong.getSaveFile().getName();
		} else if (exportFilenameTemplate.isEnabled()) // else use pattern if usage is enabled
		{
			fileName = exportFilenameTemplate.formatName();
		} else if (abcSong.getSourceFile() != null) // else default to source file (midi/abc)
		{
			fileName = abcSong.getSourceFilename();
		}

		int dot = fileName.lastIndexOf('.');
		if (dot > 0)
			fileName = fileName.substring(0, dot);
		else if (dot == 0)
			fileName = "";
		fileName = StringCleaner.cleanForFileName(fileName);
		fileName += ".abc";
		
		File finalFolder = getTreeFolder(sourceFolderAuto, destFolderAuto, project);

		exportFile = new File(finalFolder, fileName);
		String finalName = exportFile.getName();
		dot = finalName.lastIndexOf('.');
		if (dot > 0)
			finalName = finalName.substring(0, dot);
		int n = 1;
		while (exportFile.exists()) {
			n++;
			exportFile = new File(exportFile.getParentFile(), finalName + " (" + n + ").abc");
		}
		finalFolder.mkdirs();// for recursive exporting we need the folders to exist.

		if (projectModified && frame.getSaveMSXSelected()) {
			try {
				XmlUtil.saveDocument(abcSong.saveToXml(), abcSong.getSaveFile());
				appendToField("<br>&nbsp;&nbsp;msx saved.");
			} catch (FileNotFoundException e) {
				appendToField("<br><font color='red'>&nbsp;&nbsp;msx saving failed.</font>");
			} catch (IOException | TransformerException e) {
				appendToField("<br><font color='red'>&nbsp;&nbsp;msx saving failed.</font>");
			}				
		}
		
		// Save abc file after saving msx file, so we don't change the msx abc save filename.
		abcSong.exportAbc(exportFile);

		appendToField("<br>&nbsp;&nbsp;as " + exportFile.getName());
	}

	/**
	 * 
	 * @param sourceFolderAuto2
	 * @param destFolderAuto
	 * @param project
	 * @return Project file but in a folder nested inside destFolderAuto in same manner as project is nested inside sourceFolderAuto2
	 * @throws IOException
	 */
	private File getTreeFolder(File sourceFolderAuto2, File destFolderAuto2, File project) throws IOException {
		if (project.getParentFile().equals(sourceFolderAuto2)) {
			//appendToField("<br><font color='red'> no tree! </font>");
			return destFolderAuto2;
		}
		List<String> theList = new ArrayList<>();
		File now = new File(project.getParent());
		int iterCheck = 0;
		while (!now.equals(sourceFolderAuto2) && iterCheck < 100) {
			iterCheck++;
			theList.add(now.getName());			
			now = now.getParentFile();
		}
		if (iterCheck == 100) throw new IOException("Something went wrong with path tree");
		File future = new File(destFolderAuto2.getPath());
		for (int i = theList.size()-1 ; i >= 0 ; i--) {
			String branch = theList.get(i);
			future = new File (future, branch);
		}
		return future;
	}

	private ActionListener getSourceAutoActionListener() {
		return new ActionListener() {
			JFileChooser openFileChooser;

			@Override
			public void actionPerformed(ActionEvent e) {
				if (openFileChooser == null) {
					openFileChooser = new JFileChooser(sourceFolderAuto);
					openFileChooser.setMultiSelectionEnabled(false);
					// openFileChooser.setFileFilter(new ExtensionFileFilter("ABC files", "abc", "txt"));
					openFileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
					openFileChooser.setDialogTitle("Source folder");
				}
				
				result = openFileChooser.showOpenDialog(frame);
				if (result == JFileChooser.APPROVE_OPTION) {
					sourceFolderAuto = openFileChooser.getSelectedFile();
					refreshAuto();
				}
			}
		};
	}

	private ActionListener getMIDIAutoActionListener() {
		return new ActionListener() {
			JFileChooser openFileChooser;

			@Override
			public void actionPerformed(ActionEvent e) {
				if (openFileChooser == null) {
					openFileChooser = new JFileChooser(midiFolderAuto);
					openFileChooser.setMultiSelectionEnabled(false);
					// openFileChooser.setFileFilter(new ExtensionFileFilter("ABC files", "abc", "txt"));
					openFileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
					openFileChooser.setDialogTitle("MIDI folder");
				}
				result = openFileChooser.showOpenDialog(frame);
				if (result == JFileChooser.APPROVE_OPTION) {
					midiFolderAuto = openFileChooser.getSelectedFile();
					refreshAuto();
				}
			}
		};
	}

	private ActionListener getDestAutoActionListener() {
		return new ActionListener() {
			JFileChooser openFileChooser;

			@Override
			public void actionPerformed(ActionEvent e) {
				if (openFileChooser == null) {
					openFileChooser = new JFileChooser(destFolderAuto);
					openFileChooser.setMultiSelectionEnabled(false);
					openFileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
					openFileChooser.setDialogTitle("Destination folder");
				}
				
				result = openFileChooser.showOpenDialog(frame);
				if (result == JFileChooser.APPROVE_OPTION) {
					destFolderAuto = openFileChooser.getSelectedFile();
					refreshAuto();
				}
			}
		};
	}

	/** Used when the MIDI file in a Maestro song project can't be loaded. */
	private FileResolver openFileResolver = new FileResolver() {
		private File newMidi;

		@Override
		public File locateFile(File original, String message) {
			newMidi = new File(midiFolderAuto, original.getName());
			if (original.equals(newMidi)) {
				message += "\n\nWould you like to try to locate the file?";
				return resolveHelper(original, message);
			}
			projectModified = true;
			return newMidi;
		}

		@Override
		public File resolveFile(File original, String message) {
			message += "\n\nWould you like to pick a different file?";
			return resolveHelper(original, message);
		}

		private File resolveHelper(File original, String message) {
			try {
				SwingUtilities.invokeAndWait(() -> {
					result = JOptionPane.showConfirmDialog(frame, message, "Failed to open file",
							JOptionPane.OK_CANCEL_OPTION);
				});
	
				File alternateFile = null;
				if (result == JOptionPane.OK_OPTION) {
					JFileChooser jfc = new JFileChooser();
					jfc.setDialogTitle("Open missing MIDI");
					if (original != null)
						jfc.setSelectedFile(original);
		
					try {
						SwingUtilities.invokeAndWait(() -> {
							result = jfc.showOpenDialog(frame);
						});
					} catch (Exception e) {
						appendToField("<br><font color='red'>"+e.toString()+"</font>");
						e.printStackTrace();
					}
					if (result == JFileChooser.APPROVE_OPTION) {
						alternateFile = jfc.getSelectedFile();
						projectModified = true;
					}
				}
		
				return alternateFile;
			} catch (InvocationTargetException e) {
				appendToField("<br><font color='red'>"+e.toString()+"</font>");
				e.printStackTrace();
			} catch (InterruptedException e) {
				appendToField("<br><font color='red'>"+e.toString()+"</font>");
				e.printStackTrace();
			}
			return null;
		}
	};
}
