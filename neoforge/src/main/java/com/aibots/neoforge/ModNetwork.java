package com.aibots.neoforge;

import com.aibots.common.ai.AiConfig;
import com.aibots.common.ai.AiNetworking;
import com.aibots.common.ai.AiProvider;
import com.aibots.common.ai.AiService;
import com.aibots.common.ai.AiSettings;
import com.aibots.common.entity.AiBotEntity;
import com.aibots.common.entity.PlayerConversation;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.function.BiConsumer;

public final class ModNetwork {

    public static final ResourceLocation ASK_ID = ResourceLocation.fromNamespaceAndPath("aibots", "ask");
    public static final ResourceLocation REPLY_ID = ResourceLocation.fromNamespaceAndPath("aibots", "reply");
    public static final ResourceLocation SETTINGS_REQUEST_ID = ResourceLocation.fromNamespaceAndPath("aibots", "settings_request");
    public static final ResourceLocation SETTINGS_SYNC_ID = ResourceLocation.fromNamespaceAndPath("aibots", "settings_sync");
    public static final ResourceLocation SETTINGS_ID = ResourceLocation.fromNamespaceAndPath("aibots", "settings");

    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("aibots").versioned("1").optional();
        registrar.playToServer(AskMessage.TYPE, AskMessage.STREAM_CODEC, ModNetwork::handleAsk);
        registrar.playToClient(ReplyMessage.TYPE, ReplyMessage.STREAM_CODEC, ModNetwork::handleReply);
        registrar.playToServer(SettingsRequestMessage.TYPE, SettingsRequestMessage.STREAM_CODEC,
                ModNetwork::handleSettingsRequest);
        registrar.playToClient(SettingsSyncMessage.TYPE, SettingsSyncMessage.STREAM_CODEC,
                ModNetwork::handleSettingsSync);
        registrar.playToServer(SettingsMessage.TYPE, SettingsMessage.STREAM_CODEC,
                ModNetwork::handleSettings);
    }

    // ---------------- Ask: client -> server ----------------

    public record AskMessage(String message) implements CustomPacketPayload {
        public static final Type<AskMessage> TYPE = new Type<>(ASK_ID);
        public static final StreamCodec<ByteBuf, AskMessage> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, AskMessage::message, AskMessage::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void handleAsk(AskMessage msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                ask(player, msg.message(), (prefix, reply) ->
                        PacketDistributor.sendToPlayer(player, new ReplyMessage(prefix, reply)));
            }
        });
    }

    // ---------------- Reply: server -> client ----------------

    public record ReplyMessage(String prefix, String reply) implements CustomPacketPayload {
        public static final Type<ReplyMessage> TYPE = new Type<>(REPLY_ID);
        public static final StreamCodec<ByteBuf, ReplyMessage> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, ReplyMessage::prefix,
                        ByteBufCodecs.STRING_UTF8, ReplyMessage::reply,
                        ReplyMessage::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void handleReply(ReplyMessage msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> AiNetworking.onReply(msg.prefix(), msg.reply()));
    }

    // ---------------- Settings request: client -> server ----------------

    public record SettingsRequestMessage() implements CustomPacketPayload {
        public static final Type<SettingsRequestMessage> TYPE = new Type<>(SETTINGS_REQUEST_ID);
        public static final StreamCodec<ByteBuf, SettingsRequestMessage> STREAM_CODEC =
                StreamCodec.unit(new SettingsRequestMessage());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void handleSettingsRequest(SettingsRequestMessage msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                sendSync(player);
            }
        });
    }

    // ---------------- Settings sync: server -> client ----------------

    public record SettingsSyncMessage(AiProvider provider, String apiKey, String model)
            implements CustomPacketPayload {
        public static final Type<SettingsSyncMessage> TYPE = new Type<>(SETTINGS_SYNC_ID);
        public static final StreamCodec<ByteBuf, SettingsSyncMessage> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8.map(ModNetwork::parseProvider, AiProvider::name),
                        SettingsSyncMessage::provider,
                        ByteBufCodecs.STRING_UTF8, SettingsSyncMessage::apiKey,
                        ByteBufCodecs.STRING_UTF8, SettingsSyncMessage::model,
                        SettingsSyncMessage::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void handleSettingsSync(SettingsSyncMessage msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> AiNetworking.onSettings(
                new AiSettings(msg.provider(), msg.apiKey(), msg.model())));
    }

    // ---------------- Settings save: client -> server ----------------

    public record SettingsMessage(AiProvider provider, String apiKey, String model)
            implements CustomPacketPayload {
        public static final Type<SettingsMessage> TYPE = new Type<>(SETTINGS_ID);
        public static final StreamCodec<ByteBuf, SettingsMessage> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8.map(ModNetwork::parseProvider, AiProvider::name),
                        SettingsMessage::provider,
                        ByteBufCodecs.STRING_UTF8, SettingsMessage::apiKey,
                        ByteBufCodecs.STRING_UTF8, SettingsMessage::model,
                        SettingsMessage::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void handleSettings(SettingsMessage msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                AiConfig.applyClientSettings(msg.provider(), msg.apiKey(), msg.model());
                sendSync(player);
            }
        });
    }

    static AiProvider parseProvider(String name) {
        try {
            return AiProvider.valueOf(name);
        } catch (Exception e) {
            return AiProvider.OPENROUTER;
        }
    }

    static void sendSync(ServerPlayer player) {
        AiConfig cfg = AiConfig.get();
        PacketDistributor.sendToPlayer(player,
                new SettingsSyncMessage(cfg.provider, cfg.apiKey(), cfg.model()));
    }

    /** Shared ask logic used by both the command and the GUI path. */
    public static void ask(ServerPlayer player, String message, BiConsumer<String, String> callback) {
        AiBotEntity target = PlayerConversation.getTarget(player);
        String prefix;
        String system;
        if (target != null) {
            prefix = "<" + target.botName() + "> ";
            system = target.getPersonality();
        } else {
            prefix = "";
            system = AiConfig.get().systemPrompt;
        }

        player.sendSystemMessage(Component.literal("Thinking...").withStyle(ChatFormatting.GRAY));
        AiService.ask(system, message).thenAccept(reply ->
                player.server.execute(() -> callback.accept(prefix, reply)));
    }
}
