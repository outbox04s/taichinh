package vn.personalfinance.domain

import vn.personalfinance.domain.model.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class MonthlyPlan(val month:YearMonth,val income:Long,val fixedExpenses:Long,val debtDue:Long,val overdue:Long) {
    val remaining:Long get()=income-fixedExpenses-debtDue-overdue
}
data class CategoryExpense(val categoryId:String?,val amount:Long,val count:Int)

object ReportCalculator {
    // A full-month spending plan, not a projection of the current account balance.
    fun forecast(snapshot:FinanceSnapshot,today:LocalDate,months:Int=12):List<MonthlyPlan> {
        val first=YearMonth.from(today)
        val debtIds=snapshot.debts.map{it.id}.toSet()
        val installments=snapshot.installments.filter{it.debtId in debtIds && it.status!="cancelled"}
        val overdue=installments.filter{it.dueDate<first.atDay(1)}.sumOf{(it.totalDue-it.paidAmount).coerceAtLeast(0)}
        return (0 until months).map{offset->
            val month=first.plusMonths(offset.toLong())
            val income=snapshot.incomeSources.filter{it.active}.sumOf{source->
                val payments=snapshot.incomePayments.filter{it.incomeSourceId==source.id && YearMonth.from(it.expectedDate)==month}
                if(source.frequency=="weekly") {
                    val scheduled=source.nextExpectedDate?.let{anchor->days(month).filter{it>=anchor && ChronoUnit.DAYS.between(anchor,it)%7L==0L}}?:emptyList()
                    payments.sumOf{it.actualAmount?:it.expectedAmount} + scheduled.count{date->payments.none{it.expectedDate==date}}*source.expectedAmount
                }
                else if(payments.isNotEmpty()) payments.sumOf{it.actualAmount?:it.expectedAmount}
                else when(source.frequency) {
                    "monthly" -> if(source.nextExpectedDate==null || YearMonth.from(source.nextExpectedDate)<=month) source.expectedAmount else 0L
                    else -> if(source.nextExpectedDate?.let{YearMonth.from(it)==month}==true)source.expectedAmount else 0L
                }
            }
            val fixed=snapshot.recurringEntries.filter{it.active && it.type==TransactionType.EXPENSE}.sumOf{entry->
                days(month).count{date->occurs(entry,date)}*entry.amount
            }
            MonthlyPlan(month,income,fixed,installments.filter{YearMonth.from(it.dueDate)==month}.sumOf{it.totalDue},if(offset==0)overdue else 0L)
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
