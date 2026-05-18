package com.deadlinemate.domain

import com.deadlinemate.domain.model.ImportanceLevel
import com.deadlinemate.domain.model.RepeatRule
import com.deadlinemate.domain.model.Task
import com.deadlinemate.domain.model.TaskStatus
import java.util.concurrent.TimeUnit

class PriorityCalculator {
    fun calculate(task: Task, now: Long = System.currentTimeMillis()): Int {
        if (task.status == TaskStatus.DONE) return 0
        val hours = TimeUnit.MILLISECONDS.toHours(task.deadlineDateTime - now)
        var score = when {
            task.deadlineDateTime < now -> 100
            hours <= 24 -> 80
            hours <= 72 -> 50
            hours <= 168 -> 30
            else -> 0
        }
        score += when (task.importance) {
            ImportanceLevel.HIGH -> 40
            ImportanceLevel.MEDIUM -> 20
            ImportanceLevel.LOW -> 5
        }
        if (task.repeatRule != RepeatRule.NONE) score += 10
        return score
    }
}
