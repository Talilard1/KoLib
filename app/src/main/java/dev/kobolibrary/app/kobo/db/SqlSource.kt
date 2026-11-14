package dev.kobolibrary.app.kobo.db

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.util.TreeMap

/**
 * The smallest possible view of a SQLite database: run a query, get rows back.
 * The Kobo-reading logic is written against this so it can be tested on a desktop JVM.
 */
interface SqlSource {
    fun query(sql: String, args: List<String> = emptyList()): List<SqlRow>
}

/** One result row. Column lookups ignore case, as SQLite does. */
class SqlRow(values: Map<String, Any?>) {
    private val map = TreeMap<String, Any?>(String.CASE_INSENSITIVE_ORDER).apply { putAll(values) }

    fun has(column: String): Boolean = map.containsKey(column)

    fun string(column: String): String? = when (val v = map[column]) {
        null, is ByteArray -> null
        else -> v.toString()
    }

    fun long(column: String): Long? = when (val v = map[column]) {
        null -> null
        is Number -> v.toLong()
        is Boolean -> if (v) 1L else 0L
        is String -> v.trim().toLongOrNull() ?: v.trim().toDoubleOrNull()?.toLong()
        else -> null
    }

    fun double(column: String): Double? = when (val v = map[column]) {
        null -> null
        is Number -> v.toDouble()
        is String -> v.trim().toDoubleOrNull()
        else -> null
    }

    /** Kobo booleans may be stored as 'true'/'false' text or as 1/0, even within one column. */
    fun bool(column: String): Boolean? = when (val v = map[column]) {
        null -> null
        is Boolean -> v
        is Number -> v.toLong() != 0L
        is String -> when (v.trim().lowercase()) {
            "true", "1" -> true
            "false", "0", "" -> false
            else -> null
        }
        else -> null
    }
}

class AndroidSqlSource(private val db: SQLiteDatabase) : SqlSource {
    override fun query(sql: String, args: List<String>): List<SqlRow> {
        db.rawQuery(sql, args.toTypedArray()).use { c ->
            val names = c.columnNames
            val rows = ArrayList<SqlRow>()
            while (c.moveToNext()) {
                val values = HashMap<String, Any?>(names.size)
                for (i in names.indices) {
                    values[names[i]] = when (c.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                        Cursor.FIELD_TYPE_BLOB -> c.getBlob(i)
                        else -> c.getString(i)
                    }
                }
                rows.add(SqlRow(values))
            }
            return rows
        }
    }
}
