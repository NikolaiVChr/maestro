package com.digero.maestro.view;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class SaveAndExportSettings {
	public static final String[] TIMING_CHOICES = {"Legacy","Legacy Swing/Triplet","Mix Timings","Mix Timings Swing/Triplet","Mix Timings Combine Priorities","Mix Timings Swing/Triplet Combine Priorities","Organic Singlestage","Organic Multistage"};
	
	public boolean promptSaveNewSong = true;
	public boolean showExportFileChooser = false;
	public boolean skipSilenceAtStart = true;
	public boolean deleteMinimalNotes = false;
	public boolean useRestsInChords = false;
    public boolean warnOnExportOfSamePartNames = true;
	public String defaultTiming = TIMING_CHOICES[2];
	// public boolean showPruned = false;
	public boolean convertABCStringsToBasicAscii = true;

	private final Preferences prefs;

	

	public SaveAndExportSettings(Preferences prefs) {
		this.prefs = prefs;
		promptSaveNewSong = prefs.getBoolean("promptSaveNewSong", promptSaveNewSong);
		showExportFileChooser = prefs.getBoolean("showExportFileChooser", showExportFileChooser);
		skipSilenceAtStart = prefs.getBoolean("skipSilenceAtStart", skipSilenceAtStart);
		deleteMinimalNotes = prefs.getBoolean("deleteMinimalNotes", deleteMinimalNotes);
		defaultTiming = prefs.get("defaultTiming", defaultTiming);
        warnOnExportOfSamePartNames = prefs.getBoolean("warnOnExportOfSamePartNames", warnOnExportOfSamePartNames);
		useRestsInChords = prefs.getBoolean("useRestsInChords", useRestsInChords);
		// showPruned = prefs.getBoolean("showPruned", showPruned);
		convertABCStringsToBasicAscii = prefs.getBoolean("convertABCStringsToBasicAscii",
				convertABCStringsToBasicAscii);
	}

	public SaveAndExportSettings(SaveAndExportSettings that) {
		this.prefs = that.prefs;
		copyFrom(that);
	}

	public void copyFrom(SaveAndExportSettings that) {
		promptSaveNewSong = that.promptSaveNewSong;
		showExportFileChooser = that.showExportFileChooser;
		skipSilenceAtStart = that.skipSilenceAtStart;
		deleteMinimalNotes = that.deleteMinimalNotes;
		defaultTiming = that.defaultTiming;
		useRestsInChords = that.useRestsInChords;
        warnOnExportOfSamePartNames = that.warnOnExportOfSamePartNames;
		// showPruned = that.showPruned;
		convertABCStringsToBasicAscii = that.convertABCStringsToBasicAscii;
	}

	public void saveToPrefs() {
		prefs.putBoolean("promptSaveNewSong", promptSaveNewSong);
		prefs.putBoolean("showExportFileChooser", showExportFileChooser);
		prefs.putBoolean("skipSilenceAtStart", skipSilenceAtStart);
		prefs.putBoolean("deleteMinimalNotes", deleteMinimalNotes);
		prefs.put("defaultTiming", defaultTiming);
        prefs.putBoolean("warnOnExportOfSamePartNames", warnOnExportOfSamePartNames);
		prefs.putBoolean("useRestsInChords", useRestsInChords);
		// prefs.putBoolean("showPruned", showPruned);
		prefs.putBoolean("convertABCStringsToBasicAscii", convertABCStringsToBasicAscii);
	}

	public void restoreDefaults() {
		try {
			prefs.clear();
		} catch (BackingStoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		SaveAndExportSettings fresh = new SaveAndExportSettings(prefs);
		this.copyFrom(fresh);
	}

	public SaveAndExportSettings getCopy() {
		return new SaveAndExportSettings(this);
	}
}
