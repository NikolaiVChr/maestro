package com.digero.maestro.view;

import static javax.swing.SwingConstants.CENTER;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import info.clearthought.layout.TableLayout;

class SectionEditorLine implements Comparable<SectionEditorLine> {
	private final double[] LAYOUT_ROWS = new double[] { TableLayout.PREFERRED };	
	
	JCheckBox[] enable = {new JCheckBox(),new JCheckBox(),new JCheckBox()};
	JTextField[] barA = {new JTextField("0"),new JTextField("0"),new JTextField("0")};
	JTextField[] barB = {new JTextField("0"),new JTextField("0"),new JTextField("0")};
	JTextField transpose = new JTextField("0");
	JTextField velo = new JTextField("0");
	JCheckBox silent = new JCheckBox();
	JCheckBox resetVelocities = new JCheckBox();
	JTextField fade = new JTextField("0");
	JCheckBox doubling0 = new JCheckBox();
	JCheckBox doubling1 = new JCheckBox();
	JCheckBox doubling2 = new JCheckBox();
	JCheckBox doubling3 = new JCheckBox();
	JTextField fromPitch = new JTextField();
	JTextField toPitch = new JTextField();
	JLabel textPitch = new JLabel();
	
	JPanel tab1line = new JPanel(new TableLayout(SectionEditor.LAYOUT_COLS_TABS, LAYOUT_ROWS));
	JPanel tab2line = new JPanel(new TableLayout(SectionEditor.LAYOUT_COLS_TABS, LAYOUT_ROWS));
	JPanel tab3line = new JPanel(new TableLayout(SectionEditor.LAYOUT_COLS_TABS, LAYOUT_ROWS));
	
	public SectionEditorLine() {
		super();
		addToLayout();
		setAlignment();
		setTooltips();
		setListeners();
	}
	
	@Override
	public int compareTo(SectionEditorLine that) {
		if (that == null) throw new NullPointerException();
		
		String thisStr = this.barA[0].getText();
		String thatStr = that.barA[0].getText();
				
		Integer thisNum = Integer.MAX_VALUE;
		Integer thatNum = Integer.MAX_VALUE;
		
		if ("0".equals(thatStr)) {
			thatStr = "10000000";// Bigger than a user would input, but smaller than max
		}
		if ("0".equals(thisStr)) {
			thisStr = "10000000";
		}
		
		try {
			thisNum = Integer.parseInt(thisStr);
		} catch (NumberFormatException e) {
			
		}
		try {
			thatNum = Integer.parseInt(thatStr);
		} catch (NumberFormatException e) {
			
		}
		
		return thisNum.compareTo(thatNum);
	}

