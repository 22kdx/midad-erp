package com.example.midad.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import com.example.midad.core.Contracting
import com.example.midad.core.Money
import com.example.midad.core.Restaurant
import com.example.midad.ui.MidadViewModel
import com.example.midad.ui.UiState
import com.example.midad.ui.components.*
import com.example.midad.ui.theme.LocalMizan

/**
 * التحليلات والوحدات القطاعية: مبيعات بحسب الصنف/العميل/الشهر،
 * طاولات المطعم، وحاسبة مستخلص المقاولات.
 */
@Composable
fun AnalyticsScreen(vm: MidadViewModel, s: UiState) {
    val c = LocalMizan.current
    val context = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("الأصناف", "العملاء", "الشهور", "الطاولات", "المستخلص")

    ScreenScaffold("التحليلات", "أرقام من المستندات لا من تقديرات") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("صافي المبيعات", s.analyticsNet, modifier = Modifier.weight(1f))
            StatCard("الربح", s.analyticsProfit, modifier = Modifier.weight(1f), accent = c.mint)
            StatCard("فواتير", "${s.analyticsDocs}", modifier = Modifier.weight(1f), accent = c.blue)
        }
        Spacer(Modifier.height(10.dp))
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp, containerColor = c.surface, divider = {}) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontSize = 12.sp) })
            }
        }
        Spacer(Modifier.height(10.dp))

        when (tab) {
            0, 1, 2 -> {
                val rows = when (tab) {
                    0 -> s.topProducts
                    1 -> s.salesByParty
                    else -> s.salesByMonth
                }
                Column(Modifier.weight(1f)) {
                    if (rows.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                val csv = vm.analyticsCsv()
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_SUBJECT, "تحليلات المبيعات")
                                    putExtra(Intent.EXTRA_TEXT, csv)
                                }
                                context.startActivity(Intent.createChooser(send, "مشاركة التقرير"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("تصدير CSV", fontSize = 12.sp) }
                        Spacer(Modifier.height(8.dp))
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(rows) { row ->
                            MizanCard {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text(row.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                                        Text(
                                            "الكمية ${row.qtyMilli / 1000.0} · ${row.docs} مستند",
                                            fontSize = 10.sp, color = c.text3,
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(Money.format(row.netMinor, s.currency), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                                        Text(
                                            "ربح ${Money.format(row.profitMinor, s.currency)} · ${row.marginPct}%",
                                            fontSize = 10.sp,
                                            color = if (row.profitMinor < 0) c.coral else c.mint,
                                        )
                                    }
                                }
                            }
                        }
                        if (rows.isEmpty()) item { EmptyHint("لا بيانات مبيعات بعد") }
                        item { Spacer(Modifier.height(20.dp)) }
                    }
                }
            }

            3 -> TablesTab(vm, s)

            else -> ProgressBillTab(s)
        }
    }
}

