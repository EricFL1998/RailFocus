"""从 OpenMapTiles 源数据（scripts/osm_data/rail_focus_v2.mbtiles）裁剪 App 用的离线矢量瓦片。

只保留底图需要的图层：地名（place，仅 city/town）、道路（transportation）、
水系（waterway 的 class、water）、行政边界（boundary 的 admin_level）。

⚠️ 地名只能由「本机字体能光栅化的字符」组成。样式里 glyphs 指向不存在的
asset://glyphs/{fontstack}/{range}.pbf，中文全靠 MapLibreView 的
localIdeographFontFamily("sans-serif") 在本地光栅化。任何落在该系统字体覆盖范围外的
字符（拉丁字母、西里尔、泰文、缅甸文、傣仂文、空格、斜杠、数字、U+200E 等）都会让
该瓦片的 symbol 图层解析失败，**整块瓦片渲染成空白**（实测 z4 的一条
"香港 Hong Kong" 就吃掉整块瓦片）。所以这里按 GOOD_RANGES 白名单过滤地名，
过滤后为空的要素直接丢弃。

用法：python scripts/strip_vector_tiles.py
"""

import gzip
import re
import shutil
import sqlite3
import sys
from pathlib import Path

import mapbox_vector_tile as mvt

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / 'scripts' / 'osm_data' / 'rail_focus_v2.mbtiles'
OUT = ROOT / 'app' / 'src' / 'main' / 'assets' / 'tiles_vector'
MIN_ZOOM = 0
MAX_ZOOM = 10

KEEP = {
    'place': {'name', 'class', 'rank'},
    'transportation': {'class'},
    'waterway': {'class'},
    'water': set(),
    'boundary': {'admin_level'},
}
PLACE_CLASSES = {'city', 'town'}

# 本地字形光栅化（localIdeographFontFamily）覆盖的码位区间。
# 只有这些字符能不经 glyphs PBF 直接渲染，其余一律剔除。
GOOD_RANGES = [
    (0x3000, 0x303F),   # CJK 标点
    (0x3040, 0x309F),   # 平假名
    (0x30A0, 0x30FF),   # 片假名
    (0x3130, 0x318F),   # 韩文兼容字母
    (0x3400, 0x4DBF),   # CJK 扩展 A
    (0x4E00, 0x9FFF),   # CJK 基本区
    (0xAC00, 0xD7AF),   # 韩文音节
    (0xF900, 0xFAFF),   # CJK 兼容表意文字
    (0xFF00, 0xFFEF),   # 全角/半角形式
]


def name_is_good(ch: str) -> bool:
    o = ord(ch)
    return any(lo <= o <= hi for lo, hi in GOOD_RANGES)


def sanitize(name: str) -> str:
    """去掉无法本地光栅化的字符；空白类字符一并去掉（中文地名不需要空格）。"""
    if not name:
        return ''
    s = ''.join(c for c in name if name_is_good(c))
    return re.sub(r'\s+', '', s).strip()


def main() -> int:
    if not SRC.exists():
        print('source mbtiles not found:', SRC)
        return 1
    if OUT.exists():
        shutil.rmtree(OUT)

    con = sqlite3.connect(str(SRC))
    rows = con.execute(
        'select zoom_level, tile_column, tile_row, tile_data from tiles '
        'where zoom_level between ? and ?', (MIN_ZOOM, MAX_ZOOM)).fetchall()
    print('source tiles:', len(rows), flush=True)

    written = 0
    total_bytes = 0
    dropped_place = 0
    emptied_name = 0
    renamed = 0
    skipped = 0

    for z, x, y, data in rows:
        if data[:2] == bytes([0x1f, 0x8b]):
            data = gzip.decompress(data)
        tile = mvt.decode(data, y_coord_down=True)

        layers = []
        for layer_name, layer in tile.items():
            keep = KEEP.get(layer_name)
            if keep is None:
                continue
            feats = []
            for feat in layer['features']:
                props = {k: v for k, v in feat.get('properties', {}).items() if k in keep}
                if layer_name == 'place':
                    if props.get('class') not in PLACE_CLASSES:
                        dropped_place += 1
                        continue
                    lon, lat = feat['geometry']['coordinates'][:2]
                    if not (0 <= lon <= 4096 and 0 <= lat <= 4096):
                        dropped_place += 1
                        continue
                    cleaned = sanitize(props.get('name', ''))
                    if not cleaned:
                        # 名字全是不支持的文字（如 Замын-Үүд），留给系统也只会渲染成空白
                        emptied_name += 1
                        continue
                    if cleaned != props.get('name'):
                        renamed += 1
                    props['name'] = cleaned
                feats.append({
                    'geometry': feat['geometry'],
                    'properties': props,
                    'id': feat.get('id', 0),
                    'type': feat.get('type'),
                })
            if feats:
                layers.append({'name': layer_name, 'features': feats})

        if not layers:
            skipped += 1
            continue

        blob = mvt.encode(layers, y_coord_down=True)
        out_y = (1 << z) - 1 - y  # mbtiles 用 TMS 行号，磁盘上用 XYZ
        d = OUT / str(z) / str(x)
        d.mkdir(parents=True, exist_ok=True)
        (d / (str(out_y) + '.pbf')).write_bytes(blob)
        written += 1
        total_bytes += len(blob)

    print('tiles written:', written, 'skipped(empty):', skipped)
    print('place dropped(class/coord):', dropped_place,
          'dropped(no renderable name):', emptied_name, 'names sanitized:', renamed)
    print('total bytes:', total_bytes)

    # 自检：产物里不允许再出现无法本地光栅化的字符
    bad = 0
    checked = 0
    for pbf in OUT.rglob('*.pbf'):
        raw = pbf.read_bytes()
        if raw[:2] == bytes([0x1f, 0x8b]):
            raw = gzip.decompress(raw)
        checked += 1
        t = mvt.decode(raw, y_coord_down=True)
        for f in t.get('place', {}).get('features', []):
            n = f.get('properties', {}).get('name') or ''
            if any(not name_is_good(c) for c in n):
                print('  BAD NAME', pbf, repr(n))
                bad += 1
    print('self-check: tiles=%d bad_names=%d' % (checked, bad))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
