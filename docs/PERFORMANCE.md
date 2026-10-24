# Server Polling and Compatibility

The 26.3 bridge keeps the existing ENB ledger and music protocols. Its working set contains registered objects in loaded chunks; unloading a chunk suspends recurring physical checks without removing saved music data. Loading the chunk queues reconciliation again. World loading, unloading and `/enb reload` rebuild the relevant runtime state.

Render calculation and CraftEngine synchronization share a block lookup. Stable render records are reused, default note configuration is created only when needed, and removals are queued instead of copying every previous render key on every tick. Dirty registration is processed after the complete import transaction.

Redstone polling remains one tick by default. Projection playback retains its independent one-tick timer. Unloaded transmitters retain their known power and route, while reloading a powered note block does not invent a rising edge. CraftEngine's stable-state reconciliation and migration budget remain bounded per logic tick. World access stays on the server thread and never forces chunk loading.

The existing optional-audio mode retains its UUIDs, `optional-audio-players.txt` format and split-pack rules. Disabling audio keeps the base pack. Full Fabric and Paper Client remain separate editions and preserve both ordinary-building metadata and workshop projection imports.

Regression coverage includes the 8,879-object unloaded ledger, stable record reuse, shared block resolution, negative chunk coordinates, independent worlds, deletion, reload, wireless power retention and note power edges. These isolated checks measure bridge work; they do not establish production TPS or client FPS. Production comparisons must use matching server/plugin versions, data, loaded chunks, player counts and playback activity, with repeated JFR windows and MSPT/allocation/I/O observations.

When every registered object is active, Paper Server 0.14.5 traverses ledger entries directly, reading each type from the entry instead of performing another registry lookup. Active keys are a subset of registered keys, so matching counts select this path. Partial loading continues to traverse only the active set. Chunk churn and repeated registration have regression coverage.

The fixed mock replay compares 0, 128 and 8,879 loaded objects, including indexes rebuilt after loading the ledger. Queries remain equal to the loaded object count and temporary allocations remain small. Performance runs use repeated JVM processes, adaptive CPU-time batches and a fixed measurement core to avoid mixing the laptop's performance and efficiency cores. Mixed-scenario JVM runs showed substantial CPU variation; scenario isolation is checked separately, and no speedup for every workload is promised. Real CraftEngine, physical world access and playback costs are outside that replay; it does not establish production TPS or client FPS.

The authoritative build source remains the base bridge, `craftengine/integration/` and the preparation scripts. Run optional-audio and performance preparation after schematic preparation and before language preparation. The complete chain is tested for repeated execution. Generated Java files and private runtime diagnostics must not be committed.
