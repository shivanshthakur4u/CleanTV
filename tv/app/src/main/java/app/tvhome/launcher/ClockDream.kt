package app.tvhome.launcher

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView

class ClockDream : DreamService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var clock: TextView
    private var position = 0
    override fun onAttachedToWindow() {
        super.onAttachedToWindow(); isInteractive = false; isFullscreen = true; isScreenBright = false
        val frame = FrameLayout(this).apply { setBackgroundColor(Color.rgb(8, 14, 15)) }
        clock = TextView(this).apply { textSize = 52f; setTextColor(Color.rgb(175, 189, 180)); setPadding(56, 48, 56, 48); typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL); setLineSpacing(0f, 1.1f) }
        frame.addView(clock); setContentView(frame); update.run()
    }
    private val update = object : Runnable {
        override fun run() {
            val format = if (HomeStore(this@ClockDream).clock24h) "HH:mm" else "h:mm"
            val now = java.util.Date(); val store = HomeStore(this@ClockDream); val weather = Weather(this@ClockDream)
            val line = java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault()).format(now) + if (weather.city.isNotBlank() && weather.city == store.weatherCity) "  ·  ${weather.temp}°" else ""
            clock.text = android.text.SpannableString("${java.text.SimpleDateFormat(format, java.util.Locale.getDefault()).format(now)}\n$line").apply {
                setSpan(android.text.style.RelativeSizeSpan(.3f), length - line.length, length, 0)
            }
            val gravity = intArrayOf(Gravity.TOP or Gravity.START, Gravity.TOP or Gravity.END, Gravity.BOTTOM or Gravity.END, Gravity.BOTTOM or Gravity.START)
            clock.layoutParams = FrameLayout.LayoutParams(-2, -2, gravity[position++ % gravity.size])
            handler.postDelayed(this, 60_000)
        }
    }
    override fun onDetachedFromWindow() { handler.removeCallbacksAndMessages(null); super.onDetachedFromWindow() }
}
