#!/usr/bin/env python3
"""
Download Gaode (AutoNavi) Chinese raster tiles for the China region,
apply a light/monochrome filter, and save them into app/src/main/assets/tiles.

Zoom range: 4-9 (country -> province -> city view).
Bounds: lat 18-54, lon 73-136 (China + small buffer).
"""
import math
import concurrent.futures
import urllib.request
from pathlib import Path
from PIL import Image

# China + small buffer
LAT_NORTH = 54.0
LAT_SOUTH = 18.0
LON_EAST = 136.0
LON_WEST = 73.0

# Zoom range to bundle
MIN_ZOOM = 4
MAX_ZOOM = 9

USER_AGENT = "RailFocus Tile Downloader (offline bundle)"
MAX_WORKERS = 24

PROJECT_ROOT = Path(__file__).resolve().parent.parent
ASSET_TILE_DIR = PROJECT_ROOT / "app" / "src" / "main" / "assets" / "tiles"


def tile_bounds_for_region(lat_north, lat_south, lon_east, lon_west, zoom):
    """Return min/max x/y tile indices that cover the given lat/lon bounds at zoom."""
    n = 2 ** zoom

    def lat2y(lat):
        lat_rad = math.radians(lat)
        return int((1 - math.asinh(math.tan(lat_rad)) / math.pi) / 2 * n)

    def lon2x(lon):
        return int((lon + 180) / 360 * n)

    min_x = max(0, lon2x(lon_west))
    max_x = min(n - 1, lon2x(lon_east))
    max_y = min(n - 1, lat2y(lat_south))
    min_y = max(0, lat2y(lat_north))
    return min_x, max_x, min_y, max_y


def style_tile(img: Image.Image, z: int) -> Image.Image:
    """
    Convert the tile toward a clean light style with clearly visible gray road lines.
    Reference: dark-gray road grid on a near-white background.
    """
    # Convert to grayscale
    gray = img.convert("L")

    # Increase contrast and shift toward strong light/dark separation.
    # Midtones (roads, labels) become darker; near-whites stay light.
    enhanced = gray.point(lambda p: max(0, min(255, (p - 120) * 2.2 + 180)))

    # Convert back to RGB
    rgb = enhanced.convert("RGB")

    # Blend only slightly toward a clean white background so roads remain crisp
    bg = Image.new("RGB", rgb.size, (250, 250, 250))
    blended = Image.blend(rgb, bg, alpha=0.15)

    return blended


def download_tile(z, x, y):
    subdomains = ["1", "2", "3", "4"]
    url = f"https://webrd0{subdomains[(x + y) % 4]}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}"
    out_path = ASSET_TILE_DIR / str(z) / str(x) / f"{y}.png"
    if out_path.exists():
        return True, z, x, y, None

    try:
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Referer": "https://www.amap.com/"})
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = resp.read()
        if not data or len(data) < 100:
            return False, z, x, y, "empty response"

        img = Image.open(BytesIO(data))
        styled = style_tile(img, z)

        out_path.parent.mkdir(parents=True, exist_ok=True)
        styled.save(out_path, "PNG", optimize=True)
        return True, z, x, y, None
    except Exception as e:
        return False, z, x, y, str(e)


def main():
    ASSET_TILE_DIR.mkdir(parents=True, exist_ok=True)

    tiles = []
    for z in range(MIN_ZOOM, MAX_ZOOM + 1):
        min_x, max_x, min_y, max_y = tile_bounds_for_region(
            LAT_NORTH, LAT_SOUTH, LON_EAST, LON_WEST, z
        )
        for x in range(min_x, max_x + 1):
            for y in range(min_y, max_y + 1):
                tiles.append((z, x, y))

    print(f"Total tiles to download: {len(tiles)}")
    print(f"Output directory: {ASSET_TILE_DIR}")

    success = 0
    failed = 0
    with concurrent.futures.ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
        futures = [executor.submit(download_tile, z, x, y) for (z, x, y) in tiles]
        for i, future in enumerate(concurrent.futures.as_completed(futures), 1):
            ok, z, x, y, err = future.result()
            if ok:
                success += 1
            else:
                failed += 1
                print(f"[FAIL] z={z} x={x} y={y}: {err}")
            if i % 100 == 0 or i == len(tiles):
                print(f"Progress: {i}/{len(tiles)} (success={success}, failed={failed})")

    print(f"Done. Success={success}, Failed={failed}")


if __name__ == "__main__":
    from io import BytesIO
    main()
