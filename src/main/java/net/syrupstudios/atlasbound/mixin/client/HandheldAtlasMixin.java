package net.syrupstudios.atlasbound.mixin.client;

import folk.sisby.antique_atlas.AntiqueAtlas;
import net.minecraft.world.item.ItemStack;
import net.syrupstudios.atlasbound.Atlasbound;
import net.syrupstudios.atlasbound.AtlasItemData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AntiqueAtlas.class, remap = false)
public abstract class HandheldAtlasMixin {
    @Inject(method = "getHandheldAtlas", at = @At("HEAD"), cancellable = true, remap = false)
    private static void atlasbound$useOwnedAtlasInCreative(CallbackInfoReturnable<ItemStack> cir) {
        cir.setReturnValue(Atlasbound.ATLAS.getDefaultInstance());
    }

    @Inject(method = "isHandheldAtlas", at = @At("HEAD"), cancellable = true, remap = false)
    private static void atlasbound$recognizeAtlasboundItem(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(AtlasItemData.isAtlas(stack));
    }
}
