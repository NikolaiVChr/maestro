package com.digero.maestro.view;

import static javax.swing.SwingConstants.CENTER;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import com.digero.common.midi.Note;
import com.digero.common.util.Listener;
import com.digero.common.util.ParseException;
import com.digero.common.view.PatchedJScrollPane;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcPartEvent;
import com.digero.maestro.abc.AbcPartEvent.AbcPartProperty;
import com.digero.maestro.abc.AbcSongEvent;
import com.digero.maestro.abc.PartSection;

import info.clearthought.layout.TableLayout;

public class SectionEditor {

	private static Point lastLocation = new Point(100, 100);
	public static int numberOfSections = 10;
	static boolean clipboardArmed = false;
	static String[] clipboardStart = new String[numberOfSections];
	static String[] clipboardEnd = new String[numberOfSections];
	static boolean[] clipboardEnabled = new boolean[numberOfSections];
	private static JDialog openDialog = null;

	public static void show(JFrame jf, NoteGraph noteGraph, AbcPart abcPart, int track, final boolean percussion,
			final List<DrumPanel> dPanels) {
		if (openDialog != null)
			return;

		@SuppressWarnings("serial")
		class SectionDialog extends JDialog {

			private final double[] LAYOUT_COLS = new double[] { TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED, TableLayout.PREFERRED };
			private double[] LAYOUT_ROWS;
			private AbcPart abcPart;
			private int track;
			private JLabel titleLabel = new JLabel();

			private List<SectionEditorLine> sectionInputs = new ArrayList<>(numberOfSections);
			private SectionEditorLine nonSectionInput = new SectionEditorLine();

			JButton showVolume = new JButton("Show volume");
			JButton copySections = new JButton("Copy");
			JButton pasteSections = new JButton("Paste");
			
			// NoteGraph noteGraph = null;

			public SectionDialog(JFrame jf, final NoteGraph noteGraph, String title, boolean modal, AbcPart abcPart,
					int track) throws ParseException {
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
				LAYOUT_ROWS = new double[firstRowIndex + numberOfSections + 1 + 1 + 1 + 1];
				
				LAYOUT_ROWS[0] = auxHeight;
				LAYOUT_ROWS[1] = auxHeight;
				for (int i = 0; i < numberOfSections + 1; i++) {
					LAYOUT_ROWS[i + firstRowIndex] = rowHeight;
				}
				LAYOUT_ROWS[numberOfSections + firstRowIndex + 1] = auxHeight;// to from notes
				LAYOUT_ROWS[numberOfSections + firstRowIndex + 1 + 1] = auxHeight;// Checkboxes for panning
				LAYOUT_ROWS[numberOfSections + firstRowIndex + 1 + 1 + 1] = rowHeight;

//		        LAYOUT_ROWS[0] = TableLayoutConstants.PREFERRED;
//		        LAYOUT_ROWS[1] = 20;
//		        LAYOUT_ROWS[2] = TableLayoutConstants.PREFERRED;
//		        for (int l = 0;l<numberOfSections;l++) {
//		        	LAYOUT_ROWS[3+l] = rowHeight;		        	
//		        }
//		        LAYOUT_ROWS[3+numberOfSections] = TableLayoutConstants.PREFERRED;
//		        LAYOUT_ROWS[4+numberOfSections] = TableLayoutConstants.PREFERRED;
//		        LAYOUT_ROWS[5+numberOfSections] = TableLayoutConstants.FILL;
				/*
				 * LAYOUT_ROWS[6+numberOfSections] = TableLayoutConstants.PREFERRED; LAYOUT_ROWS[7+numberOfSections] =
				 * TableLayoutConstants.PREFERRED; LAYOUT_ROWS[8+numberOfSections] = TableLayoutConstants.PREFERRED;
				 * LAYOUT_ROWS[9+numberOfSections] = TableLayoutConstants.PREFERRED; LAYOUT_ROWS[10+numberOfSections] =
				 * TableLayoutConstants.PREFERRED; LAYOUT_ROWS[11+numberOfSections] = TableLayoutConstants.PREFERRED;
				 * LAYOUT_ROWS[12+numberOfSections] = TableLayoutConstants.PREFERRED; LAYOUT_ROWS[13+numberOfSections] =
				 * TableLayoutConstants.PREFERRED;
				 * 
				 */
				//TableLayout layout = new TableLayout(LAYOUT_COLS, LAYOUT_ROWS);
//				int vg = layout.getVGap();
//				int w = 34 * rowHeight;
//				int h = (numberOfSections + 1) * rowHeight + 5 * auxHeight + (4 + numberOfSections) * vg + rowHeight;
//				int w = 1200;
//				int h = 900;
//				this.setSize(w, h);

				panel.setLayout(new TableLayout(LAYOUT_COLS, LAYOUT_ROWS));

				// Row 0
				titleLabel = new JLabel("<html><b> " + abcPart.getTitle() + ": </b> "
						+ abcPart.getInstrument().toString() + " on track " + track + " </html>");
				panel.add(titleLabel, "0, 0, 7, 0, C, C");
				JTabbedPane tabPanel = new JTabbedPane();
				tabPanel.setTabPlacement(JTabbedPane.TOP);
				double[] LAYOUT_COLS_D = new double[] { 0.125, 0.125, 0.125, 0.125, 0.125, 0.125, 0.125, 0.125 };
				double[] LAYOUT_ROWS_D = new double[2 + numberOfSections];
				for (int i = 0; i <= numberOfSections + 1; i++) {
					LAYOUT_ROWS_D[i] = rowHeight;
				}
				TableLayout doublingLayout = new TableLayout(LAYOUT_COLS_D, LAYOUT_ROWS_D);
				JPanel doublingPanel = new JPanel(doublingLayout);
				
				
				
				double[] LAYOUT_COLS_M = new double[] { 0.125, 0.125, 0.125, 0.125, 0.125, 0.125, 0.125, 0.125 };
				TableLayout miscLayout = new TableLayout(LAYOUT_COLS_M, LAYOUT_ROWS_D);
				JPanel miscPanel = new JPanel(miscLayout);
				
				double[] LAYOUT_COLS_R = new double[] { 0.125, 0.125, 0.125, 0.125, 0.125, 0.125, 0.125, 0.125 };
				TableLayout rangeLayout = new TableLayout(LAYOUT_COLS_R, LAYOUT_ROWS_D);
				JPanel rangePanel = new JPanel(rangeLayout);
				
				// octDouble = new JTextField("Octave doubling");
				//octDouble.setEditable(false);
				//octDouble.setHorizontalAlignment(CENTER);
//		        panel.add(octDouble, "7, 0, 10, 0, f, f");
				panel.add(tabPanel, "0, 1, 7, "+(numberOfSections+firstRowIndex)+", f, f");

								
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
				panel.add(panLabel, "3, " + (firstRowIndex + 2 + numberOfSections) + ", 4, "
						+ (firstRowIndex + 2 + numberOfSections) + ", f, f");
				panel.add(left, "5, " + (firstRowIndex + 2 + numberOfSections) + ", 5, "
						+ (firstRowIndex + 2 + numberOfSections) + ", f, f");
				panel.add(center, "6, " + (firstRowIndex + 2 + numberOfSections) + ", 6, "
						+ (firstRowIndex + 2 + numberOfSections) + ", f, f");
				panel.add(right, "7, " + (firstRowIndex + 2 + numberOfSections) + ",  7, "
						+ (firstRowIndex + 2 + numberOfSections) + ", f, f");

				// Row 1
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
				JTextField nonSection = new JTextField("Rest of the track");
				nonSection.setEditable(false);
				nonSection.setHorizontalAlignment(CENTER);
				miscPanel.add(nonSection, "1, " + (1 + numberOfSections) + ", 2, "
						+ (1 + numberOfSections) + ", f, f");
				JTextField nonSection2 = new JTextField("Rest of the track");
				nonSection2.setEditable(false);
				nonSection2.setHorizontalAlignment(CENTER);
				doublingPanel.add(nonSection2, "1, " + (1 + numberOfSections) + ", 2, "
						+ (1 + numberOfSections) + ", f, f");
				JTextField nonSection3 = new JTextField("Rest of the track");
				nonSection3.setEditable(false);
				nonSection3.setHorizontalAlignment(CENTER);
				rangePanel.add(nonSection3, "1, " + (1 + numberOfSections) + ", 2, "
						+ (1 + numberOfSections) + ", f, f");
				// panel.add(new JLabel("Rest of the track"), "1, "+(3+numberOfSections)+", 2, "+(3+numberOfSections)+",
				// c, c");
				
				panel.revalidate();
				Dimension sz = panel.getPreferredSize();
				//System.out.println("w: " + sz.width + " h: " + sz.height);

				
				for (int j = 0; j < numberOfSections; j++) {
					SectionEditorLine l = new SectionEditorLine();
					l.transpose.setEnabled(!percussion);
					l.doubling0.setEnabled(!percussion);
					l.doubling1.setEnabled(!percussion);
					l.doubling2.setEnabled(!percussion);
					l.doubling3.setEnabled(!percussion);
					l.fromPitch.setEnabled(!percussion);
					l.toPitch.setEnabled(!percussion);
					sectionInputs.add(l);
				}

				TreeMap<Integer, PartSection> tree = abcPart.sections.get(track);
				if (tree != null) {
					/*
					 *    Initialize values in the swing components that has sections edited when dialog opens 
					 */
					int number = 0;
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
						} else if (number >= numberOfSections) {
							System.err.println(
									"Too many sections in treemap in section-editor, or line numbers was badly edited in .msx file.");
							numberOfSections+=5;
							throw new ParseException("Dialog open failed, try again.","Non file");
						} else {
							sectionInputs.get(number).enable[0].setSelected(true);
							sectionInputs.get(number).barA[0].setText(String.valueOf(ps.startBar));
							sectionInputs.get(number).barB[0].setText(String.valueOf(ps.endBar));
							sectionInputs.get(number).enable[1].setSelected(true);
							sectionInputs.get(number).barA[1].setText(String.valueOf(ps.startBar));
							sectionInputs.get(number).barB[1].setText(String.valueOf(ps.endBar));
							sectionInputs.get(number).enable[2].setSelected(true);
							sectionInputs.get(number).barA[2].setText(String.valueOf(ps.startBar));
							sectionInputs.get(number).barB[2].setText(String.valueOf(ps.endBar));
							
							sectionInputs.get(number).transpose.setText(String.valueOf(ps.octaveStep));
							sectionInputs.get(number).velo.setText(String.valueOf(ps.volumeStep));
							sectionInputs.get(number).silent.setSelected(ps.silence);
							sectionInputs.get(number).fade.setText(String.valueOf(ps.fade));
							sectionInputs.get(number).resetVelocities.setSelected(ps.resetVelocities);
							sectionInputs.get(number).doubling0.setSelected(ps.doubling[0]);
							sectionInputs.get(number).doubling1.setSelected(ps.doubling[1]);
							sectionInputs.get(number).doubling2.setSelected(ps.doubling[2]);
							sectionInputs.get(number).doubling3.setSelected(ps.doubling[3]);
							sectionInputs.get(number).fromPitch.setText(ps.fromPitch.toString());
							sectionInputs.get(number).toPitch.setText(ps.toPitch.toString());
							sectionInputs.get(number).textPitch.setText("("+ps.fromPitch.id+" to "+ps.toPitch.id+")");

						}
						number++;
					}
				}

				PartSection ps = abcPart.nonSection.get(track);
				nonSectionInput.silent.setSelected(ps != null && ps.silence);
				nonSectionInput.resetVelocities.setSelected(ps != null && ps.resetVelocities);
				nonSectionInput.doubling0.setSelected(ps != null && ps.doubling[0]);
				nonSectionInput.doubling1.setSelected(ps != null && ps.doubling[1]);
				nonSectionInput.doubling2.setSelected(ps != null && ps.doubling[2]);
				nonSectionInput.doubling3.setSelected(ps != null && ps.doubling[3]);
				nonSectionInput.doubling0.setEnabled(!percussion);
				nonSectionInput.doubling1.setEnabled(!percussion);
				nonSectionInput.doubling2.setEnabled(!percussion);
				nonSectionInput.doubling3.setEnabled(!percussion);
				nonSectionInput.fromPitch.setText(ps != null?ps.fromPitch.toString():Note.C0.toString());
				nonSectionInput.toPitch.setText(ps != null?ps.toPitch.toString():Note.MAX.toString());
				nonSectionInput.textPitch.setText("("+(ps != null?ps.fromPitch.id:Note.C0.id)+" to "+(ps != null?ps.toPitch.id:Note.MAX.id)+")");
				nonSectionInput.fromPitch.setEnabled(!percussion);
				nonSectionInput.toPitch.setEnabled(!percussion);
				

				// Tooltips
				String enable = "<html><b> Enable a specific section edit. </b><br> Pressing APPLY will disable a section if it is bad.</html>";
				String barA = "<html><b> The start bar (inclusive) where this section edit starts. </b><br> Must be above 0 and greater than the 'From bar' of previous enabled section.</html>";
				String barB = "<html><b> The end bar (inclusive) where this section edit end. </b><br> Must be equal or greater than the 'From bar'.</html>";
				String transpose = "<html><b> Transpose this section some octaves up or down. </b><br> Enter a positive or negative number. </html>";
				String velo = "<html><b> Offset the volume of this section. </b><br> Experiment to find the number that does what you want. <br> Normally a number from -250 to 250. </html>";
				String silent = "<html><b> Silence this section. </b></html>";
				String reset = "<html><b> Reset volumes from the source notes. </b></html>";
				String fade = "<html><b> Fade in/out the volume of this section. </b><br> 0 = no fading <br> 100 = fade out full <br> -100 = fade in full <br> 150 = fade out before section ends <br> Etc. etc.. </html>";
				String d0 = "<html><b> Double all notes in this section 2 octaves below.</b></html>";
				String d1 = "<html><b> Double all notes in this section 1 octave below.</b></html>";
				String d2 = "<html><b> Double all notes in this section 1 octave above.</b></html>";
				String d3 = "<html><b> Double all notes in this section 2 octaves above.</b></html>";

				for (int i = 0; i < numberOfSections; i++) {
					final SectionEditorLine sectionLine = sectionInputs.get(i);
					
					sectionLine.resetVelocities.setToolTipText(reset);
					sectionLine.fade.setToolTipText(fade);
					sectionLine.silent.setToolTipText(silent);
					sectionLine.velo.setToolTipText(velo);
					sectionLine.transpose.setToolTipText(transpose);
					sectionLine.barB[0].setToolTipText(barB);
					sectionLine.barA[0].setToolTipText(barA);
					sectionLine.enable[0].setToolTipText(enable);
					sectionLine.barB[1].setToolTipText(barB);
					sectionLine.barA[1].setToolTipText(barA);
					sectionLine.enable[1].setToolTipText(enable);
					sectionLine.barB[2].setToolTipText(barB);
					sectionLine.barA[2].setToolTipText(barA);
					sectionLine.enable[2].setToolTipText(enable);
					sectionLine.doubling0.setToolTipText(d0);
					sectionLine.doubling1.setToolTipText(d1);
					sectionLine.doubling2.setToolTipText(d2);
					sectionLine.doubling3.setToolTipText(d3);
					sectionLine.fromPitch.setToolTipText("Enter the note name (like Gb4 or Fs4) or the note number (like 60).");
					sectionLine.toPitch.setToolTipText("Enter the note name (like Gb4 or Fs4) or the note number (like 60).");
					
					sectionLine.barA[0].setHorizontalAlignment(CENTER);
					sectionLine.barB[0].setHorizontalAlignment(CENTER);
					sectionLine.barA[1].setHorizontalAlignment(CENTER);
					sectionLine.barB[1].setHorizontalAlignment(CENTER);
					sectionLine.barA[2].setHorizontalAlignment(CENTER);
					sectionLine.barB[2].setHorizontalAlignment(CENTER);
					sectionLine.transpose.setHorizontalAlignment(CENTER);
					sectionLine.velo.setHorizontalAlignment(CENTER);
					sectionLine.fade.setHorizontalAlignment(CENTER);
					sectionLine.fromPitch.setHorizontalAlignment(CENTER);
					sectionLine.toPitch.setHorizontalAlignment(CENTER);
					
					
					ActionListener enabler = new ActionListener () {
						@Override
						public void actionPerformed(ActionEvent a) {
							for (JCheckBox chkbox : sectionLine.enable) {
								if (a.getSource() != chkbox) {
									chkbox.setSelected(((JCheckBox)a.getSource()).isSelected());
								}
							}
						}
					};
					sectionLine.enable[0].addActionListener(enabler);
					sectionLine.enable[1].addActionListener(enabler);
					sectionLine.enable[2].addActionListener(enabler);
					DocumentListener starter = new DocumentListener () {
						volatile boolean working = false;
						
						public void myUpdate(DocumentEvent a) {	
							if (working) return;
							working = true;
							for (JTextField stbar : sectionLine.barA) {
								if (a.getDocument() != stbar.getDocument()) {
									try {
										stbar.setText(a.getDocument().getText(0, a.getDocument().getLength()));
									} catch (Exception e) {
										// Must catch all exceptions, so we are sure working gets set to false
										e.printStackTrace();
									}
								}
							}
							working = false;
						}

						@Override
						public void insertUpdate(DocumentEvent e) {
							myUpdate(e);
						}
						@Override
						public void removeUpdate(DocumentEvent e) {
							myUpdate(e);
						}
						@Override
						public void changedUpdate(DocumentEvent a) {
							myUpdate(a);
						}
					};
					sectionLine.barA[0].getDocument().addDocumentListener(starter);
					sectionLine.barA[1].getDocument().addDocumentListener(starter);
					sectionLine.barA[2].getDocument().addDocumentListener(starter);
					DocumentListener ender = new DocumentListener () {
						volatile boolean working = false;
						
						public void myUpdate(DocumentEvent a) {	
							if (working) return;
							working = true;
							for (JTextField enbar : sectionLine.barB) {
								if (a.getDocument() != enbar.getDocument()) {
									try {
										enbar.setText(a.getDocument().getText(0, a.getDocument().getLength()));
									} catch (Exception e) {
										// Must catch all exceptions, so we are sure working gets set to false
										e.printStackTrace();
									}
								}
							}
							working = false;
						}

						@Override
						public void insertUpdate(DocumentEvent e) {
							myUpdate(e);
						}
						@Override
						public void removeUpdate(DocumentEvent e) {
							myUpdate(e);
						}
						@Override
						public void changedUpdate(DocumentEvent a) {
							myUpdate(a);
						}
					};
					sectionLine.barB[0].getDocument().addDocumentListener(ender);
					sectionLine.barB[1].getDocument().addDocumentListener(ender);
					sectionLine.barB[2].getDocument().addDocumentListener(ender);

					miscPanel.add(sectionLine.enable[0], "0," + (1 + i) + ",C,C");
					miscPanel.add(sectionLine.barA[0], "1," + (1 + i) + ",f,f");
					miscPanel.add(sectionLine.barB[0], "2," + (1 + i) + ",f,f");
					doublingPanel.add(sectionLine.enable[1], "0," + (1 + i) + ",C,C");
					doublingPanel.add(sectionLine.barA[1], "1," + (1 + i) + ",f,f");
					doublingPanel.add(sectionLine.barB[1], "2," + (1 + i) + ",f,f");
					rangePanel.add(sectionLine.enable[2], "0," + (1 + i) + ",C,C");
					rangePanel.add(sectionLine.barA[2], "1," + (1 + i) + ",f,f");
					rangePanel.add(sectionLine.barB[2], "2," + (1 + i) + ",f,f");
					
					miscPanel.add(sectionLine.transpose, "3," + (1 + i) + ",f,f");
					miscPanel.add(sectionLine.velo, "4," + (1 + i) + ",f,f");
					miscPanel.add(sectionLine.silent, "5," + (1 + i) + ",c,f");
					miscPanel.add(sectionLine.fade, "6," + (1 + i) + ",f,f");
					miscPanel.add(sectionLine.resetVelocities, "7," + (1 + i) + ",c,f");
					
					doublingPanel.add(sectionLine.doubling0, "3, "+(i+1)+", c, c");
					doublingPanel.add(sectionLine.doubling1, "4, "+(i+1)+", c, c");
					doublingPanel.add(sectionLine.doubling2, "5, "+(i+1)+", c, c");
					doublingPanel.add(sectionLine.doubling3, "6, "+(i+1)+", c, c");

					
					rangePanel.add(sectionLine.fromPitch, "3, "+(i+1)+", f, f");
					rangePanel.add(sectionLine.toPitch, "4, "+(i+1)+", f, f");
					rangePanel.add(sectionLine.textPitch, "5, "+(i+1)+", 5, "+(i+1)+", c, c");
				}

				nonSectionInput.silent.setToolTipText(silent);
				nonSectionInput.resetVelocities.setToolTipText(reset);
				nonSectionInput.doubling0.setToolTipText(d0);
				nonSectionInput.doubling1.setToolTipText(d1);
				nonSectionInput.doubling2.setToolTipText(d2);
				nonSectionInput.doubling3.setToolTipText(d3);
				miscPanel.add(nonSectionInput.silent, "5," + (1 + numberOfSections) + ",c,f");
				miscPanel.add(nonSectionInput.resetVelocities, "7," + (1 + numberOfSections) + ",c,f");
				doublingPanel.add(nonSectionInput.doubling0, "3,"+(numberOfSections+1)+",c,c");
				doublingPanel.add(nonSectionInput.doubling1, "4,"+(numberOfSections+1)+",c,c");
				doublingPanel.add(nonSectionInput.doubling2, "5,"+(numberOfSections+1)+",c,c");
				doublingPanel.add(nonSectionInput.doubling3, "6,"+(numberOfSections+1)+",c,c");
				nonSectionInput.fromPitch.setToolTipText("Enter the note name (like Gb4 or Fs4) or the note number (like 60).");
				nonSectionInput.toPitch.setToolTipText("Enter the note name (like Gb4 or Fs4) or the note number (like 60).");
				nonSectionInput.fromPitch.setHorizontalAlignment(CENTER);
				nonSectionInput.toPitch.setHorizontalAlignment(CENTER);
				rangePanel.add(nonSectionInput.fromPitch, "3, "+(numberOfSections+1)+", f, f");
				rangePanel.add(nonSectionInput.toPitch, "4, "+(numberOfSections+1)+", f, f");
				rangePanel.add(nonSectionInput.textPitch, "5, "+(numberOfSections+1)+", 5, "+(numberOfSections+1)+", c, c");

				copySections.getModel().addActionListener(e -> {
					for (int i = 0; i < numberOfSections; i++) {
						clipboardStart[i] = sectionInputs.get(i).barA[0].getText();
						clipboardEnd[i] = sectionInputs.get(i).barB[0].getText();
						clipboardEnabled[i] = sectionInputs.get(i).enable[0].isSelected();
					}
					clipboardArmed = true;
					pasteSections.setEnabled(clipboardArmed);
				});
				copySections.setToolTipText("<html><b> Copy the section starts and ends.</html>");
				panel.add(copySections, "1," + (firstRowIndex + 1 + numberOfSections) + ",2,"
						+ (firstRowIndex + 1 + numberOfSections) + ",f,f");

				pasteSections.getModel().addActionListener(e -> {
					if (!clipboardArmed)
						return;
					for (int i = 0; i < numberOfSections; i++) {
						sectionInputs.get(i).barA[0].setText(clipboardStart[i]);
						sectionInputs.get(i).barB[0].setText(clipboardEnd[i]);
						// The other tabs; barA and B will be autoset by listeners
						sectionInputs.get(i).enable[0].setSelected(clipboardEnabled[i]);
						sectionInputs.get(i).enable[1].setSelected(clipboardEnabled[i]);
						sectionInputs.get(i).enable[2].setSelected(clipboardEnabled[i]);
					}
				});
				pasteSections.setToolTipText("<html><b> Paste the section starts and ends.</html>");
				panel.add(pasteSections, "3," + (firstRowIndex + 1 + numberOfSections) + ",4,"
						+ (firstRowIndex + 1 + numberOfSections) + ",f,f");
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
				panel.add(showVolume, "5," + (firstRowIndex + 1 + numberOfSections) + ",6,"
						+ (firstRowIndex + 1 + numberOfSections) + ",f,f");

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
				panel.add(help, "0," + (firstRowIndex + 2 + numberOfSections) + ", 0, "
						+ (firstRowIndex + 2 + numberOfSections) + ",f,f");

				JButton add = new JButton("Add 5");
				add.setToolTipText("Add 5 section lines. Close and open the window to see effect.");
				add.addActionListener(e -> {numberOfSections+=5;if (numberOfSections >=20) numberOfSections = 20;
					this.setTitle("Reopen tune- and section-editors to apply changes");
					add.setEnabled(numberOfSections < 20);});
				panel.add(add, "0," + (firstRowIndex + 1 + numberOfSections) + ", 0, "
						+ (firstRowIndex + 1 + numberOfSections) + ",f,f");
				add.setEnabled(numberOfSections < 20);
				
				/*
				 * To do sort and pack, best expand SectionEditorLine to be comparable and have 3 JPanels in it.
				 */
				JButton sort = new JButton("Sort");
				sort.setToolTipText("Sort section lines.");
				sort.addActionListener(e -> {});
				//panel.add(sort, "1," + (firstRowIndex + 2 + numberOfSections) + ", 1, "
				//		+ (firstRowIndex + 2 + numberOfSections) + ",f,f");
				
				JButton pack = new JButton("Pack");
				pack.setToolTipText("Pack section lines.");
				pack.addActionListener(e -> {});
				//panel.add(pack, "2," + (firstRowIndex + 2 + numberOfSections) + ", 2, "
				//		+ (firstRowIndex + 2 + numberOfSections) + ",f,f");
				
				
				JButton okButton = new JButton("APPLY");
				final int numberOfSectionsFinal = numberOfSections;
				okButton.addActionListener(e -> {
					TreeMap<Integer, PartSection> tm = new TreeMap<>();

					int lastEnd = 0;
					for (int k = 0; k < numberOfSectionsFinal; k++) {
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
				panel.add(okButton, "7," + (firstRowIndex + 1 + numberOfSections) + ", 7, "
						+ (firstRowIndex + 1 + numberOfSections) + ",f,f");
				/*
				 * panel.add(new JLabel("Enabled sections must have no overlap."), "0,"+(6+numberOfSections)+", 6,"
				 * +(6+numberOfSections)+", c, c"); panel.add(new
				 * JLabel("Bar numbers are inclusive and use original MIDI bars."),
				 * "0, "+(7+numberOfSections)+", 6, "+(7+numberOfSections)+", c, c"); panel.add(new
				 * JLabel("No decimal numbers allowed, only whole numbers."), "0, "+(8+numberOfSections)+", 6,"
				 * +(8+numberOfSections)+", c, c"); panel.add(new
				 * JLabel("Bar numbers must be positive and greater than zero."), "0, "+(9+numberOfSections)+", 6,"
				 * +(9+numberOfSections)+", c, c"); panel.add(new
				 * JLabel("Clicking APPLY will also disable faulty sections."), "0, "+(10+numberOfSections)+", 6,"
				 * +(10+numberOfSections)+", c, c");
				 * 
				 * JLabel warn1 = new JLabel("Warning: If 'Remove initial silence' is enabled or the"); JLabel warn2 =
				 * new JLabel("meter is modified, then the bar counter in lower-right might"); JLabel warn3 = new
				 * JLabel("not match up, unless your preview mode is in 'Original'."); warn1.setForeground(new
				 * Color(1f,0f,0f)); warn2.setForeground(new Color(1f,0f,0f)); warn3.setForeground(new Color(1f,0f,0f));
				 * panel.add(warn1, "0," +(11+numberOfSections)+", 6," +(11+numberOfSections)+", c, c");
				 * panel.add(warn2, "0," +(12+numberOfSections)+", 6," +(12+numberOfSections)+", c, c");
				 * panel.add(warn3, "0," +(13+numberOfSections)+", 6," +(13+numberOfSections)+", c, c");
				 * 
				 * panel.add(new JLabel("Doubling works by copying all"), "7,"+(6+numberOfSections)+", 10,"
				 * +(6+numberOfSections)+", c, c"); panel.add(new JLabel("notes and pasting them 1 or 2"),
				 * "7, "+(7+numberOfSections)+", 10, "+(7+numberOfSections)+", c, c"); panel.add(new
				 * JLabel("octaves from their original pitch."), "7, "+(8+numberOfSections)+", 10,"
				 * +(8+numberOfSections)+", c, c");
				 * 
				 * panel.add(new JLabel("The last line under the sections is all"), "7, "+(10+numberOfSections)+", 10,"
				 * +(10+numberOfSections)+", c, c"); panel.add(new JLabel("notes that is not covered by sections."),
				 * "7, "+(11+numberOfSections)+", 10," +(11+numberOfSections)+", c, c");
				 */
				
				PatchedJScrollPane scrollPane = new PatchedJScrollPane(panel);
				scrollPane.setViewportBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

				this.getContentPane().add(scrollPane);
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
				// System.err.println(Thread.currentThread().getName()); Swing event thread
			}

			private Listener<AbcPartEvent> abcPartListener = e -> {
				if (e.getProperty() == AbcPartProperty.TITLE) {
					titleLabel.setText("<html><b> " + abcPart.getTitle() + ": </b> "
							+ abcPart.getInstrument().toString() + " on track " + track + " </html>");
				} else if (e.getProperty() == AbcPartProperty.INSTRUMENT) {
					titleLabel.setText("<html><b> " + abcPart.getTitle() + ": </b> "
							+ abcPart.getInstrument().toString() + " on track " + track + " </html>");
					
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
					nonSectionInput.fromPitch.setEnabled(!percussion);
					nonSectionInput.toPitch.setEnabled(!percussion);
				}
			};

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

		try {
			openDialog = new SectionDialog(jf, noteGraph, "Section editor", false, abcPart, track);
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

	public static void clearClipboard() {
		clipboardArmed = false;
	}
}