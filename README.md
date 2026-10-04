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
6. **经验实体收集**：清扫时把经验球折算成附魔之瓶存入垃圾箱（`XP_PER_BOTTLE` 默认 10，适度折损防刷经验），玩家可从垃圾箱取回。
7. **垃圾桶自动清理**：两种模式——**清空**（直接清掉所有桶）与**打包**（把桶内容压缩存盘后清空，可用指令导回），支持定时自动处理与指令手动触发。
8. **本地化**：内置 `en_us` 与 `zh_cn` 语言文件，消息类配置留空即跟随游戏语言。
9. **聊天快捷开箱**：清扫后聊天消息中的 `[垃圾箱 N]` 点击**直接打开**对应页垃圾箱（无需手动补全命令；`/sweepermaid dustbin <页>` 命令仍可用）。
10. **分支欢迎提示**：默认在玩家登录时提示这是 Yansunsky 分支版本，问题反馈请勿联系原作者；可用 `/sweepermaid welcome on|off`（OP）或配置项 `SHOW_FORK_WELCOME` 开关。
11. **大数量缩写显示**：垃圾箱槽位物品数量 ≥ 1000 时以 `1.2K`、`4.6M`、`1.5B` 形式显示；数量文本超出槽位宽度时自动缩小字号，确保不压到相邻槽位；鼠标悬停物品会显示信息，大堆叠物品额外显示确切数量（取走仍按精确数量计算）。
12. **配置双语注释**：所有配置项注释中英双语。
13. **游戏测试**：内置 GameTest 覆盖大容量序列化、翻页合并、排序、取走限制、打包导入等核心逻辑（`gradlew runGameTestServer`）。
14. **客户端可选（v2.1.0）**：**未安装本模组的玩家也能正常进服并使用垃圾箱**。服务端改用原版箱子菜单承载垃圾箱（不再注册自定义菜单类型），翻页状态走可选网络通道；装了模组的客户端会把它「接管」成带翻页控件的自研界面，没装的则看到原版 6×9 箱子界面（当前页可正常查看与取放，只是没有翻页按钮与数量缩写）。三种客户端（装了模组 / 只装 NeoForge / 纯原版）均已实机验证。

> **为什么要点 14**：以前未装模组的客户端**连服都会被踢**（自定义 `MenuType` 解析失败、非可选网络负载握手失败），装了 NeoForge 但没装本模组的客户端尤其容易中招。现在这些限制全部移除。

## 命令

| 命令 | 说明 | 权限 |
|---|---|---|
| `/sweepermaid help` | 显示所有指令说明 | 无 |
| `/sweepermaid dustbin [index]` | 打开指定页垃圾箱（默认第 0 页） | `PERMISSION_LEVEL_DUSTBIN`（默认 0） |
| `/sweepermaid clean` | 立即执行一次清扫 | `PERMISSION_LEVEL_CLEAN`（默认 2） |
| `/sweepermaid welcome on|off` | 开关玩家登录时的分支声明 | OP（2 级） |
| `/sweepermaid trash` | 立即按配置模式处理垃圾桶（清空或打包） | OP（2 级） |
| `/sweepermaid pack` | 立即将垃圾桶内容打包为压缩文件 | OP（2 级） |
| `/sweepermaid unpack <文件>` | 导入打包文件并合并进垃圾桶（Tab 补全文件名） | OP（2 级） |

打包文件保存在存档目录的 `sweeper_maid_packs/` 下（`pack-<时间戳>.nbt.gz`，gzip 压缩 NBT）；导入成功后文件自动移入 `imported/` 子文件夹。

## 配置（`config/sweeper_maid-common.toml`）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ITEM_SWEEP_INTERVAL` | 600 | 定时清扫间隔（秒），0 禁用 |
| `ITEM_OVERLOAD_THRESHOLD` | 640 | 区块掉落物过载警告阈值（仅警告） |
| `GLOBAL_ITEM_COUNT_THRESHOLD` | 500 | 全局掉落物堆数阈值，超过则提前额外清扫 |
| `OVERLOAD_SWEEP_COOLDOWN` | 60 | 过载清扫冷却（秒） |
| `DUSTBIN_SLOT_CAPACITY` | 1000000 | 垃圾箱单格堆叠上限 |
| `DUSTBIN_TRASH_MODE` | clear | 垃圾桶自动处理模式：`clear` 清空 / `pack` 打包 |
| `DUSTBIN_TRASH_INTERVAL` | -1 | 垃圾桶自动处理间隔（秒），-1 禁用 |
| `SHOW_FORK_WELCOME` | true | 是否在玩家登录时发送分支声明，可用 `welcome on/off` 动态修改 |
| `XP_PER_BOTTLE` | 10 | 每多少点经验折算 1 个附魔之瓶 |
| `DUSTBIN_NAME` | 空 | 垃圾箱名称前缀（空=跟随语言） |
| `EXTRA_ENTITY_TYPES` / `ITEM_WHITELIST` / `ITEM_BLACKLIST` | — | 额外清除实体 / 白名单 / 黑名单 |
| `MESSAGE_*` | 空 | 各类提示消息模板（空=跟随语言） |
| `PERMISSION_LEVEL_DUSTBIN` / `PERMISSION_LEVEL_CLEAN` | 0 / 2 | 命令权限等级 |

## 开发

```
gradlew runClient          # 启动客户端
gradlew runServer          # 启动专用服务器（需 run/server/eula.txt 中 eula=true）
gradlew runGameTestServer  # 运行游戏测试（结构模板：src/main/resources/data/sweeper_maid/structure/empty.nbt）
```

客户端可选用例的自动化验收工具见 [`tools/README.md`](tools/README.md)：其中的 `launch_nomod_client.py` 与
`launch_plain_client.py` 可分别拉起「只装 NeoForge」与「纯原版」客户端，用于验证未装模组的玩家能否正常进服开箱。

## 许可

沿用原仓库许可（见 `LICENSE`）。原始仓库：[Viola-Siemens/Sweeper-Maid](https://github.com/Viola-Siemens/Sweeper-Maid)
