import json
import sqlite3
import os
import math

base_dir = os.path.dirname(os.path.abspath(__file__))
city_facts_path = os.path.join(base_dir, '..', 'app', 'src', 'main', 'assets', 'city_facts.json')
db_path = os.path.join(base_dir, '..', 'app', 'src', 'main', 'assets', 'databases', 'rail_focus.db')
output_path = os.path.join(base_dir, '..', 'scripts', 'nearest_city_matches.txt')

with open(city_facts_path, 'r', encoding='utf-8') as f:
    city_facts = json.load(f)

valid_cities = set(city_facts.keys())

conn = sqlite3.connect(db_path)
cursor = conn.cursor()

# 获取所有有效城市的中心坐标
cursor.execute('''
    SELECT city, AVG(lat), AVG(lon)
    FROM stations
    WHERE city IS NOT NULL AND city != "" AND city IN ({})
    GROUP BY city
'''.format(','.join('?' * len(valid_cities))), list(valid_cities))
city_centers = {row[0]: (row[1], row[2]) for row in cursor.fetchall()}

# 获取未匹配城市的中心坐标（在 valid_cities 之外的 city）
cursor.execute('''
    SELECT city, AVG(lat), AVG(lon), COUNT(*)
    FROM stations
    WHERE city IS NOT NULL AND city != "" AND city NOT IN ({})
    GROUP BY city
'''.format(','.join('?' * len(valid_cities))), list(valid_cities))
unmatched_centers = [(row[0], row[1], row[2], row[3]) for row in cursor.fetchall()]

def haversine(lat1, lon1, lat2, lon2):
    if lat1 is None or lon1 is None or lat2 is None or lon2 is None:
        return float('inf')
    R = 6371.0
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2)**2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2)**2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return R * c

with open(output_path, 'w', encoding='utf-8') as f:
    f.write('未匹配城市按地理距离最近的合法城市:\n')
    for raw_city, lat, lon, count in unmatched_centers:
        if lat is None or lon is None:
            f.write(f'  {raw_city}: 无坐标数据\n')
            continue
        nearest = None
        min_dist = float('inf')
        for city, (clat, clon) in city_centers.items():
            dist = haversine(lat, lon, clat, clon)
            if dist < min_dist:
                min_dist = dist
                nearest = city
        f.write(f'  {raw_city} ({lat:.3f}, {lon:.3f}, {count}站) -> {nearest} ({min_dist:.1f}km)\n')

conn.close()
print(f'最近城市匹配结果已写入: {output_path}')
