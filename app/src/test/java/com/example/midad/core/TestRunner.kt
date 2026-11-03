package com.example.midad.core

/**
 * اختبارات النواة — بلا أي مكتبة خارجية (تعمل بـ main فقط).
 * لتشغيلها: ./build.sh
 */

private var passed = 0
private var failed = 0
private val failures = ArrayList<String>()

private fun check(name: String, block: () -> Unit) {
    try { block(); passed++; println("  ✅ $name") }
    catch (t: Throwable) { failed++; failures += "$name ⇒ ${t.message}"; println("  ❌ $name\n     ${t.message}") }
}

private fun eq(actual: Any?, expected: Any?, what: String = "") {
    if (actual != expected) throw AssertionError("$what المتوقع $expected · الحاصل $actual")
}

private fun expectError(code: String, block: () -> Unit) {
    try { block(); throw AssertionError("توقّعنا رفضاً بالكود $code لكن القيد قُبل") }
    catch (e: AccountingException) { if (e.code != code) throw AssertionError("الكود ${e.code} بدل $code — ${e.message}") }
}

// ── بيئة الاختبار ────────────────────────────────────────────────
private const val CO = "co-1"

private fun accounts(): Map<String, AccountRef> =
    Chart.YEMEN.associate {
        "acc-${it.code}" to AccountRef("acc-${it.code}", it.code, it.name, it.type, it.isGroup, true)
    }

private fun book(): AccountBook = AccountBook(Chart.YEMEN.associate { it.code to "acc-${it.code}" })

private fun ctx(
    lockDate: String? = null,
    period: PeriodStatus = PeriodStatus.OPEN,
    duplicate: String? = null,
): JournalContext = JournalContext(
    companyId = CO,
    functionalCurrency = "YER",
    allowedCurrencies = setOf("YER", "USD", "SAR"),
    lockDate = lockDate,
    periodStatus = period,
    periodLabel = "2026-03",
    accounts = accounts(),
    duplicateEntryNumber = duplicate,
)

private fun req(
    date: String = "2026-03-15",
    sourceType: String = "MANUAL",
    sourceId: String = "s-${System.nanoTime()}",
    lines: List<JournalLine> = listOf(
        JournalLine("acc-1101", debit = 100_000),
        JournalLine("acc-4101", credit = 100_000),
    ),
    currency: String? = null,
) = JournalRequest(CO, date, "قيد اختبار", sourceType, sourceId, currency, lines)

