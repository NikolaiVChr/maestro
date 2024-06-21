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
}
