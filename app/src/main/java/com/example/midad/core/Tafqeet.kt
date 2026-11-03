package com.example.midad.core

/**
 * التفقيط — تحويل الرقم إلى نص عربي مكتوب.
 * مثال: 1234.50 + YER ⇒ «فقط ألف ومئتان وأربعة وثلاثون ريالاً و خمسون فلساً لا غير»
 */
object Tafqeet {

    private val ones = arrayOf(
        "", "واحد", "اثنان", "ثلاثة", "أربعة", "خمسة", "ستة", "سبعة", "ثمانية", "تسعة",
    )
    private val teens = arrayOf(
        "عشرة", "أحد عشر", "اثنا عشر", "ثلاثة عشر", "أربعة عشر", "خمسة عشر",
        "ستة عشر", "سبعة عشر", "ثمانية عشر", "تسعة عشر",
    )
    /** الصيغة المذكّرة ٣-١٠ (تُستخدم مع الأسماء المؤنثة مثل: ثلاث هللات) */
    private val mascOnes = arrayOf("", "واحد", "اثنان", "ثلاث", "أربع", "خمس", "ست", "سبع", "ثمان", "تسع", "عشر")

    private val tens = arrayOf("", "عشرة", "عشرون", "ثلاثون", "أربعون", "خمسون", "ستون", "سبعون", "ثمانون", "تسعون")
    private val hundreds = arrayOf(
        "", "مئة", "مئتان", "ثلاثمئة", "أربعمئة", "خمسمئة", "ستمئة", "سبعمئة", "ثمانمئة", "تسعمئة",
    )

    /** أسماء المنازل: مفرد · مثنى · جمع */
    private data class Scale(val one: String, val two: String, val many: String)

    private val scales = arrayOf(
        Scale("", "", ""),
        Scale("ألف", "ألفان", "آلاف"),
        Scale("مليون", "مليونان", "ملايين"),
        Scale("مليار", "مليار", "مليارات"),
        Scale("تريليون", "تريليونان", "تريليونات"),
    )

    private fun under1000(n: Int): String {
        val parts = ArrayList<String>()
        val h = n / 100
        val r = n % 100
        if (h > 0) parts += hundreds[h]

        when {
            r == 0 -> Unit
            r < 10 -> parts += ones[r]
            r < 20 -> parts += teens[r - 10]
            else -> {
                val t = r / 10
                val o = r % 10
                if (o == 0) parts += tens[t] else parts += "${ones[o]} و${tens[t]}"
            }
        }
        return parts.joinToString(" و")
    }

    /** الرقم الصحيح إلى كلمات */
    fun intToWords(value: Long): String {
        if (value == 0L) return "صفر"
        require(value >= 0) { "لا يُفكَّر في الأعداد السالبة" }

        var n = value
        val groups = ArrayList<Int>()
        while (n > 0) {
            groups += (n % 1000).toInt()
            n /= 1000
        }

        val out = ArrayList<String>()
        for (i in groups.indices.reversed()) {
            val g = groups[i]
            if (g == 0) continue
            val scale = scales[i]
            val word = when {
                i == 0 -> under1000(g)
                g == 1 -> scale.one
                g == 2 -> scale.two
                g in 3..10 -> "${under1000(g)} ${scale.many}"
                else -> "${under1000(g)} ${scale.one}"
            }
            out += word
        }
        return out.joinToString(" و")
    }

    /** أسماء العملات: مفرد · مثنى · جمع · كسر */
    private data class CurrencyWords(val one: String, val two: String, val many: String, val sub: String, val subMany: String)

    private val currencies = mapOf(
        "YER" to CurrencyWords("ريال يمني", "ريالان يمنيان", "ريالات يمنية", "فلس", "فلوس"),
        "SAR" to CurrencyWords("ريال سعودي", "ريالان سعوديان", "ريالات سعودية", "هللة", "هللات"),
        "USD" to CurrencyWords("دولار", "دولاران", "دولارات", "سنت", "سنتات"),
        "EUR" to CurrencyWords("يورو", "يوروان", "يوروات", "سنت", "سنتات"),
        "AED" to CurrencyWords("درهم", "درهمان", "دراهم", "فلس", "فلوس"),
        "EGP" to CurrencyWords("جنيه", "جنيهان", "جنيهات", "قرش", "قروش"),
    )

    /**
     * التفقيط الكامل لمبلغ بالوحدات الصغرى.
     * @return «فقط … لا غير»
     */
    fun amount(minor: Long, currency: String = "YER", withPrefix: Boolean = true): String {
        val d = Money.decimals(currency)
        val base = 10L.let { if (d == 0) 1L else generateSequence(1L) { it * 10 }.elementAt(d) }
        val whole = minor / base
        val frac = (minor % base).let { if (it < 0) -it else it }

        val words = CurrencyWords("", "", "", "", "")
        val w = currencies[currency] ?: words

        val parts = ArrayList<String>()
        when {
            whole == 0L -> parts += "صفر ${w.one}"
            whole == 1L -> parts += "${w.one} واحد"
            whole == 2L -> parts += w.two
            whole in 3L..10L -> parts += "${intToWords(whole)} ${w.many}"
            else -> parts += "${intToWords(whole)} ${w.one}"
        }

        if (frac > 0) {
            when {
                frac == 1L -> parts += "${w.sub} واحد"
                frac == 2L -> parts += "${w.sub}ان"
                frac in 3L..10L -> parts += "${mascOnes[frac.toInt()]} ${w.subMany}"
                else -> parts += "${intToWords(frac)} ${w.sub}"
            }
        }

        val body = parts.joinToString(" و")
        return if (withPrefix) "فقط $body لا غير" else body
    }

    /** نسخة للطباعة: تُعيد النص بلا بادئة */
    fun plain(minor: Long, currency: String = "YER"): String = amount(minor, currency, withPrefix = false)
}
