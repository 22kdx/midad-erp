package com.example.midad.core

import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64

/**
 * الامتثال والتكامل المستندي:
 *  - رمز الفاتورة الإلكترونية (TLV / Base64) كما في متطلبات الفوترة الإلكترونية.
 *  - سلسلة بصمات (Hash chain) تجعل أي تعديل على مستند مُرحَّل مكشوفاً.
 *  - التحقق من صحة الرقم الضريبي وتسلسل الترقيم.
 *
 * كلها دوال نقية قابلة للاختبار، ولا تلمس أي حالة.
 */
object Compliance {

    // ── رمز الفاتورة (QR payload) ────────────────────────────────
    data class QrFields(
        val sellerName: String,
        val vatNumber: String,
        val timestamp: String,      // ISO-8601 محلي
        val totalWithVatMinor: Long,
        val vatTotalMinor: Long,
        val currency: String = "SAR",
    )

    /** الترميز الثنائي: لكل حقل [tag][len][value] */
    fun tlv(fields: QrFields): ByteArray {
        val out = ArrayList<Byte>()
        fun add(tag: Int, value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            require(bytes.size <= 255) { "الحقل $tag أطول من ٢٥٥ بايت" }
            out.add(tag.toByte())
            out.add(bytes.size.toByte())
            out.addAll(bytes.toList())
        }
        add(1, fields.sellerName)
        add(2, fields.vatNumber)
        add(3, fields.timestamp)
        add(4, Money.format(fields.totalWithVatMinor, fields.currency))
        add(5, Money.format(fields.vatTotalMinor, fields.currency))
        return out.toByteArray()
    }

    fun qrBase64(fields: QrFields): String = Base64.getEncoder().encodeToString(tlv(fields))

    /** قراءة الترميز مرة أخرى — تُستخدم في الاختبار وفي قارئ الرمز */
    fun parseTlv(blob: ByteArray): Map<Int, String> {
        val map = LinkedHashMap<Int, String>()
        var i = 0
        while (i + 1 < blob.size) {
            val tag = blob[i].toInt() and 0xFF
            val len = blob[i + 1].toInt() and 0xFF
            require(i + 2 + len <= blob.size) { "رمز مشوّه: الطول المعلن أكبر من البيانات" }
            map[tag] = String(blob, i + 2, len, Charsets.UTF_8)
            i += 2 + len
        }
        return map
    }

    fun qrTimestamp(date: String, time: String = "00:00:00"): String =
        "${date}T${time}Z"

