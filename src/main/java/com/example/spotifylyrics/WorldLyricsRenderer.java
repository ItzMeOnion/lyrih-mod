package com.example.spotifylyrics;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

/**
 * The "spawn around me" mode: lyric lines float in a ring around the player.
 * The current line sits in front, earlier lines drift to one side and upcoming lines to the other,
 * and the ring turns smoothly as the song moves on. Every line always faces the player.
 */
public final class WorldLyricsRenderer {
    private static final int VISIBLE_EACH_SIDE = 4;
    private static final double SLOT = Math.PI * 2 / 10.0; // angle between neighbouring lines
    private static final float TEXT_SCALE = 0.025f;
    private static final int SPOTIFY_GREEN = 0x1DB954;

    private WorldLyricsRenderer() {}

    public static void render(WorldRenderContext ctx) {
        Config.Data cfg = Config.get();
        if (!"float".equals(cfg.lyricsMode)) return;

        Track track = NowPlaying.track();
        if (track == null) return;
        Lyrics lyrics = NowPlaying.lyrics();
        if (lyrics.lines().isEmpty()) return;

        long now = track.progressNow() + cfg.lyricsOffsetMs;
        int idx = lyrics.indexAt(now);
        if (idx < 0) return;

        // Fractional position between the current line and the next one, so the ring moves smoothly.
        float frac = 0f;
        if (idx + 1 < lyrics.lines().size()) {
            long a = lyrics.lines().get(idx).timeMs();
            long b = lyrics.lines().get(idx + 1).timeMs();
            if (b > a) frac = Math.min(1f, Math.max(0f, (now - a) / (float) (b - a)));
        }
        float f = idx + frac;

        Camera camera = ctx.camera();
        Vec3d camPos = camera.getPos();
        double baseYaw = Math.toRadians(camera.getYaw());
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        MatrixStack matrices = ctx.matrices();
        VertexConsumerProvider consumers = ctx.consumers();

        int from = Math.max(0, idx - VISIBLE_EACH_SIDE);
        int to = Math.min(lyrics.lines().size() - 1, idx + VISIBLE_EACH_SIDE);
        for (int i = from; i <= to; i++) {
            double angle = (i - f) * SLOT;                  // 0 = straight in front of the player
            double absAngle = Math.abs(angle);
            float alpha = (float) Math.max(0.0, 1.0 - absAngle / (Math.PI * 0.6)); // fade toward the back
            if (alpha <= 0.02f) continue;

            // Spawn effect: new lines fade in over the first 300 ms of the line.
            long ageMs = now - lyrics.lines().get(i).timeMs();
            if (i == idx && ageMs < 300) alpha *= Math.max(0f, ageMs / 300f);

            // Direction in the world: yaw rotates the ring with the player's view.
            double worldYaw = baseYaw + angle;
            double dx = -Math.sin(worldYaw) * cfg.floatRadius;
            double dz = Math.cos(worldYaw) * cfg.floatRadius;
            double dy = cfg.floatHeight;

            String text = lyrics.lines().get(i).text();
            if (text.isEmpty()) text = "♪";
            boolean current = i == idx;
            int rgb = current ? SPOTIFY_GREEN : 0xFFFFFF;
            int argb = ((int) (alpha * 255) << 24) | rgb;

            matrices.push();
            matrices.translate(dx, dy, dz);                  // world space, relative to the camera
            matrices.multiply(camera.getRotation());         // billboard: face the player
            matrices.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
            float x = -tr.getWidth(text) / 2f;
            tr.draw(text, x, 0, argb, false,
                    matrices.peek().getPositionMatrix(), consumers,
                    TextRenderer.TextLayerType.SEE_THROUGH, 0,
                    LightmapTextureManager.MAX_LIGHT_COORDINATE);
            matrices.pop();
        }
    }
}
