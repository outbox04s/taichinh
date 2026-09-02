package vn.personalfinance.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import vn.personalfinance.domain.model.TransactionSource
import vn.personalfinance.domain.model.TransactionStatus
import vn.personalfinance.presentation.FinanceUiState
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(state: FinanceUiState, onSync: () -> Unit, onCategorize: (String, String) -> Unit) {
    val pending = state.snapshot.transactions.filter {
        it.source == TransactionSource.SEPAY && it.status == TransactionStatus.PENDING
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Icon(Icons.Rounded.CloudSync,null,tint=MaterialTheme.colorScheme.primary)
                Text("Kết nối SePay", style = MaterialTheme.typography.headlineSmall)
            }
            Text("Đồng bộ giao dịch ngân hàng an toàn qua Supabase. Thông tin bảo mật không được lưu trên thiết bị.",color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (pending.isNotEmpty()) "Đã nhận dữ liệu • ${pending.size} giao dịch chờ phân loại" else "Sẵn sàng đối soát",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val lastSync = state.lastSePaySync?.atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                        ?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) ?: "Chưa có trong phiên này"
                    Text("Lần đồng bộ cuối: $lastSync")
                    Button(
                        onClick = onSync,
                        enabled = !state.saving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .semantics { contentDescription = "Đối soát giao dịch SePay ngay" },
                    ) { Text(if (state.saving) "Đang đối soát…" else "Đối soát ngay") }
                    state.error?.let {
                        Surface(color=MaterialTheme.colorScheme.errorContainer,shape=MaterialTheme.shapes.medium){
                            Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                                Icon(Icons.Rounded.ErrorOutline,null,tint=MaterialTheme.colorScheme.onErrorContainer)
                                Text(it,Modifier.weight(1f),color=MaterialTheme.colorScheme.onErrorContainer,style=MaterialTheme.typography.bodyMedium)
                            }
                        }
                        TextButton(onClick = onSync) { Text("Thử lại") }
                    }
                }
            }
        }
        item { Text("Giao dịch chờ phân loại", style = MaterialTheme.typography.titleLarge) }
        if (pending.isEmpty()) item { Text("Không có giao dịch nào đang chờ.") }
        items(pending, key = { it.id }) { transaction ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(transaction.description ?: "Giao dịch SePay")
                    Text("${transaction.amount} VND • cần xác nhận danh mục")
                    var open by remember { mutableStateOf(false) }
                    TextButton(onClick = { open = true }) { Text("Chọn danh mục") }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        state.snapshot.categories.filter { it.type == transaction.type }.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = { onCategorize(transaction.id, category.id); open = false },
                            )
                        }
                    }
                }
            }
        }
        item {
            HorizontalDivider()
            Text("Cảnh báo rủi ro là công cụ hỗ trợ theo dõi dựa trên quy tắc, không phải tư vấn tài chính chuyên nghiệp.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(24.dp))
        }
    }
}
