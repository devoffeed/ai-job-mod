package com.aibots.common.client;

import com.aibots.common.ai.AiNetworking;
import com.aibots.common.ai.AiProvider;
import com.aibots.common.ai.AiService;
import com.aibots.common.ai.AiSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Centered AI chat screen. Press M to open, Esc or the Close button to dismiss.
 * Messages are sent to the server via {@link AiNetworking}; replies render here.
 * The Settings button opens a panel where the player can pick the AI provider
 * (Gemini / OpenRouter / OpenAI) and enter the API key + model. Pure client
 * rendering classes only, so it compiles in both loaders.
 */
public class AiBotScreen extends Screen {

    private static final int WIDTH = 420;
    private static final int HEIGHT = 260;
    private static final int MAX_LINES = 14;

    private boolean settingsMode;

    private EditBox input;
    private EditBox apiKeyField;
    private EditBox modelField;
    private AiProvider selectedProvider = AiProvider.OPENROUTER;
    private Button providerOpenAI;
    private Button providerGemini;
    private Button providerOpenRouter;
    private Button providerLocal;

    private final Deque<String> log = new ArrayDeque<>();

    public AiBotScreen() {
        super(Component.literal("AI Bots"));
    }

    @Override
    protected void init() {
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;

        if (settingsMode) {
            int providerRowY = top + 40;
            providerOpenAI = providerButton(AiProvider.OPENAI, left + 12, providerRowY);
            providerGemini = providerButton(AiProvider.GEMINI, left + 112, providerRowY);
            providerOpenRouter = providerButton(AiProvider.OPENROUTER, left + 212, providerRowY);
            providerLocal = providerButton(AiProvider.LOCAL, left + 312, providerRowY);
            refreshProviderButtons();

            addRenderableWidget(providerOpenAI);
            addRenderableWidget(providerGemini);
            addRenderableWidget(providerOpenRouter);
            addRenderableWidget(providerLocal);

            apiKeyField = new EditBox(font, left + 12, top + 76, WIDTH - 24, 20,
                    Component.literal("API Key"));
            apiKeyField.setMaxLength(512);
            apiKeyField.setHint(Component.literal("Paste your API key here..."));
            modelField = new EditBox(font, left + 12, top + 112, WIDTH - 24, 20,
                    Component.literal("Model"));
            modelField.setMaxLength(128);
            modelField.setHint(Component.literal("Optional model override (e.g. gemini-3.1-flash-lite)"));

            Button saveButton = Button.builder(Component.literal("Save"), b -> saveSettings())
                    .bounds(left + 12, top + HEIGHT - 40, (WIDTH - 36) / 2, 20)
                    .build();
            Button backButton = Button.builder(Component.literal("Back"), b -> openChat())
                    .bounds(left + WIDTH - 24 - (WIDTH - 36) / 2, top + HEIGHT - 40, (WIDTH - 36) / 2, 20)
                    .build();

            addRenderableWidget(apiKeyField);
            addRenderableWidget(modelField);
            addRenderableWidget(saveButton);
            addRenderableWidget(backButton);

            AiNetworking.requestSettings();
            setInitialFocus(apiKeyField);
            return;
        }

        input = new EditBox(font, left + 12, top + HEIGHT - 40, WIDTH - 128, 20, Component.literal("Ask..."));
        input.setMaxLength(512);
        input.setHint(Component.literal("Ask the AI..."));

        Button askButton = Button.builder(Component.literal("Ask"), b -> submit())
                .bounds(left + WIDTH - 104, top + HEIGHT - 40, 92, 20)
                .build();
        Button settingsButton = Button.builder(Component.literal("Settings"), b -> openSettings())
                .bounds(left + 12, top + HEIGHT - 16, 92, 12)
                .build();
        Button closeButton = Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(left + WIDTH - 104, top + HEIGHT - 16, 92, 12)
                .build();

        addRenderableWidget(input);
        addRenderableWidget(askButton);
        addRenderableWidget(settingsButton);
        addRenderableWidget(closeButton);

        AiNetworking.setReplyListener(this::appendReply);
        AiNetworking.setSettingsListener(this::applySettings);
        setInitialFocus(input);
    }

    private void openSettings() {
        settingsMode = true;
        clearWidgets();
        init();
    }

    private void openChat() {
        settingsMode = false;
        clearWidgets();
        init();
    }

