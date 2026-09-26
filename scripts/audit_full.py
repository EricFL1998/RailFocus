#!/usr/bin/env python3
"""全面审计 rail_focus.db 数据质量，输出分类问题清单。"""
import math
import sqlite3
import sys
from collections import defaultdict

sys.stdout.reconfigure(encoding="utf-8")

DB = r"app/src/main/assets/databases/rail_focus.db"


def hav(a, b):
    r = 6371.0
    p1, p2 = math.radians(a[0]), math.radians(b[0])
    dp = math.radians(b[0] - a[0])
    dl = math.radians(b[1] - a[1])
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(h))


conn = sqlite3.connect(DB)
conn.row_factory = sqlite3.Row
cur = conn.cursor()

stations = {r["id"]: r for r in cur.execute("SELECT * FROM stations")}
edges = cur.execute("SELECT * FROM edges").fetchall()

print(f"stations={len(stations)} edges={len(edges)}")

# ---- 1. 边引用了不存在的站点 ----
print("\n== 1. 引用缺失站点的边 ==")
bad = 0
for e in edges:
    if e["from_id"] not in stations or e["to_id"] not in stations:
        bad += 1
        if bad <= 30:
            print(f"  edge {e['id']}: {e['from_id']} -> {e['to_id']} [{e['line']}]")
print(f"  total: {bad}")

# ---- 2. 字段缺失 / 异常 ----
print("\n== 2. 站点字段缺失或异常 ==")
for r in stations.values():
    issues = []
    if r["lat"] is None or r["lon"] is None:
        issues.append("no coords")
    if not r["province"] or not r["city"]:
        issues.append("no province/city")
    if r["lat"] is not None and not (18 < r["lat"] < 54):
        issues.append(f"lat={r['lat']}")
    if r["lon"] is not None and not (73 < r["lon"] < 136):
        issues.append(f"lon={r['lon']}")
    if r["is_major"] not in (0, 1):
        issues.append(f"is_major={r['is_major']}")
    if r["tier"] is None or not (1 <= r["tier"] <= 6):
        issues.append(f"tier={r['tier']}")
    if issues:
        print(f"  {r['id']}: {', '.join(issues)}")

# ---- 3. 数字 id 站点 ----
print("\n== 3. 数字 id 的站点 ==")
for r in stations.values():
    if r["id"].isdigit():
        print(f"  id={r['id']} name={r['name']} {r['province']}/{r['city']} ({r['lat']},{r['lon']})")

# ---- 4. 同名多记录（潜在同名站冲突） ----
print("\n== 4. 同名站点（按 display_name 分组，多于一条） ==")
by_name = defaultdict(list)
for r in stations.values():
    by_name[r["name"]].append(r)
for name, rows in sorted(by_name.items()):
    if len(rows) > 1:
        locs = {(r["province"], r["city"]) for r in rows}
        if len(locs) > 1:
            print(f"  {name}: " + "; ".join(
                f"{r['id']}={r['province']}/{r['city']}" for r in rows))

# ---- 5. 坐标离群：站点距本市其他站点中位数过远 ----
print("\n== 5. 疑似坐标错误（距同城站点群中位距离 > 80km） ==")
by_city = defaultdict(list)
for r in stations.values():
    if r["lat"] is not None:
        by_city[(r["province"], r["city"])].append(r)
for (prov, city), rows in sorted(by_city.items()):
    if len(rows) < 3:
        continue
    center = (
        sum(r["lat"] for r in rows) / len(rows),
        sum(r["lon"] for r in rows) / len(rows),
    )
    for r in rows:
        d = hav((r["lat"], r["lon"]), center)
        if d > 80:
            print(f"  {r['id']:12s} {prov}/{city} ({r['lat']:.3f},{r['lon']:.3f})"
                  f" 距市中心 {d:.0f}km")

# ---- 6. 重复边（同 from/to/line） ----
print("\n== 6. 完全重复边（同 from/to/line） ==")
seen = defaultdict(list)
for e in edges:
    seen[(e["from_id"], e["to_id"], e["line"])].append(e)
n = 0
for k, v in seen.items():
    if len(v) > 1:
        n += 1
        if n <= 40:
            print(f"  {k[0]} -> {k[1]} [{k[2]}] x{len(v)}"
                  f" ids={[e['id'] for e in v]} d={[e['distance_km'] for e in v]}")
print(f"  total dup groups: {n}")

# ---- 7. 线路边缺反向 ----
print("\n== 7. 非同城换乘边缺反向（前 40） ==")
pair = {(e["from_id"], e["to_id"]) for e in edges}
n = 0
for e in edges:
    if e["line"] in ("city_transfer", "regional_link"):
        continue
    if (e["to_id"], e["from_id"]) not in pair:
        n += 1
        if n <= 40:
            print(f"  {e['from_id']} -> {e['to_id']} [{e['line']}] {e['distance_km']}km id={e['id']}")
print(f"  total: {n}")

conn.close()
