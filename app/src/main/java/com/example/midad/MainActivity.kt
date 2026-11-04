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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.example.midad.ui.components.MizanCard
import com.example.midad.ui.components.ScreenScaffold
import com.example.midad.ui.theme.LocalMizan
import com.example.midad.ui.theme.MidadTheme

private data class RootDestination(val label: String, val icon: ImageVector, val tab: Int)
private data class MoreDestination(val label: String, val detail: String, val icon: ImageVector, val tab: Int)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MidadTheme {
                // الاتجاه على الجذر مع إبقاء الحقول والمبالغ قابلة للقراءة.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MidadRoot()
                }
            }
        }
    }
}

@Composable
private fun MidadRoot() {
    val vm: MidadViewModel = viewModel()
    val state by vm.state.collectAsState()
    var tab by remember { mutableStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val appCtx = LocalContext.current.applicationContext

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
    val printing = state.printHtml
    if (printing != null) {
        PrintPreviewScreen(printing, state.printTitle, onClose = { vm.clearPrint() })
        return
    }

    val destinations = listOf(
        RootDestination("الرئيسية", Icons.Default.Home, 0),
        RootDestination("البيع", Icons.Default.PointOfSale, 1),
        RootDestination("المبيعات", Icons.Default.ReceiptLong, 2),
        RootDestination("المخزون", Icons.Default.Inventory, 4),
        RootDestination("المزيد", Icons.Default.Menu, 12),
    )
    val selectedDestination = when (tab) {
        0 -> 0
        1 -> 1
        2 -> 2
        4 -> 3
        else -> 4
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(c.surface)
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 18.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(42.dp).background(c.primary, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("T", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Column(Modifier.padding(start = 11.dp)) {
                            Text("TAMM", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.text)
                            Text(state.companyName, fontSize = 11.sp, color = c.text3, maxLines = 1)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.background(
                                if (state.balanced) c.mint.copy(alpha = 0.10f) else c.coral.copy(alpha = 0.10f),
                                RoundedCornerShape(50),
                            ).padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(7.dp).background(if (state.balanced) c.mint else c.coral, RoundedCornerShape(50)))
                            Text(
                                if (state.balanced) "متوازن" else "مراجعة الميزان",
                                Modifier.padding(start = 6.dp), fontSize = 10.sp,
                                color = if (state.balanced) c.mint else c.coral,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            if (state.pinConfigured) "قفل" else "PIN",
                            fontSize = 11.sp,
                            color = c.text2,
                            modifier = Modifier
                                .background(c.surface2, RoundedCornerShape(12.dp))
                                .clickable { if (state.pinConfigured) vm.lockNow() else tab = 11 }
                                .padding(horizontal = 11.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = c.surface) {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = selectedDestination == index,
                        onClick = { tab = destination.tab },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label, fontSize = 10.sp) },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = c.primary,
                            selectedTextColor = c.primary,
                            indicatorColor = c.primary.copy(alpha = 0.12f),
                            unselectedIconColor = c.text3,
                            unselectedTextColor = c.text3,
                        ),
                    )
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
                11 -> SettingsScreen(vm, state)
                else -> MoreHubScreen { tab = it }
            }
        }
    }
}

@Composable
private fun MoreHubScreen(onNavigate: (Int) -> Unit) {
    val c = LocalMizan.current
    val links = listOf(
        MoreDestination("المشتريات", "فواتير وأوامر الشراء", Icons.Default.ReceiptLong, 3),
        MoreDestination("العملاء والموردون", "الأرصدة وكشوف الحساب", Icons.Default.People, 5),
        MoreDestination("المالية", "الصناديق والقيود", Icons.Default.AccountBalance, 6),
        MoreDestination("السندات", "القبض والصرف والشيكات", Icons.Default.Wallet, 8),
        MoreDestination("التحليلات", "الأداء والاتجاهات", Icons.Default.ShowChart, 7),
        MoreDestination("الأصول والرواتب", "الأصول الثابتة والموظفون", Icons.Default.Assessment, 9),
        MoreDestination("التقارير", "الميزان والضريبة والتدفق", Icons.Default.Assessment, 10),
        MoreDestination("الإعدادات", "المنشأة والأمان والنسخ", Icons.Default.Settings, 11),
    )

    ScreenScaffold("المزيد", "كل أدوات TAMM في مكان واحد") {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(links) { link ->
                MizanCard(
                    modifier = Modifier.clickable { onNavigate(link.tab) },
                ) {
                    Box(
                        Modifier.size(42.dp).background(c.primary.copy(alpha = 0.10f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(link.icon, contentDescription = null, tint = c.primary) }
                    Text(link.label, Modifier.padding(top = 12.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                    Text(link.detail, Modifier.padding(top = 4.dp), fontSize = 10.sp, color = c.text3)
                }
            }
        }
    }
}
