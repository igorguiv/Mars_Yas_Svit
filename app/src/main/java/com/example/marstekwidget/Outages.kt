package com.example.marstekwidget

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Iv(val s: Int, val e: Int)
data class RegionDso(val regionId: Int, val dsoId: Int, val label: String)
data class Day(val date: String, val status: String, val slots: List<Iv>)
class Plan(val today: List<Iv>, val all: List<Iv>, val emergency: Boolean)

class OutageView(
    val nextText: String,
    val verdict: String?,
    val verdictOk: Boolean,
    val fcLabel: String,
    val fcValue: String,
    val rsLabel: String,
    val rsValue: String,
    val assump: String
)

object Outages {
    private const val BASE = "https://app.yasno.ua/api/blackout-service/public/shutdowns"

    private fun url(regionId: Int, dsoId: Int) = "$BASE/regions/$regionId/dsos/$dsoId/planned-outages"

    fun normGroup(g: String): String {
        val t = g.trim()
        return if (t.contains('.')) t else "$t.1"
    }

    /** Повертає JSON лише для вашої групи. */
    fun fetch(group: String, regionId: Int = 25, dsoId: Int = 902): String {
        val root = JSONObject(Http.request("GET", url(regionId, dsoId), failOnHttpError = true))
        val key = normGroup(group)
        val g = root.optJSONObject(key)
            ?: error("Групу $key не знайдено. Є: " + root.keys().asSequence().take(8).joinToString())
        return g.toString()
    }

    /** Список груп, які віддає Yasno для регіону. */
    fun groupKeys(regionId: Int, dsoId: Int): List<String> {
        val root = JSONObject(Http.request("GET", url(regionId, dsoId), failOnHttpError = true))
        val keys = root.keys().asSequence().filter { root.optJSONObject(it) != null }.toList()
        return keys.sortedWith(Comparator { a, b ->
            val pa = a.split('.').map { it.toIntOrNull() ?: 0 }
            val pb = b.split('.').map { it.toIntOrNull() ?: 0 }
            var r = 0
            for (i in 0 until maxOf(pa.size, pb.size)) {
                r = (pa.getOrElse(i) { 0 }).compareTo(pb.getOrElse(i) { 0 })
                if (r != 0) break
            }
            r
        })
    }

