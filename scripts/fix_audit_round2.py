"""Round-2 comprehensive data fixes:
1. station coordinate fixes
2. split ambiguous 清河 (邢台 vs 北京) and rewire edges
3. distance normalization (nominal := haversine when physically impossible/exaggerated)
4. unify asymmetric bidirectional distances
5. delete skip-station edges when a subpath through the skipped station exists
"""
import math
import shutil
import sqlite3
import sys
from collections import defaultdict

sys.stdout.reconfigure(encoding="utf-8")

db = "app/src/main/assets/databases/rail_focus.db"
shutil.copyfile(db, db + ".bak_round2")
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


def exempt(line, a_name, b_name):
    return (
        any(line.startswith(p) for p in EXEMPT_PREFIX)
        or a_name in EXEMPT_NAMES
        or b_name in EXEMPT_NAMES
    )


# ---------- 1. coordinate fixes ----------
COORD_FIXES = {
    "岐山": (34.45, 107.75),      # was in Sichuan
    "怀仁东": (39.79, 113.12),    # 55km off
    "吐哈": (43.00, 90.95),       # was merged with 鄯善北 area
    "百色": (23.90, 106.62),      # 120km off in longitude
    "萧县北": (34.19, 116.94),    # was placed next to 徐州东
    "秦安": (34.86, 105.68),      # 50km off in latitude
    "普者黑": (24.05, 104.19),
    "三门峡南": (34.70, 111.10),
}
n = 0
for name, (lat, lon) in COORD_FIXES.items():
    cur = c.execute("SELECT lat, lon FROM stations WHERE id=?", (name,)).fetchone()
    if cur:
        c.execute("UPDATE stations SET lat=?, lon=? WHERE id=?", (lat, lon, name))
        n += 1
        print(f"coord {name}: ({cur['lat']:.3f},{cur['lon']:.3f}) -> ({lat},{lon})")
conn.commit()

# ---------- 2. split 清河 ----------
# id '清河' = 邢台清河县; id '清河站' = 北京清河(京张高铁)
c.execute("UPDATE stations SET id='清河城', name='清河城', display_name='清河城站' WHERE id='清河'")
moved = 0
for row in c.execute("SELECT id, from_id, to_id, line FROM edges WHERE from_id='清河' OR to_id='清河'").fetchall():
    if row["from_id"] == "清河":
        c.execute("UPDATE edges SET from_id='清河城' WHERE id=?", (row["id"],))
        moved += 1
    if row["to_id"] == "清河":
        c.execute("UPDATE edges SET to_id='清河城' WHERE id=?", (row["id"],))
        moved += 1
print(f"清河: renamed 邢台 station to 清河城, retargeted {moved} edges")
conn.commit()

# ---------- 3. distance normalization ----------
stations = {r["id"]: (r["lat"], r["lon"], r["name"]) for r in c.execute("SELECT id, lat, lon, name FROM stations")}
fixed_short = fixed_exag = 0
rows = c.execute("SELECT * FROM edges").fetchall()
for e in rows:
    a, b = stations.get(e["from_id"]), stations.get(e["to_id"])
    if not a or not b or e["distance_km"] <= 0:
        continue
    if exempt(e["line"], a[2], b[2]):
        continue
    s = hav(a[:2], b[:2])
    d = e["distance_km"]
    if d < s * 0.9 - 3:
        c.execute("UPDATE edges SET distance_km=? WHERE id=?", (round(s, 1), e["id"]))
        fixed_short += 1
    elif s > 10 and d > 3 * s and d > 30:
        c.execute("UPDATE edges SET distance_km=? WHERE id=?", (round(s, 1), e["id"]))
        fixed_exag += 1
print(f"distance: normalized {fixed_short} shorter-than-straight, {fixed_exag} exaggerated")
conn.commit()

# ---------- 4. unify asymmetric bidirectional distances ----------
pairs = defaultdict(dict)
for e in c.execute("SELECT * FROM edges").fetchall():
    pairs[(frozenset([e["from_id"], e["to_id"]]), e["line"])][e["from_id"]] = (
        e["id"], e["distance_km"])
unified = 0
for (key, line), d in pairs.items():
    if len(d) != 2:
        continue
    items = list(d.items())
    (id1, (eid1, v1)), (id2, (eid2, v2)) = items
    if abs(v1 - v2) < 1 or (max(v1, v2) <= 1.05 * min(v1, v2)):
        continue
    ids = list(key)
    a, b = stations.get(ids[0]), stations.get(ids[1])
    if not a or not b:
        continue
    s = hav(a[:2], b[:2])
    def score(v):
        if 0.5 * s <= v <= 5 * s:
            return abs(math.log(v / max(s, 0.1)))
        return 999
    chosen = v1 if score(v1) <= score(v2) else v2
    if score(chosen) == 999:
        chosen = round(s, 1)
    c.execute("UPDATE edges SET distance_km=? WHERE id=?", (chosen, eid1))
    c.execute("UPDATE edges SET distance_km=? WHERE id=?", (chosen, eid2))
    unified += 1
print(f"asym: unified {unified} bidirectional pairs")
conn.commit()

# ---------- 5. delete skip-station edges when subpath exists ----------
RAIL_LINES = {"city_transfer", "city_rail", "regional_link"}


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


edges = [dict(r) for r in c.execute("SELECT * FROM edges")]
adj = defaultdict(set)  # undirected rail adjacency (any rail line)
for e in edges:
    if e["line"] in RAIL_LINES:
        continue
    adj[e["from_id"]].add(e["to_id"])
    adj[e["to_id"]].add(e["from_id"])

line_stations = defaultdict(set)
for e in edges:
    if e["line"] in RAIL_LINES:
        continue
    line_stations[e["line"]].add(e["from_id"])
    line_stations[e["line"]].add(e["to_id"])

deleted, kept = 0, []
for e in edges:
    if e["line"] in RAIL_LINES:
        continue
    a, b = stations.get(e["from_id"]), stations.get(e["to_id"])
    if not a or not b:
        continue
    if exempt(e["line"], a[2], b[2]):
        continue
    s = hav(a[:2], b[:2])
    if s < 60:
        continue
    pa, pb = a[:2], b[:2]
    hit = None
    for sid in line_stations[e["line"]]:
        if sid in (e["from_id"], e["to_id"]):
            continue
        st = stations[sid]
        d, t = pt_seg(st[:2], pa, pb)
        if d < 12 and 0.05 < t < 0.95:
            hit = sid
            break
    if not hit:
        continue
    if e["to_id"] in adj.get(hit, ()) and e["from_id"] in adj.get(hit, ()):
        c.execute("DELETE FROM edges WHERE id=?", (e["id"],))
        deleted += 1
    else:
        kept.append(f"{a[2]}->{b[2]} {e['line']} via {stations[hit][2]}")
print(f"skip: deleted {deleted} edges with subpath, {len(kept)} kept for review")
for k in kept:
    print("  keep:", k)
conn.commit()
conn.close()
