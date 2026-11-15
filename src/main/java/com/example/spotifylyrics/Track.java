package com.example.spotifylyrics;

/** A snapshot of the playing song. Progress is interpolated between polls so the bar moves smoothly. */
public record Track(String id, String name, String artists, long durationMs,
                    long progressMs, boolean playing, long fetchedAtNanos) {

    public long progressNow() {
        long value = progressMs;
        if (playing) {
            value += (System.nanoTime() - fetchedAtNanos) / 1_000_000L;
        }
        if (durationMs > 0) value = Math.min(value, durationMs);
        return Math.max(0, value);
    }

    public float fraction() {
        return durationMs > 0 ? Math.min(1f, progressNow() / (float) durationMs) : 0f;
    }
}
