package com.example.marstekwidget

import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest

data class Battery(
    val name: String,
    val soc: Double,
    val chargeW: Double,
    val dischargeW: Double,
    val loadW: Double,
    val reportTimeMs: Long?,
    val raw: String
)

object MarstekApi {
    private const val BASE = "https://eu.hamedata.com"

    private fun md5(s: String) =
        MessageDigest.getInstance("MD5").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun login(email: String, password: String): String {
        val url = "$BASE/app/Solar/v2_get_device.php?pwd=${md5(password)}&mailbox=${enc(email)}"
        val j = JSONObject(Http.request("POST", url))
        val token = j.optString("token", "")
        if (token.isEmpty()) error("Вхід не вдався: " + j.optString("msg", j.toString().take(120)))
        return token
    }

    fun fetch(email: String, token: String): Battery {
        val url = "$BASE/ems/api/v1/getDeviceList?mailbox=${enc(email)}&token=${enc(token)}"
        return parse(Http.request("GET", url))
    }

    fun parse(raw: String): Battery {
        val j = JSONObject(raw)
        val arr = j.optJSONArray("data")
        if (arr == null || arr.length() == 0) error("Немає даних: " + j.optString("msg", raw.take(120)))
        val d = arr.getJSONObject(0)
        fun num(k: String) = d.optString(k, "").toDoubleOrNull() ?: 0.0
        val rt = d.optString("report_time", "").toDoubleOrNull()?.toLong()?.let {
            if (it < 100_000_000_000L) it * 1000 else it
        }
        val name = d.optString("name", "").ifEmpty { "Venus A" }
        return Battery(name, num("soc"), num("charge"), num("discharge"), num("load"), rt, raw)
    }
}
