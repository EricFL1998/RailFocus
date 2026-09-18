#!/usr/bin/env python3
"""
重建预置数据库脚本

将现有 rail_focus.db 中的数据迁移到与 Room Entity schema 完全匹配的新数据库中。
修复 id 列缺少 NOT NULL、is_major/tier 列多余的 DEFAULT 值问题。

用法：python scripts/rebuild_db.py
"""

import sqlite3
import os
import shutil

DB_PATH = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "app", "src", "main", "assets", "databases", "rail_focus.db"
)
BACKUP_PATH = DB_PATH + ".bak"
TEMP_PATH = DB_PATH + ".tmp"


def read_data(db_path):
    """从现有数据库读取全部数据"""
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row

    stations = conn.execute("SELECT * FROM stations").fetchall()
    edges = conn.execute("SELECT * FROM edges").fetchall()

    station_cols = [desc[0] for desc in conn.execute("SELECT * FROM stations LIMIT 0").description]
    edge_cols = [desc[0] for desc in conn.execute("SELECT * FROM edges LIMIT 0").description]

    conn.close()
    return station_cols, stations, edge_cols, edges


def create_new_db(db_path, station_cols, stations, edge_cols, edges):
    """用匹配 Room Entity 的正确 schema 创建新数据库"""
    conn = sqlite3.connect(db_path)
    c = conn.cursor()

    # stations 表 —— 与 Room StationEntity 完全匹配
    #   id: NOT NULL PRIMARY KEY
    #   is_major: 无 DEFAULT
    #   tier: 无 DEFAULT
    c.execute("""
        CREATE TABLE stations (
            id TEXT NOT NULL PRIMARY KEY,
            name TEXT NOT NULL,
            display_name TEXT,
            province TEXT,
            city TEXT,
            lat REAL,
            lon REAL,
            is_major INTEGER,
            tier INTEGER
        )
    """)

    # edges 表 —— 与 Room EdgeEntity 匹配
    #   id: NOT NULL PRIMARY KEY
    c.execute("""
        CREATE TABLE edges (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            from_id TEXT NOT NULL,
            from_name TEXT,
            to_id TEXT NOT NULL,
            to_name TEXT,
            line TEXT,
            distance_km REAL,
            source TEXT
        )
    """)

    # 创建索引
    c.execute("CREATE INDEX idx_stations_name ON stations (name ASC)")
    c.execute("CREATE INDEX idx_stations_province ON stations (province ASC)")

    # 插入 stations 数据
    placeholders = ",".join(["?"] * len(station_cols))
    station_rows = [tuple(row) for row in stations]
    c.executemany(f"INSERT INTO stations ({','.join(station_cols)}) VALUES ({placeholders})", station_rows)

    # 插入 edges 数据
    placeholders = ",".join(["?"] * len(edge_cols))
    edge_rows = [tuple(row) for row in edges]
    c.executemany(f"INSERT INTO edges ({','.join(edge_cols)}) VALUES ({placeholders})", edge_rows)

    conn.commit()
    conn.close()


def main():
    print(f"数据库路径: {os.path.abspath(DB_PATH)}")

    # 1. 读取数据
    print("读取现有数据...")
    station_cols, stations, edge_cols, edges = read_data(DB_PATH)
    print(f"  stations: {len(stations)} 条记录, 列: {station_cols}")
    print(f"  edges: {len(edges)} 条记录, 列: {edge_cols}")

    # 2. 备份原文件
    print(f"备份原数据库到 {BACKUP_PATH}...")
    shutil.copy2(DB_PATH, BACKUP_PATH)

    # 3. 创建新数据库
    print("创建新数据库...")
    create_new_db(TEMP_PATH, station_cols, stations, edge_cols, edges)

    # 4. 验证新数据库
    print("验证新数据库...")
    conn = sqlite3.connect(TEMP_PATH)
    new_stations_count = conn.execute("SELECT COUNT(*) FROM stations").fetchone()[0]
    new_edges_count = conn.execute("SELECT COUNT(*) FROM edges").fetchone()[0]

    # 检查 stations 表 schema
    columns = conn.execute("PRAGMA table_info(stations)").fetchall()
    print(f"  stations 列信息:")
    for col in columns:
        # col: (cid, name, type, notnull, dflt_value, pk)
        print(f"    {col[1]}: type={col[2]}, notnull={col[3]}, default={col[4]}, pk={col[5]}")

    # 验证 id 列是 NOT NULL
    id_col = [c for c in columns if c[1] == 'id'][0]
    assert id_col[3] == 1, f"id 列应为 NOT NULL，实际 notnull={id_col[3]}"

    # 验证 is_major 和 tier 无默认值
    is_major_col = [c for c in columns if c[1] == 'is_major'][0]
    assert is_major_col[4] is None, f"is_major 列不应有默认值，实际 default={is_major_col[4]}"

    tier_col = [c for c in columns if c[1] == 'tier'][0]
    assert tier_col[4] is None, f"tier 列不应有默认值，实际 default={tier_col[4]}"

    conn.close()

    assert new_stations_count == len(stations), f"站点数不匹配: {new_stations_count} != {len(stations)}"
    assert new_edges_count == len(edges), f"边数不匹配: {new_edges_count} != {len(edges)}"

    print(f"  ✓ stations: {new_stations_count} 条")
    print(f"  ✓ edges: {new_edges_count} 条")
    print("  ✓ schema 验证通过")

    # 5. 替换原文件
    print("替换原数据库...")
    os.replace(TEMP_PATH, DB_PATH)

    print("\n✅ 数据库重建完成！")
    print(f"备份文件: {BACKUP_PATH}")


if __name__ == "__main__":
    main()
