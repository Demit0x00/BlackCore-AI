package com.blackcore.ai

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chat_messages")
data class StoredMessage(@PrimaryKey val id: String, val role: String, val content: String, val createdAt: Long)

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY createdAt ASC") fun observeMessages(): Flow<List<StoredMessage>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(message: StoredMessage)
    @Query("DELETE FROM chat_messages") suspend fun clear()
}

@Database(entities = [StoredMessage::class], version = 1, exportSchema = false)
abstract class BlackCoreDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    companion object {
        @Volatile private var instance: BlackCoreDatabase? = null
        fun get(context: Context): BlackCoreDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, BlackCoreDatabase::class.java, "blackcore-chat.db").build().also { instance = it }
        }
    }
}
