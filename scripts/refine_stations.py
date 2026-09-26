#!/usr/bin/env python3
"""
铁路数据精化：

1. 主要站点坐标精化：约 160 个 is_major 站点的坐标是直接用城市中心坐标
    （小数呈 .xx17/.xx33 模式），替换为真实车站坐标。
2. 单向线路边补反向边：约 400 条非换乘边只存了单向（RailGraph 建图时
    会自动补反向，但补齐后数据自洽，方便外部工具使用）。
3. 补建合武铁路（宁蓉线合武段）：新建金寨、麻城北、红安西站，
    并补 合肥—六安—金寨—麻城北—红安西—汉口 边。

用法：python scripts/refine_stations.py
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
BACKUP_PATH = DB_PATH + ".bak_refine"


def hav(a, b):
    r = 6371.0
    p1, p2 = math.radians(a[0]), math.radians(b[0])
    dp = math.radians(b[0] - a[0])
    dl = math.radians(b[1] - a[1])
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(h))


# 主要站点真实坐标：id -> (lat, lon)
STATION_COORDS = {
    "三亚": (18.29, 109.51), "东莞": (23.09, 113.87), "虎门": (22.82, 113.86),
    "东营": (37.45, 118.50), "中卫": (37.52, 105.19), "临汾": (36.09, 111.50),
    "临沂": (35.06, 118.34), "丹东": (40.13, 124.39), "乌海": (39.67, 106.82),
    "乌鲁木齐": (43.82, 87.58), "佛山": (23.03, 113.10), "佳木斯": (46.81, 130.34),
    "保定": (38.87, 115.48), "雄安": (39.00, 116.13), "包头": (40.66, 109.83),
    "北海": (21.48, 109.10), "南宁": (22.82, 108.32), "南宁东": (22.79, 108.35),
    "南昌西": (28.63, 115.87), "进贤南": (28.36, 116.24), "南通": (32.04, 120.84),
    "厦门北": (24.64, 118.07), "合肥": (31.88, 117.29), "合肥南": (31.79, 117.29),
    "吉林": (43.88, 126.56), "吴忠": (37.98, 106.19), "哈密": (42.83, 93.51),
    "哈尔滨": (45.76, 126.62), "哈尔滨西": (45.71, 126.59),
    "唐山": (39.63, 118.18), "商丘": (34.44, 115.65),
    "嘉兴南": (30.70, 120.77), "海宁西": (30.50, 120.43),
    "天水": (34.57, 105.90), "天津": (39.13, 117.21), "太原": (37.87, 112.59),
    "威海": (37.42, 122.11), "孝感北": (31.46, 114.12), "宁波": (29.87, 121.54),
    "安庆": (30.55, 117.05), "安顺西": (26.21, 105.90), "定西北": (35.61, 104.60),
    "丰城东": (28.19, 115.84), "宣城": (30.95, 118.75),
    "宿迁": (33.95, 118.28), "宿迁东": (33.94, 118.40),
    "岳阳东": (29.44, 113.22), "巴彦淖尔": (40.74, 107.41),
    "常州北": (31.81, 119.96), "庆盛": (22.87, 113.49), "庆阳": (35.72, 107.64),
    "开封": (34.79, 114.35), "张家口": (40.81, 114.88), "张掖": (38.93, 100.45),
    "怀化南": (27.55, 110.04), "新晃西": (27.32, 109.20),
    "溆浦南": (27.68, 110.55), "惠州": (23.11, 114.41),
    "天府机场": (30.31, 104.44), "承德": (40.97, 117.96),
    "抚州东": (28.23, 116.60), "揭阳": (23.54, 116.36), "新余北": (27.90, 114.95),
    "无锡东": (31.60, 120.30), "日照": (35.42, 119.53),
    "昆明": (25.02, 102.72), "昆明南": (24.87, 102.86),
    "昌吉": (44.02, 87.30), "晋中": (37.69, 112.74),
    "富源北": (25.68, 104.25), "曲靖北": (25.84, 103.82), "本溪": (41.30, 123.77),
    "临平南": (30.42, 120.30), "杭州南": (30.21, 120.28),
    "柳州": (24.32, 109.40), "桂林": (25.27, 110.28), "梅州": (24.29, 116.11),
    "汕头": (23.37, 116.68), "汕尾": (22.79, 115.34), "江门": (22.61, 113.10),
    "池州": (30.66, 117.48), "沈阳北": (41.82, 123.43), "沈阳": (41.79, 123.39),
    "沧州": (38.30, 116.84), "河源": (23.73, 114.69),
    "济南": (36.67, 116.99), "海口": (20.03, 110.29), "淄博": (36.80, 118.06),
    "淮安": (33.60, 119.03), "光明城": (22.74, 113.94),
    "深圳": (22.53, 114.12), "深圳北": (22.61, 114.03), "福田": (22.54, 114.06),
    "英德西": (24.20, 113.42), "滕州东": (35.10, 117.22), "潍坊": (36.71, 119.11),
    "潮州": (23.66, 116.63), "烟台": (37.55, 121.39), "焦作": (35.22, 113.24),
    "牡丹江": (44.58, 129.60), "玉山南": (28.68, 118.25), "玉林": (22.63, 110.15),
    "珠海": (22.22, 113.55), "白银": (36.55, 104.18), "盐城": (33.38, 120.14),
    "石嘴山": (39.02, 106.38), "福州": (26.11, 119.32), "福州南": (26.05, 119.38),
    "秦皇岛": (39.95, 119.61), "耒阳西": (26.42, 112.85), "肇庆": (23.05, 112.47),
    "芜湖": (31.34, 118.38), "茂名": (21.67, 110.92), "萍乡北": (27.67, 113.87),
    "营口": (40.68, 122.24), "葫芦岛": (40.76, 120.86), "衡水": (37.74, 115.70),
    "衡山西": (27.21, 112.86), "西宁": (36.62, 101.78), "西安北": (34.38, 108.94),
    "许昌": (34.03, 113.83), "平坝南": (26.43, 106.52), "贵阳东": (26.65, 106.76),
    "赤峰": (42.28, 118.92), "辽阳": (41.27, 123.17), "运城": (35.03, 111.00),
    "连云港": (34.60, 119.18), "通辽": (43.65, 122.25), "邢台": (37.07, 114.48),
    "邯郸": (36.60, 114.48), "郑州": (34.75, 113.65), "郑州东": (34.76, 113.77),
    "金华": (29.08, 119.65), "铁岭": (42.22, 123.85), "铜仁南": (27.30, 108.98),
    "银川": (38.49, 106.23), "锦州": (41.10, 121.13), "镇江": (32.20, 119.43),
    "长春": (43.90, 125.32), "长春西": (43.88, 125.20), "阳江": (21.87, 111.98),
    "青岛": (36.06, 120.31), "鞍山": (41.11, 122.99), "香港西九龙": (22.30, 114.17),
    "鸡西": (45.30, 130.97), "鹰潭北": (28.28, 117.02), "三穗": (26.95, 108.68),
    "凯里南": (26.53, 107.88), "贵定北": (26.58, 107.22),
    "齐齐哈尔": (47.35, 123.92),
    # 漏网修正（不在 .xx17 模式里但偏差明显）
    "汉口": (30.62, 114.25), "武汉": (30.62, 114.43),
}

# 合武铁路补建：id, name, display, province, city, lat, lon, is_major, tier
HEWU_STATIONS = [
    ("金寨", "金寨", "金寨站", "安徽", "六安", 31.72, 115.93, 0, 4),
    ("麻城北", "麻城北", "麻城北站", "湖北", "黄冈", 31.19, 114.85, 0, 4),
    ("红安西", "红安西", "红安西站", "湖北", "黄冈", 31.28, 114.60, 0, 4),
]
HEWU_LEGS = [("合肥", "六安"), ("六安", "金寨"), ("金寨", "麻城北"),
             ("麻城北", "红安西"), ("红安西", "汉口")]


def main():
    print(f"数据库路径: {os.path.abspath(DB_PATH)}")
    shutil.copy2(DB_PATH, BACKUP_PATH)
    print(f"已备份到 {BACKUP_PATH}")

    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()

    # 1. 坐标精化
    missing = [sid for sid in STATION_COORDS
               if not cur.execute("SELECT 1 FROM stations WHERE id=?",
                                  (sid,)).fetchone()]
    if missing:
        raise SystemExit(f"坐标表中的站点不存在: {missing}")
    for sid, (la, lo) in STATION_COORDS.items():
        cur.execute("UPDATE stations SET lat=?, lon=? WHERE id=?", (la, lo, sid))
    print(f"1. 精化坐标 {len(STATION_COORDS)} 个站点")

    # 2. 补建合武铁路站点与边
    for s in HEWU_STATIONS:
        if not cur.execute("SELECT 1 FROM stations WHERE id=?", (s[0],)).fetchone():
            cur.execute(
                "INSERT INTO stations (id,name,display_name,province,city,lat,lon,"
                " is_major,tier) VALUES (?,?,?,?,?,?,?,?,?)", s)
    coords = {r["id"]: (r["lat"], r["lon"])
              for r in cur.execute("SELECT id, lat, lon FROM stations")}
    existing = {(r["from_id"], r["to_id"], r["line"])
                for r in cur.execute("SELECT from_id, to_id, line FROM edges")}
    n = 0
    for a, b in HEWU_LEGS:
        d = round(hav(coords[a], coords[b]), 1)
        for fa, fb in [(a, b), (b, a)]:
            if (fa, fb, "合武铁路") not in existing:
                cur.execute(
                    "INSERT INTO edges (from_id, from_name, to_id, to_name, line,"
                    " distance_km, source) VALUES (?,?,?,?,'合武铁路',?,NULL)",
                    (fa, fa, fb, fb, d))
                n += 1
        print(f"   合武铁路: {a} <-> {b} ({d} km)")
    print(f"2. 合武铁路补建边 {n} 条")

    # 3. 单向线路边补反向
    pairs = {(r["from_id"], r["to_id"]) for r in
             cur.execute("SELECT from_id, to_id FROM edges")}
    skip_lines = ("city_transfer", "regional_link")
    n = 0
    for e in cur.execute(
        "SELECT from_id, from_name, to_id, to_name, line, distance_km FROM edges"
    ).fetchall():
        if e["line"] in skip_lines:
            continue
        if (e["to_id"], e["from_id"]) not in pairs:
            cur.execute(
                "INSERT INTO edges (from_id, from_name, to_id, to_name, line,"
                " distance_km, source) VALUES (?,?,?,?,?,?,NULL)",
                (e["to_id"], e["to_name"], e["from_id"], e["from_name"],
                 e["line"], e["distance_km"]))
            pairs.add((e["to_id"], e["from_id"]))
            n += 1
    print(f"3. 补齐反向边 {n} 条")

    conn.commit()

    # 4. 坐标变化站点的 city_transfer / regional_link 距离重算
    n = 0
    changed = set(STATION_COORDS) | {s[0] for s in HEWU_STATIONS}
    marks = ",".join("?" * len(changed))
    for e in cur.execute(
        f"SELECT id, from_id, to_id FROM edges"
        f" WHERE line IN ('city_transfer','regional_link')"
        f" AND (from_id IN ({marks}) OR to_id IN ({marks}))",
        tuple(changed) * 2,
    ).fetchall():
        a, b = coords.get(e["from_id"]), coords.get(e["to_id"])
        if a and b:
            cur.execute("UPDATE edges SET distance_km=? WHERE id=?",
                        (round(hav(a, b), 2), e["id"]))
            n += 1
    conn.commit()
    print(f"4. 重算换乘边距离 {n} 条")

    # 验证
    sset = {r[0] for r in cur.execute("SELECT id FROM stations")}
    d = sum(1 for e in cur.execute("SELECT from_id, to_id FROM edges")
            if e[0] not in sset or e[1] not in sset)
    n_s = cur.execute("SELECT COUNT(*) FROM stations").fetchone()[0]
    n_e = cur.execute("SELECT COUNT(*) FROM edges").fetchone()[0]
    print(f"验证: stations={n_s} edges={n_e} 悬空边={d}")
    conn.close()


if __name__ == "__main__":
    main()
