# Atlasbound for Antique Atlas 4

Atlasbound saves terrain exploration on the server for each atlas. An atlas stores only a UUID. Copies with the same UUID share exploration.

Use a cartography table to copy an atlas. Put one tagged atlas in the first slot and one or more ordinary books in the second. Each copy uses one book and leaves the original in place. It keeps the atlas UUID, so both atlases share exploration and markers. An atlas without a valid UUID cannot be copied. Hold it in your inventory first so Atlasbound can assign one.

The target is Minecraft 1.21.1. Build and run commands use Java 21.

## Install

Install Atlasbound, Fabric API, and Surveyor 1.2.3+1.21 on the client and server. Clients also need Antique Atlas 4 3.1.2+1.21 for the map screen. Craft the Atlasbound atlas from one book and one compass. The AA4 Atlas addon is not required.

Both targets use Fabric Loader APIs. Fabric runs the jar directly. The NeoForge target makes a Connector-compatible Fabric jar. It is not a native NeoForge build. NeoForge needs Sinytra Connector and Forgified Fabric API. Runtime support with Connector has not been checked in-game.

## Build

```sh
./gradlew :1.21.1-fabric:build
./gradlew :1.21.1-neoforge:build
./gradlew :1.21.1-fabric:buildAndCollect :1.21.1-neoforge:buildAndCollect
```

Collected jars go to `build/libs/0.1.0/`. The target name is included in each artifact name. Development run directories are shared under `run/`. For a Fabric client, run `./gradlew :1.21.1-fabric:runClient`. The Gradle client task does not start NeoForge.

To select a Stonecutter target, run `./gradlew "Set active project to 1.21.1-fabric"` or `./gradlew "Set active project to 1.21.1-neoforge"`.

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

## License

This project is licensed under the [GNU Lesser General Public License, version 3 or later](LICENSE.md).
