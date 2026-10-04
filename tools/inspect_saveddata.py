"""读取并打印 SweeperMaid-SavedData.dat 的页数与每页物品（gzip + 极简 NBT 解析）。"""

import gzip
import struct
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

PATH = sys.argv[1] if len(sys.argv) > 1 else (
    r"run\server\world\data\SweeperMaid-SavedData.dat"
)


def read_nbt(data: bytes, offset: int = 0):
    """返回 (tag, 新偏移)。tag 是 (type, name, payload) 或裸值。"""
    raise NotImplementedError


class Reader:
    def __init__(self, data: bytes):
        self.d = data
        self.i = 0

    def u1(self) -> int:
        v = self.d[self.i]
        self.i += 1
        return v

    def i2(self) -> int:
        v = struct.unpack_from(">h", self.d, self.i)[0]
        self.i += 2
        return v

    def i4(self) -> int:
        v = struct.unpack_from(">i", self.d, self.i)[0]
        self.i += 4
        return v

    def i8(self) -> int:
        v = struct.unpack_from(">q", self.d, self.i)[0]
        self.i += 8
        return v

    def f4(self):
        v = struct.unpack_from(">f", self.d, self.i)[0]
        self.i += 4
        return v

    def f8(self):
        v = struct.unpack_from(">d", self.d, self.i)[0]
        self.i += 8
        return v

    def s(self) -> str:
        n = struct.unpack_from(">H", self.d, self.i)[0]
        self.i += 2
        v = self.d[self.i : self.i + n].decode("utf8", errors="replace")
        self.i += n
        return v

    def payload(self, t: int):
        if t == 1:
            return struct.unpack_from(">b", self.d, self.i - 1)[0] if False else self._b()
        if t == 2:
            return self.i2()
        if t == 3:
            return self.i4()
        if t == 4:
            return self.i8()
        if t == 5:
            return self.f4()
        if t == 6:
            return self.f8()
        if t == 7:
            n = self.i4()
            v = list(self.d[self.i : self.i + n])
            self.i += n
            return v
        if t == 8:
            return self.s()
        if t == 9:
            elem = self.u1()
            n = self.i4()
            return [self.payload(elem) for _ in range(n)]
        if t == 10:
            out = {}
            while True:
                tt = self.u1()
                if tt == 0:
                    return out
                name = self.s()
                out[name] = self.payload(tt)
        if t == 11:
            n = self.i4()
            v = list(struct.unpack_from(">%di" % n, self.d, self.i))
            self.i += 4 * n
            return v
        if t == 12:
            n = self.i4()
            v = list(struct.unpack_from(">%dq" % n, self.d, self.i))
            self.i += 8 * n
            return v
        raise ValueError("未知 tag 类型 %d at %d" % (t, self.i))

    def _b(self) -> int:
        v = struct.unpack_from(">b", self.d, self.i)[0]
        self.i += 1
        return v


def main() -> int:
    with gzip.open(PATH, "rb") as f:
        raw = f.read()
    r = Reader(raw)
    root_type = r.u1()
    root_name = r.s()
    assert root_type == 10, "根 tag 不是 Compound"
    root = r.payload(10)
    print("root name=%r keys=%s" % (root_name, sorted(root.keys())))

    # Vanilla 把每个 SavedData 存在 data/<id> 下
    node = root.get("data", root)
    if isinstance(node, dict) and "data" in node and "dustbins" not in node:
        node = node["data"]
    print("data keys = %s" % sorted(node.keys()))

    dustbins = node.get("dustbins") or node.get("Dustbins")
    if dustbins is None:
        print("!! 没有 dustbins 字段")
        return 1
    print("页数 = %d" % len(dustbins))
    for i, page in enumerate(dustbins):
        items = []
        if isinstance(page, dict):
            stack_list = page.get("items") or page.get("Items") or []
            for entry in stack_list:
                if isinstance(entry, dict):
                    cnt = entry.get("count") or entry.get("Count") or 0
                    it = entry.get("stack") or entry.get("id") or "?"
                    items.append((it, cnt))
        total = sum(int(c) for _n, c in items if isinstance(c, int))
        print("  第 %d 页: %d 格有物, 合计 %d, 前几项=%s" % (i, len(items), total, items[:4]))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
