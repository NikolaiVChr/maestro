package com.digero.abcplayer.view;

import com.digero.abcplayer.AbcPlayer;
import com.digero.abcplayer.MidiToWav;
import com.digero.common.abctomidi.AbcInfo;
import com.digero.common.abctomidi.AbcToMidi;
import com.digero.common.abctomidi.FileAndData;
import com.digero.common.util.Util;
import com.digero.common.view.UIText;
import net.miginfocom.swing.MigLayout;

import javax.sound.midi.Sequence;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class PlaylistMp3ExportWizard extends JDialog {
	private enum Quality {
		Medium, Standard, Extreme
	}

	private File playlistFile;
	private List<AbcInfo> setData;
	private Preferences prefs;

	private JLabel statusLabel;
	private JProgressBar progressBar;
	private JTextField outputDirField;
	private JRadioButton[] qualityButtons;
	private JCheckBox numberPrefixCheckbox;
	private JButton exportButton;
	private JButton cancelButton;
	private Mp3ExportWorker worker;

	public PlaylistMp3ExportWizard(JFrame owner, Preferences prefs, File playlistFile, List<AbcInfo> setData) {
		super(owner, UIText.get("abcplayer.export.playlist.to.mp3s"), false);

		this.playlistFile = playlistFile;
		this.setData = setData;
		this.prefs = prefs;

		exportButton = new JButton(UIText.get("abcplayer.export"));
		getRootPane().setDefaultButton(exportButton);
		exportButton.addActionListener(e -> startExport());

		cancelButton = new JButton(UIText.get("common.cancel"));
		cancelButton.addActionListener(e -> {
			if (worker != null && !worker.isDone()) {
				worker.cancel(false);
			} else {
				setVisible(false);
			}
		});

		statusLabel = new JLabel(" ");
		progressBar = new JProgressBar(0, 100);

		JPanel buttonsPanel = new JPanel(new MigLayout("fillx"));
		buttonsPanel.add(progressBar, "span 4, grow, wrap");
		buttonsPanel.add(statusLabel, "span 4, grow, wrap");
		buttonsPanel.add(new JLabel(), "growx -1");
		buttonsPanel.add(cancelButton, "align right");
		buttonsPanel.add(exportButton, "align left");
		buttonsPanel.add(new JLabel(), "growx -1");

		JPanel mainPanel = new JPanel(new BorderLayout());
		mainPanel.add(createMp3ExportSettingsPanel());
		mainPanel.add(buttonsPanel, BorderLayout.SOUTH);

		setContentPane(mainPanel);
		pack();
		setLocationRelativeTo(owner);
	}

	private JPanel createMp3ExportSettingsPanel() {
		JPanel exportSettingsPanel = new JPanel(new MigLayout("wrap 3", "[][grow][]"));

		JPanel qualityPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		ButtonGroup qualityButtonGroup = new ButtonGroup();
		qualityButtons = new JRadioButton[Quality.values().length];
		int savedQuality = Util.clamp(prefs.getInt("common.mp3.quality", Quality.Standard.ordinal()), 0, qualityButtons.length - 1);
		for (Quality q : Quality.values()) {
			int i = q.ordinal();
			qualityButtons[i] = new JRadioButton(q.toString(), i == savedQuality);
			qualityButtonGroup.add(qualityButtons[i]);
			qualityPanel.add(qualityButtons[i]);
		}

		String defaultBaseDir = playlistFile != null
				? playlistFile.getParentFile().getAbsolutePath()
				: Util.getLotroMusicPath(false).getAbsolutePath();
		String baseDir = prefs.get("mp3ExportDirectory", defaultBaseDir);
		String playlistName;
		if (playlistFile != null) {
			playlistName = playlistFile.getName();
			int dot = playlistName.lastIndexOf('.');
			if (dot >= 0) playlistName = playlistName.substring(0, dot);
		} else {
			playlistName = "untitled_playlist";
		}
		String outputDir = new File(baseDir, playlistName).getAbsolutePath();

		outputDirField = new JTextField(outputDir, 30);
		JButton browseButton = new JButton(UIText.get("abcplayer.browse"));
		browseButton.addActionListener(e -> {
			JFileChooser fc = new JFileChooser(outputDirField.getText());
			fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			int result = fc.showOpenDialog(PlaylistMp3ExportWizard.this);
			if (result == JFileChooser.APPROVE_OPTION) {
				outputDirField.setText(fc.getSelectedFile().getAbsolutePath());
			}
		});

		numberPrefixCheckbox = new JCheckBox(UIText.get("abcplayer.export.mp3.prefix.with.index"), prefs.getBoolean("mp3NumberPrefix", false));

		exportSettingsPanel.add(new JLabel(UIText.get("common.mp3.quality")));
		exportSettingsPanel.add(qualityPanel, "span 2, wrap");
		exportSettingsPanel.add(new JLabel(UIText.get("abcplayer.output.folder")));
		exportSettingsPanel.add(outputDirField, "growx");
		exportSettingsPanel.add(browseButton, "wrap");
		exportSettingsPanel.add(numberPrefixCheckbox, "span 3");

		return exportSettingsPanel;
	}

	private int getQualityIndex() {
		for (int i = 0; i < qualityButtons.length; i++) {
			if (qualityButtons[i].isSelected())
				return i;
		}
		return Quality.Standard.ordinal();
	}

	private String getQuality() {
		return qualityButtons[getQualityIndex()].getText().toLowerCase();
	}

	private void startExport() {
		File outputDir = new File(outputDirField.getText());
		if (!outputDir.exists()) {
			int result = JOptionPane.showConfirmDialog(this,
					UIText.get("common.mp3.folder.doesn.t.exist.create", outputDir.getName()),
					UIText.get("common.mp3.create.directory"), JOptionPane.OK_CANCEL_OPTION);
			if (result != JOptionPane.OK_OPTION) return;
			if (!outputDir.mkdirs()) {
				JOptionPane.showMessageDialog(this,
						UIText.get("common.mp3.failed.to.create.parent.folder"),
						UIText.get("common.mp3.failed.to.create.folder"), JOptionPane.ERROR_MESSAGE);
				return;
			}
		}

		prefs.put("mp3ExportDirectory", outputDir.getParentFile().getAbsolutePath());
		prefs.putInt("common.mp3.quality", getQualityIndex());
		prefs.putBoolean("mp3NumberPrefix", numberPrefixCheckbox.isSelected());

		exportButton.setEnabled(false);
		progressBar.setValue(0);
		statusLabel.setText(" ");

		List<AbcInfo> snapshot = new ArrayList<>(setData);
		worker = new Mp3ExportWorker(outputDir, getQuality(), numberPrefixCheckbox.isSelected(), snapshot);
		worker.execute();
	}

	private class Mp3ExportWorker extends SwingWorker<Boolean, String> {
		private final File outputDir;
		private final String quality;
		private final boolean numberPrefix;
		private final List<AbcInfo> songs;
		private String error = "";

		public Mp3ExportWorker(File outputDir, String quality, boolean numberPrefix, List<AbcInfo> songs) {
			this.outputDir = outputDir;
			this.quality = quality;
			this.numberPrefix = numberPrefix;
			this.songs = songs;
		}

		@Override
		protected Boolean doInBackground() {
			int count = songs.size();
			for (int i = 0; i < count; i++) {
				if (isCancelled()) return false;

				AbcInfo inf = songs.get(i);
				File abcFile = inf.getSourceFiles().get(0);
				String baseName = abcFile.getName();
				if (baseName.toLowerCase().endsWith(Util.ABC_FILE_EXTENSION)) {
					baseName = baseName.substring(0, baseName.length() - 4);
				}
				if (numberPrefix) {
					baseName = String.format("%03d - %s", i + 1, baseName);
				}
				String mp3Name = baseName + Util.MP3_FILE_EXTENSION;
				File mp3File = new File(outputDir, mp3Name);

				publish(UIText.get("abcplayer.export.mp3.exporting.0.of.1.2", i + 1, count, mp3Name));

				try {
					List<FileAndData> data = new ArrayList<>();
					data.add(new FileAndData(abcFile, AbcToMidi.readLines(abcFile)));

					AbcToMidi.Params params = new AbcToMidi.Params(data);
					params.useLotroInstruments = true;
					params.abcInfo = new AbcInfo();
					params.enableLotroErrors = false;
					params.stereo = prefs.parent().parent().getInt("stereoSliderMenuItem", 100);
					Sequence sequence = AbcToMidi.convert(params);

					File wavFile = File.createTempFile("Abc-", Util.WAV_FILE_EXTENSION);
					try (FileOutputStream fos = new FileOutputStream(wavFile)) {
						MidiToWav.render(sequence, fos, 0);
						fos.close();

						List<String> args = new ArrayList<>();
						args.add("--silent");
						args.add("--preset");
						args.add(quality);
						if (!params.abcInfo.getTitle().isEmpty()) {
							args.add("--tt");
							args.add(params.abcInfo.getTitle());
						}
						if (!params.abcInfo.getComposer().isEmpty()) {
							args.add("--ta");
							args.add(params.abcInfo.getComposer());
						}
						args.add("--tc");
						args.add("Encoded by: " + AbcPlayer.APP_NAME);
						args.add("--nores");
						args.add(wavFile.getAbsolutePath());
						args.add(mp3File.getAbsolutePath());

						de.sciss.jump3r.Main.main(args.toArray(new String[0]));
					} finally {
						wavFile.delete();
					}
				} catch (Exception e) {
					error = UIText.get("abcplayer.export.mp3.failed.0", abcFile.getName(), e.getMessage());
					return false;
				}

				int progress = (int) (((i + 1) / (float) count) * 100);
				setProgress(progress);
			}
			return true;
		}

		@Override
		protected void process(List<String> chunks) {
			String latest = chunks.get(chunks.size() - 1);
			statusLabel.setText(latest);
			progressBar.setValue(getProgress());
		}

		@Override
		protected void done() {
			progressBar.setValue(100);
			exportButton.setEnabled(true);

			if (isCancelled()) {
				statusLabel.setText(UIText.get("abcplayer.export.mp3.cancelled"));
			} else if (!error.isEmpty()) {
				statusLabel.setText(error);
				JOptionPane.showMessageDialog(PlaylistMp3ExportWizard.this, error,
						UIText.get("common.error.saving.mp3.file"), JOptionPane.ERROR_MESSAGE);
			} else {
				statusLabel.setText(UIText.get("abcplayer.export.mp3.finished.0", songs.size()));
			}
		}
	}
}
