package com.example.midad.core

/**
 * المستودعات — رصيد لكل (مستودع × صنف)، والتحويل بينها لا يُغيّر قيمة المخزون الكلية.
 *
 * قاعدة ذهبية: التحويل حركة موقع لا بيع، فلا قيد إيراد ولا ضريبة،
 * والقيمة تُنقل بتكلفة المصدر المتوسطة كي لا تتغيّر قيمة الأصول.
 */
data class Warehouse(
    val id: String,
    val name: String,
    val isDefault: Boolean = false,
    val isActive: Boolean = true,
)

data class StockLevel(
    val warehouseId: String,
    val productId: String,
    val qtyMilli: Long,
    val valueMinor: Long,
) {
    /** متوسط تكلفة الوحدة في هذا المستودع */
    val avgCostMinor: Long get() = if (qtyMilli <= 0L) 0L else Money.mulDiv(valueMinor, 1000L, qtyMilli)
}

object Warehouses {

    fun of(warehouses: List<Warehouse>): Warehouse? =
        warehouses.firstOrNull { it.isDefault && it.isActive } ?: warehouses.firstOrNull { it.isActive }

    fun levelsOf(levels: List<StockLevel>, productId: String): List<StockLevel> =
        levels.filter { it.productId == productId }

    /** الكمية الكلية لصنف في كل المستودعات */
    fun totalQty(levels: List<StockLevel>, productId: String): Long =
        levelsOf(levels, productId).sumOf { it.qtyMilli }

    /** قيمة المخزون الكلية */
    fun totalValue(levels: List<StockLevel>): Long = levels.sumOf { it.valueMinor }

    /** متوسط التكلفة المرجّح على مستوى المنشأة (بكل المستودعات) */
    fun weightedAvgCost(levels: List<StockLevel>, productId: String): Long {
        val scoped = levelsOf(levels, productId)
        val qty = scoped.sumOf { it.qtyMilli }
        if (qty <= 0L) return 0L
        return Money.mulDiv(scoped.sumOf { it.valueMinor }, 1000L, qty)
    }

    /**
     * تحويل كمية بين مستودعين.
     * يُرجع (المصدر بعد التحويل, الهدف بعد التحويل) — ومجموع القيمة قبل = بعده.
     */
    fun transfer(from: StockLevel, to: StockLevel, qtyMilli: Long): Pair<StockLevel, StockLevel> {
        if (from.productId != to.productId)
            throw AccountingException("BAD_TRANSFER", "لا يمكن التحويل بين صنفين مختلفين")
        if (from.warehouseId == to.warehouseId)
            throw AccountingException("BAD_TRANSFER", "المستودع المصدر والهدف متطابقان")
        if (qtyMilli <= 0L)
            throw AccountingException("BAD_TRANSFER", "الكمية يجب أن تكون أكبر من صفر")
        if (qtyMilli > from.qtyMilli)
            throw AccountingException("BAD_TRANSFER", "المتاح ${from.qtyMilli / 1000.0} فقط في المصدر")

        val movedValue = Money.mulDiv(from.avgCostMinor, qtyMilli, 1000L)
        val newFrom = from.copy(qtyMilli = from.qtyMilli - qtyMilli, valueMinor = from.valueMinor - movedValue)
        val newTo = to.copy(qtyMilli = to.qtyMilli + qtyMilli, valueMinor = to.valueMinor + movedValue)
        return newFrom to newTo
    }

    /** استلام في مستودع معيّن بتكلفة وحدة معروفة */
    fun receive(level: StockLevel, qtyMilli: Long, unitCostMinor: Long): StockLevel {
        if (qtyMilli <= 0L) throw AccountingException("BAD_QTY", "الكمية يجب أن تكون أكبر من صفر")
        return level.copy(
            qtyMilli = level.qtyMilli + qtyMilli,
            valueMinor = level.valueMinor + Money.mulDiv(unitCostMinor, qtyMilli, 1000L),
        )
    }

    /** صرف من مستودع بالمتوسط — يُرجع (القيمة المصروفة, الرصيد الجديد) */
    fun issue(level: StockLevel, qtyMilli: Long): Pair<Long, StockLevel> {
        if (qtyMilli > level.qtyMilli)
            throw AccountingException("INSUFFICIENT", "المتاح ${level.qtyMilli / 1000.0} فقط")
        val cost = Money.mulDiv(level.avgCostMinor, qtyMilli, 1000L)
        return cost to level.copy(qtyMilli = level.qtyMilli - qtyMilli, valueMinor = level.valueMinor - cost)
    }

    /** أصناف تحت الحد في مستودع معيّن */
    fun belowMin(levels: List<StockLevel>, warehouseId: String, mins: Map<String, Long>): List<StockLevel> =
        levels.filter { it.warehouseId == warehouseId && it.qtyMilli <= (mins[it.productId] ?: Long.MIN_VALUE) }
}
