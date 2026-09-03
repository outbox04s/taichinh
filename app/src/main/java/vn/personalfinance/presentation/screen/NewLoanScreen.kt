@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package vn.personalfinance.presentation.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vn.personalfinance.domain.model.FinancialAccount
import vn.personalfinance.domain.repository.DebtInput
import vn.personalfinance.presentation.VietnamZone
import vn.personalfinance.presentation.toVnd
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun CreateDebtScreen(accounts:List<FinancialAccount>,saving:Boolean,error:String?,onBack:()->Unit,onSave:(DebtInput)->Unit){
    var isNew by remember{mutableStateOf(true)};var name by remember{mutableStateOf("")};var lender by remember{mutableStateOf("")};var total by remember{mutableStateOf("")};var received by remember{mutableStateOf("")};var account by remember{mutableStateOf("")};var hasInterest by remember{mutableStateOf(true)}
    var monthly by remember{mutableStateOf("")};var months by remember{mutableStateOf("")};var frequency by remember{mutableStateOf("monthly")};var payDay by remember{mutableStateOf(LocalDate.now(VietnamZone).dayOfMonth.toString())};var annualDate by remember{mutableStateOf(LocalDate.now(VietnamZone).plusYears(1).toString())};var note by remember{mutableStateOf("")};var validation by remember{mutableStateOf<String?>(null)}
    Scaffold(topBar={TopAppBar({Text("TẠO KHOẢN VAY",fontWeight=FontWeight.Bold)},navigationIcon={TextButton(onBack){Text("‹ Quay lại")}})}){padding->LazyColumn(Modifier.padding(padding).fillMaxSize().imePadding(),contentPadding=PaddingValues(16.dp,16.dp,16.dp,120.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){listOf(true to "KHOẢN MỚI",false to "ĐANG HIỆN CÓ").forEachIndexed{i,(value,label)->SegmentedButton(isNew==value,{isNew=value},SegmentedButtonDefaults.itemShape(i,2)){Text(label,maxLines=1)}}};Text(if(isNew)"Tiền thực nhận sẽ được cộng vào tài khoản đã chọn." else "Chỉ ghi nhận dư nợ hiện tại, không cộng lại tiền vào tài khoản.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        item{OutlinedTextField(name,{name=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("Tên khoản vay")});OutlinedTextField(lender,{lender=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("Đơn vị/người cho vay")})}
        item{LoanMoneyField("Tổng tiền phải trả",total){total=it};LoanMoneyField("Số tiền thực nhận",received){received=it};if(isNew)LoanAccountPicker(accounts,account){account=it}}
        item{Row(verticalAlignment=Alignment.CenterVertically){Switch(hasInterest,{hasInterest=it});Text(if(hasInterest)" Có lãi" else " Không lãi",fontWeight=FontWeight.SemiBold)};LoanMoneyField("Số tiền trả mỗi kỳ",monthly){monthly=it};OutlinedTextField(months,{months=it.filter(Char::isDigit).take(3)},Modifier.fillMaxWidth(),singleLine=true,label={Text("Số tháng/kỳ còn lại")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))}
        item{LoanChoice("Chu kỳ thanh toán",listOf("monthly" to "Hàng tháng","yearly" to "Hàng năm"),frequency){frequency=it};if(frequency=="monthly")OutlinedTextField(payDay,{payDay=it.filter(Char::isDigit).take(2)},Modifier.fillMaxWidth(),singleLine=true,label={Text("Ngày thanh toán hàng tháng (1–31)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))else DatePickerField("Tháng và ngày thanh toán hàng năm",annualDate){annualDate=it};OutlinedTextField(note,{note=it},Modifier.fillMaxWidth(),label={Text("Ghi chú")},minLines=2)}
        item{(validation?:error)?.let{Text(it,color=MaterialTheme.colorScheme.error)};Button(enabled=!saving,onClick={val totalValue=total.toLongOrNull();val receivedValue=received.toLongOrNull();val payment=monthly.toLongOrNull();val count=months.toIntOrNull();val day=payDay.toIntOrNull();val annual=runCatching{LocalDate.parse(annualDate)}.getOrNull();val today=LocalDate.now(VietnamZone);val due=when{frequency=="monthly"&&day in 1..31->loanMonthlyDate(today,day!!);frequency=="yearly"&&annual!=null->loanYearlyDate(today,annual.monthValue,annual.dayOfMonth);else->null};validation=when{name.isBlank()->"Nhập tên khoản vay";totalValue==null||totalValue<=0->"Tổng tiền phải trả phải lớn hơn 0";receivedValue==null||receivedValue<0->"Số tiền thực nhận không hợp lệ";isNew&&receivedValue>0&&account.isBlank()->"Chọn tài khoản nhận tiền";payment==null||payment<=0->"Số tiền trả mỗi kỳ phải lớn hơn 0";count==null||count<=0->"Số kỳ còn lại phải lớn hơn 0";payment!=null&&count!=null&&payment*count<totalValue->"Số tiền mỗi kỳ × số kỳ chưa đủ tổng tiền phải trả";due==null->"Ngày thanh toán không hợp lệ";else->null};if(validation==null)onSave(DebtInput(name.trim(),lender.trim(),"loan",totalValue!!,totalValue,null,if(hasInterest)"monthly" else "none",today,null,frequency,payment!!,due!!,note.ifBlank{null},receivedValue!!,isNew,count,if(isNew)account else null))},modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){Text(if(saving)"ĐANG LƯU…" else "TẠO KHOẢN VAY",maxLines=1)}}
    }}
}

private fun loanMonthlyDate(start:LocalDate,day:Int):LocalDate{val first=start.withDayOfMonth(minOf(day,start.lengthOfMonth()));val next=start.plusMonths(1);return if(first.isBefore(start))next.withDayOfMonth(minOf(day,next.lengthOfMonth()))else first}
private fun loanYearlyDate(start:LocalDate,month:Int,day:Int):LocalDate{fun date(year:Int):LocalDate{val ym=YearMonth.of(year,month);return LocalDate.of(year,month,minOf(day,ym.lengthOfMonth()))};val first=date(start.year);return if(first.isBefore(start))date(start.year+1)else first}
@Composable private fun LoanMoneyField(label:String,value:String,onChange:(String)->Unit)=OutlinedTextField(value,{onChange(it.filter(Char::isDigit).take(18))},Modifier.fillMaxWidth(),singleLine=true,label={Text(label)},supportingText={Text(value.toLongOrNull()?.toVnd().orEmpty())},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
@Composable private fun LoanChoice(label:String,items:List<Pair<String,String>>,selected:String,onSelect:(String)->Unit){var open by remember{mutableStateOf(false)};Box{OutlinedButton({open=true},Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("$label: ${items.firstOrNull{it.first==selected}?.second?:"Chọn"}",Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)};DropdownMenu(open,{open=false}){items.forEach{(id,title)->DropdownMenuItem({Text(title)},{onSelect(id);open=false})}}}}
@Composable private fun LoanAccountPicker(accounts:List<FinancialAccount>,selected:String,onSelect:(String)->Unit)=LoanChoice("Tài khoản nhận tiền",accounts.map{it.id to it.name},selected,onSelect)
