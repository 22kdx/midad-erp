package com.example.midad.core

/**
 * محرّك المستندات — بنود متعددة · خصم بند · خصم مستند · ضريبة لكل بند · تقريب.
 *
 * المبادئ:
 *  - كل الحساب بالأعداد الصحيحة (وحدات صغرى ×١٠٠٠ للكمية) ولا كسور عائمة.
 *  - خصم المستند يُوزَّع على البنود بطريقة «أكبر باقٍ» حتى يكون الضريبة صحيحة لكل بند.
 *  - الإجماليات تُغذّي سطور القيد في [DocPosting] وتُرحَّل عبر الكيرنل نفسه.
 */

enum class DocKind(val code: String, val label: String, val isSales: Boolean) {
    QUOTE("QTE", "عرض سعر", true),
    ORDER("SO", "أمر بيع", true),
    INVOICE("INV", "فاتورة مبيعات", true),
    RETURN("SR", "مردود مبيعات", true),
    PURCHASE_ORDER("PO", "أمر شراء", false),
    PURCHASE("PINV", "فاتورة مشتريات", false),
    PURCHASE_RETURN("PR", "مردود مشتريات", false),
}

/** حالات المستند ودورة حياته — لا يُرحَّل إلا ما وصل حالة «معتمد» أو «مفوتر» */
enum class DocStatus(val code: String, val label: String) {
    DRAFT("DRAFT", "مسودة"),
    CONFIRMED("CONFIRMED", "معتمد"),
    POSTED("POSTED", "مُرحَّل"),
    CANCELLED("CANCELLED", "ملغى"),
}

enum class DocAction(val label: String) {
    CONFIRM("اعتماد"),
    POST("ترحيل"),
    CANCEL("إلغاء"),
    COPY("نسخ كمسودة"),
}

object DocFlow {
    /** الانتقال المسموح فقط — أي انتقال آخر يُرفض */
    fun next(kind: DocKind, from: DocStatus, action: DocAction): DocStatus = when (action) {
        DocAction.CONFIRM -> if (from == DocStatus.DRAFT) DocStatus.CONFIRMED
            else throw AccountingException("BAD_TRANSITION", "لا يمكن الاعتماد من حالة ${from.label}")
        DocAction.POST -> if (from == DocStatus.CONFIRMED) DocStatus.POSTED
            else throw AccountingException("BAD_TRANSITION", "يُرحَّل المستند بعد الاعتماد فقط")
        DocAction.CANCEL -> if (from == DocStatus.POSTED)
            throw AccountingException("BAD_TRANSITION", "المُرحَّل يُعالج بقيد عكسي لا بالإلغاء")
            else DocStatus.CANCELLED
        DocAction.COPY -> DocStatus.DRAFT
    }

    /** هل يمكن تعديل المستند؟ المُرحَّل لا يُعدَّل (مبدأ التطبيق) */
    fun editable(status: DocStatus): Boolean = status == DocStatus.DRAFT

    /** المستندات التي تُنشئ قيداً */
    fun posts(kind: DocKind): Boolean =
        kind == DocKind.INVOICE || kind == DocKind.RETURN ||
            kind == DocKind.PURCHASE || kind == DocKind.PURCHASE_RETURN
}

/** بند مستند */
data class DocLine(
    val productId: String,
    val name: String,
    val qtyMilli: Long,              // الكمية ×1000
    val unitPriceMinor: Long,
    val discountPct: String = "0",   // خصم البند ٪
    val taxRatePct: String = "0",    // ضريبة البند ٪
    val unitCostMinor: Long = 0L,    // من محرك التكلفة
)

/** إجماليات بند بعد التوزيع */
data class LineTotals(
    val line: DocLine,
    val gross: Long,
    val discount: Long,
    val net: Long,
    val tax: Long,
) { val total: Long get() = net + tax }

/** إجماليات مستند */
data class DocTotals(
    val lines: List<LineTotals>,
    val gross: Long,
    val lineDiscount: Long,
    val invoiceDiscount: Long,
    val net: Long,            // الخاضع للضريبة
    val tax: Long,
    val rounding: Long,
    val total: Long,
    val cogs: Long,
) {
    val discount: Long get() = lineDiscount + invoiceDiscount
}

