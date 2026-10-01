package net.syrupstudios.atlasbound.mixin.client;

import folk.sisby.antique_atlas.WorldAtlasData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.syrupstudios.atlasbound.client.AtlasClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WorldAtlasData.class, remap = false)
public abstract class WorldAtlasDataMixin {
    // The pinned AA4 version has no per-atlas data hook; all reads use our filtered view.
    @Inject(method = "getOrCreate", at = @At("HEAD"), cancellable = true, remap = false)
    private static void atlasbound$selectedView(ResourceKey<Level> dimension, CallbackInfoReturnable<WorldAtlasData> cir) {
        cir.setReturnValue(AtlasClientState.view(dimension));
    }
}
