package io.papermc.paper.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.List;

/**
 * Read-only snapshot of block states for a 3x3 chunk grid (center + neighbors).
 * Used for async physics computations to avoid data races.
 */

/**
 * Read-only snapshot of block states for a 3x3 chunk grid (center + neighbors).
 * Used for async physics computations to avoid data races.
 */
public class PhysicsSnapshot {
    private final BlockState[][][] states; // [x][y][z] relative to chunk origin
    private final int minX, minY, minZ;
    private final int sizeX, sizeY, sizeZ;

    public PhysicsSnapshot(LevelChunk centerChunk, LevelChunk[][] neighbors) {
        // Assume 16x384x16 per chunk, but for physics, focus on relevant heights
        this.sizeX = 16 * 3; // 3 chunks
        this.sizeY = 384; // full height
        this.sizeZ = 16 * 3;
        this.states = new BlockState[sizeX][sizeY][sizeZ];
        this.minX = centerChunk.getPos().getMinBlockX() - 16;
        this.minY = centerChunk.getLevel().getMinY();
        this.minZ = centerChunk.getPos().getMinBlockZ() - 16;

        // Copy block states from chunks (center and neighbors)
        for (int cx = 0; cx < 3; cx++) {
            for (int cz = 0; cz < 3; cz++) {
                LevelChunk chunk = (cx == 1 && cz == 1) ? centerChunk : neighbors[cx][cz];
                if (chunk != null) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int y = minY; y < minY + sizeY; y++) {
                                BlockPos pos = new BlockPos(minX + cx * 16 + x, y, minZ + cz * 16 + z);
                                BlockState state = chunk.getBlockState(pos);
                                states[cx * 16 + x][y - minY][cz * 16 + z] = state;
                            }
                        }
                    }
                }
            }
        }
    }

    public BlockState getBlockState(BlockPos pos) {
        int rx = pos.getX() - minX;
        int ry = pos.getY() - minY;
        int rz = pos.getZ() - minZ;
        if (rx >= 0 && rx < sizeX && ry >= 0 && ry < sizeY && rz >= 0 && rz < sizeZ) {
            return states[rx][ry][rz];
        }
        return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(); // fallback
    }

    // Add methods for neighbor access, etc.
}