#!/usr/bin/env python3
"""
ASCII-Art Pipe Launcher Icon Generator for `pipe` (`com.sujan.pipe`).
Renders the 20x8 ASCII-art smoking pipe with Unix `|` / `>` smoke flair into:
- `store/icon_512.png` (512x512 high-res PNG)
- `drawable/ic_launcher_foreground.xml` (Android vector drawable scanline paths)
- `drawable/ic_launcher_monochrome.xml` (Android 13+ themed icon vector)
- `values/icon_colors.xml` & `mipmap-anydpi-v26/ic_launcher*.xml`
"""
import argparse
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

FONT_CANDIDATES = [
    "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf",
    "/usr/share/fonts/truetype/liberation2/LiberationMono-Bold.ttf",
    "/usr/share/fonts/truetype/liberation/LiberationMono-Bold.ttf",
]

PALETTE = {
    "bg": "#0E1912",
    "surface": "#16271C",
    "border": "#33513D",
    "fg": "#E9F3E3",
    "lime": "#C6F36B",
    "peach": "#FFB38A",
    "sky": "#7FD3FF",
    "gold": "#FFD166",
}


def load_font(size: int):
    for p in FONT_CANDIDATES:
        if Path(p).exists():
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def render_ascii_pipe_canvas(size: int = 512) -> Image.Image:
    img = Image.new("RGBA", (size, size), PALETTE["bg"])
    draw = ImageDraw.Draw(img)

    scale = size / 512.0
    font_main = load_font(max(8, int(round(31 * scale))))
    font_sm = load_font(max(6, int(round(20 * scale))))

    b0, b1 = int(round(64 * scale)), int(round(448 * scale))
    header_y = int(round(104 * scale))
    bw = max(1, int(round(4 * scale)))
    lw = max(1, int(round(2 * scale)))

    draw.rectangle([b0, b0, b1, b1], fill=PALETTE["surface"], outline=PALETTE["border"], width=bw)
    draw.line([b0, header_y, b1, header_y], fill=PALETTE["border"], width=lw)
    draw.text((int(round(80 * scale)), int(round(74 * scale))), "~/pipe", font=font_sm, fill=PALETTE["peach"])
    draw.text((int(round(352 * scale)), int(round(74 * scale))), "[$ | >]", font=font_sm, fill=PALETTE["lime"])

    grid_lines = [
        "     .  ~   |  *    ",
        "   (   |  ~  )   >  ",
        "    .==========.    ",
        "   |##########|  __ ",
        "   |##########| /=/ ",
        "   |##########|/=/  ",
        "    \\###########/   ",
        "     `---------'    ",
    ]

    char_w = font_main.getlength("M")
    line_h = 35.0 * scale
    grid_w = 20 * char_w
    grid_h = len(grid_lines) * line_h
    start_x = (size - grid_w) / 2.0
    start_y = header_y + ((b1 - header_y) - grid_h) / 2.0 - 4 * scale

    for r, row_str in enumerate(grid_lines):
        y = start_y + r * line_h
        for c_idx, ch in enumerate(row_str):
            if ch == " ":
                continue
            x = start_x + c_idx * char_w
            if r in (0, 1):
                if ch in ("|", ">"):
                    col = PALETTE["lime"]
                elif ch == "*":
                    col = PALETTE["gold"]
                else:
                    col = PALETTE["sky"]
            elif r == 2:
                col = PALETTE["gold"]
            elif r in (3, 4, 5) and c_idx >= 15:
                col = PALETTE["lime"] if ch == "=" else PALETTE["fg"]
            elif ch == "#":
                col = PALETTE["peach"]
            elif ch in ("|", "\\", "/", "`", "'", "-"):
                col = PALETTE["fg"]
            else:
                col = PALETTE["peach"]
            draw.text((x, y), ch, font=font_main, fill=col)

    return img


def rgb_to_hex(rgb: tuple[int, int, int]) -> str:
    return f"#{rgb[0]:02X}{rgb[1]:02X}{rgb[2]:02X}"


