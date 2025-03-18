package com.digero.abcplayer.view;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import com.digero.common.abctomidi.AbcInfo;

public class AbcInfoTableModel extends AbstractTableModel {

	private static final long serialVersionUID = -7672178885656979023L;
	
	private ArrayList<AbcInfo> data = new ArrayList<AbcInfo>();
	
	public List<AbcInfo> getTableData() {
		return data;
	}
	
	public void clearRows() {
		int sz = getRowCount();
		data.clear();
		fireTableRowsDeleted(0, sz);
	}
	
	public void addRow(AbcInfo inf) {
		data.add(inf);
		fireTableRowsInserted(getRowCount() - 1, getRowCount() - 1);
	}
	
	public void insertRow(AbcInfo inf, int idx) {
		data.add(idx, inf);
		fireTableRowsInserted(idx, idx + 2);
	}
	
	public void moveRows(int rowsToMove[], int toIdx) {
		int effectiveInsertIdx = toIdx;
		ArrayList<AbcInfo> tmpSwap = new ArrayList<AbcInfo>();
		Arrays.sort(rowsToMove);

		for (int i = rowsToMove.length - 1; i >= 0; i--) {
			int r = rowsToMove[i];
			if (r < toIdx) {
				effectiveInsertIdx--;
			}
			tmpSwap.add(data.get(r));
			data.remove(r);
		}
		
		for (int i = 0; i < tmpSwap.size(); i++) {
			data.add(effectiveInsertIdx, tmpSwap.get(i));
		}
		
		fireTableRowsInserted(0, getRowCount());
	}
	
	/// COLUMNS
	public static final int COL_COUNT = 12;
	
	@Override
	public int getColumnCount() {
		return COL_COUNT;
	}

	@Override
	public int getRowCount() {
		return data.size();
	}
	
	@Override
	public String getColumnName(int colIndex) {
		switch (colIndex) {
		case 0:
			return "File Name";
		case 1:
			return "Song Name";
		case 2:
			return "Part Count";
		case 3:
			return "Setups Min";
		case 4:
			return "Setups Max";
		case 5:
			return "Duration";
		case 6:
			return "Artist";
		case 7:
			return "Transcriber";
		case 8:
			return "Mood";
		case 9:
			return "Genre";
		case 10:
			return "Export Date";
		case 11:
			return "Exported By";
		}
		return "ERR";
	}
	
	@Override
	public Object getValueAt(int rowIndex, int colIndex) {
		AbcInfo inf = data.get(rowIndex);
		switch(colIndex) {
		case 0:  return inf.getSourceFiles().get(0).getName();
		case 1:  return inf.getTitle();
		case 2:  return inf.getPartCount();
		case 3:  return inf.getPartSetupsMin();
		case 4:  return inf.getPartSetupsMax();
		case 5:  return inf.getSongDurationStr();
		case 6:  return inf.getComposer();
		case 7:  return inf.getTranscriber();
		case 8:  return inf.getMood();
		case 9:  return inf.getGenre();
		case 10: return inf.getExportTimestamp();
		case 11: return inf.getAbcCreator();
		}
		return null;
	}
	
	public final String[] DEFAULT_ENABLED_COLS = {"Song Name", "Part Count", "Duration", "Artist", "Transcriber" };
	
	public boolean getColumnDefaultEnabled(String colName) {
		if (Arrays.stream(DEFAULT_ENABLED_COLS).anyMatch(colName::equals)) {
			return true;
		}
		return false;
	}
	
	public List<String> getColumnNames() {
		List<String> cols = new ArrayList<String>(COL_COUNT);
		for (int i = 0; i < COL_COUNT; i++) {
			cols.add(getColumnName(i));
		}
		return cols;
	}
	
	public int getIdxForAbcInfo(AbcInfo inf) {
		for (int i = 0; i < data.size(); i++) {
			if (data.get(i) == inf) {
				return i;
			}
		}
		return -1;
	}
	
	public AbcInfo getAbcInfoAt(int rowIndex) {
		return data.get(rowIndex);
	}
	
	public void removeRow(int rowIdx) {
		data.remove(rowIdx);
		fireTableRowsDeleted(rowIdx, rowIdx);
	}

}
