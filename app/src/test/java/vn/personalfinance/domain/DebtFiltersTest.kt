package vn.personalfinance.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import vn.personalfinance.domain.model.*

class DebtFiltersTest {
    private val today=LocalDate.of(2026,9,6)
    private val debt=Debt("d","Loan",null,"loan",100,100,null,"none",today,null,"monthly",10,LocalDate.of(2026,8,10),"active",null)
    private fun due(date:LocalDate,paid:Long=0)=DebtInstallment("i","d",date,10,0,0,10,paid,null,"upcoming")
    @Test fun `near due includes entire current month including earlier unpaid dates`() {
        listOf(1,6,30).forEach{assertTrue(debtDueThisMonth(debt,listOf(due(today.withDayOfMonth(it))),today))}
    }
    @Test fun `near due excludes adjacent months settled and fully paid installments`() {
        assertFalse(debtDueThisMonth(debt,listOf(due(today.minusMonths(1))),today))
        assertFalse(debtDueThisMonth(debt,listOf(due(today.plusMonths(1))),today))
        assertFalse(debtDueThisMonth(debt,listOf(due(today,10)),today))
        assertFalse(debtDueThisMonth(debt.copy(status="paid"),listOf(due(today)),today))
    }
    @Test fun `month comes from realtime input and schedule overrides stale next due date`() {
        val next=LocalDate.of(2027,1,1)
        assertTrue(debtDueThisMonth(debt,listOf(due(next)),next))
        assertFalse(debtDueThisMonth(debt,listOf(due(next)),next.minusDays(1)))
        assertTrue(debtDueThisMonth(debt.copy(nextDueDate=today),emptyList(),today))
        assertFalse(debtDueThisMonth(debt.copy(nextDueDate=today),listOf(due(today,10)),today))
    }
}
