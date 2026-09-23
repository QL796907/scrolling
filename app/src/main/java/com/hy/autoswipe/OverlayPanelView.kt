package com.hy.autoswipe

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.LinearLayout
import kotlin.math.hypot

class OverlayPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    var interceptAllTouches: Boolean = false
    var onDrag: ((dx: Int, dy: Int) -> Unit)? = null
    var onDragEnd: ((moved: Boolean) -> Unit)? = null
    var onPointerDown: (() -> Unit)? = null

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                lastX = ev.rawX
                lastY = ev.rawY
                dragging = false
                onPointerDown?.invoke()
                if (interceptAllTouches) return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (hypot((ev.rawX - downX).toDouble(), (ev.rawY - downY).toDouble()) > slop) {
                    dragging = true
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                lastX = event.rawX
                lastY = event.rawY
                dragging = false
                onPointerDown?.invoke()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (hypot((event.rawX - downX).toDouble(), (event.rawY - downY).toDouble()) > slop) {
                    dragging = true
                }
                if (dragging) {
                    val dx = (event.rawX - lastX).toInt()
                    val dy = (event.rawY - lastY).toInt()
                    lastX = event.rawX
                    lastY = event.rawY
                    onDrag?.invoke(dx, dy)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                onDragEnd?.invoke(dragging)
                dragging = false
            }
        }
        return true
    }
}
