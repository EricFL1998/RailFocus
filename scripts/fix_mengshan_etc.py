#!/usr/bin/env python3
"""
修复 费县北—蒙山 等问题：

1. 拆分同名站「蒙山」：
   - 「蒙山」→ 山东临沂蒙山站（日兰高铁，费县北—蒙山—泗水南）
   - 新建「梧州蒙山」承接原广西梧州的同城换乘边
2. 站点坐标修正（约 60 个）：宝鸡南（坐标在四川！）、怀化方向、湖州/宜兴、
    泉州/惠安/仙游、淮北（ lat 差了 2°）、雄安西站群、云桂铁路车站等。
3. 线路边距离修正：两端坐标正常但标称距离失真的边（杭深线、海南环岛、
    郑西、云桂等），标称距离改为两端直线距离。
   豁免：和若铁路 / 青藏铁路 / 丽香铁路（沙漠高原铁路 curated 距离
    大于直线属正常）。
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
BACKUP_PATH = DB_PATH + ".bak_mengshan"


def hav(a, b):
    r = 6371.0
    p1, p2 = math.radians(a[0]), math.radians(b[0])
    dp = math.radians(b[0] - a[0])
    dl = math.radians(b[1] - a[1])
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(h))


# 坐标修正：id -> (lat, lon)
COORD_FIXES = {
    "曲阜东": (35.59, 116.99), "许昌东": (34.05, 113.87),
    "东海县": (34.54, 118.75), "华山北": (34.56, 110.13),
    "宝鸡南": (34.36, 107.23), "东岔": (34.37, 106.95),
    "怀来": (40.35, 115.51), "宣化北": (40.61, 115.05),
    "温岭": (28.38, 121.39), "台州西": (28.66, 121.25),
    "瑞安": (27.78, 120.65), "仙游": (25.36, 118.69),
    "惠安": (25.03, 118.80), "泉州": (24.91, 118.59),
    "湖州": (30.87, 120.05), "宜兴": (31.36, 119.82),
    "句容西": (31.95, 119.15), "都匀东": (26.30, 107.54),
    "莱州": (37.18, 119.95), "泸州": (28.88, 105.45),
    "云浮": (23.07, 112.04), "黔江": (29.53, 108.77),
    "凉雾": (30.30, 108.72), "红安东": (31.28, 114.63),
    "淮北北": (34.00, 116.83), "淮北西": (33.94, 116.78),
    "双堆集": (33.55, 117.06), "金湖": (33.02, 119.02),
    "五河": (33.13, 117.89), "洞口": (27.06, 110.58),
    "珠海北": (22.37, 113.58), "四团": (30.90, 121.72),
    "上海东": (31.08, 121.75), "桐乡": (30.63, 120.55),
    "惠山": (31.68, 120.30), "田阳": (23.72, 107.10),
    "横州": (23.19, 109.25), "容县南": (22.86, 110.56),
    "弥勒": (24.41, 103.45), "开远南": (23.71, 103.25),
    "红河": (23.39, 103.38), "鸟岛": (36.98, 100.04),
    "怀仁东": (39.32, 113.12), "怀安": (40.68, 114.40),
    "唐县": (38.75, 114.98), "望都北": (38.69, 115.13),
    "曲阳": (38.62, 114.70), "阜平": (38.85, 114.19),
    "五台山": (38.73, 113.57), "五台县": (38.73, 113.25),
    "定襄北": (38.48, 112.95), "忻州西": (38.41, 112.72),
    "高淳": (31.33, 118.90), "太湖南": (30.44, 116.31),
    "珠琳": (24.14, 104.70), "普者黑": (24.24, 103.70),
    "曹县西": (34.95, 115.60), "菏泽东": (35.22, 115.57),
    "苇河西": (45.20, 128.18), "亚布力西": (44.58, 128.55),
    "凤城东": (40.43, 124.15), "黄尾": (30.87, 116.02),
    "淮安东": (33.61, 119.10), "绍兴北": (30.10, 120.60),
    "济宁北": (35.50, 116.50),
}

# 豁免站点：沙漠/高原/山区铁路 curated 距离 > 直线属正常，不动
EXEMPT = set("""和田 洛浦 策勒 于田 民丰 且末 瓦石峡 若羌 米兰 格尔木南 格尔木
德令哈 乌兰 都兰 天峻 柯柯 察汗诺 夏日哈 望昆 不冻泉 楚玛尔河 五道梁
秀水河 风火山 沱沱河 通天河 唐古拉山 唐古拉 唐古拉北 唐古拉南 布玛德
布强格 雁石坪 布曲 扎加藏布 托居 达琼果 安多 措那湖 那曲 乌玛塘 当雄
羊八井 拉萨西 拉萨 丽江 拉市海 达落 新尚 虎跳峡 螺丝湾 花椒坡 万拉木
塘布 小中甸 鲁吉 香格里拉""".split())


def main():
    print(f"数据库路径: {os.path.abspath(DB_PATH)}")
    shutil.copy2(DB_PATH, BACKUP_PATH)
    print(f"已备份到 {BACKUP_PATH}")

    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()

    # 1. 拆分蒙山
    cur.execute(
        "UPDATE stations SET province='山东', city='临沂', lat=35.45, lon=117.70,"
        " is_major=0, tier=4 WHERE id='蒙山'"
    )
    cur.execute(
        "INSERT INTO stations (id,name,display_name,province,city,lat,lon,"
        " is_major,tier) VALUES ('梧州蒙山','蒙山','蒙山站','广西','梧州',"
        " 24.20,110.52,0,4)"
    )
    cur.execute(
        "UPDATE edges SET from_id='梧州蒙山', from_name='蒙山'"
        " WHERE from_id='蒙山' AND line='city_transfer'"
    )
    cur.execute(
        "UPDATE edges SET to_id='梧州蒙山', to_name='蒙山'"
        " WHERE to_id='蒙山' AND line='city_transfer'"
    )
    print("1. 「蒙山」归为山东临沂日兰高铁蒙山站，梧州同城边改指「梧州蒙山」")

    # 2. 坐标修正
    for sid, (la, lo) in COORD_FIXES.items():
        cur.execute("UPDATE stations SET lat=?, lon=? WHERE id=?", (la, lo, sid))
    print(f"2. 修正坐标 {len(COORD_FIXES)} 个")

    conn.commit()

    # 3. 线路边距离修正（两端直线距离超过标称 2 倍且 >30km）
    coords = {r["id"]: (r["lat"], r["lon"])
              for r in cur.execute("SELECT id, lat, lon FROM stations")}
    n = 0
    for e in cur.execute(
        "SELECT id, from_id, to_id, line, distance_km FROM edges"
        " WHERE line NOT IN ('city_transfer','regional_link') AND distance_km > 0"
    ).fetchall():
        if e["from_id"] in EXEMPT or e["to_id"] in EXEMPT:
            continue
        a, b = coords.get(e["from_id"]), coords.get(e["to_id"])
        if not a or not b:
            continue
        d = hav(a, b)
        if d / e["distance_km"] > 2 and d > 30:
            cur.execute("UPDATE edges SET distance_km=? WHERE id=?",
                        (round(d, 1), e["id"]))
            n += 1
    conn.commit()
    print(f"3. 修正线路边距离 {n} 条")

    # 4. 坐标变化站点的换乘边距离重算
    changed = set(COORD_FIXES) | {"蒙山", "梧州蒙山"}
    marks = ",".join("?" * len(changed))
    n = 0
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
    print(f"验证: 悬空边={d}")
    conn.close()


if __name__ == "__main__":
    main()
