package com.digero.common.view;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.border.Border;

import com.digero.abcplayer.AbcPlayer;
import com.digero.common.util.Util;

@SuppressWarnings("serial")
public class ExportMp3Dialog extends JDialog implements TableLayoutConstants {
	private static final int TEXT_FIELD_COLS = 28;

	private enum Quality {
		Medium, Standard, Extreme
	}

	private JTextField saveAsField;
	private JTextField titleField;
	private JCheckBox addLotroCheckbox;
	private JTextField artistField;
	private JTextField albumField;
	private ButtonGroup qualityButtonGroup;
	private JRadioButton[] qualityButtons;

	private Preferences prefs;
	private TableLayout layout;
	private JPanel content;

	private List<ActionListener> actionListeners;

	public ExportMp3Dialog(JFrame parent, File theExe, Preferences prefs, File abcFile, String songTitle,
			String songArtist) {
		super(parent, AbcPlayer.APP_NAME + " - Export to MP3", false);

		this.prefs = prefs;
		this.actionListeners = new ArrayList<>();

		Border outerBorder = BorderFactory.createEmptyBorder(8, 8, 8, 8);

		layout = new TableLayout(//
				new double[] { PREFERRED, FILL, PREFERRED }, //
				new double[] {});
		content = new JPanel(layout);

		String saveDir = prefs.get("saveDirectory", abcFile.getParentFile().getAbsolutePath());
		String saveName = abcFile.getName();
		if (saveName.toLowerCase().endsWith(Util.ABC_FILE_EXTENSION)) {
			saveName = saveName.substring(0, saveName.length() - 4) + ".mp3";
		}
		File saveFile = new File(saveDir, saveName);

		titleField = new JTextField(songTitle, TEXT_FIELD_COLS);
		addLotroCheckbox = new JCheckBox(UIText.get("common.add.lotro"), prefs.getBoolean("addLotro", true));
		artistField = new JTextField(songArtist, TEXT_FIELD_COLS);
		albumField = new JTextField(prefs.get("common.album", ""), TEXT_FIELD_COLS);
		saveAsField = new JTextField(saveFile.getAbsolutePath(), TEXT_FIELD_COLS);

		JButton browseButton = new JButton(UIText.get("abcplayer.browse"));
		browseButton.setMnemonic(KeyEvent.VK_B);
		browseButton.addActionListener(e -> {
			JFileChooser fc = new JFileChooser();
			fc.setSelectedFile(new File(saveAsField.getText()));
			int result = fc.showSaveDialog(ExportMp3Dialog.this);
			if (result == JFileChooser.APPROVE_OPTION) {
				File f = fc.getSelectedFile();
				if (f.getName().indexOf('.') < 0)
					f = new File(f.getParentFile(), f + ".mp3");
				saveAsField.setText(fc.getSelectedFile().getAbsolutePath());
			}
		});

		JPanel qualityPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		qualityButtons = new JRadioButton[Quality.values().length];
		qualityButtonGroup = new ButtonGroup();
		int iQuality = Util.clamp(prefs.getInt("common.mp3.quality", Quality.Standard.ordinal()), 0, qualityButtons.length - 1);
		for (Quality q : Quality.values()) {
			int i = q.ordinal();
			qualityButtons[i] = new JRadioButton(q.toString(), i == iQuality);
			qualityButtonGroup.add(qualityButtons[i]);
			qualityPanel.add(qualityButtons[i]);
		}

		addRow(UIText.get("common.title"), titleField, addLotroCheckbox);
		addRow(UIText.get("common.artist"), artistField, null);
		addRow(UIText.get("common.album"), albumField, null);
		addRow(UIText.get("common.mp3.quality"), qualityPanel, qualityPanel);
		addRow(UIText.get("common.save.as"), saveAsField, browseButton);
		for (int r = 0; r < layout.getNumRow(); r++) {
			layout.setRow(r, 1.0 / layout.getNumRow());
		}
		layout.insertRow(layout.getNumRow(), 16);

		JPanel okCancelPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		JButton okButton = new JButton(UIText.get("common.mp3.convert"));
		okButton.setMnemonic(KeyEvent.VK_O);
		okButton.setFont(okButton.getFont().deriveFont(Font.BOLD));
		okButton.addActionListener(e -> {
			if (validateFile()) {
				saveSettings();
				setVisible(false);
				fireActionPerformed();
			}
		});
		JButton cancelButton = new JButton(UIText.get("common.cancel"));
		cancelButton.setMnemonic(KeyEvent.VK_C);
		cancelButton.addActionListener(e -> setVisible(false));
		JPanel spacer = new JPanel();
		spacer.setPreferredSize(new Dimension(5, 5));
		okCancelPanel.add(okButton);
		okCancelPanel.add(spacer);
		okCancelPanel.add(cancelButton);

		JPanel outerContent = new JPanel(new BorderLayout());
		outerContent.setBorder(outerBorder);
		setContentPane(outerContent);
		outerContent.add(content, BorderLayout.CENTER);
		outerContent.add(okCancelPanel, BorderLayout.SOUTH);

		pack();
		setLocation(getOwner().getX() + (getOwner().getWidth() - getWidth()) / 2,
				getOwner().getY() + (getOwner().getHeight() - getHeight()) / 2);
		setResizable(false);
	}

