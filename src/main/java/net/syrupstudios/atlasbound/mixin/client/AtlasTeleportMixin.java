package net.syrupstudios.atlasbound.mixin.client;

import folk.sisby.antique_atlas.gui.AtlasScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.syrupstudios.atlasbound.client.AtlasClientState;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AtlasScreen.class, remap = false)
public abstract class AtlasTeleportMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = true)
    private void atlasbound$teleport(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        AtlasScreen screen = (AtlasScreen) (Object) this;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !controlDown() || screen.markerModal.getParent() != null) return;
        screen.updateMouse(mouseX, mouseY);
        if (!screen.isMouseOverMap) return;
        AtlasClientState.teleport(screen.dim().location(), screen.screenXToWorldX(mouseX), screen.screenYToWorldZ(mouseY));
        cir.setReturnValue(true);
    }

    private static boolean controlDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }
}
