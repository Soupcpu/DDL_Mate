# Codex 开发提示词：Deadline Mate / 时限管家

你现在要开发一个 Android App，项目名称为 **Deadline Mate / 时限管家**。

这是一个 Deadline 管理 App，主要帮助用户记录任务、管理截止时间、区分重要性和紧急性，并通过日历、优先级队列、语音输入、截图识别等方式提升任务管理效率。

请严格根据以下要求开发。

## 1. 技术栈要求

请使用：

```text
Kotlin
Jetpack Compose
Material 3
Room
ViewModel
StateFlow
Navigation Compose
AlarmManager
Notification
Android SpeechRecognizer
Android Photo Picker
```

如果 OCR 暂时无法完整实现，可以先写接口和占位逻辑，但代码结构必须预留。

## 2. 总体开发目标

请实现一个完整的 Android Studio 项目骨架，要求：

1. 使用 Kotlin。
2. 使用 Jetpack Compose 构建 UI。
3. 使用 MVVM 架构。
4. 使用 Room 存储任务数据。
5. 使用底部导航栏。
6. 实现首页、日历页、添加页、优先级页、我的页。
7. 实现任务增删改查。
8. 实现任务完成状态切换。
9. 实现任务优先级排序。
10. 实现重复任务字段。
11. 实现提醒字段。
12. 为语音输入和截图识别预留功能入口。
13. UI 风格参考 iOS 卡片感和清浊 App 的轻工具感。
14. 避免花哨 emoji，任务图标使用标题首字。
15. 所有智能识别结果都必须进入用户确认编辑流程。

## 3. 项目定位

Deadline Mate 不是普通待办清单，而是围绕 Deadline 管理的任务工具。

核心功能包括：

```text
Deadline 管理
任务提醒
优先级排序
日历视图
重复任务
语音添加
截图识别
手动确认与微调
```

用户打开 App 后最关心的是：

```text
今天该做什么
哪个任务最紧急
本周还有多少任务
哪些 Deadline 临近
```

## 4. UI 风格要求

整体风格：

```text
简洁
干净
轻量工具感
iOS 卡片风
大圆角
浅灰背景
白色半透明卡片
低饱和色彩
细边框
柔和阴影
```

推荐色值：

```text
背景色：#F5F5F7 / #F8FAFC
主色：#007AFF
危险色：#FF3B30
警告色：#FF9500
黄色提醒：#F7B500
完成色：#34C759
文字色：#1D1D1F
辅助文字：#86868B
边框：rgba(60,60,67,0.12)
```

## 5. 图标设计规则

不要滥用 emoji。尤其不要把颜色相近的 emoji 放在同色背景上。

错误示例：

```text
红色背景 + 红色闹钟 emoji
橙色背景 + 橙色闪电 emoji
```

正确规则：

首页统计卡：

```text
今日截止 → 红底白字「今」
重要紧急 → 橙底白字「急」
已完成 → 绿底白字「完」
```

任务卡图标：

```text
取任务标题第一个有效字符
白色加粗
放在圆角色块中间
色块颜色根据紧急程度变化
```

示例：

```text
数据库实验报告 → 数
AI Coding 项目提交 → A
英语测试复习 → 英
```

添加页输入方式：

```text
手动 → 手
语音 → 声
截图 → 图
```

这些文字图标需要设计成圆角色块，低饱和渐变背景，细边框，加粗字体，居中对齐。

底部导航可以使用 emoji 或简洁图标，但大小、颜色和间距要统一。

## 6. 页面导航

底部导航栏包含 5 个 Tab：

```text
首页
日历
添加
优先级
我的
```

中间的「添加」Tab 要比普通 Tab 略突出，但不要做成特别夸张的悬浮按钮。它应该和其他 Tab 保持同一高度，只在图标区域用蓝色强调。

请实现 Navigation Compose。

页面路由建议：

```kotlin
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Calendar : Screen("calendar")
    object AddTask : Screen("add_task")
    object Priority : Screen("priority")
    object Profile : Screen("profile")
}
```

## 7. 数据模型

请创建 Task 数据模型，包含以下字段：

```kotlin
data class Task(
    val id: Long = 0,
    val title: String,
    val description: String? = null,
    val deadlineDateTime: Long,
    val importance: ImportanceLevel,
    val urgency: UrgencyLevel,
    val status: TaskStatus,
    val category: TaskCategory,
    val repeatRule: RepeatRule,
    val reminderEnabled: Boolean,
    val reminderMinutesBefore: Int?,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null
)
```

枚举：

```kotlin
enum class ImportanceLevel {
    HIGH, MEDIUM, LOW
}

enum class UrgencyLevel {
    OVERDUE, HIGH, MEDIUM, LOW
}

enum class TaskStatus {
    TODO, IN_PROGRESS, DONE, EXPIRED
}

enum class TaskCategory {
    STUDY, HOMEWORK, COMPETITION, MEETING, LIFE, OTHER
}

enum class RepeatRule {
    NONE, DAILY, WEEKLY, MONTHLY, CUSTOM
}
```

