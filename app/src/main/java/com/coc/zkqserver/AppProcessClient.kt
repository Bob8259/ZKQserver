package com.coc.zkqserver

import com.coc.zkqserver.command_handlers.FileHandler
import com.coc.zkqserver.command_handlers.TouchHandler
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * 客户端入口点，通过 app_process 启动时主动连接到 ws://localhost:16839/zkq。
 * 连接失败时每 2 秒重试，直到成功为止。
 */
object AppProcessClient {

    private const val SERVER_URL = "ws://localhost:16839/zkq"
    private const val RETRY_DELAY_MS = 2000L

    @JvmStatic
    fun main(args: Array<String>) {
        println("--- ZKQserver 客户端模式已启动 ---")

        val client = HttpClient(CIO) {
            install(WebSockets)
        }

        runBlocking {
            connectWithRetry(client)
        }

        client.close()
    }

    /**
     * Keeps attempting to connect to the server every [RETRY_DELAY_MS] ms until
     * a connection is established. On first successful connection, sends the
     * announcement message, then enters the message-handling loop.
     */
    private suspend fun connectWithRetry(client: HttpClient) {
        var firstConnection = true

        while (true) {
            try {
                println("正在尝试连接到 $SERVER_URL ...")

                client.webSocket(SERVER_URL) {
                    println("已连接到服务器")

                    // Send the announcement message on the very first connection
                    if (firstConnection) {
                        firstConnection = false
                        val announcement = JsonObject().apply {
                            addProperty("actionType", "client_connected")
                            addProperty("message", "ZKQserver connected")
                        }
                        send(Frame.Text(announcement.toString()))
                        println("已发送连接公告消息")
                    }

                    // Handle incoming frames from the server
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            val responseJson = handleIncomingMessage(text)
                            if (responseJson != null) {
                                send(Frame.Text(responseJson))
                            }
                        }
                    }

                    println("服务器连接已断开")
                }
            } catch (e: Exception) {
                println("连接失败: ${e.message}，${RETRY_DELAY_MS}ms 后重试...")
            }

            delay(RETRY_DELAY_MS)
        }
    }

    /**
     * Parses the incoming JSON message from the server and dispatches it to the
     * appropriate handler. Returns the JSON response string to send back, or null
     * if the message could not be parsed.
     */
    private fun handleIncomingMessage(text: String): String? {
        return try {
            val jsonElement = JsonParser.parseString(text)
            if (!jsonElement.isJsonObject) {
                buildErrorResponse("无效的 JSON 消息")
            } else {
                val actionType = jsonElement.asJsonObject
                    .getAsJsonPrimitive("actionType")?.asString
                when (actionType) {
                    "connection_test" -> {
                        // Respond with a simple success to acknowledge the ping
                        val response = JsonObject().apply {
                            addProperty("status", "success")
                            addProperty("data", "connected")
                        }
                        response.toString()
                    }
                    "file_action" -> FileHandler.handle(text)
                    else -> TouchHandler.handle(text)
                }
            }
        } catch (e: Exception) {
            println("处理消息时出错: ${e.message}")
            buildErrorResponse("处理消息时出错: ${e.message}")
        }
    }

    private fun buildErrorResponse(message: String): String {
        return JsonObject().apply {
            addProperty("status", "error")
            addProperty("message", message)
        }.toString()
    }
}
