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

import com.digero.common.view.UIText;
import info.clearthought.layout.TableLayout;

class SectionEditorLine implements Comparable<SectionEditorLine> {
	private final double[] LAYOUT_ROWS = new double[] { TableLayout.PREFERRED };	
	
	JCheckBox[] enable = {new JCheckBox(),new JCheckBox(),new JCheckBox()};
	JTextField[] barA = {new JTextField("0.0"),new JTextField("0.0"),new JTextField("0.0")};
	JTextField[] barB = {new JTextField("0.0"),new JTextField("0.0"),new JTextField("0.0")};
	JTextField transpose = new JTextField("0");
	JTextField velo = new JTextField("0");
	JCheckBox silent = new JCheckBox();
	JCheckBox legato = new JCheckBox();
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
		
		String thisStr = this.barB[0].getText();
		String thatStr = that.barB[0].getText();
				
		Float thisNum = Float.MAX_VALUE;
		float thatNum = Float.MAX_VALUE;
		
		try {
			thisNum = Float.parseFloat(thisStr);
		} catch (NumberFormatException e) {
		
		}
		try {
			thatNum = Float.parseFloat(thatStr);
		} catch (NumberFormatException e) {
			
		}
		
		if (thatNum == 0.0f) {
			thatNum = 1000000f;// Bigger than a user would input, but smaller than max
		}
		if (thisNum == 0.0f) {
			thisNum = 1000000f;
		}
		
		int result = thisNum.compareTo(thatNum);
		if (result == 0) {
			thisStr = this.barA[0].getText();
			thatStr = that.barA[0].getText();
					
			thisNum = Float.MAX_VALUE;
			thatNum = Float.MAX_VALUE;
			
			try {
				thisNum = Float.parseFloat(thisStr);
			} catch (NumberFormatException e) {
			}
			try {
				thatNum = Float.parseFloat(thatStr);
			} catch (NumberFormatException e) {
			}
			result = thisNum.compareTo(thatNum);
		}
		return result;
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
		String enableTT = UIText.get("maestro.sectionedit.tip.enable");
		String barATT = UIText.get("maestro.sectionedit.tip.bar.begin");
		String barBTT = UIText.get("maestro.sectionedit.tip.bar.end");
		String transposeTT = UIText.get("maestro.sectionedit.tip.transpose");
		String veloTT = UIText.get("maestro.sectionedit.tip.velocity");
		String silentTT = UIText.get("maestro.sectionedit.tip.silent");
		String legatoTT = UIText.get("maestro.sectionedit.tip.legato");
		String resetTT = UIText.get("maestro.sectionedit.tip.reset");
		String fadeTT = UIText.get("maestro.sectionedit.tip.fade");
		String d0TT = UIText.get("maestro.sectionedit.tip.double.2below");
		String d1TT = UIText.get("maestro.sectionedit.tip.double.1below");
		String d2TT = UIText.get("maestro.sectionedit.tip.double.1above");
		String d3TT = UIText.get("maestro.sectionedit.tip.double.2above");
		
		resetVelocities.setToolTipText(resetTT);
		fade.setToolTipText(fadeTT);
		silent.setToolTipText(silentTT);
		legato.setToolTipText(legatoTT);
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
		fromPitch.setToolTipText(UIText.get("maestro.sectionedit.tip.enter.note.limit"));
		toPitch.setToolTipText(UIText.get("maestro.sectionedit.tip.enter.note.limit"));
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
		tab3line.add(legato, "6, 0, c, f");
	}
}
