package com.coc.zkqserver

import android.net.LocalSocket
import android.net.LocalSocketAddress
import com.coc.zkqserver.command_handlers.FileHandler
import com.coc.zkqserver.command_handlers.TouchHandler
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Sole entry point for the ZKQserver client.
 *
 * The process opens no listening socket. It dials the upstream control server
 * over a Unix domain socket and executes the JSON commands that server sends.
 *
 * Launch:
 * ```
 * app_process /data/local/tmp com.coc.zkqserver.AppProcessClient [socket]
 * ```
 *
 * `socket` is optional and defaults to [DEFAULT_SOCKET_NAME]. A value starting
 * with `/` is used as a filesystem socket path; any other value is used as an
 * abstract namespace name.
 *
 * Wire format: newline-delimited JSON (NDJSON). Every frame is exactly one JSON
 * object followed by a single `\n` byte, in both directions. This replaces the
 * WebSocket text frames used by earlier versions - WebSocket is an HTTP upgrade
 * over TCP and therefore cannot be carried by a Unix domain socket.
 */
object AppProcessClient {

    private const val DEFAULT_SOCKET_NAME = "zkqRootServer"
    private const val RETRY_DELAY_MS = 2000L
    private const val MAX_FRAME_BYTES = 1024 * 1024
    private const val FRAME_DELIMITER = '\n'

    @JvmStatic
    fun main(args: Array<String>) {
        val socketName = args.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_SOCKET_NAME
        val address = resolveAddress(socketName)

        println("--- ZKQserver client started ---")
        println("Upstream socket: $socketName (${address.namespace})")

        connectWithRetry(address)
    }

    /**
     * Maps a socket name to a [LocalSocketAddress].
     *
     * Android exposes Unix domain sockets only through `android.net.LocalSocket`,
     * which distinguishes the abstract namespace from the filesystem namespace.
     * An absolute path selects the filesystem namespace; a bare name selects the
     * abstract namespace, which needs no filesystem permissions and disappears
     * automatically when the process exits.
     */
    private fun resolveAddress(socketName: String): LocalSocketAddress {
        val namespace = if (socketName.startsWith("/")) {
            LocalSocketAddress.Namespace.FILESYSTEM
        } else {
            LocalSocketAddress.Namespace.ABSTRACT
        }
        return LocalSocketAddress(socketName, namespace)
    }

    /**
     * Reconnects forever, waiting [RETRY_DELAY_MS] between attempts. The
     * announcement frame is sent once, on the first successful connection, and is
     * not repeated on later reconnections.
     */
    private fun connectWithRetry(address: LocalSocketAddress) {
        var announced = false

        while (true) {
            val socket = LocalSocket()
            try {
                println("Connecting to ${address.name} ...")
                socket.connect(address)
                println("Connected")

                val input = BufferedInputStream(socket.inputStream)
                val output = BufferedOutputStream(socket.outputStream)

                if (!announced) {
                    announced = true
                    writeFrame(output, buildAnnouncement())
                    println("Announcement frame sent")
                }

                readFrames(input, output)
                println("Upstream closed the connection")
            } catch (e: Exception) {
                println("Connection failed: ${e.message}; retrying in ${RETRY_DELAY_MS}ms ...")
            } finally {
                closeQuietly(socket)
            }

            Thread.sleep(RETRY_DELAY_MS)
        }
    }

    /**
     * Reads delimited frames until the peer closes the socket, dispatching each
     * one and writing back its response. Returns when the stream reaches EOF.
     */
    private fun readFrames(input: InputStream, output: OutputStream) {
        val buffer = ByteArrayOutputStream()
        var announcedTooLarge = false

        while (true) {
            val byte = input.read()
            if (byte == -1) return

            if (byte == FRAME_DELIMITER.code) {
                val payload = String(buffer.toByteArray(), Charsets.UTF_8)
                buffer.reset()
                announcedTooLarge = false

                if (payload.isBlank()) continue

                val response = handleIncomingMessage(payload)
                if (response != null) writeFrame(output, response)
            } else {
                if (buffer.size() >= MAX_FRAME_BYTES) {
                    // Drop the rest of this frame instead of growing without bound.
                    if (!announcedTooLarge) {
                        announcedTooLarge = true
                        println("Dropping frame larger than $MAX_FRAME_BYTES bytes")
                    }
                    continue
                }
                buffer.write(byte)
            }
        }
    }

    /** Writes one JSON frame followed by the delimiter and flushes it. */
    private fun writeFrame(output: OutputStream, json: String) {
        output.write(json.toByteArray(Charsets.UTF_8))
        output.write(FRAME_DELIMITER.code)
        output.flush()
    }

    private fun buildAnnouncement(): String = JsonObject().apply {
        addProperty("actionType", "client_connected")
        addProperty("message", "ZKQserver connected")
    }.toString()

    /**
     * Parses an incoming JSON command from the upstream server and routes it to
     * the matching handler. Always returns a response frame; parse failures are
     * reported back to the caller as an error object.
     */
    private fun handleIncomingMessage(text: String): String? {
        return try {
            val jsonElement = JsonParser.parseString(text)
            if (!jsonElement.isJsonObject) {
                buildErrorResponse("Invalid JSON message")
            } else {
                val actionType = jsonElement.asJsonObject
                    .getAsJsonPrimitive("actionType")?.asString
                when (actionType) {
                    "connection_test" -> JsonObject().apply {
                        addProperty("status", "success")
                        addProperty("data", "connected")
                    }.toString()
                    "file_action" -> FileHandler.handle(text)
                    else -> TouchHandler.handle(text)
                }
            }
        } catch (e: Exception) {
            println("Failed to handle message: ${e.message}")
            buildErrorResponse("Failed to handle message: ${e.message}")
        }
    }

    private fun buildErrorResponse(message: String): String = JsonObject().apply {
        addProperty("status", "error")
        addProperty("message", message)
    }.toString()

    private fun closeQuietly(socket: LocalSocket) {
        try {
            socket.close()
        } catch (_: Exception) {
            // Nothing useful to do while tearing down a broken connection.
        }
    }
}
