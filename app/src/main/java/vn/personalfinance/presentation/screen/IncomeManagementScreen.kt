@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package vn.personalfinance.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import vn.personalfinance.domain.model.*
import vn.personalfinance.domain.repository.IncomeSourceInput
import vn.personalfinance.presentation.*
import vn.personalfinance.presentation.components.glass.GlassCard
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable fun IncomeSummary(snapshot:FinanceSnapshot,onManage:()->Unit) {
    GlassCard {
        Text("NGUỒN THU NHẬP",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        TextButton(onManage){Text("Quản lý · Thêm / sửa / xóa")}
        if(snapshot.incomeSources.isEmpty())Text("Chưa thiết lập nguồn thu nhập")
        snapshot.incomeSources.forEach{source->IncomeDetails(source,snapshot)}
    }
}

@Composable private fun IncomeDetails(source:IncomeSource,snapshot:FinanceSnapshot) {
    val today=LocalDate.now(VietnamZone)
    val month=YearMonth.from(today)
    val received=snapshot.incomePayments.filter{it.incomeSourceId==source.id && YearMonth.from(it.expectedDate)==month && it.transactionId!=null}
    Column(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(source.name,fontWeight=FontWeight.Bold)
        Text("${source.expectedAmount.toVnd()} / ${when(source.frequency){"monthly"->"tháng";"weekly"->"tuần";else->"lần"}}")
        if(!source.active)Text("Tạm dừng")
        else {
            Text(if(source.frequency=="monthly")"Ngày nhận hàng tháng: ${source.payDay?:source.nextExpectedDate?.dayOfMonth?:"Chưa đặt"}" else "Ngày dự kiến: ${source.nextExpectedDate?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))?:"Chưa đặt"}")
            Text("Tháng ${month.format(DateTimeFormatter.ofPattern("MM/yyyy"))}: "+if(received.isEmpty())"Chưa ghi nhận thu nhập" else "Đã ghi nhận ${received.size} kỳ · ${received.sumOf{it.actualAmount?:0}.toVnd()}")
        }
    }
}

@Composable fun IncomeManagementScreen(state:FinanceUiState,onBack:()->Unit,onRetry:()->Unit,onSave:(String?,IncomeSourceInput,()->Unit)->Unit,onDelete:(String,()->Unit)->Unit,onLink:(String,String,Long)->Unit) {
    var editing by remember{mutableStateOf<IncomeSource?>(null)}
    var open by remember{mutableStateOf(false)}
    var deleting by remember{mutableStateOf<IncomeSource?>(null)}
    var linking by remember{mutableStateOf<IncomePayment?>(null)}
    Scaffold(topBar={TopAppBar(title={Text("Quản lý nguồn thu")},navigationIcon={TextButton(onBack){Text("Quay lại")}})}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {
                Button({editing=null;open=true},enabled=!state.saving){Text("Thêm nguồn thu nhập")}
                if(state.loading)CircularProgressIndicator()
                state.error?.let{Text(it,color=MaterialTheme.colorScheme.error);TextButton(onRetry){Text("Thử lại")}}
                if(!state.loading && state.snapshot.incomeSources.isEmpty())Text("Chưa có nguồn thu nhập.")
            }
            items(state.snapshot.incomeSources,key={it.id}){source->GlassCard {
                IncomeDetails(source,state.snapshot)
                Row {
                    TextButton({editing=source;open=true},enabled=!state.saving){Text("Sửa")}
                    TextButton({deleting=source},enabled=!state.saving){Text("Xóa",color=MaterialTheme.colorScheme.error)}
                }
                state.snapshot.incomePayments.filter{it.incomeSourceId==source.id && it.transactionId==null}.sortedBy{it.expectedDate}.forEach{payment->
                    TextButton({linking=payment},enabled=!state.saving){Text("Liên kết thu ngày ${payment.expectedDate}")}
                }
            }}
        }
    }
    if(open)IncomeEditor(editing,state,{if(!state.saving)open=false}){onSave(editing?.id,it){open=false}}
    linking?.let{payment->
        val transactions=state.snapshot.transactions.filter{it.type==TransactionType.INCOME && it.status==TransactionStatus.CONFIRMED && it.deletedAt==null && state.snapshot.incomePayments.none{p->p.transactionId==it.id}}.sortedByDescending{it.transactionAt}
        AlertDialog(onDismissRequest={linking=null},title={Text("Chọn giao dịch tiền đã nhận")},text={LazyColumn {
            if(transactions.isEmpty())item{Text("Chưa có giao dịch thu chưa liên kết.")}
            items(transactions,key={it.id}){tx->TextButton({onLink(payment.id,tx.id,tx.amount);linking=null},enabled=!state.saving){Text("${tx.transactionAt.atZone(VietnamZone).toLocalDate()} · ${tx.description?:"Tiền vào"} · ${tx.amount.toVnd()}")}}
        }},confirmButton={},dismissButton={TextButton({linking=null}){Text("Đóng")}})
    }
    deleting?.let{source->AlertDialog(onDismissRequest={if(!state.saving)deleting=null},title={Text("Xóa ${source.name}?")},text={Column{Text("Xóa nguồn thu và lịch dự kiến. Các giao dịch tiền đã nhận vẫn được giữ lại.");state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={Button({onDelete(source.id){deleting=null}},enabled=!state.saving){Text("Xóa")}},dismissButton={TextButton({deleting=null},enabled=!state.saving){Text("Hủy")}})}
}

@Composable private fun IncomeEditor(source:IncomeSource?,state:FinanceUiState,onDismiss:()->Unit,onSave:(IncomeSourceInput)->Unit) {
    var name by remember{mutableStateOf(source?.name.orEmpty())}
    var amount by remember{mutableStateOf(source?.expectedAmount?.toString().orEmpty())}
    var frequency by remember{mutableStateOf(source?.frequency?:"monthly")}
    var day by remember{mutableStateOf(source?.payDay?.toString()?:LocalDate.now(VietnamZone).dayOfMonth.toString())}
    var date by remember{mutableStateOf((source?.nextExpectedDate?:LocalDate.now(VietnamZone)).toString())}
    var menu by remember{mutableStateOf(false)}
    var validation by remember{mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(source==null)"Thêm nguồn thu" else "Sửa nguồn thu")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(name,{name=it.take(120)},label={Text("Tên nguồn thu")})
        OutlinedTextField(amount,{amount=it.filter(Char::isDigit).take(15)},label={Text("Số tiền dự kiến (VND)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
        Box {
            OutlinedButton({menu=true}){Text(when(frequency){"monthly"->"Hàng tháng";"weekly"->"Hàng tuần";else->"Không định kỳ"})}
            DropdownMenu(menu,{menu=false}){listOf("monthly" to "Hàng tháng","weekly" to "Hàng tuần","irregular" to "Không định kỳ").forEach{(id,label)->DropdownMenuItem({Text(label)},{frequency=id;menu=false})}}
        }
        if(frequency=="monthly")OutlinedTextField(day,{day=it.filter(Char::isDigit).take(2)},label={Text("Ngày nhận (1–31)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
        else DatePickerField("Ngày nhận dự kiến",date){date=it}
        Text("Sửa nguồn thu sẽ cập nhật các kỳ chưa nhận. Các kỳ đã nhận được giữ lại.",style=MaterialTheme.typography.bodySmall)
        (validation?:state.error)?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={Button({
        val value=amount.toLongOrNull();val payDay=day.toIntOrNull();val today=LocalDate.now(VietnamZone)
        val next=if(frequency=="monthly" && payDay in 1..31)today.withDayOfMonth(minOf(payDay!!,today.lengthOfMonth())) else runCatching{LocalDate.parse(date)}.getOrNull()
        validation=when{name.isBlank()->"Nhập tên nguồn thu";value==null || value<=0->"Số tiền phải lớn hơn 0";frequency=="monthly" && payDay !in 1..31->"Ngày nhận phải từ 1 đến 31";next==null->"Ngày dự kiến không hợp lệ";else->null}
        if(validation==null)onSave(IncomeSourceInput(name.trim(),source?.type?:"salary",value!!,if(frequency=="monthly")payDay else null,frequency,next))
    },enabled=!state.saving){Text(if(state.saving)"Đang lưu…" else "Lưu")}},dismissButton={TextButton(onDismiss,enabled=!state.saving){Text("Hủy")}})
}
