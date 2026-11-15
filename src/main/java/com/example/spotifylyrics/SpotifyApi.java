package com.example.spotifylyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Thin wrapper around the Spotify Web API endpoints the mod uses. */
public final class SpotifyApi {
    private static final String BASE = "https://api.spotify.com";
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private SpotifyApi() {}

    /** Returns what is playing now, or null if nothing (or an ad/podcast) is playing. */
    public static Track currentlyPlaying() throws Exception {
        HttpResponse<String> res = request("GET", "/v1/me/player/currently-playing", null);
        if (res.statusCode() == 204 || res.body().isBlank()) return null;
        if (res.statusCode() != 200) throw new IOException("currently-playing HTTP " + res.statusCode());

        JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
        if (!o.has("item") || o.get("item").isJsonNull()) return null;
        JsonObject item = o.getAsJsonObject("item");

        String name = item.get("name").getAsString();
        String artists = joinArtists(item.getAsJsonArray("artists"));
        String id = (item.has("id") && !item.get("id").isJsonNull())
                ? item.get("id").getAsString()
                : name + "|" + artists; // local files have no id
        long duration = item.get("duration_ms").getAsLong();
        long progress = (o.has("progress_ms") && !o.get("progress_ms").isJsonNull())
                ? o.get("progress_ms").getAsLong() : 0L;
        boolean playing = o.get("is_playing").getAsBoolean();

        return new Track(id, name, artists, duration, progress, playing, System.nanoTime());
    }

    public static void play() throws Exception { command("PUT", "/v1/me/player/play"); }
    public static void pause() throws Exception { command("PUT", "/v1/me/player/pause"); }
    public static void next() throws Exception { command("POST", "/v1/me/player/next"); }
    public static void previous() throws Exception { command("POST", "/v1/me/player/previous"); }

    private static void command(String method, String path) throws Exception {
        HttpResponse<String> res = request(method, path, null);
        if (res.statusCode() / 100 != 2) {
            // Playback control needs Spotify Premium and an active device.
            throw new IOException("Spotify returned HTTP " + res.statusCode()
                    + " (playback control needs Premium and an open Spotify device)");
        }
    }

    private static HttpResponse<String> request(String method, String path, String body) throws Exception {
        String token = SpotifyAuth.accessToken();
        if (token == null) throw new IOException("not connected to Spotify");
        HttpRequest.BodyPublisher pub = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + path))
                .header("Authorization", "Bearer " + token)
                .method(method, pub)
                .build();
        return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static String joinArtists(JsonArray arr) {
        if (arr == null) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonElement e : arr) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.getAsJsonObject().get("name").getAsString());
        }
        return sb.toString();
    }
}
