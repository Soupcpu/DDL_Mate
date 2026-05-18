package com.deadlinemate.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.deadlinemate.domain.model.ImportanceLevel
import com.deadlinemate.domain.model.RepeatRule
import com.deadlinemate.domain.model.Task
import com.deadlinemate.domain.model.TaskCategory
import com.deadlinemate.domain.model.TaskStatus
import com.deadlinemate.domain.model.UrgencyLevel

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String?,
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
    val completedAt: Long?
)

fun TaskEntity.toDomain() = Task(
    id = id,
    title = title,
    description = description,
    deadlineDateTime = deadlineDateTime,
    importance = importance,
    urgency = urgency,
    status = status,
    category = category,
    repeatRule = repeatRule,
    reminderEnabled = reminderEnabled,
    reminderMinutesBefore = reminderMinutesBefore,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt
)

fun Task.toEntity() = TaskEntity(
    id = id,
    title = title,
    description = description,
    deadlineDateTime = deadlineDateTime,
    importance = importance,
    urgency = urgency,
    status = status,
    category = category,
    repeatRule = repeatRule,
    reminderEnabled = reminderEnabled,
    reminderMinutesBefore = reminderMinutesBefore,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt
)
