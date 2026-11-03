package com.example.midad.core

/**
 * تكلفة المخزون — المتوسط المرجّح المتحرك.
 * القاعدة: التكلفة تتغيّر عند الاستلام فقط، ولا تتغيّر عند الصرف.
 * التطبيق الحالي يستخدم costPrice = price * 0.72 وهو رقم مخترع ⇒ الربح الإجمالي غير صحيح.
 *
 * الكميات تُخزَّن ×1000 لدعم الكسور (كيلو/متر)، والتكلفة بالوحدة الصغرى للوحدة الكاملة.
 */
data class Stock(
    val qtyMilli: Long = 0L,
    val valueMinor: Long = 0L,
    val avgCostMinor: Long = 0L,
) {
    val qty: Double get() = qtyMilli / 1000.0
}

object Costing {

    /** استلام كمية بتكلفة الوحدة ⇒ متوسط جديد */
    fun receive(stock: Stock, qtyMilli: Long, unitCostMinor: Long): Stock {
        require(qtyMilli > 0) { "كمية الاستلام يجب أن تكون أكبر من صفر" }
        val newQty = stock.qtyMilli + qtyMilli
        val added = Money.mulDiv(unitCostMinor, qtyMilli, 1000L)
        val newValue = stock.valueMinor + added
        val newAvg = Money.mulDiv(newValue, 1000L, newQty)
        return Stock(newQty, newValue, newAvg)
    }

    /** صرف كمية ⇒ (تكلفة الصرف, المخزون الجديد) بلا تغيير المتوسط */
    fun issue(stock: Stock, qtyMilli: Long): Pair<Long, Stock> {
        require(qtyMilli > 0) { "كمية الصرف يجب أن تكون أكبر من صفر" }
        require(qtyMilli <= stock.qtyMilli) {
            "المتاح ${stock.qty} فقط — لا يمكن صرف ${qtyMilli / 1000.0}"
        }
        val cost = Money.mulDiv(stock.avgCostMinor, qtyMilli, 1000L)
        return cost to Stock(stock.qtyMilli - qtyMilli, stock.valueMinor - cost, stock.avgCostMinor)
    }

    /** تسوية جرد: الفرق موجب = فائض، سالب = عجز */
    fun adjustTo(stock: Stock, countedQtyMilli: Long): Triple<Long, Long, Stock> {
        val diff = countedQtyMilli - stock.qtyMilli
        if (diff == 0L) return Triple(0L, 0L, stock)
        return if (diff > 0) {
            val value = Money.mulDiv(stock.avgCostMinor, diff, 1000L)
            Triple(diff, value, receive(stock, diff, stock.avgCostMinor))
        } else {
            val (cost, newStock) = issue(stock, -diff)
            Triple(diff, -cost, newStock)
        }
    }
}
