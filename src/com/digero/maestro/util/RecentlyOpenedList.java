package com.digero.maestro.util;

import java.io.File;
import java.util.LinkedList;
import java.util.prefs.Preferences;

// manages list of recently opened msx/abc files in maestro for file menu

public class RecentlyOpenedList {
	
	private static final int RECENT_LIST_MAX_SIZE = 22;
	
	private final Preferences prefs;
	private final LinkedList<File> openedFiles;
	
	public RecentlyOpenedList(Preferences prefs) {
		this.prefs = prefs;
		openedFiles = new LinkedList<>();
	}
	
	public LinkedList<File> getList() {
		openedFiles.clear();
		
		for (int i = 0; i < RECENT_LIST_MAX_SIZE; i++) {
			String path = prefs.get("recentlyOpened" + i, "");
			
			if (!path.isEmpty()) {
				openedFiles.addLast(new File(path));
			} else {
				break;
			}
		}
		
		return openedFiles;
	}
	
	public void addOpenedFile(File file) {
		getList();
		
		// if it exists already, remove it and re-insert at beginning
		File removeMe = null;
		for (File f : openedFiles) {
			if (f.getAbsolutePath().equals(file.getAbsolutePath())) {
				removeMe = f;
			}
		}
		
		if (removeMe != null) {
			openedFiles.remove(removeMe);
		}
		
		openedFiles.addFirst(file);
		
		if (openedFiles.size() > RECENT_LIST_MAX_SIZE) {
			openedFiles.removeLast();
		}
		
		putList();
	}
	
	private void putList() {
		int i = 0;
		for (File f : openedFiles) {
			prefs.put("recentlyOpened" + i++, f.getAbsolutePath());
		}
		
		while (i < RECENT_LIST_MAX_SIZE) {
			prefs.put("recentlyOpened" + i++, "");
		}
	}
	
	public void clearList() {
		openedFiles.clear();
		putList();
	}
}
