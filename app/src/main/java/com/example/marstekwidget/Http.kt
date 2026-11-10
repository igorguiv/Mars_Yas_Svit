package com.example.marstekwidget

import java.net.HttpURLConnection
import java.net.URL

object Http {
    fun request(method: String, url: String, failOnHttpError: Boolean = false): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15000
        c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) MarstekWidget")
        c.setRequestProperty("Accept", "application/json")
        if (method == "POST") {
            c.doOutput = true
            c.outputStream.use { }
        }
        val code = c.responseCode
        if (failOnHttpError && code >= 400) error("HTTP $code")
        val stream = if (code < 400) c.inputStream else c.errorStream
        return stream.bufferedReader().use { it.readText() }
    }
}
