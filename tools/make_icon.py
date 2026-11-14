#!/usr/bin/env python3
"""
Deterministic ASCII Grid -> Android Adaptive Vector Icon & 512px Play Store PNG generator.
Usage:
  python tools/make_icon.py icon.grid --bg 0E1912 --fg FFB38A --accent C6F36B --res app/src/main/res --png-out store/icon_512.png
"""
import argparse
from pathlib import Path

try:
    from PIL import Image, ImageDraw
except ImportError:
    Image = None


def parse_grid(grid_path: Path) -> list[str]:
    lines = [
        line.strip()
        for line in grid_path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.strip().startswith("//")
    ]
    if not lines:
        raise ValueError("icon.grid is empty")
    width = len(lines[0])
    for idx, row in enumerate(lines):
        if len(row) != width:
            raise ValueError(f"Row {idx} length {len(row)} != expected {width}")
    return lines


def build_paths(lines: list[str], target_char: str, offset: float, cell_size: float) -> list[str]:
    cmds = []
    for r, row in enumerate(lines):
        for c, ch in enumerate(row):
            if ch == target_char:
                x1 = round(offset + c * cell_size, 2)
                y1 = round(offset + r * cell_size, 2)
                x2 = round(x1 + cell_size, 2)
                y2 = round(y1 + cell_size, 2)
                cmds.append(f"M{x1},{y1}H{x2}V{y2}H{x1}Z")
    return cmds


def main() -> None:
    parser = argparse.ArgumentParser(description="Build adaptive launcher icon from ASCII grid.")
    parser.add_argument("grid", type=Path, help="Path to icon.grid")
    parser.add_argument("--bg", required=True, help="Background hex RGB (e.g. 0E1912)")
    parser.add_argument("--fg", required=True, help="Foreground hex RGB (e.g. FFB38A)")
    parser.add_argument("--accent", required=True, help="Accent hex RGB (e.g. C6F36B)")
    parser.add_argument("--res", type=Path, required=True, help="Android res/ directory")
    parser.add_argument("--png-out", type=Path, default=None, help="Output 512px PNG path")
    parser.add_argument("--legacy", action="store_true", help="Also emit legacy PNG mipmaps")
    args = parser.parse_args()

    bg_hex = args.bg.lstrip("#").upper()
    fg_hex = args.fg.lstrip("#").upper()
    acc_hex = args.accent.lstrip("#").upper()

    lines = parse_grid(args.grid)
    rows = len(lines)
    cols = len(lines[0])
    n = max(rows, cols)

    span = 55.0
    cell = span / n
    offset = (108.0 - n * cell) / 2.0

    fg_cmds = build_paths(lines, "#", offset, cell)
    acc_cmds = build_paths(lines, "+", offset, cell)
    mono_cmds = fg_cmds + acc_cmds

    drawable_dir = args.res / "drawable"
    values_dir = args.res / "values"
    mipmap_v26_dir = args.res / "mipmap-anydpi-v26"
    drawable_dir.mkdir(parents=True, exist_ok=True)
    values_dir.mkdir(parents=True, exist_ok=True)
    mipmap_v26_dir.mkdir(parents=True, exist_ok=True)

    for stale in [
        args.res / "drawable-v24" / "ic_launcher_foreground.xml",
        args.res / "drawable" / "ic_launcher_background.xml",
    ]:
        if stale.exists():
            stale.unlink()

    fg_xml = f"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#{fg_hex}"
        android:pathData="{' '.join(fg_cmds)}" />
    <path
        android:fillColor="#{acc_hex}"
        android:pathData="{' '.join(acc_cmds)}" />
</vector>
"""
    (drawable_dir / "ic_launcher_foreground.xml").write_text(fg_xml, encoding="utf-8")

    mono_xml = f"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="{' '.join(mono_cmds)}" />
</vector>
"""
    (drawable_dir / "ic_launcher_monochrome.xml").write_text(mono_xml, encoding="utf-8")

    icon_colors_xml = f"""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#{bg_hex}</color>
</resources>
"""
    (values_dir / "icon_colors.xml").write_text(icon_colors_xml, encoding="utf-8")

    adaptive_xml = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
"""
    (mipmap_v26_dir / "ic_launcher.xml").write_text(adaptive_xml, encoding="utf-8")
    (mipmap_v26_dir / "ic_launcher_round.xml").write_text(adaptive_xml, encoding="utf-8")

    if args.png_out and Image is not None:
        args.png_out.parent.mkdir(parents=True, exist_ok=True)
        size = 512
        img = Image.new("RGBA", (size, size), f"#{bg_hex}")
        draw = ImageDraw.Draw(img)
        pad = 96
        inner = size - 2 * pad
        px_cell = inner / n
        for r, row in enumerate(lines):
            for c, ch in enumerate(row):
                if ch in ("#", "+"):
                    color = f"#{fg_hex}" if ch == "#" else f"#{acc_hex}"
                    x0 = int(round(pad + c * px_cell))
                    y0 = int(round(pad + r * px_cell))
                    x1 = int(round(pad + (c + 1) * px_cell))
                    y1 = int(round(pad + (r + 1) * px_cell))
                    draw.rectangle([x0, y0, x1 - 1, y1 - 1], fill=color)
        img.save(args.png_out, "PNG")


if __name__ == "__main__":
    main()
