package com.example.midad.core

/** صف صنف كما تعرضه شاشة المخزون وتحتاجه قواعد التسعير/التنبيه */
data class ItemRow(
    val id: String,
    val name: String,
    val salePriceMinor: Long = 0L,
    val costMinor: Long = 0L,
    val qtyMilli: Long = 0L,
    val minQtyMilli: Long = 0L,
    val isService: Boolean = false,
    val isActive: Boolean = true,
)

/** شرائح السعر (جملة/نصف جملة) — تُختار أعلى شريحة مؤهلة بالكمية */
object Pricing {

    data class Tier(val minQtyMilli: Long, val priceMinor: Long, val label: String)

    fun priceFor(item: ItemRow, qtyMilli: Long, tiers: List<Tier>): Long =
        tiers.filter { qtyMilli >= it.minQtyMilli }
            .maxByOrNull { it.minQtyMilli }
            ?.priceMinor
            ?: item.salePriceMinor

    /** هامش الربح على سعر البيع بخانة عشرية واحدة — نص جاهز للعرض */
    fun marginPct(sellMinor: Long, costMinor: Long): String {
        if (sellMinor <= 0) return "0.0"
        val tenths = Money.mulDiv(sellMinor - costMinor, 1000L, sellMinor)
        val sign = if (tenths < 0) "-" else ""
        val abs = kotlin.math.abs(tenths)
        return "$sign${abs / 10}.${abs % 10}"
    }

    /** «15.5» ⇒ ١٥٥ (عشرات النسبة) لتفادي الكسور العائمة */
    fun pctTenths(pct: String): Long {
        val clean = pct.filter { it.isDigit() || it == '.' || it == '-' }
        if (clean.isEmpty()) return 0L
        val negative = clean.startsWith("-")
        val parts = clean.removePrefix("-").split(".")
        val whole = parts.getOrNull(0)?.toLongOrNull() ?: 0L
        val frac = (parts.getOrNull(1) ?: "").padEnd(1, '0').take(1).toLongOrNull() ?: 0L
        val v = whole * 10 + frac
        return if (negative) -v else v
    }

    /** حدود الخصم: المدير والمالك بلا قيد، والباقي بحد يمرّره المستدعي */
    fun discountAllowed(role: Role, pctTenths: Long, limitTenths: Long): Boolean = when (role) {
        Role.OWNER, Role.MANAGER -> true
        else -> pctTenths <= limitTenths
    }
}

/** تنبيهات إعادة الطلب — النافد أولاً ثم ما وصل للحد الأدنى */
object Reorder {

    enum class Level { OUT, LOW }

    data class Alert(
        val itemId: String,
        val name: String,
        val level: Level,
        val qtyMilli: Long,
        val minQtyMilli: Long,
    )

    fun scan(items: List<ItemRow>): List<Alert> = items
        .filter { it.isActive }
        .filterNot { it.isService }
        .filter { it.qtyMilli <= it.minQtyMilli }
        .map {
            Alert(
                itemId = it.id,
                name = it.name,
                level = if (it.qtyMilli <= 0L) Level.OUT else Level.LOW,
                qtyMilli = it.qtyMilli,
                minQtyMilli = it.minQtyMilli,
            )
        }
        .sortedWith(compareBy({ if (it.level == Level.OUT) 0 else 1 }, { it.qtyMilli }))

    /** كمية مقترحة للشراء تغطي [months] أشهر من الاستهلاك الأدنى */
    fun suggestedQty(item: ItemRow, months: Int): Long {
        if (item.isService || months <= 0) return 0L
        val target = item.minQtyMilli * months
        return (target - item.qtyMilli).coerceAtLeast(0L)
    }
}

/** تفكيك الباقات إلى مكوّنات (منتج مركّب) */
object Kits {

    data class Component(val productId: String, val qtyMilli: Long)
    data class Kit(val id: String, val components: List<Component>)

    /** (معرّف المكوّن, الكمية المطلوبة) بلا تكرار */
    fun explode(kit: Kit, qtyMilli: Long): List<Pair<String, Long>> {
        val map = LinkedHashMap<String, Long>()
        kit.components.forEach { c ->
            map[c.productId] = (map[c.productId] ?: 0L) + Money.mulDiv(c.qtyMilli, qtyMilli, 1000L)
        }
        return map.map { it.key to it.value }
    }

