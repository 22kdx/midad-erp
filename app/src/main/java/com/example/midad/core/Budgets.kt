package com.example.midad.core

/**
 * الموازنات التقديرية مقابل الفعلي.
 *
 * المنطق: الموازنة ليست قيداً ولا تُرحَّل — هي مرجع للمقارنة فقط.
 * الفعلي يُقرأ من القيود (مدين − دائن حسب طبيعة الحساب) في الفترة المطلوبة.
 */
object Budgets {

    data class Line(
        val accountCode: String,
        val accountName: String,
        val periodKey: String,        // "2026" سنوي · "2026-03" شهري
        val amountMinor: Long,
    )

    data class Row(
        val accountCode: String,
        val accountName: String,
        val budgetMinor: Long,
        val actualMinor: Long,
        val varianceMinor: Long,     // الفعلي − الموازنة (موجب = تجاوز)
        val usedPctOfBudget: Long,   // عشرات النسبة (٩٥٠ = ٩٥٪)
    ) {
        val over: Boolean get() = varianceMinor > 0
        val usedPctText: String get() = "${usedPctOfBudget / 10}.${usedPctOfBudget % 10}"
    }

    /** دمج الموازنات المتشابهة (نفس الحساب والفترة) وتجاهل غير الموجبة */
    fun normalize(lines: List<Line>): List<Line> = lines
        .filter { it.amountMinor > 0 }
        .groupBy { it.accountCode to it.periodKey }
        .map { (key, group) ->
            Line(key.first, group.first().accountName, key.second, group.sumOf { it.amountMinor })
        }
        .sortedBy { it.accountCode }

    /**
     * مقارنة الموازنة بالفعلي.
     * @param actual دالة تُرجع الفعلي لحساب معيّن داخل الفترة — تُمرَّر من طبقة القيود.
     */
    fun compare(
        lines: List<Line>,
        periodKey: String,
        actual: (accountCode: String) -> Long,
    ): List<Row> = normalize(lines.filter { it.periodKey == periodKey }).map { line ->
        val act = actual(line.accountCode)
        val variance = act - line.amountMinor
        val pct = if (line.amountMinor == 0L) 0L else Money.mulDiv(act, 1000L, line.amountMinor)
        Row(line.accountCode, line.accountName, line.amountMinor, act, variance, pct)
    }

    data class Totals(val budgetMinor: Long, val actualMinor: Long) {
        val varianceMinor: Long get() = actualMinor - budgetMinor
        val usedPctOfBudget: Long get() = if (budgetMinor == 0L) 0L else Money.mulDiv(actualMinor, 1000L, budgetMinor)
    }

    fun totals(rows: List<Row>): Totals = Totals(rows.sumOf { it.budgetMinor }, rows.sumOf { it.actualMinor })

    /** تنبيهات: تجاوز الموازنة أو اقترابها من الحد */
    fun alerts(rows: List<Row>, nearPct: Long = 900): List<String> = rows.mapNotNull { r ->
        when {
            r.budgetMinor <= 0 -> null
            r.usedPctOfBudget >= 1000 -> "تجاوز «${r.accountName}» الموازنة بنسبة ${r.usedPctText}٪"
            r.usedPctOfBudget >= nearPct -> "«${r.accountName}» استهلك ${r.usedPctText}٪ من الموازنة"
            else -> null
        }
    }

    /** توزيع موازنة سنوية على ١٢ شهراً بلا فقدان وحدة صغرى */
    fun monthly(annualMinor: Long): List<Long> {
        require(annualMinor >= 0) { "الموازنة لا تكون سالبة" }
        val base = annualMinor / 12
        val remainder = annualMinor - base * 12
        return (0 until 12).map { i -> base + if (i < remainder) 1L else 0L }
    }

    /** النسبة المتوقعة من الموازنة حتى تاريخ معيّن (خط أساس زمني · عشرات النسبة) */
    fun expectedUsagePct(month: Int, day: Int): Long {
        val elapsedDays = (month.coerceIn(1, 12) - 1) * 31L + day.coerceIn(1, 31)
        return Money.mulDiv(elapsedDays, 1000L, 31L * 12L) + 1L
    }

    /** هل التجاوز حقيقي أم مجرد توقيت؟ يقارن الاستهلاك بخط الأساس الزمني */
    fun isTimingOnly(row: Row, month: Int, day: Int): Boolean {
        if (row.budgetMinor <= 0) return false
        return row.usedPctOfBudget <= expectedUsagePct(month, day)
    }
}
