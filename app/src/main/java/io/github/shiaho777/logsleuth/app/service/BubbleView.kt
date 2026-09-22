package io.github.shiaho777.logsleuth.app.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.ImageButton
import android.widget.LinearLayout
import io.github.shiaho777.logsleuth.app.R
import kotlin.math.abs

/**
 * Floating control pill: [record toggle] [bookmark] [hide]. The whole pill is
 * draggable — a move past touch slop is treated as a drag, otherwise the
 * tapped button handles the event.
 */
@SuppressLint("ViewConstructor")
class BubbleView(context: Context) : LinearLayout(context) {

    var onToggleRecording: (() -> Unit)? = null
    var onBookmark: (() -> Unit)? = null
    var onHide: (() -> Unit)? = null
    var onDrag: ((Float, Float) -> Unit)? = null

    private var recording = false
    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val pill = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 28f * density
        setColor(0xE6_1F1F1F.toInt())
        setStroke((1.5f * density).toInt(), 0xFF_B0B0B0.toInt())
    }

    private val recordButton = actionButton(
        icon = R.drawable.ic_bubble_record,
        tint = 0xFF_FF453A.toInt(),
        description = context.getString(R.string.record_start),
    ) { onToggleRecording?.invoke() }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        background = pill
        val pad = (6 * density).toInt()
        setPadding(pad, pad, pad, pad)

        addView(recordButton)
        addView(
            actionButton(
                icon = R.drawable.ic_bubble_bookmark,
                tint = 0xFF_FFFFFF.toInt(),
                description = context.getString(R.string.notif_action_bookmark),
            ) { onBookmark?.invoke() },
        )
        addView(
            actionButton(
                icon = R.drawable.ic_bubble_close,
                tint = 0xFF_B0B0B0.toInt(),
                description = context.getString(R.string.bubble_hide),
            ) { onHide?.invoke() },
        )
    }

    fun setRecording(value: Boolean) {
        if (recording == value) return
        recording = value
        recordButton.setImageResource(
            if (value) R.drawable.ic_bubble_stop else R.drawable.ic_bubble_record,
        )
        recordButton.contentDescription = context.getString(
            if (value) R.string.record_stop else R.string.record_start,
        )
        pill.setStroke(
            (1.5f * density).toInt(),
            if (value) 0xFF_FF453A.toInt() else 0xFF_B0B0B0.toInt(),
        )
    }

    private fun actionButton(
        icon: Int,
        tint: Int,
        description: String,
        onClick: () -> Unit,
    ): ImageButton = ImageButton(context).apply {
        setImageResource(icon)
        setColorFilter(tint)
        contentDescription = description
        val size = (44 * density).toInt()
        layoutParams = LayoutParams(size, size)
        val pad = (10 * density).toInt()
        setPadding(pad, pad, pad, pad)
        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        val outValue = TypedValue()
        context.theme.resolveAttribute(
            android.R.attr.selectableItemBackgroundBorderless,
            outValue,
            true,
        )
        setBackgroundResource(outValue.resourceId)
        setOnClickListener { onClick() }
    }

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                lastX = event.rawX
                lastY = event.rawY
            }

            MotionEvent.ACTION_MOVE -> {
                if (abs(event.rawX - downX) > touchSlop ||
                    abs(event.rawY - downY) > touchSlop
                ) {
                    lastX = event.rawX
                    lastY = event.rawY
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(event)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastX
                val dy = event.rawY - lastY
                lastX = event.rawX
                lastY = event.rawY
                if (dx != 0f || dy != 0f) onDrag?.invoke(dx, dy)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
        }
        return super.onTouchEvent(event)
    }
}