object DocEngine {

    /** توزيع مبلغ على أوزان بطريقة أكبر باقٍ — مجموع الناتج = المبلغ بالضبط */
    fun distribute(total: Long, weights: List<Long>): List<Long> {
        val sum = weights.sum()
        if (total == 0L || sum == 0L) return List(weights.size) { 0L }
        val base = LongArray(weights.size)
        val rem = LongArray(weights.size)
        for (i in weights.indices) {
            base[i] = Math.floorDiv(total * weights[i], sum)
            rem[i] = Math.floorMod(total * weights[i], sum)
        }
        var left = total - base.sum()
        val order = weights.indices.sortedByDescending { rem[it] }
        var k = 0
        while (left > 0 && order.isNotEmpty()) {
            val idx = order[k % order.size]
            base[idx] = base[idx] + 1
            left -= 1
            k += 1
        }
        return base.toList()
    }

    fun lineGross(l: DocLine): Long = Money.mulDiv(l.unitPriceMinor, l.qtyMilli, 1000L)

    /**
     * حساب مستند كامل.
     * @param invoiceDiscountPct خصم على إجمالي المستند (بعد خصومات البنود)
     * @param roundingStep تقريب الإجمالي لأقرب مضاعف (مثلاً 100 = 1.00) — 0 يعني بلا تقريب
     */
    fun totals(
        lines: List<DocLine>,
        invoiceDiscountPct: String = "0",
        roundingStep: Long = 0L,
        landedCostMinor: Long = 0L,
    ): DocTotals {
        val gross = lines.map { lineGross(it) }
        val lineDisc = lines.mapIndexed { i, l -> Money.percent(gross[i], l.discountPct) }
        val netBefore = gross.mapIndexed { i, g -> g - lineDisc[i] }
        val invDisc = Money.percent(netBefore.sum(), invoiceDiscountPct)
        val shares = distribute(invDisc, netBefore)

        val outLines = ArrayList<LineTotals>(lines.size)
        var taxTotal = 0L
        var cogs = 0L
        for (i in lines.indices) {
            val net = netBefore[i] - shares[i]
            val tax = Money.percent(net, lines[i].taxRatePct)
            taxTotal += tax
            cogs += Money.mulDiv(lines[i].unitCostMinor, lines[i].qtyMilli, 1000L)
            outLines += LineTotals(lines[i], gross[i], lineDisc[i] + shares[i], net, tax)
        }

        val net = netBefore.sum() - invDisc
        val raw = net + taxTotal + landedCostMinor
        val rounded = if (roundingStep > 1L) Math.floorDiv(raw + roundingStep / 2, roundingStep) * roundingStep else raw
        return DocTotals(
            lines = outLines,
            gross = gross.sum(),
            lineDiscount = lineDisc.sum(),
            invoiceDiscount = invDisc,
            net = net + landedCostMinor,
            tax = taxTotal,
            rounding = rounded - raw,
            total = rounded,
            cogs = cogs,
        )
    }

    /** تحقق من صحة المستند قبل أي ترحيل — يُرجع قائمة أخطاء عربية فارغة عند السلامة */
    fun validate(lines: List<DocLine>): List<String> {
        val errors = ArrayList<String>()
        if (lines.isEmpty()) errors += "لا يمكن حفظ مستند بلا بنود"
        lines.forEachIndexed { i, l ->
            val n = i + 1
            if (l.name.isBlank()) errors += "البند $n: الاسم مطلوب"
            if (l.qtyMilli <= 0) errors += "البند $n: الكمية يجب أن تكون أكبر من صفر"
            if (l.unitPriceMinor < 0) errors += "البند $n: السعر لا يكون سالباً"
            val d = Money.parse(l.discountPct, "XXX")
            if (d < 0 || d > 10_000) errors += "البند $n: نسبة الخصم بين ٠ و١٠٠"
            val t = Money.parse(l.taxRatePct, "XXX")
            if (t < 0 || t > 10_000) errors += "البند $n: نسبة الضريبة بين ٠ و١٠٠"
        }
        return errors
    }
}

