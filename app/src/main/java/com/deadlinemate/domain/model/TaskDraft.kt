package com.deadlinemate.domain.model

data class TaskDraft(
    val title: String? = null,
    val description: String? = null,
    val deadlineDateTime: Long? = null,
    val deadlineDateTimeText: String? = null,
    val importance: ImportanceLevel? = null,
    val repeatRule: RepeatRule? = null,
    val category: TaskCategory? = null,
    val confidence: Double? = null,
    val missingFields: List<String> = emptyList(),
    val rawText: String? = null
)
