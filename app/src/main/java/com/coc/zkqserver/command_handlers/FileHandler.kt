package com.coc.zkqserver.command_handlers

import com.coc.zkqserver.file_actions.FileActions
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object FileHandler {
    fun handle(command: String): String {
        val response = JsonObject()
        try {
            val jsonElement = JsonParser.parseString(command)
            if (!jsonElement.isJsonObject) {
                return errorResponse("Invalid JSON command")
            }
            val jsonCommand = jsonElement.asJsonObject
            val actionType = jsonCommand.getAsJsonPrimitive("actionType")?.asString

            if (actionType == "file_action") {
                val subAction = jsonCommand.getAsJsonPrimitive("subAction")?.asString
                val path = jsonCommand.getAsJsonPrimitive("path")?.asString
                    ?: return errorResponse("Path is missing")

                return when (subAction) {
                    "read" -> {
                        val content = FileActions.readFile(path)
                        successResponse(content)
                    }
                    "write" -> {
                        val content = jsonCommand.getAsJsonPrimitive("content")?.asString
                            ?: return errorResponse("Content is missing for write action")
                        val bytesWritten = FileActions.writeFile(path, content)
                        successResponse(bytesWritten.toString())
                    }
                    "create" -> {
                        FileActions.createFile(path)
                        successResponse("File created successfully")
                    }
                    "delete" -> {
                        FileActions.deleteFile(path)
                        successResponse("File deleted successfully")
                    }
                    "check_exists" -> {
                        val exists = FileActions.checkFileExists(path)
                        successResponse(exists.toString())
                    }
                    "copy" -> {
                        val destPath = jsonCommand.getAsJsonPrimitive("destPath")?.asString
                            ?: return errorResponse("destPath is missing for copy action")
                        val result = FileActions.copyFile(path, destPath)
                        successResponse(result.toString())
                    }
                    "rename" -> {
                        val newPath = jsonCommand.getAsJsonPrimitive("newPath")?.asString
                            ?: return errorResponse("newPath is missing for rename action")
                        val result = FileActions.renameFile(path, newPath)
                        successResponse(result.toString())
                    }
                    else -> errorResponse("Unknown file sub-action: $subAction")
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
