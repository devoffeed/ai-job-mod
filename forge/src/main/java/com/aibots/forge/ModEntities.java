package com.aibots.forge;

import com.aibots.common.entity.AiBotEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, AIBotsForge.MOD_ID);

    public static final RegistryObject<EntityType<AiBotEntity>> AI_BOT =
            ENTITIES.register("ai_bot", () ->
                    EntityType.Builder.of(AiBotEntity::new, MobCategory.CREATURE)
                            .sized(0.6f, 1.95f)
                            .build("ai_bot"));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        modBus.addListener(ModEntities::registerAttributes);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(AI_BOT.get(), AiBotEntity.createAttributes().build());
    }
}
