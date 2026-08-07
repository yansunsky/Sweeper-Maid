# Sweeper-Maid

A Minecraft mod that cleans up dropped items and unnecessary entities on your server (NeoForge 1.21.1 / Java 21).

> **Fork notice**: This project is a modified fork of the original [Sweeper-Maid by Liu Dongyu (liudongyu)](https://github.com/Viola-Siemens/Sweeper-Maid), not an original work. Thanks to the original author for the open-source contribution.

---

## Changes vs. the original (this fork)

1. **Paged dustbin GUI**: Previous / page number / next buttons at the top of the GUI, with hover tooltips; page switching without closing the screen (no mouse reset).
2. **Dynamic page count**: Page 2 only appears once page 1 is full (54 kinds); empty pages are reclaimed automatically.
3. **Large stacking**: Per-slot stack limit is changed from vanilla 64 to `DUSTBIN_SLOT_CAPACITY` (default 1,000,000). One kind per slot, counts keep stacking up.
4. **Global sorting after sweeping**: After each sweep, all items are re-packed sorted by count descending.
5. **Global overload auto-sweep**: Real-time dropped-item pile counter (checked every tick, corrected every 5s); triggers an extra sweep beyond the threshold to prevent lag.
6. **Experience orb collection**: Experience orbs are converted into experience bottles and stored in the dustbin (`XP_PER_BOTTLE`, default 10, slight loss to prevent duplication).
7. **Dustbin auto trash**: Two modes — **clear** (empty all dustbins) and **pack** (compress the contents to a file, then empty them; importable later). Supports scheduled auto-processing and manual commands.
8. **Localization**: Built-in `en_us` / `zh_cn` language files; message configs follow the client language when left empty.
9. **Chat quick-open**: Clicking `[Dustbin N]` in the sweep message opens that page directly (the `/sweepermaid dustbin <page>` command still works).
10. **Fork welcome message**: Players are notified on login that this is the Yansunsky fork; please do not report issues to the original author.
11. **Bilingual config comments**: All config options have both English and Chinese comments.
12. **Game tests**: Built-in GameTests cover large-stack serialization, merge, sorting, take limits, pack/import roundtrips, etc. (`gradlew runGameTestServer`).

## Commands

| Command | Description | Permission |
|---|---|---|
| `/sweepermaid help` | Show all command descriptions | none |
| `/sweepermaid dustbin [index]` | Open the dustbin page (default page 0) | `PERMISSION_LEVEL_DUSTBIN` (default 0) |
| `/sweepermaid clean` | Run a sweep immediately | `PERMISSION_LEVEL_CLEAN` (default 2) |
| `/sweepermaid trash` | Process the dustbins now (clear or pack, follows the config mode) | OP (level 2) |
| `/sweepermaid pack` | Pack the dustbin contents into a compressed file now | OP (level 2) |
| `/sweepermaid unpack <file>` | Import a pack file and merge into the dustbins (Tab-complete file names) | OP (level 2) |

Pack files are stored under `sweeper_maid_packs/` in the world save directory (`pack-<timestamp>.nbt.gz`, gzip-compressed NBT). After a successful import, the file is moved into the `imported/` subfolder.

## Configuration (`config/sweeper_maid-common.toml`)

| Option | Default | Description |
|---|---|---|
| `ITEM_SWEEP_INTERVAL` | 600 | Scheduled sweep interval (seconds); 0 disables |
| `ITEM_OVERLOAD_THRESHOLD` | 640 | Chunk overload warning threshold (warning only) |
| `GLOBAL_ITEM_COUNT_THRESHOLD` | 500 | Global pile threshold; triggers an extra sweep |
| `OVERLOAD_SWEEP_COOLDOWN` | 60 | Cooldown between overload sweeps (seconds) |
| `DUSTBIN_SLOT_CAPACITY` | 1000000 | Max stack per dustbin slot |
| `DUSTBIN_TRASH_MODE` | clear | Dustbin auto-trash mode: `clear` / `pack` |
| `DUSTBIN_TRASH_INTERVAL` | -1 | Dustbin auto-trash interval (seconds); -1 disables |
| `XP_PER_BOTTLE` | 10 | Experience points per bottle |
| `DUSTBIN_NAME` | empty | Dustbin name prefix (empty = localized) |
| `EXTRA_ENTITY_TYPES` / `ITEM_WHITELIST` / `ITEM_BLACKLIST` | — | Extra entities / whitelist / blacklist |
| `MESSAGE_*` | empty | Message templates (empty = localized) |
| `PERMISSION_LEVEL_DUSTBIN` / `PERMISSION_LEVEL_CLEAN` | 0 / 2 | Command permission levels |

## Development

```
gradlew runClient          # launch the client
gradlew runServer          # launch the dedicated server (set eula=true in run/server/eula.txt)
gradlew runGameTestServer  # run game tests (needs run/testServer/gameteststructures/empty.snbt)
```

## License

Same license as the original repository (see `LICENSE`). Original repo: [Viola-Siemens/Sweeper-Maid](https://github.com/Viola-Siemens/Sweeper-Maid)