/** توزيع الدفع لفاتورة */
data class Settlement(val cashMinor: Long = 0L, val cardMinor: Long = 0L) {
    companion object {
        fun cash(total: Long) = Settlement(cashMinor = total)
        fun card(total: Long) = Settlement(cardMinor = total)
        fun credit() = Settlement()
        /** التبسيط: يقبض الباقي نقداً من مبلغ مدفوع */
        fun ofCash(total: Long, paid: Long) = Settlement(cashMinor = minOf(paid, total))
    }
}

/**
 * سطور القيد من إجماليات المستند.
 * الإيراد يُقيَّد إجمالاً ثم يُخصم بخصم مبيعات ⇒ صافي الإيراد = الخاضع للضريبة،
 * وهذا يُبقي أثر الخصم مرئياً في التقارير (بدل إخفائه داخل السعر).
 */
object DocPosting {

    private fun roundingLine(book: AccountBook, rounding: Long, currency: String?, fxRate: String): List<JournalLine> = when {
        rounding > 0 -> listOf(JournalLine(book.id(Chart.Acc.ROUNDING), credit = rounding, note = "فروق تقريب", currency = currency, fxRate = fxRate))
        rounding < 0 -> listOf(JournalLine(book.id(Chart.Acc.ROUNDING), debit = -rounding, note = "فروق تقريب", currency = currency, fxRate = fxRate))
        else -> emptyList()
    }

    private fun line(
        accountId: String, debit: Long = 0L, credit: Long = 0L, note: String,
        currency: String?, fxRate: String,
    ): JournalLine = JournalLine(accountId, debit = debit, credit = credit, currency = currency, fxRate = fxRate, note = note)

    fun sales(
        book: AccountBook,
        t: DocTotals,
        pay: Settlement,
        currency: String? = null,
        fxRate: String = "1",
        outputTaxAccountId: String? = null,
        settlementAccountId: String? = null,
    ): Pair<List<JournalLine>, SaleTotals> {
        val credit = t.total - pay.cashMinor - pay.cardMinor
        if (credit < 0) throw AccountingException("OVERPAID", "المدفوع أكبر من إجمالي الفاتورة")
        val out = ArrayList<JournalLine>(10)
        if (pay.cashMinor > 0) out += line(settlementAccountId ?: book.id(Chart.Acc.CASH), debit = pay.cashMinor, note = "نقداً", currency = currency, fxRate = fxRate)
        if (pay.cardMinor > 0) out += line(book.id(Chart.Acc.BANK), debit = pay.cardMinor, note = "شبكة/بنك", currency = currency, fxRate = fxRate)
        if (credit > 0) out += line(book.id(Chart.Acc.AR), debit = credit, note = "آجل", currency = currency, fxRate = fxRate)
        out += line(book.id(Chart.Acc.SALES), credit = t.gross, note = "إيراد المبيعات", currency = currency, fxRate = fxRate)
        if (t.discount > 0) out += line(book.id(Chart.Acc.SALES_DISCOUNT), debit = t.discount, note = "خصم مبيعات", currency = currency, fxRate = fxRate)
        if (t.tax > 0) out += line(outputTaxAccountId ?: book.id(Chart.Acc.VAT_OUT), credit = t.tax, note = "ضريبة مخرجات", currency = currency, fxRate = fxRate)
        out += roundingLine(book, t.rounding, currency, fxRate)
        if (t.cogs > 0) {
            out += JournalLine(book.id(Chart.Acc.COGS), debit = t.cogs, note = "تكلفة المبيعات")
            out += JournalLine(book.id(Chart.Acc.INVENTORY), credit = t.cogs, note = "صرف مخزون")
        }
        return out to SaleTotals(t.net, t.tax, t.total, t.cogs)
    }

