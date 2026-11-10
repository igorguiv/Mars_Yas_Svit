package com.example.marstekwidget

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Подія зі Світлобота: світло з'явилося (on) або зникло (off). */
class Ev(val on: Boolean, val ms: Long, val durMin: Int, val planned: String)

object Telegram {

    class Result(val title: String, val events: List<Ev>)

    class Fact(
        val intervals: List<Iv>,
        val onNow: Boolean,
        val sinceMs: Long,
        val lastDurMin: Int,
        val planned: String
    )

    /** Приймає t.me/xxx, telegram.me/xxx, https://t.me/s/xxx або @xxx. */
    fun channelName(input: String): String {
        var s = input.trim()
        s = s.removePrefix("https://").removePrefix("http://")
        for (p in listOf("t.me/", "telegram.me/", "telegram.dog/")) s = s.removePrefix(p)
        s = s.removePrefix("s/").removePrefix("@")
        return s.substringBefore('/').substringBefore('?').trim()
    }

    fun fetch(channel: String): Result {
        val name = channelName(channel)
        if (name.isEmpty()) error("Порожнє посилання на канал")
        val html = Http.request("GET", "https://t.me/s/$name", failOnHttpError = true)
        val title = Regex("""property="og:title"\s+content="([^"]*)"""").find(html)?.groupValues?.get(1)
            ?.let { clean(it).replace("⚡", "").replace("️", "").replace(Regex("\\s+"), " ").trim() } ?: ""
        return Result(title, parse(html))
    }

    private fun clean(raw: String): String {
        var s = raw.replace(Regex("<br\\s*/?>"), " ").replace(Regex("<[^>]+>"), "")
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        s = Regex("&#(\\d+);").replace(s) { m -> m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: "" }
        s = Regex("&#x([0-9a-fA-F]+);").replace(s) { m -> m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: "" }
        return s.replace(Regex("\\s+"), " ").trim()
    }

    private fun parseIso(s: String): Long? = try {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(s)?.time
    } catch (e: Exception) { null }

    fun parse(html: String): List<Ev> {
        val out = mutableListOf<Ev>()
        val main = Regex("(\\d{1,2}):(\\d{2})\\s+Світло\\s+(зникло|з['’ʼ`]?явилося)")
        val dur = Regex("було\\s+(?:(\\d+)\\s*д\\s*)?(?:(\\d+)\\s*год\\s*)?(?:(\\d+)\\s*хв)?")
        val next = Regex("Наступне планове:?\\s*(\\d{1,2}:\\d{2})\\s*[-–—]\\s*(\\d{1,2}:\\d{2})")
        val back = Regex("Включення за графіком о\\s*(\\d{1,2}:\\d{2})")
        val textRx = Regex("tgme_widget_message_text[^>]*>(.*?)</div>", RegexOption.DOT_MATCHES_ALL)
        val timeRx = Regex("<time[^>]*datetime=\"([^\"]+)\"")

        for (chunk in html.split("tgme_widget_message_wrap").drop(1)) {
            val block = textRx.find(chunk)?.groupValues?.get(1) ?: continue
            val text = clean(block)
            val m = main.find(text) ?: continue
            val post = parseIso(timeRx.find(chunk)?.groupValues?.get(1) ?: continue) ?: continue
            val on = !m.groupValues[3].startsWith("зник")
            val h = m.groupValues[1].toInt()
            val mi = m.groupValues[2].toInt()
            val cal = Calendar.getInstance()
            cal.timeInMillis = post
            cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, mi); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            var ms = cal.timeInMillis
            if (ms > post + 10 * 60_000L) ms -= 86_400_000L

            var d = 0
            dur.find(text.substring(m.range.last))?.let { dm ->
                val days = dm.groupValues[1].toIntOrNull() ?: 0
                val hrs = dm.groupValues[2].toIntOrNull() ?: 0
                val min = dm.groupValues[3].toIntOrNull() ?: 0
                d = days * 1440 + hrs * 60 + min
            }
            var planned = ""
            next.find(text)?.let { planned = "наступне планове ${it.groupValues[1]}–${it.groupValues[2]}" }
            if (planned.isEmpty()) back.find(text)?.let { planned = "включення за графіком о ${it.groupValues[1]}" }
            out.add(Ev(on, ms, d, planned))
        }
        return out.sortedBy { it.ms }
    }

    // ---------- зберігання ----------
    fun serialize(list: List<Ev>): String =
        list.joinToString(";") { "${if (it.on) 1 else 0}|${it.ms}|${it.durMin}|${it.planned.replace(";", ",").replace("|", "/")}" }

    fun deserialize(s: String): List<Ev> = s.split(";").mapNotNull {
        val a = it.split("|")
        if (a.size < 4) null else {
            val ms = a[1].toLongOrNull()
            val d = a[2].toIntOrNull()
            if (ms == null || d == null) null else Ev(a[0] == "1", ms, d, a[3])
        }
    }

    fun merge(old: List<Ev>, fresh: List<Ev>, nowMs: Long): List<Ev> {
        val map = LinkedHashMap<Long, Ev>()
        for (e in old) map[e.ms] = e
        for (e in fresh) map[e.ms] = e
        val cutoff = nowMs - 3 * 86_400_000L
        return map.values.filter { it.ms >= cutoff }.sortedBy { it.ms }.takeLast(120)
    }

    /** Фактичні інтервали відсутності світла за сьогодні + поточний стан. */
    fun fact(events: List<Ev>, nowMs: Long, midnightMs: Long): Fact? {
        if (events.isEmpty()) return null
        val nowMin = ((nowMs - midnightMs) / 60000).toInt()
        val ivs = mutableListOf<Iv>()
        fun add(startMs: Long, endMs: Long) {
            val s = ((startMs - midnightMs) / 60000).toInt()
            val e = ((endMs - midnightMs) / 60000).toInt()
            val cs = maxOf(s, 0)
            val ce = minOf(e, nowMin)
            if (ce > cs) ivs.add(Iv(cs, ce))
        }
        for (ev in events) if (ev.on) add(ev.ms - ev.durMin * 60_000L, ev.ms)
        val last = events.last()
        if (!last.on) add(last.ms, nowMs)
        return Fact(Outages.merge(ivs), last.on, last.ms, last.durMin, last.planned)
    }
}