    // ── سلسلة البصمات ────────────────────────────────────────────
    fun sha256Hex(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(text.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append("%02x".format(b))
        return sb.toString()
    }

    /** النص القانوني الذي تُحسب منه البصمة — ترتيب ثابت لا يتغيّر */
    fun canonical(
        number: String,
        date: String,
        partyId: String,
        totalMinor: Long,
        taxMinor: Long,
        currency: String,
    ): String = listOf(number, date, partyId, totalMinor.toString(), taxMinor.toString(), currency)
        .joinToString("|")

    data class Sealed(val hash: String, val prevHash: String) {
        val short: String get() = hash.take(12)
    }

    fun seal(
        number: String,
        date: String,
        partyId: String,
        totalMinor: Long,
        taxMinor: Long,
        currency: String,
        prevHash: String = GENESIS,
    ): Sealed {
        val text = canonical(number, date, partyId, totalMinor, taxMinor, currency) + "|" + prevHash
        return Sealed(sha256Hex(text), prevHash)
    }

    const val GENESIS = "0000000000000000000000000000000000000000000000000000000000000000"

    data class ChainReport(val ok: Boolean, val checked: Int, val brokenAt: String?, val reason: String?)

    /**
     * فحص سلسلة المستندات بالترتيب الزمني: كل مستند يحمل بصمة المستند السابق.
     * أي تعديل على مبلغ أو تاريخ أو رقم يكسر السلسلة من نقطة التعديل.
     */
    fun verifyChain(links: List<Pair<Sealed, String>>): ChainReport {
        var prev = GENESIS
        links.forEachIndexed { index, (sealed, canonicalText) ->
            if (sealed.prevHash != prev) {
                return ChainReport(
                    ok = false, checked = index, brokenAt = canonicalText,
                    reason = "بصمة المستند السابق غير مطابقة — ترتيب أو حذف مستند",
                )
            }
            val recomputed = sha256Hex(canonicalText + "|" + prev)
            if (recomputed != sealed.hash) {
                return ChainReport(
                    ok = false, checked = index, brokenAt = canonicalText,
                    reason = "بصمة المستند لا تطابق محتواه — تعديل بعد الترحيل",
                )
            }
            prev = sealed.hash
        }
        return ChainReport(ok = true, checked = links.size, brokenAt = null, reason = null)
    }

    // ── التحقق من الرقم الضريبي ──────────────────────────────────
    data class VatCheck(val valid: Boolean, val reason: String? = null)

    /** الرقم الضريبي: ١٥ رقماً يبدأ وينتهي بـ٣ (النمط المعتمد) */
    fun checkVatNumber(raw: String): VatCheck {
        val v = raw.filter { it.isDigit() }
        if (raw.any { !it.isDigit() && it != ' ' && it != '-' }) return VatCheck(false, "يحتوي رموزاً غير رقمية")
        if (v.length != 15) return VatCheck(false, "يجب أن يكون ١٥ رقماً (المُدخل ${v.length})")
        if (!v.startsWith("3")) return VatCheck(false, "يجب أن يبدأ بالرقم ٣")
        if (!v.endsWith("3")) return VatCheck(false, "يجب أن ينتهي بالرقم ٣")
        return VatCheck(true)
    }

    // ── سلامة الترقيم ────────────────────────────────────────────
    data class Gap(val expected: Int, val found: Int)

    /**
     * يفحص تسلسل الأرقام المستخرجة من أرقام المستندات (INV-2026-0007 ⇒ ٧).
     * الفجوة ليست خطأ بالضرورة (مستند محذوف قبل الترحيل)، لكنها تستحق عرضاً.
     */
    fun numberGaps(numbers: List<String>): List<Gap> {
        val parsed = numbers.mapNotNull { n ->
            Regex("(\\d+)\\s*$").find(n)?.groupValues?.get(1)?.toIntOrNull()
        }.sorted()
        val gaps = ArrayList<Gap>()
        parsed.zipWithNext().forEach { (a, b) ->
            if (b > a + 1) gaps += Gap(a + 1, b)
        }
        return gaps
    }

    fun nextNumber(lastNumber: String?, prefix: String, year: String, width: Int = 4): String {
        val last = lastNumber?.let { Regex("(\\d+)\\s*$").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0
        return "$prefix-$year-" + (last + 1).toString().padStart(width, '0')
    }
}

/**
 * الفواتير الدورية (إيجارات · اشتراكات · عقود صيانة).
 * تُحسب التواريخ المستحقة فقط — الترحيل يبقى عبر الخدمة نفسها كأي مستند.
 */
object Recurring {

    enum class Cycle(val label: String) { DAILY("يومي"), WEEKLY("أسبوعي"), MONTHLY("شهري"), QUARTERLY("ربع سنوي"), YEARLY("سنوي") }

    data class Schedule(
        val id: String,
        val partyId: String,
        val title: String,
        val cycle: Cycle,
        val startDate: String,
        val endDate: String? = null,
        val dayOfMonth: Int? = null,          // ٣١ ⇒ يُقصّ لآخر يوم في الشهر
        val amountMinor: Long,
        val taxRatePct: String = "0",
        val lastRunDate: String? = null,
        val active: Boolean = true,
    )

    fun nextDate(from: String, cycle: Cycle, dayOfMonth: Int? = null): String {
        val d = LocalDate.parse(from)
        val next = when (cycle) {
            Cycle.DAILY -> d.plusDays(1)
            Cycle.WEEKLY -> d.plusWeeks(1)
            Cycle.MONTHLY -> d.plusMonths(1)
            Cycle.QUARTERLY -> d.plusMonths(3)
            Cycle.YEARLY -> d.plusYears(1)
        }
        val day = dayOfMonth ?: return next.toString()
        val safeDay = minOf(day, next.lengthOfMonth())
        return next.withDayOfMonth(safeDay).toString()
    }

    /** كل الاستحقاقات حتى تاريخ معيّن (يشمل المتأخرة) */
    fun dueDates(schedule: Schedule, until: String): List<String> {
        if (!schedule.active) return emptyList()
        val out = ArrayList<String>()
        var cursor = schedule.lastRunDate?.let {
            nextDate(it, schedule.cycle, schedule.dayOfMonth)
        } ?: schedule.startDate

        var guard = 0
        while (cursor <= until && guard++ < 600) {
            schedule.endDate?.let { if (cursor > it) return out }
            out += cursor
            cursor = nextDate(cursor, schedule.cycle, schedule.dayOfMonth)
        }
        return out
    }

    data class RunLine(val date: String, val amountMinor: Long, val taxMinor: Long) {
        val totalMinor: Long get() = amountMinor + taxMinor
    }

    /** الأسطر الجاهزة للترحيل: كل استحقاق بضريبته */
    fun runPlan(schedule: Schedule, until: String): List<RunLine> =
        dueDates(schedule, until).map {
            RunLine(it, schedule.amountMinor, Money.percent(schedule.amountMinor, schedule.taxRatePct))
        }

    fun summarize(plans: List<RunLine>): Pair<Long, Long> =
        plans.sumOf { it.amountMinor } to plans.sumOf { it.taxMinor }
}

/**
 * الحسابات البنكية والتسوية: مطابقة كشف البنك بالقيود، وكشف غير المطابق.
 */
object Banking {

    data class Account(
        val id: String,
        val name: String,
        val iban: String = "",
        val accountCode: String = Chart.Acc.BANK,
        val currency: String = "YER",
    )

    data class StatementLine(
        val id: String,
        val date: String,
        val description: String,
        val amountMinor: Long,          // موجب = إيداع، سالب = سحب
        val reference: String = "",
    )

    data class BookLine(
        val id: String,                 // معرّف القيد
        val date: String,
        val description: String,
        val amountMinor: Long,
    )

    data class Match(val statementId: String, val bookId: String, val amountMinor: Long)

    data class Reconciliation(
        val matches: List<Match>,
        val unmatchedStatement: List<StatementLine>,
        val unmatchedBook: List<BookLine>,
        val statementTotal: Long,
        val bookTotal: Long,
        val differenceMinor: Long,
    ) {
        val balanced: Boolean get() = unmatchedStatement.isEmpty() && unmatchedBook.isEmpty() && differenceMinor == 0L
    }

    /**
     * مطابقة تلقائية: المبلغ أولاً ثم أقرب تاريخ (±٣ أيام).
     * البنود المطابقة تُستبعد من الجانبين، والباقي يُعرض للمستخدم.
     */
    fun autoMatch(
        statement: List<StatementLine>,
        book: List<BookLine>,
        dateWindow: Long = 3,
    ): Reconciliation {
        val usedBook = HashSet<String>()
        val matches = ArrayList<Match>()

        for (s in statement.sortedBy { it.date }) {
            val candidate = book
                .filter { it.id !in usedBook && it.amountMinor == s.amountMinor }
                .minByOrNull {
                    kotlin.math.abs(Statements.daysBetween(it.date, s.date))
                }
                ?.takeIf { kotlin.math.abs(Statements.daysBetween(it.date, s.date)) <= dateWindow }

            if (candidate != null) {
                usedBook += candidate.id
                matches += Match(s.id, candidate.id, s.amountMinor)
            }
        }

        val unmatchedStatement = statement.filter { s -> matches.none { it.statementId == s.id } }
        val unmatchedBook = book.filter { it.id !in usedBook }

        val sTotal = statement.sumOf { it.amountMinor }
        val bTotal = book.sumOf { it.amountMinor }
        return Reconciliation(
            matches = matches,
            unmatchedStatement = unmatchedStatement,
            unmatchedBook = unmatchedBook,
            statementTotal = sTotal,
            bookTotal = bTotal,
            differenceMinor = sTotal - bTotal,
        )
    }

    /**
     * مطابقة يدوية لبند واحد — ترفض مبلغاً مختلفاً إلا مع تسجيل فرق صريح.
     */
    fun manualMatch(statementLine: StatementLine, bookLine: BookLine, allowDifference: Boolean = false): Match {
        val diff = statementLine.amountMinor - bookLine.amountMinor
        if (diff != 0L && !allowDifference) {
            throw AccountingException(
                Err.BAD_LINE,
                "فرق ${Money.format(kotlin.math.abs(diff))} بين الكشف والقيود — لا تُطابق دون تسجيل الفرق",
            )
        }
        return Match(statementLine.id, bookLine.id, bookLine.amountMinor)
    }

    /** بنود التسوية المقترحة: شيكات لم تُحصّل · إيداعات لم تظهر · رسوم بنكية */
    fun adjustmentHints(r: Reconciliation): List<String> {
        val hints = ArrayList<String>()
        r.unmatchedBook.filter { it.amountMinor > 0 }
            .forEach { hints += "إيداع مسجّل لم يظهر في الكشف: ${it.description} (${it.date})" }
        r.unmatchedBook.filter { it.amountMinor < 0 }
            .forEach { hints += "سحب/شيك مسجّل لم يُصرف: ${it.description} (${it.date})" }
        r.unmatchedStatement.filter { it.amountMinor < 0 }
            .forEach { hints += "رسوم أو خصم بنكي غير مسجّل: ${it.description}" }
        r.unmatchedStatement.filter { it.amountMinor > 0 }
            .forEach { hints += "إيداع في الكشف بلا قيد: ${it.description}" }
        return hints
    }
}
