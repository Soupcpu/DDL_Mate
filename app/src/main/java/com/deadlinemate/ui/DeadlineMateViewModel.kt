package com.deadlinemate.ui

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deadlinemate.data.repository.TaskRepository
import com.deadlinemate.deepseek.ApiKeyStore
import com.deadlinemate.deepseek.DeepSeekApiClient
import com.deadlinemate.deepseek.DeepSeekConfig
import com.deadlinemate.deepseek.DeepSeekTaskParseDebugResult
import com.deadlinemate.deepseek.TaskTextParser
import com.deadlinemate.domain.UrgencyCalculator
import com.deadlinemate.domain.model.ImportanceLevel
import com.deadlinemate.domain.model.RepeatRule
import com.deadlinemate.domain.model.Task
import com.deadlinemate.domain.model.TaskCategory
import com.deadlinemate.domain.model.TaskDraft
import com.deadlinemate.domain.model.TaskStatus
import com.deadlinemate.reminder.ReminderScheduler
import com.deadlinemate.update.AppUpdateInfo
import com.deadlinemate.update.AppUpdateInstaller
import com.deadlinemate.update.UpdateDownloadState
import com.deadlinemate.update.GithubUpdateChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.deadlinemate.ui.i18n.defaultAppLanguage

data class UiSettings(
    val notificationEnabled: Boolean = true,
    val defaultReminderMinutes: Int? = 1440,
    val speechLanguage: String = defaultAppLanguage(),
    val themeStyle: String = "跟随系统",
    val profileName: String = "",
    val avatarPath: String? = null,
    val developerModeEnabled: Boolean = false,
    val showDeveloperEntry: Boolean = false
)

