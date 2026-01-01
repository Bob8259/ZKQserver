package com.coc.zkqserver.command_handlers

import com.coc.zkqserver.touch_actions.Pinch
import com.coc.zkqserver.touch_actions.Touch
import com.google.gson.JsonParser

object TouchHandler {
    fun handle(command: String) {
        try {
            val jsonElement = JsonParser.parseString(command)
            if (!jsonElement.isJsonObject) {
                println("Invalid JSON command: $command")
                return
            }
            val jsonCommand = jsonElement.asJsonObject
            val actionType = jsonCommand.getAsJsonPrimitive("actionType")?.asString

            if (actionType == "touch_action") {
                when (val subAction = jsonCommand.getAsJsonPrimitive("subAction")?.asString) {
                    "touchdown" -> {
                        val x = jsonCommand.getAsJsonPrimitive("x").asDouble.toFloat()
                        val y = jsonCommand.getAsJsonPrimitive("y").asDouble.toFloat()
                        val id = jsonCommand.getAsJsonPrimitive("id").asInt
                        Touch.touchDown(x, y, id)
                    }
                    "touchmove" -> {
                        val x = jsonCommand.getAsJsonPrimitive("x").asDouble.toFloat()
                        val y = jsonCommand.getAsJsonPrimitive("y").asDouble.toFloat()
                        val id = jsonCommand.getAsJsonPrimitive("id").asInt
                        val duration = if (jsonCommand.has("duration")) jsonCommand.getAsJsonPrimitive("duration").asLong else 150L
                        val jitter = if (jsonCommand.has("jitter")) jsonCommand.getAsJsonPrimitive("jitter").asBoolean else true
                        Touch.touchMove(x, y, id, duration, jitter)
                    }
                    "touchup" -> {
                        val id = jsonCommand.getAsJsonPrimitive("id").asInt
                        Touch.touchUp(id)
                    }
                    "pinchin" -> {
                        val x1 = jsonCommand.getAsJsonPrimitive("x1").asDouble.toFloat()
                        val y1 = jsonCommand.getAsJsonPrimitive("y1").asDouble.toFloat()
                        val x2 = jsonCommand.getAsJsonPrimitive("x2").asDouble.toFloat()
                        val y2 = jsonCommand.getAsJsonPrimitive("y2").asDouble.toFloat()
                        val finalX = jsonCommand.getAsJsonPrimitive("finalX").asDouble.toFloat()
                        val finalY = jsonCommand.getAsJsonPrimitive("finalY").asDouble.toFloat()
                        val duration = if (jsonCommand.has("duration")) jsonCommand.getAsJsonPrimitive("duration").asLong else 150L
                        val jitter = if (jsonCommand.has("jitter")) jsonCommand.getAsJsonPrimitive("jitter").asBoolean else true
                        Pinch.pinchIn(x1, y1, x2, y2, finalX, finalY, duration, jitter)
                    }
                    "pinchout" -> {
                        val x1 = jsonCommand.getAsJsonPrimitive("x1").asDouble.toFloat()
                        val y1 = jsonCommand.getAsJsonPrimitive("y1").asDouble.toFloat()
                        val x2 = jsonCommand.getAsJsonPrimitive("x2").asDouble.toFloat()
                        val y2 = jsonCommand.getAsJsonPrimitive("y2").asDouble.toFloat()
                        val finalX = jsonCommand.getAsJsonPrimitive("finalX").asDouble.toFloat()
                        val finalY = jsonCommand.getAsJsonPrimitive("finalY").asDouble.toFloat()
                        val duration = if (jsonCommand.has("duration")) jsonCommand.getAsJsonPrimitive("duration").asLong else 150L
                        val jitter = if (jsonCommand.has("jitter")) jsonCommand.getAsJsonPrimitive("jitter").asBoolean else true
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
