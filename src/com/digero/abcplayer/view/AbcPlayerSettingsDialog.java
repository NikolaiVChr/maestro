package com.digero.abcplayer.view;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.prefs.Preferences;

import javax.swing.*;

import com.digero.common.util.Themer;

import com.digero.common.view.UIText;
import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;

public class AbcPlayerSettingsDialog extends JDialog implements TableLayoutConstants{
	
	private static final long serialVersionUID = -1718493618042918571L;

	private JTabbedPane tabPanel;
	
	private static final int PAD = 4;
	
	private final JComboBox<String> themeBox = new JComboBox<>();
	private final JComboBox<String> fontBox = new JComboBox<>();
	
	private final Preferences prefs;
	
	public AbcPlayerSettingsDialog(JFrame owner, Preferences abcPlayerPrefs) {
		super(owner, UIText.get("abcplayer.more.options"), true);
		
		prefs = abcPlayerPrefs;
		
		JButton okButton = new JButton("OK");
		getRootPane().setDefaultButton(okButton);
		okButton.setMnemonic('O');
		okButton.addActionListener(e -> {
			prefs.put("theme", (String)themeBox.getSelectedItem());
			prefs.putInt("fontSize", Integer.parseInt((String) fontBox.getSelectedItem()));
			AbcPlayerSettingsDialog.this.setVisible(false);
		});
		
		JButton cancelButton = new JButton(UIText.get("common.cancel"));
		cancelButton.setMnemonic('C');
		cancelButton.addActionListener(e -> {
			AbcPlayerSettingsDialog.this.setVisible(false);
		});
		
		JPanel buttonsPanel = new JPanel(new TableLayout(//
				new double[] { 0.5, 0.5}, //
				new double[] { PREFERRED }));
		((TableLayout) buttonsPanel.getLayout()).setHGap(PAD);
		buttonsPanel.add(okButton, "0, 0, f, f");
		buttonsPanel.add(cancelButton, "1, 0, f, f");
		JPanel buttonsContainerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, PAD / 2));
		buttonsContainerPanel.add(buttonsPanel);
		
//		tabPanel = new JTabbedPane();
//		tabPanel.addTab("More Options", createMoreOptionsPanel());
//		tabPanel.setFocusable(false);
		
		JPanel mainPanel = new JPanel(new BorderLayout(PAD,PAD));
		mainPanel.setBorder(BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD));
		mainPanel.add(createMoreOptionsPanel(), BorderLayout.CENTER);
		mainPanel.add(buttonsContainerPanel, BorderLayout.SOUTH);
		
		setContentPane(mainPanel);
		pack();
		
		if (owner != null) {
			int left = owner.getX() + (owner.getWidth() - this.getWidth()) / 2;
			int top = owner.getY() + (owner.getHeight() - this.getHeight()) / 2;
			this.setLocation(left, top);
		}
	}
	
	private JPanel createMoreOptionsPanel() {
		
		final JLabel themeLabel = new JLabel(UIText.get("abcplayer.theme.requires.restart"));
		
		themeBox.setToolTipText(
				UIText.get("abcplayer.html.select.the.theme.for.abc.player"));
		for (String theme : Themer.themes) {
			themeBox.addItem(theme);
		}
		themeBox.setEditable(false);
		themeBox.setSelectedItem(prefs.get("theme", Themer.FLAT_LIGHT_THEME));
		
		final JLabel fontSizeLabel = new JLabel(UIText.get("abcplayer.font.size.requires.restart"));
		
		fontBox.setToolTipText(
				UIText.get("abcplayer.html.select.a.font.size"));
		fontBox.setEditable(false);
		for (int i : Themer.fontSizes) {
			fontBox.addItem(Integer.toString(i));
		}
		fontBox.setSelectedItem(Integer.toString(prefs.getInt("fontSize", Themer.DEFAULT_FONT_SIZE)));

		final JCheckBox flawedMaestroCheckbox = new JCheckBox(UIText.get("abcplayer.enable.popup.flawed.maestro.versions"));
		flawedMaestroCheckbox.setSelected(prefs.getBoolean("flawedMaestroPopup", true));
		flawedMaestroCheckbox.addActionListener(e-> {
			prefs.putBoolean("flawedMaestroPopup", flawedMaestroCheckbox.isSelected());
		});
		
		TableLayout layout = new TableLayout();
		layout.insertColumn(0, FILL);
		layout.setVGap(PAD);

		JPanel panel = new JPanel(layout);
		panel.setBorder(BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD));
		
		int row = -1;
		
		layout.insertRow(++row, PREFERRED);
		panel.add(themeLabel, "0, " + row);
		
		layout.insertRow(++row, PREFERRED);
		panel.add(themeBox, "0, " + row);
		
		layout.insertRow(++row, PREFERRED);
		panel.add(fontSizeLabel, "0, " + row);
		
		layout.insertRow(++row, PREFERRED);
		panel.add(fontBox, "0, " + row);

		layout.insertRow(++row, PREFERRED);
		panel.add(flawedMaestroCheckbox, "0, " + row);
		
		return panel;
	}
}
