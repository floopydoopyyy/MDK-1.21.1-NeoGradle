package com.oliver.witchmod.blocks;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import com.oliver.witchmod.WitchMod;

/**
 * Purifying Water (CLAUDE.md section 2.5) — a real custom fluid (source + flowing pair), not just a
 * reskinned block, so it flows/fills like water. Reuses vanilla water's still/flowing textures with a
 * distinct tint since no custom art exists yet (see Human Action Items); swap the texture references once
 * real art lands. Client-side rendering (texture/tint) is registered separately in {@code WitchModClient}
 * since it's a {@code Dist.CLIENT}-only concern.
 */
public final class WitchModFluids {
    public static final ResourceLocation WATER_STILL = ResourceLocation.withDefaultNamespace("block/water_still");
    public static final ResourceLocation WATER_FLOW = ResourceLocation.withDefaultNamespace("block/water_flow");
    public static final int TINT_COLOR = 0xFF9B6FD1;

    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, WitchMod.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, WitchMod.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(WitchMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(WitchMod.MODID);

    public static final DeferredHolder<FluidType, FluidType> PURIFYING_WATER_TYPE = FLUID_TYPES.register("purifying_water",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid.witchmod.purifying_water")
                    .canConvertToSource(true)
                    .sound(SoundActions.BUCKET_FILL, net.minecraft.sounds.SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> PURIFYING_WATER =
            FLUIDS.register("purifying_water", () -> new BaseFlowingFluid.Source(fluidProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> PURIFYING_WATER_FLOWING =
            FLUIDS.register("purifying_water_flowing", () -> new BaseFlowingFluid.Flowing(fluidProperties()));

    public static final DeferredBlock<PurifyingWaterBlock> PURIFYING_WATER_BLOCK = BLOCKS.registerBlock("purifying_water",
            props -> new PurifyingWaterBlock(PURIFYING_WATER.get(), props), BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .replaceable()
                    .noCollission()
                    .strength(100.0F)
                    .noLootTable()
                    .liquid()
                    .sound(net.minecraft.world.level.block.SoundType.EMPTY)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));

    public static final DeferredItem<Item> PURIFYING_WATER_BUCKET = ITEMS.register("purifying_water_bucket",
            () -> new BucketItem(PURIFYING_WATER.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    private WitchModFluids() {}

    private static BaseFlowingFluid.Properties fluidProperties() {
        return new BaseFlowingFluid.Properties(PURIFYING_WATER_TYPE, PURIFYING_WATER, PURIFYING_WATER_FLOWING)
                .bucket(PURIFYING_WATER_BUCKET)
                .block(PURIFYING_WATER_BLOCK);
    }

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
