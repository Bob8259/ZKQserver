package com.coc.zkqserver

import com.coc.zkqserver.command_handlers.TouchHandler
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
                                    TouchHandler.handle(text)
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
