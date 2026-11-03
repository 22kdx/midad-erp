package com.example.midad.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.midad.MidadApp
import com.example.midad.core.AccountRef
import com.example.midad.core.Analytics
import com.example.midad.core.Batch
import com.example.midad.core.Batches
import com.example.midad.core.Contracting
import com.example.midad.core.GroupAgg
import com.example.midad.core.HeldInvoice
import com.example.midad.core.Holds
import com.example.midad.core.Recurring
import com.example.midad.core.Restaurant
import com.example.midad.core.SaleFact
import com.example.midad.core.Shift
import com.example.midad.core.Shifts
import com.example.midad.core.StockLevel
import com.example.midad.core.TaxReturn
import com.example.midad.core.VatBox
import com.example.midad.core.Warehouse
import com.example.midad.core.Warehouses
import com.example.midad.core.AccountingException
import com.example.midad.core.Backup
import com.example.midad.core.Banking
import com.example.midad.core.Chart
import com.example.midad.core.DataPort
import com.example.midad.core.DataTable
import com.example.midad.core.DocKind
import com.example.midad.core.DocLine
import com.example.midad.core.DocStatus
import com.example.midad.core.DueWatch
import com.example.midad.core.ErpStore
import com.example.midad.core.FixedAssets
import com.example.midad.core.ItemRow
import com.example.midad.core.LedgerRow
import com.example.midad.core.PartyDoc
import com.example.midad.core.PartyLedger
import com.example.midad.core.Payroll
import com.example.midad.core.JournalLine
import com.example.midad.core.JournalRequest
import com.example.midad.core.Money
import com.example.midad.core.netBase
import com.example.midad.core.NormLine
import com.example.midad.core.Parties
import com.example.midad.core.PaymentSplit
import com.example.midad.core.Pin
import com.example.midad.core.PostedEntry
import com.example.midad.core.PostingService
import com.example.midad.core.PrintCompany
import com.example.midad.core.PrintDoc
import com.example.midad.core.Printing
import com.example.midad.core.Rbac
import com.example.midad.core.Reorder
import com.example.midad.core.Reports
import com.example.midad.core.Role
import com.example.midad.core.Statements
import com.example.midad.core.Tafqeet
import com.example.midad.data.local.AccountEntity
import com.example.midad.data.local.AuditEntity
import com.example.midad.data.local.AssetEntity
import com.example.midad.data.local.BankStatementLineEntity
import com.example.midad.data.local.BatchEntity
import com.example.midad.data.local.HeldInvoiceEntity
import com.example.midad.data.local.ShiftEntity
import com.example.midad.data.local.StockLevelEntity
import com.example.midad.data.local.TableEntity
import com.example.midad.data.local.WarehouseEntity
import com.example.midad.data.local.EmployeeEntity
import com.example.midad.data.local.ChequeEntity
import com.example.midad.data.local.CompanyEntity
import com.example.midad.data.local.DocumentEntity
import com.example.midad.data.local.DocumentLineEntity
import com.example.midad.data.local.JournalEntryEntity
import com.example.midad.data.local.JournalLineEntity
import com.example.midad.data.local.PartyEntity
import com.example.midad.data.local.PeriodEntity
import com.example.midad.data.local.ProductEntity
import com.example.midad.data.local.ScheduleEntity
import com.example.midad.data.local.SettingEntity
import com.example.midad.data.local.StockEntity
import com.example.midad.data.local.StockMovementEntity
import com.example.midad.data.local.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID

// ── نماذج العرض ─────────────────────────────────────────────────
data class StockRow(val id: String, val name: String, val qty: Double, val avgCost: String, val value: String, val low: Boolean = false)

data class EntryRow(val id: String, val number: String, val date: String, val description: String, val amount: String, val status: String)

data class BalanceRowUi(val code: String, val name: String, val debit: String, val credit: String)

data class PartyRow(
    val id: String,
    val name: String,
    val kindLabel: String,
    val phone: String,
    val balance: String,
    val creditLimit: String,
    val overLimit: Boolean,
)

data class DocRowUi(
    val id: String,
    val number: String,
    val title: String,
    val partyName: String,
    val date: String,
    val total: String,
    val status: String,
    val paid: String,
)

data class AlertUi(val text: String, val severity: String)   // OUT | LOW | DUE | INFO

data class AssetRow(
    val id: String,
    val name: String,
    val cost: String,
    val accumulated: String,
    val book: String,
    val months: Int,
    val startPeriod: String,
    val lastPeriod: String?,
)

data class EmployeeRow(
    val id: String,
    val name: String,
    val basic: Long,
    val allowances: Long,
    val deductions: Long,
    val net: Long,
)

data class WarehouseRow(
    val id: String,
    val name: String,
    val isDefault: Boolean,
    val totalQty: String,
    val totalValue: String,
)

data class BatchRow(
    val id: String,
    val productName: String,
    val qty: String,
    val expiry: String,
    val status: String,       // EXPIRED | SOON | OK
    val value: String,
)

data class BankLineRow(
    val id: String,
    val date: String,
    val description: String,
    val amount: String,
    val matched: Boolean,
    val matchedTo: String = "",
)

data class ScheduleRow(
    val id: String,
    val title: String,
    val partyName: String,
    val kindLabel: String,
    val cycleLabel: String,
    val nextDate: String,
    val dueCount: Int,
    val amount: String,
    val active: Boolean,
)

data class HeldRow(val id: String, val label: String, val createdAt: String, val total: String, val items: Int)

data class TableRow(val id: String, val name: String, val seats: Int, val status: String)

data class UserRow(val id: String, val name: String, val phone: String, val roleLabel: String, val isActive: Boolean)

data class AuditRowUi(val entity: String, val action: String, val detail: String, val at: String)

data class ChequeRow(
    val id: String,
    val number: String,
    val amount: String,
    val dueDate: String,
    val status: String,
    val incoming: Boolean,
)

data class PickItem(
    val id: String,
    val name: String,
    val price: String,
    val available: Double,
    val unit: String,
    val sku: String = "",
    val barcode: String = "",
)

/** بند يُدخله المستخدم في شاشة الفاتورة */
data class LineInput(
    val productId: String,
    val name: String,
    val qty: Double,
    val price: String,
    val discountPct: String = "0",
    val taxPct: String = "0",
)

data class UiState(
    val ready: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    // لوحة
    val cash: String = "0",
    val inventory: String = "0",
    val salesToday: String = "0",
    val netProfit: String = "0",
    val arBalance: String = "0",
    val apBalance: String = "0",
    // التقارير
    val trialRows: List<BalanceRowUi> = emptyList(),
    val totalDebit: String = "0",
    val totalCredit: String = "0",
    val balanced: Boolean = true,
    val incomeNetRevenue: String = "0",
    val incomeCogs: String = "0",
    val incomeGross: String = "0",
    val incomeNet: String = "0",
    val taxPayable: String = "0",
    val aging30: String = "0",
    val aging60: String = "0",
    val aging90: String = "0",
    // قوائم
    val entries: List<EntryRow> = emptyList(),
    val stocks: List<StockRow> = emptyList(),
    val products: List<ProductEntity> = emptyList(),
    val items: List<PickItem> = emptyList(),
    val parties: List<PartyRow> = emptyList(),
    val documents: List<DocRowUi> = emptyList(),
    val alerts: List<AlertUi> = emptyList(),
    val cheques: List<ChequeRow> = emptyList(),
    // الميزانية العمومية والتدفق النقدي
    val bsAssets: String = "0",
    val bsLiabilities: String = "0",
    val bsEquity: String = "0",
    val bsRetained: String = "0",
    val bsBalanced: Boolean = true,
    val cfOpening: String = "0",
    val cfOperating: String = "0",
    val cfInvesting: String = "0",
    val cfFinancing: String = "0",
    val cfClosing: String = "0",
    val cfVerified: Boolean = true,
    // كشف حساب طرف
    val statementPartyId: String? = null,
    val statementPartyName: String = "",
    val statement: List<LedgerRow> = emptyList(),
    val statementBalance: String = "0",
    // الأصول والموظفون
    val assets: List<AssetRow> = emptyList(),
    val employees: List<EmployeeRow> = emptyList(),
    val payrollGross: String = "0",
    val payrollNet: String = "0",
    // المستودعات والدفعات
    val warehouses: List<WarehouseRow> = emptyList(),
    val batches: List<BatchRow> = emptyList(),
    val atRisk: String = "0",
    // الوردية والتعليق
    val shiftOpen: Boolean = false,
    val shiftLabel: String = "",
    val shiftExpectedCash: String = "0",
    val shiftVariance: String? = null,
    val held: List<HeldRow> = emptyList(),
    val resumeLines: List<LineInput>? = null,
    // الطاولات
    val tables: List<TableRow> = emptyList(),
    val users: List<UserRow> = emptyList(),
    val audit: List<AuditRowUi> = emptyList(),
    // الإقرار الضريبي والتحليلات
    val vatBoxes: List<VatBox> = emptyList(),
    val vatPayable: String = "0",
    val vatIsCredit: Boolean = false,
    val topProducts: List<GroupAgg> = emptyList(),
    val salesByParty: List<GroupAgg> = emptyList(),
    val salesByMonth: List<GroupAgg> = emptyList(),
    val analyticsNet: String = "0",
    val analyticsProfit: String = "0",
    val analyticsDocs: Int = 0,
    // الإعدادات والهوية
    val companyName: String = "مؤسستي",
    val companyTax: String = "",
    val currency: String = "YER",
    val userName: String = "",
    val userPhone: String = "",
    val role: Role = Role.OWNER,
    val pinConfigured: Boolean = false,
    val locked: Boolean = true,
    val paper: String = "A4",
    // مخرجات عابرة
    val printHtml: String? = null,
    val printTitle: String = "",
    val backupText: String? = null,
    val lastBackupAt: String? = null,
    // التسوية البنكية
    val bankLines: List<BankLineRow> = emptyList(),
    val bankMatched: Int = 0,
    val bankUnmatched: Int = 0,
    val bankDifference: String = "0",
    val bankBalanced: Boolean = false,
    val bankHints: List<String> = emptyList(),
    // الفواتير الدورية
    val schedules: List<ScheduleRow> = emptyList(),
    val schedulesDue: Int = 0,
    val schedulesDueValue: String = "0",
)

class MidadViewModel(app: Application) : AndroidViewModel(app) {

