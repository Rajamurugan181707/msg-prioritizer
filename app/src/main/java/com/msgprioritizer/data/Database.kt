package com.msgprioritizer.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.msgprioritizer.scoring.ContactHistory
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "messages", indices = [Index(value = ["dedupeKey"], unique = true)])
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** WhatsApp re-posts the same message in every notification update; this stops duplicates. */
    val dedupeKey: String,
    val app: String,
    val chat: String,
    val sender: String,
    val text: String,
    val isGroup: Boolean,
    val receivedAt: Long,
    val score: Int,
    val level: String,
    /** Newline-separated reasons from the scorer. */
    val reasons: String,
    val done: Boolean = false,
    /** When the message says something is due ("fees due tomorrow 5pm"), or null. */
    val dueAt: Long? = null,
    /** False when only a day was given ("due tomorrow"), so no clock time is shown. */
    @ColumnInfo(defaultValue = "0") val dueHasTime: Boolean = false,
    /** The user's verdict on the rank: 1 = right, -1 = wrong, null = not rated. */
    val feedback: Int? = null,
)

@Dao
interface MessageDao {
    /** Returns -1 when the message was already stored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: Message): Long

    @Query("SELECT * FROM messages WHERE done = 0 ORDER BY score DESC, receivedAt DESC")
    fun active(): Flow<List<Message>>

    @Query("UPDATE messages SET done = 1 WHERE id = :id")
    suspend fun markDone(id: Long)

    @Query(
        "SELECT COUNT(*) FROM messages WHERE sender = :sender AND chat = :chat " +
            "AND receivedAt > :since AND receivedAt < :before"
    )
    suspend fun countBetween(sender: String, chat: String, since: Long, before: Long): Int

    @Query("DELETE FROM messages WHERE receivedAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM messages WHERE done = 1")
    suspend fun clearDone()

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    @Query("SELECT * FROM messages WHERE done = 0")
    suspend fun activeOnce(): List<Message>

    @Query("UPDATE messages SET score = :score, level = :level, reasons = :reasons WHERE id = :id")
    suspend fun updateScore(id: Long, score: Int, level: String, reasons: String)

    @Query("UPDATE messages SET done = 1 WHERE level = :level AND done = 0")
    suspend fun markLevelDone(level: String)

    @Query("UPDATE messages SET feedback = :value WHERE id = :id")
    suspend fun setFeedback(id: Long, value: Int?)
}

/** What the app has learned about one chat from the user's behaviour. */
@Entity(tableName = "contact_stats")
data class ContactStats(
    @PrimaryKey val chat: String,
    /** Notification tapped, chat opened in WhatsApp, or "Open WhatsApp" pressed here. */
    val opens: Int,
    /** Notification swiped away without opening. */
    val ignores: Int,
    val totalOpenDelayMs: Long,
) {
    fun toHistory() = ContactHistory(opens, ignores, if (opens == 0) 0 else totalOpenDelayMs / opens)
}

@Dao
interface ContactStatsDao {
    @Query("SELECT * FROM contact_stats WHERE chat = :chat")
    suspend fun get(chat: String): ContactStats?

    @Query("SELECT * FROM contact_stats ORDER BY (opens + ignores) DESC")
    fun all(): Flow<List<ContactStats>>

    @Query("SELECT * FROM contact_stats")
    suspend fun allOnce(): List<ContactStats>

    @Query("INSERT OR IGNORE INTO contact_stats (chat, opens, ignores, totalOpenDelayMs) VALUES (:chat, 0, 0, 0)")
    suspend fun ensure(chat: String)

    @Query("UPDATE contact_stats SET opens = opens + 1, totalOpenDelayMs = totalOpenDelayMs + :delayMs WHERE chat = :chat")
    suspend fun addOpen(chat: String, delayMs: Long)

    @Query("UPDATE contact_stats SET ignores = ignores + 1 WHERE chat = :chat")
    suspend fun addIgnore(chat: String)

    @Query("DELETE FROM contact_stats")
    suspend fun reset()
}

suspend fun ContactStatsDao.recordOpen(chat: String, delayMs: Long) {
    ensure(chat)
    addOpen(chat, delayMs.coerceAtLeast(0))
}

suspend fun ContactStatsDao.recordIgnore(chat: String) {
    ensure(chat)
    addIgnore(chat)
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `contact_stats` (`chat` TEXT NOT NULL, `opens` INTEGER NOT NULL, " +
                "`ignores` INTEGER NOT NULL, `totalOpenDelayMs` INTEGER NOT NULL, PRIMARY KEY(`chat`))"
        )
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `messages` ADD COLUMN `dueAt` INTEGER")
        db.execSQL("ALTER TABLE `messages` ADD COLUMN `dueHasTime` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `messages` ADD COLUMN `feedback` INTEGER")
    }
}

@Database(entities = [Message::class, ContactStats::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messages(): MessageDao
    abstract fun contactStats(): ContactStatsDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "messages.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
