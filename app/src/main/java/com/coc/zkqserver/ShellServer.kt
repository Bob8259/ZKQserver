package com.coc.zkqserver

import com.coc.zkqserver.command_handlers.FileHandler
import com.coc.zkqserver.command_handlers.TouchHandler
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.ContentType
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object ShellServer {

    private const val CLIENT_TARGET_URL = "ws://localhost:16839/zkq"
    private const val CLIENT_RETRY_DELAY_MS = 2000L

    @JvmStatic
    fun main(args: Array<String>) {
        println("--- Kotlin WebSocket Tool Started ---")

        val httpClient = HttpClient(ClientCIO) {
            install(ClientWebSockets)
        }

        runBlocking {
            // Launch the embedded WebSocket/HTTP server in a concurrent coroutine
            launch {
                embeddedServer(CIO, port = 6839) {
                    routing {
                        // HTTP GET endpoint: all command parameters are passed as URL query parameters.
                        // Example: GET /zkq?actionType=connection_test
                        get("/zkq") {
                            val params = call.request.queryParameters

                            // Build a JsonObject from all query parameters so existing handlers can reuse it directly
                            val jsonObject = JsonObject()
                            params.names().forEach { name ->
                                jsonObject.addProperty(name, params[name])
                            }

                            val actionType = params["actionType"]
                            val responseJson = when (actionType) {
                                "connection_test" -> {
                                    val response = JsonObject()
                                    response.addProperty("status", "success")
                                    response.addProperty("data", "connected")
                                    response.toString()
                                }
                                "file_action" -> FileHandler.handle(jsonObject.toString())
                                else -> TouchHandler.handle(jsonObject.toString())
                            }

                            call.respondText(responseJson, ContentType.Application.Json)
                        }
                    }
                }.start(wait = true)
            }

            // Launch the outbound WebSocket client in a concurrent coroutine
            launch {
                connectAsClient(httpClient)
            }
        }

        httpClient.close()
    }

    /**
     * Continuously attempts to connect to [CLIENT_TARGET_URL] as a WebSocket client.
     * Retries every [CLIENT_RETRY_DELAY_MS] ms on failure. Sends an announcement
     * message on the first successful connection, then dispatches incoming server
     * messages to the existing action handlers.
     */
    private suspend fun connectAsClient(client: HttpClient) {
        var firstConnection = true

        while (true) {
            try {
                println("正在尝试连接到 $CLIENT_TARGET_URL ...")

                client.webSocket(CLIENT_TARGET_URL) {
                    println("已成功连接到 $CLIENT_TARGET_URL")

                    // Send the announcement message only on the very first connection
                    if (firstConnection) {
                        firstConnection = false
                        val announcement = JsonObject().apply {
                            addProperty("actionType", "client_connected")
                            addProperty("message", "ZKQserver connected")
                        }
                        send(Frame.Text(announcement.toString()))
                        println("已发送连接公告消息")
                    }

                    // Receive and dispatch messages sent by the remote server
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            val response = dispatchClientMessage(text)
                            if (response != null) {
                                send(Frame.Text(response))
                            }
                        }
                    }

                    println("与 $CLIENT_TARGET_URL 的连接已断开")
                }
            } catch (e: Exception) {
                println("客户端连接失败: ${e.message}，${CLIENT_RETRY_DELAY_MS}ms 后重试...")
            }

            delay(CLIENT_RETRY_DELAY_MS)
        }
    }

    /**
     * Parses an incoming JSON message from the remote server and routes it to
     * the appropriate handler. Returns the JSON response string, or null if the
     * message could not be parsed.
     */
    private fun dispatchClientMessage(text: String): String? {
        return try {
            val jsonElement = JsonParser.parseString(text)
            if (!jsonElement.isJsonObject) return buildClientError("无效的 JSON 消息")

            when (jsonElement.asJsonObject.getAsJsonPrimitive("actionType")?.asString) {
                "connection_test" -> JsonObject().apply {
                    addProperty("status", "success")
                    addProperty("data", "connected")
                }.toString()
                "file_action" -> FileHandler.handle(text)
                else -> TouchHandler.handle(text)
            }
        } catch (e: Exception) {
            println("处理客户端消息时出错: ${e.message}")
            buildClientError("处理消息时出错: ${e.message}")
        }
    }

    private fun buildClientError(message: String): String =
        JsonObject().apply {
            addProperty("status", "error")
            addProperty("message", message)
        }.toString()
}
