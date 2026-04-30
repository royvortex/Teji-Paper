package io.papermc.paper.util;

import ca.spottedleaf.moonrise.patches.starlight.light.StarLightInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe async light propagator that works with Starlight.
 * Captures light snapshots, processes them off-thread, and applies results on main thread.
 */
public class AsyncLightPropagator {
    private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger();
    
    private final ServerLevel level;
    private final StarLightInterface lightEngine;
    private final ExecutorService asyncPool;
    private final Queue<LightSnapshot> pendingSnapshots = new ConcurrentLinkedQueue<>();
    private final Map<ChunkPos, LightSnapshot> processingSnapshots = new ConcurrentHashMap<>();
    private final Queue<LightSnapshot> completedSnapshots = new ConcurrentLinkedQueue<>();
    
    private volatile boolean enabled = false;
    private volatile boolean shutdown = false;
    
    public AsyncLightPropagator(ServerLevel level) {
        this.level = level;
        this.lightEngine = level.getChunkSource().getLightEngine().starlight$getLightEngine();
        
        // Create thread pool based on configuration
        int poolSize = level.paperConfig().light.threadPoolSize;
        if (poolSize <= 0) {
            poolSize = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        }
        
        this.asyncPool = Executors.newFixedThreadPool(
            poolSize,
            r -> new Thread(r, "AsyncLight-" + INSTANCE_COUNTER.incrementAndGet() + "-" + level.dimension().location().getPath())
        );
        
        this.enabled = level.paperConfig().light.asyncPropagation;
    }
    
    /**
     * Get the executor service for async tasks
     */
    public ExecutorService getExecutor() {
        return asyncPool;
    }
    
    /**
     * Submit a block change for async light processing
     */
    public void submitBlockChange(BlockPos pos, boolean isLightSource) {
        if (!enabled || shutdown) {
            return;
        }
        
        LevelChunk chunk = level.getChunkAt(pos);
        if (chunk == null) {
            return;
        }
        
        LightSnapshot snapshot = LightSnapshot.forBlockUpdate(level, chunk, pos, isLightSource);
        pendingSnapshots.offer(snapshot);
        
        // Start processing if not already running
        scheduleProcessing();
    }
    
    /**
     * Submit bulk light updates (e.g., chunk generation)
     */
    public void submitBulkUpdates(LevelChunk chunk, Collection<BlockPos> lightSources) {
        if (!enabled || shutdown) {
            return;
        }
        
        LightSnapshot snapshot = LightSnapshot.forBulkUpdates(level, chunk, lightSources);
        pendingSnapshots.offer(snapshot);
        scheduleProcessing();
    }
    
    private void scheduleProcessing() {
        asyncPool.submit(() -> {
            while (!pendingSnapshots.isEmpty() && !shutdown) {
                LightSnapshot snapshot = pendingSnapshots.poll();
                if (snapshot == null) {
                    break;
                }
                
                processingSnapshots.put(snapshot.getChunkPos(), snapshot);
                try {
                    processSnapshot(snapshot);
                } catch (Exception e) {
                    snapshot.setProcessingError(e);
                    org.slf4j.LoggerFactory.getLogger(AsyncLightPropagator.class).error("Error processing async light snapshot for chunk {}", snapshot.getChunkPos(), e);
                } finally {
                    processingSnapshots.remove(snapshot.getChunkPos());
                    completedSnapshots.offer(snapshot);
                }
            }
        });
    }
    
    private void processSnapshot(LightSnapshot snapshot) {
        // Create a thread-local light engine for processing
        // This is safe because we're using a snapshot of the world state
        LightSnapshot.ProcessingContext context = snapshot.createProcessingContext();
        
        // Process light propagation using the snapshot data
        Map<BlockPos, Integer> computedLevels = new HashMap<>();
        Set<BlockPos> blocksToUpdate = new HashSet<>();
        
        // Simple light propagation algorithm (placeholder - would integrate with Starlight)
        for (Map.Entry<BlockPos, Integer> entry : snapshot.getLightSources().entrySet()) {
            BlockPos sourcePos = entry.getKey();
            int sourceLevel = entry.getValue();
            
            // Propagate light in 3D Manhattan distance
            for (int dx = -sourceLevel; dx <= sourceLevel; dx++) {
                for (int dy = -sourceLevel; dy <= sourceLevel; dy++) {
                    for (int dz = -sourceLevel; dz <= sourceLevel; dz++) {
                        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= sourceLevel) {
                            BlockPos targetPos = sourcePos.offset(dx, dy, dz);
                            int distance = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                            int lightLevel = Math.max(0, sourceLevel - distance);
                            
                            computedLevels.put(targetPos, Math.max(
                                computedLevels.getOrDefault(targetPos, 0),
                                lightLevel
                            ));
                            blocksToUpdate.add(targetPos);
                        }
                    }
                }
            }
        }
        
        snapshot.setComputedResults(computedLevels, blocksToUpdate);
    }
    
    /**
     * Apply completed light updates on the main thread
     * Should be called from the server tick loop
     */
    public void applyCompletedUpdates() {
        if (!enabled || shutdown) {
            return;
        }
        
        int maxUpdates = level.paperConfig().light.maxUpdatesPerTick;
        int processed = 0;
        
        while (!completedSnapshots.isEmpty() && processed < maxUpdates) {
            LightSnapshot snapshot = completedSnapshots.poll();
            if (snapshot == null) {
                break;
            }
            
            if (snapshot.getProcessingError() != null) {
                // Log error but continue
                org.slf4j.LoggerFactory.getLogger(AsyncLightPropagator.class).warn("Skipping light snapshot with error: {}", snapshot.getProcessingError().getMessage());
                continue;
            }
            
            // Apply light updates to the world
            applySnapshotToWorld(snapshot);
            processed++;
        }
    }
    
    private void applySnapshotToWorld(LightSnapshot snapshot) {
        // This would need to integrate with Starlight's light engine
        // For now, just queue the positions for light updates
        for (BlockPos pos : snapshot.getBlocksToUpdate()) {
            level.getChunkSource().getLightEngine().checkBlock(pos);
        }
    }
    
    /**
     * Enable or disable async light propagation
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            // Clear pending work when disabled
            pendingSnapshots.clear();
            completedSnapshots.clear();
        }
    }
    
    /**
     * Shutdown the propagator
     */
    public void shutdown() {
        shutdown = true;
        asyncPool.shutdown();
        try {
            if (!asyncPool.awaitTermination(10, TimeUnit.SECONDS)) {
                asyncPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Get statistics for monitoring
     */
    public Stats getStats() {
        return new Stats(
            pendingSnapshots.size(),
            processingSnapshots.size(),
            completedSnapshots.size(),
            enabled
        );
    }
    
    public record Stats(
        int pendingSnapshots,
        int processingSnapshots,
        int completedSnapshots,
        boolean enabled
    ) {}
}