package com.coc.zkqserver

import com.coc.zkqserver.command_handlers.FileHandler
import com.coc.zkqserver.command_handlers.TouchHandler
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.runBlocking

object ShellServer {
    @JvmStatic
    fun main(args: Array<String>) {
        println("--- Kotlin WebSocket Tool Started ---")

        runBlocking {
            embeddedServer(CIO, port = 6839) {
                install(WebSockets) {
                    pingPeriodMillis = 15000L
                    timeoutMillis = 15000L
                    maxFrameSize = 20 * 1024 * 1024L
                    masking = false
                }
                routing {
                    webSocket("/zkq") {
                        println("Client connected")
                        try {
                            for (frame in incoming) {
                                if (frame is Frame.Text) {
                                    val text = frame.readText()
                                    try {
                                        val jsonElement = JsonParser.parseString(text)
                                        if (jsonElement.isJsonObject) {
                                            val actionType =
                                                jsonElement.asJsonObject.getAsJsonPrimitive("actionType")?.asString
                                            when (actionType) {
                                                "connection_test" -> {
                                                    val response = JsonObject()
                                                    response.addProperty("status", "success")
                                                    response.addProperty("data", "connected")
                                                    send(Frame.Text(response.toString()))
                                                }
                                                "file_action" -> {
                                                    val response = FileHandler.handle(text)
                                                    send(Frame.Text(response))
                                                }
                                                else -> {
                                                    // Removed clipboard_action; defaults to TouchHandler
                                                    val response = TouchHandler.handle(text)
                                                    send(Frame.Text(response))
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        println("Error handling frame: ${e.message}")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            println("Connection error: ${e.message}")
                        } finally {
                            println("Client disconnected")
                        }
                    }
                }
            }.start(wait = true)
        }
    }
}
