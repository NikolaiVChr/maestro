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
import java.util.*;
import java.util.Timer;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.xml.transform.TransformerException;

import com.aifel.abctools.AbcTools.MsxFileFilter;
import com.aifel.abctools.AbcTools.FolderFileFilter;
import com.digero.common.abc.StringCleaner;
import com.digero.common.abctomidi.AbcInfo;
import com.digero.common.abctomidi.AbcToMidi;
import com.digero.common.abctomidi.FileAndData;
import com.digero.common.util.ExtensionFileFilter;
import com.digero.common.util.LotroFileParseException;
import com.digero.common.util.FileParseException;
import com.digero.common.util.Util;
import com.digero.common.util.WarningHandler;
import com.digero.maestro.MaestroMain;
import com.digero.maestro.abc.AbcSong;
import com.digero.maestro.abc.ExportFilenameTemplate;
import com.digero.maestro.abc.PartAutoNumberer;
import com.digero.maestro.abc.PartNameTemplate;
import com.digero.maestro.midi.Chord;
import com.digero.maestro.util.FileResolver;
import com.digero.maestro.util.XmlUtil;
import com.digero.maestro.view.InstrNameSettings;
import com.digero.maestro.view.MiscSettings;
import com.digero.maestro.view.SaveAndExportSettings;
import org.jetbrains.annotations.NotNull;

public class AutoExporter implements WarningHandler {
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
	private final AtomicInteger exportCount = new AtomicInteger(0);
	private int totalExportCount = 0;
	private volatile int result = 0;
	private volatile String textAuto = "";
    private volatile boolean txtFieldClear = false;
	private boolean txtFieldDirty = false;
	private final Object txtFieldMutex = new Object();

    private final List<File> skippedProjects = Collections.synchronizedList(new ArrayList<>());
    //private List<File> highCandidates = new ArrayList<>();
    private final Object fileNamingLock = new Object();
	private volatile int progressInt = 0;
	private volatile boolean txtFieldPrimedForUpdate = false;
	private final AbcTools main;
	private final Preferences autoPrefs;
	private volatile boolean cancel = false;
    private volatile boolean inProgress = false;
	
	PartAutoNumberer partAutoNumberer;
	PartNameTemplate partNameTemplate;
	ExportFilenameTemplate exportFilenameTemplate;
	InstrNameSettings instrNameSettings;
	SaveAndExportSettings saveSettings;
	MiscSettings miscSettings;

    private final Map<String, Boolean> proceedAllWarningMap = Collections.synchronizedMap(new HashMap<>());
    private final AtomicReference<FileResolverAsync.MissingFileDecision> missingFileDecision =
            new AtomicReference<>(FileResolverAsync.MissingFileDecision.ASK);
    private final Object modalLock = new Object();
	
