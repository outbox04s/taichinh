@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package vn.personalfinance.presentation.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.personalfinance.domain.FinanceCalculator
import vn.personalfinance.domain.RiskEngine
import vn.personalfinance.domain.RiskLevel
import vn.personalfinance.domain.model.*
import vn.personalfinance.domain.repository.*
import vn.personalfinance.presentation.*
import vn.personalfinance.presentation.components.glass.GlassCard
import vn.personalfinance.presentation.components.glass.GlassChip
import vn.personalfinance.presentation.theme.LiquidGlassColors
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val positive=Color(0xFF14805E); private val negative=Color(0xFFB3261E)

@Composable fun ScreenState(loading:Boolean,error:String?,empty:Boolean,onRetry:()->Unit,content:@Composable ()->Unit){
    when{loading->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator(Modifier.semantics{contentDescription="Đang tải dữ liệu"})}
        error!=null->FriendlyState(Icons.Rounded.CloudOff,"Không tải được dữ liệu",error,"Thử lại",onRetry)
        empty->FriendlyState(Icons.Rounded.Inbox,"Chưa có dữ liệu","Hãy thêm mục đầu tiên để bắt đầu.")
        else->content()}
}

@Composable private fun FriendlyState(icon:androidx.compose.ui.graphics.vector.ImageVector,title:String,message:String,action:String?=null,onAction:()->Unit={}){
 Box(Modifier.fillMaxSize().padding(32.dp),contentAlignment=Alignment.Center){
  Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)){
   Column(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
    Icon(icon,null,Modifier.size(44.dp),tint=MaterialTheme.colorScheme.primary)
    Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
    Text(message,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    action?.let{Button(onClick=onAction,Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(it)}}
   }
  }
 }
}

@Composable private fun LegacyOverviewScreen(state:FinanceUiState,onRetry:()->Unit,onPeriod:(PeriodFilter)->Unit,onAccount:(String?)->Unit,onCustom:(LocalDate,LocalDate)->Unit,onAddIncome:(IncomeSourceInput)->Unit,onLinkIncome:(String,String,Long)->Unit){
    val range=state.dateRange();val tx=state.snapshot.transactions.filter{state.accountId==null||it.accountId==state.accountId}
    val flow=FinanceCalculator.cashFlow(tx,range.first,range.second,VietnamZone);val assets=FinanceCalculator.totalAssets(state.snapshot.accounts)
    val debt=state.snapshot.debts.filter{it.status!="paid"}.sumOf{it.currentPrincipal};val today=LocalDate.now(VietnamZone)
    fun due(days:Long)=state.snapshot.installments.filter{it.status!="paid"&&!it.dueDate.isBefore(today)&&!it.dueDate.isAfter(today.plusDays(days))}.sumOf{it.totalDue-it.paidAmount}
    val riskResult=RiskEngine.calculate(state.snapshot,today);val projected=riskResult.projectedCash30Days
    val risk=when(riskResult.level){RiskLevel.SAFE->"An toàn";RiskLevel.ATTENTION->"Cần chú ý";RiskLevel.DANGEROUS->"Nguy hiểm";RiskLevel.INSUFFICIENT_DATA->"Chưa đủ dữ liệu"}
    ScreenState(state.loading,state.error,false,onRetry){LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text("Tổng quan",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);FilterPanel(state,onPeriod,onAccount,onCustom)}
        item{MetricGrid(listOf("Tài sản khả dụng" to assets,"Tiền vào" to flow.income,"Tiền ra" to flow.expense,"Dòng tiền ròng" to flow.net))}
        item{Card{Column(Modifier.padding(16.dp)){Text("Sức khỏe tài chính",fontWeight=FontWeight.Bold);MetricLine("Tổng dư nợ",debt);MetricLine("Phải trả trong 7 ngày",due(7));MetricLine("Phải trả trong 30 ngày",due(30));MetricLine("Số dư dự kiến sau 30 ngày",projected);Text("Mức rủi ro: $risk"+(riskResult.score?.let{" • $it/100"}?:""),color=if(risk=="An toàn")positive else negative,fontWeight=FontWeight.Bold);riskResult.reasons.take(3).forEach{Text("• ${it.description}")};Text("Cảnh báo chỉ nhằm hỗ trợ theo dõi, không phải tư vấn tài chính chuyên nghiệp.",style=MaterialTheme.typography.bodySmall)}}}
        item{CashFlowChart(tx,range.first,range.second)}
        item{ExpenseStructure(tx,state.snapshot.categories,range.first,range.second)}
        item{SectionTitle("Giao dịch gần nhất")}
        items(tx.filter{it.deletedAt==null}.sortedByDescending{it.transactionAt}.take(5),key={it.id}){TransactionRow(it,state.snapshot)}
        item{SectionTitle("Khoản nợ sắp đến hạn")}
        val upcoming=state.snapshot.installments.filter{it.status!="paid"}.sortedBy{it.dueDate}.take(5)
        if(upcoming.isEmpty()) item{Text("Không có khoản đến hạn.")} else items(upcoming,key={it.id}){Text("⚠ ${state.snapshot.debts.firstOrNull{d->d.id==it.debtId}?.name?:"Khoản nợ"} • ${it.dueDate} • ${(it.totalDue-it.paidAmount).toVnd()}")}
        item{IncomeSourcesCard(state.snapshot,onAddIncome,onLinkIncome)}
    }}
}

