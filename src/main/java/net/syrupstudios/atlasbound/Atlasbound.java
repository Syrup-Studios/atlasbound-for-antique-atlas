package net.syrupstudios.atlasbound;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import folk.sisby.surveyor.WorldSummary;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.syrupstudios.atlasbound.network.AtlasNetworking;

public final class Atlasbound {
    public static final String MOD_ID = "atlasbound";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private Atlasbound() {}

    public static final Item ATLAS = Registry.register(BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "atlas"), new Item(new Item.Properties().stacksTo(1)));

    public static void initialize(String loader) {
        WorldSummary.enableTerrain();
        AtlasNetworking.initialize();
        LOGGER.info("Atlasbound initialized for {}", loader);
    }
}
