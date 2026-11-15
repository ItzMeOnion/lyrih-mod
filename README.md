# Spotify Lyrics (Minecraft 1.21.11, Fabric)

Shows what Spotify is playing, with synced lyrics:
- **top** mode: a chill card at the top of the screen with the song and the lines around the current one, plus a progress bar.
- **float** mode: lyric lines float in a ring around you and turn as the song plays.

## Setup

1. Create a free app at https://developer.spotify.com/dashboard.
2. In the app settings, add redirect URI: `http://127.0.0.1:8888/callback`.
3. Under User Management, add your own Spotify email (apps in development mode only work for listed users).
4. Copy the Client ID into `config/spotify-lyrics.json` ("clientId"), then start Minecraft.
5. In game, run `/spotifylyrics connect` and log in in the browser.

## Commands

| Command | What it does |
|---|---|
| `/spotifylyrics connect` / `disconnect` / `status` | Account handling |
| `/spotifylyrics mode top\|float\|off` | Lyric style |
| `/spotifylyrics card` | Toggle the now-playing card |
| `/spotifylyrics playpause` / `next` / `prev` | Playback (needs Spotify Premium) |
| `/spotifylyrics offset <ms>` | Shift lyrics if they feel early or late |

## Build

Fabric projects need the official template's Gradle wrapper and version numbers.

1. Generate or clone `fabric-example-mod` at the 1.21.11 branch.
2. Copy this project's `src/` folder over it, and replace its `gradle.properties` version values with the ones here (fill in the `REPLACE_` lines).
3. Run `./gradlew build`. The jar appears in `build/libs/`.

Requires Java 21 and Fabric Loader plus Fabric API for 1.21.11.

## Not in this version yet

The video also shows hats, sky themes, glow-to-beat, crosshair, album cover art, and a Windows desktop-app mode that needs no login. These can be added next.

## Status

The code has not been compiled or run yet. The Minecraft and Fabric API calls for rendering, HUD, and commands are the parts most likely to need small name changes for 1.21.11. Those are in `HudRenderer.java`, `WorldLyricsRenderer.java`, and `Commands.java`.
