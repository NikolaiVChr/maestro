package com.digero.abcplayer.view;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import com.digero.common.util.Themer;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;

public class AbcPlayerSettingsDialog extends JDialog implements TableLayoutConstants{
	
	private JTabbedPane tabPanel;
	
	private static final int PAD = 4;
	
	private final JComboBox<String> themeBox = new JComboBox<>();
	private final JComboBox<String> fontBox = new JComboBox<>();
	
	private final Preferences prefs;
	
	public AbcPlayerSettingsDialog(JFrame owner, Preferences abcPlayerPrefs) {
		super(owner, "More Options", true);
		
		prefs = abcPlayerPrefs;
		
		JButton okButton = new JButton("OK");
		getRootPane().setDefaultButton(okButton);
		okButton.setMnemonic('O');
		okButton.addActionListener(e -> {
			prefs.put("theme", (String)themeBox.getSelectedItem());
			prefs.putInt("fontSize", Integer.parseInt((String) fontBox.getSelectedItem()));
			System.out.println("test");
			AbcPlayerSettingsDialog.this.setVisible(false);
		});
		
		JButton cancelButton = new JButton("Cancel");
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
		
		final JLabel themeLabel = new JLabel("Theme (Requires restart):");
		
		themeBox.setToolTipText(
				"<html>Select the theme for Maestro. Must restart Maestro for it to take effect.</html>");
		for (String theme : Themer.themes) {
			themeBox.addItem(theme);
		}
		themeBox.setEditable(false);
		themeBox.setSelectedItem(prefs.get("theme", Themer.FLAT_LIGHT_THEME));
		
		final JLabel fontSizeLabel = new JLabel("Font size (requires restart)");
		
		fontBox.setToolTipText(
				"<html>Select a font size. Must restart Maestro for it to take effect.</html>");
		fontBox.setEditable(false);
		for (int i : Themer.fontSizes) {
			fontBox.addItem(Integer.toString(i));
		}
		fontBox.setSelectedItem(Integer.toString(prefs.getInt("fontSize", Themer.DEFAULT_FONT_SIZE)));
		
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
		
		return panel;
	}
}
