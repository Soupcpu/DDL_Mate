package com.deadlinemate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.deadlinemate.navigation.DeadlineMateApp
import com.deadlinemate.ui.DeadlineMateViewModel
import com.deadlinemate.ui.theme.DeadlineMateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DeadlineMateApplication
        val vm = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DeadlineMateViewModel(
                        app.repository,
                        app.getSharedPreferences("deadline_mate_settings", MODE_PRIVATE),
                        app.apiKeyStore,
                        app.deepSeekApiClient,
                        app.taskTextParser,
                        app.reminderScheduler,
                        app.updateChecker,
                        app.updateInstaller
                    ) as T
                }
            }
        )[DeadlineMateViewModel::class.java]
        setContent {
            DeadlineMateTheme {
                DeadlineMateApp(vm)
            }
        }
    }
}
