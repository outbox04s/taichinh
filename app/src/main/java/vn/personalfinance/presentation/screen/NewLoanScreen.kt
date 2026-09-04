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
import vn.personalfinance.presentation.components.glass.GlassCard
import vn.personalfinance.presentation.components.glass.GlassSegmentedControl
import vn.personalfinance.presentation.theme.LiquidGlassColors
import vn.personalfinance.presentation.toVnd
import java.time.LocalDate

@Composable fun CreateDebtScreen(accounts:List<FinancialAccount>,saving:Boolean,error:String?,onBack:()->Unit,onSave:(DebtInput)->Unit){
 var existing by remember{mutableStateOf(true)};var name by remember{mutableStateOf("")};var lender by remember{mutableStateOf("")};var principal by remember{mutableStateOf("")};var account by remember{mutableStateOf("")};var hasInterest by remember{mutableStateOf(true)};var mode by remember{mutableStateOf("principal_interest")};var payment by remember{mutableStateOf("")};var totalPeriods by remember{mutableStateOf("")};var remainingPeriods by remember{mutableStateOf("")};var firstDue by remember{mutableStateOf(LocalDate.now(VietnamZone).plusMonths(1).toString())};var note by remember{mutableStateOf("")};var validation by remember{mutableStateOf<String?>(null)}
 LaunchedEffect(hasInterest){if(hasInterest&&mode=="principal")mode="principal_interest"}
 Scaffold(containerColor=LiquidGlassColors.Background,topBar={TopAppBar({Text("TẠO KHOẢN VAY",fontWeight=FontWeight.Bold)},navigationIcon={TextButton(onBack){Text("‹ Quay lại")}},colors=TopAppBarDefaults.topAppBarColors(containerColor=LiquidGlassColors.Background))}){pad->LazyColumn(Modifier.padding(pad).fillMaxSize().imePadding(),contentPadding=PaddingValues(16.dp,12.dp,16.dp,120.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  item{GlassSegmentedControl(listOf(true to "ĐANG HIỆN CÓ",false to "KHOẢN MỚI"),existing,onSelected={existing=it})}
  item{GlassCard{Text(if(existing)"Nhập số tiền vay gốc đang còn theo dõi. App không cộng tiền vào tài khoản." else "Khoản mới sẽ cộng số tiền vay vào tài khoản nhận.",color=LiquidGlassColors.TextSecondary)}}
  item{GlassCard{Field("Tên khoản vay",name){name=it};Field("Đơn vị/người cho vay",lender){lender=it};MoneyField("Số tiền vay",principal){principal=it};if(!existing)Choice("Tài khoản nhận tiền",accounts.map{it.id to it.name},account){account=it}}}
  item{GlassCard{Row(verticalAlignment=Alignment.CenterVertically){Switch(hasInterest,{hasInterest=it});Text(if(hasInterest)"Có lãi" else "Không lãi",Modifier.padding(start=8.dp),fontWeight=FontWeight.SemiBold)};Choice("Hình thức thanh toán",buildList{add("principal_interest" to "Trả gốc + lãi");if(!hasInterest)add("principal" to "Trả gốc");if(hasInterest)add("interest_only" to "Trả lãi")},mode){mode=it};Text(when(mode){"interest_only"->"Tiền hàng tháng chỉ là lãi, không giảm nợ gốc. Khi thanh toán có thể nhập thêm một khoản gốc riêng.";"principal"->"Toàn bộ khoản trả sẽ trừ vào tiền vay gốc.";else->"Phần gốc của mỗi kỳ sẽ được trừ trực tiếp vào tiền vay."},style=MaterialTheme.typography.bodySmall,color=LiquidGlassColors.TextSecondary);MoneyField(if(mode=="interest_only")"Tiền lãi trả hằng tháng" else "Số tiền trả mỗi kỳ",payment){payment=it}}}
  item{GlassCard{Field("Tổng số tháng khoản vay",totalPeriods,true){totalPeriods=it.filter(Char::isDigit).take(3)};if(existing)Field("Số kỳ còn lại",remainingPeriods,true){remainingPeriods=it.filter(Char::isDigit).take(3)};DatePickerField("Thời gian trả tiền đầu tiên",firstDue){firstDue=it};Field("Ghi chú",note){note=it}}}
  item{(validation?:error)?.let{Text(it,color=MaterialTheme.colorScheme.error)};Button(enabled=!saving,onClick={val p=principal.toLongOrNull();val amount=payment.toLongOrNull();val total=totalPeriods.toIntOrNull();val left=(if(existing)remainingPeriods else totalPeriods).toIntOrNull();val due=runCatching{LocalDate.parse(firstDue)}.getOrNull();validation=when{name.isBlank()->"Nhập tên khoản vay";p==null||p<=0->"Số tiền vay phải lớn hơn 0";!existing&&account.isBlank()->"Chọn tài khoản nhận tiền";amount==null||amount<=0->"Số tiền trả mỗi kỳ phải lớn hơn 0";total==null||total<=0->"Tổng số tháng phải lớn hơn 0";left==null||left<=0||left>total->"Số kỳ còn lại phải từ 1 đến tổng số tháng";due==null->"Thời gian trả đầu tiên không hợp lệ";mode!="interest_only"&&amount*left<p->"Số tiền mỗi kỳ × số kỳ còn lại chưa đủ số tiền vay";else->null};if(validation==null)onSave(DebtInput(name.trim(),lender.trim(),"loan",p!!,p,null,if(hasInterest)"monthly" else "none",LocalDate.now(VietnamZone),null,"monthly",amount!!,due!!,note.ifBlank{null},if(existing)0 else p,!existing,left,if(existing)null else account,total,mode))},modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){Text(if(saving)"ĐANG LƯU…" else "LƯU KHOẢN VAY",fontWeight=FontWeight.Bold)}}
 }}}
@Composable private fun Field(label:String,value:String,numeric:Boolean=false,onChange:(String)->Unit)=OutlinedTextField(value,onChange,Modifier.fillMaxWidth().padding(vertical=4.dp),singleLine=true,label={Text(label)},keyboardOptions=KeyboardOptions(keyboardType=if(numeric)KeyboardType.Number else KeyboardType.Text))
@Composable private fun MoneyField(label:String,value:String,onChange:(String)->Unit)=OutlinedTextField(value,{onChange(it.filter(Char::isDigit).take(18))},Modifier.fillMaxWidth().padding(vertical=4.dp),singleLine=true,label={Text(label)},supportingText={Text(value.toLongOrNull()?.toVnd().orEmpty())},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
@Composable private fun Choice(label:String,items:List<Pair<String,String>>,selected:String,onSelect:(String)->Unit){var open by remember{mutableStateOf(false)};Box{OutlinedButton({open=true},Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("$label: ${items.firstOrNull{it.first==selected}?.second?:"Chọn"}",Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)};DropdownMenu(open,{open=false}){items.forEach{(id,title)->DropdownMenuItem({Text(title)},{onSelect(id);open=false})}}}}
