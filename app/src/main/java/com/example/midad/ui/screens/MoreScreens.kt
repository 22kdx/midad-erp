package com.example.midad.ui.screens

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.midad.ui.MidadViewModel
import com.example.midad.ui.UiState
import com.example.midad.ui.components.*
import com.example.midad.ui.theme.LocalMizan
import java.time.LocalDate

// ── العملاء والموردون ───────────────────────────────────────────
@Composable
fun PartiesScreen(vm: MidadViewModel, s: UiState) {
    val c = LocalMizan.current
    var tab by remember { mutableStateOf(0) }
    var showForm by remember { mutableStateOf(false) }

    ScreenScaffold("العملاء والموردون", "أرصدة مشتقة من المستندات لا مُدخلة يدوياً") {
        TabRow(selectedTabIndex = tab, containerColor = c.surface) {
            listOf("العملاء", "الموردون").forEachIndexed { i, t ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text("$t (${s.parties.count { it.kindLabel == (if (i == 0) "عميل" else "مورد") }})", fontSize = 12.sp) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { showForm = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
        ) { Text(if (tab == 0) "عميل جديد" else "مورد جديد", fontSize = 14.sp) }

        Spacer(Modifier.height(12.dp))
        val wanted = if (tab == 0) "عميل" else "مورد"
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(s.parties.filter { it.kindLabel == wanted }) { p ->
                MizanCard {
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.openStatement(p.id) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                            if (p.phone.isNotBlank()) Text(p.phone, fontSize = 12.sp, color = c.text3)
                            if (p.overLimit) Text("تجاوز الحد الائتماني", fontSize = 11.sp, color = c.coral)
                            Text("اضغط لعرض كشف الحساب", fontSize = 10.sp, color = c.text3)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(p.balance, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (p.overLimit) c.coral else c.text)
                            Text("الحد ${p.creditLimit}", fontSize = 11.sp, color = c.text3)
                        }
                    }
                }
            }
            if (s.parties.none { it.kindLabel == wanted }) item { EmptyHint("لا يوجد $wanted مسجّل بعد") }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    if (showForm) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var limit by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showForm = false },
            title = { Text(if (tab == 0) "عميل جديد" else "مورد جديد", fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(phone, { phone = it }, label = { Text("الهاتف") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    OutlinedTextField(limit, { limit = it }, label = { Text("الحد الائتماني") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    s.error?.let { Text(it, color = c.coral, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addParty(name, phone, tab == 0, limit)
                    showForm = false
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showForm = false; vm.dismissMessage() }) { Text("إلغاء") } },
        )
    }

    // كشف الحساب + التحصيل
    val stParty = s.statementPartyId
    if (stParty != null) {
        var amount by remember { mutableStateOf("") }
        var viaBank by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { vm.closeStatement() },
            title = { Text("كشف حساب: ${s.statementPartyName}", fontSize = 15.sp) },
            text = {
                Column(Modifier.heightIn(max = 420.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الرصيد المستحق", fontSize = 12.sp, color = c.text2)
                        Text(s.statementBalance, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.primary)
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 220.dp)) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("المستند", fontSize = 11.sp, color = c.text3, modifier = Modifier.weight(1f))
                                Text("مدين", fontSize = 11.sp, color = c.text3, modifier = Modifier.width(70.dp), textAlign = TextAlign.End)
                                Text("دائن", fontSize = 11.sp, color = c.text3, modifier = Modifier.width(70.dp), textAlign = TextAlign.End)
                                Text("الرصيد", fontSize = 11.sp, color = c.text3, modifier = Modifier.width(78.dp), textAlign = TextAlign.End)
                            }
                        }
                        items(s.statement) { r ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text("${r.label} ${r.number}", fontSize = 11.sp, color = c.text)
                                    Text(r.date, fontSize = 10.sp, color = c.text3)
                                }
                                Text(if (r.debit > 0) com.example.midad.core.Money.format(r.debit, s.currency) else "—", fontSize = 10.sp, modifier = Modifier.width(70.dp), textAlign = TextAlign.End, color = c.text2)
                                Text(if (r.credit > 0) com.example.midad.core.Money.format(r.credit, s.currency) else "—", fontSize = 10.sp, modifier = Modifier.width(70.dp), textAlign = TextAlign.End, color = c.text2)
                                Text(com.example.midad.core.Money.format(r.balance, s.currency), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(78.dp), textAlign = TextAlign.End, color = c.text)
                            }
                            Divider(color = c.stroke)
                        }
                        if (s.statement.isEmpty()) item { EmptyHint("لا حركات على هذا الطرف") }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("تحصيل (يوزَّع على الأقدم أولاً)", fontSize = 12.sp, color = c.text2)
                    OutlinedTextField(amount, { amount = it }, label = { Text("المبلغ") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(viaBank, { viaBank = it })
                        Text("إلى البنك", fontSize = 12.sp, color = c.text2)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.collectFrom(stParty, amount, viaBank)
                    amount = ""
                }) { Text("تحصيل") }
            },
            dismissButton = { TextButton(onClick = { vm.closeStatement() }) { Text("إغلاق") } },
        )
    }
}

