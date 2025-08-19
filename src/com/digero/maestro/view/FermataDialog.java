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

@Deprecated
public class FermataDialog {

	protected static Point lastLocation = new Point(0, 0);
	private static JDialog conclusionFermataDialog = null;

	public static void show(ProjectFrame jf, AbcPart abcPart) {
		
		@SuppressWarnings("serial")
		class ConclusionFermataDialogWindow extends JDialog {

			private final double[] LAYOUT_COLS = new double[] { 0.1, 0.4, 0.4, 0.1 };
			private double[] LAYOUT_ROWS = new double[] { 0.30, 0.20, 0.20, 0.15, 0.15 };
			
			JLabel titleLabel;
			JTextField conclusionFermataField;
			AbcPart abcPart;

			public ConclusionFermataDialogWindow(final ProjectFrame jf, AbcPart part) {
				super(jf, "Conclusion Fermata Part Editor", false);
				
				abcPart = part;

				ConclusionFermataDialogWindow.this.addWindowListener(new WindowAdapter() {

					@Override
					public void windowClosing(WindowEvent we) {
						FermataDialog.lastLocation = ConclusionFermataDialogWindow.this.getLocation();
						conclusionFermataDialog = null;
						jf.updatePartEditorButton();
						if (abcPart.getAbcSong() != null) {
							abcPart.getAbcSong().removeSongListener(songListener);
						}
						abcPart.removeAbcListener(abcPartListener);
					}
				});
				
				abcPart.addAbcListener(abcPartListener);
				abcPart.getAbcSong().addSongListener(songListener);

				this.setSize(315, 170);
				JPanel panel = new JPanel();

				panel.setLayout(new TableLayout(LAYOUT_COLS, LAYOUT_ROWS));

				conclusionFermataField = new JTextField(String.format("%.3f", abcPart.conclusionFermata * 0.001f));
				conclusionFermataField.setHorizontalAlignment(SwingConstants.CENTER);

				JButton okButton = new JButton("APPLY");
				okButton.addActionListener(e -> {
					try {
						float conclusionFermata = Float.parseFloat(conclusionFermataField.getText().replace(',', '.'));
						if (conclusionFermata >= 0.000f && conclusionFermata <= 5.00f) {
							abcPart.conclusionFermata = (int) (conclusionFermata * 1000);
							abcPart.conclusionFermataEdited();
						}
					} catch (NumberFormatException nfe) {

					}
					conclusionFermataField.setText(String.format("%.3f", abcPart.conclusionFermata * 0.001f));
				});
				titleLabel = new JLabel("<html><b> Conclusion fermata on " + abcPart.toString() + " </html>");
				panel.add(titleLabel, "0, 0, 3, 0, C, C");
				panel.add(conclusionFermataField, "1, 1, f, f");
				panel.add(new JLabel("Seconds"), "2, 1, C, C");
				panel.add(okButton, "1, 2, f, f");
				panel.add(new JLabel("Put a conclusion fermata from 0s to 5.00s on a part."), "0, 3, 3, 3, C, C");
				panel.add(new JLabel("Do nothing if note not sustained by chosen instrument."), "0, 4, 3, 4, C, C");
				conclusionFermataField.setToolTipText("Seconds of fermata");
				
				

				this.getContentPane().add(panel);
				if (FermataDialog.lastLocation.x == 0 && FermataDialog.lastLocation.y == 0) {
					this.setLocationRelativeTo(null);
				}
				else {
					this.setLocation(FermataDialog.lastLocation);	
				}
				this.setVisible(true);
			}
			
		    private Listener<AbcPartEvent> abcPartListener = e -> {
				if (e.getProperty() == AbcPartProperty.TITLE && e.getSource() == abcPart) {
					titleLabel.setText("<html><b> Conclusion fermata on " + abcPart.toString() + " </html>");
				}
			};

		    private Listener<AbcSongEvent> songListener = e -> {
				switch (e.getProperty()) {
					case BEFORE_PART_REMOVED:
						AbcPart deleted = e.getPart();
						if (deleted.equals(abcPart)) {
							// The abcPart for this editor is being deleted, lets close the dialog.
							dispose();
							conclusionFermataDialog = null;
						}
						break;
					case SONG_CLOSING:
						dispose();
						conclusionFermataDialog = null;
						break;
					default:
						break;
				}
			};
			
			// Support clicking conclusionFermata button while dialog is already opened to change the pointed-to part
			public void changeAbcPart(AbcPart newPart) {
				if (abcPart.getAbcSong() != null) {
					abcPart.getAbcSong().removeSongListener(songListener);
				}
				abcPart.removeAbcListener(abcPartListener);
				
				abcPart = newPart;
				
				abcPart.addAbcListener(abcPartListener);
				abcPart.getAbcSong().addSongListener(songListener);
				conclusionFermataField.setText(String.format("%.3f", abcPart.conclusionFermata * 0.001f));
				titleLabel.setText("<html><b> Conclusion fermata on " + abcPart.toString() + " </html>");
			}
		}
		
		if (conclusionFermataDialog != null) {
			((ConclusionFermataDialogWindow)conclusionFermataDialog).changeAbcPart(abcPart);
		}
		else {
			conclusionFermataDialog = new ConclusionFermataDialogWindow(jf, abcPart);	
		}
	}

}