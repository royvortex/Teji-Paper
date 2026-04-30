package io.papermc.paper.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.ThreadPoolExecutor;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    
    // Paper start - async light engine thread pool
    @Unique
    private ThreadPoolExecutor asyncLightPool;
    
    @Unique
    private io.papermc.paper.util.AsyncLightPropagator asyncLightPropagator;
    // Paper end - async light engine thread pool
    
    @Inject(method = "<init>", at = @At("TAIL"))
    private void paper$initAsyncLightPool(CallbackInfo ci) {
        ServerLevel self = (ServerLevel)(Object)this;
        
        // Paper start - async light engine thread pool initialization
        int asyncLightThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        this.asyncLightPool = new java.util.concurrent.ThreadPoolExecutor(
            asyncLightThreads,
            asyncLightThreads,
            60L,
            java.util.concurrent.TimeUnit.SECONDS,
            new java.util.concurrent.LinkedBlockingQueue<>(),
            r -> {
                Thread t = new Thread(r, "Async Light Engine Pool");
                t.setDaemon(true);
                return t;
            }
        );
        
        this.asyncLightPropagator = new io.papermc.paper.util.AsyncLightPropagator(self);
        // Paper end - async light engine thread pool initialization
    }
    
    // Paper start - async light engine getter
    @Unique
    public io.papermc.paper.util.AsyncLightPropagator getAsyncLightPropagator() {
        return this.asyncLightPropagator;
    }
    // Paper end - async light engine getter
    
    // Paper start - shutdown async light pool
    @Unique
    public void shutdownAsyncLightPool() {
        if (this.asyncLightPropagator != null) {
            this.asyncLightPropagator.shutdown();
        }
    }
    // Paper end - shutdown async light pool
    
    // Paper start - hook block changes for async light updates
    @Inject(method = "setBlockAndUpdate", at = @At("RETURN"))
    private void paper$onSetBlockAndUpdate(BlockPos pos, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            ServerLevel self = (ServerLevel)(Object)this;
            
            // Check if async light propagation is enabled
            if (self.paperConfig().light.asyncPropagation) {
                // Determine if this block change affects lighting
                boolean isLightSource = state.getLightEmission() > 0 ||
                                      state.getLightBlock() < 15;
                
                // Submit for async light processing
                this.asyncLightPropagator.submitBlockChange(pos, isLightSource);
            }
        }
    }
    // Paper end - hook block changes for async light updates
    
    // Paper start - apply async light updates during tick
    @Unique
    public void applyAsyncLightUpdates() {
        ServerLevel self = (ServerLevel)(Object)this;
        if (self.paperConfig().light.asyncPropagation && this.asyncLightPropagator != null) {
            this.asyncLightPropagator.applyCompletedUpdates();
        }
    }
    // Paper end - apply async light updates during tick
}
