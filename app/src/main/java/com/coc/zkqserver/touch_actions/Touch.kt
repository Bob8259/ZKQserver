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
                val steps = (duration / 10).toInt().coerceAtLeast(1)

                for (i in 1..steps) {
                    val progress = i.toFloat() / steps
                    // Add slight human-like jitter (up to 1.5 pixels) if enabled
                    val jitterX = if (jitter) (Random.nextFloat() - 0.5f) * 1.5f else 0f
                    val jitterY = if (jitter) (Random.nextFloat() - 0.5f) * 1.5f else 0f

                    val currentX = fromX + (x - fromX) * progress + jitterX
                    val currentY = fromY + (y - fromY) * progress + jitterY
                    val currentTime = startTime + (duration * progress).toLong()

                    state.lastX = currentX
                    state.lastY = currentY

                    injectMotionEvent(MotionEvent.ACTION_MOVE, downTime, currentTime)
                    // Randomize delay between 8ms and 12ms to avoid perfect 10ms intervals
                    delay(Random.nextLong(8, 13))
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