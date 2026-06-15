package com.digero.maestro.view.song;

public record SongInfo(
                String title,
                String composer,
                String transcriber,
                String genre,
                String mood) {

        public static SongInfo empty() {
                return new SongInfo("", "", "", "", "");
        }
}
