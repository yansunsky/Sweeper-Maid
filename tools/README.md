# 验收工具（tools/）

这套工具用来验证本项目最核心的一条验收标准：

> **服务端装了本模组，客户端可以不装** —— 未装模组的玩家照样能进服、开垃圾桶、取放物品。

其中「装了 NeoForge 但没装本模组」与「纯原版」这两种客户端**必须真的跑一次**，
因为它们的差异只体现在客户端侧（菜单类型解析、界面渲染），GameTest 跑在服务端**测不到**。

## 快速上手

```powershell
# 0) 前置：服务端开启 RCON（首次）
#    run/server/server.properties: enable-rcon=true, rcon.password=<你的密码>
#    本仓库开发默认用 25595（避开其它项目占用的 25575）

# 1) 起服务端（独立端口 25585/25595，避免与其它项目抢 25565）
gradlew.bat runServer --console=plain --no-daemon

# 2) 造出多页垃圾桶（翻页功能需要 >54 种不同物品）
python tools/accept_dustbin.py --seed

# 3) 起「装了模组」的客户端（自动连服）
gradlew.bat runClient -Pquickplay=127.0.0.1:25585

# 4) 起「装了 NeoForge 但没装本模组」的客户端
#    先从运行中的 dev 客户端派生原始命令行，再剔除本模组的 classpath 入口
python tools/launch_nomod_client.py <模组客户端PID> 9877

# 5) 起「纯原版 1.21.1」客户端
python tools/launch_plain_client.py vanilla 25585 Plain
```

## 各脚本职责

| 脚本 | 用途 |
|---|---|
| `rcon_cli.py` | 标准库实现的 RCON 长连接客户端，驱动服务端命令 |
| `mcp_api.py` | HTTP 直连 mcpmod（`minecraft-mod-mcp`），读客户端界面状态、注入输入。支持 `--port` 与 `release-mouse` |
| `shot_window.py` | OS 层 `PrintWindow` 截窗口；支持按 PID 定位、自动还原最小化窗口 |
| `launch_nomod_client.py` | 从运行中的 dev 客户端**派生**一个不加载本模组的客户端（见下） |
| `launch_plain_client.py` | 直接以 `net.minecraft.client.main.Main` 启动纯原版 / 仅 NeoForge 客户端 |
| `accept_dustbin.py` | 造多页垃圾桶内容（清扫 → `sortDustbins` 全局重排） |
| `inspect_saveddata.py` | 解析 `SweeperMaid-SavedData.dat`（gzip + 极简 NBT），核对页数与每页物品 |

## 为什么 `launch_nomod_client.py` 要「派生」而不是新建一个 run

NeoGradle 7.1 只认 `client` / `server` / `gameTestServer` / `data` 这几种内置 run 类型，
自定义 `clientNoMod { client() ... }` 会报 `The run type 'clientNoMod' was not found`。
（`RunSourceSets` 只有 `add`，没有移除 API，也无法在 `configureEach` 之后清空 `modSource`。）

所以改为**从正在运行的模组客户端命令行派生**：把 classpath 里的
`build/classes/java/main` 与 `build/resources/main` 两项剔除——FML 正是靠这两项发现本模组的。
其余一切（同一个 NeoForge、同一个 JDK、同一份 vanilla jar）完全不变，
于是这是一个**严格的单变量实验**：两个客户端之间任何行为差异，只可能来自本模组本身。

## 踩过的坑（改脚本前先看这里）

1. **PowerShell 会把中文路径变成 `??????`**
   项目路径含「工作目录」。读命令行时必须让 PowerShell 用 UTF-8 输出
   （`[Console]::OutputEncoding=[System.Text.Encoding]::UTF8`）并按 UTF-8 解码，
   否则 JVM 报 `InvalidPathException: Illegal char <?>`。

2. **`Out-File -Encoding utf8` 会写 BOM**
   读回命令行文件要用 `utf-8-sig`，否则第一个参数（java.exe 路径）前面多个 `\ufeff`，
   `CreateProcess` 报「系统找不到指定的文件」。

3. **`-cp` 与其值必须是两个独立参数**
   拼成 `-cp<value>` 会被 JVM 当成未知选项（`Unrecognized option: -cpD:\...`）。

4. **两个客户端不能同时用同名账号**
   都用 `Dev` 会触发 `multiplayer.disconnect.duplicate_login`。
   派生客户端因此换成 `--username Plain`，并且**同一时间只让一个客户端在线**。

5. **纯原版客户端必须关掉多人游戏警告**
   `run-vanilla/options.txt` 里 `skipMultiplayerWarning:true`，否则 `--quickPlayMultiplayer`
   会被警告界面挡住，永远连不上（表现为「客户端起来了但 `list` 里没人」）。

6. **`pauseOnLostFocus` 必须为 `false`**
   脚本环境窗口必然失焦，否则游戏立刻弹暂停界面，之后所有输入注入都打在暂停界面上，
   表现为「右键毫无反应」而服务端一切正常。该文件只在启动时读，**改完必须重启客户端**。

7. **远程会话里窗口常被最小化**
   截到的是 159×27 的标题栏。`shot_window.py` 会先 `ShowWindow(SW_RESTORE)` 再截。

8. **mcpmod 的 `/api/cmd` 一律 HTTP 200**
   成功/失败写在返回体的 `error` 字段里，只看状态码会把失败当成功。
   `click` 收的是**物理像素**，返回值里的 `gui` 字段给出换算后的 GUI 坐标（本机 854×480 时缩放为 1）。

