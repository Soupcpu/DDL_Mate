package com.deadlinemate.domain.model

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
