package vn.personalfinance.presentation

import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale

val VietnamZone:ZoneId=ZoneId.of("Asia/Ho_Chi_Minh")
fun Long.toVnd():String=NumberFormat.getCurrencyInstance(Locale.forLanguageTag("vi-VN")).format(this)
fun FinanceUiState.dateRange(today:LocalDate=LocalDate.now(VietnamZone)):Pair<LocalDate,LocalDate> = when(period){
    PeriodFilter.WEEK -> today.minusDays((today.dayOfWeek.value-1).toLong()) to today.plusDays((7-today.dayOfWeek.value).toLong())
    PeriodFilter.MONTH -> today.withDayOfMonth(1) to today.with(TemporalAdjusters.lastDayOfMonth())
    PeriodFilter.QUARTER -> {val month=((today.monthValue-1)/3)*3+1;today.withMonth(month).withDayOfMonth(1) to today.withMonth(month+2).with(TemporalAdjusters.lastDayOfMonth())}
    PeriodFilter.YEAR -> today.withDayOfYear(1) to today.with(TemporalAdjusters.lastDayOfYear())
    PeriodFilter.CUSTOM -> (customStart?:today) to (customEnd?:today)
}
