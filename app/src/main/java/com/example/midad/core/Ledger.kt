package com.example.midad.core

/**
 * دفتر الطرف — كشف حساب وتحصيل، مبنيّان على مستندات الطرف لا على إدخال يدوي.
 * كل صف في الكشف هو مستند (فاتورة/مردود/سداد)، والرصيد تراكمي.
 */
data class PartyDoc(
    val id: String,
    val number: String,
    val date: String,
    val kind: String,          // INVOICE | RETURN | PAYMENT
    val totalMinor: Long,
    val paidMinor: Long = 0L,
    val note: String = "",
) {
    val outstanding: Long get() = (totalMinor - paidMinor).coerceAtLeast(0L)
    /** الفاتورة تزيد الذمة، المردود والسداد يخفضانها */
    val isDebit: Boolean get() = kind == KIND_INVOICE
    companion object {
        const val KIND_INVOICE = "INVOICE"
        const val KIND_RETURN = "RETURN"
        const val KIND_PAYMENT = "PAYMENT"
    }
}

data class LedgerRow(
    val date: String,
    val number: String,
    val label: String,
    val debit: Long,
    val credit: Long,
    val balance: Long,
)

data class CollectionPlan(
    val allocations: List<Parties.Allocation>,
    val advanceMinor: Long,     // دفعة مقدمة (زيادة عن المستحق)
    val totalMinor: Long,
)

object PartyLedger {

    fun label(kind: String): String = when (kind) {
        PartyDoc.KIND_INVOICE -> "فاتورة"
        PartyDoc.KIND_RETURN -> "مردود"
        PartyDoc.KIND_PAYMENT -> "سداد"
        else -> kind
    }

    /** كشف حساب تراكمي — الأقدم أولاً، مع رصيد افتتاحي اختياري */
    fun statement(docs: List<PartyDoc>, openingMinor: Long = 0L): List<LedgerRow> {
        var balance = openingMinor
        return docs.sortedWith(compareBy({ it.date }, { it.number })).map { d ->
            val debit = if (d.isDebit) d.totalMinor else 0L
            val credit = if (d.isDebit) 0L else d.totalMinor
            balance += debit - credit
            LedgerRow(d.date, d.number, label(d.kind), debit, credit, balance)
        }
    }

    /** الرصيد المستحق على الطرف الآن (موجب = عليه) */
    fun balance(docs: List<PartyDoc>): Long =
        docs.sumOf { if (it.isDebit) it.totalMinor - it.paidMinor else -(it.totalMinor) }

    /** الفواتير المفتوحة جاهزة للتوزيع (FIFO) */
    fun openInvoices(docs: List<PartyDoc>): List<Parties.OpenInvoice> =
        docs.filter { it.isDebit && it.outstanding > 0 }
            .sortedBy { it.date }
            .map { Parties.OpenInvoice(it.id, it.number, it.date, it.date, it.totalMinor, it.paidMinor) }

    /** خطة تحصيل: توزيع المبلغ على الفواتير الأقدم فالأحدث، والباقي دفعة مقدمة */
    fun planCollection(amountMinor: Long, docs: List<PartyDoc>): CollectionPlan {
        if (amountMinor <= 0L) throw AccountingException("BAD_AMOUNT", "مبلغ التحصيل يجب أن يكون أكبر من صفر")
        val result = Parties.allocate(amountMinor, openInvoices(docs))
        return CollectionPlan(result.allocations, result.unallocatedMinor, amountMinor)
    }

    /** يطبّق التوزيع على المستندات ويُرجع خريطة (معرّف الفاتورة → المدفوع الجديد) */
    fun applyCollection(docs: List<PartyDoc>, plan: CollectionPlan): Map<String, Long> {
        val out = HashMap<String, Long>()
        plan.allocations.forEach { a ->
            val doc = docs.firstOrNull { it.id == a.invoiceId } ?: return@forEach
            out[a.invoiceId] = minOf(doc.totalMinor, doc.paidMinor + a.amountMinor)
        }
        return out
    }
}
