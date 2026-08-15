package com.aibots.common.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.http.HttpRequest;
import java.util.concurrent.CompletableFuture;

/**
 * Talks to a local proxy that forwards requests to an AI API (e.g. Gemini).
 * The API key lives in the proxy, not in Minecraft, so the client can use this
 * on any server even without the mod installed there.
 *
 * Contract:
 *   POST {baseUrl}/generate
 *   body: {"system": "...", "prompt": "..."}
 *   response 200: {"reply": "..."}  (plain text also accepted)
 */
public class LocalProxyClient extends AiClient {

    private static final String ENDPOINT = "/generate";

    public LocalProxyClient(AiConfig config) {
        super(config);
    }

    @Override
    public CompletableFuture<String> chatAsync(String system, String user) {
        String base = config.baseUrl();
        if (base == null || base.isBlank()) {
            base = "http://localhost:8844";
        }
        String url = base.endsWith("/") ? base + ENDPOINT.substring(1) : base + ENDPOINT;

        JsonObject body = new JsonObject();
        if (system != null && !system.isBlank()) {
            body.addProperty("system", system);
        }
        body.addProperty("prompt", user);

        HttpRequest request = HttpRequest.newBuilder(java.net.URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return send(request)
                .thenApply(this::parseReply)
                .exceptionally(this::errorText);
    }

    private String parseReply(java.net.http.HttpResponse<String> response) {
        int status = response.statusCode();
        if (status != 200) {
            return "Proxy error " + status + ": " + truncate(response.body(), 200);
        }
        String body = response.body();
        if (body == null) {
            return "No response from proxy.";
        }
        String trimmed = body.trim();
        if (trimmed.isEmpty()) {
            return "No response from proxy.";
        }
        if (trimmed.startsWith("{")) {
            try {
                JsonObject root = JsonParser.parseString(trimmed).getAsJsonObject();
                if (root.has("reply") && !root.get("reply").isJsonNull()) {
                    return stripCodeFences(root.get("reply").getAsString());
                }
                if (root.has("error") && !root.get("error").isJsonNull()) {
                    return "Proxy error: " + root.get("error").getAsString();
                }
            } catch (Exception ignored) {
            }
        }
        return stripCodeFences(body);
    }

    private String errorText(Throwable t) {
        return "Proxy request failed: " + t.getMessage();
    }

    private static String truncate(String s, int n) {
        return s != null && s.length() > n ? s.substring(0, n) + "..." : s;
    }
}
