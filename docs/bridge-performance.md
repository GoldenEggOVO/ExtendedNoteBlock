# Bridge scheduling and persistence

The wireless-redstone timer and projection-playback timer are independent. `wireless-redstone.poll-period-ticks` still defaults to 1 for existing redstone pulse behavior; changing it no longer reduces music polling frequency. Music timing remains limited by the server's actual tick rate.

Indexed objects cache their parsed coordinates. Receivers are indexed by world, so a world power change visits only its receivers. Arbitrary block lookups are not added to the cache, and object removal/reload releases index entries. This does not force-load unloaded chunks or change the existing unloaded-transmitter policy.

Ordinary block and command edits coalesce YAML writes using `persistence.batch-delay-ticks` (default 10 ticks, clamped to 1–200). The latest authoritative state is saved once per dirty file per batch. Failed writes stay dirty and retry. Normal disable saves all state, and `/enb reload` flushes dirty state before reading files; a failed flush aborts the reload. An abrupt process termination may lose edits inside the batch window. Set the delay to 1 to minimize this window.

Projection import completion retains the existing synchronous save acknowledgement. Bukkit world access, YAML snapshot construction and writes stay on the server thread; this change reduces repeated full-file writes but does not make a single very large save asynchronous. No wire format, resource pack or client mod changes are required.

Tests include independent timers, coalesced snapshots, shutdown flush, failed-save retry and index cleanup. No real-server performance percentage is claimed by these tests.
