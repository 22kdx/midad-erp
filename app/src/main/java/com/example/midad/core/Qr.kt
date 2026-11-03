package com.example.midad.core

/**
 * مولّد رمز QR مستقل تماماً (بلا أي مكتبة خارجية) — Byte mode · ECC L/M · الإصدارات ١–١٠.
 *
 * لماذا مكتوب يدوياً؟ الطباعة داخل التطبيق تعمل بلا إنترنت، ولوجو/رمز الفاتورة الإلكترونية
 * يجب أن يُولَّد من البيانات نفسها. الكود مغطّى باختبار يقارن مصفوفة الرمز مع أداة مرجعية.
 *
 * الاستخدام: `QrCode.svg(payload, moduleSize)` ⇒ نص SVG جاهز للطباعة.
 */
object QrCode {

    enum class Ecc(val bits: Int, val label: String) {
        L(0b01, "L"), M(0b00, "M"), Q(0b11, "Q"), H(0b10, "H")
    }

    /** (إجمالي كلمات الكود, [عدد كتل المجموعة الأولى, كلمات بياناتها], [عدد المجموعة الثانية, كلمات بياناتها], كلمات التصحيح لكل كتلة) */
    private data class Spec(
        val total: Int,
        val blocks1: Int, val data1: Int,
        val blocks2: Int, val data2: Int,
        val ecPerBlock: Int,
    )

    private val L = mapOf(
        1 to Spec(26, 1, 19, 0, 0, 7),
        2 to Spec(44, 1, 34, 0, 0, 10),
        3 to Spec(70, 1, 55, 0, 0, 15),
        4 to Spec(100, 1, 80, 0, 0, 20),
        5 to Spec(134, 1, 108, 0, 0, 26),
        6 to Spec(172, 2, 68, 0, 0, 18),
        7 to Spec(196, 2, 78, 0, 0, 20),
        8 to Spec(242, 2, 97, 0, 0, 24),
        9 to Spec(292, 2, 116, 0, 0, 30),
        10 to Spec(346, 2, 68, 2, 69, 18),
    )

    private val M = mapOf(
        1 to Spec(26, 1, 16, 0, 0, 10),
        2 to Spec(44, 1, 28, 0, 0, 16),
        3 to Spec(70, 1, 44, 0, 0, 26),
        4 to Spec(100, 2, 32, 0, 0, 18),
        5 to Spec(134, 2, 43, 0, 0, 24),
        6 to Spec(172, 4, 27, 0, 0, 16),
        7 to Spec(196, 4, 31, 0, 0, 18),
        8 to Spec(242, 2, 38, 2, 39, 22),
        9 to Spec(292, 3, 36, 2, 37, 22),
        10 to Spec(346, 4, 43, 1, 44, 26),
    )

    private fun spec(version: Int, ecc: Ecc): Spec =
        (if (ecc == Ecc.L) L[version] else M[version])
            ?: error("الإصدار $version غير مدعوم في هذا المولّد")

    private fun dataCodewords(version: Int, ecc: Ecc): Int {
        val s = spec(version, ecc)
        return s.blocks1 * s.data1 + s.blocks2 * s.data2
    }

    // ── ترميز البيانات ────────────────────────────────────────────
    private fun payloadBits(text: String): List<Int> {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val bits = ArrayList<Int>(bytes.size * 8 + 20)
        fun push(value: Int, len: Int) {
            for (i in len - 1 downTo 0) bits.add((value shr i) and 1)
        }
        push(0b0100, 4)                     // byte mode
        push(bytes.size, 8)                 // v1..v9 ⇒ عدّاد ٨ بت
        bytes.forEach { push(it.toInt() and 0xFF, 8) }
        return bits
    }

    fun chooseVersion(text: String, ecc: Ecc = Ecc.L): Int {
        val needBits = 4 + 8 + text.toByteArray(Charsets.UTF_8).size * 8
        for (v in 1..10) {
            if (dataCodewords(v, ecc) * 8 >= needBits) return v
        }
        throw AccountingException(Err.BAD_LINE, "النص أطول من سعة الرمز المدعومة (١٠ إصدارات)")
    }

