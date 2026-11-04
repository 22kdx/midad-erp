package com.example.midad.core

/**
 * الإقرار الضريبي (ضريبة القيمة المضافة) — يُبنى من القيود لا من إدخال يدوي.
 * كل خانة مصدرها حساب: مخرجات 2201 · مدخلات 2202، والمردودات تخصم.
 */
data class VatBox(val code: Int, val label: String, val amountMinor: Long)

data class VatReturn(
    val from: String,
    val to: String,
    val currency: String,
    val salesBase: Long,
    val outputTax: Long,
    val creditNoteBase: Long,
    val creditNoteTax: Long,
    val purchasesBase: Long,
    val inputTax: Long,
    val purchaseReturnBase: Long,
    val purchaseReturnTax: Long,
) {
    /** صافي ضريبة المخرجات بعد خصم إشعارات الدائن */
    val netOutputTax: Long get() = outputTax - creditNoteTax
    /** صافي ضريبة المدخلات بعد خصم مردودات المشتريات */
    val netInputTax: Long get() = inputTax - purchaseReturnTax
    /** المستحق للسداد (سالب = رصيد دائن يُرحَّل) */
    val netPayable: Long get() = netOutputTax - netInputTax
    val isCredit: Boolean get() = netPayable < 0

    val boxes: List<VatBox>
        get() = listOf(
            VatBox(1, "المبيعات الخاضعة", salesBase),
            VatBox(2, "ضريبة المخرجات", outputTax),
            VatBox(3, "مردودات وإشعارات دائنة (أساس)", creditNoteBase),
            VatBox(4, "ضريبة المردودات", creditNoteTax),
            VatBox(5, "صافي ضريبة المخرجات", netOutputTax),
            VatBox(6, "المشتريات الخاضعة", purchasesBase),
            VatBox(7, "ضريبة المدخلات", inputTax),
            VatBox(8, "مردودات مشتريات (أساس)", purchaseReturnBase),
            VatBox(9, "ضريبة مردودات المشتريات", purchaseReturnTax),
            VatBox(10, "صافي ضريبة المدخلات", netInputTax),
            VatBox(11, if (isCredit) "رصيد دائن يُرحَّل" else "الصافي المستحق", netPayable),
        )
}

object TaxReturn {

    fun build(
        from: String,
        to: String,
        salesBase: Long = 0L,
        outputTax: Long = 0L,
        creditNoteBase: Long = 0L,
        creditNoteTax: Long = 0L,
        purchasesBase: Long = 0L,
        inputTax: Long = 0L,
        purchaseReturnBase: Long = 0L,
        purchaseReturnTax: Long = 0L,
        currency: String = "YER",
    ) = VatReturn(
        from, to, currency,
        salesBase, outputTax, creditNoteBase, creditNoteTax,
        purchasesBase, inputTax, purchaseReturnBase, purchaseReturnTax,
    )

    /** بناء الإقرار من قيود الفترة مباشرة — بلا أي إدخال يدوي */
    fun fromEntries(
        entries: List<Pair<JournalRequest, List<NormLine>>>,
        accounts: Map<String, AccountRef>,
        from: String,
        to: String,
        currency: String = "YER",
        taxRatePct: String = "0",
        outputTaxAccountId: String? = null,
        inputTaxAccountId: String? = null,
    ): VatReturn {
        fun codeOf(id: String) = accounts[id]?.code
        val scoped = entries.filter { (r, _) -> r.date in from..to }
        fun net(code: String, configuredId: String? = null): Long = scoped.sumOf { (_, lines) ->
            lines.filter { it.accountId == configuredId || (configuredId == null && codeOf(it.accountId) == code) }
                .sumOf { it.debit - it.credit }
        }
        // الإيراد سالب في الميزان ⇒ نأخذ القيمة المطلقة كأساس
        val salesBase = -net(Chart.Acc.SALES)
        val creditBase = net(Chart.Acc.SALES_RETURN)
        val outputTax = -net(Chart.Acc.VAT_OUT, outputTaxAccountId) // الرصيد الدائن
        val purchasesBase = net(Chart.Acc.INVENTORY)      // تبسيط: تكلفة المشتريات = حركة المخزون المدينة
        val inputTax = net(Chart.Acc.VAT_IN, inputTaxAccountId)
        val creditNoteTax = if (outputTaxAccountId != null) scoped.sumOf { (_, lines) ->
            lines.filter { it.accountId == outputTaxAccountId }.sumOf { it.debit }
        } else Money.percent(creditBase, taxRatePct)

        return build(
            from = from, to = to,
            salesBase = salesBase,
            outputTax = outputTax,
            creditNoteBase = creditBase,
            creditNoteTax = creditNoteTax,
            purchasesBase = purchasesBase.coerceAtLeast(0L),
            inputTax = inputTax,
            currency = currency,
        )
    }

    /** ورقة إقرار قابلة للطباعة/PDF */
    fun html(ret: VatReturn, company: PrintCompany): String {
        val rows = ret.boxes.joinToString("\n") {
            "<tr><td>${it.code}</td><td>${Printing.escape(it.label)}</td>" +
                "<td class='num'>${Printing.escape(Money.format(it.amountMinor, ret.currency))}</td></tr>"
        }
        return """
<!DOCTYPE html>
<html lang="ar" dir="rtl"><head><meta charset="utf-8">
<title>إقرار ضريبي ${ret.from} — ${ret.to}</title>
<style>
 @page { size: A4; margin: 12mm; }
 body { font-family: 'Tajawal', Arial, sans-serif; color:#0F1424; direction: rtl; }
 h1 { color:#6E54FF; font-size: 20px; margin: 0 0 4px; }
 .sub { color:#555c6e; font-size: 12px; margin-bottom: 14px; }
 table { width:100%; border-collapse: collapse; }
 th { background:#6E54FF; color:#fff; font-size:12px; padding:8px; text-align:right; }
 td { padding:8px; border-bottom:1px solid #E6E9F2; font-size:13px; }
 .num { font-variant-numeric: tabular-nums; text-align:left; }
 .total { background:#F4F6FA; font-weight:700; }
 .words { margin-top:12px; border-right:4px solid #0B9A6A; padding:8px 10px; background:#F4F6FA; font-size:13px; }
</style></head><body>
 <h1>${Printing.escape(company.name)}</h1>
 <div class="sub">إقرار ضريبة القيمة المضافة · الفترة ${Printing.escape(ret.from)} إلى ${Printing.escape(ret.to)}
 ${if (company.taxNumber.isNotBlank()) " · الرقم الضريبي " + Printing.escape(company.taxNumber) else ""}</div>
 <table>
  <thead><tr><th>م</th><th>البيان</th><th>المبلغ (${Printing.escape(ret.currency)})</th></tr></thead>
  <tbody>
$rows
  </tbody>
 </table>
 <div class="words">فقط ${Printing.escape(Tafqeet.amount(ret.netPayable, ret.currency))}</div>
</body></html>
""".trimIndent()
    }
}
