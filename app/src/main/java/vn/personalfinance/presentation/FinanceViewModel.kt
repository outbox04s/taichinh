package vn.personalfinance.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.personalfinance.domain.model.*
import vn.personalfinance.domain.repository.*
import vn.personalfinance.BuildConfig
import vn.personalfinance.data.update.AppUpdateInstaller
import vn.personalfinance.data.update.UpdateInstallResult
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

enum class PeriodFilter { WEEK, MONTH, QUARTER, YEAR, CUSTOM }
enum class DebtSort { DUE_DATE, BALANCE, RISK }
data class FinanceUiState(
    val loading:Boolean=true,val saving:Boolean=false,val error:String?=null,val snapshot:FinanceSnapshot=FinanceSnapshot(),
    val period:PeriodFilter=PeriodFilter.MONTH,val customStart:LocalDate?=null,val customEnd:LocalDate?=null,val accountId:String?=null,
    val search:String="",val transactionType:TransactionType?=null,val source:TransactionSource?=null,val categoryId:String?=null,val debtSort:DebtSort=DebtSort.DUE_DATE,val lastSePaySync:Instant?=null,
    val availableUpdate:AppRelease?=null,val updateInstalling:Boolean=false,val updateError:String?=null,
)

@HiltViewModel
class FinanceViewModel @Inject constructor(private val repository:FinanceRepository,private val updateInstaller:AppUpdateInstaller):ViewModel(){
    private val local=MutableStateFlow(FinanceUiState())
    val uiState:StateFlow<FinanceUiState> = combine(local,repository.snapshot){state,data->state.copy(snapshot=data)}
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),local.value)
    init{refresh();checkForUpdate()}
    fun refresh()=viewModelScope.launch{local.value=local.value.copy(loading=true,error=null);repository.refresh().fold({local.value=local.value.copy(loading=false)},{local.value=local.value.copy(loading=false,error=it.userMessage())})}
    fun setPeriod(value:PeriodFilter)=update{copy(period=value)}
    fun setAccount(value:String?)=update{copy(accountId=value)}
    fun setCustomRange(start:LocalDate,end:LocalDate)=update{copy(period=PeriodFilter.CUSTOM,customStart=start,customEnd=end)}
    fun setSearch(value:String)=update{copy(search=value)}
    fun setType(value:TransactionType?)=update{copy(transactionType=value)}
    fun setSource(value:TransactionSource?)=update{copy(source=value)}
    fun setCategory(value:String?)=update{copy(categoryId=value)}
    fun addTransaction(input:ManualTransactionInput,onDone:()->Unit)=mutate(onDone){repository.addManualTransaction(input)}
    fun editTransaction(id:String,edits:TransactionEdits)=mutate{repository.updateTransaction(id,edits)}
    fun deleteTransaction(id:String)=mutate{repository.softDeleteManualTransaction(id)}
    fun transfer(from:String,to:String,amount:Long,description:String?,onDone:()->Unit)=mutate(onDone){repository.transfer(from,to,amount,Instant.now(),description)}
    fun addIncome(input:IncomeSourceInput)=mutate{repository.addIncomeSource(input)}
    fun linkIncome(paymentId:String,transactionId:String,amount:Long)=mutate{repository.linkIncomePayment(paymentId,transactionId,amount)}
    fun addBudget(input:BudgetInput)=mutate{repository.addBudget(input)}
    fun setDebtSort(value:DebtSort)=update{copy(debtSort=value)}
    fun addDebt(input:DebtInput,onDone:(String)->Unit)=viewModelScope.launch{local.value=local.value.copy(saving=true,error=null);repository.addDebt(input).fold({id->local.value=local.value.copy(saving=false);onDone(id)},{local.value=local.value.copy(saving=false,error=it.userMessage())})}
    fun payDebt(installmentId:String,accountId:String,amount:Long,advance:Boolean)=mutate{repository.recordDebtPayment(installmentId,accountId,amount,advance)}
    fun reverseDebtPayment(transactionId:String)=mutate{repository.reverseDebtPayment(transactionId)}
    fun settleDebt(debtId:String)=mutate{repository.confirmDebtSettlement(debtId)}
    fun updateInstallment(id:String,due:LocalDate,principal:Long,interest:Long,fee:Long)=mutate{repository.updateDebtInstallment(id,due,principal,interest,fee)}
    fun reconcileSePay()=mutate(onDone={local.value=local.value.copy(lastSePaySync=Instant.now())}){repository.reconcileSePay()}
    fun checkForUpdate()=viewModelScope.launch{repository.latestAppRelease().onSuccess{release->local.value=local.value.copy(availableUpdate=release?.takeIf{it.versionCode>BuildConfig.VERSION_CODE})}}
    fun dismissUpdate(){if(local.value.availableUpdate?.mandatory!=true)local.value=local.value.copy(availableUpdate=null)}
    fun installUpdate(){val release=local.value.availableUpdate?:return;viewModelScope.launch{
        local.value=local.value.copy(updateInstalling=true,updateError=null)
        runCatching{updateInstaller.install(release.apkUrl,release.versionCode)}.fold(
            {result->local.value=local.value.copy(updateInstalling=false,updateError=if(result==UpdateInstallResult.PermissionRequired)"Hãy bật Cho phép từ nguồn này, quay lại app rồi nhấn UPDATE lần nữa." else null)},
            {failure->local.value=local.value.copy(updateInstalling=false,updateError=failure.message?:"Không thể chuẩn bị bản cập nhật.")}
        )
    }}
    private fun mutate(onDone:()->Unit={},block:suspend()->Result<Unit>)=viewModelScope.launch{local.value=local.value.copy(saving=true,error=null);block().fold({local.value=local.value.copy(saving=false);onDone()},{local.value=local.value.copy(saving=false,error=it.userMessage())})}
    private fun update(block:FinanceUiState.()->FinanceUiState){local.value=local.value.block()}
    private fun Throwable.userMessage():String {
        val detail=message.orEmpty()
        return when {
            detail.contains("NOT_FOUND",true) && detail.contains("sepay-sync",true) -> "Tính năng đồng bộ SePay chưa được kích hoạt. Vui lòng triển khai Edge Function rồi thử lại."
            detail.contains("PGRST205",true) || detail.contains("schema cache",true) -> "Cơ sở dữ liệu chưa được cập nhật đầy đủ. Vui lòng chạy migration Supabase rồi thử lại."
            detail.contains("network",true) || detail.contains("Unable to resolve host",true) -> "Không thể kết nối mạng. Vui lòng kiểm tra Internet rồi thử lại."
            detail.contains("Phiên",true) || detail.contains("JWT",true) || detail.contains("unauthorized",true) -> "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
            else -> "Đã xảy ra lỗi khi xử lý dữ liệu. Vui lòng thử lại sau."
        }
    }
}
