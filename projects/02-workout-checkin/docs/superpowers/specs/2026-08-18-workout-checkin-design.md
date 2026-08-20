# 运动打卡 App 设计文档

- 日期：2026-08-18（初版）；2026-08-18（技术栈改为 Kotlin 原生）
- 平台：Android
- 目标：个人使用，本地数据，无需账号与后端

## 1. 背景与目标

做一个简易的运动打卡 App，帮助用户每天在家做无器械运动并坚持打卡。核心价值是"低门槛坚持"：

- 记录"我今天运动了"：选择运动类型 + 时长，完成签到
- 连续打卡激励机制：打卡日历 + 连胜天数
- 内置居家无器械运动库：每种运动带文字步骤、时长建议和视频链接，离线可看

## 2. 技术栈

| 层 | 技术 | 职责 |
|---|---|---|
| UI | Kotlin + Jetpack Compose | 界面、交互、倒计时 |
| 架构 | MVVM（ViewModel + Repository） | 状态管理与数据流 |
| 数据 | Room（SQLite） | 打卡记录持久化 |
| 构建 | Android Studio + Gradle | 编译、打包、真机安装 |

> 不需要 NDK（无 Rust/C++），环境现成（Android Studio + JDK 21）。

## 3. 架构与数据流

```
Compose 界面 ──观察──> ViewModel ──调用──> Repository ──> Room(SQLite)
      <──State 驱动──  <──Flow 返回──     <────读/写────
                                 └──> domain/streak（纯函数，单测）
```

- 界面通过 ViewModel 暴露的 `StateFlow` 渲染；用户操作调 ViewModel 方法
- ViewModel 调 Repository，Repository 封装 Room DAO 与 JSON 运动库
- 连胜计算是纯 Kotlin 函数，放在 `domain` 包，JUnit 可直接测试
- 运动库数据以 JSON 资源内嵌（`res/raw/exercises.json`），离线可用

### 目录结构

```
app/src/main/java/com/example/workout/
  MainActivity.kt        # 单 Activity + 底部 Tab 导航
  ui/
    theme/               # Material3 主题
    HomeScreen.kt        # 今日打卡页
    LibraryScreen.kt     # 运动库页
    CalendarScreen.kt    # 打卡日历页
    components/
      CheckinForm.kt     # 打卡表单
      Countdown.kt       # 倒计时组件
      StreakBadge.kt     # 连胜徽章
  viewmodel/
    HomeViewModel.kt
    CalendarViewModel.kt
  data/
    db/                  # Room：Entity、DAO、Database
    repository/          # CheckinRepository
    exercise/            # 运动库模型 + JSON 解析
  domain/
    Streak.kt            # 连胜计算纯函数
app/src/main/res/raw/exercises.json   # 内置运动库
app/src/test/...         # JUnit 单元测试（domain、解析）
```

## 4. 功能设计

### 4.1 今日打卡页（Home）

- 顶部：连胜徽章（"已连续打卡 X 天"）+ 今日是否已打卡状态
- 打卡表单：
  - 运动类型下拉选择（来自运动库，按分类分组）
  - 时长输入（分钟，可选；若该运动是"计时型"，提供"开始倒计时"按钮）
  - 提交按钮 → `checkIn()` 写库
- 倒计时（计时型运动）：
  - 默认时长取自运动库建议（如"原地慢跑 2 分钟"），**可自定义秒数**
  - 点击"开始"后显示倒计时，结束时提示"完成打卡？"
  - 倒计时仅在 App 前台运行，不要求后台运行
- 交互细节：同一天已打卡则显示"今日已打卡"并禁用重复提交（Room 以日期唯一索引兜底）

### 4.2 运动库页（Library）

- 按分类分组展示运动卡片：名称、一句话说明、建议时长/次数
- 点开详情：文字步骤列表 + "观看视频教程"按钮（Intent 跳系统浏览器）+ 时长/组数建议
- 数据来源：内置 `exercises.json`，离线可用

### 4.3 打卡日历页（Calendar）

