package com.oliver.witchmod.blocks;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import com.oliver.witchmod.ui.BewitchingTableMenu;

/**
 * The ritual block (CLAUDE.md section 2.1). Right-clicking opens the real Bewitching Table screen (Phase
 * 5, {@link BewitchingTableMenu}/{@code BewitchingTableScreen}), which replaces the Phase 4 placeholder's
 * direct item-insertion/cast-on-click interaction. {@code useItemOn} isn't overridden — vanilla's default
 * ({@code PASS_TO_DEFAULT_BLOCK_INTERACTION}) already falls through to {@code useWithoutItem} below
 * regardless of what's in the player's hand, so a single method covers both cases.
 */
public final class BewitchingTableBlock extends BaseEntityBlock {
    public static final MapCodec<BewitchingTableBlock> CODEC = simpleCodec(BewitchingTableBlock::new);

    public BewitchingTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BewitchingTableBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(level.getBlockEntity(pos) instanceof BewitchingTableBlockEntity table)) {
            return InteractionResult.SUCCESS;
        }

        MenuProvider menuProvider = new SimpleMenuProvider(
                (windowId, inventory, p) -> new BewitchingTableMenu(windowId, inventory, table),
                Component.translatable("block.witchmod.bewitching_table"));
        serverPlayer.openMenu(menuProvider, pos);
        return InteractionResult.SUCCESS;
    }
}
