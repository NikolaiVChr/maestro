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

public class MaxDialog {

	protected static Point lastLocation = new Point(0, 0);
	private static JDialog maxDialog = null;

	public static void show(ProjectFrame jf, AbcPart abcPart) {
		
		@SuppressWarnings("serial")
		class MaxDialogWindow extends JDialog {

			private final double[] LAYOUT_COLS = new double[] { 0.1, 0.4, 0.4, 0.1 };
			private double[] LAYOUT_ROWS = new double[] { 0.30, 0.20, 0.20, 0.15, 0.15 };
			
			JLabel titleLabel;
			JTextField maxField;
			AbcPart abcPart;

			public MaxDialogWindow(final ProjectFrame jf, AbcPart part) {
				super(jf, "Delay Part Editor", false);
				
				abcPart = part;

				MaxDialogWindow.this.addWindowListener(new WindowAdapter() {

					@Override
					public void windowClosing(WindowEvent we) {
						MaxDialog.lastLocation = MaxDialogWindow.this.getLocation();
						maxDialog = null;
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

				maxField = new JTextField(String.format("%d", abcPart.getNoteMax()));
				maxField.setHorizontalAlignment(SwingConstants.CENTER);

				JButton okButton = new JButton("APPLY");
				okButton.addActionListener(e -> {
					try {
						int max = Integer.parseInt(maxField.getText());
						if (max >= 1 && max <= 6) {
							abcPart.setNoteMax(max);
							abcPart.maxEdited();
						}
					} catch (NumberFormatException nfe) {

					}
					maxField.setText(String.format("%d", abcPart.getNoteMax()));
				});
				titleLabel = new JLabel("<html><b> Max notes in " + abcPart.toString() + " </html>");
				panel.add(titleLabel, "0, 0, 3, 0, C, C");
				panel.add(maxField, "1, 1, f, f");
				panel.add(new JLabel("Notes"), "2, 1, C, C");
				panel.add(okButton, "1, 2, f, f");
				panel.add(new JLabel("Put a max from 1 to 6 on a part."), "0, 3, 3, 3, C, C");
				panel.add(new JLabel("The effect wont be shown graphically"), "0, 4, 3, 4, C, C");
				maxField.setToolTipText("Max concurrent notes");
				
				

				this.getContentPane().add(panel);
				if (MaxDialog.lastLocation.x == 0 && MaxDialog.lastLocation.y == 0) {
					this.setLocationRelativeTo(null);
				}
				else {
					this.setLocation(MaxDialog.lastLocation);	
				}
				this.setVisible(true);
			}
			
		    private Listener<AbcPartEvent> abcPartListener = e -> {
				if (e.getProperty() == AbcPartProperty.TITLE && e.getSource() == abcPart) {
					titleLabel.setText("<html><b> Max notes in " + abcPart.toString() + " </html>");
				}
			};

		    private Listener<AbcSongEvent> songListener = e -> {
				switch (e.getProperty()) {
					case BEFORE_PART_REMOVED:
						AbcPart deleted = e.getPart();
						if (deleted.equals(abcPart)) {
							// The abcPart for this editor is being deleted, lets close the dialog.
							dispose();
							maxDialog = null;
						}
						break;
					case SONG_CLOSING:
						dispose();
						maxDialog = null;
						break;
					default:
						break;
				}
			};
			
			// Support clicking max button while dialog is already opened to change the pointed-to part
			public void changeAbcPart(AbcPart newPart) {
				if (abcPart.getAbcSong() != null) {
					abcPart.getAbcSong().removeSongListener(songListener);
				}
				abcPart.removeAbcListener(abcPartListener);
				
				abcPart = newPart;
				
				abcPart.addAbcListener(abcPartListener);
				abcPart.getAbcSong().addSongListener(songListener);
				maxField.setText(String.format("%d", abcPart.getNoteMax()));
				titleLabel.setText("<html><b> Max notes in " + abcPart.toString() + " </html>");
			}
		}
		
		if (maxDialog != null) {
			((MaxDialogWindow)maxDialog).changeAbcPart(abcPart);
		}
		else {
			maxDialog = new MaxDialogWindow(jf, abcPart);	
		}
	}

}