    private fun dataCodewords(text: String, version: Int, ecc: Ecc): IntArray {
        val capacityBits = dataCodewords(version, ecc) * 8
        val bits = payloadBits(text).toMutableList()
        require(bits.size <= capacityBits) { "النص أطول من سعة الإصدار $version" }

        // ٤ أصفار إنباء (أو أقل إن لم يتبقَّ مكان)، ثم حشو لحدود البايت
        repeat(minOf(4, capacityBits - bits.size)) { bits.add(0) }
        while (bits.size % 8 != 0) bits.add(0)

        val out = ArrayList<Int>()
        var i = 0
        while (i < bits.size) {
            var b = 0
            for (k in 0 until 8) b = (b shl 1) or bits[i + k]
            out.add(b)
            i += 8
        }
        val pads = intArrayOf(0xEC, 0x11)
        var p = 0
        while (out.size < capacityBits / 8) out.add(pads[p++ % 2])
        return out.toIntArray()
    }

    // ── تصحيح الأخطاء (Reed–Solomon على GF(256)) ──────────────────
    private val exp = IntArray(512)
    private val log = IntArray(256)

    private fun initTables() {
        if (exp[0] != 0) return
        var x = 1
        for (i in 0 until 255) {
            exp[i] = x
            log[x] = i
            x = x shl 1
            if (x and 0x100 != 0) x = x xor 0x11D
        }
        for (i in 255 until 512) exp[i] = exp[i - 255]
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        initTables()
        return exp[log[a] + log[b]]
    }

    private fun rsGenerator(degree: Int): IntArray {
        initTables()
        var poly = intArrayOf(1)
        for (i in 0 until degree) {
            val next = IntArray(poly.size + 1)
            for (j in poly.indices) {
                next[j] = next[j] xor poly[j]
                next[j + 1] = next[j + 1] xor gfMul(poly[j], exp[i])
            }
            poly = next
        }
        return poly
    }

    /** القسمة المطوّلة على GF(256) — باقي القسمة هو كلمات التصحيح */
    private fun rsRemainder(data: IntArray, degree: Int): IntArray {
        val gen = rsGenerator(degree)
        val work = IntArray(data.size + degree)
        System.arraycopy(data, 0, work, 0, data.size)
        for (i in data.indices) {
            val factor = work[i]
            if (factor == 0) continue
            for (j in gen.indices) work[i + j] = work[i + j] xor gfMul(gen[j], factor)
        }
        return work.copyOfRange(data.size, work.size)
    }

    private fun interleave(data: IntArray, version: Int, ecc: Ecc): IntArray {
        val s = spec(version, ecc)
        val blocks = ArrayList<IntArray>()
        val ecBlocks = ArrayList<IntArray>()
        var offset = 0
        for (b in 0 until s.blocks1) {
            val chunk = data.copyOfRange(offset, offset + s.data1)
            offset += s.data1
            blocks += chunk
            ecBlocks += rsRemainder(chunk, s.ecPerBlock)
        }
        for (b in 0 until s.blocks2) {
            val chunk = data.copyOfRange(offset, offset + s.data2)
            offset += s.data2
            blocks += chunk
            ecBlocks += rsRemainder(chunk, s.ecPerBlock)
        }

        val out = ArrayList<Int>()
        val maxData = blocks.maxOf { it.size }
        for (i in 0 until maxData) {
            for (b in blocks) if (i < b.size) out += b[i]
        }
        for (i in 0 until s.ecPerBlock) {
            for (b in ecBlocks) if (i < b.size) out += b[i]
        }
        return out.toIntArray()
    }

    // ── المصفوفة ─────────────────────────────────────────────────
    private class Grid(val size: Int) {
        val modules = Array(size) { BooleanArray(size) }
        val reserved = Array(size) { BooleanArray(size) }
    }

    private fun alignmentPositions(version: Int): List<Int> = when (version) {
        1 -> emptyList()
        2 -> listOf(6, 18)
        3 -> listOf(6, 22)
        4 -> listOf(6, 26)
        5 -> listOf(6, 30)
        6 -> listOf(6, 34)
        7 -> listOf(6, 22, 38)
        8 -> listOf(6, 24, 42)
        9 -> listOf(6, 26, 46)
        10 -> listOf(6, 28, 50)
        else -> emptyList()
    }

    private fun placeFinder(g: Grid, row: Int, col: Int) {
        for (r in -1..7) for (c in -1..7) {
            val rr = row + r
            val cc = col + c
            if (rr !in 0 until g.size || cc !in 0 until g.size) continue
            val isBorder = r == -1 || r == 7 || c == -1 || c == 7
            val isRing = r in 0..6 && c in 0..6 && (r == 0 || r == 6 || c == 0 || c == 6)
            val isCore = r in 2..4 && c in 2..4
            g.modules[rr][cc] = !isBorder && (isRing || isCore)
            g.reserved[rr][cc] = true
        }
    }