    private Button providerButton(AiProvider provider, int x, int y) {
        return Button.builder(
                        Component.literal(providerDisplayName(provider)),
                        b -> selectProvider(provider))
                .bounds(x, y, 94, 20)
                .build();
    }

    private void selectProvider(AiProvider provider) {
        selectedProvider = provider;
        refreshProviderButtons();
    }

    private void refreshProviderButtons() {
        providerOpenAI.active = selectedProvider != AiProvider.OPENAI;
        providerGemini.active = selectedProvider != AiProvider.GEMINI;
        providerOpenRouter.active = selectedProvider != AiProvider.OPENROUTER;
        providerLocal.active = selectedProvider != AiProvider.LOCAL;
    }

    private void saveSettings() {
        String key = apiKeyField.getValue().trim();
        String model = modelField.getValue().trim();
        if (selectedProvider != AiProvider.LOCAL && key.isEmpty()) {
            appendLog("! API key is empty");
            return;
        }
        AiNetworking.sendSettings(new AiSettings(selectedProvider, key, model));
        appendLog("[Settings] Saved provider: " + providerDisplayName(selectedProvider));
        openChat();
    }

    /** Applies the settings synced back from the server into the open fields. */
    private void applySettings(AiSettings settings) {
        selectedProvider = settings.provider;
        refreshProviderButtons();
        if (apiKeyField != null) {
            apiKeyField.setValue(settings.apiKey);
        }
        if (modelField != null && !settings.model.isEmpty()) {
            modelField.setValue(settings.model);
        }
    }

    private static String providerDisplayName(AiProvider provider) {
        return switch (provider) {
            case OPENAI -> "OpenAI";
            case GEMINI -> "Gemini";
            case OPENROUTER -> "OpenRouter";
            case LOCAL -> "Local";
        };
    }

    private void submit() {
        String message = input.getValue().trim();
        if (message.isEmpty()) {
            return;
        }
        appendLog("> " + message);
        input.setValue("");
        AiNetworking.sendAsk(message);
    }

    private void appendReply(String prefix, String reply) {
        String line = prefix + reply;
        // simple word-wrap to the panel width
        int maxChars = (WIDTH - 32) / Math.max(6, font.width("M") / 2 + 1);
        while (line.length() > maxChars) {
            int cut = Math.min(maxChars, line.length());
            int space = line.lastIndexOf(' ', cut);
            if (space > maxChars / 2) {
                cut = space;
            }
            appendLog(line.substring(0, cut));
            line = line.substring(cut).trim();
        }
        appendLog(line);
    }

    private void appendLog(String line) {
        log.addLast(AiService.sanitize(line));
        while (log.size() > MAX_LINES) {
            log.removeFirst();
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick); // draws dim background + widgets
        int left = (width - WIDTH) / 2;
        int top = (height - HEIGHT) / 2;

        // panel background
        gui.fill(left, top, left + WIDTH, top + HEIGHT, 0xEE101318);
        gui.fill(left, top, left + WIDTH, top + 28, 0xEE1D6FD7);

        // title
        gui.drawString(font, settingsMode ? "AI Bots - Settings" : "AI Bots",
                left + 12, top + 8, 0xFFFFFFFF);

        if (settingsMode) {
            gui.drawString(font, "Provider", left + 12, top + 28, 0xFFE0E0E0);
            gui.drawString(font, "API Key", left + 12, top + 62, 0xFFE0E0E0);
            gui.drawString(font, "Model (optional)", left + 12, top + 98, 0xFFE0E0E0);
            return;
        }

        // log area (scrolled by dropping oldest)
        int y = top + 36;
        for (String line : log) {
            gui.drawString(font, line, left + 12, y, 0xFFE0E0E0);
            y += 12;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!settingsMode && (keyCode == 257 || keyCode == 335)) { // Enter / Numpad Enter
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (settingsMode && apiKeyField != null) {
            String key = apiKeyField.getValue().trim();
            String model = modelField.getValue().trim();
            if (!key.isEmpty() || selectedProvider == AiProvider.LOCAL) {
                AiNetworking.sendSettings(new AiSettings(selectedProvider, key, model));
            }
        }
        AiNetworking.clearReplyListener();
        AiNetworking.clearSettingsListener();
        super.onClose();
    }
}
