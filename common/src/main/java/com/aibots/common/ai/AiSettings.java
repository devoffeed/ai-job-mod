package com.aibots.common.ai;

/** Settings chosen in the in-game GUI: provider + API key + model. */
public final class AiSettings {

    public final AiProvider provider;
    public final String apiKey;
    public final String model;

    public AiSettings(AiProvider provider, String apiKey, String model) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.model = model;
    }
}
