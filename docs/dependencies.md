# Dependencies and hooks

The targets use Minecraft 1.21.1 and Java 21. These exact dependencies are pinned in the build:

| Mod | Coordinate | License | Required side |
| --- | --- | --- | --- |
| Surveyor | `folk.sisby:surveyor:1.2.3+1.21` | LGPL-3.0-or-later | Client and server |
| Antique Atlas 4 | `folk.sisby:antique-atlas:3.1.2+1.21` | LGPL-3.0-or-later | Client |
| AA4 Atlas | `maven.modrinth:aR9FhY20:6qNlFux1` (`1.1.2+1.21`) | LGPL-3.0-or-later | Client and server |

The client initializer checks that Antique Atlas 4 has version `3.1.2+1.21`.

Surveyor and Antique Atlas 4 resolve from `https://repo.sleeping.town/`. AA4 Atlas resolves from `https://api.modrinth.com/maven`. The artifact ID and version ID pin the 1.21.1 release. Do not use the Maven coordinate `maven.modrinth:aa4-atlas:1.1.2`; it resolves a 1.20.1 artifact.

Kaleido Config is a transitive dependency of Surveyor and Antique Atlas 4. McQoy adds an in-game config screen. It is optional; AA4 reads its TOML config without McQoy.

## Loader support

Both targets use Fabric Loader APIs and produce Fabric mod jars. The `neoforge` target changes the artifact name and run directory. It is not a native NeoForge mod. See the [README](../README.md) for required Connector setup.

## Hook map

| Area | API or hook | Use and limit |
| --- | --- | --- |
| Surveyor terrain | `WorldTerrain.onChunkLoad`, `WorldTerrain.queueUpdate`, `WorldSummary.terrain().getRegion(...).bitSet()` | Atlasbound calls the public methods to populate and send shared terrain. Its client view intersects the atlas mask with Surveyor's cached terrain bits. |
| Surveyor server exploration | `PlayerSummary` and `ServerSummary`; Surveyor's own `MixinChunkDataSender.sendChunkData` | Surveyor tracks player and group exploration separately. Its mixin records chunks as the server sends them. Atlasbound does not replace this global exploration path. |
| Surveyor client exploration | `SurveyorClient.ClientExploration.addChunk` and `mergeRegion`; `SurveyorClientEvents` | Surveyor stores and publishes its client-side exploration data. Atlasbound keeps its UUID masks separate. |
| AA4 map view | `WorldAtlasData.getOrCreate` mixin; `AtlasMapView.onTerrainUpdated` | Atlasbound returns a view for the selected UUID and filters terrain updates through that UUID's region mask. It registers the view in `WorldAtlasData.WORLDS` so AA4 can find it by dimension. |
| AA4 tile reads | `AtlasMapView.getTile`, `getProvider`, and `getTilePredicate` | These methods deny tiles outside the selected atlas mask or after selection changes. Structure and landmark data is disabled in this view. |
| AA4 open flow | `AntiqueAtlas.openAtlasScreen` mixin | A key press requests a server selection first. The client opens the screen after it receives the selected atlas snapshot. |
| Physical item open | `ItemStack.use` mixin | The physical addon's empty `AtlasOpenPayload` does not carry atlas identity. Atlasbound cancels that path and sends its own server selection request. The server checks the item slot. |
| Atlas identity | `ItemStack.onCraftedBy` mixin and server inventory tick | Crafting creates a UUID and blank saved data. Other stacks get a UUID on the first server tick that sees them without one. Copies with the same custom-data UUID share data. |
| Atlas copying | `CartographyTableMenu.slotsChanged`, input slots, `quickMoveStack`, and result slot `onTake` | The first slot accepts one valid-ID atlas and the second accepts books. The result copies all atlas components; taking it consumes one book and preserves the original. Vanilla map operations stay unchanged. The native screen preview is blank because AA4 atlases have no vanilla map ID. |
| AA4 controls | `AtlasRenderer.registerOverlay` and `AtlasOverlay.onScreenInit` | The overlay removes the map's edit and delete controls. The project does not use a screen mixin for those controls. |
| AA4 custom markers | `MarkerModal` and `WorldSummary.landmarks` | The local adapter redirects marker reads to the selected atlas's server-saved markers. The server accepts edits only for the selected atlas UUID and current epoch. The native toolbar, icon palette, and marker editing flow remain AA4-owned. |
| Held atlas model | `ItemInHandRenderer.renderMap` mixin, priority 2000 | The mixin hides an atlas map render when that held stack is not the selected UUID. |
| AA4 key binding | Fabric `START_CLIENT_TICK` callback | When the screen is closed and an atlas is present, Atlasbound consumes the AA4 key press and asks the server to select an atlas. |

