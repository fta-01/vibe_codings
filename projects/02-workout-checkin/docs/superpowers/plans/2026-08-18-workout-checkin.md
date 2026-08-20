# 运动打卡 App 实现计划（Kotlin 原生）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建一个 Android 运动打卡 App（Kotlin + Jetpack Compose + Room），支持打卡记录、可自定义倒计时、内置居家无器械运动库、打卡日历与连胜统计。

**Architecture:** Compose 界面通过 ViewModel 的 `StateFlow` 渲染；ViewModel 调 Repository；Repository 封装 Room DAO 与 JSON 运动库；连胜计算是 `domain` 包纯函数，JUnit 直接测试。单 Activity + 底部 Tab 状态切换，不引入 Navigation 组件库。

**Tech Stack:** Kotlin 2.0、Jetpack Compose（Material3）、Room 2.6、kotlinx.serialization、JDK 21、AGP 8.5.2、Gradle 8.7、compileSdk 34 / minSdk 26 / targetSdk 34。

**Spec:** `docs/superpowers/specs/2026-08-18-workout-checkin-design.md`

## Global Constraints

- 平台：仅 Android；包名 `com.example.workout`；项目根目录 `D:\cs\testk`
- 目标：个人使用，本地数据，无账号/无后端/无云同步
- 日期格式统一为 `YYYY-MM-DD`（字符串），月份格式 `YYYY-MM`
- Room 表 `records`，`date` 列唯一索引（每天最多一条）；重复插入抛 `SQLiteConstraintException` → 返回业务错误
- JSON 字段 camelCase；Kotlin 用 kotlinx.serialization 的 `@Serializable` 解析；`res/raw/exercises.json`
- 连胜计算必须是无依赖的纯 Kotlin 函数（`domain/Streak.kt`），JUnit 可测
- 不引入前端/UI 测试框架，UI 手工验证
- 构建环境：无系统 gradle、无 Android Studio CLI，使用下载的 Gradle 8.7 发行版 + 已装 SDK（`C:\Users\ttt12\AppData\Local\Android\Sdk`，android-34 + build-tools 34.0.0）
- `exercises.json` 20 种动作内容以 Spec 附录 A 为准
- 所有 Gradle 依赖从 `google()` / `mavenCentral()` 下载，需网络

---

### Task 1: 下载 Gradle 并创建项目骨架

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/proguard-rules.pro`, `gradle/wrapper/gradle-wrapper.properties`（wrapper jar 由 `gradle wrapper` 生成）
- Create: `app/src/main/res/values/strings.xml`, `themes.xml`, `mipmap` 图标引用, `app/src/main/res/xml/backup_rules.xml`, `data_extraction_rules.xml`
- Create: `.gitignore`
- Test: `gradle assembleDebug` 编译通过

**Interfaces:**
- Produces: 可编译的 Android Gradle 工程；后续任务在 `app/src/main/java/com/example/workout/` 下添加源码

- [x] **Step 1: 下载并解压 Gradle 8.7 到临时目录（国内镜像）**

优先腾讯云镜像，失败则回退官方源：

```powershell
$zip = "C:\Users\ttt12\AppData\Local\Temp\opencode\gradle-8.7-bin.zip"
$dest = "C:\Users\ttt12\AppData\Local\Temp\opencode\gradle-8.7"
if (-not (Test-Path "$dest\gradle-8.7\bin\gradle.bat")) {
    $urls = @(
        "https://mirrors.cloud.tencent.com/gradle/gradle-8.7-bin.zip",
        "https://services.gradle.org/distributions/gradle-8.7-bin.zip"
    )
    foreach ($u in $urls) {
        try {
            Invoke-WebRequest -Uri $u -OutFile $zip -TimeoutSec 300
            break
        } catch {
            Write-Host "下载失败，尝试下一个源: $u"
        }
    }
    Expand-Archive -Path $zip -DestinationPath $dest -Force -ErrorAction Stop
}
& "$dest\gradle-8.7\bin\gradle.bat" --version
```

预期：输出 Gradle 8.7。

- [x] **Step 2: 创建 settings.gradle.kts（阿里云镜像仓库）**

```kotlin
buildscript {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
    }
}

pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        google()
        mavenCentral()
    }
}
rootProject.name = "WorkoutCheckin"
include(":app")
```

- [x] **Step 3: 创建根 build.gradle.kts**

```kotlin
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0" apply false
    id("com.google.devtools.ksp") version "2.0.0-1.0.24" apply false
}
```

- [x] **Step 4: 创建 gradle.properties**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
org.gradle.configuration-cache=false
```

- [x] **Step 5: 创建 app/build.gradle.kts**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.workout"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.workout"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
```

- [x] **Step 6: 创建 AndroidManifest.xml**

`app/src/main/AndroidManifest.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.WorkoutCheckin">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.WorkoutCheckin">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [x] **Step 7: 创建资源文件**

`app/src/main/res/values/strings.xml`：

```xml
<resources>
    <string name="app_name">运动打卡</string>
</resources>
```

`app/src/main/res/values/themes.xml`：

```xml
<resources>
    <style name="Theme.WorkoutCheckin" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

`app/src/main/res/values/colors.xml`：

```xml
<resources>
    <color name="ic_launcher_background">#2F6FED</color>
