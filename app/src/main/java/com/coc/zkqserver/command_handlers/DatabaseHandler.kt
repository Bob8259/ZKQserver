package com.coc.zkqserver.command_handlers

import com.coc.zkqserver.database_actions.Database
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object DatabaseHandler {
    fun handle(command: String): String {
        try {
            val jsonElement = JsonParser.parseString(command)
            if (!jsonElement.isJsonObject) {
                return errorResponse("Invalid JSON command")
            }
            val jsonCommand = jsonElement.asJsonObject
            val actionType = jsonCommand.getAsJsonPrimitive("actionType")?.asString

            if (actionType == "database_action") {
                val subAction = jsonCommand.getAsJsonPrimitive("subAction")?.asString
                return when (subAction) {
                    "open" -> {
                        val path = jsonCommand.getAsJsonPrimitive("path")?.asString
                            ?: return errorResponse("Path is missing")
                        Database.open(path)
                        successResponse("Database opened at $path")
                    }
                    "execute" -> {
                        val sql = jsonCommand.getAsJsonPrimitive("sql")?.asString
                            ?: return errorResponse("SQL is missing")
                        val result = Database.execute(sql)
                        successResponse(result)
                    }
                    "close" -> {
                        Database.close()
                        successResponse("Database closed")
                    }
                    else -> errorResponse("Unknown database sub-action: $subAction")
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
