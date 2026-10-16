package com.hy.autoswipe

import kotlin.math.hypot

/** Panel and edge arrow share this tracker, and its one hypot test against scaledTouchSlop. */
class OverlayPointerTracker(private val slop: Int) {
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    fun down(rawX: Float, rawY: Float) {
        downX = rawX
        downY = rawY
        lastX = rawX
        lastY = rawY
        dragging = false
    }

    fun crossedSlop(rawX: Float, rawY: Float): Boolean {
        // 分轴的 |dx| 或 |dy| 会把已经超过半径的对角线仍当成点击。
        if (!dragging && hypot((rawX - downX).toDouble(), (rawY - downY).toDouble()) > slop) {
            dragging = true
        }
        return dragging
    }

    fun dragStep(rawX: Float, rawY: Float): Pair<Int, Int>? {
        if (!crossedSlop(rawX, rawY)) return null
        val dx = (rawX - lastX).toInt()
        val dy = (rawY - lastY).toInt()
        lastX = rawX
        lastY = rawY
        return dx to dy
    }

    fun finish(): Boolean {
        val moved = dragging
        dragging = false
        return moved
    }
}