@Composable internal fun FilterPanel(state:FinanceUiState,onPeriod:(PeriodFilter)->Unit,onAccount:(String?)->Unit,onCustom:(LocalDate,LocalDate)->Unit){
    Column{PeriodFilter.entries.chunked(3).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){row.forEach{p->FilterChip(selected=state.period==p,onClick={onPeriod(p)},label={Text(mapOf(PeriodFilter.WEEK to "Tuần",PeriodFilter.MONTH to "Tháng",PeriodFilter.QUARTER to "Quý",PeriodFilter.YEAR to "Năm",PeriodFilter.CUSTOM to "Tùy chọn")[p]!!)})}}}
        var accountOpen by remember{mutableStateOf(false)};Box{TextButton(onClick={accountOpen=true},Modifier.heightIn(min=48.dp)){Text("Tài khoản: ${state.snapshot.accounts.firstOrNull{it.id==state.accountId}?.name?:"Tất cả"} ▾")};DropdownMenu(accountOpen,{accountOpen=false}){DropdownMenuItem({Text("Tất cả")},{onAccount(null);accountOpen=false});state.snapshot.accounts.forEach{a->DropdownMenuItem({Text(a.name)},{onAccount(a.id);accountOpen=false})}}}
        if(state.period==PeriodFilter.CUSTOM){var start by remember{mutableStateOf(state.customStart?.toString()?:LocalDate.now(VietnamZone).withDayOfMonth(1).toString())};var end by remember{mutableStateOf(state.customEnd?.toString()?:LocalDate.now(VietnamZone).toString())};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){DatePickerField("Từ ngày",start,modifier=Modifier.weight(1f)){start=it};DatePickerField("Đến ngày",end,modifier=Modifier.weight(1f)){end=it}};TextButton(onClick={runCatching{onCustom(LocalDate.parse(start),LocalDate.parse(end))}}){Text("Áp dụng")}}
    }
}
@Composable private fun MetricGrid(values:List<Pair<String,Long>>){Column(verticalArrangement=Arrangement.spacedBy(8.dp)){values.chunked(2).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){row.forEach{(label,value)->Card(Modifier.weight(1f)){Column(Modifier.padding(12.dp)){Text(label,style=MaterialTheme.typography.labelLarge);Text(value.toVnd(),color=if(label.contains("ra")||value<0)negative else positive,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)}}};if(row.size==1)Spacer(Modifier.weight(1f))}}}
}
@Composable private fun MetricLine(label:String,value:Long)=Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label);Text(value.toVnd(),fontWeight=FontWeight.SemiBold)}
@Composable private fun SectionTitle(value:String)=Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)

