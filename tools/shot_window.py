"""抓取 Minecraft 客户端窗口截图（不依赖任何客户端 mod）。

使用 Win32 PrintWindow，把窗口内容渲染到位图后存盘。
用途：验证「服务端装模组、客户端可选」时各客户端实际画出来的界面。
"""

import ctypes
import ctypes.wintypes as wt
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

user32 = ctypes.windll.user32
gdi32 = ctypes.windll.gdi32

PW_RENDERFULLCONTENT = 0x00000002
SRCCOPY = 0x00CC0020


class BITMAPINFOHEADER(ctypes.Structure):
    _fields_ = [
        ("biSize", wt.DWORD),
        ("biWidth", ctypes.c_long),
        ("biHeight", ctypes.c_long),
        ("biPlanes", wt.WORD),
        ("biBitCount", wt.WORD),
        ("biCompression", wt.DWORD),
        ("biSizeImage", wt.DWORD),
        ("biXPelsPerMeter", ctypes.c_long),
        ("biYPelsPerMeter", ctypes.c_long),
        ("biClrUsed", wt.DWORD),
        ("biClrImportant", wt.DWORD),
    ]


class BITMAPINFO(ctypes.Structure):
    _fields_ = [("bmiHeader", BITMAPINFOHEADER), ("bmiColors", wt.DWORD * 3)]


def find_window(keyword: str = "Minecraft", pid: int | None = None):
    found = []

    @ctypes.WINFUNCTYPE(ctypes.c_bool, wt.HWND, wt.LPARAM)
    def cb(hwnd, _lparam):
        if not user32.IsWindowVisible(hwnd):
            return True
        if pid is not None:
            owner = wt.DWORD()
            user32.GetWindowThreadProcessId(hwnd, ctypes.byref(owner))
            if owner.value != pid:
                return True
        length = user32.GetWindowTextLengthW(hwnd)
        if length == 0:
            return True
        buf = ctypes.create_unicode_buffer(length + 1)
        user32.GetWindowTextW(hwnd, buf, length + 1)
        title = buf.value
        if keyword.lower() in title.lower():
            rect = wt.RECT()
            user32.GetWindowRect(hwnd, ctypes.byref(rect))
            found.append((hwnd, title, rect.right - rect.left, rect.bottom - rect.top))
        return True

    user32.EnumWindows(cb, 0)
    return found


def restore_window(hwnd: int) -> None:
    """若窗口被最小化则还原并前置——否则截到的是 159x27 的标题栏。"""
    SW_RESTORE = 9
    if user32.IsIconic(hwnd):
        user32.ShowWindow(hwnd, SW_RESTORE)
    else:
        user32.ShowWindow(hwnd, SW_RESTORE)
    user32.SetForegroundWindow(hwnd)
    import time

    time.sleep(1.0)


def capture(hwnd: int, path: str) -> tuple[int, int]:
    rect = wt.RECT()
    user32.GetWindowRect(hwnd, ctypes.byref(rect))
    width, height = rect.right - rect.left, rect.bottom - rect.top

    hdc_window = user32.GetWindowDC(hwnd)
    hdc_mem = gdi32.CreateCompatibleDC(hdc_window)
    hbitmap = gdi32.CreateCompatibleBitmap(hdc_window, width, height)
    gdi32.SelectObject(hdc_mem, hbitmap)

    ok = user32.PrintWindow(hwnd, hdc_mem, PW_RENDERFULLCONTENT)
    if not ok:
        gdi32.BitBlt(hdc_mem, 0, 0, width, height, hdc_window, 0, 0, SRCCOPY)

    bmi = BITMAPINFO()
    bmi.bmiHeader.biSize = ctypes.sizeof(BITMAPINFOHEADER)
    bmi.bmiHeader.biWidth = width
    bmi.bmiHeader.biHeight = -height  # 负值 = 自顶向下
    bmi.bmiHeader.biPlanes = 1
    bmi.bmiHeader.biBitCount = 32
    bmi.bmiHeader.biCompression = 0  # BI_RGB

    bufsize = width * height * 4
    buffer = ctypes.create_string_buffer(bufsize)
    gdi32.GetDIBits(hdc_mem, hbitmap, 0, height, buffer, ctypes.byref(bmi), 0)

    # 写成 BMP（无需第三方库）
    with open(path, "wb") as f:
        row_size = width * 4
        pixel_bytes = buffer.raw
        file_header = b"BM" + (14 + 40 + len(pixel_bytes)).to_bytes(4, "little")
        file_header += b"\x00\x00\x00\x00" + (14 + 40).to_bytes(4, "little")
        info = (40).to_bytes(4, "little") + width.to_bytes(4, "little", signed=True)
        info += (-height).to_bytes(4, "little", signed=True)
        info += (1).to_bytes(2, "little") + (32).to_bytes(2, "little")
        info += (0).to_bytes(4, "little") + len(pixel_bytes).to_bytes(4, "little")
        info += (2835).to_bytes(4, "little") * 2 + (0).to_bytes(4, "little") * 2
        f.write(file_header + info + pixel_bytes)
        _ = row_size

    gdi32.DeleteObject(hbitmap)
    gdi32.DeleteDC(hdc_mem)
    user32.ReleaseDC(hwnd, hdc_window)
    return width, height


def main() -> int:
    out = sys.argv[1] if len(sys.argv) > 1 else "shot.bmp"
    keyword = sys.argv[2] if len(sys.argv) > 2 else "Minecraft"
    pid = int(sys.argv[3]) if len(sys.argv) > 3 else None
    windows = find_window(keyword, pid)
    if not windows:
        print("未找到包含 %r 的可见窗口（pid=%s）" % (keyword, pid))
        print("--- 所有可见窗口标题 ---")
        for hwnd, title, w, h in find_window(""):
            owner = wt.DWORD()
            user32.GetWindowThreadProcessId(hwnd, ctypes.byref(owner))
            print("  hwnd=%s pid=%s %dx%d %s" % (hwnd, owner.value, w, h, title))
        return 1
    # 选面积最大的那个（避免截到缩略的标题栏）
    windows.sort(key=lambda t: t[2] * t[3], reverse=True)
    for hwnd, title, w, h in windows:
        print("窗口: hwnd=%s %dx%d 标题=%r" % (hwnd, w, h, title))
    hwnd, title, _w, _h = windows[0]
    restore_window(hwnd)
    width, height = capture(hwnd, out)
    print("已保存 %s (%dx%d)" % (out, width, height))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
