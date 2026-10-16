package com.hy.autoswipe

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout

class OverlayPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    var onDrag: ((dx: Int, dy: Int) -> Unit)? = null
    var onDragEnd: ((moved: Boolean) -> Unit)? = null
    var onPointerDown: (() -> Unit)? = null

    private val pointerTracker = OverlayPointerTracker(
        ViewConfiguration.get(context).scaledTouchSlop,
    )

    fun bindEdgeArrow(arrow: View) {
        arrow.setOnTouchListener { _, event -> handlePointer(event) }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> beginPointer(ev)
            MotionEvent.ACTION_MOVE -> {
                if (pointerTracker.crossedSlop(ev.rawX, ev.rawY)) return true
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                beginPointer(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> dispatchDrag(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                onDragEnd?.invoke(pointerTracker.finish())
            }
        }
        return true
    }

    private fun handlePointer(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> beginPointer(event)
            MotionEvent.ACTION_MOVE -> dispatchDrag(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                onDragEnd?.invoke(pointerTracker.finish())
            }
            else -> return false
        }
        return true
    }

    private fun beginPointer(event: MotionEvent) {
        pointerTracker.down(event.rawX, event.rawY)
        onPointerDown?.invoke()
    }

    private fun dispatchDrag(event: MotionEvent) {
        pointerTracker.dragStep(event.rawX, event.rawY)?.let { (dx, dy) ->
            onDrag?.invoke(dx, dy)
        }
    }
}
