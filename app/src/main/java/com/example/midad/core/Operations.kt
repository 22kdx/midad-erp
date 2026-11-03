package com.example.midad.core

/**
 * عمليات الوحدات: السندات الـ١٤ · الأصول الثابتة · الرواتب · الشيكات.
 * كلها تُنتج سطور قيد تُمرَّر إلى JournalKernel — لا تكتب شيئاً بنفسها.
 */
object Vouchers {

    enum class Kind(val labelArabic: String, val codePrefix: String) {
        RECEIPT("سند قبض (نقدي/بنكي)", "RV"),
        PAYMENT("سند صرف (نقدي/بنكي)", "PV"),
        JOURNAL("سند قيد يومية مركّب", "JV"),
        TRANSFER("تحويل بين الصناديق والبنوك", "FT"),
        FX_EXCHANGE("مصارفة وتحويل عملات", "FX"),
        CUSTODY("عهدة مالية وتصفيتها", "CS"),
        CREDIT_NOTE("إشعار دائن (خصم مسموح)", "CN"),
        DEBIT_NOTE("إشعار مدين (غرامة/تصحيح)", "DN"),
        MULTI_PARTY("قبض وصرف متعدد الأطراف", "MP"),
        DEPOSIT("أمانات وتأمينات مستردة", "DP"),
        PROFIT_DISTRIBUTION("توزيع أرباح ومسحوبات", "PD"),
        CASH_DISCREPANCY("تسوية عجز/فائض نقدي", "CD"),
        REMITTANCE("حوالة صادرة/واردة", "RM"),
        OPENING_CLOSING("رصيد افتتاحي/قيد إقفال", "OC"),
    }

    /** قبض من عميل */
    fun receipt(book: AccountBook, amount: Long, toBank: Boolean = false): List<JournalLine> =
        Rules.receipt(book, amount, toBank)

    /** صرف لمورد */
    fun payment(book: AccountBook, amount: Long, fromBank: Boolean = false): List<JournalLine> =
        Rules.supplierPayment(book, amount, fromBank)

    /** تحويل بين صندوقين/حسابين بنكيين */
    fun transfer(book: AccountBook, amount: Long, toBank: Boolean): List<JournalLine> = listOf(
        JournalLine(if (toBank) book.id(Chart.Acc.BANK) else book.id(Chart.Acc.CASH), debit = amount, note = "المستلم"),
        JournalLine(if (toBank) book.id(Chart.Acc.CASH) else book.id(Chart.Acc.BANK), credit = amount, note = "المُحوّل"),
    )

    /** مصارفة عملات: مبلغان بعملتين مختلفتين والفرق ربح/خسارة عملة */
    fun fxExchange(
        book: AccountBook,
        toAmount: Long, toCurrency: String,
        fromAmount: Long, fromCurrency: String,
        fromRate: String = "1", toRate: String = "1",
        gain: Long = 0, loss: Long = 0,
        toBank: Boolean = false, fromBank: Boolean = false,
    ): List<JournalLine> {
        val out = arrayListOf(
            JournalLine(
                if (toBank) book.id(Chart.Acc.BANK) else book.id(Chart.Acc.CASH),
                debit = toAmount, currency = toCurrency, fxRate = toRate, note = "استلام عملة",
            ),
            JournalLine(
                if (fromBank) book.id(Chart.Acc.BANK) else book.id(Chart.Acc.CASH),
                credit = fromAmount, currency = fromCurrency, fxRate = fromRate, note = "تسليم عملة",
            ),
        )
        if (gain > 0) out += JournalLine(book.id(Chart.Acc.COUNT_GAIN), credit = gain, note = "ربح عملة")
        if (loss > 0) out += JournalLine(book.id(Chart.Acc.COUNT_LOSS), debit = loss, note = "خسارة عملة")
        return out
    }

    /** فتح عهدة: نقل من الصندوق إلى عهدة الموظف */
    fun custodyOpen(book: AccountBook, amount: Long, toBank: Boolean = false): List<JournalLine> = listOf(
        JournalLine(book.id(Chart.Acc.CUSTODY), debit = amount, note = "عهدة"),
        JournalLine(if (toBank) book.id(Chart.Acc.BANK) else book.id(Chart.Acc.CASH), credit = amount, note = "صرف عهدة"),
    )