## 8. TaskDraft

语音和截图识别后不要直接生成 Task，而是先生成 TaskDraft。

```kotlin
data class TaskDraft(
    val title: String? = null,
    val description: String? = null,
    val deadlineDateTime: Long? = null,
    val importance: ImportanceLevel? = null,
    val repeatRule: RepeatRule? = null,
    val category: TaskCategory? = null,
    val rawText: String? = null
)
```

用户必须确认和微调 TaskDraft 后，才能保存为 Task。

## 9. Room 数据库

请实现：

```text
TaskEntity
TaskDao
TaskDatabase
TaskRepository
```

DAO 至少包含：

```kotlin
@Query("SELECT * FROM tasks ORDER BY deadlineDateTime ASC")
fun observeAllTasks(): Flow<List<TaskEntity>>

@Query("SELECT * FROM tasks WHERE status != 'DONE' ORDER BY deadlineDateTime ASC")
fun observeActiveTasks(): Flow<List<TaskEntity>>

@Insert
suspend fun insertTask(task: TaskEntity)

@Update
suspend fun updateTask(task: TaskEntity)

@Delete
suspend fun deleteTask(task: TaskEntity)
```

## 10. 首页 HomeScreen

首页用于展示用户当前最需要关注的 Deadline。

首页包含：

1. 顶部日期和标题。
2. 本周剩余任务概览卡。
3. 三个统计卡：
   - 今日截止
   - 重要紧急
   - 已完成
4. 今日状态建议卡。
5. 临近 Deadline 任务列表。

首页不要显示完成度历史模块。完成度和历史记录放到「我的」页。

首页任务卡包含：

```text
任务首字图标
任务标题
任务描述
优先级标签
截止时间
```

图标颜色规则：

```text
逾期 / 24小时内截止：红色
3天内截止：橙色
7天内截止：黄色
7天以上：蓝色或灰色
已完成：绿色
```

## 11. 添加任务 AddTaskScreen

添加页顶部有三个输入模式：

```text
手动
语音
截图
```

点击不同模式后，下方显示不同内容。

### 11.1 手动模式

显示完整表单：

```text
任务名称
截止日期
截止时间
重要性
提醒
重复
任务类型
备注
保存按钮
```

选项：

重要性：

```text
高
中
低
```

提醒：

```text
截止前 1 天
截止前 3 小时
截止前 30 分钟
不提醒
```

重复：

```text
不重复
每天
每周
每月
自定义
```

任务类型：

```text
学习
作业
比赛
会议
生活
其他
```

### 11.2 语音模式

语音页参考微信语音输入。

页面结构：

```text
上方：语音识别内容区域
中间：任务字段标签
底部：按住说话按钮
```

具体要求：

1. 用户按住按钮开始录音。
2. 松开按钮停止录音。
3. 使用 Android SpeechRecognizer 将语音转文字。
4. 识别文字显示在上方区域。
5. 系统根据文字提取任务草稿。
6. 用户点击确认后进入手动编辑确认。

语音识别完成后不要直接保存任务。

示例语句：

```text
明天晚上八点提醒我提交数据库实验报告，重要性高，每周重复。
```

应解析为：

```text
任务名称：提交数据库实验报告
截止时间：明天 20:00
重要性：高
重复规则：每周
```

### 11.3 截图模式

截图页只需要简单入口，不要复杂说明。

页面内容：

```text
标题：选择一张截图
说明：从相册选择通知、课程平台或比赛要求截图，识别后自动整理为任务草稿。
按钮：打开相册
```

交互要求：

1. 点击打开相册。
2. 使用 Android Photo Picker 选择图片。
3. 使用 OCR 识别图片文字。
4. 提取任务名称、截止时间、提交要求等信息。
5. 生成任务草稿。
6. 用户确认 / 微调。
7. 保存任务。

如果 OCR 暂时不实现，可以先写占位接口：

```kotlin
interface ScreenshotTaskParser {
    suspend fun parseImage(uri: Uri): TaskDraft
}
```

## 12. 日历 CalendarScreen

日历页支持：

```text
周历
月历
```

右上角按钮用于切换：

```text
月历 / 周历
```

周历展示本周 7 天：

```text
星期
日期
任务小圆点
```

点击某一天，下方显示当天任务时间线。

月历要求：

1. 顶部显示当前年月。
2. 左右按钮切换上个月 / 下个月。
3. 有任务的日期显示小红点。
4. 今天用蓝色高亮。
5. 点击日期后展示当天任务。

## 13. 优先级 PriorityScreen

不要做传统四象限。

请实现更实用的优先级队列页面。

分为：

