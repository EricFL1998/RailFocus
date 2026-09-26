"""Comprehensive data audit for rail_focus.db (final).

Checks:
 1. dangling edges
 2. same-direction duplicate edges
 3. asymmetric bidirectional distances
 4. nominal < straight*0.9 - 3 (physically impossible)
 5. nominal > straight*3 + >30km (exaggerated)
 6. same-name stations in different places (minus intentional splits)
 7. stations far from declared city centroid (minus huge prefectures)
 8. long line edges skipping a same-line station on the corridor
"""
import math
import sqlite3
import sys
from collections import defaultdict

sys.stdout.reconfigure(encoding="utf-8")

db = "app/src/main/assets/databases/rail_focus.db"
conn = sqlite3.connect(db)
conn.row_factory = sqlite3.Row
c = conn.cursor()


def hav(a, b):
    R = 6371.0
    p1, p2 = math.radians(a[0]), math.radians(b[0])
    dp, dl = math.radians(b[0] - a[0]), math.radians(b[1] - a[1])
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * R * math.asin(math.sqrt(h))


EXEMPT_PREFIX = (
    "青藏铁路", "和若铁路", "格库铁路", "临哈铁路", "南疆铁路", "北疆铁路",
    "敦格铁路", "哈罗铁路", "嘉策铁路", "额哈铁路", "川藏铁路", "拉林铁路",
    "丽香铁路", "新藏铁路", "哈木铁路", "柴木铁路",
)
EXEMPT_NAMES = {
    "米兰", "若羌", "且末", "民丰", "于田", "策勒", "洛浦", "和田",
    "花土沟", "茫崖", "冷湖", "大柴旦", "格尔木", "那曲", "拉萨",
    "日喀则", "林芝", "香格里拉", "丽江", "敦煌", "阿克塞", "肃北",
    "罗布泊", "哈密", "吐鲁番", "库尔勒", "喀什", "阿克苏", "阿拉尔",
    "德令哈", "乌兰", "海晏", "门源", "民乐", "山丹", "吐哈",
    "五道梁", "沱沱河", "不冻泉", "望昆", "秀水河", "楚玛尔河",
    "风火山", "当雄", "羊八井", "达琼果", "乌玛塘", "通天河",
    "唐古拉", "雁石坪", "布强格", "布曲", "托居", "扎加藏布",
}
OK_DUP = {"蒙山"}
OK_FAR_NAMES = {"亚布力西", "兴山", "武威"}
SKIP_EXEMPT_LINES = {"沪乍杭高铁"}  # under construction, provisional coordinates
OK_FAR_CITIES = {
    "重庆", "海西", "巴音郭楞", "和田", "那曲", "喀什", "阿克苏",
    "甘孜", "阿坝", "凉山", "日喀则", "林芝", "昌都", "伊犁",
    "塔城", "阿勒泰", "博尔塔拉", "巴州", "锡林郭勒", "阿拉善",
    "呼伦贝尔", "兴安盟", "通辽", "赤峰", "吐鲁番", "哈密",
    "固原", "中卫", "百色", "河池", "崇左", "文山", "怒江",
    "迪庆", "临沧", "普洱", "西双版纳", "汉中", "安康", "商洛",
    "娄底", "怀化", "张家界", "恩施", "湘西", "柳州", "桂林",
}


def is_exempt(line, na, nb):
    return (
        any(line.startswith(p) for p in EXEMPT_PREFIX)
        or na in EXEMPT_NAMES
        or nb in EXEMPT_NAMES
    )


stations = {r["id"]: dict(r) for r in c.execute("SELECT * FROM stations")}
edges = [dict(r) for r in c.execute("SELECT * FROM edges")]
print(f"stations={len(stations)} edges={len(edges)}")
issues = []

for e in edges:
    if e["from_id"] not in stations or e["to_id"] not in stations:
        issues.append(f"[dangling] {e['from_id']}->{e['to_id']} ({e['line']})")

seen = defaultdict(list)
for e in edges:
    seen[(e["from_id"], e["to_id"], e["line"])].append(e)
for key, lst in seen.items():
    if len(lst) > 1:
        issues.append(f"[dup-edge] {key[0]}->{key[1]} {key[2]} x{len(lst)}")

