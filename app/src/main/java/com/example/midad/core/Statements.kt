package com.example.midad.core

/**
 * القوائم المالية الثلاث + أعمار الديون + التقرير الضريبي.
 * كلها تُبنى من القيود والبنود مباشرة (أرصدة مشتقة لا مخزّنة).
 */
object Statements {

    private fun netOf(entries: List<Pair<JournalRequest, List<NormLine>>>, accountId: String): Long =
        entries.sumOf { (_, lines) ->
            lines.filter { it.accountId == accountId }.sumOf { it.netBase() }
        }

    private fun inRange(entries: List<Pair<JournalRequest, List<NormLine>>>, from: String?, to: String?) =
        entries.filter { (req, _) ->
            (from == null || req.date >= from) && (to == null || req.date <= to)
        }

    // ── قائمة الدخل ───────────────────────────────────────────────
    data class IncomeStatement(
        val revenue: Long,
        val revenueReturn: Long,
        val netRevenue: Long,
        val cogs: Long,
        val grossProfit: Long,
        val expenses: List<Reports.BalanceRow>,
        val totalExpenses: Long,
        val netProfit: Long,
    )

    fun incomeStatement(
        allEntries: List<Pair<JournalRequest, List<NormLine>>>,
        accounts: Map<String, AccountRef>,
        from: String? = null,
        to: String? = null,
    ): IncomeStatement {
        val entries = inRange(allEntries, from, to)

        fun codeOf(accountId: String) = accounts[accountId]?.code ?: ""
        fun byCode(code: String) = accounts.entries.firstOrNull { it.value.code == code }?.key

        val sales = byCode(Chart.Acc.SALES)?.let { netOf(entries, it) } ?: 0L
        val returns = byCode(Chart.Acc.SALES_RETURN)?.let { netOf(entries, it) } ?: 0L
        val cogs = byCode(Chart.Acc.COGS)?.let { netOf(entries, it) } ?: 0L

        // الإيراد دائن ⇒ رصيده سالب. نعيده موجباً.
        val revenue = -sales
        val revenueReturn = returns
        val netRevenue = revenue - revenueReturn

        val expenseRows = entries
            .flatMap { it.second }
            .filter { accounts[it.accountId]?.type == AccountType.EXPENSE }
            .groupBy { it.accountId }
            .map { (accId, lines) ->
                val a = accounts[accId]
                Reports.BalanceRow(accId, a?.code ?: "", a?.name ?: "", lines.sumOf { it.netBase() }, 0)
            }
            .filter { it.debit != 0L }
            .sortedBy { it.code }

        val totalExpenses = expenseRows.sumOf { it.debit }
        val gross = netRevenue - cogs

        // صافي الربح = إجمالي الإيرادات − إجمالي المصروفات (تكلفة المبيعات ضمن المصروفات)
        val netProfit = gross - (totalExpenses - cogs)

        // تنظيف متغير غير مستخدم
        @Suppress("UNUSED_EXPRESSION") codeOf("")

        return IncomeStatement(revenue, revenueReturn, netRevenue, cogs, gross, expenseRows, totalExpenses, netProfit)
    }

    // ── الميزانية العمومية ────────────────────────────────────────
    data class BalanceSheet(
        val assets: List<Reports.BalanceRow>,
        val liabilities: List<Reports.BalanceRow>,
        val equity: List<Reports.BalanceRow>,
        val totalAssets: Long,
        val totalLiabilities: Long,
        val totalEquity: Long,
        val retainedEarnings: Long,
    ) {
        val balanced: Boolean get() = totalAssets == totalLiabilities + totalEquity + retainedEarnings
    }

    fun balanceSheet(
        entries: List<Pair<JournalRequest, List<NormLine>>>,
        accounts: Map<String, AccountRef>,
        asOf: String? = null,
    ): BalanceSheet {
        val scoped = if (asOf == null) entries else entries.filter { (r, _) -> r.date <= asOf }

        val byAccount = scoped.flatMap { it.second }
            .groupBy { it.accountId }
            .mapValues { (_, lines) -> lines.sumOf { it.netBase() } }

        fun rowsOf(type: AccountType): List<Reports.BalanceRow> = byAccount
            .filter { accounts[it.key]?.type == type && it.value != 0L }
            .map { (accId, net) ->
                val a = accounts[accId]
                Reports.BalanceRow(
                    accId, a?.code ?: "", a?.name ?: "",
                    debit = if (net > 0) net else 0,
                    credit = if (net < 0) -net else 0,
                )
            }
            .sortedBy { it.code }

        val assets = rowsOf(AccountType.ASSET)
        val liabilities = rowsOf(AccountType.LIABILITY)
        val equity = rowsOf(AccountType.EQUITY)

        // صافي الربح غير المقفل يُضاف لحقوق الملكية
        val revenue = byAccount.filter { accounts[it.key]?.type == AccountType.REVENUE }.values.sum()
        val expense = byAccount.filter { accounts[it.key]?.type == AccountType.EXPENSE }.values.sum()
        val retained = -revenue - expense // الإيراد سالب ⇒ يقلب الإشارة

        return BalanceSheet(
            assets = assets,
            liabilities = liabilities,
            equity = equity,
            totalAssets = assets.sumOf { it.debit - it.credit },
            totalLiabilities = liabilities.sumOf { it.credit - it.debit },
            totalEquity = equity.sumOf { it.credit - it.debit },
            retainedEarnings = retained,
        )
    }