    private fun placeAlignment(g: Grid, version: Int) {
        val pos = alignmentPositions(version)
        for (r in pos) for (c in pos) {
            // تجاهل ما يتقاطع مع أنماط الزوايا
            if ((r == 6 && c == 6) || (r == 6 && c == pos.last()) || (r == pos.last() && c == 6)) continue
            for (dr in -2..2) for (dc in -2..2) {
                val rr = r + dr
                val cc = c + dc
                if (rr !in 0 until g.size || cc !in 0 until g.size) continue
                val dark = maxOf(kotlin.math.abs(dr), kotlin.math.abs(dc)) != 1
                g.modules[rr][cc] = dark
                g.reserved[rr][cc] = true
            }
        }
    }

    private fun placeTiming(g: Grid) {
        for (i in 8 until g.size - 8) {
            val dark = i % 2 == 0
            g.modules[6][i] = dark
            g.reserved[6][i] = true
            g.modules[i][6] = dark
            g.reserved[i][6] = true
        }
    }

    private fun formatBits(ecc: Ecc, mask: Int): Int {
        val data = (ecc.bits shl 3) or mask
        var value = data shl 10
        val gen = 0x537
        var msb = 14
        while (msb >= 10) {
            if ((value shr msb) and 1 == 1) value = value xor (gen shl (msb - 10))
            msb--
        }
        return ((data shl 10) or value) xor 0x5412
    }

    private fun versionBits(version: Int): Int {
        var value = version shl 12
        val gen = 0x1F25
        var msb = 17
        while (msb >= 12) {
            if ((value shr msb) and 1 == 1) value = value xor (gen shl (msb - 12))
            msb--
        }
        return (version shl 12) or value
    }

    /** مواضع بتات التنسيق الثمانية عشر (نسختان) + الوحدة الداكنة الثابتة */
    private fun formatPositions(size: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        for (i in 0..5) out += i to 8
        out += 7 to 8
        out += 8 to 8
        out += 8 to 7
        for (i in 9..14) out += 8 to (14 - i)
        for (i in 0..7) out += 8 to (size - 1 - i)
        for (i in 8..14) out += (size - 15 + i) to 8
        return out
    }

    private fun placeFormat(g: Grid, ecc: Ecc, mask: Int) {
        val bits = formatBits(ecc, mask)
        fun bit(i: Int) = (bits shr i) and 1 == 1

        // المواصفة تكتب المواضع بصيغة (عمود, صف) — نعكسها إلى [صف][عمود]
        for (i in 0..5) g.modules[i][8] = bit(i)
        g.modules[7][8] = bit(6)
        g.modules[8][8] = bit(7)
        g.modules[8][7] = bit(8)
        for (i in 9..14) g.modules[8][14 - i] = bit(i)
        for (i in 0..7) g.modules[8][g.size - 1 - i] = bit(i)
        for (i in 8..14) g.modules[g.size - 15 + i][8] = bit(i)

        // الوحدة الداكنة الثابتة (الصف size-8 · العمود 8)
        g.modules[g.size - 8][8] = true

        formatPositions(g.size).forEach { (r, c) -> g.reserved[r][c] = true }
        g.reserved[g.size - 8][8] = true
        g.reserved[6][8] = true
        g.reserved[8][6] = true
    }

    private fun placeVersion(g: Grid, version: Int) {
        if (version < 7) return
        val bits = versionBits(version)
        for (i in 0 until 18) {
            val dark = (bits shr i) and 1 == 1
            val a = g.size - 11 + i % 3
            val b = i / 3
            g.modules[a][b] = dark
            g.modules[b][a] = dark
            g.reserved[a][b] = true
            g.reserved[b][a] = true
        }
    }

    private fun placeData(g: Grid, codewords: IntArray) {
        var bitIndex = 0
        val totalBits = codewords.size * 8
        var right = g.size - 1
        var upward = true

        while (right >= 1) {
            if (right == 6) right-- // عمود التوقيت
            for (i in 0 until g.size) {
                val row = if (upward) g.size - 1 - i else i
                for (c in 0..1) {
                    val col = right - c
                    if (g.reserved[row][col]) continue
                    val bit = if (bitIndex < totalBits) {
                        (codewords[bitIndex / 8] shr (7 - bitIndex % 8)) and 1
                    } else 0
                    g.modules[row][col] = bit == 1
                    bitIndex++
                }
            }
            right -= 2
            upward = !upward
        }
    }