	// For testing:
	private static final boolean neverLocateMidi = false;// for testing
	private static final boolean testIfOutputIsValid = true;// makes it slower
	
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
		setToField("<p>Start with selecting source, midi and destination folders.</p>"
				+ "<p>Destination folder must be empty!</p>"
				+ "<p>MIDI folder is optional. It is used when midi cannot be found,"
				+ " then it looks in that folder before asking for location.</p>"
				+ "<p>When exporting it will use your Maestro settings for filename, partname etc etc.</p>"
				+ "<p>Close Maestro while this app runs.</p>");
		
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
            frame.setVolumeMethodEnabled(false);
			frame.getBtnStartExport().addActionListener(getStartExportActionListener());
			frame.getBtnCancelExport().addActionListener(getCancelExportActionListener());
			frame.getBtnDestAuto().addActionListener(getDestAutoActionListener());
			frame.getBtnMIDI().addActionListener(getMIDIAutoActionListener());
			frame.getBtnSourceAuto().addActionListener(getSourceAutoActionListener());
			frame.addForceOrganicActionListener(getOrganicActionListener());
            frame.addForceOrganic2ActionListener(getOrganicActionListener());
            frame.addForceMixActionListener(getOrganicActionListener());
            frame.addForceLegacyActionListener(getOrganicActionListener());
            frame.addForceVolumeMethodListener(getVolumeActionListener());
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
					appendToField("<p><font color='red'>"+ioe.toString()+"</font></p>");
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
                        frame.setSaveMSXvolumeEnabled(true);
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
        proceedAllWarningMap.clear();
        missingFileDecision.set(FileResolverAsync.MissingFileDecision.ASK);
		SwingUtilities.invokeAndWait(() -> {
			refreshAuto();
			frame.getBtnStartExport().setEnabled(false);
			frame.getBtnCancelExport().setEnabled(true);
			frame.setForceMixTimingEnabled(false);
            frame.setForceLegacyTimingEnabled(false);
			frame.setForceOrganicEnabled(false);
			frame.setForceOrganic2Enabled(false);
            frame.setForceVolumeMethodEnabled(false);
            frame.setVolumeMethodEnabled(false);
			frame.setBtnDestAutoEnabled(false);
			frame.setBtnMIDIEnabled(false);
			frame.setBtnSourceAutoEnabled(false);
			frame.setSaveMSXEnabled(false);
			frame.setSaveMSXtimingEnabled(false);
			frame.setSaveMSXabcEnabled(false);
            frame.setSaveMSXvolumeEnabled(false);
			frame.setTabsEnabled(false);
			frame.setRecursiveCheckBoxEnabled(false);
		});
		// Test if dest is empty
		if (destFolderAuto.listFiles(new FolderFileFilter()).length != 0) {
            inProgress = false;
			setToField("<p>Start with selecting source, midi and destination folders.</p><p>"
					+ "<font color='red'>Destination folder must be empty!</font></p>"
					+ "<p>MIDI folder is optional. It is used when midi cannot be found,"
					+ " then it looks in that folder before asking for location.</p>"
					+ "<p>When exporting it will use your Maestro settings for filename, partname etc etc.</p>"
					+ "<p>Close Maestro while this app runs.</p>");
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
                frame.setSaveMSXvolumeEnabled(true);
				frame.setTabsEnabled(true);
				frame.setRecursiveCheckBoxEnabled(true);
			});
			setProgress(0);
			return;
		}
		
		setToField("<p>Keep Maestro closed while this app runs.</p><p></p><p>Exporting in progress</p>");

		partAutoNumberer = new PartAutoNumberer(prefs.node("partAutoNumberer"));
		partNameTemplate = new PartNameTemplate(prefs.node("partNameTemplate"));
		exportFilenameTemplate = new ExportFilenameTemplate(prefs.node("exportFilenameTemplate"));
		instrNameSettings = new InstrNameSettings(prefs.node("instrNameSettings"));
		saveSettings = new SaveAndExportSettings(prefs.node("saveAndExportSettings"));
		miscSettings = new MiscSettings(prefs.node("miscSettings"), true);

        StringCleaner.cleanABC = saveSettings.convertABCStringsToBasicAscii;

        // Output current Maestro settings that affects abc exporting:
        appendToField("<p></p>");
        appendToField("<p>Clean output for non-ascii chars: "+Boolean.toString(StringCleaner.cleanABC)+"</p>");
        appendToField("<p>Remove initial silence: "+Boolean.toString(saveSettings.skipSilenceAtStart)+"</p>");
        appendToField("<p>Reduced file size: "+Boolean.toString(saveSettings.reducedFilesize)+"</p>");
        if (!frame.getForceLegacyTimingSelected() && !frame.getForceMixTimingSelected()) {
            appendToField("<p>Organic allowed part polyphony above 6: " + Boolean.toString(saveSettings.useRestsInChords) + "</p>");
        }
        if (!frame.getForceLegacyTimingSelected() && !frame.getForceOrganicSelected() && !frame.getForceOrganic2Selected()) {
            appendToField("<p>Mix timings allowed to delete minimal notes: "+Boolean.toString(saveSettings.deleteMinimalNotes)+"</p>");
        }
        appendToField("<p>Output extended songbook metainfo: "+Boolean.toString(miscSettings.showBadger)+"</p>");
        boolean pattern = false;
        if (exportFilenameTemplate.shouldRegenerateFilename()) {
            appendToField("<p>ABC filenames will all be generated by pattern.</p>");
            pattern = true;
        } else {
            if (prefs.node("exportFilenameTemplate").getBoolean("exportFilenamePatternEnabled", false)) {
                appendToField("<p>ABC filenames will be last exported filename, if that's not available then generated by pattern.</p>");
                pattern = true;
            } else {
                appendToField("<p>ABC filenames will be last exported filename, if that's not available then generated from source midi/abc.</p>");
            }
        }
        if (pattern) appendToField("<p>&nbsp;&nbsp;The filename pattern is "+prefs.node("exportFilenameTemplate").get("exportFilenamePattern", "$PartCount - $SongTitle")+".abc</p>");
        appendToField("<p>Part title pattern is "+prefs.node("partNameTemplate").get("partNamePattern", "$SongTitle ($SongLength) - $PartName")+"</p>");
        appendToField("<p></p>");

        skippedProjects.clear();
        //highCandidates = new ArrayList<>();
		setProgress(0);
		cancel = false;
        List<Path> filesToProcess;
        if (!frame.getRecursiveCheckBoxSelected()) {
            File[] projects = sourceFolderAuto.listFiles(new MsxFileFilter());
            filesToProcess = (projects != null)
                    ? Arrays.stream(projects).map(File::toPath).toList()
                    : List.of();
        } else {
            try (var paths = Files.walk(sourceFolderAuto.toPath())) {
                filesToProcess = paths
                        .filter(path -> !path.getFileName().toString().startsWith("."))
                        .filter(Files::isRegularFile)
                        .filter(path -> new MsxFileFilter().accept(path.toFile()))
                        .toList();
            }
        }
		if (!filesToProcess.isEmpty()) {
            int total = filesToProcess.size();
            appendToField("<p>Found " + total + " project files.</p><p></p>");

            progressFactor = 1000.0d / total;
            exportCount.set(0);

            try (ForkJoinPool customPool = new ForkJoinPool(8)) {
                customPool.submit(() -> {
                    filesToProcess.parallelStream().forEach(file -> {
                        if (cancel) {
                            return;
                        }

                try {
                    // thread-safe
                    exportProject(file.toFile());
                } catch (Throwable e) {
                    log.log(Level.WARNING, file.getFileName().toString(), e);

                    skippedProjects.add(file.toFile());

                    appendToField("<p><font color='red'>" + e.toString() + "</font></p>");
                }

                        setProgress((int) (exportCount.incrementAndGet() * progressFactor));
                    });
                }).get(); // Wait for completion
            } catch (Throwable e) {
                log.log(Level.WARNING, "Parallel export failed", e);
                appendToField("<p></p><p>An error cancelled exporting: "+e+"</p>");
            }
        }
        if (!skippedProjects.isEmpty()) {
            appendToField("<p></p><p>Skipped/failed " + skippedProjects.size() + " project files:</p>");
            for (File f : skippedProjects) {
                appendToField("<p><font color='orange'>" + f.getParent() + File.separator + f.getName()+"</font></p>");
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
			appendToField("<p></p><p>Exports finished. </p>");//+com.digero.maestro.abc.PolyphonyHistogram.successes
			log.info("Auto exports finished outputting "+totalExportCount+" files");
		} else {
			appendToField("<p></p><p>Exports cancelled.</p>");
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
            frame.setSaveMSXvolumeEnabled(true);
			frame.setTabsEnabled(true);
			frame.setRecursiveCheckBoxEnabled(true);
		});
	}

    @Deprecated
	public class CountFiles extends SimpleFileVisitor<Path> {
		
		MsxFileFilter f = new MsxFileFilter();
		
	    @Override
	    public @NotNull FileVisitResult visitFile(Path file, BasicFileAttributes attr) {
	        if (attr.isRegularFile() && f.accept(file.toFile())) {
	        	totalExportCount++;
	        }
	        return CONTINUE;
	    }
	
	    @Override
	    public @NotNull FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
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
	    public @NotNull FileVisitResult postVisitDirectory(Path dir,
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
	    public @NotNull FileVisitResult visitFileFailed(Path file,
                                                        IOException exc) {
			log.warning(exc.getMessage());
	        return CONTINUE;
	    }
	}

    @Deprecated
	public class ProcessFiles extends SimpleFileVisitor<Path> {

		MsxFileFilter f = new MsxFileFilter();
		
	    @Override
	    public @NotNull FileVisitResult visitFile(Path file, BasicFileAttributes attr) {
	    	if (cancel) return TERMINATE;
	    	if (f.accept(file.toFile())) {
		        if (attr.isSymbolicLink()) {
		            System.out.format("Ignoring symbolic link: %s ", file);
		        } else if (attr.isRegularFile()) {
		        	try {
						exportProject(file.toFile());
					} catch (Exception e) {
						log.warning(file.getFileName()+": "+e.getMessage());
						appendToField("<p></p><p><font color='red'>"+e.toString()+"</font></p>");
                        skippedProjects.add(file.toFile());
					}
					setProgress((int) (exportCount.incrementAndGet() * progressFactor));
		        } else {
		            System.out.format("Ignoring: %s ", file);
		        }
	    	}
	        return CONTINUE;
	    }
	    
	    @Override
	    public @NotNull FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
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
	    public @NotNull FileVisitResult postVisitDirectory(Path dir,
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
	    public @NotNull FileVisitResult visitFileFailed(Path file,
                                                        IOException exc) {
	        log.warning(exc.getMessage());
	        return CONTINUE;
	    }
	}

	private void setProgress(final int progress) {
		progressInt = progress;
	}

    /**
     * Do not use <br> wrap each line in <p></p> instead
     */
	private void appendToField(String txt) {
		synchronized(txtFieldMutex) {
			txtFieldDirty = true;
			textAuto += txt;
		}
	}

    /**
     * Do not use <br> wrap each line in <p></p> instead
     */
	private void setToField(String txt) {
		synchronized(txtFieldMutex) {
			txtFieldDirty = true;
            txtFieldClear = true;
			textAuto = txt;
		}
	}

	private void updateField() {
		synchronized(txtFieldMutex) {
			if (txtFieldDirty && !txtFieldPrimedForUpdate) {
				txtFieldPrimedForUpdate = true;
                final String textToProcess = textAuto;
                final boolean clear = txtFieldClear;
                textAuto = "";
                txtFieldClear = false;
                txtFieldDirty = false;
				SwingUtilities.invokeLater(() -> {
                    try {
                        if (clear) {
                            frame.getTxtAutoExport().setText(textToProcess);
                        } else {
                            HTMLEditorKit kit = (HTMLEditorKit) frame.getTxtAutoExport().getEditorKit();
                            HTMLDocument doc = (HTMLDocument) frame.getTxtAutoExport().getDocument();
                            Element body = doc.getElement("body");
                            if (body != null) {
                                kit.insertHTML(doc, body.getEndOffset() - 1, textToProcess, 0, 0, null);
                            } else {
                                // Fallback
                                kit.insertHTML(doc, doc.getLength(), textToProcess, 0, 0, null);
                            }
                        }
                    } catch (BadLocationException | IOException e) {
                        log.warning(e.getMessage());
                    }

                    synchronized(txtFieldMutex) {
						txtFieldPrimedForUpdate = false;
					}
				});
			}
		}
	}

    private void updateProgress() {
        SwingUtilities.invokeLater(() -> {
            frame.setProgressBarValue(progressInt);
        });
    }

    /**
     * Class to hold thread-specific fields.
     */
    private class ProjectInfo {
        boolean projectModified;
        File newNestedMidi;
        File oldMidi;
        File nestedProject;
        String appendText;
    }

	private void exportProject(File project) throws Exception {
        ProjectInfo pInfo = new ProjectInfo();
        pInfo.projectModified = false;
        pInfo.newNestedMidi = null;
        pInfo.oldMidi = null;
        pInfo.nestedProject = project;
        pInfo.appendText = "<p>Exporting " + project.getName()+"</p>";

        FileResolverAsync openFileResolver = new FileResolverAsync();
        openFileResolver.pInfo = pInfo;

		AbcSong abcSong = new AbcSong(project, partAutoNumberer, partNameTemplate, exportFilenameTemplate,
				instrNameSettings, openFileResolver, miscSettings, frame.getSaveMSXSelected(), saveSettings, true, this);
    /*
        if (abcSong.highCandidate) {
            highCandidates.add(project);
        }

     */

		boolean timingModified = false;
        boolean dynaModified = false;
		boolean oldMix = abcSong.isMixTiming();
		boolean oldOrganic = abcSong.isOrganic();
		boolean oldOrganic2 = abcSong.isOrganic2();
        Chord.CalcDynamics oldDyna = abcSong.dynamicsMethod;
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
        if (frame.getForceVolumeMethodSelected()) {
            if (oldDyna != frame.getVolumeMethodSelected()) {
                dynaModified = frame.isSaveMSXvolumeSelected();
            }
            abcSong.dynamicsMethod = frame.getVolumeMethodSelected();
        }
		
		abcSong.storeNewExportFile = frame.getSaveMSXabcSelected();
		abcSong.setSkipSilenceAtStart(saveSettings.skipSilenceAtStart);
        abcSong.setReducedFilesize(saveSettings.reducedFilesize);
		abcSong.setDeleteMinimalNotes(saveSettings.deleteMinimalNotes);
        abcSong.setUseRestsInChords(saveSettings.useRestsInChords);
		abcSong.setBadger(miscSettings.showBadger);

		File exportFile = abcSong.getExportFile();
		String fileName = "mySong"+Util.ABC_FILE_EXTENSION;

		// Always regenerate setting from pattern export is highest precedent
		if (exportFilenameTemplate.shouldRegenerateFilename()) {
			fileName = exportFilenameTemplate.formatName(abcSong);
		} else if (exportFile != null) // else use abc filename if exists already
		{
			fileName = exportFile.getName();
		} else if (abcSong.getProjectFile() != null) // else use msx filename if exists already
		{
			fileName = abcSong.getProjectFile().getName();
		} else if (exportFilenameTemplate.isEnabled()) // else use pattern if usage is enabled
		{
			fileName = exportFilenameTemplate.formatName(abcSong);
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

        synchronized (fileNamingLock) {
            String finalName = exportFile.getName();
            dot = finalName.lastIndexOf('.');
            if (dot > 0)
                finalName = finalName.substring(0, dot);
            int n = 1;
            while (exportFile.exists()) {
                n++;
                exportFile = new File(exportFile.getParentFile(), finalName + " (" + n + ")" + Util.ABC_FILE_EXTENSION);
            }
            finalFolder.mkdirs();// for recursive exporting we need the folders to exist.
        }

        try {
            abcSong.exportAbc(exportFile, AbcTools.APP_NAME);
        } catch (Throwable t) {
            exportFile.delete();
            throw t;
        }
		int maxPoly = abcSong.getMaxPartPoly();
		if (maxPoly > 6) pInfo.appendText += "<p><font color='orange'>&nbsp;&nbsp;part polyphony max was "+maxPoly+".</font></p>";
		if ((abcSong.getExportFile() == null || exportFile.compareTo(abcSong.getExportFile()) != 0) && frame.getSaveMSXabcSelected()) {
			pInfo.projectModified = true;
		}
		abcSong.setExportFile(exportFile);
		
		if (!frame.getSaveMSXtimingSelected()) {
			// Don't save forced timing changes to project file
			abcSong.setMixTiming(oldMix);
			abcSong.setOrganic(oldOrganic);
			abcSong.setOrganic2(oldOrganic2);
		}

        if (!frame.isSaveMSXvolumeSelected()) {
            // Don't save forced dynamics to project file
            abcSong.dynamicsMethod = oldDyna;
        }
		
		if (dynaModified || timingModified || ( pInfo.projectModified && (frame.getSaveMSXSelected() || frame.getSaveMSXabcSelected()) )) {
			try {
				XmlUtil.saveDocument(abcSong.saveToXml(), abcSong.getProjectFile());
                pInfo.appendText += "<p>&nbsp;&nbsp;msx saved.</p>";
			} catch (FileNotFoundException e) {
                pInfo.appendText += "<p><font color='red'>&nbsp;&nbsp;msx saving failed.</font></p>";
			} catch (IOException | TransformerException e) {
                pInfo.appendText += "<p><font color='red'>&nbsp;&nbsp;msx saving failed.</font></p>";
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
				params.stereo = 0;
				params.generateRegions = false;//only needed in abc player
				AbcToMidi.convert(params);
			} catch (LotroFileParseException e) {
				JOptionPane.showMessageDialog(frame, e.getMessage(), exportFile.getName()+": Error parsing ABC", JOptionPane.ERROR_MESSAGE);
			} catch (FileParseException e) {
				JOptionPane.showMessageDialog(frame, e.getMessage(), exportFile.getName()+": Error reading ABC", JOptionPane.ERROR_MESSAGE);
			}
		}


        pInfo.appendText += "<p>&nbsp;&nbsp;as " + exportFile.getName()+"</p>";
        appendToField(pInfo.appendText);
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

    private ActionListener getVolumeActionListener() {
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
        frame.setVolumeMethodEnabled(frame.getForceVolumeMethodSelected());
        frame.setForceVolumeMethodEnabled(true);
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
	private class FileResolverAsync implements FileResolver {
		private File newMidi;
        public ProjectInfo pInfo = null;

		@Override
		public File locateFile(File original, String message) {
			//System.out.println("\nOriginal="+original.getPath());
			if (pInfo.oldMidi == null) pInfo.oldMidi = original;// To ensure message on screen shows midi from project file.
			newMidi = new File(midiFolderAuto, original.getName());
			if (pInfo.newNestedMidi == null && frame.getRecursiveCheckBoxSelected()) {
				try {
					File finalFolder = getTreeFolderMidi(pInfo.nestedProject);
					if (finalFolder != null) {
                        pInfo.newNestedMidi = new File(finalFolder, original.getName());
						//System.out.println("New="+newNestedMidi.getPath());
					} else {
						//System.out.println("finalFolder == null");
					}
				} catch (IOException e) {
                    pInfo.newNestedMidi = null;
					//System.out.println("IO");
				}
			} else {
				if (pInfo.newNestedMidi != null) {
					//System.out.println("New already="+String.valueOf(newNestedMidi.getPath()));
				} else {
					//System.out.println("NULL - New already=");
				}
			}
			if (original.equals(pInfo.newNestedMidi)) {
				if (neverLocateMidi) return null;
				message += "\n\nWould you like to try to locate the file?";
				return resolveHelper(pInfo.oldMidi, message);
			} else if (original.equals(newMidi)) {
				if (pInfo.newNestedMidi != null) {
					//System.out.println("return newNestedMidi");
                    pInfo.projectModified = true;
					return pInfo.newNestedMidi;
				}
				//System.out.println("newNestedMidi == null");
				if (neverLocateMidi) return null;
				message += "\n\nWould you like to try to locate the file?";
				return resolveHelper(pInfo.oldMidi, message);
			}
			//System.out.println("return newMidi="+newMidi.getPath());
            pInfo.projectModified = true;
			return newMidi;
		}

		@Override
		public File resolveFile(File original, String message) {
			message += "\n\nWould you like to pick a different file?";
			return resolveHelper(original, message);
		}

		private File resolveHelper(File original, String message) {
            synchronized(modalLock) {
                if (missingFileDecision.get() == MissingFileDecision.SKIP_ALL) {
                    return null;
                }
                if (missingFileDecision.get() == MissingFileDecision.CANCEL_ALL) {
                    cancel = true;
                    return null;
                }
                if (cancel) {
                    return null;
                }

                final int[] userChoice = new int[1];
                final boolean[] applyToAll = new boolean[1];

                try {
                    SwingUtilities.invokeAndWait(() -> {
                        String[] options = {"Locate File...", "Skip", "Cancel"};
                        JCheckBox skipAllCheckbox = new JCheckBox("Skip all missing files");
                        Object[] params = {message, skipAllCheckbox};
                        userChoice[0] = JOptionPane.showOptionDialog(
                                frame, params, "Failed to open file",
                                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                                null, options, options[0]
                        );
                        if (userChoice[0] == 1) { // skip
                            applyToAll[0] = skipAllCheckbox.isSelected();
                        } else {
                            applyToAll[0] = false;
                        }
                    });
                } catch (InvocationTargetException | InterruptedException e) {
                    log.log(Level.SEVERE, "Something went wrong when asking user about locate midi", e);
                    pInfo.appendText += "<p><font color='red'>" + e.toString() + "</font></p>";
                }

                File alternateFile = null;
                switch (userChoice[0]) {
                    case 0:
                        JFileChooser jfc = new JFileChooser();
                        jfc.setDialogTitle("Open missing MIDI/ABC");
                        jfc.addChoosableFileFilter(
                                new ExtensionFileFilter("MIDI",
                                        Util.MID_FILE_EXTENSION_NO_DOT,
                                        Util.MIDI_FILE_EXTENSION_NO_DOT, Util.KAR_FILE_EXTENSION_NO_DOT));
                        jfc.addChoosableFileFilter(
                                new ExtensionFileFilter("ABC",
                                        Util.ABC_FILE_EXTENSION_NO_DOT,
                                        Util.TXT_FILE_EXTENSION_NO_DOT));
                        jfc.setFileFilter(new ExtensionFileFilter("MIDI and ABC files",
                                Util.ABC_FILE_EXTENSION_NO_DOT, Util.TXT_FILE_EXTENSION_NO_DOT,
                                Util.MID_FILE_EXTENSION_NO_DOT, Util.MIDI_FILE_EXTENSION_NO_DOT,
                                Util.KAR_FILE_EXTENSION_NO_DOT));
                        jfc.setAcceptAllFileFilterUsed(false);
                        if (original != null)
                            jfc.setSelectedFile(original);

                        try {
                            SwingUtilities.invokeAndWait(() -> {
                                result = jfc.showOpenDialog(frame);
                            });
                        } catch (InvocationTargetException | InterruptedException e) {
                            log.log(Level.SEVERE, "Something went wrong when asking user to locate midi", e);
                            pInfo.appendText += "<p><font color='red'>" + e.toString() + "</font></p>";
                        }
                        if (result == JFileChooser.APPROVE_OPTION) {
                            alternateFile = jfc.getSelectedFile();
                            pInfo.projectModified = true;
                        }
                        break;
                    case 1: // Skip
                        if (applyToAll[0]) {
                            missingFileDecision.set(MissingFileDecision.SKIP_ALL);
                        }
                        // alternateFile is null
                        break;
                    case 2: // Cancel
                    case JOptionPane.CLOSED_OPTION:
                    default:
                        cancel = true;
                        missingFileDecision.set(MissingFileDecision.CANCEL_ALL);
                        // alternateFile is null
                        break;
                }

                return alternateFile;
            }
		}

        private static enum MissingFileDecision {
            ASK,
            SKIP_ALL,
            CANCEL_ALL
        }
	}
	
	void flushPrefs () {
		autoPrefs.put(DIR_AUTO_SOURCE, sourceFolderAuto.getAbsolutePath());
		autoPrefs.put(DIR_AUTO_MIDI, midiFolderAuto.getAbsolutePath());
		autoPrefs.put(DIR_AUTO_DEST, destFolderAuto.getAbsolutePath());
	}

    @Override
    public WarningAction handleWarning(String warningId, String title, String message) {

        synchronized(modalLock) {

            if (proceedAllWarningMap.getOrDefault(warningId, false)) {
                return WarningAction.PROCEED;
            }

            if (this.cancel) {
                return WarningAction.SKIP_FILE;
            }

            // these are in arrays to be able to make them final while still mutable
            final int[] userChoice = new int[1];
            final boolean[] proceedForAll = new boolean[1];

            try {
                SwingUtilities.invokeAndWait(() -> {
                    String fullMessage = message + "\n\nWhat would you like to do?";
                    String[] options = {"Proceed", "Skip", "Cancel"};
                    JCheckBox proceedForAllCheckbox = new JCheckBox("Apply choice to all '" + warningId + "' warnings");

                    Object[] params = {fullMessage, proceedForAllCheckbox};

                    userChoice[0] = JOptionPane.showOptionDialog(frame, params, title, JOptionPane.DEFAULT_OPTION,
                            JOptionPane.WARNING_MESSAGE, null, options, options[0]
                    );
                    proceedForAll[0] = proceedForAllCheckbox.isSelected();
                });
            } catch (InvocationTargetException | InterruptedException ex) {
                log.log(Level.WARNING, "Error showing warning dialog", ex);
                this.cancel = true;
                return WarningAction.SKIP_FILE;
            }

            return switch (userChoice[0]) {
                case 0 -> {
                    if (proceedForAll[0]) {
                        proceedAllWarningMap.put(warningId, true);
                    }
                    yield WarningAction.PROCEED;
                }
                case 1 -> WarningAction.SKIP_FILE;// Cancel All

                default -> {
                    this.cancel = true;
                    yield WarningAction.SKIP_FILE;
                }
            };
        }
    }
}
