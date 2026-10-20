package net.syrupstudios.atlasbound;

import java.util.Map;

import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Trinkets API calls are isolated here so the API stays optional at runtime. */
public final class TrinketsCompat {
    private TrinketsCompat() {}

    public static ItemStack getAtlas(LivingEntity entity) {
        return TrinketsApi.getTrinketComponent(entity)
                .map(component -> component.getInventory().getOrDefault("chest", Map.of()).get("atlas"))
                .filter(inventory -> inventory != null && inventory.getContainerSize() > 0)
                .map(inventory -> inventory.getItem(0))
                .orElse(ItemStack.EMPTY);
    }
}
