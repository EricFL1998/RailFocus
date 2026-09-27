# -*- coding: utf-8 -*-
import json, os, re
BASE = os.path.dirname(os.path.abspath(__file__))
JSON_PATH = os.path.join(BASE, '..', 'app', 'src', 'main', 'assets', 'city_facts.json')
COPY = {'上饶':'上饶市','宜春':'宜春市','大同':'大同南','崇左':'崇左南','平顶山':'平顶山南',
 '忻州':'忻州西','晋城':'晋城东','景德镇':'景德镇北','朔州':'朔州东','来宾':'来宾北',
 '河池':'河池西','泰州':'泰州南','榆林':'榆林南','淮北':'淮北北','万安县':'万安',
 '仙桃':'仙桃西','南溪':'南溪北','周口':'周口西','眉山':'峨眉山','郑州航空港':'郑州',
 '湖州南浔':'湖州','酒泉':'酒泉南','松山湖':'松山湖北','咸阳':'咸阳北'}
# 需要修正的目标城市：为它们写准确的 railway/trivia（替换掉模板里的跨城市引用）
FIX = {
 '上饶': {'railway':['上饶位于沪昆高铁与京福高铁交汇，是赣东北铁路枢纽','上饶站是江西重要的铁路客运站'], 'trivia':['上饶是道教名山三清山所在地','上饶鸡腿是火车站著名小吃']},
 '眉山': {'railway':['成贵高铁、成绵乐城际经过眉山，眉山至成都半小时','眉山东站是主要高铁站'], 'trivia':['眉山是苏东坡故里，三苏祠是文化圣地','眉山彭山是长寿之乡']},
 '宜春': {'railway':['沪昆高铁、宜万铁路经过宜春','宜春站是赣西铁路枢纽'], 'trivia':['宜春因宜春泉得名，宜春游春','明月山温泉是宜春名片']},
 '咸阳': {'railway':['陇海铁路、西宝高铁经过咸阳','咸阳秦都站接入全国高铁网'], 'trivia':['咸阳是秦朝都城，历史文化名城','咸阳是西安都市圈核心城市']},
 '湖州南浔': {'railway':['沪苏湖高铁、宁杭高铁经过湖州南浔','南浔站服务南浔古镇'], 'trivia':['南浔古镇是江南六大古镇之一','南浔辑里湖丝闻名天下']},
 '郑州航空港': {'railway':['郑州航空港站是郑州高铁枢纽的重要组成','郑万、郑阜高铁经过航空港'], 'trivia':['郑州航空港经济综合实验区是国家级新区','新郑国际机场是中部最大航空枢纽']},
}
NL = {'railway':'铁路','trivia':'趣闻'}
with open(JSON_PATH, encoding='utf-8') as f:
    data = json.load(f)
c = data['cities']
for city, fix in FIX.items():
    if city not in c: continue
    for cat, items in fix.items():
        c[city][cat] = [{'title': f'{city}{NL[cat]}', 'content': s} for s in items]
with open(JSON_PATH, 'w', encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
print('fixed', len(FIX), 'cities')
