package vn.personalfinance.domain

import vn.personalfinance.domain.model.*
import java.time.LocalDate
import java.time.ZoneId

enum class RiskLevel { SAFE, ATTENTION, DANGEROUS, INSUFFICIENT_DATA }
data class RiskReason(val code:String,val description:String,val points:Int,val source:String)
data class RiskThresholds(val attentionDebtRatio:Double=.35,val dangerousDebtRatio:Double=.50,val safeEmergencyMonths:Double=3.0,val dangerousEmergencyMonths:Double=1.0,val budgetAttentionPercent:Int=80,val largePaymentDays:Int=7)
data class RiskResult(val level:RiskLevel,val score:Int?,val netCashFlow:Long,val projectedCash30Days:Long,val debtPaymentRatio:Double?,val emergencyCoverageMonths:Double?,val overdueDebtCount:Int,val overdueAmount:Long,val maxOverdueDays:Long,val reasons:List<RiskReason>)

object RiskEngine {
 fun calculate(snapshot:FinanceSnapshot,today:LocalDate,zone:ZoneId=ZoneId.of("Asia/Ho_Chi_Minh"),thresholds:RiskThresholds=RiskThresholds()):RiskResult{
  val confirmed=snapshot.transactions.filter{it.deletedAt==null&&it.status==TransactionStatus.CONFIRMED&&it.type!=TransactionType.TRANSFER}
  val monthStart=today.withDayOfMonth(1);val cash=FinanceCalculator.cashFlow(confirmed,monthStart,today,zone)
  val threeMonthStart=monthStart.minusMonths(2);val recent=confirmed.filter{val date=it.transactionAt.atZone(zone).toLocalDate();!date.isBefore(threeMonthStart)&&!date.isAfter(today)}
  val coveredMonths=recent.map{java.time.YearMonth.from(it.transactionAt.atZone(zone))}.distinct().size
  val incomeAverage=if(coveredMonths==3)recent.filter{it.type==TransactionType.INCOME}.sumOf{it.amount}.toDouble()/3 else null
  val essentialIds=snapshot.categories.filter{it.type==TransactionType.EXPENSE&&it.isEssential}.map{it.id}.toSet()
  val essentialAverage=if(coveredMonths==3)recent.filter{it.type==TransactionType.EXPENSE&&it.categoryId in essentialIds}.sumOf{it.amount}.toDouble()/3 else null
  val due30=snapshot.installments.filter{!it.dueDate.isBefore(today)&&!it.dueDate.isAfter(today.plusDays(30))}.sumOf{(it.totalDue-it.paidAmount).coerceAtLeast(0)}
  val expectedIncome=snapshot.incomeSources.filter{it.active&&it.nextExpectedDate?.let{d->!d.isBefore(today)&&!d.isAfter(today.plusDays(30))}==true}.sumOf{it.expectedAmount}
  val recurringExpense=snapshot.recurringEntries.filter{it.active&&it.type==TransactionType.EXPENSE}.sumOf{it.amount}
  val available=FinanceCalculator.totalAssets(snapshot.accounts);val projected=available+expectedIncome-recurringExpense-due30
  val monthDebt=snapshot.installments.filter{it.dueDate.year==today.year&&it.dueDate.month==today.month}.sumOf{it.totalDue}
  val debtRatio=incomeAverage?.takeIf{it>0}?.let{monthDebt/it};val emergency=essentialAverage?.takeIf{it>0}?.let{available/it}
  val overdue=snapshot.installments.filter{it.dueDate.isBefore(today)&&it.paidAmount<it.totalDue};val overdueAmount=overdue.sumOf{it.totalDue-it.paidAmount};val maxDays=overdue.maxOfOrNull{java.time.temporal.ChronoUnit.DAYS.between(it.dueDate,today)}?:0
  val reasons=mutableListOf<RiskReason>()
  if(projected<0)reasons+=RiskReason("NEGATIVE_PROJECTED_CASH","Số dư dự kiến 30 ngày đang âm",35,"accounts+income_sources-recurring_entries-debt_installments")
  if(overdue.isNotEmpty())reasons+=RiskReason("OVERDUE_DEBT","Có ${overdue.size} kỳ trả nợ quá hạn",30,"debt_installments")
  debtRatio?.let{when{it>thresholds.dangerousDebtRatio->reasons+=RiskReason("HIGH_DEBT_RATIO","Tỷ lệ trả nợ vượt ${percent(thresholds.dangerousDebtRatio)}",25,"debt_installments/transactions");it>thresholds.attentionDebtRatio->reasons+=RiskReason("ELEVATED_DEBT_RATIO","Tỷ lệ trả nợ cần chú ý",15,"debt_installments/transactions")}}
  emergency?.let{when{it<thresholds.dangerousEmergencyMonths->reasons+=RiskReason("LOW_EMERGENCY_COVERAGE","Dự phòng dưới ${thresholds.dangerousEmergencyMonths} tháng",20,"accounts/essential_expenses");it<thresholds.safeEmergencyMonths->reasons+=RiskReason("LIMITED_EMERGENCY_COVERAGE","Dự phòng chưa đạt ${thresholds.safeEmergencyMonths} tháng",10,"accounts/essential_expenses")}}
  val budgetHigh=snapshot.budgets.filter{it.active}.maxOfOrNull{FinanceCalculator.budgetUsage(it,confirmed,zone).percent}?:0;if(budgetHigh>=thresholds.budgetAttentionPercent)reasons+=RiskReason("BUDGET_THRESHOLD","Ngân sách đã dùng $budgetHigh%",10,"budgets/transactions")
  if(snapshot.installments.any{!it.dueDate.isBefore(today)&&!it.dueDate.isAfter(today.plusDays(thresholds.largePaymentDays.toLong()))&&it.paidAmount<it.totalDue})reasons+=RiskReason("PAYMENT_DUE_SOON","Có khoản phải trả trong ${thresholds.largePaymentDays} ngày",8,"debt_installments")
  val sufficient=incomeAverage!=null&&essentialAverage!=null;val level=when{projected<0||overdue.isNotEmpty()||(debtRatio?.let{it>thresholds.dangerousDebtRatio}==true)||(emergency?.let{it<thresholds.dangerousEmergencyMonths}==true)->RiskLevel.DANGEROUS;reasons.isNotEmpty()->RiskLevel.ATTENTION;sufficient->RiskLevel.SAFE;else->RiskLevel.INSUFFICIENT_DATA}
  return RiskResult(level,if(sufficient)reasons.sumOf{it.points}.coerceIn(0,100) else null,cash.net,projected,debtRatio,emergency,overdue.size,overdueAmount,maxDays,reasons.sortedByDescending{it.points})
 }
 private fun percent(value:Double)="${(value*100).toInt()}%"
}