    private val application = app as MidadApp
    private val dao = application.db.erpDao()
    private val store: ErpStore = application.store
    private val posting: PostingService = application.posting

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val company = MidadApp.COMPANY_ID
    private val currency = "YER"
    private val OWNER_PHONE = "700000000"
    private var statementPartyId: String? = null
    private var lastDocId: String = ""

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { bootstrap() } }
            refresh()
        }
    }

    // ── التهيئة الأولى ────────────────────────────────────────────
    private fun bootstrap() {
        if (dao.company(company) == null) {
            dao.upsertCompany(
                CompanyEntity(
                    id = company,
                    name = "مؤسستي",
                    functionalCurrency = currency,
                    currenciesCsv = "$currency,USD,SAR",
                    taxNumber = null,
                ),
            )
        }
        if (dao.accounts(company).isEmpty()) {
            dao.upsertAccounts(
                Chart.YEMEN.map {
                    AccountEntity(
                        id = "acc-" + it.code,
                        companyId = company,
                        code = it.code,
                        name = it.name,
                        type = it.type.name,
                        parentCode = it.parentCode,
                        isGroup = it.isGroup,
                        isActive = true,
                    )
                },
            )
        }
        val year = LocalDate.now().year
        dao.upsertPeriods(
            (1..12).map {
                PeriodEntity(id = "$company-$year-$it", companyId = company, year = year, month = it, status = "OPEN")
            },
        )
        if (dao.products(company).isEmpty()) {
            val demo = listOf(
                ProductEntity("p1", company, "أرز بسمتي ٥ كجم", "SKU-001", "كيس", Money.parse("12000"), minQtyMilli = 10_000),
                ProductEntity("p2", company, "زيت طبخ ١.٨ لتر", "SKU-002", "عبوة", Money.parse("8500"), minQtyMilli = 8_000),
                ProductEntity("p3", company, "سكر أبيض ١٠ كجم", "SKU-003", "كيس", Money.parse("9500"), minQtyMilli = 8_000),
            )
            dao.upsertProducts(demo)
        }
        // مخزون افتتاحي مرة واحدة
        if (dao.entries(company, null, null).isEmpty()) {
            posting.receiveOpeningStock(company, today(), "OPEN-1", "p1", 50_000, Money.parse("9000"))
            posting.receiveOpeningStock(company, today(), "OPEN-2", "p2", 30_000, Money.parse("6500"))
            posting.receiveOpeningStock(company, today(), "OPEN-3", "p3", 40_000, Money.parse("7000"))
        }
        if (dao.warehouses(company).isEmpty()) {
            dao.upsertWarehouse(WarehouseEntity("wh-main", company, "المستودع الرئيسي", isDefault = true))
            dao.upsertWarehouse(WarehouseEntity("wh-2", company, "المستودع الفرعي", isDefault = false))
        }
        if (dao.parties(company).isEmpty()) {
            dao.upsertParties(
                listOf(
                    PartyEntity("c1", company, Parties.Kind.CUSTOMER.name, "مؤسسة النور", phone = "771234567", creditLimitMinor = Money.parse("100000")),
                    PartyEntity("c2", company, Parties.Kind.CUSTOMER.name, "بقالة الأمانة", phone = "773334444"),
                    PartyEntity("s1", company, Parties.Kind.SUPPLIER.name, "شركة الأغذية المتحدة", phone = "775556666"),
                ),
            )
        }
    }

    // ── المبيعات والمشتريات (مستندات متعددة البنود) ───────────────
    fun saveDocument(
        kind: DocKind,
        partyId: String?,
        lines: List<LineInput>,
        invoiceDiscountPct: String,
        paid: String,
        note: String = "",
        currencyCode: String? = null,
        fxRate: String = "1",
        onSaved: (String) -> Unit = {},
        onDone: () -> Unit = {},
    ) {
        if (lines.isEmpty()) {
            _state.value = _state.value.copy(error = "أضف بنداً واحداً على الأقل")
            return
        }
        val neededPerm = if (kind.isSales) com.example.midad.core.Perm.SALE_EDIT
            else com.example.midad.core.Perm.PURCHASE_EDIT
        if (!Rbac.allows(_state.value.role, neededPerm)) {
            _state.value = _state.value.copy(error = "لا تملك صلاحية إنشاء المبيعات")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            try {
                withContext(Dispatchers.IO) {
                    val docCur = currencyCode ?: currency
                    val payments = if (paid.isBlank()) 0L else Money.parse(paid, docCur)
                    val inputs = lines.map {
                        PostingService.DocInput(
                            productId = it.productId,
                            name = it.name,
                            qtyMilli = (it.qty * 1000).toLong(),
                            unitPriceMinor = Money.parse(it.price.ifBlank { "0" }, docCur),
                            discountPct = it.discountPct.ifBlank { "0" },
                            taxRatePct = it.taxPct.ifBlank { "0" },
                        )
                    }
                    val docId = UUID.randomUUID().toString()
                    lastDocId = docId
                    val number = dao.nextNumber(company, kind.code, today().take(4))

                    val result: PostingService.DocResult = when (kind) {
                        DocKind.INVOICE -> posting.recordInvoiceDoc(
                            company, today(), docId, inputs, invoiceDiscountPct, 0L,
                            cashMinor = payments, currency = currencyCode, fxRate = fxRate,
                        )
                        DocKind.RETURN -> posting.recordReturnDoc(
                            company, today(), docId, inputs, invoiceDiscountPct,
                            refundCashMinor = payments, currency = currencyCode, fxRate = fxRate,
                        )
                        DocKind.PURCHASE -> posting.recordPurchaseDoc(
                            company, today(), docId, inputs, invoiceDiscountPct, 0L,
                            cashPaidMinor = payments, currency = currencyCode, fxRate = fxRate,
                        )
                        DocKind.PURCHASE_RETURN -> posting.recordPurchaseReturnDoc(
                            company, today(), docId, inputs, invoiceDiscountPct,
                            refundCashMinor = payments, currency = currencyCode, fxRate = fxRate,
                        )
                        else -> throw AccountingException("NOT_POSTABLE", "هذا النوع لا يُرحَّل — احفظه كعرض فقط")
                    }

                    val t = result.totals
                    dao.insertDocument(
                        DocumentEntity(
                            id = docId,
                            companyId = company,
                            kind = kind.code,
                            number = number,
                            partyId = partyId,
                            date = today(),
                            currency = docCur,
                            status = DocStatus.POSTED.code,
                            invoiceDiscountPct = invoiceDiscountPct.ifBlank { "0" },
                            grossMinor = t.gross,
                            discountMinor = t.discount,
                            netMinor = t.net,
                            taxMinor = t.tax,
                            roundingMinor = t.rounding,
                            totalMinor = t.total,
                            paidMinor = payments,
                            entryId = result.entry.id,
                            note = note,
                            createdBy = _state.value.userName,
                        ),
                    )
                    dao.insertDocLines(
                        t.lines.mapIndexed { i, lt ->
                            DocumentLineEntity(
                                documentId = docId,
                                companyId = company,
                                lineNo = i + 1,
                                productId = lt.line.productId,
                                name = lt.line.name,
                                qtyMilli = lt.line.qtyMilli,
                                unitPriceMinor = lt.line.unitPriceMinor,
                                discountPct = lt.line.discountPct,
                                taxRatePct = lt.line.taxRatePct,
                                netMinor = lt.net,
                                taxMinor = lt.tax,
                                totalMinor = lt.total,
                                cogsMinor = Money.mulDiv(lt.line.unitCostMinor, lt.line.qtyMilli, 1000L),
                            )
                        },
                    )
                }
                onSaved(lastDocId)
                onDone()
                refresh()
                _state.value = _state.value.copy(busy = false, message = "تم حفظ المستند وترحيله بنجاح")
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, error = e.message ?: "تعذّر حفظ المستند")
            }
        }
    }

    /** عرض سعر: يُحفظ كمسودة بلا قيد ولا مخزون، ويُحوَّل لاحقاً إلى فاتورة */
    fun saveQuote(partyId: String?, lines: List<LineInput>, invoiceDiscountPct: String, note: String, onDone: () -> Unit = {}) {
        if (lines.isEmpty()) {
            _state.value = _state.value.copy(error = "أضف بنداً واحداً على الأقل")
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val docId = UUID.randomUUID().toString()
                    val number = dao.nextNumber(company, DocKind.QUOTE.code, today().take(4))
                    val totals = com.example.midad.core.DocEngine.totals(
                        lines.map {
                            DocLine(
                                it.productId, it.name, (it.qty * 1000).toLong(),
                                Money.parse(it.price.ifBlank { "0" }, currency), it.discountPct, it.taxPct, 0L,
                            )
                        },
                        invoiceDiscountPct.ifBlank { "0" },
                    )
                    dao.insertDocument(
                        DocumentEntity(
                            id = docId, companyId = company, kind = DocKind.QUOTE.code, number = number,
                            partyId = partyId, date = today(), currency = currency,
                            status = DocStatus.DRAFT.code,
                            invoiceDiscountPct = invoiceDiscountPct.ifBlank { "0" },
                            grossMinor = totals.gross, discountMinor = totals.discount, netMinor = totals.net,
                            taxMinor = totals.tax, roundingMinor = totals.rounding, totalMinor = totals.total,
                            note = note, createdBy = _state.value.userName,
                        ),
                    )
                    dao.insertDocLines(
                        totals.lines.mapIndexed { i, lt ->
                            DocumentLineEntity(
                                documentId = docId, companyId = company, lineNo = i + 1,
                                productId = lt.line.productId, name = lt.line.name, qtyMilli = lt.line.qtyMilli,
                                unitPriceMinor = lt.line.unitPriceMinor, discountPct = lt.line.discountPct,
                                taxRatePct = lt.line.taxRatePct, netMinor = lt.net, taxMinor = lt.tax,
                                totalMinor = lt.total,
                            )
                        },
                    )
                }
                onDone()
                refresh()
                _state.value = _state.value.copy(message = "تم حفظ عرض السعر")
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "تعذّر حفظ العرض")
            }
        }
    }

    /** تحويل عرض سعر إلى فاتورة مرحلة — يعيد استخدام البنود كما هي */
    fun convertQuote(docId: String) {
        viewModelScope.launch {
            try {
                val lines = withContext(Dispatchers.IO) {
                    dao.docLines(docId).map {
                        LineInput(
                            productId = it.productId, name = it.name,
                            qty = it.qtyMilli / 1000.0,
                            price = Money.format(it.unitPriceMinor, currency),
                            discountPct = it.discountPct, taxPct = it.taxRatePct,
                        )
                    }
                }
                val doc = withContext(Dispatchers.IO) { dao.document(company, docId) }
                saveDocument(
                    kind = DocKind.INVOICE,
                    partyId = doc?.partyId,
                    lines = lines,
                    invoiceDiscountPct = doc?.invoiceDiscountPct ?: "0",
                    paid = "",
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    /** إعادة ترحيل فاتورة = قيد عكسي (لا حذف) */
    fun reverseEntry(entryId: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { posting.reverse(company, entryId, reason = "إلغاء من الشاشة") }
                refresh()
                _state.value = _state.value.copy(message = "تم عكس القيد")
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    // ── الأطراف ──────────────────────────────────────────────────
    fun addParty(name: String, phone: String, isCustomer: Boolean, creditLimit: String) {
        if (name.isBlank()) {
            _state.value = _state.value.copy(error = "اسم الطرف مطلوب")
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.upsertParty(
                    PartyEntity(
                        id = UUID.randomUUID().toString(),
                        companyId = company,
                        kind = if (isCustomer) Parties.Kind.CUSTOMER.name else Parties.Kind.SUPPLIER.name,
                        name = name.trim(),
                        phone = phone.trim(),
                        creditLimitMinor = if (creditLimit.isBlank()) 0L else Money.parse(creditLimit, currency),
                    ),
                )
            }
            refresh()
            _state.value = _state.value.copy(message = "تم حفظ الطرف")
        }
    }

    // ── الأصناف ──────────────────────────────────────────────────
    fun addProduct(name: String, sku: String, unit: String, price: String, cost: String, minQty: String, isService: Boolean) {
        if (name.isBlank()) {
            _state.value = _state.value.copy(error = "اسم الصنف مطلوب")
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.upsertProducts(
                    listOf(
                        ProductEntity(
                            id = UUID.randomUUID().toString(),
                            companyId = company,
                            name = name.trim(),
                            sku = sku.ifBlank { "SKU-" + System.currentTimeMillis().toString().takeLast(6) },
                            unit = unit.ifBlank { "حبة" },
                            priceMinor = if (price.isBlank()) 0L else Money.parse(price, currency),
                            costMinor = if (cost.isBlank()) 0L else Money.parse(cost, currency),
                            minQtyMilli = if (minQty.isBlank()) 0L else (minQty.toDouble() * 1000).toLong(),
                            isService = isService,
                        ),
                    ),
                )
            }
            refresh()
            _state.value = _state.value.copy(message = "تم إضافة الصنف")
        }
    }

    fun stockCount(productId: String, countedQty: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    posting.stockCount(company, today(), "COUNT-" + System.currentTimeMillis(), productId, (countedQty.toDouble() * 1000).toLong())
                }
                refresh()
                _state.value = _state.value.copy(message = "تمت تسوية الجرد")
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    // ── الشيكات ──────────────────────────────────────────────────
    fun addCheque(number: String, amount: String, dueDate: String, incoming: Boolean, partyId: String?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.upsertCheque(
                    ChequeEntity(
                        id = UUID.randomUUID().toString(),
                        companyId = company,
                        partyId = partyId,
                        direction = if (incoming) "IN" else "OUT",
                        number = number,
                        amountMinor = Money.parse(amount.ifBlank { "0" }, currency),
                        dueDate = dueDate,
                        status = "RECEIVED",
                    ),
                )
            }
            refresh()
            _state.value = _state.value.copy(message = "تم تسجيل الشيك")
        }
    }

    /** سند قبض/صرف نقدي أو بنكي — يُرحَّل عبر النواة نفسها */
    fun postVoucher(incoming: Boolean, amount: String, viaBank: Boolean) {
        if (!Rbac.allows(_state.value.role, com.example.midad.core.Perm.PAYMENT_EDIT)) {
            _state.value = _state.value.copy(error = "لا تملك صلاحية السندات")
            return
        }
        val minor = runCatching { Money.parse(amount.ifBlank { "0" }, currency) }.getOrDefault(0L)
        if (minor <= 0L) {
            _state.value = _state.value.copy(error = "المبلغ يجب أن يكون أكبر من صفر")
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val id = "VCH-" + System.currentTimeMillis()
                    if (incoming) posting.receive(company, today(), id, minor, toBank = viaBank)
                    else posting.paySupplier(company, today(), id, minor, fromBank = viaBank)
                }
                refresh()
                _state.value = _state.value.copy(message = if (incoming) "تم ترحيل سند القبض" else "تم ترحيل سند الصرف")
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    /** آلة حالات الشيك + ترحيل أثره المحاسبي */
    fun chequeAction(chequeId: String, action: com.example.midad.core.Cheques.Action) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val row = dao.cheques(company).firstOrNull { it.id == chequeId } ?: return@withContext
                    val current = runCatching { com.example.midad.core.Cheques.Status.valueOf(row.status) }
                        .getOrDefault(com.example.midad.core.Cheques.Status.RECEIVED)
                    val next = com.example.midad.core.Cheques.next(current, action)
                    dao.upsertCheque(row.copy(status = next.name))
                    posting.chequeAction(company, today(), row.number, row.amountMinor, current, action)
                }
                refresh()
                _state.value = _state.value.copy(message = "تم تحديث حالة الشيك")
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    // ── الإقفال ──────────────────────────────────────────────────
    fun closePeriod(year: Int, month: Int) {
        if (!Rbac.allows(_state.value.role, com.example.midad.core.Perm.PERIOD_CLOSE)) {
            _state.value = _state.value.copy(error = "لا تملك صلاحية إقفال الفترة")
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val last = LocalDate.of(year, month, 1).lengthOfMonth()
                    posting.closePeriod(company, year, month, "%d-%02d-%02d".format(year, month, last))
                }
                refresh()
                _state.value = _state.value.copy(message = "تم إقفال الفترة وترحيل النتيجة")
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    // ── الطباعة ──────────────────────────────────────────────────
    fun buildPrint(documentId: String) {
        viewModelScope.launch {
            val html = withContext(Dispatchers.IO) { renderPrint(documentId) }
            _state.value = _state.value.copy(printHtml = html.first, printTitle = html.second)
        }
    }

    fun clearPrint() {
        _state.value = _state.value.copy(printHtml = null, printTitle = "")
    }

    /** رمز الفاتورة الإلكترونية: Base64 لترميز TLV (اسم البائع · الرقم الضريبي · التاريخ · الإجمالي · الضريبة) */
    private fun taxQrPayload(
        sellerName: String,
        vatNumber: String,
        date: String,
        totalMinor: Long,
        taxMinor: Long,
        currency: String,
    ): String = runCatching {
        com.example.midad.core.Compliance.qrBase64(
            com.example.midad.core.Compliance.QrFields(
                sellerName = sellerName,
                vatNumber = vatNumber,
                timestamp = com.example.midad.core.Compliance.qrTimestamp(date),
                totalWithVatMinor = totalMinor,
                vatTotalMinor = taxMinor,
                currency = currency,
            ),
        )
    }.getOrDefault("")

    private fun renderPrint(documentId: String): Pair<String, String> {
        val doc = dao.document(company, documentId)
            ?: return "" to "مستند غير موجود"
        val lines = dao.docLines(documentId)
        val party = doc.partyId?.let { dao.party(company, it) }
        val co = dao.company(company)
        val totals = com.example.midad.core.DocTotals(
            lines = lines.map { l ->
                com.example.midad.core.LineTotals(
                    DocLine(l.productId, l.name, l.qtyMilli, l.unitPriceMinor, l.discountPct, l.taxRatePct, 0L),
                    l.unitPriceMinor * l.qtyMilli / 1000,
                    if (l.discountPct == "0") 0L else (l.unitPriceMinor * l.qtyMilli / 1000) - l.netMinor,
                    l.netMinor,
                    l.taxMinor,
                )
            },
            gross = doc.grossMinor,
            lineDiscount = doc.discountMinor,
            invoiceDiscount = 0L,
            net = doc.netMinor,
            tax = doc.taxMinor,
            rounding = doc.roundingMinor,
            total = doc.totalMinor,
            cogs = 0L,
        )
        val title = when (doc.kind) {
            "INV" -> "فاتورة مبيعات"
            "SR" -> "مردود مبيعات"
            "PINV" -> "فاتورة مشتريات"
            "QTE" -> "عرض سعر"
            else -> "مستند"
        }
        val paper = when (_state.value.paper) {
            "A5" -> com.example.midad.core.Paper.A5
            "THERMAL_80" -> com.example.midad.core.Paper.THERMAL_80
            else -> com.example.midad.core.Paper.A4
        }
        val html = Printing.html(
            PrintDoc(
                title = title,
                number = doc.number,
                date = doc.date,
                partyLabel = if (doc.kind == "PINV" || doc.kind == "PR") "المورد" else "العميل",
                partyName = party?.name ?: "—",
                partyPhone = party?.phone ?: "",
                partyTaxNumber = party?.taxNumber ?: "",
                totals = totals,
                currency = doc.currency,
                paidMinor = doc.paidMinor,
                userName = doc.createdBy ?: _state.value.userName,
                notes = doc.note,
                qrPayload = taxQrPayload(
                    sellerName = co?.name ?: "مؤسستي",
                    vatNumber = co?.taxNumber ?: "",
                    date = doc.date,
                    totalMinor = doc.totalMinor,
                    taxMinor = doc.taxMinor,
                    currency = doc.currency,
                ),
            ),
            PrintCompany(co?.name ?: "مؤسستي", taxNumber = co?.taxNumber ?: ""),
            paper,
        )
        return html to "${title} ${doc.number}"
    }

    // ── النسخ الاحتياطي المشفّر ──────────────────────────────────
    fun exportBackup(password: String) {
        if (password.length < 8) {
            _state.value = _state.value.copy(error = "كلمة مرور النسخة ٨ محارف على الأقل")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            try {
                val blob = withContext(Dispatchers.IO) {
                    val tables = dumpTables()
                    val payload = DataPort.write(tables)
                    val meta = Backup.Meta(
                        appVersion = "1.0.0",
                        createdAt = LocalDate.now().toString(),
                        companyId = company,
                        companyName = dao.company(company)?.name ?: "مؤسستي",
                        counts = DataPort.summary(tables),
                    )
                    Backup.seal(payload, password, meta).also { text ->
                        val dir = File(application.filesDir, "backup").apply { mkdirs() }
                        File(dir, "midad-${System.currentTimeMillis()}.mbk").writeText(text)
                    }
                }
                _state.value = _state.value.copy(
                    busy = false,
                    backupText = blob,
                    lastBackupAt = LocalDate.now().toString(),
                    message = "تم إنشاء نسخة مشفّرة وحفظها على الجهاز",
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, error = e.message ?: "تعذّر إنشاء النسخة")
            }
        }
    }

    fun restoreBackup(blob: String, password: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            try {
                val counts = withContext(Dispatchers.IO) {
                    val (_, payload) = Backup.open(blob.trim(), password)
                    val tables = DataPort.read(payload)
                    applyTables(tables)
                    DataPort.summary(tables)
                }
                refresh()
                _state.value = _state.value.copy(
                    busy = false,
                    message = "تمت الاستعادة: " + counts.entries.joinToString(" · ") { "${it.key}=${it.value}" },
                )
            } catch (e: AccountingException) {
                _state.value = _state.value.copy(busy = false, error = e.message)
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, error = "ملف غير صالح أو كلمة مرور خاطئة")
            }
        }
    }

    private fun dumpTables(): List<DataTable> {
        val entries = dao.allEntries(company)
        val lines = if (entries.isEmpty()) emptyList() else dao.allLines(entries.map { it.id })
        fun s(v: String?) = v ?: ""
        return listOf(
            DataTable("company", listOf("id", "name", "functionalCurrency", "currenciesCsv", "lockDate", "taxNumber"),
                dao.company(company)?.let { listOf(listOf(it.id, it.name, it.functionalCurrency, it.currenciesCsv, s(it.lockDate), s(it.taxNumber))) } ?: emptyList()),
            DataTable("party", listOf("id", "kind", "name", "phone", "address", "taxNumber", "creditLimitMinor", "openingBalanceMinor", "isActive"),
                dao.parties(company).map { listOf(it.id, it.kind, it.name, it.phone, it.address, it.taxNumber, "${it.creditLimitMinor}", "${it.openingBalanceMinor}", if (it.isActive) "1" else "0") }),
            DataTable("product", listOf("id", "name", "sku", "unit", "priceMinor", "costMinor", "minQtyMilli", "barcode", "category", "isService", "isActive"),
                dao.products(company).map { listOf(it.id, it.name, it.sku, it.unit, "${it.priceMinor}", "${it.costMinor}", "${it.minQtyMilli}", it.barcode, it.category, if (it.isService) "1" else "0", if (it.isActive) "1" else "0") }),
            DataTable("stock", listOf("productId", "qtyMilli", "valueMinor", "avgCostMinor"),
                dao.allStocks(company).map { listOf(it.productId, "${it.qtyMilli}", "${it.valueMinor}", "${it.avgCostMinor}") }),
            DataTable("journal_entry", listOf("id", "number", "date", "description", "sourceType", "sourceId", "currency", "totalBase", "status", "reversalOf", "reversedBy", "createdAt"),
                entries.map { listOf(it.id, it.number, it.date, it.description, it.sourceType, it.sourceId, it.currency, "${it.totalBase}", it.status, s(it.reversalOf), s(it.reversedBy), "${it.createdAt}") }),
            DataTable("journal_line", listOf("entryId", "companyId", "accountId", "debitMinor", "creditMinor", "currency", "fxRate", "amountBase", "costCenterId", "note"),
                lines.map { listOf(it.entryId, it.companyId, it.accountId, "${it.debitMinor}", "${it.creditMinor}", it.currency, it.fxRate, "${it.amountBase}", s(it.costCenterId), it.note) }),
            DataTable("document", listOf("id", "kind", "number", "partyId", "date", "currency", "status", "grossMinor", "discountMinor", "netMinor", "taxMinor", "roundingMinor", "totalMinor", "paidMinor", "entryId", "note", "createdAt"),
                dao.documents(company).map { listOf(it.id, it.kind, it.number, s(it.partyId), it.date, it.currency, it.status, "${it.grossMinor}", "${it.discountMinor}", "${it.netMinor}", "${it.taxMinor}", "${it.roundingMinor}", "${it.totalMinor}", "${it.paidMinor}", s(it.entryId), it.note, "${it.createdAt}") }),
            DataTable("document_line", listOf("documentId", "companyId", "lineNo", "productId", "name", "qtyMilli", "unitPriceMinor", "discountPct", "taxRatePct", "netMinor", "taxMinor", "totalMinor"),
                dao.documents(company).flatMap { d -> dao.docLines(d.id) }.map {
                    listOf(it.documentId, it.companyId, "${it.lineNo}", it.productId, it.name, "${it.qtyMilli}", "${it.unitPriceMinor}", it.discountPct, it.taxRatePct, "${it.netMinor}", "${it.taxMinor}", "${it.totalMinor}")
                }),
            DataTable("cheque", listOf("id", "partyId", "direction", "number", "bank", "amountMinor", "dueDate", "status", "note"),
                dao.cheques(company).map { listOf(it.id, s(it.partyId), it.direction, it.number, it.bank, "${it.amountMinor}", it.dueDate, it.status, it.note) }),
        )
    }

    private fun applyTables(tables: List<DataTable>) {
        DataPort.of(tables, "party")?.let { t ->
            dao.upsertParties(t.rows.map {
                PartyEntity(
                    id = t.value(it, "id"), companyId = company,
                    kind = t.value(it, "kind").ifBlank { Parties.Kind.CUSTOMER.name },
                    name = t.value(it, "name"), phone = t.value(it, "phone"),
                    address = t.value(it, "address"), taxNumber = t.value(it, "taxNumber"),
                    creditLimitMinor = t.value(it, "creditLimitMinor").toLongOrNull() ?: 0L,
                    openingBalanceMinor = t.value(it, "openingBalanceMinor").toLongOrNull() ?: 0L,
                    isActive = t.value(it, "isActive") != "0",
                )
            })
        }
        DataPort.of(tables, "product")?.let { t ->
            dao.upsertProducts(t.rows.map {
                ProductEntity(
                    id = t.value(it, "id"), companyId = company, name = t.value(it, "name"),
                    sku = t.value(it, "sku"), unit = t.value(it, "unit").ifBlank { "حبة" },
                    priceMinor = t.value(it, "priceMinor").toLongOrNull() ?: 0L,
                    costMinor = t.value(it, "costMinor").toLongOrNull() ?: 0L,
                    minQtyMilli = t.value(it, "minQtyMilli").toLongOrNull() ?: 0L,
                    barcode = t.value(it, "barcode"), category = t.value(it, "category"),
                    isService = t.value(it, "isService") == "1", isActive = t.value(it, "isActive") != "0",
                )
            })
        }
        DataPort.of(tables, "stock")?.let { t ->
            t.rows.forEach {
                dao.upsertStock(
                    StockEntity(
                        companyId = company, productId = t.value(it, "productId"),
                        qtyMilli = t.value(it, "qtyMilli").toLongOrNull() ?: 0L,
                        valueMinor = t.value(it, "valueMinor").toLongOrNull() ?: 0L,
                        avgCostMinor = t.value(it, "avgCostMinor").toLongOrNull() ?: 0L,
                    ),
                )
            }
        }
        DataPort.of(tables, "journal_entry")?.let { t ->
            dao.insertEntryRows(
                t.rows.map {
                    JournalEntryEntity(
                        id = t.value(it, "id"), companyId = company, number = t.value(it, "number"),
                        date = t.value(it, "date"), description = t.value(it, "description"),
                        sourceType = t.value(it, "sourceType"), sourceId = t.value(it, "sourceId"),
                        currency = t.value(it, "currency"),
                        totalBase = t.value(it, "totalBase").toLongOrNull() ?: 0L,
                        status = t.value(it, "status").ifBlank { "POSTED" },
                        reversalOf = t.value(it, "reversalOf").ifBlank { null },
                        reversedBy = t.value(it, "reversedBy").ifBlank { null },
                    )
                },
            )
        }
        DataPort.of(tables, "journal_line")?.let { t ->
            dao.insertLines(
                t.rows.map {
                    JournalLineEntity(
                        entryId = t.value(it, "entryId"), companyId = company,
                        accountId = t.value(it, "accountId"),
                        debitMinor = t.value(it, "debitMinor").toLongOrNull() ?: 0L,
                        creditMinor = t.value(it, "creditMinor").toLongOrNull() ?: 0L,
                        currency = t.value(it, "currency").ifBlank { currency },
                        fxRate = t.value(it, "fxRate").ifBlank { "1" },
                        amountBase = t.value(it, "amountBase").toLongOrNull() ?: 0L,
                        costCenterId = t.value(it, "costCenterId").ifBlank { null },
                        note = t.value(it, "note"),
                    )
                },
            )
        }
        DataPort.of(tables, "document")?.let { t ->
            t.rows.forEach {
                dao.insertDocument(
                    DocumentEntity(
                        id = t.value(it, "id"), companyId = company, kind = t.value(it, "kind"),
                        number = t.value(it, "number"), partyId = t.value(it, "partyId").ifBlank { null },
                        date = t.value(it, "date"), currency = t.value(it, "currency").ifBlank { currency },
                        status = t.value(it, "status").ifBlank { "POSTED" },
                        grossMinor = t.value(it, "grossMinor").toLongOrNull() ?: 0L,
                        discountMinor = t.value(it, "discountMinor").toLongOrNull() ?: 0L,
                        netMinor = t.value(it, "netMinor").toLongOrNull() ?: 0L,
                        taxMinor = t.value(it, "taxMinor").toLongOrNull() ?: 0L,
                        roundingMinor = t.value(it, "roundingMinor").toLongOrNull() ?: 0L,
                        totalMinor = t.value(it, "totalMinor").toLongOrNull() ?: 0L,
                        paidMinor = t.value(it, "paidMinor").toLongOrNull() ?: 0L,
                        entryId = t.value(it, "entryId").ifBlank { null },
                        note = t.value(it, "note"),
                    ),
                )
            }
        }
        DataPort.of(tables, "document_line")?.let { t ->
            dao.insertDocLines(
                t.rows.map {
                    DocumentLineEntity(
                        documentId = t.value(it, "documentId"), companyId = company,
                        lineNo = t.value(it, "lineNo").toIntOrNull() ?: 1,
                        productId = t.value(it, "productId"), name = t.value(it, "name"),
                        qtyMilli = t.value(it, "qtyMilli").toLongOrNull() ?: 0L,
                        unitPriceMinor = t.value(it, "unitPriceMinor").toLongOrNull() ?: 0L,
                        discountPct = t.value(it, "discountPct"), taxRatePct = t.value(it, "taxRatePct"),
                        netMinor = t.value(it, "netMinor").toLongOrNull() ?: 0L,
                        taxMinor = t.value(it, "taxMinor").toLongOrNull() ?: 0L,
                        totalMinor = t.value(it, "totalMinor").toLongOrNull() ?: 0L,
                    )
                },
            )
        }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null, error = null)
    }

    // ── الدخول و PIN ─────────────────────────────────────────────
    fun setupPin(pin: String, name: String, phone: String) {
        if (!Pin.isValidFormat(pin)) {
            _state.value = _state.value.copy(error = "الرمز ٤ إلى ٦ أرقام")
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val h = Pin.hash(pin, iterations = 20_000)
                dao.upsertUser(
                    UserEntity(
                        id = "owner",
                        companyId = company,
                        phone = phone.ifBlank { OWNER_PHONE },
                        name = name.ifBlank { "المالك" },
                        role = Role.OWNER.name,
                        pinSaltB64 = h.saltB64,
                        pinHashB64 = h.hashB64,
                        pinIterations = h.iterations,
                    ),
                )
                dao.upsertSetting(SettingEntity("pin_set", "1"))
            }
            refresh()
        }
    }

    fun unlock(pin: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val user = dao.users(company).firstOrNull() ?: return@withContext false
                if (user.lockedUntil != null && System.currentTimeMillis() < user.lockedUntil!!) return@withContext false
                val h = com.example.midad.core.PinHash(user.pinSaltB64, user.pinHashB64, user.pinIterations)
                val good = Pin.verify(pin, h)
                if (!good) {
                    val fails = user.failedAttempts + 1
                    dao.updateUserLock(user.id, fails, if (fails >= 5) System.currentTimeMillis() + 15 * 60_000L else null)
                } else {
                    dao.updateUserLock(user.id, 0, null)
                }
                good
            }
            if (ok) {
                refresh()
                _state.value = _state.value.copy(locked = false)
            } else {
                _state.value = _state.value.copy(error = "رمز غير صحيح")
            }
        }
    }

    fun lockNow() {
        _state.value = _state.value.copy(locked = true)
    }

    fun setPaper(paper: String) {
        _state.value = _state.value.copy(paper = paper)
        viewModelScope.launch { withContext(Dispatchers.IO) { dao.upsertSetting(SettingEntity("paper", paper)) } }
    }

    fun saveCompany(name: String, taxNumber: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val co = dao.company(company) ?: return@withContext
                dao.updateCompany(co.copy(name = name.ifBlank { co.name }, taxNumber = taxNumber.ifBlank { null }))
            }
            refresh()
            _state.value = _state.value.copy(message = "تم حفظ بيانات المنشأة")
        }
    }

    // ── كشف حساب وتحصيل ─────────────────────────────────────────
    fun openStatement(partyId: String) {
        statementPartyId = partyId
        refresh()
    }

    fun closeStatement() {
        statementPartyId = null
        refresh()
    }

    private fun partyDocs(partyId: String): List<PartyDoc> =
        dao.documents(company).filter { it.partyId == partyId }.map {
            PartyDoc(
                id = it.id,
                number = it.number,
                date = it.date,
                kind = when (it.kind) {
                    "SR" -> PartyDoc.KIND_RETURN
                    "PR" -> PartyDoc.KIND_RETURN
                    else -> PartyDoc.KIND_INVOICE
                },
                totalMinor = it.totalMinor,
                paidMinor = it.paidMinor,
                note = it.note,
            )
        }

    /** تحصيل من عميل: توزيع FIFO على الفواتير الأقدم + ترحيل سند قبض */
    fun collectFrom(partyId: String, amount: String, viaBank: Boolean) {
        if (!Rbac.allows(_state.value.role, com.example.midad.core.Perm.PAYMENT_EDIT)) {
            _state.value = _state.value.copy(error = "لا تملك صلاحية التحصيل")
            return
        }
        val minor = runCatching { Money.parse(amount.ifBlank { "0" }, currency) }.getOrDefault(0L)
        if (minor <= 0L) {
            _state.value = _state.value.copy(error = "المبلغ يجب أن يكون أكبر من صفر")
            return
        }
        viewModelScope.launch {
            try {
                val message = withContext(Dispatchers.IO) {
                    val docs = partyDocs(partyId)
                    val plan = PartyLedger.planCollection(minor, docs)
                    PartyLedger.applyCollection(docs, plan).forEach { (docId, paid) ->
                        dao.updateDocPaid(docId, paid)
                    }
                    posting.receive(company, today(), "RCPT-" + System.currentTimeMillis(), minor, toBank = viaBank)
                    if (plan.advanceMinor > 0L)
                        "تم التحصيل · ${plan.allocations.size} فاتورة · دفعة مقدمة ${Money.format(plan.advanceMinor, currency)}"
                    else "تم التحصيل على ${plan.allocations.size} فاتورة"
                }
                refresh()
                _state.value = _state.value.copy(message = message)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    // ── الأصول الثابتة ───────────────────────────────────────────
    fun addAsset(name: String, cost: String, salvage: String, months: String, startPeriod: String) {
        if (name.isBlank()) {
            _state.value = _state.value.copy(error = "اسم الأصل مطلوب")
            return
        }
        val m = months.toIntOrNull() ?: 0
        if (m <= 0) {
            _state.value = _state.value.copy(error = "العمر بالأشهر يجب أن يكون أكبر من صفر")
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.upsertAsset(
                    AssetEntity(
                        id = UUID.randomUUID().toString(),
                        companyId = company,
                        name = name.trim(),
                        costMinor = Money.parse(cost.ifBlank { "0" }, currency),
                        salvageMinor = Money.parse(salvage.ifBlank { "0" }, currency),
                        months = m,
                        startPeriod = startPeriod.ifBlank { today().take(7) },
                    ),
                )
            }
            refresh()
            _state.value = _state.value.copy(message = "تم حفظ الأصل")
        }
    }

    /** إهلاك الشهر الحالي للأصل — لا يتكرر لنفس الفترة */
    fun depreciateAsset(assetId: String) {
        viewModelScope.launch {
            try {
                val msg = withContext(Dispatchers.IO) {
                    val asset = dao.assets(company).firstOrNull { it.id == assetId }
                        ?: return@withContext "الأصل غير موجود"
                    val period = today().take(7)
                    if (asset.lastDepreciatedPeriod == period) return@withContext "أُهلك هذا الشهر مسبقاً"
                    val schedule = FixedAssets.schedule(
                        asset.costMinor, asset.salvageMinor, asset.months, asset.startPeriod,
                    )
                    val amount = schedule.firstOrNull { it.period == period }?.amount
                        ?: return@withContext "لا إهلاك مستحق لهذه الفترة"
                    posting.depreciate(company, today(), asset.id, amount)
                    dao.updateAssetDep(asset.id, asset.accumulatedMinor + amount, period)
                    "تم إهلاك ${Money.format(amount, currency)}"
                }
                refresh()
                _state.value = _state.value.copy(message = msg)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    // ── الموظفون والرواتب ────────────────────────────────────────
    fun addEmployee(name: String, phone: String, basic: String, allowances: String, deductions: String) {
        if (name.isBlank()) {
            _state.value = _state.value.copy(error = "اسم الموظف مطلوب")
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.upsertEmployee(
                    EmployeeEntity(
                        id = UUID.randomUUID().toString(),
                        companyId = company,
                        name = name.trim(),
                        phone = phone.trim(),
                        basicMinor = Money.parse(basic.ifBlank { "0" }, currency),
                        allowancesMinor = Money.parse(allowances.ifBlank { "0" }, currency),
                        deductionsMinor = Money.parse(deductions.ifBlank { "0" }, currency),
                    ),
                )
            }
            refresh()
            _state.value = _state.value.copy(message = "تم إضافة الموظف")
        }
    }

    /** مسير رواتب الشهر: يجمع كل الموظفين في قيد واحد متوازن */
    fun runPayroll() {
        if (!Rbac.allows(_state.value.role, com.example.midad.core.Perm.PAYROLL_EDIT)) {
            _state.value = _state.value.copy(error = "لا تملك صلاحية الرواتب")
            return
        }
        viewModelScope.launch {
            try {
                val msg = withContext(Dispatchers.IO) {
                    val staff = dao.employees(company)
                    if (staff.isEmpty()) return@withContext "لا موظفين مسجلين"
                    var gross = 0L
                    var deductions = 0L
                    var net = 0L
                    staff.forEach { e ->
                        val r = Payroll.compute(Payroll.Input(e.basicMinor, e.allowancesMinor, deductionsMinor = e.deductionsMinor))
                        gross += r.grossMinor
                        deductions += r.totalDeductionsMinor
                        net += r.netMinor
                    }
                    posting.payrollRun(company, today(), "PAY-" + System.currentTimeMillis(), gross, deductions, net)
                    "تم ترحيل مسير ${staff.size} موظف · صافي ${Money.format(net, currency)}"
                }
                refresh()
                _state.value = _state.value.copy(message = msg)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    // ── المستودعات والتحويل ──────────────────────────────────────
    fun addWarehouse(name: String) {
        if (name.isBlank()) {
            _state.value = _state.value.copy(error = "اسم المستودع مطلوب")
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val existing = dao.warehouses(company)
                dao.upsertWarehouse(
                    WarehouseEntity(
                        id = UUID.randomUUID().toString(),
                        companyId = company,
                        name = name.trim(),
                        isDefault = existing.isEmpty(),
                    ),
                )
            }
            refresh()
            _state.value = _state.value.copy(message = "تم إنشاء المستودع")
        }
    }

    /** تحويل بين مستودعين: يحرّك الكمية والقيمة بلا أثر على الأرباح */
    fun transferStock(productId: String, fromWarehouse: String, toWarehouse: String, qty: String) {
        val qtyMilli = ((qty.toDoubleOrNull() ?: 0.0) * 1000).toLong()
        if (qtyMilli <= 0L) {
            _state.value = _state.value.copy(error = "الكمية يجب أن تكون أكبر من صفر")
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val from = dao.stockLevel(company, fromWarehouse, productId)
                        ?: StockLevelEntity(company, fromWarehouse, productId)
                    val to = dao.stockLevel(company, toWarehouse, productId)
                        ?: StockLevelEntity(company, toWarehouse, productId)
                    val (nf, nt) = Warehouses.transfer(
                        StockLevel(from.warehouseId, from.productId, from.qtyMilli, from.valueMinor),
                        StockLevel(to.warehouseId, to.productId, to.qtyMilli, to.valueMinor),
                        qtyMilli,
                    )
                    dao.upsertStockLevel(StockLevelEntity(company, nf.warehouseId, nf.productId, nf.qtyMilli, nf.valueMinor))
                    dao.upsertStockLevel(StockLevelEntity(company, nt.warehouseId, nt.productId, nt.qtyMilli, nt.valueMinor))
                }
                refresh()
                _state.value = _state.value.copy(message = "تم التحويل بين المستودعين")
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun addBatch(productId: String, qty: String, unitCost: String, expiry: String, warehouseId: String) {
        val qtyMilli = ((qty.toDoubleOrNull() ?: 0.0) * 1000).toLong()
        if (productId.isBlank() || qtyMilli <= 0L) {
            _state.value = _state.value.copy(error = "اختر صنفاً وكمية صحيحة")
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.upsertBatch(
                    BatchEntity(
                        id = UUID.randomUUID().toString(),
                        companyId = company,
                        productId = productId,
                        warehouseId = warehouseId,
                        qtyMilli = qtyMilli,
                        unitCostMinor = Money.parse(unitCost.ifBlank { "0" }, currency),
                        expiry = expiry.ifBlank { null },
                        receivedDate = today(),
                    ),
                )
                val level = dao.stockLevel(company, warehouseId, productId)
                    ?: StockLevelEntity(company, warehouseId, productId)
                val updated = Warehouses.receive(
                    StockLevel(level.warehouseId, level.productId, level.qtyMilli, level.valueMinor),
                    qtyMilli, Money.parse(unitCost.ifBlank { "0" }, currency),
                )
                dao.upsertStockLevel(StockLevelEntity(company, updated.warehouseId, updated.productId, updated.qtyMilli, updated.valueMinor))
            }
            refresh()
            _state.value = _state.value.copy(message = "تم استلام الدفعة")
        }
    }

    // ── الوردية والفواتير المعلّقة ───────────────────────────────
    fun openShift(openingCash: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.upsertShift(
                    ShiftEntity(
                        id = UUID.randomUUID().toString(),
                        companyId = company,
                        openedAt = today(),
                        openingCashMinor = Money.parse(openingCash.ifBlank { "0" }, currency),
                    ),
                )
            }
            refresh()
            _state.value = _state.value.copy(message = "تم فتح الوردية")
        }
    }

    fun closeShift(countedCash: String) {
        viewModelScope.launch {
            try {
                val msg = withContext(Dispatchers.IO) {
                    val row = dao.openShift(company) ?: return@withContext "لا وردية مفتوحة"
                    val shift = Shift(
                        row.id, row.openedAt, row.openingCashMinor, row.closedAt, row.countedCashMinor,
                        row.invoices, row.cashSalesMinor, row.cardSalesMinor, row.creditSalesMinor, row.refundsMinor,
                    )
                    val counted = Money.parse(countedCash.ifBlank { "0" }, currency)
                    val closed = Shifts.close(shift, counted, today())
                    dao.upsertShift(
                        row.copy(closedAt = closed.closedAt, countedCashMinor = closed.countedCashMinor),
                    )
                    val variance = Shifts.summary(closed).variance ?: 0L
                    "أُغلقت الوردية · الفرق ${Money.format(variance, currency)}"
                }
                refresh()
                _state.value = _state.value.copy(message = msg)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    /** تعليق سلة الكاشير للرجوع إليها لاحقاً */
    fun holdInvoice(label: String, lines: List<LineInput>, discountPct: String) {
        if (lines.isEmpty()) {
            _state.value = _state.value.copy(error = "السلة فارغة")
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val payload = lines.joinToString(";;") {
                        listOf(
                            it.productId, it.name, (it.qty * 1000).toLong().toString(),
                            it.price, it.discountPct, it.taxPct,
                        ).joinToString("|")
                    }
                    dao.upsertHeldInvoice(
                        HeldInvoiceEntity(
                            id = UUID.randomUUID().toString(),
                            companyId = company,
                            label = label.ifBlank { "فاتورة معلّقة" },
                            createdAt = today(),
                            payload = payload,
                            invoiceDiscountPct = discountPct.ifBlank { "0" },
                        ),
                    )
                }
                refresh()
                _state.value = _state.value.copy(message = "تم تعليق الفاتورة")
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun resumeHeld(id: String) {
        viewModelScope.launch {
            val lines = withContext(Dispatchers.IO) {
                val row = dao.heldInvoices(company).firstOrNull { it.id == id } ?: return@withContext emptyList()
                dao.deleteHeldInvoice(id)
                row.payload.split(";;").filter { it.isNotBlank() }.mapNotNull { chunk ->
                    val parts = chunk.split("|")
                    if (parts.size < 6) null else LineInput(
                        productId = parts[0],
                        name = parts[1],
                        qty = (parts[2].toLongOrNull() ?: 0L) / 1000.0,
                        price = parts[3],
                        discountPct = parts[4],
                        taxPct = parts[5],
                    )
                }
            }
            refresh()
            _state.value = _state.value.copy(resumeLines = lines, message = "تم استرجاع الفاتورة المعلّقة")
        }
    }

    fun consumeResume() {
        _state.value = _state.value.copy(resumeLines = null)
    }

    // ── طاولات المطعم ────────────────────────────────────────────
    fun addTable(name: String, seats: String) {
        if (name.isBlank()) {
            _state.value = _state.value.copy(error = "اسم الطاولة مطلوب")
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dao.upsertTable(
                    TableEntity(
                        id = UUID.randomUUID().toString(),
                        companyId = company,
                        name = name.trim(),
                        seats = seats.toIntOrNull() ?: 4,
                    ),
                )
            }
            refresh()
        }
    }

    fun tableAction(tableId: String, action: Restaurant.TableAction) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val row = dao.tables(company).firstOrNull { it.id == tableId } ?: return@withContext
                    val current = runCatching { Restaurant.TableState.valueOf(row.status) }
                        .getOrDefault(Restaurant.TableState.FREE)
                    val next = Restaurant.next(current, action)
                    dao.upsertTable(row.copy(status = next.name))
                }
                refresh()
                _state.value = _state.value.copy(message = "تم تحديث حالة الطاولة")
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    // ── الإقرار الضريبي (طباعة) ─────────────────────────────────
    fun buildTaxPrint() {
        viewModelScope.launch {
            val html = withContext(Dispatchers.IO) {
                val accounts = dao.accounts(company).associateBy { it.id }.mapValues {
                    AccountRef(it.value.id, it.value.code, it.value.name,
                        com.example.midad.core.AccountType.valueOf(it.value.type), it.value.isGroup, it.value.isActive)
                }
                val pairs = dao.entries(company, null, null).map { e ->
                    JournalRequest(company, e.date, e.description, e.sourceType, e.sourceId, e.currency,
                        dao.linesOf(listOf(e.id)).map { JournalLine(it.accountId, it.debitMinor, it.creditMinor, it.currency, it.fxRate) }) to
                        dao.linesOf(listOf(e.id)).map {
                            NormLine(it.accountId, it.debitMinor, it.creditMinor, it.currency, it.fxRate, it.amountBase, it.costCenterId, it.note)
                        }
                }
                val ret = TaxReturn.fromEntries(pairs, accounts, today().take(4) + "-01-01", today(), currency)
                TaxReturn.html(ret, PrintCompany(dao.company(company)?.name ?: "مؤسستي", taxNumber = dao.company(company)?.taxNumber ?: ""))
            }
            _state.value = _state.value.copy(printHtml = html, printTitle = "الإقرار الضريبي")
        }
    }

    /** تصدير تحليلات المبيعات CSV (جاهز للمشاركة) */
    fun analyticsCsv(): String {
        val facts = runCatching { salesFacts() }.getOrDefault(emptyList())
        return Analytics.toCsv(facts)
    }

    private fun salesFacts(): List<SaleFact> =
        dao.documents(company).filter { it.kind == DocKind.INVOICE.code }.flatMap { d ->
            dao.docLines(d.id).map { l ->
                SaleFact(
                    date = d.date,
                    docId = d.id,
                    productId = l.productId,
                    productName = l.name,
                    partyId = d.partyId ?: "walk-in",
                    partyName = d.partyId?.let { dao.party(company, it)?.name } ?: "نقدي",
                    qtyMilli = l.qtyMilli,
                    netMinor = l.netMinor,
                    taxMinor = l.taxMinor,
                    cogsMinor = l.cogsMinor,
                )
            }
        }

    /** إضافة مستخدم (كاشير/محاسب/مدير) برمز PIN ودور محدد */
    fun addUser(name: String, phone: String, role: Role, pin: String) {
        if (!Rbac.allows(_state.value.role, com.example.midad.core.Perm.USER_MANAGE)) {
            _state.value = _state.value.copy(error = "إدارة المستخدمين للمالك فقط")
            return
        }
        if (name.isBlank() || phone.isBlank()) {
            _state.value = _state.value.copy(error = "الاسم والهاتف مطلوبان")
            return
        }
        if (!Pin.isValidFormat(pin)) {
            _state.value = _state.value.copy(error = "الرمز ٤ إلى ٦ أرقام")
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (dao.userByPhone(company, phone) != null)
                        throw AccountingException("DUPLICATE", "رقم الهاتف مستخدم مسبقاً")
                    val h = Pin.hash(pin, iterations = 20_000)
                    dao.upsertUser(
                        UserEntity(
                            id = UUID.randomUUID().toString(),
                            companyId = company,
                            phone = phone.trim(),
                            name = name.trim(),
                            role = role.name,
                            pinSaltB64 = h.saltB64,
                            pinHashB64 = h.hashB64,
                            pinIterations = h.iterations,
                        ),
                    )
                }
                refresh()
                _state.value = _state.value.copy(message = "تم إضافة المستخدم")
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    // ── تحديث الشاشة ─────────────────────────────────────────────
    // ── التسوية البنكية ──────────────────────────────────────────
    /** إضافة بند من كشف البنك (موجب = إيداع · سالب = سحب) */
    fun addBankLine(date: String, description: String, amount: String) {
        if (description.isBlank()) {
            _state.value = _state.value.copy(error = "أدخل وصف حركة الكشف")
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val minor = Money.parse(amount.ifBlank { "0" }, currency)
                    dao.upsertBankLine(
                        BankStatementLineEntity(
                            id = UUID.randomUUID().toString(),
                            companyId = company,
                            date = date.ifBlank { today() },
                            description = description,
                            amountMinor = minor,
                        ),
                    )
                }
                refresh()
                _state.value = _state.value.copy(message = "أُضيف بند الكشف")
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "تعذّر إضافة البند")
            }
        }
    }

    fun deleteBankLine(id: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { dao.deleteBankLine(id) }
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "تعذّر الحذف")
            }
        }
    }

    /** تثبيت نتيجة المطابقة التلقائية في القاعدة (تُعاد الحسابات عند كل تحديث) */
    fun reconcileBank() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val rows = dao.bankLines(company)
                    val accounts = dao.accounts(company).associateBy { it.id }
                    val bankAccountId = accounts.values.firstOrNull { it.code == Chart.Acc.BANK }?.id
                    if (bankAccountId != null) {
                        val book = dao.entries(company, null, null).mapIndexedNotNull { idx, e ->
                            val delta = dao.linesOf(listOf(e.id))
                                .filter { it.accountId == bankAccountId }
                                .sumOf { it.debitMinor - it.creditMinor }
                            if (delta == 0L) null else Banking.BookLine("$idx", e.date, e.description, delta)
                        }
                        val recon = Banking.autoMatch(
                            rows.map { Banking.StatementLine(it.id, it.date, it.description, it.amountMinor) },
                            book,
                        )
                        dao.clearBankMatches(company)
                        recon.matches.forEach { dao.matchBankLine(it.statementId, it.bookId) }
                    }
                }
                refresh()
                _state.value = _state.value.copy(message = "تمت المطابقة وتثبيتها")
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "تعذّرت المطابقة")
            }
        }
    }

    // ── الفواتير الدورية ─────────────────────────────────────────
    fun addSchedule(
        title: String,
        partyId: String?,
        kind: String,
        cycle: String,
        startDate: String,
        dayOfMonth: Int?,
        productId: String,
        productName: String,
        qtyMilli: Long,
        amount: String,
        taxRate: String,
        endDate: String? = null,
        onDone: () -> Unit = {},
    ) {
        if (title.isBlank()) {
            _state.value = _state.value.copy(error = "أدخل عنوان الجدول")
            return
        }
        val minor = Money.parse(amount.ifBlank { "0" }, currency)
        if (minor <= 0) {
            _state.value = _state.value.copy(error = "أدخل مبلغاً صحيحاً")
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    dao.upsertSchedule(
                        ScheduleEntity(
                            id = UUID.randomUUID().toString(),
                            companyId = company,
                            partyId = partyId,
                            title = title,
                            kind = kind,
                            cycle = cycle,
                            startDate = startDate.ifBlank { today() },
                            endDate = endDate,
                            dayOfMonth = dayOfMonth,
                            productId = productId.ifBlank { "service" },
                            productName = productName.ifBlank { title },
                            qtyMilli = if (qtyMilli <= 0) 1_000 else qtyMilli,
                            amountMinor = minor,
                            taxRatePct = taxRate.ifBlank { "0" },
                        ),
                    )
                }
                onDone()
                refresh()
                _state.value = _state.value.copy(message = "أُضيف الجدول الدوري")
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "تعذّر إضافة الجدول")
            }
        }
    }

    /** تنفيذ كل الاستحقاقات المستحقة لجدول واحد — كل استحقاق = مستند مرحّل بقيده */
    fun runSchedule(id: String) {
        if (!Rbac.allows(_state.value.role, com.example.midad.core.Perm.SALE_EDIT)) {
            _state.value = _state.value.copy(error = "لا تملك صلاحية إنشاء المستندات")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            try {
                val posted = withContext(Dispatchers.IO) {
                    val raw = dao.schedules(company).firstOrNull { it.id == id } ?: return@withContext 0
                    if (!raw.active) return@withContext 0
                    val model = Recurring.Schedule(
                        id = raw.id,
                        partyId = raw.partyId ?: "",
                        title = raw.title,
                        cycle = runCatching { Recurring.Cycle.valueOf(raw.cycle) }
                            .getOrDefault(Recurring.Cycle.MONTHLY),
                        startDate = raw.startDate,
                        endDate = raw.endDate,
                        dayOfMonth = raw.dayOfMonth,
                        amountMinor = raw.amountMinor,
                        taxRatePct = raw.taxRatePct,
                        lastRunDate = raw.lastRunDate,
                        active = raw.active,
                    )
                    val due = Recurring.dueDates(model, today())
                    var count = 0
                    for (date in due) {
                        val docId = UUID.randomUUID().toString()
                        val number = dao.nextNumber(company, raw.kind, date.take(4))
                        val inputs = listOf(
                            PostingService.DocInput(
                                productId = raw.productId,
                                name = raw.productName,
                                qtyMilli = raw.qtyMilli,
                                unitPriceMinor = raw.amountMinor,
                                discountPct = "0",
                                taxRatePct = raw.taxRatePct,
                                isService = true,
                            ),
                        )
                        val result = if (raw.kind == "INV") {
                            posting.recordInvoiceDoc(company, date, docId, inputs, "0", 0L)
                        } else {
                            posting.recordPurchaseDoc(company, date, docId, inputs, "0", 0L, cashPaidMinor = 0L)
                        }
                        val t = result.totals
                        dao.insertDocument(
                            DocumentEntity(
                                id = docId,
                                companyId = company,
                                kind = raw.kind,
                                number = number,
                                partyId = raw.partyId,
                                date = date,
                                currency = currency,
                                status = DocStatus.POSTED.code,
                                grossMinor = t.gross,
                                discountMinor = t.discount,
                                netMinor = t.net,
                                taxMinor = t.tax,
                                roundingMinor = t.rounding,
                                totalMinor = t.total,
                                paidMinor = 0L,
                                entryId = result.entry.id,
                                note = "فاتورة دورية: ${raw.title}",
                                createdBy = _state.value.userName,
                            ),
                        )
                        dao.insertDocLines(
                            t.lines.mapIndexed { i, lt ->
                                DocumentLineEntity(
                                    documentId = docId,
                                    companyId = company,
                                    lineNo = i + 1,
                                    productId = lt.line.productId,
                                    name = lt.line.name,
                                    qtyMilli = lt.line.qtyMilli,
                                    unitPriceMinor = lt.line.unitPriceMinor,
                                    discountPct = lt.line.discountPct,
                                    taxRatePct = lt.line.taxRatePct,
                                    netMinor = lt.net,
                                    taxMinor = lt.tax,
                                    totalMinor = lt.total,
                                    cogsMinor = 0L,
                                )
                            },
                        )
                        dao.markScheduleRun(raw.id, date)
                        count++
                    }
                    count
                }
                refresh()
                _state.value = _state.value.copy(
                    busy = false,
                    message = if (posted == 0) "لا استحقاقات جديدة" else "تم ترحيل $posted مستنداً دورياً",
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, error = e.message ?: "تعذّر تنفيذ الجدول")
            }
        }
    }

    fun toggleSchedule(id: String, active: Boolean) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { dao.setScheduleActive(id, active) }
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "تعذّر التحديث")
            }
        }
    }

    fun deleteSchedule(id: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { dao.deleteSchedule(id) }
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "تعذّر الحذف")
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val s = withContext(Dispatchers.IO) { runCatching { compute() }.getOrElse { e -> UiState(ready = true, error = e.message) } }
            _state.value = s
        }
    }

    private fun compute(): UiState {
        val accounts = dao.accounts(company).associateBy { it.id }
            .mapValues {
                AccountRef(
                    it.value.id, it.value.code, it.value.name,
                    com.example.midad.core.AccountType.valueOf(it.value.type),
                    it.value.isGroup, it.value.isActive,
                )
            }
        val entries = dao.entries(company, null, null)

        val pairs = entries.map { e ->
            val lines: List<NormLine> = dao.linesOf(listOf(e.id)).map {
                NormLine(it.accountId, it.debitMinor, it.creditMinor, it.currency, it.fxRate, it.amountBase, it.costCenterId, it.note)
            }
            JournalRequest(company, e.date, e.description, e.sourceType, e.sourceId, e.currency,
                lines.map { JournalLine(it.accountId, it.debit, it.credit, it.currency, it.fxRate) }) to lines
        }

        val tb = Reports.trialBalance(pairs, accounts)
        val inc = Statements.incomeStatement(pairs, accounts)
        val tax = Statements.taxReport(pairs, accounts)

        fun bal(code: String): Long = accounts.values.firstOrNull { it.code == code }
            ?.let { acc -> Reports.balanceOf(pairs, acc.id) } ?: 0L

        val products = dao.products(company)
        val stocks = products.map { p ->
            val st = dao.stock(company, p.id)
            val qty = st?.qtyMilli ?: 0L
            StockRow(
                id = p.id,
                name = p.name,
                qty = qty / 1000.0,
                avgCost = Money.format(st?.avgCostMinor ?: 0L, currency),
                value = Money.format(st?.valueMinor ?: 0L, currency),
                low = !p.isService && p.minQtyMilli > 0 && qty <= p.minQtyMilli,
            )
        }

        val items = products.filter { it.isActive }.map { p ->
            PickItem(
                id = p.id,
                name = p.name,
                price = Money.format(p.priceMinor, currency),
                available = (dao.stock(company, p.id)?.qtyMilli ?: 0L) / 1000.0,
                unit = p.unit,
                sku = p.sku,
                barcode = p.barcode,
            )
        }

        // أرصدة الأطراف من المستندات غير المسددة
        val docs = dao.documents(company)
        val partyRows = dao.parties(company).map { p ->
            val own = docs.filter { it.partyId == p.id && (it.kind == "INV" || it.kind == "PINV" || it.kind == "SR") }
            val openAmount = own.sumOf { (it.totalMinor - it.paidMinor).coerceAtLeast(0L) }
            val over = p.creditLimitMinor > 0 && openAmount > p.creditLimitMinor
            PartyRow(
                id = p.id,
                name = p.name,
                kindLabel = if (p.kind == Parties.Kind.SUPPLIER.name) "مورد" else "عميل",
                phone = p.phone,
                balance = Money.format(openAmount, currency),
                creditLimit = Money.format(p.creditLimitMinor, currency),
                overLimit = over,
            )
        }

        // أعمار الديون من المستندات الآجلة
        val openItems = docs.filter { (it.kind == "INV" || it.kind == "PINV") && it.totalMinor > it.paidMinor }
            .map { Statements.OpenItem(it.number, it.partyId ?: "", it.date, it.totalMinor - it.paidMinor) }
        val aging = Statements.aging(openItems, today())
        fun bucket(label: String): Long = aging.sumOf { it.buckets[label] ?: 0L }

        // تنبيهات
        val alerts = ArrayList<AlertUi>()
        Reorder.scan(products.map {
            ItemRow(it.id, it.name, it.priceMinor, it.costMinor,
                dao.stock(company, it.id)?.qtyMilli ?: 0L, it.minQtyMilli, it.isService, it.isActive)
        }).forEach {
            alerts += AlertUi(
                "«${it.name}» " + if (it.level == Reorder.Level.OUT) "نفد من المخزون" else "تحت الحد الأدنى",
                if (it.level == Reorder.Level.OUT) "OUT" else "LOW",
            )
        }
        DueWatch.overdue(openItems, today(), termDays = 30).take(5).forEach {
            alerts += AlertUi("فاتورة ${it.ref} متأخرة ${it.daysOverdue} يوم بمبلغ ${Money.format(it.amountMinor, currency)}", "DUE")
        }
        DueWatch.chequesInWindow(
            dao.cheques(company).filter { it.status == "RECEIVED" || it.status == "DEPOSITED" }
                .map { DueWatch.ChequeItem(it.id, it.partyId ?: "", it.amountMinor, it.dueDate, it.direction == "IN") },
            today(), windowDays = 7,
        ).forEach {
            alerts += AlertUi("شيك بمبلغ ${Money.format(it.amountMinor, currency)} يستحق في ${it.dueDate}", "INFO")
        }
        partyRows.filter { it.overLimit }.forEach {
            alerts += AlertUi("العميل «${it.name}» تجاوز الحد الائتماني", "DUE")
        }

        val bs = Statements.balanceSheet(pairs, accounts)
        val yearStart = today().take(4) + "-01-01"
        val cf = Statements.cashFlow(pairs, accounts, yearStart, today())

        // كشف حساب الطرف المفتوح
        val stParty = statementPartyId
        val partyDocs = if (stParty != null) partyDocs(stParty) else emptyList()
        val statementRows = if (stParty != null) PartyLedger.statement(partyDocs) else emptyList()
        val statementBalance = if (stParty != null) PartyLedger.balance(partyDocs) else 0L

        // الأصول والموظفون
        val assetRows = dao.assets(company).map { a ->
            AssetRow(
                id = a.id,
                name = a.name,
                cost = Money.format(a.costMinor, currency),
                accumulated = Money.format(a.accumulatedMinor, currency),
                book = Money.format(a.costMinor - a.accumulatedMinor, currency),
                months = a.months,
                startPeriod = a.startPeriod,
                lastPeriod = a.lastDepreciatedPeriod,
            )
        }
        val staffRows = dao.employees(company).map { e ->
            val r = Payroll.compute(Payroll.Input(e.basicMinor, e.allowancesMinor, deductionsMinor = e.deductionsMinor))
            EmployeeRow(e.id, e.name, e.basicMinor, e.allowancesMinor, e.deductionsMinor, r.netMinor)
        }

        // ── المستودعات والدفعات ──
        val whRows = dao.warehouses(company)
        val levels = dao.stockLevels(company)
        val warehousesUi = whRows.map { w ->
            val scoped = levels.filter { it.warehouseId == w.id }
            WarehouseRow(
                id = w.id,
                name = w.name,
                isDefault = w.isDefault,
                totalQty = (scoped.sumOf { it.qtyMilli } / 1000.0).toString(),
                totalValue = Money.format(scoped.sumOf { it.valueMinor }, currency),
            )
        }
        val batchModels = dao.batches(company).map {
            Batch(it.id, it.productId, it.qtyMilli, it.unitCostMinor, it.expiry, it.receivedDate, it.warehouseId)
        }
        fun productName(id: String) = products.firstOrNull { it.id == id }?.name ?: id
        val batchUi = batchModels.filter { it.qtyMilli > 0 }.map {
            val status = when {
                it.expiry == null -> "OK"
                it.expiry < today() -> "EXPIRED"
                Statements.daysBetween(today(), it.expiry) <= 90 -> "SOON"
                else -> "OK"
            }
            BatchRow(
                id = it.id,
                productName = productName(it.productId),
                qty = (it.qtyMilli / 1000.0).toString(),
                expiry = it.expiry ?: "—",
                status = status,
                value = Money.format(it.valueMinor, currency),
            )
        }

        // ── الوردية ──
        val openShiftRow = dao.openShift(company)
        val shiftModel = openShiftRow?.let {
            Shift(it.id, it.openedAt, it.openingCashMinor, it.closedAt, it.countedCashMinor,
                it.invoices, it.cashSalesMinor, it.cardSalesMinor, it.creditSalesMinor, it.refundsMinor)
        }
        val shiftSum = shiftModel?.let { Shifts.summary(it) }

        // ── الإقرار الضريبي والتحليلات ──
        val vat = TaxReturn.fromEntries(pairs, accounts, yearStart, today(), currency)
        val facts = salesFacts()

        val settings = dao.settings().associate { it.key to it.value }
        val user = dao.users(company).firstOrNull()

        // ── التسوية البنكية: كشف البنك مقابل حركة حساب البنك في الأستاذ ──
        val bankRows = dao.bankLines(company)
        val bankAccountId = accounts.entries.firstOrNull { it.value.code == Chart.Acc.BANK }?.key
        val bookLines = if (bankAccountId == null) emptyList() else pairs.mapIndexedNotNull { idx, pair ->
            val req = pair.first
            val normLines = pair.second
            val delta = normLines.filter { it.accountId == bankAccountId }.sumOf { it.netBase() }
            if (delta == 0L) null
            else Banking.BookLine("$idx", req.date, req.description, delta)
        }
        val recon = Banking.autoMatch(
            bankRows.map { Banking.StatementLine(it.id, it.date, it.description, it.amountMinor, it.reference) },
            bookLines,
        )
        val bankLineRows = bankRows.map { row ->
            val m = recon.matches.firstOrNull { it.statementId == row.id }
            BankLineRow(
                id = row.id,
                date = row.date,
                description = row.description,
                amount = Money.format(row.amountMinor, currency),
                matched = m != null,
                matchedTo = row.matchedEntryId ?: (m?.bookId ?: ""),
            )
        }

        // ── الفواتير الدورية: المستحق حتى اليوم ──
        val scheduleModels = dao.schedules(company).map { sch ->
            sch to Recurring.Schedule(
                id = sch.id,
                partyId = sch.partyId ?: "",
                title = sch.title,
                cycle = runCatching { Recurring.Cycle.valueOf(sch.cycle) }
                    .getOrDefault(Recurring.Cycle.MONTHLY),
                startDate = sch.startDate,
                endDate = sch.endDate,
                dayOfMonth = sch.dayOfMonth,
                amountMinor = sch.amountMinor,
                taxRatePct = sch.taxRatePct,
                lastRunDate = sch.lastRunDate,
                active = sch.active,
            )
        }
        val scheduleRows = scheduleModels.map { (raw, model) ->
            val due = runCatching { Recurring.dueDates(model, today()) }.getOrDefault(emptyList())
            ScheduleRow(
                id = raw.id,
                title = raw.title,
                partyName = partyRows.firstOrNull { it.id == raw.partyId }?.name ?: "—",
                kindLabel = if (raw.kind == "INV") "مبيعات" else "مشتريات",
                cycleLabel = when (model.cycle) {
                    Recurring.Cycle.DAILY -> "يومي"
                    Recurring.Cycle.WEEKLY -> "أسبوعي"
                    Recurring.Cycle.MONTHLY -> "شهري"
                    Recurring.Cycle.QUARTERLY -> "ربع سنوي"
                    Recurring.Cycle.YEARLY -> "سنوي"
                },
                nextDate = due.firstOrNull() ?: "—",
                dueCount = due.size,
                amount = Money.format(raw.amountMinor, currency),
                active = raw.active,
            )
        }
        val dueTotal = scheduleModels.sumOf { (_, model) ->
            runCatching {
                Recurring.runPlan(model, today()).sumOf { it.totalMinor }
            }.getOrDefault(0L)
        }

        return UiState(
            ready = true,
            cash = Money.format(bal(Chart.Acc.CASH), currency),
            inventory = Money.format(bal(Chart.Acc.INVENTORY), currency),
            arBalance = Money.format(-bal(Chart.Acc.AR), currency),
            apBalance = Money.format(-bal(Chart.Acc.AP), currency),
            salesToday = Money.format(-bal(Chart.Acc.SALES) - bal(Chart.Acc.SALES_RETURN), currency),
            netProfit = Money.format(inc.netProfit, currency),
            trialRows = tb.rows.map { BalanceRowUi(it.code, it.name, Money.format(it.debit, currency), Money.format(it.credit, currency)) },
            totalDebit = Money.format(tb.totalDebit, currency),
            totalCredit = Money.format(tb.totalCredit, currency),
            balanced = tb.balanced,
            incomeNetRevenue = Money.format(inc.netRevenue, currency),
            incomeCogs = Money.format(inc.cogs, currency),
            incomeGross = Money.format(inc.grossProfit, currency),
            incomeNet = Money.format(inc.netProfit, currency),
            taxPayable = Money.format(tax.netPayable, currency),
            aging30 = Money.format(bucket("١-٣٠"), currency),
            aging60 = Money.format(bucket("٣١-٦٠"), currency),
            aging90 = Money.format(bucket("٦١-٩٠") + bucket("أكثر من ٩٠"), currency),
            entries = entries.reversed().map {
                EntryRow(it.id, it.number, it.date, it.description, Money.format(it.totalBase, currency), it.status)
            },
            stocks = stocks,
            products = products,
            items = items,
            parties = partyRows,
            documents = docs.map { d ->
                DocRowUi(
                    id = d.id,
                    number = d.number,
                    title = when (d.kind) {
                        "INV" -> "فاتورة مبيعات"
                        "PINV" -> "فاتورة مشتريات"
                        "SR" -> "مردود مبيعات"
                        "PR" -> "مردود مشتريات"
                        "QTE" -> "عرض سعر"
                        else -> d.kind
                    },
                    partyName = d.partyId?.let { id -> dao.party(company, id)?.name } ?: "—",
                    date = d.date,
                    total = Money.format(d.totalMinor, currency),
                    status = if (d.status == "POSTED") "مُرحَّل" else d.status,
                    paid = Money.format(d.paidMinor, currency),
                )
            },
            alerts = alerts,
            bsAssets = Money.format(bs.totalAssets, currency),
            bsLiabilities = Money.format(bs.totalLiabilities, currency),
            bsEquity = Money.format(bs.totalEquity + bs.retainedEarnings, currency),
            bsRetained = Money.format(bs.retainedEarnings, currency),
            bsBalanced = bs.balanced,
            cfOpening = Money.format(cf.openingCash, currency),
            cfOperating = Money.format(cf.operating, currency),
            cfInvesting = Money.format(cf.investing, currency),
            cfFinancing = Money.format(cf.financing, currency),
            cfClosing = Money.format(cf.closingCash, currency),
            cfVerified = cf.verified,
            statementPartyId = stParty,
            statementPartyName = stParty?.let { id -> dao.party(company, id)?.name } ?: "",
            statement = statementRows,
            statementBalance = Money.format(statementBalance, currency),
            assets = assetRows,
            employees = staffRows,
            payrollGross = Money.format(staffRows.sumOf { it.basic + it.allowances }, currency),
            payrollNet = Money.format(staffRows.sumOf { it.net }, currency),
            warehouses = warehousesUi,
            batches = batchUi,
            atRisk = Money.format(Batches.atRiskValue(batchModels, today(), 90), currency),
            shiftOpen = openShiftRow != null,
            shiftLabel = openShiftRow?.let { "وردية ${it.openedAt}" } ?: "لا وردية مفتوحة",
            shiftExpectedCash = Money.format(shiftSum?.expectedCash ?: 0L, currency),
            shiftVariance = shiftSum?.variance?.let { Money.format(it, currency) },
            held = dao.heldInvoices(company).map { h ->
                HeldRow(
                    id = h.id,
                    label = h.label,
                    createdAt = h.createdAt,
                    total = h.payload.split(";;").filter { it.isNotBlank() }.sumOf { chunk ->
                        val parts = chunk.split("|")
                        val qty = parts.getOrNull(2)?.toLongOrNull() ?: 0L
                        val price = parts.getOrNull(3)?.let { runCatching { Money.parse(it, currency) }.getOrDefault(0L) } ?: 0L
                        Money.mulDiv(price, qty, 1000L)
                    }.let { Money.format(it, currency) },
                    items = h.payload.split(";;").count { it.isNotBlank() },
                )
            },
            tables = dao.tables(company).map { TableRow(it.id, it.name, it.seats, it.status) },
            users = dao.users(company).map {
                UserRow(
                    id = it.id,
                    name = it.name,
                    phone = it.phone,
                    roleLabel = runCatching { Role.valueOf(it.role).label }.getOrDefault(it.role),
                    isActive = it.isActive,
                )
            },
            audit = dao.audits(company).map { a ->
                AuditRowUi(
                    entity = a.entity,
                    action = a.action,
                    detail = a.detail,
                    at = java.time.Instant.ofEpochMilli(a.occurredAt)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate().toString(),
                )
            },
            vatBoxes = vat.boxes,
            vatPayable = Money.format(vat.netPayable, currency),
            vatIsCredit = vat.isCredit,
            topProducts = Analytics.topProfit(facts, 6),
            salesByParty = Analytics.byParty(facts).take(6),
            salesByMonth = Analytics.byMonth(facts),
            analyticsNet = Money.format(Analytics.totals(facts).netMinor, currency),
            analyticsProfit = Money.format(Analytics.totals(facts).profitMinor, currency),
            analyticsDocs = Analytics.totals(facts).docs,
            cheques = dao.cheques(company).map { ch ->
                ChequeRow(
                    id = ch.id,
                    number = ch.number,
                    amount = Money.format(ch.amountMinor, currency),
                    dueDate = ch.dueDate,
                    status = ch.status,
                    incoming = ch.direction == "IN",
                )
            },
            companyName = dao.company(company)?.name ?: "مؤسستي",
            companyTax = dao.company(company)?.taxNumber ?: "",
            currency = currency,
            userName = user?.name ?: "",
            userPhone = user?.phone ?: "",
            role = user?.role?.let { r -> runCatching { Role.valueOf(r) }.getOrNull() } ?: Role.OWNER,
            pinConfigured = user != null && settings["pin_set"] == "1",
            locked = _state.value.locked,
            paper = settings["paper"] ?: "A4",
            bankLines = bankLineRows,
            bankMatched = recon.matches.size,
            bankUnmatched = recon.unmatchedStatement.size + recon.unmatchedBook.size,
            bankDifference = Money.format(recon.differenceMinor, currency),
            bankBalanced = recon.balanced,
            bankHints = Banking.adjustmentHints(recon),
            schedules = scheduleRows,
            schedulesDue = scheduleRows.sumOf { it.dueCount },
            schedulesDueValue = Money.format(dueTotal, currency),
        )
    }

    private fun today(): String = LocalDate.now().toString()

    /** تفقيط مبلغ للعرض في الشاشات */
    fun tafqeet(amount: String): String = runCatching { Tafqeet.amount(Money.parse(amount, currency), currency) }.getOrDefault("")
}
