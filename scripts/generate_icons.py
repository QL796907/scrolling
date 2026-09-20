from __future__ import annotations

import struct
import zlib
from pathlib import Path


def png(width: int, height: int, pixels: list[tuple[int, int, int, int]]) -> bytes:
    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            raw.extend(pixels[y * width + x])
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return b"".join(
        [
            b"\x89PNG\r\n\x1a\n",
            chunk(b"IHDR", ihdr),
            chunk(b"IDAT", zlib.compress(bytes(raw), 9)),
            chunk(b"IEND", b""),
        ]
    )


def arrow_icon(size: int) -> bytes:
    bg = (16, 18, 24, 255)
    fg = (255, 122, 69, 255)
    pixels = [bg] * (size * size)
    cx = size / 2
    top = size * 0.22
    mid = size * 0.50
    bottom = size * 0.78
    stem_w = size * 0.18
    head_w = size * 0.36

    for y in range(size):
        for x in range(size):
            # circular background already square; draw arrow
            in_head = top <= y <= mid and abs(x - cx) <= ((y - top) / (mid - top)) * head_w
            in_stem = mid <= y <= bottom and abs(x - cx) <= stem_w
            if in_head or in_stem:
                pixels[y * size + x] = fg
    return png(size, size, pixels)


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    mapping = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, size in mapping.items():
        out = root / "app" / "src" / "main" / "res" / folder
        out.mkdir(parents=True, exist_ok=True)
        data = arrow_icon(size)
        (out / "ic_launcher.png").write_bytes(data)
        (out / "ic_launcher_round.png").write_bytes(data)


if __name__ == "__main__":
    main()
