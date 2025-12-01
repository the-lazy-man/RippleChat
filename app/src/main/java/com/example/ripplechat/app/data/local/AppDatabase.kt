package com.example.ripplechat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.ripplechat.app.data.local.MessageDao
import com.example.ripplechat.app.data.local.MessageEntity
import androidx.room.migration.Migration // Add this
import androidx.sqlite.db.SupportSQLiteDatabase // Add this

@Database(entities = [MessageEntity::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}
// 2. DEFINE MIGRATION OBJECT (e.g., in a separate file or a companion object)
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add the new columns to the existing 'messages' table
        database.execSQL("ALTER TABLE messages ADD COLUMN edited INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE messages ADD COLUMN mediaUrl TEXT")
        database.execSQL("ALTER TABLE messages ADD COLUMN isMedia INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE messages ADD COLUMN mediaType TEXT")
    }
}