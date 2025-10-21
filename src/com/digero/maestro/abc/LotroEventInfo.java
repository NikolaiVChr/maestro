package com.digero.maestro.abc;

import com.digero.common.midi.Note;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a drum hit or fx note that can be played by a Lotro instrument.
 */
public abstract class LotroEventInfo<T extends LotroEventInfo<T>> implements Comparable<T> {
    protected static final String noneName = "None";
    public abstract String getName();
    public abstract Note getNote();

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public boolean equals(Object obj) {
        if (!this.getClass().isInstance(obj))
            return false;

        return this.getNote().id == ((LotroEventInfo<?>)obj).getNote().id;
    }

    @Override
    public int hashCode() {
        return this.getNote().id;
    }

    @Override
    public int compareTo(@NotNull T that) {
        return Integer.compare(this.getNote().id, that.getNote().id);
    }
}
