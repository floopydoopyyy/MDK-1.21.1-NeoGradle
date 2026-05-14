package com.loopy.loopypowers.entity;

import com.loopy.loopypowers.Loopypowers;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Loopypowers.MOD_ID);

    // FIREBALL
    public static final DeferredHolder<EntityType<?>, EntityType<PowerFireballEntity>> POWER_FIREBALL =
            ENTITY_TYPES.register("power_fireball", () -> EntityType.Builder.<PowerFireballEntity>of(PowerFireballEntity::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("power_fireball")
            );

    // BLOOD CLOT
    public static final DeferredHolder<EntityType<?>, EntityType<BloodClotEntity>> BLOOD_CLOT =
            ENTITY_TYPES.register("blood_clot", () -> EntityType.Builder.<BloodClotEntity>of(BloodClotEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("blood_clot")
            );

    // SONIC BOLT
    public static final DeferredHolder<EntityType<?>, EntityType<SonicBoltEntity>> SONIC_BOLT =
            ENTITY_TYPES.register("sonic_bolt", () -> EntityType.Builder.<SonicBoltEntity>of(SonicBoltEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("sonic_bolt")
            );

    // SHADOW STEP
    public static final DeferredHolder<EntityType<?>, EntityType<ShadowStepEntity>> SHADOW_STEP =
            ENTITY_TYPES.register("shadow_step", () -> EntityType.Builder.<ShadowStepEntity>of(ShadowStepEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("shadow_step")
            );

    // COMPEL PROJECTILE
    public static final DeferredHolder<EntityType<?>, EntityType<CompelEntity>> COMPEL_ENTITY =
            ENTITY_TYPES.register("compel_entity", () -> EntityType.Builder.<CompelEntity>of(CompelEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(4)
                    .updateInterval(1)
                    .build("compel_entity")
            );

    // PUPPETRY PROJECTILE
    public static final DeferredHolder<EntityType<?>, EntityType<PuppetryEntity>> PUPPETRY_ENTITY =
            ENTITY_TYPES.register("puppetry_entity", () -> EntityType.Builder.<PuppetryEntity>of(PuppetryEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(4)
                    .updateInterval(1)
                    .build("puppetry_entity")
            );

    // COSMIC BLACK HOLE
    public static final DeferredHolder<EntityType<?>, EntityType<BlackHoleEntity>> BLACK_HOLE_ENTITY =
            ENTITY_TYPES.register("black_hole", () -> EntityType.Builder.<BlackHoleEntity>of(BlackHoleEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .build("black_hole")
            );

    // DIMENSIONAL DISPLACEMENT
    public static final DeferredHolder<EntityType<?>, EntityType<DisplaceEntity>> DISPLACE_ENTITY =
            ENTITY_TYPES.register("displace_entity", () -> EntityType.Builder.<DisplaceEntity>of(DisplaceEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .build("displace_entity")
            );

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}