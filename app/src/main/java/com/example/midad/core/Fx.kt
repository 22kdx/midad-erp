package com.example.midad.core

/** فروق العملة */
object Fx {

    data class FxResult(val gain: Long, val loss: Long, val base: Long)

    /** فرق محقق عند السداد بسعر مختلف عن سعر الفاتورة */
    fun realized(
        amountForeignMinor: Long,
        invoiceRate: String,
        settleRate: String,
        fromCur: String,
        baseCur: String,
    ): FxResult {
        val atInvoice = Money.convert(amountForeignMinor, invoiceRate, fromCur, baseCur)
        val atSettle = Money.convert(amountForeignMinor, settleRate, fromCur, baseCur)
        val diff = atSettle - atInvoice      // موجب = دفعنا أكثر = خسارة
        return if (diff > 0) FxResult(0L, diff, atSettle) else FxResult(-diff, 0L, atSettle)
    }

    data class RevalRow(val currency: String, val bookBase: Long, val revalued: Long, val diff: Long)

    /** إعادة تقييم أرصدة أجنبية بسعر الإقفال ⇒ فرق غير محقق */
    fun unrealized(
        balances: List<Triple<String, Long, Long>>,   // (currency, amountMinor, bookBaseMinor)
        closingRates: Map<String, String>,
        baseCur: String,
    ): Pair<List<RevalRow>, FxResult> {
        val rows = ArrayList<RevalRow>()
        var gain = 0L
        var loss = 0L
        for ((cur, amount, bookBase) in balances) {
            val rate = closingRates[cur] ?: continue
            val revalued = Money.convert(amount, rate, cur, baseCur)
            val diff = revalued - bookBase
            rows += RevalRow(cur, bookBase, revalued, diff)
            if (diff > 0) gain += diff else if (diff < 0) loss += -diff
        }
        return rows to FxResult(gain, loss, 0L)
    }
}