9. **`open_chat` + `type_text` 发聊天命令不可靠**
   实测 `press_key enter` 不会提交 `/connect`。连服一律走启动参数 `--quickPlayMultiplayer`。

## 已验证的结论（2026-10 实机）

| 客户端类型 | 进服 | 打开垃圾桶 | 界面 | 翻页 |
|---|---|---|---|---|
| 装了本模组 | ✅ | ✅ | `SMDustbinScreen`（自绘翻页控件） | ✅ 真实点击可用 |
| 装了 NeoForge、没装本模组 | ✅ | ✅ | 原版 `ContainerScreen`，标题「垃圾箱 N」 | ❌ 无按钮 |
| 纯原版 1.21.1 | ✅ | ✅ | 原版 `ContainerScreen`，标题「垃圾箱 N」 | ❌ 无按钮 |

三种客户端都能正常取放物品，且服务端**零异常**。
未装模组的客户端聊天消息与容器标题均按**它自己的语言**显示，
不再出现 `sweeper_maid.message.xxx` 这样的裸翻译键。

---

## 构建入口：`tools/gw.ps1`（本机环境适配）

直接用 `gradlew.bat` 在本机**跑不通**，`gw.ps1` 把这些坑都封好了。它解决的三个问题：

| # | 现象 | 原因 | 处理 |
|---|---|---|---|
| 1 | 创建 `gradle-9.2.1-bin.zip.lck` 被拒 | DSH 沙箱只允许写工作区与少量临时目录，Gradle 默认要写 `~/.gradle` | `GRADLE_USER_HOME` 指向临时目录；`GRADLE_RO_DEP_CACHE` **只读**复用 `~/.gradle/caches` |
| 2 | `ClassNotFoundException: GradleWorkerMain` | Gradle 用 UTF-8 写 worker 类路径 argfile，JDK 启动器按系统 ANSI 代码页（本机 936/GBK）解析它 → 中文路径「工作目录」被解码成乱码 | `GRADLE_USER_HOME` 与构建目录都放在**纯 ASCII 路径**下 |
| 3 | 下载全部 `Connection refused` | 全局 `gradle.properties` 写死 Clash 代理 `127.0.0.1:7897`，代理没开时连直连也被带崩 | 脚本**探测端口**：代理在监听才走代理，否则直连（本机直连实测可用） |

另：工作区里 `.gradle` / `src` / `run` / `docs` / `config-examples` 这几个**改动前就存在**的子目录
缺少沙箱写入授权项（同级的 `build` / `tools` 有，**新建**目录也有），
所以脚本默认在 ASCII 临时目录里做**镜像构建**（把 `src` 同步过去），产物再拷回 `build/libs`。
加 `-InPlace` 可强制原地构建。

```powershell
.\tools\gw.ps1 build                 # 构建（镜像模式，推荐）
.\tools\gw.ps1 runGameTestServer     # 跑游戏测试
.\tools\gw.ps1 runServer             # 起专用服务端
.\tools\gw.ps1 build -InPlace        # 原地构建
```

## 机械动力联动的验收流程（2026-10 实机）

经验折算的目标物由 `XP_CONVERSION_MODE` 决定（`auto` / `bottle` / `nugget` / `discard`）。
要**真的**验证联动生效，必须让服务端加载机械动力，光跑单测不够。

**准备**：把 Create 及其前置丢进 `run/server/mods/`：

| Jar | 说明 |
|---|---|
| `create-1.21.1-6.0.10-280-slim.jar` | 本体（slim 变体已够） |
| `flywheel-neoforge-1.21.1-1.0.6.jar` | 客户端渲染前置（服务端也要求存在） |
| `ponder-neoforge-1.0.87+mc1.21.1.jar` | Ponder 前置 |
| `Registrate-MC1.21-1.3.0+67.jar` | Registrate 前置 |

`run/server/server.properties` 里开 RCON（用独立端口，避开其它项目）：

```properties
enable-rcon=true
rcon.password=sweeptest
rcon.port=25596
server-port=25586
```

**验收步骤**（`Rcon` 即 `python tools/rcon_cli.py 127.0.0.1 25596 sweeptest <命令>`）：

```powershell
Rcon 'sweepermaid trash'                                    # 清空垃圾桶
Rcon 'kill @e[type=minecraft:experience_orb]'               # 清掉残留经验球
Rcon 'execute in minecraft:overworld run summon minecraft:experience_orb 0 5 0 {Value:300}'
Rcon 'sweepermaid clean'                                    # 触发清扫
Rcon 'save-all flush'                                       # SavedData 默认不立刻落盘，必须存一次
python tools\inspect_saveddata.py run\server\world\data\SweeperMaid-SavedData.dat
```

**实测结果**（1 颗经验颗粒 = 3 点经验，取自 Create `ExperienceNuggetItem#use`）：

| 配置 | 投入经验 | 垃圾桶实际内容 | 结论 |
|---|---|---|---|
| `auto`（装了 Create） | 300 | `create:experience_nugget` × **100** | 300/3，正确 |
| `auto` | 100 | `create:experience_nugget` × **33** | 向下取整，余 1 点丢弃，**不刷经验** |
| `discard` | 300 | **0 格有物** | 直接清除，无任何产出 |
| `bottle` | 100 | `minecraft:experience_bottle` × **10** | 服主强制瓶子时忽略 Create，向上取整 |
| `nugget` | 100 | `create:experience_nugget` × **33** | 强制颗粒生效 |

> **注意**：`save-all flush` 不能省。`SMSavedData` 由原版 `SavedData` 管理，只在存档周期或关服时才落盘，
> 不 flush 的话 `.dat` 还是旧的，会误判成「清扫没生效」。