@Composable private fun CashFlowChart(items:List<Transaction>,start:LocalDate,end:LocalDate){
    val days=(0..minOf(13,(end.toEpochDay()-start.toEpochDay()).toInt())).map{start.plusDays(it.toLong())};val max=days.maxOfOrNull{day->items.filter{it.transactionAt.atZone(VietnamZone).toLocalDate()==day&&it.status==TransactionStatus.CONFIRMED&&it.type!=TransactionType.TRANSFER}.sumOf{it.amount}}?.coerceAtLeast(1)?:1
    Card{Column(Modifier.padding(16.dp)){Text("Thu và chi theo ngày",fontWeight=FontWeight.Bold);Row(verticalAlignment=Alignment.CenterVertically){Text("● Thu",color=positive);Spacer(Modifier.width(12.dp));Text("■ Chi",color=negative)};Canvas(Modifier.fillMaxWidth().height(150.dp).semantics{contentDescription="Biểu đồ thu và chi theo ngày"}){val step=size.width/days.size.coerceAtLeast(1);days.forEachIndexed{i,d->val dayItems=items.filter{it.transactionAt.atZone(VietnamZone).toLocalDate()==d&&it.status==TransactionStatus.CONFIRMED};val inc=dayItems.filter{it.type==TransactionType.INCOME}.sumOf{it.amount};val exp=dayItems.filter{it.type==TransactionType.EXPENSE}.sumOf{it.amount};drawLine(positive,Offset(i*step+step*.35f,size.height),Offset(i*step+step*.35f,size.height-size.height*inc/max),step*.22f,StrokeCap.Round);drawLine(negative,Offset(i*step+step*.65f,size.height),Offset(i*step+step*.65f,size.height-size.height*exp/max),step*.22f,StrokeCap.Round)}}}}
}
@Composable private fun ExpenseStructure(items:List<Transaction>,categories:List<Category>,start:LocalDate,end:LocalDate){val expenses=items.filter{it.type==TransactionType.EXPENSE&&it.status==TransactionStatus.CONFIRMED&&it.deletedAt==null}.filter{val d=it.transactionAt.atZone(VietnamZone).toLocalDate();!d.isBefore(start)&&!d.isAfter(end)};val total=expenses.sumOf{it.amount};Card{Column(Modifier.padding(16.dp)){Text("Cơ cấu chi tiêu",fontWeight=FontWeight.Bold);if(total==0L)Text("Chưa có chi tiêu trong kỳ") else expenses.groupBy{it.categoryId}.mapValues{it.value.sumOf(Transaction::amount)}.entries.sortedByDescending{it.value}.take(5).forEach{(id,value)->Text("• ${categories.firstOrNull{it.id==id}?.name?:"Chưa phân loại"}: ${value.toVnd()} (${value*100/total}%)")}}}}

