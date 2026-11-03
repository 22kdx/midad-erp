package com.example.midad.core

/**
 * طباعة المستندات — HTML مستقل (inline CSS, RTL) جاهز لـ Android PrintManager ⇒ PDF.
 * لا يعتمد على أي مكتبة: الشاشة تُمرّر السلسلة إلى WebView ثم تطبع، أو تحفظ PDF.
 */
enum class Paper(val widthMm: Int, val heightMm: Int, val label: String) {
    A4(210, 297, "A4"),
    A5(148, 210, "A5"),
    THERMAL_80(80, 0, "حراري 80مم"),
    THERMAL_58(58, 0, "حراري 58مم");

    val isThermal: Boolean get() = this == THERMAL_80 || this == THERMAL_58
}

data class PrintCompany(
    val name: String,
    val address: String = "",
    val phone: String = "",
    val taxNumber: String = "",
    val footerNote: String = "شكراً لتعاملكم معنا",
)

data class PrintDoc(
    val title: String,
    val number: String,
    val date: String,
    val partyLabel: String,          // «العميل» أو «المورد»
    val partyName: String,
    val partyPhone: String = "",
    val partyTaxNumber: String = "",
    val totals: DocTotals,
    val currency: String = "YER",
    val paidMinor: Long = 0L,
    val userName: String = "",
    val notes: String = "",
    /** محتوى رمز الاستجابة السريعة (Base64 لترميز TLV) — يُطبع في تذييل الفاتورة */
    val qrPayload: String = "",
)

object Printing {