// ── التقارير والرقابة ───────────────────────────────────────────
@Composable
fun ReportsScreen(vm: MidadViewModel, s: UiState) {
    val c = LocalMizan.current
    ScreenScaffold("التقارير والرقابة", "كل رقم قابل للتتبع إلى قيده") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("١-٣٠", s.aging30, modifier = Modifier.weight(1f), accent = c.mint)
            StatCard("٣١-٦٠", s.aging60, modifier = Modifier.weight(1f), accent = c.amber)
            StatCard("+٦٠", s.aging90, modifier = Modifier.weight(1f), accent = c.coral)
        }
        Spacer(Modifier.height(12.dp))
        SectionTitle("القيود", "${s.entries.size}")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(s.entries) { e ->
                MizanCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${e.number} · ${e.date}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                            Text(e.description, fontSize = 11.sp, color = c.text2)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(e.amount, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                            if (e.status != "REVERSED") {
                                TextButton(onClick = { vm.reverseEntry(e.id) }) {
                                    Text("عكس", fontSize = 11.sp, color = c.coral)
                                }
                            } else {
                                Text("معكوس", fontSize = 11.sp, color = c.text3)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

// ── الإعدادات (منشأة · أمان · مستخدمون · سجل · نسخ) ────────────
@Composable
fun SettingsScreen(vm: MidadViewModel, s: UiState) {
    val c = LocalMizan.current
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("المنشأة", "مالية", "الأمان", "المستخدمون", "السجل", "النسخ")

    ScreenScaffold("الإعدادات", "بياناتك على جهازك — نسخة مشفّرة تحميها") {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp, containerColor = c.surface, divider = {}) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontSize = 12.sp) })
            }
        }
        Spacer(Modifier.height(10.dp))
        when (tab) {
            0 -> CompanyTab(vm, s)
            1 -> FinanceSettingsTab(vm, s)
            2 -> SecurityTab(vm, s)
            3 -> UsersTab(vm, s)
            4 -> AuditTab(s)
            else -> BackupTab(vm, s)
        }
    }
}

