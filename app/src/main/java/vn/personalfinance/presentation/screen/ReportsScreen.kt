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
import vn.personalfinance.domain.ReportCalculator
import vn.personalfinance.domain.model.*
import vn.personalfinance.domain.repository.FixedExpenseInput
import vn.personalfinance.presentation.*
import vn.personalfinance.presentation.components.glass.GlassCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable fun ReportsScreen(state:FinanceUiState,onRetry:()->Unit,onFixed:()->Unit,onCategories:()->Unit) {
    ScreenState(state.loading,state.error,false,onRetry) {
        val plans=ReportCalculator.forecast(state.snapshot,LocalDate.now(VietnamZone))
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {
                Text("Báo cáo",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                Text("Kế hoạch thu chi 12 tháng",style=MaterialTheme.typography.titleMedium)
                Text("Còn cho chi tiêu khác = nguồn thu dự kiến − chi phí cố định − gốc, lãi và phí vay theo lịch.")
                Text("Tính trọn tháng, gồm cả kỳ nợ đã trả. Chưa trừ chi tiêu sinh hoạt khác; đây không phải số dư tài khoản.",style=MaterialTheme.typography.bodySmall)
                OutlinedButton(onFixed,Modifier.fillMaxWidth()){Text("Quản lý chi phí cố định")}
                OutlinedButton(onCategories,Modifier.fillMaxWidth()){Text("Tổng hợp chi tiêu theo danh mục")}
            }
            item { GlassCard {
                val deficits=plans.filter{it.remaining<0}
                Text(if(deficits.isEmpty())"Không có tháng thiếu hụt theo kế hoạch" else "${deficits.size} tháng có nguy cơ âm",fontWeight=FontWeight.Bold,color=if(deficits.isEmpty())MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                deficits.firstOrNull()?.let{Text("Tháng đầu: ${it.month.format(DateTimeFormatter.ofPattern("MM/yyyy"))} • thiếu ${(-it.remaining).toVnd()}")}
                if(state.snapshot.incomeSources.none{it.active})Text("Chưa thiết lập nguồn thu nhập. Thêm nguồn thu tại Tổng quan để dự toán đầy đủ.")
                if(state.snapshot.debts.any{d->d.status!="paid" && state.snapshot.installments.none{it.debtId==d.id}})Text("Có khoản vay chưa có lịch trả: dự toán có thể thiếu nghĩa vụ trả nợ.")
            } }
            items(plans,key={it.month.toString()}){plan->GlassCard {
                Text(plan.month.format(DateTimeFormatter.ofPattern("MM/yyyy")),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                ReportAmount("Thu nhập dự kiến",plan.income)
                ReportAmount("Chi phí cố định",plan.fixedExpenses)
                ReportAmount("Trả vay theo lịch",plan.debtDue)
                if(plan.overdue>0)ReportAmount("Nợ tồn từ tháng trước",plan.overdue)
                HorizontalDivider()
                Text(if(plan.remaining<0)"Nguy cơ âm: thiếu ${(-plan.remaining).toVnd()}" else "Còn cho chi tiêu khác: ${plan.remaining.toVnd()}",fontWeight=FontWeight.Bold,color=if(plan.remaining<0)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }}
        }
    }
}

@Composable private fun ReportAmount(label:String,amount:Long) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(label,Modifier.weight(1f));Text(amount.toVnd(),fontWeight=FontWeight.SemiBold)
    }
}

@Composable fun CategoryReportScreen(state:FinanceUiState,onRetry:()->Unit,onBack:()->Unit,onPeriod:(PeriodFilter)->Unit,onAccount:(String?)->Unit,onCustom:(LocalDate,LocalDate)->Unit) {
    Scaffold(containerColor=androidx.compose.ui.graphics.Color.Transparent,topBar={TopAppBar(title={Text("Chi tiêu theo danh mục")},navigationIcon={TextButton(onBack){Text("Quay lại")}})}){padding->
        ScreenState(state.loading,state.error,false,onRetry) {
            val range=state.dateRange()
            val groups=ReportCalculator.expenses(state.snapshot,range.first,range.second,VietnamZone,state.accountId)
            val total=groups.sumOf{it.amount}
            LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item { FilterPanel(state,onPeriod,onAccount,onCustom);Text("${range.first} — ${range.second}");Text("Tổng chi: ${total.toVnd()}",style=MaterialTheme.typography.titleLarge) }
                if(groups.isEmpty())item{Text("Chưa có chi tiêu trong kỳ đã chọn.")}
                items(groups,key={it.categoryId?:"uncategorized"}){group->GlassCard {
                    Text(state.snapshot.categories.firstOrNull{it.id==group.categoryId}?.name?:"Chưa phân loại",fontWeight=FontWeight.Bold)
                    ReportAmount("${group.count} giao dịch",group.amount)
                    val fraction=if(total>0)group.amount.toFloat()/total else 0f
                    LinearProgressIndicator(progress={fraction},modifier=Modifier.fillMaxWidth())
                    Text(String.format(java.util.Locale.forLanguageTag("vi-VN"),"%.1f%% tổng chi",fraction*100))
                }}
            }
        }
    }
}

