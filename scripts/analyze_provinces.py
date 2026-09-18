import json
import os

base_dir = os.path.dirname(os.path.abspath(__file__))
city_facts_path = os.path.join(base_dir, '..', 'app', 'src', 'main', 'assets', 'city_facts.json')
output_path = os.path.join(base_dir, '..', 'scripts', 'city_facts_by_province.txt')

with open(city_facts_path, 'r', encoding='utf-8') as f:
    data = json.load(f)

by_province = {}
for city, info in data.items():
    province = info.get('province', '未知')
    by_province.setdefault(province, []).append(city)

with open(output_path, 'w', encoding='utf-8') as f:
    for province in sorted(by_province.keys()):
        cities = sorted(by_province[province])
        f.write(f'{province} ({len(cities)}个): {", ".join(cities)}\n\n')

print(f'按省份分类结果已写入: {output_path}')
