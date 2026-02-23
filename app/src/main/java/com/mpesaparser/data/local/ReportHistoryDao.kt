package com.mpesaparser.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ReportHistoryEntity)

    @Query("SELECT * FROM report_history ORDER BY createdAtMillis DESC LIMIT 50")
    fun observeRecent(): Flow<List<ReportHistoryEntity>>

    @Query("DELETE FROM report_history WHERE id NOT IN (SELECT id FROM report_history ORDER BY createdAtMillis DESC LIMIT 200)")
    suspend fun trimToRecent()
}
