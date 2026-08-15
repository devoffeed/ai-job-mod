package com.aibots.forge;

import com.aibots.common.ai.AiConfig;
import com.aibots.common.ai.AiNetworking;
import com.aibots.common.ai.AiProvider;
import com.aibots.common.ai.AiService;
import com.aibots.common.ai.AiSettings;
import com.aibots.common.entity.AiBotEntity;
import com.aibots.common.entity.PlayerConversation;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public final class ModNetwork {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(AIBotsForge.MOD_ID, "main"))
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .simpleChannel();

    private ModNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, AskMessage.class, AskMessage::encode, AskMessage::decode,
                AskMessage::handle);
        CHANNEL.registerMessage(id++, ReplyMessage.class, ReplyMessage::encode, ReplyMessage::decode,
                ReplyMessage::handle);
        CHANNEL.registerMessage(id++, SettingsRequestMessage.class, SettingsRequestMessage::encode,
                SettingsRequestMessage::decode, SettingsRequestMessage::handle);
        CHANNEL.registerMessage(id++, SettingsSyncMessage.class, SettingsSyncMessage::encode,
                SettingsSyncMessage::decode, SettingsSyncMessage::handle);
        CHANNEL.registerMessage(id++, SettingsMessage.class, SettingsMessage::encode,
                SettingsMessage::decode, SettingsMessage::handle);
    }

    // ---------------- Ask: client -> server ----------------

    public static class AskMessage {
        final String message;

        public AskMessage(String message) {
            this.message = message;
        }

        static void encode(AskMessage msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.message, 512);
        }

        static AskMessage decode(FriendlyByteBuf buf) {
            return new AskMessage(buf.readUtf(512));
        }

        static void handle(AskMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) {
                    return;
                }
                ask(player, msg.message, (prefix, reply) ->
                        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                                new ReplyMessage(prefix, reply)));
            });
            context.setPacketHandled(true);
        }
    }

    // ---------------- Reply: server -> client ----------------

    public static class ReplyMessage {
        final String prefix;
        final String reply;

        public ReplyMessage(String prefix, String reply) {
            this.prefix = prefix;
            this.reply = reply;
        }

        static void encode(ReplyMessage msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.prefix, 128);
            buf.writeUtf(msg.reply, 8192);
        }

        static ReplyMessage decode(FriendlyByteBuf buf) {
            return new ReplyMessage(buf.readUtf(128), buf.readUtf(8192));
        }

        static void handle(ReplyMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> AiNetworking.onReply(msg.prefix, msg.reply));
            context.setPacketHandled(true);
        }
    }

    // ---------------- Settings request: client -> server ----------------

    public static class SettingsRequestMessage {
        public SettingsRequestMessage() {
        }

        static void encode(SettingsRequestMessage msg, FriendlyByteBuf buf) {
        }

        static SettingsRequestMessage decode(FriendlyByteBuf buf) {
            return new SettingsRequestMessage();
        }

        static void handle(SettingsRequestMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) {
                    return;
                }
                sendSync(player);
            });
            context.setPacketHandled(true);
        }
    }

    // ---------------- Settings sync: server -> client ----------------

    public static class SettingsSyncMessage {
        final AiProvider provider;
        final String apiKey;
        final String model;

        SettingsSyncMessage(AiProvider provider, String apiKey, String model) {
            this.provider = provider;
            this.apiKey = apiKey;
            this.model = model;
        }

        static void encode(SettingsSyncMessage msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.provider.name());
            buf.writeUtf(msg.apiKey, 512);
            buf.writeUtf(msg.model, 128);
        }

        static SettingsSyncMessage decode(FriendlyByteBuf buf) {
            return new SettingsSyncMessage(parseProvider(buf.readUtf(32)),
                    buf.readUtf(512), buf.readUtf(128));
        }

        static void handle(SettingsSyncMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() ->
                    AiNetworking.onSettings(new AiSettings(msg.provider, msg.apiKey, msg.model)));
            context.setPacketHandled(true);
        }
    }

    // ---------------- Settings save: client -> server ----------------

    public static class SettingsMessage {
        final AiProvider provider;
        final String apiKey;
        final String model;

        public SettingsMessage(AiProvider provider, String apiKey, String model) {
            this.provider = provider;
            this.apiKey = apiKey;
            this.model = model;
        }

        static void encode(SettingsMessage msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.provider.name());
            buf.writeUtf(msg.apiKey, 512);
            buf.writeUtf(msg.model, 128);
        }

        static SettingsMessage decode(FriendlyByteBuf buf) {
            return new SettingsMessage(parseProvider(buf.readUtf(32)),
                    buf.readUtf(512), buf.readUtf(128));
        }

        static void handle(SettingsMessage msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) {
                    return;
                }
                AiConfig.applyClientSettings(msg.provider, msg.apiKey, msg.model);
                sendSync(player);
            });
            context.setPacketHandled(true);
        }
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
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SettingsSyncMessage(cfg.provider, cfg.apiKey(), cfg.model()));
    }

    /** Shared ask logic used by both the command and the GUI path. */
    public static void ask(ServerPlayer player, String message, ReplyCallback callback) {
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

    public interface ReplyCallback {
        void accept(String prefix, String reply);
    }
}