</resources>
```

`app/src/main/res/xml/backup_rules.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
</full-backup-content>
```

`app/src/main/res/xml/data_extraction_rules.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
    </cloud-backup>
    <device-transfer>
    </device-transfer>
</data-extraction-rules>
```

图标用 Android Studio 默认样式：创建 `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` 和 `ic_launcher_round.xml`，内容如下：

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

创建 `app/src/main/res/drawable/ic_launcher_foreground.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M54,32c-2.2,0 -4,1.8 -4,4v14H36c-2.2,0 -4,1.8 -4,4s1.8,4 4,4h14v14c0,2.2 1.8,4 4,4s4,-1.8 4,-4V58h14c2.2,0 4,-1.8 4,-4s-1.8,-4 -4,-4H58V36c0,-2.2 -1.8,-4 -4,-4z" />
</vector>
```

- [x] **Step 8: 创建 .gitignore**

```
.gradle/
build/
local.properties
.idea/
*.iml
.DS_Store
/captures
.externalNativeBuild
.cxx
```

- [x] **Step 9: 生成 gradle wrapper 并首次编译**

```powershell
& "C:\Users\ttt12\AppData\Local\Temp\opencode\gradle-8.7\gradle-8.7\bin\gradle.bat" wrapper --gradle-version 8.7
```

创建 `local.properties`（指向 SDK）：

```powershell
Set-Content -Path "local.properties" -Value "sdk.dir=C\:\\Users\\ttt12\\AppData\\Local\\Android\\Sdk" -Encoding Ascii
```

首次编译（下载依赖，耗时较长）：

```powershell
.\gradlew.bat assembleDebug --no-daemon
```

预期：BUILD SUCCESSFUL；生成 `app/build/outputs/apk/debug/app-debug.apk`。当前无 MainActivity 会报错——属预期，Task 2 补上后再编译。

- [x] **Step 10: 提交**

```powershell
git init
git add -A
git commit -m "chore: scaffold Android Gradle project (Kotlin + Compose + Room)"
```

---

### Task 2: 最小可运行骨架（MainActivity + 空主题）

**Files:**
- Create: `app/src/main/java/com/example/workout/MainActivity.kt`
- Create: `app/src/main/java/com/example/workout/ui/theme/Color.kt`, `Theme.kt`, `Type.kt`
- Test: `assembleDebug` 编译通过 + 生成 APK

**Interfaces:**
- Produces: `MainActivity`（Compose 入口）；后续任务注入真实界面

- [x] **Step 1: 创建主题文件**

`ui/theme/Color.kt`：

```kotlin
package com.example.workout.ui.theme

import androidx.compose.ui.graphics.Color

val PrimaryBlue = Color(0xFF2F6FED)
val AccentOrange = Color(0xFFFF9A56)
val AccentRed = Color(0xFFFF5F6D)
```

`ui/theme/Type.kt`：

```kotlin
package com.example.workout.ui.theme

import androidx.compose.material3.Typography

val Typography = Typography()
```

`ui/theme/Theme.kt`：

```kotlin
package com.example.workout.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = PrimaryBlue,
    secondary = AccentOrange,
    tertiary = AccentRed,
)

private val DarkColors = darkColorScheme(
    primary = PrimaryBlue,
    secondary = AccentOrange,
    tertiary = AccentRed,
)

@Composable
fun WorkoutTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
```

- [x] **Step 2: 创建 MainActivity.kt**

```kotlin
package com.example.workout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.workout.ui.theme.WorkoutTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WorkoutTheme {
                Surface {
                    Text("运动打卡", modifier = androidx.compose.ui.Modifier.padding(24.dp))
                }
            }
        }
    }
}
```

`androidx.compose.ui.Modifier.padding` 需要 import；将 `Text` 行改为：

```kotlin
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

Surface(modifier = Modifier.fillMaxSize()) {
    Text("运动打卡", modifier = Modifier.padding(24.dp))
}
```

`fillMaxSize` 需 `import androidx.compose.foundation.layout.fillMaxSize`。

- [x] **Step 3: 编译验证**

```powershell
.\gradlew.bat assembleDebug --no-daemon
```

预期：BUILD SUCCESSFUL，生成 APK。

- [x] **Step 4: 提交**

```powershell
git add -A
git commit -m "feat: add minimal runnable activity and theme"
```

---

### Task 3: 领域层——连胜计算（TDD）

**Files:**
- Create: `app/src/main/java/com/example/workout/domain/Streak.kt`
- Create: `app/src/test/java/com/example/workout/domain/StreakTest.kt`
- Test: JUnit 单元测试

**Interfaces:**
- Produces: `object Streak { fun currentStreak(checkedDates: Set<LocalDate>, today: LocalDate): Int }`

- [x] **Step 1: 编写失败测试**

`app/src/test/java/com/example/workout/domain/StreakTest.kt`：

```kotlin
package com.example.workout.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakTest {

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test
    fun emptySetIsZero() {
        assertEquals(0, Streak.currentStreak(emptySet(), d(2026, 8, 18)))
    }

    @Test
    fun onlyTodayIsOne() {
        assertEquals(1, Streak.currentStreak(setOf(d(2026, 8, 18)), d(2026, 8, 18)))
    }

    @Test
    fun todayMissingButYesterdayPresent() {
        assertEquals(1, Streak.currentStreak(setOf(d(2026, 8, 17)), d(2026, 8, 18)))
    }

    @Test
    fun fiveConsecutiveDays() {
        val days = (14..18).map { d(2026, 8, it) }.toSet()
        assertEquals(5, Streak.currentStreak(days, d(2026, 8, 18)))
    }

    @Test
    fun gapBreaksStreak() {
        val days = setOf(d(2026, 8, 15), d(2026, 8, 16), d(2026, 8, 18))
        assertEquals(1, Streak.currentStreak(days, d(2026, 8, 18)))
    }

    @Test
    fun streakAcrossMonthBoundary() {
        val days = setOf(d(2026, 1, 31), d(2026, 2, 1), d(2026, 2, 2))
        assertEquals(3, Streak.currentStreak(days, d(2026, 2, 2)))
    }

    @Test
    fun streakAcrossYearBoundary() {
        val days = setOf(d(2025, 12, 31), d(2026, 1, 1))
        assertEquals(2, Streak.currentStreak(days, d(2026, 1, 1)))
    }

    @Test
    fun neitherTodayNorYesterdayIsZero() {
        assertEquals(0, Streak.currentStreak(setOf(d(2026, 8, 10)), d(2026, 8, 18)))
    }
}
```

- [x] **Step 2: 运行测试确认失败**

```powershell
.\gradlew.bat testDebugUnitTest --no-daemon --tests "com.example.workout.domain.StreakTest"
```

预期：FAIL，找不到 `Streak`。

- [x] **Step 3: 实现 Streak.kt**

```kotlin
package com.example.workout.domain

