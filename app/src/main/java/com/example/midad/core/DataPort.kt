package com.example.midad.core

/**
 * ناقل البيانات — صيغة نصية بسيطة وقابلة للقراءة الآدمية للنسخ الاحتياطي والتصدير.
 *
 * الصيغة:
 *   @MIDAD-DATA 1
 *   ##party
 *   id|name|phone
 *   p1|أحمد|777000111
 *
 * الفوائد: لا مكتبة JSON، تُفتح بأي محرّر، ويمكن فكّها بأمان عند الاستعادة.
 */
data class DataTable(
    val name: String,
    val columns: List<String>,
    val rows: List<List<String>>,
) {
    fun value(row: List<String>, column: String): String {
        val i = columns.indexOf(column)
        return if (i in row.indices) row[i] else ""
    }
}

object DataPort {

    const val MAGIC = "@MIDAD-DATA 1"
    private const val SEP = '|'

    private fun esc(v: String): String = buildString {
        v.forEach {
            when (it) {
                '\\' -> append("\\\\")
                SEP -> append("\\p")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(it)
            }
        }
    }

    private fun unesc(v: String): String = buildString {
        var i = 0
        while (i < v.length) {
            val ch = v[i]
            if (ch == '\\' && i + 1 < v.length) {
                when (v[i + 1]) {
                    'p' -> append(SEP)
                    'n' -> append('\n')
                    'r' -> append('\r')
                    '\\' -> append('\\')
                    else -> append(v[i + 1])
                }
                i += 2
            } else { append(ch); i++ }
        }
    }

    fun write(tables: List<DataTable>): String = buildString {
        append(MAGIC).append('\n')
        tables.forEach { t ->
            append("##").append(t.name).append('\n')
            append(t.columns.joinToString(SEP.toString()) { esc(it) }).append('\n')
            t.rows.forEach { r ->
                append(r.joinToString(SEP.toString()) { esc(it) }).append('\n')
            }
        }
    }

    fun read(text: String): List<DataTable> {
        val lines = text.replace("\r\n", "\n").lines()
        if (lines.firstOrNull()?.trim() != MAGIC)
            throw AccountingException("BAD_FORMAT", "الملف ليس ملف بيانات مِداد")
        val out = ArrayList<DataTable>()
        var i = 1
        while (i < lines.size) {
            val line = lines[i]
            if (!line.startsWith("##")) { i++; continue }
            val name = line.removePrefix("##").trim()
            i++
            if (i >= lines.size) throw AccountingException("BAD_FORMAT", "الجلسة «$name» بلا أعمدة")
            val cols = splitRow(lines[i])
            i++
            val rows = ArrayList<List<String>>()
            while (i < lines.size && !lines[i].startsWith("##") && lines[i].isNotEmpty()) {
                rows += splitRow(lines[i])
                i++
            }
            out += DataTable(name, cols, rows)
        }
        return out
    }

    private fun splitRow(line: String): List<String> {
        val cells = ArrayList<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch == '\\' && i + 1 < line.length) {
                sb.append(ch).append(line[i + 1]); i += 2
            } else if (ch == SEP) {
                cells += unesc(sb.toString()); sb.clear(); i++
            } else { sb.append(ch); i++ }
        }
        cells += unesc(sb.toString())
        return cells
    }

    fun of(tables: List<DataTable>, name: String): DataTable? = tables.firstOrNull { it.name == name }

    /** عدّادات تُعرض في شاشة النسخ الاحتياطي قبل التصدير */
    fun summary(tables: List<DataTable>): Map<String, Int> =
        tables.filter { it.rows.isNotEmpty() }.associate { it.name to it.rows.size }
}
