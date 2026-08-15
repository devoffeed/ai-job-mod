package com.aibots.neoforge.client;

import com.aibots.common.client.AiBotScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class KeyHandler {

    public static final KeyMapping OPEN_AI = new KeyMapping(
            "key.aibots.open",
            GLFW.GLFW_KEY_M,
            "key.categories.aibots");

    private KeyHandler() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_AI);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        while (OPEN_AI.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof AiBotScreen) {
                mc.setScreen(null);
            } else {
                mc.setScreen(new AiBotScreen());
            }
        }
    }
}
