package com.example.midad.core

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * النقود — تُخزَّن دائماً كأعداد صحيحة بالوحدة الصغرى (هللة/فلس/قرش).
 * لا تُستخدم Double في أي حساب مالي داخل النواة.
 */
object Money {

    private val DECIMALS = mapOf(
        "YER" to 2, "SAR" to 2, "AED" to 2, "USD" to 2, "EUR" to 2, "EGP" to 2, "SDG" to 2,
        "OMR" to 3, "KWD" to 3, "BHD" to 3,
    )

    fun decimals(currency: String): Int = DECIMALS[currency] ?: 2

    /** "12.34" + YER  ->  1234 */
    fun parse(value: String, currency: String = "YER"): Long {
        val d = decimals(currency)
        val clean = value.trim().replace(",", "")
        require(clean.matches(Regex("^-?\\d+(\\.\\d+)?$"))) { "مبلغ غير صالح: $value" }
        return BigDecimal(clean).setScale(d, RoundingMode.HALF_UP)
            .movePointRight(d).toLong()
    }

    /** 12.34 (Double من الواجهة) -> 1234 — عبر BigDecimal لا float */
    fun parse(value: Double, currency: String = "YER"): Long =
        parse(BigDecimal.valueOf(value).toPlainString(), currency)

    /** 1234 -> "12.34" */
    fun format(minor: Long, currency: String = "YER"): String {
        val d = decimals(currency)
        return BigDecimal.valueOf(minor).movePointLeft(d).setScale(d, RoundingMode.HALF_UP).toPlainString()
    }

    /** (a × b) / scale بتقريب نصف-للأعلى */
    fun mulDiv(a: Long, b: Long, scale: Long): Long {
        require(scale != 0L) { "قسمة على صفر" }
        return BigDecimal.valueOf(a).multiply(BigDecimal.valueOf(b))
            .divide(BigDecimal.valueOf(scale), 0, RoundingMode.HALF_UP).toLong()
    }

    /** نسبة مئوية: percent(10000, "15") = 1500 */
    fun percent(minor: Long, ratePct: String): Long {
        val rate = parse(ratePct, "XXX")          // بدقة خانتين: 15 -> 1500، 7.5 -> 750
        return mulDiv(minor, rate, 10_000L)
    }

    /**
     * تحويل عملة: مبلغ بالوحدات الصغرى × سعر صرف => بالوحدات الصغرى للعملة الهدف.
     * convert(10000 = 100.00 USD, "250", "USD", "YER") = 250000 = 2500.00 YER
     */
    fun convert(minor: Long, fxRate: String, fromCur: String, toCur: String): Long {
        val rateMinor = parse(fxRate, "XXX")
        val numerator = BigDecimal.valueOf(minor)
            .multiply(BigDecimal.valueOf(rateMinor))
            .multiply(BigDecimal.TEN.pow(decimals(toCur)))
        val denominator = BigDecimal.TEN.pow(decimals(fromCur) + 2)
        return numerator.divide(denominator, 0, RoundingMode.HALF_UP).toLong()
    }

    fun convert(minor: Long, fxRate: Double, fromCur: String, toCur: String): Long =
        convert(minor, BigDecimal.valueOf(fxRate).toPlainString(), fromCur, toCur)

    const val ZERO: Long = 0L
}
