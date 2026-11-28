package io.lifeos.focuslab

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Native accessibility-overlay counterpart of the Compose clock. */
class FocusRingView(context: Context) : View(context) {
    private val store = LabStore(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    fun refresh() { invalidate() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val session = store.session() ?: return
        val dp = resources.displayMetrics.density
        val radius = (minOf(width, height) / 2f - 14 * dp).coerceAtLeast(0f)
        val cx = width / 2f; val cy = height / 2f
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 8 * dp; paint.strokeCap = Paint.Cap.ROUND
        val dark = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        paint.color = if (dark) Color.rgb(48, 70, 58) else Color.rgb(225, 236, 227); canvas.drawArc(rect, -90f, 360f, false, paint)
        paint.color = if (dark) Color.rgb(178, 208, 190) else Color.rgb(49, 92, 75)
        canvas.drawArc(rect, -90f, 360f * FocusPresentation.progress(session.duration, store.remainingMillis()), false, paint)
        paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER
        paint.textSize = 14 * resources.displayMetrics.scaledDensity
        canvas.drawText("这一段 · 专注", cx, cy - 24 * dp, paint)
        val text = FocusPresentation.range(session.start, session.end)
        paint.textSize = 26 * resources.displayMetrics.scaledDensity
        val available = radius * 1.8f
        if (paint.measureText(text) > available) paint.textSize *= available / paint.measureText(text)
        canvas.drawText(text, cx, cy + 14 * dp, paint)
        contentDescription = "专注时段 $text"
    }
}
