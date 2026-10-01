package net.syrupstudios.atlasbound.network;

import java.util.BitSet;
import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.syrupstudios.atlasbound.AtlasMarker;

public final class AtlasPackets {
    private AtlasPackets() {}

    public record Open(int slot) implements CustomPacketPayload {
        public static final Type<Open> TYPE = AtlasPackets.type("open");
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.of(
                (buf, packet) -> buf.writeVarInt(packet.slot), buf -> new Open(buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Close() implements CustomPacketPayload {
        public static final Type<Close> TYPE = AtlasPackets.type("close");
        public static final StreamCodec<RegistryFriendlyByteBuf, Close> CODEC = StreamCodec.unit(new Close());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Selection(long epoch, UUID atlas, boolean openScreen) implements CustomPacketPayload {
        public static final Type<Selection> TYPE = AtlasPackets.type("selection");
        public static final StreamCodec<RegistryFriendlyByteBuf, Selection> CODEC = StreamCodec.of((buf, packet) -> {
            buf.writeVarLong(packet.epoch);
            buf.writeBoolean(packet.atlas != null);
            if (packet.atlas != null) buf.writeUUID(packet.atlas);
            buf.writeBoolean(packet.openScreen);
        }, buf -> new Selection(buf.readVarLong(), buf.readBoolean() ? buf.readUUID() : null, buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Region(long epoch, ResourceLocation dimension, long region, long[] bits) implements CustomPacketPayload {
        public static final Type<Region> TYPE = AtlasPackets.type("region");
        public static final StreamCodec<RegistryFriendlyByteBuf, Region> CODEC = StreamCodec.of((buf, packet) -> {
            if (packet.bits.length > 16) throw new IllegalArgumentException("Atlasbound region exceeds 1024 bits");
            buf.writeVarLong(packet.epoch);
            buf.writeResourceLocation(packet.dimension);
            buf.writeLong(packet.region);
            buf.writeByte(packet.bits.length);
            for (long word : packet.bits) buf.writeLong(word);
        }, buf -> {
            long epoch = buf.readVarLong();
            ResourceLocation dimension = buf.readResourceLocation();
            long region = buf.readLong();
            int count = buf.readUnsignedByte();
            if (count > 16) throw new IllegalArgumentException("Atlasbound region exceeds 1024 bits");
            long[] bits = new long[count];
            for (int i = 0; i < count; i++) bits[i] = buf.readLong();
            if (BitSet.valueOf(bits).length() > 1024) throw new IllegalArgumentException("Atlasbound region exceeds 1024 bits");
            return new Region(epoch, dimension, region, bits);
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Ready(long epoch) implements CustomPacketPayload {
        public static final Type<Ready> TYPE = AtlasPackets.type("ready");
        public static final StreamCodec<RegistryFriendlyByteBuf, Ready> CODEC = StreamCodec.of(
                (buf, packet) -> buf.writeVarLong(packet.epoch), buf -> new Ready(buf.readVarLong()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record MarkerEdit(long epoch, ResourceLocation dimension, ResourceLocation previous, AtlasMarker marker)
            implements CustomPacketPayload {
        public static final Type<MarkerEdit> TYPE = AtlasPackets.type("marker_edit");
        public static final StreamCodec<RegistryFriendlyByteBuf, MarkerEdit> CODEC = StreamCodec.of(
                (buf, packet) -> writeMarkerChange(buf, packet.epoch, packet.dimension, packet.previous, packet.marker),
                buf -> {
                    MarkerChange change = readMarkerChange(buf);
                    return new MarkerEdit(change.epoch, change.dimension, change.removed, change.marker);
                });
        public MarkerEdit {
            requireMarkerChange(previous, marker);
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Marker(long epoch, ResourceLocation dimension, ResourceLocation removed, AtlasMarker marker)
            implements CustomPacketPayload {
        public static final Type<Marker> TYPE = AtlasPackets.type("marker");
        public static final StreamCodec<RegistryFriendlyByteBuf, Marker> CODEC = StreamCodec.of(
                (buf, packet) -> writeMarkerChange(buf, packet.epoch, packet.dimension, packet.removed, packet.marker),
                buf -> {
                    MarkerChange change = readMarkerChange(buf);
                    return new Marker(change.epoch, change.dimension, change.removed, change.marker);
                });
        public Marker {
            requireMarkerChange(removed, marker);
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private record MarkerChange(long epoch, ResourceLocation dimension, ResourceLocation removed, AtlasMarker marker) {}

    private static void requireMarkerChange(ResourceLocation removed, AtlasMarker marker) {
        if (removed == null && marker == null) throw new IllegalArgumentException("Empty Atlasbound marker change");
        if (removed != null && removed.toString().length() > AtlasMarker.MAX_ID_LENGTH)
            throw new IllegalArgumentException("Atlasbound marker id is too long");
    }

    private static void writeMarkerChange(RegistryFriendlyByteBuf buf, long epoch, ResourceLocation dimension,
                                          ResourceLocation removed, AtlasMarker marker) {
        requireMarkerChange(removed, marker);
        buf.writeVarLong(epoch);
        buf.writeResourceLocation(dimension);
        buf.writeBoolean(removed != null);
        if (removed != null) buf.writeUtf(removed.toString(), AtlasMarker.MAX_ID_LENGTH);
        buf.writeBoolean(marker != null);
        if (marker != null) AtlasMarker.STREAM_CODEC.encode(buf, marker);
    }

    private static MarkerChange readMarkerChange(RegistryFriendlyByteBuf buf) {
        long epoch = buf.readVarLong();
        ResourceLocation dimension = buf.readResourceLocation();
        ResourceLocation removed = buf.readBoolean() ? ResourceLocation.parse(buf.readUtf(AtlasMarker.MAX_ID_LENGTH)) : null;
        AtlasMarker marker = buf.readBoolean() ? AtlasMarker.STREAM_CODEC.decode(buf) : null;
        requireMarkerChange(removed, marker);
        return new MarkerChange(epoch, dimension, removed, marker);
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("atlasbound", path));
    }
}
