package com.coc.zkqserver.touch_actions

import android.os.SystemClock
import android.view.MotionEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

object Pinch {
    fun pinchIn(x1: Float, y1: Float, x2: Float, y2: Float, finalX: Float, finalY: Float, duration: Long = 150L, jitter: Boolean = true) {
        performPinch(x1, y1, x2, y2, finalX, finalY, finalX, finalY, duration, jitter)
    }

    fun pinchOut(startX: Float, startY: Float, x1: Float, y1: Float, x2: Float, y2: Float, duration: Long = 150L, jitter: Boolean = true) {
        performPinch(startX, startY, startX, startY, x1, y1, x2, y2, duration, jitter)
    }
    
    private fun performPinch(
        s1x: Float, s1y: Float, 
        s2x: Float, s2y: Float, 
        e1x: Float, e1y: Float, 
        e2x: Float, e2y: Float, 
        duration: Long,
        jitter: Boolean
    ) {
        val id1 = 10 // Use distinct IDs for pinch actions
        val id2 = 11

        Touch.scope.launch {
            val startTime = SystemClock.uptimeMillis()
            
            // Start the gesture
            Touch.touchDown(s1x, s1y, id1)
            Touch.touchDown(s2x, s2y, id2)

            val steps = (duration / 10).toInt().coerceAtLeast(1)
            val downTime = Touch.pointerStateMap[id1]?.downTime ?: startTime

            for (i in 1..steps) {
                val progress = i.toFloat() / steps

                // Add slight human-like jitter to both fingers if enabled
                val jitter1x = if (jitter) (Random.nextFloat() - 0.5f) * 1.5f else 0f
                val jitter1y = if (jitter) (Random.nextFloat() - 0.5f) * 1.5f else 0f
                val jitter2x = if (jitter) (Random.nextFloat() - 0.5f) * 1.5f else 0f
                val jitter2y = if (jitter) (Random.nextFloat() - 0.5f) * 1.5f else 0f

                val curr1x = s1x + (e1x - s1x) * progress + jitter1x
                val curr1y = s1y + (e1y - s1y) * progress + jitter1y
                val curr2x = s2x + (e2x - s2x) * progress + jitter2x
                val curr2y = s2y + (e2y - s2y) * progress + jitter2y
                val currentTime = startTime + (duration * progress).toLong()

                Touch.pointerStateMap[id1]?.let { it.lastX = curr1x; it.lastY = curr1y }
                Touch.pointerStateMap[id2]?.let { it.lastX = curr2x; it.lastY = curr2y }

                Touch.injectMotionEvent(MotionEvent.ACTION_MOVE, downTime, currentTime)
                // Randomize delay between 8ms and 12ms
                delay(Random.nextLong(8, 13))
            }

            // End the gesture
            Touch.touchUp(id1)
            Touch.touchUp(id2)
        }
    }
}
