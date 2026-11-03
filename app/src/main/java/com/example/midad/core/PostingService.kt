package com.example.midad.core

/**
 * خدمة الترحيل — الجسر بين الشاشات والنواة.
 * كل عملية تجارية (بيع، شراء، قبض، صرف، جرد، إهلاك، رواتب) تدخل من هنا:
 *   ١) تُحمَّل السياق من المخزن  ٢) يتحقق الكيرنل  ٣) يُرقَّم ويُكتب  ٤) يُسجَّل في التدقيق
 * لا تبني أي شاشة قيداً بنفسها.
 */
class PostingService(
    private val store: ErpStore,
    private val ids: IdGenerator = UuidIdGenerator,
) {

    // ── القيد العام ───────────────────────────────────────────────
    fun post(request: JournalRequest): PostedEntry {
        val company = store.company(request.companyId)
            ?: throw AccountingException(Err.NO_COMPANY, "المنشأة غير موجودة أو غير نشطة")

        val (year, month) = request.date.split("-").let { it[0].toInt() to it[1].toInt() }

        val ctx = JournalContext(
            companyId = company.id,
            functionalCurrency = company.functionalCurrency,
            allowedCurrencies = company.allowedCurrencies,
            lockDate = company.lockDate,
            periodStatus = store.periodStatus(company.id, year, month),
            periodLabel = "%d-%02d".format(year, month),
            accounts = store.accounts(company.id).associateBy { it.id },
            duplicateEntryNumber = store.postedEntryNumber(company.id, request.sourceType, request.sourceId),
        )

        val draft = JournalKernel.validate(request, ctx)
        val number = store.nextNumber(company.id, docTypeOf(request.sourceType), request.date.take(4))

        val entry = PostedEntry(
            id = ids.next(),
            companyId = company.id,
            number = number,
            date = request.date,
            description = request.description,
            sourceType = request.sourceType,
            sourceId = request.sourceId,
            currency = draft.currency,
            totalBase = draft.totalBase,
            status = "POSTED",
            lines = draft.lines,
            reversalOf = request.reversalOf,
        )
        store.insertEntry(entry)
        store.audit(AuditRow(company.id, request.sourceType, request.sourceId, "POST", "قيد $number بمبلغ ${Money.format(entry.totalBase)}"))
        return entry
    }

    // ── العكس ────────────────────────────────────────────────────
    fun reverse(companyId: String, entryId: String, date: String? = null, reason: String = ""): PostedEntry {
        val original = store.entry(companyId, entryId)
            ?: throw AccountingException(Err.BAD_ACCOUNT, "القيد غير موجود")
        if (original.status == "REVERSED") {
            throw AccountingException("ALREADY_REVERSED", "القيد ${original.number} معكوس مسبقاً")
        }
        if (original.reversalOf != null) {
            throw AccountingException("REVERSE_OF_REVERSE", "لا يمكن عكس قيد عكسي")
        }

        val req = JournalRequest(
            companyId = companyId,
            date = date ?: original.date,
            description = "عكس القيد ${original.number}" + if (reason.isBlank()) "" else " — $reason",
            sourceType = original.sourceType + ":REVERSAL",
            sourceId = original.sourceId + ":R" + original.number,
            currency = original.currency,
            reversalOf = original.id,
            lines = original.lines.map {
                JournalLine(it.accountId, debit = it.credit, credit = it.debit,
                    currency = it.currency, fxRate = it.fxRate, costCenterId = it.costCenterId,
                    note = "عكس: ${it.note}")
            },
        )
        val rev = post(req)
        store.markReversed(companyId, original.id, rev.id)
        store.audit(AuditRow(companyId, "ENTRY", original.id, "REVERSE", "عكس ${original.number} بالقيد ${rev.number}"))
        return rev
    }

    // ── دورة المبيعات ────────────────────────────────────────────
    data class SaleInput(
        val productId: String,
        val qtyMilli: Long,
        val unitPriceMinor: Long,
        val taxRatePct: String = "0",
        val isService: Boolean = false,
    )

    data class SaleResult(val entry: PostedEntry, val totals: SaleTotals, val cogs: Long)

    /**
     * فاتورة مبيعات: تكلفة حقيقية من مخزون كل صنف، ثم ترحيل واحد متوازن، ثم صرف المخزون.
     * هذا ما يُصلح أكبر خطأ في التطبيق الحالي: لا COGS ولا حركة مخزون محاسبية.
     */
    fun recordSale(
        companyId: String,
        date: String,
        invoiceId: String,
        inputs: List<SaleInput>,
        payment: PaymentSplit,
    ): SaleResult {
        val book = book(companyId)

        // ١) تكلفة الوحدة من محرك التكلفة (لا price × 0.72)
        val priced = inputs.map {
            val stock = store.stock(companyId, it.productId)
            SaleLine(it.qtyMilli, it.unitPriceMinor, it.taxRatePct,
                if (it.isService) 0L else stock.avgCostMinor)
        }

        // ٢) منع البيع بأكثر من المتاح (قبل أي كتابة)
        inputs.filterNot { it.isService }.forEach {
            val stock = store.stock(companyId, it.productId)
            if (it.qtyMilli > stock.qtyMilli) {
                throw AccountingException(
                    Err.BAD_LINE,
                    "المتاح ${stock.qty} فقط من الصنف — لا يمكن بيع ${it.qtyMilli / 1000.0}",
                )
            }
        }

        val (lines, totals) = Rules.sale(book, priced, payment)
        val entry = post(
            JournalRequest(companyId, date, "فاتورة مبيعات $invoiceId", "INVOICE", invoiceId,
                currency = null, lines = lines),
        )

        // ٣) صرف المخزون وتسجيل الحركة — بعد نجاح القيد فقط
        inputs.filterNot { it.isService }.forEach {
            val stock = store.stock(companyId, it.productId)
            val (cost, after) = Costing.issue(stock, it.qtyMilli)
            store.saveStock(companyId, it.productId, after)
            store.insertMovementSafe(companyId, it.productId, "OUT", it.qtyMilli, cost, "INVOICE", invoiceId, date)
        }
        return SaleResult(entry, totals, totals.cogs)
    }

    /** مردود مبيعات: يعكس القيد ويعيد الكمية للمخزون بمتوسطها الحالي */
    fun recordSalesReturn(
        companyId: String,
        date: String,
        returnId: String,
        inputs: List<SaleInput>,
        refundCash: Long = 0,
        refundCredit: Long = 0,
    ): Pair<PostedEntry, SaleTotals> {
        val book = book(companyId)
        val priced = inputs.map {
            val stock = store.stock(companyId, it.productId)
            SaleLine(it.qtyMilli, it.unitPriceMinor, it.taxRatePct, if (it.isService) 0L else stock.avgCostMinor)
        }
        val (lines, totals) = Rules.salesReturn(book, priced, refundCash, refundCredit)
        val entry = post(
            JournalRequest(companyId, date, "مردود مبيعات $returnId", "RETURN", returnId, null, lines),
        )
        inputs.filterNot { it.isService }.forEach {
            val stock = store.stock(companyId, it.productId)
            store.saveStock(companyId, it.productId, Costing.receive(stock, it.qtyMilli, stock.avgCostMinor))
            store.insertMovementSafe(companyId, it.productId, "RETURN", it.qtyMilli, stock.avgCostMinor, "RETURN", returnId, date)
        }
        return entry to totals
    }

    // ── دورة المشتريات ───────────────────────────────────────────
    data class PurchaseInput(
        val productId: String,
        val qtyMilli: Long,
        val unitPriceMinor: Long,
        val landedShareMinor: Long = 0L,
    )

    fun recordPurchase(
        companyId: String,
        date: String,
        purchaseId: String,
        inputs: List<PurchaseInput>,
        taxRatePct: String = "0",
        landedCostMinor: Long = 0L,
        cashPaidMinor: Long = 0L,
    ): Pair<PostedEntry, Rules.PurchaseTotals> {
        val book = book(companyId)
        val (lines, totals) = Rules.purchase(
            book,
            inputs.map { Rules.PurchaseLine(it.qtyMilli, it.unitPriceMinor) },
            taxRatePct, landedCostMinor, cashPaidMinor,
        )
        val entry = post(
            JournalRequest(companyId, date, "فاتورة مشتريات $purchaseId", "PURCHASE", purchaseId, null, lines),
        )
        // استلام المخزون بتكلفة الوصول موزّعة على البنود
        inputs.forEach {
            val stock = store.stock(companyId, it.productId)
            // إجمالي تكلفة السطر ثم تُحوَّل إلى تكلفة الوحدة الكاملة
            val lineCost = Money.mulDiv(it.unitPriceMinor, it.qtyMilli, 1000L) + it.landedShareMinor
            val unitCost = Money.mulDiv(lineCost, 1000L, it.qtyMilli)
            store.saveStock(companyId, it.productId, Costing.receive(stock, it.qtyMilli, unitCost))
            store.insertMovementSafe(companyId, it.productId, "IN", it.qtyMilli, unitCost, "PURCHASE", purchaseId, date)
        }
        return entry to totals
    }

    // ── المستندات متعددة البنود ──────────────────────────────────
    data class DocInput(
        val productId: String,
        val name: String = "",
        val qtyMilli: Long,
        val unitPriceMinor: Long,
        val discountPct: String = "0",
        val taxRatePct: String = "0",
        val isService: Boolean = false,
    )

    data class DocResult(val entry: PostedEntry, val totals: DocTotals)

    /** فاتورة مبيعات ببنود وخصومات وضريبة لكل بند — تكلفتها من المخزون الحقيقي */
    fun recordInvoiceDoc(
        companyId: String,
        date: String,
        docId: String,
        inputs: List<DocInput>,
        invoiceDiscountPct: String = "0",
        roundingStep: Long = 0L,
        cashMinor: Long = 0L,
        cardMinor: Long = 0L,
        currency: String? = null,
        fxRate: String = "1",
    ): DocResult {
        val errors = DocEngine.validate(inputs.map {
            DocLine(it.productId, it.name.ifBlank { it.productId }, it.qtyMilli, it.unitPriceMinor,
                it.discountPct, it.taxRatePct, 0L)
        })
        if (errors.isNotEmpty()) throw AccountingException(Err.BAD_LINE, errors.joinToString(" · "))

        val lines = inputs.map {
            val stock = store.stock(companyId, it.productId)
            DocLine(it.productId, it.name, it.qtyMilli, it.unitPriceMinor, it.discountPct, it.taxRatePct,
                if (it.isService) 0L else stock.avgCostMinor)
        }

        // لا بيع بأكثر من المتاح — قبل أي كتابة
        inputs.filterNot { it.isService }.forEach {
            val stock = store.stock(companyId, it.productId)
            if (it.qtyMilli > stock.qtyMilli)
                throw AccountingException(Err.BAD_LINE, "المتاح ${stock.qty} فقط من «${it.name}»")
        }

        val totals = DocEngine.totals(lines, invoiceDiscountPct, roundingStep)
        val (jl, _) = DocPosting.sales(book(companyId), totals, Settlement(cashMinor, cardMinor), currency, fxRate)
        val entry = post(
            JournalRequest(companyId, date, "فاتورة مبيعات $docId", "INVOICE", docId, currency, jl),
        )
        inputs.filterNot { it.isService }.forEach {
            val stock = store.stock(companyId, it.productId)
            val (cost, after) = Costing.issue(stock, it.qtyMilli)
            store.saveStock(companyId, it.productId, after)
            store.insertMovementSafe(companyId, it.productId, "OUT", it.qtyMilli, cost, "INVOICE", docId, date)
        }
        return DocResult(entry, totals)
    }

    /** مردود مبيعات ببنود متعددة */
    fun recordReturnDoc(
        companyId: String,
        date: String,
        docId: String,
        inputs: List<DocInput>,
        invoiceDiscountPct: String = "0",
        refundCashMinor: Long = 0L,
        refundCreditMinor: Long = 0L,
        currency: String? = null,
        fxRate: String = "1",
    ): DocResult {
        val lines = inputs.map { it ->
            DocLine(it.productId, it.name, it.qtyMilli, it.unitPriceMinor, it.discountPct, it.taxRatePct,
                store.stock(companyId, it.productId).avgCostMinor)
        }
        val totals = DocEngine.totals(lines, invoiceDiscountPct)
        val (jl, _) = DocPosting.salesReturn(book(companyId), totals, refundCashMinor, refundCreditMinor, currency, fxRate)
        val entry = post(JournalRequest(companyId, date, "مردود مبيعات $docId", "RETURN", docId, currency, jl))
        inputs.filterNot { it.isService }.forEach {
            val stock = store.stock(companyId, it.productId)
            val cost = stock.avgCostMinor
            store.saveStock(companyId, it.productId, Costing.receive(stock, it.qtyMilli, cost))
            store.insertMovementSafe(companyId, it.productId, "RETURN", it.qtyMilli, cost, "RETURN", docId, date)
        }
        return DocResult(entry, totals)
    }

    /** فاتورة مشتريات ببنود ومصاريف وصول موزّعة بالتكلفة على البنود */
    fun recordPurchaseDoc(
        companyId: String,
        date: String,
        docId: String,
        inputs: List<DocInput>,
        invoiceDiscountPct: String = "0",
        landedCostMinor: Long = 0L,
        cashPaidMinor: Long = 0L,
        currency: String? = null,
        fxRate: String = "1",
    ): DocResult {
        val lines = inputs.map {
            DocLine(it.productId, it.name, it.qtyMilli, it.unitPriceMinor, it.discountPct, it.taxRatePct, 0L)
        }
        val totals = DocEngine.totals(lines, invoiceDiscountPct, 0L, landedCostMinor)
        val (jl, _) = DocPosting.purchase(book(companyId), totals, cashPaidMinor, landedCostMinor, currency, fxRate)
        val entry = post(JournalRequest(companyId, date, "فاتورة مشتريات $docId", "PURCHASE", docId, currency, jl))

        // مصاريف الوصول تُوزّع على البنود بنسبة قيمتها
        val shares = DocEngine.distribute(landedCostMinor, totals.lines.map { it.net })
        inputs.forEachIndexed { i, it ->
            if (it.isService) return@forEachIndexed
            val stock = store.stock(companyId, it.productId)
            val lineCost = totals.lines[i].net + shares[i]
            val unitCost = Money.mulDiv(lineCost, 1000L, it.qtyMilli)
            store.saveStock(companyId, it.productId, Costing.receive(stock, it.qtyMilli, unitCost))
            store.insertMovementSafe(companyId, it.productId, "IN", it.qtyMilli, unitCost, "PURCHASE", docId, date)
        }
        return DocResult(entry, totals)
    }

    /** مردود مشتريات: يخرج من المخزون ويخصم من ذمة المورد (أو يسترد نقداً) */
    fun recordPurchaseReturnDoc(
        companyId: String,
        date: String,
        docId: String,
        inputs: List<DocInput>,
        invoiceDiscountPct: String = "0",
        refundCashMinor: Long = 0L,
        currency: String? = null,
        fxRate: String = "1",
    ): DocResult {
        val lines = inputs.map {
            DocLine(it.productId, it.name, it.qtyMilli, it.unitPriceMinor, it.discountPct, it.taxRatePct,
                store.stock(companyId, it.productId).avgCostMinor)
        }
        val totals = DocEngine.totals(lines, invoiceDiscountPct)
        val (jl, _) = DocPosting.purchaseReturn(book(companyId), totals, refundCashMinor, currency, fxRate)
        val entry = post(
            JournalRequest(companyId, date, "مردود مشتريات $docId", "PURCHASE_RETURN", docId, currency, jl),
        )
        inputs.forEach {
            val stock = store.stock(companyId, it.productId)
            if (it.qtyMilli > stock.qtyMilli)
                throw AccountingException(Err.BAD_LINE, "المتاح ${stock.qty} فقط من «${it.name}»")
            val (cost, after) = Costing.issue(stock, it.qtyMilli)
            store.saveStock(companyId, it.productId, after)
            store.insertMovementSafe(companyId, it.productId, "RETURN", -it.qtyMilli, cost, "PURCHASE_RETURN", docId, date)
        }
        return DocResult(entry, totals)
    }

    // ── السندات ──────────────────────────────────────────────────
    fun receive(
        companyId: String, date: String, voucherId: String,
        amountMinor: Long, toBank: Boolean = false,
    ): PostedEntry = post(
        JournalRequest(companyId, date, "سند قبض $voucherId", "VOUCHER", voucherId,
            null, Rules.receipt(book(companyId), amountMinor, toBank)),
    )

    fun paySupplier(
        companyId: String, date: String, voucherId: String,
        amountMinor: Long, fromBank: Boolean = false,
    ): PostedEntry = post(
        JournalRequest(companyId, date, "سند صرف $voucherId", "VOUCHER", voucherId,
            null, Rules.supplierPayment(book(companyId), amountMinor, fromBank)),
    )

    /** مخزون افتتاحي: كميات بلا قيد موردين (يقابلها رصيد افتتاحي) */
    fun receiveOpeningStock(
        companyId: String, date: String, docId: String, productId: String,
        qtyMilli: Long, unitCostMinor: Long,
    ): PostedEntry {
        val book = book(companyId)
        val value = Money.mulDiv(unitCostMinor, qtyMilli, 1000L)
        val entry = post(
            JournalRequest(companyId, date, "رصيد افتتاحي مخزون $docId", "OPENING", docId, null,
                listOf(
                    JournalLine(book.id(Chart.Acc.INVENTORY), debit = value, note = "مخزون افتتاحي"),
                    JournalLine(book.id(Chart.Acc.CAPITAL), credit = value, note = "رصيد افتتاحي"),
                )),
        )
        val stock = store.stock(companyId, productId)
        store.saveStock(companyId, productId, Costing.receive(stock, qtyMilli, unitCostMinor))
        store.insertMovementSafe(companyId, productId, "IN", qtyMilli, unitCostMinor, "OPENING", docId, date)
        return entry
    }

    /** تسوية جرد: يعدّل المخزون ويُرحّل الفرق */
    fun stockCount(
        companyId: String, date: String, countId: String,
        productId: String, countedQtyMilli: Long,
    ): PostedEntry {
        val stock = store.stock(companyId, productId)
        val (diffQty, diffValue, after) = Costing.adjustTo(stock, countedQtyMilli)
        if (diffQty == 0L) throw AccountingException(Err.BAD_LINE, "لا فرق في الجرد — لا حاجة لقيد")
        val entry = post(
            JournalRequest(companyId, date, "تسوية جرد $countId", "STOCK_COUNT", countId, null,
                Rules.stockCount(book(companyId), diffValue)),
        )
        store.saveStock(companyId, productId, after)
        store.insertMovementSafe(companyId, productId, "ADJUST", diffQty, after.avgCostMinor, "STOCK_COUNT", countId, date)
        return entry
    }

    // ── السندات الـ١٤ ────────────────────────────────────────────
    fun postVoucher(
        companyId: String, date: String, number: String,
        kind: Vouchers.Kind, lines: List<JournalLine>,
    ): PostedEntry = post(
        JournalRequest(companyId, date, "سند ${kind.labelArabic} $number", "VOUCHER", number, null, lines),
    )

    /** قبض مع توزيع على الفواتير المفتوحة (الأقدم أولاً) */
    fun receiveWithAllocation(
        companyId: String, date: String, number: String, amountMinor: Long,
        toBank: Boolean, openInvoices: List<Parties.OpenInvoice>,
    ): Pair<PostedEntry, Parties.AllocationResult> {
        val allocation = Parties.allocate(amountMinor, openInvoices)
        val entry = receive(companyId, date, number, amountMinor, toBank)
        return entry to allocation
    }

    /** إهلاك شهري لأصل */
    fun depreciate(
        companyId: String, date: String, assetId: String,
        amountMinor: Long,
    ): PostedEntry = post(
        JournalRequest(companyId, date, "إهلاك الأصل $assetId", "DEPRECIATION", "$assetId:$date", null,
            Rules.depreciation(book(companyId), amountMinor)),
    )

    /** مسير رواتب */
    fun payrollRun(
        companyId: String, date: String, runId: String,
        grossMinor: Long, deductionsMinor: Long, netMinor: Long,
    ): PostedEntry = post(
        JournalRequest(companyId, date, "مسير رواتب $runId", "PAYROLL", runId, null,
            Rules.payroll(book(companyId), grossMinor, deductionsMinor, netMinor)),
    )

    /** حركة شيك حسب آلة الحالات */
    fun chequeAction(
        companyId: String, date: String, chequeNumber: String,
        amountMinor: Long, from: Cheques.Status, action: Cheques.Action, feeMinor: Long = 0,
    ): PostedEntry {
        val to = Cheques.next(from, action)
        val b = book(companyId)
        val lines = when (to) {
            Cheques.Status.DEPOSITED -> Cheques.receivedLines(b, amountMinor)
            Cheques.Status.CLEARED -> Cheques.clearedLines(b, amountMinor)
            Cheques.Status.BOUNCED -> Cheques.bouncedLines(b, amountMinor, feeMinor)
            else -> throw AccountingException("BAD_CHEQUE_STATE", "لا قيد لهذا الانتقال")
        }
        return post(
            JournalRequest(companyId, date, "شيك $chequeNumber (${to.labelArabic})", "CHEQUE", "$chequeNumber:$to", null, lines),
        )
    }

    /** إقفال الفترة: قيد تصفير النتيجة في الأرباح المبقاة ثم قفل الفترة */
    fun closePeriod(companyId: String, year: Int, month: Int, date: String): PostedEntry? {
        val accounts = store.accounts(companyId).associateBy { it.id }
        val entries = store.entries(companyId, "%04d-%02d-01".format(year, month), lastDay(year, month))
            .map { it.toRequest() to it.lines }
        val retainedId = store.accounts(companyId).firstOrNull { it.code == Chart.Acc.RETAINED }?.id
            ?: throw AccountingException(Err.BAD_ACCOUNT, "حساب الأرباح المبقاة غير موجود")

        val summary = PeriodClose.buildClosingEntry(entries, accounts, retainedId)
        val entry = if (summary.closingLines.size >= 2) {
            post(JournalRequest(companyId, date, "قيد إقفال %04d-%02d".format(year, month), "PERIOD_CLOSE",
                "%04d-%02d".format(year, month), null, summary.closingLines))
        } else null
        return entry
    }

    // ── تقارير ───────────────────────────────────────────────────
    fun trialBalance(companyId: String, from: String? = null, to: String? = null): Reports.TrialBalance {
        val entries = store.entries(companyId, from, to).map { it.toRequest() to it.lines }
        return Reports.trialBalance(entries, store.accounts(companyId).associateBy { it.id })
    }

    fun balanceOf(companyId: String, code: String): Long {
        val acc = store.accounts(companyId).firstOrNull { it.code == code }
            ?: throw AccountingException(Err.BAD_ACCOUNT, "حساب غير موجود: $code")
        return Reports.balanceOf(store.entries(companyId).map { it.toRequest() to it.lines }, acc.id)
    }

    /** فحص تكاملي: قيمة المخزون في الأستاذ = مجموع دفتر الحركات */
    fun inventoryMatchesLedger(companyId: String, products: List<String>): Boolean {
        val fromMovements = products.sumOf { store.stock(companyId, it).valueMinor }
        val ledger = balanceOf(companyId, Chart.Acc.INVENTORY)
        return fromMovements == ledger
    }

    // ── أدوات داخلية ─────────────────────────────────────────────
    private fun book(companyId: String): AccountBook =
        AccountBook(store.accounts(companyId).associate { it.code to it.id })

    private fun lastDay(year: Int, month: Int): String {
        val d = java.time.YearMonth.of(year, month).atEndOfMonth()
        return d.toString()
    }

    private fun docTypeOf(sourceType: String): String = when {
        sourceType.startsWith("INVOICE") -> "INVOICE"
        sourceType.startsWith("PURCHASE") -> "PURCHASE"
        sourceType.startsWith("VOUCHER") -> "VOUCHER"
        sourceType.startsWith("RETURN") -> "RETURN"
        sourceType.startsWith("OPENING") -> "OPENING"
        sourceType.startsWith("DEPRECIATION") -> "DEPR"
        sourceType.startsWith("PAYROLL") -> "PAY"
        sourceType.startsWith("CHEQUE") -> "CHQ"
        sourceType.startsWith("PERIOD_CLOSE") -> "CLS"
        else -> "JOURNAL"
    }
}

/** تسجيل حركة مخزون عبر الواجهة (يُغلَّف في RoomStore بجدول stock_movement) */
private fun ErpStore.insertMovementSafe(
    companyId: String, productId: String, type: String, qtyMilli: Long,
    unitCostMinor: Long, sourceType: String, sourceId: String, date: String,
) = insertMovement(
    StockMovement(java.util.UUID.randomUUID().toString(), companyId, productId, type, qtyMilli, unitCostMinor, sourceType, sourceId, date),
)

private fun PostedEntry.toRequest() = JournalRequest(
    companyId, date, description, sourceType, sourceId, currency,
    lines.map { JournalLine(it.accountId, it.debit, it.credit, it.currency, it.fxRate, it.costCenterId, it.note) },
    reversalOf,
)
