package vn.personalfinance.domain

import java.time.LocalDate
import java.time.YearMonth
import vn.personalfinance.domain.model.Debt
import vn.personalfinance.domain.model.DebtInstallment

fun debtDueThisMonth(debt:Debt,installments:List<DebtInstallment>,today:LocalDate):Boolean {
    if(debt.status=="paid")return false
    val month=YearMonth.from(today)
    val schedule=installments.filter{it.debtId==debt.id}
    return if(schedule.isEmpty())debt.nextDueDate?.let{YearMonth.from(it)==month}==true
    else schedule.any{YearMonth.from(it.dueDate)==month && it.paidAmount<it.totalDue && it.status!="cancelled"}
}
