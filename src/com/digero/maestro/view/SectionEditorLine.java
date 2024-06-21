package com.digero.maestro.view;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JTextField;

class SectionEditorLine {
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
	
//	private JCheckBox enable = new JCheckBox();
//	private JTextField barA = new JTextField("0");
//	private JTextField barB = new JTextField("0");
//	private JTextField transpose = new JTextField("0");
//	private JTextField velo = new JTextField("0");
//	private JCheckBox silent = new JCheckBox();
//	private JCheckBox resetVelocities = new JCheckBox();
//	private JTextField fade = new JTextField("0");
//	private JCheckBox doubling0 = new JCheckBox();
//	private JCheckBox doubling1 = new JCheckBox();
//	private JCheckBox doubling2 = new JCheckBox();
//	private JCheckBox doubling3 = new JCheckBox();
	
//	boolean isPercussion = false;
//	boolean isRestOfTrack = false;
	
//	public SectionEditorLine(boolean isPercussion, boolean isRestOfTrack) {
//		this.isPercussion = isPercussion;
//		this.isRestOfTrack = isRestOfTrack;
//		
//		transpose.setEnabled(!isPercussion);
//		doubling0.setEnabled(!isPercussion);
//		doubling1.setEnabled(!isPercussion);
//		doubling2.setEnabled(!isPercussion);
//		doubling3.setEnabled(!isPercussion);
//	}
}
