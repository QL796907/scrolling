from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res"
BG = (255, 240, 230, 255)
CORAL = (255, 143, 102, 255)
PATHS = [[(36, 51), (54, 35), (72, 51)], [(36, 73), (54, 57), (72, 73)]]
DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def draw_icon(size: int) -> Image.Image:
    img = Image.new("RGBA", (size, size), BG)
    draw = ImageDraw.Draw(img)
    scale = size / 108.0
    width = max(2, int(round(13 * scale)))
    radius = width / 2.0

    def pt(x: float, y: float) -> tuple[float, float]:
        return (x * scale, y * scale)

    for path in PATHS:
        pts = [pt(x, y) for x, y in path]
        draw.line(pts, fill=CORAL, width=width)
        for point in pts:
            draw.ellipse(
                [point[0] - radius, point[1] - radius, point[0] + radius, point[1] + radius],
                fill=CORAL,
            )
    return img


def circle_crop(img: Image.Image) -> Image.Image:
    size = img.size[0]
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(img, mask=mask)
    return out


def main() -> None:
    for folder, size in DENSITIES.items():
        dest = ROOT / folder
        dest.mkdir(parents=True, exist_ok=True)
        icon = draw_icon(size)
        icon.save(dest / "ic_launcher.png")
        circle_crop(icon).save(dest / "ic_launcher_round.png")
        print(f"wrote {folder} {size}")

    preview = ROOT.parents[3] / "icon-previews"
    preview.mkdir(exist_ok=True)
    big = draw_icon(1024)
    radius = int(1024 * 0.22)
    mask = Image.new("L", (1024, 1024), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, 1023, 1023], radius=radius, fill=255)
    rounded = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
    rounded.paste(big, mask=mask)
    rounded.save(preview / "icon-rounded.png")
    circle_crop(big).save(preview / "icon-circle.png")
    print("previews ok")


if __name__ == "__main__":
    main()
