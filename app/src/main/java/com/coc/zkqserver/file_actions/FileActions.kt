package com.coc.zkqserver.file_actions

import java.io.File
import java.io.IOException

object FileActions {
    /**
     * Read the file at [path] and return its contents as a UTF-8 String.
     * Throws IOException on error.
     */
    @Throws(IOException::class)
    fun readFile(path: String): String {
        val file = File(path)
        if (!file.exists()) throw IOException("File not found: $path")
        return file.readText(Charsets.UTF_8)
    }

    /**
     * Write [content] to the file at [path], creating parent directories if necessary.
     * Returns the number of bytes written. Throws IOException on error.
     */
    @Throws(IOException::class)
    fun writeFile(path: String, content: String): Int {
        val file = File(path)
        file.parentFile?.let { if (!it.exists()) it.mkdirs() }
        file.writeText(content, Charsets.UTF_8)
        return content.toByteArray(Charsets.UTF_8).size
    }

    /**
     * Create a new file at [path].
     * Throws IOException if the file already exists or an error occurs during creation.
     */
    @Throws(IOException::class)
    fun createFile(path: String): Boolean {
        val file = File(path)
        if (file.exists()) {
            throw IOException("File already exists: $path")
        }
        file.parentFile?.let { if (!it.exists()) it.mkdirs() }
        return file.createNewFile()
    }

    /**
     * Delete the file at [path].
     * Throws IOException if the file does not exist or an error occurs during deletion.
     */
    @Throws(IOException::class)
    fun deleteFile(path: String): Boolean {
        val file = File(path)
        if (!file.exists()) {
            throw IOException("File not found: $path")
        }
        return file.delete()
    }

    /**
     * Check if a file exists at [path].
     */
    fun checkFileExists(path: String): Boolean {
        val file = File(path)
        return file.exists()
    }

    /**
     * Copy a file from [sourcePath] to [destPath].
     */
    @Throws(IOException::class)
    fun copyFile(sourcePath: String, destPath: String): Boolean {
        val source = File(sourcePath)
        val dest = File(destPath)
        if (!source.exists()) throw IOException("Source file not found: $sourcePath")
        
        dest.parentFile?.let { if (!it.exists()) it.mkdirs() }
        source.copyTo(dest, overwrite = true)
        return true
    }

    /**
     * Rename a file from [oldPath] to [newPath].
     */
    @Throws(IOException::class)
    fun renameFile(oldPath: String, newPath: String): Boolean {
        val oldFile = File(oldPath)
        val newFile = File(newPath)
        if (!oldFile.exists()) throw IOException("Source file not found: $oldPath")
        if (newFile.exists()) throw IOException("Destination file already exists: $newPath")

        newFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
        return oldFile.renameTo(newFile)
    }
}
