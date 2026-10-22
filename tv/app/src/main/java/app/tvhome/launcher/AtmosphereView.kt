package app.tvhome.launcher

import android.content.Context
import android.graphics.*
import android.view.View

class AtmosphereView(context: Context) : View(context) {
    var scene = "dusk"
        set(value) { field = value; rebuild(); invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var base: Shader? = null
    private var glow: Shader? = null
    private val hills = Array(4) { Path() }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); rebuild() }
    private fun rebuild() {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val colors = when (scene) {
            "aurora" -> intArrayOf(Color.rgb(6, 24, 36), Color.rgb(35, 92, 94), Color.rgb(17, 27, 37))
            "sand" -> intArrayOf(Color.rgb(33, 30, 36), Color.rgb(162, 119, 96), Color.rgb(69, 55, 53))
            "ocean" -> intArrayOf(Color.rgb(10, 27, 44), Color.rgb(45, 92, 113), Color.rgb(13, 37, 53))
            else -> intArrayOf(Color.rgb(21, 28, 35), Color.rgb(103, 105, 93), Color.rgb(24, 43, 43))
        }
        base = LinearGradient(0f, 0f, w * .8f, h, colors, null, Shader.TileMode.CLAMP)
        glow = RadialGradient(w * .72f, h * .34f, w * .45f, Color.argb(55, 242, 216, 171), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        repeat(4) { i ->
            val y = h * (.50f + i * .11f)
            hills[i].apply { reset(); moveTo(0f, y); cubicTo(w * .22f, y - h * .15f, w * .65f, y + h * .22f, w, y - h * .05f); lineTo(w, h); lineTo(0f, h); close() }
        }
    }
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        paint.shader = base; canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = glow; canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        repeat(4) { i -> paint.color = Color.argb(30 + i * 18, 10, 29, 29); canvas.drawPath(hills[i], paint) }
    }
}