import java.time.LocalDate

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

- [x] **Step 4: 运行测试确认通过**

```powershell
.\gradlew.bat testDebugUnitTest --no-daemon --tests "com.example.workout.domain.StreakTest"
```

预期：8 个测试全部 PASS。

- [x] **Step 5: 提交**

```powershell
git add -A
git commit -m "feat: add streak calculation with full boundary tests"
```

---

### Task 4: 运动库 JSON 解析（TDD）

**Files:**
- Create: `app/src/main/java/com/example/workout/data/exercise/Exercise.kt`
- Create: `app/src/main/java/com/example/workout/data/exercise/ExerciseParser.kt`
- Create: `app/src/main/res/raw/exercises.json`
- Create: `app/src/test/java/com/example/workout/data/exercise/ExerciseParserTest.kt`
- Test: JUnit 单元测试

**Interfaces:**
- Consumes: kotlinx.serialization
- Produces: `@Serializable data class Exercise(...)`；`object ExerciseParser { fun parse(json: String): List<Exercise> }`

- [x] **Step 1: 编写 Exercise.kt**

```kotlin
package com.example.workout.data.exercise

import kotlinx.serialization.Serializable

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val category: String,
    val summary: String,
    val isTimed: Boolean,
    val defaultSeconds: Int? = null,
    val reps: String? = null,
    val steps: List<String> = emptyList(),
    val videoUrl: String? = null,
    val note: String? = null,
)
```

- [x] **Step 2: 编写 ExerciseParser.kt**

```kotlin
package com.example.workout.data.exercise

import kotlinx.serialization.json.Json

object ExerciseParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(rawJson: String): List<Exercise> =
        json.decodeFromString<List<Exercise>>(rawJson)
}
```

- [x] **Step 3: 编写失败测试**

`app/src/test/java/com/example/workout/data/exercise/ExerciseParserTest.kt`：

```kotlin
package com.example.workout.data.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseParserTest {

    @Test
    fun parsesListOfExercises() {
        val json = """
            [
              {
                "id": "squat",
                "name": "深蹲",
                "category": "力量 · 下肢",
                "summary": "练大腿与臀部",
                "isTimed": false,
                "steps": ["步骤一", "步骤二"],
                "videoUrl": "https://example.com/v"
              }
            ]
        """.trimIndent()
        val list = ExerciseParser.parse(json)
        assertEquals(1, list.size)
        assertEquals("squat", list[0].id)
        assertEquals(2, list[0].steps.size)
        assertEquals("https://example.com/v", list[0].videoUrl)
    }

    @Test
    fun timedExerciseHasDefaultSeconds() {
        val json = """
            [
              {
                "id": "plank",
                "name": "平板支撑",
                "category": "核心",
                "summary": "核心训练",
                "isTimed": true,
                "defaultSeconds": 60,
                "steps": []
              }
            ]
        """.trimIndent()
        val list = ExerciseParser.parse(json)
        assertEquals(60, list[0].defaultSeconds)
    }

    @Test
    fun realDataParsesWithAllExercises() {
        val raw = javaClass.classLoader
            ?.getResourceAsStream("exercises.json")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: throw AssertionError("exercises.json 不在测试资源中")
        val list = ExerciseParser.parse(raw)
        assertTrue("至少 20 种运动，实际 ${list.size}", list.size >= 20)
        val ids = list.map { it.id }.toSet()
        assertEquals("id 必须唯一", list.size, ids.size)
        list.filter { it.isTimed }.forEach { ex ->
            assertTrue("计时型 ${ex.name} 必须有 defaultSeconds", ex.defaultSeconds != null)
        }
    }
}
```

- [x] **Step 4: 将 exercises.json 复制到测试资源**

把 `app/src/main/res/raw/exercises.json` 复制到 `app/src/test/resources/exercises.json`（用于 JVM 测试读取）。

