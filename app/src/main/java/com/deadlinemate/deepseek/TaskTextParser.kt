package com.deadlinemate.deepseek

import com.deadlinemate.domain.model.TaskDraft

interface TaskTextParser {
    suspend fun parse(text: String): Result<TaskDraft>
}

class DeepSeekTaskTextParser(
    private val apiClient: DeepSeekApiClient,
    private val apiKeyStore: ApiKeyStore
) : TaskTextParser {
    override suspend fun parse(text: String): Result<TaskDraft> {
        val config = apiKeyStore.getDeepSeekConfig()
            ?.takeIf { it.enabled && it.apiKey.isNotBlank() }
            ?: return Result.failure(IllegalStateException("DeepSeek API 未启用"))
        return apiClient.parseTaskText(config, text)
    }
}
