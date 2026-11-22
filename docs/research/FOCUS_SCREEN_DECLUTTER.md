# 专注页「去噪」研究与重构规格

> 对标：FocusFlight（专注飞机，id6648771147，App Store「App of the Day」）为主，辅以 Forest / 潮汐 这类「单焦点计时」产品的通用做法。
> 结论基于 `FocusSessionScreen.kt`(404 行)、`FocusComponents.kt`(1257 行)、`MapLibreView.kt` 的实际代码，以及 promo 截图 04。

---

## 0. 先看一个必须确认的问题

**当前这份代码里，专注页没有倒计时。**

- `CountdownDisplay(remainingSeconds)` 定义在 `FocusComponents.kt:72`，**全项目没有任何调用点**。
- `FocusSessionScreen.kt:140-243` 的页面结构是：顶部区域 → `Spacer(weight 1)`（一片空白）→ `StationInfoCard`。
- 唯一用到 `uiState.remainingSeconds` 的地方是 `:287`（取消后的未完成车票算时长）。

也就是说：**屏幕正中是空的，而所有次要信息都挤在上下两条边上**。这正是「噪点太多」的根因——不是元素数量超标，而是**没有主角，所有元素被迫平级**。

> 如果这不是你本地的临时改动，这是一个必须最先修的回归。promo 截图 04 里那个巨大的 `14:55` 证明它曾经存在。
> 另外 `RouteProgressionStrip`（`FocusComponents.kt:380`，站序示意条）同样是「写好了但没接线」的状态。

---

## 1. 诊断：噪点不是数量，是「中心竞争」

把 FocusFlight 的飞行中界面和你的专注页并排看，会得到一个反直觉的结论：**它的按钮比你多，却比你安静。**

| | FocusFlight 飞行中 | RailFocus 专注页 |
|---|---|---|
| 按钮 | 6 个（暂停/声音/视角 · 路线/定位/图层） | 4 个（声音/暂停/结束） |
| 按钮位置 | 全部贴左右边缘，纵向排列 | 与 3 行标题**挤在同一条水平线上** |
| 按钮材质 | 统一玻璃圆、低对比、半透明 | 白底 + `tonalElevation 2dp` + 一个红色图标 |
| 屏幕中心 | 飞机 + 航线，干净 | **空的** |
| 主数字 | 底部左右两个（剩余时间 / 剩余距离） | **没有** |
| 地图文字 | 只有起终点与当前 | **每个途经站都有一个名牌 pill** |
| 可退出复杂 | 一键「纯净模式」，双击退出 | 无 |

由此得到三条可迁移的判定标准：

1. **中心只能有一个东西**。FocusFlight 的中心是地图 + 飞机；你的中心是空白，所以视线被迫在上下两条边来回跳。
2. **边缘可以放很多，但必须同材质、同尺寸、同对比度**。你的 4 个按钮里，白噪音是 45dp 圆、暂停/结束是 46dp 圆、但三个底色不同（`primaryContainer` / `surface 0.92` / `surface 0.92`），结束按钮还用了红色——红色在专注场景里是**警报级**信号，出现在每一秒都在看的屏幕上，就是持续的紧张源。
3. **文字越靠近中心越贵**。顶部那 3 行（正在前往 / 终点站 / 车次·座位·场景）全部居中、全部压在视觉焦点区，而它们的信息价值从「每秒」到「一次性」不等：车次号和座位是**开始前**才需要确认的，专注中完全不需要。

### 1.1 当前同屏元素清单（18 类）

| # | 元素 | 出处 | 更新频率 | 判定 |
|---|---|---|---|---|
| 1 | 白噪音圆钮 | `FocusSessionScreen.kt:154-178` | 静态 | 降级（进「更多」） |
| 2 | 「正在前往」 | `:187` | 静态 | 删（无信息） |
| 3 | 终点站名 | `:192` | 静态 | 保留但缩小，移到语境层 |
| 4 | 车次·座位·场景 一行 | `:207-217` | 静态 | 移出（开始前信息） |
| 5 | 暂停圆钮 | `:363` | 静态 | 保留，边缘化 |
| 6 | 结束圆钮（红） | `:386-402` | 静态 | 降级到「更多」，去红 |
| 7 | StationInfoCard 容器 | `FocusComponents.kt:160` | 静态 | 删除容器（不需要卡片） |
| 8 | 「当前站」label | `:264` | 静态 | 合并进一行 |
| 9 | 当前站名 | `:265` | 每段变化 | 保留 |
| 10 | 速度数字 + 单位 | `:279-313` | **每秒** | 降级（全量档） |
| 11 | 「下一站」label | `:317` | 静态 | 合并 |
| 12 | 下一站名 | `:319` | 每段变化 | 保留 |
| 13 | 路线 polyline | `MapLibreView.kt:1246` | 静态 | 保留（加强） |
| 14 | 每个途经站 pin | `MapLibreView.kt:1143` | 静态 | 只留 2 个 |
| 15 | 每个途经站名牌 pill | `MapLibreView.kt:1431` | 静态 | **只留 3 个** |
| 16 | 列车 marker | `MapLibreView.kt:1329` | 每秒 | 保留（唯一动点） |
| 17 | 定位 marker | `MapLibreView.kt:1108` | 静态 | 专注页删除（你在车上，不在街上） |
| 18 | 天气覆盖层 | `WeatherAnimationOverlay` | 每秒 | 默认关（见 §6） |

