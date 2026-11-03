package com.example.midad.data.local

import com.example.midad.core.AccountRef
import com.example.midad.core.AccountType
import com.example.midad.core.AuditRow
import com.example.midad.core.CompanyRow
import com.example.midad.core.ErpStore
import com.example.midad.core.NormLine
import com.example.midad.core.PeriodStatus
import com.example.midad.core.PostedEntry
import com.example.midad.core.Stock
import com.example.midad.core.StockMovement

/**
 * تنفيذ ErpStore فوق Room — يُمرَّر إلى PostingService.
 * النواة لا تعرف Room، وRoom لا تعرف النواة؛ كل الربط في هذا الملف.
 */
class RoomErpStore(private val dao: ErpDao) : ErpStore {

    override fun company(companyId: String): CompanyRow? = dao.company(companyId)?.let {
        CompanyRow(
            id = it.id,
            name = it.name,
            functionalCurrency = it.functionalCurrency,
            allowedCurrencies = it.currenciesCsv.split(",").map(String::trim).toSet(),
            lockDate = it.lockDate,
        )
    }

    override fun accounts(companyId: String): List<AccountRef> =
        dao.accounts(companyId).map { it.toRef() }

    override fun account(companyId: String, accountId: String): AccountRef? =
        dao.account(companyId, accountId)?.toRef()

    /** يُستخدم عند بناء AccountBook للقواعد */
    fun accountIdByCode(companyId: String, code: String): String? =
        dao.accountByCode(companyId, code)?.id

    override fun periodStatus(companyId: String, year: Int, month: Int): PeriodStatus =
        when (dao.periodStatus(companyId, year, month)) {
            "OPEN" -> PeriodStatus.OPEN
            "CLOSED" -> PeriodStatus.CLOSED
            else -> PeriodStatus.NOT_FOUND
        }

    override fun postedEntryNumber(companyId: String, sourceType: String, sourceId: String): String? =
        dao.postedEntryNumber(companyId, sourceType, sourceId)

    override fun nextNumber(companyId: String, docType: String, year: String): String =
        dao.nextNumber(companyId, docType, year)

    override fun insertEntry(entry: PostedEntry) {
        dao.insertEntry(
            JournalEntryEntity(
                id = entry.id,
                companyId = entry.companyId,
                number = entry.number,
                date = entry.date,
                description = entry.description,
                sourceType = entry.sourceType,
                sourceId = entry.sourceId,
                currency = entry.currency,
                totalBase = entry.totalBase,
                status = entry.status,
                reversalOf = entry.reversalOf,
                reversedBy = entry.reversedBy,
                postedAt = System.currentTimeMillis(),
            ),
        )
        dao.insertLines(
            entry.lines.map {
                JournalLineEntity(
                    entryId = entry.id,
                    companyId = entry.companyId,
                    accountId = it.accountId,
                    debitMinor = it.debit,
                    creditMinor = it.credit,
                    currency = it.currency,
                    fxRate = it.fxRate,
                    amountBase = it.amountBase,
                    costCenterId = it.costCenterId,
                    note = it.note,
                )
            },
        )
    }

    override fun entry(companyId: String, entryId: String): PostedEntry? {
        val e = dao.entry(companyId, entryId) ?: return null
        return e.toPosted(dao.linesOf(listOf(e.id)).map { it.toNorm() })
    }

    override fun entries(companyId: String, from: String?, to: String?): List<PostedEntry> {
        val rows = dao.entries(companyId, from, to)
        if (rows.isEmpty()) return emptyList()
        val byEntry = dao.linesOf(rows.map { it.id }).groupBy { it.entryId }
        return rows.map { e -> e.toPosted(byEntry[e.id].orEmpty().map { it.toNorm() }) }
    }

    override fun markReversed(companyId: String, entryId: String, byEntryId: String) =
        dao.markReversed(companyId, entryId, byEntryId)

    override fun audit(row: AuditRow) = dao.insertAudit(
        AuditEntity(
            companyId = row.companyId,
            entity = row.entity,
            entityId = row.entityId,
            action = row.action,
            detail = row.detail,
            actorId = row.actorId,
        ),
    )

    override fun stock(companyId: String, productId: String): Stock =
        dao.stock(companyId, productId)
            ?.let { Stock(it.qtyMilli, it.valueMinor, it.avgCostMinor) }
            ?: Stock()

    override fun saveStock(companyId: String, productId: String, stock: Stock) =
        dao.upsertStock(
            StockEntity(companyId, productId, stock.qtyMilli, stock.valueMinor, stock.avgCostMinor),
        )

    override fun movement(companyId: String, productId: String): List<StockMovement> =
        dao.movements(companyId, productId).map {
            StockMovement(
                it.id, it.companyId, it.productId, it.type, it.qtyMilli,
                it.unitCostMinor, it.sourceType, it.sourceId, it.date,
            )
        }

    override fun insertMovement(movement: StockMovement) = dao.insertMovement(
        StockMovementEntity(
            movement.id, movement.companyId, movement.productId, movement.type,
            movement.qtyMilli, movement.unitCostMinor, movement.sourceType, movement.sourceId, movement.date,
        ),
    )

    // ── تحويلات ───────────────────────────────────────
    private fun AccountEntity.toRef() = AccountRef(
        id = id,
        code = code,
        name = name,
        type = runCatching { AccountType.valueOf(type) }.getOrDefault(AccountType.ASSET),
        isGroup = isGroup,
        isActive = isActive,
    )

    private fun JournalLineEntity.toNorm() = NormLine(
        accountId = accountId,
        debit = debitMinor,
        credit = creditMinor,
        currency = currency,
        fxRate = fxRate,
        amountBase = amountBase,
        costCenterId = costCenterId,
        note = note,
    )

    private fun JournalEntryEntity.toPosted(lines: List<NormLine>) = PostedEntry(
        id = id,
        companyId = companyId,
        number = number,
        date = date,
        description = description,
        sourceType = sourceType,
        sourceId = sourceId,
        currency = currency,
        totalBase = totalBase,
        status = status,
        lines = lines,
        reversalOf = reversalOf,
        reversedBy = reversedBy,
    )
}
