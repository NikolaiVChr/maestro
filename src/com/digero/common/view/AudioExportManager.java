package com.digero.common.view;

import java.awt.BorderLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.sound.midi.Sequence;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

import com.digero.abcplayer.MidiToWav;
import com.digero.common.midi.LotroSequencerWrapper;
import com.digero.common.util.ExtensionFileFilter;
import com.digero.maestro.MaestroMain;
import com.digero.maestro.view.ProjectFrame;

public class AudioExportManager {
	private static final Logger log = Logger.getLogger("export.audio");
		
	private final JFrame parentWindow;
	private JFileChooser exportFileDialog;
	private boolean isExporting = false;
	private final Preferences prefs;
	
	private final String encodedBy;

	public AudioExportManager(JFrame parentWindow, String encodedBy, Preferences prefs) {
		this.parentWindow = parentWindow;
		this.encodedBy = encodedBy;
		this.prefs = prefs;
	}
	
	public void exportWav(LotroSequencerWrapper sequencer, File abcFile) {
		Preferences mp3Prefs = prefs.node("mp3");
		if (exportFileDialog == null) {
			exportFileDialog = new JFileChooser(
					mp3Prefs.get("saveDirectory", abcFile.getParentFile().getAbsolutePath()));

			if (abcFile != null) {
				String openedName = abcFile.getName();
				int dot = openedName.lastIndexOf('.');
				if (dot >= 0) {
					openedName = openedName.substring(0, dot);
				}
				openedName += ".wav";
				exportFileDialog.setSelectedFile(new File(exportFileDialog.getCurrentDirectory() + "/" + openedName));
			}
		}

		exportFileDialog.setFileFilter(new ExtensionFileFilter("WAV Files", "wav"));

		int result = exportFileDialog.showSaveDialog(parentWindow);
		if (result == JFileChooser.APPROVE_OPTION) {
			mp3Prefs.put("saveDirectory", exportFileDialog.getCurrentDirectory().getAbsolutePath());

			File saveFile = exportFileDialog.getSelectedFile();
			if (saveFile.getName().indexOf('.') < 0) {
				saveFile = new File(saveFile.getParent() + "/" + saveFile.getName() + ".wav");
				exportFileDialog.setSelectedFile(saveFile);
			}

			JDialog waitFrame = new WaitDialog(parentWindow, saveFile);
			waitFrame.setVisible(true);
            Thread.ofVirtual().start(new ExportWavTask(sequencer.getSequence(), saveFile, waitFrame, sequencer.getStartTick()));
		}
	}
	
	public void exportMp3Builtin(LotroSequencerWrapper sequencer, File abcFile, String title, String composer) {
		Preferences mp3Prefs = prefs.node("mp3");
		ExportMp3Dialog mp3Dialog = new ExportMp3Dialog(parentWindow, null, mp3Prefs, abcFile, title, composer);
		mp3Dialog.setIconImages(parentWindow.getIconImages());
		mp3Dialog.addActionListener(e -> {
			ExportMp3Dialog dialog = (ExportMp3Dialog) e.getSource();
			JDialog waitFrame = new WaitDialog(parentWindow, dialog.getSaveFile());
			waitFrame.setVisible(true);
            Thread.ofVirtual().start(new ExportMp3BuiltinTask(sequencer.getSequence(), dialog, waitFrame, sequencer.getStartTick()));
		});
		mp3Dialog.setVisible(true);
	}
	
	private class ExportMp3BuiltinTask implements Runnable {
		private final Sequence sequence;
		private final ExportMp3Dialog mp3Dialog;
		private final JDialog waitFrame;
		private final long startTick;

		public ExportMp3BuiltinTask(Sequence sequence, ExportMp3Dialog mp3Dialog, JDialog waitFrame, long startTick) {
			this.sequence = sequence;
			this.mp3Dialog = mp3Dialog;
			this.waitFrame = waitFrame;
			this.startTick = startTick;
		}

