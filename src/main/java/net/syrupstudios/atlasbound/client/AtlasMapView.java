package net.syrupstudios.atlasbound.client;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.nio.file.Path;

import com.google.common.collect.Multimap;
import folk.sisby.antique_atlas.MarkerTexture;
import folk.sisby.antique_atlas.TileTexture;
import folk.sisby.antique_atlas.TerrainTileProvider;
import folk.sisby.antique_atlas.TileElevation;
import folk.sisby.antique_atlas.reloader.BiomeTileProviders;
import folk.sisby.antique_atlas.WorldAtlasData;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.util.RegionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.syrupstudios.atlasbound.AtlasMarker;

public final class AtlasMapView extends WorldAtlasData {
    private final ResourceLocation dimension;
    private final UUID owner;
    Path cachePath;
    String cacheFingerprint;
    Map<ChunkPos, AtlasTileCache.Entry> cacheEntries = Map.of();
    boolean cacheLoaded;
    boolean cacheDirty;

    AtlasMapView(ResourceLocation dimension, UUID owner) {
        this.dimension = dimension;
        this.owner = owner;
    }

    ResourceLocation dimensionId() { return dimension; }
    UUID ownerId() { return owner; }
    void removeQueuedTiles() {
        terrainDeque.removeIf(biomeTiles::containsKey);
        terrainDequeHash.removeAll(biomeTiles.keySet());
    }

    @Override
    public void onTerrainUpdated(WorldSummary summary, Map<RegionPos, BitSet> chunks) {
        if (!AtlasClientState.owns(owner)) return;
        Map<RegionPos, BitSet> visible = new HashMap<>();
        chunks.forEach((region, incoming) -> {
            BitSet allowed = AtlasClientState.allowed(dimension, region.toLong());
            allowed.and(incoming);
            if (!allowed.isEmpty()) visible.put(region, allowed);
        });
        if (!visible.isEmpty()) super.onTerrainUpdated(summary, visible);
    }

    @Override
    public void tick(WorldSummary summary) {
        int size = biomeTiles.size();
        super.tick(summary);
        if (biomeTiles.size() != size) cacheDirty = true;
    }

    void restore(ChunkPos pos, AtlasTileCache.Entry entry) {
        if (biomeTiles.containsKey(pos)) return;
        try {
            TerrainTileProvider provider = BiomeTileProviders.getInstance().getTileProvider(entry.provider());
            if (!provider.id().equals(entry.provider())) return;
            TileElevation elevation = null;
            if (entry.elevation() != null) {
                for (TileElevation candidate : TileElevation.values())
                    if (candidate.getName().equals(entry.elevation())) elevation = candidate;
                if (elevation == null) return;
            }
            biomeTiles.put(pos, provider.getTexture(pos, elevation));
            debugBiomes.put(pos, provider);
            debugBiomePredicates.put(pos, elevation == null ? null : elevation.getName());
            tileScope.extendTo(pos.x, pos.z);
        } catch (RuntimeException ignored) {
            // Invalid or no longer available cache entries are rebuilt from Surveyor terrain.
        }
    }

    Map<ChunkPos, AtlasTileCache.Entry> cacheSnapshot() {
        Map<ChunkPos, AtlasTileCache.Entry> snapshot = new HashMap<>();
        debugBiomes.forEach((pos, provider) -> {
            if (biomeTiles.containsKey(pos) && provider.id().toString().length() <= 256)
                snapshot.put(pos, new AtlasTileCache.Entry(provider.id(), debugBiomePredicates.get(pos)));
        });
        return snapshot;
    }

    @Override
    public void onStructuresAdded(WorldSummary summary, Multimap<ResourceKey<Structure>, ChunkPos> starts) {}

    @Override public void onLandmarksAdded(WorldSummary summary, Multimap<java.util.UUID, ResourceLocation> landmarks) {}
    @Override public void onLandmarksRemoved(WorldSummary summary, Multimap<java.util.UUID, ResourceLocation> landmarks) {}
    @Override public void addLandmark(Landmark landmark) {
        if (AtlasClientState.owns(owner)) super.addLandmark(landmark);
    }

    void setMarkers(Map<ResourceLocation, AtlasMarker> markers) {
        landmarkMarkers.clear();
        if (!AtlasClientState.owns(owner)) return;
        markers.values().forEach(marker -> super.addLandmark(AtlasClientState.landmark(marker)));
    }

    @Override public boolean deleteLandmark(ResourceKey<Level> dimension, Landmark landmark) {
        if (!AtlasClientState.owns(owner) || !this.dimension.equals(dimension.location())
                || landmark == null || !landmark.owner().equals(AtlasClientState.markerOwner())) return false;
        AtlasMarker marker = AtlasClientState.marker(this.dimension, landmark.id());
        if (marker == null) return false;
        return AtlasClientState.editMarker(this.dimension, marker.id(), null);
    }

    @Override public Map<Landmark, MarkerTexture> getEditableLandmarks() {
        return AtlasClientState.owns(owner) ? super.getEditableLandmarks() : Map.of();
    }

    @Override public Map<Landmark, MarkerTexture> getAllMarkers(int tileChunks) {
        return AtlasClientState.owns(owner) ? super.getAllMarkers(tileChunks) : Map.of();
    }

    @Override
    public TileTexture getTile(ChunkPos pos) {
        return AtlasClientState.owns(owner) && AtlasClientState.allows(dimension, pos) ? super.getTile(pos) : null;
    }

    @Override public ResourceLocation getProvider(ChunkPos pos) {
        return AtlasClientState.owns(owner) && AtlasClientState.allows(dimension, pos) ? super.getProvider(pos) : null;
    }

    @Override public String getTilePredicate(ChunkPos pos) {
        return AtlasClientState.owns(owner) && AtlasClientState.allows(dimension, pos) ? super.getTilePredicate(pos) : null;
    }
}
