package com.example.midad.core

/**
 * الدفعات وتواريخ الصلاحية — صرف بأقرب انتهاء أولاً (FEFO) لا بأقدم استلام.
 * مهم للصيدليات والأغذية: بضاعة تنتهي لا تُصرف أخيراً بل أولاً.
 */
data class Batch(
    val id: String,
    val productId: String,
    val qtyMilli: Long,
    val unitCostMinor: Long,
    val expiry: String? = null,      // ISO yyyy-MM-dd أو null (بلا صلاحية)
    val receivedDate: String,
    val warehouseId: String = "main",
) {
    val valueMinor: Long get() = Money.mulDiv(unitCostMinor, qtyMilli, 1000L)
}

object Batches {

    data class Issue(val batchId: String, val qtyMilli: Long, val costMinor: Long)

    fun onHand(batches: List<Batch>, productId: String? = null): Long =
        batches.filter { productId == null || it.productId == productId }.sumOf { it.qtyMilli }

    fun value(batches: List<Batch>): Long = batches.sumOf { it.valueMinor }

    /** ترتيب FEFO: الأقرب انتهاءً أولاً، وبلا صلاحية آخر الأمر، ثم الأقدم استلاماً */
    fun fefoOrder(batches: List<Batch>): List<Batch> =
        batches.filter { it.qtyMilli > 0 }
            .sortedWith(compareBy({ it.expiry == null }, { it.expiry ?: "" }, { it.receivedDate }, { it.id }))

    /** صرف كمية بتوزيع FEFO عبر الدفعات */
    fun issue(batches: List<Batch>, qtyMilli: Long, asOf: String? = null): List<Issue> {
        if (qtyMilli <= 0L) throw AccountingException("BAD_QTY", "الكمية يجب أن تكون أكبر من صفر")

        // الدفعات المنتهية لا تُصرف أبداً
        val usable = fefoOrder(batches).filter { b ->
            val exp = b.expiry ?: return@filter true
            asOf == null || exp >= asOf
        }
        val available = usable.sumOf { it.qtyMilli }
        if (qtyMilli > available)
            throw AccountingException("INSUFFICIENT", "المتاح ${available / 1000.0} فقط من الدفعات الصالحة")

        var remaining = qtyMilli
        val out = ArrayList<Issue>()
        for (b in usable) {
            if (remaining <= 0L) break
            val take = minOf(b.qtyMilli, remaining)
            out += Issue(b.id, take, Money.mulDiv(b.unitCostMinor, take, 1000L))
            remaining -= take
        }
        return out
    }

    /** يطبّق الصرف على الدفعات ويُرجع القائمة المحدَّثة */
    fun apply(batches: List<Batch>, issues: List<Issue>): List<Batch> {
        val taken = issues.groupBy { it.batchId }.mapValues { it.value.sumOf { i -> i.qtyMilli } }
        return batches.map { b ->
            val q = taken[b.id] ?: 0L
            if (q == 0L) b else b.copy(qtyMilli = (b.qtyMilli - q).coerceAtLeast(0L))
        }
    }

    /** دفعات منتهية حتى تاريخ معيّن */
    fun expired(batches: List<Batch>, asOf: String): List<Batch> =
        batches.filter { it.qtyMilli > 0 && it.expiry != null && it.expiry < asOf }
            .sortedBy { it.expiry }

    /** دفعات تنتهي خلال عدد أيام (للتنبيه المبكر) */
    fun expiringSoon(batches: List<Batch>, asOf: String, days: Long = 30): List<Batch> =
        batches.filter {
            it.qtyMilli > 0 && it.expiry != null &&
                it.expiry >= asOf && Statements.daysBetween(asOf, it.expiry) <= days
        }.sortedBy { it.expiry }

    /** قيمة البضاعة المعرّضة للتلف — تُستخدم في تقرير المخاطر */
    fun atRiskValue(batches: List<Batch>, asOf: String, days: Long = 30): Long =
        (expired(batches, asOf) + expiringSoon(batches, asOf, days)).sumOf { it.valueMinor }

    /** توزيع الكمية على المستودعات لصنف */
    fun byWarehouse(batches: List<Batch>, productId: String, warehouses: List<Warehouse>): Map<String, Long> {
        val map = LinkedHashMap<String, Long>()
        warehouses.forEach { map[it.id] = 0L }
        batches.filter { it.productId == productId }.forEach {
            map[it.warehouseId] = (map[it.warehouseId] ?: 0L) + it.qtyMilli
        }
        return map
    }
}
