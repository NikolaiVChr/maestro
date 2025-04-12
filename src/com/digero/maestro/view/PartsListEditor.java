package com.digero.maestro.view;

import java.awt.Dimension;
import java.util.ArrayList;

import com.digero.common.midi.SequencerWrapper;
import com.digero.common.util.Listener;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcSong;
import com.digero.maestro.abc.AbcSongEvent;

public class PartsListEditor extends PartsList {
	private static final long serialVersionUID = -3564677504833477636L;
	Dimension rowDim = null;

	public PartsListEditor(SequencerWrapper abcSequencer, MiscSettings miscSettings) {
		super(abcSequencer, miscSettings);
		songListener = e -> {
			AbcSong song = e.getSource();
			if (song == null)
				return;

			switch (e.getProperty()) {
			case PART_ADDED:
				e.getPart().addAbcListener(partListener);
				//updateParts(); listens to model also, so not needed
				break;
			case BEFORE_PART_REMOVED:
				AbcPart part = e.getPart();
				part.removeAbcListener(partListener);
				break;
			case PART_LIST_ORDER:
				updateParts();
				break;
			default:
				break;
			}
		};
		itemListener = null;
	}
	
	@Override
	public Dimension getPreferredSize() {
		if (rowDim == null) {
			rowDim = PartEditorItem.getProtoDimension();
		}
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
