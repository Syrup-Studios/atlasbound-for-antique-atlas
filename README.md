# Atlasbound for Antique Atlas 4

Atlasbound saves terrain exploration on the server for each atlas. An atlas stores only a UUID. Copies with the same UUID share exploration.

Use a cartography table to copy an atlas. Put one tagged atlas in the first slot and one or more ordinary books in the second. Each copy uses one book and leaves the original in place. It keeps the atlas UUID, so both atlases share exploration and markers. An atlas without a valid UUID cannot be copied. Hold it in your inventory first so Atlasbound can assign one.

The target is Minecraft 1.21.1. Build and run commands use Java 21.

## Install

Install Atlasbound, Fabric API, and Surveyor on the client and server. Atlasbound bundles Rosetta Library. Clients also need Antique Atlas 4 for the map screen. Craft the Atlasbound atlas from one book and one compass.

Both targets use Fabric Loader APIs. Fabric runs the jar directly. The NeoForge target makes a Connector-compatible Fabric jar. It is not a native NeoForge build. NeoForge needs Sinytra Connector and Forgified Fabric API.

## Build

```sh
./gradlew :1.21.1-fabric:build
./gradlew :1.21.1-neoforge:build
./gradlew :1.21.1-fabric:buildAndCollect :1.21.1-neoforge:buildAndCollect
```

## Publish

The `publishMods` tasks publish each target jar to Modrinth and CurseForge. Targets are labeled `Fabric` or `NeoForge`. The `NeoForge` target uses Fabric Loader through Connector. Release notes use the root [CHANGELOG.md](CHANGELOG.md). Platform metadata declares dependency relationships. Antique Atlas 4 is client-only.

Run this command to publish both targets. It uses a dry run if either token is missing:

```sh
./gradlew publishMods --no-parallel
```

To force a preview, run:

```sh
./gradlew :1.21.1-fabric:publishMods :1.21.1-neoforge:publishMods -Ppublish.dry_run=true
```

Set `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN` as environment variables or Gradle properties to enable uploads. Gradle properties can use the uppercase names or `publish.modrinth_token` and `publish.curseforge_token`; do not commit tokens. Project IDs come from `publish.modrinth` and `publish.curseforge` in `stonecutter.properties.yaml` and can be configured there.

## Data and limits

The server records loaded chunks every 20 ticks for valid atlas UUIDs in inventory slots 0-35 and the offhand. The tracking view can include chunks waiting to be sent. Atlasbound does not generate chunks or merge past exploration. Copies with the same UUID share terrain and markers. Structures, automatic landmarks, and Surveyor waypoint imports are not included. See [docs/storage.md](docs/storage.md) for storage details and manual checks.

The client saves rendered tile resolutions under `<world>/data/atlasbound/cache/` in singleplayer, or `<game>/data/surveyor/surveyor/<seed>/atlasbound/<server hash>/` in multiplayer. Each atlas UUID and dimension has a separate cache file. A tile restores only after the server confirms that the atlas can show its chunk. Resource or cache format changes cause tiles to rebuild. The cache does not store exploration permissions or marker data.
