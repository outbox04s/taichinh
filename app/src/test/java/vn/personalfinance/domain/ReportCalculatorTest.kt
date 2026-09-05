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

    @Test fun `paid obligations are not charged again and monthly surplus accumulates`() {
        val snapshot=FinanceSnapshot(incomeSources=listOf(income),recurringEntries=listOf(fixed),debts=listOf(debt),installments=listOf(installment("sep",today,8_000_000,8_000_000),installment("oct",today.plusMonths(1),2_000_000)))
        val plans=ReportCalculator.forecast(snapshot,today)
        assertEquals(12,plans.size)
        assertEquals(7_000_000L,plans[0].remaining)
        assertEquals(12_000_000L,plans[1].remaining)
        assertEquals(19_000_000L,plans[2].remaining)
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
        assertEquals(-500L,plans[1].remaining)
    }
    @Test fun `weekly and irregular income use actual occurrence dates`() {
        val snapshot=FinanceSnapshot(incomeSources=listOf(income.copy(expectedAmount=100,frequency="weekly",nextExpectedDate=LocalDate.of(2026,9,1)),income.copy(id="once",expectedAmount=50,frequency="irregular"),income.copy(id="inactive",active=false)))
        val plans=ReportCalculator.forecast(snapshot,today,2)
        assertEquals(550L,plans[0].income)
        assertEquals(400L,plans[1].income)
    }
    @Test fun `linked income replaces estimate without counting twice`() {
        val snapshot=FinanceSnapshot(incomeSources=listOf(income),incomePayments=listOf(IncomePayment("p","salary",today,10_000_000,"tx",9_000_000)))
        assertEquals(0L,ReportCalculator.forecast(snapshot,today,1).single().income)
    }
    @Test fun `one weekly receipt does not remove the remaining expected weeks`() {
        val date=LocalDate.of(2026,9,1)
        val snapshot=FinanceSnapshot(incomeSources=listOf(income.copy(frequency="weekly",expectedAmount=100,nextExpectedDate=date)),incomePayments=listOf(IncomePayment("p","salary",date,100,"tx",80)))
        assertEquals(400L,ReportCalculator.forecast(snapshot,today,1).single().income)
    }
    @Test fun `live assets seed only first month and account changes flow through every month`() {
        val snapshot=FinanceSnapshot(accounts=listOf(FinancialAccount("a","Cash","cash",5_000_000),FinancialAccount("old","Closed","cash",99_000_000,false)),incomeSources=listOf(income),recurringEntries=listOf(fixed))
        val plans=ReportCalculator.forecast(snapshot,today,3)
        assertEquals(listOf(5_000_000L,12_000_000L,19_000_000L),plans.map{it.openingBalance})
        assertEquals(listOf(12_000_000L,19_000_000L,26_000_000L),plans.map{it.remaining})
        val updated=ReportCalculator.forecast(snapshot.copy(accounts=listOf(snapshot.accounts.first().copy(currentBalance=4_000_000))),today,3)
        assertEquals(listOf(1_000_000L,1_000_000L,1_000_000L),plans.zip(updated).map{(a,b)->a.remaining-b.remaining})
    }
    @Test fun `partial debt payment changes assets and obligation equally`() {
        val snapshot=FinanceSnapshot(accounts=listOf(FinancialAccount("a","Cash","cash",5_000_000)),debts=listOf(debt),installments=listOf(installment("sep",today,3_000_000)))
        val before=ReportCalculator.forecast(snapshot,today,2)
        val after=ReportCalculator.forecast(snapshot.copy(accounts=listOf(snapshot.accounts.single().copy(currentBalance=4_000_000)),installments=listOf(snapshot.installments.single().copy(paidAmount=1_000_000))),today,2)
        assertEquals(before.map{it.remaining},after.map{it.remaining})
    }
    @Test fun `received salary and paid fixed bill already in assets are not counted again`() {
        val zone=ZoneId.of("Asia/Ho_Chi_Minh")
        val expense=fixed.copy(title="Rent",accountId="a",categoryId="rent")
        val snapshot=FinanceSnapshot(accounts=listOf(FinancialAccount("a","Cash","cash",2_000_000)),incomeSources=listOf(income),recurringEntries=listOf(expense))
        val before=ReportCalculator.forecast(snapshot,today,2)
        val paid=Transaction("rent-paid","a","rent",TransactionType.EXPENSE,3_000_000,today.atStartOfDay(zone).toInstant(),"Rent",null,TransactionSource.MANUAL,TransactionStatus.CONFIRMED)
        val after=ReportCalculator.forecast(snapshot.copy(accounts=listOf(snapshot.accounts.single().copy(currentBalance=9_000_000)),incomePayments=listOf(IncomePayment("salary-paid","salary",today,10_000_000,"salary-tx",10_000_000)),transactions=listOf(paid)),today,2)
        assertEquals(before.map{it.remaining},after.map{it.remaining})
        assertEquals(0L,after.first().fixedExpenses)
        assertEquals(3_000_000L,after.last().fixedExpenses)
    }
    @Test fun `one fixed bill payment is shared once and unrelated expenses do not offset it`() {
        val zone=ZoneId.of("Asia/Ho_Chi_Minh")
        val expense=fixed.copy(title="Rent",accountId="a",categoryId="rent")
        val paid=Transaction("p","a","rent",TransactionType.EXPENSE,4_000_000,today.atStartOfDay(zone).toInstant(),"Rent",null,TransactionSource.MANUAL,TransactionStatus.CONFIRMED)
        val snapshot=FinanceSnapshot(recurringEntries=listOf(expense,expense.copy(id="second")),transactions=listOf(paid,paid.copy(id="other",description="Other"),paid.copy(id="pending",status=TransactionStatus.PENDING),paid.copy(id="deleted",deletedAt=paid.transactionAt)))
        assertEquals(2_000_000L,ReportCalculator.forecast(snapshot,today,1).single().fixedExpenses)
    }
    @Test fun `new current month starts again from actual assets not prior projection`() {
        val snapshot=FinanceSnapshot(accounts=listOf(FinancialAccount("a","Cash","cash",1_000_000)),incomeSources=listOf(income))
        assertEquals(1_000_000L,ReportCalculator.forecast(snapshot,today.plusMonths(1),1).single().openingBalance)
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
