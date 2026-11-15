package com.example.spotifylyrics;

import java.util.List;

/** Time-synced lyrics for one song. */
public record Lyrics(List<Line> lines) {
    public static final Lyrics EMPTY = new Lyrics(List.of());

    public record Line(long timeMs, String text) {}

    /** Index of the line that is current at {@code ms}, or -1 before the first line. */
    public int indexAt(long ms) {
        int idx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).timeMs() <= ms) idx = i;
            else break;
        }
        return idx;
    }
}