```text
马上处理
计划推进
可延后
```

马上处理包含：

```text
已逾期
今天截止
24小时内截止
重要性高且临近截止
```

计划推进包含：

```text
重要但不马上截止
需要持续推进的任务
重复任务
```

可延后包含：

```text
不紧急
重要性低
没有明确截止压力
```

请实现 PriorityCalculator：

```kotlin
class PriorityCalculator {
    fun calculate(task: Task, now: Long): Int {
        // 分数越高越优先
    }
}
```

建议规则：

```text
已逾期：+100
24小时内截止：+80
3天内截止：+50
7天内截止：+30
重要性高：+40
重要性中：+20
重要性低：+5
重复任务：+10
```

## 14. 我的 ProfileScreen

我的页包含：

```text
用户信息
本周完成度
连续完成天数
最近完成任务
通知提醒设置
默认提醒时间
语音识别设置
主题样式
```

完成反馈不要放在首页第一屏。

显示：

```text
本周完成度百分比
已完成任务数 / 总任务数
连续完成天数
最近完成任务列表
```

示例：

```text
本周完成度：76%
已完成 13 / 17 个任务
连续完成：6 天
```

## 15. 提醒功能

使用 Android 通知系统。

提醒方式：

```text
截止前 1 天
截止前 3 小时
截止前 30 分钟
不提醒
```

实现建议：

```text
AlarmManager
BroadcastReceiver
NotificationManager
```

需要考虑 Android 13+ 通知权限。

请封装：

```kotlin
class ReminderScheduler {
    fun schedule(task: Task)
    fun cancel(taskId: Long)
}
```

## 16. 推荐项目结构

请按照以下结构组织项目：

```text
app/src/main/java/你的包名/
├── MainActivity.kt
├── navigation/
│   └── AppNavGraph.kt
├── data/
│   ├── local/
│   │   ├── TaskEntity.kt
│   │   ├── TaskDao.kt
│   │   └── TaskDatabase.kt
│   └── repository/
│       └── TaskRepository.kt
├── domain/
│   ├── model/
│   │   ├── Task.kt
│   │   ├── TaskDraft.kt
│   │   └── TaskEnums.kt
│   ├── PriorityCalculator.kt
│   ├── UrgencyCalculator.kt
│   ├── VoiceTaskParser.kt
│   └── ScreenshotTaskParser.kt
├── reminder/
│   ├── ReminderScheduler.kt
│   ├── AlarmReceiver.kt
│   └── NotificationHelper.kt
├── ui/
│   ├── theme/
│   ├── components/
│   ├── home/
│   ├── calendar/
│   ├── add/
│   ├── priority/
│   └── profile/
└── util/
    └── DateTimeUtils.kt
```

## 17. 开发顺序

请按这个顺序开发：

1. 搭建 Compose 项目结构。
2. 完成底部导航。
3. 完成 Task 数据模型和 Room。
4. 完成手动添加任务。
5. 完成首页任务展示。
6. 完成任务完成 / 删除 / 编辑。
7. 完成优先级页。
8. 完成日历页周历和月历。
9. 完成我的页统计。
10. 接入通知提醒。
11. 接入语音识别。
12. 接入截图选择和 OCR 占位。

## 18. MVP 必须完成

MVP 至少要能做到：

1. 手动添加任务。
2. 本地保存任务。
3. 首页展示临近任务。
4. 任务按截止时间排序。
5. 显示今日截止数量。
6. 显示重要紧急数量。
7. 显示已完成数量。
8. 日历页查看任务。
9. 优先级页按规则分组。
10. 我的页查看完成度和历史。
11. 可以标记任务完成。
12. 可以删除任务。

语音和截图可以先做 UI 与接口占位，但要保证结构能扩展。

## 19. 注意事项

请特别注意：

1. 不要把首页做得太复杂。
2. 不要在首页第一屏展示完成度历史。
3. 不要使用颜色相近的 emoji 和背景。
4. 不要用传统四象限页面。
5. 不要让语音和截图识别结果直接保存。
6. 所有 AI / OCR / 语音识别结果都必须进入确认编辑流程。
7. UI 要简洁、干净、轻量，不要做成花哨宣传页。
8. 添加任务按钮要在底部导航中间，但不要突兀。
9. 任务图标使用标题首字，不使用随机 emoji。
10. 页面要适合单手使用。

## 20. 最终一句话总结

请根据以上说明，开发一个 Kotlin + Jetpack Compose 的 Android Deadline 管理 App。项目重点不是普通待办清单，而是围绕 Deadline、优先级、日历、提醒、语音输入和截图识别构建一个简洁、实用、好看的任务管理工具。UI 风格参考 iOS 卡片感和清浊 App 的轻工具感，避免花哨 emoji，任务图标使用标题首字，所有智能识别结果都必须进入用户确认编辑流程。
