package com.deadlinemate.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY deadlineDateTime ASC")
    fun observeAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status != 'DONE' ORDER BY deadlineDateTime ASC")
    fun observeActiveTasks(): Flow<List<TaskEntity>>

    @Insert
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query(
        """
        DELETE FROM tasks
        WHERE (title = '数据库实验报告' AND description = 'SQL 文件、截图和实验总结')
           OR (title = 'AI Coding 项目提交' AND description = 'GitHub 链接与压缩包')
           OR (title = '英语测试复习' AND description = '单词、阅读和作文模板')
        """
    )
    suspend fun deleteGeneratedDemoTasks()
}
