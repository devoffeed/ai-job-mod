package com.aibots.common.ai;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Bridge between the client GUI (common) and each loader's networking layer.
 * The loader module installs {@link #setAskSender} during client init and routes
 * replies into {@link #onReply}; the open screen listens via {@link #setReplyListener}.
 * Settings flow: the GUI requests the current settings ({@link #requestSettings}),
 * the server replies with a sync ({@link #onSettings}), and saving sends new values
 * back ({@link #sendSettings}).
 */
public final class AiNetworking {

    private static Consumer<String> askSender = s -> {
    };
    private static BiConsumer<String, String> replyListener = (prefix, reply) -> {
    };
    private static Runnable settingsRequestSender = () -> {
    };
    private static Consumer<AiSettings> settingsSender = s -> {
    };
    private static Consumer<AiSettings> settingsListener = s -> {
    };

    private AiNetworking() {
    }

    /** Installed by the loader's client code: sends the user message to the server. */
    public static void setAskSender(Consumer<String> sender) {
        askSender = sender == null ? s -> {
        } : sender;
    }

    /** The open GUI screen subscribes here to receive replies. */
    public static void setReplyListener(BiConsumer<String, String> listener) {
        replyListener = listener == null ? (p, r) -> {
        } : listener;
    }

    public static void clearReplyListener() {
        replyListener = (p, r) -> {
        };
    }

    /** Installed by the loader's client code: asks the server for the current settings. */
    public static void setSettingsRequestSender(Runnable sender) {
        settingsRequestSender = sender == null ? () -> {
        } : sender;
    }

    /** Installed by the loader's client code: sends new settings to the server. */
    public static void setSettingsSender(Consumer<AiSettings> sender) {
        settingsSender = sender == null ? s -> {
        } : sender;
    }

    /** The open GUI screen subscribes here to receive the current settings. */
    public static void setSettingsListener(Consumer<AiSettings> listener) {
        settingsListener = listener == null ? s -> {
        } : listener;
    }

    public static void clearSettingsListener() {
        settingsListener = s -> {
        };
    }

    /** Called by the GUI when the player submits a message. */
    public static void sendAsk(String message) {
        askSender.accept(message);
    }

    /** Called by the loader's client network handler with the AI reply. */
    public static void onReply(String prefix, String reply) {
        replyListener.accept(prefix, reply);
    }

    /** Called by the GUI when opening the settings panel. */
    public static void requestSettings() {
        settingsRequestSender.run();
    }

    /** Called by the GUI when the player saves settings. */
    public static void sendSettings(AiSettings settings) {
        settingsSender.accept(settings);
    }

    /** Called by the loader's client network handler with the current settings. */
    public static void onSettings(AiSettings settings) {
        settingsListener.accept(settings);
    }
}
