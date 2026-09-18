import json
import os

base_dir = os.path.dirname(os.path.abspath(__file__))
source_path = os.path.join(base_dir, '..', 'RailDataSet', 'city_knowledge.json')
target_path = os.path.join(base_dir, '..', 'app', 'src', 'main', 'assets', 'city_facts.json')

with open(source_path, 'r', encoding='utf-8') as f:
    data = json.load(f)

cities = data.get('cities', {})
output = {}

for city_name, city_data in cities.items():
    def extract_contents(key):
        items = city_data.get(key, [])
        return [item['content'] for item in items if isinstance(item, dict) and 'content' in item]

    output[city_name] = {
        '美食': extract_contents('food'),
        '历史': extract_contents('history'),
        '地理': extract_contents('geography'),
        '风景': extract_contents('culture'),
    }

with open(target_path, 'w', encoding='utf-8') as f:
    json.dump(output, f, ensure_ascii=False, indent=2)

print(f'转换完成：{len(output)} 个城市已写入 {target_path}')
