package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QrDao {
    @Query("SELECT * FROM qr_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<QrItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: QrItem): Long

    @Query("DELETE FROM qr_history WHERE id = :id")
    suspend fun deleteItemById(id: Long)

    @Query("DELETE FROM qr_history")
    suspend fun clearAllHistory()
}