    fun escape(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")

    private fun money(minor: Long, cur: String) = escape(Money.format(minor, cur)) + " " + escape(cur)

    private fun qty(milli: Long): String {
        val whole = milli / 1000
        val frac = milli % 1000
        return if (frac == 0L) whole.toString()
        else (whole.toString() + "." + frac.toString().padStart(3, '0').trimEnd('0'))
    }

    private fun css(paper: Paper): String {
        val page = if (paper.isThermal) "size: ${paper.widthMm}mm auto; margin: 2mm;"
        else "size: ${paper.widthMm}mm ${paper.heightMm}mm; margin: 8mm;"
        val base = if (paper.isThermal) "9px" else "12px"
        return """
            @page { $page }
            * { box-sizing: border-box; -webkit-print-color-adjust: exact; }
            body { font-family: 'Tajawal', 'Cairo', 'Segoe UI', Arial, sans-serif; direction: rtl;
                   margin: 0; color: #0F1424; background: #fff; font-size: $base; }
            .qr { display: flex; flex-direction: column; align-items: center; gap: 2px;
                  margin: 10px 0; font-size: .8em; color: #555c6e; }
            .foot { text-align: center; color: #555c6e; font-size: .85em; margin-top: 10px; }
            .head { display: flex; justify-content: space-between; align-items: flex-start;
                    border-bottom: 2px solid #6E54FF; padding-bottom: 8px; margin-bottom: 10px; }
            .brand h1 { margin: 0 0 4px; font-size: 1.5em; color: #6E54FF; }
            .brand div, .meta div { color: #555c6e; font-size: .85em; line-height: 1.6; }
            .meta { text-align: left; }
            .meta b { color: #0F1424; }
            .parties { display: flex; gap: 10px; margin-bottom: 10px; }
            .card { flex: 1; background: #F4F6FA; border-radius: 10px; padding: 8px 10px; font-size: .9em; }
            .card h3 { margin: 0 0 4px; font-size: .8em; color: #6E54FF; font-weight: 600; }
            table { width: 100%; border-collapse: collapse; margin-bottom: 10px; }
            th { background: #6E54FF; color: #fff; font-size: .8em; padding: 6px; text-align: right; }
            td { padding: 6px; border-bottom: 1px solid #E6E9F2; font-size: .85em; }
            tr:nth-child(even) td { background: #FAFBFE; }
            .num { font-variant-numeric: tabular-nums; white-space: nowrap; }
            .totals { width: 46%; margin-right: auto; margin-left: 0; }
            .totals td { border: none; padding: 4px 6px; }
            .totals .grand td { border-top: 2px solid #6E54FF; font-weight: 700; font-size: 1.05em; color: #6E54FF; }
            .words { background: #F4F6FA; border-right: 4px solid #0B9A6A; border-radius: 8px;
                     padding: 8px 10px; font-size: .9em; margin-bottom: 8px; }
            .foot { margin-top: 14px; border-top: 1px dashed #C9CFDD; padding-top: 6px;
                    color: #555c6e; font-size: .75em; display: flex; justify-content: space-between; }
            .stamp { border: 1px dashed #C9CFDD; border-radius: 8px; min-height: 44px; width: 32%;
                     text-align: center; color: #8b93a7; font-size: .75em; padding-top: 22px; }
            @media screen { body { max-width: ${paper.widthMm}mm; padding: 6mm; margin: 0 auto; } }
        """.trimIndent()
    }

    /** HTML كامل لمستند بيع/شراء */
    fun html(doc: PrintDoc, company: PrintCompany, paper: Paper = Paper.A4): String {
        val cur = doc.currency
        val t = doc.totals
        val rows = t.lines.joinToString("\n") { lt ->
            val l = lt.line
            """
            <tr>
              <td>${escape(l.name)}</td>
              <td class="num">${qty(l.qtyMilli)}</td>
              <td class="num">${money(l.unitPriceMinor, cur)}</td>
              <td class="num">${if (lt.discount > 0) money(lt.discount, cur) else "—"}</td>
              <td class="num">${if (l.taxRatePct == "0" || l.taxRatePct == "0.00") "—" else escape(l.taxRatePct) + "%"}</td>
              <td class="num">${money(lt.total, cur)}</td>
            </tr>
            """.trimIndent()
        }

        val totalsRows = buildString {
            append(row("الإجمالي قبل الخصم", money(t.gross, cur)))
            if (t.discount > 0) append(row("الخصم", "- " + money(t.discount, cur)))
            append(row("الخاضع للضريبة", money(t.net, cur)))
            if (t.tax > 0) append(row("الضريبة", money(t.tax, cur)))
            if (t.rounding != 0L) append(row("فروق تقريب", money(t.rounding, cur)))
            append("<tr class='grand'>" + row("الإجمالي المستحق", money(t.total, cur), cellsOnly = true) + "</tr>")
            if (doc.paidMinor > 0) append(row("المدفوع", money(doc.paidMinor, cur)))
            if (doc.paidMinor > 0) append(row("المتبقي", money(t.total - doc.paidMinor, cur)))
        }

        val words = Tafqeet.amount(t.total, cur)

        return """
<!DOCTYPE html>
<html lang="ar" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escape(doc.title)} ${escape(doc.number)}</title>
<style>${css(paper)}</style>
</head>
<body>
  <div class="head">
    <div class="brand">
      <h1>${escape(company.name)}</h1>
      ${if (company.address.isNotBlank()) "<div>${escape(company.address)}</div>" else ""}
      ${if (company.phone.isNotBlank()) "<div>هاتف: ${escape(company.phone)}</div>" else ""}
      ${if (company.taxNumber.isNotBlank()) "<div>الرقم الضريبي: ${escape(company.taxNumber)}</div>" else ""}
    </div>
    <div class="meta">
      <div><b>${escape(doc.title)}</b></div>
      <div>رقم: <b>${escape(doc.number)}</b></div>
      <div>التاريخ: ${escape(doc.date)}</div>
      ${if (doc.userName.isNotBlank()) "<div>المستخدم: ${escape(doc.userName)}</div>" else ""}
    </div>
  </div>

  <div class="parties">
    <div class="card">
      <h3>${escape(doc.partyLabel)}</h3>
      <div>${escape(doc.partyName)}</div>
      ${if (doc.partyPhone.isNotBlank()) "<div>هاتف: ${escape(doc.partyPhone)}</div>" else ""}
      ${if (doc.partyTaxNumber.isNotBlank()) "<div>الرقم الضريبي: ${escape(doc.partyTaxNumber)}</div>" else ""}
    </div>
    <div class="card">
      <h3>العملة</h3>
      <div>${escape(cur)}</div>
      <div>عدد البنود: ${t.lines.size}</div>
    </div>
  </div>

  <table>
    <thead>
      <tr>
        <th>البند</th><th>الكمية</th><th>السعر</th><th>الخصم</th><th>الضريبة</th><th>الإجمالي</th>
      </tr>
    </thead>
    <tbody>
$rows
    </tbody>
  </table>

  <table class="totals">
    <tbody>
$totalsRows
    </tbody>
  </table>

  <div class="words">فقط ${escape(words)}</div>

  ${if (doc.notes.isNotBlank()) "<div class='card'><h3>ملاحظات</h3><div>${escape(doc.notes)}</div></div>" else ""}

  <div class="foot">
    ${if (doc.qrPayload.isBlank()) "" else "<div class='qr'>" + QrCode.svg(doc.qrPayload, moduleSize = 3) + "<div>فاتورة إلكترونية</div></div>"}
    <div class="foot">${escape(company.footerNote)}</div>
    <div>${escape(doc.number)}</div>
  </div>
  <div style="display:flex; gap:8px; margin-top:8px;">
    <div class="stamp">توقيع المستلم</div>
    <div class="stamp">الختم</div>
  </div>
</body>
</html>
""".trimIndent()
    }

    private fun row(label: String, value: String, cellsOnly: Boolean = false): String =
        if (cellsOnly) "<td>${escape(label)}</td><td class='num'>$value</td>"
        else "<tr><td>${escape(label)}</td><td class='num'>$value</td></tr>"

    /** اسم ملف الطباعة — آمن للمشاركة */
    fun fileName(title: String, number: String): String =
        number.ifBlank { title }.replace(Regex("[^\\p{L}\\p{N}\\-_.]"), "-") + ".pdf"

    /** CSV للتصدير من أي جدول — بترميز UTF-8 مع BOM حتى تفتحه Excel بالعربية */
    fun csv(headers: List<String>, rows: List<List<String>>): String {
        fun cell(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
        val sb = StringBuilder("\uFEFF")
        sb.append(headers.joinToString(",") { cell(it) }).append("\r\n")
        rows.forEach { r -> sb.append(r.joinToString(",") { cell(it) }).append("\r\n") }
        return sb.toString()
    }
}
