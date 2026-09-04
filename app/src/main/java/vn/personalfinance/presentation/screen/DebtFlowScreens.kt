@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package vn.personalfinance.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.personalfinance.domain.model.*
import vn.personalfinance.domain.repository.DebtInput
import vn.personalfinance.presentation.VietnamZone
import vn.personalfinance.presentation.toVnd
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
private fun LegacyCreateDebtV2Screen(saving:Boolean,error:String?,onBack:()->Unit,onSave:(DebtInput)->Unit){
    var name by remember{mutableStateOf("")};var lender by remember{mutableStateOf("")};var original by remember{mutableStateOf("")};var current by remember{mutableStateOf("")}
    var hasInterest by remember{mutableStateOf(false)};var frequency by remember{mutableStateOf("monthly")};var expected by remember{mutableStateOf("")};var monthlyDay by remember{mutableStateOf(LocalDate.now(VietnamZone).dayOfMonth.toString())}
    var yearlyDate by remember{mutableStateOf(LocalDate.now(VietnamZone).plusYears(1).toString())};var start by remember{mutableStateOf(LocalDate.now(VietnamZone).toString())};var note by remember{mutableStateOf("")};var validation by remember{mutableStateOf<String?>(null)}
    Scaffold(topBar={TopAppBar({Text("TẠO KHOẢN NỢ",fontWeight=FontWeight.Bold)},navigationIcon={TextButton(onBack){Text("‹ Quay lại")}})}){padding->
        LazyColumn(Modifier.padding(padding).fillMaxSize().imePadding(),contentPadding=PaddingValues(16.dp,16.dp,16.dp,120.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            item{OutlinedTextField(name,{name=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("Tên khoản nợ")});OutlinedTextField(lender,{lender=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("Bên cho vay (không bắt buộc)")})}
            item{DebtMoneyField("Số tiền gốc",original){original=it};DebtMoneyField("Dư nợ hiện tại",current){current=it}}
            item{Row(verticalAlignment=Alignment.CenterVertically){Switch(hasInterest,{hasInterest=it});Text(if(hasInterest)" Có lãi" else " Không lãi",fontWeight=FontWeight.SemiBold)};if(hasInterest)Text("Chỉ nhập số tiền phải trả định kỳ. Tiền gốc có thể tất toán vào bất kỳ thời điểm nào.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            item{DatePickerField("Ngày bắt đầu",start){start=it};DebtChoice("Chu kỳ thanh toán",listOf("monthly" to "Hàng tháng","yearly" to "Hàng năm"),frequency){frequency=it};if(frequency=="monthly")OutlinedTextField(monthlyDay,{monthlyDay=it.filter(Char::isDigit).take(2)},Modifier.fillMaxWidth(),singleLine=true,label={Text("Ngày thanh toán hàng tháng (1–31)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number)) else DatePickerField("Tháng và ngày thanh toán hàng năm",yearlyDate){yearlyDate=it}}
            item{DebtMoneyField(if(hasInterest)"Số tiền phải trả mỗi kỳ" else "Số tiền trả gốc mỗi kỳ",expected){expected=it};OutlinedTextField(note,{note=it},Modifier.fillMaxWidth(),label={Text("Ghi chú")},minLines=2)}
            item{(validation?:error)?.let{Text(it,color=MaterialTheme.colorScheme.error)};Button(enabled=!saving,onClick={
                val o=original.toLongOrNull();val c=current.toLongOrNull();val amount=expected.toLongOrNull();val startDate=runCatching{LocalDate.parse(start)}.getOrNull();val day=monthlyDay.toIntOrNull();val annual=runCatching{LocalDate.parse(yearlyDate)}.getOrNull()
                val firstDue=when{startDate==null->null;frequency=="monthly"&&day in 1..31->nextMonthlyDate(startDate,day!!);frequency=="yearly"&&annual!=null->nextYearlyDate(startDate,annual.monthValue,annual.dayOfMonth);else->null}
                validation=when{name.isBlank()->"Nhập tên khoản nợ";o==null||o<=0->"Tiền gốc phải lớn hơn 0";c==null||c<0||c>o->"Dư nợ hiện tại không hợp lệ";amount==null||amount<=0->"Số tiền phải trả phải lớn hơn 0";startDate==null->"Ngày bắt đầu không hợp lệ";frequency=="monthly"&&day !in 1..31->"Ngày thanh toán phải từ 1 đến 31";firstDue==null->"Ngày thanh toán không hợp lệ";else->null}
                if(validation==null)onSave(DebtInput(name.trim(),lender.trim(),"loan",o!!,c!!,null,if(hasInterest)"monthly" else "none",startDate!!,null,frequency,amount!!,firstDue!!,note.ifBlank{null}))
            },modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){Text(if(saving)"ĐANG LƯU…" else "TẠO KHOẢN NỢ",maxLines=1)}}
        }
    }
}

