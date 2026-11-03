package com.example.midad.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.midad.ui.MidadViewModel
import com.example.midad.ui.ScheduleRow
import com.example.midad.ui.UiState
import com.example.midad.ui.components.MizanCard
import com.example.midad.ui.components.SectionTitle
import com.example.midad.ui.theme.LocalMizan

/**
 * تسوية بنكية: كشف البنك يُدخل يدوياً، ويُطابق آلياً مع حركة حساب البنك في الأستاذ.
 * المطابقة نفسها في النواة (`core/Banking.kt`) ومُختبرة — هذه الشاشة عرض وإدخال فقط.
 */
@Composable
fun BankTab(vm: MidadViewModel, s: UiState, modifier: Modifier = Modifier) {
    val c = LocalMizan.current
    var showForm by remember { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MizanCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("بنود الكشف", fontSize = 11.sp, color = c.text3)
                    Text("${s.bankLines.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.text)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("مطابَقة", fontSize = 11.sp, color = c.text3)
                    Text("${s.bankMatched}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.primary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("غير مطابَقة", fontSize = 11.sp, color = c.text3)
                    Text("${s.bankUnmatched}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.text2)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("الفرق", fontSize = 11.sp, color = c.text3)
                    Text(s.bankDifference, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.text)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (s.bankBalanced) "الكشف مطابق تماماً لحركة البنك ✓" else "يوجد بنود غير مطابقة — راجع التلميحات أدناه",
                fontSize = 11.sp,
                color = if (s.bankBalanced) c.primary else c.text2,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { showForm = true },
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("بند في الكشف", fontSize = 12.sp) }
            OutlinedButton(
                onClick = { vm.reconcileBank() },
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("مطابقة تلقائية", fontSize = 12.sp) }
        }

        if (s.bankHints.isNotEmpty()) {
            SectionTitle("تلميحات التسوية", "${s.bankHints.size}")
            s.bankHints.take(4).forEach { hint ->
                Text("• $hint", fontSize = 11.sp, color = c.text2, modifier = Modifier.fillMaxWidth())
            }
        }

        SectionTitle("حركات الكشف", "${s.bankLines.size}")
        if (s.bankLines.isEmpty()) {
            Text(
                "لم تُدخل أي بند من كشف البنك بعد. أضف حركات الكشف ثم اضغط «مطابقة تلقائية».",
                fontSize = 12.sp,
                color = c.text3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(s.bankLines) { line ->
                MizanCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(line.description, fontSize = 12.sp, color = c.text)
                            Text(
                                line.date + if (line.matched) " · مطابق" else " · غير مطابق",
                                fontSize = 10.sp,
                                color = if (line.matched) c.primary else c.text3,
                            )
                        }
                        Text(line.amount, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                        TextButton(onClick = { vm.deleteBankLine(line.id) }) {
                            Text("حذف", fontSize = 11.sp, color = c.text3)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    if (showForm) AddBankLineDialog(vm) { showForm = false }
}

@Composable
private fun AddBankLineDialog(vm: MidadViewModel, onClose: () -> Unit) {
    val c = LocalMizan.current
    var date by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var outgoing by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("بند من كشف البنك", fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    date, { date = it },
                    label = { Text("التاريخ (سنة-شهر-يوم)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    desc, { desc = it },
                    label = { Text("الوصف") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    amount, { amount = it },
                    label = { Text("المبلغ") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(false to "إيداع", true to "سحب").forEach { (flag, label) ->
                        val selected = outgoing == flag
                        Text(
                            label,
                            fontSize = 12.sp,
                            color = if (selected) c.surface else c.text2,
                            modifier = Modifier
                                .weight(1f)
                                .background(if (selected) c.primary else c.surface2, RoundedCornerShape(10.dp))
                                .clickable { outgoing = flag }
                                .padding(vertical = 8.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val signed = if (outgoing) "-$amount" else amount
                vm.addBankLine(date, desc, signed)
                onClose()
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("إلغاء") } },
    )
}

/**
 * الفواتير الدورية: جدول واحد = عقد إيجار/اشتراك، وكل استحقاق يُرحَّل كمستند مستقل بقيده.
 */
@Composable
fun SchedulesDialog(vm: MidadViewModel, s: UiState, onClose: () -> Unit) {
    val c = LocalMizan.current
    var showForm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("الفواتير الدورية", fontSize = 17.sp) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "المستحق حتى اليوم: ${s.schedulesDue} مستنداً بقيمة ${s.schedulesDueValue}",
                    fontSize = 12.sp,
                    color = c.text2,
                )
                if (s.schedules.isEmpty()) {
                    Text("لا توجد جداول بعد.", fontSize = 12.sp, color = c.text3)
                }
                s.schedules.forEach { row -> ScheduleCard(vm, row) }
                if (showForm) {
                    Spacer(Modifier.height(4.dp))
                    ScheduleForm(vm, s) { showForm = false }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { showForm = true }) { Text("جدول جديد") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("إغلاق") } },
    )
}

@Composable
private fun ScheduleCard(vm: MidadViewModel, row: ScheduleRow) {
    val c = LocalMizan.current
    MizanCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${row.title} · ${row.partyName}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                Text(
                    "${row.kindLabel} · ${row.cycleLabel} · التالي: ${row.nextDate}",
                    fontSize = 10.sp,
                    color = c.text3,
                )
                Text(
                    "${row.amount} · مستحق ${row.dueCount}" + if (row.active) "" else " · موقوف",
                    fontSize = 10.sp,
                    color = if (row.dueCount > 0) c.primary else c.text3,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (row.dueCount > 0 && row.active) {
                    TextButton(onClick = { vm.runSchedule(row.id) }) { Text("تشغيل", fontSize = 11.sp) }
                }
                TextButton(onClick = { vm.toggleSchedule(row.id, !row.active) }) {
                    Text(if (row.active) "إيقاف" else "تفعيل", fontSize = 11.sp, color = c.text3)
                }
                TextButton(onClick = { vm.deleteSchedule(row.id) }) {
                    Text("حذف", fontSize = 11.sp, color = c.text3)
                }
            }
        }
    }
}

@Composable
private fun ScheduleForm(vm: MidadViewModel, s: UiState, onDone: () -> Unit) {
    val c = LocalMizan.current
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var tax by remember { mutableStateOf("15") }
    var startDate by remember { mutableStateOf("") }
    var dayOfMonth by remember { mutableStateOf("") }
    var cycle by remember { mutableStateOf("MONTHLY") }
    var kind by remember { mutableStateOf("INV") }
    var partyId by remember { mutableStateOf<String?>(null) }

    val cycles = listOf(
        "DAILY" to "يومي", "WEEKLY" to "أسبوعي", "MONTHLY" to "شهري",
        "QUARTERLY" to "ربع سنوي", "YEARLY" to "سنوي",
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(title, { title = it }, label = { Text("العنوان (إيجار مكتب…)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(amount, { amount = it }, label = { Text("المبلغ قبل الضريبة") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(tax, { tax = it }, label = { Text("نسبة الضريبة ٪") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(startDate, { startDate = it }, label = { Text("تاريخ البداية") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(dayOfMonth, { dayOfMonth = it }, label = { Text("يوم الشهر (اختياري)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            cycles.forEach { (code, label) ->
                val selected = cycle == code
                Text(
                    label,
                    fontSize = 10.sp,
                    color = if (selected) c.surface else c.text2,
                    modifier = Modifier
                        .weight(1f)
                        .background(if (selected) c.primary else c.surface2, RoundedCornerShape(8.dp))
                        .clickable { cycle = code }
                        .padding(vertical = 6.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("INV" to "مبيعات", "PINV" to "مشتريات").forEach { (code, label) ->
                val selected = kind == code
                Text(
                    label,
                    fontSize = 11.sp,
                    color = if (selected) c.surface else c.text2,
                    modifier = Modifier
                        .weight(1f)
                        .background(if (selected) c.primary else c.surface2, RoundedCornerShape(8.dp))
                        .clickable { kind = code }
                        .padding(vertical = 6.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }

        PartiesPicker(s.parties.map { it.id to "${it.name} · ${it.kindLabel}" }) { partyId = it }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                vm.addSchedule(
                    title = title,
                    partyId = partyId,
                    kind = kind,
                    cycle = cycle,
                    startDate = startDate,
                    dayOfMonth = dayOfMonth.toIntOrNull(),
                    productId = "service",
                    productName = title,
                    qtyMilli = 1_000,
                    amount = amount,
                    taxRate = tax,
                    onDone = onDone,
                )
            },
            modifier = Modifier.weight(1f),
        ) { Text("حفظ الجدول", fontSize = 12.sp) }
        TextButton(onClick = onDone) { Text("إلغاء", fontSize = 12.sp) }
    }
}

@Composable
private fun PartiesPicker(parties: List<Pair<String, String>>, onPick: (String?) -> Unit) {
    val c = LocalMizan.current
    if (parties.isEmpty()) {
        Text("أضف عميلاً/مورّداً أولاً من تبويب العملاء", fontSize = 11.sp, color = c.text3)
        return
    }
    Text("الطرف", fontSize = 11.sp, color = c.text3)
    Column(Modifier.fillMaxWidth()) {
        parties.take(6).forEach { (id, label) ->
            Text(
                label,
                fontSize = 11.sp,
                color = c.text2,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(id) }
                    .padding(vertical = 4.dp),
            )
        }
    }
}
