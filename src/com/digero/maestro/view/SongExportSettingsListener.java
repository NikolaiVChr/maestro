package com.digero.maestro.view;

public interface SongExportSettingsListener {

    void transposeSettingsChanged();

    void tempoSettingsChanged();

    void tempoResetRequested();

    void timeSignatureChanged();

    void keySignatureChanged();

    void timingSettingsChanged();

    void dynamicChordSettingsChanged();

    void countOnlyTempoChangesFromFirstTrackSettingsChanged();

    void exportRequested();

}
