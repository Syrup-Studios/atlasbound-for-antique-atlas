package net.syrupstudios.atlasbound.network;

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import folk.sisby.surveyor.terrain.WorldTerrain;
import folk.sisby.surveyor.util.RegionPos;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.syrupstudios.atlasbound.AtlasData;
import net.syrupstudios.atlasbound.AtlasManager;
import net.syrupstudios.atlasbound.AtlasStorage;
import net.syrupstudios.atlasbound.Atlasbound;

/** Fabric play networking and Surveyor synchronization for atlas views. */
public final class AtlasNetworking {
    private static final Map<UUID, ClientState> CLIENTS = new HashMap<>();
    private static final Map<UUID, Integer> LAST_OPEN_TICK = new HashMap<>();
    private static final Map<UUID, Long> LAST_MARKER_EDIT_NANOS = new HashMap<>();
    private static final Set<String> WARNED_TERRAIN = new HashSet<>();
    private static boolean initialized;

    private AtlasNetworking() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        PayloadTypeRegistry.playC2S().register(AtlasPackets.Open.TYPE, AtlasPackets.Open.CODEC);
        PayloadTypeRegistry.playC2S().register(AtlasPackets.Close.TYPE, AtlasPackets.Close.CODEC);
        PayloadTypeRegistry.playC2S().register(AtlasPackets.MarkerEdit.TYPE, AtlasPackets.MarkerEdit.CODEC);
        PayloadTypeRegistry.playS2C().register(AtlasPackets.Selection.TYPE, AtlasPackets.Selection.CODEC);
        PayloadTypeRegistry.playS2C().register(AtlasPackets.Region.TYPE, AtlasPackets.Region.CODEC);
        PayloadTypeRegistry.playS2C().register(AtlasPackets.Ready.TYPE, AtlasPackets.Ready.CODEC);
        PayloadTypeRegistry.playS2C().register(AtlasPackets.Marker.TYPE, AtlasPackets.Marker.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(AtlasPackets.Open.TYPE,
                (packet, context) -> open(context.player(), packet.slot()));
        ServerPlayNetworking.registerGlobalReceiver(AtlasPackets.Close.TYPE,
                (packet, context) -> AtlasManager.close(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(AtlasPackets.MarkerEdit.TYPE,
                (packet, context) -> editMarker(context.player(), packet));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> disconnect(handler.player));
        AtlasManager.setTransport(new AtlasManager.Transport() {
            @Override
            public void prepareChunk(ServerPlayer player, ChunkPos chunk) {
                ServerLevel level = player.serverLevel();
                LevelChunk loaded = level.getChunkSource().getChunkNow(chunk.x, chunk.z);
                if (loaded != null) WorldTerrain.onChunkLoad(level, loaded);
            }

            @Override
            public void selection(ServerPlayer player, UUID atlasId, AtlasData data, boolean openScreen) {
                sendSelection(player, atlasId, data, openScreen);
            }

            @Override
            public void regionChanged(ServerPlayer player, UUID atlasId, ResourceLocation dimension, long region, BitSet bits) {
                ClientState state = CLIENTS.get(player.getUUID());
                if (state == null || !atlasId.equals(state.atlas) || !canSend(player)) return;
                ServerPlayNetworking.send(player, new AtlasPackets.Region(state.epoch, dimension, region, bits.toLongArray()));
                queueSurveyor(player, dimension, region, bits);
            }
        });
    }

    /** Called for both a keybind request and a vanilla atlas item-use event. */
    public static boolean open(ServerPlayer player, int slot) {
        if (!canSend(player)) return false;
        int tick = player.getServer().getTickCount();
        Integer previous = LAST_OPEN_TICK.get(player.getUUID());
        if (previous != null && tick - previous < 10) return false;
        LAST_OPEN_TICK.put(player.getUUID(), tick);
        return AtlasManager.open(player, slot);
    }

    public static void disconnect(ServerPlayer player) {
        AtlasManager.removePlayer(player);
        CLIENTS.remove(player.getUUID());
        LAST_OPEN_TICK.remove(player.getUUID());
        LAST_MARKER_EDIT_NANOS.remove(player.getUUID());
    }

    public static void serverStopped(MinecraftServer server) {
        CLIENTS.clear();
        LAST_OPEN_TICK.clear();
        LAST_MARKER_EDIT_NANOS.clear();
        WARNED_TERRAIN.clear();
        AtlasManager.serverStopped(server);
    }

    private static void sendSelection(ServerPlayer player, UUID atlas, AtlasData data, boolean openScreen) {
        if (!canSend(player)) return;
        ClientState state = CLIENTS.computeIfAbsent(player.getUUID(), ignored -> new ClientState());
        state.epoch++;
        state.atlas = atlas;
        ServerPlayNetworking.send(player, new AtlasPackets.Selection(state.epoch, atlas, openScreen));
        if (atlas != null && data != null) {
            // Snapshot transfer size grows with the atlas's explored regions.
            data.dimensions().forEach((dimension, regions) -> {
                regions.forEach((region, bits) -> {
                    ServerPlayNetworking.send(player,
                            new AtlasPackets.Region(state.epoch, dimension, region, bits.toLongArray()));
                    queueSurveyor(player, dimension, region, bits);
                });
            });
            if (ServerPlayNetworking.canSend(player, AtlasPackets.Marker.TYPE))
                data.markers().forEach((dimension, markers) -> markers.values().forEach(marker ->
                        ServerPlayNetworking.send(player, new AtlasPackets.Marker(state.epoch, dimension, null, marker))));
        }
        ServerPlayNetworking.send(player, new AtlasPackets.Ready(state.epoch));
    }

    private static void queueSurveyor(ServerPlayer player, ResourceLocation dimension, long region, BitSet explored) {
        try {
            ServerLevel level = player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            if (level == null) return;
            WorldTerrain terrain = WorldTerrain.of(level);
            if (terrain == null) return;
            RegionPos position = RegionPos.of(region);
            BitSet terrainChunks = terrain.getRegion(position).bitSet();
            BitSet visible = (BitSet) explored.clone();
            visible.and(terrainChunks);
            if (!visible.isEmpty()) terrain.queueUpdate(position, visible, player);
        } catch (RuntimeException exception) {
            String key = player.getServer().getWorldData().getLevelName() + ":" + dimension + ":" + region;
            if (WARNED_TERRAIN.add(key))
                Atlasbound.LOGGER.warn("Could not sync Surveyor region {} in {}", region, dimension, exception);
        }
    }

    private static boolean canSend(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, AtlasPackets.Selection.TYPE)
                && ServerPlayNetworking.canSend(player, AtlasPackets.Region.TYPE)
                && ServerPlayNetworking.canSend(player, AtlasPackets.Ready.TYPE);
    }

