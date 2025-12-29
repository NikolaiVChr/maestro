package com.digero.abcplayer.view;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import com.digero.common.abctomidi.AbcInfo;
import com.digero.common.view.UIText;

public class AbcInfoTableModel extends AbstractTableModel {

	private static final long serialVersionUID = -7672178885656979023L;
	
	private ArrayList<AbcInfo> data = new ArrayList<>();
	
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
		ArrayList<AbcInfo> tmpSwap = new ArrayList<>();
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
	public static final int COL_COUNT = 13;
	
	@Override
	public int getColumnCount() {
		return COL_COUNT;
	}

	@Override
	public int getRowCount() {
		return data.size();
	}
	
	public void sortBy(String columnName, boolean ascending) {
		int colIdx = getColumnNames().indexOf(columnName);
		if (colIdx == -1) {
			return;
		}
		
		Comparator<AbcInfo> comp = (AbcInfo row1, AbcInfo row2) -> {
			Object val1 = getColumnValueForAbcInfo(row1, colIdx);
			Object val2 = getColumnValueForAbcInfo(row2, colIdx);
			
			if (val1 == null && val2 == null) return 0;
			else if (val1 == null) return ascending ? -1 : 1;
			else if (val2 == null) return ascending ? 1 : -1;
			
			if (columnName.equals(UIText.get("common.duration"))) {
				return compareDurations((String)val1, (String)val2, ascending);
			}
			
			if (val1 instanceof String && val2 instanceof String) {
				return ascending ?
						((String)val1).compareToIgnoreCase((String)val2) :
						((String)val2).compareToIgnoreCase((String)val1);
			} else if (val1 instanceof Integer && val2 instanceof Integer) {
				return ascending ?
						((Integer)val1).compareTo((Integer)val2) :
						((Integer)val2).compareTo((Integer)val1);
			} else { // default to string
				return ascending ?
						val1.toString().compareToIgnoreCase(val2.toString()) :
						val2.toString().compareToIgnoreCase(val1.toString());
			}
		};
		
		data.sort(comp);
		
		fireTableDataChanged();
	}
	
	public static int compareDurations(String ds1, String ds2, boolean ascending) {
		try {
			String[] duration1 = ds1.split(":");
			String[] duration2 = ds2.split(":");
			
			int min1 = Integer.parseInt(duration1[0]);
			int sec1 = Integer.parseInt(duration1[1]);
			
			int min2 = Integer.parseInt(duration2[0]);
			int sec2 = Integer.parseInt(duration2[1]);
			
			int totalSec1 = (min1 * 60) + sec1;
			int totalSec2 = (min2 * 60) + sec2;
			
			return ascending ? Integer.compare(totalSec1, totalSec2) : Integer.compare(totalSec2, totalSec1);
		} catch (Exception e) {
			return ascending ? ds1.compareToIgnoreCase(ds2) : ds2.compareToIgnoreCase(ds1);
		}
	}
	
	public static String getNameOfColumn(int colIndex) {
		switch (colIndex) {
		case 0:
			return UIText.get("common.file.name");
		case 1:
			return UIText.get("abcplayer.full.file.path");
		case 2:
			return UIText.get("abcplayer.song.name");
		case 3:
			return UIText.get("abcplayer.part.count");
		case 4:
			return UIText.get("abcplayer.setups.min");
		case 5:
			return UIText.get("abcplayer.setups.max");
		case 6:
			return UIText.get("common.duration");
		case 7:
			return UIText.get("common.artist");
		case 8:
			return UIText.get("abcplayer.transcriber");
		case 9:
			return UIText.get("common.mood");
		case 10:
			return UIText.get("common.genre");
		case 11:
			return UIText.get("abcplayer.export.date");
		case 12:
			return UIText.get("abcplayer.exported.by");
		}
		return "ERR";
	}
	
	@Override
	public String getColumnName(int colIndex) {
		return getNameOfColumn(colIndex);
	}
	
	public static Object getColumnValueForAbcInfo(AbcInfo inf, int colIndex) {
		switch(colIndex) {
		case 0:  return inf.getSourceFiles().get(0).getName();
		case 1:  return inf.getSourceFiles().get(0).getAbsolutePath();
		case 2:  return inf.getTitle();
		case 3:  return inf.getPartCount();
		case 4:  return inf.getPartSetupsMin();
		case 5:  return inf.getPartSetupsMax();
		case 6:  return inf.getSongDurationStr();
		case 7:  return inf.getComposer();
		case 8:  return inf.getTranscriber();
		case 9:  return inf.getMood();
		case 10: return inf.getGenre();
		case 11: return inf.getExportTimestamp();
		case 12: return inf.getAbcCreator();
		}
		return null;
	}
	
	@Override
	public Object getValueAt(int rowIndex, int colIndex) {
		AbcInfo inf = data.get(rowIndex);
		return getColumnValueForAbcInfo(inf, colIndex);
	}
	
	public static final String[] DEFAULT_ENABLED_COLS = {UIText.get("abcplayer.song.name"), UIText.get("abcplayer.part.count"), UIText.get("common.duration"), UIText.get("common.artist"), UIText.get("abcplayer.transcriber")};
	
	public boolean getColumnDefaultEnabled(String colName) {
		if (Arrays.stream(DEFAULT_ENABLED_COLS).anyMatch(colName::equals)) {
			return true;
		}
		return false;
	}
	
	public List<String> getColumnNames() {
		List<String> cols = new ArrayList<>(COL_COUNT);
		for (int i = 0; i < COL_COUNT; i++) {
			cols.add(getColumnName(i));
		}
		return cols;
	}
	
	public static List<String> getColNames() {
		List<String> cols = new ArrayList<>(COL_COUNT);
		for (int i = 0; i < COL_COUNT; i++) {
			cols.add(getNameOfColumn(i));
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
