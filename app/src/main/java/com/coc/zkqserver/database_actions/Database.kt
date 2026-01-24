package com.coc.zkqserver.database_actions

import android.database.sqlite.SQLiteDatabase
import android.database.Cursor
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object Database {
    private var database: SQLiteDatabase? = null

    fun open(path: String) {
        val dbFile = File(path)
        dbFile.parentFile?.mkdirs()
        database = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    }

    fun execute(sql: String): String {
        val db = database ?: return "Error: Database not opened"
        return try {
            if (sql.trim().uppercase().startsWith("SELECT")) {
                val cursor = db.rawQuery(sql, null)
                val result = cursorToJSON(cursor)
                cursor.close()
                result
            } else {
                db.execSQL(sql)
                "Success"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun cursorToJSON(cursor: Cursor): String {
        val resultSet = JSONArray()
        cursor.moveToFirst()
        while (!cursor.isAfterLast) {
            val totalColumn = cursor.columnCount
            val rowObject = JSONObject()
            for (i in 0 until totalColumn) {
                if (cursor.getColumnName(i) != null) {
                    try {
                        when (cursor.getType(i)) {
                            Cursor.FIELD_TYPE_INTEGER -> rowObject.put(cursor.getColumnName(i), cursor.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> rowObject.put(cursor.getColumnName(i), cursor.getDouble(i))
                            Cursor.FIELD_TYPE_STRING -> rowObject.put(cursor.getColumnName(i), cursor.getString(i))
                            Cursor.FIELD_TYPE_BLOB -> rowObject.put(cursor.getColumnName(i), cursor.getBlob(i).toString())
                            else -> rowObject.put(cursor.getColumnName(i), cursor.getString(i))
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
            resultSet.put(rowObject)
            cursor.moveToNext()
        }
        return resultSet.toString()
    }

    fun close() {
        database?.close()
        database = null
    }
}
