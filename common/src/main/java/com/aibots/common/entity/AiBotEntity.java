package com.aibots.common.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;

/**
 * A peaceful humanoid NPC the player can talk to.
 * Right-clicking starts a conversation; subsequent /ask commands route to this NPC.
 */
public class AiBotEntity extends PathfinderMob {

    private String personality = "You are a curious traveler who has seen many adventures. Stay in character.";

    public AiBotEntity(EntityType<? extends AiBotEntity> type, Level level) {
        super(type, level);
    }

    public void setPersonality(String personality) {
        if (personality != null && !personality.isBlank()) {
            this.personality = personality;
        }
    }

    public String getPersonality() {
        return personality;
    }

    public String botName() {
        Component name = getCustomName();
        return name != null ? name.getString() : "AI Bot";
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!player.level().isClientSide) {
            // The per-player conversation target is managed by the loader-side handler.
            com.aibots.common.entity.PlayerConversation.setTarget(player, this);
            player.displayClientMessage(Component.literal("Talking to " + botName() + ". Use /ask <message>."), false);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.35D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }
}