		@Override
		public void run() {
			isExporting = true;
			Exception error = null;
			try {
				File wavFile = File.createTempFile("Abc-", ".wav");
				try (FileOutputStream fos = new FileOutputStream(wavFile)) {
					MidiToWav.render(sequence, fos, startTick);
					fos.close();
					
					String[] args = mp3Dialog.getCommandLineBuiltinLame(wavFile, encodedBy).toArray(new String[0]);
					
					// Invoke LAME library from https://mvnrepository.com/artifact/de.sciss/jump3r/1.0.5
					de.sciss.jump3r.Main.main(args);
				    
				    log.info("MP3 Encoding done");
				} finally {
					wavFile.delete();
				}
			} catch (Exception e) {
				error = e;
			}
			isExporting = false;
			SwingUtilities.invokeLater(new ExportMp3FinishedTask(error, waitFrame));
		}
	}

	private class ExportMp3FinishedTask implements Runnable {
		private final Exception error;
		private final JDialog waitFrame;

		public ExportMp3FinishedTask(Exception error, JDialog waitFrame) {
			this.error = error;
			this.waitFrame = waitFrame;
		}

		@Override
		public void run() {
			if (error != null) {
				JOptionPane.showMessageDialog(parentWindow, error.getMessage(), "Error saving MP3 file",
						JOptionPane.ERROR_MESSAGE);
				log.log(Level.WARNING, "Something happened while converting to mp3.", error);
				if (parentWindow instanceof ProjectFrame) ProjectFrame.feed("ERROR: " + error, MaestroMain.getFirstLines(error));
			}
			waitFrame.setVisible(false);
		}

	}
	
	private class ExportWavTask implements Runnable {
		private final Sequence sequence;
		private final File file;
		private final JDialog waitFrame;
		private final long startTick;

		public ExportWavTask(Sequence sequence, File file, JDialog waitFrame, long startTick) {
			this.sequence = sequence;
			this.file = file;
			this.waitFrame = waitFrame;
			this.startTick = startTick;
		}

		@Override
		public void run() {
			isExporting = true;
			Exception error = null;
			try {
				try (FileOutputStream fos = new FileOutputStream(file)) {
					MidiToWav.render(sequence, fos, startTick);
				}
			} catch (Exception e) {
				error = e;
				log.warning(e.getMessage());
			} finally {
				isExporting = false;
				SwingUtilities.invokeLater(new ExportWavFinishedTask(error, waitFrame));
				log.info("Wav generation finished");
			}
		}
	}

	private class ExportWavFinishedTask implements Runnable {
		private final Exception error;
		private final JDialog waitFrame;

		public ExportWavFinishedTask(Exception error, JDialog waitFrame) {
			this.error = error;
			this.waitFrame = waitFrame;
		}

		@Override
		public void run() {
			if (error != null) {
				JOptionPane.showMessageDialog(parentWindow, error.getMessage(), "Error saving WAV file",
						JOptionPane.ERROR_MESSAGE);
				log.log(Level.WARNING, "Something happened while converting to wav.", error);
				if (parentWindow instanceof ProjectFrame) ProjectFrame.feed("ERROR: " + error, MaestroMain.getFirstLines(error));
			}
			waitFrame.setVisible(false);
		}
	}
	
	private class WaitDialog extends JDialog {
		public WaitDialog(JFrame owner, File saveFile) {
			super(owner, "Exporting...", false);
			JPanel waitContent = new JPanel(new BorderLayout(5, 5));
			setContentPane(waitContent);
			waitContent.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
			waitContent.add(new JLabel("Saving " + saveFile.getName() + ". Please wait..."), BorderLayout.CENTER);
			JProgressBar waitProgress = new JProgressBar();
			waitProgress.setIndeterminate(true);
			waitContent.add(waitProgress, BorderLayout.SOUTH);
			pack();
			setLocation(getOwner().getX() + (getOwner().getWidth() - getWidth()) / 2,
					getOwner().getY() + (getOwner().getHeight() - getHeight()) / 2);
			setResizable(false);
			setEnabled(false);
			setIconImages(parentWindow.getIconImages());
		}
	}
	
	public boolean isExporting() {
		return isExporting;
	}
}
