package com.aibots.common.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * A single-turn chat completion request against an AI provider.
 * Implementations are plain Java SE + java.net.http, no external deps.
 */
public abstract class AiClient {

    protected final AiConfig config;
    protected final HttpClient http;

    protected AiClient(AiConfig config) {
        this.config = config;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public static AiClient create(AiConfig config) {
        return switch (config.provider) {
            case OPENAI, OPENROUTER -> new OpenAiCompatibleClient(config);
            case GEMINI -> new GeminiClient(config);
            case LOCAL -> new LocalProxyClient(config);
        };
    }

    /**
     * Sends a chat request asynchronously.
     *
     * @param system system prompt
     * @param user   user message
     * @return future of the assistant's reply text
     */
    public abstract CompletableFuture<String> chatAsync(String system, String user);

    // ---------- helpers ----------

    protected HttpRequest buildPost(String url, String json) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(config.timeoutSeconds))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
    }

    protected CompletableFuture<HttpResponse<String>> send(HttpRequest request) {
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    protected String stripCodeFences(String text) {
        if (text == null) return "";
        String t = text.trim();
        if (t.startsWith("```") && t.length() >= 6) {
            int firstNl = t.indexOf('\n');
            if (firstNl > 0) {
                t = t.substring(firstNl + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        return t.trim();
    }
}
