package vn.personalfinance.domain

import vn.personalfinance.domain.model.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class MonthlyPlan(val month:YearMonth,val income:Long,val fixedExpenses:Long,val debtDue:Long,val overdue:Long,val openingBalance:Long=0) {
    val netCashFlow:Long get()=income-fixedExpenses-debtDue-overdue
    val remaining:Long get()=openingBalance+netCashFlow
}
data class CategoryExpense(val categoryId:String?,val amount:Long,val count:Int)

object ReportCalculator {
    // Seed once from live assets; carry both surplus and deficit into the next month.
    fun forecast(snapshot:FinanceSnapshot,today:LocalDate,months:Int=12):List<MonthlyPlan> {
        val first=YearMonth.from(today)
        val debtIds=snapshot.debts.map{it.id}.toSet()
        val installments=snapshot.installments.filter{it.debtId in debtIds && it.status!="cancelled"}
        val overdue=installments.filter{it.dueDate<first.atDay(1)}.sumOf{(it.totalDue-it.paidAmount).coerceAtLeast(0)}
        var balance=FinanceCalculator.totalAssets(snapshot.accounts)
        return (0 until months).map{offset->
            val month=first.plusMonths(offset.toLong())
            val income=snapshot.incomeSources.filter{it.active}.sumOf{source->
                val payments=snapshot.incomePayments.filter{it.incomeSourceId==source.id && YearMonth.from(it.expectedDate)==month}
                if(source.frequency=="weekly") {
                    val scheduled=source.nextExpectedDate?.let{anchor->days(month).filter{it>=anchor && ChronoUnit.DAYS.between(anchor,it)%7L==0L}}?:emptyList()
                    payments.filter{it.transactionId==null && it.actualAmount==null}.sumOf{it.expectedAmount} + scheduled.count{date->payments.none{it.expectedDate==date}}*source.expectedAmount
                }
                else if(payments.isNotEmpty()) payments.filter{it.transactionId==null && it.actualAmount==null}.sumOf{it.expectedAmount}
                else when(source.frequency) {
                    "monthly" -> if(source.nextExpectedDate==null || YearMonth.from(source.nextExpectedDate)<=month) source.expectedAmount else 0L
                    else -> if(source.nextExpectedDate?.let{YearMonth.from(it)==month}==true)source.expectedAmount else 0L
                }
            }
            val fixed=remainingFixedExpenses(snapshot,month,today)
            MonthlyPlan(month,income,fixed,installments.filter{YearMonth.from(it.dueDate)==month}.sumOf{(it.totalDue-it.paidAmount).coerceAtLeast(0)},if(offset==0)overdue else 0L,balance)
                .also{balance=it.remaining}
        }
    }
    private fun remainingFixedExpenses(snapshot:FinanceSnapshot,month:YearMonth,today:LocalDate):Long {
        val zone=ZoneId.of("Asia/Ho_Chi_Minh")
        // Reconcile by account, category and exact title, sharing one payment pool
        // so the same expense is never deducted twice for duplicate recurring rules.
        val paid=snapshot.transactions.filter{
            it.deletedAt==null && it.status==TransactionStatus.CONFIRMED && it.type==TransactionType.EXPENSE &&
                it.source!=TransactionSource.DEBT_PAYMENT && it.source!=TransactionSource.ADJUSTMENT &&
                it.transactionAt.atZone(zone).toLocalDate()<=today && YearMonth.from(it.transactionAt.atZone(zone))==month
        }.groupBy{Triple(it.accountId,it.categoryId,it.description?.trim().orEmpty())}
            .mapValues{(_,items)->items.sumOf{it.amount}}.toMutableMap()
        return snapshot.recurringEntries.filter{it.active && it.type==TransactionType.EXPENSE}.sumOf{entry->
            val planned=days(month).count{date->occurs(entry,date)}*entry.amount
            val key=Triple(entry.accountId,entry.categoryId,entry.title.trim())
            val applied=if(entry.title.isBlank())0L else minOf(planned,paid[key]?:0L)
            paid[key]=(paid[key]?:0L)-applied
            planned-applied
        }
    }
    private fun days(month:YearMonth)=(1..month.lengthOfMonth()).map{month.atDay(it)}
    private fun occurs(entry:RecurringEntry,date:LocalDate):Boolean {
        if(date<entry.startDate || entry.endDate?.let{date>it}==true)return false
        return when(entry.frequency) {
            "daily" -> true
            "weekly" -> ChronoUnit.DAYS.between(entry.startDate,date)%7L==0L
            "monthly" -> date.dayOfMonth==minOf(entry.startDate.dayOfMonth,date.lengthOfMonth())
            "yearly" -> date.month==entry.startDate.month && date.dayOfMonth==minOf(entry.startDate.dayOfMonth,date.lengthOfMonth())
            else -> false
        }
    }
    fun expenses(snapshot:FinanceSnapshot,start:LocalDate,end:LocalDate,zone:ZoneId,accountId:String?=null):List<CategoryExpense> =
        snapshot.transactions.filter{it.deletedAt==null && it.status==TransactionStatus.CONFIRMED && it.type==TransactionType.EXPENSE && (accountId==null || it.accountId==accountId) && it.transactionAt.atZone(zone).toLocalDate() in start..end}
            .groupBy{it.categoryId}.map{(id,items)->CategoryExpense(id,items.sumOf{it.amount},items.size)}.sortedByDescending{it.amount}
}
