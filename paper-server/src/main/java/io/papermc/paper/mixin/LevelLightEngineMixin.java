package io.papermc.paper.mixin;

import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LevelLightEngine.class)
public abstract class LevelLightEngineMixin {
    
    // Paper start - Starlight integration
    @Unique
    public ca.spottedleaf.moonrise.patches.starlight.light.StarLightInterface starlight$getLightEngine() {
        // This method should return the Starlight interface for async light propagation
        // In the actual implementation, this would be a cast or access to a field
        throw new UnsupportedOperationException("starlight$getLightEngine() not implemented");
    }
    // Paper end - Starlight integration
}
