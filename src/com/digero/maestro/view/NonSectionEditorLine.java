package com.digero.maestro.view;

import static javax.swing.SwingConstants.CENTER;

import javax.swing.JTextField;

public class NonSectionEditorLine extends SectionEditorLine {
	
	
	private static final JTextField nonSection = new JTextField("Rest of the track");
	private static final JTextField nonSection2 = new JTextField("Rest of the track");
	private static final JTextField nonSection3 = new JTextField("Rest of the track");

	public NonSectionEditorLine() {
		super();
	}

	@Override
	protected void setTooltips() {
		super.setTooltips();
	}

	@Override
	protected void setAlignment() {
		super.setAlignment();
		nonSection.setHorizontalAlignment(CENTER);
		nonSection2.setHorizontalAlignment(CENTER);
		nonSection3.setHorizontalAlignment(CENTER);
	}

	@Override
	protected void addToLayout() {
		addContentToLayout();
	}

	@Override
	protected void addContentToLayout() {
		nonSection.setEditable(false);
		tab1line.add(nonSection, "1, 0, 2, 0, f, f");
		nonSection2.setEditable(false);
		tab2line.add(nonSection2, "1, 0, 2, 0, f, f");
		nonSection3.setEditable(false);
		tab3line.add(nonSection3, "1, 0, 2, 0, f, f");
		
		//tab1line.add(velo, "4,0,f,f");
		tab1line.add(silent, "5,0,c,f");
		
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
