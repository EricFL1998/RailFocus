# -*- coding: utf-8 -*-
import json, os
BASE = os.path.dirname(os.path.abspath(__file__))
JP = os.path.join(BASE, '..', 'app', 'src', 'main', 'assets', 'city_facts.json')
NL = {'railway':'铁路','landmark':'地标','trivia':'趣闻','specialty':'风物','travel':'漫游'}
data = json.load(open(JP, encoding='utf-8'))
c = data['cities']
updated = 0
for fn in ['_tier1_a.json', '_tier1_b.json']:
    block = json.load(open(os.path.join(BASE, fn), encoding='utf-8'))
    for city, cats in block.items():
        if city not in c: continue
        for cat, items in cats.items():
            # title 留空：卡片回退显示 城市+类别，不再叠加重复类别标签
            c[city][cat] = [{'title': '', 'content': s} for s in items]
        updated += 1
json.dump(data, open(JP, 'w', encoding='utf-8'), ensure_ascii=False, indent=2)
print('updated tier-1 cities:', updated)
