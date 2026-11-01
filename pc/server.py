# タブレットからのキー入力をPCに注入する (Windows / 標準ライブラリのみ)
import ctypes
import socket
import threading
import time
from ctypes import wintypes

user32 = ctypes.windll.user32
ULONG_PTR = ctypes.c_size_t


class MOUSEINPUT(ctypes.Structure):
    _fields_ = [("dx", wintypes.LONG), ("dy", wintypes.LONG), ("mouseData", wintypes.DWORD),
                ("dwFlags", wintypes.DWORD), ("time", wintypes.DWORD), ("dwExtraInfo", ULONG_PTR)]


class KEYBDINPUT(ctypes.Structure):
    _fields_ = [("wVk", wintypes.WORD), ("wScan", wintypes.WORD), ("dwFlags", wintypes.DWORD),
                ("time", wintypes.DWORD), ("dwExtraInfo", ULONG_PTR)]


class HARDWAREINPUT(ctypes.Structure):
    _fields_ = [("uMsg", wintypes.DWORD), ("wParamL", wintypes.WORD), ("wParamH", wintypes.WORD)]


class _U(ctypes.Union):
    _fields_ = [("mi", MOUSEINPUT), ("ki", KEYBDINPUT), ("hi", HARDWAREINPUT)]


class INPUT(ctypes.Structure):
    _anonymous_ = ("u",)
    _fields_ = [("type", wintypes.DWORD), ("u", _U)]


def key(code, up):
    flags = 0x0008  # KEYEVENTF_SCANCODE
    if code > 0xFF:
        flags |= 0x0001  # EXTENDEDKEY
    if up:
        flags |= 0x0002  # KEYUP
    inp = INPUT(type=1)
    inp.ki = KEYBDINPUT(0, code & 0xFF, flags, 0, 0)
    user32.SendInput(1, ctypes.byref(inp), ctypes.sizeof(INPUT))


NO_REPEAT = {0x2A, 0x36, 0x1D, 0xE01D, 0x38, 0xE038, 0xE05B, 0x3A}


def handle(conn):
    held = {}
    lock = threading.Lock()
    alive = [True]

    def repeater():
        while alive[0]:
            time.sleep(0.03)
            now = time.time()
            with lock:
                for c, t in list(held.items()):
                    if c not in NO_REPEAT and now - t > 0.45:
                        key(c, False)

    threading.Thread(target=repeater, daemon=True).start()
    buf = b""
    try:
        while True:
            data = conn.recv(1024)
            if not data:
                break
            buf += data
            while b"\n" in buf:
                line, buf = buf.split(b"\n", 1)
                p = line.decode(errors="ignore").split()
                if len(p) == 2 and p[0] in ("D", "U"):
                    c = int(p[1], 16)
                    with lock:
                        if p[0] == "D":
                            if c not in held:
                                held[c] = time.time()
                                key(c, False)
                        else:
                            held.pop(c, None)
                            key(c, True)
    except OSError:
        pass
    finally:
        alive[0] = False
        with lock:
            for c in list(held):
                key(c, True)
        conn.close()
        print("tablet disconnected")


def main():
    s = socket.socket()
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(("127.0.0.1", 5555))
    s.listen(1)
    print("waiting for tablet on 127.0.0.1:5555 ...")
    while True:
        conn, _ = s.accept()
        conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        print("tablet connected")
        threading.Thread(target=handle, args=(conn,), daemon=True).start()


main()