- 月视图，已打卡日期高亮
- 支持左右切换月份
- 数据来自 Room 查询当月记录

### 4.4 连胜统计（Streak）

- 规则：从**今天**起往前数连续有记录的日期；若今天还没有记录，则从**昨天**起往前数
- 遇断签即停（日期不连续 = 断签）
- 例如：今天未打卡且昨天已打卡 → 当前连胜 = 昨天开始往前连续的天数
- 打卡后立即刷新连胜显示

## 5. 数据模型

### Room 实体（records 表）

```kotlin
@Entity(
    tableName = "records",
    indices = [Index(value = ["date"], unique = true)]
)
data class RecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,        // 'YYYY-MM-DD'，每天最多一条
    val exercise: String,    // 运动 id（对应 exercises.json）
    val durationMinutes: Int // 时长（分钟）
)
```

- `date` 唯一索引兜底每日一条
- DAO：`insert`（冲突返回错误）、`getRecordsForMonth(month)`、`getTodayRecord(date)`、`getAllDates()`

### 运动库数据（exercises.json）

```json
{
  "id": "jog-in-place",
  "name": "原地慢跑",
  "category": "有氧",
  "summary": "零门槛有氧，随时开始",
  "isTimed": true,
  "defaultSeconds": 120,
  "reps": null,
  "steps": ["身体直立，原地交替抬腿，膝盖抬高到髋部高度", "手臂自然摆动，保持均匀呼吸", "落地轻盈，前脚掌着地"],
  "videoUrl": "https://...",
  "note": "新手可从 1 分钟开始"
}
```

字段说明：
- `isTimed: true` 表示该运动以"计时"为主（如原地慢跑、平板支撑），打卡页显示倒计时入口
- `reps` 表示组数与次数建议（非计时运动使用，如"3 组 x 15 次"），可空
- 运动库初期内置 20 种，见附录 A
- JSON 字段用 camelCase；Kotlin 用 `kotlinx.serialization` 解析（`@Serializable`）

## 6. Repository 接口

```kotlin
interface CheckinRepository {
    suspend fun checkIn(date: String, exercise: String, durationMinutes: Int): Boolean
    fun getRecordsForMonth(month: String): Flow<List<RecordEntity>>
    fun getTodayRecord(date: String): Flow<RecordEntity?>
    fun getAllDates(): Flow<List<String>>
    suspend fun getExercises(): List<Exercise>
    suspend fun getStreak(today: LocalDate): StreakInfo
}
```

错误处理：`checkIn` 在 date 已存在时返回 `false`（或抛出业务异常），ViewModel 转为中文提示。DAO 用 `OnConflictStrategy.ABORT` 并捕获 `SQLiteConstraintException`。

## 7. 连胜计算规则（重点）

纯函数，输入已打卡日期集合 + 今天的日期，输出连胜天数：

```kotlin
object Streak {
    fun currentStreak(checkedDates: Set<LocalDate>, today: LocalDate): Int {
        val start = when {
            today in checkedDates -> today
            today.minusDays(1) in checkedDates -> today.minusDays(1)
            else -> return 0
        }
        var streak = 0
        var cursor = start
        while (cursor in checkedDates) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}
```

1. 若 `今天` 在集合中，从今天开始计数
2. 否则若 `昨天` 在集合中，从昨天开始计数
3. 否则连胜为 0
4. 从起点日期逐天向前，只要在集合中则计数 +1，遇到不在的立即停止
5. 使用 `java.time.LocalDate`（真实日历，自动处理大小月、跨月、跨年）

### 单测覆盖的边界情况

- 空记录 → 0
- 仅今天打卡 → 1
- 今天未打卡但昨天打卡 → 从昨天起算
- 连续 5 天 → 5
- 断签：中间缺一天 → 只算到断点
- 跨月连续（1-31 到 2-1）→ 正确
- 跨年连续（12-31 到 1-1）→ 正确
- 今天昨天都没有 → 0

## 8. 测试策略

