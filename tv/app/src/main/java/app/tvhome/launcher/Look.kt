package app.tvhome.launcher

import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable

/** Small line icons drawn in code on a 24-unit grid, so the launcher ships no icon assets. */
class Glyph(private val name: String, color: Int) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; this.color = color }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color }
    fun tint(color: Int) { p.color = color; fill.color = color; invalidateSelf() }
    override fun draw(c: Canvas) {
        val s = minOf(bounds.width(), bounds.height()) / 24f
        c.save(); c.translate(bounds.exactCenterX() - 12 * s, bounds.exactCenterY() - 12 * s); c.scale(s, s)
        when (name) {
            "wifi", "offline" -> {
                for (r in floatArrayOf(5f, 9.5f, 14f)) c.drawArc(RectF(12 - r, 20 - r, 12 + r, 20 + r), -135f, 90f, false, p)
                c.drawCircle(12f, 19.5f, 1.4f, fill)
                if (name == "offline") c.drawLine(4f, 4f, 20f, 20f, p)
            }
            "ethernet" -> { c.drawRect(6f, 5f, 18f, 14f, p); for (x in floatArrayOf(9f, 12f, 15f)) c.drawLine(x, 14f, x, 19f, p) }
            "bluetooth" -> c.drawPath(path(7f, 7f, 17f, 16f, 12f, 20.5f, 12f, 3.5f, 17f, 8f, 7f, 17f), p)
            "gear" -> { c.drawCircle(12f, 12f, 3f, p); c.drawCircle(12f, 12f, 7f, p); for (i in 0 until 8) { val a = Math.toRadians(i * 45.0); c.drawLine(12 + 7 * cos(a), 12 + 7 * sin(a), 12 + 9.5f * cos(a), 12 + 9.5f * sin(a), p) } }
            "moon" -> c.drawPath(Path().apply { addCircle(12f, 12f, 8f, Path.Direction.CW); op(Path().apply { addCircle(16.5f, 8f, 7f, Path.Direction.CW) }, Path.Op.DIFFERENCE) }, fill)
            "sun" -> { c.drawCircle(12f, 12f, 4f, p); for (i in 0 until 8) { val a = Math.toRadians(i * 45.0); c.drawLine(12 + 7 * cos(a), 12 + 7 * sin(a), 12 + 9.5f * cos(a), 12 + 9.5f * sin(a), p) } }
            "cloud" -> c.drawPath(cloud(0f), fill)
            "rain", "snow", "storm" -> {
                c.drawPath(cloud(-3f), fill)
                when (name) {
                    "rain" -> for (x in floatArrayOf(8f, 12f, 16f)) c.drawLine(x, 18f, x - 1f, 21f, p)
                    "snow" -> for (x in floatArrayOf(8f, 12f, 16f)) c.drawCircle(x, 19.5f, 1.2f, fill)
                    else -> c.drawPath(path(13f, 16f, 10.5f, 19f, 13.5f, 19f, 11f, 22f), p)
                }
            }
            "power" -> { c.drawArc(RectF(4.5f, 5.5f, 19.5f, 20.5f), -60f, 300f, false, p); c.drawLine(12f, 3f, 12f, 11f, p) }
            "input" -> { c.drawPath(path(14f, 5f, 19f, 5f, 19f, 19f, 14f, 19f), p); c.drawLine(4f, 12f, 14f, 12f, p); c.drawPath(path(10f, 8f, 14f, 12f, 10f, 16f), p) }
            "cast" -> { c.drawPath(path(3f, 9f, 3f, 5f, 21f, 5f, 21f, 19f, 15f, 19f), p); for (r in floatArrayOf(4f, 8f)) c.drawArc(RectF(3 - r, 19 - r, 3 + r, 19 + r), -90f, 90f, false, p) }
            "display" -> { c.drawRoundRect(3f, 5f, 21f, 16f, 2f, 2f, p); c.drawLine(8f, 20f, 16f, 20f, p); c.drawLine(12f, 16f, 12f, 20f, p) }
            "sound" -> { c.drawPath(path(4f, 9f, 8f, 9f, 13f, 5f, 13f, 19f, 8f, 15f, 4f, 15f).apply { close() }, p); for (r in floatArrayOf(4f, 7.5f)) c.drawArc(RectF(13 - r, 12 - r, 13 + r, 12 + r), -45f, 90f, false, p) }
            "apps" -> for (x in floatArrayOf(4f, 13.5f)) for (y in floatArrayOf(4f, 13.5f)) c.drawRoundRect(x, y, x + 6.5f, y + 6.5f, 1.5f, 1.5f, p)
            "recents" -> { c.drawRoundRect(4f, 8f, 16f, 20f, 2f, 2f, p); c.drawPath(path(8f, 4f, 20f, 4f, 20f, 16f), p) }
            "home" -> c.drawPath(path(4f, 11f, 12f, 4f, 20f, 11f, 20f, 20f, 4f, 20f).apply { close() }, p)
            "plus" -> { c.drawLine(12f, 5f, 12f, 19f, p); c.drawLine(5f, 12f, 19f, 12f, p) }
            "phone" -> { c.drawRoundRect(7f, 3f, 17f, 21f, 2.5f, 2.5f, p); c.drawLine(11f, 18f, 13f, 18f, p) }
        }
        c.restore()
    }
    private fun cos(a: Double) = kotlin.math.cos(a).toFloat()
    private fun sin(a: Double) = kotlin.math.sin(a).toFloat()
    private fun path(vararg xy: Float) = Path().apply { moveTo(xy[0], xy[1]); for (i in 2 until xy.size step 2) lineTo(xy[i], xy[i + 1]) }
    private fun cloud(dy: Float) = Path().apply {
        addCircle(9f, 14f + dy, 4.5f, Path.Direction.CW)
        op(Path().apply { addCircle(14.5f, 11.5f + dy, 5.5f, Path.Direction.CW) }, Path.Op.UNION)
        op(Path().apply { addRoundRect(RectF(4.5f, 13f + dy, 20f, 18.5f + dy), 3f, 3f, Path.Direction.CW) }, Path.Op.UNION)
    }
    override fun setAlpha(alpha: Int) { p.alpha = alpha; fill.alpha = alpha }
    override fun setColorFilter(filter: ColorFilter?) { p.colorFilter = filter; fill.colorFilter = filter }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Glass surfaces. Liquid is clear with a bright rim; Frosted is a denser, softer fill. */
