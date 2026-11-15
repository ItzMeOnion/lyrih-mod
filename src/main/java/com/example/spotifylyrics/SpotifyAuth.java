package com.example.spotifylyrics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Spotify OAuth 2.0 with PKCE (no client secret needed).
 * The user logs in in their browser; Spotify redirects to a tiny server on 127.0.0.1:8888.
 */
public final class SpotifyAuth {
    static final String REDIRECT_URI = "http://127.0.0.1:8888/callback";
    static final String SCOPES = "user-read-playback-state user-modify-playback-state";
    static final String TOKEN_URL = "https://accounts.spotify.com/api/token";
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final SecureRandom RANDOM = new SecureRandom();

    private static String accessToken;
    private static long accessExpiresAt;
    private static String pendingVerifier;
    private static HttpServer server;

    private SpotifyAuth() {}

    public static boolean isConnected() {
        return !Config.get().refreshToken.isBlank();
    }

    /** Starts the login flow. Progress messages go to {@code say}. */
    public static void startLogin(Consumer<String> say) {
        String clientId = Config.get().clientId.trim();
        if (clientId.isBlank()) {
            say.accept("Set \"clientId\" in config/spotify-lyrics.json first (see README).");
            return;
        }
        try {
            pendingVerifier = randomString(64);
            String challenge = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(MessageDigest.getInstance("SHA-256")
                            .digest(pendingVerifier.getBytes(StandardCharsets.US_ASCII)));
            String url = "https://accounts.spotify.com/authorize?response_type=code"
                    + "&client_id=" + enc(clientId)
                    + "&scope=" + enc(SCOPES)
                    + "&redirect_uri=" + enc(REDIRECT_URI)
                    + "&code_challenge_method=S256&code_challenge=" + challenge;

            startCallbackServer(clientId, pendingVerifier, say);

            try {
                Desktop.getDesktop().browse(URI.create(url));
                say.accept("Opening your browser to log in to Spotify...");
            } catch (Exception e) {
                say.accept("Open this link to log in: " + url);
            }
        } catch (Exception e) {
            say.accept("Could not start login: " + e.getMessage());
        }
    }

    public static void disconnect() {
        accessToken = null;
        accessExpiresAt = 0;
        Config.get().refreshToken = "";
        Config.save();
    }

    /** Returns a valid access token, refreshing it if needed. Null when not connected. */
    public static synchronized String accessToken() {
        if (accessToken != null && System.currentTimeMillis() < accessExpiresAt - 60_000) {
            return accessToken;
        }
        Config.Data cfg = Config.get();
        if (cfg.refreshToken.isBlank() || cfg.clientId.isBlank()) return null;
        try {
            JsonObject tok = postForm(TOKEN_URL,
                    "client_id=" + enc(cfg.clientId)
                            + "&grant_type=refresh_token"
                            + "&refresh_token=" + enc(cfg.refreshToken));
            storeTokens(tok);
            return accessToken;
        } catch (Exception e) {
            accessToken = null;
            return null;
        }
    }

    private static void startCallbackServer(String clientId, String verifier, Consumer<String> say) throws IOException {
        stopServer();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8888), 0);
        server.createContext("/callback", exchange -> {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
            String page;
            try {
                if (params.containsKey("error")) {
                    throw new IOException("Spotify returned: " + params.get("error"));
                }
                exchangeCode(params.get("code"), clientId, verifier);
                page = "<h2>Connected to Spotify.</h2><p>You can close this tab and return to Minecraft.</p>";
                say.accept("Spotify connected! Your music will now show in game.");
            } catch (Exception e) {
                page = "<h2>Login failed.</h2><p>Check the Minecraft chat for details.</p>";
                say.accept("Spotify login failed: " + e.getMessage());
            }
            byte[] bytes = page.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            new Thread(SpotifyAuth::stopServer, "spotify-auth-stop").start();
        });
        server.start();
        // Safety net: stop listening after 5 minutes if the login never finishes.
        new Thread(() -> {
            try { Thread.sleep(300_000); } catch (InterruptedException ignored) { }
            stopServer();
        }, "spotify-auth-timeout").start();
    }

    private static synchronized void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private static void exchangeCode(String code, String clientId, String verifier) throws Exception {
        if (code == null || code.isBlank()) throw new IOException("no code in callback");
        JsonObject tok = postForm(TOKEN_URL,
                "client_id=" + enc(clientId)
                        + "&grant_type=authorization_code"
                        + "&code=" + enc(code)
                        + "&redirect_uri=" + enc(REDIRECT_URI)
                        + "&code_verifier=" + enc(verifier));
        storeTokens(tok);
    }

    private static void storeTokens(JsonObject tok) {
        accessToken = tok.get("access_token").getAsString();
        accessExpiresAt = System.currentTimeMillis() + tok.get("expires_in").getAsLong() * 1000L;
        if (tok.has("refresh_token") && !tok.get("refresh_token").isJsonNull()) {
            Config.get().refreshToken = tok.get("refresh_token").getAsString();
            Config.save();
        }
    }

    static JsonObject postForm(String url, String form) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + res.statusCode() + " " + res.body());
        }
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> out = new HashMap<>();
        if (raw == null) return out;
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                out.put(URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    private static String randomString(int len) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        return sb.toString();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
