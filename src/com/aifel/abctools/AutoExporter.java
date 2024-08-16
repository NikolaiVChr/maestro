package com.aifel.abctools;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.TERMINATE;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.prefs.Preferences;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.xml.transform.TransformerException;

import com.aifel.abctools.AbcTools.MsxFileFilter;
import com.aifel.abctools.AbcTools.FolderFileFilter;
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

public class AutoExporter {
	volatile File sourceFolderAuto;
	volatile File destFolderAuto;
	volatile File midiFolderAuto;

	private Preferences prefs = Preferences.userNodeForPackage(MaestroMain.class);
	private final String DIR_AUTO_SOURCE  = "dir_source";
	private final String DIR_AUTO_MIDI    = "dir_midi";
	private final String DIR_AUTO_DEST    = "dir_destination";

	private final MultiMergerView frame;
	private final Timer swingUpdateTimer;

	private double progressFactor = 1;
	private int exportCount = 0;
	private int totalExportCount = 0;
	private volatile int result = 0;
	private volatile String textAuto = "";
	private boolean txtFieldDirty = false;
	private final Object txtFieldMutex = new Object();
	
	private volatile int progressInt = 0;
	private volatile boolean txtFieldPrimedForUpdate = false;
	private boolean projectModified = false;
	private final AbcTools main;
	private final Preferences autoPrefs;
	private File newNestedMidi = null;
	private File nestedProject = null;
	private File oldMidi = null;
	private volatile boolean cancel = false;
	
	AutoExporter (MultiMergerView frame, String myHome, AbcTools main, Preferences autoPrefs) {
		this.frame = frame;
		this.main = main;
		this.autoPrefs = autoPrefs;
		
		swingUpdateTimer = new Timer();
		swingUpdateTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				try {
					updateProgress();
					updateField();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}, 250L, 250L);
		setToField("Start with selecting source, midi and destination folders."
				+ "<br>Destination folder must be empty!"
				+ "<br>MIDI folder is optional. It is used when midi cannot be found,"
				+ " then it looks in that folder before asking for location."
				+ "<br>When exporting it will use your Maestro settings for filename, partname etc etc."
				+ "<br>Close Maestro while this app runs.");
		
		sourceFolderAuto = new File(autoPrefs.get(DIR_AUTO_SOURCE, myHome));
		midiFolderAuto = new File(autoPrefs.get(DIR_AUTO_MIDI, myHome));
		destFolderAuto = new File(autoPrefs.get(DIR_AUTO_DEST, myHome));
		
		if (!sourceFolderAuto.exists())
			sourceFolderAuto = new File(myHome);
		if (!midiFolderAuto.exists())
			midiFolderAuto = new File(myHome);
		if (!destFolderAuto.exists())
			destFolderAuto = new File(myHome);
		
		frame.getBtnStartExport().addActionListener(getStartExportActionListener());
		frame.getBtnCancelExport().addActionListener(getCancelExportActionListener());
		frame.getBtnDestAuto().addActionListener(getDestAutoActionListener());
		frame.getBtnMIDI().addActionListener(getMIDIAutoActionListener());
		frame.getBtnSourceAuto().addActionListener(getSourceAutoActionListener());
		
		refreshAuto();
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
						frame.getBtnCancelExport().setEnabled(false);
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
	