@Composable fun FixedExpensesScreen(state:FinanceUiState,onRetry:()->Unit,onBack:()->Unit,onSave:(String?,FixedExpenseInput,()->Unit)->Unit,onDelete:(String,()->Unit)->Unit) {
    var editing by remember{mutableStateOf<RecurringEntry?>(null)}
    var open by remember{mutableStateOf(false)}
    var deleting by remember{mutableStateOf<RecurringEntry?>(null)}
    Scaffold(containerColor=androidx.compose.ui.graphics.Color.Transparent,topBar={TopAppBar(title={Text("Chi phí cố định")},navigationIcon={TextButton(onBack){Text("Quay lại")}})}){padding->
        if(state.loading)Box(Modifier.padding(padding)){CircularProgressIndicator()} else LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {
                Text("Các khoản dự kiến mất hàng tháng, dùng để lập kế hoạch chi tiêu. Việc thiết lập không ghi nhận tiền đã chi.")
                Button({editing=null;open=true},enabled=!state.saving,modifier=Modifier.fillMaxWidth()){Text("Thêm chi phí cố định")}
                state.error?.let{Text(it,color=MaterialTheme.colorScheme.error);TextButton(onRetry){Text("Thử tải lại")}}
            }
            val entries=state.snapshot.recurringEntries.filter{it.type==TransactionType.EXPENSE && it.frequency=="monthly"}
            if(entries.isEmpty())item{Text("Chưa có chi phí cố định. Thêm tiền nhà, điện nước, học phí hoặc khoản chi hàng tháng khác.")}
            items(entries,key={it.id}){entry->GlassCard {
                Text(entry.title,fontWeight=FontWeight.Bold)
                ReportAmount(state.snapshot.categories.firstOrNull{it.id==entry.categoryId}?.name?:"Chưa phân loại",entry.amount)
                Text("Ngày ${entry.startDate.dayOfMonth} hàng tháng • ${if(entry.active)"Đang áp dụng" else "Tạm dừng"}")
                Text("Từ ${entry.startDate}${entry.endDate?.let{" đến $it"}?:" • Không thời hạn"}",style=MaterialTheme.typography.bodySmall)
                Row { TextButton({editing=entry;open=true},enabled=!state.saving){Text("Sửa")};TextButton({deleting=entry},enabled=!state.saving){Text("Xóa",color=MaterialTheme.colorScheme.error)} }
            }}
        }
    }
    if(open)FixedExpenseDialog(editing,state,{if(!state.saving)open=false}){input->onSave(editing?.id,input){open=false}}
    deleting?.let{entry->AlertDialog(onDismissRequest={if(!state.saving)deleting=null},title={Text("Xóa ${entry.title}?")},text={Column{Text("Khoản này sẽ được bỏ khỏi kế hoạch chi phí cố định. Giao dịch đã ghi nhận vẫn được giữ lại.");state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={Button({onDelete(entry.id){deleting=null}},enabled=!state.saving){Text("Xóa")}},dismissButton={TextButton({deleting=null},enabled=!state.saving){Text("Hủy")}})}
}

@Composable private fun FixedExpenseDialog(entry:RecurringEntry?,state:FinanceUiState,onDismiss:()->Unit,onSave:(FixedExpenseInput)->Unit) {
    var title by remember{mutableStateOf(entry?.title.orEmpty())}
    var amount by remember{mutableStateOf(entry?.amount?.toString().orEmpty())}
    var account by remember{mutableStateOf(entry?.accountId)}
    var category by remember{mutableStateOf(entry?.categoryId)}
    var start by remember{mutableStateOf((entry?.startDate?:LocalDate.now(VietnamZone)).toString())}
    var end by remember{mutableStateOf(entry?.endDate?.toString().orEmpty())}
    var hasEnd by remember{mutableStateOf(entry?.endDate!=null)}
    var active by remember{mutableStateOf(entry?.active?:true)}
    var validation by remember{mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(entry==null)"Thêm chi phí cố định" else "Sửa chi phí cố định")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(title,{title=it.take(120)},label={Text("Tên khoản chi")})
        OutlinedTextField(amount,{amount=it.filter(Char::isDigit).take(15)},label={Text("Số tiền mỗi tháng (VND)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
        SelectionField("Tài khoản",state.snapshot.accounts,account,{it.name}){account=it.id}
        SelectionField("Danh mục",state.snapshot.categories.filter{it.type==TransactionType.EXPENSE},category,{it.name}){category=it.id}
        if(state.snapshot.categories.none{it.type==TransactionType.EXPENSE})Text("Thêm danh mục chi tiêu trong Cài đặt trước khi lưu.")
        DatePickerField("Ngày thanh toán đầu tiên",start){start=it}
        Text("Lặp hàng tháng vào ngày đã chọn; tháng ngắn dùng ngày cuối tháng.",style=MaterialTheme.typography.bodySmall)
        Row { Checkbox(hasEnd,{hasEnd=it;if(it && end.isBlank())end=start});Text("Có ngày kết thúc") }
        if(hasEnd)DatePickerField("Ngày kết thúc",end){end=it}
        Row { Switch(active,{active=it});Text("Áp dụng",Modifier.padding(12.dp)) }
        (validation?:state.error)?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={Button({
        val value=amount.toLongOrNull();val from=runCatching{LocalDate.parse(start)}.getOrNull();val to=if(hasEnd)runCatching{LocalDate.parse(end)}.getOrNull() else null
        validation=when {
            title.isBlank()->"Nhập tên khoản chi"
            value==null || value<=0->"Số tiền phải lớn hơn 0"
            state.snapshot.accounts.none{it.id==account}->"Chọn tài khoản"
            state.snapshot.categories.none{it.id==category && it.type==TransactionType.EXPENSE}->"Chọn danh mục chi tiêu"
            from==null || (hasEnd && (to==null || to<from))->"Kiểm tra ngày bắt đầu và kết thúc"
            else->null
        }
        if(validation==null)onSave(FixedExpenseInput(title.trim(),account!!,category!!,value!!,from!!,to,active))
    },enabled=!state.saving){Text(if(state.saving)"Đang lưu…" else "Lưu")}},dismissButton={TextButton(onDismiss,enabled=!state.saving){Text("Hủy")}})
}
