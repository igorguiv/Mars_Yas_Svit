package com.example.marstekwidget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

class WTheme(val dark: Boolean) {
    val bgRes = if (dark) R.drawable.widget_bg_dark else R.drawable.widget_bg
    val cardRes = if (dark) R.drawable.card_bg_dark else R.drawable.card_bg
    val t1 = Color.parseColor(if (dark) "#F3F4F6" else "#111827")
    val t2 = Color.parseColor(if (dark) "#9CA3AF" else "#6B7280")
    val t3 = Color.parseColor(if (dark) "#6B7280" else "#9CA3AF")
    val green = Color.parseColor(if (dark) "#34D399" else "#1FA578")
    val red = Color.parseColor(if (dark) "#F87171" else "#E5484D")
    val amber = Color.parseColor(if (dark) "#FBBF24" else "#B45309")
    val gray = Color.parseColor("#9CA3AF")
    val track = Color.parseColor(if (dark) "#374151" else "#D3D6DD")
    val line = Color.parseColor(if (dark) "#60A5FA" else "#2563EB")

    companion object {
        fun resolve(ctx: Context, mode: Int): WTheme {
            val dark = when (mode) {
                1 -> false
                2 -> true
                else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES
            }
            return WTheme(dark)
        }
    }
}
