package net.syrupstudios.atlasbound.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.syrupstudios.atlasbound.network.AtlasPackets;

public final class AtlasClientPacketHandlers {
    private AtlasClientPacketHandlers() {}

    public static void selection(AtlasPackets.Selection packet, Level level, Player player) {
        Minecraft.getInstance().execute(() -> AtlasClientState.begin(packet));
    }

    public static void region(AtlasPackets.Region packet, Level level, Player player) {
        Minecraft.getInstance().execute(() -> AtlasClientState.region(packet));
    }

    public static void ready(AtlasPackets.Ready packet, Level level, Player player) {
        Minecraft.getInstance().execute(() -> AtlasClientState.ready(packet));
    }

    public static void marker(AtlasPackets.Marker packet, Level level, Player player) {
        Minecraft.getInstance().execute(() -> AtlasClientState.marker(packet));
    }
}
