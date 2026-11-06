# 天时闹钟（MobileAlarm）

一个面向中国用户的 Android 闹钟：按法定节假日 / 调休判断工作日，结合天气预报告诉你「今天该不该提前出门」，并允许为每个闹钟配置可视化天气条件与提前 / 延后分钟数。还支持按步行 / 自行车 / 电瓶车 / 开车 / 公共交通分别反推通勤的最迟出门时间。

- 技术栈：Kotlin、Jetpack Compose（Material 3）、Room、DataStore、WorkManager、AlarmManager
- 最低系统：Android 8.0（API 26），目标 API 35
- 语言：简体中文
- 天气与节假日数据均来自免费、无需 API Key 的公开接口

## 功能

### 1. 法定节假日与调休

- 内置 2024–2026 年国务院放假安排（`app/src/main/assets/holidays.json`），离线可用。
- 自动区分：工作日、调休上班、法定节假日、周末、手动覆盖。
- 闹钟可选择「只响一次」「工作日」「每天」「休息日」「自定义星期」，并可选择是否跟随法定节假日 / 调休。
  - 工作日模式：普通周一至周五响铃，法定节假日不响，调休上班日响铃。
  - 自定义星期 + 跟随节假日：法定节假日与手动「休息日」覆盖会抑制响铃，调休上班与手动「工作日」覆盖会强制响铃。
  - 只响一次：在日历中选择一个具体日期，只在该日响铃；响铃后自动关闭，不影响其它日期。日期可以选到任意远的将来，不受 32 天搜索范围限制。
