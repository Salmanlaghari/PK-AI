package com.salmanlaghari.pkai.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

data class HackerEarthSubmissionRequest(
    val source: String,
    val lang: String,
    val input: String = "",
    val memory_limit: Int = 262144,
    val time_limit: Int = 5
)

data class HackerEarthRequestStatus(
    val code: String?,
    val message: String?
)

data class HackerEarthRunStatus(
    val status: String?,
    val output: String?,
    val stderr: String?,
    val time_used: Double?,
    val memory_used: Long?
)

data class HackerEarthResult(
    val compile_status: String?,
    val run_status: HackerEarthRunStatus?
)

data class HackerEarthSubmissionResponse(
    val he_id: String?,
    val request_status: HackerEarthRequestStatus?,
    val result: HackerEarthResult?,
    val status_update_url: String?,
    val message: String?,
    val errors: Map<String, Any>?
)

data class ProxyRunCodeResponse(
    val he_id: String?,
    val request_status: HackerEarthRequestStatus?,
    val compile_status: String?,
    val run_status: String?,
    val stdout: String?,
    val stderr: String?,
    val time_used: Double?,
    val memory_used: Long?,
    val error: Any?
)

interface HackerEarthApiService {
    @POST("v4/partner/code-evaluation/submissions/")
    suspend fun submitCode(
        @Header("client-secret") clientSecret: String,
        @Body request: HackerEarthSubmissionRequest
    ): Response<HackerEarthSubmissionResponse>

    @GET
    suspend fun getSubmissionStatus(
        @Url statusUrl: String,
        @Header("client-secret") clientSecret: String
    ): Response<HackerEarthSubmissionResponse>

    @GET
    suspend fun fetchOutputText(
        @Url outputUrl: String
    ): Response<ResponseBody>

    @POST
    suspend fun runViaProxy(
        @Url proxyUrl: String,
        @Body request: HackerEarthSubmissionRequest
    ): Response<ProxyRunCodeResponse>
}