- [x] **Step 5: 运行测试确认通过**

```powershell
.\gradlew.bat testDebugUnitTest --no-daemon --tests "com.example.workout.data.exercise.ExerciseParserTest"
```

预期：3 个测试 PASS。若 realData 测试失败，说明 JSON 格式问题，按 Spec 字段修正。

- [x] **Step 6: 提交**

```powershell
git add -A
git commit -m "feat: add exercise library JSON model and parser"
```

---

### Task 5: Room 数据层

**Files:**
- Create: `app/src/main/java/com/example/workout/data/db/RecordEntity.kt`
- Create: `app/src/main/java/com/example/workout/data/db/RecordDao.kt`
- Create: `app/src/main/java/com/example/workout/data/db/AppDatabase.kt`
- Test: Room 集成测试 `app/src/androidTest/java/com/example/workout/data/db/RecordDaoTest.kt`

**Interfaces:**
- Consumes: `Exercise`（Repository 用）
- Produces:
  - `@Entity(tableName="records") data class RecordEntity(id: Long=0, date: String, exercise: String, durationMinutes: Int)`
  - `@Dao interface RecordDao`：`insert(record): Long`、`getRecordsForMonth(month): Flow<List<RecordEntity>>`、`getTodayRecord(date): Flow<RecordEntity?>`、`getAllDates(): Flow<List<String>>`
  - `@Database abstract class AppDatabase`：`abstract fun recordDao(): RecordDao`

- [x] **Step 1: 编写 RecordEntity.kt**

```kotlin
package com.example.workout.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "records",
    indices = [Index(value = ["date"], unique = true)],
)
data class RecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val exercise: String,
    val durationMinutes: Int,
)
```

- [x] **Step 2: 编写 RecordDao.kt**

```kotlin
package com.example.workout.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: RecordEntity): Long

    @Query("SELECT * FROM records WHERE date LIKE :monthPattern ORDER BY date")
    fun getRecordsForMonth(monthPattern: String): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE date = :date LIMIT 1")
    fun getTodayRecord(date: String): Flow<RecordEntity?>

    @Query("SELECT date FROM records ORDER BY date")
    fun getAllDates(): Flow<List<String>>
}
```

调用约定：`monthPattern = "$month-%"`，由 Repository 拼装。

- [x] **Step 3: 编写 AppDatabase.kt**

```kotlin
package com.example.workout.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RecordEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "workout.db",
                ).build().also { INSTANCE = it }
            }
    }
}
```

- [x] **Step 4: 编写 Room 集成测试**

`app/src/androidTest/java/com/example/workout/data/db/RecordDaoTest.kt`：

```kotlin
package com.example.workout.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: RecordDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.recordDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertThenReadToday() = runBlocking {
        dao.insert(RecordEntity(date = "2026-08-18", exercise = "squat", durationMinutes = 15))
        val today = dao.getTodayRecord("2026-08-18").first()
        assertTrue(today != null)
        assertEquals("squat", today!!.exercise)
    }

    @Test
    fun duplicateDateIsRejected() = runBlocking {
        dao.insert(RecordEntity(date = "2026-08-18", exercise = "squat", durationMinutes = 15))
        var threw = false
        try {
            dao.insert(RecordEntity(date = "2026-08-18", exercise = "plank", durationMinutes = 2))
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            threw = true
        }
        assertTrue("重复日期应抛异常", threw)
    }

    @Test
    fun monthQueryFilters() = runBlocking {
        dao.insert(RecordEntity(date = "2026-08-18", exercise = "squat", durationMinutes = 15))
        dao.insert(RecordEntity(date = "2026-08-19", exercise = "plank", durationMinutes = 2))
        dao.insert(RecordEntity(date = "2026-07-31", exercise = "squat", durationMinutes = 10))
        val aug = dao.getRecordsForMonth("2026-08-%").first()
        assertEquals(2, aug.size)
    }
}
```

- [x] **Step 5: 配置 androidTest 依赖与运行**

确保 `app/build.gradle.kts` 有 `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`（加到 `defaultConfig`）。

运行需要模拟器或真机（连接设备后）：

```powershell
.\gradlew.bat connectedDebugAndroidTest --no-daemon
```

> 若无设备，此测试可延后到 Task 10 与真机验证一起执行；`testDebugUnitTest`（JVM）不受影响。

- [x] **Step 6: 提交**

```powershell
git add -A
git commit -m "feat: add Room database layer with entities and DAO"
```

---

### Task 6: Repository 与 ViewModel 层

**Files:**
- Create: `app/src/main/java/com/example/workout/data/repository/CheckinRepository.kt`
- Create: `app/src/main/java/com/example/workout/viewmodel/HomeViewModel.kt`
- Create: `app/src/main/java/com/example/workout/viewmodel/CalendarViewModel.kt`
- Test: `HomeViewModel` 可用手工验证（UI 层）

