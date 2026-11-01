package com.gamewatchdog

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Một lần chạm: toạ độ theo % màn hình, và thời gian chờ (ms) trước khi chạm. */
data class TapStep(val xPct: Float, val yPct: Float, val delayMs: Long)

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("watchdog", Context.MODE_PRIVATE)

    fun getPackage(c: Context): String = sp(c).getString("pkg", "") ?: ""
    fun setPackage(c: Context, v: String) = sp(c).edit().putString("pkg", v).apply()

    fun getGraceSec(c: Context): Int = sp(c).getInt("grace", 5)
    fun setGraceSec(c: Context, v: Int) = sp(c).edit().putInt("grace", v).apply()

    fun getStartupSec(c: Context): Int = sp(c).getInt("startup", 25)
    fun setStartupSec(c: Context, v: Int) = sp(c).edit().putInt("startup", v).apply()

    fun getTaps(c: Context): String = sp(c).getString("taps", "") ?: ""
    fun setTaps(c: Context, v: String) = sp(c).edit().putString("taps", v).apply()

    fun isEnabled(c: Context): Boolean = sp(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit()
        .putBoolean("enabled", v)
        .putLong("enabledAt", System.currentTimeMillis())
        .apply()

    fun enabledAt(c: Context): Long = sp(c).getLong("enabledAt", 0L)

    fun getLog(c: Context): String = sp(c).getString("log", "") ?: ""

    @Synchronized
    fun log(c: Context, msg: String) {
        val t = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val lines = (getLog(c).lines().filter { it.isNotBlank() } + "$t  $msg").takeLast(40)
        sp(c).edit().putString("log", lines.joinToString("\n")).apply()
    }

    /** Mỗi dòng: x,y,chờ_ms   (x,y là % màn hình, 0–100). Dòng bắt đầu bằng # là ghi chú. */
    fun parseTaps(text: String): List<TapStep> =
        text.lines().mapNotNull { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@mapNotNull null
            val p = line.split(",").map { it.trim() }
            if (p.size < 2) return@mapNotNull null
            val x = p[0].toFloatOrNull() ?: return@mapNotNull null
            val y = p[1].toFloatOrNull() ?: return@mapNotNull null
            val d = p.getOrNull(2)?.toLongOrNull() ?: 1000L
            TapStep(x.coerceIn(0f, 100f), y.coerceIn(0f, 100f), d.coerceAtLeast(0L))
        }
}
