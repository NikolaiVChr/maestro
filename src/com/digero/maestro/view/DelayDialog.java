package com.digero.maestro.view;

import java.awt.Point;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import com.digero.common.util.Listener;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcPartEvent;
import com.digero.maestro.abc.AbcPartEvent.AbcPartProperty;
import com.digero.maestro.abc.AbcSongEvent;

import info.clearthought.layout.TableLayout;

public class DelayDialog {

	protected static Point lastLocation = new Point(0, 0);
	private static JDialog delayDialog = null;

	public static void show(ProjectFrame jf, AbcPart abcPart) {
		
		@SuppressWarnings("serial")
		class DelayDialogWindow extends JDialog {

			private final double[] LAYOUT_COLS = new double[] { 0.1, 0.4, 0.4, 0.1 };
			private double[] LAYOUT_ROWS = new double[] { 0.30, 0.20, 0.20, 0.15, 0.15 };
			
			JLabel titleLabel;
			JTextField delayField;
			AbcPart abcPart;

			public DelayDialogWindow(final ProjectFrame jf, AbcPart part) {
				super(jf, "Delay Part Editor", false);
				
				abcPart = part;

				DelayDialogWindow.this.addWindowListener(new WindowAdapter() {

					@Override
					public void windowClosing(WindowEvent we) {
						DelayDialog.lastLocation = DelayDialogWindow.this.getLocation();
						delayDialog = null;
						jf.updateDelayButton();
						if (abcPart.getAbcSong() != null) {
							abcPart.getAbcSong().removeSongListener(songListener);
						}
						abcPart.removeAbcListener(abcPartListener);
					}
				});
				
				abcPart.addAbcListener(abcPartListener);
				abcPart.getAbcSong().addSongListener(songListener);

				this.setSize(250, 170);
				JPanel panel = new JPanel();

				panel.setLayout(new TableLayout(LAYOUT_COLS, LAYOUT_ROWS));

				delayField = new JTextField(String.format("%.3f", abcPart.delay * 0.001f));
				delayField.setHorizontalAlignment(SwingConstants.CENTER);

				JButton okButton = new JButton("APPLY");
				okButton.addActionListener(e -> {
					try {
						float delay = Float.parseFloat(delayField.getText().replace(',', '.'));
						if (delay >= 0.000f && delay <= 1.00f) {
							abcPart.delay = (int) (delay * 1000);
							abcPart.delayEdited();
						}
					} catch (NumberFormatException nfe) {

					}
					delayField.setText(String.format("%.3f", abcPart.delay * 0.001f));
				});
				titleLabel = new JLabel("<html><b> Delay on " + abcPart.toString() + " </html>");
				panel.add(titleLabel, "0, 0, 3, 0, C, C");
				panel.add(delayField, "1, 1, f, f");
				panel.add(new JLabel("Seconds"), "2, 1, C, C");
				panel.add(okButton, "1, 2, f, f");
				panel.add(new JLabel("Put a delay from 0s to 1.00s on a part."), "0, 3, 3, 3, C, C");
				panel.add(new JLabel("Have no effect if tempo lower than 50."), "0, 4, 3, 4, C, C");
				delayField.setToolTipText("Seconds of delay");
				
				

				this.getContentPane().add(panel);
				if (DelayDialog.lastLocation.x == 0 && DelayDialog.lastLocation.y == 0) {
					this.setLocationRelativeTo(null);
				}
				else {
					this.setLocation(DelayDialog.lastLocation);	
				}
				this.setVisible(true);
			}
			
		    private Listener<AbcPartEvent> abcPartListener = e -> {
				if (e.getProperty() == AbcPartProperty.TITLE && e.getSource() == abcPart) {
					titleLabel.setText("<html><b> Delay on " + abcPart.toString() + " </html>");
				}
			};

		    private Listener<AbcSongEvent> songListener = e -> {
				switch (e.getProperty()) {
					case BEFORE_PART_REMOVED:
						AbcPart deleted = e.getPart();
						if (deleted.equals(abcPart)) {
							// The abcPart for this editor is being deleted, lets close the dialog.
							dispose();
							delayDialog = null;
						}
						break;
					case SONG_CLOSING:
						dispose();
						delayDialog = null;
						break;
					default:
						break;
				}
			};
			
			// Support clicking delay button while dialog is already opened to change the pointed-to part
			public void changeAbcPart(AbcPart newPart) {
				if (abcPart.getAbcSong() != null) {
					abcPart.getAbcSong().removeSongListener(songListener);
				}
				abcPart.removeAbcListener(abcPartListener);
				
				abcPart = newPart;
				
				abcPart.addAbcListener(abcPartListener);
				abcPart.getAbcSong().addSongListener(songListener);
				delayField.setText(String.format("%.3f", abcPart.delay * 0.001f));
				titleLabel.setText("<html><b> Delay on " + abcPart.toString() + " </html>");
			}
		}
		
		if (delayDialog != null) {
			((DelayDialogWindow)delayDialog).changeAbcPart(abcPart);
		}
		else {
			delayDialog = new DelayDialogWindow(jf, abcPart);	
		}
	}

}