@Composable fun TransactionsScreen(state:FinanceUiState,onRetry:()->Unit,onSearch:(String)->Unit,onType:(TransactionType?)->Unit,onSource:(TransactionSource?)->Unit,onAccount:(String?)->Unit,onCategory:(String?)->Unit,onDelete:(String)->Unit,onTransfer:()->Unit,onAdd:()->Unit){
    var deleting by remember{mutableStateOf<Transaction?>(null)}
    val visible=state.snapshot.transactions.filter{it.deletedAt==null}.filter{t->state.search.isBlank()||listOf(t.description,t.note,t.amount.toString()).any{it?.contains(state.search,true)==true}}.filter{state.transactionType==null||it.type==state.transactionType}.filter{state.source==null||it.source==state.source}.filter{state.accountId==null||it.accountId==state.accountId}.filter{state.categoryId==null||it.categoryId==state.categoryId}.sortedByDescending{it.transactionAt}
    Scaffold(containerColor=Color.Transparent,floatingActionButton={ExtendedFloatingActionButton(onClick=onAdd,modifier=Modifier.padding(bottom=92.dp).semantics{contentDescription="Thêm giao dịch"},text={Text("Thêm giao dịch")},icon={Text("＋")})}){padding->ScreenState(state.loading,state.error,visible.isEmpty()&&!state.loading,onRetry){LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(start=16.dp,top=16.dp,end=16.dp,bottom=220.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Text("Giao dịch",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);OutlinedTextField(state.search,onSearch,Modifier.fillMaxWidth().testTag("transaction_search"),label={Text("Tìm nội dung, ghi chú hoặc số tiền")},leadingIcon={Text("⌕")});TransactionFilters(state,onType,onSource,onAccount,onCategory);OutlinedButton(onTransfer,Modifier.heightIn(min=48.dp)){Text("⇄ Chuyển tiền")}}
        visible.groupBy{it.transactionAt.atZone(VietnamZone).toLocalDate()}.forEach{(date,group)->item(key="h$date"){Text(date.format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy",java.util.Locale.forLanguageTag("vi-VN"))),fontWeight=FontWeight.Bold)};items(group,key={it.id}){t->TransactionRow(t,state.snapshot){deleting=t}}}
    }}}
    deleting?.let{transaction->AlertDialog(onDismissRequest={deleting=null},title={Text("Xóa giao dịch?")},text={Text("Giao dịch sẽ bị xóa hẳn khỏi database và số dư liên quan sẽ được tính lại.")},confirmButton={Button({onDelete(transaction.id);deleting=null},colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)){Text("XÓA")}},dismissButton={TextButton({deleting=null}){Text("HỦY")}})}
}
@Composable private fun TransactionFilters(state:FinanceUiState,onType:(TransactionType?)->Unit,onSource:(TransactionSource?)->Unit,onAccount:(String?)->Unit,onCategory:(String?)->Unit){Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){GlassChip("Tất cả",state.transactionType==null,{onType(null)});GlassChip("Tiền vào",state.transactionType==TransactionType.INCOME,{onType(TransactionType.INCOME)});GlassChip("Tiền ra",state.transactionType==TransactionType.EXPENSE,{onType(TransactionType.EXPENSE)})};Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){GlassChip("SePay",state.source==TransactionSource.SEPAY,{onSource(if(state.source==TransactionSource.SEPAY)null else TransactionSource.SEPAY)});GlassChip("Nhập thủ công",state.source==TransactionSource.MANUAL,{onSource(if(state.source==TransactionSource.MANUAL)null else TransactionSource.MANUAL)})};Row{FilterPicker("Tài khoản",state.snapshot.accounts.map{it.id to it.name},state.accountId,onAccount,Modifier.weight(1f));FilterPicker("Danh mục",state.snapshot.categories.map{it.id to it.name},state.categoryId,onCategory,Modifier.weight(1f))}}
}
@Composable private fun FilterPicker(label:String,items:List<Pair<String,String>>,selected:String?,onSelect:(String?)->Unit,modifier:Modifier=Modifier){var open by remember{mutableStateOf(false)};Box(modifier){TextButton({open=true},Modifier.heightIn(min=48.dp)){Text("$label: ${items.firstOrNull{it.first==selected}?.second?:"Tất cả"} ▾",maxLines=1,overflow=TextOverflow.Ellipsis)};DropdownMenu(open,{open=false}){DropdownMenuItem({Text("Tất cả")},{onSelect(null);open=false});items.forEach{item->DropdownMenuItem({Text(item.second)},{onSelect(item.first);open=false})}}}}
@Composable fun TransactionRow(item:Transaction,snapshot:FinanceSnapshot,onDelete:(String)->Unit={}){GlassCard{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(if(item.type==TransactionType.INCOME)"↗" else if(item.type==TransactionType.EXPENSE)"↘" else "⇄",Modifier.semantics{contentDescription=item.type.name},color=LiquidGlassColors.Blue);Column(Modifier.weight(1f).padding(horizontal=10.dp)){Text(item.description?.ifBlank{null}?:snapshot.categories.firstOrNull{it.id==item.categoryId}?.name?:"Chuyển khoản",fontWeight=FontWeight.SemiBold,color=LiquidGlassColors.TextPrimary);Text(if(item.source==TransactionSource.SEPAY)"SePay" else "Nhập thủ công",style=MaterialTheme.typography.labelMedium,color=LiquidGlassColors.TextSecondary)};Text((if(item.type==TransactionType.EXPENSE)"− " else if(item.type==TransactionType.INCOME)"+ " else "")+item.amount.toVnd(),color=if(item.type==TransactionType.EXPENSE)negative else positive,fontWeight=FontWeight.Bold);Box{var open by remember{mutableStateOf(false)};IconButton({open=true}){Text("⋮")};DropdownMenu(open,{open=false}){DropdownMenuItem({Text("XÓA",color=MaterialTheme.colorScheme.error)},{onDelete(item.id);open=false})}}}}
}

