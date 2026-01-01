package com.coc.zkqserver.touch_actions

import android.os.IBinder
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

class Touch {
    companion object {
        internal var inputManager: Any? = null
        internal var injectInputEventMethod: Method? = null

        internal val pointerStateMap = ConcurrentHashMap<Int, PointerState>()
        // Keeps track of active pointer IDs in order to assign indices
        internal val activePointerIds = CopyOnWriteArrayList<Int>()
        internal val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        internal data class PointerState(
            val downTime: Long,
            var lastX: Float,
            var lastY: Float
        )

        init {
            try {
                val serviceManagerClass = Class.forName("android.os.ServiceManager")
                val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
                val inputServiceBinder = getServiceMethod.invoke(null, "input")

                val inputManagerStubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
                val asInterfaceMethod = inputManagerStubClass.getMethod("asInterface", IBinder::class.java)
                inputManager = asInterfaceMethod.invoke(null, inputServiceBinder)

                injectInputEventMethod = inputManager?.javaClass?.getMethod(
                    "injectInputEvent",
                    InputEvent::class.java,
                    Int::class.javaPrimitiveType
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        @Synchronized
        fun touchDown(x: Float, y: Float, id: Int) {
            val now = SystemClock.uptimeMillis()
            val state = PointerState(now, x, y)
            pointerStateMap[id] = state

            if (!activePointerIds.contains(id)) {
                activePointerIds.add(id)
            }

            val index = activePointerIds.indexOf(id)
            val action = if (activePointerIds.size == 1) {
                MotionEvent.ACTION_DOWN
            } else {
                MotionEvent.ACTION_POINTER_DOWN or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            }

            injectMotionEvent(action, now, now)
        }

        fun touchMove(x: Float, y: Float, id: Int, duration: Long = 150L, jitter: Boolean = true) {
            val state = pointerStateMap[id] ?: return
            val fromX = state.lastX
            val fromY = state.lastY
            val downTime = state.downTime

            scope.launch {
                val startTime = SystemClock.uptimeMillis()
                val steps = (duration / 5).toInt().coerceAtLeast(1)

                if (!jitter) {
                    // Move in a straight line without any jitter or curves
                    for (i in 1..steps) {
                        val progress = i.toFloat() / steps
                        val currentX = fromX + (x - fromX) * progress
                        val currentY = fromY + (y - fromY) * progress
                        val currentTime = startTime + (duration * progress).toLong()

                        state.lastX = currentX
                        state.lastY = currentY

                        injectMotionEvent(MotionEvent.ACTION_MOVE, downTime, currentTime)
                        // Randomize delay slightly to avoid perfectly uniform intervals
                        delay(Random.nextLong(4, 6))
                    }

                    state.lastX = x
                    state.lastY = y
                    injectMotionEvent(MotionEvent.ACTION_MOVE, downTime, SystemClock.uptimeMillis())
                    return@launch
                }

                // jitter == true: build a path consisting of a single quadratic Bezier curve
                val dx = x - fromX
                val dy = y - fromY
                // normalize direction (avoid division by zero)
                val length = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
                val ux = dx / length
                val uy = dy / length
                // perpendicular vector
                val px = -uy
                val py = ux

                // randomize curve offset magnitude (small, human-like)
                val maxCurveOffset = 50f + Random.nextFloat() * 20f

                // single control point placed near midpoint along the straight line, offset perpendicularly
                val cx = fromX + dx * 0.5f + px * ((Random.nextFloat() - 0.5f) * 2f * maxCurveOffset)
                val cy = fromY + dy * 0.5f + py * ((Random.nextFloat() - 0.5f) * 2f * maxCurveOffset)

                for (i in 1..steps) {
                    val progress = i.toFloat() / steps
                    val currentTime = startTime + (duration * progress).toLong()

                    // single quadratic Bezier: from -> to, control = (cx, cy)
                    val t = progress
                    val inv = 1f - t
                    val bx = inv * inv * fromX + 2f * inv * t * cx + t * t * x
                    val by = inv * inv * fromY + 2f * inv * t * cy + t * t * y

                    // small per-step random jitter to simulate human noise
                    val jitterX = (Random.nextFloat() - 0.5f) * 3f
                    val jitterY = (Random.nextFloat() - 0.5f) * 3f

                    val finalX = bx + jitterX
                    val finalY = by + jitterY

                    state.lastX = finalX
                    state.lastY = finalY

                    injectMotionEvent(MotionEvent.ACTION_MOVE, downTime, currentTime)
                    delay(Random.nextLong(4, 6))
                }

                state.lastX = x
                state.lastY = y
                injectMotionEvent(MotionEvent.ACTION_MOVE, downTime, SystemClock.uptimeMillis())
            }
        }


        @Synchronized
        fun touchUp(id: Int) {
            val state = pointerStateMap[id] ?: return
            val now = SystemClock.uptimeMillis()

            val index = activePointerIds.indexOf(id)
            if (index == -1) return

            val action = if (activePointerIds.size == 1) {
                MotionEvent.ACTION_UP
            } else {
                MotionEvent.ACTION_POINTER_UP or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            }

            injectMotionEvent(action, state.downTime, now)

            activePointerIds.removeAt(index)
            pointerStateMap.remove(id)
        }

        internal fun injectMotionEvent(action: Int, downTime: Long, eventTime: Long) {
            val pointerCount = activePointerIds.size
            if (pointerCount == 0) return

            val pointerProperties = arrayOfNulls<MotionEvent.PointerProperties>(pointerCount)
            val pointerCoords = arrayOfNulls<MotionEvent.PointerCoords>(pointerCount)

            for (i in 0 until pointerCount) {
                val id = activePointerIds[i]
                val state = pointerStateMap[id] ?: continue

                val props = MotionEvent.PointerProperties()
                props.id = id
                props.toolType = MotionEvent.TOOL_TYPE_FINGER
                pointerProperties[i] = props

                val coords = MotionEvent.PointerCoords()
                coords.x = state.lastX
                coords.y = state.lastY
                // Randomize pressure and size slightly
                coords.pressure = 0.7f + Random.nextFloat() * 0.3f
                coords.size = 0.05f + Random.nextFloat() * 0.05f
                pointerCoords[i] = coords
            }

            // Filter out any nulls if state was missing
            val validProps = pointerProperties.filterNotNull().toTypedArray()
            val validCoords = pointerCoords.filterNotNull().toTypedArray()

            if (validProps.size != pointerCount) return

            val event = MotionEvent.obtain(
                downTime,
                eventTime,
                action,
                pointerCount,
                validProps,
                validCoords,
                0, // metaState
                0, // buttonState
                // Randomize precision slightly to simulate different touch resolutions/noise
                0.9f + Random.nextFloat() * 0.2f, // xPrecision
                0.9f + Random.nextFloat() * 0.2f, // yPrecision
                0, // deviceId
                0, // edgeFlags
                InputDevice.SOURCE_TOUCHSCREEN,
                0 // flags
            )

            try {
                // INJECT_INPUT_EVENT_MODE_ASYNC = 0
                injectInputEventMethod?.invoke(inputManager, event, 0)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                event.recycle()
            }
        }
    }
}
