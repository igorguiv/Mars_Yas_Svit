package com.example.marstekwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object WidgetRenderer {

    private class Flow(
        val status: String, val word: String, val color: Int,
        val etaLabel: String, val eta: String, val power: String
    )

    private class RingInfo(val text: String, val label: String, val off: Boolean?)

    private class Snap(
        val ctx: Context, val p: Prefs, val t: WTheme, val b: Battery?, val error: String?,
        val plan: Plan?, val view: OutageView?, val nowMin: Int, val midnight: Long, val refreshing: Boolean,
        val fact: Telegram.Fact?
    )

    fun updateFromCache(ctx: Context) {
        val p = Prefs(ctx)
        val b = try { if (p.lastRaw.isNotEmpty()) MarstekApi.parse(p.lastRaw) else null } catch (e: Exception) { null }
        updateAll(ctx, b, p.lastError.ifEmpty { null })
    }

    fun updateAll(ctx: Context, b: Battery?, error: String?) {
        val p = Prefs(ctx)
        val t = WTheme.resolve(ctx, p.theme)
        val cal = Calendar.getInstance()
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val mc = cal.clone() as Calendar
        mc.set(Calendar.HOUR_OF_DAY, 0); mc.set(Calendar.MINUTE, 0); mc.set(Calendar.SECOND, 0); mc.set(Calendar.MILLISECOND, 0)
        val plan = try {
            if (p.outageRaw.isNotEmpty()) Outages.plan(Outages.parse(p.outageRaw), cal) else null
        } catch (e: Exception) { null }
        val view = if (b != null && plan != null) Outages.describe(plan, nowMin, b.soc, p.capacity, p.minSoc, p.avgW) else null
        val refreshing = p.refreshStart > 0 && System.currentTimeMillis() - p.refreshStart < 45_000L
        val fact = try {
            Telegram.fact(Telegram.deserialize(p.tgEvents), System.currentTimeMillis(), mc.timeInMillis)
        } catch (e: Exception) { null }
        val s = Snap(ctx, p, t, b, error, plan, view, nowMin, mc.timeInMillis, refreshing, fact)

        push(ctx, WidgetRings::class.java) { rings(s) }
        push(ctx, WidgetBattery::class.java) { battery(s) }
        push(ctx, WidgetLoad::class.java) { load(s) }
        push(ctx, WidgetOutage::class.java) { outage(s) }
        push(ctx, WidgetForecast::class.java) { forecast(s) }
        push(ctx, WidgetChart::class.java) { chart(s) }
        push(ctx, WidgetPlanFact::class.java) { planFact(s) }
    }

    private fun push(ctx: Context, cls: Class<out BaseWidget>, build: () -> RemoteViews) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, cls))
        if (ids.isEmpty()) return
        val v = build()
        val pi = PendingIntent.getBroadcast(
            ctx, cls.name.hashCode(),
            Intent(ctx, cls).setAction(BaseWidget.ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        v.setOnClickPendingIntent(R.id.root, pi)
        v.setOnClickPendingIntent(R.id.ivRefresh, pi)
        for (id in ids) mgr.updateAppWidget(id, v)
    }

    // ---------- допоміжне ----------
    private fun RemoteViews.t(id: Int, text: String, color: Int) {
        setTextViewText(id, text)
        setTextColor(id, color)
    }

    private fun base(s: Snap, layout: Int): RemoteViews {
        val v = RemoteViews(s.ctx.packageName, layout)
        v.setInt(R.id.root, "setBackgroundResource", s.t.bgRes)
        v.setInt(R.id.ivRefresh, "setColorFilter", if (s.refreshing) s.t.line else s.t.t3)
        return v
    }

    private fun fmt(d: Double) = String.format(Locale("uk"), "%.2f", d)

    private fun hm(hours: Double): String {
        if (hours.isNaN() || hours.isInfinite() || hours > 99) return "—"
        return Outages.dur((hours * 60).toInt())
    }

    private fun fmtTs(ms: Long): String {
        val tf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val now = Calendar.getInstance()
        val d = Calendar.getInstance().apply { timeInMillis = ms }
        val sameDay = now.get(Calendar.YEAR) == d.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == d.get(Calendar.DAY_OF_YEAR)
        val y = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterday = y.get(Calendar.YEAR) == d.get(Calendar.YEAR) && y.get(Calendar.DAY_OF_YEAR) == d.get(Calendar.DAY_OF_YEAR)
        return when {
            sameDay -> tf.format(Date(ms))
            yesterday -> "вчора " + tf.format(Date(ms))
            else -> SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(ms))
        }
    }

    private fun timeLine(s: Snap): String {
        if (s.refreshing) return "оновлення…"
        val b = s.b ?: return (s.error ?: "").take(50)
        val data = b.reportTimeMs?.let { fmtTs(it) } ?: "—"
        val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        return "дані $data · віджет $now" + if (s.error != null) " ⚠" else ""
    }

    private fun flow(b: Battery, cap: Double, t: WTheme): Flow {
        val energy = cap * b.soc / 100.0
        return when {
            b.chargeW > 10 -> Flow("⚡ Заряджається", "Заряджається", t.green, "до повного",
                hm((cap - energy) / (b.chargeW / 1000.0)), "+${b.chargeW.toInt()} Вт")
            b.dischargeW > 10 -> Flow("Розряджається", "Розряджається", t.red, "вистачить на",
                hm(energy / (b.dischargeW / 1000.0)), "−${b.dischargeW.toInt()} Вт")
            else -> Flow("Очікування", "Очікування", t.gray, "залишок часу", "—", "0 Вт")
        }
    }

    private fun ringInfo(plan: Plan?, nowMin: Int): RingInfo {
        if (plan == null) return RingInfo("—", "графік недоступний", null)
        if (plan.emergency) return RingInfo("!", "екстрені відключення", true)
        val cur = plan.all.firstOrNull { nowMin >= it.s && nowMin < it.e }
        val next = plan.all.firstOrNull { it.s > nowMin }
        return when {
            cur != null -> RingInfo(Outages.fmtHm(cur.e), "планове увімкнення", true)
            next != null -> RingInfo(Outages.fmtHm(next.s), "наступне відключення", false)
            else -> RingInfo("—", "без відключень", false)
        }
    }

    // ---------- віджети ----------
    private fun rings(s: Snap): RemoteViews {
        val v = base(s, R.layout.widget_rings)
        val t = s.t
        val b = s.b
        if (b == null) {
            v.setImageViewBitmap(R.id.ivRingL, Draw.ring(360, 0.0, t.gray, t.track, "--%", null, t))
            v.t(R.id.tvCapL, "Помилка", t.red)
        } else {
            val f = flow(b, s.p.capacity, t)
            val energy = s.p.capacity * b.soc / 100.0
            v.setImageViewBitmap(R.id.ivRingL, Draw.ring(360, b.soc / 100.0, f.color, t.track, "${b.soc.toInt()}%", "${fmt(energy)} кВт·год", t))
            v.t(R.id.tvCapL, f.word, f.color)
        }
        val r = ringInfo(s.plan, s.nowMin)
        val rc = when (r.off) { true -> t.red; false -> t.green; null -> t.gray }
        v.setImageViewBitmap(R.id.ivRingR, Draw.ring(360, null, rc, t.track, r.text, null, t))
        v.t(R.id.tvCapR, r.label, t.t2)
        return v
    }

    private fun battery(s: Snap): RemoteViews {
        val v = base(s, R.layout.widget_battery)
        val t = s.t
        val b = s.b
        if (b == null) {
            v.t(R.id.tvSoc, "--%", t.t1)
            v.t(R.id.tvEnergy, "", t.t2)
            v.t(R.id.tvTitle, "Venus A", t.t2)
            v.t(R.id.tvStatus, "Помилка", t.red)
            v.setImageViewBitmap(R.id.ivBar, Draw.socBar(1000, 22, 0.0, t))
            v.t(R.id.tvEta, (s.error ?: "").take(40), t.t2)
            v.t(R.id.tvPower, "", t.t1)
        } else {
            val f = flow(b, s.p.capacity, t)
            val energy = s.p.capacity * b.soc / 100.0
            v.t(R.id.tvSoc, "${b.soc.toInt()}%", t.t1)
            v.t(R.id.tvEnergy, "${fmt(energy)} з ${fmt(s.p.capacity)} кВт·год", t.t2)
            v.t(R.id.tvTitle, b.name, t.t2)
            v.t(R.id.tvStatus, f.status, f.color)
            v.setImageViewBitmap(R.id.ivBar, Draw.socBar(1000, 22, b.soc, t))
            v.t(R.id.tvEta, "${f.etaLabel} ${f.eta}", t.t1)
            v.t(R.id.tvPower, f.power, t.t1)
        }
        return v
    }

    private fun load(s: Snap): RemoteViews {
        val v = base(s, R.layout.widget_load)
        val t = s.t
        val b = s.b
        v.t(R.id.tvLoadLabel, "навантаження", t.t2)
        v.t(R.id.tvSocLabel, "заряд станції", t.t2)
        if (b == null) {
            v.t(R.id.tvLoad, "—", t.t1)
            v.t(R.id.tvFlowLabel, "станція", t.t2)
            v.t(R.id.tvFlow, "—", t.t1)
            v.t(R.id.tvSocV, "--%", t.t1)
        } else {
            val f = flow(b, s.p.capacity, t)
            v.t(R.id.tvLoad, if (b.loadW > 0) "${b.loadW.toInt()} Вт" else "—", t.t1)
            v.t(R.id.tvFlowLabel, f.word.lowercase(Locale("uk")), t.t2)
            v.t(R.id.tvFlow, f.power, f.color)
            v.t(R.id.tvSocV, "${b.soc.toInt()}%", t.t1)
        }
        v.t(R.id.tvTime, timeLine(s), t.t3)
        return v
    }

    private fun outage(s: Snap): RemoteViews {
        val v = base(s, R.layout.widget_outage)
        val t = s.t
        v.t(R.id.tvOutTitle, "Відключення сьогодні", t.t2)
        if (s.plan == null) {
            v.t(R.id.tvGroup, "", t.amber)
            v.setViewVisibility(R.id.ivTimeline, View.GONE)
            val msg = if (s.p.group.isBlank()) "Оберіть групу в застосунку"
            else if (s.p.outageErr.isNotEmpty()) "Графік недоступний: " + s.p.outageErr.take(50)
            else "Графік ще не завантажено"
            v.t(R.id.tvOutNext, msg, t.t2)
        } else {
            v.t(R.id.tvGroup, "група ${Outages.normGroup(s.p.group)}", t.amber)
            v.setViewVisibility(R.id.ivTimeline, View.VISIBLE)
            v.setImageViewBitmap(R.id.ivTimeline, Draw.timeline(1000, s.plan.today, s.nowMin, t))
            val text = Outages.describe(s.plan, s.nowMin, s.b?.soc ?: 0.0, s.p.capacity, s.p.minSoc, s.p.avgW).nextText
            v.t(R.id.tvOutNext, text, t.t1)
        }
        return v
    }

    private fun forecast(s: Snap): RemoteViews {
        val v = base(s, R.layout.widget_forecast)
        val t = s.t
        val w = s.view
        if (w == null) {
            val msg = if (s.plan == null) "Прогноз недоступний" else "Немає даних станції"
            v.t(R.id.tvVerdict, msg, t.t2)
            v.t(R.id.tvFcLabel, "", t.t2); v.t(R.id.tvForecast, "", t.t1)
            v.t(R.id.tvRsLabel, "", t.t2); v.t(R.id.tvReserve, "", t.t1)
            v.t(R.id.tvAssump, if (s.plan == null) "потрібні група відключень і дані станції" else "", t.t3)
        } else if (w.verdict == null) {
            v.t(R.id.tvVerdict, w.nextText, t.amber)
            v.t(R.id.tvFcLabel, "", t.t2); v.t(R.id.tvForecast, "", t.t1)
            v.t(R.id.tvRsLabel, "", t.t2); v.t(R.id.tvReserve, "", t.t1)
            v.t(R.id.tvAssump, "", t.t3)
        } else {
            v.t(R.id.tvVerdict, w.verdict, if (w.verdictOk) t.green else t.red)
            v.t(R.id.tvFcLabel, w.fcLabel, t.t2)
            v.t(R.id.tvForecast, w.fcValue, t.t1)
            v.t(R.id.tvRsLabel, w.rsLabel, t.t2)
            v.t(R.id.tvReserve, w.rsValue, t.t1)
            v.t(R.id.tvAssump, w.assump, t.t3)
        }
        return v
    }

    private fun chart(s: Snap): RemoteViews {
        val v = base(s, R.layout.widget_chart)
        val t = s.t
        v.t(R.id.tvChartTitle, "Заряд за добу", t.t2)
        v.t(R.id.tvTime, timeLine(s), t.t3)
        v.setImageViewBitmap(
            R.id.ivChart,
            Draw.chart(1000, 150, Refresher.parseHistory(s.p.history), s.plan?.all ?: emptyList(),
                s.midnight, System.currentTimeMillis(), t)
        )
        return v
    }

    private fun planFact(s: Snap): RemoteViews {
        val v = base(s, R.layout.widget_planfact)
        val t = s.t
        v.setInt(R.id.divider, "setBackgroundColor", t.track)

        // --- план (Yasno) ---
        v.t(R.id.tvPlanTitle, "План · Yasno", t.t2)
        if (s.plan == null) {
            v.t(R.id.tvPlanGroup, "", t.amber)
            v.setViewVisibility(R.id.ivPlan, View.GONE)
            val msg = if (s.p.group.isBlank()) "Оберіть регіон і групу в застосунку"
            else if (s.p.outageErr.isNotEmpty()) "Графік недоступний: " + s.p.outageErr.take(50)
            else "Графік ще не завантажено"
            v.t(R.id.tvPlanNext, msg, t.t2)
        } else {
            v.t(R.id.tvPlanGroup, "${s.p.regionLabel} · група ${Outages.normGroup(s.p.group)}", t.amber)
            v.setViewVisibility(R.id.ivPlan, View.VISIBLE)
            v.setImageViewBitmap(R.id.ivPlan, Draw.timeline(1000, s.plan.today, s.nowMin, t))
            val txt = Outages.describe(s.plan, s.nowMin, s.b?.soc ?: 0.0, s.p.capacity, s.p.minSoc, s.p.avgW).nextText
            v.t(R.id.tvPlanNext, txt, t.t1)
        }

        // --- факт (СвітлоБот) ---
        v.t(R.id.tvFactTitle, "Факт · СвітлоБот", t.t2)
        val ch = Telegram.channelName(s.p.channel)
        v.t(R.id.tvFactName, if (s.p.tgTitle.isNotBlank()) s.p.tgTitle else if (ch.isNotBlank()) "@$ch" else "", t.t2)
        val f = s.fact
        if (ch.isBlank()) {
            v.setViewVisibility(R.id.ivFact, View.GONE)
            v.t(R.id.tvFactDot, "", t.t2)
            v.t(R.id.tvFactStatus, "Додайте посилання на канал СвітлоБота в застосунку", t.t2)
        } else if (f == null) {
            v.setViewVisibility(R.id.ivFact, View.GONE)
            v.t(R.id.tvFactDot, "", t.t2)
            v.t(R.id.tvFactStatus, if (s.p.tgErr.isNotEmpty()) "СвітлоБот недоступний: " + s.p.tgErr.take(60) else "Подій ще немає", t.t2)
        } else {
            v.setViewVisibility(R.id.ivFact, View.VISIBLE)
            v.setImageViewBitmap(R.id.ivFact, Draw.factTimeline(1000, f.intervals, s.nowMin, t))
            val since = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(f.sinceMs))
            val plannedPart = if (f.planned.isNotBlank()) " · " + f.planned else ""
            val txt = if (f.onNow) {
                "Світло є з $since" + (if (f.lastDurMin > 0) " · не було " + Outages.dur(f.lastDurMin) else "") + plannedPart
            } else {
                val mins = ((System.currentTimeMillis() - f.sinceMs) / 60000).toInt()
                "Світла немає з $since · вже " + Outages.dur(mins) + plannedPart
            }
            v.t(R.id.tvFactDot, "●", if (f.onNow) t.green else t.red)
            v.t(R.id.tvFactStatus, txt, t.t1)
        }
        v.t(R.id.tvTime, if (s.refreshing) "оновлення…" else "оновлено " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), t.t3)
        return v
    }
}
