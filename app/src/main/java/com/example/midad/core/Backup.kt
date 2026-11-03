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
 * النسخ الاحتياطي المشفّر — AES-256-GCM مع مفتاح مشتق من كلمة المرور (PBKDF2-HMAC-SHA256).
 *
 * صيغة الملف (نصية، تُشارك بسهولة):
 *   MIDAD-BK1
 *   iterations
 *   saltB64
 *   ivB64
 *   cipherB64
 *
 * لا توجد طريقة لاستعادة الملف بلا كلمة المرور — نحن لا نخزّنها في أي مكان.
 */
object Backup {

    const val MAGIC = "MIDAD-BK1"
    const val ITERATIONS = 100_000
    private const val TAG_BITS = 128
    private val rnd = SecureRandom()

    /** بيانات المنشأة داخل النسخة — نص JSON مبسّط يُبنى يدوياً (بلا مكتبات) */
    data class Meta(
        val appVersion: String,
        val createdAt: String,
        val companyId: String,
        val companyName: String,
        val counts: Map<String, Int> = emptyMap(),
        val schemaVersion: Int = 1,
    )

    fun digest(payload: String): String =
        MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun metaJson(meta: Meta, payloadDigest: String): String {
        fun esc(v: String) = v.replace("\\", "\\\\").replace("\"", "\\\"")
        val counts = meta.counts.entries.joinToString(",") { "\"${esc(it.key)}\":${it.value}" }
        return "{\"appVersion\":\"${esc(meta.appVersion)}\",\"createdAt\":\"${esc(meta.createdAt)}\"," +
            "\"companyId\":\"${esc(meta.companyId)}\",\"companyName\":\"${esc(meta.companyName)}\"," +
            "\"schemaVersion\":${meta.schemaVersion},\"payloadSha256\":\"$payloadDigest\"," +
            "\"counts\":{$counts}}"
    }

    /** تغليف الحمولة (JSON التطبيق) في ملف مشفّر قابل للكتابة على القرص أو المشاركة */
    fun seal(payload: String, password: String, meta: Meta, iterations: Int = ITERATIONS): String {
        require(password.length >= 8) { "كلمة مرور النسخة يجب أن تكون ٨ محروفاً على الأقل" }
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val body = plaintext(payload, meta)
        val ct = encrypt(body.toByteArray(Charsets.UTF_8), password, salt, iv, iterations)
        val b64 = Base64.getEncoder()
        return listOf(
            MAGIC,
            iterations.toString(),
            b64.encodeToString(salt),
            b64.encodeToString(iv),
            b64.encodeToString(ct),
        ).joinToString("\n")
    }

    /** فك التغليف — يرمي AccountingException برسالة عربية واضحة عند الفشل */
    fun open(blob: String, password: String): Pair<Meta, String> {
        val parts = blob.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size != 5 || parts[0] != MAGIC)
            throw AccountingException("BAD_BACKUP", "الملف ليس نسخة احتياطية من هذا التطبيق")
        val iterations = parts[1].toIntOrNull()
            ?: throw AccountingException("BAD_BACKUP", "ترويسة النسخة تالفة")
        val b64 = Base64.getDecoder()
        val (salt, iv, ct) = try {
            Triple(b64.decode(parts[2]), b64.decode(parts[3]), b64.decode(parts[4]))
        } catch (e: IllegalArgumentException) {
            throw AccountingException("BAD_BACKUP", "محتوى النسخة تالف")
        }
        val body = try {
            decrypt(ct, password, salt, iv, iterations).toString(Charsets.UTF_8)
        } catch (e: Exception) {
            throw AccountingException("WRONG_PASSWORD", "كلمة المرور غير صحيحة أو الملف تالف")
        }
        return split(body)
    }

    fun isSealed(blob: String): Boolean = blob.trim().startsWith(MAGIC)

    // ── الداخل ────────────────────────────────────────────────
    private const val SEP = "\n\u0000MIDAD-META\u0000\n"

    private fun plaintext(payload: String, meta: Meta): String {
        val json = metaJson(meta, digest(payload))
        return json + SEP + payload
    }

    private fun split(body: String): Pair<Meta, String> {
        val i = body.indexOf(SEP)
        if (i < 0) throw AccountingException("BAD_BACKUP", "بنية النسخة غير معروفة")
        val json = body.substring(0, i)
        val payload = body.substring(i + SEP.length)
        if (!payload.contains("\"schemaVersion\"") && !json.contains("\"schemaVersion\""))
            throw AccountingException("BAD_BACKUP", "لا توجد ترويسة وصفية")
        return parseMeta(json) to payload
    }

    /** قارئ JSON محدود لترويسة نعرف شكلها — يكفي ولا نضيف مكتبة */
    fun parseMeta(json: String): Meta {
        fun str(key: String): String =
            Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json)?.groupValues?.get(1)
                ?.replace("\\\"", "\"")?.replace("\\\\", "\\") ?: ""
        fun num(key: String): Int =
            Regex("\"$key\"\\s*:\\s*(-?\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val counts = Regex("\"(\\w+)\"\\s*:\\s*(\\d+)").findAll(
            Regex("\"counts\"\\s*:\\s*\\{([^}]*)}").find(json)?.groupValues?.get(1) ?: ""
        ).associate { it.groupValues[1] to it.groupValues[2].toInt() }
        return Meta(
            appVersion = str("appVersion"),
            createdAt = str("createdAt"),
            companyId = str("companyId"),
            companyName = str("companyName"),
            counts = counts,
            schemaVersion = num("schemaVersion"),
        )
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val key = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return SecretKeySpec(key, "AES")
    }

    private fun encrypt(data: ByteArray, password: String, salt: ByteArray, iv: ByteArray, iterations: Int): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(MAGIC.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(data)
    }

    private fun decrypt(data: ByteArray, password: String, salt: ByteArray, iv: ByteArray, iterations: Int): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(MAGIC.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(data)
    }
}
