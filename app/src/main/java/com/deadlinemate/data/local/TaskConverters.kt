package com.deadlinemate.data.local

import androidx.room.TypeConverter
import com.deadlinemate.domain.model.ImportanceLevel
import com.deadlinemate.domain.model.RepeatRule
import com.deadlinemate.domain.model.TaskCategory
import com.deadlinemate.domain.model.TaskStatus
import com.deadlinemate.domain.model.UrgencyLevel

class TaskConverters {
    @TypeConverter fun importance(value: ImportanceLevel) = value.name
    @TypeConverter fun importance(value: String) = ImportanceLevel.valueOf(value)
    @TypeConverter fun urgency(value: UrgencyLevel) = value.name
    @TypeConverter fun urgency(value: String) = UrgencyLevel.valueOf(value)
    @TypeConverter fun status(value: TaskStatus) = value.name
    @TypeConverter fun status(value: String) = TaskStatus.valueOf(value)
    @TypeConverter fun category(value: TaskCategory) = value.name
    @TypeConverter fun category(value: String) = TaskCategory.valueOf(value)
    @TypeConverter fun repeat(value: RepeatRule) = value.name
    @TypeConverter fun repeat(value: String) = RepeatRule.valueOf(value)
}
