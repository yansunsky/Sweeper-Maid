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
6. **经验实体收集**：清扫时把经验球折算成物品存入垃圾箱，玩家可从垃圾箱取回。折算目标由 `XP_CONVERSION_MODE` 决定（见第 15 条）；未装机械动力时折算成附魔之瓶（`XP_PER_BOTTLE` 默认 10，适度折损防刷经验）。
7. **垃圾桶自动清理**：两种模式——**清空**（直接清掉所有桶）与**打包**（把桶内容压缩存盘后清空，可用指令导回），支持定时自动处理与指令手动触发。
8. **本地化**：内置 `en_us` 与 `zh_cn` 语言文件，消息类配置留空即跟随游戏语言。
9. **聊天快捷开箱**：清扫后聊天消息中的 `[垃圾箱 N]` 点击**直接打开**对应页垃圾箱（无需手动补全命令；`/sweepermaid dustbin <页>` 命令仍可用）。
10. **分支欢迎提示**：默认在玩家登录时提示这是 Yansunsky 分支版本，问题反馈请勿联系原作者；可用 `/sweepermaid welcome on|off`（OP）或配置项 `SHOW_FORK_WELCOME` 开关。
11. **大数量缩写显示**：垃圾箱槽位物品数量 ≥ 1000 时以 `1.2K`、`4.6M`、`1.5B` 形式显示；数量文本超出槽位宽度时自动缩小字号，确保不压到相邻槽位；鼠标悬停物品会显示信息，大堆叠物品额外显示确切数量（取走仍按精确数量计算）。
12. **配置双语注释**：所有配置项注释中英双语。
13. **游戏测试**：内置 GameTest 覆盖大容量序列化、翻页合并、排序、取走限制、打包导入等核心逻辑（`gradlew runGameTestServer`）。未装机械动力 21/21 通过，装了机械动力 85/85 通过。
14. **客户端可选（v2.1.0）**：**未安装本模组的玩家也能正常进服并使用垃圾箱**。服务端改用原版箱子菜单承载垃圾箱（不再注册自定义菜单类型），翻页状态走可选网络通道；装了模组的客户端会把它「接管」成带翻页控件的自研界面，没装的则看到原版 6×9 箱子界面（当前页可正常查看与取放，只是没有翻页按钮与数量缩写）。三种客户端（装了模组 / 只装 NeoForge / 纯原版）均已实机验证。
15. **机械动力（Create）联动（可选依赖）**：装了机械动力时，清扫收集到的经验折算成**经验颗粒**（`create:experience_nugget`，官方译名「经验颗粒」）；没装则照旧折算成附魔之瓶。由 `XP_CONVERSION_MODE` 控制，可选 `auto`（默认）/`bottle`/`nugget`/`discard`。
16. **垃圾箱提示跟随权限（v2.1.2）**：清扫后的聊天提示与 `[垃圾箱 N]` 链接只发给权限达到 `PERMISSION_LEVEL_DUSTBIN` 的玩家；调高权限后不再出现「点了没反应的死链接」，设为 4 即彻底关闭提示。

> **为什么要点 14**：以前未装模组的客户端**连服都会被踢**（自定义 `MenuType` 解析失败、非可选网络负载握手失败），装了 NeoForge 但没装本模组的客户端尤其容易中招。现在这些限制全部移除。

### 机械动力联动细节（第 15 条）

| 项目 | 值 | 出处 |
|---|---|---|
| 物品 ID | `create:experience_nugget`（经验颗粒） | Create `AllItems.EXP_NUGGET` |
| 换算率 | **1 颗粒 = 3 点经验** | Create `ExperienceNuggetItem#use`：`Mth.ceil(3f * amountUsed)` |
| 反向折算 | `经验 / 3` **向下取整**，余数丢弃 | 本模组；向下取整保证绝不凭空刷经验 |
| 依赖方式 | **可选依赖**，纯注册名软引用 | 未装 Create 时不会加载任何 Create 的类，无 `NoClassDefFoundError` 风险 |

`XP_CONVERSION_MODE` 四种取值：

| 取值 | 行为 |
|---|---|
| `auto`（默认） | 装了 Create → 经验颗粒；没装 → 附魔之瓶 |
| `bottle` | 始终附魔之瓶（即使装了 Create） |
| `nugget` | 始终经验颗粒；**未装 Create 时自动退回附魔之瓶**，不让经验凭空消失 |
| `discard` | **直接清除**，不产出任何物品（适合不想要经验产出的服） |

**实机验收结果**（真实服务端 + 机械动力 6.0.10 已加载，经 RCON 触发清扫并读存档核对）：

