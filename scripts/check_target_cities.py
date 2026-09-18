import json
import os

base_dir = os.path.dirname(os.path.abspath(__file__))
city_facts_path = os.path.join(base_dir, '..', 'app', 'src', 'main', 'assets', 'city_facts.json')
output_path = os.path.join(base_dir, '..', 'scripts', 'target_city_check.txt')

with open(city_facts_path, 'r', encoding='utf-8') as f:
    city_facts = json.load(f)

candidates = [
    '黔东南', '黔南', '三门峡', '黄山', '绍兴', '天水', '东莞', '宜春', '梅州', '杭州',
    '淄博', '威海', '孝感', '上饶', '桂林', '丹东', '哈尔滨', '亳州', '台州', '南京',
    '宁波', '襄阳', '赣州', '深圳', '开封', '宜昌', '宜宾', '内江', '安阳', '淮南',
    '渭南', '濮阳', '中山', '上海', '本溪', '惠州', '琼海', '新乡', '临沂', '双鸭山',
    '湘西', '宁德', '鞍山', '北海', '周口', '河源', '咸阳', '珠海', '青岛', '天津',
    '廊坊', '大庆', '毕节', '镇江', '敦化', '重庆', '安庆', '昭通', '长沙', '汉中',
    '定西', '南宁', '南阳',
]

with open(output_path, 'w', encoding='utf-8') as f:
    f.write('候选目标城市在 city_facts 中的存在情况:\n')
    for c in candidates:
        status = '存在' if c in city_facts else '不存在'
        f.write(f'  {c}: {status}\n')

    f.write('\ncity_facts 中贵州省相关城市:\n')
    for c in sorted(city_facts.keys()):
        if '黔' in c:
            f.write(f'  {c}\n')

    f.write('\ncity_facts 中浙江省相关城市:\n')
    for c in sorted(city_facts.keys()):
        if any(keyword in c for keyword in ['杭', '宁', '绍', '台', '金', '温', '丽', '衢', '湖', '嘉']):
            f.write(f'  {c}\n')

print(f'检查结果已写入: {output_path}')
