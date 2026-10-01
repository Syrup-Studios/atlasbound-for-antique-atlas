package net.syrupstudios.atlasbound.client;

import java.util.UUID;

import com.google.common.collect.HashBasedTable;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.landmark.Landmark;
import folk.sisby.surveyor.landmark.WorldLandmarks;
import net.minecraft.resources.ResourceLocation;

/** Intercepts AA4's marker modal writes so they stay in the selected atlas. */
public final class AtlasMarkerLandmarks extends WorldLandmarks {
    private final WorldSummary summary;
    private ResourceLocation previous;
    private boolean editable;

    public AtlasMarkerLandmarks(WorldSummary summary) {
        super(summary, HashBasedTable.create(), null, false);
        this.summary = summary;
    }

    @Override
    public void remove(UUID owner, ResourceLocation id) {
        if (!owner.equals(AtlasClientState.markerOwner())) return;
        if (AtlasClientState.hasMarker(summary.dimension().location(), id)) {
            previous = id;
            editable = true;
        } else if (id.getPath().equals("newmarker")) {
            previous = null;
            editable = true;
        }
    }

    @Override
    public void put(Landmark landmark) {
        if (!editable || !landmark.owner().equals(AtlasClientState.markerOwner())) return;
        AtlasClientState.submitMarker(summary, previous, landmark);
    }
}
