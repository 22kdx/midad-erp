package com.example.midad.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.midad.core.Cheques
import com.example.midad.ui.MidadViewModel
import com.example.midad.ui.UiState
import com.example.midad.ui.components.*
import com.example.midad.ui.theme.LocalMizan
import java.time.LocalDate

/**
 * السندات والشيكات — آلة حالات الشيك تمنع أي انتقال غير محاسبي،
 * وكل إجراء يُرحَّل قيداً عبر النواة نفسها.
 */
@Composable
fun VouchersScreen(vm: MidadViewModel, s: UiState) {
    val c = LocalMizan.current
    var incoming by remember { mutableStateOf(true) }
    var amount by remember { mutableStateOf("") }
    var viaBank by remember { mutableStateOf(false) }
    var showCheque by remember { mutableStateOf(false) }

    ScreenScaffold("السندات والشيكات", "قبض وصرف وشيكات بحالات محكومة") {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                MizanCard {
                    SectionTitle("سند جديد")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(true to "قبض", false to "صرف").forEach { (v, label) ->
                            val selected = incoming == v
                            Text(
                                label,
                                fontSize = 13.sp,
                                color = if (selected) c.primary else c.text2,
                                modifier = Modifier
                                    .background(if (selected) c.primary.copy(alpha = 0.12f) else c.surface2, RoundedCornerShape(10.dp))
                                    .clickable { incoming = v }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        amount, { amount = it },
                        label = { Text("المبلغ") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(viaBank, { viaBank = it })
                        Text(if (incoming) "إلى البنك" else "من البنك", fontSize = 12.sp, color = c.text2)
                    }
                    if (amount.isNotBlank()) {
                        Text(vm.tafqeet(amount), fontSize = 11.sp, color = c.text3)
                        Spacer(Modifier.height(6.dp))
                    }
                    Button(
                        onClick = {
                            vm.postVoucher(incoming, amount, viaBank)
                            amount = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("ترحيل السند") }
                }
            }

            item {
                MizanCard {
                    SectionTitle("شيكات", "${s.cheques.size}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showCheque = true }, shape = RoundedCornerShape(14.dp)) { Text("شيك جديد") }
                        OutlinedButton(
                            onClick = { vm.printChequesPortfolio("ALL") },
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("طباعة الحافظة") }
                    }
                }
            }

            items(s.cheques) { ch ->
                MizanCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("${ch.number} · ${if (ch.incoming) "وارد" else "صادر"}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                            Text("استحقاق ${ch.dueDate}", fontSize = 12.sp, color = c.text2)
                            Text(
                                when (ch.status) {
                                    "RECEIVED" -> "مستلم"
                                    "DEPOSITED" -> "مودع"
                                    "CLEARED" -> "محقق"
                                    "BOUNCED" -> "مرتجع"
                                    else -> ch.status
                                },
                                fontSize = 11.sp,
                                color = when (ch.status) {
                                    "CLEARED" -> c.mint
                                    "BOUNCED" -> c.coral
                                    else -> c.text3
                                },
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(ch.amount, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                            Row {
                                if (ch.status == "RECEIVED") {
                                    TextButton(onClick = { vm.chequeAction(ch.id, Cheques.Action.DEPOSIT) }) {
                                        Text("إيداع", fontSize = 11.sp)
                                    }
                                }
                                if (ch.status == "RECEIVED" || ch.status == "DEPOSITED") {
                                    TextButton(onClick = { vm.chequeAction(ch.id, Cheques.Action.CLEAR) }) {
                                        Text("تحقق", fontSize = 11.sp, color = c.mint)
                                    }
                                    TextButton(onClick = { vm.chequeAction(ch.id, Cheques.Action.BOUNCE) }) {
                                        Text("ارتجاع", fontSize = 11.sp, color = c.coral)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (s.cheques.isEmpty()) item { EmptyHint("لا شيكات مسجلة") }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showCheque) {
        var number by remember { mutableStateOf("") }
        var chAmount by remember { mutableStateOf("") }
        var due by remember { mutableStateOf(LocalDate.now().plusDays(30).toString()) }
        var isIncoming by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showCheque = false },
            title = { Text("شيك جديد", fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(true to "وارد", false to "صادر").forEach { (v, label) ->
                            val selected = isIncoming == v
                            Text(
                                label,
                                fontSize = 12.sp,
                                color = if (selected) c.primary else c.text2,
                                modifier = Modifier
                                    .background(if (selected) c.primary.copy(alpha = 0.12f) else c.surface2, RoundedCornerShape(10.dp))
                                    .clickable { isIncoming = v }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                            )
                        }
                    }
                    OutlinedTextField(number, { number = it }, label = { Text("رقم الشيك") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(chAmount, { chAmount = it }, label = { Text("المبلغ") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(due, { due = it }, label = { Text("تاريخ الاستحقاق YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addCheque(number, chAmount, due, isIncoming, null)
                    showCheque = false
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showCheque = false }) { Text("إلغاء") } },
        )
    }
}

// ── الأصول الثابتة والرواتب ─────────────────────────────────────
@Composable
fun AssetsPayrollScreen(vm: MidadViewModel, s: UiState) {
    val c = LocalMizan.current
    var tab by remember { mutableStateOf(0) }
    var showAsset by remember { mutableStateOf(false) }
    var showEmp by remember { mutableStateOf(false) }

    ScreenScaffold("الأصول والرواتب", "إهلاك شهري ومسير رواتب بقيد واحد متوازن") {
        TabRow(selectedTabIndex = tab, containerColor = c.surface) {
            listOf("الأصول", "الرواتب").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontSize = 12.sp) })
            }
        }
        Spacer(Modifier.height(10.dp))

        if (tab == 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showAsset = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Text("أصل جديد", fontSize = 13.sp)
                }
                OutlinedButton(onClick = { vm.refresh() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Text("تحديث", fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(s.assets) { a ->
                    MizanCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(a.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                                Text("التكلفة ${a.cost} · العمر ${a.months} شهراً", fontSize = 11.sp, color = c.text2)
                                Text("مجمع الإهلاك ${a.accumulated}", fontSize = 11.sp, color = c.text3)
                                Text("منذ ${a.startPeriod} · آخر إهلاك ${a.lastPeriod ?: "—"}", fontSize = 10.sp, color = c.text3)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(a.book, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.blue)
                                TextButton(onClick = { vm.depreciateAsset(a.id) }) { Text("إهلاك الشهر", fontSize = 11.sp) }
                            }
                        }
                    }
                }
                if (s.assets.isEmpty()) item { EmptyHint("لا أصول مسجلة") }
                item { Spacer(Modifier.height(20.dp)) }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showEmp = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Text("موظف جديد", fontSize = 13.sp)
                }
                Button(onClick = { vm.runPayroll() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                    Text("ترحيل المسير", fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("إجمالي الاستحقاق", s.payrollGross, modifier = Modifier.weight(1f))
                StatCard("صافي المستحق", s.payrollNet, modifier = Modifier.weight(1f), accent = c.mint)
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(s.employees) { e ->
                    MizanCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(e.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                                Text(
                                    "أساسي ${com.example.midad.core.Money.format(e.basic, s.currency)} · بدلات ${com.example.midad.core.Money.format(e.allowances, s.currency)}",
                                    fontSize = 11.sp, color = c.text2,
                                )
                                Text("خصومات ${com.example.midad.core.Money.format(e.deductions, s.currency)}", fontSize = 11.sp, color = c.text3)
                            }
                            Text(com.example.midad.core.Money.format(e.net, s.currency), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.mint)
                        }
                    }
                }
                if (s.employees.isEmpty()) item { EmptyHint("لا موظفين مسجلين") }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }

    if (showAsset) {
        var name by remember { mutableStateOf("") }
        var cost by remember { mutableStateOf("") }
        var salvage by remember { mutableStateOf("0") }
        var months by remember { mutableStateOf("12") }
        var start by remember { mutableStateOf(LocalDate.now().toString().take(7)) }
        AlertDialog(
            onDismissRequest = { showAsset = false },
            title = { Text("أصل جديد", fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("اسم الأصل") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(cost, { cost = it }, label = { Text("التكلفة") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(salvage, { salvage = it }, label = { Text("القيمة المتبقية") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(months, { months = it }, label = { Text("العمر بالأشهر") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(start, { start = it }, label = { Text("بداية الإهلاك YYYY-MM") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addAsset(name, cost, salvage, months, start)
                    showAsset = false
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showAsset = false }) { Text("إلغاء") } },
        )
    }

    if (showEmp) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var basic by remember { mutableStateOf("") }
        var allowances by remember { mutableStateOf("0") }
        var deductions by remember { mutableStateOf("0") }
        AlertDialog(
            onDismissRequest = { showEmp = false },
            title = { Text("موظف جديد", fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(phone, { phone = it }, label = { Text("الهاتف") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    OutlinedTextField(basic, { basic = it }, label = { Text("الراتب الأساسي") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(allowances, { allowances = it }, label = { Text("البدلات") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(deductions, { deductions = it }, label = { Text("الخصومات والسلف") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addEmployee(name, phone, basic, allowances, deductions)
                    showEmp = false
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showEmp = false }) { Text("إلغاء") } },
        )
    }
}
