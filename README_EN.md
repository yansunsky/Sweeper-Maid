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
6. **Experience orb collection**: swept experience orbs are converted into items and stored in the dustbin, retrievable by players. The target is chosen by `XP_CONVERSION_MODE` (see item 14); without Create they become experience bottles (`XP_PER_BOTTLE`, default 10, slight loss to prevent duplication).
7. **Dustbin auto trash**: Two modes — **clear** (empty all dustbins) and **pack** (compress the contents to a file, then empty them; importable later). Supports scheduled auto-processing and manual commands.
8. **Localization**: Built-in `en_us` / `zh_cn` language files; message configs follow the client language when left empty.
9. **Chat quick-open**: Clicking `[Dustbin N]` in the sweep message opens that page directly (the `/sweepermaid dustbin <page>` command still works).
10. **Fork welcome message**: Players are notified on login by default that this is the Yansunsky fork; please do not report issues to the original author. Toggle it with `/sweepermaid welcome on|off` (OP) or the `SHOW_FORK_WELCOME` config option.
11. **Bilingual config comments**: All config options have both English and Chinese comments.
12. **Game tests**: Built-in GameTests cover large-stack serialization, merge, sorting, take limits, pack/import roundtrips, etc. (`gradlew runGameTestServer`). 21/21 pass without Create, 85/85 with Create installed.
13. **Optional client (v2.1.0)**: **Players without this mod can join the server and use the dustbin normally.** The server now hosts the dustbin on a vanilla chest menu (no custom menu type is registered) and carries the page state over an optional network channel. A modded client "takes over" that screen and gets the custom UI with paging controls; an unmodded client sees a plain vanilla 6×9 chest showing the current page (viewable and usable, just without the paging buttons or the abbreviated counts). All three client types — modded, NeoForge-only, and fully vanilla — have been verified on a live server.
14. **Create integration (optional dependency)**: when Create is loaded, swept experience becomes **Nuggets of Experience** (`create:experience_nugget`); otherwise it stays experience bottles. Controlled by `XP_CONVERSION_MODE`: `auto` (default) / `bottle` / `nugget` / `discard`.
15. **Dustbin prompt follows permissions (v2.1.2)**: the post-sweep chat prompt and its `[Dustbin N]` links are only sent to players reaching `PERMISSION_LEVEL_DUSTBIN`; no more dead links that do nothing when clicked. Setting it to 4 disables the prompt entirely.

> **Why item 13 matters**: previously a client without this mod **was kicked during the handshake** (an unresolvable custom `MenuType` plus non-optional network payloads). Players running NeoForge but not this mod were especially affected. Those constraints are now gone.

### Create integration details (item 14)

| | Value | Source |
|---|---|---|
| Item id | `create:experience_nugget` | Create `AllItems.EXP_NUGGET` |
| Conversion rate | **1 nugget = 3 xp** | Create `ExperienceNuggetItem#use`: `Mth.ceil(3f * amountUsed)` |
| Reverse conversion | `xp / 3`, **truncated**, remainder discarded | This mod; truncation guarantees no xp is ever created out of nothing |
| Dependency | **optional**, referenced by registry name only | No Create class is ever loaded when Create is absent — no `NoClassDefFoundError` risk |

`XP_CONVERSION_MODE` values:

| Value | Behaviour |
|---|---|
| `auto` (default) | Create loaded → nuggets; otherwise → bottles |
| `bottle` | Always bottles (even with Create) |
| `nugget` | Always nuggets; **falls back to bottles when Create is absent**, so xp never vanishes |
| `discard` | **Discard outright** — no item is produced at all |

**Live-server verification** (real dedicated server with Create 6.0.10 loaded; sweep driven over RCON, results read back from the save file):

| Config | XP in | Actual dustbin contents | Conclusion |
|---|---|---|---|
| `auto` | 300 | `create:experience_nugget` × **100** | 300/3, correct |
| `auto` | 100 | `create:experience_nugget` × **33** | truncated, remainder 1 discarded — **no xp duplication** |
| `discard` | 300 | **0 slots used** | discarded outright, no output |
| `bottle` | 100 | `minecraft:experience_bottle` × **10** | forcing bottles ignores Create |
| `nugget` | 100 | `create:experience_nugget` × **33** | forcing nuggets works |