- 日历页支持按月查看、手动把某天设为「工作日 / 休息日 / 恢复法定」。
- 支持在线更新：从 [holiday-cn](https://github.com/NateScarlet/holiday-cn) 的 jsDelivr CDN 拉取当年及相邻年份数据；每年 12 月中旬后，如果下一年度数据尚未发布，会在日历页提示。
- 数据优先级：手动覆盖 > 在线更新 > 内置数据 > 周一至周五兜底。

### 2. 天气与可视化条件

- 使用 [Open-Meteo](https://open-meteo.com/) 免费预报（无需注册），支持未来 16 天、逐小时数据。
- 城市设置入口：首页顶部的城市文字、天气刷新按钮，或右上角齿轮进入设置；设置里可搜索城市或使用当前位置。
- 城市搜索使用 Open-Meteo Geocoding；也可申请定位权限后使用「当前位置」。
- 每个闹钟可以添加多条天气条件，每条条件独立设置「提前 / 延后」分钟数：
  - 降水概率（≥ / ≤）
  - 天气类型（晴、多云、雾、雨、雪、雷暴；属于 / 不属于）
  - 当前温度、当日最低温、当日最高温（≥ / ≤）
  - 风速（≥ / ≤，km/h）
  - 降水量（≥ / ≤，mm）
  - 紫外线指数（≥ / ≤）
- 多条条件同时满足时，偏移量相加；总偏移限制在 **±180 分钟**，避免出现半夜响铃。
- 闹钟编辑页会实时预览：基准时间、天气偏移、最终时间、命中的条件、当日天气。
- 没有天气数据（未授权、离线、超出预报范围）时，按基准时间响铃，不会因为天气规则漏响。

### 3. 通勤出门提醒（按出行方式倒推）

- 从首页时间线的「出门」节点或设置里的「通勤提醒」进入，输入出发地、目的地、到达时间与缓冲分钟数。
- 先选出行方式：**步行 / 自行车 / 电瓶车 / 开车 / 公共交通**，五种方式分别取耗时，不再统一按驾车算。
- 通过高德地图 Web 服务 API 规划路线：
  - 开车：`/v3/direction/driving`，耗时含实时路况与红绿灯；
  - 步行：`/v3/direction/walking`；
  - 自行车：`/v4/direction/bicycling`；
  - 电瓶车：没有独立路径接口，按骑行路线耗时 × 0.72 估算；
  - 公共交通：`/v3/direction/transit/integrated`，取最快方案，含换乘、步行接驳与候车时间。
- 按 `最迟出门时间 = 到达时间 − 所选方式耗时 − 缓冲` 反推，再提前一个缓冲量提醒。
- 例如 09:00 到达、步行 35 分钟、缓冲 10 分钟：08:25 最迟出门，08:15 提醒你出门。
- 提醒走高优先级通知，锁屏可见；通知里会写明「步行约 35 分钟」等出行方式。
- 开车 / 公交仅在到达前 4 小时内、且耗时超过 12 分钟未更新时自动刷新；步行 / 骑行 / 电瓶车没有实时路况，不会反复请求，节省高德配额。
- 可设置「仅工作日提醒」，自动跟随法定节假日 / 调休；周末与节假日不提醒。
- 需要用户自行申请**高德「Web 服务」Key**（个人开发者有免费额度，具体以高德控制台为准）。应用不内置共享 Key，也不会上传 Key。公共交通会优先使用地点搜索返回的城市名，其次使用天气设置中的城市。

### 4. 闹钟运行

- 新建 / 编辑闹钟时用「上午 / 下午 · 小时 · 分钟」三列滚轮选时间，带吸附与触感反馈。
- 使用 `AlarmManager.setAlarmClock()` 安排精确闹钟，状态栏显示下一次闹钟图标；无精确闹钟权限时降级为 `setAndAllowWhileIdle`。
- 响铃时启动前台服务，循环播放系统闹钟铃声（可自定义铃声）、按需震动。
- 手机解锁时显示高优先级横幅通知，可直接「稍后提醒」或「关闭闹钟」；锁屏 / 熄屏时拉起全屏响铃页。
- 响铃通知带 10 分钟倒计时（Android 原生 chronometer），锁屏可见；Android 没有 iPhone 灵动岛的公开 API，这是最接近的替代方案。
- 支持「稍后提醒」（可选 5 / 10 / 15 / 20 / 30 分钟）与「关闭闹钟」。
- 「只响一次」闹钟在触发后立即自动关闭；「稍后提醒」仍然有效，但不会因为稍后提醒再排下一次。
- 关闭或稍后提醒后，重复闹钟自动计算并安排下一次响铃。
- 开机、时间/时区变化、应用更新后自动重排闹钟。
- 天气每 3 小时、节假日数据每 12 小时后台刷新（WorkManager，带网络约束）。

### 5. 界面：纸上时辰

1.3.0 起，首页、编辑页与响铃页换成「纸上时辰」手账风，不再是卡片列表式仪表盘：

- 暖纸底色、墨色文字、朱砂红只标记「现在 / 已调整」；分隔用发丝线，按钮只保留按下去才需要的圆角。
- 首页只有一个主角：下一次实际响铃时间（含天气调整后）。下方按「现在 → 起床 → 出门」排成一条时间线，通勤提醒成为「出门」节点，不再单独占一张卡。
- 天气条件在编辑页以编号卡片呈现，直接写清「明早 07:20 下雨 · 提前 15 分钟」，点开卡片即可改阈值、天气类型与调整幅度。
- 响铃页改为夜间纸面：稀疏表盘、大号衬线时间、只留「关闭」与「稍后 10 分钟」两个动作，按钮为低对比的纸白描边。
- 字体使用系统衬线族显示时间与大标题，正文保持无衬线；大字号时间不再用装饰渐变或发光。
- 设计稿、设计说明与可复现的 HTML/PNG 在 `design/ui-direction-01/`，方便后续继续调整。

### 6. 权限与引导

应用内会显示权限状态卡片，并提供跳转：

- 通知权限（Android 13+，首次启动请求一次）
- 精确闹钟权限（Android 12+）
- 全屏通知权限（Android 14+）
- 忽略电池优化（避免部分国产 ROM 杀后台）
- 定位权限（仅在使用「当前位置」时请求）

## 构建

### 环境

- JDK 17
- Android SDK Platform 35、Build-Tools 35.0.0（或让 Gradle 自动安装）
- 无需本机安装 Gradle，仓库自带 Wrapper

`local.properties` 中的 `sdk.dir` 指向你的 Android SDK：

```properties
sdk.dir=C\:\\path\\to\\Android\\Sdk
```

### 命令

```bash
# Debug APK
./gradlew :app:assembleDebug

# 单元测试
./gradlew :app:testDebugUnitTest

# Android Lint
./gradlew :app:lintDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`（applicationId：`com.mobilealarm.debug`）。

> Wrapper 默认使用腾讯云 Gradle 镜像 `https://mirrors.cloud.tencent.com/gradle/gradle-8.9-bin.zip`。
> 如果网络环境访问该镜像较慢，可将 `gradle/wrapper/gradle-wrapper.properties` 中的 `distributionUrl` 改为
> `https\://services.gradle.org/distributions/gradle-8.9-bin.zip` 或使用本机已解压的 Gradle 8.9。

### 安装

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

安装后请按首页提示授予通知、精确闹钟权限；Android 14+ 建议同时允许全屏通知。

## 项目结构

```
app/src/main/java/com/mobilealarm/
├── MainActivity.kt              # 导航与通知权限请求
├── MobileAlarmApp.kt            # Application / 手动依赖容器
├── alarm/                       # 调度、响铃服务、通知、广播接收器、全屏页
├── data/
│   ├── local/                   # Room 实体、DAO、映射
│   ├── prefs/                   # DataStore 设置
│   ├── remote/                  # HttpURLConnection、Open-Meteo、高德 Web 服务
│   ├── holiday/                 # 节假日合并、在线更新、手动覆盖
│   ├── weather/                 # 天气缓存、定位
│   └── commute/                 # 通勤计划、路线刷新
├── domain/
│   ├── almanac/                 # 二十四节气文案
│   ├── model/                   # 领域模型、序列化
│   ├── holiday/                 # 节假日 JSON 解析、WorkdayCalendar
│   └── rule/                    # 时间计算与天气规则匹配
├── ui/
│   ├── alarms/                  # 首页列表、通勤入口
│   ├── edit/                    # 新建 / 编辑闹钟、滚轮时间选择
│   ├── commute/                 # 通勤计划与出门提醒
│   ├── holiday/                 # 节假日日历
│   ├── settings/                # 城市、天气、权限、数据源
│   └── theme/
├── util/SystemPermissions.kt
└── worker/Workers.kt
```

数据文件：

- `app/src/main/assets/holidays.json`：2024–2026 年内置放假安排。
- 在线节假日：`https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master/{year}.json`
- 天气：`https://api.open-meteo.com/v1/forecast`
- 城市搜索：`https://geocoding-api.open-meteo.com/v1/search`
- 通勤路线（需用户自己的 Key）：
  - 驾车：`https://restapi.amap.com/v3/direction/driving`
  - 步行：`https://restapi.amap.com/v3/direction/walking`
  - 骑行：`https://restapi.amap.com/v4/direction/bicycling`
  - 公交：`https://restapi.amap.com/v3/direction/transit/integrated`
- 地点搜索：`https://restapi.amap.com/v3/place/text`（需用户自己的 Key）

## 测试

当前包含 27 个 JVM 单元测试，覆盖：

- 工作日 / 周末 / 法定节假日 / 调休上班 / 手动覆盖的响铃判断
- 自定义星期与「跟随节假日」的组合语义
- 多条天气规则偏移求和、±180 分钟截断
- 无天气数据时按基准时间
- 下一次响铃跳过已过去时间
- 内置与在线节假日 JSON 解析、无数据年份兜底
- 闹钟 Room 映射与异常数据清洗
- 「只响一次」的日期匹配、超出常规搜索范围的远期日期、过去日期不再排铃
- 通勤公式（到达 − 所选方式耗时 − 缓冲）、跳过节假日、调休上班、仅工作日开关、缺失路线与负缓冲
- 五种出行方式的时间估算（电瓶车按骑行 × 0.72）、旧版通勤 JSON 默认按开车读取、出行方式与城市名的序列化

验证状态（1.4.0）：`assembleDebug`、27 个 JVM 单元测试、`lintDebug` 均通过（lint 0 error / 已接受的警告）。数据库从 v1 升级到 v2 时使用 `ALTER TABLE alarms ADD COLUMN oneTimeDate TEXT` 迁移，已有闹钟会保留；旧版通勤计划没有出行方式字段，升级后默认按「开车」读取。当前构建环境没有可用的硬件虚拟化，Android 模拟器无法启动，也没有连接真机，因此界面与真机响铃流程尚未在设备上实测；设计阶段用 `design/ui-direction-01/` 的 390×844 静态稿校验版式，首次安装后建议先建一个 2 分钟后的测试闹钟，确认通知、全屏页和铃声权限。

## 已知限制

- 2027 年放假安排通常要到 2026 年底才公布；公布前 2027 年按「周一至周五」兜底，日历页会提示更新。
- 天气规则只对预报范围内的日期生效（约 16 天）；更远的日期按基准时间。
- 天气使用所选城市的当地日期 / 小时；若手机与所选城市跨时区，闹钟仍按手机本地时间响铃。
- 通勤路线依赖高德 Web 服务 Key 与网络；个人免费额度、并发限制以高德控制台为准。完全没有 Key 时无法稳定获取耗时。
- 开车 / 公交耗时是导航服务的预测值，不是保证值；恶劣天气、临时管制时请自行留足缓冲。电瓶车没有独立路径规划接口，按骑行路线 × 0.72 估算，实际会受车型、路况和驾驶习惯影响。
- 公共交通需要城市名；优先使用地点搜索返回的城市，其次使用天气设置中的城市。跨城公交或城市名不匹配时可能查不到方案。
- WorkManager 的 30 分钟后台刷新是近似周期，系统可能延后；应用启动、保存计划、到达前 4 小时内的刷新会重新安排提醒。
- Android 没有 iPhone 灵动岛的第三方 API；锁屏倒计时通过高优先级通知的 chronometer 实现，不同 ROM 的展示样式会略有差异。
- 部分国产 ROM 需要手动允许自启动、后台运行与精确闹钟，否则可能延迟或收不到响铃。
- 本项目不收集账号或个人信息；定位仅用于获取天气坐标，不会上传到自建服务器。

## 参考

- 工作日数据与判定思路参考 `D:\code\alarm`（WorkdayAlarm）
- 节假日数据：[NateScarlet/holiday-cn](https://github.com/NateScarlet/holiday-cn)
- 天气数据：[Open-Meteo](https://open-meteo.com/)
- 通勤路线：[高德开放平台 Web 服务](https://lbs.amap.com/api/webservice/summary)