object Look {
    private fun surface(radius: Float, colors: IntArray, stroke: Int, width: Int) = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors).apply { cornerRadius = radius; setStroke(width, stroke) }

    /** Overlay panels always use a dark tint so white text stays readable over any wallpaper. */
    fun panel(liquid: Boolean, radius: Float, rim: Int) =
        if (liquid) surface(radius, intArrayOf(0x47FFFFFF, 0x1FFFFFFF), 0x73FFFFFF, rim)
        else surface(radius, intArrayOf(0xD93A3A3E.toInt(), 0xD9242428.toInt()), 0x26FFFFFF, rim)

    fun shelf(liquid: Boolean, dark: Boolean, radius: Float, rim: Int) = when {
        liquid && !dark -> surface(radius, intArrayOf(0x66FFFFFF, 0x2EFFFFFF), 0x8CFFFFFF.toInt(), rim)
        liquid -> surface(radius, intArrayOf(0x38FFFFFF, 0x14FFFFFF), 0x47FFFFFF, rim)
        !dark -> surface(radius, intArrayOf(0xB3EDEDF2.toInt(), 0xA6DCDCE2.toInt()), 0x73FFFFFF, rim)
        else -> surface(radius, intArrayOf(0xBF2C2C2E.toInt(), 0xBF1C1C1E.toInt()), 0x26FFFFFF, rim)
    }

    /** Control tiles turn solid white when focused, like the reference Control Center. */
    fun control(liquid: Boolean, radius: Float, rim: Int) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), surface(radius, intArrayOf(Color.WHITE, Color.WHITE), Color.WHITE, rim))
        addState(intArrayOf(), if (liquid) surface(radius, intArrayOf(0x3DFFFFFF, 0x14FFFFFF), 0x40FFFFFF, rim) else surface(radius, intArrayOf(0xA6404044.toInt(), 0xA62E2E32.toInt()), 0x1AFFFFFF, rim))
    }

    /** Average color of a drawable, favoring saturated pixels, for the adaptive backdrop. */
    fun accent(drawable: Drawable): Int {
        val bitmap = Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888)
        val old = Rect(drawable.bounds)
        drawable.setBounds(0, 0, 16, 9); drawable.draw(Canvas(bitmap)); drawable.bounds = old
        var r = 0.0; var g = 0.0; var b = 0.0; var weight = 0.0
        val hsv = FloatArray(3)
        for (x in 0 until 16) for (y in 0 until 9) {
            val c = bitmap.getPixel(x, y)
            if (Color.alpha(c) < 128) continue
            Color.colorToHSV(c, hsv)
            val w = 0.05 + hsv[1] * hsv[2]
            r += Color.red(c) * w; g += Color.green(c) * w; b += Color.blue(c) * w; weight += w
        }
        bitmap.recycle()
        if (weight == 0.0) return DEFAULT_ACCENT
        Color.colorToHSV(Color.rgb((r / weight).toInt(), (g / weight).toInt(), (b / weight).toInt()), hsv)
        if (hsv[1] < .18f) return DEFAULT_ACCENT
        hsv[1] = hsv[1].coerceAtLeast(.55f); hsv[2] = hsv[2].coerceIn(.55f, .9f)
        return Color.HSVToColor(hsv)
    }
    const val DEFAULT_ACCENT = 0xFF2F6FDB.toInt()
}
