package com.digero.abcplayer.view;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.TransferHandler;

public class PlaylistTransferHandler extends TransferHandler {
	
	public interface AbcLoaderCallback {
		public void load(List<File> files, int insertIdx);
	}
	
	private JTable playlistTable;
	private Consumer<File> playlistLoader;
	private AbcLoaderCallback abcFileLoader;
	private final DataFlavor indexDataFlavor =
		new DataFlavor(Integer.class,"Integer Row Index");
	
	
	public PlaylistTransferHandler(JTable playlistTable) {
		this.playlistTable = playlistTable;
	}
	
	public void setPlaylistLoadCallback(Consumer<File> callback) {
		playlistLoader = callback;
	}
	
	public void setAbcFileLoadCallback(AbcLoaderCallback callback) {
		abcFileLoader = callback;
	}
	
	@Override
	public boolean canImport(TransferHandler.TransferSupport info) {
		if (info.getComponent() != playlistTable) {
			return false;
		}
		if (!info.isDrop()) {
			return false;
		}
		
		// String from internal GUI components or file list from file explorer
		if (!(info.isDataFlavorSupported(indexDataFlavor) || info.isDataFlavorSupported(DataFlavor.javaFileListFlavor))) {
			return false;
		}

		return true;
	}
	
	@Override
	public boolean importData(TransferHandler.TransferSupport info) {
		if (!info.isDrop() || info.getComponent() != playlistTable) {
			return false;
		}
		
		JTable.DropLocation dl = (JTable.DropLocation)info.getDropLocation();
		int idx = dl.getRow();
		AbcInfoTableModel model = (AbcInfoTableModel)playlistTable.getModel();
		
		Transferable t = info.getTransferable();
		try {
			// Dragging from file explorer or from JTree explorer
			if (info.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
				@SuppressWarnings("unchecked")
				List<File> files = (List<File>)(t.getTransferData(DataFlavor.javaFileListFlavor));
				
				// Playlist load
				if (files.size() == 1 && files.get(0).getName().endsWith(".abcp")) {
					playlistLoader.accept(files.get(0));
					return true;
				}
				
				abcFileLoader.load(files, idx);
			}
			// Reorder operation within table using index
			else if (info.isDataFlavorSupported(indexDataFlavor)) {
				Integer rowSrc = (Integer)t.getTransferData(indexDataFlavor);
				if (rowSrc >= 0) {
					int selectedRows[] = playlistTable.getSelectedRows();
					model.moveRows(selectedRows, idx);
					playlistTable.clearSelection();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return false;
	}
	
	@Override
	protected Transferable createTransferable(JComponent c) {
		int rows[] = playlistTable.getSelectedRows();
		
		if (rows == null) {
			return null;
		}

		return new Transferable() {
			private final Integer rowIndex = rows[0];

			@Override
			public DataFlavor[] getTransferDataFlavors() {
				return new DataFlavor[] {indexDataFlavor};
			}

			@Override
			public boolean isDataFlavorSupported(DataFlavor flavor) {
				return indexDataFlavor.equals(flavor);
			}

			@Override
			public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
				if (!isDataFlavorSupported(flavor)) {
					throw new UnsupportedFlavorException(flavor);
				}

				return rowIndex;
			}
		};
	}

	@Override
	protected void exportDone(JComponent source, Transferable data, int action) {
	}

	@Override
	public int getSourceActions(JComponent c) {
		return COPY_OR_MOVE;
	}

	private static final long serialVersionUID = 7948705873203228584L;

}