    /** كم باقة يمكن تجهيزها من المخزون المتاح (بالملّي) */
    fun buildable(kit: Kit, stock: Map<String, Long>): Long =
        explode(kit, 1_000L).minOfOrNull { (pid, per) ->
            if (per <= 0) Long.MAX_VALUE / 2
            else Money.mulDiv(stock[pid] ?: 0L, 1_000L, per)
        }?.coerceAtMost(Long.MAX_VALUE / 2) ?: 0L

    /** المكوّنات الناقصة لعدد باقات مطلوب */
    fun shortages(kit: Kit, qtyMilli: Long, stock: Map<String, Long>): List<Pair<String, Long>> =
        explode(kit, qtyMilli).mapNotNull { (pid, need) ->
            val have = stock[pid] ?: 0L
            if (have < need) pid to (need - have) else null
        }
}

/** الاستحقاقات المتأخرة والشيكات القريبة */
object DueWatch {

    data class Due(val ref: String, val partyId: String, val daysOverdue: Long, val amountMinor: Long)

    fun overdue(items: List<Statements.OpenItem>, asOf: String, termDays: Int): List<Due> =
        items.mapNotNull { item ->
            val days = Statements.daysBetween(item.date, asOf) - termDays
            if (days > 0) Due(item.ref, item.partyId, days, item.amountMinor) else null
        }.sortedByDescending { it.daysOverdue }

    fun total(dues: List<Due>): Long = dues.sumOf { it.amountMinor }

    data class ChequeItem(
        val id: String,
        val partyId: String,
        val amountMinor: Long,
        val dueDate: String,
        val incoming: Boolean,
    )

    fun chequesInWindow(cheques: List<ChequeItem>, asOf: String, windowDays: Int): List<ChequeItem> =
        cheques.filter {
            val d = Statements.daysBetween(asOf, it.dueDate)
            d in 0..windowDays.toLong()
        }.sortedBy { it.dueDate }
}

/** مؤشرات تشغيلية سريعة */
object Insights {

    data class ProductSale(val productId: String, val qtyMilli: Long, val netMinor: Long, val cogsMinor: Long) {
        val profitMinor: Long get() = netMinor - cogsMinor
    }

    fun topByProfit(rows: List<ProductSale>, limit: Int = 5): List<ProductSale> =
        rows.sortedByDescending { it.profitMinor }.take(limit)

    fun lossMaking(rows: List<ProductSale>): List<ProductSale> =
        rows.filter { it.profitMinor < 0 }.sortedBy { it.profitMinor }

    fun averageBasket(amounts: List<Long>): Long =
        if (amounts.isEmpty()) 0L else amounts.sum() / amounts.size

    /** نسبة البيع الآجل من إجمالي المبيعات كنص بخانة عشرية */
    fun creditRatio(creditMinor: Long, totalMinor: Long): String {
        if (totalMinor <= 0L) return "0.0"
        val tenths = Money.mulDiv(creditMinor, 1000L, totalMinor)
        return "${tenths / 10}.${kotlin.math.abs(tenths % 10)}"
    }
}

// ══════════════════════════════════════════════════════════════
//  إضافات تشغيلية موسّعة (قوائم أسعار متعددة · تغطية أيام · باقات تكلفة
//  · استحقاقات بمجموعات · مؤشرات إدارة). منفصلة عن الأسماء أعلاه
//  حتى لا تتغيّر واجهات الشاشات القائمة.
// ══════════════════════════════════════════════════════════════

/** تغذية المخزون: نقص فعلي أو تغطية أيام */
object Replenish {

    data class Product(
        val id: String,
        val name: String,
        val qtyMilli: Long,
        val minQtyMilli: Long,
        val maxQtyMilli: Long = 0L,
        val lastPurchaseCostMinor: Long = 0L,
    )

    data class Suggestion(
        val productId: String,
        val name: String,
        val onHandMilli: Long,
        val suggestQtyMilli: Long,
        val estCostMinor: Long,
    )

