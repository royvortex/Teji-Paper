package io.papermc.paper.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LightEngine;

/**
 * Immutable snapshot of chunk data for async packet serialization.
 */
public class ChunkSnapshot {
    private final BlockState[][][] blockStates; // [x][y][z]
    private final short[][][] blockLight; // etc.
    private final short[][][] skyLight;
    // Biomes, etc.

    public ChunkSnapshot(LevelChunk chunk, LightEngine lightEngine) {
        // Copy block states, light, etc. into immutable arrays
        this.blockStates = new BlockState[16][chunk.getHeight() + 1][16];
        // Populate from chunk
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = chunk.getMinY(); y <= chunk.getMaxY(); y++) {
                    BlockPos pos = new BlockPos(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
                    blockStates[x][y - chunk.getMinY()][z] = chunk.getBlockState(pos);
                }
            }
        }
        // Copy light data
        this.blockLight = new short[16][chunk.getSectionsCount()][16];
        this.skyLight = new short[16][chunk.getSectionsCount()][16];
        // Populate from lightEngine
    }

    public BlockState getBlockState(BlockPos pos) {
        int rx = pos.getX() & 15;
        int ry = pos.getY() - chunk.getMinY(); // Adjust
        int rz = pos.getZ() & 15;
        return blockStates[rx][ry][rz];
    }
}