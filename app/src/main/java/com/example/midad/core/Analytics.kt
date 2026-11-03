package com.example.midad.core

/**
 * تحليلات المبيعات — تُغذّى من بنود المستندات المُحاسبة (SaleFact)، لا من شاشة.
 * كل تقرير هنا دالة نقية على قائمة حقائق، فتُختبر وحدها.
 */
data class SaleFact(
    val date: String,               // yyyy-MM-dd
    val docId: String,
    val productId: String,
    val productName: String,
    val partyId: String,
    val partyName: String,
    val qtyMilli: Long,
    val netMinor: Long,             // بعد الخصم وقبل الضريبة
    val taxMinor: Long,
    val cogsMinor: Long,
) {
    val profitMinor: Long get() = netMinor - cogsMinor
    val month: String get() = date.take(7)
}

data class GroupAgg(
    val id: String,
    val name: String,
    val qtyMilli: Long,
    val netMinor: Long,
    val cogsMinor: Long,
    val docs: Int,
) {
    val profitMinor: Long get() = netMinor - cogsMinor
    /** هامش الربح ٪ بخانة عشرية */
    val marginPct: String
        get() = if (netMinor == 0L) "0.0" else {
            val tenths = Money.mulDiv(profitMinor, 1000L, netMinor)
            "${tenths / 10}.${(tenths % 10).let { if (it < 0) -it else it }}"
        }
}

data class SalesTotals(
    val netMinor: Long,
    val taxMinor: Long,
    val cogsMinor: Long,
    val docs: Int,
    val lines: Int,
) {
    val profitMinor: Long get() = netMinor - cogsMinor
    val averageBasket: Long get() = if (docs == 0) 0L else netMinor / docs
}

object Analytics {

    fun inRange(facts: List<SaleFact>, from: String, to: String): List<SaleFact> =
        facts.filter { it.date in from..to }

    fun totals(facts: List<SaleFact>): SalesTotals = SalesTotals(
        netMinor = facts.sumOf { it.netMinor },
        taxMinor = facts.sumOf { it.taxMinor },
        cogsMinor = facts.sumOf { it.cogsMinor },
        docs = facts.map { it.docId }.distinct().size,
        lines = facts.size,
    )

    fun byProduct(facts: List<SaleFact>): List<GroupAgg> = group(facts, { it.productId }, { it.productName })
        .sortedByDescending { it.netMinor }

    fun byParty(facts: List<SaleFact>): List<GroupAgg> = group(facts, { it.partyId }, { it.partyName })
        .sortedByDescending { it.netMinor }

    fun byMonth(facts: List<SaleFact>): List<GroupAgg> =
        group(facts, { it.month }, { it.month }).sortedBy { it.id }

    private fun group(
        facts: List<SaleFact>,
        key: (SaleFact) -> String,
        name: (SaleFact) -> String,
    ): List<GroupAgg> = facts.groupBy(key).map { (id, rows) ->
        GroupAgg(
            id = id,
            name = name(rows.first()).ifBlank { id },
            qtyMilli = rows.sumOf { it.qtyMilli },
            netMinor = rows.sumOf { it.netMinor },
            cogsMinor = rows.sumOf { it.cogsMinor },
            docs = rows.map { it.docId }.distinct().size,
        )
    }

    /** أفضل الأصناف ربحية (لا إيراداً فقط) */
    fun topProfit(facts: List<SaleFact>, limit: Int = 5): List<GroupAgg> =
        byProduct(facts).sortedByDescending { it.profitMinor }.take(limit)

    /** أصناف تُباع بخسارة — مؤشر خطأ سعري */
    fun lossMaking(facts: List<SaleFact>): List<GroupAgg> =
        byProduct(facts).filter { it.profitMinor < 0 }.sortedBy { it.profitMinor }

    /** مشاركة الصنف من الإيراد ٪ */
    fun share(facts: List<SaleFact>): List<Pair<String, String>> {
        val total = facts.sumOf { it.netMinor }
        if (total == 0L) return emptyList()
        return byProduct(facts).map {
            val tenths = Money.mulDiv(it.netMinor, 1000L, total)
            it.name to "${tenths / 10}.${(tenths % 10).let { v -> if (v < 0) -v else v }}"
        }
    }

    /** بيانات جاهزة لتصدير CSV */
    fun toCsv(facts: List<SaleFact>): String =
        Printing.csv(
            listOf("التاريخ", "المستند", "الصنف", "العميل", "الكمية", "الصافي", "الضريبة", "التكلفة", "الربح"),
            facts.sortedBy { it.date }.map {
                listOf(
                    it.date, it.docId, it.productName, it.partyName,
                    (it.qtyMilli / 1000.0).toString(),
                    Money.format(it.netMinor), Money.format(it.taxMinor),
                    Money.format(it.cogsMinor), Money.format(it.profitMinor),
                )
            },
        )
}