    // ── التدفق النقدي ────────────────────────────────────────────
    data class CashFlow(
        val openingCash: Long,
        val operating: Long,
        val investing: Long,
        val financing: Long,
        val netChange: Long,
        val closingCash: Long,
        val verified: Boolean,
    )

    /**
     * تصنيف كل حركة نقدية بحسب الطرف المقابل:
     * حقوق ملكية ⇒ تمويلي · أصول ثابتة ⇒ استثماري · غير ذلك ⇒ تشغيلي.
     * بهذا يتحقق دائماً: الرصيد الافتتاحي + صافي التغير = الرصيد الختامي.
     */
    fun cashFlow(
        entries: List<Pair<JournalRequest, List<NormLine>>>,
        accounts: Map<String, AccountRef>,
        from: String,
        to: String,
    ): CashFlow {
        val cashCodes = setOf(Chart.Acc.CASH, Chart.Acc.BANK)
        val fixedCodes = setOf(Chart.Acc.FIXED_ASSET, Chart.Acc.ACCUM_DEP, Chart.Acc.DEP_EXP)

        fun cashDelta(scoped: List<Pair<JournalRequest, List<NormLine>>>): Long = scoped.sumOf { (_, lines) ->
            lines.filter { accounts[it.accountId]?.code in cashCodes }.sumOf { it.netBase() }
        }

        val opening = cashDelta(entries.filter { (r, _) -> r.date < from })
        val scoped = inRange(entries, from, to)

        var operating = 0L
        var investing = 0L
        var financing = 0L

        for ((_, lines) in scoped) {
            val cash = lines.filter { accounts[it.accountId]?.code in cashCodes }
            if (cash.isEmpty()) continue
            val delta = cash.sumOf { it.netBase() }
            val counterTypes = lines.filter { accounts[it.accountId]?.code !in cashCodes }
                .mapNotNull { accounts[it.accountId] }

            when {
                counterTypes.any { it.type == AccountType.EQUITY } -> financing += delta
                counterTypes.any { it.code in fixedCodes } -> investing += delta
                else -> operating += delta
            }
        }

        val netChange = operating + investing + financing
        val closing = opening + cashDelta(scoped)

        return CashFlow(
            openingCash = opening,
            operating = operating,
            investing = investing,
            financing = financing,
            netChange = netChange,
            closingCash = closing,
            verified = opening + netChange == closing,
        )
    }

    // ── أعمار الديون ─────────────────────────────────────────────
    data class OpenItem(val ref: String, val partyId: String, val date: String, val amountMinor: Long)

    data class AgingBucket(val label: String, val minDays: Long, val maxDays: Long)

    val DEFAULT_BUCKETS = listOf(
        AgingBucket("جاري", Long.MIN_VALUE, 0),
        AgingBucket("١-٣٠", 1, 30),
        AgingBucket("٣١-٦٠", 31, 60),
        AgingBucket("٦١-٩٠", 61, 90),
        AgingBucket("أكثر من ٩٠", 91, Long.MAX_VALUE),
    )

    data class AgingResult(val partyId: String, val buckets: Map<String, Long>, val total: Long)

    fun aging(items: List<OpenItem>, asOf: String, buckets: List<AgingBucket> = DEFAULT_BUCKETS): List<AgingResult> {
        return items.groupBy { it.partyId }.map { (partyId, partyItems) ->
            val map = LinkedHashMap<String, Long>()
            buckets.forEach { map[it.label] = 0L }
            var total = 0L
            for (item in partyItems) {
                val days = daysBetween(item.date, asOf)
                val bucket = buckets.firstOrNull { days >= it.minDays && days <= it.maxDays } ?: buckets.last()
                map[bucket.label] = (map[bucket.label] ?: 0L) + item.amountMinor
                total += item.amountMinor
            }
            AgingResult(partyId, map, total)
        }.sortedByDescending { it.total }
    }

    fun daysBetween(from: String, to: String): Long {
        val d1 = java.time.LocalDate.parse(from)
        val d2 = java.time.LocalDate.parse(to)
        return java.time.temporal.ChronoUnit.DAYS.between(d1, d2)
    }

    // ── التقرير الضريبي ──────────────────────────────────────────
    data class TaxReport(val outputTax: Long, val inputTax: Long, val netPayable: Long)

    fun taxReport(
        entries: List<Pair<JournalRequest, List<NormLine>>>,
        accounts: Map<String, AccountRef>,
        from: String? = null,
        to: String? = null,
    ): TaxReport {
        val scoped = inRange(entries, from, to)
        fun bal(code: String): Long {
            val id = accounts.entries.firstOrNull { it.value.code == code }?.key ?: return 0L
            return netOf(scoped, id)
        }
        val output = -bal(Chart.Acc.VAT_OUT)   // التزام ⇒ سالب ⇒ يقلب
        val input = bal(Chart.Acc.VAT_IN)      // أصل/خصم ⇒ موجب
        return TaxReport(output, input, output - input)
    }
}
