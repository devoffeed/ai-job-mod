package com.aibots.common.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry point for making AI chat requests from the game.
 * Runs requests on a shared background thread pool so the server thread is never blocked.
 */
public final class AiService {

    private static volatile ExecutorService executor;

    private static ExecutorService executor() {
        ExecutorService ex = executor;
        if (ex == null) {
            synchronized (AiService.class) {
                if (executor == null) {
                    executor = Executors.newCachedThreadPool(r -> {
                        Thread t = new Thread(r, "ai-bots-request");
                        t.setDaemon(true);
                        return t;
                    });
                }
                ex = executor;
            }
        }
        return ex;
    }

    /**
     * Asks the configured AI provider asynchronously.
     *
     * @param system system prompt (may be null to use config default)
     * @param user   user message
     * @return future resolving to the reply text (or an error description string)
     */
    public static CompletableFuture<String> ask(String system, String user) {
        AiConfig cfg = AiConfig.get();
        String sys = (system == null || system.isBlank()) ? cfg.systemPrompt : system;
        AiClient client = AiClient.create(cfg);
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sanitize(client.chatAsync(sys, user).get());
            } catch (Exception e) {
                return "Request failed: " + e.getMessage();
            }
        }, executor());
    }

    /**
     * Removes characters that would render as garbage in Minecraft: control
     * characters, control pictures (U+2400-U+243F, e.g. "LF in a box"),
     * variation selectors, zero-width joiners/formatting marks, emoji skin-tone
     * modifiers, private-use and unassigned codepoints. Everything else is kept
     * as-is, including emoji: Minecraft 1.20.2+ falls back to GNU Unifont for
     * any codepoint not in the vanilla glyphs, so emoji render as monochrome
     * glyphs instead of tofu boxes. Line/paragraph separators (U+2028/U+2029)
     * are converted to plain newlines.
     */
    public static String sanitize(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        StringBuilder sb = new StringBuilder(text.length());
        int dropped = 0;
        StringBuilder droppedInfo = new StringBuilder();
        Set<Integer> keptUnusual = new LinkedHashSet<>();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == 0x2028 || cp == 0x2029) {
                sb.append('\n');
            } else if (isRenderable(cp)) {
                sb.appendCodePoint(cp);
                if (cp > 0x24F) {
                    keptUnusual.add(cp);
                }
            } else {
                dropped++;
                droppedInfo.append(String.format("U+%04X(%s) ", cp, printableCp(cp)));
            }
        }
        if (dropped > 0) {
            debugLog("sanitize dropped " + dropped + ": " + droppedInfo + " in: " + text);
        }
        if (!keptUnusual.isEmpty()) {
            StringBuilder kept = new StringBuilder();
            for (int cp : keptUnusual) {
                kept.append(String.format("U+%04X(%s) ", cp, printableCp(cp)));
            }
            debugLog("sanitize kept unusual: " + kept + " in: " + text);
        }
        return sb.toString();
    }

    private static String printableCp(int cp) {
        try {
            char[] chars = Character.toChars(cp);
            return String.valueOf(chars);
        } catch (Exception e) {
            return "?";
        }
    }

    private static void debugLog(String line) {
        try {
            Path p = AiConfig.debugLogPath();
            if (p != null) {
                Files.writeString(p, line + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (IOException ignored) {
        }
    }

    private static boolean isRenderable(int cp) {
        if (cp == '\n' || cp == '\t' || cp == '\r') {
            return true;
        }
        if (cp < 0x20 || (cp >= 0x7F && cp <= 0x9F)) { // C0/C1 controls
            return false;
        }
        if (cp >= 0x2400 && cp <= 0x243F) { // control pictures (e.g. "LF in a box")
            return false;
        }
        if (cp >= 0xD800 && cp <= 0xDFFF) { // surrogates (never appear alone)
            return false;
        }
        if (cp >= 0xE000 && cp <= 0xF8FF) { // private use area
            return false;
        }
        if (cp >= 0xF0000 && cp <= 0x10FFFD) { // supplementary private use areas
            return false;
        }
        if ((cp & 0xFFFF) >= 0xFFFE) { // noncharacters U+FFFE/U+FFFF (and plane-end)
            return false;
        }
        if (cp >= 0x200B && cp <= 0x200F) { // zero-width space, ZWNJ, ZWJ, LRM/RLM
            return false;
        }
        if (cp >= 0x202A && cp <= 0x202E) { // bidi embedding/override controls
            return false;
        }
        if (cp >= 0x2060 && cp <= 0x206F) { // word joiner, invisible operators
            return false;
        }
        if (cp == 0xFEFF) { // zero-width no-break space / BOM
            return false;
        }
        if (cp >= 0xFE00 && cp <= 0xFE0F) { // variation selectors
            return false;
        }
        if (cp >= 0x1F3FB && cp <= 0x1F3FF) { // emoji skin-tone modifiers
            return false;
        }
        if (cp >= 0xE0000 && cp <= 0xE007F) { // tag characters (flags etc.)
            return false;
        }
        // Anything else is kept: Unifont fallback renders it (emoji, Cyrillic,
        // CJK, math, symbols, ...) even if vanilla glyphs do not.
        return true;
    }

    /** Convenience overload using the configured default system prompt. */
    public static CompletableFuture<String> ask(String user) {
        return ask(null, user);
    }

    /** Shuts down the thread pool. Call on mod unload if needed. */
    public static void shutdown() {
        ExecutorService ex = executor;
        if (ex != null) {
            ex.shutdownNow();
        }
    }
}
