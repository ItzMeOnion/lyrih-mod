package com.example.spotifylyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fetches synced (LRC) lyrics from lrclib.net, a free service that needs no API key. */
public final class LrcLib {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Pattern LRC_LINE = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?](.*)");

    private LrcLib() {}

    /** Returns synced lyrics for the track, or {@link Lyrics#EMPTY} if none exist. Blocking; call off the render thread. */
    public static Lyrics fetch(Track track) {
        String artist = track.artists().split(",")[0].trim();
        try {
            // 1) exact match (duration helps pick the right version)
            String exact = "https://lrclib.net/api/get?track_name=" + enc(track.name())
                    + "&artist_name=" + enc(artist)
                    + "&duration=" + Math.round(track.durationMs() / 1000.0);
            HttpResponse<String> res = get(exact);
            if (res.statusCode() == 200) {
                String synced = JsonParser.parseString(res.body()).getAsJsonObject()
                        .get("syncedLyrics").isJsonNull() ? null
                        : JsonParser.parseString(res.body()).getAsJsonObject().get("syncedLyrics").getAsString();
                if (synced != null) return parseLrc(synced);
            }

            // 2) fuzzy search (handles "(feat. ...)" and remaster suffixes)
            String search = "https://lrclib.net/api/search?track_name=" + enc(track.name())
                    + "&artist_name=" + enc(artist);
            HttpResponse<String> s = get(search);
            if (s.statusCode() == 200) {
                JsonArray arr = JsonParser.parseString(s.body()).getAsJsonArray();
                for (JsonElement e : arr) {
                    JsonObject o = e.getAsJsonObject();
                    if (o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull()) {
                        return parseLrc(o.get("syncedLyrics").getAsString());
                    }
                }
            }
        } catch (Exception ignored) {
            // no lyrics available; the card still shows the song
        }
        return Lyrics.EMPTY;
    }

    static Lyrics parseLrc(String lrc) {
        List<Lyrics.Line> lines = new ArrayList<>();
        for (String raw : lrc.split("\\R")) {
            Matcher m = LRC_LINE.matcher(raw.trim());
            if (!m.matches()) continue;
            long minutes = Long.parseLong(m.group(1));
            long seconds = Long.parseLong(m.group(2));
            long millis = 0;
            if (m.group(3) != null) {
                millis = Long.parseLong((m.group(3) + "00").substring(0, 3));
            }
            lines.add(new Lyrics.Line(minutes * 60_000 + seconds * 1000 + millis, m.group(4).trim()));
        }
        lines.sort(Comparator.comparingLong(Lyrics.Line::timeMs));
        return new Lyrics(lines);
    }

    private static HttpResponse<String> get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "SpotifyLyricsMod/1.0 (Minecraft Fabric)")
                .GET()
                .build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
