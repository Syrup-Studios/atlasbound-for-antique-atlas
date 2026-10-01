package net.syrupstudios.atlasbound.mixin.client;

import folk.sisby.antique_atlas.AntiqueAtlas;
import folk.sisby.antique_atlas.gui.AtlasScreen;
import net.syrupstudios.atlasbound.client.AtlasClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AntiqueAtlas.class, remap = false)
public abstract class AtlasOpenMixin {
    // AA4 3.1.2+1.21 opens without item identity; ask the server to select a held atlas first.
    @Inject(method = "openAtlasScreen", at = @At("HEAD"), cancellable = true, remap = false)
    private static void atlasbound$requestServerSelection(CallbackInfoReturnable<AtlasScreen> cir) {
        if (AtlasClientState.allowScreenOpen()) return;
        AtlasClientState.requestOpen();
        cir.setReturnValue(null);
    }
}
