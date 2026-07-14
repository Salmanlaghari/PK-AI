package com.salmanlaghari.pkai.data.remote

import retrofit2.http.GET

interface ApiService {
    @GET("status")
    suspend fun getApiStatus(): Map<String, String>
}
