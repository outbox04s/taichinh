package vn.personalfinance.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import vn.personalfinance.BuildConfig
import vn.personalfinance.domain.model.TransactionSource
import vn.personalfinance.domain.model.TransactionStatus
import vn.personalfinance.domain.model.TransactionType
import vn.personalfinance.domain.repository.CategoryInput
import vn.personalfinance.presentation.FinanceUiState
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    state:FinanceUiState,
    onSync:()->Unit,
    onCategorize:(String,String)->Unit,
    onAddCategory:(CategoryInput)->Unit,
) {
    val pending=state.snapshot.transactions.filter { it.source==TransactionSource.SEPAY && it.status==TransactionStatus.PENDING }
    var categoryType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var showAddCategory by remember { mutableStateOf(false) }
    val categories=state.snapshot.categories.filter { it.type==categoryType }.sortedBy { it.name }

    LazyColumn(
        modifier=Modifier.fillMaxSize().padding(horizontal=16.dp),
        contentPadding=PaddingValues(bottom=28.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("CÀI ĐẶT",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.CloudSync,null,tint=MaterialTheme.colorScheme.primary)
                        Text("KẾT NỐI SEPAY",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    }
                    Text(if(pending.isNotEmpty())"Đã nhận dữ liệu • ${pending.size} giao dịch chờ phân loại" else "Sẵn sàng đối soát")
                    val lastSync=state.lastSePaySync?.atZone(ZoneId.of("Asia/Ho_Chi_Minh"))?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))?:"Chưa có trong phiên này"
                    Text("Lần đồng bộ cuối: $lastSync",style=MaterialTheme.typography.bodySmall)
                    Button(
                        onClick=onSync,
                        enabled=!state.saving,
                        modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).semantics { contentDescription="Đối soát giao dịch SePay ngay" },
                    ) { Text(if(state.saving)"ĐANG ĐỐI SOÁT…" else "ĐỐI SOÁT NGAY",maxLines=1) }
                    state.error?.let { error ->
                        Surface(color=MaterialTheme.colorScheme.errorContainer,shape=MaterialTheme.shapes.medium) {
                            Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Rounded.ErrorOutline,null)
                                Text(error,Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("DANH MỤC GIAO DỊCH",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                FilledTonalIconButton(onClick={showAddCategory=true}) { Icon(Icons.Rounded.Add,"Thêm danh mục") }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(TransactionType.EXPENSE to "TIỀN RA",TransactionType.INCOME to "TIỀN VÀO").forEachIndexed { index,(type,label) ->
                    SegmentedButton(categoryType==type,{categoryType=type},SegmentedButtonDefaults.itemShape(index,2)) { Text(label,maxLines=1) }
                }
            }
        }
        if(categories.isEmpty()) {
            item { Text("Chưa có danh mục.") }
        } else {
            items(categories,key={it.id}) { category ->
                ListItem(
                    headlineContent={Text(category.name,fontWeight=FontWeight.SemiBold)},
                    supportingContent={Text(if(categoryType==TransactionType.EXPENSE)"Danh mục tiền ra" else "Danh mục tiền vào")},
                    leadingContent={Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.primaryContainer){Icon(Icons.Rounded.Label,null,Modifier.padding(10.dp),tint=MaterialTheme.colorScheme.primary)}},
                )
            }
        }
        item { Text("GIAO DỊCH CHỜ PHÂN LOẠI",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold) }
        if(pending.isEmpty()) {
            item { Text("Không có giao dịch nào đang chờ.") }
        } else {
            items(pending,key={it.id}) { transaction -> PendingTransactionCard(transaction.id,transaction.description,transaction.amount,transaction.type,state,onCategorize) }
        }
        item {
            Spacer(Modifier.height(8.dp));HorizontalDivider()
            Column(Modifier.fillMaxWidth().padding(vertical=18.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("PHIÊN BẢN: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",fontWeight=FontWeight.Bold)
                Text("Phát hành: ${BuildConfig.RELEASED_AT}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Quản lý tài chính cá nhân",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if(showAddCategory) AddCategoryDialog(categoryType,{showAddCategory=false}) { name -> onAddCategory(CategoryInput(name,categoryType));showAddCategory=false }
}

@Composable
private fun PendingTransactionCard(id:String,description:String?,amount:Long,type:TransactionType,state:FinanceUiState,onCategorize:(String,String)->Unit) {
    var open by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(description?:"Giao dịch SePay",fontWeight=FontWeight.SemiBold)
            Text("$amount VND • cần xác nhận danh mục")
            OutlinedButton({open=true},Modifier.heightIn(min=48.dp)) { Text("CHỌN DANH MỤC",maxLines=1) }
            DropdownMenu(open,{open=false}) {
                state.snapshot.categories.filter{it.type==type}.forEach { category -> DropdownMenuItem({Text(category.name)},{onCategorize(id,category.id);open=false}) }
            }
        }
    }
}

@Composable
private fun AddCategoryDialog(type:TransactionType,onDismiss:()->Unit,onSave:(String)->Unit) {
    var name by remember { mutableStateOf("") };var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text("THÊM DANH MỤC ${if(type==TransactionType.EXPENSE)"TIỀN RA" else "TIỀN VÀO"}")},
        text={Column{OutlinedTextField(name,{name=it.take(60)},Modifier.fillMaxWidth(),singleLine=true,label={Text("Tên danh mục")});error?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},
        confirmButton={Button({error=if(name.trim().length<2)"Tên danh mục phải có ít nhất 2 ký tự" else null;if(error==null)onSave(name.trim())}){Text("THÊM")}},
        dismissButton={TextButton(onDismiss){Text("HỦY")}},
    )
}