Build entry point and the full RCON acceptance flow are documented in [`tools/README.md`](tools/README.md).

## Commands

| Command | Description | Permission |
|---|---|---|
| `/sweepermaid help` | Show all command descriptions | none |
| `/sweepermaid dustbin [index]` | Open the dustbin page (default page 0) | `PERMISSION_LEVEL_DUSTBIN` (default 0) |
| `/sweepermaid clean` | Run a sweep immediately | `PERMISSION_LEVEL_CLEAN` (default 2) |
| `/sweepermaid welcome on|off` | Toggle the fork welcome message | OP (level 2) |
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
| `SHOW_FORK_WELCOME` | true | Whether to send the fork welcome message on login; toggle with `welcome on/off` |
| `XP_PER_BOTTLE` | 10 | Experience points per bottle |
| `XP_CONVERSION_MODE` | auto | Conversion target: `auto` / `bottle` / `nugget` / `discard` |
| `DUSTBIN_NAME` | empty | Dustbin name prefix (empty = localized) |
| `EXTRA_ENTITY_TYPES` / `ITEM_WHITELIST` / `ITEM_BLACKLIST` | — | Extra entities / whitelist / blacklist |
| `MESSAGE_*` | empty | Message templates (empty = localized) |
| `PERMISSION_LEVEL_DUSTBIN` / `PERMISSION_LEVEL_CLEAN` | 0 / 2 | Command permission levels (the former also gates the after-sweep dustbin prompt, see below) |

### Permissions and the dustbin prompt (fixed in v2.1.2)

The post-sweep chat message ("Anything's missing? Let's check out the dustbin: `[Dustbin N]`") is now **only sent to players whose permission level reaches `PERMISSION_LEVEL_DUSTBIN`**.

- Players below that level receive **nothing at all** — neither the prompt text nor a dead link.
- To turn the dustbin prompt off completely, set `PERMISSION_LEVEL_DUSTBIN` to `4`; nobody is prompted then.
- The ActionBar sweep summary (how many dropped items were cleaned) is unaffected and still shows.

> **What this fixes**: in v2.1.0 and earlier the command was permission-checked but the prompt was not — raising the permission level still left players seeing a `[Dustbin 0]` link that **did nothing when clicked**. Both paths now share one rule.

## Known issues (to be addressed)

### The overload auto-sweep can interrupt in-progress machine logistics

`GLOBAL_ITEM_COUNT_THRESHOLD` (default 500) counts **all** dropped-item piles server-wide and triggers an extra sweep beyond it. The problem: it cannot tell "this is litter" from "this is an item a machine is currently moving".

- Measured evidence (2026-10, Create 6.0.10 GameTest environment): with sweeping off (`ITEM_SWEEP_INTERVAL=0`) 3/3 runs passed; with sweeping on, 2 of 3 runs failed with `Storage missing content` — Create's own storage test had its in-transit items swept away by this mod's overload sweep.
- Real-world impact: cobble generators, farms, conveyor belts and logistics pipes that briefly produce many dropped items can be mistaken for litter and cleared, **interrupting in-progress logistics**. Items end up in the dustbin rather than vanishing, but the machine flow is broken.
- There is currently no option to disable only the overload sweep while keeping the scheduled one.

**Workaround**: set `GLOBAL_ITEM_COUNT_THRESHOLD` to a huge value (e.g. `2147483647`) to disable overload triggering; the scheduled sweep is unaffected.

**Candidate fixes**: an exemption list for the overload sweep (machine intermediates excluded by default), or requiring operator confirmation.

## Development

```
gradlew runClient          # launch the client
gradlew runServer          # launch the dedicated server (set eula=true in run/server/eula.txt)
gradlew runGameTestServer  # run game tests (structure template: src/main/resources/data/sweeper_maid/structure/empty.nbt)
```

For the optional-client acceptance tooling, see [`tools/README.md`](tools/README.md): `launch_nomod_client.py` and
`launch_plain_client.py` start a NeoForge-only and a fully vanilla client respectively, to verify that players
without the mod can still join and open the dustbin.

## License

Same license as the original repository (see `LICENSE`). Original repo: [Viola-Siemens/Sweeper-Maid](https://github.com/Viola-Siemens/Sweeper-Maid)
