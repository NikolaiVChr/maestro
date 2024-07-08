package com.digero.abcplayer.view;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;

public class AbcPlayerSettingsDialog extends JDialog implements TableLayoutConstants{

	private boolean success = false;
	
	private JTabbedPane tabPanel;
	
	private static final int PAD = 4;
	
	public AbcPlayerSettingsDialog(JFrame owner, Preferences abcPlayerPrefs) {
		super(owner, "More Options", true);
		
		JButton okButton = new JButton("OK");
		getRootPane().setDefaultButton(okButton);
		okButton.setMnemonic('O');
		okButton.addActionListener(e -> {
			success = true;
			AbcPlayerSettingsDialog.this.setVisible(false);
		});
		
		JButton cancelButton = new JButton("Cancel");
		cancelButton.setMnemonic('C');
		cancelButton.addActionListener(e -> {
			success = false;
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
		
		tabPanel = new JTabbedPane();
		tabPanel.addTab("More Options", createMoreOptionsPanel());
		tabPanel.setFocusable(false);
		
		JPanel mainPanel = new JPanel(new BorderLayout(PAD,PAD));
		mainPanel.add(tabPanel, BorderLayout.CENTER);
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
		final JLabel fontSizeLabel = new JLabel("Font size (requires restart)");
		final JComboBox<String> fontBox = new JComboBox<>();
		
//		fontBox.setToolTipText();
		return new JPanel();
	}
}
