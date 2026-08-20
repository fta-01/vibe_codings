# 运动打卡(Workout Check-in)

一款本地运行、离线可用的 Android 运动打卡应用:每日打卡、连续天数统计、内置运动库、
组合训练执行器(倒计时 + 休息节点 + 提示音),数据全部存在本机,不依赖任何云端服务。

## 特性

- ⭐ **每日打卡**:选择运动 + 时长一键记录,或「⚡ 一键打卡」最快 1 秒完成;
- ⭐ **连续天数徽章**:按本地日历自动计算连续打卡天数(streak),打卡成功弹窗鼓励;
- ⭐ **运动库**:内置 `exercises.json` 动作库,按类别浏览,详情含动作步骤 / 建议时长 / 视频教程链接;
- ⭐ **组合训练执行器**:计划依次执行 热身→主运动→拉伸→休息,计时动作自动倒计时,
  休息节点自动计时并有开始/结束提示音;完成后一条路径直达打卡;
- ⭐ **完成反馈**:可选 11 种合成完成音效 + 震动开关;已打卡当天不再出现任何打卡入口,防止重复;
- 🗓 **日历视图**:按月展示打卡记录,一眼看出自己的运动足迹;
- 🛠 **本地数据**:Room(SQLite)存储打卡记录,无账号、无网络依赖。

## 技术栈

- Kotlin 2.0 · Jetpack Compose(Material 3)· Room 2.6 · kotlinx-serialization · KSP
- minSdk 26 / targetSdk 34,Gradle 8.5.2

## 构建运行

需要:JDK 17+、Android SDK(build-tools / platform 34)。

```bash
# 进入 Monorepo 子目录
cd projects/02-workout-checkin

# 调试包(可用 Android Studio 直接打开或命令行)
./gradlew.bat assembleDebug

# Release 包:未配置签名时产出未签名 APK
./gradlew.bat assembleRelease
```

> **Release 签名**:`workout-release.keystore` 仅保存在本机(不入库),签名凭据写在
> 被 git 忽略的 `local.properties`:
>
> ```
> workout.storeFile=workout-release.keystore
> workout.storePassword=xxx
> workout.keyAlias=workout
> workout.keyPassword=xxx
> ```
>
> **务必备份 keystore**:签名与包身份绑定,丢失后同一应用将无法再发更新版本。

## 目录结构

```
02-workout-checkin/
├── app/
│   ├── src/main/java/com/example/workout/
│   │   ├── MainActivity.kt        # 入口 + 底部三 Tab(打卡/运动库/日历)
│   │   ├── ui/                    # Home / Library / Calendar + 组件
│   │   ├── ui/components/         # 打卡表单、倒计时、组合训练执行器……
│   │   ├── data/                  # Room 数据库、动作库解析、仓库
│   │   ├── domain/Streak.kt       # 连续天数算法
│   │   ├── audio/                 # 完成音效 / 休息提示音(合成音)
│   │   └── viewmodel/             # Home / Calendar ViewModel
│   └── src/main/res/raw/exercises.json  # 内置动作库(JSON)
├── gradle/wrapper/
├── settings.gradle.kts            # rootProject.name = WorkoutCheckin
└── build.gradle.kts
```

## 开发记录(dev log)

这是 vibe coding(对话式 AI 辅助编程)迭代出来的应用,下面是真实迭代轨迹:

- **第 1 轮「骨架先跑通」**:从一句"我要一个运动打卡 App"开始,搭起 打卡 / 运动库 / 日历
  三 Tab + Room 存储 + 连续天数算法的骨架,`assembleDebug` + 单测全部通过。
- **第 2 轮「组合训练」**:加组合训练执行器——一组动作按顺序执行、计时动作自动倒计时、
  完成后能直接打卡;发现打卡后面板不应隐藏,改为"功能继续用、只换打卡按钮状态"。
- **第 3 轮「休息与提示音」**:给组合加休息节点(自动倒计时、开始/结束提示音、最后 3 秒滴答)。
- **第 4 轮「倒计时体验」**:修 auto-start 倒计时、可滚动的执行器界面、修复"接下来"预览被挤压。
- **第 5 轮「可配置完成音」**:生成 6 种完成音 + 震动开关 + 手动开始倒计时。
- **第 6 轮「音效引擎重写」**:系统铃声在部分机型不可靠 → 改用 AudioTrack MODE_STREAM
  播放真实系统通知/铃声/闹钟音。
- **第 7 轮「纯合成音库」**:系统铃声仍不稳 → 彻底换成 11 种自研合成音(水滴、旋律、音阶、扫弦、鼓点……),
  零资源依赖、任何机型都稳。
- **第 8 轮「双打卡路径」**:组合完成直接打卡 + 快速一键打卡,统一成功弹窗(音效 + 震动)。
- **第 9 轮「打卡状态化」**:当天已打卡后不再出现任何打卡提示/按钮,避免重复打卡;
  新增无任何限制的「⚡ 一键打卡」;11/11 单测通过。
- **第 10 轮「发布签名」**:自签 keystore 接入 release 构建,`assembleRelease` 产出签名 APK。

### 踩过的坑

- 系统来电铃声/通知音在多个机型/API 上播放不稳定,换文件资源或合成音才是可靠解。
- 打卡后隐藏表单会让"训练+计时"功能不可用——正确的产品做法是保留功能、只改状态。
- 已打卡状态的完成弹窗还会给出"立即打卡"入口,导致用户误点重复打卡 → 做成状态感知。

### 学到的东西

- 本地 App 的核心是"状态一致":打卡、表单、弹窗、音效由同一个状态驱动,才不会自相矛盾;
- 音效/铃声这类系统能力,能用自产资源就不用系统资源,可预期性优先。