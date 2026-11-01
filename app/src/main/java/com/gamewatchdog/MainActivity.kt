package com.gamewatchdog

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvGame: TextView
    private lateinit var etGrace: EditText
    private lateinit var etStartup: EditText
    private lateinit var etTaps: EditText
    private lateinit var swEnable: Switch
    private lateinit var tvLog: TextView

    private val ui = Handler(Looper.getMainLooper())
    private val refresher = object : Runnable {
        override fun run() {
            refreshStatus()
            ui.postDelayed(this, 2000L)
        }
    }

    private val enableListener = CompoundButton.OnCheckedChangeListener { _, checked ->
        saveFields()
        if (checked && Prefs.getPackage(this).isEmpty()) {
            toast("Hãy chọn game trước")
            swEnable.isChecked = false
        } else {
            Prefs.setEnabled(this, checked)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(40))
        }
        setContentView(ScrollView(this).apply { addView(root) })

        root.addView(text("Game Watchdog", 24f, bold = true))
        tvStatus = text("", 15f)
        root.addView(tvStatus)

        root.addView(button("1. Bật dịch vụ trợ năng") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(text("Nút bật bị mờ (Android 13+)? Mở thông tin ứng dụng → ⋮ góc phải → \"Cho phép cài đặt bị hạn chế\", rồi quay lại bật dịch vụ.", 13f))
        root.addView(button("Mở thông tin ứng dụng") {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        })

        root.addView(text("2. Game cần giám sát", 16f, bold = true))
        tvGame = text("", 14f)
        root.addView(tvGame)
        root.addView(button("Chọn game") { showPicker() })

        root.addView(text("3. Cài đặt", 16f, bold = true))
        root.addView(text("Số giây game vắng mặt trước khi coi là đã thoát/văng", 13f))
        etGrace = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine()
        }
        root.addView(etGrace)

        root.addView(text("Số giây chờ game tải xong trước khi bắt đầu chạm", 13f))
        etStartup = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine()
        }
        root.addView(etStartup)

        root.addView(text("Danh sách điểm chạm — mỗi dòng: x,y,chờ_ms", 13f))
        root.addView(text("x, y là % chiều ngang/dọc màn hình (0–100). chờ_ms là thời gian chờ trước khi chạm. Ví dụ \"50,80,3000\" = chờ 3 giây rồi chạm giữa màn hình, ở 80% từ trên xuống. Dòng bắt đầu bằng # là ghi chú.", 12f))
        etTaps = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 6
            gravity = Gravity.TOP or Gravity.START
            hint = "50,80,3000\n50,60,1500"
        }
        root.addView(etTaps)

        root.addView(button("Lưu cài đặt") {
            saveFields()
            toast("Đã lưu")
        })

        swEnable = Switch(this).apply {
            text = "Bật giám sát"
            textSize = 17f
            setPadding(0, dp(12), 0, dp(12))
        }
        root.addView(swEnable)

        root.addView(button("Thử ngay: mở game rồi chạm") {
            saveFields()
            val service = WatchdogService.instance
            when {
                service == null -> toast("Dịch vụ trợ năng chưa bật")
                Prefs.getPackage(this).isEmpty() -> toast("Chưa chọn game")
                service.runNow() -> toast("Đang chạy thử…")
                else -> toast("Đang bận, thử lại sau")
            }
        })

        root.addView(button("Tắt tối ưu pin (nên làm)") { requestBatteryExemption() })

        root.addView(text("Nhật ký (mới nhất ở trên)", 16f, bold = true))
        tvLog = text("", 12f)
        root.addView(tvLog)
    }

    override fun onResume() {
        super.onResume()
        etGrace.setText(Prefs.getGraceSec(this).toString())
        etStartup.setText(Prefs.getStartupSec(this).toString())
        etTaps.setText(Prefs.getTaps(this))
        swEnable.setOnCheckedChangeListener(null)
        swEnable.isChecked = Prefs.isEnabled(this)
        swEnable.setOnCheckedChangeListener(enableListener)
        ui.post(refresher)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(refresher)
        saveFields()
    }

    private fun saveFields() {
        Prefs.setGraceSec(this, (etGrace.text.toString().toIntOrNull() ?: 5).coerceIn(2, 600))
        Prefs.setStartupSec(this, (etStartup.text.toString().toIntOrNull() ?: 25).coerceIn(0, 600))
        Prefs.setTaps(this, etTaps.text.toString())
    }

    private fun refreshStatus() {
        val on = WatchdogService.instance != null
        tvStatus.text = if (on) {
            "● Dịch vụ trợ năng: ĐANG CHẠY"
        } else {
            "○ Dịch vụ trợ năng: CHƯA BẬT (bấm nút 1)"
        }
        val pkg = Prefs.getPackage(this)
        tvGame.text = if (pkg.isEmpty()) "(chưa chọn)" else pkg
        val log = Prefs.getLog(this).lines().filter { it.isNotBlank() }.reversed().joinToString("\n")
        tvLog.text = if (log.isEmpty()) "(trống)" else log
    }

    private fun showPicker() {
        val pm = packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(query, 0)
            .map { Pair(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.second != packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }

        AlertDialog.Builder(this)
            .setTitle("Chọn game")
            .setItems(apps.map { "${it.first}\n${it.second}" }.toTypedArray()) { _, i ->
                Prefs.setPackage(this, apps[i].second)
                refreshStatus()
            }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    private fun requestBatteryExemption() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            toast("Đã tắt tối ưu pin cho app này")
        } else {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(s: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(10), 0, dp(2))
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }
}
