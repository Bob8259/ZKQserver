package com.coc.zkqserver.command_handlers

import com.coc.zkqserver.touch_actions.Pinch
import com.coc.zkqserver.touch_actions.Touch
import org.json.JSONObject

object TouchHandler {
    fun handle(command: String) {
        try {
            val jsonCommand = JSONObject(command)
            val actionType = jsonCommand.getString("actionType")

            if (actionType == "touch_action") {
                val subAction = jsonCommand.getString("subAction")
                when (subAction) {
                    "touchdown" -> {
                        val x = jsonCommand.getDouble("x").toFloat()
                        val y = jsonCommand.getDouble("y").toFloat()
                        val id = jsonCommand.getInt("id")
                        Touch.touchDown(x, y, id)
                    }
                    "touchmove" -> {
                        val x = jsonCommand.getDouble("x").toFloat()
                        val y = jsonCommand.getDouble("y").toFloat()
                        val id = jsonCommand.getInt("id")
                        val duration = jsonCommand.optLong("duration", 150L)
                        val jitter = jsonCommand.optBoolean("jitter", true)
                        Touch.touchMove(x, y, id, duration, jitter)
                    }
                    "touchup" -> {
                        val id = jsonCommand.getInt("id")
                        Touch.touchUp(id)
                    }
                    "pinchin" -> {
                        val x1 = jsonCommand.getDouble("x1").toFloat()
                        val y1 = jsonCommand.getDouble("y1").toFloat()
                        val x2 = jsonCommand.getDouble("x2").toFloat()
                        val y2 = jsonCommand.getDouble("y2").toFloat()
                        val finalX = jsonCommand.getDouble("finalX").toFloat()
                        val finalY = jsonCommand.getDouble("finalY").toFloat()
                        val duration = jsonCommand.optLong("duration", 150L)
                        val jitter = jsonCommand.optBoolean("jitter", true)
                        Pinch.pinchIn(x1, y1, x2, y2, finalX, finalY, duration, jitter)
                    }
                    "pinchout" -> {
                        val x1 = jsonCommand.getDouble("x1").toFloat()
                        val y1 = jsonCommand.getDouble("y1").toFloat()
                        val x2 = jsonCommand.getDouble("x2").toFloat()
                        val y2 = jsonCommand.getDouble("y2").toFloat()
                        val finalX = jsonCommand.getDouble("finalX").toFloat()
                        val finalY = jsonCommand.getDouble("finalY").toFloat()
                        val duration = jsonCommand.optLong("duration", 150L)
                        val jitter = jsonCommand.optBoolean("jitter", true)
                        Pinch.pinchOut(x1, y1, x2, y2, finalX, finalY, duration, jitter)
                    }
                    else -> {
                        println("Unknown touch sub-action: $subAction")
                    }
                }
            } else {
                println("Unknown actionType: $actionType")
            }
        } catch (e: Exception) {
            println("Error parsing command: $command - ${e.message}")
        }
    }
}
