package com.example.midad.core

/** خريطة كود الحساب -> معرّفه داخل المنشأة */
class AccountBook(private val byCode: Map<String, String>) {
    fun id(code: String): String =
        byCode[code] ?: throw IllegalStateException("حساب غير موجود في الدليل: $code")
}

/** بند فاتورة مبيعات */
data class SaleLine(
    val qty: Long,
    val unitPriceMinor: Long,
    val taxRatePct: String = "0",
    val unitCostMinor: Long = 0L,   // من محرك التكلفة (Costing) لا من price×0.72
)

/** توزيع الدفع */
data class PaymentSplit(
    val cashMinor: Long = 0L,
    val cardMinor: Long = 0L,
    val creditMinor: Long = 0L,
)

data class SaleTotals(val netSales: Long, val tax: Long, val total: Long, val cogs: Long)

/**
 * قواعد الترحيل — تُنتج سطور القيد فقط.
 * كل قاعدة تُرجع (السطور, الإجماليات) ويُمرِّرها المستدعي إلى JournalKernel.validate.
 */
object Rules {

    /** فاتورة مبيعات: نقد/شبكة/آجل/مقسّم + ضريبة لكل بند + تكلفة البضاعة المباعة */
    fun sale(book: AccountBook, lines: List<SaleLine>, payment: PaymentSplit): Pair<List<JournalLine>, SaleTotals> {
        val net = lines.sumOf { lineTotal(it) }
        val tax = lines.sumOf { Money.percent(lineTotal(it), it.taxRatePct) }
        val cogs = lines.sumOf { Money.mulDiv(it.unitCostMinor, it.qty, 1000L) }
        val total = net + tax

        val out = ArrayList<JournalLine>(8)
        if (payment.cashMinor > 0) out += JournalLine(book.id(Chart.Acc.CASH), debit = payment.cashMinor, note = "نقداً")
        if (payment.cardMinor > 0) out += JournalLine(book.id(Chart.Acc.BANK), debit = payment.cardMinor, note = "شبكة/بنك")
        if (payment.creditMinor > 0) out += JournalLine(book.id(Chart.Acc.AR), debit = payment.creditMinor, note = "آجل")

        out += JournalLine(book.id(Chart.Acc.SALES), credit = net, note = "إيراد المبيعات")
        if (tax > 0) out += JournalLine(book.id(Chart.Acc.VAT_OUT), credit = tax, note = "ضريبة مخرجات")

        if (cogs > 0) {
            out += JournalLine(book.id(Chart.Acc.COGS), debit = cogs, note = "تكلفة المبيعات")
            out += JournalLine(book.id(Chart.Acc.INVENTORY), credit = cogs, note = "صرف مخزون")
        }
        return out to SaleTotals(net, tax, total, cogs)
    }

    /** مردود مبيعات — يعكس الفاتورة حرفياً (إيراد + ضريبة + ذمة + تكلفة/مخزون) */
    fun salesReturn(
        book: AccountBook,
        lines: List<SaleLine>,
        refundCashMinor: Long = 0L,
        refundCreditMinor: Long = 0L,
    ): Pair<List<JournalLine>, SaleTotals> {
        val net = lines.sumOf { lineTotal(it) }
        val tax = lines.sumOf { Money.percent(lineTotal(it), it.taxRatePct) }
        val cogs = lines.sumOf { Money.mulDiv(it.unitCostMinor, it.qty, 1000L) }

        val out = ArrayList<JournalLine>(6)
        out += JournalLine(book.id(Chart.Acc.SALES_RETURN), debit = net, note = "مردودات مبيعات")
        if (tax > 0) out += JournalLine(book.id(Chart.Acc.VAT_OUT), debit = tax, note = "عكس ضريبة مخرجات")
        if (refundCashMinor > 0) out += JournalLine(book.id(Chart.Acc.CASH), credit = refundCashMinor, note = "رد نقدي")
        if (refundCreditMinor > 0) out += JournalLine(book.id(Chart.Acc.AR), credit = refundCreditMinor, note = "تخفيض ذمة")
        if (cogs > 0) {
            out += JournalLine(book.id(Chart.Acc.INVENTORY), debit = cogs, note = "إرجاع للمخزون")
            out += JournalLine(book.id(Chart.Acc.COGS), credit = cogs, note = "عكس تكلفة المبيعات")
        }
        return out to SaleTotals(net, tax, net + tax, cogs)
    }

