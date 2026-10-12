package com.example.gateway

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GatewayClient {

    suspend fun checkGatewayStatus(host: String, port: Int = 8080): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$host:$port/api/status")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
            }
            val code = conn.responseCode
            if (code in 200..299) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                Result.success(response)
            } else {
                conn.disconnect()
                Result.failure(Exception("رمز الاستجابة: $code"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendRemoteSms(host: String, port: Int = 8080, sender: String, body: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://$host:$port/api/sms/ingest")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4000
                readTimeout = 4000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            val escapedBody = body.replace("\"", "\\\"").replace("\n", "\\n")
            val payload = """{"sender":"$sender","body":"$escapedBody"}"""
            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(payload)
                it.flush()
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val resp = stream.bufferedReader().use { it.readText() }
            conn.disconnect()
            if (code in 200..299) {
                Result.success(resp)
            } else {
                Result.failure(Exception("خطأ من الخادم: $resp"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