    fun scan(products: List<Product>): List<Suggestion> = products.mapNotNull { p ->
        val target = if (p.maxQtyMilli > p.minQtyMilli) p.maxQtyMilli else p.minQtyMilli * 2
        if (p.qtyMilli > p.minQtyMilli) return@mapNotNull null
        val need = (target - p.qtyMilli).coerceAtLeast(0L)
        if (need <= 0L) null else Suggestion(
            p.id, p.name, p.qtyMilli, need, Money.mulDiv(p.lastPurchaseCostMinor, need, 1000L),
        )
    }.sortedByDescending { it.estCostMinor }

    /** متوسط الصرف اليومي من حركات الفترة */
    fun dailyUsage(movements: List<StockMovement>, productId: String, from: String, to: String): Double {
        val days = maxOf(1L, Statements.daysBetween(from, to))
        val out = movements
            .filter { it.productId == productId && it.date in from..to && it.qtyMilli < 0 }
            .sumOf { -it.qtyMilli }
        return out.toDouble() / days
    }

    /** اقتراح شراء يكفي لتغطية [coverageDays] يوماً */
    fun byCoverage(
        products: List<Product>,
        movements: List<StockMovement>,
        asOf: String,
        coverageDays: Int,
        windowDays: Int = 30,
    ): List<Suggestion> {
        val from = java.time.LocalDate.parse(asOf).minusDays(windowDays.toLong()).toString()
        return products.mapNotNull { p ->
            val daily = dailyUsage(movements, p.id, from, asOf)
            val need = (daily * coverageDays - p.qtyMilli / 1000.0).toLong()
            if (need <= 0) null
            else Suggestion(
                p.id, p.name, p.qtyMilli, need * 1000,
                Money.mulDiv(p.lastPurchaseCostMinor, need * 1000, 1000L),
            )
        }.sortedByDescending { it.estCostMinor }
    }
}

/** قوائم أسعار متعددة التصنيفات (تجزئة/جملة/عقد) + حدود الخصم بالدور */
object PriceBook {

    enum class Tier(val label: String) {
        RETAIL("تجزئة"), WHOLESALE("جملة"), SEMI("نصف جملة"), SPECIAL("عقد/مشروع"), COST_PLUS("تكلفة + هامش")
    }

    data class PriceEntry(
        val productId: String,
        val tier: Tier,
        val priceMinor: Long,
        val minQtyMilli: Long = 1000L,
    )

    fun priceFor(
        entries: List<PriceEntry>,
        productId: String,
        tier: Tier,
        qtyMilli: Long,
        fallbackMinor: Long,
    ): Long {
        val candidates = entries
            .filter { it.productId == productId && it.tier == tier && it.minQtyMilli <= qtyMilli }
            .sortedByDescending { it.minQtyMilli }
        if (candidates.isNotEmpty()) return candidates.first().priceMinor
        return entries.firstOrNull { it.productId == productId && it.tier == Tier.RETAIL }?.priceMinor
            ?: fallbackMinor
    }

    fun costPlus(costMinor: Long, marginPct: String): Long =
        costMinor + Money.percent(costMinor, marginPct)

    /** هامش الربح على سعر البيع (وليس على التكلفة) */
    fun marginPercent(sellMinor: Long, costMinor: Long): Double {
        if (sellMinor <= 0) return 0.0
        return (sellMinor - costMinor) * 100.0 / sellMinor
    }

    fun markupPercent(sellMinor: Long, costMinor: Long): Double {
        if (costMinor <= 0) return 0.0
        return (sellMinor - costMinor) * 100.0 / costMinor
    }

    /** هل الخصم المطلوب مسموح لدور المستخدم؟ (حد الدور من Rbac) */
    fun discountAllowed(role: Role, percent: String): Boolean {
        val p = Money.parse(percent, "XXX").toDouble() / 100.0
        return p <= Rbac.maxDiscount(role) + 1e-9
    }

    /** أعلى خصم يمكن منحه مع الحفاظ على هامش أدنى */
    fun maxDiscountWithinMargin(sellMinor: Long, costMinor: Long, minMarginPct: Double): String {
        if (sellMinor <= 0) return "0"
        val floorPrice = (costMinor / (1 - minMarginPct / 100.0)).toLong()
        val pct = ((sellMinor - floorPrice) * 100.0 / sellMinor).coerceIn(0.0, 100.0)
        return String.format(java.util.Locale.US, "%.2f", pct)
    }

