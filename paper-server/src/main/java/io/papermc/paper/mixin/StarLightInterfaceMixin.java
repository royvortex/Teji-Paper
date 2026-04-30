package io.papermc.paper.mixin;

import ca.spottedleaf.moonrise.patches.starlight.light.StarLightInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

@Mixin(StarLightInterface.class)
public abstract class StarLightInterfaceMixin {
    
    @Unique
    private net.minecraft.world.level.Level world;
    
    // Paper start - async light propagation
    /**
     * Process light propagation asynchronously for the given positions.
     * Returns a future that completes when the light propagation is done.
     */
    @Unique
    public CompletableFuture<Void> asyncPropagateLight(
        Collection<BlockPos> positions,
        boolean isLightSource
    ) {
        if (this.world == null || !(this.world instanceof ServerLevel)) {
            return CompletableFuture.completedFuture(null);
        }
        
        ServerLevel serverLevel = (ServerLevel) this.world;
        io.papermc.paper.util.AsyncLightPropagator propagator = serverLevel.getAsyncLightPropagator();
        
        if (propagator == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.runAsync(() -> {
            // Group positions by chunk
            java.util.Map<net.minecraft.world.level.ChunkPos, java.util.List<BlockPos>> byChunk = new java.util.HashMap<>();
            for (BlockPos pos : positions) {
                net.minecraft.world.level.ChunkPos chunkPos = new net.minecraft.world.level.ChunkPos(pos);
                byChunk.computeIfAbsent(chunkPos, k -> new java.util.ArrayList<>()).add(pos);
            }
            
            // Submit each chunk's positions
            for (java.util.Map.Entry<net.minecraft.world.level.ChunkPos, java.util.List<BlockPos>> entry : byChunk.entrySet()) {
                net.minecraft.world.level.chunk.LevelChunk chunk = serverLevel.getChunk(entry.getKey().x, entry.getKey().z);
                if (chunk != null) {
                    propagator.submitBulkUpdates(chunk, entry.getValue());
                }
            }
        }, propagator.getExecutor());
    }
    
    /**
     * Process a single block change asynchronously
     */
    @Unique
    public void asyncBlockChange(BlockPos pos, boolean isLightSource) {
        if (this.world == null || !(this.world instanceof ServerLevel)) {
            return;
        }
        
        ServerLevel serverLevel = (ServerLevel) this.world;
        io.papermc.paper.util.AsyncLightPropagator propagator = serverLevel.getAsyncLightPropagator();
        
        if (propagator != null) {
            propagator.submitBlockChange(pos, isLightSource);
        }
    }
    // Paper end - async light propagation
    
    @Inject(method = "<init>", at = @At("TAIL"))
    private void paper$initWorldField(
        net.minecraft.world.level.chunk.LightChunkGetter lightAccess,
        boolean hasSkyLight,
        boolean hasBlockLight,
        net.minecraft.world.level.lighting.LevelLightEngine lightEngine,
        CallbackInfo ci
    ) {
        // Store reference to the world for async light propagation
        if (lightAccess instanceof net.minecraft.world.level.Level) {
            this.world = (net.minecraft.world.level.Level) lightAccess;
        }
    }
}