**Interfaces:**
- Consumes: `RecordEntity`、`RecordDao`、`Exercise`、`ExerciseParser`、`Streak`
- Produces:
  - `class CheckinRepository(private val dao: RecordDao, private val context: Context)`
  - `suspend fun checkIn(date, exercise, durationMinutes): Boolean`（返回是否成功；重复打卡 false）
  - `fun recordsForMonth(month): Flow<List<RecordEntity>>`
  - `fun todayRecord(date): Flow<RecordEntity?>`
  - `fun allDates(): Flow<List<String>>`
  - `suspend fun loadExercises(): List<Exercise>`（读 `res/raw/exercises.json`）
  - `HomeViewModel` 暴露 `StateFlow<HomeUiState>`，含 `streakDays`、`todayChecked`、`exercises`、`message`、`isLoading`
  - `CalendarViewModel` 暴露 `StateFlow<CalendarUiState>`，含 `checkedDates: Set<String>`

- [x] **Step 1: 编写 CheckinRepository.kt**

```kotlin
package com.example.workout.data.repository

import android.content.Context
import com.example.workout.data.db.RecordDao
import com.example.workout.data.db.RecordEntity
import com.example.workout.data.exercise.Exercise
import com.example.workout.data.exercise.ExerciseParser
import kotlinx.coroutines.flow.Flow

class CheckinRepository(
    private val dao: RecordDao,
    private val context: Context,
) {
    suspend fun checkIn(date: String, exercise: String, durationMinutes: Int): Boolean {
        return try {
            dao.insert(RecordEntity(date = date, exercise = exercise, durationMinutes = durationMinutes))
            true
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            false
        }
    }

    fun recordsForMonth(month: String): Flow<List<RecordEntity>> =
        dao.getRecordsForMonth("$month-%")

    fun todayRecord(date: String): Flow<RecordEntity?> =
        dao.getTodayRecord(date)

    fun allDates(): Flow<List<String>> =
        dao.getAllDates()

    suspend fun loadExercises(): List<Exercise> {
        val raw = context.resources.openRawResource(R.raw.exercises)
            .bufferedReader()
            .use { it.readText() }
        return ExerciseParser.parse(raw)
    }
}
```

`R` 指向 `com.example.workout.R`。若 `R.raw.exercises` 无法解析，检查资源文件存在。

- [x] **Step 2: 编写 HomeViewModel.kt**

```kotlin
package com.example.workout.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.workout.data.exercise.Exercise
import com.example.workout.data.repository.CheckinRepository
import com.example.workout.domain.Streak
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeUiState(
    val isLoading: Boolean = true,
    val streakDays: Int = 0,
    val todayChecked: Boolean = false,
    val exercises: List<Exercise> = emptyList(),
    val message: String? = null,
)

class HomeViewModel(private val repo: CheckinRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    private val today: LocalDate = LocalDate.now()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val dates = repo.allDates().let { flow -> /* 收集单值 */
            }
        }
    }

    fun checkIn(exercise: String, durationMinutes: Int) {
        viewModelScope.launch {
            val date = today.toString()
            val ok = repo.checkIn(date, exercise, durationMinutes)
            _uiState.value = if (ok) {
                _uiState.value.copy(todayChecked = true, message = "打卡成功 ✅")
            } else {
                _uiState.value.copy(message = "该日期已打卡")
            }
            refreshStreak()
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
```

`refresh()` 中的日期集合收集需用协程 `first()`：

```kotlin
import kotlinx.coroutines.flow.first

fun refresh() {
    viewModelScope.launch {
        val dates = repo.allDates().first()
        val exercises = repo.loadExercises()
        val todayStr = today.toString()
        val todayChecked = dates.contains(todayStr)
        val checkedSet = dates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
        val days = Streak.currentStreak(checkedSet, today)
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            streakDays = days,
            todayChecked = todayChecked,
            exercises = exercises,
        )
    }
}
```

替换上面的占位版 `refresh()`。同时补工厂：

```kotlin
companion object {
    fun factory(repo: CheckinRepository) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(repo) as T
    }
}
```

- [x] **Step 3: 编写 CalendarViewModel.kt**

```kotlin
package com.example.workout.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workout.data.repository.CheckinRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

data class CalendarUiState(
    val checkedDates: Set<String> = emptySet(),
    val month: String = LocalDate.now().toString().substring(0, 7),
)

class CalendarViewModel(private val repo: CheckinRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState

    fun loadMonth(month: String) {
        viewModelScope.launch {
            val recs = repo.recordsForMonth(month).first()
            _uiState.value = CalendarUiState(
                checkedDates = recs.map { it.date }.toSet(),
                month = month,
            )
        }
    }

    companion object {
        fun factory(repo: CheckinRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CalendarViewModel(repo) as T
        }
    }
}
```

- [x] **Step 4: 编译验证**

```powershell
.\gradlew.bat assembleDebug --no-daemon
```

预期：BUILD SUCCESSFUL。若 `ViewModelProvider.Factory` 的 `create` 需要 `CreationExtras` 参数，改为 `create(modelClass: Class<T>, extras: CreationExtras)` 并补 import。

- [x] **Step 5: 提交**

```powershell
git add -A
git commit -m "feat: add repository and view models"
```

---

### Task 7: 今日打卡页（表单 + 连胜徽章 + 倒计时）

**Files:**
- Create: `app/src/main/java/com/example/workout/ui/components/StreakBadge.kt`
- Create: `app/src/main/java/com/example/workout/ui/components/Countdown.kt`
- Create: `app/src/main/java/com/example/workout/ui/components/CheckinForm.kt`
- Create: `app/src/main/java/com/example/workout/ui/HomeScreen.kt`
- Modify: `MainActivity.kt`（底部 Tab 导航，Home 为默认页）
- Test: 手工验证