**噪点公式**：`同屏文字块 × 变化频率 × 靠近中心的程度`。速度数字之所以最刺眼，是因为它同时满足「贴近中心 + 每秒变化 + 大字号 Black」。

---

## 2. 参考 FocusFlight 的六条规则

FocusFlight 官方站点的几句话，几乎就是这份重构的提纲：

| 原文 | 迁移到 RailFocus |
|---|---|
| 「移动的飞机会提供**低干扰**的进度提示，让你一眼查看，又不会把专注变成分数竞赛」 | 进度用一条 2dp 线 + 一个光点表达，不要用百分比数字、不要用刻度 |
| 「飞行元素只是为这段时间**赋予形状**，不是为了增加使用负担」 | 车次号/座位/场景是「登机前」的信息，不该占用飞行中的屏幕 |
| 「如果空间上的进展能激励你，就保留地图；如果它让你分心，就在**纯净模式**中隐藏」 | 给三档密度，默认标准档，纯净档可双击切换 |
| 纯净模式：控件全隐、`双击退出` | 不要用「设置里关掉」这种重决策，用一次性手势 |
| 底部只有 `Time Remaining` / `Distance Remaining` 两个数字 | 主数字只留两个：剩余时间、剩余距离（你已经有 `PathResult.totalDistanceKm`） |
| 控制按钮全部贴边、玻璃圆、同尺寸 | 统一控件规格：44dp 圆、`surface.copy(0.55f)` + 1px 高光，不用红色 |

> 一个反面参照：FocusFlight 明确写了「计时器从你实际拥有的时间出发，**而不是由两个机场之间的距离决定**」。你的产品逻辑相反（时长决定能到哪）——这是你的特色，不要为了「像飞行」而动摇。

---

## 3. 新布局规格

### 3.1 三段式

```
┌──────────────────────────────────────────┐
│ ‹                      ◐        ⋯        │  A 边缘层：44dp 玻璃圆，距边 16dp，距顶 statusBar+8
│                                          │
│                                          │
│                 (地图)                    │  B 画布层：只画 3 个标签 + 列车
│                  ●                       │
│                                          │
│                                          │
│  21:37 双鸭山西             桦南东 23:23  │  C 语境层（1 行，labelMedium，50% 透明）
│  ━━━━━━━━━━━━●──────────────────────     │  D 进度线（2dp，光点在头部）
│                                          │
│  14:55                                   │  E 主信息：剩余时间（72sp，tnum，Black）
│  已行进 42 / 64 km                        │  F 副信息（bodySmall，55%）
└──────────────────────────────────────────┘
```

- **A 边缘层**：3 个圆钮。左：返回（`‹`，替代现在藏在系统返回键里的退出）。右：暂停/继续、更多（`⋯`，展开：结束旅程 / 白噪音 / 报告问题）。
- **B 画布层**：地图。列车是唯一每秒变化的图形。
- **C 语境层**：`起点时间 起点站 ─── 终点站 预计到达`，一行。等宽数字，不换行。
- **D 进度线**：2dp，已走 `primary`，未走 `onSurface 12%`，头部 6dp 光点。
- **E 主信息**：72sp（`displayLarge` 放大或自定义），`fontFeatureSettings = "tnum"`，只做 120ms 淡入，**不做数字滚动**。
- **F 副信息**：`已行进 42 / 64 km`，可选后缀 `· 63 km/h`（只在全量档）。

### 3.2 尺寸与颜色

| 元素 | 规格 |
|---|---|
| 边缘圆钮 | 44dp，`surface.copy(alpha = 0.55f)`，1dp `white 55% → 0` 渐变描边，图标 20dp，`onSurface 80%` |
| 圆钮间距 | 16dp |
| 主数字 | 72sp / lineHeight 76sp / letterSpacing -2sp / `onBackground` |
| 进度线 | 高 2dp，圆角 1dp，左右 24dp |
| 语境行 | `labelMedium`，`onBackground 55%`，站名用 `titleSmall` 加粗 |
| 底部留白 | `navigationBarsPadding() + 24dp` |
| 地图内缩 | 顶部 120dp（给边缘层让位），底部 220dp（给信息块让位） |

