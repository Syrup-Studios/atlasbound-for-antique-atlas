package net.syrupstudios.atlasbound.client;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import folk.sisby.antique_atlas.AntiqueAtlas;
import folk.sisby.antique_atlas.WorldAtlasData;
import folk.sisby.antique_atlas.gui.AtlasScreen;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.client.SurveyorClient;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.component.LandmarkComponentTypes;
import folk.sisby.surveyor.util.RegionPos;
import net.syrupstudios.atlasbound.Atlasbound;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.syrupstudios.atlasbound.AtlasItemData;
import net.syrupstudios.atlasbound.AtlasMarker;
import net.syrupstudios.atlasbound.network.AtlasPackets;
import net.rasanovum.rosetta.network.RosettaNetwork;

public final class AtlasClientState {
    private static long epoch = -1;
    private static UUID atlas;
    private static boolean pendingOpen;
    private static int pendingSince;
    private static boolean allowScreenOpen;
    private static boolean screenOpen;
    private static boolean refreshOnReady;
    private static boolean readyForCache;
    private static final Map<ResourceLocation, Map<Long, BitSet>> regions = new HashMap<>();
    private static final Map<ResourceLocation, Map<ResourceLocation, AtlasMarker>> markers = new HashMap<>();
    private static final Map<ResourceLocation, AtlasMapView> views = new HashMap<>();

    private AtlasClientState() {}

    public static void begin(AtlasPackets.Selection packet) {
        if (packet.epoch() < epoch) return;
        if (packet.epoch() == epoch) {
            if (!java.util.Objects.equals(packet.atlas(), atlas)) return;
            pendingOpen = packet.openScreen() && atlas != null;
            pendingSince = Minecraft.getInstance().player == null ? 0 : Minecraft.getInstance().player.tickCount;
            return;
        }
        closeScreen();
        clearViews();
        readyForCache = false;
        epoch = packet.epoch();
        atlas = packet.atlas();
        refreshOnReady = true;
        pendingOpen = packet.openScreen() && atlas != null;
        pendingSince = Minecraft.getInstance().player == null ? 0 : Minecraft.getInstance().player.tickCount;
        regions.clear();
        markers.clear();
        Atlasbound.LOGGER.debug("Received atlas selection {} at epoch {} (open={})", atlas, epoch, packet.openScreen());
    }

