package com.example.midad.core

/**
 * كيرنل القيد المركزي — **القطعة الوحيدة** المسموح لها إنتاج قيد محاسبي.
 * كل الشاشات والمستودعات تمر من هنا؛ لا أحد يبني قيداً بنفسه.
 *
 * دالة نقية (Pure): تُعطى السياق وترجع مسودة قيد، أو ترمي AccountingException.
 * الكتابة الفعلية (Room / PostgreSQL / الترقيم) مسؤولية المستدعي — وهذا يسمح
 * باستخدام نفس المنطق في التطبيق والخادم حرفياً.
 */
object JournalKernel {

    private val DATE_RE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    fun validate(req: JournalRequest, ctx: JournalContext): JournalDraft {

        // ١) المنشأة
        if (req.companyId.isBlank() || req.companyId != ctx.companyId) {
            throw AccountingException(Err.NO_COMPANY, "المنشأة غير موجودة أو غير نشطة")
        }

        // ٢) التاريخ
        if (!DATE_RE.matches(req.date)) {
            throw AccountingException(Err.BAD_DATE, "التاريخ غير صالح — الصيغة المطلوبة YYYY-MM-DD")
        }

        // ٣) مستند المصدر إلزامي
        if (req.sourceType.isBlank() || req.sourceId.isBlank()) {
            throw AccountingException(Err.NO_SOURCE, "لا يمكن ترحيل قيد بلا مستند مصدر")
        }

        // ٤) الأسطر
        if (req.lines.isEmpty()) throw AccountingException(Err.NO_LINES, "القيد بلا بنود")
        if (req.lines.size < 2) {
            throw AccountingException(Err.TOO_FEW_LINES, "القيد يحتاج طرفين على الأقل (مدين ودائن)")
        }

        // ٥) العملة
        val docCurrency = req.currency ?: ctx.functionalCurrency
        if (docCurrency !in ctx.allowedCurrencies) {
            throw AccountingException(Err.UNKNOWN_CURRENCY, "العملة $docCurrency غير مفعّلة في المنشأة")
        }

        // ٦) تطبيع السطور + فحص الحسابات
        val norm = ArrayList<NormLine>(req.lines.size)
        req.lines.forEachIndexed { i, l ->
            val n = i + 1
            val cur = l.currency ?: docCurrency
            if (cur !in ctx.allowedCurrencies) {
                throw AccountingException(Err.UNKNOWN_CURRENCY, "السطر $n: العملة $cur غير مفعّلة")
            }
            if (l.debit < 0 || l.credit < 0) {
                throw AccountingException(Err.BAD_LINE, "السطر $n: المبلغ لا يكون سالباً")
            }
            val hasDebit = l.debit > 0
            val hasCredit = l.credit > 0
            if (hasDebit == hasCredit) {
                throw AccountingException(Err.BAD_LINE, "السطر $n: يحمل مديناً أو دائناً — لا كليهما ولا صفراً")
            }
            if (l.accountId.isBlank()) throw AccountingException(Err.BAD_LINE, "السطر $n: بلا حساب")

            val acc = ctx.accounts[l.accountId]
                ?: throw AccountingException(Err.BAD_ACCOUNT, "السطر $n: الحساب غير موجود")
            if (acc.isGroup) {
                throw AccountingException(
                    Err.GROUP_ACCOUNT,
                    "السطر $n: «${acc.name}» حساب رئيسي — اختر حساباً فرعياً",
                )
            }
            if (!acc.isActive) {
                throw AccountingException(Err.INACTIVE_ACCOUNT, "السطر $n: الحساب «${acc.name}» موقوف")
            }

            val amount = l.debit + l.credit
            val base = if (cur == ctx.functionalCurrency) amount
            else Money.convert(amount, l.fxRate, cur, ctx.functionalCurrency)

            norm.add(
                NormLine(
                    accountId = l.accountId,
                    debit = l.debit,
                    credit = l.credit,
                    currency = cur,
                    fxRate = l.fxRate,
                    amountBase = base,
                    costCenterId = l.costCenterId,
                    note = l.note,
                )
            )
        }

        // ٧) التوازن لكل عملة على حدة — للقيود أحادية العملة.
        //    القيود متعددة العملات (مصارفة/تصحيح فروق) تتوازن بالعملة الوظيفية فقط، وهو المعيار المحاسبي.
        val byCurrency = norm.groupBy { it.currency }
        for ((cur, lines) in if (byCurrency.size == 1) byCurrency else emptyMap()) {
            val d = lines.sumOf { it.debit }
            val c = lines.sumOf { it.credit }
            if (d != c) {
                throw AccountingException(
                    Err.UNBALANCED,
                    "القيد غير متوازن بعملة $cur: مدين ${Money.format(d, cur)} / دائن ${Money.format(c, cur)}",
                )
            }
        }

        // ٨) التوازن بالعملة الوظيفية
        val baseDebit = norm.filter { it.debit > 0 }.sumOf { it.amountBase }
        val baseCredit = norm.filter { it.credit > 0 }.sumOf { it.amountBase }
        if (baseDebit != baseCredit) {
            throw AccountingException(
                Err.UNBALANCED,
                "القيد غير متوازن بالعملة الوظيفية: ${Money.format(baseDebit)} مقابل ${Money.format(baseCredit)}",
            )
        }

        // ٩) الفترة المفتوحة
        if (ctx.periodStatus == PeriodStatus.NOT_FOUND) {
            throw AccountingException(Err.PERIOD_CLOSED, "لا توجد فترة مالية تغطي ${req.date}")
        }
        if (ctx.periodStatus == PeriodStatus.CLOSED) {
            throw AccountingException(Err.PERIOD_CLOSED, "الفترة ${ctx.periodLabel} مقفلة")
        }

        // ١٠) قفل التاريخ
        val lock = ctx.lockDate
        if (!lock.isNullOrBlank() && req.date <= lock) {
            throw AccountingException(
                Err.BEFORE_LOCK,
                "التاريخ ${req.date} قبل تاريخ القفل $lock — لا يمكن الترحيل",
            )
        }

        // ١١) منع ازدواج المستند
        ctx.duplicateEntryNumber?.let {
            throw AccountingException(Err.DUPLICATE_SOURCE, "المستند مُرحّل مسبقاً بالقيد $it")
        }

        // ١٢) المسودة جاهزة
        return JournalDraft(req, norm, baseDebit, docCurrency)
    }

