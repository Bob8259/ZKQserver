package com.coc.zkqserver

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
                    maxFrameSize = Long.MAX_VALUE
                    masking = false
                }
                routing {
                    webSocket("/zkq") {
                        println("Client connected")
                        try {
                            for (frame in incoming) {
                                if (frame is Frame.Text) {
                                    val text = frame.readText()
                                    handleCommand(text)
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

    private fun handleCommand(command: String) {
        // Expected format: action,from_x,from_y,to_x,to_y,time
        val parts = command.split(",")
        if (parts.size >= 6) {
            val action = parts[0]
            val fromX = parts[1]
            val fromY = parts[2]
            val toX = parts[3]
            val toY = parts[4]
            val time = parts[5]

            if (action == "touch_action") {
                Touch.swipe(fromX.toFloat(), fromY.toFloat(), toX.toFloat(), toY.toFloat(), time.toLong())
            } else {
                println("Unknown command: $action")
            }
        } else {
            println("Invalid command format. Expected 6 comma-separated values for touch_action.")
        }
    }
}
