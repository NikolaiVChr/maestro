package com.digero.maestro.view.song;

@FunctionalInterface
public interface SongInfoChangeListener {
    void songInfoChanged(SongInfoField field, SongInfo songInfo);
}
