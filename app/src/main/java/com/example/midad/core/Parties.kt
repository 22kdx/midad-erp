package com.example.midad.core

/**
 * الأطراف (عملاء/موردون) والذمم والتحصيل الجزئي.
 * كل طرف له حساب فرعي تحت 1103 (عملاء) أو 2101 (موردون)، فالذمة مشتقة من القيود.
 */
object Parties {

    enum class Kind(val labelArabic: String, val parentCode: String) {
        CUSTOMER("عميل", Chart.Acc.AR),
        SUPPLIER("مورد", Chart.Acc.AP),
        BOTH("عميل ومورد", Chart.Acc.AR),
    }

    /** فاتورة مفتوحة قابلة للتحصيل */
    data class OpenInvoice(
        val id: String,
        val number: String,
        val date: String,
        val dueDate: String,
        val totalMinor: Long,
        val paidMinor: Long = 0L,
    ) {
        val outstanding: Long get() = totalMinor - paidMinor
    }

    data class Allocation(val invoiceId: String, val number: String, val amountMinor: Long)
    data class AllocationResult(
        val allocations: List<Allocation>,
        val unallocatedMinor: Long,   // دفعة مقدمة إن زاد المبلغ
    )

    /**
     * توزيع دفعة على الفواتير — الأقدم أولاً (FIFO) كما في الأنظمة المحاسبية.
     */
    fun allocate(amountMinor: Long, invoices: List<OpenInvoice>): AllocationResult {
        require(amountMinor > 0) { "مبلغ التحصيل يجب أن يكون أكبر من صفر" }
        var remaining = amountMinor
        val out = ArrayList<Allocation>()

        for (inv in invoices.sortedBy { it.date }) {
            if (remaining <= 0) break
            val due = inv.outstanding
            if (due <= 0) continue
            val take = minOf(due, remaining)
            out += Allocation(inv.id, inv.number, take)
            remaining -= take
        }
        return AllocationResult(out, remaining)
    }

    /** توزيع يدوي يتحقق من عدم تجاوز المتبقي */
    fun allocateManual(amountMinor: Long, targets: List<Pair<OpenInvoice, Long>>): AllocationResult {
        var remaining = amountMinor
        val out = ArrayList<Allocation>()
        for ((inv, requested) in targets) {
            val take = minOf(requested, inv.outstanding, remaining)
            if (take > 0) {
                out += Allocation(inv.id, inv.number, take)
                remaining -= take
            }
        }
        return AllocationResult(out, remaining)
    }

    // ── كشف الحساب ───────────────────────────────────────────────
    data class StatementRow(
        val date: String,
        val ref: String,
        val description: String,
        val debit: Long,
        val credit: Long,
        val balance: Long,
    )

    /**
     * كشف حساب طرف من القيود.
     * الرصيد موجب = مدين (عليه)، سالب = دائن (له).
     */
    fun statement(
        entries: List<Pair<JournalRequest, List<NormLine>>>,
        partyAccountId: String,
        from: String? = null,
        to: String? = null,
    ): List<StatementRow> {
        var balance = 0L
        val rows = ArrayList<StatementRow>()

        val ordered = entries.sortedWith(compareBy({ it.first.date }, { it.first.sourceId }))
        for ((req, lines) in ordered) {
            val hits = lines.filter { it.accountId == partyAccountId }
            if (hits.isEmpty()) continue
            val d = hits.sumOf { it.debit }
            val c = hits.sumOf { it.credit }
            balance += d - c
            if (to != null && req.date > to) continue
            if (from != null && req.date < from) continue
            rows += StatementRow(req.date, req.sourceId, req.description, d, c, balance)
        }
        return rows
    }

    /** رصيد طرف — موجب مدين */
    fun balance(entries: List<Pair<JournalRequest, List<NormLine>>>, partyAccountId: String): Long =
        entries.sumOf { (_, lines) -> lines.filter { it.accountId == partyAccountId }.sumOf { it.debit - it.credit } }

    /** أعمار ديون طرف واحد */
    fun agingFor(entries: List<Pair<JournalRequest, List<NormLine>>>, partyAccountId: String, asOf: String) =
        Statements.aging(
            entries.filter { (_, lines) -> lines.any { it.accountId == partyAccountId } }
                .map { (req, lines) ->
                    val net = lines.filter { it.accountId == partyAccountId }.sumOf { it.debit - it.credit }
                    Statements.OpenItem(req.sourceId, partyAccountId, req.date, net)
                }
                .filter { it.amountMinor > 0 },
            asOf,
        )

    /** هل يتجاوز العميل حدّه الائتماني؟ */
    fun exceedsLimit(currentBalanceMinor: Long, invoiceMinor: Long, creditLimitMinor: Long): Boolean =
        creditLimitMinor > 0 && (currentBalanceMinor + invoiceMinor) > creditLimitMinor
}

/**
 * إقفال الفترة: تصفير الإيرادات والمصروفات في الأرباح المبقاة.
 */
object PeriodClose {

    data class Summary(
        val revenue: Long,
        val expenses: Long,
        val netProfit: Long,
        val closingLines: List<JournalLine>,
    )

    /** يبني سطور قيد الإقفال من أرصدة حسابات النتيجة */
    fun buildClosingEntry(
        entries: List<Pair<JournalRequest, List<NormLine>>>,
        accounts: Map<String, AccountRef>,
        retainedAccountId: String,
    ): Summary {
        val balances = entries.flatMap { it.second }
            .groupBy { it.accountId }
            .mapValues { (_, lines) -> lines.sumOf { it.debit - it.credit } }

        val resultAccounts = balances.filter {
            val t = accounts[it.key]?.type
            t == AccountType.REVENUE || t == AccountType.EXPENSE
        }

        var revenue = 0L
        var expenses = 0L
        val lines = ArrayList<JournalLine>()

        for ((accId, net) in resultAccounts) {
            if (net == 0L) continue
            val type = accounts[accId]?.type
            if (type == AccountType.REVENUE) {
                revenue += -net                       // الإيراد دائن ⇒ صافي سالب
                // نقفل الحساب بمدين بقيمته السالبة
                if (net < 0) lines += JournalLine(accId, debit = -net, note = "إقفال إيراد")
                else lines += JournalLine(accId, credit = net, note = "إقفال إيراد")
            } else {
                expenses += net
                if (net > 0) lines += JournalLine(accId, credit = net, note = "إقفال مصروف")
                else lines += JournalLine(accId, debit = -net, note = "إقفال مصروف")
            }
        }

        val netProfit = revenue - expenses
        when {
            netProfit > 0 -> lines += JournalLine(retainedAccountId, credit = netProfit, note = "أرباح مبقاة")
            netProfit < 0 -> lines += JournalLine(retainedAccountId, debit = -netProfit, note = "خسائر مُدوّرة")
        }

        return Summary(revenue, expenses, netProfit, lines)
    }
}
