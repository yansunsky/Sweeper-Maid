"""Sweeper-Maid 实机验收：驱动服务端，验证三种客户端类型下的垃圾箱行为。

关键设计：全程**不需要客户端输入注入**——
用 RCON 的 `execute as <player> run sweepermaid dustbin <i>` 从服务端触发开界面，
客户端只要连上服务器就会真实走一遍「收到原版 OpenScreen -> 收到状态包 -> 接管界面」的完整路径。
"""

import sys
import time

sys.path.insert(0, __file__.rsplit("\\", 1)[0])
from rcon_cli import Rcon  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

HOST, PORT, PASSWORD = "127.0.0.1", 25595, "sweeptest"
PLAYER = "Dev"


def connect() -> Rcon:
    return Rcon(HOST, PORT, PASSWORD)


def seed_dustbin(rcon: Rcon, pages: int = 3) -> None:
    """造出足够多的页。

    关键点：`sortDustbins()` 会把**同种物品合并**后按每页 54 格重新打包，
    所以「多页」的前提是**至少 55 种不同的物品**。一页 54 格，前 54 种占满第 0 页，
    第 55 种才会开第 1 页喵~。
    """
    rcon.cmd("sweepermaid trash")
    print("[seed] 已清空垃圾桶")

    needed = pages * 54
    if len(DISTINCT_ITEMS) < needed:
        print(
            "[seed][warn] 不同物品只有 %d 种，不足以填满 %d 页；实际最多 %d 页"
            % (len(DISTINCT_ITEMS), pages, len(DISTINCT_ITEMS) // 54 + 1)
        )

    for index, item in enumerate(DISTINCT_ITEMS[:needed]):
        # 每个物品给一个**互不相同**的数量，便于回读时逐项核对
        count = 1000 + index
        nbt = (
            '{Item:{id:"minecraft:%s",count:%d},PickupDelay:32767,Age:-32768}'
            % (item, count)
        )
        rcon.cmd(
            "execute as %s run summon minecraft:item 6.5 70.0 6.5 %s" % (PLAYER, nbt)
        )
    print("[seed] 已生成 %d 个掉落物" % len(DISTINCT_ITEMS[:needed]))

    rcon.cmd("sweepermaid clean")
    print("[seed] 已执行清扫（会触发 sortDustbins 全局重排）")
    time.sleep(1)
    rcon.cmd("save-all flush")
    print("[seed] 已落盘")


# 1.21.1 的物品清单：取足够多**不同**的物品，保证能撑出多页喵~
DISTINCT_ITEMS = [
    # 建材 / 方块（数量充足）
    "stone", "cobblestone", "dirt", "coarse_dirt", "rooted_dirt", "grass_block",
    "sand", "red_sand", "gravel", "clay", "sandstone", "red_sandstone",
    "oak_log", "spruce_log", "birch_log", "jungle_log", "acacia_log", "dark_oak_log",
    "mangrove_log", "cherry_log", "oak_planks", "spruce_planks", "birch_planks",
    "jungle_planks", "acacia_planks", "dark_oak_planks", "mangrove_planks",
    "cherry_planks", "bamboo_planks", "crimson_planks", "warped_planks",
    "glass", "sandstone_stairs", "brick", "stone_bricks", "mossy_stone_bricks",
    "cracked_stone_bricks", "chiseled_stone_bricks", "deepslate", "cobbled_deepslate",
    "calcite", "tuff", "dripstone_block", "andesite", "diorite", "granite",
    "polished_andesite", "polished_diorite", "polished_granite", "obsidian",
    "netherrack", "soul_sand", "soul_soil", "basalt", "blackstone", "end_stone",
    "purpur_block", "prismarine", "dark_prismarine", "sea_lantern", "glowstone",
    "magma_block", "nether_bricks", "red_nether_bricks", "quartz_block",
    # 矿物 / 材料
    "coal", "charcoal", "iron_ingot", "copper_ingot", "gold_ingot", "diamond",
    "emerald", "lapis_lazuli", "redstone", "quartz", "netherite_scrap",
    "raw_iron", "raw_copper", "raw_gold", "iron_nugget", "gold_nugget",
    "flint", "stick", "bowl", "string", "feather", "leather", "rabbit_hide",
    "paper", "book", "sugar", "gunpowder", "glowstone_dust", "blaze_powder",
    "slime_ball", "magma_cream", "ender_pearl", "ghast_tear", "prismarine_shard",
    "prismarine_crystals", "nautilus_shell", "heart_of_the_sea", "honeycomb",
    "clay_ball", "nether_wart", "wheat",
    "wheat_seeds", "beetroot_seeds", "melon_seeds", "pumpkin_seeds",
    "carrot", "potato", "beetroot", "melon_slice", "pumpkin", "sugar_cane",
    "bamboo", "cactus", "kelp", "dried_kelp", "apple", "sweet_berries",
    "glow_berries", "egg", "honey_bottle",
    # 杂项（凑数到 >162）
    "torch", "lantern", "soul_lantern", "chain", "iron_bars", "ladder",
    "scaffolding", "bucket", "lava_bucket", "milk_bucket",
    "compass", "clock", "spyglass", "brush", "lead", "name_tag", "saddle",
    "arrow", "spectral_arrow", "tipped_arrow", "bone", "bone_meal",
    "ink_sac", "glow_ink_sac", "cocoa_beans", "hay_block",
    "snowball", "ice", "packed_ice", "blue_ice", "moss_block", "vine",
    "lily_pad", "sponge", "wet_sponge", "cobweb", "pointed_dripstone",
    "amethyst_shard", "echo_shard", "disc_fragment_5", "goat_horn",
    "sculk", "sculk_vein", "moss_carpet", "azalea", "flowering_azalea",
    "azalea_leaves", "mangrove_roots", "mud",
    "packed_mud", "muddy_mangrove_roots", "reinforced_deepslate",
]


def main() -> int:
    rcon = connect()
    try:
        print("[ok] RCON 已连接")
        print("[list]", rcon.cmd("list").strip())
        seed = "--seed" in sys.argv
        if seed:
            seed_dustbin(rcon)
        print("[pages]", rcon.cmd("sweepermaid help").strip().splitlines()[0])
    finally:
        rcon.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