    private fun maskBit(mask: Int, row: Int, col: Int): Boolean = when (mask) {
        0 -> (row + col) % 2 == 0
        1 -> row % 2 == 0
        2 -> col % 3 == 0
        3 -> (row + col) % 3 == 0
        4 -> (row / 2 + col / 3) % 2 == 0
        5 -> (row * col) % 2 + (row * col) % 3 == 0
        6 -> ((row * col) % 2 + (row * col) % 3) % 2 == 0
        else -> ((row + col) % 2 + (row * col) % 3) % 2 == 0
    }

    private fun applyMask(g: Grid, mask: Int) {
        for (r in 0 until g.size) for (c in 0 until g.size) {
            if (g.reserved[r][c]) continue
            if (maskBit(mask, r, c)) g.modules[r][c] = !g.modules[r][c]
        }
    }

    private fun penalty(g: Grid): Int {
        var score = 0
        // القاعدة ١: سلاسل ٥ وحدات فأكثر بنفس اللون
        for (r in 0 until g.size) {
            var run = 1
            for (c in 1 until g.size) {
                if (g.modules[r][c] == g.modules[r][c - 1]) run++ else {
                    if (run >= 5) score += run - 2
                    run = 1
                }
            }
            if (run >= 5) score += run - 2
        }
        for (c in 0 until g.size) {
            var run = 1
            for (r in 1 until g.size) {
                if (g.modules[r][c] == g.modules[r - 1][c]) run++ else {
                    if (run >= 5) score += run - 2
                    run = 1
                }
            }
            if (run >= 5) score += run - 2
        }
        // القاعدة ٢: مربّعات ٢×٢ بنفس اللون
        for (r in 0 until g.size - 1) for (c in 0 until g.size - 1) {
            val v = g.modules[r][c]
            if (v == g.modules[r][c + 1] && v == g.modules[r + 1][c] && v == g.modules[r + 1][c + 1]) score += 3
        }
        // القاعدة ٤: نسبة الوحدات الداكنة
        var dark = 0
        for (r in 0 until g.size) for (c in 0 until g.size) if (g.modules[r][c]) dark++
        val percent = dark * 100 / (g.size * g.size)
        score += kotlin.math.abs(percent - 50) / 5 * 10
        return score
    }

    /** المصفوفة النهائية: true = وحدة داكنة */
    fun matrix(text: String, ecc: Ecc = Ecc.L, forcedMask: Int? = null): Array<BooleanArray> {
        val version = chooseVersion(text, ecc)
        val data = dataCodewords(text, version, ecc)
        val codewords = interleave(data, version, ecc)
        val size = version * 4 + 17

        var best: Array<BooleanArray>? = null
        var bestScore = Int.MAX_VALUE

        for (mask in (forcedMask?.let { listOf(it) } ?: (0..7).toList())) {
            val g = Grid(size)
            placeFinder(g, 0, 0)
            placeFinder(g, 0, size - 7)
            placeFinder(g, size - 7, 0)
            placeAlignment(g, version)
            placeTiming(g)
            placeVersion(g, version)
            placeFormat(g, ecc, mask)
            placeData(g, codewords)
            applyMask(g, mask)
            // إعادة كتابة معلومات التنسيق فوق القناع
            placeFormat(g, ecc, mask)
            val score = penalty(g)
            if (score < bestScore) {
                bestScore = score
                best = g.modules
            }
        }
        return best ?: error("تعذّر توليد الرمز")
    }

    /** SVG بسيط قابل للطباعة — يظهر في WebView وفي المعاينة */
    fun svg(text: String, moduleSize: Int = 4, quietZone: Int = 4, dark: String = "#111111"): String {
        val m = matrix(text)
        val n = m.size + quietZone * 2
        val px = n * moduleSize
        val sb = StringBuilder()
        sb.append("""<svg xmlns="http://www.w3.org/2000/svg" width="$px" height="$px" viewBox="0 0 $px $px">""")
        sb.append("""<rect width="$px" height="$px" fill="#FFFFFF"/>""")
        for (r in m.indices) for (c in m.indices) {
            if (!m[r][c]) continue
            val x = (c + quietZone) * moduleSize
            val y = (r + quietZone) * moduleSize
            sb.append("""<rect x="$x" y="$y" width="$moduleSize" height="$moduleSize" fill="$dark"/>""")
        }
        sb.append("</svg>")
        return sb.toString()
    }

    /** مصفوفة نصية للتشخيص والاختبار */
    fun toText(text: String): String = matrix(text).joinToString("\n") { row ->
        row.joinToString("") { if (it) "█" else " " }
    }
}
