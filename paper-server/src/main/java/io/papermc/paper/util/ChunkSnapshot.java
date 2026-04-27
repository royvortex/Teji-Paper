package io.papermc.paper.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LightEngine;
import java.util.Map;

/**
 * Immutable snapshot of chunk data for async packet serialization.
 */
public class ChunkSnapshot {
    private final BlockState[][][] blockStates;
    private final short[][][] blockLight;
    private final short[][][] skyLight;
    private final Map<net.minecraft.world.level.levelgen.Heightmap.Types, long[]> heightmaps;
    private final int minX, minY, minZ;

    public ChunkSnapshot(LevelChunk chunk, LightEngine lightEngine) {
        this.minX = chunk.getPos().getMinBlockX();
        this.minZ = chunk.getPos().getMinBlockZ();
        this.minY = chunk.getLevel().getMinY();
        int height = chunk.getHeight();
        this.blockStates = new BlockState[16][height][16];
        this.blockLight = new short[16][chunk.getSectionsCount()][16];
        this.skyLight = new short[16][chunk.getSectionsCount()][16];
        // TODO: Fix heightmaps initialization
        this.heightmaps = new java.util.HashMap<>();

        // Copy block states
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < minY + height; y++) {
                    BlockPos pos = new BlockPos(minX + x, y, minZ + z);
                    blockStates[x][y - minY][z] = chunk.getBlockState(pos);
                }
            }
        }

        // Copy light (simplified)
        // TODO: actually copy from lightEngine
    }

    public BlockState getBlockState(BlockPos pos) {
        int rx = pos.getX() - minX;
        int rz = pos.getZ() - minZ;
        int ry = pos.getY() - minY;
        if (rx >= 0 && rx < 16 && ry >= 0 && ry < blockStates[0].length && rz >= 0 && rz < 16) {
            return blockStates[rx][ry][rz];
        }
        return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    }

    public Map<net.minecraft.world.level.levelgen.Heightmap.Types, long[]> getHeightmaps() {
        return heightmaps;
    }

    // Add methods for light, etc.
}