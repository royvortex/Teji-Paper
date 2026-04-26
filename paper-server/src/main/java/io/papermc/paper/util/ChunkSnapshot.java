package io.papermc.paper.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Read-only snapshot of chunk data for async packet serialization.
 */
public class ChunkSnapshot {
    private final LevelChunk chunk;

    public ChunkSnapshot(LevelChunk chunk) {
        this.chunk = chunk;
    }

    public BlockState getBlockState(BlockPos pos) {
        return chunk.getBlockState(pos);
    }

    // Add methods for light, biomes, etc.
}