- **JUnit 单元测试**（`app/src/test/`）：连胜计算全边界（第 7 节）、运动库 JSON 解析
- **Room 集成测试**（`app/src/androidTest/`）：DAO 插入重复日期报错、月查询过滤
- **手工验证**：真机安装后过一遍功能清单（见实现计划 Task 10）

## 9. 开发环境要求

- Android Studio（已装）+ JDK 21（已装，`D:\jdk-21`）
- Android SDK：android-34 + build-tools 34.0.0（已装于 `C:\Users\ttt12\AppData\Local\Android\Sdk`）
- 无需 NDK
- 首次需在 Android Studio 设置里配置 SDK 路径，或设 `ANDROID_HOME` 环境变量

## 10. 范围外（YAGNI）

- 账号 / 登录 / 云同步
- 定位 / 拍照防作弊
- 后台倒计时 / 通知栏
- 数据导出
- 自定义运动类型（运动库固定内置）
- iOS（Kotlin 原生仅 Android）

## 附录 A：内置运动清单（20 种）

按搜索整理并经用户简化的居家无器械动作，分类如下（视频链接为 B 站公开教程；标注"视频"的为已核验链接，其余在实现时从 B 站"跟练健身Online"合集等来源补选同动作教程并核验可访问性）。热身 5-10 分钟，身体微热、微微出汗即可；居家轻量运动 3-5 分钟也够。

### 热身（10 个）
1. **原地踏步** — 轻松原地踏步，活动脚踝、热身全身。1 分钟（计时型）。
2. **高抬腿走** — 膝抬到髋部高度，慢速交替。30 秒（计时型）。
3. **脚踝绕环** — 抬起一只脚踝画圈，左右各 10 圈。
4. **髋部环绕** — 叉腰画圈，左右各 5 圈。
5. **肩部环绕** — 双手搭肩，向前/向后画圈，各 10 次。
6. **手臂摆动** — 快速开合摆臂，激活上身。30 秒（计时型）。
7. **颈部环绕** — 头缓慢左右转，放松颈椎，各 5 次。
8. **体转热身** — 双脚开立手叉腰，左右转体，各 10 次。
9. **弓步扩胸** — 前弓步同时双臂打开扩胸，左右交替 10 次。
10. **猫式伸展** — 四点跪姿，塌腰抬头、弓背低头交替。10 次。

### 力量（4 个）
11. **深蹲** — 双脚与肩同宽，脚尖微外展，臀部后坐像坐椅子，下蹲至大腿平行地面，膝盖与脚尖同向。3 组 × 15 次。视频：bilibili.com/video/BV1FB4y137gi
12. **靠墙静蹲** — 背贴墙下蹲，大腿与地面平行，膝盖不超脚尖。30-60 秒（计时型）。
13. **臀桥** — 仰卧屈膝双脚踩地，臀部发力抬起使肩-髋-膝成直线，顶端停留 2 秒。3 组 × 15 次。
14. **平板支撑** — 肘撑地，头背臀成直线，核心收紧不塌腰不撅臀。30-60 秒（计时型）。

### 有氧 · 跑动（5 个）
15. **户内快走** — 原地大步快走，摆臂，保持均匀呼吸。2 分钟（计时型）。
16. **原地慢跑** — 原地交替抬腿慢跑，手臂自然摆动。2-5 分钟（计时型）。
17. **原地高抬腿** — 上身直立，大腿抬至髋部高度快速交替。30 秒（计时型）。
18. **后踢腿跑** — 脚后跟交替踢向臀部。30 秒（计时型）。
19. **开合跳** — 跳起双脚分开同时双手过头击掌，落地轻盈。30 秒（计时型）。

### 拉伸 · 放松（1 个）
20. **婴儿式** — 跪坐身体前趴，双臂前伸，放松腰背。30-60 秒（计时型）。

> 备注：以上时长/组数为新手建议值；"计时型"运动在打卡页提供可自定义的倒计时。运动前建议先做 3-5 个热身动作。
