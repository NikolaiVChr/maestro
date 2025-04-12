package com.digero.maestro.view;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.DefaultListModel;
import javax.swing.JDialog;

import com.digero.common.midi.SequencerWrapper;
import com.digero.common.util.Listener;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcPartEvent;
import com.digero.maestro.abc.AbcSongEvent;

public class PartEditor extends JDialog {
	private static final long serialVersionUID = 2872004091137636859L;
	private PartsListEditor partsList;

	PartEditor (ProjectFrame pFrame, SequencerWrapper abcSequencer, MiscSettings miscSettings) {
		super(pFrame, "Part Editor");
		partsList = new PartsListEditor(abcSequencer, miscSettings);
		
		setLayout(new BorderLayout());
		add(partsList, BorderLayout.NORTH);
		setMinimumSize(new Dimension(300,100));
		Dimension sz = this.getMinimumSize();
		sz.width = PartEditorItem.getProtoDimension().width;
		setMinimumSize(sz);
		
		pack();
		repaint();
	}

	public void setModel(DefaultListModel<AbcPart> listModel) {
		partsList.setModel(listModel);
		pack();
		repaint();
	}

	public void updateParts() {
		partsList.updateParts();
		pack();
		repaint();
	}

	public void selectPart(int idx) {
		//partsList.selectPart(idx);
	}

	public void ensureIndexIsVisible(int idx) {
		//partsList.ensureIndexIsVisible(idx);
	}
	
	@Override
	public void repaint() {
		partsList.repaint();
		super.repaint();
	}

	public Listener<AbcSongEvent> getSongListener() {
		return partsList.songListener;
	}

	public Listener<AbcPartEvent> getPartListener() {
		return partsList.partListener;
	}
}
