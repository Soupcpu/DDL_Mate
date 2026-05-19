package com.deadlinemate

import android.app.Application
import com.deadlinemate.data.local.TaskDatabase
import com.deadlinemate.data.repository.TaskRepository
import com.deadlinemate.deepseek.ApiKeyStore
import com.deadlinemate.deepseek.DeepSeekApiClient
import com.deadlinemate.deepseek.DeepSeekTaskTextParser
import com.deadlinemate.push.MiPushInitializer
import com.deadlinemate.reminder.ReminderScheduler
import com.deadlinemate.update.AppUpdateInstaller
import com.deadlinemate.update.GithubUpdateChecker
import okhttp3.OkHttpClient

class DeadlineMateApplication : Application() {
    val database by lazy { TaskDatabase.create(this) }
    val repository by lazy { TaskRepository(database.taskDao()) }
    val apiKeyStore by lazy { ApiKeyStore(this) }
    private val httpClient by lazy { OkHttpClient.Builder().build() }
    val deepSeekApiClient by lazy { DeepSeekApiClient(httpClient) }
    val taskTextParser by lazy { DeepSeekTaskTextParser(deepSeekApiClient, apiKeyStore) }
    val reminderScheduler by lazy { ReminderScheduler(this) }
    val updateChecker by lazy { GithubUpdateChecker(httpClient) }
    val updateInstaller by lazy { AppUpdateInstaller(this, httpClient) }

    override fun onCreate() {
        super.onCreate()
        MiPushInitializer.init(this)
    }
}
