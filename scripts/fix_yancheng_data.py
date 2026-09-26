#!/usr/bin/env python3
"""
修复盐城地区铁路数据问题：

1. 删除错误站点「滨海」——该记录标注为江苏盐城，坐标却是天津滨海站
   (39.17, 117.39)，导致生成 盐城↔滨海 688km 等荒谬的同城换乘边。
   盐城市滨海县的真实车站是「滨海港站」（连盐铁路），本次一并补齐。
2. 拆分「大丰」站点 id 冲突——「大丰」实为两站同名：
   - 湖南株洲大丰站（长株潭城际），坐标 27.90,113.01 本就是株洲的；
   - 江苏盐城大丰站（盐通高铁），原先错误地与株洲站共用同一条记录，
     导致 盐城↔大丰 913km 等错误换乘边。
3. 补建连盐铁路缺失区段：灌南—响水—滨海港—阜宁东—射阳—盐城。

用法：python scripts/fix_yancheng_data.py
"""

import math
import os
import shutil
import sqlite3
import sys

sys.stdout.reconfigure(encoding="utf-8")

DB_PATH = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "app", "src", "main", "assets", "databases", "rail_focus.db"
)
BACKUP_PATH = DB_PATH + ".bak_yancheng"


def hav(lat1, lon1, lat2, lon2):
    r = 6371.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


# 新增站点：id, name, display_name, province, city, lat, lon, is_major, tier
NEW_STATIONS = [
    ("响水", "响水", "响水站", "江苏", "盐城", 34.20, 119.58, 0, 4),
    ("滨海港", "滨海港", "滨海港站", "江苏", "盐城", 34.00, 119.95, 0, 4),
    ("阜宁东", "阜宁东", "阜宁东站", "江苏", "盐城", 33.82, 119.88, 0, 4),
    ("射阳", "射阳", "射阳站", "江苏", "盐城", 33.75, 120.25, 0, 4),
    ("盐城大丰", "盐城大丰", "盐城大丰站", "江苏", "盐城", 33.20, 120.46, 0, 4),
]

# 连盐铁路新增区段（双向边由脚本生成）
LIANYAN_LEGS = [
    ("灌南", "响水"),
    ("响水", "滨海港"),
    ("滨海港", "阜宁东"),
    ("阜宁东", "射阳"),
    ("射阳", "盐城"),
]


def main():
    print(f"数据库路径: {os.path.abspath(DB_PATH)}")
    shutil.copy2(DB_PATH, BACKUP_PATH)
    print(f"已备份到 {BACKUP_PATH}")

    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()

    coords = {
        r["id"]: (r["lat"], r["lon"])
        for r in cur.execute("SELECT id, lat, lon FROM stations")
    }

    # 1. 处理错误的「滨海」站点（标注江苏盐城、坐标却是天津滨海站）
    #    站点 id「滨海」同时被天津的三条真实线路边引用（塘沽、唐山北站、
    #    唐山西站），这些边属于天津滨海站（id「滨海站」），先改指过去。
    cur.execute(
        "UPDATE edges SET from_id='滨海站' WHERE from_id='滨海'"
        " AND line IN ('京津城际','津秦高铁')"
    )
    cur.execute(
        "UPDATE edges SET to_id='滨海站' WHERE to_id='滨海'"
        " AND line IN ('京津城际','津秦高铁')"
    )
    print("天津方向 3 条线路边已改指「滨海站」")

    # 剩余引用均为盐城同城换乘错误边，连同站点一起删除
    n = cur.execute("SELECT COUNT(*) FROM edges WHERE from_id='滨海' OR to_id='滨海'").fetchone()[0]
    cur.execute("DELETE FROM edges WHERE from_id='滨海' OR to_id='滨海'")
    cur.execute("DELETE FROM stations WHERE id='滨海'")
    print(f"删除错误站点「滨海」及其 {n} 条边")

    # 2. 拆分大丰：恢复株洲大丰站身份，新建盐城大丰站
    cur.execute(
        "UPDATE stations SET province='湖南', city='株洲' WHERE id='大丰'"
    )
    print("「大丰」记录已恢复为湖南株洲大丰站（长株潭城际）")

    cur.executemany(
        "INSERT INTO stations (id, name, display_name, province, city, lat, lon,"
        " is_major, tier) VALUES (?,?,?,?,?,?,?,?,?)",
        NEW_STATIONS,
    )
    print(f"新增站点: {[s[0] for s in NEW_STATIONS]}")

    coords.update({s[0]: (s[5], s[6]) for s in NEW_STATIONS})

    # 3. 把原来指向错误「大丰」的盐城同城换乘边改指「盐城大丰」并重算距离
    cur.execute(
        "UPDATE edges SET to_id='盐城大丰', to_name='盐城大丰'"
        " WHERE to_id='大丰' AND line='city_transfer'"
    )
    cur.execute(
        "UPDATE edges SET from_id='盐城大丰', from_name='盐城大丰' WHERE from_id='大丰' AND line='city_transfer'"
    )
    fixed = 0
    for r in cur.execute(
        "SELECT id, from_id, to_id FROM edges"
        " WHERE from_id='盐城大丰' OR to_id='盐城大丰'"
    ).fetchall():
        a, b = coords.get(r["from_id"]), coords.get(r["to_id"])
        if r["from_id"] == "盐城大丰":
            a = (33.20, 120.46)
        if r["to_id"] == "盐城大丰":
            b = (33.20, 120.46)
        if a and b:
            d = round(hav(a[0], a[1], b[0], b[1]), 2)
            cur.execute("UPDATE edges SET distance_km=? WHERE id=?", (d, r["id"]))
            fixed += 1
    print(f"重接并重算 {fixed} 条盐城大丰换乘边")

    # 4. 补建连盐铁路缺失区段（双向）
    for a, b in LIANYAN_LEGS:
        d = round(hav(*coords[a], *coords[b]), 1) if a in coords else None
        if d is None:
            raise SystemExit(f"缺少站点坐标: {a}")
        for fa, fb, ta, tb in [(a, a, b, b), (b, b, a, a)]:
            cur.execute(
                "INSERT INTO edges (from_id, from_name, to_id, to_name, line,"
                " distance_km, source) VALUES (?,?,?,?,'连盐铁路',?,NULL)",
                (fa, fa, tb, tb, d),
            )
        print(f"连盐铁路: {a} <-> {b} ({d} km)")

    conn.commit()

    # 验证
    print()
    print("== 验证：盐城 现有边 ==")
    for r in cur.execute(
        "SELECT from_id, to_id, line, distance_km FROM edges"
        " WHERE from_id='盐城' ORDER BY line, to_id"
    ):
        print(f"  {r['from_id']} -> {r['to_id']}  [{r['line']}]  {r['distance_km']} km")

    n_s = cur.execute("SELECT COUNT(*) FROM stations").fetchone()[0]
    n_e = cur.execute("SELECT COUNT(*) FROM edges").fetchone()[0]
    print(f"stations: {n_s}, edges: {n_e}")
    conn.close()


if __name__ == "__main__":
    main()
