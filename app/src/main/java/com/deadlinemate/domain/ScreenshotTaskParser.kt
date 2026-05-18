package com.deadlinemate.domain

import android.net.Uri
import com.deadlinemate.domain.model.TaskDraft

interface ScreenshotTaskParser {
    suspend fun parseImage(uri: Uri): TaskDraft
}