## Source pins

- [Surveyor source at commit `0cedc8b`](https://github.com/sisby-folk/surveyor/tree/0cedc8ba893bfc79c4b2d504c34c4196d1bb3e90). Relevant files: [`WorldSummary.java`](https://github.com/sisby-folk/surveyor/blob/0cedc8ba893bfc79c4b2d504c34c4196d1bb3e90/src/main/java/folk/sisby/surveyor/WorldSummary.java), [`PlayerSummary.java`](https://github.com/sisby-folk/surveyor/blob/0cedc8ba893bfc79c4b2d504c34c4196d1bb3e90/src/main/java/folk/sisby/surveyor/PlayerSummary.java), [`ServerSummary.java`](https://github.com/sisby-folk/surveyor/blob/0cedc8ba893bfc79c4b2d504c34c4196d1bb3e90/src/main/java/folk/sisby/surveyor/ServerSummary.java), [`WorldTerrain.java`](https://github.com/sisby-folk/surveyor/blob/0cedc8ba893bfc79c4b2d504c34c4196d1bb3e90/src/main/java/folk/sisby/surveyor/terrain/WorldTerrain.java), [`SurveyorClient.java`](https://github.com/sisby-folk/surveyor/blob/0cedc8ba893bfc79c4b2d504c34c4196d1bb3e90/src/main/java/folk/sisby/surveyor/client/SurveyorClient.java), and [`MixinChunkDataSender.java`](https://github.com/sisby-folk/surveyor/blob/0cedc8ba893bfc79c4b2d504c34c4196d1bb3e90/src/main/java/folk/sisby/surveyor/mixin/MixinChunkDataSender.java).
- [Antique Atlas 4 source at commit `1821a06`](https://github.com/sleepingdragoninn/antique-atlas/tree/1821a06b0ad40b12fc1bf9943baf307fc9ed9fc3). Relevant files: [`WorldAtlasData.java`](https://github.com/sleepingdragoninn/antique-atlas/blob/1821a06b0ad40b12fc1bf9943baf307fc9ed9fc3/src/main/java/folk/sisby/antique_atlas/WorldAtlasData.java), [`AntiqueAtlas.java`](https://github.com/sleepingdragoninn/antique-atlas/blob/1821a06b0ad40b12fc1bf9943baf307fc9ed9fc3/src/main/java/folk/sisby/antique_atlas/AntiqueAtlas.java), and [`AtlasRenderer.java`](https://github.com/sleepingdragoninn/antique-atlas/blob/1821a06b0ad40b12fc1bf9943baf307fc9ed9fc3/src/main/java/folk/sisby/antique_atlas/gui/AtlasRenderer.java).
- The [AA4 Atlas 1.21 release artifact](https://api.modrinth.com/maven/aR9FhY20/6qNlFux1/aR9FhY20-6qNlFux1.jar) has SHA-1 `83ab02fdbdd36662f0ef59e3338c1585285520e0`. The available source checkout at commit `5488bbe` targets Minecraft 1.20.1 and does not match this release. The item, open-payload, and key-binding hooks match the 1.21 binary.

Project hook code is in [`AtlasNetworking.java`](../src/main/java/net/syrupstudios/atlasbound/network/AtlasNetworking.java), [`AtlasClientState.java`](../src/main/java/net/syrupstudios/atlasbound/client/AtlasClientState.java), [`AtlasMapView.java`](../src/main/java/net/syrupstudios/atlasbound/client/AtlasMapView.java), and the mixins under [`mixin/`](../src/main/java/net/syrupstudios/atlasbound/mixin/).

## Verification

Both Fabric and NeoForge-target `build` and `buildAndCollect` tasks passed. A Fabric client launch reached Atlasbound initialization without a mixin error. A dedicated Fabric server loaded Atlasbound and stopped at the EULA prompt. No in-world multiplayer, save/restart, or NeoForge Connector runtime check has been done.