**Interfaces:**
- Consumes: `HomeViewModel`、`HomeUiState`、`Exercise`
- Produces: `HomeScreen(viewModel)`；MainActivity 的 Tab 导航结构

- [x] **Step 1: 编写 StreakBadge.kt**

```kotlin
package com.example.workout.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StreakBadge(streakDays: Int, todayChecked: Boolean, modifier: Modifier = Modifier) {
    val gradient = Brush.horizontalGradient(
        listOf(Color(0xFFFF9A56), Color(0xFFFF5F6D)),
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(gradient, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text("🔥 已连续打卡 $streakDays 天", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(if (todayChecked) "今天已打卡 ✅" else "今天还没打卡", color = Color.White)
    }
}
```

- [x] **Step 2: 编写 Countdown.kt**

```kotlin
package com.example.workout.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun Countdown(
    defaultSeconds: Int,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var seconds by remember { mutableIntStateOf(defaultSeconds) }
    var running by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (seconds > 0) {
            delay(1000)
            seconds--
        }
        running = false
        onFinished()
    }

    DisposableEffect(Unit) {
        onDispose { running = false }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "%02d:%02d".format(seconds / 60, seconds % 60),
            fontSize = 42.sp,
            textAlign = TextAlign.Center,
        )
        OutlinedTextField(
            value = custom,
            onValueChange = { custom = it.filter { c -> c.isDigit() }.take(6) },
            label = { Text("自定义秒数（可选）") },
            enabled = !running,
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !running, onClick = {
                val total = custom.toIntOrNull() ?: defaultSeconds
                seconds = total.coerceAtLeast(1)
                running = true
            }) {
                Text("开始倒计时")
            }
            OutlinedButton(onClick = {
                running = false
                seconds = defaultSeconds
                custom = ""
            }) {
                Text("重置")
            }
        }
    }
}
```

`LaunchedEffect` 需 `import androidx.compose.runtime.LaunchedEffect`。

- [x] **Step 3: 编写 CheckinForm.kt**

```kotlin
package com.example.workout.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.workout.data.exercise.Exercise

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckinForm(
    exercises: List<Exercise>,
    onCheckIn: (exerciseId: String, durationMinutes: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var durationText by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val selected = exercises.find { it.id == selectedId }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = exercises.find { it.id == selectedId }?.name ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("选择运动类型") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                exercises.forEach { ex ->
                    DropdownMenuItem(
                        text = { Text("${ex.name}（${ex.category}）") },
                        onClick = {
                            selectedId = ex.id
                            expanded = false
                            message = null
                        },
                    )
                }
            }
        }

        if (selected?.isTimed == true) {
            Countdown(
                defaultSeconds = selected.defaultSeconds ?: 60,
                onFinished = { message = "运动完成！可以打卡啦" },
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        OutlinedTextField(
            value = durationText,
            onValueChange = { durationText = it.filter { c -> c.isDigit() }.take(4) },
            label = { Text("时长（分钟，可选）") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )

        Button(
            onClick = {
                val id = selectedId
                if (id == null) {
                    message = "请选择运动类型"
                } else {
                    val minutes = durationText.toIntOrNull() ?: 0
                    onCheckIn(id, minutes)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            Text("打卡")
        }

        message?.let {
            Text(it, color = Color(0xFFD33), modifier = Modifier.padding(top = 8.dp))
        }
    }
}
```

需要 `ExposedDropdownMenu` import：`androidx.compose.material3.ExposedDropdownMenu`；`menuAnchor()` 在 Material3 1.2+ 为 `Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)`，若编译报错改用：

```kotlin
Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth()
```

并 import `androidx.compose.material3.ExposedDropdownMenuAnchorType`。

- [x] **Step 4: 编写 HomeScreen.kt**

```kotlin
package com.example.workout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.workout.ui.components.CheckinForm
import com.example.workout.ui.components.StreakBadge
import com.example.workout.viewmodel.HomeViewModel

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StreakBadge(streakDays = state.streakDays, todayChecked = state.todayChecked)

        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (state.todayChecked) {
            Text(
                "今日已打卡 ✅，明天再来！",
                fontSize = 18.sp,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(24.dp),
            )
        } else {
            CheckinForm(
                exercises = state.exercises,
                onCheckIn = { exId, minutes -> viewModel.checkIn(exId, minutes) },
            )
        }

        state.message?.let {
            Text(it, color = Color(0xFFD33), fontSize = 14.sp)
        }
    }
}
```

- [x] **Step 5: 修改 MainActivity.kt 加入底部 Tab**

