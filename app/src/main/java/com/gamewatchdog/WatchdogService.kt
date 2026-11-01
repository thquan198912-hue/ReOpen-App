package com.gamewatchdog

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

/**
 * Theo dõi cửa sổ đang ở trên cùng. Nếu game đã chọn không còn ở trên cùng
 * quá N giây (thoát hoặc văng) thì mở lại game, chờ tải, rồi chạm lần lượt các điểm đã cài.
 */
class WatchdogService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: WatchdogService? = null

        private val IGNORED = setOf("com.android.systemui")
    }

    private val handler = Handler(Looper.getMainLooper())
    private var currentPkg = ""
    private var lastGameSeen = 0L
    private var busy = false
    private var runToken = 0
    private var imePkgs: Set<String> = emptySet()

    private val ticker = object : Runnable {
        override fun run() {
            try {
                watch()
            } catch (e: Exception) {
                Prefs.log(this@WatchdogService, "Lỗi: ${e.message}")
            }
            handler.postDelayed(this, 2000L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        refreshIme()
        Prefs.log(this, "Dịch vụ trợ năng đã kết nối")
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(ticker, 2000L)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Bỏ qua thanh thông báo và bàn phím để không nhầm là game đã thoát
        if (pkg in IGNORED || pkg in imePkgs) return
        currentPkg = pkg
        if (pkg == Prefs.getPackage(this)) lastGameSeen = System.currentTimeMillis()
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacksAndMessages(null)
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun refreshIme() {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imePkgs = imm.enabledInputMethodList.map { it.packageName }.toSet()
        } catch (_: Exception) {
        }
    }

    private fun watch() {
        if (!Prefs.isEnabled(this) || busy) return
        val game = Prefs.getPackage(this)
        if (game.isEmpty()) return

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) return // màn hình tắt thì không làm gì

        val now = System.currentTimeMillis()
        if (currentPkg == packageName) { // đang mở màn hình cài đặt của chính app này
            lastGameSeen = now
            return
        }
        if (currentPkg == game) return

        val ref = maxOf(lastGameSeen, Prefs.enabledAt(this))
        if (now - ref >= Prefs.getGraceSec(this) * 1000L) recover(game)
    }

    /** Dùng cho nút "Thử ngay" trong app. Trả về false nếu đang bận. */
    fun runNow(): Boolean {
        if (busy) return false
        val game = Prefs.getPackage(this)
        if (game.isEmpty()) return false
        recover(game)
        return true
    }

    private fun recover(game: String) {
        busy = true
        val token = ++runToken
        Prefs.log(this, "Game không còn ở màn hình → mở lại")

        if (!launchGame(game)) {
            Prefs.log(this, "Không mở được $game (đã gỡ cài đặt?). Tự tắt giám sát.")
            Prefs.setEnabled(this, false)
            busy = false
            return
        }

        val wait = Prefs.getStartupSec(this) * 1000L
        Prefs.log(this, "Chờ game tải ${wait / 1000}s rồi chạm")
        handler.postDelayed({
            if (token == runToken) runTaps(token)
        }, wait)
    }

    private fun launchGame(game: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(game) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        startActivity(intent)
        return true
    }

    private fun runTaps(token: Int) {
        val steps = Prefs.parseTaps(Prefs.getTaps(this))
        if (steps.isEmpty()) {
            finishRun("Không có điểm chạm nào được cài đặt")
            return
        }
        Prefs.log(this, "Bắt đầu chạm ${steps.size} điểm")
        doStep(steps, 0, token)
    }

    private fun doStep(steps: List<TapStep>, index: Int, token: Int) {
        if (token != runToken) return
        if (index >= steps.size) {
            finishRun("Xong chuỗi chạm")
            return
        }
        val s = steps[index]
        handler.postDelayed({
            if (token != runToken) return@postDelayed
            tap(s.xPct, s.yPct) { doStep(steps, index + 1, token) }
        }, s.delayMs)
    }

    private fun finishRun(msg: String) {
        Prefs.log(this, msg)
        lastGameSeen = System.currentTimeMillis()
        busy = false
    }

    private fun tap(xPct: Float, yPct: Float, done: () -> Unit) {
        val (w, h) = screenSize()
        val path = Path().apply { moveTo(w * xPct / 100f, h * yPct / 100f) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
            .build()
        val dispatched = dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    done()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    done()
                }
            },
            null
        )
        if (!dispatched) done()
    }

    private fun screenSize(): Pair<Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            Pair(b.width(), b.height())
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(m)
            Pair(m.widthPixels, m.heightPixels)
        }
    }
}