	private void addRow(String labelText, Component element, Component element2) {
		int row = layout.getNumRow();
		layout.insertRow(row, PREFERRED);

		JLabel label = new JLabel(labelText + "  ");
		label.setFont(label.getFont().deriveFont(Font.BOLD));
		content.add(label, "0, " + row);

		if (element == element2) {
			content.add(element, "1, " + row + ", 2, " + row);
		} else {
			if (element != null)
				content.add(element, "1, " + row + ", L, C");
			if (element2 != null)
				content.add(element2, "2, " + row + ", L, C");
		}
	}

	public Preferences getPreferencesNode() {
		return prefs;
	}

	private void saveSettings() {
		prefs.putBoolean("addLotro", addLotroCheckbox.isSelected());
		prefs.put("common.album", albumField.getText());
		prefs.putInt("common.mp3.quality", getQualityIndex());
		prefs.put("saveDirectory", getSaveFile().getParentFile().getAbsolutePath());
	}

	private int getQualityIndex() {
		for (int i = 0; i < qualityButtons.length; i++) {
			if (qualityButtons[i].isSelected())
				return i;
		}
		return Quality.Medium.ordinal();
	}

	public String getSongTitle() {
		String title = titleField.getText().trim();
		if (!title.isEmpty() && addLotroCheckbox.isSelected()) {
			title += " (LOTRO)";
		}
		return title;
	}

	public String getArtist() {
		return artistField.getText().trim();
	}

	public String getAlbum() {
		return albumField.getText().trim();
	}

	public String getQuality() {
		return qualityButtons[getQualityIndex()].getText().toLowerCase();
	}

	public File getSaveFile() {
		return new File(saveAsField.getText());
	}
	
	public List<String> getCommandLineBuiltinLame(File wav, String encodedBy) {
		List<String> args = new ArrayList<>();
		
		args.add("--silent");
		args.add("--preset");
		args.add(getQuality());
		if (!getSongTitle().isEmpty()) {
			args.add("--tt");
			args.add(getSongTitle());
		}
		if (!getArtist().isEmpty()) {
			args.add ("--ta");
			args.add(getArtist());
		}
		if (!getAlbum().isEmpty()) {
			args.add("--tl");
			args.add(getAlbum());
		}
		args.add("--tc");
		args.add("Encoded by: "+encodedBy);
		args.add("--nores");//no resampling
		
		args.add(wav.getAbsolutePath());
		args.add(getSaveFile().getAbsolutePath());

		return args;
	}

	private boolean validateFile() {
		File f = new File(saveAsField.getText());

		if (f.isDirectory()) {
			JOptionPane.showMessageDialog(this, UIText.get("common.mp3.specified.path.is.a.folder", f.getAbsolutePath()), UIText.get("common.mp3.invalid.file"),
					JOptionPane.ERROR_MESSAGE);
			return false;
		} else if (f.exists()) {
			int result = JOptionPane.showConfirmDialog(this, UIText.get("common.mp3.file.0.already.exists.overwrite", f.getName()),
					UIText.get("common.mp3.confirm.overwrite"), JOptionPane.YES_NO_CANCEL_OPTION);
			if (result == JOptionPane.CANCEL_OPTION || result == JOptionPane.CLOSED_OPTION) {
				setVisible(false);
				return false;
			}
			return result == JOptionPane.OK_OPTION;
		} else if (!f.getParentFile().exists()) {
			int result = JOptionPane.showConfirmDialog(this,
					UIText.get("common.mp3.folder.doesn.t.exist.create", f.getParentFile().getName()), UIText.get("common.mp3.create.directory"),
					JOptionPane.OK_CANCEL_OPTION);
			if (result == JOptionPane.OK_OPTION) {
				if (!f.getParentFile().mkdirs()) {
					JOptionPane.showMessageDialog(this, UIText.get("common.mp3.failed.to.create.parent.folder"), UIText.get("common.mp3.failed.to.create.folder"),
							JOptionPane.ERROR_MESSAGE);
					return false;
				}
				return true;
			} else {
				return false;
			}
		} else {
			return true;
		}
	}

	public void addActionListener(ActionListener listener) {
		actionListeners.add(listener);
	}

	public void removeActionListener(ActionListener listener) {
		actionListeners.remove(listener);
	}

	private void fireActionPerformed() {
		ActionEvent e = new ActionEvent(this, 0, null);
		for (ActionListener listener : actionListeners) {
			listener.actionPerformed(e);
		}
	}
}