fun main() {
    println("═".repeat(64))
    println("  اختبارات نواة المحاسبة — Kotlin")
    println("═".repeat(64))

    // ── النقود ────────────────────────────────────────────────
    println("\n[النقود]")
    check("تحويل النص إلى وحدات صغرى") {
        eq(Money.parse("1234.56"), 123456L)
        eq(Money.parse("0.07"), 7L)
        eq(Money.format(123456), "1234.56")
    }
    check("لا أخطاء الفاصلة العائمة") { eq(Money.parse(0.1 + 0.2), 30L) }
    check("النسبة المئوية") {
        eq(Money.percent(10_000, "15"), 1500L)
        eq(Money.percent(333, "15"), 50L)
    }
    check("تحويل العملة") {
        eq(Money.convert(10_000, "250", "USD", "YER"), 2_500_000L) // 100$ × 250 = 25000 ريال
        eq(Money.convert(10_000, "260", "USD", "YER"), 2_600_000L)
    }

    // ── القبول ────────────────────────────────────────────────
    println("\n[كيرنل — القبول]")
    check("قيد متوازن يُقبل") {
        val d = JournalKernel.validate(req(), ctx())
        eq(d.lines.size, 2); eq(d.totalBase, 100_000L)
    }
    check("قيد من ٤ بنود") {
        val d = JournalKernel.validate(
            req(lines = listOf(
                JournalLine("acc-1101", debit = 60_000),
                JournalLine("acc-1102", debit = 40_000),
                JournalLine("acc-4101", credit = 70_000),
                JournalLine("acc-2201", credit = 30_000),
            )), ctx())
        eq(d.lines.size, 4)
    }
    check("قيد بعملتين متوازن بالوظيفية") {
        val d = JournalKernel.validate(
            req(lines = listOf(
                JournalLine("acc-1101", debit = 50_000, currency = "USD", fxRate = "250"),
                JournalLine("acc-4101", credit = 50_000, currency = "USD", fxRate = "250"),
            )), ctx())
        eq(d.totalBase, 12_500_000L)
    }

    // ── الرفض ─────────────────────────────────────────────────
    println("\n[كيرنل — الرفض]")
    check("غير متوازن") {
        expectError(Err.UNBALANCED) {
            JournalKernel.validate(req(lines = listOf(
                JournalLine("acc-1101", debit = 100_000),
                JournalLine("acc-4101", credit = 90_000),
            )), ctx())
        }
    }
    check("سطر بلا مبلغ") {
        expectError(Err.BAD_LINE) {
            JournalKernel.validate(req(lines = listOf(
                JournalLine("acc-1101"), JournalLine("acc-4101"),
            )), ctx())
        }
    }
    check("مدين ودائن في سطر واحد") {
        expectError(Err.BAD_LINE) {
            JournalKernel.validate(req(lines = listOf(
                JournalLine("acc-1101", debit = 100_000, credit = 100_000),
                JournalLine("acc-4101", debit = 1),
            )), ctx())
        }
    }
    check("حساب رئيسي") {
        expectError(Err.GROUP_ACCOUNT) {
            JournalKernel.validate(req(lines = listOf(
                JournalLine("acc-1", debit = 100_000),
                JournalLine("acc-4101", credit = 100_000),
            )), ctx())
        }
    }
    check("حساب غير موجود") {
        expectError(Err.BAD_ACCOUNT) {
            JournalKernel.validate(req(lines = listOf(
                JournalLine("acc-9999", debit = 100_000),
                JournalLine("acc-4101", credit = 100_000),
            )), ctx())
        }
    }
    check("فترة مقفلة") { expectError(Err.PERIOD_CLOSED) { JournalKernel.validate(req(), ctx(period = PeriodStatus.CLOSED)) } }
    check("لا فترة مالية") { expectError(Err.PERIOD_CLOSED) { JournalKernel.validate(req(), ctx(period = PeriodStatus.NOT_FOUND)) } }
    check("قبل تاريخ القفل") { expectError(Err.BEFORE_LOCK) { JournalKernel.validate(req(date = "2026-03-09"), ctx(lockDate = "2026-03-10")) } }
    check("مستند مكرر") { expectError(Err.DUPLICATE_SOURCE) { JournalKernel.validate(req(), ctx(duplicate = "JOU-2026-00007")) } }
    check("بلا مستند مصدر") { expectError(Err.NO_SOURCE) { JournalKernel.validate(req(sourceType = ""), ctx()) } }
    check("سطر واحد") { expectError(Err.TOO_FEW_LINES) { JournalKernel.validate(req(lines = listOf(JournalLine("acc-1101", debit = 1, credit = 1))), ctx()) } }
    check("تاريخ بصيغة خاطئة") { expectError(Err.BAD_DATE) { JournalKernel.validate(req(date = "15/03/2026"), ctx()) } }
    check("عملة غير مفعّلة") {
        expectError(Err.UNKNOWN_CURRENCY) {
            JournalKernel.validate(req(lines = listOf(
                JournalLine("acc-1101", debit = 100, currency = "EUR"),
                JournalLine("acc-4101", credit = 100, currency = "EUR"),
            )), ctx())
        }
    }

    // ── العكس ─────────────────────────────────────────────────
    println("\n[العكس]")
    check("طلب العكس يعكس كل سطر") {
        val original = req(lines = listOf(
            JournalLine("acc-1101", debit = 100_000),
            JournalLine("acc-4101", credit = 100_000),
        ))
        val draft = JournalKernel.validate(original, ctx())
        val rev = JournalKernel.reversalRequest(original, draft.lines, "2026-03-16", "JOU-2026-00002", "خطأ")
        eq(rev.lines[0].accountId, "acc-1101"); eq(rev.lines[0].credit, 100_000L); eq(rev.lines[0].debit, 0L)
        eq(rev.lines[1].accountId, "acc-4101"); eq(rev.lines[1].debit, 100_000L)
        JournalKernel.validate(rev, ctx())   // يجب أن يُقبل
    }

    // ── التقارير ──────────────────────────────────────────────
    println("\n[التقارير]")
    check("ميزان المراجعة متوازن بعد ١٠٠ قيد") {
        val entries = (1..100).map {
            val lines = listOf(
                JournalLine("acc-1101", debit = 100_000L + it),
                JournalLine("acc-4101", credit = 100_000L + it),
            )
            val r = req(sourceId = "m-$it", lines = lines)
            r to JournalKernel.validate(r, ctx()).lines
        }
        val tb = Reports.trialBalance(entries, accounts())
        eq(tb.balanced, true, "التوازن:")
        eq(tb.totalDebit, tb.totalCredit, "الإجماليان:")
    }

    // ── قواعد الترحيل ─────────────────────────────────────────
    println("\n[قواعد الترحيل]")
    check("بيع نقدي: إيراد + ضريبة + تكلفة") {
        val (lines, t) = Rules.sale(
            book(),
            listOf(SaleLine(qty = 2_000, unitPriceMinor = Money.parse("1000"), taxRatePct = "15", unitCostMinor = Money.parse("700"))),
            PaymentSplit(cashMinor = Money.parse("2300")),
        )
        eq(t.netSales, 200_000L, "الصافي:"); eq(t.tax, 30_000L, "الضريبة:"); eq(t.cogs, 140_000L, "التكلفة:")
        eq(lines.size, 5)
        val d = JournalKernel.validate(req(sourceId = "sale-1", lines = lines), ctx())
        eq(d.lines.size, 5)
    }
    check("بيع آجل يزيد الذمم") {
        val (lines, _) = Rules.sale(
            book(),
            listOf(SaleLine(1_000, Money.parse("500"), "0", Money.parse("300"))),
            PaymentSplit(creditMinor = Money.parse("500")),
        )
        val d = JournalKernel.validate(req(sourceId = "sale-2", lines = lines), ctx())
        eq(d.lines.first { it.accountId == "acc-1103" }.debit, 50_000L)
    }
    check("مردود يعكس الفاتورة كاملة") {
        val (lines, t) = Rules.salesReturn(
            book(),
            listOf(SaleLine(3_000, Money.parse("100"), "15", Money.parse("60"))),
            refundCashMinor = Money.parse("345"),
        )
        eq(t.netSales, 30_000L); eq(t.tax, 4_500L)
        val d = JournalKernel.validate(req(sourceId = "ret-1", lines = lines), ctx())
        eq(d.totalBase, 52_500L)   // 34,500 نقدي + 18,000 تكلفة
        eq(d.lines.first { it.accountId == "acc-1201" }.debit, 18_000L) // إرجاع للمخزون
    }
    check("مشتريات بمصاريف وصول تُرأسمَل") {
        val (lines, t) = Rules.purchase(
            book(), listOf(Rules.PurchaseLine(10_000, Money.parse("100"))),
            taxRatePct = "15", landedCostMinor = Money.parse("500"),
        )
        eq(t.credit, Money.parse("1650"), "ذمة المورد:")
        val d = JournalKernel.validate(req(sourceId = "pur-1", lines = lines), ctx())
        eq(d.lines.first { it.accountId == "acc-1201" }.debit, Money.parse("1500")) // 1000 + 500 شحن
    }
    check("سند قبض يخفّض الذمة") {
        val d = JournalKernel.validate(req(sourceId = "rcp-1", lines = Rules.receipt(book(), Money.parse("300"))), ctx())
        eq(d.lines.first { it.accountId == "acc-1103" }.credit, 30_000L)
    }
    check("مسير رواتب متوازن") {
        val lines = Rules.payroll(book(), Money.parse("10000"), Money.parse("500"), Money.parse("9500"))
        val d = JournalKernel.validate(req(sourceId = "pay-1", lines = lines), ctx())
        eq(d.totalBase, Money.parse("10000"))
    }
    check("تسوية جرد بالعجز") {
        val d = JournalKernel.validate(req(sourceId = "cnt-1", lines = Rules.stockCount(book(), -Money.parse("250"))), ctx())
        eq(d.lines.first { it.accountId == "acc-5105" }.debit, 25_000L)
    }

    // ── التكلفة ───────────────────────────────────────────────
    println("\n[تكلفة المخزون]")
    check("المتوسط المرجّح") {
        var s = Stock()
        s = Costing.receive(s, 1000, Money.parse("10"))    // 1 وحدة × 10
        eq(s.avgCostMinor, Money.parse("10"))
        s = Costing.receive(s, 1000, Money.parse("20"))    // +1 × 20 ⇒ 15
        eq(s.avgCostMinor, Money.parse("15"))
        eq(s.qtyMilli, 2000L)
    }
    check("الصرف لا يغيّر المتوسط") {
        val s = Costing.receive(Stock(), 2000, Money.parse("15"))
        val (cost, after) = Costing.issue(s, 500)          // 0.5 وحدة × 15
        eq(cost, Money.parse("7.50"))
        eq(after.avgCostMinor, Money.parse("15"))
        eq(after.qtyMilli, 1500L)
    }
    check("منع الصرف أكثر من المتاح") {
        val s = Costing.receive(Stock(), 1000, Money.parse("5"))
        try { Costing.issue(s, 2000); throw AssertionError("قُبل الصرف الزائد") }
        catch (e: IllegalArgumentException) { /* متوقع */ }
    }
    check("تسوية الجرد") {
        val s = Costing.receive(Stock(), 1000, Money.parse("10"))
        val (dq, dv, _) = Costing.adjustTo(s, 1200)
        eq(dq, 200L); eq(dv, Money.parse("2"))
    }

    // ── فروق العملة ───────────────────────────────────────────
    println("\n[فروق العملة]")
    check("سداد بسعر أعلى = خسارة") {
        val r = Fx.realized(Money.parse("100", "USD"), "250", "260", "USD", "YER")
        eq(r.loss, Money.parse("1000"), "الخسارة:")   // 100 × 10 = 1000 ريال
    }
    check("سداد بسعر أقل = ربح") {
        val r = Fx.realized(Money.parse("100", "USD"), "250", "240", "USD", "YER")
        eq(r.gain, Money.parse("1000"))
    }


    // ── دورة عمل كاملة عبر خدمة الترحيل ───────────────────────
    println("\n[دورة عمل كاملة: مخزون ← بيع ← مردود ← شراء ← سداد]")
    val store = InMemoryStore().apply {
        addCompany(CompanyRow(CO, "مؤسسة تجارية", "YER", setOf("YER", "USD")))
        seedChart(CO)
        openPeriods(CO, 2026)
    }
    val svc = PostingService(store)
    val p1 = "prod-1"

    check("مخزون افتتاحي ١٠ وحدات × ٧٥٠") {
        svc.receiveOpeningStock(CO, "2026-01-01", "open-1", p1, 10_000, Money.parse("750"))
        eq(store.stock(CO, p1).avgCostMinor, Money.parse("750"))
        eq(store.stock(CO, p1).qtyMilli, 10_000L)
    }
    check("بيع نقدي يسجّل تكلفة البضاعة المباعة") {
        val r = svc.recordSale(
            CO, "2026-03-05", "INV-1",
            listOf(PostingService.SaleInput(p1, 2_000, Money.parse("1200"), "15")),
            PaymentSplit(cashMinor = Money.parse("2760")),
        )
        eq(r.cogs, Money.parse("1500"), "تكلفة المبيعات:")
        eq(r.totals.netSales, Money.parse("2400"), "صافي البيع:")
        eq(r.totals.tax, Money.parse("360"), "الضريبة:")
        eq(store.stock(CO, p1).qtyMilli, 8_000L, "المخزون بعد البيع:")
        eq(store.stock(CO, p1).avgCostMinor, Money.parse("750"), "المتوسط لم يتغير:")
    }
    check("مردود يعيد المخزون والإيراد") {
        svc.recordSalesReturn(
            CO, "2026-03-07", "RET-1",
            listOf(PostingService.SaleInput(p1, 1_000, Money.parse("1200"), "15")),
            refundCash = Money.parse("1380"),
        )
        eq(store.stock(CO, p1).qtyMilli, 9_000L)
        eq(svc.balanceOf(CO, Chart.Acc.SALES), -Money.parse("2400"))
        eq(svc.balanceOf(CO, Chart.Acc.SALES_RETURN), Money.parse("1200"))
    }
    check("شراء ١٠ وحدات + شحن ٥٠٠ يُرأسمَل على التكلفة") {
        svc.recordPurchase(
            CO, "2026-03-10", "PUR-1",
            listOf(PostingService.PurchaseInput(p1, 10_000, Money.parse("750"), landedShareMinor = Money.parse("500"))),
            landedCostMinor = Money.parse("500"),
        )
        eq(store.stock(CO, p1).qtyMilli, 19_000L, "الكمية:")
        eq(store.stock(CO, p1).valueMinor, Money.parse("14750"), "قيمة المخزون:")
    }
    check("سداد المورد + العكس يعيد الرصيد") {
        val e = svc.paySupplier(CO, "2026-03-12", "PV-1", Money.parse("3000"))
        eq(svc.balanceOf(CO, Chart.Acc.AP), -Money.parse("8000") + Money.parse("3000"), "ذمة المورد:")
        svc.reverse(CO, e.id, "2026-03-13", "خطأ إدخال")
        eq(svc.balanceOf(CO, Chart.Acc.AP), -Money.parse("8000"))
    }
    check("الميزان متوازن بعد الدورة كاملة") {
        val tb = svc.trialBalance(CO)
        eq(tb.balanced, true)
        eq(tb.totalDebit, tb.totalCredit)
    }
    check("قيمة المخزون في الأستاذ = دفتر الحركات") {
        eq(svc.balanceOf(CO, Chart.Acc.INVENTORY), Money.parse("14750"))
        eq(store.stock(CO, p1).valueMinor, Money.parse("14750"))
        eq(svc.inventoryMatchesLedger(CO, listOf(p1)), true)
    }
    check("الربح الإجمالي صحيح (١٢٠٠ − ٧٥٠ = ٤٥٠)") {
        val netSales = -svc.balanceOf(CO, Chart.Acc.SALES) - svc.balanceOf(CO, Chart.Acc.SALES_RETURN)
        val cogs = svc.balanceOf(CO, Chart.Acc.COGS)
        eq(netSales, Money.parse("1200"), "صافي المبيعات:")
        eq(cogs, Money.parse("750"), "تكلفة المبيعات:")
        eq(netSales - cogs, Money.parse("450"), "الربح الإجمالي:")
    }
    check("منع بيع كمية أكبر من المتاح") {
        try {
            svc.recordSale(
                CO, "2026-03-20", "INV-BAD",
                listOf(PostingService.SaleInput(p1, 99_000, Money.parse("1200"), "0")),
                PaymentSplit(cashMinor = Money.parse("1200")),
            )
            throw AssertionError("قُبل البيع بأكثر من المتاح")
        } catch (e: AccountingException) { eq(e.code, Err.BAD_LINE) }
    }


    // ── القوائم المالية ───────────────────────────────────────
    println("\n[القوائم المالية]")
    val allEntries = store.entries(CO).map { e ->
        val r = JournalRequest(
            CO, e.date, e.description, e.sourceType, e.sourceId, e.currency,
            e.lines.map { JournalLine(it.accountId, it.debit, it.credit, it.currency, it.fxRate, it.costCenterId, it.note) },
        )
        r to e.lines
    }
    val accMap = store.accounts(CO).associateBy { it.id }

    check("قائمة الدخل: صافي المبيعات والتكلفة والربح") {
        val is1 = Statements.incomeStatement(allEntries, accMap)
        eq(is1.revenue, Money.parse("2400"), "الإيراد:")
        eq(is1.revenueReturn, Money.parse("1200"), "المردودات:")
        eq(is1.netRevenue, Money.parse("1200"), "الصافي:")
        eq(is1.cogs, Money.parse("750"), "التكلفة:")
        eq(is1.grossProfit, Money.parse("450"), "الربح الإجمالي:")
        eq(is1.netProfit, Money.parse("450"), "صافي الربح:")
    }
    check("الميزانية العمومية متوازنة") {
        val bs = Statements.balanceSheet(allEntries, accMap)
        eq(bs.totalAssets, Money.parse("16130"), "الأصول:")      // نقد 1380 + مخزون 14750
        eq(bs.totalLiabilities, Money.parse("8180"), "الالتزامات:") // موردون 8000 + ضريبة 180
        eq(bs.totalEquity, Money.parse("7500"), "حقوق الملكية:")
        eq(bs.retainedEarnings, Money.parse("450"), "الأرباح المحتجزة:")
        eq(bs.balanced, true)
    }
    check("التدفق النقدي يتطابق مع الرصيد الفعلي") {
        val cf = Statements.cashFlow(allEntries, accMap, "2026-03-01", "2026-03-31")
        eq(cf.openingCash, 0L, "الافتتاحي:")
        eq(cf.closingCash, Money.parse("1380"), "الختامي:")   // 2760 − 1380
        eq(cf.verified, true)
    }
    check("التقرير الضريبي: صافي المستحق") {
        val t = Statements.taxReport(allEntries, accMap)
        eq(t.outputTax, Money.parse("180"), "المخرجات:")
        eq(t.inputTax, 0L, "المدخلات:")
        eq(t.netPayable, Money.parse("180"))
    }
    check("أعمار الديون تُصنّف الأرصدة في الشرائح") {
        val items = listOf(
            Statements.OpenItem("INV-1", "c1", "2026-01-01", Money.parse("1000")),  // 60+ يوم
            Statements.OpenItem("INV-2", "c1", "2026-03-01", Money.parse("500")),   // حديث
            Statements.OpenItem("INV-3", "c2", "2026-02-20", Money.parse("700")),
        )
        val r = Statements.aging(items, "2026-03-15")
        eq(r.size, 2)
        val c1 = r.first { it.partyId == "c1" }
        eq(c1.total, Money.parse("1500"))
        eq(c1.buckets["١-٣٠"], Money.parse("500"))   // عمره ١٤ يوماً
    }

    // ── الأصول الثابتة ───────────────────────────────────────
    println("\n[الأصول الثابتة]")
    check("جدول إهلاك بالقسط الثابت ١٢ شهراً") {
        val sch = FixedAssets.schedule(Money.parse("12000"), Money.parse("0"), 12, "2026-01")
        eq(sch.size, 12)
        eq(sch.sumOf { it.amount }, Money.parse("12000"))
        eq(sch.first().period, "2026-01")
        eq(sch.last().period, "2026-12")
        eq(FixedAssets.bookValue(Money.parse("12000"), sch, 6), Money.parse("6000"))
    }
    check("جدول إهلاك مع قيمة إنقاذ") {
        val sch = FixedAssets.schedule(Money.parse("10000"), Money.parse("1000"), 3, "2026-01")
        eq(sch.sumOf { it.amount }, Money.parse("9000"))
    }
    check("جدول إهلاك متناقص لا ينزل تحت الإنقاذ") {
        val sch = FixedAssets.schedule(Money.parse("10000"), Money.parse("1000"), 24, "2026-01", FixedAssets.Method.DECLINING)
        val total = sch.sumOf { it.amount }
        eq(total <= Money.parse("9000"), true, "المجموع يجب ألا يتجاوز:")
    }

    // ── الرواتب ──────────────────────────────────────────────
    println("\n[الرواتب]")
    check("حساب المسير: أساسي + بدلات + إضافي − خصومات − سلف") {
        val r = Payroll.compute(
            Payroll.Input(
                basicMinor = Money.parse("5000"), allowancesMinor = Money.parse("500"),
                overtimeHours = 10, overtimeRatePerHourMinor = Money.parse("30"),
                deductionsMinor = Money.parse("200"), advancesMinor = Money.parse("300"),
            ),
        )
        eq(r.grossMinor, Money.parse("5800"))
        eq(r.totalDeductionsMinor, Money.parse("500"))
        eq(r.netMinor, Money.parse("5300"))
    }
    check("رفض خصومات أكبر من الاستحقاق") {
        try {
            Payroll.compute(Payroll.Input(Money.parse("100"), deductionsMinor = Money.parse("200")))
            throw AssertionError("قُبلت خصومات زائدة")
        } catch (e: IllegalArgumentException) { /* متوقع */ }
    }

    // ── السندات ──────────────────────────────────────────────
    println("\n[السندات]")
    check("سند تحويل بين صندوق وبنك") {
        val d = JournalKernel.validate(req(sourceId = "ft-1", lines = Vouchers.transfer(book(), Money.parse("1000"), toBank = true)), ctx())
        eq(d.lines.first { it.accountId == "acc-1102" }.debit, Money.parse("1000"))
        eq(d.lines.first { it.accountId == "acc-1101" }.credit, Money.parse("1000"))
    }
    check("تصفية عهدة: مصروف + مرتجع = العهدة") {
        val d = JournalKernel.validate(
            req(sourceId = "cs-2", lines = Vouchers.custodySettle(book(), Money.parse("1000"), Money.parse("700"), Money.parse("300"))),
            ctx(),
        )
        eq(d.totalBase, Money.parse("1000"))
    }
    check("سند حوالة مع عمولة") {
        val d = JournalKernel.validate(req(sourceId = "rm-1", lines = Vouchers.remittance(book(), Money.parse("5000"), Money.parse("50"))), ctx())
        eq(d.totalBase, Money.parse("5050"))
    }
    check("تسوية عجز نقدي") {
        val d = JournalKernel.validate(req(sourceId = "cd-1", lines = Vouchers.cashDiscrepancy(book(), -Money.parse("75"))), ctx())
        eq(d.lines.first { it.accountId == "acc-5105" }.debit, Money.parse("75"))
    }
    check("إشعار دائن يخفّض الذمة") {
        val d = JournalKernel.validate(req(sourceId = "cn-1", lines = Vouchers.creditNote(book(), Money.parse("400"))), ctx())
        eq(d.lines.first { it.accountId == "acc-1103" }.credit, Money.parse("400"))
    }
    check("أمانة مستلمة تُسجَّل التزاماً لا إيراداً") {
        val d = JournalKernel.validate(req(sourceId = "dp-1", lines = Vouchers.depositReceived(book(), Money.parse("2000"))), ctx())
        eq(d.lines.first { it.accountId == "acc-2301" }.credit, Money.parse("2000"))
    }
    check("مصارفة عملات مع ربح") {
        // صرفنا ١٠٠ دولار (سعر ٢٥٠ ⇒ ٢٥٠٠٠ ريال) واستلمنا ٢٥٥٠٠ ريال ⇒ ربح ٥٠٠
        val lines = Vouchers.fxExchange(
            book(),
            toAmount = Money.parse("25500", "YER"), toCurrency = "YER",
            fromAmount = Money.parse("100", "USD"), fromCurrency = "USD",
            fromRate = "250",
            gain = Money.parse("500"),
        )
        val d = JournalKernel.validate(req(sourceId = "fx-1", lines = lines), ctx())
        eq(d.lines.size, 3)
        eq(d.totalBase, Money.parse("25500"), "الأساس:")
    }

    // ── الشيكات ──────────────────────────────────────────────
    println("\n[الشيكات]")
    check("مسار الشيك: مستلم ← مودع ← محقق") {
        var st = Cheques.Status.RECEIVED
        st = Cheques.next(st, Cheques.Action.DEPOSIT)
        eq(st, Cheques.Status.DEPOSITED)
        st = Cheques.next(st, Cheques.Action.CLEAR)
        eq(st, Cheques.Status.CLEARED)
    }
    check("رفض انتقال غير مسموح (محقق ← ارتجاع)") {
        try {
            Cheques.next(Cheques.Status.CLEARED, Cheques.Action.BOUNCE)
            throw AssertionError("قُبل انتقال غير مسموح")
        } catch (e: AccountingException) { eq(e.code, "BAD_CHEQUE_STATE") }
    }
    check("ترحيل استلام وتحقق الشيك") {
        JournalKernel.validate(req(sourceId = "chq-1", lines = Cheques.receivedLines(book(), Money.parse("900"))), ctx())
        JournalKernel.validate(req(sourceId = "chq-2", lines = Cheques.clearedLines(book(), Money.parse("900"))), ctx())
        eq(true, true)
    }


    // ── التفقيط ──────────────────────────────────────────────
    println("\n[التفقيط]")
    check("أرقام مفردة ومثناة وجمع") {
        eq(Tafqeet.plain(Money.parse("1")), "ريال يمني واحد")
        eq(Tafqeet.plain(Money.parse("2")), "ريالان يمنيان")
        eq(Tafqeet.plain(Money.parse("5")), "خمسة ريالات يمنية")
        eq(Tafqeet.plain(Money.parse("100")), "مئة ريال يمني")
    }
    check("الآلاف والملايين") {
        eq(Tafqeet.plain(Money.parse("1000")), "ألف ريال يمني")
        eq(Tafqeet.plain(Money.parse("2000")), "ألفان ريال يمني")
        eq(Tafqeet.plain(Money.parse("3000")), "ثلاثة آلاف ريال يمني")
        eq(Tafqeet.plain(Money.parse("1000000")), "مليون ريال يمني")
    }
    check("رقم مركب ١٢٣٤٫٥٠") {
        val t = Tafqeet.plain(Money.parse("1234.50"))
        eq(t, "ألف ومئتان وأربعة وثلاثون ريال يمني وخمسون فلس")
    }
    check("البادئة واللاحقة") {
        val t = Tafqeet.amount(Money.parse("250"))
        eq(t.startsWith("فقط "), true)
        eq(t.endsWith(" لا غير"), true)
    }
    check("عملة أخرى (SAR + هللات)") {
        eq(Tafqeet.plain(Money.parse("3.05", "SAR"), "SAR"), "ثلاثة ريالات سعودية وخمس هللات")
    }

    // ── الأطراف والتحصيل الجزئي ────────────────────────────
    println("\n[الذمم والتحصيل]")
    check("توزيع دفعة على الفواتير الأقدم أولاً") {
        val invs = listOf(
            Parties.OpenInvoice("A", "INV-1", "2026-01-05", "2026-02-05", Money.parse("1000")),
            Parties.OpenInvoice("B", "INV-2", "2026-02-05", "2026-03-05", Money.parse("1000")),
            Parties.OpenInvoice("C", "INV-3", "2026-03-05", "2026-04-05", Money.parse("1000")),
        )
        val r = Parties.allocate(Money.parse("2500"), invs)
        eq(r.allocations.size, 3)
        eq(r.allocations[0].amountMinor, Money.parse("1000"))
        eq(r.allocations[1].amountMinor, Money.parse("1000"))
        eq(r.allocations[2].amountMinor, Money.parse("500"))
        eq(r.unallocatedMinor, 0L)
    }
    check("دفعة أكبر من المستحق ⇒ الباقي دفعة مقدمة") {
        val invs = listOf(Parties.OpenInvoice("A", "INV-1", "2026-01-05", "2026-02-05", Money.parse("1000")))
        val r = Parties.allocate(Money.parse("1500"), invs)
        eq(r.unallocatedMinor, Money.parse("500"))
    }
    check("توزيع يدوي لا يتجاوز المتبقي") {
        val invs = listOf(Parties.OpenInvoice("A", "INV-1", "2026-01-01", "2026-02-01", Money.parse("800"), Money.parse("300")))
        val r = Parties.allocateManual(Money.parse("1000"), listOf(invs[0] to Money.parse("1000")))
        eq(r.allocations[0].amountMinor, Money.parse("500"))   // المتبقي فقط
        eq(r.unallocatedMinor, Money.parse("500"))
    }
    check("كشف حساب يجري رصيداً تراكمياً") {
        val acc = "acc-cust"
        val r1 = JournalRequest(CO, "2026-01-10", "فاتورة", "INVOICE", "INV-1", null, emptyList())
        val e1 = r1 to listOf(NormLine(acc, Money.parse("1000"), 0, "YER", "1", Money.parse("1000")))
        val r2 = JournalRequest(CO, "2026-01-20", "تحصيل", "VOUCHER", "RV-1", null, emptyList())
        val e2 = r2 to listOf(NormLine(acc, 0, Money.parse("400"), "YER", "1", Money.parse("400")))
        val rows = Parties.statement(listOf(e1, e2), acc)
        eq(rows.size, 2)
        eq(rows[0].balance, Money.parse("1000"))
        eq(rows[1].balance, Money.parse("600"))
    }
    check("حد ائتماني") {
        eq(Parties.exceedsLimit(Money.parse("900"), Money.parse("200"), Money.parse("1000")), true)
        eq(Parties.exceedsLimit(Money.parse("500"), Money.parse("200"), Money.parse("1000")), false)
        eq(Parties.exceedsLimit(Money.parse("5000"), Money.parse("200"), 0L), false)  // بلا حد = بلا قيد
    }

    // ── إقفال الفترة ─────────────────────────────────────────
    println("\n[إقفال الفترة]")
    check("قيد الإقفال يصفّر النتيجة إلى الأرباح المبقاة") {
        val accs = accounts()
        val retained = "acc-${Chart.Acc.RETAINED}"
        val rs = JournalRequest(CO, "2026-03-05", "بيع", "INVOICE", "I1", null, emptyList())
        val sale = rs to listOf(
            NormLine("acc-1101", Money.parse("1000"), 0, "YER", "1", Money.parse("1000")),
            NormLine("acc-4101", 0, Money.parse("1000"), "YER", "1", Money.parse("1000")),
        )
        val rc = JournalRequest(CO, "2026-03-05", "تكلفة", "INVOICE", "I1", null, emptyList())
        val cost = rc to listOf(
            NormLine("acc-5101", Money.parse("400"), 0, "YER", "1", Money.parse("400")),
            NormLine("acc-1201", 0, Money.parse("400"), "YER", "1", Money.parse("400")),
        )
        val summary = PeriodClose.buildClosingEntry(listOf(sale, cost), accs, retained)
        eq(summary.revenue, Money.parse("1000"), "الإيراد:")
        eq(summary.expenses, Money.parse("400"), "المصروفات:")
        eq(summary.netProfit, Money.parse("600"), "صافي الربح:")

        val draft = JournalKernel.validate(
            req(sourceId = "close-1", lines = summary.closingLines), ctx(),
        )
        eq(draft.totalBase, Money.parse("1000"))   // مدين الإيراد ١٠٠٠ = دائن المصروف ٤٠٠ + المبقاة ٦٠٠
    }

    // ── السندات والأصول والرواتب من الخدمة ────────────────────
    println("\n[الخدمة: سندات · إهلاك · رواتب · شيكات · إقفال]")
    check("سند تحويل من الخدمة") {
        val e = svc.postVoucher(
            CO, "2026-03-20", "FT-9", Vouchers.Kind.TRANSFER,
            Vouchers.transfer(AccountBook(store.accounts(CO).associate { it.code to it.id }), Money.parse("700"), toBank = true),
        )
        eq(e.totalBase, Money.parse("700"))
        eq(e.status, "POSTED")
    }
    check("إهلاك أصل يُنشئ قيداً") {
        val before = svc.balanceOf(CO, Chart.Acc.ACCUM_DEP)
        svc.depreciate(CO, "2026-03-31", "asset-1", Money.parse("250"))
        eq(svc.balanceOf(CO, Chart.Acc.ACCUM_DEP), before - Money.parse("250"))
    }
    check("مسير رواتب يُنشئ قيداً متوازناً") {
        val e = svc.payrollRun(CO, "2026-03-31", "PAY-1", Money.parse("10000"), Money.parse("1000"), Money.parse("9000"))
        eq(e.totalBase, Money.parse("10000"))
    }
    check("شيك: استلام ثم تحقق") {
        val b = AccountBook(store.accounts(CO).associate { it.code to it.id })
        svc.post(JournalRequest(CO, "2026-03-21", "شيك وارد", "CHEQUE", "CHQ-1", null, Cheques.receivedLines(b, Money.parse("900"))))
        val e = svc.chequeAction(CO, "2026-03-22", "CHQ-1", Money.parse("900"), Cheques.Status.DEPOSITED, Cheques.Action.CLEAR)
        eq(e.totalBase, Money.parse("900"))
    }
    check("إقفال الفترة يُنشئ قيد إقفال") {
        val e = svc.closePeriod(CO, 2026, 3, "2026-03-31")
        eq(e != null, true, "قيد الإقفال:")
        eq(svc.trialBalance(CO).balanced, true)
    }

    // ── محرّك المستندات ────────────────────────────────────────
    fun docLines() = listOf(
        DocLine("p1", "شاشة", 2_000, 5_000, discountPct = "10", taxRatePct = "15", unitCostMinor = 3_000),
        DocLine("p2", "كيبورد", 1_000, 2_500, taxRatePct = "15", unitCostMinor = 1_500),
    )

    check("فاتورة بندين: الإجمالي قبل الخصم") {
        val t = DocEngine.totals(docLines())
        eq(t.gross, 12_500L, "إجمالي البنود:")
        eq(t.lineDiscount, 1_000L, "خصم البند:")
        eq(t.net, 11_500L, "الصافي:")
        eq(t.cogs, 7_500L, "التكلفة:")
    }

    check("خصم المستند موزّع بالضبط على البنود") {
        val t = DocEngine.totals(docLines(), invoiceDiscountPct = "10")
        eq(t.invoiceDiscount, 1_150L, "خصم المستند:")
        eq(t.net, 10_350L, "الصافي:")
        eq(t.lines.sumOf { it.net }, t.net, "مجموع صافي البنود:")
        eq(t.discount, t.lineDiscount + t.invoiceDiscount, "الإجمالي:")
        eq(t.total, t.net + t.tax + t.rounding, "التركيب:")
    }

    check("توزيع أكبر باقٍ لا يضيّع هللة") {
        eq(DocEngine.distribute(100, listOf(1, 1, 1)).sum(), 100L)
        val parts = DocEngine.distribute(100, listOf(1, 1, 1))
        eq(parts.max() - parts.min() <= 1, true, "الفروق:")
        eq(DocEngine.distribute(0, listOf(5, 5)), listOf(0L, 0L))
        eq(DocEngine.distribute(7, listOf(0, 0)), listOf(0L, 0L))
    }

    check("الضريبة تُحسب على الصافي بعد الخصم") {
        val t = DocEngine.totals(docLines(), invoiceDiscountPct = "10")
        val expected = t.lines.sumOf { Money.percent(it.net, it.line.taxRatePct) }
        eq(t.tax, expected, "الضريبة:")
        eq(t.tax < Money.percent(t.gross, "15"), true, "الضريبة بعد الخصم أقل:")
    }

    check("تقريب الإجمالي لأقرب مئة") {
        val t = DocEngine.totals(
            listOf(DocLine("p", "صنف", 1_000, 1_234, taxRatePct = "0")),
            roundingStep = 100,
        )
        eq(t.total % 100, 0L, "مضاعف:")
        eq(t.rounding, t.total - (t.net + t.tax), "فروق التقريب:")
    }

    check("تحقق المستند يكشف الكمية الصفرية والاسم الفارغ") {
        val errors = DocEngine.validate(listOf(DocLine("", "", 0, -5, discountPct = "150")))
        eq(errors.size >= 4, true, "عدد الأخطاء:${errors.size}")
        eq(DocEngine.validate(docLines()).isEmpty(), true, "سليم:")
    }

    check("قيد فاتورة البنود المتعددة متوازن عبر الكيرنل") {
        val t = DocEngine.totals(docLines(), invoiceDiscountPct = "10")
        val (lines, totals) = DocPosting.sales(book(), t, Settlement.cash(5_000))
        eq(totals.total, t.total, "الإجمالي:")
        val r = JournalRequest(CO, "2026-03-20", "فاتورة", "INVOICE", "INV-1", null, lines)
        val draft = JournalKernel.validate(r, ctx())
        val debit = draft.lines.sumOf { it.debit }
        val credit = draft.lines.sumOf { it.credit }
        eq(debit, credit, "توازن: مدين مقابل دائن")
    }

    check("قيد المبيعات الآجل بالكامل يُنشئ ذمة بالباقي") {
        val t = DocEngine.totals(docLines())
        val (lines, _) = DocPosting.sales(book(), t, Settlement.credit())
        val ar = lines.first { it.accountId == "acc-1103" }
        eq(ar.debit, t.total, "ذمة العميل:")
    }

    check("الدفع الزائد يُرفض") {
        val t = DocEngine.totals(docLines())
        try {
            DocPosting.sales(book(), t, Settlement.cash(t.total + 100))
            throw AssertionError("قُبل دفع زائد")
        } catch (e: AccountingException) {
            eq(e.code, "OVERPAID")
        }
    }

    check("مردود مبيعات بعكس الإيراد والضريبة والتكلفة") {
        val t = DocEngine.totals(docLines())
        val (lines, _) = DocPosting.salesReturn(book(), t, refundCashMinor = t.total)
        val r = JournalRequest(CO, "2026-03-21", "مردود", "RETURN", "SR-1", null, lines)
        val draft = JournalKernel.validate(r, ctx())
        eq(draft.lines.sumOf { it.debit }, draft.lines.sumOf { it.credit }, "توازن المردود:")
    }

    check("فاتورة مشتريات بمصاريف وصول متوازنة") {
        val t = DocEngine.totals(
            listOf(DocLine("p1", "بضاعة", 3_000, 4_000, taxRatePct = "5")),
            landedCostMinor = 700,
        )
        val (lines, pt) = DocPosting.purchase(book(), t, cashPaidMinor = 1_000, landedCostMinor = 700)
        eq(pt.landed, 700L, "مصاريف الوصول:")
        eq(t.net, 12_700L, "قيمة المخزون مع الوصول:")
        val r = JournalRequest(CO, "2026-03-22", "شراء", "PURCHASE", "P-1", null, lines)
        val draft = JournalKernel.validate(r, ctx())
        eq(draft.lines.sumOf { it.debit }, draft.lines.sumOf { it.credit }, "توازن الشراء:")
    }

    check("دورة المستند: مسودة ⇒ معتمد ⇒ مُرحَّل · ولا ترحيل من مسودة") {
        eq(DocFlow.next(DocKind.INVOICE, DocStatus.DRAFT, DocAction.CONFIRM), DocStatus.CONFIRMED)
        eq(DocFlow.next(DocKind.INVOICE, DocStatus.CONFIRMED, DocAction.POST), DocStatus.POSTED)
        var code = ""
        try { DocFlow.next(DocKind.INVOICE, DocStatus.DRAFT, DocAction.POST) }
        catch (e: AccountingException) { code = e.code }
        eq(code, "BAD_TRANSITION", "رفض الترحيل:")
        eq(DocFlow.editable(DocStatus.POSTED), false)
        eq(DocFlow.posts(DocKind.QUOTE), false)
        eq(DocFlow.posts(DocKind.INVOICE), true)
    }

    // ── الطباعة ───────────────────────────────────────────────
    check("قالب الفاتورة يحتوي الترويسة والبنود والتفقيط") {
        val t = DocEngine.totals(docLines())
        val doc = PrintDoc(
            title = "فاتورة مبيعات", number = "INV-2026-00001", date = "2026-03-20",
            partyLabel = "العميل", partyName = "مؤسسة النور", totals = t, currency = "YER",
        )
        val html = Printing.html(doc, PrintCompany("ميزان للتجارة"), Paper.A4)
        eq(html.contains("ميزان للتجارة"), true, "اسم المنشأة:")
        eq(html.contains("INV-2026-00001"), true, "رقم الفاتورة:")
        eq(html.contains("كيبورد"), true, "اسم البند:")
        eq(html.contains("فقط"), true, "بادئة التفقيط:")
        eq(html.contains(Tafqeet.plain(t.total, "YER")), true, "نص التفقيط:")
        eq(html.contains("dir=\"rtl\""), true, "اتجاه:")
        eq(html.contains("A4".let { "210mm" }), true, "مقاس الورق:")
    }

    check("الحمي الحراري يستخدم عرض 80مم") {
        val t = DocEngine.totals(docLines())
        val html = Printing.html(
            PrintDoc(title = "إيصال", number = "R-1", date = "2026-03-20", partyLabel = "العميل", partyName = "زبون", totals = t),
            PrintCompany("متجري"), Paper.THERMAL_80,
        )
        eq(html.contains("80mm"), true, "عرض حراري:")
        eq(Paper.THERMAL_58.isThermal, true)
    }

    check("التهريب يمنع حقن HTML") {
        val bad = Printing.escape("<script>alert('x')</script> & \"س\"")
        eq(bad.contains("<script>"), false, "سكربت:")
        eq(bad.contains("&lt;script&gt;"), true, "مهرَّب:")
        eq(bad.contains("&amp;"), true, "علامة العطف:")
    }

    check("CSV يبدأ بـ BOM ويحوّل علامات التنصيص") {
        val csv = Printing.csv(listOf("الصنف", "الكمية"), listOf(listOf("شاشة \"24\"", "2")))
        eq(csv.startsWith("\uFEFF"), true, "BOM:")
        eq(csv.contains("\"شاشة \"\"24\"\"\""), true, "تهريب CSV:")
        eq(csv.contains("\r\n"), true, "أسطر:")
    }

    // ── الأمان ────────────────────────────────────────────────
    check("تجزئة PIN وتحقّقها") {
        val salt = Pin.newSalt()
        val h = Pin.hash("1234", salt, iterations = 2_000)
        eq(Pin.verify("1234", h), true, "الصحيح:")
        eq(Pin.verify("1235", h), false, "الخطأ:")
        eq(h.hashB64 == "1234", false, "لا يُخزَّن خاماً:")
        eq(Pin.isValidFormat("123"), false, "قصير:")
        eq(Pin.isValidFormat("12a4"), false, "غير رقمي:")
        eq(Pin.isValidFormat("123456"), true, "ستة:")
    }

    check("قفل الدخول بعد خمس محاولات فاشلة") {
        val gate = AuthGate(maxAttempts = 5, lockMillis = 60_000)
        val h = Pin.hash("4321", iterations = 2_000)
        repeat(4) { eq(gate.attempt("0000", h, 1_000L), LoginResult.WRONG_PIN, "محاولة ${it + 1}:") }
        eq(gate.remainingAttempts(), 1)
        eq(gate.attempt("0000", h, 1_000L), LoginResult.WRONG_PIN, "الخامسة:")
        eq(gate.attempt("4321", h, 2_000L), LoginResult.LOCKED, "مقفل:")
        eq(gate.attempt("4321", h, 100_000L), LoginResult.OK, "بعد انتهاء القفل:")
        eq(gate.remainingAttempts(), 5)
    }

    check("صلاحيات الأدوار") {
        eq(Rbac.allows(Role.CASHIER, Perm.SALE_EDIT), true)
        eq(Rbac.allows(Role.CASHIER, Perm.PERIOD_CLOSE), false, "أمين الصندوق لا يقفل:")
        eq(Rbac.allows(Role.MANAGER, Perm.PERIOD_CLOSE), true)
        eq(Rbac.allows(Role.OWNER, Perm.USER_MANAGE), true)
        eq(Rbac.allows(Role.VIEWER, Perm.SALE_EDIT), false)
        eq(Rbac.allows(Role.ACCOUNTANT, Perm.USER_MANAGE), false, "المحاسب لا يدير المستخدمين:")
        var code = ""
        try { Rbac.require(Role.WAREHOUSE, Perm.PAYROLL_EDIT) }
        catch (e: AccountingException) { code = e.code }
        eq(code, "FORBIDDEN")
    }

    check("انتهاء الجلسة بالخمول") {
        val s = Sessions.open("u1", Role.OWNER, 0L)
        eq(Sessions.expired(s, 9 * 60_000L), false)
        eq(Sessions.expired(s, 11 * 60_000L), true)
        eq(Sessions.expired(Sessions.touch(s, 9 * 60_000L), 15 * 60_000L), false)
        eq(Sessions.can(s, Perm.SETTINGS_EDIT), true)
    }

    // ── النسخ الاحتياطي ─────────────────────────────────────────
    check("نسخة مشفّرة: تغليف ثم فتح") {
        val meta = Backup.Meta("1.0.0", "2026-11-03T10:00:00", CO, "منشأة الاختبار", mapOf("invoices" to 12, "items" to 40))
        val payload = """{"entries":[{"id":"e1"}],"note":"عربي"}"""
        val blob = Backup.seal(payload, "كلمة-مرور-قوية", meta, iterations = 3_000)
        eq(Backup.isSealed(blob), true)
        eq(blob.contains(payload), false, "الحمولة مشفّرة:")
        val (m, body) = Backup.open(blob, "كلمة-مرور-قوية")
        eq(body, payload, "الحمولة:")
        eq(m.companyName, "منشأة الاختبار", "اسم المنشأة:")
        eq(m.counts["invoices"], 12, "العدّادات:")
        eq(m.appVersion, "1.0.0")
    }

    check("نسخة بكلمة مرور خاطئة أو مُعدَّلة تُرفض") {
        val meta = Backup.Meta("1.0.0", "now", CO, "م")
        val blob = Backup.seal("""{"a":1}""", "correct-horse", meta, iterations = 2_000)
        var code = ""
        try { Backup.open(blob, "wrong-pass") } catch (e: AccountingException) { code = e.code }
        eq(code, "WRONG_PASSWORD")

        val lines = blob.lines().toMutableList()
        val ct = lines[4]
        lines[4] = ct.substring(0, ct.length - 4) + "AAA="
        code = ""
        try { Backup.open(lines.joinToString("\n"), "correct-horse") } catch (e: AccountingException) { code = e.code }
        eq(code, "WRONG_PASSWORD", "العبث بالحمولة:")

        code = ""
        try { Backup.open("hello world", "x") } catch (e: AccountingException) { code = e.code }
        eq(code, "BAD_BACKUP", "ملف غريب:")
    }

    check("بصمة الحمولة ثابتة ومختلفة عند التغيير") {
        val a = Backup.digest("""{"x":1}""")
        eq(a, Backup.digest("""{"x":1}"""))
        eq(a != Backup.digest("""{"x":2}"""), true)
        eq(a.length, 64)
    }

    // ── تسعير وتنبيهات وباقات ──────────────────────────────────
    fun item(id: String, qty: Long, min: Long = 0, price: Long = 1_000, cost: Long = 700) =
        ItemRow(id, "صنف $id", salePriceMinor = price, costMinor = cost, qtyMilli = qty, minQtyMilli = min)

    check("شرائح السعر تختار أعلى شريحة مؤهلة") {
        val it0 = item("p", 0, price = 1_000)
        val tiers = listOf(
            Pricing.Tier(10_000, 950, "جملة ١٠+"),
            Pricing.Tier(50_000, 880, "جملة ٥٠+"),
        )
        eq(Pricing.priceFor(it0, 5_000, tiers), 1_000L, "أقل من الشريحة:")
        eq(Pricing.priceFor(it0, 20_000, tiers), 950L, "شريحة ١٠:")
        eq(Pricing.priceFor(it0, 60_000, tiers), 880L, "شريحة ٥٠:")
        eq(Pricing.priceFor(it0, 60_000, emptyList()), 1_000L, "بلا شرائح:")
    }

    check("هامش الربح بدقة عشرية") {
        eq(Pricing.marginPct(1_000, 700), "30.0", "هامش ٣٠٪:")
        eq(Pricing.marginPct(1_000, 650), "35.0", "هامش ٣٥٪:")
        eq(Pricing.marginPct(1_000, 1_000), "0.0")
        eq(Pricing.pctTenths("15.5"), 155L, "عشرات النسبة:")
    }

    check("حد الخصم: أمين الصندوق مقيّد والمدير لا") {
        eq(Pricing.discountAllowed(Role.CASHIER, pctTenths = 100, limitTenths = 50), false, "١٠٪ > الحد:")
        eq(Pricing.discountAllowed(Role.CASHIER, pctTenths = 40, limitTenths = 50), true, "٤٪ ≤ الحد:")
        eq(Pricing.discountAllowed(Role.MANAGER, pctTenths = 300, limitTenths = 50), true, "المدير:")
    }

    check("تنبيهات المخزون: النافد أولاً ثم تحت الحد") {
        val alerts = Reorder.scan(listOf(
            item("a", 5_000, 5_000),
            item("b", 0, 1_000),
            item("c", 20_000, 1_000),
            ItemRow("s", "خدمة", isService = true),
        ))
        eq(alerts.size, 2, "عدد التنبيهات:")
        eq(alerts.first().itemId, "b", "النافد أولاً:")
        eq(alerts.first().level, Reorder.Level.OUT)
        eq(alerts[1].level, Reorder.Level.LOW)
        eq(Reorder.suggestedQty(item("a", 5_000, 5_000), 2), 5_000L, "المقترح للشراء:")
        eq(Reorder.suggestedQty(item("c", 50_000, 1_000), 2), 0L, "لا حاجة:")
    }

    check("تفكيك الباقة إلى مكوّنات") {
        val kit = Kits.Kit("bundle-1", listOf(
            Kits.Component("p1", 2_000),
            Kits.Component("p2", 1_000),
            Kits.Component("p1", 1_000),
        ))
        val parts = Kits.explode(kit, 3_000)
        eq(parts.size, 2, "عدد المكوّنات:")
        eq(parts.first { it.first == "p1" }.second, 9_000L, "p1 ×٤٫٥:")
        eq(parts.first { it.first == "p2" }.second, 3_000L)
        eq(Kits.buildable(kit, mapOf("p1" to 9_000, "p2" to 2_000)), 2_000L, "باقات ممكنة:")
        val short = Kits.shortages(kit, 2_000, mapOf("p1" to 4_000, "p2" to 2_000))
        eq(short, listOf("p1" to 2_000L), "الناقص:")
    }

    check("ذمم متأخرة وشيكات مستحقة قريباً") {
        val items = listOf(
            Statements.OpenItem("INV-1", "c1", "2026-01-01", 100_000),
            Statements.OpenItem("INV-2", "c2", "2026-03-20", 50_000),
        )
        val dues = DueWatch.overdue(items, "2026-04-15", termDays = 30)
        eq(dues.size, 1, "عدد المتأخر:")
        eq(dues.first().ref, "INV-1")
        eq(dues.first().daysOverdue, 74L, "أيام التأخر:")
        eq(DueWatch.total(dues), 100_000L)

        val cheques = listOf(
            DueWatch.ChequeItem("ch1", "c1", 10_000, "2026-04-18", true),
            DueWatch.ChequeItem("ch2", "c2", 20_000, "2026-05-20", false),
        )
        eq(DueWatch.chequesInWindow(cheques, "2026-04-15", 7).map { it.id }, listOf("ch1"))
    }

    check("مؤشرات: أفضل الأصناف والأصناف الخاسرة") {
        val rows = listOf(
            Insights.ProductSale("p1", 2_000, 10_000, 6_000),
            Insights.ProductSale("p2", 1_000, 5_000, 6_000),
            Insights.ProductSale("p3", 4_000, 8_000, 2_000),
        )
        eq(Insights.topByProfit(rows, 2).map { it.productId }, listOf("p3", "p1"))
        eq(Insights.lossMaking(rows).map { it.productId }, listOf("p2"))
        eq(Insights.averageBasket(listOf(100L, 200L, 300L)), 200L)
        eq(Insights.creditRatio(40_000, 100_000), "40.0")
    }

    // ── ترحيل المستندات متعددة البنود عبر الخدمة ────────────
    check("فاتورة بندين عبر الخدمة: قيد متوازن ومخزون صحيح") {
        val st = InMemoryStore().apply {
            addCompany(CompanyRow("co-2", "منشأة الاختبار", "YER", setOf("YER")))
            seedChart("co-2")
            openPeriods("co-2", 2026)
        }
        val s2 = PostingService(st)
        s2.receiveOpeningStock("co-2", "2026-01-01", "OP-1", "p1", 20_000, Money.parse("500"))
        s2.receiveOpeningStock("co-2", "2026-01-01", "OP-2", "p2", 10_000, Money.parse("800"))

        val r = s2.recordInvoiceDoc(
            "co-2", "2026-03-10", "INV-D1",
            listOf(
                PostingService.DocInput("p1", "شاشة", 3_000, Money.parse("900"), discountPct = "10", taxRatePct = "15"),
                PostingService.DocInput("p2", "كيبورد", 2_000, Money.parse("1200"), taxRatePct = "15"),
            ),
            invoiceDiscountPct = "5",
            cashMinor = Money.parse("3000"),
        )
        eq(r.totals.total, Money.format(r.totals.total, "YER").let { Money.parse(it) }, "الإجمالي متسق:")
        eq(st.stock("co-2", "p1").qtyMilli, 17_000L, "مخزون p1:")
        eq(st.stock("co-2", "p2").qtyMilli, 8_000L, "مخزون p2:")
        eq(r.totals.cogs, Money.parse("3100"), "تكلفة المبيعات:")
        val tb = s2.trialBalance("co-2")
        eq(tb.balanced, true, "الميزان متوازن:")
        eq(s2.inventoryMatchesLedger("co-2", listOf("p1", "p2")), true, "المخزون يطابق الحساب:")
    }

    check("لا بيع بأكثر من المتاح في فاتورة البنود") {
        val st = InMemoryStore().apply {
            addCompany(CompanyRow("co-3", "منشأة ٣", "YER", setOf("YER")))
            seedChart("co-3")
            openPeriods("co-3", 2026)
        }
        val s3 = PostingService(st)
        s3.receiveOpeningStock("co-3", "2026-01-01", "OP-1", "p1", 1_000, Money.parse("500"))
        var code = ""
        try {
            s3.recordInvoiceDoc(
                "co-3", "2026-03-11", "INV-BAD",
                listOf(PostingService.DocInput("p1", "شاشة", 5_000, Money.parse("900"))),
            )
        } catch (e: AccountingException) { code = e.code }
        eq(code, Err.BAD_LINE, "رفض الكمية الزائدة:")
        eq(st.stock("co-3", "p1").qtyMilli, 1_000L, "المخزون لم يُمس:")
        eq(st.entries("co-3").none { it.sourceType == "INVOICE" }, true, "لا قيد فاتورة:")
    }

    check("شراء بندين مع مصاريف وصول يُرأسمَل على التكلفة") {
        val st = InMemoryStore().apply {
            addCompany(CompanyRow("co-4", "منشأة ٤", "YER", setOf("YER")))
            seedChart("co-4")
            openPeriods("co-4", 2026)
        }
        val s4 = PostingService(st)
        s4.recordPurchaseDoc(
            "co-4", "2026-03-12", "PUR-1",
            listOf(
                PostingService.DocInput("p1", "شاشة", 10_000, Money.parse("1000")),
                PostingService.DocInput("p2", "كيبورد", 10_000, Money.parse("1000")),
            ),
            landedCostMinor = Money.parse("500"),
        )
        val p1 = st.stock("co-4", "p1")
        val p2 = st.stock("co-4", "p2")
        eq(p1.qtyMilli, 10_000L)
        eq(p1.avgCostMinor + p2.avgCostMinor, Money.parse("2050"), "التكلفة مع الوصول موزّعة:")
        eq(s4.trialBalance("co-4").balanced, true, "الميزان متوازن:")
        eq(s4.inventoryMatchesLedger("co-4", listOf("p1", "p2")), true, "مطابقة المخزون:")
    }

    // ── ناقل البيانات (نسخ/استعادة) ────────────────────────────
    check("نص البيانات: كتابة ثم قراءة بلا فقد") {
        val t = listOf(
            DataTable("party", listOf("id", "name", "note"), listOf(
                listOf("p1", "مؤسسة النور", "سطر أول\nسطر ثانٍ"),
                listOf("p2", "شركة | وشركاه", ""),
            )),
            DataTable("product", listOf("id", "name"), listOf(listOf("i1", "أرز"))),
        )
        val text = DataPort.write(t)
        eq(text.startsWith(DataPort.MAGIC), true, "الترويسة:")
        val back = DataPort.read(text)
        eq(back.size, 2, "عدد الجلسات:")
        eq(back[0].columns, listOf("id", "name", "note"))
        eq(back[0].rows[0][2], "سطر أول\nسطر ثانٍ", "سطر جديد:")
        eq(back[0].rows[1][1], "شركة | وشركاه", "الرمز |:")
        eq(DataPort.summary(back), mapOf("party" to 2, "product" to 1))
        eq(DataPort.of(back, "product")?.rows?.first()?.get(1), "أرز")
    }

    check("نص بيانات بترويسة خاطئة يُرفض") {
        var code = ""
        try { DataPort.read("hello\n##party\na|b") } catch (e: AccountingException) { code = e.code }
        eq(code, "BAD_FORMAT")
    }

    check("نسخة كاملة: بيانات ⇒ تشفير ⇒ فك ⇒ قراءة") {
        val tables = listOf(
            DataTable("party", listOf("id", "name"), listOf(listOf("c1", "مؤسسة النور"))),
            DataTable("document", listOf("id", "totalMinor"), listOf(listOf("d1", "11902"))),
        )
        val payload = DataPort.write(tables)
        val meta = Backup.Meta("1.0.0", "2026-11-03", CO, "منشأة", DataPort.summary(tables))
        val blob = Backup.seal(payload, "pass-1234567", meta, iterations = 2_000)
        val (m, body) = Backup.open(blob, "pass-1234567")
        val restored = DataPort.read(body)
        eq(restored.size, 2)
        eq(m.counts["document"], 1)
        eq(DataPort.of(restored, "party")?.rows?.first()?.get(1), "مؤسسة النور")
    }

    // ── دفتر الطرف: كشف حساب وتحصيل ───────────────────────────
    fun pdoc(id: String, date: String, kind: String, total: Long, paid: Long = 0L) =
        PartyDoc(id, "DOC-$id", date, kind, total, paid)

    check("كشف حساب: رصيد تراكمي صحيح") {
        val docs = listOf(
            pdoc("1", "2026-01-05", PartyDoc.KIND_INVOICE, 100_000),
            pdoc("2", "2026-02-10", PartyDoc.KIND_RETURN, 20_000),
            pdoc("3", "2026-03-15", PartyDoc.KIND_INVOICE, 50_000),
        )
        val rows = PartyLedger.statement(docs)
        eq(rows.size, 3)
        eq(rows[0].balance, 100_000L, "بعد الفاتورة الأولى:")
        eq(rows[1].balance, 80_000L, "بعد المردود:")
        eq(rows[2].balance, 130_000L, "بعد الفاتورة الثانية:")
        eq(rows[1].label, "مردود")
        eq(rows[1].credit, 20_000L, "المردود دائن:")
        eq(PartyLedger.balance(docs), 130_000L, "الرصيد النهائي:")
    }

    check("تحصيل FIFO: الأقدم أولاً ثم الجزئي") {
        val docs = listOf(
            pdoc("1", "2026-01-05", PartyDoc.KIND_INVOICE, 40_000, paid = 10_000),
            pdoc("2", "2026-02-05", PartyDoc.KIND_INVOICE, 60_000),
        )
        val plan = PartyLedger.planCollection(70_000, docs)
        eq(plan.allocations.size, 2, "عدد التوزيعات:")
        eq(plan.allocations[0].amountMinor, 30_000L, "إغلاق الفاتورة الأولى:")
        eq(plan.allocations[1].amountMinor, 40_000L, "جزء من الثانية:")
        eq(plan.advanceMinor, 0L, "لا زيادة:")

        val applied = PartyLedger.applyCollection(docs, plan)
        eq(applied["1"], 40_000L, "المدفوع الجديد للأولى:")
        eq(applied["2"], 40_000L, "المدفوع الجديد للثانية:")
    }

    check("تحصيل أكبر من المستحق ⇒ دفعة مقدمة") {
        val docs = listOf(pdoc("1", "2026-01-05", PartyDoc.KIND_INVOICE, 50_000))
        val plan = PartyLedger.planCollection(80_000, docs)
        eq(plan.allocations.first().amountMinor, 50_000L)
        eq(plan.advanceMinor, 30_000L, "المقدمة:")
        eq(plan.totalMinor, 80_000L)
        var code = ""
        try { PartyLedger.planCollection(0, docs) } catch (e: AccountingException) { code = e.code }
        eq(code, "BAD_AMOUNT", "مبلغ صفري مرفوض:")
    }

    check("الرصيد لا يُدفع أكثر من قيمة الفاتورة") {
        val docs = listOf(pdoc("1", "2026-01-05", PartyDoc.KIND_INVOICE, 50_000, paid = 45_000))
        val plan = PartyLedger.planCollection(20_000, docs)
        eq(plan.allocations.first().amountMinor, 5_000L, "لا يتجاوز المتبقي:")
        eq(plan.advanceMinor, 15_000L, "الباقي مقدمة:")
        eq(PartyLedger.applyCollection(docs, plan)["1"], 50_000L, "لا يتجاوز الإجمالي:")
    }

    // ── المستودعات ─────────────────────────────────────────────
    check("رصيد مستودعين: كمية وقيمة ومتوسط مرجّح") {
        val a = Warehouses.receive(StockLevel("wh1", "p1", 0, 0), 10_000, Money.parse("100"))
        val b = Warehouses.receive(StockLevel("wh2", "p1", 0, 0), 10_000, Money.parse("200"))
        val levels = listOf(a, b)
        eq(Warehouses.totalQty(levels, "p1"), 20_000L, "الكمية الكلية:")
        eq(Warehouses.totalValue(levels), Money.parse("3000"), "القيمة الكلية:")
        eq(Warehouses.weightedAvgCost(levels, "p1"), Money.parse("150"), "المتوسط المرجّح:")
        eq(a.avgCostMinor, Money.parse("100"), "متوسط المستودع الأول:")
    }

    check("تحويل بين مستودعين يحفظ القيمة الكلية") {
        val src = Warehouses.receive(StockLevel("wh1", "p1", 0, 0), 10_000, Money.parse("100"))
        val dst = StockLevel("wh2", "p1", 0, 0)
        val before = src.valueMinor + dst.valueMinor
        val (newSrc, newDst) = Warehouses.transfer(src, dst, 4_000)
        eq(newSrc.qtyMilli, 6_000L, "المصدر بعد التحويل:")
        eq(newDst.qtyMilli, 4_000L, "الهدف بعد التحويل:")
        eq(newSrc.valueMinor + newDst.valueMinor, before, "القيمة محفوظة:")
        eq(newDst.avgCostMinor, Money.parse("100"), "تكلفة الهدف من المصدر:")
    }

    check("تحويل غير صالح يُرفض") {
        val src = Warehouses.receive(StockLevel("wh1", "p1", 0, 0), 1_000, Money.parse("100"))
        fun code(block: () -> Unit): String {
            var c = ""
            try { block() } catch (e: AccountingException) { c = e.code }
            return c
        }
        eq(code { Warehouses.transfer(src, StockLevel("wh2", "p1", 0, 0), 5_000) }, "BAD_TRANSFER", "كمية أكبر:")
        eq(code { Warehouses.transfer(src, StockLevel("wh1", "p1", 0, 0), 500) }, "BAD_TRANSFER", "نفس المستودع:")
        eq(code { Warehouses.transfer(src, StockLevel("wh2", "p2", 0, 0), 500) }, "BAD_TRANSFER", "صنف مختلف:")
        eq(code { Warehouses.transfer(src, StockLevel("wh2", "p1", 0, 0), 0) }, "BAD_TRANSFER", "كمية صفر:")
    }

    check("صرف من مستودع بالمتوسط المرجّح") {
        val level = Warehouses.receive(StockLevel("wh1", "p1", 0, 0), 10_000, Money.parse("100"))
        val (cost, after) = Warehouses.issue(level, 3_000)
        eq(cost, Money.parse("300"), "قيمة الصرف:")
        eq(after.qtyMilli, 7_000L)
        eq(after.valueMinor, Money.parse("700"), "المتبقي بالقيمة:")
    }

    // ── الدفعات والصلاحية ──────────────────────────────────────
    fun batchOf(id: String, qty: Long, cost: String, expiry: String?, received: String = "2026-01-01") =
        Batch(id, "p1", qty, Money.parse(cost), expiry, received)

    check("FEFO: الأقرب انتهاءً يُصرف أولاً") {
        val batches = listOf(
            batchOf("b1", 1_000, "100", "2027-12-31"),
            batchOf("b2", 1_000, "110", "2026-06-30"),
            batchOf("b3", 1_000, "120", "2026-03-31"),
        )
        eq(Batches.fefoOrder(batches).map { it.id }, listOf("b3", "b2", "b1"), "الترتيب:")
        val issues = Batches.issue(batches, 1_500, asOf = "2026-02-01")
        eq(issues.size, 2, "عدد الدفعات المصروفة:")
        eq(issues[0].batchId, "b3", "الأقرب انتهاءً أولاً:")
        eq(issues[0].costMinor, Money.parse("120"), "تكلفة الصرف:")
        eq(issues[1].batchId, "b2")
        eq(issues[1].qtyMilli, 500L, "الجزء من الثانية:")
    }

    check("لا يُصرف من دفعة منتهية") {
        val batches = listOf(
            batchOf("old", 1_000, "100", "2026-01-31"),
            batchOf("new", 1_000, "100", "2027-01-31"),
        )
        val issues = Batches.issue(batches, 1_000, asOf = "2026-02-15")
        eq(issues.single().batchId, "new", "تخطّي المنتهية:")
        var code = ""
        try { Batches.issue(batches, 5_000, asOf = "2026-02-15") } catch (e: AccountingException) { code = e.code }
        eq(code, "INSUFFICIENT", "كمية غير كافية:")
    }

    check("تنبيهات الصلاحية وقيمة الخطر") {
        val batches = listOf(
            batchOf("expired", 1_000, "100", "2026-01-01"),
            batchOf("soon", 2_000, "100", "2026-02-10"),
            batchOf("safe", 3_000, "100", "2028-01-01"),
            batchOf("noexp", 1_000, "100", null),
        )
        eq(Batches.expired(batches, "2026-02-01").map { it.id }, listOf("expired"))
        eq(Batches.expiringSoon(batches, "2026-02-01", 30).map { it.id }, listOf("soon"))
        eq(Batches.atRiskValue(batches, "2026-02-01", 30), Money.parse("300"), "قيمة الخطر:")
        eq(Batches.onHand(batches, "p1"), 7_000L)
    }

    check("تطبيق الصرف يخفض الدفعات") {
        val batches = listOf(batchOf("b1", 1_000, "100", "2026-03-01"), batchOf("b2", 1_000, "100", "2026-04-01"))
        val issues = Batches.issue(batches, 1_200, asOf = "2026-02-01")
        val after = Batches.apply(batches, issues)
        eq(after.first { it.id == "b1" }.qtyMilli, 0L, "الأولى استُهلكت:")
        eq(after.first { it.id == "b2" }.qtyMilli, 800L, "الثانية ناقصة:")
    }

    // ── الإقرار الضريبي ────────────────────────────────────────
    check("الإقرار الضريبي من القيود الفعلية") {
        val st = InMemoryStore().apply {
            addCompany(CompanyRow("co-6", "منشأة ضريبية", "YER", setOf("YER")))
            seedChart("co-6")
            openPeriods("co-6", 2026)
        }
        val s6 = PostingService(st)
        s6.receiveOpeningStock("co-6", "2026-01-01", "OP-1", "p1", 10_000, Money.parse("500"))
        s6.recordSale(
            "co-6", "2026-03-05", "INV-T1",
            listOf(PostingService.SaleInput("p1", 2_000, Money.parse("1000"), "15")),
            PaymentSplit(cashMinor = Money.parse("2300")),
        )
        s6.recordPurchase(
            "co-6", "2026-03-06", "PUR-T1",
            listOf(PostingService.PurchaseInput("p1", 10_000, Money.parse("600"))),
            taxRatePct = "15",
            cashPaidMinor = Money.parse("6900"),
        )

        val accounts = st.accounts("co-6").associateBy { it.id }
        val pairs = st.entries("co-6").map { e ->
            JournalRequest(
                "co-6", e.date, e.description, e.sourceType, e.sourceId, e.currency,
                e.lines.map { JournalLine(it.accountId, it.debit, it.credit, it.currency, it.fxRate, it.costCenterId, it.note) },
            ) to e.lines
        }
        val ret = TaxReturn.fromEntries(pairs, accounts, "2026-03-01", "2026-03-31")

        eq(ret.outputTax, Money.parse("300"), "ضريبة المخرجات:")
        eq(ret.inputTax, Money.parse("900"), "ضريبة المدخلات:")
        eq(ret.netPayable, -Money.parse("600"), "رصيد دائن يُرحَّل:")
        eq(ret.isCredit, true)
        eq(ret.boxes.size, 11, "عدد الخانات:")
        eq(ret.boxes.last().amountMinor, -Money.parse("600"), "الخانة الأخيرة:")
    }

    check("إقرار بضريبة مستحقة وطباعته") {
        val ret = TaxReturn.build(
            "2026-03-01", "2026-03-31",
            salesBase = Money.parse("10000"), outputTax = Money.parse("1500"),
            creditNoteBase = Money.parse("2000"), creditNoteTax = Money.parse("300"),
            purchasesBase = Money.parse("4000"), inputTax = Money.parse("600"),
            purchaseReturnBase = 0, purchaseReturnTax = Money.parse("150"),
        )
        eq(ret.netOutputTax, Money.parse("1200"), "صافي المخرجات:")
        eq(ret.netInputTax, Money.parse("450"), "صافي المدخلات:")
        eq(ret.netPayable, Money.parse("750"), "المستحق:")
        eq(ret.isCredit, false)
        eq(ret.boxes[10].label, "الصافي المستحق")

        val html = TaxReturn.html(ret, PrintCompany("مؤسسة الاختبار", taxNumber = "310000"))
        eq(html.contains("مؤسسة الاختبار"), true, "اسم المنشأة:")
        eq(html.contains("310000"), true, "الرقم الضريبي:")
        eq(html.contains(Tafqeet.plain(Money.parse("750"), "YER")), true, "التفقيط:")
        eq(html.contains("dir=\"rtl\""), true)
    }

    // ── تحليلات المبيعات ───────────────────────────────────────
    fun fact(date: String, product: String, party: String, qty: Long, net: String, cogs: String, doc: String = "d1") =
        SaleFact(date, doc, product, "صنف $product", party, "طرف $party", qty,
            Money.parse(net), 0L, Money.parse(cogs))

    check("تجميع المبيعات بحسب الصنف والعميل والشهر") {
        val facts = listOf(
            fact("2026-01-10", "p1", "c1", 2_000, "1000", "600", "d1"),
            fact("2026-01-20", "p1", "c2", 1_000, "500", "300", "d2"),
            fact("2026-02-05", "p2", "c1", 3_000, "1500", "900", "d3"),
        )
        val byPd = Analytics.byProduct(facts)
        eq(byPd.size, 2)
        eq(byPd.first { it.id == "p1" }.netMinor, Money.parse("1500"), "إيراد الصنف:")
        eq(byPd.first { it.id == "p1" }.qtyMilli, 3_000L, "كمية الصنف:")
        eq(byPd.first { it.id == "p1" }.profitMinor, Money.parse("600"), "ربح الصنف:")
        eq(byPd.first { it.id == "p1" }.marginPct, "40.0", "الهامش:")

        val byPt = Analytics.byParty(facts)
        eq(byPt.first().id, "c1", "أكبر عميل أولاً:")
        eq(byPt.first().netMinor, Money.parse("2500"))

        val byM = Analytics.byMonth(facts)
        eq(byM.map { it.id }, listOf("2026-01", "2026-02"))
        eq(byM[0].docs, 2, "مستندات الشهر:")
    }

    check("إجماليات ومتوسط الفاتورة والأصناف الخاسرة") {
        val facts = listOf(
            fact("2026-01-10", "p1", "c1", 1_000, "1000", "700", "d1"),
            fact("2026-01-11", "p2", "c1", 1_000, "500", "800", "d2"),
        )
        val t = Analytics.totals(facts)
        eq(t.netMinor, Money.parse("1500"))
        eq(t.cogsMinor, Money.parse("1500"))
        eq(t.profitMinor, 0L, "ربح صفري:")
        eq(t.docs, 2)
        eq(t.averageBasket, Money.parse("750"), "متوسط الفاتورة:")
        eq(Analytics.lossMaking(facts).map { it.id }, listOf("p2"), "الصنف الخاسر:")
        eq(Analytics.inRange(facts, "2026-01-11", "2026-01-31").size, 1)
    }

    check("تصدير تحليلات إلى CSV ولفرز الأصناف") {
        val facts = listOf(
            fact("2026-01-10", "p1", "c1", 1_000, "1000", "600", "d1"),
            fact("2026-01-11", "p2", "c1", 1_000, "200", "700", "d2"),
        )
        val csv = Analytics.toCsv(facts)
        eq(csv.startsWith("\uFEFF"), true, "BOM:")
        eq(csv.contains("الصافي"), true, "الترويسة:")
        eq(Analytics.share(facts).first().first, "صنف p1", "الأعلى مشاركة أولاً:")
        eq(Analytics.topProfit(facts, 1).first().id, "p1")
    }

    // ── وردية الكاشير والتعليق ────────────────────────────────
    check("وردية: النقد المتوقع والفرق عند الإقفال") {
        val shift = Shift(
            id = "sh1", openedAt = "2026-03-01", openingCashMinor = Money.parse("1000"),
            invoices = 4, cashSalesMinor = Money.parse("2000"), cardSalesMinor = Money.parse("800"),
            creditSalesMinor = Money.parse("500"), refundsMinor = Money.parse("200"),
        )
        val sum = Shifts.summary(shift)
        eq(sum.expectedCash, Money.parse("2800"), "النقد المتوقع:")
        eq(sum.salesMinor, Money.parse("3300"), "إجمالي المبيعات:")
        eq(sum.variance, null, "لم تُغلق بعد:")

        val closed = Shifts.close(shift, Money.parse("2750"), "2026-03-01")
        val v = Shifts.summary(closed).variance
        eq(v, -Money.parse("50"), "عجز ٥٠:")
        eq(Shifts.summary(closed).balanced, false)

        var code = ""
        try { Shifts.close(closed, Money.parse("2700"), "2026-03-02") } catch (e: AccountingException) { code = e.code }
        eq(code, "SHIFT_CLOSED", "لا إغلاق مزدوج:")
    }

    check("تعليق الفواتير واسترجاعها") {
        val lines = listOf(DocLine("p1", "صنف", 1_000, Money.parse("100"), "0", "0"))
        val inv = HeldInvoice("h1", "طاولة ٣", "2026-03-01T10:00", null, lines)
        var list = Holds.hold(emptyList(), inv)
        eq(list.size, 1)
        eq(inv.indicativeTotal, Money.parse("100"))
        val (resumed, rest) = Holds.resume(list, "h1")
        eq(resumed.label, "طاولة ٣", "الاسترجاع:")
        eq(rest.isEmpty(), true)
        var code = ""
        try { Holds.resume(emptyList(), "nope") } catch (e: AccountingException) { code = e.code }
        eq(code, "NOT_FOUND")
        list = emptyList()
    }

    // ── المطعم والصيدلية والمقاولات ───────────────────────────
    check("طاولة: مسار صحيح ورفض غير الصحيح") {
        eq(Restaurant.next(Restaurant.TableState.FREE, Restaurant.TableAction.SEAT), Restaurant.TableState.OCCUPIED)
        eq(Restaurant.next(Restaurant.TableState.OCCUPIED, Restaurant.TableAction.BILL), Restaurant.TableState.BILLED)
        eq(Restaurant.next(Restaurant.TableState.BILLED, Restaurant.TableAction.PAY), Restaurant.TableState.FREE)
        var code = ""
        try { Restaurant.next(Restaurant.TableState.FREE, Restaurant.TableAction.PAY) } catch (e: AccountingException) { code = e.code }
        eq(code, "BAD_TABLE_STATE", "دفع طاولة متاحة مرفوض:")
        eq(Restaurant.splitEvenly(Money.parse("100"), 3).sum(), Money.parse("100"), "التقسيم بلا فقد:")
        eq(Restaurant.splitEvenly(Money.parse("100"), 3).max(), Money.parse("33.34"), "أكبر نصيب:")
    }

    check("دواء بروشتة يُرفض بلا وصفة") {
        Pharmacy.requirePrescription(true, "RX-1")
        var code = ""
        try { Pharmacy.requirePrescription(true, null) } catch (e: AccountingException) { code = e.code }
        eq(code, "RX_REQUIRED")
        Pharmacy.requirePrescription(false, null)
    }

    check("مستخلص مقاولات: محتجز واسترداد دفعة") {
        val bill = Contracting.progressBill(
            contractMinor = Money.parse("100000"),
            previouslyExecutedMinor = Money.parse("40000"),
            executedMinor = Money.parse("20000"),
            retentionPct = "10",
            advanceBalanceMinor = Money.parse("5000"),
            advanceRecoveryPct = "25",
        )
        eq(bill.retentionMinor, Money.parse("2000"), "المحتجز:")
        eq(bill.advanceRecoveryMinor, Money.parse("5000"), "الاسترداد (محدود بالرصيد):")
        eq(bill.payableMinor, Money.parse("13000"), "المستحق:")
        eq(bill.remainingContractMinor, Money.parse("40000"), "المتبقي من العقد:")

        val r = JournalRequest(CO, "2026-03-15", "مستخلص", "PROGRESS", "PB-1", null,
            Contracting.billLines(book(), bill))
        val draft = JournalKernel.validate(r, ctx())
        eq(draft.lines.sumOf { it.debit }, draft.lines.sumOf { it.credit }, "قيد المستخلص متوازن:")

        var code = ""
        try {
            Contracting.progressBill(Money.parse("100000"), Money.parse("90000"), Money.parse("20000"))
        } catch (e: AccountingException) { code = e.code }
        eq(code, "OVER_CONTRACT", "تجاوز العقد مرفوض:")
    }

    // ── فاتورة بعملة أجنبية ───────────────────────────────────
    check("فاتورة بالدولار تُترجم للعملة الوظيفية وتتوازن") {
        val st = InMemoryStore().apply {
            addCompany(CompanyRow("co-7", "منشأة دولية", "YER", setOf("YER", "USD", "SAR")))
            seedChart("co-7")
            openPeriods("co-7", 2026)
        }
        val s7 = PostingService(st)
        s7.receiveOpeningStock("co-7", "2026-01-01", "OP-1", "p1", 10_000, Money.parse("5000"))

        val r = s7.recordInvoiceDoc(
            "co-7", "2026-03-10", "INV-USD-1",
            listOf(PostingService.DocInput("p1", "شاشة", 1_000, Money.parse("100", "USD"))),
            cashMinor = Money.parse("100", "USD"),
            currency = "USD",
            fxRate = "250",
        )
        eq(r.totals.total, Money.parse("100", "USD"), "إجمالي بالدولار:")
        eq(r.entry.currency, "USD", "عملة القيد:")
        // 100 دولار × 250 = 25,000 ريال ⇒ 2,500,000 وحدة صغرى
        eq(r.entry.totalBase, Money.parse("30000"), "القيمة بالعملة الوظيفية (٢٥٠٠٠ بيع + ٥٠٠٠ تكلفة):")
        eq(s7.trialBalance("co-7").balanced, true, "الميزان متوازن:")
        eq(s7.balanceOf("co-7", Chart.Acc.SALES), -Money.parse("25000"), "الإيراد بالعملة الوظيفية:")
    }

    check("قيد بعملة مسموحة وأخرى ممنوعة") {
        val st = InMemoryStore().apply {
            addCompany(CompanyRow("co-8", "منشأة", "YER", setOf("YER")))
            seedChart("co-8")
            openPeriods("co-8", 2026)
        }
        val s8 = PostingService(st)
        s8.receiveOpeningStock("co-8", "2026-01-01", "OP-1", "p1", 10_000, Money.parse("5000"))
        var code = ""
        try {
            s8.recordInvoiceDoc(
                "co-8", "2026-03-10", "INV-EUR-1",
                listOf(PostingService.DocInput("p1", "شاشة", 1_000, Money.parse("100", "USD"))),
                currency = "USD",
                fxRate = "250",
            )
        } catch (e: AccountingException) { code = e.code }
        eq(code, Err.UNKNOWN_CURRENCY, "عملة غير مسموحة:")
    }

    check("مردود مشتريات يخصم من المورد ويخرج من المخزون") {
        val st = InMemoryStore().apply {
            addCompany(CompanyRow("co-9", "منشأة مشتريات", "YER", setOf("YER")))
            seedChart("co-9")
            openPeriods("co-9", 2026)
        }
        val s9 = PostingService(st)
        s9.recordPurchaseDoc(
            "co-9", "2026-03-01", "PUR-1",
            listOf(PostingService.DocInput("p1", "أرز", 10_000, Money.parse("600"))),
        )
        eq(st.stock("co-9", "p1").qtyMilli, 10_000L, "بعد الشراء:")

        val r = s9.recordPurchaseReturnDoc(
            "co-9", "2026-03-05", "PR-1",
            listOf(PostingService.DocInput("p1", "أرز", 2_000, Money.parse("600"))),
        )
        eq(r.totals.total, Money.parse("1200"), "قيمة المردود:")
        eq(st.stock("co-9", "p1").qtyMilli, 8_000L, "المخزون بعد المردود:")
        eq(s9.balanceOf("co-9", Chart.Acc.AP), -Money.parse("4800"), "ذمة المورد بعد المردود (دائن):")
        eq(s9.trialBalance("co-9").balanced, true, "الميزان متوازن:")
        eq(s9.inventoryMatchesLedger("co-9", listOf("p1")), true, "المخزون يطابق الأستاذ:")
    }

    // ── الامتثال والفاتورة الإلكترونية ───────────────────────────
    check("رمز الفاتورة الإلكترونية: ترميز TLV ثم قراءته") {
        val f = Compliance.QrFields(
            sellerName = "مؤسسة مِداد",
            vatNumber = "310123456700003",
            timestamp = "2026-11-03T10:00:00Z",
            totalWithVatMinor = Money.parse("1150"),
            vatTotalMinor = Money.parse("150"),
        )
        val blob = Compliance.tlv(f)
        val back = Compliance.parseTlv(blob)
        eq(back[1], "مؤسسة مِداد", "اسم البائع:")
        eq(back[2], "310123456700003", "الرقم الضريبي:")
        eq(back[3], "2026-11-03T10:00:00Z", "التاريخ:")
        eq(back[4], Money.format(Money.parse("1150")), "الإجمالي:")
        eq(back[5], Money.format(Money.parse("150")), "الضريبة:")
        val b64 = Compliance.qrBase64(f)
        eq(Compliance.parseTlv(java.util.Base64.getDecoder().decode(b64))[2], "310123456700003", "بعد Base64:")
    }

    check("رمز QR يُولَّد بمقاس صحيح ومحدّد") {
        eq(QrCode.matrix("HELLO").size, 21, "نص قصير ⇒ الإصدار ١:")
        eq(QrCode.matrix("A".repeat(120)).size, 41, "نص ١٢٠ حرفاً ⇒ الإصدار ٦:")
        val m = QrCode.matrix("مِداد")
        // أنماط الزوايا الثلاثة
        eq(m[0][0] && m[0][6] && m[6][0] && m[3][3], true, "نمط الزاوية:")
        eq(m[0][7], false, "فاصل الزاوية:")
        eq(QrCode.matrix("مِداد").contentDeepEquals(m), true, "التوليد حتمي:")
        val svg = QrCode.svg("مِداد")
        eq(svg.contains("<rect") && svg.startsWith("<svg"), true, "SVG صالح:")
    }

    check("سلسلة البصمات تكشف أي تعديل") {
        val a = Compliance.seal("INV-2026-0001", "2026-03-01", "p1", Money.parse("1150"), Money.parse("150"), "YER")
        val b = Compliance.seal("INV-2026-0002", "2026-03-02", "p1", Money.parse("2300"), Money.parse("300"), "YER", a.hash)
        val ca = Compliance.canonical("INV-2026-0001", "2026-03-01", "p1", Money.parse("1150"), Money.parse("150"), "YER")
        val cb = Compliance.canonical("INV-2026-0002", "2026-03-02", "p1", Money.parse("2300"), Money.parse("300"), "YER")
        val ok = Compliance.verifyChain(listOf(a to ca, b to cb))
        eq(ok.ok, true, "سلسلة سليمة:")
        eq(ok.checked, 2, "عدد المستندات المفحوصة:")

        val tampered = Compliance.canonical("INV-2026-0002", "2026-03-02", "p1", Money.parse("2301"), Money.parse("300"), "YER")
        eq(Compliance.verifyChain(listOf(a to ca, b to tampered)).ok, false, "تعديل مبلغ مكشوف:")
    }

    check("الرقم الضريبي والترقيم") {
        eq(Compliance.checkVatNumber("310123456700003").valid, true, "رقم صحيح:")
        eq(Compliance.checkVatNumber("31012345670000").valid, false, "أقصر من ١٥:")
        eq(Compliance.checkVatNumber("210123456700003").valid, false, "لا يبدأ بـ٣:")
        eq(Compliance.nextNumber("INV-2026-0007", "INV", "2026"), "INV-2026-0008", "الرقم التالي:")
        val gaps = Compliance.numberGaps(listOf("INV-2026-0001", "INV-2026-0002", "INV-2026-0005"))
        eq(gaps.size, 1, "فجوة واحدة:")
        eq(gaps.first().expected to gaps.first().found, 3 to 5, "موضع الفجوة:")
    }

    // ── الفواتير الدورية والتسوية البنكية ────────────────────────
    check("فاتورة دورية شهرية: قصّ اليوم ٣١ لآخر الشهر") {
        eq(Recurring.nextDate("2026-01-31", Recurring.Cycle.MONTHLY, 31), "2026-02-28", "فبراير:")
        eq(Recurring.nextDate("2026-03-31", Recurring.Cycle.MONTHLY, 31), "2026-04-30", "أبريل:")

        val sch = Recurring.Schedule(
            id = "r1", partyId = "p1", title = "إيجار", cycle = Recurring.Cycle.MONTHLY,
            startDate = "2026-01-01", amountMinor = Money.parse("5000"), taxRatePct = "15",
        )
        val dates = Recurring.dueDates(sch, "2026-03-31")
        eq(dates.size, 3, "ثلاثة استحقاقات:")
        val plan = Recurring.runPlan(sch, "2026-03-31")
        eq(plan.sumOf { it.amountMinor }, Money.parse("15000"), "إجمالي بدون ضريبة:")
        eq(plan.sumOf { it.taxMinor }, Money.parse("2250"), "إجمالي الضريبة:")

        val afterRun = sch.copy(lastRunDate = "2026-02-01")
        eq(Recurring.dueDates(afterRun, "2026-03-31"), listOf("2026-03-01"), "بعد آخر تشغيل:")
    }

    check("التسوية البنكية: مطابقة تلقائية وكشف الفروق") {
        val statement = listOf(
            Banking.StatementLine("s1", "2026-03-02", "إيداع عميل", Money.parse("1000")),
            Banking.StatementLine("s2", "2026-03-05", "شيك مورد", -Money.parse("400")),
            Banking.StatementLine("s3", "2026-03-09", "رسوم بنك", -Money.parse("25")),
        )
        val book = listOf(
            Banking.BookLine("e1", "2026-03-01", "تحصيل", Money.parse("1000")),
            Banking.BookLine("e2", "2026-03-04", "دفعة مورد", -Money.parse("400")),
            Banking.BookLine("e3", "2026-03-20", "إيداع لم يصل", Money.parse("700")),
        )
        val r = Banking.autoMatch(statement, book)
        eq(r.matches.size, 2, "مطابقتان:")
        eq(r.unmatchedStatement.size, 1, "بند كشف بلا قيد:")
        eq(r.unmatchedBook.size, 1, "قيد بلا كشف:")
        eq(r.balanced, false, "غير مطابق:")
        eq(r.differenceMinor, -Money.parse("725"), "فرق الإجمالي (كشف − قيود):")
        eq(Banking.adjustmentHints(r).isNotEmpty(), true, "تلميحات التسوية:")
    }

    check("المطابقة اليدوية ترفض فرقاً غير مبرَّر") {
        val s = Banking.StatementLine("s1", "2026-03-02", "إيداع", Money.parse("1000"))
        val b = Banking.BookLine("e1", "2026-03-02", "تحصيل", Money.parse("980"))
        var rejected = false
        try { Banking.manualMatch(s, b) } catch (e: AccountingException) { rejected = true }
        eq(rejected, true, "يُرفض الفرق:")
        eq(Banking.manualMatch(s, b, allowDifference = true).amountMinor, Money.parse("980"), "يُقبل مع تسجيل الفرق:")
    }

    // ── إعادة الطلب والأسعار والباقات ────────────────────────────
    check("تنبيه إعادة الطلب والكمية المقترحة") {
        val products = listOf(
            Replenish.Product("p1", "أرز", 2_000, 5_000, 20_000, Money.parse("600")),
            Replenish.Product("p2", "سكر", 30_000, 5_000, 20_000, Money.parse("300")),
        )
        val s = Replenish.scan(products)
        eq(s.size, 1, "صنف واحد تحت الحد:")
        eq(s.first().productId, "p1", "الصنف:")
        eq(s.first().suggestQtyMilli, 18_000L, "الكمية المقترحة:")
        eq(s.first().estCostMinor, Money.parse("10800"), "التكلفة المقدرة:")
    }

    check("قوائم الأسعار وحدود الخصم") {
        val entries = listOf(
            PriceBook.PriceEntry("p1", PriceBook.Tier.RETAIL, Money.parse("100")),
            PriceBook.PriceEntry("p1", PriceBook.Tier.WHOLESALE, Money.parse("90"), 10_000),
            PriceBook.PriceEntry("p1", PriceBook.Tier.WHOLESALE, Money.parse("85"), 50_000),
        )
        eq(PriceBook.priceFor(entries, "p1", PriceBook.Tier.WHOLESALE, 20_000, Money.parse("100")), Money.parse("90"), "سعر الجملة:")
        eq(PriceBook.priceFor(entries, "p1", PriceBook.Tier.WHOLESALE, 60_000, Money.parse("100")), Money.parse("85"), "شريحة أكبر:")
        eq(PriceBook.priceFor(entries, "p1", PriceBook.Tier.SPECIAL, 1_000, Money.parse("100")), Money.parse("100"), "بلا سعر ⇒ الأساسي:")
        eq(PriceBook.marginPercent(Money.parse("100"), Money.parse("70")), 30.0, "هامش على البيع:")
        eq(PriceBook.costPlus(Money.parse("70"), "20"), Money.parse("84"), "تكلفة + ٢٠٪:")
        eq(PriceBook.discountAllowed(Role.CASHIER, "4"), true, "خصم ٤٪ للكاشير:")
        eq(PriceBook.discountAllowed(Role.CASHIER, "9"), false, "خصم ٩٪ مرفوض للكاشير:")
        eq(PriceBook.discountAllowed(Role.MANAGER, "20"), true, "٢٠٪ للمدير:")
    }

    check("الباقات: تفكيك المكوّنات وكشف النقص") {
        val bundles = mapOf(
            "kit1" to Bundles.Bundle("kit1", "علبة هدايا", listOf(
                Bundles.Component("p1", 2_000), Bundles.Component("p2", 1_000),
            )),
        )
        val stocks = mapOf(
            "p1" to Stock(10_000, Money.parse("7000"), Money.parse("700")),
            "p2" to Stock(1_000, Money.parse("300"), Money.parse("300")),
        )
        val plan = Bundles.plan(bundles, listOf(Bundles.Component("kit1", 3_000)), { stocks[it] ?: Stock() })
        eq(plan.lines.size, 2, "مكوّنان:")
        eq(plan.lines.first { it.component.productId == "p1" }.component.qtyMilli, 6_000L, "كمية المكوّن الأول:")
        eq(plan.totalCostMinor, Money.parse("4200") + Money.parse("900"), "إجمالي التكلفة:")
        eq(plan.shortages.isEmpty(), false, "يوجد نقص:")
        eq(Bundles.plan(bundles, listOf(Bundles.Component("kit1", 2_000)), { stocks[it] ?: Stock() }).shortages.size, 1, "نقص واحد:")
    }

    check("فحص صحة البيانات: صنف بلا حركة وقيد بلا مستند") {
        val problems = Management.health(
            entries = listOf(
                PostedEntry("e1", "co-1", "J-1", "2026-03-01", "بيع", "INVOICE", "docX", "YER",
                    Money.parse("100"), "POSTED", emptyList()),
                PostedEntry("e2", "co-1", "J-2", "2026-03-02", "شراء", "PURCHASE", "doc1", "YER",
                    Money.parse("100"), "POSTED", emptyList()),
            ),
            documentIds = setOf("doc1"),
            productIds = setOf("p1", "p2"),
            movedProductIds = setOf("p1"),
        )
        eq(problems.size, 2, "مشكلتان:")
        eq(problems.any { it.contains("docX") }, true, "قيد بلا مستند:")
        eq(problems.any { it.contains("p2") }, true, "صنف بلا حركة:")
    }

    // ── الموازنات التقديرية ──────────────────────────────────────
    check("موازنة: دمج وتطبيع ومقارنة بالفعلي") {
        val lines = listOf(
            Budgets.Line("5101", "إيجار", "2026", Money.parse("1200")),
            Budgets.Line("5101", "إيجار", "2026", Money.parse("800")),
            Budgets.Line("5102", "كهرباء", "2026", Money.parse("500")),
            Budgets.Line("5103", "صفر", "2026", 0L),
        )
        val norm = Budgets.normalize(lines)
        eq(norm.size, 2, "الأسطر بعد الدمج:")
        eq(norm.first { it.accountCode == "5101" }.amountMinor, Money.parse("2000"), "مجموع الحساب المكرر:")

        val rows = Budgets.compare(lines, "2026") { code ->
            if (code == "5101") Money.parse("2500") else Money.parse("100")
        }
        eq(rows.size, 2, "صفوف المقارنة:")
        val rent = rows.first { it.accountCode == "5101" }
        eq(rent.actualMinor, Money.parse("2500"), "الفعلي:")
        eq(rent.varianceMinor, Money.parse("500"), "التجاوز:")
        eq(rent.over, true, "متجاوز:")
        eq(rent.usedPctText, "125.0", "نسبة الاستهلاك:")
        val power = rows.first { it.accountCode == "5102" }
        eq(power.over, false, "داخل الموازنة:")
    }

    check("موازنة: الإجماليات والتنبيهات") {
        val rows = listOf(
            Budgets.Row("5101", "إيجار", Money.parse("1000"), Money.parse("1100"), Money.parse("100"), 1100),
            Budgets.Row("5102", "كهرباء", Money.parse("1000"), Money.parse("950"), -Money.parse("50"), 950),
            Budgets.Row("5103", "صيانة", Money.parse("1000"), Money.parse("100"), -Money.parse("900"), 100),
        )
        val t = Budgets.totals(rows)
        eq(t.budgetMinor, Money.parse("3000"), "إجمالي الموازنة:")
        eq(t.actualMinor, Money.parse("2150"), "إجمالي الفعلي:")
        eq(t.varianceMinor, -Money.parse("850"), "الفرق:")

        val alerts = Budgets.alerts(rows)
        eq(alerts.size, 2, "تنبيهان (تجاوز + اقتراب):")
        eq(alerts.first().contains("تجاوز"), true, "أول تنبيه تجاوز:")
    }

    check("موازنة: توزيع سنوي على ١٢ شهراً بلا فقدان") {
        val months = Budgets.monthly(Money.parse("1000"))
        eq(months.size, 12, "عدد الأشهر:")
        eq(months.sum(), Money.parse("1000"), "المجموع كما هو:")
        val uneven = Budgets.monthly(1000L)
        eq(uneven.sum(), 1000L, "بلا فقدان وحدة صغرى:")
        eq(uneven.count { it == 84L }, 4, "الأشهر التي تحمل الباقي:")
    }

    check("موازنة: خط الأساس الزمني يميّز التوقيت من التجاوز") {
        val halfYear = Budgets.expectedUsagePct(7, 1)      // بداية يوليو ≈ ٥٠٪
        eq(halfYear in 480L..520L, true, "منتصف السنة:")
        val row = Budgets.Row("5101", "إيجار", Money.parse("1000"), Money.parse("600"), -Money.parse("400"), 600)
        eq(Budgets.isTimingOnly(row, 10, 1), true, "استهلاك ٦٠٪ في أكتوبر = طبيعي:")
        val hot = Budgets.Row("5101", "إيجار", Money.parse("1000"), Money.parse("900"), -Money.parse("100"), 900)
        eq(Budgets.isTimingOnly(hot, 3, 1), false, "٩٠٪ في مارس = تجاوز حقيقي:")
    }

    // ── الأرقام التسلسلية ────────────────────────────────────────
    check("تسلسلي: توليد نطاق ورفض الأرقام غير الصالحة") {
        val nums = Serials.generate("SN-", 1001, 3)
        eq(nums, listOf("SN-1001", "SN-1002", "SN-1003"), "النطاق:")
        eq(Serials.expand("SN-10", "SN-12"), listOf("SN-10", "SN-11", "SN-12"), "من/إلى:")
        eq(Serials.validate("SN-1001"), null, "رقم صالح:")
        eq(Serials.validate("SN 1001") != null, true, "مسافة مرفوضة:")
        eq(Serials.validate("A") != null, true, "قصير مرفوض:")
        eq(Serials.duplicates(listOf("A-1", "A-2", "A-1")), listOf("A-1"), "المكرر:")
    }

    check("تسلسلي: التخصيص FIFO ورفض النقص") {
        val items = listOf(
            Serials.Item("1", "p1", "SN-3", Serials.Status.IN_STOCK, date = "2026-03-03"),
            Serials.Item("2", "p1", "SN-1", Serials.Status.IN_STOCK, date = "2026-03-01"),
            Serials.Item("3", "p1", "SN-2", Serials.Status.SOLD, date = "2026-03-02"),
            Serials.Item("4", "p2", "SN-9", Serials.Status.IN_STOCK, date = "2026-03-01"),
        )
        val alloc = Serials.allocateForSale(items, "p1", 2)
        eq(alloc.map { it.number }, listOf("SN-1", "SN-3"), "الأقدم أولاً ومن المخزون فقط:")
        var rejected = false
        try { Serials.allocateForSale(items, "p1", 5) } catch (e: AccountingException) { rejected = true }
        eq(rejected, true, "النقص يُرفض:")
    }

    check("تسلسلي: آلة الحالات والملخّص") {
        eq(Serials.next(Serials.Status.IN_STOCK, Serials.Action.SELL), Serials.Status.SOLD, "بيع:")
        eq(Serials.next(Serials.Status.SOLD, Serials.Action.RETURN), Serials.Status.RETURNED, "مرتجع:")
        eq(Serials.next(Serials.Status.RETURNED, Serials.Action.RESTOCK), Serials.Status.IN_STOCK, "إعادة للمخزون:")
        var rejected = false
        try { Serials.next(Serials.Status.SOLD, Serials.Action.VOID) } catch (e: AccountingException) { rejected = true }
        eq(rejected, true, "إلغاء المُباع مرفوض:")

        val items = listOf(
            Serials.Item("1", "p1", "SN-1", Serials.Status.IN_STOCK),
            Serials.Item("2", "p1", "SN-2", Serials.Status.IN_STOCK),
            Serials.Item("3", "p1", "SN-3", Serials.Status.SOLD),
            Serials.Item("4", "p1", "SN-4", Serials.Status.RETURNED),
        )
        val sum = Serials.summary(items)
        eq(sum.inStock, 2, "في المخزون:")
        eq(sum.sold, 1, "مُباع:")
        eq(sum.total, 4, "الإجمالي:")
        eq(Serials.reconciliationGap(items, "p1", 3_000L), -1_000L, "فرق التسوية (رقم ناقص):")
    }

    // ── الخلاصة ───────────────────────────────────────────────
    println("\n" + "═".repeat(64))
    println("  نجح: $passed · فشل: $failed")
    if (failed > 0) {
        println("\n  الإخفاقات:")
        failures.forEach { println("   • $it") }
        println("═".repeat(64))
        kotlin.system.exitProcess(1)
    }
    println("  ✅ كل الاختبارات ناجحة")
    println("═".repeat(64))
}