    /** تصفية عهدة: ما صُرف فعلاً + ما رُدّ */
    fun custodySettle(book: AccountBook, custodyAmount: Long, spent: Long, returned: Long): List<JournalLine> {
        require(spent + returned == custodyAmount) { "المصروف + المرتجع يجب أن يساوي العهدة" }
        val out = arrayListOf(JournalLine(book.id(Chart.Acc.CUSTODY), credit = custodyAmount, note = "إقفال عهدة"))
        if (spent > 0) out += JournalLine(book.id(Chart.Acc.RENT_EXP), debit = spent, note = "مصروف عهدة")
        if (returned > 0) out += JournalLine(book.id(Chart.Acc.CASH), debit = returned, note = "مرتجع عهدة")
        return out
    }

    /** إشعار دائن: خصم مسموح به للعميل */
    fun creditNote(book: AccountBook, amount: Long): List<JournalLine> = listOf(
        JournalLine(book.id(Chart.Acc.SALES_DISCOUNT), debit = amount, note = "خصم مسموح"),
        JournalLine(book.id(Chart.Acc.AR), credit = amount, note = "تخفيض ذمة"),
    )

    /** إشعار مدين: غرامة أو تصحيح على العميل */
    fun debitNote(book: AccountBook, amount: Long): List<JournalLine> = listOf(
        JournalLine(book.id(Chart.Acc.AR), debit = amount, note = "زيادة ذمة"),
        JournalLine(book.id(Chart.Acc.COUNT_GAIN), credit = amount, note = "إيراد آخر"),
    )

    /** توزيع أرباح/مسحوبات شريك */
    fun profitDistribution(book: AccountBook, amount: Long): List<JournalLine> = listOf(
        JournalLine(book.id(Chart.Acc.RETAINED), debit = amount, note = "توزيع أرباح"),
        JournalLine(book.id(Chart.Acc.CASH), credit = amount, note = "صرف للشريك"),
    )

    /** تسوية العجز/الفائض النقدي بعد العدّ الفعلي */
    fun cashDiscrepancy(book: AccountBook, difference: Long): List<JournalLine> =
        if (difference > 0) listOf(
            JournalLine(book.id(Chart.Acc.CASH), debit = difference, note = "فائض نقدي"),
            JournalLine(book.id(Chart.Acc.COUNT_GAIN), credit = difference, note = "إيراد فائض"),
        ) else {
            val loss = -difference
            listOf(
                JournalLine(book.id(Chart.Acc.COUNT_LOSS), debit = loss, note = "عجز نقدي"),
                JournalLine(book.id(Chart.Acc.CASH), credit = loss, note = "تخفيض صندوق"),
            )
        }

    /** حوالة صادرة مع عمولتها */
    fun remittance(book: AccountBook, amount: Long, fee: Long): List<JournalLine> = listOf(
        JournalLine(book.id(Chart.Acc.CASH), credit = amount + fee, note = "حوالة + عمولة"),
        JournalLine(book.id(Chart.Acc.AP), debit = amount, note = "مستحق للمستلم"),
        JournalLine(book.id(Chart.Acc.RENT_EXP), debit = fee, note = "مصروف عمولة"),
    )

    /** أمانة/تأمين مستلم: التزام مجمّد لا إيراد */
    fun depositReceived(book: AccountBook, amount: Long): List<JournalLine> = listOf(
        JournalLine(book.id(Chart.Acc.CASH), debit = amount, note = "استلام أمانة"),
        JournalLine(book.id(Chart.Acc.DEPOSITS), credit = amount, note = "التزام أمانة"),
    )
}

object FixedAssets {

    enum class Method { STRAIGHT_LINE, DECLINING }

    data class MonthDep(val period: String, val amount: Long)

    /**
     * جدول إهلاك مُسبق.
     * @param costMinor التكلفة · salvageMinor قيمة الإنقاذ · months العمر بالأشهر
     */
    fun schedule(
        costMinor: Long,
        salvageMinor: Long,
        months: Int,
        startPeriod: String,           // "2026-01"
        method: Method = Method.STRAIGHT_LINE,
    ): List<MonthDep> {
        require(months > 0) { "العمر يجب أن يكون أكبر من صفر" }
        val depreciable = costMinor - salvageMinor
        require(depreciable >= 0) { "قيمة الإنقاذ أكبر من التكلفة" }

        var year = startPeriod.substring(0, 4).toInt()
        var month = startPeriod.substring(5, 7).toInt()

        return when (method) {
            Method.STRAIGHT_LINE -> {
                val per = depreciable / months
                val last = depreciable - per * (months - 1)
                (1..months).map { i ->
                    val p = "%04d-%02d".format(year, month)
                    month++
                    if (month > 12) { month = 1; year++ }
                    MonthDep(p, if (i == months) last else per)
                }
            }
            Method.DECLINING -> {
                val rate = 2.0 / (months / 12.0.coerceAtLeast(1.0))  // ضعف القسط الثابت
                var book = costMinor
                (1..months).map { i ->
                    val p = "%04d-%02d".format(year, month)
                    month++
                    if (month > 12) { month = 1; year++ }
                    var amount = Money.mulDiv(book, (rate * 100).toLong(), 100 * 12)  // شهري
                    if (book - amount < salvageMinor) amount = book - salvageMinor
                    if (i == months) amount = (book - salvageMinor)
                    book -= amount
                    MonthDep(p, if (amount < 0) 0 else amount)
                }
            }
        }
    }

