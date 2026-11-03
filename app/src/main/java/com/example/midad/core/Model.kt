package com.example.midad.core

/** نوع الحساب — يطابق AccountType في التطبيق */
enum class AccountType { ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE }

/** الحساب كما تحتاجه النواة (لا يقرأ من قاعدة البيانات بنفسه) */
data class AccountRef(
    val id: String,
    val code: String,
    val name: String,
    val type: AccountType,
    val isGroup: Boolean = false,
    val isActive: Boolean = true,
)

/** حالة الفترة المالية */
enum class PeriodStatus { OPEN, CLOSED, NOT_FOUND }

/**
 * كل ما تحتاجه النواة لترحيل قيد — تُحمَّل قبل الاستدعاء (في coroutine من الشاشة/المستودع).
 * هذا يجعل الكيرنل Pure Function: بلا I/O وبلا side effects وبلا اعتماد على Room أو Retrofit.
 */
data class JournalContext(
    val companyId: String,
    val functionalCurrency: String,
    val allowedCurrencies: Set<String>,
    val lockDate: String?,              // ISO yyyy-MM-dd أو null
    val periodStatus: PeriodStatus,
    val periodLabel: String = "",
    val accounts: Map<String, AccountRef> = emptyMap(),   // id -> account
    val duplicateEntryNumber: String? = null,             // قيد مُرحَّل مسبقاً لنفس المستند
)

/** سطر قيد مُطبَّع (بعد التحقق) — بالوحدات الصغرى */
data class NormLine(
    val accountId: String,
    val debit: Long,
    val credit: Long,
    val currency: String,
    val fxRate: String,
    val amountBase: Long,
    val costCenterId: String? = null,
    val note: String = "",
)

/**
 * القيمة بالعملة الوظيفية — كل التقارير تُجمَّع بـ amountBase لا بالمبلغ الأصلي،
 * وإلا اختلطت عملات في ميزان واحد. (الاحتياط: سطر قديم بلا amountBase).
 */
fun NormLine.baseDebit(): Long = if (debit <= 0L) 0L else if (amountBase != 0L) amountBase else debit
fun NormLine.baseCredit(): Long = if (credit <= 0L) 0L else if (amountBase != 0L) amountBase else credit
fun NormLine.netBase(): Long = baseDebit() - baseCredit()

/** سطر قيد كما تطلبه القواعد قبل التحقق */
data class JournalLine(
    val accountId: String,
    val debit: Long = 0L,
    val credit: Long = 0L,
    val currency: String? = null,
    val fxRate: String = "1",
    val costCenterId: String? = null,
    val note: String = "",
)

/** طلب ترحيل قيد */
data class JournalRequest(
    val companyId: String,
    val date: String,                  // ISO yyyy-MM-dd
    val description: String = "",
    val sourceType: String,
    val sourceId: String,
    val currency: String? = null,      // عملة المستند (افتراضياً عملة المنشأة)
    val lines: List<JournalLine>,
    val reversalOf: String? = null,
)

/** نتيجة التحقق: قيد جاهز للكتابة */
data class JournalDraft(
    val request: JournalRequest,
    val lines: List<NormLine>,
    val totalBase: Long,
    val currency: String,
)

class AccountingException(val code: String, message: String) : Exception(message)

object Err {
    const val NO_COMPANY = "NO_COMPANY"
    const val BAD_DATE = "BAD_DATE"
    const val NO_SOURCE = "NO_SOURCE"
    const val NO_LINES = "NO_LINES"
    const val TOO_FEW_LINES = "TOO_FEW_LINES"
    const val BAD_LINE = "BAD_LINE"
    const val BAD_ACCOUNT = "BAD_ACCOUNT"
    const val GROUP_ACCOUNT = "GROUP_ACCOUNT"
    const val INACTIVE_ACCOUNT = "INACTIVE_ACCOUNT"
    const val UNBALANCED = "UNBALANCED"
    const val PERIOD_CLOSED = "PERIOD_CLOSED"
    const val BEFORE_LOCK = "BEFORE_LOCK"
    const val DUPLICATE_SOURCE = "DUPLICATE_SOURCE"
    const val UNKNOWN_CURRENCY = "UNKNOWN_CURRENCY"
}
