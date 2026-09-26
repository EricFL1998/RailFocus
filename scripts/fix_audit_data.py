#!/usr/bin/env python3
"""
全面修复 rail_focus.db 数据问题（配合 audit_full.py 使用）：

A. 补建真实缺失站点（丽香铁路、和若铁路、青藏铁路格拉段、钦防铁路、
    宁杭高铁瓦屋山站、北京清河站）
B. 边端点 id 规范化：大量边的端点写的是站名（或带"站"后缀），
    而 stations 表用数字 id 或省后缀 id，导致图算法里这些站点"隐身"。
    按 id > name > display_name > 去"站"后缀 的优先级唯一解析并重指。
C. 坐标修正：约 50 个站点坐标张冠李戴（如格尔木坐标在内蒙古、
    阜阳西坐标在湖南），按真实位置修正。
D. 省市修正：乌兰木图（→辽宁阜新）、古城东（→安徽亳州）、
    唐河北（→河南南阳）、董家口（→青岛）。
E. 合并重复站点「高明珠三角枢纽机场」→「珠三角枢纽机场」。
F. 删除因错标城市产生的虚假同城换乘边（乌兰木图↔邢台等）。
G. 数字 id 站点改名为其站名（无任何边引用数字 id，已验证）。
H. 重算全部 city_transfer / regional_link 边距离（基于修正后坐标）。
I. 删除完全重复的边。

用法：python scripts/fix_audit_data.py
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
BACKUP_PATH = DB_PATH + ".bak_full_audit"


def hav(a, b):
    r = 6371.0
    p1, p2 = math.radians(a[0]), math.radians(b[0])
    dp = math.radians(b[0] - a[0])
    dl = math.radians(b[1] - a[1])
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(h))


# ---- A. 新增站点：id, name, display, province, city, lat, lon, is_major, tier ----
NEW_STATIONS = [
    # 丽香铁路（云南）
    ("丽江", "丽江", "丽江站", "云南", "丽江", 26.86, 100.22, 1, 2),
    ("拉市海", "拉市海", "拉市海站", "云南", "丽江", 26.88, 100.12, 0, 4),
    ("达落", "达落", "达落站", "云南", "丽江", 26.98, 100.02, 0, 4),
    ("新尚", "新尚", "新尚站", "云南", "丽江", 27.06, 99.94, 0, 4),
    ("虎跳峡", "虎跳峡", "虎跳峡站", "云南", "迪庆", 27.20, 100.04, 0, 4),
    ("螺丝湾", "螺丝湾", "螺丝湾站", "云南", "迪庆", 27.26, 99.93, 0, 4),
    ("花椒坡", "花椒坡", "花椒坡站", "云南", "迪庆", 27.32, 99.82, 0, 4),
    ("万拉木", "万拉木", "万拉木站", "云南", "迪庆", 27.38, 99.72, 0, 4),
    ("塘布", "塘布", "塘布站", "云南", "迪庆", 27.44, 99.66, 0, 4),
    ("小中甸", "小中甸", "小中甸站", "云南", "迪庆", 27.50, 99.75, 0, 4),
    ("鲁吉", "鲁吉", "鲁吉站", "云南", "迪庆", 27.62, 99.72, 0, 4),
    ("香格里拉", "香格里拉", "香格里拉站", "云南", "迪庆", 27.80, 99.78, 1, 2),
    # 和若铁路（新疆 / 青海）
    ("和田", "和田", "和田站", "新疆", "和田", 37.12, 79.92, 1, 2),
    ("洛浦", "洛浦", "洛浦站", "新疆", "和田", 37.07, 80.18, 0, 4),
    ("策勒", "策勒", "策勒站", "新疆", "和田", 37.00, 80.78, 0, 4),
    ("于田", "于田", "于田站", "新疆", "和田", 36.85, 81.65, 0, 4),
    ("民丰", "民丰", "民丰站", "新疆", "和田", 37.05, 82.70, 0, 4),
    ("且末", "且末", "且末站", "新疆", "巴音郭楞", 38.13, 85.53, 0, 4),
    ("瓦石峡", "瓦石峡", "瓦石峡站", "新疆", "巴音郭楞", 38.90, 87.45, 0, 4),
    ("若羌", "若羌", "若羌站", "新疆", "巴音郭楞", 39.02, 88.17, 0, 4),
    ("米兰", "米兰", "米兰站", "新疆", "巴音郭楞", 39.25, 88.95, 0, 4),
    ("格尔木南", "格尔木南", "格尔木南站", "青海", "海西", 36.32, 94.90, 0, 4),
    # 青藏铁路格拉段（西藏）
    ("那曲", "那曲", "那曲站", "西藏", "那曲", 31.48, 92.05, 1, 2),
    ("拉萨西", "拉萨西", "拉萨西站", "西藏", "拉萨", 29.68, 91.00, 0, 4),
    # 钦防铁路（广西）
    ("钦州", "钦州", "钦州站", "广西", "钦州", 21.98, 108.65, 1, 2),
    ("钦州北", "钦州北", "钦州北站", "广西", "钦州", 21.99, 108.60, 0, 4),
    ("防城港北", "防城港北", "防城港北站", "广西", "防城港", 21.70, 108.35, 0, 4),
    # 宁杭高铁（江苏）
    ("瓦屋山站", "瓦屋山", "瓦屋山站", "江苏", "镇江", 31.55, 119.28, 0, 4),
    # 京张高铁北京清河站（区别于河北邢台清河县站「清河」）
    ("清河站", "清河", "清河站", "北京", "北京", 40.04, 116.34, 1, 2),
    # 天津滨海站（原于家堡站，京津城际延伸线终点；原库中边已引用但站点行缺失）
    ("滨海站", "滨海", "滨海站", "天津", "天津", 39.01, 117.70, 1, 2),
]

# ---- C. 坐标修正：id -> (lat, lon) ----
COORD_FIXES = {
    "沙河": (40.14, 116.28), "北京北": (39.94, 116.35), "昌平": (40.22, 116.23),
    "八达岭长城": (40.36, 115.99),
    "宜宾": (28.75, 104.62), "宜宾西": (28.68, 104.63),
    "利辛": (33.15, 116.21), "蒙城": (33.26, 116.56),
    "太湖西": (30.42, 116.27),
    "砀山南": (34.40, 116.35), "宿州西": (33.63, 116.95), "泗县东": (33.48, 117.88),
    "天长": (32.68, 119.00),
    "阜阳西": (32.90, 115.82), "阜南东": (32.63, 115.59), "阜阳北": (32.95, 115.88),
    "曲阜南": (35.55, 116.97),
    "大同南": (40.02, 113.13), "天镇": (40.42, 114.09),
    "新兴南": (22.70, 112.23), "罗定北": (22.77, 111.57),
    "珠三角枢纽机场": (22.75, 112.78),
    "湛江北": (21.27, 110.36),
    "田东北": (23.62, 107.12), "隆安东": (23.18, 107.70), "平果": (23.33, 107.58),
    "兴业南": (22.74, 109.88),
    "蒙山": (24.20, 110.52), "岑溪东": (22.92, 110.99),
    "溧阳": (31.42, 119.48), "溧水西": (31.55, 118.98),
    "新沂南": (34.37, 118.35),
    "赣州": (25.83, 114.93), "于都": (25.95, 115.42),
    "会昌北": (25.60, 115.79), "瑞金": (25.89, 116.03),
    "东花园北": (40.36, 115.83),
    "灵宝西": (34.52, 110.88), "渑池南": (34.77, 111.76),
    "淮滨东": (32.43, 115.42), "潢川南": (32.13, 115.05), "新县北": (31.64, 114.85),
    "利川": (30.29, 108.94),
    "杨陵南": (34.27, 108.08),
    "格尔木": (36.42, 94.91), "乌兰": (36.93, 98.48), "天峻": (37.30, 99.02),
    "羊八井": (30.08, 90.48), "当雄": (30.48, 91.10), "安多": (32.27, 91.68),
    "唐古拉北": (33.35, 91.85), "唐古拉南": (33.05, 91.95),
    "措那湖": (32.02, 91.58), "乌玛塘": (30.55, 91.40),
    "乌兰察布": (41.03, 113.11), "卓资东": (40.90, 112.58),
    "兴和北": (40.88, 113.83), "玉林北": (22.75, 110.12),
    "阳高南": (40.36, 113.75), "下花园北": (40.48, 115.30),
    "江口": (27.70, 108.85), "龙山北": (29.52, 109.44),
    "旗下营南": (40.88, 112.15), "丰镇北": (40.43, 113.15),
    "唐河北": (32.68, 112.85),
}

# ---- D. 省市修正：id -> (province, city) ----
CITY_FIXES = {
    "乌兰木图": ("辽宁", "阜新"),
    "古城东": ("安徽", "亳州"),
    "唐河北": ("河南", "南阳"),
    "董家口": ("山东", "青岛"),
    "措那湖": ("西藏", "那曲"),
    "乌玛塘": ("西藏", "拉萨"),
}


def resolve_aliases(cur, stations, edges):
    """把边端点重指到规范化站点 id，返回重指数量。"""
    by_name, by_display, by_id = {}, {}, {}
    for r in stations.values():
        by_id[r["id"]] = r["id"]
        by_name.setdefault(r["name"], []).append(r["id"])
        if r["display_name"]:
            by_display.setdefault(r["display_name"], []).append(r["id"])

    def candidates(ref):
        out = []
        if ref in by_id:
            return [by_id[ref]]              # 规则 a：精确 id
        if ref in by_name:
            return by_name[ref]              # 规则 b：name 精确
        if ref in by_display:
            return by_display[ref]           # 规则 c：display_name 精确
        if ref.endswith("站"):               # 规则 e：去「站」后缀
            base = ref[:-1]
            if base in by_id:
                out.append(by_id[base])
            if base in by_name:
                out.extend(by_name[base])
        return sorted(set(out))

    remap, ambiguous, unresolved = {}, [], []
    refs = {e["from_id"] for e in edges} | {e["to_id"] for e in edges}
    for ref in refs:
        if ref in stations:
            continue
        c = candidates(ref)
        if len(c) == 1:
            remap[ref] = c[0]
        elif len(c) > 1:
            ambiguous.append((ref, c))
        else:
            unresolved.append(ref)

    for ref, c in ambiguous:
        remap[ref] = c[0]
        print(f"  [ambiguous] {ref} -> {c[0]} (candidates {c})")
    if unresolved:
        print(f"  [unresolved] {unresolved}")
        raise SystemExit("存在无法解析的边端点，请先补建站点")

    n = 0
    for e in edges:
        fid = remap.get(e["from_id"], e["from_id"])
        tid = remap.get(e["to_id"], e["to_id"])
        if fid != e["from_id"] or tid != e["to_id"]:
            fn = stations[fid]["name"]
            tn = stations[tid]["name"]
            cur.execute(
                "UPDATE edges SET from_id=?, from_name=?, to_id=?, to_name=?"
                " WHERE id=?",
                (fid, fn, tid, tn, e["id"]),
            )
            n += 1
    return n


def main():
    print(f"数据库路径: {os.path.abspath(DB_PATH)}")
    shutil.copy2(DB_PATH, BACKUP_PATH)
    print(f"已备份到 {BACKUP_PATH}")

    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()

    # G前置：数字 id 站点改名为站名（已验证无边引用数字 id、无名字冲突），
    #        必须在别名解析之前完成，否则解析结果会指向旧数字 id。
    for r in cur.execute(
        "SELECT id, name FROM stations WHERE id GLOB '[0-9]*'"
    ).fetchall():
        clash = cur.execute(
            "SELECT 1 FROM stations WHERE id=? AND id<>?", (r["name"], r["id"])
        ).fetchone()
        if clash:
            print(f"   [skip] id={r['id']} name={r['name']} 目标 id 已存在")
            continue
        cur.execute("UPDATE stations SET id=? WHERE id=?", (r["name"], r["id"]))
    print("G. 数字 id 站点已改名为站名")

    stations = {r["id"]: r for r in cur.execute("SELECT * FROM stations")}

    # A. 补建缺失站点
    for s in NEW_STATIONS:
        if s[0] in stations:
            raise SystemExit(f"站点已存在: {s[0]}")
    cur.executemany(
        "INSERT INTO stations (id,name,display_name,province,city,lat,lon,is_major,tier)"
        " VALUES (?,?,?,?,?,?,?,?,?)",
        NEW_STATIONS,
    )
    print(f"A. 补建缺失站点 {len(NEW_STATIONS)} 个")

    # E前置：合并「高明珠三角枢纽机场」→「珠三角枢纽机场」
    cur.execute(
        "UPDATE edges SET from_id='珠三角枢纽机场', from_name='珠三角枢纽机场'"
        " WHERE from_id='高明珠三角枢纽机场'"
    )
    cur.execute(
        "UPDATE edges SET to_id='珠三角枢纽机场', to_name='珠三角枢纽机场'"
        " WHERE to_id='高明珠三角枢纽机场'"
    )
    cur.execute("DELETE FROM stations WHERE id='高明珠三角枢纽机场'")
    print("E. 已合并「高明珠三角枢纽机场」→「珠三角枢纽机场」")

    # E2：合并同城重复站点「瓦屋山」（city 字段错误）→「瓦屋山站」（宁杭高铁）
    cur.execute(
        "UPDATE edges SET from_id='瓦屋山站', from_name='瓦屋山'"
        " WHERE from_id='瓦屋山'"
    )
    cur.execute(
        "UPDATE edges SET to_id='瓦屋山站', to_name='瓦屋山'"
        " WHERE to_id='瓦屋山'"
    )
    cur.execute("DELETE FROM stations WHERE id='瓦屋山'")
    print("E2. 已合并「瓦屋山」→「瓦屋山站」")

    stations = {r["id"]: r for r in cur.execute("SELECT * FROM stations")}
    edges = cur.execute("SELECT * FROM edges").fetchall()

    # B. 边端点规范化
    n = resolve_aliases(cur, stations, edges)
    print(f"B. 重指边端点 {n} 条")

    # 清河站（北京）承接原「清河」上的京张高铁边
    cur.execute(
        "UPDATE edges SET from_id='清河站', from_name='清河'"
        " WHERE from_id='清河' AND line='京张高铁'"
    )
    cur.execute(
        "UPDATE edges SET to_id='清河站', to_name='清河'"
        " WHERE to_id='清河' AND line='京张高铁'"
    )
    print("   京张高铁边已改指北京「清河站」")

    # C. 坐标修正
    for sid, (la, lo) in COORD_FIXES.items():
        cur.execute("UPDATE stations SET lat=?, lon=? WHERE id=?", (la, lo, sid))
    print(f"C. 修正坐标 {len(COORD_FIXES)} 个")

    # D. 省市修正
    for sid, (pv, ct) in CITY_FIXES.items():
        cur.execute(
            "UPDATE stations SET province=?, city=? WHERE id=?", (pv, ct, sid)
        )
    print(f"D. 修正省市 {len(CITY_FIXES)} 个")

    # F. 删除因错标城市产生的虚假同城换乘边（乌兰木图现属辽宁阜新）
    cur.execute(
        "DELETE FROM edges WHERE line='city_transfer'"
        " AND ('乌兰木图' IN (from_id, to_id))"
        " AND ('邢台' IN (from_id, to_id) OR '邢台东' IN (from_id, to_id)"
        " OR '清河' IN (from_id, to_id))"
    )
    print(f"F. 删除虚假同城换乘边 {cur.rowcount} 条")

    conn.commit()

    # H. 重算 city_transfer / regional_link 距离
    stations = {
        r["id"]: (r["lat"], r["lon"])
        for r in cur.execute("SELECT id, lat, lon FROM stations")
    }
    n = 0
    for e in cur.execute(
        "SELECT id, from_id, to_id FROM edges"
        " WHERE line IN ('city_transfer','regional_link')"
    ).fetchall():
        a, b = stations.get(e["from_id"]), stations.get(e["to_id"])
        if a and b:
            cur.execute(
                "UPDATE edges SET distance_km=? WHERE id=?",
                (round(hav(a, b), 2), e["id"]),
            )
            n += 1
    print(f"H. 重算 city_transfer/regional_link 距离 {n} 条")

    # I. 删除完全重复边（同 from/to/line，保留最小 id）
    cur.execute(
        """
        DELETE FROM edges WHERE id NOT IN (
            SELECT MIN(id) FROM edges GROUP BY from_id, to_id, line
        )
        """
    )
    print(f"I. 删除重复边 {cur.rowcount} 条")

    conn.commit()

    # ---- 验证 ----
    stations_set = {r[0] for r in cur.execute("SELECT id FROM stations")}
    dangling = 0
    for e in cur.execute("SELECT from_id, to_id FROM edges"):
        if e["from_id"] not in stations_set or e["to_id"] not in stations_set:
            dangling += 1
    n_s = cur.execute("SELECT COUNT(*) FROM stations").fetchone()[0]
    n_e = cur.execute("SELECT COUNT(*) FROM edges").fetchone()[0]
    print(f"\n验证: stations={n_s} edges={n_e} 悬空边={dangling}")
    conn.close()


if __name__ == "__main__":
    main()