    /** بناء طلب عكس قيد قائم (سطر بسطر معكوساً) */
    fun reversalRequest(
        original: JournalRequest,
        originalLines: List<NormLine>,
        date: String,
        number: String,
        reason: String = "",
    ): JournalRequest = JournalRequest(
        companyId = original.companyId,
        date = date,
        description = "عكس القيد $number" + if (reason.isBlank()) "" else " — $reason",
        sourceType = original.sourceType + ":REVERSAL",
        sourceId = original.sourceId + ":R" + number,
        currency = original.currency,
        reversalOf = original.sourceId,
        lines = originalLines.map {
            JournalLine(
                accountId = it.accountId,
                debit = it.credit,
                credit = it.debit,
                currency = it.currency,
                fxRate = it.fxRate,
                costCenterId = it.costCenterId,
                note = "عكس: ${it.note}",
            )
        },
    )
}

/** أدوات تقارير تعمل على مجموعة القيود المُرحّلة */
object Reports {

    data class BalanceRow(
        val accountId: String,
        val code: String,
        val name: String,
        val debit: Long,
        val credit: Long,
    )

    data class TrialBalance(
        val rows: List<BalanceRow>,
        val totalDebit: Long,
        val totalCredit: Long,
    ) {
        val balanced: Boolean get() = totalDebit == totalCredit
    }

    /** ميزان المراجعة من القيود — الأرصدة مشتقة لا مخزّنة */
    fun trialBalance(entries: List<Pair<JournalRequest, List<NormLine>>>, accounts: Map<String, AccountRef>): TrialBalance {
        val map = LinkedHashMap<String, LongArray>() // accountId -> [debit, credit]
        for ((_, lines) in entries) {
            for (l in lines) {
                val acc = map.getOrPut(l.accountId) { LongArray(2) }
                acc[0] += l.debit
                acc[1] += l.credit
            }
        }
        val rows = map.map { (accId, v) ->
            val net = v[0] - v[1]
            val a = accounts[accId]
            BalanceRow(
                accountId = accId,
                code = a?.code ?: "",
                name = a?.name ?: "",
                debit = if (net > 0) net else 0L,
                credit = if (net < 0) -net else 0L,
            )
        }.sortedBy { it.code }

        return TrialBalance(rows, rows.sumOf { it.debit }, rows.sumOf { it.credit })
    }

    /** رصيد حساب (مدين موجب) */
    fun balanceOf(entries: List<Pair<JournalRequest, List<NormLine>>>, accountId: String): Long =
        entries.sumOf { (_, lines) -> lines.filter { it.accountId == accountId }.sumOf { it.netBase() } }
}