class DeadlineMateViewModel(
    private val repository: TaskRepository,
    private val prefs: SharedPreferences,
    private val apiKeyStore: ApiKeyStore,
    private val deepSeekApiClient: DeepSeekApiClient,
    private val taskTextParser: TaskTextParser,
    private val reminderScheduler: ReminderScheduler,
    private val updateChecker: GithubUpdateChecker,
    private val updateInstaller: AppUpdateInstaller
) : ViewModel() {
    val tasks: StateFlow<List<Task>> = repository.allTasks.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
    private val _uiSettings = MutableStateFlow(loadSettings())
    val uiSettings: StateFlow<UiSettings> = _uiSettings.asStateFlow()
    private val _deepSeekConfig = MutableStateFlow(normalizeDeepSeekConfig(apiKeyStore.getDeepSeekConfig()))
    val deepSeekConfig: StateFlow<DeepSeekConfig?> = _deepSeekConfig.asStateFlow()
    val updateDownloadState: StateFlow<UpdateDownloadState> = updateInstaller.downloadState

    init {
        cleanupGeneratedDemoTasksOnce()
        restoreRemindersOnStart()
    }

    fun addTask(
        title: String,
        description: String?,
        deadline: Long,
        importance: ImportanceLevel,
        category: TaskCategory,
        repeatRule: RepeatRule,
        reminderMinutes: Int?
    ) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val task = Task(
            title = title,
            description = description?.takeIf { it.isNotBlank() },
            deadlineDateTime = deadline,
            importance = importance,
            urgency = UrgencyCalculator.calculate(deadline, TaskStatus.TODO, now),
            status = TaskStatus.TODO,
            category = category,
            repeatRule = repeatRule,
            reminderEnabled = reminderMinutes != null,
            reminderMinutesBefore = reminderMinutes,
            createdAt = now,
            updatedAt = now
        )
        val id = repository.addTask(task)
        scheduleReminderIfNeeded(task.copy(id = id))
    }

    fun toggleDone(task: Task) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val done = task.status != TaskStatus.DONE
        val updated = task.copy(
            status = if (done) TaskStatus.DONE else TaskStatus.TODO,
            completedAt = if (done) now else null,
            updatedAt = now
        )
        repository.updateTask(updated)
        if (done) {
            reminderScheduler.cancel(task.id)
        } else {
            scheduleReminderIfNeeded(updated)
        }
    }

    fun deleteTask(task: Task) = viewModelScope.launch {
        reminderScheduler.cancel(task.id)
        repository.deleteTask(task)
    }

    fun updateTaskDetails(
        task: Task,
        title: String,
        description: String?,
        deadline: Long,
        importance: ImportanceLevel,
        category: TaskCategory,
        repeatRule: RepeatRule,
        reminderMinutes: Int?
    ) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val updated = task.copy(
            title = title.trim(),
            description = description?.takeIf { it.isNotBlank() },
            deadlineDateTime = deadline,
            importance = importance,
            urgency = UrgencyCalculator.calculate(deadline, task.status, now),
            category = category,
            repeatRule = repeatRule,
            reminderEnabled = reminderMinutes != null,
            reminderMinutesBefore = reminderMinutes,
            updatedAt = now
        )
        reminderScheduler.cancel(task.id)
        repository.updateTask(updated)
        scheduleReminderIfNeeded(updated)
    }

    fun toggleNotifications() = updateSettings {
        val enabled = !it.notificationEnabled
        viewModelScope.launch {
            if (enabled) {
                tasks.value.filter { task -> task.status != TaskStatus.DONE }.forEach(::scheduleReminderIfNeeded)
            } else {
                tasks.value.forEach { task -> reminderScheduler.cancel(task.id) }
            }
        }
        it.copy(notificationEnabled = enabled)
    }

    fun cycleDefaultReminder() = updateSettings {
        val next = when (it.defaultReminderMinutes) {
            1440 -> 180
            180 -> 30
            30 -> null
            else -> 1440
        }
        it.copy(defaultReminderMinutes = next)
    }

    fun setDefaultReminder(minutes: Int?) = updateSettings {
        it.copy(defaultReminderMinutes = minutes)
    }

    fun cycleSpeechLanguage() = updateSettings {
        val next = if (normalizeSpeechLanguage(it.speechLanguage) == "English") "中文" else "English"
        it.copy(speechLanguage = next)
    }

    fun setSpeechLanguage(language: String) = updateSettings {
        it.copy(speechLanguage = normalizeSpeechLanguage(language))
    }

    fun cycleThemeStyle() = updateSettings {
        val next = when (it.themeStyle) {
            "跟随系统" -> "亮色"
            "亮色" -> "暗色"
            else -> "跟随系统"
        }
        it.copy(themeStyle = next)
    }

    fun setThemeStyle(theme: String) = updateSettings {
        it.copy(themeStyle = normalizeThemeStyle(theme))
    }

    fun setProfileName(name: String) = updateSettings {
        it.copy(profileName = name.trim())
    }

    fun setAvatarPath(path: String?) = updateSettings {
        it.copy(avatarPath = path?.takeIf { value -> value.isNotBlank() })
    }

    fun enableDeveloperMode() = updateSettings {
        it.copy(developerModeEnabled = true, showDeveloperEntry = true)
    }

    fun setShowDeveloperEntry(show: Boolean) = updateSettings {
        it.copy(showDeveloperEntry = show)
    }

    fun saveDeepSeekConfig(config: DeepSeekConfig) {
        apiKeyStore.saveDeepSeekConfig(normalizeDeepSeekConfig(config) ?: config)
        _deepSeekConfig.value = normalizeDeepSeekConfig(apiKeyStore.getDeepSeekConfig())
    }

    fun clearDeepSeekConfig() {
        apiKeyStore.clearDeepSeekConfig()
        _deepSeekConfig.value = null
    }

    suspend fun testDeepSeekConnection(config: DeepSeekConfig): Result<Unit> {
        return deepSeekApiClient.testConnection(config)
    }

    suspend fun parseTaskText(text: String): Result<TaskDraft> {
        return taskTextParser.parse(text)
    }

    suspend fun parseTaskTextForDebug(text: String): Result<DeepSeekTaskParseDebugResult> {
        val config = _deepSeekConfig.value
            ?.takeIf { it.enabled && it.apiKey.isNotBlank() }
            ?: return Result.failure(IllegalStateException("DeepSeek API 未配置或未启用"))
        return deepSeekApiClient.parseTaskTextDebug(config, text)
    }

    suspend fun checkAppUpdate(currentVersion: String): Result<AppUpdateInfo> {
        return updateChecker.checkLatest(currentVersion)
    }

    fun downloadAndInstallUpdate(apkUrl: String, apkName: String?): Result<Unit> {
        return updateInstaller.downloadAndInstall(apkUrl, apkName)
    }

    private fun normalizeDeepSeekConfig(config: DeepSeekConfig?): DeepSeekConfig? {
        return config?.copy(enabled = config.apiKey.isNotBlank())
    }

    private fun updateSettings(transform: (UiSettings) -> UiSettings) {
        _uiSettings.update(transform)
        saveSettings(_uiSettings.value)
    }

    private fun scheduleReminderIfNeeded(task: Task) {
        if (!_uiSettings.value.notificationEnabled) return
        if (task.status == TaskStatus.DONE) return
        reminderScheduler.schedule(task)
    }

    private fun restoreRemindersOnStart() {
        viewModelScope.launch {
            if (!_uiSettings.value.notificationEnabled) return@launch
            repository.allTasks.first()
                .filter { it.status != TaskStatus.DONE }
                .forEach(::scheduleReminderIfNeeded)
        }
    }

    private fun cleanupGeneratedDemoTasksOnce() {
        if (prefs.getBoolean("cleanup.generated_demo_tasks.v1", false)) return
        viewModelScope.launch {
            repository.deleteGeneratedDemoTasks()
            prefs.edit().putBoolean("cleanup.generated_demo_tasks.v1", true).apply()
        }
    }

    private fun loadSettings(): UiSettings {
        val reminder = if (prefs.getBoolean("settings.reminder.enabled", true)) {
            prefs.getInt("settings.reminder.minutes", 1440)
        } else {
            null
        }
        return UiSettings(
            notificationEnabled = prefs.getBoolean("settings.notifications", true),
            defaultReminderMinutes = reminder,
            speechLanguage = normalizeSpeechLanguage(prefs.getString("settings.speech_language", defaultAppLanguage()) ?: defaultAppLanguage()),
            themeStyle = normalizeThemeStyle(prefs.getString("settings.theme", "跟随系统") ?: "跟随系统"),
            profileName = prefs.getString("settings.profile.name", "")?.trim().orEmpty(),
            avatarPath = prefs.getString("settings.profile.avatar_path", null)?.takeIf { it.isNotBlank() },
            developerModeEnabled = prefs.getBoolean("settings.developer.enabled", false),
            showDeveloperEntry = prefs.getBoolean("settings.developer.show_entry", false)
        )
    }

    private fun saveSettings(settings: UiSettings) {
        prefs.edit()
            .putBoolean("settings.notifications", settings.notificationEnabled)
            .putBoolean("settings.reminder.enabled", settings.defaultReminderMinutes != null)
            .putInt("settings.reminder.minutes", settings.defaultReminderMinutes ?: -1)
            .putString("settings.speech_language", settings.speechLanguage)
            .putString("settings.theme", settings.themeStyle)
            .putString("settings.profile.name", settings.profileName)
            .putString("settings.profile.avatar_path", settings.avatarPath)
            .putBoolean("settings.developer.enabled", settings.developerModeEnabled)
            .putBoolean("settings.developer.show_entry", settings.showDeveloperEntry)
            .apply()
    }

    private fun normalizeSpeechLanguage(language: String): String {
        return if (language == "English") "English" else "中文"
    }

    private fun normalizeThemeStyle(theme: String): String {
        return when (theme) {
            "亮色", "Light" -> "亮色"
            "暗色", "Dark" -> "暗色"
            "跟随系统", "System" -> "跟随系统"
            else -> "跟随系统"
        }
    }
}
