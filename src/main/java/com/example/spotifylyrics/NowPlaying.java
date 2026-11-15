package com.example.spotifylyrics;

/** Polls Spotify in the background and keeps the current track and its lyrics ready for rendering. */
public final class NowPlaying {
    private static volatile Track track;
    private static volatile Lyrics lyrics = Lyrics.EMPTY;
    private static volatile String lyricsTrackId;

    private NowPlaying() {}

    public static Track track() {
        return track;
    }

    public static Lyrics lyrics() {
        return lyrics;
    }

    public static void start() {
        Thread t = new Thread(NowPlaying::loop, "spotifylyrics-poll");
        t.setDaemon(true);
        t.start();
    }

    private static void loop() {
        while (true) {
            long delay = 1500;
            try {
                if (SpotifyAuth.isConnected()) {
                    onTrack(SpotifyApi.currentlyPlaying());
                } else {
                    track = null;
                }
            } catch (Exception e) {
                delay = 5000; // back off on network or auth errors
            }
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static void onTrack(Track t) {
        track = t;
        if (t == null || t.id().equals(lyricsTrackId)) return;

        lyricsTrackId = t.id();
        lyrics = Lyrics.EMPTY;
        String id = t.id();
        Thread fetch = new Thread(() -> {
            Lyrics fetched = LrcLib.fetch(t);
            if (id.equals(lyricsTrackId)) lyrics = fetched;
        }, "spotifylyrics-fetch");
        fetch.setDaemon(true);
        fetch.start();
    }
}
