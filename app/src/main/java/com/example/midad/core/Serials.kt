package com.example.midad.core

/**
 * الأرقام التسلسلية — تتبّع الوحدة الواحدة (أجهزة · هواتف · قطع غيار).
 *
 * القيود المحاسبية لا تتغيّر: التسلسلي طبقة تتبّع فوق المخزون،
 * لكنه يمنع بيع وحدة مرتين ويعطي المستخدم فكرة دقيقة عمّا بقي.
 */
object Serials {

    enum class Status(val code: String, val label: String) {
        IN_STOCK("IN_STOCK", "في المخزون"),
        SOLD("SOLD", "مُباع"),
        RETURNED("RETURNED", "مرتجع"),
        VOID("VOID", "ملغى"),
    }

    enum class Action { SELL, RETURN, VOID, RESTOCK }

    data class Item(
        val id: String,
        val productId: String,
        val number: String,
        val status: Status = Status.IN_STOCK,
        val documentId: String? = null,
        val date: String? = null,
    )

    /** أرقام مقبولة: ٣–٤٠ حرفاً/رقماً، بلا مسافات ولا شرطة في الطرفين */
    fun validate(number: String): String? {
        val n = number.trim()
        return when {
            n.length < 3 -> "الرقم التسلسلي قصير جداً"
            n.length > 40 -> "الرقم التسلسلي أطول من المسموح"
            n.any { it.isWhitespace() } -> "الرقم التسلسلي لا يحتوي مسافات"
            n.startsWith("-") || n.endsWith("-") -> "الرقم التسلسلي لا يبدأ أو ينتهي بشرطة"
            else -> null
        }
    }

    /** توليد نطاق: بادئة + عدد، أو من/إلى رقمين متسلسلين */
    fun generate(prefix: String, from: Int, count: Int): List<String> {
        require(count in 1..5_000) { "عدد الأرقام يجب أن يكون بين ١ و٥٠٠٠" }
        require(from >= 0) { "بداية الترقيم لا تكون سالبة" }
        return (0 until count).map { prefix + (from + it) }
    }

    /** من/إلى: أرقام متسلسلة رقمياً أو أبجدياً بنفس البادئة */
    fun expand(from: String, to: String): List<String> {
        val prefix = from.takeWhile { !it.isDigit() }
        val a = from.drop(prefix.length).toIntOrNull()
        val b = to.drop(prefix.length).toIntOrNull()
        if (a != null && b != null && b >= a && prefix == to.takeWhile { !it.isDigit() }) {
            return generate(prefix, a, b - a + 1)
        }
        return listOf(from, to).distinct()
    }

    /** الأرقام المكرّرة داخل قائمة المدخلات */
    fun duplicates(numbers: List<String>): List<String> =
        numbers.groupingBy { it.trim() }.eachCount().filter { it.value > 1 }.keys.toList()

    /** ترتيب التخصيص FIFO: الأقدم إدخالاً أولاً، من المخزون فقط */
    fun inStock(items: List<Item>): List<Item> =
        items.filter { it.status == Status.IN_STOCK }.sortedWith(compareBy({ it.date ?: "" }, { it.number }))

    /** تخصيص N وحدة للبيع — يرفض إن لم تكفِ الكمية */
    fun allocateForSale(items: List<Item>, productId: String, qty: Int): List<Item> {
        if (qty <= 0) throw AccountingException("BAD_QTY", "الكمية يجب أن تكون أكبر من صفر")
        val pool = inStock(items.filter { it.productId == productId })
        if (pool.size < qty) {
            throw AccountingException(
                "NO_SERIALS",
                "المتاح من الأرقام التسلسلية لهذا الصنف ${pool.size} فقط — المطلوب $qty",
            )
        }
        return pool.take(qty)
    }

    /** آلة حالات التسلسلي — أي انتقال غير مسموح يُرفض */
    fun next(current: Status, action: Action): Status = when (action) {
        Action.SELL -> when (current) {
            Status.IN_STOCK -> Status.SOLD
            else -> throw AccountingException("BAD_TRANSITION", "لا يمكن بيع رقم حالته ${current.label}")
        }
        Action.RETURN -> when (current) {
            Status.SOLD -> Status.RETURNED
            else -> throw AccountingException("BAD_TRANSITION", "المرتجع يكون من «مُباع» فقط")
        }
        Action.RESTOCK -> when (current) {
            Status.RETURNED, Status.VOID -> Status.IN_STOCK
            else -> throw AccountingException("BAD_TRANSITION", "لا يُعاد للمخزون إلا مرتجع أو ملغى")
        }
        Action.VOID -> when (current) {
            Status.SOLD -> throw AccountingException("BAD_TRANSITION", "المُباع يُعالج بالمرتجع لا بالإلغاء")
            Status.VOID -> Status.VOID
            else -> Status.VOID
        }
    }

    data class Summary(
        val inStock: Int,
        val sold: Int,
        val returned: Int,
        val voided: Int,
    ) {
        val total: Int get() = inStock + sold + returned + voided
    }

    fun summary(items: List<Item>): Summary = Summary(
        inStock = items.count { it.status == Status.IN_STOCK },
        sold = items.count { it.status == Status.SOLD },
        returned = items.count { it.status == Status.RETURNED },
        voided = items.count { it.status == Status.VOID },
    )

    /** فرق بين عدد الأرقام المتاحة وكمية المخزون — يكشف خللاً في التسجيل */
    fun reconciliationGap(items: List<Item>, productId: String, stockQtyMilli: Long): Long {
        val registered = inStock(items.filter { it.productId == productId }).size.toLong() * 1000L
        return registered - stockQtyMilli
    }
}