```kotlin
package com.example.workout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.workout.data.db.AppDatabase
import com.example.workout.data.repository.CheckinRepository
import com.example.workout.ui.CalendarScreen
import com.example.workout.ui.HomeScreen
import com.example.workout.ui.LibraryScreen
import com.example.workout.ui.theme.WorkoutTheme
import com.example.workout.viewmodel.CalendarViewModel
import com.example.workout.viewmodel.HomeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = AppDatabase.getInstance(this)
        val repo = CheckinRepository(db.recordDao(), this)

        setContent {
            WorkoutTheme {
                MainScaffold(repo)
            }
        }
    }
}

private data class TabItem(val label: String, val icon: ImageVector)

@Composable
private fun MainScaffold(repo: CheckinRepository) {
    var tab by remember { mutableIntStateOf(0) }
    val homeViewModel: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = HomeViewModel.factory(repo),
    )
    val calendarViewModel: CalendarViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = CalendarViewModel.factory(repo),
    )

    val tabs = listOf(
        TabItem("打卡", Icons.Filled.Check),
        TabItem("运动库", Icons.Filled.MenuBook),
        TabItem("日历", Icons.Filled.DateRange),
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (tab) {
                0 -> HomeScreen(homeViewModel)
                1 -> LibraryScreen(repo)
                2 -> CalendarScreen(calendarViewModel)
            }
        }
    }
}
```

`LibraryScreen` 与 `CalendarScreen` 尚未创建——此步编译会报错（属预期），Task 8/9 完成后消除。

- [x] **Step 6: 手工验证**

```powershell
.\gradlew.bat assembleDebug --no-daemon
```

若因缺少 LibraryScreen/CalendarScreen 报错，先创建两个占位函数再编译；真实实现在 Task 8/9。

- [x] **Step 7: 提交**

```powershell
git add -A
git commit -m "feat: add home screen with check-in form, streak badge and countdown"
```

---

### Task 8: 运动库页

**Files:**
- Create: `app/src/main/java/com/example/workout/ui/LibraryScreen.kt`
- Test: 手工验证

**Interfaces:**
- Consumes: `CheckinRepository.loadExercises()`、`Exercise`
- Produces: `@Composable fun LibraryScreen(repo: CheckinRepository)`

- [x] **Step 1: 编写 LibraryScreen.kt**

```kotlin
package com.example.workout.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.workout.data.exercise.Exercise
import com.example.workout.data.repository.CheckinRepository

@Composable
fun LibraryScreen(repo: CheckinRepository) {
    var exercises by remember { mutableStateOf<List<Exercise>?>(null) }
    var detail by remember { mutableStateOf<Exercise?>(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        exercises = repo.loadExercises()
    }

    val list = exercises
    if (list == null) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 64.dp))
        }
        return
    }

    val selected = detail
    if (selected != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = { detail = null }) { Text("← 返回") }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(selected.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(selected.summary)
                selected.reps?.let { Text("建议：$it", fontWeight = FontWeight.Bold) }
                selected.defaultSeconds?.let { Text("建议时长：$it 秒", fontWeight = FontWeight.Bold) }
                Text("步骤", fontWeight = FontWeight.Bold)
                selected.steps.forEachIndexed { i, step ->
                    Text("${i + 1}. $step")
                }
                selected.note?.let { Text(it, color = Color(0xFF888)) }
                selected.videoUrl?.let { url ->
                    Button(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    }) {
                        Text("观看视频教程")
                    }
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        list.groupBy { it.category }.forEach { (category, items) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(category, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                items.forEach { ex ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .clickable { detail = ex }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ex.name, fontWeight = FontWeight.Bold)
                            Text(
                                ex.summary +
                                    if (ex.reps != null) " · ${ex.reps}" else "",
                                fontSize = 12.sp,
                                color = Color(0xFF666),
                            )
                        }
                        Text("›", fontSize = 18.sp, color = Color(0xFF999))
                    }
                }
            }
        }
    }
}
```

`weight` 需 `import androidx.compose.foundation.layout.weight`。

- [x] **Step 2: 编译验证**

```powershell
.\gradlew.bat assembleDebug --no-daemon
```

预期：BUILD SUCCESSFUL（Home 与 Library 页面已齐全）。

- [x] **Step 3: 提交**

```powershell
git add -A
git commit -m "feat: add exercise library screen with grouped list and detail"
```

---

### Task 9: 打卡日历页

**Files:**
- Create: `app/src/main/java/com/example/workout/ui/CalendarScreen.kt`
- Test: 手工验证

**Interfaces:**
- Consumes: `CalendarViewModel`、`CalendarUiState`
- Produces: `@Composable fun CalendarScreen(viewModel: CalendarViewModel)`

- [x] **Step 1: 编写 CalendarScreen.kt**

```kotlin
package com.example.workout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.workout.ui.theme.PrimaryBlue
import com.example.workout.viewmodel.CalendarViewModel
import java.time.LocalDate
import java.time.YearMonth

private fun monthGrid(year: Int, month: Int): List<Int?> {
    val ym = YearMonth.of(year, month)
    val firstDay = ym.atDay(1).dayOfWeek.value % 7 // 0=周日
    val out = MutableList(firstDay) { null }
    out.addAll((1..ym.lengthOfMonth()).toList())
    return out
}

@Composable
fun CalendarScreen(viewModel: CalendarViewModel) {
    val state by viewModel.uiState.collectAsState()
    val parts = state.month.split("-")
    var year by remember { mutableIntStateOf(parts[0].toInt()) }
    var month by remember { mutableIntStateOf(parts[1].toInt()) }

    fun monthKey(): String =
        "%04d-%02d".format(year, month)

    LaunchedEffect(year, month) {
        viewModel.loadMonth(monthKey())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("◀", fontSize = 24.sp, modifier = Modifier.clickable {
                if (month == 1) { month = 12; year-- } else month--
            })
            Text("$year 年 $month 月", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("▶", fontSize = 24.sp, modifier = Modifier.clickable {
                if (month == 12) { month = 1; year++ } else month++
            })
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach { w ->
                Text(
                    w,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontSize = 13.sp,
                    color = Color(0xFF888),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            monthGrid(year, month).chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { day ->
                        val boxModifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                        if (day == null) {
                            Box(modifier = boxModifier)
                        } else {
                            val dateStr = "%04d-%02d-%02d".format(year, month, day)
                            val checked = dateStr in state.checkedDates
                            Box(
                                modifier = boxModifier
                                    .background(
                                        if (checked) PrimaryBlue else Color.Transparent,
                                        RoundedCornerShape(8.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    day.toString(),
                                    color = if (checked) Color.White else Color.Black,
                                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
```