    fun salesReturn(
        book: AccountBook,
        t: DocTotals,
        refundCashMinor: Long = 0L,
        refundCreditMinor: Long = 0L,
        currency: String? = null,
        fxRate: String = "1",
        outputTaxAccountId: String? = null,
        settlementAccountId: String? = null,
    ): Pair<List<JournalLine>, SaleTotals> {
        val out = ArrayList<JournalLine>(10)
        out += line(book.id(Chart.Acc.SALES_RETURN), debit = t.gross, note = "مردودات مبيعات", currency = currency, fxRate = fxRate)
        if (t.discount > 0) out += line(book.id(Chart.Acc.SALES_DISCOUNT), credit = t.discount, note = "عكس خصم", currency = currency, fxRate = fxRate)
        if (t.tax > 0) out += line(outputTaxAccountId ?: book.id(Chart.Acc.VAT_OUT), debit = t.tax, note = "عكس ضريبة مخرجات", currency = currency, fxRate = fxRate)
        if (refundCashMinor > 0) out += line(settlementAccountId ?: book.id(Chart.Acc.CASH), credit = refundCashMinor, note = "رد نقدي", currency = currency, fxRate = fxRate)
        if (refundCreditMinor > 0) out += line(book.id(Chart.Acc.AR), credit = refundCreditMinor, note = "تخفيض ذمة", currency = currency, fxRate = fxRate)
        out += roundingLine(book, -t.rounding, currency, fxRate)
        if (t.cogs > 0) {
            out += JournalLine(book.id(Chart.Acc.INVENTORY), debit = t.cogs, note = "إرجاع للمخزون")
            out += JournalLine(book.id(Chart.Acc.COGS), credit = t.cogs, note = "عكس تكلفة المبيعات")
        }
        return out to SaleTotals(t.net, t.tax, t.total, t.cogs)
    }

    fun purchase(
        book: AccountBook,
        t: DocTotals,
        cashPaidMinor: Long = 0L,
        landedCostMinor: Long = 0L,
        currency: String? = null,
        fxRate: String = "1",
        inputTaxAccountId: String? = null,
        settlementAccountId: String? = null,
    ): Pair<List<JournalLine>, Rules.PurchaseTotals> {
        val invValue = t.net            // يشمل مصاريف الوصول (أُضيفت داخل net)
        val credit = t.total - cashPaidMinor
        if (credit < 0) throw AccountingException("OVERPAID", "المدفوع أكبر من إجمالي الفاتورة")
        val out = ArrayList<JournalLine>(6)
        out += line(book.id(Chart.Acc.INVENTORY), debit = invValue, note = if (landedCostMinor > 0) "مخزون + مصاريف وصول" else "مخزون", currency = currency, fxRate = fxRate)
        if (t.tax > 0) out += line(inputTaxAccountId ?: book.id(Chart.Acc.VAT_IN), debit = t.tax, note = "ضريبة مدخلات", currency = currency, fxRate = fxRate)
        out += roundingLine(book, t.rounding, currency, fxRate)
        if (cashPaidMinor > 0) out += line(settlementAccountId ?: book.id(Chart.Acc.CASH), credit = cashPaidMinor, note = "دفع نقدي", currency = currency, fxRate = fxRate)
        if (credit > 0) out += line(book.id(Chart.Acc.AP), credit = credit, note = "ذمة المورد", currency = currency, fxRate = fxRate)
        return out to Rules.PurchaseTotals(t.net, t.tax, landedCostMinor, t.total, credit)
    }

    fun purchaseReturn(
        book: AccountBook,
        t: DocTotals,
        refundCashMinor: Long = 0L,
        currency: String? = null,
        fxRate: String = "1",
        inputTaxAccountId: String? = null,
        settlementAccountId: String? = null,
    ): Pair<List<JournalLine>, Rules.PurchaseTotals> {
        val refundCredit = t.total - refundCashMinor
        val out = ArrayList<JournalLine>(6)
        out += line(book.id(Chart.Acc.AP), debit = refundCredit, note = "تخفيض ذمة المورد", currency = currency, fxRate = fxRate)
        if (refundCashMinor > 0) out += line(settlementAccountId ?: book.id(Chart.Acc.CASH), debit = refundCashMinor, note = "استرداد نقدي", currency = currency, fxRate = fxRate)
        out += line(book.id(Chart.Acc.INVENTORY), credit = t.net, note = "إخراج من المخزون", currency = currency, fxRate = fxRate)
        if (t.tax > 0) out += line(inputTaxAccountId ?: book.id(Chart.Acc.VAT_IN), credit = t.tax, note = "عكس ضريبة مدخلات", currency = currency, fxRate = fxRate)
        out += roundingLine(book, -t.rounding, currency, fxRate)
        return out to Rules.PurchaseTotals(t.net, t.tax, 0L, t.total, -refundCredit)
    }
}
