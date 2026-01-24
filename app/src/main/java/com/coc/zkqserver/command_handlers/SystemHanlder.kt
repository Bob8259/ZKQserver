package com.coc.zkqserver.command_handlers

import com.coc.zkqserver.system_actions.DownloadFiles
import com.coc.zkqserver.system_actions.SHA256
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object SystemHanlder {
    fun handle(command: String): String {
        try {
            val jsonElement = JsonParser.parseString(command)
            if (!jsonElement.isJsonObject) {
                return errorResponse("Invalid JSON command")
            }
            val jsonCommand = jsonElement.asJsonObject
            val actionType = jsonCommand.getAsJsonPrimitive("actionType")?.asString

            if (actionType == "system_action") {
                val subAction = jsonCommand.getAsJsonPrimitive("subAction")?.asString
                return when (subAction) {
                    "download" -> {
                        val url = jsonCommand.getAsJsonPrimitive("url")?.asString
                            ?: return errorResponse("URL is missing")
                        val savePath = jsonCommand.getAsJsonPrimitive("savePath")?.asString
                            ?: return errorResponse("savePath is missing")
                        val result = DownloadFiles.downloadFile(url, savePath)
                        successResponse(result)
                    }
                    "sha256" -> {
                        val input = jsonCommand.getAsJsonPrimitive("input")?.asString
                            ?: return errorResponse("input is missing")
                        val result = SHA256.sha256(input)
                        successResponse(result)
                    }
                    else -> errorResponse("Unknown system sub-action: $subAction")
                }
            } else {
                return errorResponse("Unknown actionType: $actionType")
            }
        } catch (e: Exception) {
            return errorResponse("Error: ${e.message}")
        }
    }

    private fun successResponse(data: String): String {
        val response = JsonObject()
        response.addProperty("status", "success")
        response.addProperty("data", data)
        return response.toString()
    }

    private fun errorResponse(message: String): String {
        val response = JsonObject()
        response.addProperty("status", "error")
        response.addProperty("message", message)
        return response.toString()
    }
}
