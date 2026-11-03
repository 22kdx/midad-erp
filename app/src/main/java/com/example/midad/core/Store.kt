package com.example.midad.core

/** صف المنشأة كما تحتاجه النواة */
data class CompanyRow(
    val id: String,
    val name: String,
    val functionalCurrency: String,
    val allowedCurrencies: Set<String>,
    val lockDate: String? = null,
)

/** قيد مُرحّل محفوظ */
data class PostedEntry(
    val id: String,
    val companyId: String,
    val number: String,
    val date: String,
    val description: String,
    val sourceType: String,
    val sourceId: String,
    val currency: String,
    val totalBase: Long,
    val status: String,              // POSTED | REVERSED
    val lines: List<NormLine>,
    val reversalOf: String? = null,
    val reversedBy: String? = null,
)

data class StockMovement(
    val id: String,
    val companyId: String,
    val productId: String,
    val type: String,                // IN | OUT | RETURN | ADJUST | DAMAGE
    val qtyMilli: Long,
    val unitCostMinor: Long,
    val sourceType: String,
    val sourceId: String,
    val date: String,
)

data class AuditRow(
    val companyId: String,
    val entity: String,
    val entityId: String,
    val action: String,
    val detail: String,
    val actorId: String? = null,
)

/**
 * واجهة التخزين — يطبّقها RoomStore على الجوال وPgStore على الخادم.
 * كل الدوال متزامنة لأن النواة تُستدعى داخل coroutine واحدة تحمل معاملة (transaction).
 */
interface ErpStore {
    fun company(companyId: String): CompanyRow?
    fun accounts(companyId: String): List<AccountRef>
    fun account(companyId: String, accountId: String): AccountRef?
    fun periodStatus(companyId: String, year: Int, month: Int): PeriodStatus
    fun postedEntryNumber(companyId: String, sourceType: String, sourceId: String): String?

    /** ترقيم مستند بتسلسل آمن — يُنفَّذ داخل نفس المعاملة */
    fun nextNumber(companyId: String, docType: String, year: String): String

    fun insertEntry(entry: PostedEntry)
    fun entry(companyId: String, entryId: String): PostedEntry?
    fun entries(companyId: String, from: String? = null, to: String? = null): List<PostedEntry>
    fun markReversed(companyId: String, entryId: String, byEntryId: String)

    fun audit(row: AuditRow)

    fun stock(companyId: String, productId: String): Stock
    fun saveStock(companyId: String, productId: String, stock: Stock)
    fun movement(companyId: String, productId: String): List<StockMovement>

    /** يُستدعى بعد نجاح القيد فقط */
    fun insertMovement(movement: StockMovement)
}

/** مفتاح معرّفات — يُستبدل بـUUID على الجوال */
fun interface IdGenerator { fun next(): String }

object UuidIdGenerator : IdGenerator {
    override fun next(): String = java.util.UUID.randomUUID().toString()
}

/** تنفيذ مرجعي في الذاكرة — للاختبار وللتوضيح، لا للإنتاج */
class InMemoryStore : ErpStore {
    private val companies = HashMap<String, CompanyRow>()
    private val accountMap = HashMap<String, MutableList<AccountRef>>()
    private val periods = HashMap<String, PeriodStatus>()          // "co:2026-03"
    private val entries = ArrayList<PostedEntry>()
    private val sequences = HashMap<String, Int>()                 // "co:JOURNAL:2026"
    private val stocks = HashMap<String, Stock>()                  // "co:product"
    private val movements = ArrayList<StockMovement>()
    val auditLog = ArrayList<AuditRow>()

    fun addCompany(c: CompanyRow) { companies[c.id] = c }

    fun seedChart(companyId: String, chart: List<Chart.Def> = Chart.YEMEN, prefix: String = "acc-") {
        accountMap[companyId] = chart.map {
            AccountRef(prefix + it.code, it.code, it.name, it.type, it.isGroup, true)
        }.toMutableList()
    }

    fun openPeriods(companyId: String, year: Int) {
        (1..12).forEach { m -> periods["$companyId:$year-${"%02d".format(m)}"] = PeriodStatus.OPEN }
    }

    fun closePeriod(companyId: String, year: Int, month: Int) {
        periods["$companyId:$year-${"%02d".format(month)}"] = PeriodStatus.CLOSED
    }

    fun setLockDate(companyId: String, date: String?) {
        companies[companyId] = companies.getValue(companyId).copy(lockDate = date)
    }

    override fun company(companyId: String) = companies[companyId]
    override fun accounts(companyId: String) = accountMap[companyId] ?: emptyList()
    override fun account(companyId: String, accountId: String) =
        accountMap[companyId]?.firstOrNull { it.id == accountId }

    override fun periodStatus(companyId: String, year: Int, month: Int) =
        periods["$companyId:$year-${"%02d".format(month)}"] ?: PeriodStatus.NOT_FOUND

    override fun postedEntryNumber(companyId: String, sourceType: String, sourceId: String) =
        entries.firstOrNull {
            it.companyId == companyId && it.sourceType == sourceType &&
                it.sourceId == sourceId && it.status == "POSTED"
        }?.number

    override fun nextNumber(companyId: String, docType: String, year: String): String {
        val key = "$companyId:$docType:$year"
        val n = (sequences[key] ?: 0) + 1
        sequences[key] = n
        return "${docType.take(3)}-$year-${"%05d".format(n)}"
    }

    override fun insertEntry(entry: PostedEntry) { entries.add(entry) }
    override fun entry(companyId: String, entryId: String) =
        entries.firstOrNull { it.companyId == companyId && it.id == entryId }

    override fun entries(companyId: String, from: String?, to: String?) = entries.filter {
        it.companyId == companyId && it.status != "DRAFT" &&
            (from == null || it.date >= from) && (to == null || it.date <= to)
    }

    override fun markReversed(companyId: String, entryId: String, byEntryId: String) {
        val i = entries.indexOfFirst { it.companyId == companyId && it.id == entryId }
        if (i >= 0) entries[i] = entries[i].copy(status = "REVERSED", reversedBy = byEntryId)
    }

    override fun audit(row: AuditRow) { auditLog.add(row) }

    override fun stock(companyId: String, productId: String) =
        stocks["$companyId:$productId"] ?: Stock()

    override fun saveStock(companyId: String, productId: String, stock: Stock) {
        stocks["$companyId:$productId"] = stock
    }

    override fun movement(companyId: String, productId: String) =
        movements.filter { it.companyId == companyId && it.productId == productId }

    override fun insertMovement(movement: StockMovement) { movements.add(movement) }
}
