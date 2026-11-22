# Rail Focus UI 一次性落地规格（待确认）

> 合并 `UI_LIVELINESS_RESEARCH.md` 与 `FOCUS_SCREEN_DECLUTTER.md` 两份研究。
> 目标：一次实现，统一「灵动」与「去噪」两条线，避免反复返工。
> 范围：UI 层与资源层，**不改业务逻辑、不改数据库、不改计时算法**。

---

## 0. 一句话方案

**首页灵动化，专注页去噪化，中间用同一套「铁道材质 + 光 = 进度」的设计语言连起来。**

| 层 | 首页 / 选择 | 专注页 |
|---|---|---|
| 视觉主角 | 地图 + 「开始旅程」 | **剩余时间（恢复 CountdownDisplay）** |
| 材质 | 纸感地图 + 浮起卡片 | 地图 + 玻璃边缘控件 |
| 动效预算 | 1 个呼吸 + 1 个怠速 + 场景动效 | 列车位置 + 秒数，其余全静态 |
| 色彩 | 品牌绿 + 信号黄点缀 | 中性为主，去红色警报 |
| 文案 | 可以抒情 | 只留信息 |

---

## 1. 文件级改动清单

### 新增（5 个文件）

| 文件 | 职责 |
|---|---|
| `ui/theme/Motion.kt` | 动效 token（4 档时长 + 4 种曲线 + pressScale 修饰符） |
| `ui/theme/Material.kt` | 三级光影：`PaperBackground` / `FloatingCard` / `GlassSurface` |
| `ui/components/map/RouteGlow.kt` | 三层辉光轨迹 + 路线生长动画（Compose 覆盖层） |
| `ui/components/map/LivingMapEffects.kt` | 昼夜色温层 + 当前站呼吸环 + 列车怠速浮动 |
| `ui/focus/FocusHud.kt` | 新专注页 HUD：语境行 / 进度线 / 主数字 / 副信息 |

### 修改（按依赖顺序）

| # | 文件 | 改什么 | 风险 |
|---|---|---|---|
| 1 | `ui/theme/Theme.kt` | 默认 `dynamicColor = false`；加 `brandFirst` 参数 | 极低 |
| 2 | `ui/theme/Color.kt` | 加信号黄 `#F2A93B`、夜行地图色系 | 极低 |
| 3 | `ui/theme/Type.kt` | 加 `displayTimer`（72sp tnum）、`labelContext` | 极低 |
| 4 | `ui/theme/Motion.kt`（新） | token + `pressScale` + `LocalReduceMotion` | 低 |
| 5 | `ui/theme/Material.kt`（新） | 三级光影组件 | 低 |
| 6 | `ui/components/MapLibreView.kt` | 地图样式 v2（浅色/暗色/铁路提亮）；加 `showLocationMarker`、`initialTilt`、`labelDensity` 参数；站点标签只画关键 3 个 | 中 |
| 7 | `ui/components/map/RouteGlow.kt`（新） | 三层辉光 + 生长动画 | 中 |
| 8 | `ui/components/map/LivingMapEffects.kt`（新） | 昼夜色温 + 呼吸 + 怠速 | 中 |
| 9 | `ui/home/HomeScreen.kt` | 转场改容器形变；主按钮加物理厚度；接 LivingMapEffects | 中 |
| 10 | `ui/home/components/LocationHeader.kt` | 出发信息牌（左侧色条 + 站台牌质感） | 低 |
| 11 | `ui/timeselection/components/DestinationList.kt` | 卡片轮播加视差 + Z 轴旋转 | 低 |
| 12 | `ui/timeselection/components/TimeDurationPicker.kt` | 手柄吸附回弹 + 刻度点亮扩散 | 低 |
| 13 | `ui/focus/FocusSessionScreen.kt` | **重构**：删顶部 3 行改语境行；接 FocusHud；边缘控件统一 44dp 玻璃圆；结束收进「⋯」；加纯净模式 + 30s 自动淡出 | 高 |
| 14 | `ui/focus/FocusHud.kt`（新） | 专注页信息块 | 中 |
| 15 | `ui/components/FocusComponents.kt` | `CountdownDisplay` 去边框改玻璃 + 去掉冒号闪烁；`StationInfoCard` 改为可选（全量档）；完成页保留但去掉 dots_orbit 无限动画 | 中 |
| 16 | `ui/focus/TicketCheckInScreen.kt` | 车票纸纹 + 盖章过冲 + 墨迹收敛；车次/座位信息集中在这里 | 低 |
| 17 | `ui/history/components/TrainTicketCard.kt` | 纸纹 + 压印质感 | 低 |
| 18 | `ui/components/weather/WeatherAnimationOverlay.kt` | 加 `bounded` 模式，专注页限制在地图区 | 低 |
| 19 | `res/values/strings.xml` | 新增约 15 条（语境行、里程、纯净模式提示、专注页「更多」项） | 极低 |
| 20 | `ui/settings/SettingsScreen.kt` | 加「专注页密度」设置项（纯净/标准/全量） | 低 |

### 明确不做（本期）

- 不加第二个角色（小鹿/精灵）——先验证列车替身是否成立。
- 不做「铁路星座图」完整版——放在下一期，因为它需要独立的可视化设计。
- 不做车窗 3 层视差——需要美术资源，先用「地图 tilt + 速度线」替代。
- 不改 `MaterialExpressiveTheme`——1.4.0 的实验 API 风险大，用 token 手工覆盖即可。