### 3.3 状态矩阵

| 状态 | 变化 |
|---|---|
| 巡航 | 上面这套 |
| 停靠（`isDwelling`） | 语境行替换为：`停靠 双鸭山西 · 2 分钟`，主数字下方出现一条 3dp 的停靠倒计时细线（不要整块换布局） |
| 暂停（`isPaused`） | 主数字降到 40% 透明 + 一个 `已暂停` 的 badge；**不要脉冲闪烁**（每秒闪 = 噪点） |
| 接近终点（progress > 0.9） | 进度线末端光点变金色，主数字下方换一行 `即将抵达 桦南东` |
| 完成 | 交给 `CompletionOverlay`（那一屏可以热闹，因为它是奖励时刻） |

### 3.4 三档密度（渐进披露）

| 档 | 显示 | 进入方式 | 适用 |
|---|---|---|---|
| **纯净** | 只有主数字 + 进度线 | 双击屏幕任意处 | 长专注（≥ 45 分钟） |
| **标准**（默认） | 主数字 + 语境行 + 进度线 + 边缘层 | 默认 | 大多数 |
| **全量** | 标准 + 站序条（复用 `RouteProgressionStrip`）+ 速度 | 点 `⋯` → 「显示行驶信息」 | 想「看风景」的用户 |

**自动淡出**：30 秒无操作，边缘层透明度降到 0.35（不消失，避免找不到）；触摸任意处恢复到 1。这比「设置里关掉」轻，比「一直显示」静。

---

## 4. 地图去噪规则

专注页的地图不需要「信息」，只需要「我在前进」。

| 规则 | 现状 | 改为 |
|---|---|---|
| 名牌数量 | 每个途经站一个 pill（`MapLibreView.kt:1431` 遍历全部） | 只保留 **当前站 / 下一站 / 终点** 3 个 |
| 站点 pin | 全部途经站（`stations = routeStations.drop(1)`） | 传 `listOfNotNull(next, end)` |
| 定位 marker | 始终显示 | 专注页传 `null`（你不是在街上走） |
| 路线 | 单线 60% 透明 | 三层辉光（18/7/3px），见上一份文档 §5.1 |
| 底图文字 | OSM 全量标签 | 专注页把 label 层 `text-opacity` 降到 0.45，让地名退到背景 |
| 相机 | `cameraInsetTop 300 / bottom 180` | 顶部 120 / 底部 220（配合新布局），`zoom` 略降 0.3，让路线更完整 |
| 倾斜 | 无 | 进专注时 `tilt = 35°`，600ms 缓入；这让「前进」有透视感，成本极低 |

> MapLibre 支持 `CameraPosition.tilt`，一次设置即可，不需要每帧更新。

---

## 5. 天气：从「覆盖层」改成「介质层」

`WeatherAnimationOverlay` 现在是一层横穿全屏的雨丝（见 promo 截图 04 的斜线），它压在计时器和站名上，是**字面意义的视觉噪点**。而且它每帧 `setProperties` 的 canvas 开销不小。

专注页的正确做法：

1. **默认关闭**（跟随设置，但专注页默认不叠加）。
2. 如果用户开启，把它**限制在地图区域内部**：`Box(Modifier.fillMaxHeight(0.62f))` + 顶部一条 24dp 的渐变遮罩，让雨「下在地图上」，而不是「下在 UI 上」。
3. 雨丝透明度 ≤ 0.18，密度减半，加 1 层 8% 的雾（`Brush.verticalGradient`），这样更像「窗外」而不是「划痕」。
4. 只在天气切换时淡入，不要一直高频重绘；粒子用 `withInfiniteAnimationFrameNanos` 并限制 30fps。

---

## 6. 动效与触感（专注页专属，要更克制）

专注页的动效原则：**允许缓慢，禁止频繁**。

| 元素 | 允许 | 禁止 |
|---|---|---|
| 主数字 | 秒变化时 120ms 淡入 | 滚动/翻转/缩放 |
| 进度线 | 光点随进度线性移动 | 呼吸/闪烁 |
| 列车 | 匀速前进 + 停靠时轻微晃动 | 脉冲光环 |
| 边缘层 | 30s 淡到 0.35 | 一直明暗呼吸 |
| 暂停 | 整体降到 40% + 一次 200ms 的缩放 0.98 | 无限脉冲（现在 `FocusSessionScreen.kt:347` 就是无限 alpha 呼吸） |

