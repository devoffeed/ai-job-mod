package com.aibots.neoforge.client;

import com.aibots.common.ai.AiConfig;
import com.aibots.common.ai.AiNetworking;
import com.aibots.common.ai.AiProvider;
import com.aibots.common.ai.LocalProxyClient;
import com.aibots.neoforge.ModEntities;
import com.aibots.neoforge.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "aibots", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class AIBotsNeoForgeClient {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.AI_BOT.get(), AiBotRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        KeyHandler.register(event);
        AiNetworking.setAskSender(msg -> sendAsk(msg));
        AiNetworking.setSettingsRequestSender(() ->
                send(new ModNetwork.SettingsRequestMessage()));
        AiNetworking.setSettingsSender(settings ->
                send(new ModNetwork.SettingsMessage(
                        settings.provider, settings.apiKey, settings.model)));
        NeoForge.EVENT_BUS.addListener(KeyHandler::onClientTick);
    }

    /**
     * Sends a modded payload to the server only if the remote side supports the
     * channel. On plain vanilla servers no channel exists, so sending would make
     * NeoForge throw {@link UnsupportedOperationException}; show a notice instead.
     */
    private static void send(CustomPacketPayload payload) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection instanceof ICommonPacketListener listener
                && listener.hasChannel(payload.type().id())) {
            PacketDistributor.sendToServer(payload);
        } else {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(
                        Component.literal("AI Bots: this server does not support the mod")
                                .withStyle(ChatFormatting.RED), true);
            }
        }
    }

    /**
     * Routes an AI ask to the server when the channel exists; otherwise falls back
     * to the local proxy so chatting works on any server (even vanilla ones where
     * the mod's server side is absent).
     */
    private static void sendAsk(String message) {
        var payload = new ModNetwork.AskMessage(message);
        var connection = Minecraft.getInstance().getConnection();
        if (connection instanceof ICommonPacketListener listener
                && listener.hasChannel(payload.type().id())) {
            PacketDistributor.sendToServer(payload);
            return;
        }
        localAsk(message);
    }

    private static void localAsk(String message) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player != null) {
            player.displayClientMessage(
                    Component.literal("AI Bots: local proxy mode").withStyle(ChatFormatting.GRAY),
                    true);
        }
        AiConfig cfg = AiConfig.get();
        var local = new AiConfig();
        local.provider = AiProvider.LOCAL;
        local.localBaseUrl = cfg.localBaseUrl;
        var client = new LocalProxyClient(local);
        client.chatAsync(cfg.systemPrompt, message).thenAccept(reply ->
                mc.execute(() -> AiNetworking.onReply("", reply)));
    }
}