---

## 2. 关键交互规格

### 2.1 首页

- 静置 3 秒可见：当前站呼吸环（12fps，MapLibre 单 layer）、昼夜色温漂移（每分钟更新）、列车怠速浮动（±1.5dp / 2.4s）。
- 主按钮：按下 0.96 → 松手回弹（`Motion.press`，d=0.55）+ 120ms 涟漪。
- 进入路线选择：header 与主按钮 `sharedBounds` 形变（已有），地图相机 480ms 推近。

### 2.2 路线选择

- 改时长：旧线淡出（160ms）→ 新线从起点生长到终点（480ms）→ 站点依次弹入（28ms stagger）→ 数字 count-up（240ms）。
- 卡片轮播：未选中项 `translationX` 8% 视差 + 2° 旋转。
- 滑杆：吸附时 `Settle` 回弹 + 8ms 触感 + 刻度点亮扩散（80ms）。

### 2.3 打卡页

- 车票滑入（已有）+ 纸纹。
- 打孔：盖章快速落下（80ms，过冲 1.12）→ 回弹 → 墨迹 blur 3px→0.4px（120ms）→ 纸张抖动 1.5dp。
- 打孔音 + 振动 + 动画三者同时。

### 2.4 专注页（重点）

```
┌──────────────────────────────────────────┐
│ ‹                        ⏸       ⋯       │  44dp 玻璃圆，30s 后降到 35%
│                                          │
│                (地图 tilt 35°)            │  只画 当前站/下一站/终点 3 个标签
│                  🚄                       │  列车是唯一动点
│                                          │
│  21:37 双鸭山西             桦南东 23:23  │  1 行语境
│  ━━━━━━━━━━━━●──────────────────────     │  2dp 进度线 + 光点
│                                          │
│  14:55                                   │  72sp tnum，唯一主角
│  已行进 42 / 64 km                        │  bodySmall 55%
└──────────────────────────────────────────┘
```

- **双击** → 纯净模式（只剩主数字 + 进度线），再双击退出。
- **30 秒无操作** → 边缘控件降到 35% 透明（不消失）。
- **暂停** → 主数字降到 40% + `已暂停` badge，无闪烁。
- **停靠** → 语境行换为 `停靠 双鸭山西 · 2 分钟`，主数字下方出现停靠倒计时细线。
- **接近终点**（progress > 0.9）→ 进度线末端光点变金色。
- **天气** → 默认关，开启时只画在地图区（上 62%）。

### 2.5 到站

- 相机 `zoom +0.6` + tilt，600ms 缓入后回正。
- 报站音 + 振动 + 盖章三者同时。
- 车票飞入「我的」入口（`sharedBounds`）。

---

## 3. 动效 token 总表（实现时只允许用这些值）

| Token | 值 | 用在哪 |
|---|---|---|
| Instant | 90ms | 按下、勾选 |
| Fast | 160ms | 颜色、透明度 |
| Base | 240ms | 卡片展开、列表位移 |
| Scene | 480ms | 场景转换、路线生长 |
| Press | spring(d=0.55, k=900) | 按压回弹（带过冲） |
| Settle | spring(d=0.78, k=420) | 吸附、归位 |
| Float | spring(d=0.9, k=180) | 呼吸、怠速 |
| Stagger | 28ms × index | 列表/站点依次 |

**无限动画预算**：每屏 ≤ 1 个（专注页 0 个，靠数据驱动）。

---

## 4. 验收标准

### 首页
- 静置 3 秒，能看到 3 处缓慢变化。
- 品牌色不再随壁纸变化。

### 专注页
- 同屏文字块 ≤ 6 个（现在 12 个）。
- 每秒变化的元素 ≤ 2 个（列车 + 秒数）。
- 途经 10 站的长旅程，地图上名词 ≤ 3 个。
- 30 秒不操作，界面明显变安静。
- 双击能进出纯净模式。
- 暂停时没有任何闪烁。

### 通用
- 每屏无限动画 ≤ 1。
- 所有动画状态读取在 `graphicsLayer` / `drawBehind` 内。
- 开启系统「移除动画」后界面完整可用。

---

## 5. 实施顺序与回滚点

| 阶段 | 内容 | 回滚点 |
|---|---|---|
| A | token + 材质 + 关动态取色 | 只影响视觉，随时可回滚 |
| B | 地图样式 v2 + 辉光轨迹 | 样式独立，可切回 v1 |
| C | 首页动效 + 转场 | 不涉及业务 |
| D | **专注页重构**（最大改动） | 单独 commit，可整体回滚 |
| E | 车票/到站/文案 | 独立 |
| F | 设置项 + 减少动效适配 | 独立 |

每阶段完成后 `./gradlew assembleDebug` 验证；D 阶段额外跑一次专注流程的 instrumented test（如果已有）。

---

## 6. 需要用户确认的 3 个决策

1. **车次号/座位号是否从专注页移除？** 建议：移到打卡页（FocusFlight 也是这么做的——登机牌上有座位，飞行中不显示）。
2. **纯净模式的进入手势**：双击屏幕 vs 长按？建议双击（FocusFlight 同款），但需要防误触（双击间隔 < 300ms 才算）。
3. **专注页天气默认关闭**：是否同意？（首页天气保留）

---

*状态：待确认后一次性实施*
*版本：2026-11-22*