private fun nextMonthlyDate(start:LocalDate,day:Int):LocalDate {
    fun inMonth(date:LocalDate):LocalDate = date.withDayOfMonth(minOf(day,date.lengthOfMonth()))
    val candidate=inMonth(start)
    return if(candidate.isBefore(start))inMonth(start.plusMonths(1))else candidate
}
private fun nextYearlyDate(start:LocalDate,month:Int,day:Int):LocalDate {
    fun inYear(year:Int):LocalDate {
        val ym=YearMonth.of(year,month)
        return LocalDate.of(year,month,minOf(day,ym.lengthOfMonth()))
    }
    val candidate=inYear(start.year)
    return if(candidate.isBefore(start))inYear(start.year+1)else candidate
}

@Composable
fun DebtDetailScreen(debt:Debt?,snapshot:FinanceSnapshot,onBack:()->Unit,onPay:(String,String,Long,Boolean)->Unit,onReverse:(String)->Unit,onSettle:(String,String,Long,Long)->Unit,onEdit:(String,LocalDate,Long,Long,Long)->Unit){
    if(debt==null){ScreenState(false,"Không tìm thấy khoản nợ",false,onBack){};return}
    val installments=snapshot.installments.filter{it.debtId==debt.id}.sortedBy{it.dueDate};val payments=snapshot.debtPayments.filter{it.debtId==debt.id&&it.reversedAt==null}.sortedByDescending{it.createdAt};val displayMode=if(installments.firstOrNull()?.let{it.principalAmount==0L&&it.interestAmount>0}==true)"interest_only" else debt.paymentMode;var paying by remember{mutableStateOf<DebtInstallment?>(null)};var editing by remember{mutableStateOf<DebtInstallment?>(null)};var settling by remember{mutableStateOf(false)}
    Scaffold(topBar={TopAppBar({Text(debt.name.uppercase(),maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Bold)},navigationIcon={TextButton(onBack){Text("‹ Quay lại")}})}){padding->LazyColumn(Modifier.padding(padding).fillMaxSize(),contentPadding=PaddingValues(16.dp,16.dp,16.dp,110.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Card{Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){DebtLine("Số tiền vay",debt.originalPrincipal.toVnd());DebtLine("Nợ gốc còn lại",debt.currentPrincipal.toVnd());DebtLine("Tổng số tháng","${debt.totalPeriods?:debt.remainingMonths?:installments.size} tháng");DebtLine("Số kỳ còn lại","${debt.remainingMonths?:installments.count{it.status!="paid"}} kỳ");DebtLine(if(displayMode=="interest_only")"Tiền lãi hằng tháng" else "Khoản trả mỗi kỳ",debt.expectedPaymentAmount.toVnd());Text((if(debt.isNewLoan)"Khoản vay mới" else "Khoản vay hiện có")+" • "+when(displayMode){"interest_only"->"Trả lãi";"principal"->"Trả gốc";else->"Trả gốc + lãi"},color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold);if(debt.status!="paid")Button({settling=true},Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("ĐÃ TẤT TOÁN",maxLines=1)}}}}
        item{Text("DANH SÁCH KỲ THANH TOÁN",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
        if(installments.isEmpty())item{Text("Không có kỳ thanh toán đang chờ.")} else items(installments,key={it.id}){i->SimpleInstallment(i,{paying=i}){editing=i}}
        item{Text("LỊCH SỬ THANH TOÁN",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
        if(payments.isEmpty())item{Text("Chưa có thanh toán")};items(payments,key={it.id}){p->Card{Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(p.totalPaid.toVnd(),fontWeight=FontWeight.Bold);Text(p.createdAt.atZone(VietnamZone).toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),style=MaterialTheme.typography.bodySmall)};TextButton({onReverse(p.transactionId)},Modifier.heightIn(min=48.dp)){Text("Hoàn tác",maxLines=1)}}}}
    }}
    paying?.let{i->SimplePaymentDialog(i,snapshot.accounts,{paying=null}){account,amount,advance->onPay(i.id,account,amount,advance);paying=null}}
    editing?.let{i->InstallmentAmountDialog(i,{editing=null}){amount->onEdit(i.id,i.dueDate,amount,0,i.feeAmount);editing=null}}
    if(settling)SettlementDialog(debt,snapshot.accounts,{settling=false}){account,amount,fee->onSettle(debt.id,account,amount,fee);settling=false}
}

