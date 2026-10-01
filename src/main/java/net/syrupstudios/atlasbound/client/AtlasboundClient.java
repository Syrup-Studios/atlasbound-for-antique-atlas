package net.syrupstudios.atlasbound.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.syrupstudios.atlasbound.network.AtlasPackets;
import net.fabricmc.loader.api.FabricLoader;
import folk.sisby.antique_atlas.AntiqueAtlasKeybindings;
import net.fabricmc.loader.api.ModContainer;

public final class AtlasboundClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModContainer antiqueAtlas = FabricLoader.getInstance().getModContainer("antique_atlas")
                .orElseThrow(() -> new IllegalStateException("Atlasbound requires Antique Atlas 4 3.1.2+1.21 on the client"));
        if (!"3.1.2+1.21".equals(antiqueAtlas.getMetadata().getVersion().getFriendlyString()))
            throw new IllegalStateException("Atlasbound requires Antique Atlas 4 3.1.2+1.21 on the client");
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> AtlasClientState.clear(true));
        ClientPlayNetworking.registerGlobalReceiver(AtlasPackets.Selection.TYPE, (packet, context) -> context.client().execute(() -> AtlasClientState.begin(packet)));
        ClientPlayNetworking.registerGlobalReceiver(AtlasPackets.Region.TYPE, (packet, context) -> context.client().execute(() -> AtlasClientState.region(packet)));
        ClientPlayNetworking.registerGlobalReceiver(AtlasPackets.Ready.TYPE, (packet, context) -> context.client().execute(() -> AtlasClientState.ready(packet)));
        ClientPlayNetworking.registerGlobalReceiver(AtlasPackets.Marker.TYPE, (packet, context) -> context.client().execute(() -> AtlasClientState.marker(packet)));
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            // The add-on checks exact default-stack components. Atlas UUID data changes that equality.
            if (client.screen == null && client.player != null && AtlasClientState.hasAtlas(client.player.getInventory()))
                while (AntiqueAtlasKeybindings.ATLAS_KEYMAPPING.consumeClick()) AtlasClientState.requestOpen();
        });
        ClientTickEvents.END_CLIENT_TICK.register(AtlasClientState::tick);
    }
}