	protected void setListeners() {
		ActionListener enabler = new ActionListener () {
			@Override
			public void actionPerformed(ActionEvent a) {
				for (JCheckBox chkbox : enable) {
					if (a.getSource() != chkbox) {
						chkbox.setSelected(((JCheckBox)a.getSource()).isSelected());
					}
				}
			}
		};
		enable[0].addActionListener(enabler);
		enable[1].addActionListener(enabler);
		enable[2].addActionListener(enabler);
		DocumentListener starter = new DocumentListener () {
			volatile boolean working = false;
			
			public void myUpdate(DocumentEvent a) {	
				if (working) return;
				working = true;
				for (JTextField stbar : barA) {
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
		barA[0].getDocument().addDocumentListener(starter);
		barA[1].getDocument().addDocumentListener(starter);
		barA[2].getDocument().addDocumentListener(starter);
		DocumentListener ender = new DocumentListener () {
			volatile boolean working = false;
			
			public void myUpdate(DocumentEvent a) {	
				if (working) return;
				working = true;
				for (JTextField enbar : barB) {
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
		barB[0].getDocument().addDocumentListener(ender);
		barB[1].getDocument().addDocumentListener(ender);
		barB[2].getDocument().addDocumentListener(ender);
	}

	protected void setTooltips() {
		String enableTT = "<html><b> Enable a specific section edit. </b><br> Pressing APPLY will disable a section if it is bad.</html>";
		String barATT = "<html><b> The start bar (inclusive) where this section edit starts. </b><br> Must be above 0 and greater than the 'From bar' of previous enabled section.</html>";
		String barBTT = "<html><b> The end bar (inclusive) where this section edit end. </b><br> Must be equal or greater than the 'From bar'.</html>";
		String transposeTT = "<html><b> Transpose this section some octaves up or down. </b><br> Enter a positive or negative number. </html>";
		String veloTT = "<html><b> Offset the volume of this section. </b><br> Experiment to find the number that does what you want. <br> Normally a number from -250 to 250. </html>";
		String silentTT = "<html><b> Silence this section. </b></html>";
		String resetTT = "<html><b> Reset volumes from the source notes. </b></html>";
		String fadeTT = "<html><b> Fade in/out the volume of this section. </b><br> 0 = no fading <br> 100 = fade out full <br> -100 = fade in full <br> 150 = fade out before section ends <br> Etc. etc.. </html>";
		String d0TT = "<html><b> Double all notes in this section 2 octaves below.</b></html>";
		String d1TT = "<html><b> Double all notes in this section 1 octave below.</b></html>";
		String d2TT = "<html><b> Double all notes in this section 1 octave above.</b></html>";
		String d3TT = "<html><b> Double all notes in this section 2 octaves above.</b></html>";
		
		resetVelocities.setToolTipText(resetTT);
		fade.setToolTipText(fadeTT);
		silent.setToolTipText(silentTT);
		velo.setToolTipText(veloTT);
		transpose.setToolTipText(transposeTT);
		barB[0].setToolTipText(barBTT);
		barA[0].setToolTipText(barATT);
		enable[0].setToolTipText(enableTT);
		barB[1].setToolTipText(barBTT);
		barA[1].setToolTipText(barATT);
		enable[1].setToolTipText(enableTT);
		barB[2].setToolTipText(barBTT);
		barA[2].setToolTipText(barATT);
		enable[2].setToolTipText(enableTT);
		doubling0.setToolTipText(d0TT);
		doubling1.setToolTipText(d1TT);
		doubling2.setToolTipText(d2TT);
		doubling3.setToolTipText(d3TT);
		fromPitch.setToolTipText("Enter the note name (like Gb4 or Fs4) or the note number (like 60).");
		toPitch.setToolTipText("Enter the note name (like Gb4 or Fs4) or the note number (like 60).");
	}

	protected void setAlignment() {
		barA[0].setHorizontalAlignment(CENTER);
		barB[0].setHorizontalAlignment(CENTER);
		barA[1].setHorizontalAlignment(CENTER);
		barB[1].setHorizontalAlignment(CENTER);
		barA[2].setHorizontalAlignment(CENTER);
		barB[2].setHorizontalAlignment(CENTER);
		transpose.setHorizontalAlignment(CENTER);
		velo.setHorizontalAlignment(CENTER);
		fade.setHorizontalAlignment(CENTER);
		fromPitch.setHorizontalAlignment(CENTER);
		toPitch.setHorizontalAlignment(CENTER);
	}

	protected void addToLayout() {
		tab1line.add(enable[0], "0,0,C,C");
		tab1line.add(barA[0], "1,0,f,f");
		tab1line.add(barB[0], "2,0,f,f");
		tab2line.add(enable[1], "0,0,C,C");
		tab2line.add(barA[1], "1,0,f,f");
		tab2line.add(barB[1], "2,0,f,f");
		tab3line.add(enable[2], "0,0,C,C");
		tab3line.add(barA[2], "1,0,f,f");
		tab3line.add(barB[2], "2,0,f,f");
		
		addContentToLayout();
	}

	protected void addContentToLayout() {
		tab1line.add(transpose, "3,0,f,f");
		tab1line.add(velo, "4,0,f,f");
		tab1line.add(silent, "5,0,c,f");
		tab1line.add(fade, "6,0,f,f");
		tab1line.add(resetVelocities, "7,0,c,f");
		
		tab2line.add(doubling0, "3, 0, c, c");
		tab2line.add(doubling1, "4, 0, c, c");
		tab2line.add(doubling2, "5, 0, c, c");
		tab2line.add(doubling3, "6, 0, c, c");

		
		tab3line.add(fromPitch, "3, 0, f, f");
		tab3line.add(toPitch, "4, 0, f, f");
		tab3line.add(textPitch, "5, 0, c, c");
	}
}
