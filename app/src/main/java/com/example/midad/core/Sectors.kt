package com.example.midad.core

/**
 * تشغيل الوحدات القطاعية: وردية الكاشير · تعليق الفواتير · طاولات المطعم ·
 * وصفة الصيدلية · مستخلص المقاولات.
 *
 * كلها قواعد نقية قابلة للاختبار، وتُغذّي الشاشات نفسها بدل تكرار المنطق فيها.
 */

// ── وردية الكاشير ───────────────────────────────────────────────
data class Shift(
    val id: String,
    val openedAt: String,
    val openingCashMinor: Long,
    val closedAt: String? = null,
    val countedCashMinor: Long? = null,
    val invoices: Int = 0,
    val cashSalesMinor: Long = 0,
    val cardSalesMinor: Long = 0,
    val creditSalesMinor: Long = 0,
    val refundsMinor: Long = 0,
) {
    val isOpen: Boolean get() = closedAt == null
    val salesMinor: Long get() = cashSalesMinor + cardSalesMinor + creditSalesMinor
}

data class ShiftSummary(
    val openingCash: Long,
    val cashSales: Long,
    val cardSales: Long,
    val creditSales: Long,
    val refunds: Long,
    val expectedCash: Long,
    val countedCash: Long?,
    val invoices: Int,
) {
    val salesMinor: Long get() = cashSales + cardSales + creditSales
    val variance: Long? get() = countedCash?.minus(expectedCash)
    val balanced: Boolean? get() = variance?.let { it == 0L }
}

object Shifts {

    /** النقد المتوقع في الدرج = رصيد البداية + مبيعات نقدية − مرتجعات نقدية */
    fun expectedCash(openingCashMinor: Long, cashSalesMinor: Long, refundsMinor: Long = 0L): Long =
        openingCashMinor + cashSalesMinor - refundsMinor

    fun summary(shift: Shift): ShiftSummary = ShiftSummary(
        openingCash = shift.openingCashMinor,
        cashSales = shift.cashSalesMinor,
        cardSales = shift.cardSalesMinor,
        creditSales = shift.creditSalesMinor,
        refunds = shift.refundsMinor,
        expectedCash = expectedCash(shift.openingCashMinor, shift.cashSalesMinor, shift.refundsMinor),
        countedCash = shift.countedCashMinor,
        invoices = shift.invoices,
    )

    fun close(shift: Shift, countedCashMinor: Long, closedAt: String): Shift {
        if (!shift.isOpen) throw AccountingException("SHIFT_CLOSED", "الوردية مغلقة مسبقاً")
        if (countedCashMinor < 0L) throw AccountingException("BAD_AMOUNT", "المبلغ المعدود لا يكون سالباً")
        return shift.copy(closedAt = closedAt, countedCashMinor = countedCashMinor)
    }
}

// ── تعليق الفواتير (Hold) ───────────────────────────────────────
data class HeldInvoice(
    val id: String,
    val label: String,
    val createdAt: String,
    val partyId: String? = null,
    val lines: List<DocLine>,
    val invoiceDiscountPct: String = "0",
) {
    val itemsCount: Int get() = lines.size
    val indicativeTotal: Long get() = DocEngine.totals(lines, invoiceDiscountPct).total
}

object Holds {
    const val MAX_HELD = 20

    fun hold(list: List<HeldInvoice>, invoice: HeldInvoice): List<HeldInvoice> {
        if (list.size >= MAX_HELD)
            throw AccountingException("TOO_MANY_HELD", "لا يمكن تعليق أكثر من $MAX_HELD فاتورة — استرجع إحداها")
        if (list.any { it.id == invoice.id })
            throw AccountingException("DUPLICATE", "الفاتورة معلّقة مسبقاً")
        return list + invoice
    }

    fun resume(list: List<HeldInvoice>, id: String): Pair<HeldInvoice, List<HeldInvoice>> {
        val found = list.firstOrNull { it.id == id }
            ?: throw AccountingException("NOT_FOUND", "الفاتورة المعلّقة غير موجودة")
        return found to list.filterNot { it.id == id }
    }

    fun total(list: List<HeldInvoice>): Long = list.sumOf { it.indicativeTotal }
}

// ── طاولات المطعم ───────────────────────────────────────────────
object Restaurant {

    enum class TableState(val label: String) {
        FREE("متاحة"),
        RESERVED("محجوزة"),
        OCCUPIED("مشغولة"),
        BILLED("بانتظار الدفع"),
    }

    enum class TableAction { RESERVE, SEAT, BILL, PAY, CANCEL }

