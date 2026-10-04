"""启动一个**未安装 Sweeper-Maid** 的客户端连到验收服务器。

支持两种形态（覆盖用户要求的三种客户端类型中的后两种）：
  vanilla  : 纯原版 1.21.1（未装任何 mod，也未装 NeoForge）
  neoforge : 装了 NeoForge 21.1.172，但**没装** Sweeper-Maid

用法：
    python tools/launch_plain_client.py vanilla
    python tools/launch_plain_client.py neoforge 25585
"""

import json
import os
import subprocess
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

MC = os.path.join(os.environ["APPDATA"], ".minecraft")
VERSIONS = os.path.join(MC, "versions")
LIBS = os.path.join(MC, "libraries")
ASSETS = os.path.join(MC, "assets")
NATIVES = os.path.join(MC, "versions", ".dsh-natives")

VANILLA_SPLIT = {"1.21.1"}


def lib_path(name: str, downloads: dict) -> str | None:
    """按 maven 坐标算出本机 jar 路径。"""
    artifact = downloads.get("artifact")
    if artifact:
        rel = artifact["path"]
        return os.path.join(LIBS, rel.replace("/", os.sep))
    return None


def collect_libraries(version_id: str, seen: set) -> list:
    """递归收集版本（含 inheritsFrom 父版本）的库，父在前。"""
    vdir = os.path.join(VERSIONS, version_id)
    json_path = os.path.join(vdir, f"{version_id}.json")
    if not os.path.exists(json_path):
        raise SystemExit(f"找不到版本 JSON：{json_path}")
    with open(json_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    libs = []
    parent = data.get("inheritsFrom")
    if parent and parent not in seen:
        seen.add(parent)
        libs.extend(collect_libraries(parent, seen))

    for entry in data.get("libraries", []):
        rules = entry.get("rules")
        if rules and not allowed(rules):
            continue
        path = lib_path(entry["name"], entry.get("downloads", {}))
        if path and os.path.exists(path):
            libs.append(path)
    return libs


def allowed(rules: list) -> bool:
    """按原版规则判定当前平台是否允许该库。"""
    result = False
    for rule in rules:
        action = rule.get("action") == "allow"
        os_name = rule.get("os", {}).get("name")
        if os_name is None:
            result = action
        elif os_name == "windows":
            result = action
    return result


def main() -> int:
    kind = sys.argv[1] if len(sys.argv) > 1 else "vanilla"
    port = sys.argv[2] if len(sys.argv) > 2 else "25585"
    username = sys.argv[3] if len(sys.argv) > 3 else "Plain"

    if kind == "vanilla":
        version_id = "1.21.1"
        extra_jvm = []
    elif kind == "neoforge":
        version_id = "neoforge-21.1.172"
        extra_jvm = []
    else:
        raise SystemExit("kind 只能是 vanilla 或 neoforge")

    vdir = os.path.join(VERSIONS, version_id)
    with open(os.path.join(vdir, f"{version_id}.json"), "r", encoding="utf-8") as f:
        data = json.load(f)
    main_class = data["mainClass"]
    assets_index = data.get("assets", "17")
    if version_id == "neoforge-21.1.172":
        with open(os.path.join(VERSIONS, "1.21.1", "1.21.1.json"), "r", encoding="utf-8") as f:
            assets_index = json.load(f).get("assets", "17")

    libs = collect_libraries(version_id, set())
    # 原版 client.jar 作为 classpath 末位（Forge 形态由 BSL 自己拼装，但仍需 vanilla jar）
    client_jar = os.path.join(VERSIONS, "1.21.1", "1.21.1.jar")
    cp_parts = libs + [client_jar]
    classpath = os.pathsep.join(cp_parts)

    os.makedirs(NATIVES, exist_ok=True)
    gamedir = os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "..", f"run-{kind}"
    )
    gamedir = os.path.abspath(gamedir)
    os.makedirs(gamedir, exist_ok=True)

    java = os.environ.get("DSH_JAVA21") or "java"
    cmd = [
        java,
        f"-Djava.library.path={NATIVES}",
        "-Dminecraft.launcher.brand=dsh-accept",
        "-Dminecraft.launcher.version=1.0",
        "-Dfml.earlyprogresswindow=false",
        *extra_jvm,
        "-cp",
        classpath,
        main_class,
        "--username",
        username,
        "--version",
        "1.21.1",
        "--gameDir",
        gamedir,
        "--assetsDir",
        ASSETS,
        "--assetIndex",
        assets_index,
        "--uuid",
        "0000000000000000000000000000000a",
        "--accessToken",
        "0",
        "--userType",
        "legacy",
        "--quickPlayMultiplayer",
        f"127.0.0.1:{port}",
    ]

    log = os.path.join(gamedir, f"launch-{kind}.log")
    print(f"[launch] kind={kind} version={version_id}")
    print(f"[launch] main={main_class}")
    print(f"[launch] cpsize={len(cp_parts)} gameDir={gamedir}")
    print(f"[launch] log={log}")
    with open(log, "wb") as f:
        subprocess.Popen(cmd, cwd=gamedir, stdout=f, stderr=subprocess.STDOUT)
    print("[launch] 已启动（后台）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
