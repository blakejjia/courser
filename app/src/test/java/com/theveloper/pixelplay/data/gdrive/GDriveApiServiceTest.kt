package com.theveloper.pixelplay.data.gdrive

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GDriveApiServiceTest {

    @Test
    fun listAudioFiles_usesPageSize1000() = runTest {
        val okHttpClient = mockk<OkHttpClient>()
        val call = mockk<Call>()
        val requestSlot = slot<Request>()

        every { okHttpClient.newCall(capture(requestSlot)) } returns call
        every { call.execute() } answers {
            Response.Builder()
                .request(requestSlot.captured)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"files":[]}""".toResponseBody())
                .build()
        }

        val api = GDriveApiService(okHttpClient)
        api.setAccessToken("test_token")
        api.listAudioFiles("test_folder_id")

        val url = requestSlot.captured.url.toString()
        assertTrue(url.contains("pageSize=1000"), "Expected URL to contain pageSize=1000, but was: $url")
    }

    @Test
    fun listFolders_usesPageSize1000() = runTest {
        val okHttpClient = mockk<OkHttpClient>()
        val call = mockk<Call>()
        val requestSlot = slot<Request>()

        every { okHttpClient.newCall(capture(requestSlot)) } returns call
        every { call.execute() } answers {
            Response.Builder()
                .request(requestSlot.captured)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"files":[]}""".toResponseBody())
                .build()
        }

        val api = GDriveApiService(okHttpClient)
        api.setAccessToken("test_token")
        api.listFolders("root")

        val url = requestSlot.captured.url.toString()
        assertTrue(url.contains("pageSize=1000"), "Expected URL to contain pageSize=1000, but was: $url")
    }

    @Test
    fun executeGet_retriesWithBackoffOnHttp429() = runTest {
        val okHttpClient = mockk<OkHttpClient>()
        val call1 = mockk<Call>()
        val call2 = mockk<Call>()
        val requestSlot = slot<Request>()

        val request = Request.Builder().url("https://www.googleapis.com/drive/v3/files").build()
        val response429 = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(429)
            .message("Too Many Requests")
            .body("Rate limit exceeded".toResponseBody())
            .build()

        val response200 = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("""{"success": true}""".toResponseBody())
            .build()

        every { okHttpClient.newCall(capture(requestSlot)) } returns call1 andThen call2
        every { call1.execute() } returns response429
        every { call2.execute() } returns response200

        val api = GDriveApiService(okHttpClient)
        api.setAccessToken("test_token")
        val result = api.getFileMetadata("file_123")

        assertEquals("""{"success": true}""", result)
    }
}