def quantize_to_palette(rgb: tuple[int, int, int]) -> str:
    best_hex = PALETTE["bg"]
    best_dist = 10**9
    for h in PALETTE.values():
        hr, hg, hb = int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16)
        d = (rgb[0] - hr) ** 2 + (rgb[1] - hg) ** 2 + (rgb[2] - hb) ** 2
        if d < best_dist:
            best_dist = d
            best_hex = h
    return best_hex


def build_vector_xml_from_image(img_512: Image.Image) -> tuple[str, str]:
    # Sample onto a crisp 108x108 grid matching the adaptive icon viewport
    vp = 108
    small = img_512.resize((vp, vp), Image.Resampling.NEAREST).convert("RGB")
    color_runs: dict[str, list[str]] = {h: [] for h in PALETTE.values() if h != PALETTE["bg"]}
    mono_runs: list[str] = []

    for y in range(vp):
        cur_col = None
        run_start = 0
        for x in range(vp + 1):
            col = quantize_to_palette(small.getpixel((x, y))) if x < vp else PALETTE["bg"]
            if col != cur_col:
                if cur_col is not None and cur_col != PALETTE["bg"]:
                    cmd = f"M{run_start},{y}H{x}V{y+1}H{run_start}Z"
                    color_runs[cur_col].append(cmd)
                    if cur_col != PALETTE["surface"]:
                        mono_runs.append(cmd)
                cur_col = col
                run_start = x

    path_tags = []
    for hex_col, cmds in color_runs.items():
        if cmds:
            path_tags.append(
                f'    <path\n        android:fillColor="{hex_col}"\n        android:pathData="{" ".join(cmds)}" />'
            )

    fg_xml = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp"\n'
        '    android:height="108dp"\n'
        '    android:viewportWidth="108"\n'
        '    android:viewportHeight="108">\n'
        + "\n".join(path_tags)
        + "\n</vector>\n"
    )

    mono_xml = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp"\n'
        '    android:height="108dp"\n'
        '    android:viewportWidth="108"\n'
        '    android:viewportHeight="108">\n'
        f'    <path\n        android:fillColor="#FFFFFF"\n        android:pathData="{" ".join(mono_runs)}" />\n'
        "</vector>\n"
    )
    return fg_xml, mono_xml


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("grid", nargs="?", default="icon.grid")
    parser.add_argument("--bg", default="0E1912")
    parser.add_argument("--fg", default="FFB38A")
    parser.add_argument("--accent", default="C6F36B")
    parser.add_argument("--res", type=Path, required=True)
    parser.add_argument("--png-out", type=Path, default=None)
    args = parser.parse_args()

    img = render_ascii_pipe_canvas(512)
    if args.png_out:
        args.png_out.parent.mkdir(parents=True, exist_ok=True)
        img.save(args.png_out, "PNG")

    fg_xml, mono_xml = build_vector_xml_from_image(img)

    drawable_dir = args.res / "drawable"
    values_dir = args.res / "values"
    mipmap_v26_dir = args.res / "mipmap-anydpi-v26"
    drawable_dir.mkdir(parents=True, exist_ok=True)
    values_dir.mkdir(parents=True, exist_ok=True)
    mipmap_v26_dir.mkdir(parents=True, exist_ok=True)

    (drawable_dir / "ic_launcher_foreground.xml").write_text(fg_xml, encoding="utf-8")
    (drawable_dir / "ic_launcher_monochrome.xml").write_text(mono_xml, encoding="utf-8")
    (values_dir / "icon_colors.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">#0E1912</color>\n</resources>\n',
        encoding="utf-8",
    )
    adaptive_xml = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@color/ic_launcher_background" />\n'
        '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
        '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
        "</adaptive-icon>\n"
    )
    (mipmap_v26_dir / "ic_launcher.xml").write_text(adaptive_xml, encoding="utf-8")
    (mipmap_v26_dir / "ic_launcher_round.xml").write_text(adaptive_xml, encoding="utf-8")


if __name__ == "__main__":
    main()
