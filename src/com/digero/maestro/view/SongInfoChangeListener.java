package com.digero.maestro.view;

@FunctionalInterface
public interface SongInfoChangeListener {
    void songInfoChanged(SongInfoField field, SongInfo songInfo);
}
