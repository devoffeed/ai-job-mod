package com.aibots.common.ai;

/** Supported AI providers. */
public enum AiProvider {
    OPENAI,
    GEMINI,
    OPENROUTER,
    /** Local proxy (e.g. http://localhost:8844) that forwards to an AI API. */
    LOCAL
}
