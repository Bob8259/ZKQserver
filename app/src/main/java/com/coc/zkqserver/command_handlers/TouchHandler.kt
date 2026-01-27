package com.coc.zkqserver.command_handlers

import com.coc.zkqserver.touch_actions.Pinch
import com.coc.zkqserver.touch_actions.Touch
import com.google.gson.JsonParser

object TouchHandler {
    fun handle(command: String): String {
        try {
            val jsonElement = JsonParser.parseString(command)
            if (!jsonElement.isJsonObject) {
                return errorResponse("Invalid JSON command")
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
                        return successResponse("null")
                    }
                    "touchmove" -> {
                        val x = jsonCommand.getAsJsonPrimitive("x").asDouble.toFloat()
                        val y = jsonCommand.getAsJsonPrimitive("y").asDouble.toFloat()
                        val id = jsonCommand.getAsJsonPrimitive("id").asInt
                        val duration = if (jsonCommand.has("duration")) jsonCommand.getAsJsonPrimitive("duration").asLong else 150L
                        val jitter = if (jsonCommand.has("jitter")) jsonCommand.getAsJsonPrimitive("jitter").asBoolean else true
                        Touch.touchMove(x, y, id, duration, jitter)
                        return successResponse("null")
                    }
                    "touchup" -> {
                        val id = jsonCommand.getAsJsonPrimitive("id").asInt
                        Touch.touchUp(id)
                        return successResponse("null")
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
                        return successResponse("null")
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
                        return successResponse("null")
                    }
                    else -> {
                        return errorResponse("Unknown touch sub-action: $subAction")
                    }
                }
            } else {
                return errorResponse("Unknown actionType: $actionType")
            }
        } catch (e: Exception) {
            return errorResponse("Error parsing command: ${e.message}")
        }
    }

    private fun successResponse(data: String): String {
        val response = com.google.gson.JsonObject()
        response.addProperty("status", "success")
        response.addProperty("data", data)
        return response.toString()
    }

    private fun errorResponse(message: String): String {
        val response = com.google.gson.JsonObject()
        response.addProperty("status", "error")
        response.addProperty("message", message)
        return response.toString()
    }
}
