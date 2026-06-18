package com.digero.maestro.view.parts;

public interface SongPartsActionListener {
    void createPartRequested();

    void deletePartRequested();

    void sortPartsRequested();

    void numeratePartsRequested();

    void openPartEditorRequested();
}
