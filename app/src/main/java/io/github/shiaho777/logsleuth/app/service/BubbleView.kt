package io.github.shiaho777.logsleuth.app.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * A plain drawn bubble: circle + bookmark glyph. Red ring while recording.
 * Tap = bookmark, long-press = toggle recording, drag = move.
 */
@SuppressLint("ViewConstructor")
class BubbleView(context: Context) : View(context) {

    var onBookmark: (() -> Unit)? = null
    var onToggleRecording: (() -> Unit)? = null
    var onDrag: ((Float, Float) -> Unit)? = null

    private var recording = false

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(0xE6, 0x1F, 0x1F, 0x1F)
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(0xFF, 0xB0, 0xB0, 0xB0)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        textSize = 34f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            onBookmark?.invoke()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            onToggleRecording?.invoke()
        }
    })

    init {
        val size = (56 * resources.displayMetrics.density).toInt()
        layoutParams = android.view.ViewGroup.LayoutParams(size, size)
    }

    fun setRecording(value: Boolean) {
        if (recording != value) {
            recording = value
            ringPaint.color = if (value) Color.argb(0xFF, 0xFF, 0x45, 0x3A) else Color.argb(0xFF, 0xB0, 0xB0, 0xB0)
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = (56 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        val r = width / 2f - ringPaint.strokeWidth
        canvas.drawCircle(width / 2f, height / 2f, r, fillPaint)
        canvas.drawCircle(width / 2f, height / 2f, r, ringPaint)
        canvas.drawText("◉", width / 2f, height / 2f + glyphPaint.textSize / 3f, glyphPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.rawX
                lastY = event.rawY
                dragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastX
                val dy = event.rawY - lastY
                if (abs(dx) > 6 || abs(dy) > 6) {
                    dragging = true
                    onDrag?.invoke(dx, dy)
                    lastX = event.rawX
                    lastY = event.rawY
                }
            }
        }
        return gestures.onTouchEvent(event) || super.onTouchEvent(event)
    }
}
