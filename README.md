# Deadline Mate

Deadline Mate 是一个 Kotlin + Jetpack Compose 的 Android Deadline 管理 App 骨架，已按设计文档补齐基础环境和 MVP 代码结构。

## 已包含

- Android Studio 工程结构：`settings.gradle`、根 `build.gradle`、`app` 模块。
- Kotlin、Jetpack Compose、Material 3、Room、ViewModel、StateFlow、Navigation Compose 依赖配置。
- 五个主页面：首页、日历、添加任务、优先级、我的。
- Room 数据层：`TaskEntity`、`TaskDao`、`TaskDatabase`、`TaskRepository`。
- 领域模型：`Task`、`TaskDraft`、枚举、优先级算法、紧急度算法。
- 提醒结构：`ReminderScheduler`、`AlarmReceiver`、`NotificationHelper`。
- 语音与截图识别扩展点：`VoiceTaskParser`、`ScreenshotTaskParser`。

## 本机还需要配置

当前机器已安装 Android Studio，但没有发现 Android SDK 路径。请在 Android Studio 里安装 SDK，然后创建 `local.properties`：

```properties
sdk.dir=C\:/Users/User/AppData/Local/Android/Sdk
```

也可以把 `local.properties.example` 复制为 `local.properties` 后改成真实 SDK 路径。

## 构建

```powershell
.\gradlew.bat :app:assembleDebug
```

如果 Android Studio 提示缺少 SDK Platform，安装 `compileSdk 35` 对应平台即可。