    private static void editMarker(ServerPlayer player, AtlasPackets.MarkerEdit packet) {
        UUID atlas = AtlasManager.editingAtlas(player);
        ClientState state = CLIENTS.get(player.getUUID());
        long now = System.nanoTime();
        Long previousEdit = LAST_MARKER_EDIT_NANOS.get(player.getUUID());
        if (atlas == null || state == null || !atlas.equals(state.atlas) || packet.epoch() != state.epoch
                || !ServerPlayNetworking.canSend(player, AtlasPackets.Marker.TYPE)
                || previousEdit != null && now - previousEdit < 100_000_000L
                || packet.marker() == null && packet.previous() == null
                || player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, packet.dimension())) == null)
            return;

        AtlasData data;
        try {
            data = AtlasStorage.get(player.getServer(), atlas);
        } catch (IllegalStateException exception) {
            Atlasbound.LOGGER.debug("Rejected marker edit for invalid atlas {}", atlas);
            return;
        }
        LAST_MARKER_EDIT_NANOS.put(player.getUUID(), now);
        if (!data.updateMarker(packet.dimension(), packet.previous(), packet.marker())) {
            Atlasbound.LOGGER.debug("Rejected stale marker edit for atlas {} in {}", atlas, packet.dimension());
            return;
        }

        Atlasbound.LOGGER.debug("Accepted marker edit for atlas {} in {}", atlas, packet.dimension());
        for (ServerPlayer viewer : player.getServer().getPlayerList().getPlayers()) {
            if (!atlas.equals(AtlasManager.selected(viewer))) continue;
            ClientState viewerState = CLIENTS.get(viewer.getUUID());
            if (viewerState != null && atlas.equals(viewerState.atlas) && canSend(viewer)
                    && ServerPlayNetworking.canSend(viewer, AtlasPackets.Marker.TYPE))
                ServerPlayNetworking.send(viewer, new AtlasPackets.Marker(viewerState.epoch,
                        packet.dimension(), packet.previous(), packet.marker()));
        }
    }

    private static final class ClientState {
        private long epoch;
        private UUID atlas;
    }
}
