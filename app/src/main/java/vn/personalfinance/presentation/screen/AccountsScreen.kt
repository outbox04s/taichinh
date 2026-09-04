package vn.personalfinance.presentation.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import vn.personalfinance.domain.model.FinancialAccount
import vn.personalfinance.domain.repository.AccountInput
import vn.personalfinance.presentation.FinanceUiState
import vn.personalfinance.presentation.toVnd
import java.net.HttpURLConnection
import java.net.URL

data class VietQrBank(val id:Int,val code:String,val shortName:String,val name:String,val logo:String?)

@Composable
fun AccountsScreen(state:FinanceUiState,onRetry:()->Unit,onAdd:(AccountInput)->Unit,onDelete:(String)->Unit){
    var showAdd by remember{mutableStateOf(false)};var deleting by remember{mutableStateOf<FinancialAccount?>(null)}
    ScreenState(state.loading,state.error,false,onRetry){LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp,16.dp,16.dp,110.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("TÀI KHOẢN",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text("Tổng số dư ${state.snapshot.accounts.sumOf{it.currentBalance}.toVnd()}",color=MaterialTheme.colorScheme.onSurfaceVariant)};FilledTonalIconButton({showAdd=true}){Icon(Icons.Rounded.Add,"Thêm tài khoản")}}}
        if(state.snapshot.accounts.isEmpty())item{Card{Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Rounded.AccountBalance,null,Modifier.size(38.dp));Text("CHƯA CÓ TÀI KHOẢN",fontWeight=FontWeight.Bold);Button({showAdd=true},Modifier.padding(top=10.dp).heightIn(min=48.dp)){Text("THÊM TÀI KHOẢN")}}}} else items(state.snapshot.accounts,key={it.id}){AccountRow(it){deleting=it}}
    }}
    if(showAdd)AddBankAccountDialog({showAdd=false}){onAdd(it);showAdd=false}
    deleting?.let{account->DeleteDialog("Xóa tài khoản?","${account.name} sẽ được ẩn. Lịch sử giao dịch vẫn được giữ.",{deleting=null}){onDelete(account.id);deleting=null}}
}

@Composable private fun AccountRow(account:FinancialAccount,onLongClick:()->Unit){Card(Modifier.fillMaxWidth().combinedClickable(onClick={},onLongClick=onLongClick)){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){BankLogo(account.bankLogo,Modifier.size(38.dp));Column(Modifier.weight(1f).padding(horizontal=12.dp)){Text(account.name,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis);Text("${account.bankShortName?:"Ngân hàng"} • ${account.accountNumber?:"Chưa có số tài khoản"}",maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall);account.purpose?.let{Text(it,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)}};Text(account.currentBalance.toVnd(),fontWeight=FontWeight.Bold,maxLines=1)}}}

@Composable private fun DeleteDialog(title:String,message:String,onDismiss:()->Unit,onDelete:()->Unit)=AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Text(message)},confirmButton={Button(onDelete,colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)){Text("XÓA")}},dismissButton={TextButton(onDismiss){Text("HỦY")}})

