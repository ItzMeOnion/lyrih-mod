package com.example.spotifylyrics;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;

/** Chat commands: /spotifylyrics connect | disconnect | status | mode top|float|off | card | playpause | next | prev | offset <ms> */
public final class Commands {
    private Commands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
                ClientCommandManager.literal("spotifylyrics")
                        .then(ClientCommandManager.literal("connect").executes(c -> {
                            SpotifyAuth.startLogin(Chat::say);
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("disconnect").executes(c -> {
                            SpotifyAuth.disconnect();
                            feedback(c, "Disconnected from Spotify.");
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("status").executes(c -> {
                            Track t = NowPlaying.track();
                            String state = !SpotifyAuth.isConnected() ? "not connected"
                                    : t == null ? "connected, nothing playing"
                                    : "now playing: " + t.name() + " - " + t.artists();
                            feedback(c, "Spotify: " + state);
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("mode")
                                .then(ClientCommandManager.literal("top").executes(c -> setMode(c, "top")))
                                .then(ClientCommandManager.literal("float").executes(c -> setMode(c, "float")))
                                .then(ClientCommandManager.literal("off").executes(c -> setMode(c, "off"))))
                        .then(ClientCommandManager.literal("card").executes(c -> {
                            Config.get().showNowPlaying = !Config.get().showNowPlaying;
                            Config.save();
                            feedback(c, "Now-playing card: " + (Config.get().showNowPlaying ? "on" : "off"));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("playpause").executes(c -> control(c, true)))
                        .then(ClientCommandManager.literal("next").executes(c -> control(c, false, 1)))
                        .then(ClientCommandManager.literal("prev").executes(c -> control(c, false, -1)))
                        .then(ClientCommandManager.literal("offset")
                                .then(ClientCommandManager.argument("ms", IntegerArgumentType.integer(-10000, 10000))
                                        .executes(c -> {
                                            Config.get().lyricsOffsetMs = IntegerArgumentType.getInteger(c, "ms");
                                            Config.save();
                                            feedback(c, "Lyrics offset: " + Config.get().lyricsOffsetMs + " ms");
                                            return 1;
                                        })))
        ));
    }

    private static int setMode(com.mojang.brigadier.context.CommandContext<?> c, String mode) {
        Config.get().lyricsMode = mode;
        Config.save();
        feedback(c, "Lyrics mode: " + mode);
        return 1;
    }

    private static int control(com.mojang.brigadier.context.CommandContext<?> c, boolean playPause) {
        return control(c, playPause, 0);
    }

    /** playPause=true toggles; otherwise dir 1 = next, -1 = previous. */
    private static int control(com.mojang.brigadier.context.CommandContext<?> c, boolean playPause, int dir) {
        Thread t = new Thread(() -> {
            try {
                if (playPause) {
                    Track now = NowPlaying.track();
                    if (now != null && now.playing()) SpotifyApi.pause(); else SpotifyApi.play();
                } else if (dir > 0) {
                    SpotifyApi.next();
                } else {
                    SpotifyApi.previous();
                }
            } catch (Exception e) {
                Chat.say("Control failed: " + e.getMessage());
            }
        }, "spotifylyrics-control");
        t.setDaemon(true);
        t.start();
        return 1;
    }

    private static void feedback(com.mojang.brigadier.context.CommandContext<?> c, String msg) {
        Chat.say(msg);
    }

    /** Sends a chat line from any thread. */
    static final class Chat {
        static void say(String msg) {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            mc.execute(() -> {
                if (mc.player != null) mc.player.sendMessage(Text.literal("[Spotify] " + msg), false);
            });
        }
    }
}
