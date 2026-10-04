package net.syrupstudios.atlasbound.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import folk.sisby.antique_atlas.AntiqueAtlas;
import folk.sisby.antique_atlas.reloader.BiomeTileProviders;
import folk.sisby.surveyor.client.ClientSummary;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.syrupstudios.atlasbound.Atlasbound;

final class AtlasTileCache implements SimpleSynchronousResourceReloadListener, IdentifiableResourceReloadListener {
    private static final int MAGIC = 0x41544243;
    private static final int FORMAT = 1;
    private static final int MAX_BYTES = 64 * 1024 * 1024;
    private static final int MAX_ENTRIES = 2_000_000;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Atlasbound tile cache");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile String fingerprint;
    private static volatile boolean fingerprintFailed;

    private AtlasTileCache() {}

    static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new AtlasTileCache());
    }

    static Path path(Minecraft client, ResourceLocation dimension, java.util.UUID atlas) {
        Path root;
        if (client.getSingleplayerServer() != null) {
            root = client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).resolve("data/atlasbound/cache");
        } else {
            if (client.getConnection() == null || client.getCurrentServer() == null) return null;
            ClientSummary summary;
            try {
                summary = ClientSummary.of(client.getConnection());
            } catch (RuntimeException exception) {
                return null;
            }
            if (summary == null || summary.saveFile == null) return null;
            String address = summaryAddress(client);
            if (address == null) return null;
            root = summary.saveFile.toPath().getParent().resolve("atlasbound").resolve(hash(address));
        }
        Path base = root.resolve(atlas.toString()).toAbsolutePath().normalize();
        Path file = base.resolve("dimensions").resolve(hash(dimension.toString()) + ".dat").normalize();
        return file.startsWith(base) ? file : null;
    }

    private static String summaryAddress(Minecraft client) {
        String address = client.getCurrentServer().ip;
        if (address == null || address.isBlank()) return null;
        return address.trim().toLowerCase(java.util.Locale.ROOT);
    }

    static void load(AtlasMapView view, Path path) {
        if (path == null) return;
        view.cachePath = path;
        String expected = fingerprint();
        if (expected == null) {
            view.cacheLoaded = true;
            return;
        }
        view.cacheFingerprint = expected;
        IO.execute(() -> {
            Map<ChunkPos, Entry> entries = read(path, expected);
            Minecraft.getInstance().execute(() -> {
                if (!AtlasClientState.isCurrent(view)) return;
                view.cacheEntries = entries;
                view.cacheLoaded = true;
                AtlasClientState.restoreCached(view);
            });
        });
    }

    static void save(AtlasMapView view) {
        if (!view.cacheDirty || view.cachePath == null || view.cacheFingerprint == null) return;
        Path path = view.cachePath;
        String signature = view.cacheFingerprint;
        Map<ChunkPos, Entry> snapshot = view.cacheSnapshot();
        IO.execute(() -> {
            Map<ChunkPos, Entry> merged = read(path, signature);
            merged.putAll(snapshot);
            write(path, signature, merged);
        });
        view.cacheDirty = false;
    }

    private static Map<ChunkPos, Entry> read(Path path, String expected) {
        Map<ChunkPos, Entry> entries = new HashMap<>();
        if (!Files.isRegularFile(path)) return entries;
        try {
            long size = Files.size(path);
            if (size < 48 || size > MAX_BYTES) return entries;
            try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
                if (input.readInt() != MAGIC || input.readInt() != FORMAT || !input.readUTF().equals(expected)) return entries;
                int count = input.readInt();
                if (count < 0 || count > MAX_ENTRIES) return entries;
                for (int i = 0; i < count; i++) {
                    ChunkPos pos = new ChunkPos(input.readInt(), input.readInt());
                    if (Math.abs((long) pos.x) > 1_875_000L || Math.abs((long) pos.z) > 1_875_000L) return new HashMap<>();
                    String providerId = input.readUTF();
                    ResourceLocation provider = providerId.length() <= 256 ? ResourceLocation.tryParse(providerId) : null;
                    String elevation = input.readUTF();
                    if (elevation.length() > 32) return new HashMap<>();
                    if (elevation.isEmpty()) elevation = null;
                    else {
                        boolean known = false;
                        for (folk.sisby.antique_atlas.TileElevation value : folk.sisby.antique_atlas.TileElevation.values())
                            known |= value.getName().equals(elevation);
                        if (!known) continue;
                    }
                    if (provider == null) continue;
                    entries.put(pos, new Entry(provider, elevation));
                }
                if (input.read() != -1) return new HashMap<>();
            }
        } catch (IOException | RuntimeException exception) {
            Atlasbound.LOGGER.debug("Ignoring invalid rendered tile cache {}", path, exception);
            entries.clear();
        }
        return entries;
    }

    private static void write(Path path, String signature, Map<ChunkPos, Entry> entries) {
        if (entries.size() > MAX_ENTRIES) return;
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new BoundedOutputStream(Files.newOutputStream(temp), MAX_BYTES)))) {
                output.writeInt(MAGIC);
                output.writeInt(FORMAT);
                output.writeUTF(signature);
                output.writeInt(entries.size());
                for (Map.Entry<ChunkPos, Entry> entry : entries.entrySet()) {
                    output.writeInt(entry.getKey().x);
                    output.writeInt(entry.getKey().z);
                    output.writeUTF(entry.getValue().provider().toString());
                    output.writeUTF(entry.getValue().elevation() == null ? "" : entry.getValue().elevation());
                }
            }
            if (Files.size(temp) > MAX_BYTES) {
                Files.deleteIfExists(temp);
                return;
            }
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            Atlasbound.LOGGER.warn("Could not save rendered tile cache {}", path, exception);
            try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
        }
    }

    private static String fingerprint() {
        if (fingerprintFailed) return null;
        String current = fingerprint;
        if (current != null) return current;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Minecraft client = Minecraft.getInstance();
            ResourceManager manager = client.getResourceManager();
            {
                manager.listResources("atlas/biome", id -> id.getPath().endsWith(".json"))
                        .entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                            digest.update(entry.getKey().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            try (var stream = entry.getValue().open()) {
                                byte[] bytes = new byte[8192];
                                int length;
                                while ((length = stream.read(bytes)) >= 0) digest.update(bytes, 0, length);
                            } catch (IOException exception) {
                                throw new IllegalStateException(exception);
                            }
                        });
            }
            String aa = FabricLoader.getInstance().getModContainer("antique_atlas").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("missing");
            String surveyor = FabricLoader.getInstance().getModContainer("surveyor").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("missing");
            current = HexFormat.of().formatHex(digest.digest()) + ":" + aa + ":" + surveyor + ":" + net.minecraft.SharedConstants.getCurrentVersion().getName() + ":" + AntiqueAtlas.CONFIG.fallbackFailHandling;
            fingerprint = current;
            return current;
        } catch (NoSuchAlgorithmException | RuntimeException exception) {
            Atlasbound.LOGGER.warn("Could not fingerprint rendered tile resources", exception);
            fingerprintFailed = true;
            return null;
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Override public net.minecraft.resources.ResourceLocation getFabricId() { return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("atlasbound", "tile_cache"); }
    @Override public java.util.Collection<net.minecraft.resources.ResourceLocation> getFabricDependencies() {
        return java.util.List.of(BiomeTileProviders.ID);
    }
    @Override public void onResourceManagerReload(ResourceManager manager) {
        fingerprint = null;
        fingerprintFailed = false;
        fingerprint();
        Minecraft.getInstance().execute(AtlasClientState::resourceReloaded);
    }

    static void flush() {
        try { java.util.concurrent.CompletableFuture.runAsync(() -> {}, IO).get(10, java.util.concurrent.TimeUnit.SECONDS); }
        catch (Exception exception) { Atlasbound.LOGGER.warn("Timed out saving rendered tile cache", exception); }
    }

    private static final class BoundedOutputStream extends OutputStream {
        private final OutputStream output;
        private final int limit;
        private int count;

        private BoundedOutputStream(OutputStream output, int limit) {
            this.output = output;
            this.limit = limit;
        }

        @Override public void write(int value) throws IOException {
            check(1);
            output.write(value);
            count++;
        }

        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            check(length);
            output.write(bytes, offset, length);
            count += length;
        }

        private void check(int length) throws IOException {
            if (length < 0 || count + (long) length > limit) throw new IOException("Rendered tile cache exceeds size limit");
        }

        @Override public void flush() throws IOException { output.flush(); }
        @Override public void close() throws IOException { output.close(); }
    }

    record Entry(ResourceLocation provider, String elevation) {}
}
