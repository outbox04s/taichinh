package vn.personalfinance.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class ExistingLoanSchedule(val remaining:Int,val nextDue:LocalDate)

fun existingLoanSchedule(firstDue:LocalDate,total:Int,today:LocalDate,currentPaid:Boolean):ExistingLoanSchedule {
    require(total>0)
    val elapsed=ChronoUnit.MONTHS.between(YearMonth.from(firstDue),YearMonth.from(today))
    // The checkbox refers to the calendar month, even before its due day.
    val paid=if(elapsed<0)0 else (elapsed+if(currentPaid)1 else 0).coerceAtMost(total.toLong()).toInt()
    return ExistingLoanSchedule(total-paid,firstDue.plusMonths(paid.toLong()))
}
