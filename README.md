# Rail Focus（铁道专注）

Rail Focus 是一款高铁出行主题的 Android 应用：选择出发城市和愿意乘坐的时长，应用会推荐这段时间内所有可达的火车站。旅途中它通过定位追踪行程进度，并提供专注计时器，让乘车时间变成一段安静专注的时光。

## 功能特性

- **可达车站推荐** —— 基于预置铁路网络的图搜索（Dijkstra + BFS），结合列车速度模型（加速、巡航、减速及经停站停留时间）计算贴近实际的车程
- **行程追踪** —— 基于 GPS 的进度追踪，自动检测到达车站
- **专注模式** —— 前台服务计时器，行车途中持续运行
- **行程历史与统计** —— 历史行程记录，配合图表展示（Vico）
- **离线地图** —— MapLibre GL 加载免费的 OpenStreetMap 瓦片，无需 API Key
- **浅色 / 深色主题**、新手引导流程，设置项通过 DataStore 存储

## 技术栈

- **UI**：Jetpack Compose，Material 3
- **架构**：Clean Architecture（`data` / `domain` / `ui` 分层），经用例层单向传递数据
- **依赖注入**：Hilt
- **持久化**：Room 预置数据库（1,477 个车站、4,073 条边，通过 `createFromAsset()` 加载），DataStore 存储偏好
- **异步**：Kotlin Coroutines + Flow
- **定位**：Google Play Services Location
- **地图**：MapLibre GL Native
- **图表**：Vico
- **最低 / 目标 SDK**：26 / 37

## 项目结构

```
app/src/main/java/com/hsr/railfocus/
├── data/          # Room、图算法（RailGraph）、仓库、定位服务、DataStore
├── domain/        # 领域模型、仓库/服务接口、用例
├── ui/            # 按功能划分的 Compose 界面（home、focus、history、settings 等）
├── di/            # Hilt 模块
├── service/       # 前台服务（专注计时器）
└── util/          # 工具类
```

## 构建与测试

```bash
./gradlew build                  # 构建应用
./gradlew test                   # 运行单元测试（JUnit 4 + MockK）
./gradlew connectedAndroidTest   # 仪器化测试（需连接设备或模拟器）
./gradlew installDebug           # 安装 Debug APK
```

数据库工具位于 [scripts/](scripts/)：

```bash
python scripts/rebuild_db.py     # 修改表结构后重新生成预置数据库
python scripts/check_edges.py    # 校验边数据完整性
```

更多开发指引（如何新增界面、修改图算法、变更数据库结构等）见 [AGENTS.md](AGENTS.md)。

## 许可证

待定。
