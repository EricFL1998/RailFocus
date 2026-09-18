import json
import sqlite3
import os

base_dir = os.path.dirname(os.path.abspath(__file__))
city_facts_path = os.path.join(base_dir, '..', 'app', 'src', 'main', 'assets', 'city_facts.json')
db_path = os.path.join(base_dir, '..', 'app', 'src', 'main', 'assets', 'databases', 'rail_focus.db')
output_path = os.path.join(base_dir, '..', 'scripts', 'city_analysis_report.txt')

with open(city_facts_path, 'r', encoding='utf-8') as f:
    city_facts = json.load(f)

city_list = set(city_facts.keys())

conn = sqlite3.connect(db_path)
cursor = conn.cursor()

cursor.execute('SELECT city, COUNT(*) FROM stations GROUP BY city ORDER BY COUNT(*) DESC')
db_cities = cursor.fetchall()

db_city_names = set()
for city, count in db_cities:
    if city:
        db_city_names.add(city)

cursor.execute('SELECT COUNT(*) FROM stations WHERE city IS NULL OR city = ""')
null_count = cursor.fetchone()[0]

with open(output_path, 'w', encoding='utf-8') as f:
    f.write(f'city_facts.json 中的城市数量: {len(city_list)}\n')
    f.write(f'数据库中的城市数量 (按 city 分组): {len(db_cities)}\n')
    f.write(f'city 为 NULL 或空字符串的站点数: {null_count}\n\n')

    f.write(f'数据库中有但 city_facts.json 中没有的城市 ({len(db_city_names - city_list)}):\n')
    for city in sorted(db_city_names - city_list):
        count = next(n for c, n in db_cities if c == city)
        f.write(f'  {city}: {count} 个站点\n')

    f.write(f'\ncity_facts.json 中有但数据库中没有的城市 ({len(city_list - db_city_names)}):\n')
    for city in sorted(city_list - db_city_names):
        f.write(f'  {city}\n')

    f.write('\n\n数据库中所有城市分布:\n')
    for city, count in db_cities:
        f.write(f'  {city}: {count}\n')

conn.close()
print(f'分析报告已写入: {output_path}')
