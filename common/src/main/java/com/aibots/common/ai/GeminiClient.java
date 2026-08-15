package com.aibots.common.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Google Gemini (generativelanguage.googleapis.com) chat client. */
public class GeminiClient extends AiClient {

    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta";

    public GeminiClient(AiConfig config) {
        super(config);
    }

    @Override
    public CompletableFuture<String> chatAsync(String system, String user) {
        String url = ENDPOINT + "/models/" + config.model() + ":generateContent?key=" + config.apiKey();

        JsonObject body = new JsonObject();

        if (system != null && !system.isBlank()) {
            JsonObject sysPart = new JsonObject();
            sysPart.addProperty("text", system);
            JsonObject sysParts = new JsonObject();
            sysParts.add("parts", arr(sysPart));
            body.add("system_instruction", sysParts);
        }

        JsonObject userPart = new JsonObject();
        userPart.addProperty("text", user);
        JsonObject content = new JsonObject();
        content.add("parts", arr(userPart));
        body.add("contents", arr(content));

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("maxOutputTokens", config.maxTokens);
        generationConfig.addProperty("temperature", 0.7);
        body.add("generationConfig", generationConfig);

        HttpRequest request = HttpRequest.newBuilder(java.net.URI.create(url))
                .timeout(Duration.ofSeconds(config.timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return send(request)
                .thenApply(this::parseReply)
                .exceptionally(this::errorText);
    }

    private static JsonArray arr(JsonObject... items) {
        JsonArray a = new JsonArray();
        for (JsonObject o : items) a.add(o);
        return a;
    }

    private String parseReply(java.net.http.HttpResponse<String> response) {
        int status = response.statusCode();
        if (status != 200) {
            return "API error " + status + ": " + truncate(response.body(), 200);
        }
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray candidates = root.has("candidates") ? root.getAsJsonArray("candidates") : null;
        if (candidates == null || candidates.isEmpty()) {
            if (root.has("promptFeedback")) {
                JsonObject fb = root.getAsJsonObject("promptFeedback");
                if (fb.has("blockReason")) {
                    return "Blocked by API: " + fb.get("blockReason").getAsString();
                }
            }
            return "No response from API.";
        }
        JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
        JsonArray parts = content.getAsJsonArray("parts");
        if (parts == null || parts.isEmpty()) {
            return "Empty response from API.";
        }
        StringBuilder sb = new StringBuilder();
        for (var part : parts) {
            JsonObject p = part.getAsJsonObject();
            if (p.has("text")) {
                sb.append(p.get("text").getAsString());
            }
        }
        return stripCodeFences(sb.toString());
    }

    private String errorText(Throwable t) {
        return "Request failed: " + t.getMessage();
    }

    private static String truncate(String s, int n) {
        return s != null && s.length() > n ? s.substring(0, n) + "..." : s;
    }
}
