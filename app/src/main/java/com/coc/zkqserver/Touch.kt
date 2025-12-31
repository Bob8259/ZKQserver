package com.coc.zkqserver

import android.view.InputEvent
import android.view.MotionEvent
import android.os.SystemClock
import android.view.InputDevice
import java.lang.reflect.Method

class Touch {
    companion object {
        private var inputManager: Any? = null
        private var injectInputEventMethod: Method? = null

        init {
            try {
                val serviceManagerClass = Class.forName("android.os.ServiceManager")
                val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
                val inputServiceBinder = getServiceMethod.invoke(null, "input")
                
                val inputManagerStubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
                val asInterfaceMethod = inputManagerStubClass.getMethod("asInterface", android.os.IBinder::class.java)
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

        fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, duration: Long) {
            val startTime = SystemClock.uptimeMillis()
            
            // Mouse/Touch constants (INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH = 2)
            val injectMode = 2 

            // Down Event
            injectEvent(MotionEvent.ACTION_DOWN, fromX, fromY, startTime, startTime)

            val steps = (duration / 10).toInt().coerceAtLeast(1)
            for (i in 1..steps) {
                val progress = i.toFloat() / steps
                val currentX = fromX + (toX - fromX) * progress
                val currentY = fromY + (toY - fromY) * progress
                val currentTime = startTime + (duration * progress).toLong()
                
                injectEvent(MotionEvent.ACTION_MOVE, currentX, currentY, startTime, currentTime)
                SystemClock.sleep(10)
            }

            // Up Event
            injectEvent(MotionEvent.ACTION_UP, toX, toY, startTime, startTime + duration)
        }

        private fun injectEvent(action: Int, x: Float, y: Float, downTime: Long, eventTime: Long) {
            val event = MotionEvent.obtain(
                downTime,
                eventTime,
                action,
                x,
                y,
                1.0f, // pressure
                1.0f, // size
                0,    // metaState
                1.0f, // xPrecision
                1.0f, // yPrecision
                0,    // deviceId
                0     // edgeFlags
            )
            event.source = InputDevice.SOURCE_TOUCHSCREEN

            try {
                injectInputEventMethod?.invoke(inputManager, event, 2)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                event.recycle()
            }
        }
    }
}
