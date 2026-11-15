package com.example.spotifylyrics;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

public final class SpotifyLyricsMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Config.load();
        NowPlaying.start();
        Commands.register();
        HudRenderCallback.EVENT.register(HudRenderer::render);
        WorldRenderEvents.AFTER_ENTITIES.register(WorldLyricsRenderer::render);
    }
}
