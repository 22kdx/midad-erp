package com.example.midad.core

/**
 * حافظة الشيكات — قائمة مطبوعة قابلة للتسليم للبنك أو للمحاسب.
 *
 * القيود لا تتغيّر هنا: الشيك يُرحَّل عند الاستلام/الإيداع/التحصيل عبر Cheques.
 * هذه الطبقة للعرض والطباعة فقط، وتجمع الوارد والصادر في حافظة واحدة بترتيب الاستحقاق.
 */
object Portfolio {

    data class Row(
        val number: String,
        val party: String,
        val bank: String,
        val dueDate: String,
        val amountMinor: Long,
        val statusLabel: String,
        val incoming: Boolean,
        val note: String = "",
    )

    data class Sheet(
        val title: String,
        val asOf: String,
        val rows: List<Row>,
    ) {
        val incoming get() = rows.filter { it.incoming }.sortedBy { it.dueDate }
        val outgoing get() = rows.filterNot { it.incoming }.sortedBy { it.dueDate }
        val incomingTotal: Long get() = incoming.sumOf { it.amountMinor }
        val outgoingTotal: Long get() = outgoing.sumOf { it.amountMinor }
        val net: Long get() = incomingTotal - outgoingTotal
    }

    /** ترشيح الحافظة: الكل · الوارد · الصادر · حالة بعينها */
    fun filter(rows: List<Row>, direction: String, status: String): List<Row> = rows.filter { r ->
        val byDirection = when (direction) {
            "IN" -> r.incoming
            "OUT" -> !r.incoming
            else -> true
        }
        val byStatus = status.isBlank() || status == "ALL" || r.statusLabel == status
        byDirection && byStatus
    }

    /** الشيكات المستحقة خلال نافذة زمنية — للتنبيه والطباعة العاجلة */
    fun dueWithin(rows: List<Row>, asOf: String, days: Int): List<Row> = rows.filter {
        val d = Statements.daysBetween(asOf, it.dueDate)
        d in 0..days.toLong()
    }.sortedBy { it.dueDate }

    /** المتأخرة: تجاوزت الاستحقاق ولم تُحصَّل */
    fun overdue(rows: List<Row>, asOf: String): List<Row> = rows.filter {
        Statements.daysBetween(asOf, it.dueDate) < 0 && it.statusLabel != "محقق"
    }.sortedBy { it.dueDate }

    fun html(sheet: Sheet, company: PrintCompany, paper: Paper = Paper.A4, currency: String = "ر.ي"): String {
        fun money(v: Long) = Money.format(v, currency)
        fun section(title: String, list: List<Row>, total: Long): String {
            if (list.isEmpty()) return ""
            val body = list.joinToString("\n") { r ->
                """
                <tr>
                  <td>${Printing.escape(r.number)}</td>
                  <td>${Printing.escape(r.party)}</td>
                  <td>${Printing.escape(r.bank)}</td>
                  <td>${Printing.escape(r.dueDate)}</td>
                  <td>${Printing.escape(r.statusLabel)}</td>
                  <td class="num">${money(r.amountMinor)}</td>
                </tr>
                """.trimIndent()
            }
            return """
            <h2>${Printing.escape(title)} <span class="count">(${list.size})</span></h2>
            <table>
              <thead>
                <tr><th>الرقم</th><th>الطرف</th><th>المصرف</th><th>الاستحقاق</th><th>الحالة</th><th>المبلغ</th></tr>
              </thead>
              <tbody>$body</tbody>
              <tfoot><tr><td colspan="5">إجمالي $title</td><td class="num">${money(total)}</td></tr></tfoot>
            </table>
            """.trimIndent()
        }

        return """
        <!DOCTYPE html>
        <html dir="rtl" lang="ar">
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <title>${Printing.escape(sheet.title)}</title>
          <style>
            :root { --ink:#12211c; --muted:#6b7a73; --line:#dfe5e1; --accent:#0f6b4f; --soft:#f5f8f6; }
            * { box-sizing: border-box; }
            body { font-family: "Cairo","Noto Naskh Arabic",-apple-system,"Segoe UI",sans-serif;
                   color: var(--ink); margin: 0; padding: 14mm; background: #fff; font-size: 11px; }
            header { display: flex; justify-content: space-between; gap: 12px;
                     border-bottom: 2px solid var(--accent); padding-bottom: 8px; margin-bottom: 12px; }
            .brand { font-size: 17px; font-weight: 700; color: var(--accent); }
            .meta { text-align: left; color: var(--muted); line-height: 1.7; }
            h1 { font-size: 15px; margin: 0 0 4px; }
            h2 { font-size: 12px; margin: 14px 0 6px; color: var(--accent); }
            .count { color: var(--muted); font-weight: 400; }
            table { width: 100%; border-collapse: collapse; }
            th, td { border: 1px solid var(--line); padding: 5px 7px; text-align: right; }
            th { background: var(--soft); font-weight: 600; }
            tfoot td { background: var(--soft); font-weight: 700; }
            .num { direction: ltr; text-align: left; font-variant-numeric: tabular-nums; }
            .summary { display: flex; gap: 10px; margin: 12px 0; }
            .box { flex: 1; border: 1px solid var(--line); border-radius: 8px; padding: 8px; }
            .box small { display: block; color: var(--muted); }
            .box b { font-size: 13px; }
            .sign { display: flex; justify-content: space-between; margin-top: 22px; color: var(--muted); }
            .sign div { border-top: 1px dashed var(--line); padding-top: 4px; width: 40%; text-align: center; }
            footer { margin-top: 16px; border-top: 1px solid var(--line); padding-top: 6px;
                     color: var(--muted); font-size: 9px; display: flex; justify-content: space-between; }
            @page { size: ${if (paper.widthMm >= 200) "A4" else "A5"} portrait; margin: 0; }
            @media screen { body { max-width: ${paper.widthMm}mm; margin: 0 auto; padding: 8mm; } }
          </style>
        </head>
        <body>
          <header>
            <div>
              <div class="brand">${Printing.escape(company.name)}</div>
              <div>${Printing.escape(company.address)}</div>
              <div>${Printing.escape(company.phone)}</div>
            </div>
            <div class="meta">
              <h1>${Printing.escape(sheet.title)}</h1>
              <div>حتى تاريخ: ${Printing.escape(sheet.asOf)}</div>
              ${if (company.taxNumber.isNotBlank()) "<div>الرقم الضريبي: ${Printing.escape(company.taxNumber)}</div>" else ""}
              <div>عدد الشيكات: ${sheet.rows.size}</div>
            </div>
          </header>

          <div class="summary">
            <div class="box"><small>وارد</small><b class="num">${money(sheet.incomingTotal)}</b></div>
            <div class="box"><small>صادر</small><b class="num">${money(sheet.outgoingTotal)}</b></div>
            <div class="box"><small>الصافي</small><b class="num">${money(sheet.net)}</b></div>
          </div>

          ${section("الشيكات الواردة", sheet.incoming, sheet.incomingTotal)}
          ${section("الشيكات الصادرة", sheet.outgoing, sheet.outgoingTotal)}

          <p>الصافي بالحروف: ${Tafqeet.amount(kotlin.math.abs(sheet.net), currency, withPrefix = false)}${if (sheet.net < 0) " (على المنشأة)" else ""}.</p>

          <div class="sign"><div>المحاسب</div><div>المدير المالي</div></div>
          <footer>
            <span>${Printing.escape(company.footerNote)}</span>
            <span>TAMM · ${Printing.escape(sheet.asOf)}</span>
          </footer>
        </body>
        </html>
        """.trimIndent()
    }
}
