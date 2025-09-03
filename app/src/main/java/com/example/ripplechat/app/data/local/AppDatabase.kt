package com.example.ripplechat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.ripplechat.app.data.local.MessageDao
import com.example.ripplechat.app.data.local.MessageEntity

@Database(entities = [MessageEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}
