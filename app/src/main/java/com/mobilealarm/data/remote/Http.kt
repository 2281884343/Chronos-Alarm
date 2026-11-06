package com.mobilealarm.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 极简 HTTP GET，失败时抛出带中文说明的 IOException。 */
suspend fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String =
    withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "MobileAlarm/1.0 (Android)")
                headers.forEach { (key, value) -> setRequestProperty(key, value) }
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }?.take(300)
                throw IOException("网络请求失败（HTTP $code）${detail?.let { "：$it" } ?: ""}")
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException("网络请求失败：${error.message ?: error.javaClass.simpleName}", error)
        } finally {
            connection?.disconnect()
        }
    }