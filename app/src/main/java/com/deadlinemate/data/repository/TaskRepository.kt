package com.deadlinemate.data.repository

import com.deadlinemate.data.local.TaskDao
import com.deadlinemate.data.local.toDomain
import com.deadlinemate.data.local.toEntity
import com.deadlinemate.domain.model.Task
import kotlinx.coroutines.flow.map

class TaskRepository(private val dao: TaskDao) {
    val allTasks = dao.observeAllTasks().map { entities -> entities.map { it.toDomain() } }
    val activeTasks = dao.observeActiveTasks().map { entities -> entities.map { it.toDomain() } }

    suspend fun addTask(task: Task): Long = dao.insertTask(task.toEntity())
    suspend fun updateTask(task: Task) = dao.updateTask(task.toEntity())
    suspend fun deleteTask(task: Task) = dao.deleteTask(task.toEntity())
    suspend fun deleteGeneratedDemoTasks() = dao.deleteGeneratedDemoTasks()
}
