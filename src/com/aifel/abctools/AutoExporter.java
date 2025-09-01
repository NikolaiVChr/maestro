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
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.xml.transform.TransformerException;

import com.aifel.abctools.AbcTools.MsxFileFilter;
import com.aifel.abctools.AbcTools.FolderFileFilter;
import com.digero.common.abc.StringCleaner;
import com.digero.common.abctomidi.AbcInfo;
import com.digero.common.abctomidi.AbcToMidi;
import com.digero.common.abctomidi.FileAndData;
import com.digero.common.util.ExtensionFileFilter;
import com.digero.common.util.LotroParseException;
import com.digero.common.util.ParseException;
import com.digero.common.util.Util;
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
	private static final Logger log = Logger.getLogger("util");
	
	volatile File sourceFolderAuto;
	volatile File destFolderAuto;
	volatile File midiFolderAuto;

	private final Preferences prefs = Preferences.userNodeForPackage(MaestroMain.class);
	private final String DIR_AUTO_SOURCE  = "dir_source";
	private final String DIR_AUTO_MIDI    = "dir_midi";
	private final String DIR_AUTO_DEST    = "dir_destination";

	private final AbcToolsView frame;
	private final Timer swingUpdateTimer;

	private double progressFactor = 1;
	private int exportCount = 0;
	private int totalExportCount = 0;
	private volatile int result = 0;
	private volatile String textAuto = "";
	private boolean txtFieldDirty = false;
	private final Object txtFieldMutex = new Object();

    private List<File> skippedProjects = new ArrayList<>();
    //private List<File> highCandidates = new ArrayList<>();

	private volatile int progressInt = 0;
	private volatile boolean txtFieldPrimedForUpdate = false;
	private boolean projectModified = false;
	private final AbcTools main;
	private final Preferences autoPrefs;
	private File newNestedMidi = null;
	private File nestedProject = null;
	private File oldMidi = null;
	private volatile boolean cancel = false;
    private volatile boolean inProgress = false;
	
	PartAutoNumberer partAutoNumberer;
	PartNameTemplate partNameTemplate;
	ExportFilenameTemplate exportFilenameTemplate;
	InstrNameSettings instrNameSettings;
	SaveAndExportSettings saveSettings;
	MiscSettings miscSettings;
	
	// For testing:
	private static final boolean neverLocateMidi = false;// for testing
	private static final boolean testIfOutputIsValid = false;// makes it slower
	
	AutoExporter (AbcToolsView frame, String myHome, AbcTools main, Preferences autoPrefs) {
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
					log.severe("Error updating progress/field: " + e.toString());
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
		
		SwingUtilities.invokeLater(() -> {
			frame.getBtnStartExport().addActionListener(getStartExportActionListener());
			frame.getBtnCancelExport().addActionListener(getCancelExportActionListener());
			frame.getBtnDestAuto().addActionListener(getDestAutoActionListener());
			frame.getBtnMIDI().addActionListener(getMIDIAutoActionListener());
			frame.getBtnSourceAuto().addActionListener(getSourceAutoActionListener());
			frame.addForceOrganicActionListener(getOrganicActionListener());
            frame.addForceOrganic2ActionListener(getOrganicActionListener());
            frame.addForceMixActionListener(getOrganicActionListener());
            frame.addForceLegacyActionListener(getOrganicActionListener());
			refreshAuto();
		});		
	}
	
	private ActionListener getStartExportActionListener() {
		return e -> {
			(new Thread(() -> {
				try {
					autoExport();
				} catch (Exception ioe) {
                    inProgress = false;
					log.warning("Error exporting: " + ioe.toString());
					setProgress(0);
					appendToField("<br><font color='red'>"+ioe.toString()+"</font>");
					SwingUtilities.invokeLater(() -> {
						frame.getBtnStartExport().setEnabled(true);
						frame.getBtnCancelExport().setEnabled(false);
                        enableForceCheckBoxes();
						frame.setBtnDestAutoEnabled(true);
						frame.setBtnMIDIEnabled(true);
						frame.setBtnSourceAutoEnabled(true);
						frame.setSaveMSXEnabled(true);
						frame.setSaveMSXtimingEnabled(true);
						frame.setSaveMSXabcEnabled(true);
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
        inProgress = true;
		SwingUtilities.invokeAndWait(() -> {
			refreshAuto();
			frame.getBtnStartExport().setEnabled(false);
			frame.getBtnCancelExport().setEnabled(true);
			frame.setForceMixTimingEnabled(false);
            frame.setForceLegacyTimingEnabled(false);
			frame.setForceOrganicEnabled(false);
			frame.setForceOrganic2Enabled(false);
			frame.setBtnDestAutoEnabled(false);
			frame.setBtnMIDIEnabled(false);
			frame.setBtnSourceAutoEnabled(false);
			frame.setSaveMSXEnabled(false);
			frame.setSaveMSXtimingEnabled(false);
			frame.setSaveMSXabcEnabled(false);
			frame.setTabsEnabled(false);
			frame.setRecursiveCheckBoxEnabled(false);
		});
		// Test if dest is empty
		if (destFolderAuto.listFiles(new FolderFileFilter()).length != 0) {
            inProgress = false;
			setToField("Start with selecting source, midi and destination folders.<br>"
					+ "<font color='red'>Destination folder must be empty!</font>"
					+ "<br>MIDI folder is optional. It is used when midi cannot be found,"
					+ " then it looks in that folder before asking for location."
					+ "<br>When exporting it will use your Maestro settings for filename, partname etc etc."
					+ "<br>Close Maestro while this app runs.");
			SwingUtilities.invokeLater(() -> {
				frame.getBtnStartExport().setEnabled(true);
				frame.getBtnCancelExport().setEnabled(false);
                enableForceCheckBoxes();
				frame.setBtnDestAutoEnabled(true);
				frame.setBtnMIDIEnabled(true);
				frame.setBtnSourceAutoEnabled(true);
				frame.setSaveMSXEnabled(true);
				frame.setSaveMSXabcEnabled(true);
				frame.setSaveMSXtimingEnabled(true);
				frame.setTabsEnabled(true);
				frame.setRecursiveCheckBoxEnabled(true);
			});
			setProgress(0);
			return;
		}
		
		setToField("Keep Maestro closed while this app runs.<br><br>Exporting in progress");

		partAutoNumberer = new PartAutoNumberer(prefs.node("partAutoNumberer"));
		partNameTemplate = new PartNameTemplate(prefs.node("partNameTemplate"));
		exportFilenameTemplate = new ExportFilenameTemplate(prefs.node("exportFilenameTemplate"));
		instrNameSettings = new InstrNameSettings(prefs.node("instrNameSettings"));
		saveSettings = new SaveAndExportSettings(prefs.node("saveAndExportSettings"));
		miscSettings = new MiscSettings(prefs.node("miscSettings"), true);

        skippedProjects = new ArrayList<>();
        //highCandidates = new ArrayList<>();
		setProgress(0);
		cancel = false;
		if (!frame.getRecursiveCheckBoxSelected()) {
			File[] projects = sourceFolderAuto.listFiles(new MsxFileFilter());
			
			appendToField("<br>Found " + projects.length + " project files.<br>");
			
			progressFactor = 1000.0d / projects.length;
			exportCount = 0;
			
			for (File project : projects) {
				if (cancel) {
					break;
				}
				try {
					exportProject(project);
				} catch (ParseException e) {
                    log.warning(project.getName()+": "+e.getMessage());
					appendToField("<br><font color='red'>"+e.toString()+"</font>");
                    skippedProjects.add(project);
				}
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
        if (!skippedProjects.isEmpty()) {
            appendToField("<br><br>Skipped/failed " + skippedProjects.size() + " project files:");
            for (File f : skippedProjects) {
                appendToField("<br><font color='orange'>" + f.getParent() + File.separator + f.getName()+"</font>");
            }
        }
        /*
        if (!highCandidates.isEmpty()) {
            System.out.println("High candidates " + highCandidates.size() + " project files:");
            for (File f : highCandidates) {
                System.out.println(f.getParent() + File.separator + f.getName());
            }
        }
         */
		if (!cancel) {
			setProgress(1000);
			appendToField("<br><br>Exports finished. ");//+com.digero.maestro.abc.PolyphonyHistogram.successes
			log.info("Auto exports finished outputting "+totalExportCount+" files");
		} else {
			appendToField("<br><br>Exports cancelled.");
			log.info("Auto exports cancelled");
		}
        inProgress = false;
		SwingUtilities.invokeLater(() -> {
			frame.getBtnStartExport().setEnabled(true);
			frame.getBtnCancelExport().setEnabled(false);
            enableForceCheckBoxes();
			frame.setBtnDestAutoEnabled(true);
			frame.setBtnMIDIEnabled(true);
			frame.setBtnSourceAutoEnabled(true);
			frame.setSaveMSXEnabled(true);
			frame.setSaveMSXabcEnabled(true);
			frame.setSaveMSXtimingEnabled(true);
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
	
	    @Override
	    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
	        throws IOException
	    {
	        Objects.requireNonNull(dir);
	        Objects.requireNonNull(attrs);
	        if (dir.getFileName().toString().startsWith(".")) {
	        	return FileVisitResult.SKIP_SUBTREE;
	        }
	        return FileVisitResult.CONTINUE;
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
			log.warning(exc.getMessage());
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
						log.warning(file.getFileName()+": "+e.getMessage());
						appendToField("<br><font color='red'>"+e.toString()+"</font>");
                        skippedProjects.add(file.toFile());
					}
					exportCount++;
					setProgress((int) (exportCount * progressFactor));
		        } else {
		            System.out.format("Ignoring: %s ", file);
		        }
	    	}
	        return CONTINUE;
	    }
	    
	    @Override
	    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
	        throws IOException
	    {
	        Objects.requireNonNull(dir);
	        Objects.requireNonNull(attrs);
	        if (dir.getFileName().toString().startsWith(".")) {
	        	return FileVisitResult.SKIP_SUBTREE;
	        }
	        return FileVisitResult.CONTINUE;
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
	        log.warning(exc.getMessage());
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
		AbcSong abcSong = new AbcSong(project, partAutoNumberer, partNameTemplate, exportFilenameTemplate,
				instrNameSettings, openFileResolver, miscSettings, frame.getSaveMSXSelected(), saveSettings, true);
    /*
        if (abcSong.highCandidate) {
            highCandidates.add(project);
        }

     */

		boolean timingModified = false;
		boolean oldMix = abcSong.isMixTiming();
		boolean oldOrganic = abcSong.isOrganic();
		boolean oldOrganic2 = abcSong.isOrganic2();
        if (frame.getForceLegacyTimingSelected()) {
            if (oldMix || oldOrganic) timingModified = frame.getSaveMSXtimingSelected();
            abcSong.setMixTiming(false);
            abcSong.setOrganic(false);
        } else if (frame.getForceMixTimingSelected()) {
			if (oldOrganic || !oldMix) timingModified = frame.getSaveMSXtimingSelected();
			abcSong.setMixTiming(true);
            abcSong.setOrganic(false);
		} else if (frame.getForceOrganicSelected()) {
			if (!oldOrganic || oldOrganic2) timingModified = frame.getSaveMSXtimingSelected();
			abcSong.setOrganic(true);
            abcSong.setOrganic2(false);
		} else if (frame.getForceOrganic2Selected()) {
			if (!oldOrganic || !oldOrganic2) timingModified = frame.getSaveMSXtimingSelected();
            abcSong.setOrganic(true);
			abcSong.setOrganic2(true);
		}
		
		abcSong.storeNewExportFile = frame.getSaveMSXabcSelected();
		abcSong.setSkipSilenceAtStart(saveSettings.skipSilenceAtStart);
		abcSong.setDeleteMinimalNotes(saveSettings.deleteMinimalNotes);
		abcSong.setBadger(miscSettings.showBadger);
		StringCleaner.cleanABC = saveSettings.convertABCStringsToBasicAscii;

		File exportFile = abcSong.getExportFile();
		String fileName = "mySong"+Util.ABC_FILE_EXTENSION;

		// Always regenerate setting from pattern export is highest precedent
		if (exportFilenameTemplate.shouldRegenerateFilename()) {
			fileName = exportFilenameTemplate.formatName();
		} else if (exportFile != null) // else use abc filename if exists already
		{
			fileName = exportFile.getName();
		} else if (abcSong.getProjectFile() != null) // else use msx filename if exists already
		{
			fileName = abcSong.getProjectFile().getName();
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
		fileName += Util.ABC_FILE_EXTENSION;
		
		File finalFolder = getTreeFolder(sourceFolderAuto, destFolderAuto, project);

		exportFile = new File(finalFolder, fileName);
		String finalName = exportFile.getName();
		dot = finalName.lastIndexOf('.');
		if (dot > 0)
			finalName = finalName.substring(0, dot);
		int n = 1;
		while (exportFile.exists()) {
			n++;
			exportFile = new File(exportFile.getParentFile(), finalName + " (" + n + ")"+Util.ABC_FILE_EXTENSION);
		}
		finalFolder.mkdirs();// for recursive exporting we need the folders to exist.

		abcSong.exportAbc(exportFile);
		int maxPoly = abcSong.getMaxPartPoly();
		if (maxPoly > 6) appendToField("<br><font color='orange'>&nbsp;&nbsp;part polyphony max was "+maxPoly+".</font>");
		if ((abcSong.getExportFile() == null || exportFile.compareTo(abcSong.getExportFile()) != 0) && frame.getSaveMSXabcSelected()) {
			projectModified = true;
		}
		abcSong.setExportFile(exportFile);
		
		if (projectModified && !frame.getSaveMSXtimingSelected()) {
			// Don't save forced timing changes to project file
			abcSong.setMixTiming(oldMix);
			abcSong.setOrganic(oldOrganic);
			abcSong.setOrganic2(oldOrganic2);
		}
		
		if (timingModified || ( projectModified && (frame.getSaveMSXSelected() || frame.getSaveMSXabcSelected()) )) {
			try {
				XmlUtil.saveDocument(abcSong.saveToXml(), abcSong.getProjectFile());
				appendToField("<br>&nbsp;&nbsp;msx saved.");
			} catch (FileNotFoundException e) {
				appendToField("<br><font color='red'>&nbsp;&nbsp;msx saving failed.</font>");
			} catch (IOException | TransformerException e) {
				appendToField("<br><font color='red'>&nbsp;&nbsp;msx saving failed.</font>");
			}				
		}
		
		if (testIfOutputIsValid) {
			try {
				//
				// Test if the file is valid
				//
				List<FileAndData> data = new ArrayList<>();
				data.add(new FileAndData(exportFile, AbcToMidi.readLines(exportFile)));
				AbcInfo info = new AbcInfo();
				AbcToMidi.Params params = new AbcToMidi.Params(data);
				params.useLotroInstruments = true;
				params.abcInfo = info;
				params.enableLotroErrors = true;
				params.stereo = false;
				params.generateRegions = true;
				AbcToMidi.convert(params);
			} catch (LotroParseException e) {
				JOptionPane.showMessageDialog(frame, e.getMessage(), exportFile.getName()+": Error parsing ABC", JOptionPane.ERROR_MESSAGE);
			} catch (ParseException e) {
				JOptionPane.showMessageDialog(frame, e.getMessage(), exportFile.getName()+": Error reading ABC", JOptionPane.ERROR_MESSAGE);
			}
		}
		
		
		appendToField("<br>&nbsp;&nbsp;as " + exportFile.getName());
	}

	/**
	 * 
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
	
	private ActionListener getOrganicActionListener() {
		return new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
                enableForceCheckBoxes();
			}			
		};
	}

    private void enableForceCheckBoxes() {
        if (inProgress) return;
        if (frame.getForceOrganicSelected()) {
            frame.setForceMixTimingEnabled(false);
            frame.setForceLegacyTimingEnabled(false);
            frame.setForceOrganic2Enabled(false);
            frame.setForceOrganicEnabled(true);
        } else if (frame.getForceOrganic2Selected()) {
            frame.setForceMixTimingEnabled(false);
            frame.setForceLegacyTimingEnabled(false);
            frame.setForceOrganicEnabled(false);
            frame.setForceOrganic2Enabled(true);
        } else if (frame.getForceMixTimingSelected()) {
            frame.setForceOrganicEnabled(false);
            frame.setForceLegacyTimingEnabled(false);
            frame.setForceOrganic2Enabled(false);
            frame.setForceMixTimingEnabled(true);
        } else if (frame.getForceLegacyTimingSelected()) {
            frame.setForceOrganicEnabled(false);
            frame.setForceMixTimingEnabled(false);
            frame.setForceOrganic2Enabled(false);
            frame.setForceLegacyTimingEnabled(true);
        } else {
            frame.setForceOrganicEnabled(true);
            frame.setForceMixTimingEnabled(true);
            frame.setForceLegacyTimingEnabled(true);
            frame.setForceOrganic2Enabled(true);
        }
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
	private final FileResolver openFileResolver = new FileResolver() {
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
				if (newNestedMidi != null) {
					//System.out.println("New already="+String.valueOf(newNestedMidi.getPath()));
				} else {
					//System.out.println("NULL - New already=");
				}
			}
			if (original.equals(newNestedMidi)) {
				if (neverLocateMidi) return null;
				message += "\n\nWould you like to try to locate the file?";
				return resolveHelper(oldMidi, message);
			} else if (original.equals(newMidi)) {
				if (newNestedMidi != null) {
					//System.out.println("return newNestedMidi");
					projectModified = true;
					return newNestedMidi;
				}
				//System.out.println("newNestedMidi == null");
				if (neverLocateMidi) return null;
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
					jfc.setDialogTitle("Open missing MIDI/ABC");
					jfc.setFileFilter(new ExtensionFileFilter("MIDI and ABC files",
							Util.ABC_FILE_EXTENSION_NO_DOT,Util.TXT_FILE_EXTENSION_NO_DOT,
							Util.MID_FILE_EXTENSION_NO_DOT,Util.MIDI_FILE_EXTENSION_NO_DOT,
							Util.KAR_FILE_EXTENSION_NO_DOT));
					if (original != null)
						jfc.setSelectedFile(original);
		
					try {
						SwingUtilities.invokeAndWait(() -> {
							result = jfc.showOpenDialog(frame);
						});
					} catch (Exception e) {
						log.severe(e.toString());
						appendToField("<br><font color='red'>"+e.toString()+"</font>");
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
				log.severe(e.toString());
				appendToField("<br><font color='red'>"+e.toString()+"</font>");
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
