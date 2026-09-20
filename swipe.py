"""每隔 60–120 秒随机上滑一次手机屏幕（通过 ADB）。"""

from __future__ import annotations

import random
import shutil
import subprocess
import sys
import time

MIN_INTERVAL = 60
MAX_INTERVAL = 120
SWIPE_DURATION_MS = 300


def run_adb(*args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["adb", *args],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )


def ensure_device() -> None:
    if shutil.which("adb") is None:
        print("未找到 adb，请先安装 Android Platform Tools 并加入 PATH。")
        sys.exit(1)

    result = run_adb("devices")
    devices = [
        line
        for line in result.stdout.splitlines()[1:]
        if line.strip() and "\tdevice" in line
    ]
    if not devices:
        print("未检测到已授权的安卓设备。请用 USB 连接手机并打开 USB 调试。")
        sys.exit(1)


def screen_size() -> tuple[int, int]:
    result = run_adb("shell", "wm", "size")
    # 输出类似：Physical size: 1080x2400
    for token in result.stdout.replace("\n", " ").split():
        if "x" in token and token.replace("x", "").isdigit():
            width, height = token.split("x")
            return int(width), int(height)
    return 1080, 2400


def swipe_up(width: int, height: int) -> None:
    x = width // 2
    start_y = int(height * 0.72)
    end_y = int(height * 0.28)
    result = run_adb(
        "shell",
        "input",
        "swipe",
        str(x),
        str(start_y),
        str(x),
        str(end_y),
        str(SWIPE_DURATION_MS),
    )
    if result.returncode != 0:
        err = (result.stderr or result.stdout).strip()
        raise RuntimeError(err or "adb swipe 失败")


def main() -> None:
    ensure_device()
    width, height = screen_size()
    print(f"已连接设备，屏幕 {width}x{height}")
    print(f"每 {MIN_INTERVAL}–{MAX_INTERVAL} 秒随机上滑一次，Ctrl+C 停止。")

    while True:
        wait = random.randint(MIN_INTERVAL, MAX_INTERVAL)
        print(f"等待 {wait} 秒…")
        time.sleep(wait)
        swipe_up(width, height)
        print("已上滑")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n已停止。")