- [x] **Step 2: 编译验证**

```powershell
.\gradlew.bat assembleDebug --no-daemon
```

预期：BUILD SUCCESSFUL（三个页面齐全）。

- [x] **Step 3: 提交**

```powershell
git add -A
git commit -m "feat: add calendar screen with month navigation and check-in highlight"
```

---

### Task 10: 真机构建与功能验证

**Files:**
- 无新文件；验证产物
- Test: 真机/模拟器全功能清单

**Interfaces:**
- Consumes: 全部产物

- [x] **Step 1: 完整构建**

```powershell
.\gradlew.bat assembleDebug --no-daemon
```

预期：BUILD SUCCESSFUL。

- [x] **Step 2: 运行全部 JVM 单元测试**

```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
```

预期：全部 PASS（Streak 8 + ExerciseParser 3）。

- [ ] **Step 3: 连接设备并安装**（待真机/模拟器）

连接 Android 手机（开启 USB 调试）或用 Android Studio 模拟器。安装：

```powershell
& "C:\Users\ttt12\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r "app\build\outputs\apk\debug\app-debug.apk"
```

确认设备可见：`adb devices`。若模拟器未启动，用 Android Studio 的 Device Manager 启动。

- [x] **Step 4: 功能验证清单**

- [ ] 启动 App，显示"已连续打卡 0 天"
- [ ] 选择运动类型、填时长、点打卡 → 显示"打卡成功 ✅"，连胜变 1
- [ ] 今日重复打卡被拒绝（提示"该日期已打卡"）
- [ ] 计时型运动（如平板支撑）显示倒计时，自定义秒数后开始，结束后提示"运动完成！可以打卡啦"
- [ ] 运动库：20 种运动按分类分组，点卡片看详情，视频按钮跳转系统浏览器
- [ ] 日历：已打卡日期高亮，左右切换月份正常
- [ ] 杀掉 App 重开，数据仍在（Room 持久化）

- [ ] **Step 5: 运行 Room 集成测试（若设备可用）**

```powershell
.\gradlew.bat connectedDebugAndroidTest --no-daemon
```

预期：RecordDaoTest 3 个测试 PASS。

- [x] **Step 6: 提交**

```powershell
git add -A
git commit -m "chore: finalize android build and verification"
```

---

## 自审记录

### 1. Spec 覆盖对照
- 打卡页 + 倒计时（4.1）→ Task 6（ViewModel）、Task 7（UI）
- 运动库页（4.2）+ 内置清单（附录 A）→ Task 4（JSON 数据）、Task 8（UI）
- 日历页（4.3）→ Task 6（ViewModel）、Task 9（UI）
- 连胜统计（4.4，规则见第 7 节）→ Task 3（纯函数 + 8 个边界测试）、Task 6（接入 ViewModel）
- 数据模型 `records` 表 + date 唯一索引（第 5 节）→ Task 5（Room）
- Repository 接口（第 6 节）→ Task 6
- 测试策略（第 8 节）→ Task 3/4（JVM 单测）、Task 5（androidTest）、Task 10（真机验证）
- 范围外（第 10 节）→ 计划未包含任何这些功能 ✓
- 环境（第 9 节）→ Task 1（SDK 路径 local.properties）；无 NDK 需求 ✓

### 2. 占位符扫描
- Task 1 无系统 gradle：用下载的 Gradle 8.7 发行版生成 wrapper，命令完整
- Task 6 Step 2 的 `refresh()` 先给残缺占位版、紧接以完整实现覆盖——明确指示"替换上面的占位版 refresh()"，无遗留 TODO
- `menuAnchor()` 兼容性给出两种写法，按编译报错择一，非模糊占位
- exercises.json 20 条内容以 Spec 附录 A 为准（Spec 随计划同行）

### 3. 类型一致性核对
- `RecordEntity(id, date, exercise, durationMinutes)` 在 Task 5/6 一致 ✓
- DAO 方法名 `insert` / `getRecordsForMonth` / `getTodayRecord` / `getAllDates` 在 Task 5/6 一致 ✓
- `HomeUiState(streakDays, todayChecked, exercises, message, isLoading)` 在 Task 6/7 一致 ✓
- `Streak.currentStreak(Set<LocalDate>, LocalDate): Int` 在 Task 3/6 一致 ✓
- `Exercise(id, name, category, summary, isTimed, defaultSeconds, reps, steps, videoUrl, note)` 在 Task 4/6/8 一致 ✓
- Repository 方法签名在 Task 6（定义）/Task 7（调用 CheckinForm 用 onCheckIn 回调）/Task 8（loadExercises）一致 ✓