@Composable fun TransactionFormScreen(snapshot:FinanceSnapshot,saving:Boolean,error:String?,onSave:(ManualTransactionInput)->Unit,onBack:()->Unit){TransactionFormContent(snapshot.accounts,snapshot.categories,saving,error,onSave,onBack)}
@Composable fun TransferFormScreen(accounts:List<FinancialAccount>,saving:Boolean,error:String?,onSave:(String,String,Long,String?)->Unit,onBack:()->Unit){var from by remember{mutableStateOf<String?>(null)};var to by remember{mutableStateOf<String?>(null)};var amount by remember{mutableStateOf("")};var note by remember{mutableStateOf("")};var validation by remember{mutableStateOf<String?>(null)};Scaffold(topBar={TopAppBar({Text("Chuyển tiền")},navigationIcon={TextButton(onBack){Text("‹ Quay lại")}})}){padding->Column(Modifier.padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){SelectionField("Từ tài khoản",accounts,from,{it.name}){from=it.id};SelectionField("Đến tài khoản",accounts,to,{it.name}){to=it.id};OutlinedTextField(amount,{amount=it.filter(Char::isDigit)},Modifier.fillMaxWidth(),label={Text("Số tiền VND")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number));OutlinedTextField(note,{note=it},Modifier.fillMaxWidth(),label={Text("Mô tả")});(validation?:error)?.let{Text(it,color=negative)};Button(enabled=!saving,onClick={val value=amount.toLongOrNull();validation=when{value==null||value<=0->"Số tiền phải lớn hơn 0";from==null||to==null->"Vui lòng chọn đủ hai tài khoản";from==to->"Hai tài khoản phải khác nhau";else->null};if(validation==null)onSave(from!!,to!!,value!!,note.ifBlank{null})},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("Xác nhận chuyển tiền")}}}}
@Composable fun TransactionFormContent(accounts:List<FinancialAccount>,categories:List<Category>,saving:Boolean=false,error:String?=null,onSave:(ManualTransactionInput)->Unit,onBack:()->Unit={}){
    var type by remember{mutableStateOf(TransactionType.EXPENSE)};var amount by remember{mutableStateOf("")};var account by remember{mutableStateOf<String?>(null)};var category by remember{mutableStateOf<String?>(null)};var description by remember{mutableStateOf("")};var note by remember{mutableStateOf("")};var date by remember{mutableStateOf(LocalDate.now(VietnamZone).toString())};var recurring by remember{mutableStateOf(false)};var validation by remember{mutableStateOf<String?>(null)}
    Scaffold(topBar={TopAppBar({Text("Nhập giao dịch")},navigationIcon={TextButton(onClick=onBack){Text("‹ Quay lại")}})}){padding->LazyColumn(Modifier.padding(padding).fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){listOf(TransactionType.INCOME to "Tiền vào",TransactionType.EXPENSE to "Tiền ra").forEachIndexed{i,(v,l)->SegmentedButton(type==v,{type=v;category=null},SegmentedButtonDefaults.itemShape(i,2)){Text(l)}}}}
        item{OutlinedTextField(amount,{amount=it.filter(Char::isDigit).take(18)},Modifier.fillMaxWidth().testTag("amount_input"),label={Text("Số tiền (VND)")},supportingText={Text(amount.toLongOrNull()?.toVnd().orEmpty())},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))}
        item{SelectionField("Tài khoản",accounts,account,{it.name}){account=it.id}}
        item{SelectionField("Danh mục",categories.filter{it.type==type},category,{it.name}){category=it.id}}
        item{DatePickerField("Ngày giao dịch",date){date=it};OutlinedTextField(description,{description=it},Modifier.fillMaxWidth().testTag("description_input"),label={Text("Mô tả")});OutlinedTextField(note,{note=it},Modifier.fillMaxWidth(),label={Text("Ghi chú")})}
        item{Row(verticalAlignment=Alignment.CenterVertically){Checkbox(recurring,{recurring=it});Text("Lặp lại hàng tháng")}}
        item{(validation?:error)?.let{Text(it,color=negative)};Button(enabled=!saving,onClick={val value=amount.toLongOrNull();val parsedDate=runCatching{LocalDate.parse(date)}.getOrNull();validation=when{value==null||value<=0->"Số tiền phải lớn hơn 0";account==null->"Vui lòng chọn tài khoản";category==null->"Vui lòng chọn danh mục";parsedDate==null->"Ngày không hợp lệ";else->null};if(validation==null)onSave(ManualTransactionInput(account!!,category!!,type,value!!,parsedDate!!.atStartOfDay(VietnamZone).toInstant(),description.ifBlank{null},note.ifBlank{null},recurring))},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("save_transaction")){if(saving)CircularProgressIndicator(Modifier.size(20.dp)) else Text("Lưu giao dịch")}}
    }}
}
@Composable internal fun <T> SelectionField(label:String,items:List<T>,selected:String?,name:(T)->String,onSelect:(T)->Unit){var open by remember{mutableStateOf(false)};Box{OutlinedButton({open=true},Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("$label: ${items.firstOrNull{item->when(item){is FinancialAccount->item.id==selected;is Category->item.id==selected;else->false}}?.let(name)?:"Chọn"} ▾")};DropdownMenu(open,{open=false}){items.forEach{item->DropdownMenuItem({Text(name(item))},{onSelect(item);open=false})}}}}

