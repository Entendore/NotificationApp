package com.example.notificationapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders ORDER BY sortOrder ASC")
    fun getAllFlow(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY sortOrder ASC")
    suspend fun getAll(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Int): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE type = :type")
    suspend fun getByType(type: String): List<ReminderEntity>

    @Insert
    suspend fun insert(r: ReminderEntity): Long

    @Update
    suspend fun update(r: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM reminders WHERE type = :type")
    suspend fun deleteByType(type: String)

    @Query("UPDATE reminders SET time = :time WHERE id = :id")
    suspend fun updateTime(id: Int, time: Long)

    @Query("UPDATE reminders SET sortOrder = :order WHERE id = :id")
    suspend fun updateSortOrder(id: Int, order: Double)

    @Transaction
    suspend fun reindex(items: List<ReminderEntity>) {
        items.forEachIndexed { i, r -> updateSortOrder(r.id, i.toDouble()) }
    }
}
