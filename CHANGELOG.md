# Version 0.3.0

- Bundled Rosetta 0.1.1 for networking and NBT support.
- Reopening the same atlas avoids a full sync and rebuild.
- Rendered tile cache persists per world and server. Cache keys include the atlas UUID and dimension, and permission uses current server exploration data.
- Resource changes invalidate the rendered tile cache.
