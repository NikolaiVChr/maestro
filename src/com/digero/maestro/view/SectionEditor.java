package com.digero.maestro.view;

import static javax.swing.SwingConstants.CENTER;

import java.awt.Point;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.swing.BorderFactory;
import javax.swing.ButtonModel;
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
import com.digero.common.midi.Note;
import com.digero.common.util.Listener;
import com.digero.common.view.PatchedJScrollPane;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcPartEvent;
import com.digero.maestro.abc.AbcPartEvent.AbcPartProperty;
import com.digero.maestro.abc.AbcSongEvent;
import com.digero.maestro.abc.PartSection;

import info.clearthought.layout.TableLayout;

public class SectionEditor {

	private static Point lastLocation = new Point(100, 100);
	
	public static final int numberOfSectionsMax = 20;
	static boolean clipboardArmed = false;
	static String[] clipboardStart = null;
	static String[] clipboardEnd = null;
	static boolean[] clipboardEnabled = null;
	private static JDialog openDialog = null;
	final static double[] LAYOUT_COLS_TABS = new double[] { 0.125, 0.125, 0.125, 0.125, 0.125, 0.125, 0.125, 0.125 };

	public static void show(JFrame jf, NoteGraph noteGraph, AbcPart abcPart, int track, final boolean percussion,
			final List<DrumPanel> dPanels) {
		if (openDialog != null)
			return;

		@SuppressWarnings("serial")
		class SectionDialog extends JDialog {
			
			public int numberOfSections = 8;

			private final double[] LAYOUT_COLS = new double[] { TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED };
			private double[] LAYOUT_ROWS;
			private AbcPart abcPart;
			private int track;
			private JLabel titleLabel = new JLabel();

			private List<SectionEditorLine> sectionInputs = new ArrayList<>(numberOfSections);
			private NonSectionEditorLine nonSectionInput = new NonSectionEditorLine();

			JButton showVolume = new JButton("Show volume");
			JButton copySections = new JButton("Copy");
			JButton pasteSections = new JButton("Paste");
			
			private JPanel doublingPanel;
			private JPanel miscPanel;
			private JPanel rangePanel;

			private final JButton add1 = new JButton("Add");
			
			// NoteGraph noteGraph = null;

			public SectionDialog(JFrame jf, final NoteGraph noteGraph, String title, boolean modal, AbcPart abcPart,
					int track) {
				super(jf, title, modal);
				this.abcPart = abcPart;
				this.track = track;
				// this.noteGraph = noteGraph;

				abcPart.getAbcSong().addSongListener(songListener);
				abcPart.addAbcListener(abcPartListener);

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

				SectionDialog.this.addWindowListener(new WindowAdapter() {

					@Override
					public void windowClosing(WindowEvent we) {
						SectionEditor.lastLocation = SectionDialog.this.getLocation();
						if (abcPart.getAbcSong() != null)
							abcPart.getAbcSong().removeSongListener(songListener);
						abcPart.removeAbcListener(abcPartListener);
						openDialog = null;
					}
				});

				double rowHeight = TableLayout.PREFERRED;//Must be fixed to align with the rows inside the tabbedPane
				double auxHeight = TableLayout.PREFERRED;
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

				// Set the index of the first row to 2. Rows 0 and 1 are titles and headers
				final int firstRowIndex = 3;
				
				// 2 header rows, sections, remainder section, bottom buttons
				LAYOUT_ROWS = new double[firstRowIndex + 5];
				
				LAYOUT_ROWS[0] = auxHeight;
				LAYOUT_ROWS[1] = auxHeight;
				LAYOUT_ROWS[2] = auxHeight;// to from notes
				LAYOUT_ROWS[3] = auxHeight;// Checkboxes for panning
				LAYOUT_ROWS[4] = rowHeight;


				panel.setLayout(new TableLayout(LAYOUT_COLS, LAYOUT_ROWS));

				// Row 0
				titleLabel = new JLabel("<html><b> " + abcPart.getTitle() + ": </b> "
						+ abcPart.getInstrument().toString() + " on track " + track + " </html>");
				panel.add(titleLabel, "0, 0, 7, 0, C, C");
				JTabbedPane tabPanel = new JTabbedPane();
				tabPanel.setTabPlacement(JTabbedPane.TOP);
				
				doublingPanel = new JPanel();
				miscPanel = new JPanel();
				rangePanel = new JPanel();
				
				
				panel.add(tabPanel, "0, 1, 7, 1, f, f");
				
				tabPanel.addTab("Pitch & Vol", miscPanel);
				tabPanel.addTab("Octave doubling", doublingPanel);
				tabPanel.addTab("Notes", rangePanel);// Called notes due to planning to put custom note limit in it also
				
				// Last row
				JLabel panLabel = new JLabel("<html> Only play panned:</html>");
				JCheckBox left = new JCheckBox("Left");
				JCheckBox center = new JCheckBox("Center");
				JCheckBox right = new JCheckBox("Right");
				left.setSelected(abcPart.playLeft[track]);
				center.setSelected(abcPart.playCenter[track]);
				right.setSelected(abcPart.playRight[track]);
				panel.add(panLabel, "3, 4, 4, 4, f, f");
				panel.add(left, "5, 4, f, f");
				panel.add(center, "6, 4, f, f");
				panel.add(right, "7, 4, f, f");

				
				
				TreeMap<Integer, PartSection> tree = abcPart.sections.get(track);
				if (tree != null) {
					/*
					 *    Initialize values in the swing components that has sections edited when dialog opens 
					 */
					int number = 0;
					int highestNumber = 0;
					boolean useDialogLineNumbers = true;
					for (Entry<Integer, PartSection> entry : tree.entrySet()) {
						PartSection ps = entry.getValue();
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
							while (sectionInputs.size() < number + 1) {
								makeNewSectorLine(percussion);
							}
							SectionEditorLine secInput = sectionInputs.get(number);
							secInput.enable[0].setSelected(true);
							secInput.barA[0].setText(String.valueOf(ps.startBar));
							secInput.barB[0].setText(String.valueOf(ps.endBar));
							secInput.enable[1].setSelected(true);
							secInput.barA[1].setText(String.valueOf(ps.startBar));
							secInput.barB[1].setText(String.valueOf(ps.endBar));
							secInput.enable[2].setSelected(true);
							secInput.barA[2].setText(String.valueOf(ps.startBar));
							secInput.barB[2].setText(String.valueOf(ps.endBar));
							
							secInput.transpose.setText(String.valueOf(ps.octaveStep));
							secInput.velo.setText(String.valueOf(ps.volumeStep));
							secInput.silent.setSelected(ps.silence);
							secInput.fade.setText(String.valueOf(ps.fade));
							secInput.resetVelocities.setSelected(ps.resetVelocities);
							secInput.doubling0.setSelected(ps.doubling[0]);
							secInput.doubling1.setSelected(ps.doubling[1]);
							secInput.doubling2.setSelected(ps.doubling[2]);
							secInput.doubling3.setSelected(ps.doubling[3]);
							secInput.fromPitch.setText(ps.fromPitch.toString());
							secInput.toPitch.setText(ps.toPitch.toString());
							secInput.textPitch.setText("("+ps.fromPitch.id+" to "+ps.toPitch.id+")");
							if (number > highestNumber) highestNumber = number;
						}
						number++;
					}
					numberOfSections = Math.max(numberOfSections, highestNumber + 1);
					assert numberOfSections >= sectionInputs.size();
					assert numberOfSections <= numberOfSectionsMax;
				}

				PartSection ps = abcPart.nonSection.get(track);
				nonSectionInput.silent.setSelected(ps != null && ps.silence);
				nonSectionInput.resetVelocities.setSelected(ps != null && ps.resetVelocities);
				nonSectionInput.doubling0.setSelected(ps != null && ps.doubling[0]);
				nonSectionInput.doubling1.setSelected(ps != null && ps.doubling[1]);
				nonSectionInput.doubling2.setSelected(ps != null && ps.doubling[2]);
				nonSectionInput.doubling3.setSelected(ps != null && ps.doubling[3]);
				nonSectionInput.fromPitch.setText(ps != null?ps.fromPitch.toString():Note.C0.toString());
				nonSectionInput.toPitch.setText(ps != null?ps.toPitch.toString():Note.MAX.toString());
				nonSectionInput.textPitch.setText("("+(ps != null?ps.fromPitch.id:Note.C0.id)+" to "+(ps != null?ps.toPitch.id:Note.MAX.id)+")");

				while (sectionInputs.size() <= numberOfSections) {
					makeNewSectorLine(percussion);
				}
				layoutTabs();
				enableDueToPercussion(abcPart);
				addSectorLines(nonSectionInput);

				copySections.getModel().addActionListener(e -> {
					clipboardStart = new String[numberOfSections];
					clipboardEnd = new String[numberOfSections];
					clipboardEnabled = new boolean[numberOfSections];
					for (int i = 0; i < numberOfSections; i++) {
						clipboardStart[i] = sectionInputs.get(i).barA[0].getText();
						clipboardEnd[i] = sectionInputs.get(i).barB[0].getText();
						clipboardEnabled[i] = sectionInputs.get(i).enable[0].isSelected();
					}
					clipboardArmed = true;
					pasteSections.setEnabled(clipboardArmed);
				});
				copySections.setToolTipText("<html><b> Copy the section starts and ends.</html>");
				panel.add(copySections, "1,3,2,3,f,f");

				pasteSections.getModel().addActionListener(e -> {
					if (!clipboardArmed)
						return;
					int copySize = clipboardStart.length;
					for (int i = 0; i < copySize; i++) {
						while (i + 1 > numberOfSections) addSectorLine(percussion, add1);
						sectionInputs.get(i).barA[0].setText(clipboardStart[i]);
						sectionInputs.get(i).barB[0].setText(clipboardEnd[i]);
						// The other tabs; barA and B will be autoset by listeners
						sectionInputs.get(i).enable[0].setSelected(clipboardEnabled[i]);
						sectionInputs.get(i).enable[1].setSelected(clipboardEnabled[i]);
						sectionInputs.get(i).enable[2].setSelected(clipboardEnabled[i]);
					}
					for (int i = copySize; i < numberOfSections; i++) {
						sectionInputs.get(i).barA[0].setText("0");
						sectionInputs.get(i).barB[0].setText("0");
						// The other tabs; barA and B will be autoset by listeners
						sectionInputs.get(i).enable[0].setSelected(false);
						sectionInputs.get(i).enable[1].setSelected(false);
						sectionInputs.get(i).enable[2].setSelected(false);
					}
				});
				pasteSections.setToolTipText("<html><b> Paste the section starts and ends.</html>");
				panel.add(pasteSections, "3,3,4,3,f,f");
				pasteSections.setEnabled(clipboardArmed);

				showVolume.getModel().addChangeListener(e -> {
					ButtonModel model = showVolume.getModel();
					if (model.isArmed()) {
						noteGraph.setShowingNoteVelocity(true);
						if (dPanels != null) {
							for (DrumPanel drum : dPanels) {
								drum.updateVolume(true);
							}
						}
					} else {
						noteGraph.setShowingNoteVelocity(false);
						if (dPanels != null) {
							for (DrumPanel drum : dPanels) {
								drum.updateVolume(false);
							}
						}
					}
				});
				showVolume.setToolTipText(
						"<html><b> Press and hold to see the note volumes on the track. </b><br> Only edits after clicking APPLY will show. </html>");
				panel.add(showVolume, "5,3,6,3,f,f");

				JTextField help = new JTextField("Help");
				help.setEditable(false);
				help.setHorizontalAlignment(CENTER);
				help.setToolTipText(
						"<html><b>Enabled sections must have no overlap.<br>Bar numbers are inclusive and use original MIDI bars.<br>"
								+ "No decimal numbers allowed, only whole numbers.<br>Bar numbers must be positive and greater than zero.<br>"
								+ "Clicking APPLY will also disable faulty sections.<br><br>Warning: If 'Remove initial silence' is enabled or the<br>"
								+ "meter is modified, then the bar counter in lower-right might<br>not match up, unless your preview mode is in 'Original'.<br><br>"
								+ "Doubling works by copying all<br>notes and pasting them 1 or 2<br>octaves from their original pitch.<br><br>"
								+ "The last line under the sections 'Rest of the track' is all<br>notes that is not covered by sections.</b></html>");
				panel.add(help, "0,4, 0, 4,f,f");
				
				
				add1.setToolTipText("Add 1 section line");
				add1.addActionListener(e -> {
					addSectorLine(percussion, add1);
				});
				panel.add(add1, "0,3, 0, 3,f,f");
				add1.setEnabled(numberOfSections < numberOfSectionsMax);
				
				
				/*
				 * To do sort and pack, best expand SectionEditorLine to be comparable and have 3 JPanels in it.
				 */
				JButton sort = new JButton("Sort");
				sort.setToolTipText("Sort section lines.");
				sort.addActionListener(e -> {
					Collections.sort(sectionInputs);
					addSectorLines(nonSectionInput);
					repaint();
				});
				panel.add(sort, "1,4, 1, 4,f,f");
				
				/*
				JButton pack = new JButton("Pack");
				pack.setToolTipText("Pack section lines.");
				pack.addActionListener(e -> {});
				panel.add(pack, "2," + (firstRowIndex + 2 + numberOfSections) + ", 2, "
						+ (firstRowIndex + 2 + numberOfSections) + ",f,f");
				*/
				
				JButton okButton = new JButton("APPLY");
				okButton.addActionListener(e -> {
					TreeMap<Integer, PartSection> tm = new TreeMap<>();

					int lastEnd = 0;
					for (int k = 0; k < numberOfSections; k++) {
						if (SectionDialog.this.sectionInputs.get(k).enable[0].isSelected()) {
							PartSection ps1 = new PartSection();
							try {
								ps1.octaveStep = Integer.parseInt(sectionInputs.get(k).transpose.getText());
								ps1.volumeStep = Integer.parseInt(sectionInputs.get(k).velo.getText());
								ps1.startBar = Integer.parseInt(sectionInputs.get(k).barA[0].getText());
								ps1.endBar = Integer.parseInt(sectionInputs.get(k).barB[0].getText());
								ps1.silence = sectionInputs.get(k).silent.isSelected();
								ps1.fade = Integer.parseInt(sectionInputs.get(k).fade.getText());
								ps1.resetVelocities = sectionInputs.get(k).resetVelocities.isSelected();
								ps1.doubling[0] = sectionInputs.get(k).doubling0.isSelected();
								ps1.doubling[1] = sectionInputs.get(k).doubling1.isSelected();
								ps1.doubling[2] = sectionInputs.get(k).doubling2.isSelected();
								ps1.doubling[3] = sectionInputs.get(k).doubling3.isSelected();
								try {
									ps1.fromPitch = Note.fromName(SectionDialog.this.sectionInputs.get(k).fromPitch.getText());
								}catch (IllegalArgumentException e1) {
									try {
										Note n = Note.fromId(Integer.parseInt(SectionDialog.this.sectionInputs.get(k).fromPitch.getText()));
										if (n==null) throw new IllegalArgumentException();
										ps1.fromPitch = n;
									}catch (IllegalArgumentException e3) {
										SectionDialog.this.sectionInputs.get(k).fromPitch.setText(ps1.fromPitch.toString());
									}
								}
								try {
									ps1.toPitch = Note.fromName(SectionDialog.this.sectionInputs.get(k).toPitch.getText());
								}catch (IllegalArgumentException e2) {
									try {
										Note n = Note.fromId(Integer.parseInt(SectionDialog.this.sectionInputs.get(k).toPitch.getText()));
										if (n==null) throw new IllegalArgumentException();
										ps1.toPitch = n;
									}catch (IllegalArgumentException e4) {
										SectionDialog.this.sectionInputs.get(k).toPitch.setText(ps1.toPitch.toString());
									}
								}
								SectionDialog.this.sectionInputs.get(k).textPitch.setText("("+ps1.fromPitch.id+" to "+ps1.toPitch.id+")");
								boolean soFarSoGood = true;
								for (PartSection psC : tm.values()) {
									if (!(ps1.startBar > psC.endBar || ps1.endBar < psC.startBar)) {
										soFarSoGood = false;
										break;
									}
								}
								if (ps1.startBar > 0 && ps1.startBar <= ps1.endBar && soFarSoGood) {
									tm.put(ps1.startBar, ps1);
									if (ps1.endBar > lastEnd)
										lastEnd = ps1.endBar;
									ps1.dialogLine = k;
								} else {
									SectionDialog.this.sectionInputs.get(k).enable[0].setSelected(false);
									SectionDialog.this.sectionInputs.get(k).enable[1].setSelected(false);
									SectionDialog.this.sectionInputs.get(k).enable[2].setSelected(false);
								}
							} catch (NumberFormatException nfe) {
								SectionDialog.this.sectionInputs.get(k).enable[0].setSelected(false);
								SectionDialog.this.sectionInputs.get(k).enable[1].setSelected(false);
								SectionDialog.this.sectionInputs.get(k).enable[2].setSelected(false);
							}
						}
					}
					PartSection ps1 = new PartSection();
					try {
						ps1.silence = nonSectionInput.silent.isSelected();
						ps1.resetVelocities = nonSectionInput.resetVelocities.isSelected();
						ps1.doubling[0] = nonSectionInput.doubling0.isSelected();
						ps1.doubling[1] = nonSectionInput.doubling1.isSelected();
						ps1.doubling[2] = nonSectionInput.doubling2.isSelected();
						ps1.doubling[3] = nonSectionInput.doubling3.isSelected();
						try {
							ps1.fromPitch = Note.fromName(nonSectionInput.fromPitch.getText());
						}catch (IllegalArgumentException e1) {
							try {
								Note n = Note.fromId(Integer.parseInt(nonSectionInput.fromPitch.getText()));
								if (n==null) throw new IllegalArgumentException();
								ps1.fromPitch = n;
							}catch (IllegalArgumentException e3) {
								nonSectionInput.fromPitch.setText(ps1.fromPitch.toString());
							}
						}
						try {
							ps1.toPitch = Note.fromName(nonSectionInput.toPitch.getText());
						}catch (IllegalArgumentException e2) {
							try {
								Note n = Note.fromId(Integer.parseInt(nonSectionInput.toPitch.getText()));
								if (n==null) throw new IllegalArgumentException();
								ps1.toPitch = n;
							}catch (IllegalArgumentException e4) {
								nonSectionInput.toPitch.setText(ps1.toPitch.toString());
							}
						}
						nonSectionInput.textPitch.setText("("+ps1.fromPitch.id+" to "+ps1.toPitch.id+")");
						if (ps1.silence || ps1.resetVelocities || ps1.doubling[0] || ps1.doubling[1] || ps1.doubling[2]
								|| ps1.doubling[3] || ps1.fromPitch != Note.C0 || ps1.toPitch != Note.MAX) {
							SectionDialog.this.abcPart.nonSection.set(SectionDialog.this.track, ps1);
						} else {
							SectionDialog.this.abcPart.nonSection.set(SectionDialog.this.track, null);
						}
					} catch (NumberFormatException nfe) {
					}

					if (lastEnd == 0) {
						SectionDialog.this.abcPart.sections.set(SectionDialog.this.track, null);
						SectionDialog.this.abcPart.sectionsModified.set(SectionDialog.this.track, null);
					} else {
						SectionDialog.this.abcPart.sections.set(SectionDialog.this.track, tm);
						boolean[] booleanArray = new boolean[lastEnd + 1];
						for (int m = 0; m < lastEnd + 1; m++) {
							Entry<Integer, PartSection> entry = tm.floorEntry(m + 1);
							booleanArray[m] = entry != null && entry.getValue().startBar <= m + 1
									&& entry.getValue().endBar >= m + 1;
						}

						SectionDialog.this.abcPart.sectionsModified.set(SectionDialog.this.track, booleanArray);
					}

					abcPart.playLeft[track] = left.isSelected();
					abcPart.playCenter[track] = center.isSelected();
					abcPart.playRight[track] = right.isSelected();
					
					SectionDialog.this.abcPart.sectionEdited(SectionDialog.this.track);
				});
				okButton.setToolTipText(
						"<html><b> Apply the effects. </b><br> Note that non-applied effects will not be remembered when closing dialog.<br> Sections that are not enabled will likewise also not be remembered. </html>");
				panel.add(okButton, "7,3, 7, 3,f,f");
				
				
				PatchedJScrollPane scrollPane = new PatchedJScrollPane(panel);
				scrollPane.setViewportBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

				this.getContentPane().add(scrollPane);
				panel.revalidate();
				this.pack();
				Window window = SwingUtilities.windowForComponent(this);
				if (window != null) {
					// Lets keep the dialog inside the screen, in case the screen changed resolution since it was last
					// popped up
					int maxX = window.getBounds().width - this.getWidth();
					int maxY = window.getBounds().height - this.getHeight();
					int x = Math.max(0, Math.min(maxX, SectionEditor.lastLocation.x));
					int y = Math.max(0, Math.min(maxY, SectionEditor.lastLocation.y));
					this.setLocation(new Point(x, y));
				} else {
					this.setLocation(SectionEditor.lastLocation);
				}
				
				this.setVisible(true);
				this.pack();
				this.repaint();
				// System.err.println(Thread.currentThread().getName()); Swing event thread
			}

			private void addSectorLine(final boolean percussion, JButton add1) {
				if (numberOfSections < numberOfSectionsMax) {
					numberOfSections += 1;
					makeNewSectorLine(percussion);
					layoutTabs();
					addSectorLines(nonSectionInput);
					invalidate();
					pack();
					repaint();
				}
				add1.setEnabled(numberOfSections < numberOfSectionsMax);
				add1.repaint();
			}

			private void makeNewSectorLine(final boolean percussion) {
				SectionEditorLine l = new SectionEditorLine();
				sectionInputs.add(l);
			}

			private void layoutTabs() {
				double[] LAYOUT_ROWS_NEW = tabsRows();
				TableLayout doublingLayout = new TableLayout(LAYOUT_COLS_TABS, LAYOUT_ROWS_NEW);
				TableLayout miscLayout = new TableLayout(LAYOUT_COLS_TABS, LAYOUT_ROWS_NEW);
				TableLayout rangeLayout = new TableLayout(LAYOUT_COLS_TABS, LAYOUT_ROWS_NEW);
				
				doublingPanel.setLayout(doublingLayout);
				miscPanel.setLayout(miscLayout);
				rangePanel.setLayout(rangeLayout);
				
				doublingPanel.removeAll();
				miscPanel.removeAll();
				rangePanel.removeAll();
				
				addTitlesToTabs();
			}
			
			private void addTitlesToTabs() {
				miscPanel.add(new JLabel("Enable"), "0, 0, c, c");
				miscPanel.add(new JLabel("From bar"), "1, 0, c, c");
				miscPanel.add(new JLabel("To bar"), "2, 0, c, c");
				miscPanel.add(new JLabel("Octave"), "3, 0, c, c");
				miscPanel.add(new JLabel("Volume"), "4, 0, c, c");
				miscPanel.add(new JLabel("Silence"), "5, 0, c, c");
				miscPanel.add(new JLabel("Fade %"), "6, 0, c, c");
				miscPanel.add(new JLabel("Reset Vol"), "7, 0, c, c");
				doublingPanel.add(new JLabel("Enable"), "0, 0, c, c");
				doublingPanel.add(new JLabel("From bar"), "1, 0, c, c");
				doublingPanel.add(new JLabel("To bar"), "2, 0, c, c");
				doublingPanel.add(new JLabel("2 down"), "3, 0, c, c");
				doublingPanel.add(new JLabel("1 down"), "4, 0, c, c");
				doublingPanel.add(new JLabel("1 up"),   "5, 0, c, c");
				doublingPanel.add(new JLabel("2 up"),   "6, 0, c, c");
				rangePanel.add(new JLabel("Enable"), "0, 0, c, c");
				rangePanel.add(new JLabel("From bar"), "1, 0, c, c");
				rangePanel.add(new JLabel("To bar"), "2, 0, c, c");
				rangePanel.add(new JLabel("Low limit"), "3, 0, c, c");
				rangePanel.add(new JLabel("High limit"), "4, 0, c, c");
			}

			private double[] tabsRows() {
				double[] LAYOUT_ROWS_TAB = new double[2 + numberOfSections];
				for (int i = 0; i < numberOfSections + 2; i++) {
					LAYOUT_ROWS_TAB[i] = TableLayout.PREFERRED;
				}
				return LAYOUT_ROWS_TAB;
			}

			private void addSectorLines(SectionEditorLine nonLine) {
				for (int i = 0; i < numberOfSections; i++) {
					final SectionEditorLine sectionLine = sectionInputs.get(i);
					
					miscPanel.remove(sectionLine.tab1line);
					doublingPanel.remove(sectionLine.tab2line);
					rangePanel.remove(sectionLine.tab3line);
									
					miscPanel.add(sectionLine.tab1line, "0, "+(i+1)+", 7, "+(i+1)+", f, f");
					doublingPanel.add(sectionLine.tab2line, "0, "+(i+1)+", 7, "+(i+1)+", f, f");
					rangePanel.add(sectionLine.tab3line, "0, "+(i+1)+", 7, "+(i+1)+", f, f");
				}
				miscPanel.remove(nonLine.tab1line);
				doublingPanel.remove(nonLine.tab2line);
				rangePanel.remove(nonLine.tab3line);
								
				miscPanel.add(nonLine.tab1line, "0, "+(numberOfSections+1)+", 7, "+(numberOfSections+1)+", f, f");
				doublingPanel.add(nonLine.tab2line, "0, "+(numberOfSections+1)+", 7, "+(numberOfSections+1)+", f, f");
				rangePanel.add(nonLine.tab3line, "0, "+(numberOfSections+1)+", 7, "+(numberOfSections+1)+", f, f");				
			}

			private Listener<AbcPartEvent> abcPartListener = e -> {
				if (e.getProperty() == AbcPartProperty.TITLE) {
					titleLabel.setText("<html><b> " + abcPart.getTitle() + ": </b> "
							+ abcPart.getInstrument().toString() + " on track " + track + " </html>");
				} else if (e.getProperty() == AbcPartProperty.INSTRUMENT) {
					titleLabel.setText("<html><b> " + abcPart.getTitle() + ": </b> "
							+ abcPart.getInstrument().toString() + " on track " + track + " </html>");
					
					
					
					enableDueToPercussion(abcPart);
				}
			};

			private void enableDueToPercussion(AbcPart abcPart) {
				boolean perc = abcPart.getInstrument().isPercussion;
				
				for (SectionEditorLine l : sectionInputs) {
					l.transpose.setEnabled(!perc);
					l.doubling0.setEnabled(!perc);
					l.doubling1.setEnabled(!perc);
					l.doubling2.setEnabled(!perc);
					l.doubling3.setEnabled(!perc);
					l.toPitch.setEnabled(!perc);
					l.fromPitch.setEnabled(!perc);
				}
				nonSectionInput.doubling0.setEnabled(!perc);
				nonSectionInput.doubling1.setEnabled(!perc);
				nonSectionInput.doubling2.setEnabled(!perc);
				nonSectionInput.doubling3.setEnabled(!perc);
				nonSectionInput.fromPitch.setEnabled(!perc);
				nonSectionInput.toPitch.setEnabled(!perc);
			}

			private Listener<AbcSongEvent> songListener = e -> {
				switch (e.getProperty()) {
				case BEFORE_PART_REMOVED:
					AbcPart deleted = e.getPart();
					if (deleted.equals(abcPart)) {
						// The abcPart for this editor is being deleted, lets close the dialog.
						dispose();
					}
					break;
				case SONG_CLOSING:
					dispose();
					break;
				default:
					break;
				}
			};

			
		
			
		}

		openDialog = new SectionDialog(jf, noteGraph, "Section editor", false, abcPart, track);
	}

	public static void clearClipboard() {
		clipboardArmed = false;
	}
}