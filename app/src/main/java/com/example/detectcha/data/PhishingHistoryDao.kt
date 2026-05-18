package com.example.detectcha.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PhishingHistoryDao {
    @Insert
    suspend fun insert(history: PhishingHistory)

    @Query("SELECT * FROM phishing_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<PhishingHistory>>

    @Query("DELETE FROM phishing_history")
    suspend fun deleteAll()

    @Query("DELETE FROM phishing_history WHERE id = :id")
    suspend fun deleteById(id: Long)
}
