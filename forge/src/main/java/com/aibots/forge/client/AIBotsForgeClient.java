package com.aibots.forge.client;

import com.aibots.common.ai.AiNetworking;
import com.aibots.forge.ModEntities;
import com.aibots.forge.ModNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AIBotsForgeClient.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class AIBotsForgeClient {

    public static final String MOD_ID = "aibots";

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.AI_BOT.get(), AiBotRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        KeyHandler.register(event);
        AiNetworking.setAskSender(msg -> ModNetwork.CHANNEL.sendToServer(new ModNetwork.AskMessage(msg)));
        AiNetworking.setSettingsRequestSender(() ->
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.SettingsRequestMessage()));
        AiNetworking.setSettingsSender(settings ->
                ModNetwork.CHANNEL.sendToServer(new ModNetwork.SettingsMessage(
                        settings.provider, settings.apiKey, settings.model)));
    }
}
