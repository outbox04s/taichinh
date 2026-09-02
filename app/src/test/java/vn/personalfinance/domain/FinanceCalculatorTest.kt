package vn.personalfinance.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.personalfinance.domain.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class FinanceCalculatorTest {
    private val zone=ZoneId.of("Asia/Ho_Chi_Minh");private val start=LocalDate.of(2026,9,1);private val end=LocalDate.of(2026,9,30)
    private fun tx(id:String,type:TransactionType,amount:Long,status:TransactionStatus=TransactionStatus.CONFIRMED)=Transaction(id,"a","c",type,amount,Instant.parse("2026-09-10T03:00:00Z"),null,null,TransactionSource.MANUAL,status)
    @Test fun `tinh tong tien vao chinh xac`(){assertEquals(3_000_000,FinanceCalculator.cashFlow(listOf(tx("1",TransactionType.INCOME,3_000_000),tx("2",TransactionType.EXPENSE,500_000)),start,end,zone).income)}
    @Test fun `tinh tong tien ra chinh xac`(){assertEquals(500_000,FinanceCalculator.cashFlow(listOf(tx("1",TransactionType.INCOME,3_000_000),tx("2",TransactionType.EXPENSE,500_000)),start,end,zone).expense)}
    @Test fun `transfer va excluded khong tinh vao thu chi`(){val result=FinanceCalculator.cashFlow(listOf(tx("1",TransactionType.TRANSFER,2_000_000),tx("2",TransactionType.EXPENSE,700_000,TransactionStatus.EXCLUDED)),start,end,zone);assertEquals(CashFlow(0,0),result)}
    @Test fun `ngan sach chi tinh expense confirmed trong ky`(){val budget=Budget("b","c","monthly",1_000_000,start,end,80,true);val usage=FinanceCalculator.budgetUsage(budget,listOf(tx("1",TransactionType.EXPENSE,600_000),tx("2",TransactionType.TRANSFER,900_000),tx("3",TransactionType.EXPENSE,200_000,TransactionStatus.EXCLUDED)),zone);assertEquals(600_000,usage.spent);assertEquals(60,usage.percent)}
}
