package net.syrupstudios.atlasbound.loaders.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.syrupstudios.atlasbound.AtlasManager;
import net.syrupstudios.atlasbound.Atlasbound;
import net.syrupstudios.atlasbound.network.AtlasNetworking;

public final class FabricEntrypoint implements ModInitializer {
    @Override
    public void onInitialize() {
        Atlasbound.initialize("Fabric");
        ServerTickEvents.END_SERVER_TICK.register(AtlasManager::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(AtlasNetworking::serverStopped);
    }
}