| 配置 | 投入经验 | 垃圾桶实际内容 | 结论 |
|---|---|---|---|
| `auto` | 300 | `create:experience_nugget` × **100** | 300/3，正确 |
| `auto` | 100 | `create:experience_nugget` × **33** | 向下取整、余 1 点丢弃，**不刷经验** |
| `discard` | 300 | **0 格有物** | 直接清除，无任何产出 |
| `bottle` | 100 | `minecraft:experience_bottle` × **10** | 服主强制瓶子时忽略 Create |
| `nugget` | 100 | `create:experience_nugget` × **33** | 强制颗粒生效 |

自动化测试：未装 Create 时 **19/19** 通过；装了 Create 时 **83/83** 通过（含机械动力自带的 64 个测试）。
联调步骤与构建入口见 [`tools/README.md`](tools/README.md)。

> **一个实现要点**：`BuiltInRegistries.ITEM` 是 `DefaultedRegistry`，查不到的键 `get()` 返回的是 `Items.AIR` 而**不是** `null`。若照抄 `get()`，未装 Create 时会「成功」拿到空气并塞进垃圾箱。本模组因此用 `getOptional()` 并额外排除 `AIR`，且由 GameTest 钉死这条约束。

## 命令

| 命令 | 说明 | 权限 |
|---|---|---|
| `/sweepermaid help` | 显示所有指令说明 | 无 |
| `/sweepermaid dustbin [index]` | 打开指定页垃圾箱（默认第 0 页） | `PERMISSION_LEVEL_DUSTBIN`（默认 0） || `/sweepermaid clean` | 立即执行一次清扫 | `PERMISSION_LEVEL_CLEAN`（默认 2） |
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
| `XP_CONVERSION_MODE` | auto | 经验折算目标：`auto` / `bottle` / `nugget` / `discard` |
| `DUSTBIN_NAME` | 空 | 垃圾箱名称前缀（空=跟随语言） |
| `EXTRA_ENTITY_TYPES` / `ITEM_WHITELIST` / `ITEM_BLACKLIST` | — | 额外清除实体 / 白名单 / 黑名单 |
| `MESSAGE_*` | 空 | 各类提示消息模板（空=跟随语言） |
| `PERMISSION_LEVEL_DUSTBIN` / `PERMISSION_LEVEL_CLEAN` | 0 / 2 | 命令权限等级（前者同时决定清扫后的垃圾箱提示，见下） |

### 权限与垃圾箱提示（v2.1.2 修复）

清扫后聊天栏的「有什么东西丢了吗？去垃圾箱里看看吧：`[垃圾箱 N]`」消息，**只发给权限等级达到 `PERMISSION_LEVEL_DUSTBIN` 的玩家**。

- 权限不足的玩家**完全收不到**这条消息（既不显示提示文字，也不显示点不动的死链接）。
- 想**彻底关掉**垃圾箱提示：把 `PERMISSION_LEVEL_DUSTBIN` 设为 `4`（最高级），此时不再向任何人提示。
- ActionBar 上的清扫统计（清理了多少掉落物等）不受影响，仍会正常显示。

> **修复了什么问题**：v2.1.0 及更早版本里，命令端有权限校验、而提示端没有——把权限调高后，玩家虽然打不开垃圾箱，却仍会看到 `[垃圾箱 0]` 链接，**点了毫无反应**。现在两端同源。

## 已知问题（待处理）

### 过载自动清扫会打断进行中的机器物流

`GLOBAL_ITEM_COUNT_THRESHOLD`（默认 500）统计的是**全服掉落物堆数**，超过即触发一次额外清扫。问题在于它**分辨不出**「这是垃圾」还是「这是机器正在搬运的物品」：

- 实测证据（2026-10，机械动力 6.0.10 的 GameTest 环境）：关闭清扫（`ITEM_SWEEP_INTERVAL=0`）时连跑 3 次**全过**；开启清扫时 3 次里**失败 2 次**，报 `Storage missing content` —— 机械动力自己的存储测试正在运输的物品被本模组的过载清扫扫走了。
- 现实影响：刷石机、农场、传送带、物流管道等短时间产生大量掉落物的场景，都可能被误判为「垃圾」而清走，**打断进行中的物流**（物品会进垃圾箱，不会凭空消失，但机器流程会中断）。
- 目前没有配置项能只关过载清扫而保留定时清扫。

**临时规避**：把 `GLOBAL_ITEM_COUNT_THRESHOLD` 设为极大值（如 `2147483647`）即可禁用过载触发，定时清扫不受影响。

**待定方案**：为过载清扫增加物品豁免名单（机器中间产物默认不清扫），或改为需管理员确认。

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
