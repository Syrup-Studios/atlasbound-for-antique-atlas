package net.syrupstudios.atlasbound.mixin.client;

import folk.sisby.antique_atlas.gui.MarkerModal;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import net.syrupstudios.atlasbound.client.AtlasMarkerLandmarks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = MarkerModal.class, remap = false)
public abstract class MarkerModalMixin {
    // AA4 3.1.2+1.21 Done handler; redirect its sole landmarks() call to block shared Surveyor writes.
    @Redirect(
            method = "lambda$init$2",
            at = @At(value = "INVOKE", target = "Lfolk/sisby/surveyor/WorldSummary;landmarks()Lfolk/sisby/surveyor/landmark/WorldLandmarks;"),
            remap = false
    )
    private WorldLandmarks atlasbound$useAtlasMarkers(WorldSummary summary) {
        return new AtlasMarkerLandmarks(summary);
    }
}
