package com.coc.zkqserver.system_actions

import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.net.URL

object DownloadFiles {
    fun downloadFile(url: String, savePath: String): String {
        return try {
            val urlObj = URL(url)
            val connection = urlObj.openConnection()
            connection.connect()

            val inputStream = BufferedInputStream(urlObj.openStream())
            val outputStream = FileOutputStream(savePath)

            val data = ByteArray(1024)
            var count: Int
            var total = 0L

            while (inputStream.read(data).also { count = it } != -1) {
                outputStream.write(data, 0, count)
                total += count
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            "File downloaded successfully to $savePath ($total bytes)"
        } catch (e: Exception) {
            "Error downloading file: ${e.message}"
        }
    }
}