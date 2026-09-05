package vn.personalfinance.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.personalfinance.domain.model.*
import java.time.LocalDate
import java.time.ZoneId

class ReportCalculatorTest {
    private val today=LocalDate.of(2026,9,5)
    private val income=IncomeSource("salary","Salary","salary",10_000_000,5,"monthly",true,today)
    private val fixed=RecurringEntry("rent",TransactionType.EXPENSE,3_000_000,true,startDate=today)
    private val debt=Debt("loan","Loan",null,"personal",20_000_000,20_000_000,null,"none",today,null,"monthly",8_000_000,today,"active",null)
    private fun installment(id:String,date:LocalDate,total:Long,paid:Long=0)=DebtInstallment(id,"loan",date,total,0,0,total,paid,null,if(paid==total)"paid" else "pending")

    @Test fun `monthly plan shows deficits and keeps full month paid obligations`() {
        val snapshot=FinanceSnapshot(incomeSources=listOf(income),recurringEntries=listOf(fixed),debts=listOf(debt),installments=listOf(installment("sep",today,8_000_000,8_000_000),installment("oct",today.plusMonths(1),2_000_000)))
        val plans=ReportCalculator.forecast(snapshot,today)
        assertEquals(12,plans.size)
        assertEquals(-1_000_000L,plans[0].remaining)
        assertEquals(5_000_000L,plans[1].remaining)
        assertEquals(7_000_000L,plans[2].remaining)
    }
    @Test fun `monthly recurrence clamps to month end and respects end date and pause`() {
        val jan=LocalDate.of(2028,1,31)
        val snapshot=FinanceSnapshot(recurringEntries=listOf(fixed.copy(startDate=jan,endDate=LocalDate.of(2028,2,29)),fixed.copy(id="paused",active=false)))
        assertEquals(listOf(3_000_000L,3_000_000L,0L),ReportCalculator.forecast(snapshot,jan,3).map{it.fixedExpenses})
    }
    @Test fun `overdue outstanding is carried once and removed debt is ignored`() {
        val snapshot=FinanceSnapshot(debts=listOf(debt),installments=listOf(installment("old",today.minusMonths(1),900,400),installment("removed",today,800).copy(debtId="removed")))
        val plans=ReportCalculator.forecast(snapshot,today,2)
        assertEquals(500L,plans[0].overdue)
        assertEquals(-500L,plans[0].remaining)
        assertEquals(0L,plans[1].remaining)
    }
    @Test fun `weekly and irregular income use actual occurrence dates`() {
        val snapshot=FinanceSnapshot(incomeSources=listOf(income.copy(expectedAmount=100,frequency="weekly",nextExpectedDate=LocalDate.of(2026,9,1)),income.copy(id="once",expectedAmount=50,frequency="irregular"),income.copy(id="inactive",active=false)))
        val plans=ReportCalculator.forecast(snapshot,today,2)
        assertEquals(550L,plans[0].income)
        assertEquals(400L,plans[1].income)
    }
    @Test fun `linked income replaces estimate without counting twice`() {
        val snapshot=FinanceSnapshot(incomeSources=listOf(income),incomePayments=listOf(IncomePayment("p","salary",today,10_000_000,"tx",9_000_000)))
        assertEquals(9_000_000L,ReportCalculator.forecast(snapshot,today,1).single().income)
    }
    @Test fun `one weekly receipt does not remove the remaining expected weeks`() {
        val date=LocalDate.of(2026,9,1)
        val snapshot=FinanceSnapshot(incomeSources=listOf(income.copy(frequency="weekly",expectedAmount=100,nextExpectedDate=date)),incomePayments=listOf(IncomePayment("p","salary",date,100,"tx",80)))
        assertEquals(480L,ReportCalculator.forecast(snapshot,today,1).single().income)
    }
    @Test fun `category totals include all categories and honor account date status and local timezone`() {
        val zone=ZoneId.of("Asia/Ho_Chi_Minh")
        val tx=Transaction("1","a","food",TransactionType.EXPENSE,100,today.atStartOfDay(zone).toInstant(),null,null,TransactionSource.MANUAL,TransactionStatus.CONFIRMED)
        val snapshot=FinanceSnapshot(transactions=listOf(tx,tx.copy(id="2",categoryId=null,amount=50),tx.copy(id="3",accountId="b"),tx.copy(id="4",status=TransactionStatus.PENDING),tx.copy(id="5",deletedAt=tx.transactionAt),tx.copy(id="6",type=TransactionType.TRANSFER),tx.copy(id="7",transactionAt=tx.transactionAt.minusSeconds(1))))
        val groups=ReportCalculator.expenses(snapshot,today,today,zone,"a")
        assertEquals(2,groups.size)
        assertEquals(150L,groups.sumOf{it.amount})
        assertEquals(null,groups.last().categoryId)
    }
}
