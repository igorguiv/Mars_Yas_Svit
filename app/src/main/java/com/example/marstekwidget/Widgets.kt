package com.example.marstekwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

open class BaseWidget : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        UpdateWorker.schedule(context)
        UpdateWorker.runNow(context)
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        try { WidgetRenderer.updateFromCache(context) } catch (_: Exception) { }
        UpdateWorker.schedule(context)
        UpdateWorker.runNow(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            Prefs(context).refreshStart = System.currentTimeMillis()
            try { WidgetRenderer.updateFromCache(context) } catch (_: Exception) { }
            UpdateWorker.runNow(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.example.marstekwidget.REFRESH"
    }
}

class WidgetBattery : BaseWidget()
class WidgetLoad : BaseWidget()
class WidgetOutage : BaseWidget()
class WidgetForecast : BaseWidget()
class WidgetChart : BaseWidget()
class WidgetRings : BaseWidget()
class WidgetPlanFact : BaseWidget()
