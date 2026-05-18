package com.deadlinemate.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [TaskEntity::class], version = 1, exportSchema = true)
@TypeConverters(TaskConverters::class)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        fun create(context: Context): TaskDatabase =
            Room.databaseBuilder(context, TaskDatabase::class.java, "deadline_mate.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
