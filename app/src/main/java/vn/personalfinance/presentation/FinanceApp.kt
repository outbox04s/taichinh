package vn.personalfinance.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import vn.personalfinance.domain.repository.TransactionEdits
import vn.personalfinance.presentation.components.glass.GlassLevel
import vn.personalfinance.presentation.components.glass.LiquidGlassSurface
import vn.personalfinance.presentation.screen.*
import vn.personalfinance.presentation.theme.LiquidGlassColors

private data class Destination(val route: String, val label: String, val icon: ImageVector)
private val destinations = listOf(
    Destination("overview", "Tổng quan", Icons.Rounded.Home),
    Destination("transactions", "Giao dịch", Icons.Rounded.SwapHoriz),
    Destination("budgets", "Tài khoản", Icons.Rounded.AccountBalanceWallet),
    Destination("debts", "Khoản nợ", Icons.Rounded.CreditCard),
    Destination("reports", "Báo cáo", Icons.Rounded.BarChart),
)

@Composable
fun FinanceApp(viewModel: FinanceViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mainRoute = backStack?.destination?.route in destinations.map { it.route }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) viewModel.checkForUpdate() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    state.availableUpdate?.let { release ->
        AlertDialog(
            onDismissRequest = viewModel::dismissUpdate,
            icon = { Icon(Icons.Rounded.CloudDownload, null) },
            title = { Text("Cập nhật ${release.versionName}") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Mã cập nhật: ${release.versionCode}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Text("Nội dung cập nhật", style = MaterialTheme.typography.titleSmall)
                Text(release.releaseNotes.ifBlank { "Phiên bản mới đã sẵn sàng với các cải tiến và sửa lỗi." })
                state.updateError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            } },
            confirmButton = { Button(viewModel::installUpdate, enabled = !state.updateInstalling) { if (state.updateInstalling) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("UPDATE") } },
            dismissButton = if (!release.mandatory) ({ TextButton(viewModel::dismissUpdate) { Text("Hủy bỏ") } }) else null,
        )
    }
    Scaffold(containerColor = Color.Transparent, bottomBar = {
        if (mainRoute) GlassBottomNavigation(destinations, backStack?.destination?.route) { item ->
            nav.navigate(item.route) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }
        }
    }) { padding ->
        NavHost(nav, "overview", Modifier.padding(padding)) {
            composable("overview") { OverviewScreen(state, viewModel::refresh, viewModel::setPeriod, viewModel::setAccount, viewModel::setCustomRange, viewModel::addIncome, viewModel::linkIncome, { nav.navigate("settings") }, { nav.navigate("transaction/new") }, { nav.navigate("transactions") }, { nav.navigate("debts") }) }
            composable("transactions") { TransactionsScreen(state, viewModel::refresh, viewModel::setSearch, viewModel::setType, viewModel::setSource, viewModel::setAccount, viewModel::setCategory, { id, status -> val old = state.snapshot.transactions.first { it.id == id }; viewModel.editTransaction(id, TransactionEdits(old.categoryId, old.note, status)) }, viewModel::deleteTransaction, { nav.navigate("transaction/transfer") }) { nav.navigate("transaction/new") } }
            composable("transaction/new") { TransactionFormScreen(state.snapshot, state.saving, state.error, { input -> viewModel.addTransaction(input) { nav.popBackStack() } }) { nav.popBackStack() } }
            composable("transaction/transfer") { TransferFormScreen(state.snapshot.accounts, state.saving, state.error, { from, to, amount, note -> viewModel.transfer(from, to, amount, note) { nav.popBackStack() } }) { nav.popBackStack() } }
            composable("budgets") { BudgetsScreen(state, viewModel::refresh, viewModel::addBudget) }
            composable("debts") { DebtListScreen(state, viewModel::refresh, viewModel::setDebtSort, { nav.navigate("debt/new") }) { nav.navigate("debt/$it") } }
            composable("debt/new") { CreateDebtScreen(state.saving, state.error, { nav.popBackStack() }) { input -> viewModel.addDebt(input) { id -> nav.navigate("debt/$id") { popUpTo("debts") } } } }
            composable("debt/{id}") { entry -> val id = entry.arguments?.getString("id"); DebtDetailScreen(state.snapshot.debts.firstOrNull { it.id == id }, state.snapshot, { nav.popBackStack() }, viewModel::payDebt, viewModel::reverseDebtPayment, viewModel::settleDebt, viewModel::updateInstallment) }
            composable("reports") { PlaceholderScreen("Báo cáo") }
            composable("settings") { SettingsScreen(state, viewModel::reconcileSePay) { id, category -> val old = state.snapshot.transactions.first { it.id == id }; viewModel.editTransaction(id, TransactionEdits(category, old.note, old.status)) } }
        }
    }
}

@Composable
private fun GlassBottomNavigation(items: List<Destination>, route: String?, onSelect: (Destination) -> Unit) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        LiquidGlassSurface(Modifier.fillMaxWidth().height(72.dp), GlassLevel.Primary) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                items.forEach { item ->
                    val selected = route == item.route
                    Surface(onClick = { onSelect(item) }, modifier = Modifier.weight(if (selected) 1.45f else 1f).height(56.dp), shape = CircleShape, color = if (selected) LiquidGlassColors.Mint.copy(alpha = .18f) else Color.Transparent) {
                        Row(Modifier.padding(horizontal = if (selected) 12.dp else 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(item.icon, item.label, tint = if (selected) LiquidGlassColors.Mint else LiquidGlassColors.TextSecondary)
                            if (selected) { Spacer(Modifier.width(6.dp)); Text(item.label, color = LiquidGlassColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
            }
        }
    }
}
