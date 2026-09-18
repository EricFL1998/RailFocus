import json
import os

base_dir = os.path.dirname(os.path.abspath(__file__))
city_facts_path = os.path.join(base_dir, '..', 'app', 'src', 'main', 'assets', 'city_facts.json')
plan_path = os.path.join(base_dir, '..', 'scripts', 'city_fix_plan.json')
output_path = os.path.join(base_dir, '..', 'scripts', 'invalid_special_map_targets.txt')

with open(city_facts_path, 'r', encoding='utf-8') as f:
    city_facts = json.load(f)

with open(plan_path, 'r', encoding='utf-8') as f:
    plan = json.load(f)

invalid = []
for raw, info in plan.items():
    target = info['target']
    method = info['method']
    if target and target not in city_facts:
        invalid.append((raw, target, method))

with open(output_path, 'w', encoding='utf-8') as f:
    f.write(f'映射目标不在 city_facts 中的记录数: {len(invalid)}\n\n')
    for raw, target, method in sorted(invalid, key=lambda x: x[2]):
        f.write(f'  {raw} -> {target} ({method})\n')

print(f'发现 {len(invalid)} 条映射目标无效的记录，详情见 {output_path}')
