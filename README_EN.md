# Sweeper-Maid (1.20.1 Forge Branch)

A Minecraft mod that cleans up dropped items and unnecessary entities on your server.

> **Fork notice**: This project is a modified fork of the original [Sweeper-Maid by Liu Dongyu (liudongyu)](https://github.com/Viola-Siemens/Sweeper-Maid), not an original work. Thanks to the original author for the open-source contribution.

> **⚠️ Maintenance status (important)**: This branch is a **1.20.1 Forge port** — a **quick port** from the 1.21.1 NeoForge branch. It is **not thoroughly tested**, and the author is **not planning to maintain it actively**. Use at your own risk. For a complete, tested version, prefer the [1.21.1 NeoForge branch](../../tree/dev/NeoForge-1.21.1).

## Ported Features (aligned with the 1.21.1 branch)

1. **Paged dustbin GUI**: Previous / page number / next buttons at the top of the GUI, with hover tooltips; page switching without closing the screen (no mouse reset).
2. **Dynamic page count**: Page 2 only appears once page 1 is full (54 kinds); empty pages are reclaimed automatically.
3. **Large stacking**: Per-slot stack limit is controlled by `DUSTBIN_SLOT_CAPACITY` (default 1,000,000).
4. **Global sorting after sweeping**: Items are re-packed sorted by count descending.
5. **Global overload auto-sweep**: Real-time dropped-item pile counter; triggers an extra sweep beyond the threshold (default 500 piles, 60s cooldown).
6. **Experience orb collection**: Experience orbs are converted into experience bottles and stored in the dustbin (`XP_PER_BOTTLE`, default 10).
7. **Localization**: Built-in `en_us` / `zh_cn` language files; message configs follow the client language when left empty.
8. **Chat quick-open**: Clicking `[Dustbin N]` in the sweep message opens that page directly (1.20.1 shows a command confirmation dialog, a vanilla security mechanism).
9. **Fork welcome message**: Players are notified on login that this is the Yansunsky fork.

## Known Issues (1.20.1 branch)

- **Not thoroughly tested**: GameTests were not fully run on this branch (Gradle run tasks are affected by ForgeGradle/Gradle compatibility; prefer the IDE Gradle panel to launch runServer/runClient).
- **Large stacking may not fully work**: Items of the same type may still be packed in 64-stacks after sweeping; not yet fixed.
- Requires **Java 17** to build and run (ForgeGradle 6 is incompatible with Java 21).
- First build downloads Forge 1.20.1-47.2.0 dependencies; if the official repo fails, you may add the BMCLAPI mirror (`https://bmclapi2.bangbang93.com/maven/`) to the repository list in `build.gradle`.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/sweepermaid dustbin [index]` | Open the dustbin page (default page 0) | `PERMISSION_LEVEL_DUSTBIN` (default 0) |
| `/sweepermaid clean` | Run a sweep immediately | `PERMISSION_LEVEL_CLEAN` (default 2) |

## Configuration (`config/sweeper_maid-common.toml`)

| Option | Default | Description |
|---|---|---|
| `ITEM_SWEEP_INTERVAL` | 600 | Scheduled sweep interval (seconds); 0 disables |
| `ITEM_OVERLOAD_THRESHOLD` | 640 | Chunk overload warning threshold (warning only) |
| `GLOBAL_ITEM_COUNT_THRESHOLD` | 500 | Global pile threshold; triggers an extra sweep |
| `OVERLOAD_SWEEP_COOLDOWN` | 60 | Cooldown between overload sweeps (seconds) |
| `DUSTBIN_SLOT_CAPACITY` | 1000000 | Max stack per dustbin slot |
| `XP_PER_BOTTLE` | 10 | Experience points per bottle |
| `DUSTBIN_NAME` | empty | Dustbin name prefix (empty = localized) |
| `EXTRA_ENTITY_TYPES` / `ITEM_WHITELIST` / `ITEM_BLACKLIST` | — | Extra entities / whitelist / blacklist |
| `MESSAGE_*` | empty | Message templates (empty = localized) |
| `PERMISSION_LEVEL_DUSTBIN` / `PERMISSION_LEVEL_CLEAN` | 0 / 2 | Command permission levels |

## Development

```
gradlew runClient     # launch the client (Java 17 required)
gradlew runServer     # launch the dedicated server (set eula=true in run/server/eula.txt)
```

If `gradlew runServer` reports the task not found, run it from the IDE Gradle panel (Tasks → runs → runServer).

## License

Same license as the original repository (see `LICENSE`). Original repo: [Viola-Siemens/Sweeper-Maid](https://github.com/Viola-Siemens/Sweeper-Maid)