@Composable fun BudgetsScreen(state:FinanceUiState,onRetry:()->Unit,onAdd:(BudgetInput)->Unit){var open by remember{mutableStateOf(false)};ScreenState(state.loading,state.error,false,onRetry){LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Text("Ngân sách",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text("Transfer và giao dịch đã loại trừ không được tính.");Button({open=true},Modifier.heightIn(min=48.dp)){Text("＋ Tạo ngân sách")}};if(state.snapshot.budgets.isEmpty())item{Text("Chưa có ngân sách")};items(state.snapshot.budgets.filter{it.active},key={it.id}){b->val usage=FinanceCalculator.budgetUsage(b,state.snapshot.transactions,VietnamZone);Card{Column(Modifier.padding(16.dp)){Text(state.snapshot.categories.firstOrNull{it.id==b.categoryId}?.name?:"Ngân sách tổng",fontWeight=FontWeight.Bold);Text("${usage.spent.toVnd()} / ${usage.limit.toVnd()}");LinearProgressIndicator({(usage.percent/100f).coerceIn(0f,1f)},Modifier.fillMaxWidth().semantics{contentDescription="Đã dùng ${usage.percent} phần trăm"},color=if(usage.percent>=b.alertPercent)negative else positive);Text(if(usage.percent>=b.alertPercent)"⚠ Đã đạt ngưỡng cảnh báo ${b.alertPercent}%" else "✓ Trong giới hạn • ${usage.percent}%",color=if(usage.percent>=b.alertPercent)negative else positive)}}}}};if(open)BudgetDialog(state.snapshot.categories,{open=false}){onAdd(it);open=false}}

