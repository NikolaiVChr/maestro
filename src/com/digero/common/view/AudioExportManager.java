package com.digero.common.view;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
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
import javax.swing.filechooser.FileFilter;

import com.digero.abcplayer.MidiToWav;
import com.digero.common.midi.LotroSequencerWrapper;
import com.digero.common.midi.SequencerWrapper;
import com.digero.common.util.ExtensionFileFilter;
import com.digero.common.util.Util;

public class AudioExportManager {
	
	private static final String LAME_URL = "http://lame.sourceforge.net/";
	private static final String FF_URL = "https://ffmpeg.org/";
	
	private JFrame parentWindow;
	private JFileChooser exportFileDialog;
	private boolean isExporting = false;
	private Preferences prefs;
	
	private String encodedBy;

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
			new Thread(new ExportWavTask(sequencer.getSequence(), saveFile, waitFrame, sequencer.getStartTick())).start();
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
			new Thread(new ExportMp3BuiltinTask(sequencer.getSequence(), dialog, waitFrame, sequencer.getStartTick())).start();
		});
		mp3Dialog.setVisible(true);
	}
	
	@Deprecated 
	public void exportMp3Lame(SequencerWrapper sequencer, File abcFile, String title, String composer) {
		Preferences mp3Prefs = prefs.node("mp3");
		File lameExe = new File(mp3Prefs.get("lameExe", "./lame.exe"));
		if (!lameExe.exists()) {
			outerLoop: for (File dir : new File(".").listFiles()) {
				if (dir.isDirectory()) {
					for (File file : dir.listFiles()) {
						if (file.getName().equalsIgnoreCase("lame.exe")) {
							lameExe = file;
							break outerLoop;
						}
					}
				}
			}
		}

		JLabel hyperlink = new JLabel("<html><a href='" + LAME_URL + "'>" + LAME_URL + "</a></html>");
		hyperlink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		hyperlink.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 1 && e.getButton() == MouseEvent.BUTTON1) {
					Util.openURL(LAME_URL);
				}
			}
		});

		boolean overrideAndUseExe = false;
		for (int i = 0; (!overrideAndUseExe && !isLame(lameExe)) || !lameExe.exists(); i++) {
			if (i > 0) {
				JFileChooser fc = new JFileChooser();
				fc.setFileFilter(new FileFilter() {
					@Override
					public boolean accept(File f) {
						return f.isDirectory() || f.getName().equalsIgnoreCase("lame.exe");
					}

					@Override
					public String getDescription() {
						return "lame.exe";
					}
				});
				fc.setSelectedFile(lameExe);
				int result = fc.showOpenDialog(parentWindow);

				if (result == JFileChooser.ERROR_OPTION)
					continue; // Try again
				else if (result != JFileChooser.APPROVE_OPTION)
					return;
				lameExe = fc.getSelectedFile();
			}

			if (!lameExe.exists()) {
				Object message;
				int icon;
				if (i == 0) {
					message = new Object[] {
							"Exporting to MP3 requires LAME, a free MP3 encoder.\n" + "To download LAME, visit: ",
							hyperlink, "\nAfter you download and unzip it, click OK to locate lame.exe", };
					icon = JOptionPane.INFORMATION_MESSAGE;
				} else {
					message = "File does not exist:\n" + lameExe.getAbsolutePath();
					icon = JOptionPane.ERROR_MESSAGE;
				}
				int result = JOptionPane.showConfirmDialog(parentWindow, message, "Export to MP3 requires LAME",
						JOptionPane.OK_CANCEL_OPTION, icon);
				if (result != JOptionPane.OK_OPTION)
					return;
			} else if (!isLame(lameExe)) {
				Object[] message = new Object[] {
						"The MP3 converter you selected \"" + lameExe.getName() + "\" doesn't appear to be LAME.\n"
								+ "You can download LAME from: ",
						hyperlink, "\nWould you like to use \"" + lameExe.getName() + "\" anyways?\n"
								+ "If you choose No, you'll be prompted to locate lame.exe" };
				int result = JOptionPane.showConfirmDialog(parentWindow, message, "Export to MP3 requires LAME",
						JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
				if (result == JOptionPane.YES_OPTION)
					overrideAndUseExe = true;
				else if (result == JOptionPane.NO_OPTION)
					continue; // Try again
				else
					return;
			}

			mp3Prefs.put("lameExe", lameExe.getAbsolutePath());
		}

		ExportMp3Dialog mp3Dialog = new ExportMp3Dialog(parentWindow, lameExe, mp3Prefs, abcFile, title, composer);
		mp3Dialog.setIconImages(parentWindow.getIconImages());
		mp3Dialog.addActionListener(e -> {
			ExportMp3Dialog dialog = (ExportMp3Dialog) e.getSource();
			JDialog waitFrame = new WaitDialog(parentWindow, dialog.getSaveFile());
			waitFrame.setVisible(true);
			new Thread(new ExportMp3LameTask(sequencer.getSequence(), dialog, waitFrame)).start();
		});
		mp3Dialog.setVisible(true);
	}

	@Deprecated
	public void exportMp3Ffmpeg(SequencerWrapper sequencer, File abcFile, String title, String composer) {
		Preferences mp3Prefs = prefs.node("mp3");
		File ffExe = new File(mp3Prefs.get("ffExe", "./ffmpeg.exe"));
		if (System.getProperty("os.name").contains("Windows")) {

			if (!ffExe.exists()) {
				outerLoop: for (File dir : new File(".").listFiles()) {
					if (dir.isDirectory()) {
						for (File file : dir.listFiles()) {
							if (file.getName().equalsIgnoreCase("ffmpeg.exe")) {
								ffExe = file;
								break outerLoop;
							}
						}
					}
				}
			}

			JLabel hyperlink = new JLabel("<html><a href='" + FF_URL + "'>" + FF_URL + "</a></html>");
			hyperlink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			hyperlink.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					if (e.getClickCount() == 1 && e.getButton() == MouseEvent.BUTTON1) {
						Util.openURL(FF_URL);
					}
				}
			});

			boolean overrideAndUseExe = false;
			for (int i = 0; !ffExe.exists() || (!overrideAndUseExe && !isFF(ffExe)); i++) {
				if (i > 0) {
					JFileChooser fc = new JFileChooser();
					fc.setFileFilter(new FileFilter() {
						@Override
						public boolean accept(File f) {
							return f.isDirectory() || f.getName().equalsIgnoreCase("ffmpeg.exe");
						}

						@Override
						public String getDescription() {
							return "ffmpeg.exe";
						}
					});
					fc.setSelectedFile(ffExe);
					int result = fc.showOpenDialog(parentWindow);

					if (result == JFileChooser.ERROR_OPTION)
						continue; // Try again
					else if (result != JFileChooser.APPROVE_OPTION)
						return;
					ffExe = fc.getSelectedFile();
				}

				if (!ffExe.exists()) {
					Object message;
					int icon;
					if (i == 0) {
						message = new Object[] {
								"Exporting to MP3 requires FFmpeg, a free MP3 encoder.\n"
										+ "To download FFmpeg, visit: ",
								hyperlink, "\nAfter you download and unzip it, click OK to locate ffmpeg.exe", };
						icon = JOptionPane.INFORMATION_MESSAGE;
					} else {
						message = "File does not exist:\n" + ffExe.getAbsolutePath();
						icon = JOptionPane.ERROR_MESSAGE;
					}
					int result = JOptionPane.showConfirmDialog(parentWindow, message, "Export to MP3 requires FFmpeg",
							JOptionPane.OK_CANCEL_OPTION, icon);
					if (result != JOptionPane.OK_OPTION)
						return;
				} else if (!isFF(ffExe)) {
					Object[] message = new Object[] {
							"The MP3 converter you selected \"" + ffExe.getName() + "\" doesn't appear to be FFmpeg.\n"
									+ "You can download FFmpeg from: ",
							hyperlink, "\nWould you like to use \"" + ffExe.getName() + "\" anyways?\n"
									+ "If you choose No, you'll be prompted to locate ffmpeg.exe" };
					int result = JOptionPane.showConfirmDialog(parentWindow, message, "Export to MP3 requires FFmpeg",
							JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
					if (result == JOptionPane.YES_OPTION)
						overrideAndUseExe = true;
					else if (result == JOptionPane.NO_OPTION)
						continue; // Try again
					else
						return;
				}

				mp3Prefs.put("ffExe", ffExe.getAbsolutePath());
			}
		}
		ExportMp3Dialog mp3Dialog = new ExportMp3Dialog(parentWindow, ffExe, mp3Prefs, abcFile, title, composer);
		mp3Dialog.setIconImages(parentWindow.getIconImages());
		mp3Dialog.addActionListener(e -> {
			ExportMp3Dialog dialog = (ExportMp3Dialog) e.getSource();
			JDialog waitFrame = new WaitDialog(parentWindow, dialog.getSaveFile());
			waitFrame.setVisible(true);
			new Thread(new ExportMp3FfmpegTask(sequencer.getSequence(), dialog, waitFrame)).start();
		});
		mp3Dialog.setVisible(true);
	}
	
	@Deprecated
	private class ExportMp3LameTask implements Runnable {
		private Sequence sequence;
		private ExportMp3Dialog mp3Dialog;
		private JDialog waitFrame;

		public ExportMp3LameTask(Sequence sequence, ExportMp3Dialog mp3Dialog, JDialog waitFrame) {
			this.sequence = sequence;
			this.mp3Dialog = mp3Dialog;
			this.waitFrame = waitFrame;
		}

		@Override
		public void run() {
			isExporting = true;
			Exception error = null;
			String lameExeSav = null;
			Preferences mp3Prefs = mp3Dialog.getPreferencesNode();
			try {
				lameExeSav = mp3Prefs.get("lameExe", null);
				mp3Prefs.put("lameExe", "");
				File wavFile = File.createTempFile("AbcPlayer-", ".wav");
				try (FileOutputStream fos = new FileOutputStream(wavFile)) {
					MidiToWav.render(sequence, fos, 0L);
					fos.close();
					Process p = Runtime.getRuntime().exec(mp3Dialog.getCommandLine(wavFile));
					if (p.waitFor() != 0)
						throw new Exception("LAME failed");
				} finally {
					wavFile.delete();
				}
			} catch (Exception e) {
				error = e;
			} finally {
				if (lameExeSav != null) {
					mp3Prefs.put("lameExe", lameExeSav);
				}
				isExporting = false;
				SwingUtilities.invokeLater(new ExportMp3FinishedTask(error, waitFrame));
			}
		}
	}
	
	@Deprecated
	private class ExportMp3FfmpegTask implements Runnable {
		private Sequence sequence;
		private ExportMp3Dialog mp3Dialog;
		private JDialog waitFrame;

		public ExportMp3FfmpegTask(Sequence sequence, ExportMp3Dialog mp3Dialog, JDialog waitFrame) {
			this.sequence = sequence;
			this.mp3Dialog = mp3Dialog;
			this.waitFrame = waitFrame;
		}

		@Override
		public void run() {
			isExporting = true;
			Exception error = null;
			String ffExeSav = null;
			Preferences mp3Prefs = mp3Dialog.getPreferencesNode();
			try {
				if (System.getProperty("os.name").contains("Windows")) {
					ffExeSav = mp3Prefs.get("ffExe", null);
					mp3Prefs.put("ffExe", "");
				}
				File wavFile = File.createTempFile("AbcPlayer-", ".wav");
				try (FileOutputStream fos = new FileOutputStream(wavFile)) {
					MidiToWav.render(sequence, fos, 0L);
					fos.close();
					String[] args = mp3Dialog.getCommandLineNew(wavFile, encodedBy).toArray(new String[0]);

					ProcessBuilder ps = new ProcessBuilder(args);

					ps.redirectErrorStream(true);

					Process p = ps.start();

					BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
					String line;

					while ((line = in.readLine()) != null) {
						System.out.println(line);
					}
					if (p.waitFor() != 0) {
						throw new Exception("FFmpeg failed");
					}
				} finally {
					wavFile.delete();
				}
			} catch (Exception e) {
				error = e;
			} finally {
				if (ffExeSav != null && System.getProperty("os.name").contains("Windows"))
					mp3Prefs.put("ffExe", ffExeSav);
			}
			isExporting = false;
			SwingUtilities.invokeLater(new ExportMp3FinishedTask(error, waitFrame));
		}
	}
	
	private class ExportMp3BuiltinTask implements Runnable {
		private Sequence sequence;
		private ExportMp3Dialog mp3Dialog;
		private JDialog waitFrame;
		private long startTick;

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
				File wavFile = File.createTempFile("AbcPlayer-", ".wav");
				try (FileOutputStream fos = new FileOutputStream(wavFile)) {
					MidiToWav.render(sequence, fos, startTick);
					fos.close();
					
					String[] args = mp3Dialog.getCommandLineBuiltinLame(wavFile).toArray(new String[0]);
					
					// Invoke LAME library from https://mvnrepository.com/artifact/de.sciss/jump3r/1.0.5
					de.sciss.jump3r.Main.main(args);
				    
				    System.out.println("Encoding done");
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
		private Exception error;
		private JDialog waitFrame;

		public ExportMp3FinishedTask(Exception error, JDialog waitFrame) {
			this.error = error;
			this.waitFrame = waitFrame;
		}

		@Override
		public void run() {
			if (error != null) {
				JOptionPane.showMessageDialog(parentWindow, error.getMessage(), "Error saving MP3 file",
						JOptionPane.ERROR_MESSAGE);
			}
			waitFrame.setVisible(false);
		}

	}
	
	private class ExportWavTask implements Runnable {
		private Sequence sequence;
		private File file;
		private JDialog waitFrame;
		private long startTick;

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
			} finally {
				isExporting = false;
				SwingUtilities.invokeLater(new ExportWavFinishedTask(error, waitFrame));
			}
		}
	}

	private class ExportWavFinishedTask implements Runnable {
		private Exception error;
		private JDialog waitFrame;

		public ExportWavFinishedTask(Exception error, JDialog waitFrame) {
			this.error = error;
			this.waitFrame = waitFrame;
		}

		@Override
		public void run() {
			if (error != null) {
				JOptionPane.showMessageDialog(parentWindow, error.getMessage(), "Error saving WAV file",
						JOptionPane.ERROR_MESSAGE);
			}
			waitFrame.setVisible(false);
		}
	}
	
	@SuppressWarnings("serial")
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
	
	// Cached result of isFF()
	private static File notFFExe = null;

	private boolean isFF(File ffExe) {
		if (!ffExe.exists() || ffExe.equals(notFFExe))
			return false;

		FFChecker checker = new FFChecker(ffExe);
		checker.start();
		try {
			// Wait up to 3 seconds for the program to respond
			checker.join(3000);
		} catch (InterruptedException e) {
		}
		if (checker.isAlive() && checker.process != null) {
			checker.process.destroy();
		}
		if (!checker.isFF) {
			notFFExe = ffExe;
			return false;
		}

		return true;
	}

	private static class FFChecker extends Thread {
		private boolean isFF = false;
		private File ffExe;
		private Process process;

		public FFChecker(File ffExe) {
			this.ffExe = ffExe;
		}

		@Override
		public void run() {
			try {
				process = Runtime.getRuntime().exec(new String[] { Util.quote(ffExe.getAbsolutePath()), "-help" });
				BufferedReader rdr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
				String line;
				long start = System.currentTimeMillis();
				while ((line = rdr.readLine()) != null || System.currentTimeMillis() - start < 50) {
					if (line != null && line.toLowerCase().contains("ffmpeg")) {
						// System.out.println("ERR:"+line);
						isFF = true;
						break;
					} else if (line != null) {
						// System.out.println("ERR:"+line);
					}
				}
			} catch (IOException e) {
			}
		}
	}
	
	// Cached result of isLame()
	private static File notLameExe = null;

	private boolean isLame(File lameExe) {
		if (!lameExe.exists() || lameExe.equals(notLameExe))
			return false;

		LameChecker checker = new LameChecker(lameExe);
		checker.start();
		try {
			// Wait up to 3 seconds for the program to respond
			checker.join(3000);
		} catch (InterruptedException e) {
		}
		if (checker.isAlive())
			checker.process.destroy();
		if (!checker.isLame) {
			notLameExe = lameExe;
			return false;
		}

		return true;
	}

	public boolean isExporting() {
		return isExporting;
	}

	private static class LameChecker extends Thread {
		private boolean isLame = false;
		private File lameExe;
		private Process process;

		public LameChecker(File lameExe) {
			this.lameExe = lameExe;
		}

		@Override
		public void run() {
			try {
				process = Runtime.getRuntime().exec(new String[] { Util.quote(lameExe.getAbsolutePath()), " -?" });
				BufferedReader rdr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
				String line;
				while ((line = rdr.readLine()) != null) {
					if (line.contains("LAME")) {
						isLame = true;
						break;
					}
				}
			} catch (IOException e) {
			}
		}
	}
}
