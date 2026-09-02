package vn.personalfinance.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import vn.personalfinance.domain.repository.TransactionEdits
import vn.personalfinance.presentation.screen.*

private data class Destination(val route:String,val label:String,val icon:ImageVector)
private val destinations=listOf(
 Destination("overview","Tổng quan",Icons.Rounded.Home),
 Destination("transactions","Giao dịch",Icons.Rounded.SwapHoriz),
 Destination("budgets","Ngân sách",Icons.Rounded.AccountBalanceWallet),
 Destination("debts","Khoản nợ",Icons.Rounded.CreditCard),
 Destination("reports","Báo cáo",Icons.Rounded.BarChart),
 Destination("settings","Cài đặt",Icons.Rounded.Settings),
)

@Composable fun FinanceApp(viewModel:FinanceViewModel=hiltViewModel()){
 val nav=rememberNavController();val backStack by nav.currentBackStackEntryAsState();val state by viewModel.uiState.collectAsStateWithLifecycle();val mainRoute=backStack?.destination?.route in destinations.map{it.route}
 val uriHandler=LocalUriHandler.current
 val lifecycleOwner=LocalLifecycleOwner.current
 DisposableEffect(lifecycleOwner){
  val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_START)viewModel.checkForUpdate()}
  lifecycleOwner.lifecycle.addObserver(observer)
  onDispose{lifecycleOwner.lifecycle.removeObserver(observer)}
 }
 state.availableUpdate?.let{release->AlertDialog(
  onDismissRequest=viewModel::dismissUpdate,
  icon={Icon(Icons.Rounded.CloudDownload,contentDescription=null)},
  title={Text("Cập nhật ${release.versionName}")},
  text={Column(verticalArrangement=androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)){
   Text("Mã cập nhật: ${release.versionCode}",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
   Text("Nội dung cập nhật",style=MaterialTheme.typography.titleSmall)
   Text(release.releaseNotes.ifBlank{"Phiên bản mới đã sẵn sàng với các cải tiến và sửa lỗi."})
  }},
  confirmButton={Button(onClick={uriHandler.openUri(release.apkUrl)}){Text("UPDATE")}},
  dismissButton=if(!release.mandatory){{TextButton(onClick=viewModel::dismissUpdate){Text("Hủy bỏ")}}}else null,
 )}
 Scaffold(bottomBar={if(mainRoute)NavigationBar{destinations.forEach{item->
  val selected=backStack?.destination?.route==item.route
  NavigationBarItem(selected,{nav.navigate(item.route){popUpTo(nav.graph.findStartDestination().id){saveState=true};launchSingleTop=true;restoreState=true}},icon={Icon(item.icon,contentDescription=item.label)},label={Text(item.label,maxLines=1)},alwaysShowLabel=false)
 }}}){padding->
  NavHost(nav,"overview",Modifier.padding(padding)){
   composable("overview"){OverviewScreen(state,viewModel::refresh,viewModel::setPeriod,viewModel::setAccount,viewModel::setCustomRange,viewModel::addIncome,viewModel::linkIncome)}
   composable("transactions"){TransactionsScreen(state,viewModel::refresh,viewModel::setSearch,viewModel::setType,viewModel::setSource,viewModel::setAccount,viewModel::setCategory,{id,status->val old=state.snapshot.transactions.first{it.id==id};viewModel.editTransaction(id,TransactionEdits(old.categoryId,old.note,status))},viewModel::deleteTransaction,{nav.navigate("transaction/transfer")}){nav.navigate("transaction/new")}}
   composable("transaction/new"){TransactionFormScreen(state.snapshot,state.saving,state.error,{input->viewModel.addTransaction(input){nav.popBackStack()}},{nav.popBackStack()})}
   composable("transaction/transfer"){TransferFormScreen(state.snapshot.accounts,state.saving,state.error,{from,to,amount,note->viewModel.transfer(from,to,amount,note){nav.popBackStack()}},{nav.popBackStack()})}
   composable("budgets"){BudgetsScreen(state,viewModel::refresh,viewModel::addBudget)}
   composable("debts"){DebtListScreen(state,viewModel::refresh,viewModel::setDebtSort,{nav.navigate("debt/new")}){nav.navigate("debt/$it")}}
   composable("debt/new"){CreateDebtScreen(state.saving,state.error,{nav.popBackStack()}){input->viewModel.addDebt(input){id->nav.navigate("debt/$id"){popUpTo("debts")}}}}
   composable("debt/{id}"){entry->val id=entry.arguments?.getString("id");DebtDetailScreen(state.snapshot.debts.firstOrNull{it.id==id},state.snapshot,{nav.popBackStack()},viewModel::payDebt,viewModel::reverseDebtPayment,viewModel::settleDebt,viewModel::updateInstallment)}
   composable("reports"){PlaceholderScreen("Báo cáo")};composable("settings"){SettingsScreen(state,viewModel::reconcileSePay){id,category->val old=state.snapshot.transactions.first{it.id==id};viewModel.editTransaction(id,TransactionEdits(category,old.note,old.status))}}
  }
 }
}
