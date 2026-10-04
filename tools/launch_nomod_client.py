"""从**正在运行的**模组客户端命令行派生出一个「没装本模组」的客户端并启动。

原理（单变量实验）：
  开发环境下 FML 通过 classpath 上的 `build/classes/java/main` + `build/resources/main`
  发现本模组。把这两项从 classpath 里剔除，就得到「装了 NeoForge、但没装 Sweeper-Maid」
  的客户端——**其它一切完全不变**（同一个 NeoForge、同一个 JDK、同一份 vanilla jar）。
  这样两个客户端之间任何行为差异，只可能来自本模组本身。

用法：
    python tools/launch_nomod_client.py <模组客户端PID> <新mcp端口>
"""

import os
import re
import subprocess
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GAMEDIR = os.path.join(PROJECT, "run-nomod")

# 是否保留 --quickPlayMultiplayer（自动连服）。
# 两个客户端同时用同名 Dev 账号会互相踢，所以默认由调用方决定：
# 不装模组的客户端测试时，模组客户端应当先退出喵~
KEEP_QUICKPLAY = "--keep-quickplay" in sys.argv


def get_command_line(pid: int) -> str:
    """读取目标进程的完整命令行。

    ⚠️ 必须让 PowerShell **用 UTF-8 输出**：本机项目路径含中文（WorkBuddy工作目录），
    若按默认代码页解码，中文会变成 `??????`，随后 JVM 报
    `InvalidPathException: Illegal char <?>`（我踩过一次）喵~。
    """
    ps = (
        "[Console]::OutputEncoding=[System.Text.Encoding]::UTF8; "
        "Get-CimInstance Win32_Process -Filter \"ProcessId=%d\" | "
        "Select-Object -ExpandProperty CommandLine" % pid
    )
    out = subprocess.run(
        ["powershell", "-NoProfile", "-Command", ps],
        capture_output=True,
    )
    # 显式按 UTF-8 解码，忽略非法字节
    return (out.stdout or b"").decode("utf-8", errors="replace").strip()


def split_args(cmdline: str) -> list:
    """按 Windows 规则拆分命令行（保留引号内的空格）。"""
    args, cur, in_quotes = [], "", False
    for ch in cmdline:
        if ch == '"':
            in_quotes = not in_quotes
            continue
        if ch == " " and not in_quotes:
            if cur:
                args.append(cur)
                cur = ""
            continue
        cur += ch
    if cur:
        args.append(cur)
    return args


def main() -> int:
    # 允许直接传一个已保存的命令行文件，这样源客户端被杀掉后仍能复现同一条命令喵~
    if sys.argv[1].endswith(".txt"):
        # PowerShell 的 Out-File -Encoding utf8 会写入 BOM，必须用 utf-8-sig 读掉，
        # 否则第一个参数（java.exe 路径）前面会多出 \ufeff，CreateProcess 报「找不到文件」喵~
        with open(sys.argv[1], "r", encoding="utf-8-sig", errors="replace") as f:
            raw = f.read().strip()
        mcp_port = sys.argv[2] if len(sys.argv) > 2 else "9877"
        pid = None
    else:
        pid = int(sys.argv[1])
        mcp_port = sys.argv[2] if len(sys.argv) > 2 else "9877"
        raw = get_command_line(pid)

    if not raw or "forgeclientdev" not in raw:
        print("命令行里没有 forgeclientdev，拒绝启动")
        return 1

    args = split_args(raw)
    print("[derive] 原始参数 %d 个" % len(args))

    # 原始命令行里通常**没有** -Dmcp.port（它是 Gradle 的 JVM 参数，不会传给游戏 JVM），
    # 所以这里要主动注入，否则两个客户端都抢默认的 9876，后者 bind 失败喵~
    if not any(a.startswith("-Dmcp.port") for a in args):
        args.insert(1, "-Dmcp.port=%s" % mcp_port)
        print("[derive] 注入 -Dmcp.port=%s" % mcp_port)

    dropped = []
    out = []
    skip_next = False
    for i, a in enumerate(args):
        if skip_next:
            skip_next = False
            continue
        # 剔除本模组的两个 classpath 入口
        if "Sweeper-Maid" in a and (
            os.path.join("build", "classes", "java", "main") in a
            or os.path.join("build", "resources", "main") in a
        ):
            dropped.append(a)
            continue
        # classpath 可能是单个巨型参数，需要**逐项**过滤
        if a.startswith("-cp") or a.startswith("-classpath"):
            prefix = "-cp" if a.startswith("-cp") else "-classpath"
            value = a[len(prefix) :]
            if value.startswith("="):
                value = value[1:]
            if not value and i + 1 < len(args):
                value = args[i + 1]
                skip_next = True
            parts = [p for p in value.split(os.pathsep) if p]
            kept = []
            for p in parts:
                if "Sweeper-Maid" in p and (
                    os.path.join("build", "classes", "java", "main") in p
                    or os.path.join("build", "resources", "main") in p
                ):
                    dropped.append(p)
                else:
                    kept.append(p)
            # ★ -cp 与值必须是**两个**独立参数（写成 -cp<value> 会被 JVM 当成未知选项）
            out.append(prefix)
            out.append(os.pathsep.join(kept))
            continue
        # gameDir 换成 run-nomod，避免与模组客户端抢文件
        if a.startswith("--gameDir"):
            out.append("--gameDir")
            out.append(GAMEDIR)
            continue
        # 换 MCP 端口，避免与模组客户端（9876）抢端口
        if a.startswith("-Dmcp.port"):
            out.append("-Dmcp.port=%s" % mcp_port)
            continue
        # 去掉 quickPlay（两个客户端不能同时登录同名账号）；
        # 连服改由启动后单独驱动，见 tools/nomod_connect.py 的说明喵~
        if a == "--quickPlayMultiplayer":
            if KEEP_QUICKPLAY:
                out.append(a)
                continue
            skip_next = True
            continue
        if a.startswith("--quickPlayMultiplayer"):
            if KEEP_QUICKPLAY:
                out.append(a)
                continue
            continue
        # 换一个用户名，避免与模组客户端同名冲突
        if a == "--username":
            skip_next = True
            out.append("--username")
            out.append("Plain")
            continue
        out.append(a)

    print("[derive] 剔除的模组入口 %d 项：" % len(dropped))
    for d in dropped:
        print("   -", d)

    os.makedirs(GAMEDIR, exist_ok=True)
    log = os.path.join(GAMEDIR, "launch-nomod.log")
    print("[derive] gameDir =", GAMEDIR)
    print("[derive] mcp.port =", mcp_port)
    print("[derive] log =", log)

    with open(log, "wb") as f:
        subprocess.Popen(out, cwd=GAMEDIR, stdout=f, stderr=subprocess.STDOUT)
    print("[derive] 已启动「未装本模组」客户端")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
