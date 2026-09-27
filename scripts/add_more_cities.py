# -*- coding: utf-8 -*-
import json, os
BASE = os.path.dirname(os.path.abspath(__file__))
JSON_PATH = os.path.join(BASE, '..', 'app', 'src', 'main', 'assets', 'city_facts.json')
NEW_LABELS = {'railway':'铁路','landmark':'地标','trivia':'趣闻','specialty':'风物','travel':'漫游'}
OLD_TPL = {
 'history': ['{city}历史悠久，是{prov}地区人文底蕴深厚之地','{city}见证了{prov}千年的商旅往来与文化交流'],
 'geography': ['{city}地处{prov}，自然风光与人文景观相映成趣','{city}四季分明，地理风貌独具特色'],
 'culture': ['{city}民俗淳朴，保留丰富的{prov}传统文化','{city}民间节庆与传统手工艺传承有序'],
 'food': ['{city}美食融合{prov}风味，地道小吃令人回味','{city}物产丰富，特色菜肴独具地方特色'],
}
CAT_LABEL = {'history':'历史','geography':'地理','culture':'文化','food':'美食'}
# 补充的旅游相关城市/景区：只精写 railway/landmark/trivia，其余走模板
MORE = {
 '九华山': {'prov':'安徽','railway':['池黄高铁经过九华山站，九华山接入全国高铁网','九华山站紧邻九华山风景区，下车即达佛国圣地'], 'landmark':['九华山是中国佛教四大名山之一，地藏菩萨道场','九华山九十九峰，天台峰为最高峰'], 'trivia':['九华山是地藏王菩萨道场，香火鼎盛','九华山肉身菩萨之谜令人称奇']},
 '雁荡山': {'prov':'浙江','railway':['杭温高铁经过雁荡山站，杭州至雁荡山约1小时','雁荡山站紧邻雁荡山风景区'], 'landmark':['雁荡山是世界地质公园，以奇峰瀑布著称','灵峰、灵岩、大龙湫为雁荡三绝'], 'trivia':['雁荡山因山顶湖泊、芦苇丛生、秋雁宿之得名','雁荡山夜景灵峰夫妻峰惟妙惟肖']},
 '天台山': {'prov':'浙江','railway':['杭台高铁经过天台山站，杭州至天台山约40分钟','天台山站接入全国高铁网'], 'landmark':['天台山是佛教天台宗发源地','国清寺是隋代古刹，天台宗祖庭','石梁飞瀑是天下第一奇观'], 'trivia':['天台山是济公故里','天台山云雾茶是绿茶名品']},
 '太姥山': {'prov':'福建','railway':['温福铁路经过太姥山站，太姥山站紧邻景区'], 'landmark':['太姥山是世界地质公园，以奇峰怪石著称','太姥山三面临海，山海相依'], 'trivia':['太姥山相传是太母娘娘修行之地','太姥山白茶品质优异，福鼎白茶核心产区']},
 '楠溪江': {'prov':'浙江','railway':['杭温高铁经过楠溪江站，楠溪江接入高铁网'], 'landmark':['楠溪江是国家级风景名胜区，以水秀、岩奇、瀑多著称','楠溪江古村落保存完好的宋代田园风貌'], 'trivia':['楠溪江田园牧歌式风光被称为永远的山水诗','楠溪江古村落群是中国乡土文化的史书']},
 '罗浮山': {'prov':'广东','railway':['广汕高铁经过罗浮山站，罗浮山接入高铁网'], 'landmark':['罗浮山是岭南第一山，道教名山','罗浮山飞云顶为最高峰，云海壮观'], 'trivia':['罗浮山是葛洪炼丹之地，道教南宗祖庭','罗浮山百草油是著名中成药']},
 '芙蓉镇': {'prov':'湖南','railway':['张吉怀高铁经过芙蓉镇站，芙蓉镇通高铁'], 'landmark':['芙蓉镇因电影《芙蓉镇》闻名，挂在瀑布上的千年古镇','芙蓉镇瀑布穿镇而过，夜景迷人'], 'trivia':['芙蓉镇原名王村，因电影改名','芙蓉镇是土家族古镇，摆手舞、茅古斯舞是特色']},
 '南平': {'prov':'福建','railway':['合福高铁、南三龙铁路经过南平，南平接入全国高铁网','延平站、南平市站是主要车站'], 'landmark':['武夷山是世界文化与自然双遗产，南平紧邻武夷山脉','南平是朱子故里，朱熹理学发源地'], 'trivia':['南平是福建面积最大的地级市','武夷岩茶大红袍产自南平武夷山']},
 '横琴': {'prov':'广东','railway':['珠机城际经过横琴站，横琴连接珠海与澳门'], 'landmark':['横琴长隆海洋王国是大型海洋主题乐园','横琴岛与澳门隔江相望'], 'trivia':['横琴是国家级新区、粤澳深度合作区','横琴是澳门经济多元发展的新平台']},
 '美兰': {'prov':'海南','railway':['海南环岛高铁美兰站与海口美兰机场无缝衔接，空铁联运','美兰站是环岛高铁重要站点'], 'landmark':['美兰紧邻海口美兰国际机场','海口骑楼老街在附近'], 'trivia':['美兰机场是海南最大航空枢纽','空铁联运让美兰成为进出海南的重要门户']},
 '资阳': {'prov':'四川','railway':['成渝高铁经过资阳，资阳至成都半小时','资阳是成渝双城经济圈节点城市'], 'landmark':['安岳石刻是唐宋石窟艺术瑰宝','资阳是三贤故里，苌弘、王褒、董钧'], 'trivia':['资阳是陈毅元帅故里','安岳石刻十万尊，中华石刻之乡']},
 '内江': {'prov':'四川','railway':['成渝铁路、成渝高铁经过内江，内江至成都、重庆均约40分钟','内江是成渝铁路重要节点'], 'landmark':['张大千纪念馆在内江，大千故里','内江圣水寺是川中名刹'], 'trivia':['内江是国画大师张大千故里','内江牛肉面是特色早餐']},
 '昌吉': {'prov':'新疆','railway':['兰新铁路、乌昌城际经过昌吉','昌吉紧邻乌鲁木齐，融入乌鲁木齐都市圈'], 'landmark':['天山天池位于昌吉阜康市','昌吉恐龙馆是新疆最大恐龙博物馆'], 'trivia':['昌吉是新疆重要的农牧业基地','昌吉回民小吃街民族风情浓郁']},
 '定西': {'prov':'甘肃','railway':['陇海铁路、宝兰高铁经过定西','定西北站接入全国高铁网'], 'landmark':['贵清山是陇中名山','渭河源是渭河发源地'], 'trivia':['定西是马铃薯之乡，中国薯都','定西是丝绸之路重镇']},
 '濮阳': {'prov':'河南','railway':['郑济高铁经过濮阳，濮阳接入全国高铁网','濮阳东站是主要高铁站'], 'landmark':['戚城遗址是春秋卫国都城','濮阳杂技享誉中外'], 'trivia':['濮阳是中华龙乡，蚌塑龙出土于此','濮阳是杂技之乡，东北庄杂技是国家级非遗']},
}
def mk(lst, city, prov, label):
    return [{'title': f'{city}{label}', 'content': s.format(city=city, prov=prov)} for s in lst]
with open(JSON_PATH, encoding='utf-8') as f:
    data = json.load(f)
c = data['cities']
added = 0
for name, info in MORE.items():
    if name in c: continue
    prov = info['prov']
    entry = {'province': prov, 'tier': 3}
    for cat in ['history','geography','culture','food']:
        entry[cat] = mk(OLD_TPL[cat], name, prov, CAT_LABEL[cat])
    for cat in ['railway','landmark','trivia','specialty','travel']:
        if cat in info:
            entry[cat] = mk(info[cat], name, prov, NEW_LABELS[cat])
        else:
            tpl = {'railway':['{city}是'+prov+'铁路网络的重要节点'],'landmark':['{city}拥有独特的城市景观'],'trivia':['{city}值得细细品味'],'specialty':['{city}物产丰富'],'travel':['{city}适合放慢脚步感受风土人情']}
            entry[cat] = mk(tpl[cat], name, prov, NEW_LABELS[cat])
    c[name] = entry
    added += 1
data['meta']['total_cities'] = len(c)
with open(JSON_PATH, 'w', encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)
print('added', added, 'cities; total', len(c))
