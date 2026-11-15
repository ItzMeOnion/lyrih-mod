package com.example.spotifylyrics;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.ArrayList;
import java.util.List;

/** The "chill" mode: a small card at the top centre with the song and the lyric lines. */
public final class HudRenderer {
    private static final int SPOTIFY_GREEN = 0xFF1DB954;
    private static final int CARD_BG = 0x90000000;
    private static final int DIM = 0xFF9A9A9A;
    private static final int BRIGHT = 0xFFFFFFFF;

    private HudRenderer() {}

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        Config.Data cfg = Config.get();
        boolean topLyrics = "top".equals(cfg.lyricsMode);
        if (!cfg.showNowPlaying && !topLyrics) return;

        Track track = NowPlaying.track();
        if (track == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden) return;
        TextRenderer tr = mc.textRenderer;
        int centerX = ctx.getScaledWindowWidth() / 2;

        // Build the rows: each is {text, colour}
        List<String> texts = new ArrayList<>();
        List<Integer> colours = new ArrayList<>();

        if (cfg.showNowPlaying) {
            texts.add("♪ " + track.name() + "  •  " + track.artists());
            colours.add(BRIGHT);
        }
        if (topLyrics) {
            Lyrics lyrics = NowPlaying.lyrics();
            if (!lyrics.lines().isEmpty()) {
                int idx = lyrics.indexAt(track.progressNow() + cfg.lyricsOffsetMs);
                if (idx >= 0) {
                    addLine(texts, colours, lyrics, idx - 1, DIM);
                    addLine(texts, colours, lyrics, idx, SPOTIFY_GREEN);
                    addLine(texts, colours, lyrics, idx + 1, DIM);
                }
            }
        }
        if (texts.isEmpty()) return;

        int width = 0;
        for (String s : texts) width = Math.max(width, tr.getWidth(s));
        width += 16;
        int height = texts.size() * 11 + 8 + (cfg.showProgressBar ? 3 : 0);
        int top = 4;
        int left = centerX - width / 2;

        ctx.fill(left, top, left + width, top + height, CARD_BG);

        int y = top + 4;
        for (int i = 0; i < texts.size(); i++) {
            ctx.drawCenteredTextWithShadow(tr, texts.get(i), centerX, y, colours.get(i));
            y += 11;
        }

        if (cfg.showProgressBar) {
            int barY = top + height - 3;
            ctx.fill(left + 4, barY, left + width - 4, barY + 2, 0x55FFFFFF);
            int filled = (int) ((width - 8) * track.fraction());
            ctx.fill(left + 4, barY, left + 4 + filled, barY + 2, SPOTIFY_GREEN);
        }
    }

    private static void addLine(List<String> texts, List<Integer> colours, Lyrics lyrics, int index, int colour) {
        if (index < 0 || index >= lyrics.lines().size()) return;
        String text = lyrics.lines().get(index).text();
        texts.add(text.isEmpty() ? "♪" : text);
        colours.add(colour);
    }
}
