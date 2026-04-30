package io.papermc.paper.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LightEngine;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Immutable snapshot of light data for async light propagation.
 * Captures light sources and affected blocks to be processed off-thread.
 */
public class LightSnapshot {
    private final Map<BlockPos, Integer> lightSources; // position -> light level
    private final Set<BlockPos> affectedBlocks; // blocks that need light updates
    private final ChunkPos chunkPos;
    private final long timestamp;
    private final int dimensionId;
    
    // Results after async processing
    private volatile Map<BlockPos, Integer> computedLightLevels;
    private volatile Set<BlockPos> blocksToUpdate;
    private volatile boolean processed = false;
    private volatile Throwable processingError;
    
    public LightSnapshot(Level level, LevelChunk chunk, Collection<BlockPos> sourcePositions) {
        this.chunkPos = chunk.getPos();
        this.timestamp = level.getGameTime();
        this.dimensionId = level.dimension().hashCode();
        this.lightSources = new HashMap<>();
        this.affectedBlocks = new HashSet<>();
        
        // Capture initial light sources
        net.minecraft.world.level.lighting.LevelLightEngine lightEngine = level.getLightEngine();
        for (BlockPos pos : sourcePositions) {
            if (chunk.getPos().equals(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4))) {
                int lightLevel = lightEngine.getRawBrightness(pos, 0);
                lightSources.put(pos.immutable(), lightLevel);
                
                // Pre-calculate affected area (3D Manhattan distance based on light level)
                int radius = Math.min(lightLevel, 15);
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dy = -radius; dy <= radius; dy++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= radius) {
                                BlockPos affected = pos.offset(dx, dy, dz);
                                if (chunk.getPos().equals(ChunkPos.asLong(affected.getX() >> 4, affected.getZ() >> 4))) {
                                    affectedBlocks.add(affected.immutable());
                                }
                            }
                        }
                    }
                }
            }
        }
        
        this.computedLightLevels = new ConcurrentHashMap<>();
        this.blocksToUpdate = ConcurrentHashMap.newKeySet();
    }
    
    /**
     * Create a light snapshot for a single block update
     */
    public static LightSnapshot forBlockUpdate(Level level, LevelChunk chunk, BlockPos pos, boolean isLightSource) {
        List<BlockPos> sources = new ArrayList<>();
        if (isLightSource) {
            sources.add(pos);
        }
        return new LightSnapshot(level, chunk, sources);
    }
    
    /**
     * Create a light snapshot for bulk updates (e.g., chunk generation)
     */
    public static LightSnapshot forBulkUpdates(Level level, LevelChunk chunk, Collection<BlockPos> lightSources) {
        return new LightSnapshot(level, chunk, lightSources);
    }
    
    public Map<BlockPos, Integer> getLightSources() {
        return Collections.unmodifiableMap(lightSources);
    }
    
    public Set<BlockPos> getAffectedBlocks() {
        return Collections.unmodifiableSet(affectedBlocks);
    }
    
    public ChunkPos getChunkPos() {
        return chunkPos;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public int getDimensionId() {
        return dimensionId;
    }
    
    public Map<BlockPos, Integer> getComputedLightLevels() {
        return computedLightLevels != null ? Collections.unmodifiableMap(computedLightLevels) : Collections.emptyMap();
    }
    
    public Set<BlockPos> getBlocksToUpdate() {
        return blocksToUpdate != null ? Collections.unmodifiableSet(blocksToUpdate) : Collections.emptySet();
    }
    
    public boolean isProcessed() {
        return processed;
    }
    
    public Throwable getProcessingError() {
        return processingError;
    }
    
    // Package-private setters for AsyncLightPropagator
    void setComputedResults(Map<BlockPos, Integer> computedLevels, Set<BlockPos> blocksToUpdate) {
        this.computedLightLevels = new ConcurrentHashMap<>(computedLevels);
        this.blocksToUpdate = ConcurrentHashMap.newKeySet();
        this.blocksToUpdate.addAll(blocksToUpdate);
        this.processed = true;
    }
    
    void setProcessingError(Throwable error) {
        this.processingError = error;
        this.processed = true;
    }
    
    /**
     * Create a processing context for async light propagation
     */
    public ProcessingContext createProcessingContext() {
        return new ProcessingContext(this);
    }
    
    /**
     * Context for processing light snapshots asynchronously
     */
    public static class ProcessingContext {
        private final LightSnapshot snapshot;
        private final Map<BlockPos, Integer> lightLevels = new HashMap<>();
        private final Set<BlockPos> updatedBlocks = new HashSet<>();
        
        private ProcessingContext(LightSnapshot snapshot) {
            this.snapshot = snapshot;
        }
        
        public void setLightLevel(BlockPos pos, int level) {
            lightLevels.put(pos.immutable(), level);
            updatedBlocks.add(pos.immutable());
        }
        
        public Map<BlockPos, Integer> getLightLevels() {
            return Collections.unmodifiableMap(lightLevels);
        }
        
        public Set<BlockPos> getUpdatedBlocks() {
            return Collections.unmodifiableSet(updatedBlocks);
        }
        
        public LightSnapshot getSnapshot() {
            return snapshot;
        }
    }
}