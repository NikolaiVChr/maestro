package com.digero.maestro.view;

public interface SongExportSettingsListener {

    void transposeSettingsChanged();

    void tempoSettingsChanged();

    void tempoResetRequested();

    void timeSignatureChanged();

    void keySignatureChanged();

    void timingModeChanged();

    void dynamicChordModeChanged();

    void countOnlyTempoChangesFromFirstTrackSettingsChanged();

    void exportRequested();

}
