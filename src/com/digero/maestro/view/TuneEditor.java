package com.digero.maestro.view;

import static javax.swing.SwingConstants.CENTER;

import java.awt.Point;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import com.digero.common.util.Listener;
import com.digero.common.util.ParseException;
import com.digero.common.view.PatchedJScrollPane;
import com.digero.maestro.abc.AbcSong;
import com.digero.maestro.abc.AbcSongEvent;
import com.digero.maestro.abc.TuneLine;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;

public class TuneEditor {

	private static Point lastLocation = new Point(100, 100);
	private static JDialog openDialog = null;

	public static void show(JFrame jf, AbcSong abcSong) {
		if (openDialog != null)
			return;

		@SuppressWarnings("serial")
		class TuneDialog extends JDialog {
			
			public int numberOfSections = SectionEditor.numberOfSectionsMax;

			private final double[] LAYOUT_COLS = new double[] { TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED };
			private double[] LAYOUT_ROWS;
			private AbcSong abcSong;

			private List<TuneEditorLine> tuneInputs = new ArrayList<>(numberOfSections);

			JButton copySections = new JButton("Copy");
			JButton pasteSections = new JButton("Paste");

			public TuneDialog(JFrame jf, String title, boolean modal, AbcSong abcSong) throws ParseException {
				super(jf, title, modal);
				this.abcSong = abcSong;

				abcSong.addSongListener(songListener);

				// Add support for using spacebar for pause/play.
				ActionListener spaceBarListener = ae -> {
					// Not pretty but is what I got to work
					// jf.getRootPane().dispatchEvent(ae);
					ActionListener al = jf.getRootPane().getActionForKeyStroke(KeyStroke.getKeyStroke(' '));
					if (al != null)
						al.actionPerformed(ae);
				};
				this.getRootPane().registerKeyboardAction(spaceBarListener, KeyStroke.getKeyStroke(' '),
						JComponent.WHEN_IN_FOCUSED_WINDOW);

				TuneDialog.this.addWindowListener(new WindowAdapter() {

					@Override
					public void windowClosing(WindowEvent we) {
						TuneEditor.lastLocation = TuneDialog.this.getLocation();
						abcSong.removeSongListener(songListener);
						openDialog = null;
					}
				});
				double rowHeight = TableLayout.PREFERRED;
//				double auxHeight = TableLayout.PREFERRED;
//				int rowHeight = 16;
//				int auxHeight = 24;
//				Font font = UIManager.getFont("defaultFont");
//				Graphics graphics = jf.getGraphics();
//				if (font != null && graphics != null) // Using a flat theme - resize panel based on text size
//				{
//					FontMetrics metrics = graphics.getFontMetrics(font);
//					int fontHeight = metrics.getHeight();
//					rowHeight = fontHeight + Math.max(4, (int) (fontHeight * 0.3));
//					auxHeight = (int) (rowHeight * 1.5);
//				}

				JPanel panel = new JPanel();

				LAYOUT_ROWS = new double[3 + numberOfSections + 1 + 10];
				LAYOUT_ROWS[0] = TableLayoutConstants.PREFERRED;
				LAYOUT_ROWS[1] = 20;
				LAYOUT_ROWS[2] = TableLayoutConstants.PREFERRED;
				for (int l = 0; l < numberOfSections; l++) {
					LAYOUT_ROWS[3 + l] = rowHeight;
				}
				LAYOUT_ROWS[3 + numberOfSections] = TableLayoutConstants.PREFERRED;
				LAYOUT_ROWS[4 + numberOfSections] = TableLayoutConstants.PREFERRED;
				LAYOUT_ROWS[5 + numberOfSections] = TableLayoutConstants.PREFERRED;
				/*
				 * LAYOUT_ROWS[6+numberOfSections] = TableLayoutConstants.PREFERRED;
				 * LAYOUT_ROWS[7+numberOfSections] = TableLayoutConstants.PREFERRED;
				 * LAYOUT_ROWS[8+numberOfSections] = TableLayoutConstants.PREFERRED;
				 * LAYOUT_ROWS[9+numberOfSections] = TableLayoutConstants.PREFERRED;
				 * LAYOUT_ROWS[10+numberOfSections] = TableLayoutConstants.PREFERRED;
				 * LAYOUT_ROWS[11+numberOfSections] = TableLayoutConstants.PREFERRED;
				 * LAYOUT_ROWS[12+numberOfSections] = TableLayoutConstants.PREFERRED;
				 * LAYOUT_ROWS[13+numberOfSections] = TableLayoutConstants.PREFERRED;
				 */

				TableLayout layout = new TableLayout(LAYOUT_COLS, LAYOUT_ROWS);
//				int vg = layout.getVGap();
//				int w = 25 * rowHeight;
//				// int h = 153+(rowHeight+0)*numberOfSections;
//				int h = (numberOfSections) * rowHeight + 4 * auxHeight
//						+ (3 + numberOfSections) * vg;
//				this.setSize(w, h);

				panel.setLayout(layout);
				// panel.add(new JLabel("<html><b> " + abcSong.getTitle() + "</html>"), "0, 0,
				// 6, 0, C, C");
				panel.add(new JLabel("Enable"), "0, 2, c, c");
				panel.add(new JLabel("From bar"), "1, 2, c, c");
				panel.add(new JLabel("To bar"), "2, 2, c, c");
				panel.add(new JLabel("Seminote"), "3, 2, c, c");
				panel.add(new JLabel("Tempo"), "4, 2, c, c");
				panel.add(new JLabel("Fade %"), "5, 2, c, c");
				// panel.add(new JLabel("Remove"), "4, 2, c, c");

				for (int j = 0; j < numberOfSections; j++) {
					TuneEditorLine l = new TuneEditorLine();
					tuneInputs.add(l);
				}

				SortedMap<Integer, TuneLine> tree = abcSong.tuneBars;
				if (tree != null) {
					processTree(tree);
				}

				addTooltips(panel);

				copySections.getModel().addActionListener(e -> {
					SectionEditor.clipboardStart = new String[numberOfSections];
					SectionEditor.clipboardEnd = new String[numberOfSections];
					SectionEditor.clipboardEnabled = new boolean[numberOfSections];
					for (int i = 0; i < numberOfSections; i++) {
						SectionEditor.clipboardStart[i] = tuneInputs.get(i).barA.getText();
						SectionEditor.clipboardEnd[i] = tuneInputs.get(i).barB.getText();
						SectionEditor.clipboardEnabled[i] = tuneInputs.get(i).enable.isSelected();
					}
					SectionEditor.clipboardArmed = true;
					pasteSections.setEnabled(SectionEditor.clipboardArmed);
				});
				copySections.setToolTipText("<html><b> Copy the section starts and ends.</html>");
				panel.add(copySections, "1," + (3 + numberOfSections) + ",1,"
						+ (3 + numberOfSections) + ",f,f");

				pasteSections.getModel().addActionListener(e -> {
					if (!SectionEditor.clipboardArmed)
						return;
					int copySize = SectionEditor.clipboardStart.length;
					for (int i = 0; i < copySize; i++) {
						tuneInputs.get(i).barA.setText(SectionEditor.clipboardStart[i]);
						tuneInputs.get(i).barB.setText(SectionEditor.clipboardEnd[i]);
						tuneInputs.get(i).enable.setSelected(SectionEditor.clipboardEnabled[i]);
					}
					for (int i = copySize; i < numberOfSections; i++) {
						tuneInputs.get(i).barA.setText("0");
						tuneInputs.get(i).barB.setText("0");
						tuneInputs.get(i).enable.setSelected(false);
					}
				});
				pasteSections.setToolTipText("<html><b> Paste the section starts and ends.</html>");
				panel.add(pasteSections, "2," + (3 + numberOfSections) + ",2,"
						+ (3 + numberOfSections) + ",f,f");
				pasteSections.setEnabled(SectionEditor.clipboardArmed);

				JTextField help = new JTextField("Help");
				help.setEditable(false);
				help.setHorizontalAlignment(CENTER);
				help.setToolTipText(
						"<html><b>Enabled sections must have no overlap.<br>Bar numbers are inclusive and use original MIDI bars.<br>"
								+ "No decimal numbers allowed, only whole numbers.<br>Bar numbers must be positive and greater than zero.<br>"
								+ "Clicking APPLY will also disable faulty sections.<br><br>Warning: If 'Remove initial silence' is enabled or the<br>"
								+ "meter is modified, then the bar counter in lower-right might<br>not match up, unless your preview mode is in 'Original'.</b></html>");
				panel.add(help, "3," + (3 + numberOfSections) + ", 3, "
						+ (3 + numberOfSections) + ",f,f");
				
				Integer firstBar = abcSong.getFirstBar();
				JTextField startSong = new JTextField(firstBar==null?"1":Integer.toString(firstBar)); 
				JLabel startSongLabel = new JLabel("Start song in bar: ");
				JCheckBox startSongEnable = new JCheckBox("Start song late");
				
				startSong.setHorizontalAlignment(CENTER);
				
				startSongLabel.setEnabled(firstBar != null);
				startSong.setEnabled(firstBar != null);
				startSongEnable.setSelected(firstBar != null);
				
				startSongEnable.addActionListener(al -> {
					startSongLabel.setEnabled(startSongEnable.isSelected());
					startSong.setEnabled(startSongEnable.isSelected());
				});
				
				panel.add(startSongEnable, "0," + (4 + numberOfSections) + ", 1, "	+ (4 + numberOfSections) + ",f,f");
				panel.add(startSongLabel, "2," + (4 + numberOfSections) + ", 3, "	+ (4 + numberOfSections) + ",f,f");
				panel.add(startSong, "4," + (4 + numberOfSections) + ", 4, "	+ (4 + numberOfSections) + ",f,f");

				Integer lastBar = abcSong.getLastBar();
				JTextField endSong = new JTextField(lastBar==null?"100000":Integer.toString(lastBar)); 
				JLabel endSongLabel = new JLabel("End song with bar: ");
				JCheckBox endSongEnable = new JCheckBox("End song early");
				
				endSong.setHorizontalAlignment(CENTER);
				
				endSongLabel.setEnabled(lastBar != null);
				endSong.setEnabled(lastBar != null);
				endSongEnable.setSelected(lastBar != null);
				
				endSongEnable.addActionListener(al -> {
					endSongLabel.setEnabled(endSongEnable.isSelected());
					endSong.setEnabled(endSongEnable.isSelected());
				});
				
				panel.add(endSongEnable, "0," + (5 + numberOfSections) + ", 1, "	+ (5 + numberOfSections) + ",f,f");
				panel.add(endSongLabel, "2," + (5 + numberOfSections) + ", 3, "	+ (5 + numberOfSections) + ",f,f");
				panel.add(endSong, "4," + (5 + numberOfSections) + ", 4, "	+ (5 + numberOfSections) + ",f,f");				
				
				JButton okButton = new JButton("APPLY");
				numberOfSectionsFinal = numberOfSections;
				okButton.addActionListener(e -> {
					TreeMap<Integer, TuneLine> tm = new TreeMap<>();

					int lastEnd = 0;
					lastEnd = processSections(tm, lastEnd);

					if (lastEnd == 0) {
						TuneDialog.this.abcSong.tuneBars = null;
						TuneDialog.this.abcSong.tuneBarsModified = null;
					} else {
						TuneDialog.this.abcSong.tuneBars = tm;

						boolean[] booleanArray = new boolean[lastEnd + 1];
						for (int m = 0; m < lastEnd + 1; m++) {
							Entry<Integer, TuneLine> entry = tm.floorEntry(m + 1);
							booleanArray[m] = entry != null && entry.getValue().startBar <= m + 1
									&& entry.getValue().endBar >= m + 1;
						}

						TuneDialog.this.abcSong.tuneBarsModified = booleanArray;
					}
					
					try {
						if (startSongEnable.isSelected()) {
							int startBar = Integer.parseInt(startSong.getText());
							if (startBar < 1) {
								throw new NumberFormatException();
							}
							abcSong.setFirstBar(startBar);
						} else {
							abcSong.setFirstBar(null);
						}
					} catch (NumberFormatException nfe) {
						Integer firstBars = abcSong.getFirstBar();
						startSongEnable.setSelected(firstBars != null);
						startSong.setText(firstBars==null?"1":Integer.toString(firstBars));
					}
					
					try {
						if (endSongEnable.isSelected()) {
							int endBar = Integer.parseInt(endSong.getText());
							if (endBar < 1) {
								throw new NumberFormatException();
							}
							abcSong.setLastBar(endBar);
						} else {
							abcSong.setLastBar(null);
						}
					} catch (NumberFormatException nfe) {
						Integer lastBars = abcSong.getLastBar();
						endSongEnable.setSelected(lastBars != null);
						endSong.setText(lastBars==null?"100000":Integer.toString(lastBars));
					}					
					
					TuneDialog.this.abcSong.tuneEdited();
					// System.err.println(Thread.currentThread().getName());
				});
				okButton.setToolTipText(
						"<html><b> Apply the effects. </b><br> Note that non-applied effects will not be remembered when closing dialog.<br> Sections that are not enabled will likewise also not be remembered. </html>");
				panel.add(okButton, "4," + (3 + numberOfSections) + ", 4, "
						+ (3 + numberOfSections) + ",f,f");
				/*
				 * panel.add(new JLabel("Enabled sections must have no overlap."),
				 * "0,"+(6+numberOfSections)+", 4," +(6+numberOfSections)+", c, c");
				 * panel.add(new JLabel("Bar numbers are inclusive and use original MIDI bars."),
				 * "0, "+(7+numberOfSections)+", 4, "+(7+SectionEditor. numberOfSections)+", c, c");
				 * panel.add(new JLabel("No decimal numbers allowed, only whole numbers."),
				 * "0, "+(8+numberOfSections)+", 4," +(8+numberOfSections)+", c, c");
				 * panel.add(new JLabel("Bar numbers must be positive and greater than zero."),
				 * "0, "+(9+numberOfSections)+", 4," +(9+numberOfSections)+", c, c");
				 * panel.add(new JLabel("Clicking APPLY will also disable faulty sections."),
				 * "0, "+(10+numberOfSections)+", 4," +(10+numberOfSections)+", c, c");
				 * 
				 * JLabel warn1 = new JLabel("Warning: If 'Remove initial silence' is enabled or the"); JLabel warn2 =
				 * new JLabel("meter is modified, then the bar counter in lower-right might"); JLabel warn3 = new
				 * JLabel("not match up, unless your preview mode is in 'Original'."); warn1.setForeground(new
				 * Color(1f,0f,0f)); warn2.setForeground(new Color(1f,0f,0f)); warn3.setForeground(new Color(1f,0f,0f));
				 * panel.add(warn1, "0," +(11+numberOfSections)+", 4,"
				 * +(11+numberOfSections)+", c, c"); panel.add(warn2, "0,"
				 * +(12+numberOfSections)+", 4," +(12+numberOfSections)+", c, c");
				 * panel.add(warn3, "0," +(13+numberOfSections)+", 4,"
				 * +(13+numberOfSections)+", c, c");
				 */

				PatchedJScrollPane scrollPane = new PatchedJScrollPane(panel);
				scrollPane.setViewportBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
				this.getContentPane().add(scrollPane);
				this.pack();
				Window window = SwingUtilities.windowForComponent(this);
				if (window != null) {
					// Lets keep the dialog inside the screen, in case the screen changed resolution
					// since it was last popped up
					int maxX = window.getBounds().width - this.getWidth();
					int maxY = window.getBounds().height - this.getHeight();
					int x = Math.max(0, Math.min(maxX, TuneEditor.lastLocation.x));
					int y = Math.max(0, Math.min(maxY, TuneEditor.lastLocation.y));
					this.setLocation(new Point(x, y));
				} else {
					this.setLocation(TuneEditor.lastLocation);
				}
				this.setVisible(true);
				// this.setResizable(true);
				// System.err.println(Thread.currentThread().getName()); Swing event thread
			}

			private void addTooltips(JPanel panel) {
				String enable = "<html><b> Enable a specific section edit. </b><br> Pressing APPLY will disable a section if it is bad.</html>";
				String barA = "<html><b> The start bar (inclusive) where this section edit starts. </b><br> Must be above 0 and greater than the 'From bar' of previous enabled section.</html>";
				String barB = "<html><b> The end bar (inclusive) where this section edit end. </b><br> Must be equal or greater than the 'From bar'.</html>";
				String transpose = "<html><b> Transpose this area some seminotes up or down. </b><br> Enter a positive or negative number. </html>";
				// String remove = "<html><b> Remove this area. </b></html>";
				String tempo = "<html><b> Change Tempo. </b></html>";
				String fade = "<html><b> Fade in/out the volume of this section for all parts. </b><br> 0 = no fading <br> 100 = fade out full <br> -100 = fade in full <br> 150 = fade out before section ends <br> Etc. etc.. </html>";

				for (int i = 0; i < numberOfSections; i++) {
					tuneInputs.get(i).tempo.setToolTipText(tempo);
					// tuneInputs.get(i).remove.setToolTipText(remove);
					tuneInputs.get(i).transpose.setToolTipText(transpose);
					tuneInputs.get(i).barB.setToolTipText(barB);
					tuneInputs.get(i).barA.setToolTipText(barA);
					tuneInputs.get(i).enable.setToolTipText(enable);
					tuneInputs.get(i).fade.setToolTipText(fade);

					tuneInputs.get(i).barA.setHorizontalAlignment(CENTER);
					tuneInputs.get(i).barB.setHorizontalAlignment(CENTER);
					tuneInputs.get(i).transpose.setHorizontalAlignment(CENTER);
					tuneInputs.get(i).tempo.setHorizontalAlignment(CENTER);
					tuneInputs.get(i).fade.setHorizontalAlignment(CENTER);

					panel.add(tuneInputs.get(i).enable, "0," + (3 + i) + ",C,C");
					panel.add(tuneInputs.get(i).barA, "1," + (3 + i) + ",f,f");
					panel.add(tuneInputs.get(i).barB, "2," + (3 + i) + ",f,f");
					panel.add(tuneInputs.get(i).transpose, "3," + (3 + i) + ",f,f");
					panel.add(tuneInputs.get(i).tempo, "4," + (3 + i) + ",f,f");
					panel.add(tuneInputs.get(i).fade, "5," + (3 + i) + ",f,f");
					// panel.add(tuneInputs.get(i).remove, "5,"+(3+i)+",c,f");
				}
			}
			
			private final int numberOfSectionsFinal;
			
			private int processSections(TreeMap<Integer, TuneLine> tm, int lastEnd) {
				for (int k = 0; k < numberOfSectionsFinal; k++) {
					if (TuneDialog.this.tuneInputs.get(k).enable.isSelected()) {
						TuneLine ps = new TuneLine();
						try {
							ps.seminoteStep = Integer.parseInt(tuneInputs.get(k).transpose.getText());
							if (ps.seminoteStep > 36) {
								ps.seminoteStep = 36;
								tuneInputs.get(k).transpose.setText("36");
							}
							if (ps.seminoteStep < -36) {
								ps.seminoteStep = -36;
								tuneInputs.get(k).transpose.setText("-36");
							}
							ps.startBar = Integer.parseInt(tuneInputs.get(k).barA.getText());
							ps.endBar = Integer.parseInt(tuneInputs.get(k).barB.getText());
							ps.tempo = Integer.parseInt(tuneInputs.get(k).tempo.getText());
							ps.fade = Integer.parseInt(tuneInputs.get(k).fade.getText());
							// ps.remove = tuneInputs.get(k).remove.isSelected();
							lastEnd = checkForNewLastEnd(tm, lastEnd, k, ps, soFarSoGood(tm, ps));
						} catch (NumberFormatException nfe) {
							TuneDialog.this.tuneInputs.get(k).enable.setSelected(false);
						}
					}
				}
				return lastEnd;
			}

			private int checkForNewLastEnd(TreeMap<Integer, TuneLine> tm, int lastEnd, int k, TuneLine ps,
					boolean soFarSoGood) {
				if (ps.startBar > 0 && ps.startBar <= ps.endBar && soFarSoGood) {
					tm.put(ps.startBar, ps);
					if (ps.endBar > lastEnd)
						lastEnd = ps.endBar;
					ps.dialogLine = k;
				} else {
					TuneDialog.this.tuneInputs.get(k).enable.setSelected(false);
				}
				return lastEnd;
			}

			private void processTree(SortedMap<Integer, TuneLine> tree) throws ParseException {
				int number = 0;
				boolean useDialogLineNumbers = true;
				for (Entry<Integer, TuneLine> entry : tree.entrySet()) {
					TuneLine ps = entry.getValue();
					if (ps.dialogLine == -1) {
						useDialogLineNumbers = false;
					}
					if (useDialogLineNumbers) {
						number = ps.dialogLine;
					}
					if (number < 0) {
						System.err.println(
								"Line numbers was badly edited in .msx file.");
					} else if (number >= numberOfSections) {
						System.err.println(
								"Too many sections in treemap in tune-editor, or line numbers was badly edited in .msx file.");
						numberOfSections+=5;
						throw new ParseException("Dialog open failed, try again.","Non file");
					} else {
						tuneInputs.get(number).enable.setSelected(true);
						tuneInputs.get(number).barA.setText(String.valueOf(ps.startBar));
						tuneInputs.get(number).barB.setText(String.valueOf(ps.endBar));
						tuneInputs.get(number).transpose.setText(String.valueOf(ps.seminoteStep));
						tuneInputs.get(number).tempo.setText(String.valueOf(ps.tempo));
						tuneInputs.get(number).fade.setText(String.valueOf(ps.fade));
						// tuneInputs.get(number).remove.setSelected(ps.remove);
					}
					number++;
				}
			}

			private Listener<AbcSongEvent> songListener = e -> {
				switch (e.getProperty()) {
				case SONG_CLOSING:
					dispose();
					break;
				default:
					break;
				}
			};
		}

		try {
			openDialog = new TuneDialog(jf, "Tune editor", false, abcSong);
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

	private static boolean soFarSoGood(TreeMap<Integer, TuneLine> tm, TuneLine ps) {
		for (TuneLine psC : tm.values()) {
			if (!(ps.startBar > psC.endBar || ps.endBar < psC.startBar)) {
				return false;
			}
		}
		return true;
	}
}