    /** القيمة الدفترية بعد n شهر */
    fun bookValue(costMinor: Long, schedule: List<MonthDep>, elapsedMonths: Int): Long =
        costMinor - schedule.take(elapsedMonths.coerceAtLeast(0)).sumOf { it.amount }
}

object Payroll {

    data class Input(
        val basicMinor: Long,
        val allowancesMinor: Long = 0,
        val overtimeHours: Int = 0,
        val overtimeRatePerHourMinor: Long = 0,
        val deductionsMinor: Long = 0,
        val advancesMinor: Long = 0,
    )

    data class Result(
        val grossMinor: Long,
        val totalDeductionsMinor: Long,
        val netMinor: Long,
    )

    fun compute(input: Input): Result {
        val overtime = input.overtimeHours.toLong() * input.overtimeRatePerHourMinor
        val gross = input.basicMinor + input.allowancesMinor + overtime
        val deductions = input.deductionsMinor + input.advancesMinor
        require(deductions <= gross) { "الخصومات أكبر من الاستحقاق" }
        return Result(gross, deductions, gross - deductions)
    }
}

object Cheques {

    enum class Status(val labelArabic: String) {
        RECEIVED("مستلم"),
        DEPOSITED("مودع"),
        CLEARED("محقق"),
        BOUNCED("مرتجع"),
        CANCELLED("ملغى"),
    }
    enum class Action { DEPOSIT, CLEAR, BOUNCE, CANCEL }

    /** آلة حالات الشيك — ترفض أي انتقال غير مسموح */
    fun next(current: Status, action: Action): Status = when {
        current == Status.RECEIVED && action == Action.DEPOSIT -> Status.DEPOSITED
        current == Status.DEPOSITED && action == Action.CLEAR -> Status.CLEARED
        current == Status.DEPOSITED && action == Action.BOUNCE -> Status.BOUNCED
        current == Status.RECEIVED && action == Action.BOUNCE -> Status.BOUNCED
        (current == Status.RECEIVED || current == Status.DEPOSITED) && action == Action.CANCEL -> Status.CANCELLED
        else -> throw AccountingException("BAD_CHEQUE_STATE", "انتقال غير مسموح: ${current.labelArabic} ← $action")
    }

    /** استلام شيك من عميل: من الذمم إلى شيكات تحت التحصيل */
    fun receivedLines(book: AccountBook, amount: Long): List<JournalLine> = listOf(
        JournalLine(book.id(Chart.Acc.CHEQUES), debit = amount, note = "شيك وارد"),
        JournalLine(book.id(Chart.Acc.AR), credit = amount, note = "تخفيض ذمة"),
    )

    /** تحقق الشيك: من الحافظة إلى البنك */
    fun clearedLines(book: AccountBook, amount: Long): List<JournalLine> = listOf(
        JournalLine(book.id(Chart.Acc.BANK), debit = amount, note = "تحقق شيك"),
        JournalLine(book.id(Chart.Acc.CHEQUES), credit = amount, note = "إقفال حافظة"),
    )

    /** ارتجاع الشيك: يعود للذمم مع مصروف ارتجاع */
    fun bouncedLines(book: AccountBook, amount: Long, fee: Long = 0): List<JournalLine> {
        val out = arrayListOf(
            JournalLine(book.id(Chart.Acc.AR), debit = amount, note = "إعادة ذمة العميل"),
            JournalLine(book.id(Chart.Acc.CHEQUES), credit = amount, note = "إلغاء الحافظة"),
        )
        if (fee > 0) {
            out += JournalLine(book.id(Chart.Acc.BOUNCED_CHEQUE), debit = fee, note = "مصروف شيك مرتجع")
            out += JournalLine(book.id(Chart.Acc.BANK), credit = fee, note = "رسوم بنك")
        }
        return out
    }
}