@Composable private fun AddBankAccountDialog(onDismiss:()->Unit,onSave:(AccountInput)->Unit){
    var banks by remember{mutableStateOf<List<VietQrBank>>(emptyList())};var loading by remember{mutableStateOf(true)};var loadError by remember{mutableStateOf<String?>(null)};var query by remember{mutableStateOf("")};var selected by remember{mutableStateOf<VietQrBank?>(null)};var displayName by remember{mutableStateOf("")};var accountNumber by remember{mutableStateOf("")};var purpose by remember{mutableStateOf("")};var balance by remember{mutableStateOf("")};var validation by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(Unit){runCatching{fetchVietQrBanks()}.fold({banks=it;loading=false},{loadError="Không tải được danh sách ngân hàng. Kiểm tra mạng rồi thử lại.";loading=false})}
    val normalized=query.trim();val filtered=remember(banks,normalized){if(normalized.isBlank())banks else banks.filter{it.code.contains(normalized,true)||it.shortName.contains(normalized,true)||it.name.contains(normalized,true)}.sortedBy{if(it.code.startsWith(normalized,true)||it.shortName.startsWith(normalized,true))0 else 1}.take(20)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("THÊM TÀI KHOẢN NGÂN HÀNG",maxLines=1,overflow=TextOverflow.Ellipsis)},text={Column(Modifier.heightIn(max=560.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){selected?.let{BankChoiceRow(it){selected=null;query=""}}?:run{OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("Tìm mã hoặc tên ngân hàng")},leadingIcon={Icon(Icons.Rounded.Search,null)});when{loading->LinearProgressIndicator(Modifier.fillMaxWidth());loadError!=null->Text(loadError!!,color=MaterialTheme.colorScheme.error);else->LazyColumn(Modifier.heightIn(max=190.dp)){items(filtered,key={it.id}){bank->BankChoiceRow(bank){selected=bank;displayName=bank.shortName}}}}};OutlinedTextField(accountNumber,{accountNumber=it.filter(Char::isDigit).take(24)},Modifier.fillMaxWidth(),singleLine=true,label={Text("Số tài khoản")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number));OutlinedTextField(displayName,{displayName=it.take(60)},Modifier.fillMaxWidth(),singleLine=true,label={Text("Tên hiển thị")});OutlinedTextField(purpose,{purpose=it.take(120)},Modifier.fillMaxWidth(),singleLine=true,label={Text("Mục đích tài khoản")});OutlinedTextField(balance,{balance=it.filter(Char::isDigit).take(18)},Modifier.fillMaxWidth(),singleLine=true,label={Text("Số dư hiện có")},supportingText={Text(balance.toLongOrNull()?.toVnd().orEmpty())},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number));validation?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={Button({val bank=selected;val value=balance.toLongOrNull();validation=when{bank==null->"Chọn ngân hàng";accountNumber.length<4->"Nhập số tài khoản hợp lệ";displayName.isBlank()->"Nhập tên hiển thị";purpose.isBlank()->"Nhập mục đích tài khoản";value==null||value<0->"Số dư không hợp lệ";else->null};if(validation==null)onSave(AccountInput(displayName.trim(),value!!,bank!!.shortName,bank.name,bank.logo,accountNumber,purpose.trim()))}){Text("THÊM")}},dismissButton={TextButton(onDismiss){Text("HỦY")}})
}

@Composable private fun BankChoiceRow(bank:VietQrBank,onClick:()->Unit){Surface(onClick=onClick,shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceContainerLow){Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically){BankLogo(bank.logo,Modifier.size(32.dp));Text("${bank.shortName} - ${bank.name}",Modifier.padding(start=10.dp).weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)}}}

private val bankLogoCache=object:LruCache<String,Bitmap>(30){}
@Composable private fun BankLogo(url:String?,modifier:Modifier=Modifier){var bitmap by remember(url){mutableStateOf(url?.let(bankLogoCache::get))};LaunchedEffect(url){if(url!=null&&bitmap==null)bitmap=withContext(Dispatchers.IO){runCatching{URL(url).openStream().use(BitmapFactory::decodeStream)?.also{bankLogoCache.put(url,it)}}.getOrNull()}};if(bitmap!=null)Image(bitmap!!.asImageBitmap(),null,modifier,contentScale=ContentScale.Fit)else Surface(modifier,shape=RoundedCornerShape(8.dp),color=MaterialTheme.colorScheme.primaryContainer){Box(contentAlignment=Alignment.Center){Icon(Icons.Rounded.AccountBalance,null,Modifier.size(18.dp))}}}

private suspend fun fetchVietQrBanks():List<VietQrBank> = withContext(Dispatchers.IO){val connection=(URL("https://api.vietqr.io/v2/banks").openConnection() as HttpURLConnection).apply{connectTimeout=15_000;readTimeout=20_000;setRequestProperty("Accept","application/json")};try{require(connection.responseCode in 200..299){"VietQR HTTP ${connection.responseCode}"};val data=JSONObject(connection.inputStream.bufferedReader().use{it.readText()}).getJSONArray("data");buildList{for(index in 0 until data.length()){val item=data.getJSONObject(index);add(VietQrBank(item.getInt("id"),item.optString("code"),item.optString("shortName"),item.optString("name"),item.optString("logo").takeIf(String::isNotBlank)))}}}finally{connection.disconnect()}}