    /** Регіони та оператори (ДСО), які обслуговує Yasno. */
    fun regions(): List<RegionDso> {
        val raw = Http.request("GET", "$BASE/regions", failOnHttpError = true).trim()
        val arr: JSONArray = if (raw.startsWith("[")) JSONArray(raw) else {
            val o = JSONObject(raw)
            o.optJSONArray("data") ?: o.optJSONArray("regions") ?: error("Незрозуміла відповідь: " + raw.take(100))
        }
        val out = mutableListOf<RegionDso>()
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            val rid = r.optInt("id", -1)
            if (rid < 0) continue
            val rname = r.optString("value", r.optString("name", ""))
            val dsos = r.optJSONArray("dsos") ?: continue
            for (j in 0 until dsos.length()) {
                val d = dsos.optJSONObject(j) ?: continue
                val did = d.optInt("id", -1)
                if (did < 0) continue
                out.add(RegionDso(rid, did, rname + " · " + d.optString("name", "")))
            }
        }
        return out
    }

    fun parse(groupJson: String): List<Day> {
        val o = JSONObject(groupJson)
        val out = mutableListOf<Day>()
        for (k in listOf("today", "tomorrow")) {
            val d = o.optJSONObject(k) ?: continue
            val arr = d.optJSONArray("slots")
            val raw = mutableListOf<Triple<Double, Double, String>>()
            if (arr != null) for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                raw.add(Triple(s.optDouble("start", 0.0), s.optDouble("end", 0.0), s.optString("type", "")))
            }
            val maxEnd = raw.maxOfOrNull { it.second } ?: 0.0
            val mul = if (maxEnd in 0.1..24.0) 60.0 else 1.0
            val slots = raw.filter { it.third.equals("Definite", true) }
                .map { Iv((it.first * mul).toInt(), (it.second * mul).toInt()) }
            out.add(Day(d.optString("date", ""), d.optString("status", ""), slots))
        }
        return out
    }

    fun merge(list: List<Iv>): List<Iv> {
        val sorted = list.sortedBy { it.s }
        val res = mutableListOf<Iv>()
        for (iv in sorted) {
            val last = res.lastOrNull()
            if (last != null && iv.s <= last.e) res[res.size - 1] = Iv(last.s, maxOf(last.e, iv.e))
            else res.add(iv)
        }
        return res
    }

    fun plan(days: List<Day>, now: Calendar): Plan? {
        if (days.isEmpty()) return null
        val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayStr = f.format(now.time)
        val tomStr = f.format(Date(now.timeInMillis + 86_400_000L))
        var today = days.firstOrNull { it.date.startsWith(todayStr) }
        var tomorrow = days.firstOrNull { it.date.startsWith(tomStr) }
        if (today == null && days.none { it.date.isNotEmpty() }) {
            today = days.getOrNull(0); tomorrow = days.getOrNull(1)
        }
        if (today == null) return null
        val all = merge(today.slots + (tomorrow?.slots ?: emptyList()).map { Iv(it.s + 1440, it.e + 1440) })
        return Plan(merge(today.slots), all, today.status.contains("Emergency", true))
    }

    fun fmtHm(x: Int): String = if (x == 1440) "24:00" else String.format(Locale.US, "%02d:%02d", (x % 1440) / 60, x % 60)

    fun dur(min: Int): String {
        val m = min.coerceAtLeast(0)
        return if (m < 60) "$m хв" else "${m / 60} год ${m % 60} хв"
    }

    fun describe(plan: Plan, nowMin: Int, soc: Double, cap: Double, minSoc: Double, avgW: Double): OutageView {
        val cur = plan.all.firstOrNull { nowMin >= it.s && nowMin < it.e }
        val next = plan.all.firstOrNull { it.s > nowMin }
        val nextText = when {
            plan.emergency -> "Екстрені відключення — графік не діє"
            cur != null -> "Світла немає до ${fmtHm(cur.e)} · ще ${dur(cur.e - nowMin)}"
            next != null -> "Світло є · наступне ${fmtHm(next.s)}–${fmtHm(next.e)} · через ${dur(next.s - nowMin)}"
            else -> "Світло є · відключень більше не заплановано"
        }
        if (plan.emergency || avgW <= 0 || cap <= 0) return OutageView(nextText, null, true, "", "", "", "", "")

        var remain = 0
        for (iv in plan.all) {
            val s = maxOf(iv.s, nowMin)
            val e = minOf(iv.e, 1440)
            if (e > s) remain += e - s
        }
        val loadKw = avgW / 1000.0
        val usable = (cap * (soc - minSoc) / 100.0).coerceAtLeast(0.0)
        val runMin = (usable / loadKw * 60).toInt()
        val need = loadKw * remain / 60.0
        val endSoc = (soc - need / cap * 100.0).coerceAtLeast(0.0)
        val assump = "витрата ≈ ${avgW.toInt()} Вт · без підзарядки · мін. ${minSoc.toInt()}%"

        return if (remain == 0) {
            OutageView(nextText, "ДО 24:00 ВІДКЛЮЧЕНЬ НЕМАЄ", true,
                "заряд зараз", "${soc.toInt()}%", "вистачить на", dur(runMin), assump)
        } else if (usable >= need) {
            OutageView(nextText, "✓ ЗА ПЛАНОМ ВИСТАЧИТЬ", true,
                "заряд до 24:00", "≈ ${endSoc.toInt()}%", "запас часу", dur(runMin - remain), assump)
        } else {
            OutageView(nextText, "✗ НЕ ВИСТАЧИТЬ", false,
                "заряд до 24:00", "мін.", "не вистачить на", dur(remain - runMin), assump)
        }
    }
}
