package com.example.midad.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * جداول Room — نسخة الجوال من مخطّط PostgreSQL (db/schema.sql).
 * كل جدول يحمل companyId (عزل المنشأة) وبصمات التدقيق.
 */

@Entity(tableName = "company")
data class CompanyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val functionalCurrency: String,
    val currenciesCsv: String,      // "YER,USD"
    val lockDate: String? = null,
    val taxNumber: String? = null,
)

@Entity(tableName = "account", indices = [Index("companyId", "code", unique = true)])
data class AccountEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val code: String,
    val name: String,
    val type: String,               // ASSET | LIABILITY | EQUITY | REVENUE | EXPENSE
    val parentCode: String? = null,
    val isGroup: Boolean = false,
    val isActive: Boolean = true,
)

@Entity(tableName = "period", indices = [Index("companyId", "year", "month", unique = true)])
data class PeriodEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val year: Int,
    val month: Int,
    val status: String,             // OPEN | CLOSED
)

@Entity(tableName = "sequence", primaryKeys = ["companyId", "docType", "year"])
data class SequenceEntity(
    val companyId: String,
    val docType: String,
    val year: String,
    val nextValue: Int,
)

@Entity(
    tableName = "journal_entry",
    indices = [Index("companyId", "sourceType", "sourceId"), Index("companyId", "date")],
)
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val number: String,
    val date: String,
    val description: String,
    val sourceType: String,
    val sourceId: String,
    val currency: String,
    val totalBase: Long,
    val status: String,             // POSTED | REVERSED
    val reversalOf: String? = null,
    val reversedBy: String? = null,
    val postedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String? = null,
)

@Entity(tableName = "journal_line", indices = [Index("entryId"), Index("companyId", "accountId")])
data class JournalLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: String,
    val companyId: String,
    val accountId: String,
    val debitMinor: Long,
    val creditMinor: Long,
    val currency: String,
    val fxRate: String,
    val amountBase: Long,
    val costCenterId: String? = null,
    val note: String = "",
)

@Entity(tableName = "product", indices = [Index("companyId", "sku", unique = true)])
data class ProductEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val name: String,
    val sku: String,
    val unit: String = "حبة",
    val priceMinor: Long = 0,
    val costMinor: Long = 0,
    val minQtyMilli: Long = 0,
    val barcode: String = "",
    val category: String = "",
    val isService: Boolean = false,
    val isActive: Boolean = true,
)

@Entity(tableName = "stock", primaryKeys = ["companyId", "productId"])
data class StockEntity(
    val companyId: String,
    val productId: String,
    val qtyMilli: Long,
    val valueMinor: Long,
    val avgCostMinor: Long,
)

@Entity(tableName = "stock_movement", indices = [Index("companyId", "productId")])
data class StockMovementEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val productId: String,
    val type: String,
    val qtyMilli: Long,
    val unitCostMinor: Long,
    val sourceType: String,
    val sourceId: String,
    val date: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "audit_log")
data class AuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val companyId: String,
    val entity: String,
    val entityId: String,
    val action: String,
    val detail: String,
    val actorId: String? = null,
    val occurredAt: Long = System.currentTimeMillis(),
)

/** صفوف مزامنة للعمليات التي تمت بلا اتصال */
@Entity(tableName = "sync_outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val companyId: String,
    val entity: String,
    val op: String,
    val payloadJson: String,
    val sentAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

// ── الأطراف (عملاء وموردون) ─────────────────────────────────────
@Entity(tableName = "party", indices = [Index("companyId", "kind"), Index("companyId", "phone")])
data class PartyEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val kind: String,                 // CUSTOMER | SUPPLIER
    val name: String,
    val phone: String = "",
    val address: String = "",
    val taxNumber: String = "",
    val creditLimitMinor: Long = 0,
    val openingBalanceMinor: Long = 0,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

// ── المستندات (فاتورة ببنود متعددة) ──────────────────────────────
@Entity(
    tableName = "document",
    indices = [Index("companyId", "number", unique = true), Index("companyId", "partyId"), Index("companyId", "kind")],
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val kind: String,                 // INVOICE | RETURN | PURCHASE | PURCHASE_RETURN | QUOTE
    val number: String,
    val partyId: String? = null,
    val date: String,
    val currency: String,
    val status: String = "CONFIRMED", // DRAFT | CONFIRMED | POSTED | CANCELLED
    val invoiceDiscountPct: String = "0",
    val grossMinor: Long = 0,
    val discountMinor: Long = 0,
    val netMinor: Long = 0,
    val taxMinor: Long = 0,
    val roundingMinor: Long = 0,
    val totalMinor: Long = 0,
    val paidMinor: Long = 0,
    val entryId: String? = null,
    val note: String = "",
    val createdBy: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "document_line", indices = [Index("documentId"), Index("companyId", "productId")])
data class DocumentLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: String,
    val companyId: String,
    val lineNo: Int,
    val productId: String,
    val name: String,
    val qtyMilli: Long,
    val unitPriceMinor: Long,
    val discountPct: String = "0",
    val taxRatePct: String = "0",
    val netMinor: Long = 0,
    val taxMinor: Long = 0,
    val totalMinor: Long = 0,
    val cogsMinor: Long = 0,
)