@Composable
private fun CompanyTab(vm: MidadViewModel, s: UiState) {
    val c = LocalMizan.current
    var name by remember(s.companyName) { mutableStateOf(s.companyName) }
    var tax by remember(s.companyTax) { mutableStateOf(s.companyTax) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MizanCard {
            SectionTitle("بيانات المنشأة")
            OutlinedTextField(name, { name = it }, label = { Text("اسم المنشأة") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(tax, { tax = it }, label = { Text("الرقم الضريبي") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.saveCompany(name, tax) }, shape = RoundedCornerShape(14.dp)) { Text("حفظ") }
        }
        MizanCard {
            SectionTitle("مقاس الطباعة")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("A4", "A5", "THERMAL_80").forEach { p ->
                    val selected = s.paper == p
                    Text(
                        if (p == "THERMAL_80") "حراري" else p,
                        fontSize = 12.sp,
                        color = if (selected) c.primary else c.text2,
                        modifier = Modifier
                            .background(if (selected) c.primary.copy(alpha = 0.12f) else c.surface2, RoundedCornerShape(10.dp))
                            .clickable { vm.setPaper(p) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
        MizanCard {
            SectionTitle("حالة النظام")
            InfoRow("المستخدم", s.userName.ifBlank { "المالك" })
            InfoRow("الدور", s.role.label)
            InfoRow("حالة الميزان", if (s.balanced) "متوازن ✓" else "غير متوازن ⚠")
            InfoRow("القيود", "${s.entries.size}")
            InfoRow("المستندات", "${s.documents.size}")
            InfoRow("الأصناف", "${s.stocks.size}")
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun FinanceSettingsTab(vm: MidadViewModel, s: UiState) {
    val c = LocalMizan.current
    var taxPct by remember(s.taxRatePct) { mutableStateOf(s.taxRatePct) }
    var inclusive by remember(s.pricesTaxInclusive) { mutableStateOf(s.pricesTaxInclusive) }
    var base by remember(s.currency) { mutableStateOf(s.currency) }
    var outputTax by remember(s.outputTaxAccountId) { mutableStateOf(s.outputTaxAccountId) }
    var inputTax by remember(s.inputTaxAccountId) { mutableStateOf(s.inputTaxAccountId) }
    var code by remember { mutableStateOf("") }
    var decimals by remember { mutableStateOf("2") }
    var fxCurrency by remember { mutableStateOf(s.currencies.firstOrNull { !it.isBase }?.code.orEmpty()) }
    var fxRate by remember { mutableStateOf("") }
    var fxDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var showCash by remember { mutableStateOf(false) }
    var editingCash by remember { mutableStateOf<com.example.midad.ui.CashAccountRowUi?>(null) }
    var cashRename by remember { mutableStateOf("") }
    var editingAccount by remember { mutableStateOf<com.example.midad.ui.ChartAccountRowUi?>(null) }
    var rename by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MizanCard {
            SectionTitle("الضريبة والعملة الأساسية")
            OutlinedTextField(taxPct, { taxPct = it }, label = { Text("نسبة الضريبة الافتراضية %") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("الأسعار شاملة الضريبة", fontSize = 13.sp, color = c.text)
                Switch(checked = inclusive, onCheckedChange = { inclusive = it })
            }
            Text("العملة الأساسية", fontSize = 12.sp, color = c.text2)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                s.currencies.forEach { cur ->
                    FilterChip(selected = base == cur.code, onClick = { base = cur.code }, label = { Text(cur.code) })
                }
            }
            Text("حساب ضريبة المخرجات", fontSize = 12.sp, color = c.text2)
            AccountSelector(s, outputTax) { outputTax = it }
            Text("حساب ضريبة المدخلات", fontSize = 12.sp, color = c.text2)
            AccountSelector(s, inputTax) { inputTax = it }
            Button(onClick = { vm.saveFinancialSettings(taxPct, inclusive, outputTax, inputTax, base) }) { Text("حفظ إعدادات الضريبة") }
        }

        MizanCard {
            SectionTitle("العملات والخانات العشرية")
            s.currencies.forEach { cur ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${cur.code} · ${cur.decimals} خانات" + if (cur.isBase) " · أساسية" else "", fontSize = 12.sp, color = c.text)
                    if (!cur.isBase) TextButton(onClick = { vm.removeCurrency(cur.code) }) { Text("حذف") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(code, { code = it.uppercase() }, label = { Text("رمز العملة") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(decimals, { decimals = it.filter(Char::isDigit).take(1) }, label = { Text("الخانات") }, modifier = Modifier.width(90.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            }
            Button(onClick = { vm.addCurrency(code, decimals.toIntOrNull() ?: 2); code = "" }) { Text("إضافة عملة") }
        }

        MizanCard {
            SectionTitle("أسعار الصرف المؤرخة")
            s.fxRates.forEach { rate -> Text("${rate.currency} · ${rate.rate} ${s.currency} · ${rate.date}", fontSize = 12.sp, color = c.text2) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.currencies.filterNot { it.isBase }.forEach { cur ->
                    FilterChip(selected = fxCurrency == cur.code, onClick = { fxCurrency = cur.code }, label = { Text(cur.code) })
                }
            }
            OutlinedTextField(fxRate, { fxRate = it }, label = { Text("السعر مقابل ${s.currency} (حتى ٦ منازل)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            OutlinedTextField(fxDate, { fxDate = it }, label = { Text("التاريخ YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = { vm.saveFxRate(fxCurrency, fxRate, fxDate); fxRate = "" }) { Text("حفظ سعر الصرف") }
            Text("يُستخدم أحدث سعر مؤرخ تلقائياً عند اختيار العملة في محرر الفاتورة.", fontSize = 11.sp, color = c.text3)
        }

        MizanCard {
            SectionTitle("الصناديق والحسابات البنكية")
            Button(onClick = { showCash = true }) { Text("إضافة صندوق / حساب بنكي") }
            s.cashAccounts.forEach { account ->
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text("${account.name} · ${if (account.type == "BANK") "بنك" else "صندوق"} · ${account.currency}", fontSize = 13.sp, color = c.text)
                    Text("${account.number.ifBlank { "بلا رقم" }} · رصيد افتتاحي ${account.opening}" + if (account.active) "" else " · معطّل", fontSize = 11.sp, color = c.text3)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { vm.setCashAccountDefault(account.id, true) }) { Text(if (account.defaultReceipt) "✓ افتراضي للقبض" else "افتراضي للقبض", fontSize = 10.sp) }
                        TextButton(onClick = { vm.setCashAccountDefault(account.id, false) }) { Text(if (account.defaultPayment) "✓ افتراضي للصرف" else "افتراضي للصرف", fontSize = 10.sp) }
                        TextButton(onClick = { editingCash = account; cashRename = account.name }) { Text("إعادة تسمية", fontSize = 10.sp) }
                        TextButton(onClick = { if (account.active) vm.deactivateCashAccount(account.id) else vm.activateCashAccount(account.id) }) { Text(if (account.active) "تعطيل" else "تفعيل", fontSize = 10.sp) }
                    }
                    Divider()
                }
            }
        }

        MizanCard {
            SectionTitle("دليل الحسابات")
            s.chartAccounts.filter { it.active }.forEach { account ->
                Row(Modifier.fillMaxWidth().clickable { editingAccount = account; rename = account.name }.padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("${account.code} · ${account.name}", fontSize = 12.sp, color = c.text)
                        Text(if (account.movements > 0) "${account.movements} حركة · لا يمكن التعطيل" else "بلا حركة", fontSize = 10.sp, color = c.text3)
                    }
                    Text("تعديل", fontSize = 11.sp, color = c.primary)
                }
            }
        }
        s.error?.let { Text(it, color = c.coral, fontSize = 12.sp) }
        s.message?.let { Text(it, color = c.mint, fontSize = 12.sp) }
        Spacer(Modifier.height(20.dp))
    }

    if (showCash) AddCashAccountDialog(vm, s) { showCash = false }
    editingCash?.let { selected ->
        AlertDialog(onDismissRequest = { editingCash = null }, title = { Text("إعادة تسمية ${selected.name}") },
            text = { OutlinedTextField(cashRename, { cashRename = it }, label = { Text("الاسم") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { vm.renameCashAccount(selected.id, cashRename); editingCash = null }) { Text("حفظ") } },
            dismissButton = { TextButton(onClick = { editingCash = null }) { Text("إلغاء") } })
    }
    editingAccount?.let { selected ->
        AlertDialog(onDismissRequest = { editingAccount = null }, title = { Text("تعديل الحساب ${selected.code}") },
            text = { OutlinedTextField(rename, { rename = it }, label = { Text("اسم الحساب") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { vm.updateChartAccount(selected.id, rename, false); editingAccount = null }) { Text("حفظ الاسم") } },
            dismissButton = { Row {
                TextButton(onClick = { vm.updateChartAccount(selected.id, rename, true); editingAccount = null }) { Text("تعطيل") }
                TextButton(onClick = { editingAccount = null }) { Text("إغلاق") }
            } })
    }
}

@Composable
private fun AccountSelector(s: UiState, selected: String, onSelect: (String) -> Unit) {
    val options = s.chartAccounts.filter { it.active && it.code.startsWith("22") }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        options.forEach { account ->
            FilterChip(selected = selected == account.id, onClick = { onSelect(account.id) }, label = { Text("${account.code} ${account.name}", fontSize = 10.sp) }, modifier = Modifier.padding(end = 4.dp))
        }
    }
}

@Composable
private fun AddCashAccountDialog(vm: MidadViewModel, s: UiState, onClose: () -> Unit) {
    val c = LocalMizan.current
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(s.currency) }
    var opening by remember { mutableStateOf("0") }
    var bank by remember { mutableStateOf(false) }
    var receipt by remember { mutableStateOf(false) }
    var payment by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onClose, title = { Text("إضافة حساب مالي") }, text = {
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, modifier = Modifier.fillMaxWidth())
            Row { FilterChip(selected = !bank, onClick = { bank = false }, label = { Text("صندوق") }); Spacer(Modifier.width(8.dp)); FilterChip(selected = bank, onClick = { bank = true }, label = { Text("حساب بنكي") }) }
            OutlinedTextField(number, { number = it }, label = { Text("رقم الحساب / IBAN") }, modifier = Modifier.fillMaxWidth())
            Row { s.currencies.forEach { cur -> FilterChip(selected = currency == cur.code, onClick = { currency = cur.code }, label = { Text(cur.code) }) } }
            OutlinedTextField(opening, { opening = it }, label = { Text("الرصيد الافتتاحي") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Row { Checkbox(receipt, { receipt = it }); Text("افتراضي للقبض", Modifier.align(Alignment.CenterVertically)); Checkbox(payment, { payment = it }); Text("افتراضي للصرف", Modifier.align(Alignment.CenterVertically)) }
            s.error?.let { Text(it, color = c.coral, fontSize = 11.sp) }
        }
    }, confirmButton = { TextButton(onClick = { vm.addCashAccount(name, if (bank) "BANK" else "CASH", number, currency, opening, receipt, payment); onClose() }) { Text("حفظ") } }, dismissButton = { TextButton(onClick = onClose) { Text("إلغاء") } })
}

@Composable
private fun SecurityTab(vm: MidadViewModel, s: UiState) {
    val c = LocalMizan.current
    var pin by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RemindersCard(vm, s)
        MizanCard {
            SectionTitle("رمز الدخول (PIN)", if (s.pinConfigured) "مُفعّل" else "غير مُفعّل")
            Text(
                "يُخزَّن الرمز بـ PBKDF2-HMAC-SHA256 مع ملح عشوائي — لا يُحفظ خاماً أبداً. " +
                    "بعد ٥ محاولات خاطئة يُقفل الدخول ١٥ دقيقة.",
                fontSize = 11.sp, color = c.text3,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                pin, { if (it.length <= 6) pin = it.filter { ch -> ch.isDigit() } },
                label = { Text("رمز من ٤ إلى ٦ أرقام") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.setupPin(pin, "المالك", s.userPhone) }, shape = RoundedCornerShape(14.dp)) { Text("تعيين") }
                OutlinedButton(onClick = { vm.lockNow() }, shape = RoundedCornerShape(14.dp)) { Text("قفل الآن") }
            }
        }
        MizanCard {
            SectionTitle("الصلاحيات الفعّالة")
            listOf(
                com.example.midad.core.Perm.SALE_EDIT,
                com.example.midad.core.Perm.PAYMENT_EDIT,
                com.example.midad.core.Perm.PURCHASE_EDIT,
                com.example.midad.core.Perm.PERIOD_CLOSE,
                com.example.midad.core.Perm.USER_MANAGE,
                com.example.midad.core.Perm.BACKUP_EXPORT,
            ).forEach { perm ->
                val allowed = com.example.midad.core.Rbac.allows(s.role, perm)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(perm.label, fontSize = 12.sp, color = c.text2)
                    Text(if (allowed) "مسموح" else "ممنوع", fontSize = 12.sp, color = if (allowed) c.mint else c.coral)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun UsersTab(vm: MidadViewModel, s: UiState) {
    val c = LocalMizan.current
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Button(
            onClick = { showAdd = true },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(14.dp),
        ) { Text("مستخدم جديد", fontSize = 13.sp) }
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(s.users) { u ->
                MizanCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(u.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                            Text(u.phone, fontSize = 12.sp, color = c.text3)
                        }
                        Text(u.roleLabel, fontSize = 12.sp, color = c.primary)
                    }
                }
            }
            if (s.users.isEmpty()) item { EmptyHint("لا مستخدمون — أضف مستخدماً برمز PIN") }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var pin by remember { mutableStateOf("") }
        var role by remember { mutableStateOf(com.example.midad.core.Role.CASHIER) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("مستخدم جديد", fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(phone, { phone = it }, label = { Text("الهاتف") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    OutlinedTextField(
                        pin, { if (it.length <= 6) pin = it.filter { ch -> ch.isDigit() } },
                        label = { Text("رمز PIN") }, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                    Text("الدور", fontSize = 11.sp, color = c.text3)
                    com.example.midad.core.Role.values().forEach { r ->
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (role == r) c.primary.copy(alpha = 0.12f) else c.surface2, RoundedCornerShape(10.dp))
                                .clickable { role = r }
                                .padding(8.dp),
                        ) { Text(r.label, fontSize = 12.sp, color = c.text) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addUser(name, phone, role, pin)
                    showAdd = false
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun AuditTab(s: UiState) {
    val c = LocalMizan.current
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(s.audit) { a ->
            MizanCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("${a.entity} · ${a.action}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                        Text(a.detail, fontSize = 11.sp, color = c.text2)
                    }
                    Text(a.at, fontSize = 10.sp, color = c.text3)
                }
            }
        }
        if (s.audit.isEmpty()) item { EmptyHint("لا سجلات بعد") }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun BackupTab(vm: MidadViewModel, s: UiState) {
    val c = LocalMizan.current
    var backupPass by remember { mutableStateOf("") }
    var restoreBlob by remember { mutableStateOf("") }
    val year = LocalDate.now().year
    val month = LocalDate.now().monthValue

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MizanCard {
            SectionTitle("نسخة احتياطية مشفّرة", s.lastBackupAt ?: "لم تُنشأ بعد")
            Text("AES-256-GCM بمفتاح من كلمة مرورك (PBKDF2) — بلا كلمة المرور لا يمكن فتح النسخة.", fontSize = 11.sp, color = c.text3)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(backupPass, { backupPass = it }, label = { Text("كلمة مرور النسخة") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.exportBackup(backupPass) }, shape = RoundedCornerShape(14.dp)) { Text("إنشاء نسخة") }
                OutlinedButton(onClick = { vm.restoreBackup(restoreBlob, backupPass) }, shape = RoundedCornerShape(14.dp)) { Text("استعادة") }
            }
            s.backupText?.let {
                Spacer(Modifier.height(8.dp))
                Text("النسخة جاهزة (${it.length} محرف) ومحفوظة في مجلد backup داخل التطبيق.", fontSize = 11.sp, color = c.mint)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                restoreBlob, { restoreBlob = it },
                label = { Text("الصق نص النسخة للاستعادة") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp),
            )
        }
        MizanCard {
            SectionTitle("إقفال الفترة")
            Text(
                "الفترة الحالية: $year-${month.toString().padStart(2, '0')} · الإقفال يُصفّر الإيراد والمصروف إلى الأرباح المبقاة.",
                fontSize = 11.sp, color = c.text3,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.closePeriod(year, month) }, shape = RoundedCornerShape(14.dp)) { Text("إقفال الفترة") }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val c = LocalMizan.current
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = c.text2)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c.text)
    }
}

// ── شاشة القفل ──────────────────────────────────────────────────
@Composable
fun LockScreen(vm: MidadViewModel, s: UiState) {
    val c = LocalMizan.current
    var pin by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().background(c.bg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("TAMM", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = c.primary)
        Spacer(Modifier.height(6.dp))
        Text("أدخل رمز الدخول", fontSize = 13.sp, color = c.text3)
        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(6) { i ->
                Box(
                    Modifier.size(14.dp).background(
                        if (i < pin.length) c.primary else c.surface2,
                        RoundedCornerShape(7.dp),
                    ),
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "مسح", "0", "دخول")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { key ->
                        Box(
                            Modifier
                                .size(78.dp)
                                .background(c.surface, RoundedCornerShape(18.dp))
                                .clickable {
                                    when (key) {
                                        "مسح" -> pin = if (pin.isNotEmpty()) pin.dropLast(1) else pin
                                        "دخول" -> { vm.unlock(pin); pin = "" }
                                        else -> { if (pin.length < 6) pin = pin + key }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                key,
                                fontSize = if (key.length > 1) 13.sp else 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (key == "دخول") c.primary else c.text,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        s.error?.let {
            Text(it, fontSize = 12.sp, color = c.coral)
            LaunchedEffect(it) { }
        }
        s.message?.let { Text(it, fontSize = 12.sp, color = c.mint) }
    }
}

// ── معاينة الطباعة ──────────────────────────────────────────────
@Composable
fun PrintPreviewScreen(html: String, title: String, onClose: () -> Unit) {
    val c = LocalMizan.current
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { webView?.let { printDocument(context, title, it) } },
                    shape = RoundedCornerShape(12.dp),
                ) { Text("طباعة / PDF", fontSize = 12.sp) }
                OutlinedButton(onClick = onClose, shape = RoundedCornerShape(12.dp)) { Text("إغلاق", fontSize = 12.sp) }
            }
        }
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                val wv = WebView(ctx)
                wv.webViewClient = WebViewClient()
                wv.settings.javaScriptEnabled = false
                wv.settings.loadWithOverviewMode = true
                wv.settings.useWideViewPort = true
                wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                webView = wv
                wv
            },
        )
    }
}

private fun printDocument(context: Context, title: String, webView: WebView) {
    val manager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val adapter = webView.createPrintDocumentAdapter(title)
    manager.print(
        title,
        adapter,
        PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build(),
    )
}

@Composable
private fun EmptyHintRow(text: String) {
    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 12.sp, color = LocalMizan.current.text3, textAlign = TextAlign.Center)
    }
}

/** تذكير يومي محلي — إشعار واحد يجمع الشيكات والدوريات والمستحقات */
@Composable
private fun RemindersCard(vm: MidadViewModel, s: UiState) {
    val c = LocalMizan.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    MizanCard {
        SectionTitle("التذكير اليومي", if (s.remindersOn) "مُفعّل" else "متوقف")
        Text(
            "إشعار محلي مرة يومياً: شيكات تستحق خلال ٣ أيام · فواتير دورية مستحقة · مستندات آجلة تجاوزت ٣٠ يوماً. " +
                "لا يخرج شيء من الجهاز.",
            fontSize = 11.sp, color = c.text3,
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (s.remindersOn) "يعمل" else "متوقف", fontSize = 12.sp, color = c.text2, modifier = Modifier.weight(1f))
            Switch(checked = s.remindersOn, onCheckedChange = { vm.setReminders(ctx.applicationContext, it) })
        }
    }
}
