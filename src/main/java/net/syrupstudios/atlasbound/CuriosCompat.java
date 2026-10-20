package net.syrupstudios.atlasbound;

import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Curios API calls are isolated so the API stays optional at runtime. */
public final class CuriosCompat {
    private CuriosCompat() {}

    public static ItemStack getAtlas(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity)
                .flatMap(handler -> handler.findFirstCurio(Atlasbound.ATLAS))
                .map(SlotResult::stack)
                .orElse(ItemStack.EMPTY);
    }
}
