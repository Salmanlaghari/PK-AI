package com.salmanlaghari.pkai.data.repository

import com.salmanlaghari.pkai.data.local.room.AppLog
import com.salmanlaghari.pkai.data.local.room.AppLogDao
import com.salmanlaghari.pkai.data.remote.ApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepository @Inject constructor(
    private val apiService: ApiService,
    private val appLogDao: AppLogDao
) {
    suspend fun getStatus(): Map<String, String> {
        return try {
            apiService.getApiStatus()
        } catch (e: Exception) {
            mapOf("status" to "offline", "error" to (e.message ?: "Unknown error"))
        }
    }

    suspend fun addLog(message: String) {
        appLogDao.insertLog(AppLog(message = message))
    }

    suspend fun getLogs(): List<AppLog> {
        return appLogDao.getAllLogs()
    }
}
