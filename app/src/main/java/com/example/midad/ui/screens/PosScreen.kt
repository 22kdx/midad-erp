package com.example.midad.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.midad.ui.components.EmptyHint
import com.example.midad.ui.components.MizanCard
import com.example.midad.ui.components.ScreenScaffold
import com.example.midad.ui.components.SectionTitle
import com.example.midad.ui.theme.LocalMizan

/**
 * نقاط البيع — شاشة كاشير سريعة: اضغط الصنف، عدّل الكمية، ثم نقدي أو آجل.
 * لا تبني قيداً بنفسها: كل شيء يمرّ عبر ViewModel ⇒ PostingService ⇒ الكيرنل.
 */
@Composable
fun PosScreen(vm: MidadViewModel, s: UiState) {
    val c = LocalMizan.current
    val cart = remember { mutableStateMapOf<String, Pair<LineInput, Int>>() }
    var query by remember { mutableStateOf("") }
    val shown = if (query.isBlank()) s.items else s.items.filter {
        it.name.contains(query, true) || it.sku.contains(query, true) || it.barcode.contains(query, true)
    }
    val total = cart.values.sumOf { (line, qty) ->
        Money.mulDiv(Money.parse(line.price.ifBlank { "0" }, s.currency), (qty * 1000).toLong(), 1000)
    }
    var showOpenShift by remember { mutableStateOf(false) }
    var showCloseShift by remember { mutableStateOf(false) }
    var showHold by remember { mutableStateOf(false) }
    var showHeld by remember { mutableStateOf(false) }
    var openingCash by remember { mutableStateOf("") }
    var countedCash by remember { mutableStateOf("") }
    var holdLabel by remember { mutableStateOf("") }

    LaunchedEffect(s.resumeLines) {
        val lines = s.resumeLines
        if (lines != null) {
            lines.forEach { cart[it.productId] = it to it.qty.toInt().coerceAtLeast(1) }
            vm.consumeResume()
        }
    }

    ScreenScaffold("نقاط البيع", s.shiftLabel) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (s.shiftOpen) {
                OutlinedButton(onClick = { showCloseShift = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Text("إغلاق الوردية", fontSize = 11.sp)
                }
                OutlinedButton(onClick = { showHeld = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Text("معلّقة (${s.held.size})", fontSize = 11.sp)
                }
                OutlinedButton(onClick = { showHold = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Text("تعليق", fontSize = 11.sp)
                }
            } else {
                Button(onClick = { showOpenShift = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Text("فتح وردية", fontSize = 11.sp)
                }
                OutlinedButton(onClick = { showHeld = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Text("معلّقة (${s.held.size})", fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            query, { query = it },
            label = { Text("بحث أو باركود", fontSize = 11.sp) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown) { item ->
                val inCart = cart[item.id]?.second ?: 0
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(if (inCart > 0) c.primary.copy(alpha = 0.10f) else c.surface, RoundedCornerShape(14.dp))
                        .clickable {
                            val line = LineInput(item.id, item.name, 1.0, item.price, "0", "0")
                            val current = cart[item.id]
                            cart[item.id] = if (current == null) line to 1 else current.first to (current.second + 1)
                        }
                        .padding(12.dp),
                ) {
                    Text(item.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                    Spacer(Modifier.height(4.dp))
                    Text(item.price, fontSize = 12.sp, color = c.primary)
                    Text("متاح ${item.available} ${item.unit}", fontSize = 10.sp, color = c.text3)
                    if (inCart > 0) Text("في السلة: $inCart", fontSize = 10.sp, color = c.mint)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (cart.isEmpty()) {
            EmptyHint("اضغط على صنف لإضافته إلى السلة")
        } else {
            MizanCard {
                SectionTitle("السلة", "${cart.size} صنف")
                cart.entries.toList().forEach { (id, pair) ->
                    val (line, qty) = pair
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(line.name, fontSize = 12.sp, color = c.text)
                            Text("${line.price} × $qty", fontSize = 10.sp, color = c.text3)
                        }
                        TextButtonSmall("-") {
                            if (qty <= 1) cart.remove(id) else cart[id] = line to (qty - 1)
                        }
                        Text("$qty", fontSize = 12.sp, color = c.text, textAlign = TextAlign.Center, modifier = Modifier.width(28.dp))
                        TextButtonSmall("+") { cart[id] = line to (qty + 1) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("الإجمالي", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                    Text(Money.format(total, s.currency), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.primary)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val lines = cart.values.map { it.first }
                            vm.saveDocument(
                                DocKind.INVOICE, null, lines, "0", Money.format(total, s.currency),
                                onSaved = { id -> vm.buildPrint(id) },
                            ) { cart.clear() }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("نقدي", fontSize = 14.sp) }
                    OutlinedButton(
                        onClick = {
                            val lines = cart.values.map { it.first }
                            vm.saveDocument(DocKind.INVOICE, null, lines, "0", "") { cart.clear() }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("آجل", fontSize = 14.sp) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showOpenShift) {
        AlertDialog(
            onDismissRequest = { showOpenShift = false },
            title = { Text("فتح وردية", fontSize = 17.sp) },
            text = {
                OutlinedTextField(
                    openingCash, { openingCash = it },
                    label = { Text("النقد الافتتاحي في الدرج") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.openShift(openingCash); openingCash = ""; showOpenShift = false }) { Text("فتح") }
            },
            dismissButton = { TextButton(onClick = { showOpenShift = false }) { Text("إلغاء") } },
        )
    }

    if (showCloseShift) {
        AlertDialog(
            onDismissRequest = { showCloseShift = false },
            title = { Text("إغلاق الوردية", fontSize = 17.sp) },
            text = {
                Column {
                    Text("النقد المتوقع: ${s.shiftExpectedCash}", fontSize = 12.sp, color = c.text2)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        countedCash, { countedCash = it },
                        label = { Text("النقد المعدود فعلياً") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    s.shiftVariance?.let { Text("الفرق: $it", fontSize = 12.sp, color = c.mint) }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.closeShift(countedCash); countedCash = ""; showCloseShift = false }) { Text("إغلاق") }
            },
            dismissButton = { TextButton(onClick = { showCloseShift = false }) { Text("إلغاء") } },
        )
    }

    if (showHold) {
        AlertDialog(
            onDismissRequest = { showHold = false },
            title = { Text("تعليق الفاتورة", fontSize = 17.sp) },
            text = {
                OutlinedTextField(
                    holdLabel, { holdLabel = it },
                    label = { Text("وصف (مثل: طاولة ٤)") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.holdInvoice(holdLabel, cart.values.map { it.first }, "0")
                    cart.clear()
                    holdLabel = ""
                    showHold = false
                }) { Text("تعليق") }
            },
            dismissButton = { TextButton(onClick = { showHold = false }) { Text("إلغاء") } },
        )
    }

    if (showHeld) {
        AlertDialog(
            onDismissRequest = { showHeld = false },
            title = { Text("الفواتير المعلّقة", fontSize = 17.sp) },
            text = {
                Column {
                    if (s.held.isEmpty()) Text("لا فواتير معلّقة", fontSize = 12.sp, color = c.text3)
                    s.held.forEach { h ->
                        Row(
                            Modifier.fillMaxWidth()
                                .background(c.surface2, RoundedCornerShape(10.dp))
                                .clickable { vm.resumeHeld(h.id); showHeld = false }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(h.label, fontSize = 12.sp, color = c.text)
                                Text("${h.items} صنف · ${h.createdAt}", fontSize = 10.sp, color = c.text3)
                            }
                            Text(h.total, fontSize = 12.sp, color = c.primary)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHeld = false }) { Text("إغلاق") } },
        )
    }
}

@Composable
private fun TextButtonSmall(label: String, onClick: () -> Unit) {
    val c = LocalMizan.current
    Box(
        Modifier
            .size(30.dp)
            .background(c.surface2, RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.primary)
    }
}