    public static void region(AtlasPackets.Region packet) {
        if (atlas == null || packet.epoch() != epoch) return;
        BitSet bits = BitSet.valueOf(packet.bits());
        if (bits.length() > 1024) return;
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, packet.dimension());
        AtlasMapView view = view(dimension);
        BitSet atlasBits = regions.computeIfAbsent(packet.dimension(), ignored -> new HashMap<>())
                .computeIfAbsent(packet.region(), ignored -> new BitSet(1024));
        BitSet newlyAllowed = (BitSet) bits.clone();
        newlyAllowed.andNot(atlasBits);
        atlasBits.or(bits);
        WorldSummary summary = SurveyorClient.tryGetSummary(dimension);
        if (summary != null) ensureCache(view, dimension.location());
        if (summary == null) return;
        if (readyForCache && !newlyAllowed.isEmpty()) refreshStructures(view, summary, packet.region());
        if (summary.terrain() == null) return;
        RegionPos region = RegionPos.of(packet.region());
        BitSet visible = summary.terrain().getRegion(region).bitSet();
        visible.and(atlasBits);
        if (!visible.isEmpty()) view.onTerrainUpdated(summary, Map.of(region, visible));
    }

    public static void ready(AtlasPackets.Ready packet) {
        if (packet.epoch() != epoch) return;
        readyForCache = true;
        if (refreshOnReady) {
            refreshViews();
            refreshOnReady = false;
        }
        restoreCachedViews();
        if (pendingOpen) {
            pendingOpen = false;
            allowScreenOpen = true;
            try {
                screenOpen = AntiqueAtlas.openAtlasScreen() != null;
                Atlasbound.LOGGER.debug("Opened atlas screen for {} at epoch {}", atlas, epoch);
            } finally {
                allowScreenOpen = false;
            }
        }
    }

    public static void marker(AtlasPackets.Marker packet) {
        if (atlas == null || packet.epoch() != epoch) return;
        Map<ResourceLocation, AtlasMarker> dimensionMarkers = markers.computeIfAbsent(packet.dimension(), ignored -> new HashMap<>());
        if (packet.removed() != null) dimensionMarkers.remove(packet.removed());
        if (packet.marker() != null) dimensionMarkers.put(packet.marker().id(), packet.marker());
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, packet.dimension());
        view(dimension).setMarkers(dimensionMarkers);
        if (Minecraft.getInstance().screen instanceof AtlasScreen screen) screen.updateBookmarkerList();
    }

    static boolean editMarker(ResourceLocation dimension, ResourceLocation previous, AtlasMarker marker) {
        Minecraft client = Minecraft.getInstance();
        if (atlas == null || client.getConnection() == null) return false;
        if (!ClientPlayNetworking.canSend(AtlasPackets.MarkerEdit.TYPE)) {
            if (client.player != null)
                client.player.displayClientMessage(Component.translatable("message.atlasbound.server_update_needed"), false);
            return false;
        }
        RosettaNetwork.sendToServer(new AtlasPackets.MarkerEdit(epoch, dimension, previous, marker));
        return true;
    }

    static AtlasMarker marker(ResourceLocation dimension, ResourceLocation id) {
        return markers.getOrDefault(dimension, Map.of()).get(id);
    }

    static boolean hasMarker(ResourceLocation dimension, ResourceLocation id) {
        return markers.getOrDefault(dimension, Map.of()).containsKey(id);
    }

    static UUID markerOwner() { return SurveyorClient.getClientUuid(); }

    static Landmark landmark(AtlasMarker marker) {
        return Landmark.create(markerOwner(), marker.id(), builder -> builder
                .add(LandmarkComponentTypes.POS, marker.pos())
                .add(LandmarkComponentTypes.NAME, Component.literal(marker.name()))
                .add(LandmarkComponentTypes.COLOR, marker.color() | 0xFF000000));
    }

    static void submitMarker(WorldSummary summary, ResourceLocation previous, Landmark landmark) {
        if (summary == null || landmark == null || atlas == null) return;
        var pos = landmark.get(LandmarkComponentTypes.POS);
        if (pos == null) return;
        String name = landmark.getOrDefault(LandmarkComponentTypes.NAME, Component.empty()).getString();
        if (name.length() > AtlasMarker.MAX_NAME_LENGTH) name = name.substring(0, AtlasMarker.MAX_NAME_LENGTH);
        int color = landmark.getOrDefault(LandmarkComponentTypes.COLOR, 0xFFFFFF) & 0xFFFFFF;
        try {
            editMarker(summary.dimension().location(), previous,
                    new AtlasMarker(landmark.id(), pos, name, color));
        } catch (IllegalArgumentException ignored) {
            // Ignore marker data that does not fit Atlasbound's saved format.
        }
    }

    public static AtlasMapView view(ResourceKey<Level> dimension) {
        AtlasMapView view = views.computeIfAbsent(dimension.location(), key -> new AtlasMapView(key, atlas));
        // AA4's dimension selector reads this map through WorldAtlasData.isEmpty().
        WorldAtlasData.WORLDS.put(dimension, view);
        return view;
    }

    static BitSet allowed(ResourceLocation dimension, long region) {
        BitSet bits = regions.getOrDefault(dimension, Map.of()).get(region);
        return bits == null ? new BitSet() : (BitSet) bits.clone();
    }

    public static boolean allows(ResourceLocation dimension, ChunkPos pos) {
        if (atlas == null) return false;
        long key = ChunkPos.asLong(pos.x >> 5, pos.z >> 5);
        BitSet bits = regions.getOrDefault(dimension, Map.of()).get(key);
        return bits != null && bits.get(RegionPos.chunkToBit(pos));
    }

    static boolean owns(UUID owner) { return owner != null && owner.equals(atlas); }

    public static boolean isSelectedAtlas(ItemStack stack) {
        return atlas != null && AtlasItemData.id(stack).filter(atlas::equals).isPresent();
    }

    public static boolean allowScreenOpen() {
        return allowScreenOpen;
    }

    public static void requestOpen() {
        Minecraft client = Minecraft.getInstance();
        if (pendingOpen || client.player == null || client.getConnection() == null) return;
        int slot = findAtlasSlot(client.player.getInventory());
        if (slot < 0) return;
        if (!ClientPlayNetworking.canSend(AtlasPackets.Open.TYPE)) {
            Atlasbound.LOGGER.warn("Cannot open atlas: the server does not support Atlasbound networking");
            client.player.displayClientMessage(Component.translatable("message.atlasbound.server_incompatible"), false);
            return;
        }
        pendingOpen = true;
        pendingSince = client.player.tickCount;
        RosettaNetwork.sendToServer(new AtlasPackets.Open(slot));
    }

    public static boolean hasAtlas(Inventory inventory) { return findAtlasSlot(inventory) >= 0; }

    private static int findAtlasSlot(Inventory inventory) {
        int selected = inventory.selected;
        if (AtlasItemData.isAtlas(inventory.getSelected())) return selected;
        if (AtlasItemData.isAtlas(inventory.offhand.get(0))) return 40;
        for (int i = 0; i < inventory.items.size(); i++)
            if (AtlasItemData.isAtlas(inventory.items.get(i))) return i;
        return -1;
    }

    public static void tick(Minecraft client) {
        if (atlas != null && client.getConnection() != null) {
            for (WorldSummary summary : SurveyorClient.getSummaries(client.getConnection()).values()) {
                AtlasMapView view = views.get(summary.dimension().location());
                if (view != null) ensureCache(view, summary.dimension().location());
            }
        }
        // AA4 builds tiles on world ticks, which stop while its screen pauses the world.
        if (client.isPaused() && client.screen instanceof AtlasScreen && client.getConnection() != null) {
            for (WorldSummary summary : SurveyorClient.getSummaries(client.getConnection()).values()) {
                AtlasMapView view = views.get(summary.dimension().location());
                if (view != null) view.tick(summary);
            }
        }
        if (pendingOpen && client.player != null && client.player.tickCount - pendingSince > 100) {
            pendingOpen = false;
            Atlasbound.LOGGER.debug("Atlas open request timed out at epoch {}", epoch);
            client.player.displayClientMessage(Component.translatable("message.atlasbound.open_rejected"), false);
        }
        if (screenOpen && !(client.screen instanceof AtlasScreen)) {
            screenOpen = false;
            views.values().forEach(AtlasTileCache::save);
            if (client.getConnection() != null) RosettaNetwork.sendToServer(new AtlasPackets.Close());
        }
    }

    public static void clear(boolean close) {
        if (close) closeScreen();
        clearViews();
        epoch = -1;
        atlas = null;
        pendingOpen = false;
        screenOpen = false;
        refreshOnReady = false;
        readyForCache = false;
        regions.clear();
        markers.clear();
    }

    private static void clearViews() {
        views.values().forEach(AtlasTileCache::save);
        WorldAtlasData.WORLDS.entrySet().removeIf(entry -> entry.getValue() instanceof AtlasMapView);
        views.clear();
    }

    private static void ensureCache(AtlasMapView view, ResourceLocation dimension) {
        if (view.cachePath != null || view.cacheLoaded || view.ownerId() == null) return;
        AtlasTileCache.load(view, AtlasTileCache.path(Minecraft.getInstance(), dimension, view.ownerId()));
    }

    static boolean isCurrent(AtlasMapView view) {
        return views.get(view.dimensionId()) == view;
    }

    static void restoreCachedViews() {
        if (!readyForCache) return;
        for (AtlasMapView view : views.values()) {
            restoreCached(view);
        }
    }

    static void restoreCached(AtlasMapView view) {
        if (!readyForCache || !view.cacheLoaded) return;
        view.cacheEntries.forEach((pos, entry) -> {
            if (allows(view.dimensionId(), pos)) view.restore(pos, entry);
        });
        view.cacheEntries = Map.of();
        view.removeQueuedTiles();
    }

    static void resourceReloaded() {
        clearViews();
        refreshViews();
        restoreCachedViews();
    }

    public static void stopping() {
        views.values().forEach(AtlasTileCache::save);
        AtlasTileCache.flush();
    }

    private static void closeScreen() {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof AtlasScreen) client.setScreen(null);
        screenOpen = false;
    }

    private static void refreshViews() {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() == null) return;
        for (WorldSummary summary : SurveyorClient.getSummaries(client.getConnection()).values()) {
            ResourceLocation dimension = summary.dimension().location();
            Map<RegionPos, BitSet> visible = new HashMap<>();
            if (summary.terrain() != null) {
                Map<RegionPos, BitSet> surveyed = summary.terrain().bitSet(null);
                regions.getOrDefault(dimension, Map.of()).forEach((region, bits) -> {
                    RegionPos pos = RegionPos.of(region);
                    BitSet known = surveyed.get(pos);
                    if (known != null) {
                        BitSet allowed = (BitSet) bits.clone();
                        allowed.and(known);
                        if (!allowed.isEmpty()) visible.put(pos, allowed);
                    }
                });
            }
            AtlasMapView view = view(summary.dimension());
            ensureCache(view, dimension);
            view.onTerrainUpdated(summary, visible);
            refreshStructures(view, summary);
            view.setMarkers(markers.getOrDefault(dimension, Map.of()));
        }
    }

    private static void refreshStructures(AtlasMapView view, WorldSummary summary) {
        refreshStructures(view, summary, null);
    }

    private static void refreshStructures(AtlasMapView view, WorldSummary summary, Long region) {
        if (summary.structures() == null) return;
        var starts = summary.structures().keySet(null);
        if (region == null) {
            view.onStructuresAdded(summary, starts);
            return;
        }
        Multimap<ResourceKey<Structure>, ChunkPos> changed = HashMultimap.create();
        starts.forEach((key, pos) -> {
            if (ChunkPos.asLong(pos.x >> 5, pos.z >> 5) == region) changed.put(key, pos);
        });
        if (!changed.isEmpty()) view.onStructuresAdded(summary, changed);
    }
}