    /** آلة حالات الطاولة — أي انتقال غير منطقي يُرفض */
    fun next(state: TableState, action: TableAction): TableState = when {
        state == TableState.FREE && action == TableAction.RESERVE -> TableState.RESERVED
        state == TableState.FREE && action == TableAction.SEAT -> TableState.OCCUPIED
        state == TableState.RESERVED && action == TableAction.SEAT -> TableState.OCCUPIED
        state == TableState.RESERVED && action == TableAction.CANCEL -> TableState.FREE
        state == TableState.OCCUPIED && action == TableAction.BILL -> TableState.BILLED
        state == TableState.OCCUPIED && action == TableAction.CANCEL -> TableState.FREE
        state == TableState.BILLED && action == TableAction.PAY -> TableState.FREE
        state == TableState.BILLED && action == TableAction.CANCEL -> TableState.OCCUPIED
        else -> throw AccountingException("BAD_TABLE_STATE", "انتقال غير مسموح: ${state.label} ← $action")
    }

    /** تقسيم فاتورة بالتساوي مع توزيع الكسور بلا فقد */
    fun splitEvenly(totalMinor: Long, shares: Int): List<Long> {
        if (shares <= 0) throw AccountingException("BAD_SHARE", "عدد الأنصبة يجب أن يكون أكبر من صفر")
        return DocEngine.distribute(totalMinor, List(shares) { 1L })
    }
}

// ── الصيدلية ────────────────────────────────────────────────────
object Pharmacy {

    /**
     * بيع دواء بروشتة: إذا كان الدواء يستلزم وصفة فلا بد من رقم وصفة/طبيب.
     * (+ منع صرف دفعة منتهية — عبر Batches.issue(asOf = ...))
     */
    fun requirePrescription(requiresRx: Boolean, prescriptionRef: String?) {
        if (requiresRx && prescriptionRef.isNullOrBlank())
            throw AccountingException("RX_REQUIRED", "هذا الدواء يستلزم وصفة طبية — أدخل رقم الوصفة")
    }

    /** تنبيه دواء قارب الانتهاء مع قيمته المعرّضة للتلف */
    fun riskReport(batches: List<Batch>, asOf: String, days: Long = 90): Pair<List<Batch>, Long> =
        (Batches.expired(batches, asOf) + Batches.expiringSoon(batches, asOf, days)).distinctBy { it.id } to
            Batches.atRiskValue(batches, asOf, days)
}

// ── مستخلص المقاولات ────────────────────────────────────────────
data class ProgressBill(
    val executedMinor: Long,
    val retentionMinor: Long,
    val advanceRecoveryMinor: Long,
    val payableMinor: Long,
    val remainingContractMinor: Long,
)

object Contracting {

    /**
     * مستخلص دوري: المنفَّذ − محتجز الضمان − استرداد الدفعة المقدمة = المستحق.
     * @param contractMinor قيمة العقد · executedMinor قيمة العمل المنفَّذ في هذا المستخلص
     * @param retentionPct نسبة المحتجز (مثلاً "10")
     * @param advanceBalanceMinor رصيد الدفعة المقدمة غير المسترد
     * @param advanceRecoveryPct نسبة استرداد الدفعة من كل مستخلص (مثلاً "20")
     */
    fun progressBill(
        contractMinor: Long,
        previouslyExecutedMinor: Long,
        executedMinor: Long,
        retentionPct: String = "10",
        advanceBalanceMinor: Long = 0L,
        advanceRecoveryPct: String = "0",
    ): ProgressBill {
        if (executedMinor < 0L) throw AccountingException("BAD_AMOUNT", "قيمة التنفيذ لا تكون سالبة")
        val cumulative = previouslyExecutedMinor + executedMinor
        if (cumulative > contractMinor)
            throw AccountingException("OVER_CONTRACT", "التنفيذ التراكمي يتجاوز قيمة العقد")

        val retention = Money.percent(executedMinor, retentionPct)
        val recovery = minOf(advanceBalanceMinor, Money.percent(executedMinor, advanceRecoveryPct))
        val payable = executedMinor - retention - recovery

        return ProgressBill(
            executedMinor = executedMinor,
            retentionMinor = retention,
            advanceRecoveryMinor = recovery,
            payableMinor = payable,
            remainingContractMinor = contractMinor - cumulative,
        )
    }

    /** سطور قيد المستخلص: مدين ذمم/عميل، دائن إيراد، دائن محتجز مستحق */
    fun billLines(book: AccountBook, bill: ProgressBill): List<JournalLine> {
        val out = ArrayList<JournalLine>(4)
        // المستحق على العميل + المحتجز (أصل) + استرداد الدفعة المقدمة (خصم التزام) = الإيراد
        out += JournalLine(book.id(Chart.Acc.AR), debit = bill.payableMinor, note = "مستخلص")
        if (bill.retentionMinor > 0)
            out += JournalLine(book.id(Chart.Acc.RETENTION), debit = bill.retentionMinor, note = "محتجز ضمان")
        if (bill.advanceRecoveryMinor > 0)
            out += JournalLine(book.id(Chart.Acc.DEPOSITS), debit = bill.advanceRecoveryMinor, note = "استرداد دفعة مقدمة")
        out += JournalLine(book.id(Chart.Acc.SALES), credit = bill.executedMinor, note = "إيراد مستخلص")
        return out
    }
}
