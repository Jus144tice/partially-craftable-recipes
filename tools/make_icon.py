"""One-off generator for the mod icon. Draws a 2x2 crafting grid that is *partially* filled —
two emerald "have" cells on the diagonal and two ghosted "missing" cells — the
partially-craftable motif (sibling to bedrock-crafting-controls' 3x3 grid and
bedrock-line-placement's locked block row). Supersampled for clean edges."""
from PIL import Image, ImageDraw, ImageFilter

S = 4  # supersampling factor
N = 256  # final size
W = N * S

img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
d = ImageDraw.Draw(img)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def sx(v):
    """Scale a value expressed in final (256px) units up to the supersampled canvas."""
    return v * S


# --- shared palette with the sibling mods -----------------------------------
top = (38, 42, 56)
bot = (24, 27, 38)
accent = (61, 199, 142)  # emerald highlight ("have")
accent_dark = (38, 150, 104)
neutral = (62, 69, 92)
neutral_hi = (78, 86, 112)

# --- background: vertical gradient inside a rounded square ------------------
bg = Image.new("RGBA", (W, W), (0, 0, 0, 0))
bgd = ImageDraw.Draw(bg)
for y in range(W):
    bgd.line([(0, y), (W, y)], fill=lerp(top, bot, y / W) + (255,))
mask = Image.new("L", (W, W), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, W - 1, W - 1], radius=sx(52), fill=255)
img.paste(bg, (0, 0), mask)

# subtle border
d.rounded_rectangle(
    [sx(2), sx(2), W - sx(2), W - sx(2)], radius=sx(50), outline=(70, 78, 104, 255), width=sx(2)
)

# --- 2x2 grid geometry (final 256px units, scaled by sx) --------------------
cell = 66
gap = 16
radius = 13
grid = 2 * cell + gap
margin = (N - grid) / 2  # centre the grid

# Diagonal cells are "have" (emerald); the off-diagonal are "missing" (ghost).
HAVE = {(0, 0), (1, 1)}


def draw_have(x0, y0):
    x1, y1 = x0 + cell, y0 + cell
    # glow behind the cell
    glow = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    ImageDraw.Draw(glow).rounded_rectangle(
        [sx(x0 - 8), sx(y0 - 8), sx(x1 + 8), sx(y1 + 8)], radius=sx(radius + 6), fill=accent + (95,)
    )
    glow = glow.filter(ImageFilter.GaussianBlur(sx(6)))
    img.alpha_composite(glow)
    # vertical gradient fill
    cw = int(sx(cell))
    cellimg = Image.new("RGBA", (cw, cw), (0, 0, 0, 0))
    cd = ImageDraw.Draw(cellimg)
    for yy in range(cw):
        cd.line([(0, yy), (cw, yy)], fill=lerp(accent, accent_dark, yy / cw) + (255,))
    cmask = Image.new("L", (cw, cw), 0)
    ImageDraw.Draw(cmask).rounded_rectangle([0, 0, cw - 1, cw - 1], radius=sx(radius), fill=255)
    img.paste(cellimg, (int(sx(x0)), int(sx(y0))), cmask)
    # check-mark to read as "have"
    cx, cy = x0 + cell / 2, y0 + cell / 2
    pts = [
        (sx(cx - cell * 0.22), sx(cy + cell * 0.02)),
        (sx(cx - cell * 0.04), sx(cy + cell * 0.20)),
        (sx(cx + cell * 0.26), sx(cy - cell * 0.20)),
    ]
    stroke = sx(9)
    d.line(pts, fill=(255, 255, 255, 255), width=int(stroke), joint="curve")
    r = stroke / 2
    for px, py in pts:
        d.ellipse([px - r, py - r, px + r, py + r], fill=(255, 255, 255, 255))


def draw_missing(x0, y0):
    x1, y1 = x0 + cell, y0 + cell
    # faint ghost fill + dim outline = an empty/needed slot
    d.rounded_rectangle([sx(x0), sx(y0), sx(x1), sx(y1)], radius=sx(radius), fill=neutral + (70,))
    d.rounded_rectangle(
        [sx(x0), sx(y0), sx(x1), sx(y1)], radius=sx(radius), outline=neutral_hi + (255,), width=sx(3)
    )
    # small dim plus sign = "still needed"
    cx, cy = x0 + cell / 2, y0 + cell / 2
    arm = cell * 0.18
    pw = sx(7)
    d.line([sx(cx - arm), sx(cy), sx(cx + arm), sx(cy)], fill=neutral_hi + (255,), width=int(pw))
    d.line([sx(cx), sx(cy - arm), sx(cx), sx(cy + arm)], fill=neutral_hi + (255,), width=int(pw))


for row in range(2):
    for col in range(2):
        x0 = margin + col * (cell + gap)
        y0 = margin + row * (cell + gap)
        if (col, row) in HAVE:
            draw_have(x0, y0)
        else:
            draw_missing(x0, y0)

# --- downscale --------------------------------------------------------------
out = img.resize((N, N), Image.LANCZOS)
out.save(r"C:\Users\jenny\partially-craftable-recipes\src\main\resources\partiallycraftablerecipes.png")
print("wrote icon")
