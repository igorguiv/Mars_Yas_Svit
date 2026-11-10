package com.example.marstekwidget

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

object YasnoAddress {
    private const val B = "https://app.yasno.ua/api/blackout-service/public/shutdowns/addresses/v2"

    class Item(val id: String, val name: String)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun streets(regionId: Int, dsoId: Int, query: String): List<Item> =
        items(Http.request("GET", "$B/streets?regionId=$regionId&dsoId=$dsoId&query=${enc(query)}", true))

    fun houses(regionId: Int, dsoId: Int, streetId: String, query: String): List<Item> =
        items(Http.request("GET", "$B/houses?regionId=$regionId&streetId=${enc(streetId)}&dsoId=$dsoId&query=${enc(query)}", true))

    fun group(regionId: Int, dsoId: Int, streetId: String, houseId: String): String {
        val raw = Http.request("GET", "$B/group?regionId=$regionId&streetId=${enc(streetId)}&houseId=${enc(houseId)}&dsoId=$dsoId", true)
        val t = raw.trim()
        if (!t.startsWith("{")) return Outages.normGroup(t.trim('"'))
        var o = JSONObject(t)
        o.optJSONObject("data")?.let { o = it }
        val g = o.opt("group")?.toString()?.trim().orEmpty()
        if (g.isEmpty()) error("Незрозуміла відповідь: " + t.take(120))
        if (g.contains('.')) return g
        val sub = o.opt("subgroup")?.toString()?.trim().orEmpty()
        return if (sub.isNotEmpty()) "$g.$sub" else "$g.1"
    }

    private fun items(raw: String): List<Item> {
        val t = raw.trim()
        val arr: JSONArray = if (t.startsWith("[")) JSONArray(t) else {
            val o = JSONObject(t)
            o.optJSONArray("data") ?: o.optJSONArray("items") ?: o.optJSONArray("results")
                ?: error("Незрозуміла відповідь: " + t.take(120))
        }
        val out = mutableListOf<Item>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = listOf("id", "streetId", "houseId").map { o.opt(it)?.toString().orEmpty() }.firstOrNull { it.isNotEmpty() } ?: continue
            val name = listOf("name", "value", "title", "label", "fullName").map { o.optString(it, "") }.firstOrNull { it.isNotEmpty() } ?: id
            out.add(Item(id, name))
        }
        return out
    }
}
