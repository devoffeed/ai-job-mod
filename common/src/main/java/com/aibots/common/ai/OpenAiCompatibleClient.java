package com.aibots.common.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * OpenAI-compatible chat completions client.
 * Works for both OpenAI (api.openai.com/v1) and OpenRouter (openrouter.ai/api/v1)
 * since OpenRouter exposes the same /chat/completions contract.
 */
public class OpenAiCompatibleClient extends AiClient {

    public OpenAiCompatibleClient(AiConfig config) {
        super(config);
    }

    @Override
    public CompletableFuture<String> chatAsync(String system, String user) {
        String url = config.baseUrl() + "/chat/completions";

        JsonArray messages = new JsonArray();
        if (system != null && !system.isBlank()) {
            JsonObject sys = new JsonObject();
            sys.addProperty("role", "system");
            sys.addProperty("content", system);
            messages.add(sys);
        }
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", user);
        messages.add(userMsg);

        JsonObject body = new JsonObject();
        body.addProperty("model", config.model());
        body.add("messages", messages);
        body.addProperty("max_tokens", config.maxTokens);
        body.addProperty("temperature", 0.7);

        HttpRequest request = HttpRequest.newBuilder(java.net.URI.create(url))
                .timeout(Duration.ofSeconds(config.timeoutSeconds))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + config.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return send(request)
                .thenApply(this::parseReply)
                .exceptionally(this::errorText);
    }

    private String parseReply(java.net.http.HttpResponse<String> response) {
        int status = response.statusCode();
        if (status != 200) {
            return "API error " + status + ": " + truncate(response.body(), 200);
        }
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            return "No response from API.";
        }
        JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        String content = message.has("content") && !message.get("content").isJsonNull()
                ? message.get("content").getAsString()
                : "";
        if (content.isBlank() && message.has("refusal") && !message.get("refusal").isJsonNull()) {
            content = "Refused: " + message.get("refusal").getAsString();
        }
        return stripCodeFences(content);
    }

    private String errorText(Throwable t) {
        return "Request failed: " + t.getMessage();
    }

    private static String truncate(String s, int n) {
        return s != null && s.length() > n ? s.substring(0, n) + "..." : s;
    }
}
