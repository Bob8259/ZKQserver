package com.coc.zkqserver.command_handlers

//import com.coc.zkqserver.touch_actions.Pinch
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
                        Touch.touchMove(x, y, id)
                        return successResponse("null")
                    }
                    "touchup" -> {
                        val id = jsonCommand.getAsJsonPrimitive("id").asInt
                        Touch.touchUp(id)
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