    fun suggestFromMargin(costMinor: Long, targetMarginPct: Double): Long {
        if (targetMarginPct >= 100.0) return costMinor * 10
        return Math.round(costMinor / (1 - targetMarginPct / 100.0))
    }
}

/** باقات بتكلفة حقيقية: مجموع تكلفة المكوّنات وكشف النقص */
object Bundles {

    data class Component(val productId: String, val qtyMilli: Long)
    data class Bundle(val id: String, val name: String, val components: List<Component>)

    data class Explosion(val component: Component, val unitCostMinor: Long) {
        val lineCostMinor: Long get() = Money.mulDiv(unitCostMinor, component.qtyMilli, 1000L)
    }

    data class Plan(
        val lines: List<Explosion>,
        val totalCostMinor: Long,
        val shortages: List<Pair<String, Long>>,
    )

    fun plan(
        bundles: Map<String, Bundle>,
        requested: List<Component>,
        stockOf: (String) -> Stock,
    ): Plan {
        val need = LinkedHashMap<String, Long>()
        val costs = HashMap<String, Long>()

        fun walk(productId: String, qtyMilli: Long, depth: Int) {
            require(depth < 5) { "تداخل باقات أعمق من المسموح" }
            val bundle = bundles[productId]
            if (bundle == null) {
                need[productId] = (need[productId] ?: 0L) + qtyMilli
                costs[productId] = stockOf(productId).avgCostMinor
            } else {
                bundle.components.forEach { c ->
                    walk(c.productId, Money.mulDiv(c.qtyMilli, qtyMilli, 1000L), depth + 1)
                }
            }
        }
        requested.forEach { walk(it.productId, it.qtyMilli, 0) }

        val lines = need.map { (pid, q) -> Explosion(Component(pid, q), costs[pid] ?: 0L) }
        val total = lines.sumOf { it.lineCostMinor }
        val shortages = need.mapNotNull { (pid, q) ->
            val available = stockOf(pid).qtyMilli
            if (available < q) pid to (q - available) else null
        }
        return Plan(lines, total, shortages)
    }

    fun buildable(plan: Plan): Boolean = plan.shortages.isEmpty()
}

/** استحقاقات مقسّمة إلى مجموعات زمنية (متأخر · اليوم · ٧ أيام …) */
object DueEngine {

    data class Item(
        val id: String,
        val label: String,
        val partyName: String,
        val dueDate: String,
        val amountMinor: Long,
        val kind: String = "INVOICE",
    )

    data class Bucket(val label: String, val daysFrom: Int, val daysTo: Int)

    val buckets = listOf(
        Bucket("متأخر", -100_000, -1),
        Bucket("اليوم", 0, 0),
        Bucket("خلال ٧ أيام", 1, 7),
        Bucket("٨–٣٠ يوماً", 8, 30),
        Bucket("٣١–٦٠ يوماً", 31, 60),
        Bucket("أكثر من ٦٠", 61, 100_000),
    )

    data class Summary(
        val overdueMinor: Long,
        val dueSoonMinor: Long,
        val byBucket: Map<String, Long>,
        val items: List<Item>,
    )

    fun summarize(items: List<Item>, asOf: String, soonDays: Int = 7): Summary {
        val map = LinkedHashMap<String, Long>()
        buckets.forEach { map[it.label] = 0L }
        var overdue = 0L
        var soon = 0L

        for (it in items) {
            val days = Statements.daysBetween(it.dueDate, asOf)
            val b = buckets.firstOrNull { days >= it.daysFrom && days <= it.daysTo } ?: buckets.last()
            map[b.label] = (map[b.label] ?: 0L) + it.amountMinor
            if (days < 0) overdue += it.amountMinor
            if (days in 0..soonDays) soon += it.amountMinor
        }
        return Summary(overdue, soon, map, items.sortedBy { it.dueDate })
    }
}

/** مؤشرات إدارية تُحسب من القوائم المالية */
object Management {

