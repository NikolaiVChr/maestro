package com.digero.maestro.view;

import javax.swing.JPanel;

public interface ArrangementViewItem {
	
	public JPanel getNoteGraph();
	
	public boolean isVerticalZoomForbidden();

    public boolean isAbcPreviewMode();

    public void setAbcPreviewMode(boolean abcPreviewMode);
}
