package com.digero.maestro.view;

import static javax.swing.SwingConstants.CENTER;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import info.clearthought.layout.TableLayout;

class TuneEditorLine implements Comparable<TuneEditorLine> {
	private final double[] LAYOUT_ROWS = new double[] { TableLayout.PREFERRED };
	
	JCheckBox[] enable = {new JCheckBox(),new JCheckBox()};
	JTextField[] barA = {new JTextField("0.0"),new JTextField("0.0")};
	JTextField[] barB = {new JTextField("0.0"),new JTextField("0.0")};
	JTextField transpose = new JTextField("0");
	JTextField tempo = new JTextField("0");
	JTextField fade = new JTextField("0");
	JTextField accelerando = new JTextField("0");

	JPanel tab1line = new JPanel(new TableLayout(TuneEditor.LAYOUT_COLS_TABS, LAYOUT_ROWS));
	JPanel tab2line = new JPanel(new TableLayout(TuneEditor.LAYOUT_COLS_TABS, LAYOUT_ROWS));
	
	public TuneEditorLine() {
		super();
		addToLayout();
		setAlignment();
		setTooltips();
		setListeners();
	}
	
	@Override
	public int compareTo(TuneEditorLine that) {
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
	}

	protected void setTooltips() {
		String enableTT = "<html><b> Enable a specific section edit. </b><br> Pressing APPLY will disable a section if it is bad.</html>";
		String barATT = "<html><b> The start bar (inclusive) where this section edit starts. " +
                "</b><br> Must be above or equal to 0.0 and greater or equal than the 'From bar' of previous enabled sections." +
                "<br><br>Right mouse-click to fetch current song position.</html>";
		String barBTT = "<html><b> The end bar (exclusive) where this section edit end. " +
                "</b><br> Must be greater than the 'From bar'." +
                "<br><br>Right mouse-click to fetch current song position.</html>";
		String transposeTT = "<html><b> Transpose this section some octaves up or down. </b><br> Enter a positive or negative number. </html>";
		String accTT = "<html><b> Accelerando/Ritardando in this section. The number is BPM that it will increase or decrease in this section.</b></html>";
		String tempoTT = "<html><b> Tempo BPM offset in this section.</b></html>";
		String fadeTT = "<html><b> Fade in/out the volume of this section. </b><br> 0 = no fading <br> 100 = fade out full <br> -100 = fade in full <br> 150 = fade out before section ends <br> Etc. etc.. </html>";
		
		tempo.setToolTipText(tempoTT);
		fade.setToolTipText(fadeTT);
		accelerando.setToolTipText(accTT);
		transpose.setToolTipText(transposeTT);
		barB[0].setToolTipText(barBTT);
		barA[0].setToolTipText(barATT);
		enable[0].setToolTipText(enableTT);
		barB[1].setToolTipText(barBTT);
		barA[1].setToolTipText(barATT);
		enable[1].setToolTipText(enableTT);
	}

	protected void setAlignment() {
		barA[0].setHorizontalAlignment(CENTER);
		barB[0].setHorizontalAlignment(CENTER);
		barA[1].setHorizontalAlignment(CENTER);
		barB[1].setHorizontalAlignment(CENTER);
		transpose.setHorizontalAlignment(CENTER);
		fade.setHorizontalAlignment(CENTER);
		tempo.setHorizontalAlignment(CENTER);
		accelerando.setHorizontalAlignment(CENTER);
	}

	protected void addToLayout() {
		tab1line.add(enable[0], "0,0,C,C");
		tab1line.add(barA[0], "1,0,f,f");
		tab1line.add(barB[0], "2,0,f,f");
		tab2line.add(enable[1], "0,0,C,C");
		tab2line.add(barA[1], "1,0,f,f");
		tab2line.add(barB[1], "2,0,f,f");
		
		addContentToLayout();
	}

	protected void addContentToLayout() {
		tab1line.add(transpose, "3,0,f,f");
		tab1line.add(fade, "4,0,f,f");
		
		tab2line.add(tempo, "3, 0, f, f");
		tab2line.add(accelerando, "4, 0, f, f");
	}
}
