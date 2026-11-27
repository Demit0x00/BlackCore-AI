package com.blackcore.ai

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chat_threads")
data class ChatThread(@PrimaryKey val id: String, val title: String, val createdAt: Long, val updatedAt: Long)

@Entity(tableName = "chat_messages", indices = [Index("threadId")])
data class StoredMessage(
    @PrimaryKey val id: String,
    @ColumnInfo(defaultValue = "'legacy'") val threadId: String,
    val role: String, val content: String, val createdAt: Long
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_threads ORDER BY updatedAt DESC") fun observeThreads(): Flow<List<ChatThread>>
    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY createdAt ASC") fun observeMessages(threadId: String): Flow<List<StoredMessage>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertThread(thread: ChatThread)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(message: StoredMessage)
    @Query("UPDATE chat_threads SET title = :title, updatedAt = :updatedAt WHERE id = :id") suspend fun renameThread(id: String, title: String, updatedAt: Long)
    @Query("UPDATE chat_threads SET updatedAt = :updatedAt WHERE id = :id") suspend fun touchThread(id: String, updatedAt: Long)
    @Query("DELETE FROM chat_messages WHERE threadId = :threadId") suspend fun clearMessages(threadId: String)
    @Query("DELETE FROM chat_messages WHERE threadId = :threadId") suspend fun deleteMessages(threadId: String)
    @Query("DELETE FROM chat_threads WHERE id = :threadId") suspend fun deleteThread(threadId: String)
    @Query("SELECT COUNT(*) FROM chat_messages WHERE threadId = :threadId") suspend fun messageCount(threadId: String): Int
}

@Database(entities = [ChatThread::class, StoredMessage::class], version = 2, exportSchema = false)
abstract class BlackCoreDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS chat_threads (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("INSERT INTO chat_threads (id, title, createdAt, updatedAt) VALUES ('legacy', 'Percakapan lama', 0, 0)")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN threadId TEXT NOT NULL DEFAULT 'legacy'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_threadId ON chat_messages(threadId)")
            }
        }
        @Volatile private var instance: BlackCoreDatabase? = null
        fun get(context: Context): BlackCoreDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, BlackCoreDatabase::class.java, "blackcore-chat.db")
                .addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}