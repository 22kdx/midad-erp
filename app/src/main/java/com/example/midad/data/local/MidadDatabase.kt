package com.example.midad.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface ErpDao {

    // ── المنشأة والحسابات ─────────────────────────────
    @Query("SELECT * FROM company WHERE id = :id")
    fun company(id: String): CompanyEntity?

    @Query("SELECT * FROM account WHERE companyId = :c ORDER BY code")
    fun accounts(c: String): List<AccountEntity>

    @Query("SELECT * FROM account WHERE companyId = :c AND id = :id")
    fun account(c: String, id: String): AccountEntity?

    @Query("SELECT * FROM account WHERE companyId = :c AND code = :code LIMIT 1")
    fun accountByCode(c: String, code: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAccounts(rows: List<AccountEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertCompany(row: CompanyEntity)

    @Update
    fun updateCompany(row: CompanyEntity)

    // ── الفترات ───────────────────────────────────────
    @Query("SELECT status FROM period WHERE companyId = :c AND year = :y AND month = :m")
    fun periodStatus(c: String, y: Int, m: Int): String?

    @Query("UPDATE period SET status = :s WHERE companyId = :c AND year = :y AND month = :m")
    fun setPeriodStatus(c: String, y: Int, m: Int, s: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertPeriods(rows: List<PeriodEntity>)

    // ── الترقيم ───────────────────────────────────────
    @Query("SELECT nextValue FROM sequence WHERE companyId = :c AND docType = :d AND year = :y")
    fun sequence(c: String, d: String, y: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSequence(row: SequenceEntity)

    // ── القيود ────────────────────────────────────────
    @Query(
        "SELECT number FROM journal_entry WHERE companyId = :c " +
            "AND sourceType = :st AND sourceId = :sid AND status = 'POSTED' LIMIT 1",
    )
    fun postedEntryNumber(c: String, st: String, sid: String): String?

    @Query("SELECT * FROM journal_entry WHERE companyId = :c AND id = :id")
    fun entry(c: String, id: String): JournalEntryEntity?

    @Query(
        "SELECT * FROM journal_entry WHERE companyId = :c AND status != 'DRAFT' " +
            "AND (:from IS NULL OR date >= :from) AND (:to IS NULL OR date <= :to) " +
            "ORDER BY date, number",
    )
    fun entries(c: String, from: String?, to: String?): List<JournalEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertEntryRows(rows: List<JournalEntryEntity>)

    @Query("SELECT * FROM journal_line WHERE entryId IN (:ids)")
    fun linesOf(ids: List<String>): List<JournalLineEntity>

    @Insert
    fun insertEntry(e: JournalEntryEntity)

    @Insert
    fun insertLines(rows: List<JournalLineEntity>)

    @Query("UPDATE journal_entry SET status = 'REVERSED', reversedBy = :by WHERE companyId = :c AND id = :id")
    fun markReversed(c: String, id: String, by: String)

    // ── الأصناف ───────────────────────────────────────
    @Query("SELECT * FROM product WHERE companyId = :c AND isActive = 1 ORDER BY name")
    fun products(c: String): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertProducts(rows: List<ProductEntity>)

    // ── المخزون ───────────────────────────────────────
    @Query("SELECT * FROM stock WHERE companyId = :c AND productId = :p")
    fun stock(c: String, p: String): StockEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertStock(row: StockEntity)

    @Insert
    fun insertMovement(row: StockMovementEntity)

    @Query("SELECT * FROM stock_movement WHERE companyId = :c AND productId = :p ORDER BY createdAt")
    fun movements(c: String, p: String): List<StockMovementEntity>

    // ── التدقيق والطابور ──────────────────────────────
    // ── الأطراف ───────────────────────────────────────
    @Query("SELECT * FROM party WHERE companyId = :c ORDER BY name")
    fun parties(c: String): List<PartyEntity>

    @Query("SELECT * FROM party WHERE companyId = :c AND kind = :kind ORDER BY name")
    fun partiesOfKind(c: String, kind: String): List<PartyEntity>

    @Query("SELECT * FROM party WHERE companyId = :c AND id = :id")
    fun party(c: String, id: String): PartyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertParties(rows: List<PartyEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertParty(row: PartyEntity)

    // ── المستندات ────────────────────────────────────
    @Query("SELECT * FROM document WHERE companyId = :c ORDER BY createdAt DESC")
    fun documents(c: String): List<DocumentEntity>

    @Query("SELECT * FROM document WHERE companyId = :c AND kind = :kind ORDER BY createdAt DESC")
    fun documentsOfKind(c: String, kind: String): List<DocumentEntity>

    @Query("SELECT * FROM document WHERE companyId = :c AND id = :id")
    fun document(c: String, id: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDocument(row: DocumentEntity)

    @Insert
    fun insertDocLines(rows: List<DocumentLineEntity>)

    @Query("SELECT * FROM document_line WHERE documentId = :id ORDER BY lineNo")
    fun docLines(id: String): List<DocumentLineEntity>

    @Query("SELECT * FROM document_line WHERE documentId IN (:ids) ORDER BY documentId, lineNo")
    fun docLinesOf(ids: List<String>): List<DocumentLineEntity>

    @Query("SELECT COUNT(*) FROM document WHERE companyId = :c AND kind = :kind")
    fun countDocs(c: String, kind: String): Int

    // ── المستخدمون والإعدادات ────────────────────────
    @Query("SELECT * FROM app_user WHERE companyId = :c AND isActive = 1")
    fun users(c: String): List<UserEntity>

    @Query("SELECT * FROM app_user WHERE companyId = :c AND phone = :phone LIMIT 1")
    fun userByPhone(c: String, phone: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertUser(row: UserEntity)

    @Query("UPDATE app_user SET failedAttempts = :n, lockedUntil = :lockedUntil WHERE id = :id")
    fun updateUserLock(id: String, n: Int, lockedUntil: Long?)

    @Query("SELECT * FROM app_setting WHERE settingKey = :key")
    fun setting(key: String): SettingEntity?

    @Query("SELECT * FROM app_setting")
    fun settings(): List<SettingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSetting(row: SettingEntity)

    // ── المستودعات ───────────────────────────────────
    @Query("SELECT * FROM warehouse WHERE companyId = :c AND isActive = 1 ORDER BY isDefault DESC, name")
    fun warehouses(c: String): List<WarehouseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertWarehouse(row: WarehouseEntity)

    @Query("SELECT * FROM stock_level WHERE companyId = :c")
    fun stockLevels(c: String): List<StockLevelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertStockLevel(row: StockLevelEntity)

    @Query("SELECT * FROM stock_level WHERE companyId = :c AND warehouseId = :w AND productId = :p")
    fun stockLevel(c: String, w: String, p: String): StockLevelEntity?

    // ── الدفعات ──────────────────────────────────────
    @Query("SELECT * FROM batch WHERE companyId = :c ORDER BY expiry IS NULL, expiry, receivedDate")
    fun batches(c: String): List<BatchEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertBatch(row: BatchEntity)

    @Query("UPDATE batch SET qtyMilli = :qty WHERE id = :id")
    fun updateBatchQty(id: String, qty: Long)

    // ── الورديات ─────────────────────────────────────
    @Query("SELECT * FROM shift WHERE companyId = :c ORDER BY openedAt DESC")
    fun shifts(c: String): List<ShiftEntity>

    @Query("SELECT * FROM shift WHERE companyId = :c AND closedAt IS NULL ORDER BY openedAt DESC LIMIT 1")
    fun openShift(c: String): ShiftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertShift(row: ShiftEntity)

    // ── الفواتير المعلّقة ────────────────────────────
    @Query("SELECT * FROM held_invoice WHERE companyId = :c ORDER BY createdAt DESC")
    fun heldInvoices(c: String): List<HeldInvoiceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertHeldInvoice(row: HeldInvoiceEntity)

    @Query("DELETE FROM held_invoice WHERE id = :id")
    fun deleteHeldInvoice(id: String)

    // ── طاولات المطعم ────────────────────────────────
    @Query("SELECT * FROM dining_table WHERE companyId = :c ORDER BY name")
    fun tables(c: String): List<TableEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertTable(row: TableEntity)

    // ── الأصول الثابتة ───────────────────────────────
    @Query("SELECT * FROM asset WHERE companyId = :c ORDER BY createdAt DESC")
    fun assets(c: String): List<AssetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAsset(row: AssetEntity)

    @Query("UPDATE asset SET accumulatedMinor = :acc, lastDepreciatedPeriod = :period WHERE id = :id")
    fun updateAssetDep(id: String, acc: Long, period: String)

    // ── الموظفون ─────────────────────────────────────
    @Query("SELECT * FROM employee WHERE companyId = :c AND isActive = 1 ORDER BY name")
    fun employees(c: String): List<EmployeeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertEmployee(row: EmployeeEntity)

    // ── تحديث المسدَّد من مستند ──────────────────────
    @Query("UPDATE document SET paidMinor = :paid WHERE id = :id")
    fun updateDocPaid(id: String, paid: Long)

    // ── الشيكات ──────────────────────────────────────
    @Query("SELECT * FROM stock WHERE companyId = :c")
    fun allStocks(c: String): List<StockEntity>

    @Query("SELECT * FROM stock_movement WHERE companyId = :c ORDER BY createdAt")
    fun allMovements(c: String): List<StockMovementEntity>

    @Query("SELECT * FROM journal_entry WHERE companyId = :c ORDER BY date, createdAt")
    fun allEntries(c: String): List<JournalEntryEntity>

    @Query("SELECT * FROM journal_line WHERE entryId IN (:ids)")
    fun allLines(ids: List<String>): List<JournalLineEntity>

    @Query("SELECT * FROM cheque WHERE companyId = :c ORDER BY dueDate")
    fun cheques(c: String): List<ChequeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertCheque(row: ChequeEntity)

    @Insert
    fun insertAudit(row: AuditEntity)

    @Query("SELECT * FROM audit_log WHERE companyId = :c ORDER BY id DESC LIMIT 200")
    fun audits(c: String): List<AuditEntity>

    @Query("SELECT * FROM audit_log WHERE companyId = :c AND entityId = :entityId ORDER BY id DESC")
    fun auditsOf(c: String, entityId: String): List<AuditEntity>

    // ── كشف البنك والتسوية ─────────────────────────────
    @Query("SELECT * FROM bank_statement WHERE companyId = :c ORDER BY date, id")
    fun bankLines(c: String): List<BankStatementLineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertBankLine(row: BankStatementLineEntity)

    @Query("DELETE FROM bank_statement WHERE id = :id")
    fun deleteBankLine(id: String)

    @Query("UPDATE bank_statement SET matchedEntryId = :entryId WHERE id = :id")
    fun matchBankLine(id: String, entryId: String?)

    @Query("UPDATE bank_statement SET matchedEntryId = NULL WHERE companyId = :c")
    fun clearBankMatches(c: String)

    // ── الفواتير الدورية ───────────────────────────────
    @Query("SELECT * FROM doc_schedule WHERE companyId = :c ORDER BY active DESC, startDate")
    fun schedules(c: String): List<ScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSchedule(row: ScheduleEntity)

    @Query("UPDATE doc_schedule SET lastRunDate = :date WHERE id = :id")
    fun markScheduleRun(id: String, date: String)

    @Query("UPDATE doc_schedule SET active = :active WHERE id = :id")
    fun setScheduleActive(id: String, active: Boolean)

    @Query("DELETE FROM doc_schedule WHERE id = :id")
    fun deleteSchedule(id: String)

    // ── المرفقات ───────────────────────────────────────
    @Query("SELECT * FROM attachment WHERE companyId = :c ORDER BY createdAt DESC")
    fun attachments(c: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachment WHERE companyId = :c AND ownerId = :ownerId ORDER BY createdAt DESC")
    fun attachmentsOf(c: String, ownerId: String): List<AttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAttachment(row: AttachmentEntity)

    @Query("DELETE FROM attachment WHERE id = :id")
    fun deleteAttachment(id: String)

    // ── الأرقام التسلسلية ──────────────────────────────
    @Query("SELECT * FROM serial WHERE companyId = :c ORDER BY number")
    fun serials(c: String): List<SerialEntity>

    @Query("SELECT * FROM serial WHERE companyId = :c AND productId = :p AND status = 'IN_STOCK' ORDER BY date, number")
    fun serialsInStock(c: String, p: String): List<SerialEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSerials(rows: List<SerialEntity>)

    @Query("UPDATE serial SET status = :status, documentId = :docId, date = :date WHERE id = :id")
    fun updateSerial(id: String, status: String, docId: String?, date: String)

    @Query("DELETE FROM serial WHERE id = :id")
    fun deleteSerial(id: String)

    // ── الموازنات ──────────────────────────────────────
    @Query("SELECT * FROM budget WHERE companyId = :c AND year = :year ORDER BY accountCode")
    fun budgets(c: String, year: Int): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertBudget(row: BudgetEntity)

    @Query("DELETE FROM budget WHERE id = :id")
    fun deleteBudget(id: String)

    // ── حالة المستند (أمر شراء: معتمد/ملغى/محوَّل) ─────
    @Query("UPDATE document SET status = :status WHERE id = :id")
    fun updateDocumentStatus(id: String, status: String)

    @Insert
    fun insertOutbox(row: OutboxEntity)

    /** ترقيم داخل معاملة واحدة: قراءة + زيادة + كتابة */
    @Transaction
    fun nextNumber(c: String, docType: String, year: String): String {
        val current = sequence(c, docType, year) ?: 0
        val next = current + 1
        upsertSequence(SequenceEntity(c, docType, year, next))
        val prefix = docType.take(3)
        return prefix + "-" + year + "-" + next.toString().padStart(5, '0')
    }
}

@Database(
    entities = [
        CompanyEntity::class, AccountEntity::class, PeriodEntity::class, SequenceEntity::class,
        JournalEntryEntity::class, JournalLineEntity::class, ProductEntity::class, StockEntity::class,
        StockMovementEntity::class, AuditEntity::class, OutboxEntity::class,
        PartyEntity::class, DocumentEntity::class, DocumentLineEntity::class,
        UserEntity::class, SettingEntity::class, ChequeEntity::class,
        AssetEntity::class, EmployeeEntity::class,
        WarehouseEntity::class, StockLevelEntity::class, BatchEntity::class,
        ShiftEntity::class, HeldInvoiceEntity::class, TableEntity::class,
        BankStatementLineEntity::class, ScheduleEntity::class, BudgetEntity::class,
        SerialEntity::class, AttachmentEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class MidadDatabase : RoomDatabase() {
    abstract fun erpDao(): ErpDao
}