**触感**：只保留 3 个时刻——开始、暂停/继续（12ms 轻点）、到站（波形）。滑块与按钮不要每个都振动。

---

## 7. 代码骨架

不需要新的状态，`FocusSessionUiState` 已经够用（距离用 `path.totalDistanceKm * (1 - overallProgress)`）。

```kotlin
@Composable
fun FocusHud(
    state: FocusSessionUiState,
    chromeVisible: Boolean,
    onPauseToggle: () -> Unit,
    onExit: () -> Unit,
    onMore: () -> Unit,
) {
    val kmTotal = state.path?.totalDistanceKm?.toFloat() ?: 0f
    val kmDone = kmTotal * state.overallProgress
    val chromeAlpha by animateFloatAsState(if (chromeVisible) 1f else 0.35f, Motion.settle)

    Box(Modifier.fillMaxSize()) {
        // A 边缘层
        EdgeControls(state.isPaused, chromeAlpha, onPauseToggle, onExit, onMore,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp))

        // C/D/E/F 信息块
        Column(
            Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding().padding(horizontal = 24.dp, vertical = 24.dp)
                .fillMaxWidth()
        ) {
            ContextLine(state)                       // 21:37 双鸭山西 ─ 桦南东 23:23
            Spacer(Modifier.height(10.dp))
            ProgressRail(state.overallProgress)      // 2dp + 光点
            Spacer(Modifier.height(14.dp))
            Text(
                text = formatMmSs(state.remainingSeconds),
                fontSize = 72.sp, lineHeight = 76.sp, letterSpacing = (-2).sp,
                fontWeight = FontWeight.Black,
                fontFeatureSettings = "tnum",
                style = MaterialTheme.typography.displayLarge,
            )
            Text(
                text = stringResource(R.string.focus_km_done, kmDone.toInt(), kmTotal.toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            )
        }
    }
}
```

自动淡出（不引入新依赖）：

```kotlin
@Composable
fun rememberChromeAutoHide(idleMs: Long = 30_000): Pair<Boolean, () -> Unit> {
    var visible by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) {
        visible = true
        delay(idleMs)
        visible = false
    }
    return visible to { tick++ }
}

// 根布局：
Box(Modifier.fillMaxSize().pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) { awaitPointerEvent(); poke() }
    }
})
```

地图参数（`FocusSessionScreen.kt:113-128`）：

```kotlin
stations = listOfNotNull(uiState.nextStation, uiState.endStation),   // 只留 2 个
routeStations = routeStations,                                        // 路线照旧
showLocationMarker = false,                                           // 新增参数，专注页关掉
cameraInsetTopDp = 120,
cameraInsetBottomDp = 220,
initialTilt = 35.0,                                                   // 新增
```

---

## 8. 验收清单

改完之后，用这 8 条自检：

| # | 标准 | 判定 |
|---|---|---|
| 1 | 遮住所有文字，仍能一眼看出「还剩多久」 | 眯眼测试 |
| 2 | 屏幕中心只有一个焦点 | 截图缩到 20% 看 |
| 3 | 同屏文字块 ≤ 6 个（现在是 12 个） | 数 |
| 4 | 每秒变化的元素 ≤ 2 个（列车 + 秒数） | 录屏逐帧 |
| 5 | 途经 10 站的长旅程，地图上名词 ≤ 3 个 | 实测 |
| 6 | 30 秒不操作，界面明显变安静但不「消失」 | 静置 |
| 7 | 双击能进入纯净，再双击能出来 | 手势 |
| 8 | 暂停时没有任何闪烁 | 录屏 |

**目标**：同屏元素 18 类 → 7 类；文字块 12 → 6；红色警报色 0 处（除「结束旅程」的确认弹窗）。

---

## 9. 落地顺序

| 步 | 改动 | 风险 |
|---|---|---|
| 1 | **接回 `CountdownDisplay`**，放到屏幕下方，作为唯一主角 | 极低（组件已存在） |
| 2 | 删顶部 3 行里的 2 行，车次信息移到开始前（打卡页） | 低 |
| 3 | 底部 3 列卡片 → 1 行语境 + 1 条进度线 | 中（要挪速度） |
| 4 | 地图只传 2 个站、去掉定位点、加 tilt | 低（加参数） |
| 5 | 边缘控件统一 44dp 玻璃圆，结束收进「⋯」 | 低 |
| 6 | 三档密度 + 30s 自动淡出 + 双击纯净 | 中 |
| 7 | 天气限制在地图区 | 低 |

前 3 步做完，「噪点」问题就解决了 80%，而且不需要改任何业务逻辑。

---

*文档版本：2026-11-22 ／ 基于 Rail Focus 2.0.1*
