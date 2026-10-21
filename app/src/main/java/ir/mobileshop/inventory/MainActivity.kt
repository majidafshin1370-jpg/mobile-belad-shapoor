package ir.mobileshop.inventory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class Tab(val label: String, val icon: ImageVector) {
    Inventory("موجودی", Icons.Filled.Inventory2),
    Sales("فروش", Icons.Filled.ShoppingCart),
    Debtors("بدهکاران", Icons.Filled.People),
    Report("گزارش", Icons.Filled.BarChart),
    Settings("اتصال", Icons.Filled.Cloud)
}

class MainActivity : ComponentActivity() {
    private val vm: InventoryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Photo banners reach under the status bar; its icons stay white, the navigation bar stays light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        scheduleDebtReminders(applicationContext)
        setContent {
            ShopTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    App(vm)
                }
            }
        }
    }
}

@Composable
fun App(vm: InventoryViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.Inventory) }
    // "" = new product, null = no form open
    var formId by rememberSaveable { mutableStateOf<String?>(null) }

    // The header photo changes every few seconds, like a shop banner.
    val heroIndex by produceState(0) {
        while (true) {
            delay(6_000)
            value = (value + 1) % HERO_IMAGES.size
        }
    }

    BackHandler(enabled = formId != null || tab != Tab.Inventory) {
        if (formId != null) formId = null else tab = Tab.Inventory
    }

    CompositionLocalProvider(LocalHeroIndex provides heroIndex) {
        val openForm = formId
        if (openForm != null) {
            FormScreen(vm = vm, productId = openForm, onDone = { formId = null })
        } else {
            Scaffold(
                containerColor = PageGrey,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    NavigationBar(containerColor = Color.White) {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Icon(t.icon, t.label) },
                                label = { Text(t.label, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Red,
                                    selectedTextColor = Red,
                                    indicatorColor = RedSoft,
                                    unselectedIconColor = InkGrey,
                                    unselectedTextColor = InkGrey
                                )
                            )
                        }
                    }
                },
                floatingActionButton = {
                    if (tab == Tab.Inventory) {
                        ExtendedFloatingActionButton(
                            onClick = { formId = "" },
                            containerColor = Red,
                            contentColor = Color.White,
                            icon = { Icon(Icons.Filled.Add, null) },
                            text = { Text("افزودن کالا") }
                        )
                    }
                }
            ) { pad ->
                when (tab) {
                    Tab.Inventory -> InventoryTab(
                        vm = vm,
                        contentPadding = pad,
                        onEdit = { formId = it },
                        onOpenSettings = { tab = Tab.Settings }
                    )
                    Tab.Sales -> SalesTab(vm = vm, contentPadding = pad)
                    Tab.Debtors -> DebtorsTab(vm = vm, contentPadding = pad)
                    Tab.Report -> ReportTab(vm = vm, contentPadding = pad)
                    Tab.Settings -> SettingsTab(vm = vm, contentPadding = pad)
                }
            }
        }
    }
}
