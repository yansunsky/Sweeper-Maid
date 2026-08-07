# Sweeper-Maid（1.20.1 Forge 分支）

一个用于清理服务器掉落物与多余实体的 Minecraft 模组。

> **分支说明**：本项目是 **[刘冬煜（liudongyu）原作 Sweeper-Maid](https://github.com/Viola-Siemens/Sweeper-Maid)** 的修改分支，并非原创，感谢原作者的开源贡献。

> **⚠️ 维护状态（重要）**：当前分支是 **1.20.1 Forge 移植版**，属于**随手移植（quick port）**——由 1.21.1 NeoForge 分支移植而来，**未经充分测试**，作者**不太想继续维护**。使用风险自负，建议优先使用功能完整、经过测试的 [1.21.1 NeoForge 分支](../../tree/dev/NeoForge-1.21.1)。

## 移植功能（与 1.21.1 分支对齐）

1. **垃圾箱翻页界面**：GUI 顶部「上一页 / 页码 / 下一页」控件，悬停有提示，点击直接翻页（界面不关闭、鼠标不重置）。
2. **动态页数**：第 1 页放满 54 种物品才启用第 2 页，空页自动回收。
3. **大容量堆叠**：每格堆叠上限由配置项 `DUSTBIN_SLOT_CAPACITY` 控制（默认 1,000,000）。
4. **清扫后全局排序**：按物品数量从大到小重排再打包。
5. **全局掉落物过载自动清扫**：实时统计掉落物堆数，超阈值提前清扫（默认 500 堆，60 秒冷却）。
6. **经验实体收集**：清扫时把经验球折算成附魔之瓶存入垃圾箱（`XP_PER_BOTTLE` 默认 10）。
7. **本地化**：内置 `en_us` / `zh_cn` 语言文件，消息类配置留空即跟随游戏语言。
8. **聊天快捷开箱**：点击清扫消息中的 `[垃圾箱 N]` 直接打开对应页（1.20.1 客户端会弹一次命令确认，属原版安全机制）。
9. **分支欢迎提示**：玩家登录时提示这是 Yansunsky 分支版本。

## 已知问题（1.20.1 分支）

- **未经充分测试**：GameTest 在本分支未完整跑通（Gradle run 任务受 ForgeGradle/Gradle 兼容性影响，推荐用 IDE 的 Gradle 面板执行 runServer/runClient）。
- **大容量堆叠可能未完全生效**：清扫后的同种物品存在按 64 分格的情况，尚未修复。
- 需要 **Java 17** 构建与运行（ForgeGradle 6 不兼容 Java 21）。
- 构建首次需要下载 Forge 1.20.1-47.2.0 依赖，若官方源下载失败可尝试在 `build.gradle` 的仓库列表中加 BMCLAPI 镜像（`https://bmclapi2.bangbang93.com/maven/`）。

## 命令

| 命令 | 说明 | 权限 |
|---|---|---|
| `/sweepermaid dustbin [index]` | 打开指定页垃圾箱（默认第 0 页） | `PERMISSION_LEVEL_DUSTBIN`（默认 0） |
| `/sweepermaid clean` | 立即执行一次清扫 | `PERMISSION_LEVEL_CLEAN`（默认 2） |

## 配置（`config/sweeper_maid-common.toml`）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ITEM_SWEEP_INTERVAL` | 600 | 定时清扫间隔（秒），0 禁用 |
| `ITEM_OVERLOAD_THRESHOLD` | 640 | 区块掉落物过载警告阈值（仅警告） |
| `GLOBAL_ITEM_COUNT_THRESHOLD` | 500 | 全局掉落物堆数阈值，超过则提前额外清扫 |
| `OVERLOAD_SWEEP_COOLDOWN` | 60 | 过载清扫冷却（秒） |
| `DUSTBIN_SLOT_CAPACITY` | 1000000 | 垃圾箱单格堆叠上限 |
| `XP_PER_BOTTLE` | 10 | 每多少点经验折算 1 个附魔之瓶 |
| `DUSTBIN_NAME` | 空 | 垃圾箱名称前缀（空=跟随语言） |
| `EXTRA_ENTITY_TYPES` / `ITEM_WHITELIST` / `ITEM_BLACKLIST` | — | 额外清除实体 / 白名单 / 黑名单 |
| `MESSAGE_*` | 空 | 各类提示消息模板（空=跟随语言） |
| `PERMISSION_LEVEL_DUSTBIN` / `PERMISSION_LEVEL_CLEAN` | 0 / 2 | 命令权限等级 |

## 开发

```
gradlew runClient     # 启动客户端（需 Java 17）
gradlew runServer     # 启动专用服务器（需 run/server/eula.txt 中 eula=true）
```

若 `gradlew runServer` 提示任务不存在，请在 IDE 的 Gradle 面板（Tasks → runs → runServer）中执行。

## 许可

沿用原仓库许可（见 `LICENSE`）。原始仓库：[Viola-Siemens/Sweeper-Maid](https://github.com/Viola-Siemens/Sweeper-Maid)
