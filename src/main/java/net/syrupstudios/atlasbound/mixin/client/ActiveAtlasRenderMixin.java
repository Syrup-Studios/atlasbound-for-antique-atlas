package net.syrupstudios.atlasbound.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.syrupstudios.atlasbound.AtlasItemData;
import net.syrupstudios.atlasbound.client.AtlasClientState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemInHandRenderer.class, priority = 2000)
public abstract class ActiveAtlasRenderMixin {
    // Run before AA4's map draw so a second physical atlas cannot show the selected atlas data.
    @Inject(method = "renderMap", at = @At("HEAD"), cancellable = true)
    private void atlasbound$hideInactiveAtlas(PoseStack poseStack, MultiBufferSource buffers, int light, ItemStack stack, CallbackInfo ci) {
        if (AtlasItemData.isAtlas(stack) && !AtlasClientState.isSelectedAtlas(stack)) ci.cancel();
    }
}