	private ActionListener getCancelExportActionListener() {
		return e -> {
			cancel = true;
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
			frame.getBtnCancelExport().setEnabled(true);
			frame.setForceMixTimingEnabled(false);
			frame.setBtnDestAutoEnabled(false);
			frame.setBtnMIDIEnabled(false);
			frame.setBtnSourceAutoEnabled(false);
			frame.setSaveMSXEnabled(false);
			frame.setTabsEnabled(false);
			frame.setRecursiveCheckBoxEnabled(false);
		});
		// Test if dest is empty
		if (destFolderAuto.listFiles(new FolderFileFilter()).length != 0) {
			setToField("Start with selecting source, midi and destination folders.<br>"
					+ "<font color='red'>Destination folder must be empty!</font>"
					+ "<br>MIDI folder is optional. It is used when midi cannot be found,"
					+ " then it looks in that folder before asking for location."
					+ "<br>When exporting it will use your Maestro settings for filename, partname etc etc."
					+ "<br>Close Maestro while this app runs.");
			SwingUtilities.invokeLater(() -> {
				frame.getBtnStartExport().setEnabled(true);
				frame.getBtnCancelExport().setEnabled(false);
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
		
		setToField("Keep Maestro closed while this app runs.<br><br>Exporting in progress");

		main.partAutoNumberer = new PartAutoNumberer(prefs.node("partAutoNumberer"));
		main.partNameTemplate = new PartNameTemplate(prefs.node("partNameTemplate"));
		main.exportFilenameTemplate = new ExportFilenameTemplate(prefs.node("exportFilenameTemplate"));
		main.instrNameSettings = new InstrNameSettings(prefs.node("instrNameSettings"));
		main.saveSettings = new SaveAndExportSettings(prefs.node("saveAndExportSettings"));
		main.miscSettings = new MiscSettings(prefs.node("miscSettings"), true);

		setProgress(0);
		cancel = false;
		if (!frame.getRecursiveCheckBoxSelected()) {
			File[] projects = sourceFolderAuto.listFiles(new MsxFileFilter());
			
			appendToField("<br>Found " + projects.length + " project files.<br>");
			
			progressFactor = 1000.0d / projects.length;
			exportCount = 0;
			
			for (File project : projects) {
				if (cancel) break;
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
		if (!cancel) {
			setProgress(1000);
			appendToField("<br><br>Exports finished.");
		} else {
			appendToField("<br><br>Exports cancelled.");
		}
		
		SwingUtilities.invokeLater(() -> {
			frame.getBtnStartExport().setEnabled(true);
			frame.getBtnCancelExport().setEnabled(false);
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
	    	if (cancel) return TERMINATE;
	    	if (f.accept(file.toFile())) {
		        if (attr.isSymbolicLink()) {
		            System.out.format("Ignoring symbolic link: %s ", file);
		        } else if (attr.isRegularFile()) {
		        	try {
						exportProject(file.toFile());
					} catch (Exception e) {
						appendToField("<br><font color='red'>"+e.toString()+"</font>");
						e.printStackTrace();
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
		synchronized(txtFieldMutex) {
			txtFieldDirty = true;
			textAuto += txt;
		}
	}
	
	private void setToField(String txt) {
		synchronized(txtFieldMutex) {
			txtFieldDirty = true;
			textAuto = txt;
		}
	}
	
	private void updateProgress() {
		SwingUtilities.invokeLater(() -> {
			frame.setProgressBarValue(progressInt);
		});
	}

	private void updateField() {
		synchronized(txtFieldMutex) {
			if (txtFieldDirty && !txtFieldPrimedForUpdate) {
				txtFieldPrimedForUpdate = true;
				SwingUtilities.invokeLater(() -> {
					synchronized(txtFieldMutex) {
						frame.getTxtAutoExport().setText("<html>" + textAuto + "</html>");
						txtFieldDirty = false;
						txtFieldPrimedForUpdate = false;
					}
				});
			}
		}
	}

	private void exportProject(File project) throws Exception {
		appendToField("<br>Exporting " + project.getName());

		projectModified = false;
		newNestedMidi = null;
		oldMidi = null;
		nestedProject = project;
		AbcSong abcSong = new AbcSong(project, main.partAutoNumberer, main.partNameTemplate, main.exportFilenameTemplate,
				main.instrNameSettings, openFileResolver, main.miscSettings);

		if (frame.getForceMixTimingSelected()) {
			abcSong.setMixTiming(true);
		}

		abcSong.setSkipSilenceAtStart(main.saveSettings.skipSilenceAtStart);
		abcSong.setDeleteMinimalNotes(main.saveSettings.deleteMinimalNotes);
		abcSong.setAllOut(main.miscSettings.showBadger && main.miscSettings.allBadger);
		abcSong.setBadger(main.miscSettings.showBadger);
		StringCleaner.cleanABC = main.saveSettings.convertABCStringsToBasicAscii;

		File exportFile = abcSong.getExportFile();
		String fileName = "mySong.abc";

		// Always regenerate setting from pattern export is highest precedent
		if (main.exportFilenameTemplate.shouldRegenerateFilename()) {
			fileName = main.exportFilenameTemplate.formatName();
		} else if (exportFile != null) // else use abc filename if exists already
		{
			fileName = exportFile.getName();
		} else if (abcSong.getSaveFile() != null) // else use msx filename if exists already
		{
			fileName = abcSong.getSaveFile().getName();
		} else if (main.exportFilenameTemplate.isEnabled()) // else use pattern if usage is enabled
		{
			fileName = main.exportFilenameTemplate.formatName();
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
	 * @return folder nested inside destFolderAuto in same manner as project is nested inside sourceFolderAuto2
	 * @throws IOException
	 */
	private File getTreeFolder(File sourceFolderAuto2, File destFolderAuto2, File project) throws IOException {
		if (project.getParentFile().equals(sourceFolderAuto2)) {
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
	
	/**
	 * 
	 * @param sourceFolderAuto2
	 * @param midiFolderAuto2
	 * @param newFile
	 * @return folder nested inside midiFolderAuto in same manner as projectFile is nested inside sourceFolderAuto
	 * @throws IOException
	 */
	private File getTreeFolderMidi(File projectFile) throws IOException {
		if (projectFile.getParentFile().equals(sourceFolderAuto)) {
			return midiFolderAuto;
		}
		List<String> theList = new ArrayList<>();
		File now = new File(projectFile.getParent());
		int iterCheck = 0;
		while (now != null && !now.equals(sourceFolderAuto) && iterCheck < 100) {
			iterCheck++;
			theList.add(now.getName());			
			now = now.getParentFile();
		}
		if (iterCheck == 100 || now == null) return null;
		File future = new File(midiFolderAuto.getPath());
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
			//System.out.println("\nOriginal="+original.getPath());
			if (oldMidi == null) oldMidi = original;// To ensure message on screen shows midi from project file.
			newMidi = new File(midiFolderAuto, original.getName());
			if (newNestedMidi == null && frame.getRecursiveCheckBoxSelected()) {
				try {
					File finalFolder = getTreeFolderMidi(nestedProject);
					if (finalFolder != null) {
						newNestedMidi = new File(finalFolder, original.getName());
						//System.out.println("New="+newNestedMidi.getPath());
					} else {
						//System.out.println("finalFolder == null");
					}
				} catch (IOException e) {
					newNestedMidi = null;
					//System.out.println("IO");
				}
			} else {
				//System.out.println("New already="+newNestedMidi.getPath());
			}
			if (original.equals(newNestedMidi)) {
				message += "\n\nWould you like to try to locate the file?";
				return resolveHelper(oldMidi, message);
			} else if (original.equals(newMidi)) {
				if (newNestedMidi != null) {
					//System.out.println("return newNestedMidi");
					projectModified = true;
					return newNestedMidi;
				}
				//System.out.println("newNestedMidi == null");
				message += "\n\nWould you like to try to locate the file?";
				return resolveHelper(oldMidi, message);
			}
			//System.out.println("return newMidi="+newMidi.getPath());
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
							JOptionPane.YES_NO_CANCEL_OPTION);
				});
	
				File alternateFile = null;
				if (result == JOptionPane.YES_OPTION) {
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
				} else if (result == JOptionPane.CANCEL_OPTION) {
					cancel = true;
				}
		
				return alternateFile;
			} catch (InvocationTargetException | InterruptedException e) {
				appendToField("<br><font color='red'>"+e.toString()+"</font>");
				e.printStackTrace();
			}
			return null;
		}
	};
	
	void flushPrefs () {
		autoPrefs.put(DIR_AUTO_SOURCE, sourceFolderAuto.getAbsolutePath());
		autoPrefs.put(DIR_AUTO_MIDI, midiFolderAuto.getAbsolutePath());
		autoPrefs.put(DIR_AUTO_DEST, destFolderAuto.getAbsolutePath());
	}
}
