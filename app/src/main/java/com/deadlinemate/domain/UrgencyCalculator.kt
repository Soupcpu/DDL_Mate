package com.deadlinemate.domain

import com.deadlinemate.domain.model.TaskStatus
import com.deadlinemate.domain.model.UrgencyLevel
import java.util.concurrent.TimeUnit

object UrgencyCalculator {
    fun calculate(deadline: Long, status: TaskStatus, now: Long = System.currentTimeMillis()): UrgencyLevel {
        if (status == TaskStatus.DONE) return UrgencyLevel.LOW
        val hours = TimeUnit.MILLISECONDS.toHours(deadline - now)
        return when {
            deadline < now -> UrgencyLevel.OVERDUE
            hours <= 24 -> UrgencyLevel.HIGH
            hours <= 72 -> UrgencyLevel.MEDIUM
            else -> UrgencyLevel.LOW
        }
    }
}
