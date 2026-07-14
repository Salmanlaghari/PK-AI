package com.salmanlaghari.pkai.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AppLogDao {
    @Insert
    suspend fun insertLog(log: AppLog)

    @Query("SELECT * FROM app_logs ORDER BY timestamp DESC")
    suspend fun getAllLogs(): List<AppLog>
}
