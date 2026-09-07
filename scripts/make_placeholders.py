#!/usr/bin/env python3
"""Generate branded placeholder screenshots for the LogSleuth README."""

from PIL import Image, ImageDraw, ImageFont

W, H = 1080, 2400
BG = (18, 24, 28)
ACCENT = (0, 106, 96)      # LogSleuth primary teal
ACCENT_BRIGHT = (112, 215, 200)
TEXT = (200, 208, 214)
TEXT_DIM = (110, 118, 124)

SHOTS = [
    ("01-live-stream.png", "Live logcat stream", "实时日志流", "stream"),
    ("02-filters.png", "Filters & presets", "过滤器与预设", "filter"),
    ("03-crashes.png", "Crash & ANR detection", "崩溃与 ANR 侦测", "crash"),
    ("04-sessions.png", "Recording sessions", "录制会话", "record"),
    ("05-session-replay.png", "Session replay", "会话回放", "replay"),
    ("06-sdk-sample.png", "Embedded SDK sample", "内嵌 SDK 示例", "sdk"),
]


def load_font(size, bold=True, cjk=False):
    if cjk:
        for path in (
            "/System/Library/Fonts/PingFang.ttc",
            "/System/Library/Fonts/Hiragino Sans GB.ttc",
            "/System/Library/Fonts/STHeiti Light.ttc",
        ):
            try:
                return ImageFont.truetype(path, size)
            except (OSError, IOError):
                continue
    for path in (
        "/System/Library/Fonts/Helvetica.ttc",
        "/System/Library/Fonts/SFNSMono.ttf",
        "/System/Library/Fonts/Menlo.ttc",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ):
        try:
            return ImageFont.truetype(path, size)
        except (OSError, IOError):
            continue
    return ImageFont.load_default()


def draw_icon(d, kind, cx, cy, r):
    """Draw a simple line icon centered at (cx, cy) with radius r."""
    w = max(6, r // 14)
    if kind == "stream":
        for i, frac in enumerate((-0.5, -0.1, 0.3)):
            y = cy + int(r * frac)
            d.line((cx - r // 2, y, cx + r // 2 - i * 14, y), fill=ACCENT_BRIGHT, width=w)
        d.ellipse((cx + r // 4, cy + r // 3, cx + r // 2, cy + r // 2 + r // 6), outline=ACCENT_BRIGHT, width=w)
    elif kind == "filter":
        pts = [(cx - r // 2, cy - r // 2), (cx + r // 2, cy - r // 2), (cx + 10, cy), (cx + 10, cy + r // 2), (cx - 10, cy + r // 3), (cx - 10, cy)]
        d.polygon(pts, outline=ACCENT_BRIGHT, width=w)
    elif kind == "crash":
        d.ellipse((cx - r // 2, cy - r // 3, cx + r // 2, cy + r // 3), outline=(255, 120, 110), width=w)
        for dx in (-r // 3, 0, r // 3):
            d.line((cx + dx, cy - r // 2, cx + dx, cy - r // 3 + 6), fill=(255, 120, 110), width=w)
        d.line((cx - r // 2 - 8, cy, cx - r // 2 + 4, cy), fill=(255, 120, 110), width=w)
        d.line((cx + r // 2 - 4, cy, cx + r // 2 + 8, cy), fill=(255, 120, 110), width=w)
    elif kind == "record":
        d.ellipse((cx - r // 2, cy - r // 2, cx + r // 2, cy + r // 2), outline=(255, 90, 90), width=w)
        d.ellipse((cx - r // 4, cy - r // 4, cx + r // 4, cy + r // 4), fill=(255, 90, 90))
    elif kind == "replay":
        d.ellipse((cx - r // 2, cy - r // 2, cx + r // 2, cy + r // 2), outline=ACCENT_BRIGHT, width=w)
        d.polygon([(cx - r // 6, cy - r // 4), (cx - r // 6, cy + r // 4), (cx + r // 3, cy)], fill=ACCENT_BRIGHT)
    else:  # sdk
        d.rounded_rectangle((cx - r // 2, cy - r // 2, cx + r // 2, cy + r // 2), radius=r // 6, outline=ACCENT_BRIGHT, width=w)
        d.text((cx, cy), "</>", font=load_font(r // 2), fill=ACCENT_BRIGHT, anchor="mm")


def make_placeholder(fname, title_en, title_zh, kind):
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)

    # Subtle grid
    for x in range(0, W, 90):
        d.line((x, 0, x, H), fill=(24, 31, 36), width=1)
    for y in range(0, H, 90):
        d.line((0, y, W, y), fill=(24, 31, 36), width=1)

    # Top status-bar hint + accent header bar (mimic app bar)
    d.rectangle((0, 0, W, 240), fill=(14, 19, 22))
    d.rectangle((0, 240, W, 248), fill=ACCENT)
    d.text((60, 120), "LogSleuth", font=load_font(64), fill=(235, 240, 242), anchor="lm")

    # Dashed placeholder frame
    fx0, fy0, fx1, fy1 = 80, 420, W - 80, H - 420
    dash = 26
    for x in range(fx0, fx1, dash * 2):
        d.line((x, fy0, min(x + dash, fx1), fy0), fill=TEXT_DIM, width=4)
        d.line((x, fy1, min(x + dash, fx1), fy1), fill=TEXT_DIM, width=4)
    for y in range(fy0, fy1, dash * 2):
        d.line((fx0, y, fx0, min(y + dash, fy1)), fill=TEXT_DIM, width=4)
        d.line((fx1, y, fx1, min(y + dash, fy1)), fill=TEXT_DIM, width=4)

    # Icon
    draw_icon(d, kind, W // 2, (fy0 + fy1) // 2 - 160, 150)

    # Texts
    d.text((W // 2, (fy0 + fy1) // 2 + 40), title_en, font=load_font(72), fill=TEXT, anchor="mm")
    d.text((W // 2, (fy0 + fy1) // 2 + 150), title_zh, font=load_font(56, cjk=True), fill=TEXT_DIM, anchor="mm")
    d.text((W // 2, fy1 - 90), "PLACEHOLDER — replace docs/screenshots/" + fname,
           font=load_font(34), fill=(70, 78, 84), anchor="mm")

    img.save("docs/screenshots/" + fname, "PNG", optimize=True)
    print("wrote", fname)


for fname, en, zh, kind in SHOTS:
    make_placeholder(fname, en, zh, kind)
