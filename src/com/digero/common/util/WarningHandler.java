package com.digero.common.util;

public interface WarningHandler {

    enum WarningAction {
        /** Continue loading/exporting */
        PROCEED,
        /** Stop loading this file and skip it. */
        SKIP_FILE
    }

    /**
     * Should be thread-safe.
     *
     * @param warningId A unique ID for the warning
     * @param title     The title for the dialog.
     * @param message   The warning message.
     * @return The action
     */
    WarningAction handleWarning(String warningId, String title, String message);
}