package com.aibots.forge.client;

import com.aibots.common.entity.AiBotEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class AiBotRenderer extends HumanoidMobRenderer<AiBotEntity, HumanoidModel<AiBotEntity>> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(AIBotsForgeClient.MOD_ID, "textures/entity/ai_bot.png");

    public AiBotRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(AiBotEntity entity) {
        return TEXTURE;
    }
}
