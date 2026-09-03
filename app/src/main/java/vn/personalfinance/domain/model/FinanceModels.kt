package vn.personalfinance.domain.model

import java.time.Instant
import java.time.LocalDate

enum class TransactionType { INCOME, EXPENSE, TRANSFER }
enum class TransactionSource { MANUAL, SEPAY, RECURRING, DEBT_PAYMENT, ADJUSTMENT }
enum class TransactionStatus { CONFIRMED, PENDING, EXCLUDED }

data class FinancialAccount(val id: String, val name: String, val type: String, val currentBalance: Long, val active: Boolean = true,val bankShortName:String?=null,val bankFullName:String?=null,val bankLogo:String?=null)
data class Category(val id: String, val name: String, val type: TransactionType, val icon: String? = null, val color: String? = null, val isEssential:Boolean=false)
data class Transaction(
    val id: String, val accountId: String, val categoryId: String?, val type: TransactionType,
    val amount: Long, val transactionAt: Instant, val description: String?, val note: String?,
    val source: TransactionSource, val status: TransactionStatus, val transferGroupId: String? = null,
    val deletedAt: Instant? = null,
)
data class Budget(val id: String, val categoryId: String?, val period: String, val limitAmount: Long, val startDate: LocalDate, val endDate: LocalDate, val alertPercent: Int, val active: Boolean)
data class Debt(val id:String,val name:String,val lenderName:String?,val debtType:String,val originalPrincipal:Long,val currentPrincipal:Long,val interestRate:Double?,val interestType:String,val startDate:LocalDate,val maturityDate:LocalDate?,val paymentFrequency:String,val expectedPaymentAmount:Long,val nextDueDate:LocalDate?,val status:String,val note:String?,val receivedAmount:Long=0,val isNewLoan:Boolean=false,val remainingMonths:Int?=null)
data class DebtInstallment(val id:String,val debtId:String,val dueDate:LocalDate,val principalAmount:Long,val interestAmount:Long,val feeAmount:Long,val totalDue:Long,val paidAmount:Long,val paidAt:Instant?,val status:String)
data class DebtPaymentAllocation(val id:String,val debtId:String,val installmentId:String,val transactionId:String,val totalPaid:Long,val principalPaid:Long,val interestPaid:Long,val feePaid:Long,val advancePrincipal:Long,val reversedAt:Instant?,val createdAt:Instant)
data class DebtReminder(val installmentId:String,val debtName:String,val dueDate:LocalDate,val remainingAmount:Long,val type:String)
data class IncomeSource(val id: String, val name: String, val type: String, val expectedAmount: Long, val payDay: Int?, val frequency: String, val active: Boolean, val nextExpectedDate: LocalDate?)
data class IncomePayment(val id: String, val incomeSourceId: String, val expectedDate: LocalDate, val expectedAmount: Long, val transactionId: String?, val actualAmount: Long?)
data class RecurringEntry(val id:String,val type:TransactionType,val amount:Long,val active:Boolean)

data class FinanceSnapshot(
    val accounts: List<FinancialAccount> = emptyList(), val categories: List<Category> = emptyList(),
    val transactions: List<Transaction> = emptyList(), val budgets: List<Budget> = emptyList(),
    val debts: List<Debt> = emptyList(), val installments: List<DebtInstallment> = emptyList(), val debtPayments:List<DebtPaymentAllocation> = emptyList(),
    val incomeSources: List<IncomeSource> = emptyList(), val incomePayments: List<IncomePayment> = emptyList(), val recurringEntries:List<RecurringEntry> = emptyList(),
)