pairs = defaultdict(dict)
for e in edges:
    key = (frozenset([e["from_id"], e["to_id"]]), e["line"])
    pairs[key][e["from_id"]] = e["distance_km"]
for (key, line), d in pairs.items():
    if len(d) == 2:
        v = list(d.values())
        lo, hi = min(v), max(v)
        if hi > lo * 1.05 and hi - lo > 1.0:
            a, b = key
            issues.append(f"[asym] {a} <-> {b} {line}: {v[0]} vs {v[1]}")

for e in edges:
    a, b = stations.get(e["from_id"]), stations.get(e["to_id"])
    if not a or not b or e["distance_km"] <= 0:
        continue
    if e["line"] == "city_transfer" or is_exempt(e["line"], a["name"], b["name"]):
        continue
    s = hav((a["lat"], a["lon"]), (b["lat"], b["lon"]))
    if e["distance_km"] < s * 0.9 - 3:
        issues.append(
            f"[short] {a['name']}->{b['name']} {e['line']}: nominal={e['distance_km']} straight={s:.1f}")
    elif s > 10 and e["distance_km"] > 3 * s and e["distance_km"] > 30:
        issues.append(
            f"[exag] {a['name']}->{b['name']} {e['line']}: nominal={e['distance_km']} straight={s:.1f}")

byname = defaultdict(list)
for s in stations.values():
    byname[s["name"]].append(s)
for name, lst in byname.items():
    if len(lst) > 1 and name not in OK_DUP:
        locs = {(s["province"], s["city"]) for s in lst}
        if len(locs) > 1:
            issues.append(f"[dup-station] {name}: {sorted(locs)}")

cc = defaultdict(list)
for s in stations.values():
    cc[(s["province"], s["city"])].append((s["lat"], s["lon"]))
cen = {k: (sum(p[0] for p in v) / len(v), sum(p[1] for p in v) / len(v)) for k, v in cc.items()}
for s in stations.values():
    if s["city"] in OK_FAR_CITIES or s["name"] in OK_FAR_NAMES:
        continue
    cenp = cen.get((s["province"], s["city"]))
    if cenp:
        d = hav((s["lat"], s["lon"]), cenp)
        if d > 130:
            issues.append(
                f"[far-city] {s['name']} ({s['province']}/{s['city']}) {d:.0f}km from centroid")

RAIL_SKIP = {"city_transfer", "city_rail", "regional_link"}
line_stations = defaultdict(set)
for e in edges:
    if e["line"] in RAIL_SKIP or e["line"] in SKIP_EXEMPT_LINES:
        continue
    line_stations[e["line"]].add(e["from_id"])
    line_stations[e["line"]].add(e["to_id"])


def pt_seg(p, a, b):
    lat0 = math.radians((a[0] + b[0]) / 2)
    kx, ky = 111.32 * math.cos(lat0), 110.57
    ax, ay, bx, by = a[1] * kx, a[0] * ky, b[1] * kx, b[0] * ky
    px, py = p[1] * kx, p[0] * ky
    dx, dy = bx - ax, by - ay
    L2 = dx * dx + dy * dy
    if L2 == 0:
        return 999, 0
    t = ((px - ax) * dx + (py - ay) * dy) / L2
    cx, cy = ax + max(0, min(1, t)) * dx, ay + max(0, min(1, t)) * dy
    return math.hypot(px - cx, py - cy), t


for e in edges:
    if e["line"] in RAIL_SKIP or e["line"] in SKIP_EXEMPT_LINES:
        continue
    a, b = stations.get(e["from_id"]), stations.get(e["to_id"])
    if not a or not b:
        continue
    if is_exempt(e["line"], a["name"], b["name"]):
        continue
    s = hav((a["lat"], a["lon"]), (b["lat"], b["lon"]))
    if s < 60:
        continue
    for sid in line_stations[e["line"]]:
        if sid in (e["from_id"], e["to_id"]):
            continue
        st = stations[sid]
        d, t = pt_seg((st["lat"], st["lon"]), (a["lat"], a["lon"]), (b["lat"], b["lon"]))
        if d < 12 and 0.05 < t < 0.95:
            issues.append(
                f"[skip] {a['name']}->{b['name']} {e['line']} ({s:.0f}km) passes {st['name']}")
            break

print(f"\n=== {len(issues)} issues ===")
for i in sorted(set(issues)):
    print(i)
conn.close()
