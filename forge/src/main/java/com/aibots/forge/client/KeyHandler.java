package com.aibots.forge.client;

import com.aibots.common.client.AiBotScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

public final class KeyHandler {

    public static final KeyMapping OPEN_AI = new KeyMapping(
            "key.aibots.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "key.categories.aibots");

    private KeyHandler() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_AI);
        MinecraftForge.EVENT_BUS.register(KeyHandler.class);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && OPEN_AI.consumeClick()) {
            Minecraft.getInstance().setScreen(new AiBotScreen());
        }
    }
}