@Composable
private fun TablesTab(vm: MidadViewModel, s: UiState) {
    val c = LocalMizan.current
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Button(
            onClick = { showAdd = true },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(14.dp),
        ) { Text("طاولة جديدة", fontSize = 13.sp) }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(s.tables) { t ->
                MizanCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                            Text(
                                when (t.status) {
                                    "FREE" -> "متاحة"
                                    "RESERVED" -> "محجوزة"
                                    "OCCUPIED" -> "مشغولة"
                                    "BILLED" -> "بانتظار الدفع"
                                    else -> t.status
                                },
                                fontSize = 11.sp,
                                color = when (t.status) {
                                    "FREE" -> c.mint
                                    "RESERVED" -> c.amber
                                    "BILLED" -> c.primary
                                    else -> c.coral
                                },
                            )
                        }
                        Row {
                            when (t.status) {
                                "FREE" -> {
                                    TextButton(onClick = { vm.tableAction(t.id, Restaurant.TableAction.SEAT) }) { Text("إجلاس", fontSize = 11.sp) }
                                    TextButton(onClick = { vm.tableAction(t.id, Restaurant.TableAction.RESERVE) }) { Text("حجز", fontSize = 11.sp) }
                                }
                                "RESERVED" -> {
                                    TextButton(onClick = { vm.tableAction(t.id, Restaurant.TableAction.SEAT) }) { Text("إجلاس", fontSize = 11.sp) }
                                    TextButton(onClick = { vm.tableAction(t.id, Restaurant.TableAction.CANCEL) }) { Text("إلغاء", fontSize = 11.sp, color = c.coral) }
                                }
                                "OCCUPIED" -> {
                                    TextButton(onClick = { vm.tableAction(t.id, Restaurant.TableAction.BILL) }) { Text("الفاتورة", fontSize = 11.sp) }
                                }
                                else -> {
                                    TextButton(onClick = { vm.tableAction(t.id, Restaurant.TableAction.PAY) }) { Text("دفع وتحرير", fontSize = 11.sp, color = c.mint) }
                                }
                            }
                        }
                    }
                }
            }
            if (s.tables.isEmpty()) item { EmptyHint("لا طاولات — أضف طاولة لبدء الخدمة") }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var seats by remember { mutableStateOf("4") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("طاولة جديدة", fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("اسم/رقم الطاولة") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(seats, { seats = it }, label = { Text("عدد المقاعد") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            },
            confirmButton = { TextButton(onClick = { vm.addTable(name, seats); showAdd = false }) { Text("حفظ") } },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun ProgressBillTab(s: UiState) {
    val c = LocalMizan.current
    var contract by remember { mutableStateOf("") }
    var previous by remember { mutableStateOf("0") }
    var executed by remember { mutableStateOf("") }
    var retention by remember { mutableStateOf("10") }
    var advance by remember { mutableStateOf("0") }
    var recoveryPct by remember { mutableStateOf("20") }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<com.example.midad.core.ProgressBill?>(null) }

    Column(Modifier.fillMaxSize()) {
        Text("حاسبة مستخلص المقاولات", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(contract, { contract = it }, label = { Text("قيمة العقد") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(previous, { previous = it }, label = { Text("المنفَّذ سابقاً") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(executed, { executed = it }, label = { Text("المنفَّذ في هذا المستخلص") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(retention, { retention = it }, label = { Text("محتجز %") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(recoveryPct, { recoveryPct = it }, label = { Text("استرداد %") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(advance, { advance = it }, label = { Text("رصيد الدفعة المقدمة") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                try {
                    result = Contracting.progressBill(
                        contractMinor = Money.parse(contract.ifBlank { "0" }, s.currency),
                        previouslyExecutedMinor = Money.parse(previous.ifBlank { "0" }, s.currency),
                        executedMinor = Money.parse(executed.ifBlank { "0" }, s.currency),
                        retentionPct = retention.ifBlank { "0" },
                        advanceBalanceMinor = Money.parse(advance.ifBlank { "0" }, s.currency),
                        advanceRecoveryPct = recoveryPct.ifBlank { "0" },
                    )
                    error = null
                } catch (e: Exception) {
                    error = e.message
                    result = null
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) { Text("احسب") }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 12.sp, color = c.coral)
        }

        result?.let { r ->
            Spacer(Modifier.height(12.dp))
            MizanCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("قيمة التنفيذ", fontSize = 12.sp, color = c.text2)
                    Text(Money.format(r.executedMinor, s.currency), fontSize = 13.sp, color = c.text)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("المحتجز", fontSize = 12.sp, color = c.text2)
                    Text(Money.format(r.retentionMinor, s.currency), fontSize = 13.sp, color = c.amber)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("استرداد الدفعة", fontSize = 12.sp, color = c.text2)
                    Text(Money.format(r.advanceRecoveryMinor, s.currency), fontSize = 13.sp, color = c.blue)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("الصافي المستحق", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.text)
                    Text(Money.format(r.payableMinor, s.currency), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.primary)
                }
                Spacer(Modifier.height(6.dp))
                Text("المتبقي من العقد: ${Money.format(r.remainingContractMinor, s.currency)}", fontSize = 11.sp, color = c.text3, textAlign = TextAlign.Start)
            }
        }
    }
}
