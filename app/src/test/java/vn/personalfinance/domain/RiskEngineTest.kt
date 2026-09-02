package vn.personalfinance.domain

import org.junit.Assert.*
import org.junit.Test
import vn.personalfinance.domain.model.*
import java.time.*

class RiskEngineTest {
 private val today=LocalDate.of(2026,9,2);private val zone=ZoneId.of("Asia/Ho_Chi_Minh")
 private fun tx(id:String,type:TransactionType,amount:Long,date:LocalDate,status:TransactionStatus=TransactionStatus.CONFIRMED)=Transaction(id,"a","essential",type,amount,date.atStartOfDay(zone).toInstant(),null,null,TransactionSource.MANUAL,status)
 private fun base(transactions:List<Transaction>)=FinanceSnapshot(accounts=listOf(FinancialAccount("a","Ngân hàng","bank",10_000_000)),categories=listOf(Category("essential","Thiết yếu",TransactionType.EXPENSE,isEssential=true)),transactions=transactions)
 @Test fun `khong thu nhap khong chia cho khong`(){val result=RiskEngine.calculate(base(listOf(tx("e",TransactionType.EXPENSE,1_000_000,today))),today);assertNull(result.debtPaymentRatio);assertNull(result.score)}
 @Test fun `khong chi phi thiet yeu thi du phong chua du du lieu`(){val income=(0..2).map{tx("i$it",TransactionType.INCOME,10_000_000,today.minusMonths(it.toLong()))};assertNull(RiskEngine.calculate(base(income),today).emergencyCoverageMonths)}
 @Test fun `transfer va excluded khong duoc tinh`(){val items=listOf(tx("i",TransactionType.INCOME,100,today),tx("x",TransactionType.EXPENSE,50,today,TransactionStatus.EXCLUDED),tx("t",TransactionType.TRANSFER,900,today));assertEquals(100L,RiskEngine.calculate(base(items),today).netCashFlow)}
 @Test fun `du kien am la nguy hiem`(){val snapshot=base(emptyList()).copy(accounts=listOf(FinancialAccount("a","Ví","cash",100)),recurringEntries=listOf(RecurringEntry("r",TransactionType.EXPENSE,200,true)));val result=RiskEngine.calculate(snapshot,today);assertEquals(RiskLevel.DANGEROUS,result.level);assertTrue(result.reasons.any{it.code=="NEGATIVE_PROJECTED_CASH"})}
 @Test fun `no qua han la nguy hiem`(){val installment=DebtInstallment("p","d",today.minusDays(2),100,0,0,100,0,null,"overdue");val result=RiskEngine.calculate(base(emptyList()).copy(installments=listOf(installment)),today);assertEquals(1,result.overdueDebtCount);assertEquals(2,result.maxOverdueDays);assertEquals(RiskLevel.DANGEROUS,result.level)}
 @Test fun `nhieu tai khoan duoc cong chinh xac`(){val snapshot=base(emptyList()).copy(accounts=listOf(FinancialAccount("a","A","bank",100),FinancialAccount("b","B","cash",250)));assertEquals(350L,RiskEngine.calculate(snapshot,today).projectedCash30Days)}
 @Test fun `ranh gioi debt ratio 35 phan tram chua canh bao`(){val items=(0..2).flatMap{m->listOf(tx("i$m",TransactionType.INCOME,10_000,today.minusMonths(m.toLong())),tx("e$m",TransactionType.EXPENSE,1_000,today.minusMonths(m.toLong())))};val installment=DebtInstallment("p","d",today,3_500,0,0,3_500,0,null,"upcoming");val result=RiskEngine.calculate(base(items).copy(installments=listOf(installment)),today);assertEquals(.35,result.debtPaymentRatio!!,.0001);assertFalse(result.reasons.any{it.code.contains("DEBT_RATIO")})}
 @Test fun `tren ranh gioi 50 phan tram la nguy hiem`(){val items=(0..2).flatMap{m->listOf(tx("i$m",TransactionType.INCOME,10_000,today.minusMonths(m.toLong())),tx("e$m",TransactionType.EXPENSE,1_000,today.minusMonths(m.toLong())))};val installment=DebtInstallment("p","d",today,5_001,0,0,5_001,0,null,"upcoming");assertEquals(RiskLevel.DANGEROUS,RiskEngine.calculate(base(items).copy(installments=listOf(installment)),today).level)}
}
