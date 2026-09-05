package vn.personalfinance.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.functions.functions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.put
import vn.personalfinance.domain.model.*
import vn.personalfinance.domain.repository.*
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Serializable private data class AccountDto(val id:String,val name:String,val type:String,@SerialName("current_balance") val balance:Long,@SerialName("is_active") val active:Boolean,@SerialName("bank_short_name")val bankShortName:String?=null,@SerialName("bank_name")val bankName:String?=null,@SerialName("bank_logo")val bankLogo:String?=null,@SerialName("masked_account_number")val accountNumber:String?=null,val purpose:String?=null)
@Serializable private data class CategoryDto(val id:String,val name:String,val type:String,val icon:String?=null,val color:String?=null,@SerialName("is_essential") val essential:Boolean=false)
@Serializable private data class TransactionDto(val id:String,@SerialName("account_id") val accountId:String,@SerialName("category_id") val categoryId:String?=null,val type:String,val amount:Long,@SerialName("transaction_at") val at:String,val description:String?=null,val note:String?=null,val source:String,val status:String,@SerialName("transfer_group_id") val transferGroupId:String?=null,@SerialName("deleted_at") val deletedAt:String?=null)
@Serializable private data class BudgetDto(val id:String,@SerialName("category_id") val categoryId:String?=null,val period:String,@SerialName("limit_amount") val limit:Long,@SerialName("start_date") val start:String,@SerialName("end_date") val end:String,@SerialName("alert_percent") val alert:Int,@SerialName("is_active") val active:Boolean)
@Serializable private data class DebtDto(val id:String,val name:String,@SerialName("lender_name") val lender:String?=null,@SerialName("debt_type") val debtType:String,@SerialName("original_principal") val original:Long,@SerialName("current_principal") val principal:Long,@SerialName("interest_rate") val rate:Double?=null,@SerialName("interest_type") val interestType:String,@SerialName("start_date") val start:String,@SerialName("maturity_date") val maturity:String?=null,@SerialName("payment_frequency") val frequency:String,@SerialName("expected_payment_amount") val expected:Long,@SerialName("next_due_date") val due:String?=null,val status:String,val note:String?=null,@SerialName("received_amount")val received:Long=0,@SerialName("is_new_loan")val isNew:Boolean=false,@SerialName("remaining_months")val months:Int?=null,@SerialName("total_periods")val totalPeriods:Int?=null,@SerialName("payment_mode")val paymentMode:String="principal_interest",@SerialName("is_archived")val archived:Boolean=false)
@Serializable private data class InstallmentDto(val id:String,@SerialName("debt_id") val debtId:String,@SerialName("due_date") val due:String,@SerialName("principal_amount") val principal:Long,@SerialName("interest_amount") val interest:Long,@SerialName("fee_amount") val fee:Long,@SerialName("total_due") val total:Long,@SerialName("paid_amount") val paid:Long,@SerialName("paid_at") val paidAt:String?=null,val status:String)
@Serializable private data class AllocationDto(val id:String,@SerialName("debt_id") val debtId:String,@SerialName("installment_id") val installmentId:String,@SerialName("transaction_id") val transactionId:String,@SerialName("total_paid") val total:Long,@SerialName("principal_paid") val principal:Long,@SerialName("interest_paid") val interest:Long,@SerialName("fee_paid") val fee:Long,@SerialName("advance_principal") val advance:Long,@SerialName("reversed_at") val reversed:String?=null,@SerialName("created_at") val created:String)
@Serializable private data class ReminderDto(@SerialName("installment_id")val id:String,@SerialName("debt_name")val name:String,@SerialName("due_date")val due:String,@SerialName("remaining_amount")val amount:Long,@SerialName("notification_type")val type:String)
@Serializable private data class IncomeSourceDto(val id:String,val name:String,val type:String,@SerialName("expected_amount") val amount:Long,@SerialName("pay_day") val payDay:Int?=null,val frequency:String,@SerialName("is_active") val active:Boolean,@SerialName("next_expected_date") val next:String?=null)
@Serializable private data class IncomePaymentDto(val id:String,@SerialName("income_source_id") val sourceId:String,@SerialName("expected_date") val date:String,@SerialName("expected_amount") val expected:Long,@SerialName("transaction_id") val transactionId:String?=null,@SerialName("actual_amount") val actual:Long?=null)
@Serializable private data class RecurringDto(val id:String,val type:String,val amount:Long,@SerialName("is_active") val active:Boolean,val title:String,@SerialName("account_id")val accountId:String,@SerialName("category_id")val categoryId:String,val frequency:String,@SerialName("start_date")val start:String,@SerialName("end_date")val end:String?=null)
@Serializable private data class AppReleaseDto(@SerialName("version_code")val versionCode:Long,@SerialName("version_name")val versionName:String,@SerialName("apk_url")val apkUrl:String,@SerialName("release_notes")val releaseNotes:String="",@SerialName("is_mandatory")val mandatory:Boolean=false)
@Serializable private data class ManualInsert(@SerialName("user_id") val userId:String,@SerialName("account_id") val accountId:String,@SerialName("category_id") val categoryId:String,val type:String,val amount:Long,@SerialName("transaction_at") val at:String,val description:String?,val note:String?,val source:String="manual",val status:String="confirmed")
@Serializable private data class IncomeInsert(@SerialName("user_id") val userId:String,val name:String,val type:String,@SerialName("expected_amount") val amount:Long,@SerialName("pay_day") val payDay:Int?,val frequency:String,@SerialName("next_expected_date") val next:String?)
@Serializable private data class BudgetInsert(@SerialName("user_id") val userId:String,@SerialName("category_id") val categoryId:String?,val period:String,@SerialName("limit_amount") val amount:Long,@SerialName("start_date") val start:String,@SerialName("end_date") val end:String,@SerialName("alert_percent") val alert:Int)
@Serializable private data class CategoryInsert(@SerialName("user_id") val userId:String,val name:String,val type:String,val icon:String="category",val color:String="#1769E0",@SerialName("is_essential")val essential:Boolean=false,@SerialName("is_system")val system:Boolean=false)
@Serializable private data class AccountInsert(@SerialName("user_id")val userId:String,val name:String,val type:String,@SerialName("bank_name")val bankName:String,@SerialName("bank_short_name")val bankShortName:String,@SerialName("bank_logo")val bankLogo:String?,@SerialName("masked_account_number")val accountNumber:String,val purpose:String,@SerialName("opening_balance")val openingBalance:Long)
@Singleton
class SupabaseFinanceRepository @Inject constructor(private val client: SupabaseClient) : FinanceRepository {
    private val mutex=Mutex(); private val _snapshot=MutableStateFlow(FinanceSnapshot()); override val snapshot:StateFlow<FinanceSnapshot> = _snapshot.asStateFlow()
    private suspend fun userId():String {
        client.auth.awaitInitialization()
        if(client.auth.currentUserOrNull()==null) client.auth.signInAnonymously()
        return requireNotNull(client.auth.currentUserOrNull()?.id){"Không thể khởi tạo phiên sử dụng"}
    }
    override suspend fun refresh():Result<Unit> = runCatching { mutex.withLock {
        userId()
        coroutineScope {
        val values=listOf(
            async { client.from("financial_accounts").select().decodeList<AccountDto>() }, async { client.from("categories").select().decodeList<CategoryDto>() },
            async { client.from("transactions").select().decodeList<TransactionDto>() }, async { client.from("budgets").select().decodeList<BudgetDto>() },
            async { client.from("debts").select().decodeList<DebtDto>() }, async { client.from("debt_installments").select().decodeList<InstallmentDto>() }, async {
                runCatching { client.from("debt_payment_allocations").select().decodeList<AllocationDto>() }
                    .getOrElse { error -> if (error.message.orEmpty().contains("PGRST205",true)) emptyList() else throw error }
            },
            async { client.from("income_sources").select().decodeList<IncomeSourceDto>() }, async { client.from("income_payments").select().decodeList<IncomePaymentDto>() },
            async { client.from("recurring_entries").select().decodeList<RecurringDto>() }
        ).awaitAll()
        @Suppress("UNCHECKED_CAST")
        _snapshot.value=FinanceSnapshot(accounts=(values[0] as List<AccountDto>).map{FinancialAccount(it.id,it.name,it.type,it.balance,it.active,it.bankShortName,it.bankName,it.bankLogo,it.accountNumber,it.purpose)}.filter{it.active},categories=(values[1] as List<CategoryDto>).map{Category(it.id,it.name,TransactionType.valueOf(it.type.uppercase()),it.icon,it.color,it.essential)},transactions=(values[2] as List<TransactionDto>).map{Transaction(it.id,it.accountId,it.categoryId,TransactionType.valueOf(it.type.uppercase()),it.amount,Instant.parse(it.at),it.description,it.note,TransactionSource.valueOf(it.source.uppercase()),TransactionStatus.valueOf(it.status.uppercase()),it.transferGroupId,it.deletedAt?.let(Instant::parse))},budgets=(values[3] as List<BudgetDto>).map{Budget(it.id,it.categoryId,it.period,it.limit,LocalDate.parse(it.start),LocalDate.parse(it.end),it.alert,it.active)},debts=(values[4] as List<DebtDto>).filter{!it.archived}.map{Debt(it.id,it.name,it.lender,it.debtType,it.original,it.principal,it.rate,it.interestType,LocalDate.parse(it.start),it.maturity?.let(LocalDate::parse),it.frequency,it.expected,it.due?.let(LocalDate::parse),it.status,it.note,it.received,it.isNew,it.months,it.totalPeriods,it.paymentMode)},installments=(values[5] as List<InstallmentDto>).map{DebtInstallment(it.id,it.debtId,LocalDate.parse(it.due),it.principal,it.interest,it.fee,it.total,it.paid,it.paidAt?.let(Instant::parse),it.status)},debtPayments=(values[6] as List<AllocationDto>).map{DebtPaymentAllocation(it.id,it.debtId,it.installmentId,it.transactionId,it.total,it.principal,it.interest,it.fee,it.advance,it.reversed?.let(Instant::parse),Instant.parse(it.created))},incomeSources=(values[7] as List<IncomeSourceDto>).map{IncomeSource(it.id,it.name,it.type,it.amount,it.payDay,it.frequency,it.active,it.next?.let(LocalDate::parse))},incomePayments=(values[8] as List<IncomePaymentDto>).map{IncomePayment(it.id,it.sourceId,LocalDate.parse(it.date),it.expected,it.transactionId,it.actual)},recurringEntries=(values[9] as List<RecurringDto>).map{RecurringEntry(it.id,TransactionType.valueOf(it.type.uppercase()),it.amount,it.active,it.title,it.accountId,it.categoryId,it.frequency,LocalDate.parse(it.start),it.end?.let(LocalDate::parse))})
    } } }
    override suspend fun addManualTransaction(input:ManualTransactionInput)=mutate { client.postgrest.rpc("create_manual_transaction",buildJsonObject { put("p_account_id",input.accountId);put("p_category_id",input.categoryId);put("p_type",input.type.name.lowercase());put("p_amount",input.amount);put("p_transaction_at",input.at.toString());input.description?.let{put("p_description",it)};input.note?.let{put("p_note",it)};put("p_recurring",input.recurring) }) }
    override suspend fun updateTransaction(id:String,edits:TransactionEdits)=mutate { client.from("transactions").update({ set("category_id",edits.categoryId);set("note",edits.note);set("status",edits.status.name.lowercase()) }) { filter { eq("id",id) } } }
    override suspend fun deleteTransaction(id:String)=mutate { client.postgrest.rpc("delete_financial_transaction",buildJsonObject{put("p_transaction_id",id)}) }
    override suspend fun transfer(fromAccountId:String,toAccountId:String,amount:Long,at:Instant,description:String?)=mutate {
        client.postgrest.rpc("transfer_between_accounts",buildJsonObject {
            put("p_from_account_id",fromAccountId); put("p_to_account_id",toAccountId); put("p_amount",amount)
            put("p_transaction_at",at.toString()); description?.let { put("p_description",it) }
        })
    }
    override suspend fun addIncomeSource(input:IncomeSourceInput)=mutate { client.from("income_sources").insert(IncomeInsert(userId(),input.name,input.type,input.expectedAmount,input.payDay,input.frequency,input.nextExpectedDate?.toString())) }
    override suspend fun linkIncomePayment(paymentId:String,transactionId:String,actualAmount:Long)=mutate { client.from("income_payments").update({set("transaction_id",transactionId);set("actual_amount",actualAmount)}){filter{eq("id",paymentId)}} }
    override suspend fun saveFixedExpense(id:String?,input:FixedExpenseInput)=mutate {
        require(input.title.isNotBlank() && input.title.length<=120 && input.amount>0)
        require(input.endDate==null || !input.endDate.isBefore(input.startDate))
        val owner=userId()
        val data=buildJsonObject {
            put("user_id",owner);put("title",input.title.trim());put("account_id",input.accountId)
            put("category_id",input.categoryId);put("type","expense");put("amount",input.amount)
            put("frequency","monthly");put("start_date",input.startDate.toString())
            put("end_date",input.endDate?.toString()?.let{kotlinx.serialization.json.JsonPrimitive(it)}?:JsonNull)
            put("next_run_at",input.startDate.atStartOfDay(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().toString())
            put("is_active",input.active)
        }
        if(id==null) client.from("recurring_entries").insert(data)
        else client.from("recurring_entries").update(data){filter{eq("id",id);eq("type","expense");eq("frequency","monthly")}}
    }
    override suspend fun deleteFixedExpense(id:String)=mutate {
        userId()
        client.from("recurring_entries").delete{filter{eq("id",id);eq("type","expense");eq("frequency","monthly")}}
    }
    override suspend fun addBudget(input:BudgetInput)=mutate { client.from("budgets").insert(BudgetInsert(userId(),input.categoryId,input.period,input.limitAmount,input.startDate.toString(),input.endDate.toString(),input.alertPercent)) }
    override suspend fun addCategory(input:CategoryInput)=mutate { client.from("categories").insert(CategoryInsert(userId(),input.name.trim(),input.type.name.lowercase())) }
    override suspend fun addAccount(input:AccountInput)=mutate { client.from("financial_accounts").insert(AccountInsert(userId(),input.displayName.trim(),type="bank",bankName=input.bankFullName,bankShortName=input.bankShortName,bankLogo=input.bankLogo,accountNumber=input.accountNumber.trim(),purpose=input.purpose.trim(),openingBalance=input.openingBalance)) }
    override suspend fun deleteAccount(id:String)=mutate { client.postgrest.rpc("delete_financial_account",buildJsonObject{put("p_account_id",id)}) }
    override suspend fun addDebt(input:DebtInput):Result<String> = runCatching { val result=client.postgrest.rpc("create_debt_v4",buildJsonObject{put("p_name",input.name);put("p_lender_name",input.lenderName);put("p_principal",input.originalPrincipal);put("p_is_new_loan",input.isNewLoan);put("p_has_interest",input.interestType!="none");put("p_payment_mode",input.paymentMode);put("p_total_periods",input.totalPeriods?:input.remainingMonths?:1);put("p_remaining_periods",input.remainingMonths?:1);put("p_expected_payment_amount",input.expectedPaymentAmount);put("p_first_due_date",input.nextDueDate.toString());input.maturityDate?.let{put("p_principal_due_date",it.toString())}?:put("p_principal_due_date",JsonNull);put("p_payment_frequency",input.paymentFrequency);input.receiveAccountId?.let{put("p_receive_account_id",it)}?:put("p_receive_account_id",JsonNull);input.note?.let{put("p_note",it)}?:put("p_note",JsonNull)}).decodeAs<String>();refresh().getOrThrow();result }
    override suspend fun deleteDebt(id:String)=mutate { client.postgrest.rpc("delete_debt_permanently",buildJsonObject{put("p_debt_id",id)}) }
    override suspend fun recordDebtPayment(installmentId:String,accountId:String,amount:Long,allowAdvance:Boolean)=mutate{client.postgrest.rpc("record_debt_payment",buildJsonObject{put("p_installment_id",installmentId);put("p_account_id",accountId);put("p_amount",amount);put("p_paid_at",Instant.now().toString());put("p_allow_advance",allowAdvance)})}
    override suspend fun reverseDebtPayment(transactionId:String)=mutate{client.postgrest.rpc("reverse_debt_payment",buildJsonObject{put("p_transaction_id",transactionId)})}
    override suspend fun confirmDebtSettlement(debtId:String,accountId:String,settlementAmount:Long,penaltyFee:Long)=mutate{client.postgrest.rpc("settle_debt",buildJsonObject{put("p_debt_id",debtId);put("p_account_id",accountId);put("p_settlement_amount",settlementAmount);put("p_penalty_fee",penaltyFee);put("p_paid_at",Instant.now().toString())})}
    override suspend fun updateDebtInstallment(id:String,dueDate:LocalDate,principal:Long,interest:Long,fee:Long)=mutate{client.from("debt_installments").update({set("due_date",dueDate.toString());set("principal_amount",principal);set("interest_amount",interest);set("fee_amount",fee)}){filter{eq("id",id)}}}
    override suspend fun claimDebtReminders():Result<List<DebtReminder>> = runCatching{client.postgrest.rpc("claim_debt_reminders").decodeList<ReminderDto>().map{DebtReminder(it.id,it.name,LocalDate.parse(it.due),it.amount,it.type)}}
    override suspend fun reconcileSePay():Result<Unit> = mutate { client.functions.invoke("sepay-sync") }
    override suspend fun latestAppRelease():Result<AppRelease?> = runCatching {
        client.from("app_releases").select { filter { eq("is_published",true) }; order("version_code",io.github.jan.supabase.postgrest.query.Order.DESCENDING); limit(1) }
            .decodeList<AppReleaseDto>().firstOrNull()?.let{AppRelease(it.versionCode,it.versionName,it.apkUrl,it.releaseNotes,it.mandatory)}
    }
    private suspend fun mutate(block:suspend()->Unit):Result<Unit> = runCatching { block(); refresh().getOrThrow() }
}