// ── المستخدمون ومصادقة PIN ───────────────────────────────────────
@Entity(tableName = "app_user", indices = [Index("companyId", "phone", unique = true)])
data class UserEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val phone: String,
    val name: String,
    val role: String = "OWNER",
    val pinSaltB64: String,
    val pinHashB64: String,
    val pinIterations: Int = 120_000,
    val isActive: Boolean = true,
    val failedAttempts: Int = 0,
    val lockedUntil: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "app_setting")
data class SettingEntity(
    @PrimaryKey @ColumnInfo(name = "settingKey") val key: String,
    val value: String,
)

// ── الشيكات ─────────────────────────────────────────────────────
@Entity(tableName = "cheque", indices = [Index("companyId", "dueDate"), Index("companyId", "partyId")])
data class ChequeEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val partyId: String? = null,
    val direction: String,            // IN | OUT
    val number: String,
    val bank: String = "",
    val amountMinor: Long,
    val dueDate: String,
    val status: String = "RECEIVED",  // RECEIVED | DEPOSITED | CLEARED | BOUNCED
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

// ── الأصول الثابتة ──────────────────────────────────────────────
@Entity(tableName = "asset", indices = [Index("companyId", "isActive")])
data class AssetEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val name: String,
    val costMinor: Long,
    val salvageMinor: Long = 0,
    val months: Int,
    val startPeriod: String,          // "2026-01"
    val accumulatedMinor: Long = 0,
    val lastDepreciatedPeriod: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val bookValueMinor: Long get() = costMinor - accumulatedMinor
}

// ── الموظفون ───────────────────────────────────────────────────
@Entity(tableName = "employee", indices = [Index("companyId", "isActive")])
data class EmployeeEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val name: String,
    val phone: String = "",
    val basicMinor: Long,
    val allowancesMinor: Long = 0,
    val deductionsMinor: Long = 0,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

// ── المستودعات ومستويات المخزون ─────────────────────────────────
@Entity(tableName = "warehouse", indices = [Index("companyId", "isDefault")])
data class WarehouseEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val name: String,
    val isDefault: Boolean = false,
    val isActive: Boolean = true,
)

@Entity(tableName = "stock_level", primaryKeys = ["companyId", "warehouseId", "productId"])
data class StockLevelEntity(
    val companyId: String,
    val warehouseId: String,
    val productId: String,
    val qtyMilli: Long = 0,
    val valueMinor: Long = 0,
)

// ── الدفعات وتواريخ الصلاحية ───────────────────────────────────
@Entity(tableName = "batch", indices = [Index("companyId", "productId"), Index("companyId", "expiry")])
data class BatchEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val productId: String,
    val warehouseId: String = "main",
    val qtyMilli: Long,
    val unitCostMinor: Long,
    val expiry: String? = null,
    val receivedDate: String,
)

// ── وردية الكاشير ──────────────────────────────────────────────
@Entity(tableName = "shift", indices = [Index("companyId", "openedAt")])
data class ShiftEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val openedAt: String,
    val closedAt: String? = null,
    val openingCashMinor: Long = 0,
    val countedCashMinor: Long? = null,
    val invoices: Int = 0,
    val cashSalesMinor: Long = 0,
    val cardSalesMinor: Long = 0,
    val creditSalesMinor: Long = 0,
    val refundsMinor: Long = 0,
)

// ── فواتير معلّقة (Hold) ───────────────────────────────────────
@Entity(tableName = "held_invoice", indices = [Index("companyId", "createdAt")])
data class HeldInvoiceEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val label: String,
    val createdAt: String,
    val partyId: String? = null,
    val payload: String,          // بنود مفصولة بـ | بصيغة بسيطة
    val invoiceDiscountPct: String = "0",
)

// ── طاولات المطعم ──────────────────────────────────────────────
@Entity(tableName = "dining_table", indices = [Index("companyId", "status")])
data class TableEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val name: String,
    val seats: Int = 4,
    val status: String = "FREE",
)

// ── كشف البنك (للتسوية) ────────────────────────────────────────
@Entity(tableName = "bank_statement", indices = [Index("companyId", "date")])
data class BankStatementLineEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val date: String,
    val description: String,
    val amountMinor: Long,          // موجب = إيداع · سالب = سحب
    val reference: String = "",
    val matchedEntryId: String? = null,
)

// ── الفواتير الدورية (إيجار/اشتراك/عقد صيانة) ──────────────────
@Entity(tableName = "doc_schedule", indices = [Index("companyId", "active")])
data class ScheduleEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val partyId: String? = null,
    val title: String,
    val kind: String = "INV",          // INV مبيعات · PINV مشتريات
    val cycle: String = "MONTHLY",     // DAILY|WEEKLY|MONTHLY|QUARTERLY|YEARLY
    val startDate: String,
    val endDate: String? = null,
    val dayOfMonth: Int? = null,
    val productId: String,
    val productName: String,
    val qtyMilli: Long = 1000,
    val amountMinor: Long,
    val taxRatePct: String = "15",
    val lastRunDate: String? = null,
    val active: Boolean = true,
)
