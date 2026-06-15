package com.digero.maestro.view.song;

/**
 * Immutable song metadata edited by {@link SongInfoPanel} and copied to or from the current project.
 *
 * @param title The ABC title field.
 * @param composer The ABC composer or artist field.
 * @param transcriber The ABC transcriber field, usually the current user's name.
 * @param genre The optional ABC genre field.
 * @param mood The optional ABC mood field.
 */
public record SongInfo(
                String title,
                String composer,
                String transcriber,
                String genre,
                String mood) {

        /**
         * Creates the default metadata used when no song is loaded.
         *
         * @return A song-info object with every field set to an empty string.
         */
        public static SongInfo empty() {
                return new SongInfo("", "", "", "", "");
        }
}