@Composable private fun SimpleInstallment(item:DebtInstallment,onPay:()->Unit,onEdit:()->Unit){val remaining=item.totalDue-item.paidAmount;Card{Column(Modifier.fillMaxWidth().padding(14.dp)){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(item.dueDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),fontWeight=FontWeight.Bold);Text("Cần trả ${remaining.toVnd()}",style=MaterialTheme.typography.bodyMedium)}};if(remaining>0)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onEdit,Modifier.weight(1f).heightIn(min=48.dp)){Text("TÙY CHỈNH",maxLines=1)};Button(onPay,Modifier.weight(1f).heightIn(min=48.dp)){Text("THANH TOÁN",maxLines=1)}}}}}
@Composable private fun InstallmentAmountDialog(item:DebtInstallment,onDismiss:()->Unit,onSave:(Long)->Unit){var amount by remember{mutableStateOf(item.principalAmount.toString())};var error by remember{mutableStateOf<String?>(null)};AlertDialog(onDismissRequest=onDismiss,title={Text("TÙY CHỈNH SỐ TIỀN KỲ")},text={Column{DebtMoneyField("Số tiền phải trả kỳ này",amount){amount=it};Text("Số tiền đã trả: ${item.paidAmount.toVnd()}",style=MaterialTheme.typography.bodySmall);error?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={Button({val value=amount.toLongOrNull();error=if(value==null||value<item.paidAmount||value<=0)"Số tiền mới không được thấp hơn số đã trả" else null;if(error==null)onSave(value!!)}){Text("LƯU")}},dismissButton={TextButton(onDismiss){Text("HỦY")}})}
@Composable private fun SimplePaymentDialog(item:DebtInstallment,accounts:List<FinancialAccount>,onDismiss:()->Unit,onSave:(String,Long,Boolean)->Unit){val interestOnly=item.principalAmount==0L&&item.interestAmount>0;var account by remember{mutableStateOf("")};var payInterest by remember{mutableStateOf(true)};var principal by remember{mutableStateOf("0")};var amount by remember{mutableStateOf((item.totalDue-item.paidAmount).toString())};var error by remember{mutableStateOf<String?>(null)};AlertDialog(onDismissRequest=onDismiss,title={Text("GHI NHẬN THANH TOÁN")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){DebtChoice("Tài khoản thanh toán",accounts.map{it.id to it.name},account){account=it};if(interestOnly){Row(verticalAlignment=Alignment.CenterVertically){Checkbox(payInterest,{payInterest=it});Text("Thanh toán lãi kỳ này")};DebtMoneyField("Trả thêm một khoản gốc",principal){principal=it}}else DebtMoneyField("Số tiền trả",amount){amount=it};error?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={Button({val interest=if(interestOnly&&payInterest)item.totalDue-item.paidAmount else 0;val extra=if(interestOnly)principal.toLongOrNull() else 0;val value=if(interestOnly)interest+(extra?:-1) else amount.toLongOrNull();error=when{account.isBlank()->"Chọn tài khoản";value==null||value<=0->"Chọn trả lãi hoặc nhập khoản gốc muốn trả";else->null};if(error==null)onSave(account,value!!,interestOnly&&extra!!>0||value>item.totalDue-item.paidAmount)}){Text("XÁC NHẬN")}},dismissButton={TextButton(onDismiss){Text("HỦY")}})}
@Composable private fun SettlementDialog(debt:Debt,accounts:List<FinancialAccount>,onDismiss:()->Unit,onSave:(String,Long,Long)->Unit){var account by remember{mutableStateOf("")};var amount by remember{mutableStateOf(debt.currentPrincipal.toString())};var fee by remember{mutableStateOf("0")};var error by remember{mutableStateOf<String?>(null)};AlertDialog(onDismissRequest=onDismiss,title={Text("XÁC NHẬN ĐÃ TẤT TOÁN")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Text("Tổng tiền và phí phạt sẽ được trừ khỏi tài khoản đã chọn.");DebtChoice("Tài khoản thanh toán",accounts.map{it.id to it.name},account){account=it};DebtMoneyField("Tổng số tiền tất toán còn lại",amount){amount=it};DebtMoneyField("Phí phạt (nếu có)",fee){fee=it};Text("Tổng trừ: ${((amount.toLongOrNull()?:0)+(fee.toLongOrNull()?:0)).toVnd()}",fontWeight=FontWeight.Bold);error?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={Button({val value=amount.toLongOrNull();val penalty=fee.toLongOrNull();error=when{account.isBlank()->"Chọn tài khoản";value==null||value<0->"Số tiền tất toán không hợp lệ";penalty==null||penalty<0->"Phí phạt không hợp lệ";value+penalty<=0->"Tổng tiền phải lớn hơn 0";else->null};if(error==null)onSave(account,value!!,penalty!!)}){Text("XÁC NHẬN")}},dismissButton={TextButton(onDismiss){Text("HỦY")}})}
@Composable private fun DebtMoneyField(label:String,value:String,onChange:(String)->Unit)=OutlinedTextField(value,{onChange(it.filter(Char::isDigit).take(18))},Modifier.fillMaxWidth(),singleLine=true,label={Text(label)},supportingText={Text(value.toLongOrNull()?.toVnd().orEmpty())},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
@Composable private fun DebtChoice(label:String,items:List<Pair<String,String>>,selected:String,onSelect:(String)->Unit){var open by remember{mutableStateOf(false)};Box{OutlinedButton({open=true},Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("$label: ${items.firstOrNull{it.first==selected}?.second?:"Chọn"}",Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)};DropdownMenu(open,{open=false}){items.forEach{(id,title)->DropdownMenuItem({Text(title)},{onSelect(id);open=false})}}}}
@Composable private fun DebtLine(label:String,value:String)=Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label);Text(value,fontWeight=FontWeight.Bold)}
