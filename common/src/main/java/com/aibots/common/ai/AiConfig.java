package com.aibots.common.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistent AI configuration. Stored as JSON in <gameDir>/config/aibots.json.
 * The file is created with sensible defaults on first launch.
 */
public final class AiConfig {

    public static final String FILE_NAME = "aibots.json";

    public AiProvider provider = AiProvider.OPENROUTER;
    public String openaiApiKey = "";
    public String openaiModel = "gpt-4o-mini";
    public String openaiBaseUrl = "https://api.openai.com/v1";
    public String geminiApiKey = "";
    public String geminiModel = "gemini-3.5-flash-lite";
    public String openrouterApiKey = "";
    public String openrouterModel = "openai/gpt-4o-mini";
    public String localBaseUrl = "http://localhost:8844";
    public String localModel = "gemini-2.0-flash";
    public String systemPrompt = "You are a friendly assistant living inside Minecraft. " +
            "Answer the player's questions helpfully and stay in character. Keep answers under 200 words.";
    public int timeoutSeconds = 60;
    public int maxTokens = 512;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static AiConfig instance;
    private static Path configDir;

    public static AiConfig get() {
        if (instance == null) {
            instance = new AiConfig();
        }
        return instance;
    }

    /** Applies provider + API key + model chosen in the in-game GUI and persists. */
    public static void applyClientSettings(AiProvider provider, String apiKey, String model) {
        AiConfig cfg = get();
        cfg.provider = provider;
        switch (provider) {
            case OPENAI -> {
                cfg.openaiApiKey = apiKey;
                if (!model.isEmpty()) {
                    cfg.openaiModel = model;
                }
            }
            case GEMINI -> {
                cfg.geminiApiKey = apiKey;
                if (!model.isEmpty()) {
                    cfg.geminiModel = model;
                }
            }
            case OPENROUTER -> {
                cfg.openrouterApiKey = apiKey;
                if (!model.isEmpty()) {
                    cfg.openrouterModel = model;
                }
            }
            case LOCAL -> {
                if (!model.isEmpty()) {
                    cfg.localModel = model;
                }
            }
        }
        save(configDir);
    }

    public static void load(Path dir) {
        configDir = dir;
        Path file = configDir.resolve(FILE_NAME);
        try {
            if (Files.exists(file)) {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                instance = GSON.fromJson(json, AiConfig.class);
                if (instance == null) {
                    instance = new AiConfig();
                }
            } else {
                instance = new AiConfig();
                save(configDir);
            }
        } catch (Exception e) {
            instance = new AiConfig();
        }
    }

    public static void save(Path configDir) {
        try {
            Files.createDirectories(configDir);
            Path file = configDir.resolve(FILE_NAME);
            Files.writeString(file, GSON.toJson(get()), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    /** API key for the currently selected provider, or "" if not set. */
    public String apiKey() {
        return switch (provider) {
            case OPENAI -> openaiApiKey;
            case GEMINI -> geminiApiKey;
            case OPENROUTER -> openrouterApiKey;
            case LOCAL -> "";
        };
    }

    /** Model name for the currently selected provider. */
    public String model() {
        return switch (provider) {
            case OPENAI -> openaiModel;
            case GEMINI -> geminiModel;
            case OPENROUTER -> openrouterModel;
            case LOCAL -> localModel;
        };
    }

    /** Endpoint base URL for the currently selected provider (OpenAI-style APIs). */
    public String baseUrl() {
        return switch (provider) {
            case OPENAI -> openaiBaseUrl;
            case GEMINI -> "";
            case OPENROUTER -> "https://openrouter.ai/api/v1";
            case LOCAL -> localBaseUrl;
        };
    }

    /** Path to the debug log file (config/aibots-debug.log), or null if not initialised. */
    public static Path debugLogPath() {
        return configDir == null ? null : configDir.resolve("aibots-debug.log");
    }

    public JsonObject toJson() {
        return (JsonObject) GSON.toJsonTree(this);
    }
}
