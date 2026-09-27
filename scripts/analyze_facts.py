import json
import sqlite3
import os

base_dir = os.path.dirname(os.path.abspath(__file__))
json_path = os.path.join(base_dir, '..', 'app', 'src', 'main', 'assets', 'city_facts.json')
db_path = os.path.join(base_dir, '..', 'app', 'src', 'main', 'assets', 'databases', 'rail_focus.db')

with open(json_path, 'r', encoding='utf-8') as f:
    data = json.load(f)

cities = data.get('cities', {})

conn = sqlite3.connect(db_path)
c = conn.cursor()
c.execute("SELECT DISTINCT city, province FROM stations WHERE city IS NOT NULL AND city != '' ORDER BY city")
db_city_province = {row[0]: row[1] for row in c.fetchall()}
conn.close()

db_cities = set(db_city_province.keys())
json_cities = set(cities.keys())
missing_cities = sorted([c for c in db_cities if c not in json_cities])

tier1 = [name for name, c in cities.items() if c.get('tier') == 1]
tier2 = [name for name, c in cities.items() if c.get('tier') == 2]

print(f"JSON cities: {len(json_cities)}")
print(f"DB cities: {len(db_cities)}")
print(f"Missing cities: {len(missing_cities)}")
print(f"Tier 1: {len(tier1)}, Tier 2: {len(tier2)}")

with open(os.path.join(base_dir, 'city_facts_analysis.json'), 'w', encoding='utf-8') as f:
    json.dump({
        'missing_cities': missing_cities,
        'db_city_province': db_city_province,
        'tier1_cities': tier1,
        'tier2_cities': tier2,
    }, f, ensure_ascii=False, indent=2)

print("Analysis saved.")

