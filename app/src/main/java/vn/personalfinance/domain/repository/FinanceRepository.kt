package vn.personalfinance.domain.repository

import kotlinx.coroutines.flow.StateFlow
import vn.personalfinance.domain.model.FinanceSnapshot
import vn.personalfinance.domain.model.DebtReminder
import vn.personalfinance.domain.model.TransactionStatus
import vn.personalfinance.domain.model.TransactionType
import java.time.Instant
import java.time.LocalDate

data class ManualTransactionInput(val accountId: String, val categoryId: String, val type: TransactionType, val amount: Long, val at: Instant, val description: String?, val note: String?, val recurring:Boolean=false)
data class TransactionEdits(val categoryId: String?, val note: String?, val status: TransactionStatus)
data class IncomeSourceInput(val name: String, val type: String, val expectedAmount: Long, val payDay: Int?, val frequency: String, val nextExpectedDate: LocalDate?)
data class BudgetInput(val categoryId: String?, val period: String, val limitAmount: Long, val startDate: LocalDate, val endDate: LocalDate, val alertPercent: Int)
data class DebtInput(val name:String,val lenderName:String,val debtType:String,val originalPrincipal:Long,val currentPrincipal:Long,val interestRate:Double?,val interestType:String,val startDate:LocalDate,val maturityDate:LocalDate?,val paymentFrequency:String,val expectedPaymentAmount:Long,val nextDueDate:LocalDate,val note:String?,val receivedAmount:Long=0,val isNewLoan:Boolean=false,val remainingMonths:Int?=null,val receiveAccountId:String?=null,val totalPeriods:Int?=null,val paymentMode:String="principal_interest")
data class AppRelease(val versionCode:Long,val versionName:String,val apkUrl:String,val releaseNotes:String,val mandatory:Boolean)
data class CategoryInput(val name:String,val type:TransactionType)
data class AccountInput(val displayName:String,val openingBalance:Long,val bankShortName:String,val bankFullName:String,val bankLogo:String?,val accountNumber:String,val purpose:String)

interface FinanceRepository {
    val snapshot: StateFlow<FinanceSnapshot>
    suspend fun refresh(): Result<Unit>
    suspend fun addManualTransaction(input: ManualTransactionInput): Result<Unit>
    suspend fun updateTransaction(id: String, edits: TransactionEdits): Result<Unit>
    suspend fun softDeleteManualTransaction(id: String): Result<Unit>
    suspend fun transfer(fromAccountId: String, toAccountId: String, amount: Long, at: Instant, description: String?): Result<Unit>
    suspend fun addIncomeSource(input: IncomeSourceInput): Result<Unit>
    suspend fun linkIncomePayment(paymentId:String,transactionId:String,actualAmount:Long):Result<Unit>
    suspend fun addBudget(input: BudgetInput): Result<Unit>
    suspend fun addCategory(input:CategoryInput):Result<Unit>
    suspend fun addAccount(input:AccountInput):Result<Unit>
    suspend fun deleteAccount(id:String):Result<Unit>
    suspend fun addDebt(input:DebtInput):Result<String>
    suspend fun deleteDebt(id:String):Result<Unit>
    suspend fun recordDebtPayment(installmentId:String,accountId:String,amount:Long,allowAdvance:Boolean):Result<Unit>
    suspend fun reverseDebtPayment(transactionId:String):Result<Unit>
    suspend fun confirmDebtSettlement(debtId:String,accountId:String,settlementAmount:Long,penaltyFee:Long):Result<Unit>
    suspend fun updateDebtInstallment(id:String,dueDate:LocalDate,principal:Long,interest:Long,fee:Long):Result<Unit>
    suspend fun claimDebtReminders():Result<List<DebtReminder>>
    suspend fun reconcileSePay():Result<Unit>
    suspend fun latestAppRelease():Result<AppRelease?>
}
