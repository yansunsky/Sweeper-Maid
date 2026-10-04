"""Sweeper-Maid 验收用最小 RCON 客户端（标准库实现，长连接）。

用法：
    python tools/rcon_cli.py 127.0.0.1 25575 sweeptest "list"
    echo "list" | python tools/rcon_cli.py 127.0.0.1 25575 sweeptest
"""

import socket
import struct
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
sys.stderr.reconfigure(encoding="utf-8", errors="replace")

LOGIN = 3
COMMAND = 2


class Rcon:
    def __init__(self, host: str, port: int, password: str):
        self.sock = socket.create_connection((host, port), timeout=15)
        self.req_id = 0
        self._send(LOGIN, password)
        rid, _kind, _body = self._recv()
        if rid == -1:
            raise RuntimeError("RCON 认证失败：密码不对")

    def _send(self, kind: int, body: str) -> None:
        self.req_id += 1
        payload = struct.pack("<ii", self.req_id, kind) + body.encode("utf8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(payload)) + payload)

    def _recv(self):
        raw_len = self._read_exactly(4)
        (length,) = struct.unpack("<i", raw_len)
        data = self._read_exactly(length)
        rid, kind = struct.unpack("<ii", data[:8])
        return rid, kind, data[8:-2].decode("utf8", errors="replace")

    def _read_exactly(self, n: int) -> bytes:
        buf = b""
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                raise RuntimeError("RCON 连接被对端关闭")
            buf += chunk
        return buf

    def cmd(self, command: str) -> str:
        self._send(COMMAND, command)
        _rid, _kind, body = self._recv()
        return body

    def close(self) -> None:
        self.sock.close()


def main() -> int:
    host, port, password = sys.argv[1], int(sys.argv[2]), sys.argv[3]
    commands = sys.argv[4:]
    if not commands:
        commands = [line.rstrip("\n") for line in sys.stdin if line.strip()]

    rcon = Rcon(host, port, password)
    try:
        for command in commands:
            print(f"> {command}")
            print(rcon.cmd(command))
    finally:
        rcon.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
