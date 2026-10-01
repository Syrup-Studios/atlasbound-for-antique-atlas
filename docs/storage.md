# Storage and exploration

## Atlas identity

Each physical atlas stack stores a UUID in custom item data. A copied stack keeps this UUID. Copies with the same UUID share one server dataset. Atlasbound does not store explored chunks or map images in the item.

Crafted atlases receive an ID and an empty save entry at once. A stack obtained another way receives an ID and save entry on the first server tick that sees it. A malformed existing ID is not replaced. An unreadable saved file disables that UUID and stays in place. A missing save file creates an empty dataset for the same UUID.

Each atlas uses one `SavedData` file in the Overworld `data/` folder: `atlasbound_<UUID>.dat`. Schema 2 stores terrain and markers. The `dimensions` compound stores each dimension's regions. Each region key is a canonical signed long string. Each region value is a byte array from `BitSet.toByteArray()`, with no more than 128 bytes. A region covers 32 by 32 chunks. Bit index is `(localX << 5) + localZ`.

The `markers` compound is keyed by dimension, then marker ID. Each marker record has typed fields `id`, `x`, `y`, `z`, `name`, and `color`. IDs use the `custom/` path prefix. Positions are limited to x/z ±30,000,000 and y ±2048. Names are limited to 128 characters; colors are 24-bit RGB. The total limit is 1024 markers per atlas across dimensions. Invalid marker data rejects the saved data, so Atlasbound does not replace a malformed save. Schema 1 loads with zero markers and keeps its terrain data.

## Exploration rules

Every 20 server ticks, the server scans each online player's current server tracking view. It records each chunk that is already loaded for each distinct valid atlas UUID in inventory slots 0-35 and the offhand. The tracking view can include chunks waiting to be sent. Atlasbound does not generate chunks or merge past exploration. Copies with the same UUID share one dataset. Invalid UUIDs and corrupt saves disable only that atlas UUID. Atlas screen, hand, and inventory selection controls the displayed atlas only. While the atlas screen is open, that atlas stays selected while the player still has it. Otherwise, selection priority is main hand, offhand, then the first atlas in inventory slots 0-35.

Atlasbound does not load a chunk to record it. It uses Surveyor's loaded terrain summary to build updates. Atlasbound's explored mask controls which terrain bits go into that atlas view. Surveyor's own player and group exploration continues as a separate, shared system.

The selection packet has an epoch. The client ignores old region packets after selection changes. Region packets hold up to 16 64-bit words, or 1024 bits. A selection sends a full snapshot for every dimension and region. Transfer size grows with explored data.

## Access and limits

The server checks that a marker edit comes from a player who has the selected physical atlas and the current selection epoch. There is no account ownership binding. A player who has a copy with the same UUID can edit and see its shared markers. Different UUIDs keep separate marker sets. The server enforces the 1024 marker per-atlas limit.

The client uses AA4's native icon palette. Atlasbound does not copy AA4 assets or code. Add a marker from the toolbar, then click the map to place it. The toolbar's current-player action uses AA4's Shift behavior. Right-click an existing marker to edit it. Use the delete toolbar action, then click a marker to delete it. Atlasbound hides atlas renderings for held stacks that are not selected.

Player-position overlays remain shared. Structures and automatically generated global landmarks remain disabled. This feature covers user-created custom markers only. It does not add global Surveyor waypoints or waypoint imports.

## Manual checks

These checks need a throwaway world. They have not been done yet.

1. Craft an atlas. Confirm it gets an ID and save file.
2. Copy an atlas. Confirm both copies show the same explored chunks.
3. Carry two different atlases together and explore. Confirm both record the same new chunks and each view shows its own history.
4. Carry atlases in inventory slots 0-35 and offhand. Confirm each distinct UUID records new loaded chunks, duplicate UUIDs share one dataset, and dropped or container-stored atlases stop recording.
5. Put atlases in the main hand and offhand. Confirm the main hand takes priority.
6. Open the screen, move the atlas to another slot, and explore loaded chunks. Confirm the open atlas stays selected while you have it.
7. Transfer a copy to another player. Confirm the same UUID sees the same data and either holder can select it.
8. Switch dimensions and return. Confirm each dimension keeps its own regions.
9. Disconnect and reconnect. Confirm selection clears on disconnect and the save remains.
10. Remove an atlas stack without deleting its save file. Confirm another copy still sees the dataset.
11. In a throwaway world, corrupt an atlas save file. Confirm that UUID becomes unavailable and the bad file is not replaced.
12. In a throwaway world, remove one atlas save file while keeping its item. Confirm the same UUID starts with empty exploration.
13. Repeat on Fabric. NeoForge with Connector and Forgified Fabric API still needs a runtime check.
14. Create a marker with the native toolbar and icon palette. Confirm it appears on the map.
15. Right-click the marker to edit its name, icon, or color. Confirm the update appears.
16. Use the delete toolbar action on the marker. Confirm it is removed.
17. Restart the world. Confirm the marker remains.
18. Copy an atlas with the same UUID and confirm it sees the same markers. Use a different UUID and confirm it has a separate marker set.
