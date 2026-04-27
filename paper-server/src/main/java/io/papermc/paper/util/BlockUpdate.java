package io.papermc.paper.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Record for block updates from async physics.
 */
public record BlockUpdate(BlockPos pos, BlockState newState) {
}