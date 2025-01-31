package com.digero.maestro.view;

import static javax.swing.SwingConstants.CENTER;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
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
import javax.swing.JTabbedPane;
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

	private static Point lastLocation = null;
	private static JDialog openDialog = null;
	final static double[] LAYOUT_COLS_TABS = new double[] { 0.166, 0.166, 0.166, 0.166, 0.166, 0.17};
	public static final int numberOfSectionsMax = 20;

	public static void show(JFrame jf, AbcSong abcSong) {
		if (openDialog != null)
			return;

		@SuppressWarnings("serial")
		class TuneDialog extends JDialog {
			
			public int numberOfSections = 8;

			private final double[] LAYOUT_COLS = new double[] { TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED };
			private double[] LAYOUT_ROWS;
			private AbcSong abcSong;

			private List<TuneEditorLine> tuneInputs = new ArrayList<>(numberOfSections);

			JButton copySections = new JButton("Copy");
			JButton pasteSections = new JButton("Paste");
			
			private JPanel miscPanel;
			private JPanel tempoPanel;
			
			private final JButton add1 = new JButton("Add");

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
						freeWindow();
					}
					
					@Override
					public void windowClosed(WindowEvent we) {
						freeWindow();
					}
					
					private void freeWindow() {
						if (openDialog == null) {
							return;
						}
						TuneEditor.lastLocation = TuneDialog.this.getLocation();
						abcSong.removeSongListener(songListener);
						openDialog = null;
					}
				});
				

				JPanel panel = new JPanel();

				LAYOUT_ROWS = new double[5];
				LAYOUT_ROWS[0] = TableLayoutConstants.PREFERRED;
				LAYOUT_ROWS[1] = TableLayoutConstants.PREFERRED;
				LAYOUT_ROWS[2] = TableLayoutConstants.PREFERRED;
				LAYOUT_ROWS[3] = TableLayoutConstants.PREFERRED;
				LAYOUT_ROWS[4] = TableLayoutConstants.PREFERRED;

				TableLayout layout = new TableLayout(LAYOUT_COLS, LAYOUT_ROWS);

				panel.setLayout(layout);
				
				JTabbedPane tabPanel = new JTabbedPane();
				tabPanel.setTabPlacement(JTabbedPane.TOP);
				
				miscPanel = new JPanel();
				tempoPanel = new JPanel();
				
				
				panel.add(tabPanel, "0, 1, 5, 1, f, f");
				
				tabPanel.addTab("Pitch & Vol", miscPanel);
				tabPanel.addTab("Tempo", tempoPanel);
				
				SortedMap<Float, TuneLine> tree = abcSong.tuneBars;
				if (tree != null) {
					processTree(tree);
				}
				
				while (tuneInputs.size() <= numberOfSections) {
					makeNewTuneLine();
				}
				layoutTabs();
				addTuneLines();

				copySections.getModel().addActionListener(e -> {
					SectionEditor.clipboardStart = new String[numberOfSections];
					SectionEditor.clipboardEnd = new String[numberOfSections];
					SectionEditor.clipboardEnabled = new boolean[numberOfSections];
					for (int i = 0; i < numberOfSections; i++) {
						SectionEditor.clipboardStart[i] = tuneInputs.get(i).barA[0].getText();
						SectionEditor.clipboardEnd[i] = tuneInputs.get(i).barB[0].getText();
						SectionEditor.clipboardEnabled[i] = tuneInputs.get(i).enable[0].isSelected();
					}
					SectionEditor.clipboardArmed = true;
					pasteSections.setEnabled(SectionEditor.clipboardArmed);
				});
				copySections.setToolTipText("<html><b> Copy the section starts and ends.</html>");
				panel.add(copySections, "1,2,1,2,f,f");

				pasteSections.getModel().addActionListener(e -> {
					if (!SectionEditor.clipboardArmed)
						return;
					int copySize = SectionEditor.clipboardStart.length;
					for (int i = 0; i < copySize; i++) {
						while (i + 1 > numberOfSections) addTuneLine(add1);
						tuneInputs.get(i).barA[0].setText(SectionEditor.clipboardStart[i]);
						tuneInputs.get(i).barB[0].setText(SectionEditor.clipboardEnd[i]);
						// The other tabs; barA and B will be autoset by listeners
						tuneInputs.get(i).enable[0].setSelected(SectionEditor.clipboardEnabled[i]);
						tuneInputs.get(i).enable[1].setSelected(SectionEditor.clipboardEnabled[i]);
					}
					for (int i = copySize; i < numberOfSections; i++) {
						tuneInputs.get(i).barA[0].setText("0.0");
						tuneInputs.get(i).barB[0].setText("0.0");
						// The other tabs; barA and B will be autoset by listeners
						tuneInputs.get(i).enable[0].setSelected(false);
						tuneInputs.get(i).enable[1].setSelected(false);
					}
				});
				pasteSections.setToolTipText("<html><b> Paste the section starts and ends.</html>");
				panel.add(pasteSections, "2,2,2,2,f,f");
				pasteSections.setEnabled(SectionEditor.clipboardArmed);

				JTextField help = new JTextField("Help");
				help.setEditable(false);
				help.setHorizontalAlignment(CENTER);
				help.setToolTipText(
						"<html><b>Enabled sections must have no overlap.<br>Bar numbers use original MIDI bars.<br>"
								+ "Decimal bar numbers allowed.<br>Bar numbers must not be negative.<br>"
								+ "Bar numbers from are inclusive, to are exclusive.<br>"
								+ "Clicking APPLY will also disable faulty sections.<br><br>Warning: If 'Remove initial silence' is enabled or the<br>"
								+ "meter is modified, then the bar counter in lower-right might<br>not match up, unless your preview mode is in 'Original'.</b></html>");
				panel.add(help, "3,2, 3, 2,f,f");
				
				add1.setToolTipText("Add 1 section line");
				add1.addActionListener(e -> {
					addTuneLine(add1);
				});
				panel.add(add1, "0,2, 0, 2,f,f");
				add1.setEnabled(numberOfSections < numberOfSectionsMax);
				
				Float firstBar = abcSong.getFirstBar();
				JTextField startSong = new JTextField(firstBar==null?"0.0":Float.toString(firstBar)); 
				JLabel startSongLabel = new JLabel("Start song at: ");
				JCheckBox startSongEnable = new JCheckBox("Start song late");
				
				startSong.setHorizontalAlignment(CENTER);
				
				startSongLabel.setEnabled(firstBar != null);
				startSong.setEnabled(firstBar != null);
				startSongEnable.setSelected(firstBar != null);
				
				startSongEnable.addActionListener(al -> {
					startSongLabel.setEnabled(startSongEnable.isSelected());
					startSong.setEnabled(startSongEnable.isSelected());
				});
				
				panel.add(startSongEnable, "0,3, 1, 3,f,f");
				panel.add(startSongLabel, "2,3, 3, 3,f,f");
				panel.add(startSong, "4,3, 4, 3,f,f");

				Float lastBar = abcSong.getLastBar();
				JTextField endSong = new JTextField(lastBar==null?"100000.0":Float.toString(lastBar)); 
				JLabel endSongLabel = new JLabel("End song at: ");
				JCheckBox endSongEnable = new JCheckBox("End song early");
				
				endSong.setHorizontalAlignment(CENTER);
				
				endSongLabel.setEnabled(lastBar != null);
				endSong.setEnabled(lastBar != null);
				endSongEnable.setSelected(lastBar != null);
				
				endSongEnable.addActionListener(al -> {
					endSongLabel.setEnabled(endSongEnable.isSelected());
					endSong.setEnabled(endSongEnable.isSelected());
				});
				
				panel.add(endSongEnable, "0,4, 1, 4,f,f");
				panel.add(endSongLabel, "2,4, 3, 4,f,f");
				panel.add(endSong, "4,4, 4, 4,f,f");
								
				/*
				 * To do sort and pack, best expand SectionEditorLine to be comparable and have 3 JPanels in it.
				 */
				JButton sort = new JButton("Sort");
				sort.setToolTipText("Sort tune lines.");
				sort.addActionListener(e -> {
					Collections.sort(tuneInputs);
					addTuneLines();
					repaint();
				});
				panel.add(sort, "4,2, 4, 2,f,f");
				
				JButton okButton = new JButton("APPLY");
				okButton.addActionListener(e -> {
					TreeMap<Float, TuneLine> tm = new TreeMap<>();

					float lastEnd = 0;
					lastEnd = processSections(tm, lastEnd);

					if (lastEnd == 0) {
						TuneDialog.this.abcSong.tuneBars = null;
						TuneDialog.this.abcSong.tuneBarsModified = null;
					} else {
						TuneDialog.this.abcSong.tuneBars = tm;

						boolean[] booleanArray = new boolean[(int)(lastEnd) + 1];
						for (int m = 0; m < (int)(lastEnd) + 1; m++) {
							Entry<Float, TuneLine> entry = tm.lowerEntry(m + 1.0f);
							booleanArray[m] = entry != null && entry.getValue().startBar < m + 1
									&& entry.getValue().endBar > m;
						}

						TuneDialog.this.abcSong.tuneBarsModified = booleanArray;
					}
					
					try {
						if (startSongEnable.isSelected()) {
							float startBar = Float.parseFloat(startSong.getText().replace(",", "."));
							if (startBar < 0.0f) {
								throw new NumberFormatException();
							}
							abcSong.setFirstBar(startBar);
						} else {
							abcSong.setFirstBar(null);
						}
					} catch (NumberFormatException nfe) {
						Float firstBars = abcSong.getFirstBar();
						startSongEnable.setSelected(firstBars != null);
						startSong.setText(firstBars==null?"0.0":Float.toString(firstBars));
					}
					
					try {
						if (endSongEnable.isSelected()) {
							float endBar = Float.parseFloat(endSong.getText().replace(",", "."));
							if (endBar <= 0.0f) {
								throw new NumberFormatException();
							}
							abcSong.setLastBar(endBar);
						} else {
							abcSong.setLastBar(null);
						}
					} catch (NumberFormatException nfe) {
						Float lastBars = abcSong.getLastBar();
						endSongEnable.setSelected(lastBars != null);
						endSong.setText(lastBars==null?"100000.0":Float.toString(lastBars));
					}					
					
					TuneDialog.this.abcSong.tuneEdited();
					// System.err.println(Thread.currentThread().getName());
				});
				okButton.setToolTipText(
						"<html><b> Apply the effects. </b><br> Note that non-applied effects will not be remembered when closing dialog.<br> Sections that are not enabled will likewise also not be remembered. </html>");
				panel.add(okButton, "5,2, 5, 2,f,f");
				
				PatchedJScrollPane scrollPane = new PatchedJScrollPane(panel);
				scrollPane.setViewportBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
				
				this.getContentPane().add(scrollPane);
				panel.revalidate();
				this.pack();
				if (lastLocation == null) { // First launch of tune editor, center it on maestro window
					this.setLocationRelativeTo(jf);
				} else {
					// Ensure that window is on screen fully if monitors or resolution changed
					GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
					GraphicsDevice devices[] = ge.getScreenDevices();
					Rectangle bounds = this.getBounds();
					bounds.x = lastLocation.x;
					bounds.y = lastLocation.y;
					int areaOnScreen = 0;
					for (GraphicsDevice d : devices) {
						Rectangle screenBounds = d.getDefaultConfiguration().getBounds();
						if (bounds.intersects(screenBounds)) {
							Rectangle inter = bounds.intersection(screenBounds);
							areaOnScreen += inter.width * inter.height;
						}
					}
					if (areaOnScreen == bounds.width * bounds.height) {
						this.setLocation(lastLocation);
					} else {
						this.setLocationRelativeTo(jf);
					}
				}
				this.setVisible(true);
				this.pack();
				this.repaint();
				// this.setResizable(true);
			}
			
			private void addTuneLine(JButton add1) {
				if (numberOfSections < numberOfSectionsMax) {
					numberOfSections += 1;
					makeNewTuneLine();
					layoutTabs();
					addTuneLines();
					invalidate();
					pack();
					repaint();
				}
				add1.setEnabled(numberOfSections < numberOfSectionsMax);
				add1.repaint();
			}

			private void makeNewTuneLine() {
				TuneEditorLine l = new TuneEditorLine();
				tuneInputs.add(l);
			}
			
			private void layoutTabs() {
				double[] LAYOUT_ROWS_NEW = tabsRows();
				TableLayout miscLayout = new TableLayout(LAYOUT_COLS_TABS, LAYOUT_ROWS_NEW);
				TableLayout tempoLayout = new TableLayout(LAYOUT_COLS_TABS, LAYOUT_ROWS_NEW);
				
				miscPanel.setLayout(miscLayout);
				tempoPanel.setLayout(tempoLayout);
				
				miscPanel.removeAll();
				tempoPanel.removeAll();
				
				addTitlesToTabs();
			}
			
			private double[] tabsRows() {
				double[] LAYOUT_ROWS_TAB = new double[2 + numberOfSections];
				for (int i = 0; i < numberOfSections + 2; i++) {
					LAYOUT_ROWS_TAB[i] = TableLayout.PREFERRED;
				}
				return LAYOUT_ROWS_TAB;
			}
			
			private void addTuneLines() {
				for (int i = 0; i < numberOfSections; i++) {
					final TuneEditorLine sectionLine = tuneInputs.get(i);
					
					miscPanel.remove(sectionLine.tab1line);
					tempoPanel.remove(sectionLine.tab2line);
									
					miscPanel.add(sectionLine.tab1line, "0, "+(i+1)+", 5, "+(i+1)+", f, f");
					tempoPanel.add(sectionLine.tab2line, "0, "+(i+1)+", 5, "+(i+1)+", f, f");
				}		
			}
			
			private void addTitlesToTabs() {
				// TODO
				miscPanel.add(new JLabel("Enable"), "0, 0, c, c");
				miscPanel.add(new JLabel("From bar"), "1, 0, c, c");
				miscPanel.add(new JLabel("To bar"), "2, 0, c, c");
				miscPanel.add(new JLabel("Seminote"), "3, 0, c, c");
				miscPanel.add(new JLabel("Fade %"), "4, 0, c, c");
				tempoPanel.add(new JLabel("Enable"), "0, 0, c, c");
				tempoPanel.add(new JLabel("From bar"), "1, 0, c, c");
				tempoPanel.add(new JLabel("To bar"), "2, 0, c, c");
				tempoPanel.add(new JLabel("Offset"), "3, 0, c, c");
				tempoPanel.add(new JLabel("Accelerando"), "4, 0, c, c");
			}


			
			private float processSections(TreeMap<Float, TuneLine> tm, float lastEnd) {
				for (int k = 0; k < numberOfSections; k++) {
					if (TuneDialog.this.tuneInputs.get(k).enable[0].isSelected()) {
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
							ps.startBar = Float.parseFloat(tuneInputs.get(k).barA[0].getText().replace(",", "."));
							ps.endBar = Float.parseFloat(tuneInputs.get(k).barB[0].getText().replace(",", "."));
							ps.tempo = Integer.parseInt(tuneInputs.get(k).tempo.getText());
							ps.accelerando = Integer.parseInt(tuneInputs.get(k).accelerando.getText());
							ps.fade = Integer.parseInt(tuneInputs.get(k).fade.getText());
							lastEnd = checkForNewLastEnd(tm, lastEnd, k, ps, soFarSoGood(tm, ps));
						} catch (NumberFormatException nfe) {
							TuneDialog.this.tuneInputs.get(k).enable[0].setSelected(false);
							TuneDialog.this.tuneInputs.get(k).enable[1].setSelected(false);
						}
					}
				}
				return lastEnd;
			}

			private float checkForNewLastEnd(TreeMap<Float, TuneLine> tm, float lastEnd, int k, TuneLine ps,
					boolean soFarSoGood) {
				if (ps.startBar >= 0.0f && ps.startBar < ps.endBar && soFarSoGood) {
					tm.put(ps.startBar, ps);
					if (ps.endBar > lastEnd)
						lastEnd = ps.endBar;
					ps.dialogLine = k;
				} else {
					TuneDialog.this.tuneInputs.get(k).enable[0].setSelected(false);
					TuneDialog.this.tuneInputs.get(k).enable[1].setSelected(false);
				}
				return lastEnd;
			}

			private void processTree(SortedMap<Float, TuneLine> tree) throws ParseException {
				int number = 0;
				int highestNumber = 0;
				boolean useDialogLineNumbers = true;
				for (Entry<Float, TuneLine> entry : tree.entrySet()) {
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
					} else {
						while (tuneInputs.size() < number + 1) {
							makeNewTuneLine();
						}
						tuneInputs.get(number).enable[0].setSelected(true);
						tuneInputs.get(number).barA[0].setText(String.valueOf(ps.startBar));
						tuneInputs.get(number).barB[0].setText(String.valueOf(ps.endBar));
						tuneInputs.get(number).enable[1].setSelected(true);
						tuneInputs.get(number).barA[1].setText(String.valueOf(ps.startBar));
						tuneInputs.get(number).barB[1].setText(String.valueOf(ps.endBar));
						tuneInputs.get(number).transpose.setText(String.valueOf(ps.seminoteStep));
						tuneInputs.get(number).tempo.setText(String.valueOf(ps.tempo));
						tuneInputs.get(number).fade.setText(String.valueOf(ps.fade));
						tuneInputs.get(number).accelerando.setText(String.valueOf(ps.accelerando));
						if (number > highestNumber) highestNumber = number;
					}
					number++;
				}
				numberOfSections = Math.max(numberOfSections, highestNumber + 1);
				assert numberOfSections >= tuneInputs.size();
				assert numberOfSections <= numberOfSectionsMax;
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

	private static boolean soFarSoGood(TreeMap<Float, TuneLine> tm, TuneLine ps) {
		for (TuneLine psC : tm.values()) {
			if (!(ps.startBar >= psC.endBar || ps.endBar <= psC.startBar)) {
				return false;
			}
		}
		return true;
	}
}