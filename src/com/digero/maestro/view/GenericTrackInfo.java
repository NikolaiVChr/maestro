package com.digero.maestro.view;

import com.digero.maestro.midi.NoteEvent;
import com.digero.maestro.midi.SequenceInfo;

import java.util.List;
import java.util.SortedSet;

public interface GenericTrackInfo {
    int getTrackNumber();
    List<NoteEvent> getEvents();
    boolean isDrumTrack();
    SortedSet<Integer> getNotesInUse();
    int getMinVelocity();
    int getMaxVelocity();
    String getName();
    String getInstrumentNames();
    int getInstrumentExCount();
    SequenceInfo getSequenceInfo();
}
