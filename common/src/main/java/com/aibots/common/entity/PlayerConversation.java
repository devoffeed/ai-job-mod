package com.aibots.common.entity;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Map;
import java.util.UUID;

/**
 * Tracks which NPC each player is currently talking to (server side).
 * Purely vanilla data holders so it compiles in both loaders.
 */
public final class PlayerConversation {

    private static final Map<UUID, UUID> TARGETS = new Object2ObjectOpenHashMap<>();

    private PlayerConversation() {
    }

    public static void setTarget(net.minecraft.world.entity.player.Player player, AiBotEntity bot) {
        if (bot == null) {
            TARGETS.remove(player.getUUID());
        } else {
            TARGETS.put(player.getUUID(), bot.getUUID());
        }
    }

    public static void clear(UUID playerId) {
        TARGETS.remove(playerId);
    }

    /**
     * Finds the NPC the given player is currently talking to, by scanning the player's level.
     */
    public static AiBotEntity getTarget(net.minecraft.server.level.ServerPlayer player) {
        UUID botId = TARGETS.get(player.getUUID());
        if (botId == null) {
            return null;
        }
        for (var e : player.serverLevel().getAllEntities()) {
            if (e instanceof AiBotEntity bot && bot.getUUID().equals(botId)) {
                return bot;
            }
        }
        TARGETS.remove(player.getUUID());
        return null;
    }
}
