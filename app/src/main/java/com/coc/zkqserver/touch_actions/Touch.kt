package com.coc.zkqserver.touch_actions

import android.os.IBinder
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import java.lang.reflect.Method
import java.util.Arrays

// 1. 定义一个类似 Scrcpy 的 Pointer 类，管理单个点的属性
class Pointer {
    // 对应 Scrcpy 的 Point 逻辑
    var x: Float = 0f
    var y: Float = 0f
    var pressure: Float = 0f
    var up: Boolean = false // 标记是否处于抬起状态

    fun setPoint(x: Float, y: Float) {
        this.x = x
        this.y = y
    }
}

class Touch {
    companion object {
        internal var inputManager: Any? = null
        internal var injectInputEventMethod: Method? = null

        // 2. 引入 MAX_POINTERS 限制，模仿 Scrcpy 的 PointersState
        private const val MAX_POINTERS = 10

        // 对应 Scrcpy 的 PointersState 逻辑
        private val pointers = Array(MAX_POINTERS) { Pointer() }
        private val pointerProperties = Array(MAX_POINTERS) { MotionEvent.PointerProperties() }
        private val pointerCoords = Array(MAX_POINTERS) { MotionEvent.PointerCoords() }

        // 映射表：外部 ID -> 内部 Index (0-9)
        private val pointerIdToLocalIndex = IntArray(MAX_POINTERS) { -1 }
        private var pointersCount = 0

        init {
            // 初始化 PointerProperties，避免在循环中重复创建 (Scrcpy 优化)
            for (i in 0 until MAX_POINTERS) {
                val props = MotionEvent.PointerProperties()
                props.toolType = MotionEvent.TOOL_TYPE_FINGER
                pointerProperties[i] = props

                val coords = MotionEvent.PointerCoords()
                pointerCoords[i] = coords
            }

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
            // 逻辑修正：映射 ID
            var index = getLocalIndex(id)
            if (index == -1) {
                index = allocLocalIndex(id)
                if (index == -1) return // 超过最大触控点数
            }

            val pointer = pointers[index]
            pointer.setPoint(x, y)
            pointer.pressure = 1.0f // 按下时压力为 1
            pointer.up = false

            val now = SystemClock.uptimeMillis()

            // 计算 Action
            val action = if (pointersCount == 1) {
                MotionEvent.ACTION_DOWN
            } else {
                // 计算该点在当前 active 点中的视觉索引 (逻辑较复杂，简化处理，Scrcpy 是通过重新排列 props 实现的)
                // 这里我们直接用 pointersCount - 1 近似，或者严格重排。
                // 为了严谨，下面 inject 时会重组数组。
                val actionIndex = getActionIndex(index)
                MotionEvent.ACTION_POINTER_DOWN or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            }

            injectMotionEvent(action, now, now)
        }

        @Synchronized
        fun touchMove(x: Float, y: Float, id: Int) {
            val index = getLocalIndex(id)
            if (index == -1) return

            val pointer = pointers[index]
            pointer.setPoint(x, y)
            pointer.pressure = 1.0f
            pointer.up = false

            val now = SystemClock.uptimeMillis()
            injectMotionEvent(MotionEvent.ACTION_MOVE, now, now) // Move 时间应该使用 downTime 吗？Scrcpy 其实是用最新的 now
        }

        @Synchronized
        fun touchUp(id: Int) {
            val index = getLocalIndex(id)
            if (index == -1) return

            val pointer = pointers[index]
            // 致命修正 1：Scrcpy 在 UP 时压力设为 0
            pointer.pressure = 0.0f
            pointer.up = true

            val now = SystemClock.uptimeMillis()
            val actionIndex = getActionIndex(index)

            val action = if (pointersCount == 1) {
                MotionEvent.ACTION_UP
            } else {
                MotionEvent.ACTION_POINTER_UP or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            }

            injectMotionEvent(action, now, now)

            // 清理 ID
            freeLocalIndex(index)
        }

        // 核心：复刻 Scrcpy 的 update 逻辑
        private fun injectMotionEvent(action: Int, downTime: Long, eventTime: Long) {
            // 重新整理 props 和 coords 数组，使其紧凑
            var currentCount = 0
            // 这里的逻辑必须保证 props 数组中的顺序与 pointerIdToLocalIndex 中的有效位一致
            // Scrcpy 是通过遍历所有可能的 id 来实现的

            val finalProps = ArrayList<MotionEvent.PointerProperties>()
            val finalCoords = ArrayList<MotionEvent.PointerCoords>()

            for (i in 0 until MAX_POINTERS) {
                if (pointerIdToLocalIndex[i] != -1) { // 这是一个活跃的点
                    val props = pointerProperties[i]
                    val coords = pointerCoords[i]
                    val pointer = pointers[i]

                    props.id = i // 使用本地 ID (0-9)

                    coords.x = pointer.x
                    coords.y = pointer.y
                    coords.pressure = pointer.pressure
                    coords.size = 1.0f

                    finalProps.add(props)
                    finalCoords.add(coords)
                    currentCount++
                }
            }

            if (finalProps.isEmpty()) return

            val event = MotionEvent.obtain(
                downTime, // 注意：Scrcpy 会记录第一个点的 downTime，这里简化使用传入值
                eventTime,
                action,
                finalProps.size,
                finalProps.toTypedArray(),
                finalCoords.toTypedArray(),
                0, 0, 1.0f, 1.0f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
            )

            try {
                injectInputEventMethod?.invoke(inputManager, event, 0) // ASYNC
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                event.recycle()
            }
        }

        // --- 辅助状态管理 ---

        private fun getLocalIndex(externalId: Int): Int {
            // 简单哈希查找，Scrcpy 用的是遍历
            for (i in 0 until MAX_POINTERS) {
                if (pointerIdToLocalIndex[i] == externalId) return i
            }
            return -1
        }

        private fun allocLocalIndex(externalId: Int): Int {
            for (i in 0 until MAX_POINTERS) {
                if (pointerIdToLocalIndex[i] == -1) {
                    pointerIdToLocalIndex[i] = externalId
                    pointersCount++
                    return i
                }
            }
            return -1
        }

        private fun freeLocalIndex(index: Int) {
            if (pointerIdToLocalIndex[index] != -1) {
                pointerIdToLocalIndex[index] = -1
                pointersCount--
            }
        }

        // 获取当前点在所有活跃点中的排位（用于计算 ACTION_POINTER_INDEX）
        private fun getActionIndex(localIndex: Int): Int {
            var actionIndex = 0
            for (i in 0 until localIndex) {
                if (pointerIdToLocalIndex[i] != -1) {
                    actionIndex++
                }
            }
            return actionIndex
        }
    }
}