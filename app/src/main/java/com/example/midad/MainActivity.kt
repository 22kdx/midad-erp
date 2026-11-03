package com.example.midad

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.midad.ui.MidadViewModel
import com.example.midad.ui.screens.AnalyticsScreen
import com.example.midad.ui.screens.AssetsPayrollScreen
import com.example.midad.ui.screens.DashboardScreen
import com.example.midad.ui.screens.FinanceScreen
import com.example.midad.ui.screens.InventoryScreen
import com.example.midad.ui.screens.LockScreen
import com.example.midad.ui.screens.PartiesScreen
import com.example.midad.ui.screens.PosScreen
import com.example.midad.ui.screens.PrintPreviewScreen
import com.example.midad.ui.screens.PurchasesScreen
import com.example.midad.ui.screens.ReportsScreen
import com.example.midad.ui.screens.SalesScreen
import com.example.midad.ui.screens.SettingsScreen
import com.example.midad.ui.screens.VouchersScreen
import com.example.midad.ui.theme.LocalMizan
import com.example.midad.ui.theme.MidadTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MidadTheme {
                // الاتجاه على الجذر — الأرقام تُعرض لاتينية داخل النصوص العربية
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MidadRoot()
                }
            }
        }
    }
}

/**
 * التنقّل أعلى الشاشة (تبويبات أفقية) لا في شريط سفلي — كما طُلب.
 */
@Composable
private fun MidadRoot() {
    val vm: MidadViewModel = viewModel()
    val state by vm.state.collectAsState()
    var tab by remember { mutableStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val appCtx = androidx.compose.ui.platform.LocalContext.current.applicationContext

    // إذن الإشعارات (أندرويد ١٣+) — التذكيرات محلية فقط، وبلا إذن يبقى التطبيق يعمل طبيعياً
    val notifPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        com.example.midad.notify.Reminders.ensureChannel(appCtx)
    }

    LaunchedEffect(state.message, state.error) {
        (state.error ?: state.message)?.let {
            snackbar.showSnackbar(it)
            vm.dismissMessage()
        }
    }

    if (state.locked && state.pinConfigured) {
        LockScreen(vm, state)
        return
    }

    val c = LocalMizan.current
    val tabs = listOf(
        "الرئيسية", "البيع", "المبيعات", "المشتريات", "المخزون", "العملاء",
        "المالية", "التحليلات", "السندات", "الأصول", "التقارير", "الإعدادات",
    )

    val printing = state.printHtml
    if (printing != null) {
        PrintPreviewScreen(printing, state.printTitle, onClose = { vm.clearPrint() })
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column(Modifier.fillMaxWidth().background(c.surface)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 40.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("مِداد", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.primary)
                        Text(
                            "${state.companyName} · ${if (state.balanced) "الميزان متوازن" else "الميزان غير متوازن"}",
                            fontSize = 11.sp,
                            color = c.text3,
                        )
                    }
                    Text(
                        if (state.pinConfigured) "قفل" else "PIN",
                        fontSize = 12.sp,
                        color = c.text2,
                        modifier = Modifier
                            .background(c.surface2, RoundedCornerShape(10.dp))
                            .clickable { if (state.pinConfigured) vm.lockNow() else tab = 11 }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                ScrollableTabRow(
                    selectedTabIndex = tab,
                    edgePadding = 8.dp,
                    containerColor = c.surface,
                    contentColor = c.primary,
                    divider = {},
                ) {
                    tabs.forEachIndexed { i, title ->
                        Tab(
                            selected = tab == i,
                            onClick = { tab = i },
                            text = {
                                Text(
                                    title,
                                    fontSize = 12.sp,
                                    fontWeight = if (tab == i) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> DashboardScreen(vm, state)
                1 -> PosScreen(vm, state)
                2 -> SalesScreen(vm, state)
                3 -> PurchasesScreen(vm, state)
                4 -> InventoryScreen(vm, state)
                5 -> PartiesScreen(vm, state)
                6 -> FinanceScreen(vm, state)
                7 -> AnalyticsScreen(vm, state)
                8 -> VouchersScreen(vm, state)
                9 -> AssetsPayrollScreen(vm, state)
                10 -> ReportsScreen(vm, state)
                else -> SettingsScreen(vm, state)
            }
        }
    }
}