@Composable private fun BudgetDialog(categories:List<Category>,onDismiss:()->Unit,onSave:(BudgetInput)->Unit){var amount by remember{mutableStateOf("")};var alert by remember{mutableStateOf("80")};var category by remember{mutableStateOf<String?>(null)};val start=LocalDate.now(VietnamZone).withDayOfMonth(1);val end=start.withDayOfMonth(start.lengthOfMonth());AlertDialog(onDismissRequest=onDismiss,title={Text("Tạo ngân sách tháng")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){SelectionField("Danh mục (bỏ trống = tổng)",categories.filter{it.type==TransactionType.EXPENSE},category,{it.name}){category=it.id};OutlinedTextField(amount,{amount=it.filter(Char::isDigit)},label={Text("Hạn mức VND")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number));OutlinedTextField(alert,{alert=it.filter(Char::isDigit).take(3)},label={Text("Cảnh báo %")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))}},confirmButton={Button({val value=amount.toLongOrNull();val threshold=alert.toIntOrNull();if(value!=null&&value>0&&threshold in 1..100)onSave(BudgetInput(category,"monthly",value,start,end,threshold!!))}){Text("Lưu")}},dismissButton={TextButton(onDismiss){Text("Hủy")}})}
@Composable internal fun IncomeSourcesCard(snapshot:FinanceSnapshot,onAdd:(IncomeSourceInput)->Unit,onLink:(String,String,Long)->Unit){val today=LocalDate.now(VietnamZone);var open by remember{mutableStateOf(false)};var paymentToLink by remember{mutableStateOf<IncomePayment?>(null)};Card{Column(Modifier.padding(16.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("Nguồn thu nhập",fontWeight=FontWeight.Bold);TextButton({open=true}){Text("＋ Thêm")}};if(snapshot.incomeSources.isEmpty())Text("Chưa thiết lập nguồn thu nhập") else snapshot.incomeSources.filter{it.active}.forEach{s->val payment=snapshot.incomePayments.firstOrNull{it.incomeSourceId==s.id&&it.transactionId==null};val missed=payment?.expectedDate?.isBefore(today)==true;Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text((if(missed)"⚠ " else "✓ ")+s.name+" • ${s.expectedAmount.toVnd()}"+(if(missed)" • Chưa ghi nhận lương" else ""),Modifier.weight(1f),color=if(missed)negative else LocalContentColor.current);if(payment!=null)TextButton({paymentToLink=payment}){Text("Liên kết")}}}}};if(open)IncomeDialog({open=false}){onAdd(it);open=false};paymentToLink?.let{payment->LinkIncomeDialog(snapshot.transactions.filter{it.type==TransactionType.INCOME&&it.status==TransactionStatus.CONFIRMED&&it.deletedAt==null},{paymentToLink=null}){tx->onLink(payment.id,tx.id,tx.amount);paymentToLink=null}}
}

@Composable private fun IncomeDialog(onDismiss:()->Unit,onSave:(IncomeSourceInput)->Unit){var name by remember{mutableStateOf("")};var amount by remember{mutableStateOf("")};var day by remember{mutableStateOf("")};AlertDialog(onDismissRequest=onDismiss,title={Text("Thêm nguồn thu nhập")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(name,{name=it},label={Text("Tên nguồn, ví dụ Lương")});OutlinedTextField(amount,{amount=it.filter(Char::isDigit)},label={Text("Số tiền dự kiến")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number));OutlinedTextField(day,{day=it.filter(Char::isDigit).take(2)},label={Text("Ngày nhận lương (1–31)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))}},confirmButton={Button(onClick={val value=amount.toLongOrNull();val payDay=day.toIntOrNull();if(name.isNotBlank()&&value!=null&&value>0&&payDay in 1..31)onSave(IncomeSourceInput(name,"salary",value,payDay,"monthly",LocalDate.now(VietnamZone).withDayOfMonth(minOf(payDay!!,LocalDate.now(VietnamZone).lengthOfMonth()))))}){Text("Lưu")}},dismissButton={TextButton(onDismiss){Text("Hủy")}})}
@Composable private fun LinkIncomeDialog(items:List<Transaction>,onDismiss:()->Unit,onSelect:(Transaction)->Unit){AlertDialog(onDismissRequest=onDismiss,title={Text("Chọn giao dịch tiền vào")},text={Column{if(items.isEmpty())Text("Không có giao dịch tiền vào phù hợp") else items.take(10).forEach{tx->TextButton({onSelect(tx)},Modifier.fillMaxWidth()){Text("${tx.description?:"Tiền vào"} • ${tx.amount.toVnd()}")}}}},confirmButton={},dismissButton={TextButton(onDismiss){Text("Hủy")}})}
