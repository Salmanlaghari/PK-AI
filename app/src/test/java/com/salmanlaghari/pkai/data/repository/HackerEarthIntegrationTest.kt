package com.salmanlaghari.pkai.data.repository

import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import com.salmanlaghari.pkai.data.remote.HackerEarthApiService
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.Properties

class HackerEarthIntegrationTest {

    private lateinit var apiService: HackerEarthApiService
    private lateinit var mockPreferencesManager: PreferencesManager
    private lateinit var repository: CodeRunnerRepository
    private var clientSecret: String = ""

    @Before
    fun setUp() {
        // Load secret dynamically from environment or local.properties (non-committed)
        val localProps = Properties()
        val propFile = File("local.properties")
        if (propFile.exists()) {
            propFile.inputStream().use { localProps.load(it) }
        }
        clientSecret = System.getenv("HACKEREARTH_CLIENT_SECRET")
            ?: localProps.getProperty("HACKEREARTH_CLIENT_SECRET")
            ?: ""

        val okHttpClient = OkHttpClient.Builder().build()
        apiService = Retrofit.Builder()
            .baseUrl("https://api.hackerearth.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HackerEarthApiService::class.java)

        mockPreferencesManager = mock(PreferencesManager::class.java)
        runBlocking {
            `when`(mockPreferencesManager.getCodeRunCount()).thenReturn(0)
        }

        repository = CodeRunnerRepository(apiService, mockPreferencesManager)
    }

    @Test
    fun testPythonExecution() = runBlocking {
        if (clientSecret.isBlank()) return@runBlocking
        val pythonCode = "print('Hello Python from PK AI!')"
        val request = com.salmanlaghari.pkai.data.remote.HackerEarthSubmissionRequest(
            source = pythonCode,
            lang = "PYTHON3_8"
        )
        val submitRes = apiService.submitCode(clientSecret, request)
        assertTrue(submitRes.isSuccessful)

        val heId = submitRes.body()?.he_id ?: ""
        val statusUrl = submitRes.body()?.status_update_url ?: ""
        assertTrue(heId.isNotBlank())

        // Poll
        var statusBody = submitRes.body()
        for (i in 0 until 10) {
            val code = statusBody?.request_status?.code
            if (code == "REQUEST_COMPLETED" || code == "CODE_COMPILED") break
            kotlinx.coroutines.delay(1000)
            val poll = apiService.getSubmissionStatus(statusUrl, clientSecret)
            if (poll.isSuccessful) statusBody = poll.body()
        }

        val outputUrl = statusBody?.result?.run_status?.output
        assertTrue(!outputUrl.isNullOrBlank())

        val outRes = apiService.fetchOutputText(outputUrl!!)
        assertTrue(outRes.isSuccessful)
        val stdout = outRes.body()?.string().orEmpty()
        assertTrue("Stdout was: $stdout", stdout.contains("Hello Python from PK AI!"))
    }

    @Test
    fun testJavaExecution() = runBlocking {
        if (clientSecret.isBlank()) return@runBlocking
        val javaCode = "public class Main { public static void main(String[] args) { System.out.println(\"Hello Java from PK AI!\"); } }"
        val request = com.salmanlaghari.pkai.data.remote.HackerEarthSubmissionRequest(
            source = javaCode,
            lang = "JAVA17"
        )
        val submitRes = apiService.submitCode(clientSecret, request)
        assertTrue(submitRes.isSuccessful)

        val statusUrl = submitRes.body()?.status_update_url ?: ""

        var statusBody = submitRes.body()
        for (i in 0 until 10) {
            val code = statusBody?.request_status?.code
            if (code == "REQUEST_COMPLETED" || code == "CODE_COMPILED") break
            kotlinx.coroutines.delay(1000)
            val poll = apiService.getSubmissionStatus(statusUrl, clientSecret)
            if (poll.isSuccessful) statusBody = poll.body()
        }

        val outputUrl = statusBody?.result?.run_status?.output
        assertTrue(!outputUrl.isNullOrBlank())

        val outRes = apiService.fetchOutputText(outputUrl!!)
        val stdout = outRes.body()?.string().orEmpty()
        assertTrue("Stdout was: $stdout", stdout.contains("Hello Java from PK AI!"))
    }

    @Test
    fun testCppExecution() = runBlocking {
        if (clientSecret.isBlank()) return@runBlocking
        val cppCode = "#include <iostream>\nint main() { std::cout << \"Hello C++ from PK AI!\" << std::endl; return 0; }"
        val request = com.salmanlaghari.pkai.data.remote.HackerEarthSubmissionRequest(
            source = cppCode,
            lang = "CPP17"
        )
        val submitRes = apiService.submitCode(clientSecret, request)
        assertTrue(submitRes.isSuccessful)

        val statusUrl = submitRes.body()?.status_update_url ?: ""

        var statusBody = submitRes.body()
        for (i in 0 until 10) {
            val code = statusBody?.request_status?.code
            if (code == "REQUEST_COMPLETED" || code == "CODE_COMPILED") break
            kotlinx.coroutines.delay(1000)
            val poll = apiService.getSubmissionStatus(statusUrl, clientSecret)
            if (poll.isSuccessful) statusBody = poll.body()
        }

        val outputUrl = statusBody?.result?.run_status?.output
        assertTrue(!outputUrl.isNullOrBlank())

        val outRes = apiService.fetchOutputText(outputUrl!!)
        val stdout = outRes.body()?.string().orEmpty()
        assertTrue("Stdout was: $stdout", stdout.contains("Hello C++ from PK AI!"))
    }

    @Test
    fun testBrokenCodeCompileError() = runBlocking {
        if (clientSecret.isBlank()) return@runBlocking
        val brokenCppCode = "#include <iostream>\nint main() { std::cout << \"missing semicolon\" }"
        val request = com.salmanlaghari.pkai.data.remote.HackerEarthSubmissionRequest(
            source = brokenCppCode,
            lang = "CPP17"
        )
        val submitRes = apiService.submitCode(clientSecret, request)
        assertTrue(submitRes.isSuccessful)

        val statusUrl = submitRes.body()?.status_update_url ?: ""

        var statusBody = submitRes.body()
        for (i in 0 until 10) {
            val code = statusBody?.request_status?.code
            if (code == "REQUEST_COMPLETED" || code == "CODE_COMPILED") break
            kotlinx.coroutines.delay(1000)
            val poll = apiService.getSubmissionStatus(statusUrl, clientSecret)
            if (poll.isSuccessful) statusBody = poll.body()
        }

        val compileErr = statusBody?.result?.compile_status.orEmpty()
        assertTrue("Compile error details: $compileErr", compileErr.contains("expected ';'"))
    }
}
