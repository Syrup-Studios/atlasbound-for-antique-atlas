package net.syrupstudios.atlasbound;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import folk.sisby.surveyor.WorldSummary;
import net.syrupstudios.atlasbound.network.AtlasNetworking;

public final class Atlasbound {
    public static final String MOD_ID = "atlasbound";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private Atlasbound() {}

    public static void initialize(String loader) {
        WorldSummary.enableTerrain();
        AtlasNetworking.initialize();
        LOGGER.info("Atlasbound initialized for {}", loader);
    }
}
