"""通过 HTTP 直连驱动 mcpmod（无需 DSH 注册 MCP server）。

mcpmod v0.4.1 的 HTTP 层：
  GET  /api/status         存活判据（ok/type/port 三字段）
  POST /api/cmd            通用命令通道；body 用 {"cmd": "...", "params": {...}}
  GET  /api/screenshot     ⚠️ 返回 alpha 全 0 的假图，勿用于像素验收
  GET  /api/events         SSE 长挂，会 timeout，属正常

重要纪律：
  * `/api/cmd` 一律 HTTP 200，成功/失败都写在 body 的 error 字段里 —— 必须检查 error。
  * 任何聊天命令都会 setScreen(ChatScreen)，顶掉自研界面。
"""

import json
import sys
import urllib.error
import urllib.request

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

CANDIDATE_PORTS = [9875, 9876, 9877, 9878, 9879, 9000]


def _get(url: str, timeout: float = 5.0):
    with urllib.request.urlopen(url, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8", errors="replace"))


def find_port() -> int | None:
    for port in CANDIDATE_PORTS:
        try:
            data = _get(f"http://127.0.0.1:{port}/api/status")
        except Exception:
            continue
        if isinstance(data, dict) and data.get("ok") and data.get("type") == "minecraft-mod":
            return port
    return None


def cmd(port: int, name: str, params: dict | None = None, timeout: float = 30.0):
    body = {"cmd": name}
    if params:
        body["params"] = params
    req = urllib.request.Request(
        f"http://127.0.0.1:{port}/api/cmd",
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        return {"error": f"HTTP {e.code}", "body": e.read().decode("utf-8", "replace")}
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return {"error": "non-json response", "body": raw[:400]}


def main() -> int:
    # 端口可显式指定：python tools/mcp_api.py --port 9877 debug_fields
    explicit = None
    args = sys.argv[1:]
    if args and args[0] == "--port":
        explicit = int(args[1])
        args = args[2:]

    port = explicit if explicit is not None else find_port()
    if port is None:
        print("未找到在监听的 mcpmod（探测端口 %s）" % CANDIDATE_PORTS)
        return 1
    print(f"[ok] mcpmod 在端口 {port}")
    print("[status]", json.dumps(_get(f"http://127.0.0.1:{port}/api/status"), ensure_ascii=False))

    if not args:
        return 0

    if args[0] == "release-mouse":
        # 关键：先进控制模式，再释放鼠标，让用户能继续用电脑
        print("[enter_control_mode]", json.dumps(cmd(port, "enter_control_mode"), ensure_ascii=False))
        result = cmd(port, "release_mouse")
        print("[release_mouse]", json.dumps(result, ensure_ascii=False))
        return 0 if "error" not in result else 1

    name = args[0]
    if len(args) > 1:
        raw = args[1]
        # 允许 @file.json，避开 PowerShell 对引号的二次转义（实测会把 " 吃掉）
        if raw.startswith("@"):
            with open(raw[1:], "r", encoding="utf-8") as f:
                raw = f.read()
        params = json.loads(raw)
    else:
        params = None
    result = cmd(port, name, params)
    print(f"[{name}]", json.dumps(result, ensure_ascii=False))
    return 0 if "error" not in result else 1


if __name__ == "__main__":
    raise SystemExit(main())
