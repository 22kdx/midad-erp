package com.example.midad.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.midad.core.DocKind
import com.example.midad.core.Money
import com.example.midad.ui.LineInput
import com.example.midad.ui.MidadViewModel
import com.example.midad.ui.UiState
import com.example.midad.ui.components.*
import com.example.midad.ui.theme.LocalMizan

// ── الرئيسية ────────────────────────────────────────────────────
@Composable
fun DashboardScreen(vm: MidadViewModel, s: UiState) {
    val c = LocalMizan.current
    ScreenScaffold("مِداد", "${s.companyName} · محاسبة كاملة على جهازك") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("الصندوق", s.cash, modifier = Modifier.weight(1f), accent = c.mint)
            StatCard("المخزون", s.inventory, modifier = Modifier.weight(1f), accent = c.blue)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("المبيعات", s.salesToday, modifier = Modifier.weight(1f))
            StatCard("صافي الربح", s.netProfit, modifier = Modifier.weight(1f), accent = c.primary)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("ذمم العملاء", s.arBalance, modifier = Modifier.weight(1f), accent = c.amber)
            StatCard("ذمم الموردين", s.apBalance, modifier = Modifier.weight(1f), accent = c.coral)
        }

        if (s.alerts.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionTitle("تنبيهات تحتاج إجراء", "${s.alerts.size}")
        }

        LazyColumn(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(s.alerts) { a ->
                val color = when (a.severity) {
                    "OUT" -> c.coral
                    "LOW" -> c.amber
                    "DUE" -> c.primary
                    else -> c.blue
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(c.surface, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).background(color, RoundedCornerShape(4.dp)))
                    Spacer(Modifier.width(10.dp))
                    Text(a.text, fontSize = 12.sp, color = c.text2)
                }
            }

            if (s.alerts.isEmpty()) {
                item { SectionTitle("آخر القيود", "${s.entries.size} قيد") }
            }
            items(s.entries.take(15)) { e ->
                MizanCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(e.number, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                            Text(e.description, fontSize = 12.sp, color = c.text2)
                            Text(e.date, fontSize = 11.sp, color = c.text3)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(e.amount, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                            Text(
                                if (e.status == "REVERSED") "معكوس" else "مُرحّل",
                                fontSize = 11.sp,
                                color = if (e.status == "REVERSED") c.coral else c.mint,
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

// ── محرّر المستند (بنود متعددة) ──────────────────────────────────
@Composable
fun DocEditorDialog(
    kind: DocKind,
    vm: MidadViewModel,
    s: UiState,
    asQuote: Boolean = false,
    onClose: () -> Unit,
) {
    val c = LocalMizan.current
    val lines = remember { mutableStateListOf<LineInput>() }
    var partyId by remember { mutableStateOf(s.parties.firstOrNull { if (kind.isSales) it.kindLabel == "عميل" else it.kindLabel == "مورد" }?.id ?: "") }
    var invoiceDiscount by remember { mutableStateOf("0") }
    var paid by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var docCurrency by remember { mutableStateOf(s.currency) }
    var fxRate by remember { mutableStateOf("1") }

    // بند جديد
    var itemId by remember { mutableStateOf(s.items.firstOrNull()?.id ?: "") }
    var qty by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf(s.items.firstOrNull()?.price ?: "") }
    var lineDiscount by remember { mutableStateOf("0") }
    var taxPct by remember { mutableStateOf("15") }

    val total = remember(lines.toList(), invoiceDiscount) {
        runCatching {
            val t = com.example.midad.core.DocEngine.totals(
                lines.map {
                    com.example.midad.core.DocLine(
                        it.productId, it.name, (it.qty * 1000).toLong(),
                        Money.parse(it.price.ifBlank { "0" }), it.discountPct, it.taxPct, 0L,
                    )
                },
                invoiceDiscount.ifBlank { "0" },
            )
            t.total
        }.getOrDefault(0L)
    }

    val title = if (asQuote) "عرض سعر" else when (kind) {
        DocKind.INVOICE -> "فاتورة مبيعات"
        DocKind.PURCHASE -> "فاتورة مشتريات"
        DocKind.RETURN -> "مردود مبيعات"
        DocKind.PURCHASE_RETURN -> "مردود مشتريات"
        DocKind.PURCHASE_ORDER -> "أمر شراء"
        else -> "مستند"
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title, fontSize = 17.sp) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (kind.isSales) "العميل" else "المورد", fontSize = 12.sp, color = c.text2)
                s.parties
                    .filter { if (kind.isSales) it.kindLabel == "عميل" else it.kindLabel == "مورد" }
                    .forEach { p ->
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (partyId == p.id) c.primary.copy(alpha = 0.12f) else c.surface2, RoundedCornerShape(10.dp))
                                .clickable { partyId = p.id }
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(p.name, fontSize = 13.sp, color = c.text)
                            Text(p.balance, fontSize = 11.sp, color = c.text3)
                        }
                    }

                Divider(color = c.stroke, modifier = Modifier.padding(vertical = 4.dp))
                Text("البنود", fontSize = 12.sp, color = c.text2)

                lines.forEachIndexed { index, l ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.name, fontSize = 13.sp, color = c.text)
                            Text("${l.qty} × ${l.price} · خصم ${l.discountPct}% · ضريبة ${l.taxPct}%", fontSize = 11.sp, color = c.text3)
                        }
                        TextButton(onClick = { lines.removeAt(index) }) { Text("حذف", fontSize = 11.sp, color = c.coral) }
                    }
                }

                Divider(color = c.stroke, modifier = Modifier.padding(vertical = 4.dp))
                Text("إضافة بند", fontSize = 12.sp, color = c.text2)
                s.items.take(12).forEach { p ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (itemId == p.id) c.primary.copy(alpha = 0.12f) else c.surface2, RoundedCornerShape(10.dp))
                            .clickable {
                                itemId = p.id
                                price = p.price
                            }
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(p.name, fontSize = 12.sp, color = c.text)
                        Text("متاح ${p.available}", fontSize = 11.sp, color = c.text3)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(qty, { qty = it }, label = { Text("كمية", fontSize = 10.sp) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(price, { price = it }, label = { Text("سعر", fontSize = 10.sp) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(lineDiscount, { lineDiscount = it }, label = { Text("خصم %", fontSize = 10.sp) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(taxPct, { taxPct = it }, label = { Text("ضريبة %", fontSize = 10.sp) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                TextButton(
                    onClick = {
                        val p = s.items.firstOrNull { it.id == itemId }
                        if (p != null) {
                            lines.add(LineInput(p.id, p.name, qty.toDoubleOrNull() ?: 0.0, price, lineDiscount, taxPct))
                            qty = "1"; lineDiscount = "0"
                        }
                    },
                ) { Text("أضف البند") }

                Divider(color = c.stroke, modifier = Modifier.padding(vertical = 4.dp))
                Text("العملة", fontSize = 12.sp, color = c.text2)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(s.currency, "USD", "SAR").distinct().forEach { cur ->
                        Text(
                            cur,
                            fontSize = 11.sp,
                            color = if (docCurrency == cur) c.primary else c.text2,
                            modifier = Modifier
                                .background(if (docCurrency == cur) c.primary.copy(alpha = 0.12f) else c.surface2, RoundedCornerShape(8.dp))
                                .clickable { docCurrency = cur }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                if (docCurrency != s.currency) {
                    OutlinedTextField(
                        fxRate, { fxRate = it },
                        label = { Text("سعر الصرف (${s.currency} لكل ١ $docCurrency)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                OutlinedTextField(invoiceDiscount, { invoiceDiscount = it }, label = { Text("خصم على المستند %") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(paid, { paid = it }, label = { Text("المدفوع (اتركه فارغاً للآجل)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(note, { note = it }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth())

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("الإجمالي", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                    Text(Money.format(total, docCurrency), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.primary)
                }
                Text(
                    runCatching { com.example.midad.core.Tafqeet.amount(total, docCurrency) }.getOrDefault(""),
                    fontSize = 11.sp, color = c.text3,
                )

                s.error?.let { Text(it, color = c.coral, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (asQuote) {
                        vm.saveQuote(partyId.ifBlank { null }, lines.toList(), invoiceDiscount, note) { onClose() }
                    } else if (kind == DocKind.PURCHASE_ORDER) {
                        vm.savePurchaseOrder(partyId.ifBlank { null }, lines.toList(), invoiceDiscount, note) { onClose() }
                    } else {
                        val cur = if (docCurrency == s.currency) null else docCurrency
                        vm.saveDocument(
                            kind, partyId.ifBlank { null }, lines.toList(), invoiceDiscount, paid, note,
                            currencyCode = cur,
                            fxRate = if (cur == null) "1" else fxRate.ifBlank { "1" },
                        ) { onClose() }
                    }
                },
            ) {
                Text(
                    when {
                        asQuote -> "حفظ العرض"
                        kind == DocKind.PURCHASE_ORDER -> "حفظ الأمر"
                        else -> "حفظ وترحيل"
                    },
                )
            }
        },
        dismissButton = { TextButton(onClick = { vm.dismissMessage(); onClose() }) { Text("إلغاء") } },
    )
}

// ── المبيعات ────────────────────────────────────────────────────
@Composable
fun SalesScreen(vm: MidadViewModel, s: UiState) {
    val c = LocalMizan.current
    var showForm by remember { mutableStateOf(false) }
    var showQuote by remember { mutableStateOf(false) }
    var showReturn by remember { mutableStateOf(false) }
    var showSchedules by remember { mutableStateOf(false) }

    ScreenScaffold("المبيعات", "كل فاتورة = قيد متوازن مع تكلفتها الحقيقية") {
        Button(
            onClick = { showForm = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("فاتورة جديدة", fontSize = 15.sp) }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { showQuote = true },
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("عرض سعر", fontSize = 12.sp) }
            OutlinedButton(
                onClick = { showReturn = true },
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("مردود", fontSize = 12.sp) }
            OutlinedButton(
                onClick = { showSchedules = true },
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("دورية", fontSize = 12.sp) }
        }

        val quotes = s.documents.filter { it.title == "عرض سعر" }
        if (quotes.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionTitle("عروض الأسعار", "${quotes.size}")
            quotes.forEach { q ->
                MizanCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${q.number} · ${q.partyName}", fontSize = 12.sp, color = c.text)
                            Text(q.date, fontSize = 10.sp, color = c.text3)
                        }
                        Text(q.total, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                        TextButton(onClick = { vm.convertQuote(q.id) }) { Text("إلى فاتورة", fontSize = 11.sp) }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("المستندات", "${s.documents.count { it.title.contains("مبيعات") }}")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(s.documents.filter { it.title.contains("مبيعات") || it.title.contains("مردود") }) { d ->
                MizanCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("${d.title} · ${d.number}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                            Text("${d.partyName} · ${d.date}", fontSize = 12.sp, color = c.text2)
                            Text("مدفوع ${d.paid}", fontSize = 11.sp, color = c.text3)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(d.total, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                            TextButton(onClick = { vm.buildPrint(d.id) }) { Text("طباعة", fontSize = 11.sp) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    if (showForm) DocEditorDialog(DocKind.INVOICE, vm, s) { showForm = false }
    if (showQuote) QuoteDialog(vm, s) { showQuote = false }
    if (showReturn) DocEditorDialog(DocKind.RETURN, vm, s) { showReturn = false }
    if (showSchedules) SchedulesDialog(vm, s) { showSchedules = false }
}

// ── المشتريات ───────────────────────────────────────────────────
@Composable
fun PurchasesScreen(vm: MidadViewModel, s: UiState) {
    val c = LocalMizan.current
    var showForm by remember { mutableStateOf(false) }
    var showPurchaseReturn by remember { mutableStateOf(false) }
    var showPO by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }

    ScreenScaffold("المشتريات", "تكلفة الوصول تُرأسمَل على المخزون تلقائياً") {
        TabRow(selectedTabIndex = tab, containerColor = c.surface) {
            listOf("فواتير المشتريات", "أوامر الشراء").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontSize = 12.sp) } )
            }
        }
        Spacer(Modifier.height(10.dp))

        if (tab == 1) {
            PurchaseOrdersTab(vm, s, Modifier.weight(1f)) { showPO = true }
            return@ScreenScaffold
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { showForm = true },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("فاتورة مشتريات", fontSize = 14.sp) }
            OutlinedButton(
                onClick = { showPurchaseReturn = true },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("مردود مشتريات", fontSize = 14.sp) }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("فواتير المشتريات", "${s.documents.count { it.title.contains("مشتريات") }}")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(s.documents.filter { it.title.contains("مشتريات") }) { d ->
                MizanCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("${d.number}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                            Text("${d.partyName} · ${d.date}", fontSize = 12.sp, color = c.text2)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(d.total, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.coral)
                            TextButton(onClick = { vm.buildPrint(d.id) }) { Text("طباعة", fontSize = 11.sp) }
                        }
                    }
                }
            }
            if (s.documents.none { it.title.contains("مشتريات") }) item { EmptyHint("لا فواتير مشتريات بعد") }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    if (showForm) DocEditorDialog(DocKind.PURCHASE, vm, s) { showForm = false }
    if (showPurchaseReturn) DocEditorDialog(DocKind.PURCHASE_RETURN, vm, s) { showPurchaseReturn = false }
    if (showPO) DocEditorDialog(DocKind.PURCHASE_ORDER, vm, s) { showPO = false }
}

/** أوامر الشراء: التزام تشغيلي بلا قيد — يتحول لفاتورة فتُنشأ الذمة والتكلفة */
@Composable
private fun PurchaseOrdersTab(
    vm: MidadViewModel,
    s: UiState,
    modifier: Modifier = Modifier,
    onNew: () -> Unit,
) {
    val c = LocalMizan.current
    val orders = s.documents.filter { it.title == "أمر شراء" }

    Column(modifier) {
        Button(
            onClick = onNew,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
        ) { Text("أمر شراء جديد", fontSize = 14.sp) }

        Spacer(Modifier.height(12.dp))
        SectionTitle("أوامر الشراء", "${orders.size}")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (orders.isEmpty()) item { EmptyHint("لا أوامر شراء بعد") }
            items(orders) { d ->
                val converted = d.status == "CONVERTED"
                val cancelled = d.status == "CANCELLED"
                MizanCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("${d.number} · ${d.partyName}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                            Text(
                                d.date + " · " + when {
                                    converted -> "محوَّل إلى فاتورة"
                                    cancelled -> "ملغى"
                                    else -> "معتمد"
                                },
                                fontSize = 11.sp,
                                color = if (converted) c.mint else if (cancelled) c.coral else c.text3,
                            )
                        }
                        Text(d.total, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (!converted && !cancelled) {
                            TextButton(onClick = { vm.convertPurchaseOrder(d.id) }) {
                                Text("إلى فاتورة", fontSize = 11.sp)
                            }
                            TextButton(onClick = { vm.cancelDocument(d.id) }) {
                                Text("إلغاء", fontSize = 11.sp, color = c.text3)
                            }
                        }
                        TextButton(onClick = { vm.buildPrint(d.id) }) { Text("طباعة", fontSize = 11.sp) }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

// ── المخزون: أصناف · مستودعات · دفعات ──────────────────────────
@Composable
fun InventoryScreen(vm: MidadViewModel, s: UiState) {
    var tab by remember { mutableStateOf(0) }
    val c = LocalMizan.current

    ScreenScaffold("المخزون", "متوسط تكلفة مرجّح · مطابقة دائمية مع الحساب") {
        TabRow(selectedTabIndex = tab, containerColor = c.surface) {
            listOf("الأصناف", "المستودعات", "الدفعات").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontSize = 12.sp) })
            }
        }
        Spacer(Modifier.height(10.dp))
        when (tab) {
            0 -> ItemsTab(vm, s)
            1 -> WarehousesTab(vm, s)
            else -> BatchesTab(vm, s)
        }
    }
}

@Composable
private fun ItemsTab(vm: MidadViewModel, s: UiState) {
    val c = LocalMizan.current
    var showItem by remember { mutableStateOf(false) }
    var countFor by remember { mutableStateOf<String?>(null) }
    var countedQty by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showItem = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                Text("صنف جديد", fontSize = 12.sp)
            }
            OutlinedButton(onClick = { vm.refresh() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                Text("تحديث", fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(s.stocks) { st ->
                MizanCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(st.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                                if (st.low) {
                                    Spacer(Modifier.width(6.dp))
                                    Text("تحت الحد", fontSize = 10.sp, color = c.coral)
                                }
                            }
                            Text("الكمية: ${st.qty}", fontSize = 12.sp, color = c.text2)
                            Text("متوسط التكلفة: ${st.avgCost}", fontSize = 12.sp, color = c.text3)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(st.value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.blue)
                            TextButton(onClick = { countFor = st.id; countedQty = "${st.qty}" }) {
                                Text("جرد", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    if (showItem) {
        var name by remember { mutableStateOf("") }
        var sku by remember { mutableStateOf("") }
        var unit by remember { mutableStateOf("حبة") }
        var price by remember { mutableStateOf("") }
        var cost by remember { mutableStateOf("") }
        var minQty by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showItem = false },
            title = { Text("صنف جديد", fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(sku, { sku = it }, label = { Text("الرمز SKU") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(unit, { unit = it }, label = { Text("الوحدة") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(price, { price = it }, label = { Text("سعر البيع") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(cost, { cost = it }, label = { Text("التكلفة") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(minQty, { minQty = it }, label = { Text("حد إعادة الطلب") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addProduct(name, sku, unit, price, cost, minQty, false)
                    showItem = false
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showItem = false }) { Text("إلغاء") } },
        )
    }

    countFor?.let { pid ->
        AlertDialog(
            onDismissRequest = { countFor = null },
            title = { Text("تسوية جرد", fontSize = 17.sp) },
            text = {
                Column {
                    Text("أدخل الكمية الفعلية بعد العدّ — الفرق يُرحَّل تلقائياً.", fontSize = 12.sp, color = c.text2)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(countedQty, { countedQty = it }, label = { Text("الكمية المعدودة") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.stockCount(pid, countedQty)
                    countFor = null
                }) { Text("ترحيل التسوية") }
            },
            dismissButton = { TextButton(onClick = { countFor = null }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun WarehousesTab(vm: MidadViewModel, s: UiState) {
    val c = LocalMizan.current
    var showWh by remember { mutableStateOf(false) }
    var showTransfer by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showWh = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                Text("مستودع جديد", fontSize = 12.sp)
            }
            Button(onClick = { showTransfer = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                Text("تحويل", fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(s.warehouses) { w ->
                MizanCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(w.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                            if (w.isDefault) Text("افتراضي", fontSize = 10.sp, color = c.mint)
                            Text("الكمية ${w.totalQty}", fontSize = 12.sp, color = c.text2)
                        }
                        Text(w.totalValue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.blue)
                    }
                }
            }
            if (s.warehouses.isEmpty()) item { EmptyHint("لا مستودعات") }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    if (showWh) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showWh = false },
            title = { Text("مستودع جديد", fontSize = 17.sp) },
            text = { OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { vm.addWarehouse(name); showWh = false }) { Text("حفظ") } },
            dismissButton = { TextButton(onClick = { showWh = false }) { Text("إلغاء") } },
        )
    }

    if (showTransfer) {
        var productId by remember { mutableStateOf(s.items.firstOrNull()?.id ?: "") }
        var from by remember { mutableStateOf(s.warehouses.firstOrNull()?.id ?: "") }
        var to by remember { mutableStateOf(s.warehouses.getOrNull(1)?.id ?: "") }
        var qty by remember { mutableStateOf("1") }
        AlertDialog(
            onDismissRequest = { showTransfer = false },
            title = { Text("تحويل بين مستودعين", fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الصنف", fontSize = 11.sp, color = c.text3)
                    s.items.take(10).forEach { item ->
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (productId == item.id) c.primary.copy(alpha = 0.12f) else c.surface2, RoundedCornerShape(10.dp))
                                .clickable { productId = item.id }
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(item.name, fontSize = 12.sp, color = c.text)
                            Text("متاح ${item.available}", fontSize = 10.sp, color = c.text3)
                        }
                    }
                    Text("من مستودع", fontSize = 11.sp, color = c.text3)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        s.warehouses.forEach { w ->
                            Text(
                                w.name,
                                fontSize = 11.sp,
                                color = if (from == w.id) c.primary else c.text2,
                                modifier = Modifier
                                    .background(if (from == w.id) c.primary.copy(alpha = 0.12f) else c.surface2, RoundedCornerShape(8.dp))
                                    .clickable { from = w.id }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                    Text("إلى مستودع", fontSize = 11.sp, color = c.text3)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        s.warehouses.forEach { w ->
                            Text(
                                w.name,
                                fontSize = 11.sp,
                                color = if (to == w.id) c.primary else c.text2,
                                modifier = Modifier
                                    .background(if (to == w.id) c.primary.copy(alpha = 0.12f) else c.surface2, RoundedCornerShape(8.dp))
                                    .clickable { to = w.id }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                    OutlinedTextField(qty, { qty = it }, label = { Text("الكمية") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.transferStock(productId, from, to, qty)
                    showTransfer = false
                }) { Text("تحويل") }
            },
            dismissButton = { TextButton(onClick = { showTransfer = false }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun BatchesTab(vm: MidadViewModel, s: UiState) {
    val c = LocalMizan.current
    var showBatch by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("قيمة المعرّض للتلف", s.atRisk, modifier = Modifier.weight(1f), accent = c.coral)
            StatCard("دفعات", "${s.batches.size}", modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = { showBatch = true }, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(14.dp)) {
            Text("استلام دفعة", fontSize = 13.sp)
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(s.batches) { b ->
                MizanCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(b.productName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                            Text("الكمية ${b.qty} · تنتهي ${b.expiry}", fontSize = 11.sp, color = c.text2)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(b.value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                            Text(
                                when (b.status) {
                                    "EXPIRED" -> "منتهية"
                                    "SOON" -> "قريبة الانتهاء"
                                    else -> "صالحة"
                                },
                                fontSize = 10.sp,
                                color = when (b.status) {
                                    "EXPIRED" -> c.coral
                                    "SOON" -> c.amber
                                    else -> c.mint
                                },
                            )
                        }
                    }
                }
            }
            if (s.batches.isEmpty()) item { EmptyHint("لا دفعات مسجلة") }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    if (showBatch) {
        var productId by remember { mutableStateOf(s.items.firstOrNull()?.id ?: "") }
        var qty by remember { mutableStateOf("1") }
        var cost by remember { mutableStateOf("") }
        var expiry by remember { mutableStateOf("") }
        var wh by remember { mutableStateOf(s.warehouses.firstOrNull()?.id ?: "main") }
        AlertDialog(
            onDismissRequest = { showBatch = false },
            title = { Text("استلام دفعة", fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الصنف", fontSize = 11.sp, color = c.text3)
                    s.items.take(8).forEach { item ->
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (productId == item.id) c.primary.copy(alpha = 0.12f) else c.surface2, RoundedCornerShape(10.dp))
                                .clickable { productId = item.id }
                                .padding(8.dp),
                        ) { Text(item.name, fontSize = 12.sp, color = c.text) }
                    }
                    OutlinedTextField(qty, { qty = it }, label = { Text("الكمية") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(cost, { cost = it }, label = { Text("تكلفة الوحدة") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(expiry, { expiry = it }, label = { Text("تاريخ الصلاحية YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        s.warehouses.forEach { w ->
                            Text(
                                w.name,
                                fontSize = 11.sp,
                                color = if (wh == w.id) c.primary else c.text2,
                                modifier = Modifier
                                    .background(if (wh == w.id) c.primary.copy(alpha = 0.12f) else c.surface2, RoundedCornerShape(8.dp))
                                    .clickable { wh = w.id }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addBatch(productId, qty, cost, expiry, wh)
                    showBatch = false
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showBatch = false }) { Text("إلغاء") } },
        )
    }
}

// ── المالية ─────────────────────────────────────────────────────
@Composable
fun FinanceScreen(vm: MidadViewModel, s: UiState) {
    val c = LocalMizan.current
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf(
        "الميزان", "قائمة الدخل", "الميزانية", "التدفق النقدي",
        "الضريبة", "الإقرار", "الأعمار", "البنك", "الموازنات",
    )

    ScreenScaffold("المالية", if (s.balanced) "الميزان متوازن ✓" else "⚠ الميزان غير متوازن") {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp, containerColor = c.surface, divider = {}) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontSize = 11.sp) })
            }
        }
        Spacer(Modifier.height(12.dp))

        when (tab) {
            0 -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(s.trialRows) { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${r.code} ${r.name}", fontSize = 12.sp, color = c.text2, modifier = Modifier.weight(1f))
                        Text(r.debit, fontSize = 12.sp, color = c.text, modifier = Modifier.width(78.dp), textAlign = TextAlign.End)
                        Text(r.credit, fontSize = 12.sp, color = c.text, modifier = Modifier.width(78.dp), textAlign = TextAlign.End)
                    }
                    Divider(color = c.stroke)
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الإجمالي", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(s.totalDebit, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(78.dp), textAlign = TextAlign.End)
                        Text(s.totalCredit, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(78.dp), textAlign = TextAlign.End)
                    }
                }
            }

            1 -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ReportRow("صافي المبيعات", s.incomeNetRevenue, c.text)
                ReportRow("تكلفة المبيعات", "( ${s.incomeCogs} )", c.text2)
                Divider(color = c.stroke)
                ReportRow("الربح الإجمالي", s.incomeGross, c.mint)
                Divider(color = c.stroke)
                ReportRow("صافي الربح", s.incomeNet, c.primary)
            }

            2 -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ReportRow("إجمالي الأصول", s.bsAssets, c.blue)
                ReportRow("إجمالي الالتزامات", s.bsLiabilities, c.coral)
                ReportRow("حقوق الملكية", s.bsEquity, c.mint)
                ReportRow("منها أرباح مبقاة", s.bsRetained, c.text2)
                Divider(color = c.stroke)
                Text(
                    if (s.bsBalanced) "الميزانية متوازنة ✓" else "⚠ الميزانية غير متوازنة",
                    fontSize = 12.sp,
                    color = if (s.bsBalanced) c.mint else c.coral,
                )
            }

            3 -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ReportRow("رصيد النقد أول المدة", s.cfOpening, c.text2)
                ReportRow("التشغيلي", s.cfOperating, c.mint)
                ReportRow("الاستثماري", s.cfInvesting, c.amber)
                ReportRow("التمويلي", s.cfFinancing, c.blue)
                Divider(color = c.stroke)
                ReportRow("رصيد النقد آخر المدة", s.cfClosing, c.primary)
                Text(
                    if (s.cfVerified) "مطابق لرصيد الصندوق والبنك ✓" else "⚠ فرق غير مفسَّر",
                    fontSize = 12.sp,
                    color = if (s.cfVerified) c.mint else c.coral,
                )
            }

            4 -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ReportRow("صافي الضريبة المستحقة", s.taxPayable, c.amber)
                Text("تُحسب من القيود مباشرة: مخرجات − مدخلات، بلا يدوية.", fontSize = 12.sp, color = c.text3)
            }

            5 -> Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("الإقرار الضريبي", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                        Text(if (s.vatIsCredit) "رصيد دائن يُرحَّل" else "صافي مستحق", fontSize = 11.sp, color = c.text3)
                    }
                    Button(onClick = { vm.buildTaxPrint() }, shape = RoundedCornerShape(12.dp)) {
                        Text("طباعة", fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(s.vatBoxes) { box ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${box.code}. ${box.label}", fontSize = 12.sp, color = c.text2, modifier = Modifier.weight(1f))
                            Text(
                                com.example.midad.core.Money.format(box.amountMinor, s.currency),
                                fontSize = 12.sp,
                                fontWeight = if (box.code == 11) FontWeight.Bold else FontWeight.Normal,
                                color = if (box.code == 11) c.primary else c.text,
                                modifier = Modifier.width(110.dp),
                                textAlign = TextAlign.End,
                            )
                        }
                        Divider(color = c.stroke)
                    }
                }
            }

            6 -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ReportRow("١-٣٠ يوم", s.aging30, c.mint)
                ReportRow("٣١-٦٠ يوم", s.aging60, c.amber)
                ReportRow("أكثر من ٦٠ يوم", s.aging90, c.coral)
            }

            7 -> BankTab(vm, s, Modifier.weight(1f))

            else -> BudgetsTab(vm, s, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ReportRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, color = LocalMizan.current.text2)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** عرض سعر — يُحفظ بلا قيد ولا مخزون، ويُحوَّل لاحقاً إلى فاتورة */
@Composable
fun QuoteDialog(vm: MidadViewModel, s: UiState, onClose: () -> Unit) {
    DocEditorDialog(DocKind.QUOTE, vm, s, asQuote = true, onClose = onClose)
}
