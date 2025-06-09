package com.digero.common.view;

import java.awt.Component;
import java.io.File;

import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;

import com.digero.common.icons.IconLoader;
import com.digero.common.util.AbcFileTreeModel;
import com.digero.maestro.abc.AbcSong;

public class AbcPlaylistTreeCellRenderer extends DefaultTreeCellRenderer {
	
	private static final long serialVersionUID = -1143495339579531799L;

	@Override
	public Component getTreeCellRendererComponent(
			JTree tree,
			Object value,
			boolean selected,
			boolean expanded,
			boolean leaf,
			int row,
			boolean hasFocus) {
		super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
		
		AbcFileTreeModel.AbcSongFileNode df = (AbcFileTreeModel.AbcSongFileNode)value;
		File file = df.getFile();
		
		if (file.getName().endsWith(AbcSong.ABC_FILE_EXTENSION)) {
			setIcon(IconLoader.getImageIcon("abcfile_16.png"));
		} else if (file.getName().endsWith(AbcSong.ABCP_FILE_EXTENSION)) {
			setIcon(IconLoader.getImageIcon("abcplaylist_16.png"));
		}
		
		setToolTipText(file.getAbsolutePath());
		
		return this;
	}
}
