package com.digero.maestro.view;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.io.Serial;

import javax.swing.DefaultListModel;
import javax.swing.JDialog;

import com.digero.common.midi.SequencerWrapper;
import com.digero.common.util.Listener;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcPartEvent;
import com.digero.maestro.abc.AbcSongEvent;

public class PartEditor extends JDialog {
	@Serial
    private static final long serialVersionUID = 2872004091137636859L;

    private static final int DEFAULT_WIDTH = 300;
    private static final int DEFAULT_HEIGHT = 800;

	private PartsListEditor partsList;
	private ProjectFrame pFrame;

	PartEditor (ProjectFrame pFrame, SequencerWrapper abcSequencer, MiscSettings miscSettings) {
		super(pFrame, "Part Editor");
		this.pFrame = pFrame;
		partsList = new PartsListEditor(abcSequencer, miscSettings);
		
		setLayout(new BorderLayout());
		add(partsList, BorderLayout.NORTH);
		// set location should think it big to put it a bit up on screen
		// hence the 800, so if open a song with 24 parts, there will be room.
		setMinimumSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
		Dimension sz = this.getMinimumSize();
		sz.width = PartEditorItem.getProtoDimension().width;
		setMinimumSize(sz);
		
		pack();		
		
		this.setLocationRelativeTo(pFrame);
		
		sz.height = 100;
		setMinimumSize(sz);		
	}

	public void setModel(DefaultListModel<AbcPart> listModel) {
		partsList.setModel(listModel);
		pack();
		keepInScreen();
	}

	public void updateParts() {
		partsList.updateParts();
		// Since there is no scrollwindow we pack to be sure all parts can be seen
		// with 24 parts, the windows is not too large for 1080 screen at 12 pt fonts.
		pack();
		keepInScreen();
	}


	private void keepInScreen() {
		// Ensure that window is on screen fully if monitors or resolution changed
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice[] devices = ge.getScreenDevices();
		Rectangle bounds = this.getBounds();
		int areaOnScreen = 0;
		
		for (GraphicsDevice d : devices) {
			GraphicsConfiguration gc = d.getDefaultConfiguration();
			Rectangle screenBounds = gc.getBounds();
			
			// Now subtract the windows taskbar:
			Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
			screenBounds.x += insets.left;
			screenBounds.y += insets.top;
			screenBounds.width -= (insets.left + insets.right);
			screenBounds.height -= (insets.top + insets.bottom);
			
			if (bounds.intersects(screenBounds)) {
				Rectangle inter = bounds.intersection(screenBounds);
				areaOnScreen += inter.width * inter.height;
			}
		}
		if (areaOnScreen != bounds.width * bounds.height) {
			this.setLocationRelativeTo(pFrame);
		}
	}

	public void selectPart(int idx) {
		//partsList.selectPart(idx);
	}

	public void ensureIndexIsVisible(int idx) {
		//partsList.ensureIndexIsVisible(idx);
	}

	public Listener<AbcSongEvent> getSongListener() {
		return partsList.songListener;
	}

	public Listener<AbcPartEvent> getPartListener() {
		return partsList.partListener;
	}
}
