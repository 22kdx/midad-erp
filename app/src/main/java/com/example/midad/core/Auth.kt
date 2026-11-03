package com.example.midad.core

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * الأمان المحلي — مصادقة داخلية بلا أي خدمة خارجية (Keycloak مرفوض).
 *
 *  - الرمز السري (PIN) يُخزَّن كـ PBKDF2-HMAC-SHA256 بملح عشوائي و١٢٠٠٠٠ دورة، ولا يُكتب خاماً أبداً.
 *  - المقارنة بزمن ثابت (constant time) لمنع تسريب التوقيت.
 *  - قفل مؤقت بعد عدد محاولات فاشلة (يمنع التخمين على جهاز مسروق).
 *  - الأدوار تُحدَّد مسبقاً بجدول صلاحيات واحد، والشاشات تسأل [Rbac.allows] فقط.
 */

enum class Role(val label: String) {
    OWNER("مالك"),
    MANAGER("مدير"),
    ACCOUNTANT("محاسب"),
    CASHIER("أمين صندوق"),
    WAREHOUSE("أمين مخزن"),
    VIEWER("مُطّلع"),
}

enum class Perm(val label: String) {
    SALE_EDIT("إنشاء وتعديل المبيعات"),
    SALE_DISCOUNT("منح خصم"),
    INVOICE_VOID("عكس فاتورة"),
    PURCHASE_EDIT("إنشاء المشتريات"),
    PAYMENT_EDIT("سندات قبض ودفع"),
    PARTY_EDIT("إدارة العملاء والموردين"),
    ITEM_EDIT("إدارة الأصناف والأسعار"),
    STOCK_ADJUST("جرد وتسويات المخزون"),
    ASSET_EDIT("الأصول الثابتة"),
    PAYROLL_EDIT("الرواتب"),
    JOURNAL_EDIT("قيود يدوية"),
    PERIOD_CLOSE("إقفال الفترات"),
    REPORT_VIEW("عرض التقارير"),
    DASHBOARD_VIEW("عرض اللوحة"),
    SETTINGS_EDIT("إعدادات المنشأة"),
    USER_MANAGE("إدارة المستخدمين"),
    BACKUP_EXPORT("نسخ احتياطي وتصدير"),
    AUDIT_VIEW("سجل النشاط"),
    COST_VIEW("رؤية التكلفة والأرباح"),
}

object Rbac {

    private val cashier = setOf(
        Perm.SALE_EDIT, Perm.PAYMENT_EDIT, Perm.DASHBOARD_VIEW,
    )

    private val warehouse = setOf(
        Perm.STOCK_ADJUST, Perm.ITEM_EDIT, Perm.DASHBOARD_VIEW, Perm.REPORT_VIEW,
    )

    private val accountant = setOf(
        Perm.SALE_EDIT, Perm.PURCHASE_EDIT, Perm.PAYMENT_EDIT, Perm.PARTY_EDIT, Perm.ITEM_EDIT,
        Perm.STOCK_ADJUST, Perm.ASSET_EDIT, Perm.PAYROLL_EDIT, Perm.JOURNAL_EDIT,
        Perm.REPORT_VIEW, Perm.DASHBOARD_VIEW, Perm.COST_VIEW, Perm.AUDIT_VIEW,
    )

    private val manager = accountant + setOf(
        Perm.SALE_DISCOUNT, Perm.INVOICE_VOID, Perm.PERIOD_CLOSE, Perm.BACKUP_EXPORT,
    )

    private val owner = manager + setOf(Perm.SETTINGS_EDIT, Perm.USER_MANAGE)

    private val matrix: Map<Role, Set<Perm>> = mapOf(
        Role.OWNER to owner,
        Role.MANAGER to manager,
        Role.ACCOUNTANT to accountant,
        Role.CASHIER to cashier,
        Role.WAREHOUSE to warehouse,
        Role.VIEWER to setOf(Perm.DASHBOARD_VIEW, Perm.REPORT_VIEW),
    )

    fun of(role: Role): Set<Perm> = matrix[role] ?: emptySet()

    fun allows(role: Role, perm: Perm): Boolean = of(role).contains(perm)

    /** الشاشات تطلب فقط ما تحتاجه — رسالة عربية جاهزة عند الرفض */
    /** أعلى نسبة خصم مسموحة للدور (٪) — تُفحص قبل بناء سطور الفاتورة */
    fun maxDiscount(role: Role): Double = when (role) {
        Role.OWNER -> 100.0
        Role.MANAGER -> 25.0
        Role.ACCOUNTANT -> 10.0
        Role.CASHIER -> 5.0
        Role.WAREHOUSE, Role.VIEWER -> 0.0
    }

