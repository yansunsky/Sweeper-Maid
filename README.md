# Sweeper-Maid

一个用于清理服务器掉落物与多余实体的 Minecraft 模组（NeoForge 1.21.1 / Java 21）。

> **分支说明**：本项目是 **[刘冬煜（liudongyu）原作 Sweeper-Maid](https://github.com/Viola-Siemens/Sweeper-Maid)** 的修改分支，并非原创。在原作基础上增加了下述功能与优化，感谢原作者的开源贡献。

---

## 与原作的区别（本分支改动）

1. **垃圾箱翻页界面**：GUI 顶部增加「上一页 / 页码 / 下一页」控件，悬停有提示，点击直接翻页（界面不关闭、鼠标不重置）。
2. **动态页数**：总页数动态生成，第 1 页放满 54 种物品才启用第 2 页，物品被取空后自动回收空页。
3. **超大堆叠**：每格堆叠上限由原版 64 改为配置项 `DUSTBIN_SLOT_CAPACITY`（默认 1,000,000），一格一种物品，数量可一直累加。
4. **清扫后全局排序**：每次清扫结束后，按物品数量从大到小全局重排，再重新打包页面。
5. **全局掉落物过载自动清扫**：实时统计所有维度的掉落物堆数（每 tick 判断、5 秒校正一次），超过阈值自动提前清扫，防止服务器卡顿。
6. **本地化**：内置 `en_us` 与 `zh_cn` 语言文件，消息类配置留空即跟随游戏语言。
7. **聊天快捷开箱**：清扫后聊天消息中的 `[垃圾箱 N]` 点击**直接打开**对应页垃圾箱（无需手动补全命令；`/sweepermaid dustbin <页>` 命令仍可用）。
8. **配置双语注释**：所有配置项注释中英双语。
9. **游戏测试**：内置 GameTest 覆盖大容量序列化、翻页合并、排序、取走限制等核心逻辑（`gradlew runGameTestServer`）。

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
| `DUSTBIN_NAME` | 空 | 垃圾箱名称前缀（空=跟随语言） |
| `EXTRA_ENTITY_TYPES` / `ITEM_WHITELIST` / `ITEM_BLACKLIST` | — | 额外清除实体 / 白名单 / 黑名单 |
| `MESSAGE_*` | 空 | 各类提示消息模板（空=跟随语言） |
| `PERMISSION_LEVEL_DUSTBIN` / `PERMISSION_LEVEL_CLEAN` | 0 / 2 | 命令权限等级 |

## 开发

```
gradlew runClient          # 启动客户端
gradlew runServer          # 启动专用服务器（需 run/server/eula.txt 中 eula=true）
gradlew runGameTestServer  # 运行游戏测试（需 run/testServer/gameteststructures/empty.snbt）
```

## 许可

沿用原仓库许可（见 `LICENSE`）。原始仓库：[Viola-Siemens/Sweeper-Maid](https://github.com/Viola-Siemens/Sweeper-Maid)
