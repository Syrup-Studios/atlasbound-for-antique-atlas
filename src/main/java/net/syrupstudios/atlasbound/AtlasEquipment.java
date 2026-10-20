package net.syrupstudios.atlasbound;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Optional accessory APIs are isolated so their classes stay unloaded when absent. */
public final class AtlasEquipment {
    private AtlasEquipment() {}

    public static ItemStack getAtlas(LivingEntity entity) {
        if (FabricLoader.getInstance().isModLoaded("curios")) {
            ItemStack atlas = CuriosCompat.getAtlas(entity);
            if (AtlasItemData.isAtlas(atlas)) return atlas;
        }
        return FabricLoader.getInstance().isModLoaded("trinkets")
                ? TrinketsCompat.getAtlas(entity) : ItemStack.EMPTY;
    }
}
