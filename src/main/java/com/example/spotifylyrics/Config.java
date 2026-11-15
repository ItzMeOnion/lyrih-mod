package com.example.spotifylyrics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Stored in config/spotify-lyrics.json. */
public final class Config {
    public static final class Data {
        /** Spotify app Client ID from developer.spotify.com (required). */
        public String clientId = "";
        /** Filled automatically after you log in with /spotifylyrics connect. */
        public String refreshToken = "";
        /** "top" = chill lyric bar at the top, "float" = lines spawn around you, "off". */
        public String lyricsMode = "top";
        /** Show the now-playing card (song title, artist, progress bar). */
        public boolean showNowPlaying = true;
        /** Show the progress bar under the card. */
        public boolean showProgressBar = true;
        /** Shift lyrics earlier (negative) or later (positive) if they feel out of sync. */
        public int lyricsOffsetMs = 0;
        /** Radius in blocks of the floating lyric ring. */
        public float floatRadius = 3.0f;
        /** Height of the floating ring relative to your eyes (negative = lower). */
        public float floatHeight = -0.2f;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("spotify-lyrics.json");
    private static Data data = new Data();

    private Config() {}

    public static Data get() {
        return data;
    }

    public static void load() {
        try {
            if (Files.exists(PATH)) {
                Data loaded = GSON.fromJson(Files.readString(PATH), Data.class);
                if (loaded != null) data = loaded;
            }
        } catch (Exception e) {
            SpotifyLyricsMod_log("Could not read config, using defaults: " + e.getMessage());
        }
        save(); // writes defaults on first run so the file is easy to find and edit
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(data));
        } catch (Exception e) {
            SpotifyLyricsMod_log("Could not save config: " + e.getMessage());
        }
    }

    private static void SpotifyLyricsMod_log(String msg) {
        org.slf4j.LoggerFactory.getLogger("spotifylyrics").warn(msg);
    }
}