    data class Kpi(
        val revenueMinor: Long,
        val grossMarginPct: Double,
        val netMarginPct: Double,
        val expensesRatioPct: Double,
        val receivableMinor: Long,
        val payableMinor: Long,
        val cashMinor: Long,
        val stockMinor: Long,
        val currentRatio: Double,
        val breakEvenMinor: Long,
    )

    fun kpis(
        income: Statements.IncomeStatement,
        balance: Statements.BalanceSheet,
        fixedExpensesMinor: Long,
    ): Kpi {
        val grossMargin = if (income.netRevenue > 0) income.grossProfit * 100.0 / income.netRevenue else 0.0
        val netMargin = if (income.netRevenue > 0) income.netProfit * 100.0 / income.netRevenue else 0.0
        val fixedAndVar = income.totalExpenses - income.cogs
        val expRatio = if (income.netRevenue > 0) fixedAndVar * 100.0 / income.netRevenue else 0.0

        fun row(list: List<Reports.BalanceRow>, code: String) = list.firstOrNull { it.code == code }
        val currentAssets = balance.assets.filterNot { it.code.startsWith("12") }.sumOf { it.debit - it.credit }
        val currentLiabilities = balance.liabilities.filterNot { it.code.startsWith("22") }.sumOf { it.credit - it.debit }
        val receivable = row(balance.assets, Chart.Acc.AR)?.let { it.debit - it.credit } ?: 0L
        val payable = row(balance.liabilities, Chart.Acc.AP)?.let { it.credit - it.debit } ?: 0L
        val cash = (row(balance.assets, Chart.Acc.CASH)?.let { it.debit - it.credit } ?: 0L) +
            (row(balance.assets, Chart.Acc.BANK)?.let { it.debit - it.credit } ?: 0L)
        val stock = row(balance.assets, Chart.Acc.INVENTORY)?.let { it.debit - it.credit } ?: 0L
        val ratio = if (currentLiabilities > 0) currentAssets.toDouble() / currentLiabilities else 0.0

        val contribution = if (income.netRevenue > 0) income.grossProfit.toDouble() / income.netRevenue else 0.0
        val breakEven = if (contribution > 0) Math.round(fixedExpensesMinor / contribution) else 0L

        return Kpi(
            revenueMinor = income.netRevenue,
            grossMarginPct = grossMargin,
            netMarginPct = netMargin,
            expensesRatioPct = expRatio,
            receivableMinor = receivable,
            payableMinor = payable,
            cashMinor = cash,
            stockMinor = stock,
            currentRatio = ratio,
            breakEvenMinor = breakEven,
        )
    }

    /** تحليل باريتو: الأصناف التي تصنع النسبة الأكبر من الربح */
    fun pareto(revenues: List<GroupAgg>, sharePct: Double = 80.0): List<GroupAgg> {
        val sorted = revenues.sortedByDescending { it.profitMinor }
        val target = sorted.sumOf { it.profitMinor }.toDouble() * sharePct / 100.0
        val out = ArrayList<GroupAgg>()
        var acc = 0.0
        for (f in sorted) {
            if (acc >= target) break
            out += f
            acc += f.profitMinor
        }
        return out
    }

    /** صحة البيانات: أصناف بلا حركة، قيود بلا مستند، قيود عكسية ناقصة المرجع */
    fun health(
        entries: List<PostedEntry>,
        documentIds: Set<String>,
        productIds: Set<String>,
        movedProductIds: Set<String>,
    ): List<String> {
        val problems = ArrayList<String>()

        entries.filter { it.sourceType in setOf("INVOICE", "PURCHASE", "RETURN", "PURCHASE_RETURN") }
            .filter { it.sourceId !in documentIds }
            .take(20)
            .forEach { problems += "قيد ${it.number} يشير إلى مستند غير موجود (${it.sourceId})" }

        (productIds - movedProductIds).take(20)
            .forEach { problems += "صنف بلا أي حركة مخزنية: $it" }

        entries.filter { it.status == "REVERSED" && it.reversedBy == null }
            .forEach { problems += "قيد عكسي بلا مرجع: ${it.number}" }

        return problems
    }
}