    data class PurchaseLine(val qty: Long, val unitPriceMinor: Long)

    /** إجمالي بند: السعر × الكمية (الكمية ×1000) */
    private fun lineTotal(l: SaleLine): Long = Money.mulDiv(l.unitPriceMinor, l.qty, 1000L)
    private fun lineTotal(l: PurchaseLine): Long = Money.mulDiv(l.unitPriceMinor, l.qty, 1000L)
    data class PurchaseTotals(val net: Long, val tax: Long, val landed: Long, val total: Long, val credit: Long)

    /** فاتورة مشتريات + مصاريف وصول (شحن/جمارك) تُرأسمَل على المخزون */
    fun purchase(
        book: AccountBook,
        lines: List<PurchaseLine>,
        taxRatePct: String = "0",
        landedCostMinor: Long = 0L,
        cashPaidMinor: Long = 0L,
    ): Pair<List<JournalLine>, PurchaseTotals> {
        val net = lines.sumOf { lineTotal(it) }
        val tax = Money.percent(net, taxRatePct)
        val total = net + tax + landedCostMinor
        val credit = total - cashPaidMinor

        val out = ArrayList<JournalLine>(4)
        out += JournalLine(book.id(Chart.Acc.INVENTORY), debit = net + landedCostMinor, note = "مخزون + مصاريف وصول")
        if (tax > 0) out += JournalLine(book.id(Chart.Acc.VAT_IN), debit = tax, note = "ضريبة مدخلات")
        if (cashPaidMinor > 0) out += JournalLine(book.id(Chart.Acc.CASH), credit = cashPaidMinor, note = "دفع نقدي")
        if (credit > 0) out += JournalLine(book.id(Chart.Acc.AP), credit = credit, note = "ذمة المورد")
        return out to PurchaseTotals(net, tax, landedCostMinor, total, credit)
    }

    fun receipt(book: AccountBook, amountMinor: Long, toBank: Boolean = false): List<JournalLine> = listOf(
        JournalLine(if (toBank) book.id(Chart.Acc.BANK) else book.id(Chart.Acc.CASH), debit = amountMinor, note = "قبض"),
        JournalLine(book.id(Chart.Acc.AR), credit = amountMinor, note = "تخفيض ذمة العميل"),
    )

    fun supplierPayment(book: AccountBook, amountMinor: Long, fromBank: Boolean = false): List<JournalLine> = listOf(
        JournalLine(book.id(Chart.Acc.AP), debit = amountMinor, note = "سداد مورد"),
        JournalLine(if (fromBank) book.id(Chart.Acc.BANK) else book.id(Chart.Acc.CASH), credit = amountMinor, note = "صرف"),
    )

    fun depreciation(book: AccountBook, amountMinor: Long): List<JournalLine> = listOf(
        JournalLine(book.id(Chart.Acc.DEP_EXP), debit = amountMinor, note = "مصروف إهلاك"),
        JournalLine(book.id(Chart.Acc.ACCUM_DEP), credit = amountMinor, note = "مجمع الإهلاك"),
    )

    fun payroll(book: AccountBook, grossMinor: Long, deductionsMinor: Long, netMinor: Long): List<JournalLine> {
        val out = arrayListOf(
            JournalLine(book.id(Chart.Acc.SALARY_EXP), debit = grossMinor, note = "مصروف رواتب"),
            JournalLine(book.id(Chart.Acc.PAYROLL_PAYABLE), credit = netMinor, note = "صافي مستحق"),
        )
        if (deductionsMinor > 0) {
            out += JournalLine(book.id(Chart.Acc.CUSTODY), credit = deductionsMinor, note = "خصومات/سلف")
        }
        return out
    }

    /** تسوية جرد: موجب = فائض، سالب = عجز */
    fun stockCount(book: AccountBook, diffValueMinor: Long): List<JournalLine> =
        if (diffValueMinor > 0) listOf(
            JournalLine(book.id(Chart.Acc.INVENTORY), debit = diffValueMinor, note = "فائض جرد"),
            JournalLine(book.id(Chart.Acc.COUNT_GAIN), credit = diffValueMinor, note = "إيراد فروق جرد"),
        ) else {
            val loss = -diffValueMinor
            listOf(
                JournalLine(book.id(Chart.Acc.COUNT_LOSS), debit = loss, note = "عجز جرد"),
                JournalLine(book.id(Chart.Acc.INVENTORY), credit = loss, note = "تخفيض مخزون"),
            )
        }
}
