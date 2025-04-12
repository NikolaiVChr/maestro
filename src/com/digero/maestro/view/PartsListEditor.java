package com.digero.maestro.view;

import java.awt.Dimension;
import java.util.ArrayList;

import com.digero.common.midi.SequencerWrapper;
import com.digero.maestro.abc.AbcPart;

public class PartsListEditor extends PartsList {
	private static final long serialVersionUID = -3564677504833477636L;

	public PartsListEditor(SequencerWrapper abcSequencer, MiscSettings miscSettings) {
		super(abcSequencer, miscSettings);
	}
	
	@Override
	public Dimension getPreferredSize() {
		Dimension rowDim = PartEditorItem.getProtoDimension();
		rowDim.height = getComponentCount() * rowDim.height;
		return rowDim;
	}
	
	@Override
	protected void addPart(int idx) {
		AbcPart part = model.elementAt(idx);
		PartEditorItem item = new PartEditorItem(part, miscSettings.showBadger);

		//item.setItemListener(itemListener);
		
		/*
		if (part == selectedPart) {
			selectedIndex = idx;
			item.setSelected(true);
		}*/

		parts.add(idx, item);
		add(item);
	}
}
