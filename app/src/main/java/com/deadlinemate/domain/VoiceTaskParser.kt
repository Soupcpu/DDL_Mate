package com.deadlinemate.domain

import com.deadlinemate.domain.model.ImportanceLevel
import com.deadlinemate.domain.model.TaskCategory
import com.deadlinemate.domain.model.TaskDraft
import com.deadlinemate.domain.model.RepeatRule
import com.deadlinemate.util.DateTimeUtils

class VoiceTaskParser {
    fun parse(text: String): TaskDraft {
        val importance = if ("高" in text || "重要" in text) ImportanceLevel.HIGH else ImportanceLevel.MEDIUM
        val repeat = if ("每周" in text) RepeatRule.WEEKLY else if ("每天" in text) RepeatRule.DAILY else RepeatRule.NONE
        return TaskDraft(
            title = text.replace("提醒我", "").take(24).ifBlank { "语音任务" },
            deadlineDateTime = DateTimeUtils.tomorrowAt(20, 0),
            importance = importance,
            repeatRule = repeat,
            category = TaskCategory.STUDY,
            rawText = text
        )
    }
}
