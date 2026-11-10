package com.example.marstekwidget

import android.content.Context

object Refresher {

    /** Мережеві запити + оновлення всіх віджетів. Викликати НЕ з головного потоку. */
    fun refresh(ctx: Context) {
        val p = Prefs(ctx)
        var battery: Battery? = null
        var err: String? = null
        try {
            if (p.email.isBlank() || p.password.isBlank()) error("Введіть логін і пароль у застосунку")
            val b = try {
                if (p.token.isBlank()) error("no token")
                MarstekApi.fetch(p.email, p.token)
            } catch (e: Exception) {
                p.token = MarstekApi.login(p.email, p.password)
                MarstekApi.fetch(p.email, p.token)
            }
            battery = b
            p.lastRaw = b.raw
            p.lastOk = System.currentTimeMillis()
            p.lastError = ""
            addHistory(p, b.soc)
        } catch (e: Exception) {
            err = e.message ?: e.toString()
            p.lastError = err
            battery = try { if (p.lastRaw.isNotEmpty()) MarstekApi.parse(p.lastRaw) else null } catch (_: Exception) { null }
        }

        if (p.group.isNotBlank()) {
            try {
                p.outageRaw = Outages.fetch(p.group, p.regionId, p.dsoId)
                p.outageErr = ""
            } catch (e: Exception) {
                p.outageErr = e.message ?: e.toString()
            }
        }
        if (p.channel.isNotBlank()) {
            try {
                val r = Telegram.fetch(p.channel)
                if (r.title.isNotBlank()) p.tgTitle = r.title
                val merged = Telegram.merge(Telegram.deserialize(p.tgEvents), r.events, System.currentTimeMillis())
                p.tgEvents = Telegram.serialize(merged)
                p.tgErr = if (r.events.isEmpty()) "У каналі не знайдено повідомлень про світло" else ""
            } catch (e: Exception) {
                p.tgErr = e.message ?: e.toString()
            }
        }
        p.refreshStart = 0L
        WidgetRenderer.updateAll(ctx, battery, err)
    }

    fun parseHistory(s: String): List<Pair<Long, Double>> = s.split(";").mapNotNull {
        val a = it.split(",")
        if (a.size == 2) {
            val t = a[0].toLongOrNull(); val v = a[1].toDoubleOrNull()
            if (t != null && v != null) t to v else null
        } else null
    }

    private fun addHistory(p: Prefs, soc: Double) {
        val now = System.currentTimeMillis()
        val list = parseHistory(p.history).toMutableList()
        if (list.isEmpty() || now - list.last().first > 4 * 60_000L) list.add(now to soc)
        val cutoff = now - 25 * 3600_000L
        p.history = list.filter { it.first >= cutoff }.takeLast(300).joinToString(";") { "${it.first},${it.second}" }
    }
}