    fun require(role: Role, perm: Perm) {
        if (!allows(role, perm))
            throw AccountingException("FORBIDDEN", "الدور «${role.label}» لا يملك صلاحية «${perm.label}»")
    }
}

/** تجزئة PIN */
data class PinHash(val saltB64: String, val hashB64: String, val iterations: Int)

object Pin {
    const val MIN_LEN = 4
    const val MAX_LEN = 6
    const val ITERATIONS = 120_000

    private val rnd = SecureRandom()
    private val keyLenBits = 256

    fun isValidFormat(pin: String): Boolean =
        pin.length in MIN_LEN..MAX_LEN && pin.all { it in '0'..'9' }

    fun newSalt(size: Int = 16): ByteArray = ByteArray(size).also { rnd.nextBytes(it) }

    fun newIv(size: Int = 12): ByteArray = ByteArray(size).also { rnd.nextBytes(it) }

    private fun pbkdf2(secret: CharArray, salt: ByteArray, iterations: Int, bits: Int = keyLenBits): ByteArray {
        val spec = PBEKeySpec(secret, salt, iterations, bits)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun hash(pin: String, salt: ByteArray = newSalt(), iterations: Int = ITERATIONS): PinHash {
        require(isValidFormat(pin)) { "الرمز يجب أن يكون ${MIN_LEN}-${MAX_LEN} أرقام" }
        val h = pbkdf2(pin.toCharArray(), salt, iterations)
        val b64 = Base64.getEncoder()
        return PinHash(b64.encodeToString(salt), b64.encodeToString(h), iterations)
    }

    fun verify(pin: String, stored: PinHash): Boolean {
        if (!isValidFormat(pin)) return false
        val b64 = Base64.getDecoder()
        val salt = b64.decode(stored.saltB64)
        val actual = pbkdf2(pin.toCharArray(), salt, stored.iterations)
        val expected = b64.decode(stored.hashB64)
        return MessageDigest.isEqual(actual, expected)
    }

    /** احتساب بصمة قصيرة لعرضها بجانب الجلسات (ليست كلمة سر) */
    fun fingerprint(seed: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(d.copyOf(9))
    }
}

enum class LoginResult { OK, WRONG_PIN, LOCKED, BAD_FORMAT, NO_USER }

/**
 * بوابة الدخول — تتبّع المحاولات والقفل. تُستدعى من شاشة القفل قبل أي شاشة أخرى.
 * كائن حالة بسيط (لا I/O) حتى يُختبر و→ يُحفظ عدّاده في Room عند الحاجة.
 */
class AuthGate(
    val maxAttempts: Int = 5,
    val lockMillis: Long = 15 * 60_000L,
) {
    private var failures = 0
    private var lockedAtMillis: Long? = null

    fun lockedUntilMillis(): Long? = lockedAtMillis?.plus(lockMillis)

    fun isLocked(nowMillis: Long): Boolean = lockedAtMillis?.let { nowMillis < it + lockMillis } ?: false

    fun remainingAttempts(): Int = (maxAttempts - failures).coerceAtLeast(0)

    fun attempt(pin: String, stored: PinHash?, nowMillis: Long): LoginResult {
        if (isLocked(nowMillis)) return LoginResult.LOCKED
        if (stored == null) return LoginResult.NO_USER
        if (!Pin.isValidFormat(pin)) return LoginResult.BAD_FORMAT
        return if (Pin.verify(pin, stored)) {
            failures = 0
            lockedAtMillis = null
            LoginResult.OK
        } else {
            failures += 1
            if (failures >= maxAttempts) lockedAtMillis = nowMillis
            LoginResult.WRONG_PIN
        }
    }

    fun reset() { failures = 0; lockedAtMillis = null }
}

/** جلسة محلية: مهلة خمول تُقفل التطبيق تلقائياً */
data class Session(val userId: String, val role: Role, val startedAt: Long, val lastSeenAt: Long)

object Sessions {
    const val IDLE_TIMEOUT_MILLIS = 10 * 60_000L

    fun open(userId: String, role: Role, nowMillis: Long): Session =
        Session(userId, role, nowMillis, nowMillis)

    fun touch(s: Session, nowMillis: Long): Session = s.copy(lastSeenAt = nowMillis)

    fun expired(s: Session, nowMillis: Long): Boolean = nowMillis - s.lastSeenAt > IDLE_TIMEOUT_MILLIS

    fun can(s: Session, perm: Perm): Boolean = Rbac.allows(s.role, perm)
}
