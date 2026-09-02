package vn.personalfinance.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DebtCalculatorTest{
 @Test fun `tra mot phan uu tien phi lai roi goc`(){assertEquals(DebtPaymentSplit(300,100,100,100,0),DebtCalculator.splitPayment(300,1000,100,100,5000,false))}
 @Test fun `tra du ky thanh toan`(){assertEquals(1000,DebtCalculator.splitPayment(1000,1000,0,100,5000,false).installmentApplied)}
 @Test fun `tra truoc chi giam them tien goc`(){val r=DebtCalculator.splitPayment(1300,1000,0,100,5000,true);assertEquals(300,r.advancePrincipal);assertEquals(900,r.principal)}
 @Test fun `khoan khong lai co interest bang khong`(){assertEquals(0,DebtCalculator.installment(10_000,1_000,0.0,"none","monthly").interest)}
 @Test fun `khoan co lai tinh lai theo ky`(){assertEquals(100,DebtCalculator.installment(10_000,1_000,12.0,"yearly","monthly").interest)}
 @Test fun `xoa giao dich khoi phuc dung phan goc`(){val split=DebtPaymentSplit(1000,50,150,800,0);assertEquals(5_000,DebtCalculator.restoredPrincipal(4_200,split))}
 @Test fun `ky chua tra qua ngay la overdue`(){assertEquals("overdue",DebtCalculator.installmentStatus(LocalDate.of(2026,8,1),0,1000,LocalDate.of(2026,9,1)))}
 @Test fun `chi de xuat tat toan khi goc bang khong`(){assertTrue(DebtCalculator.canSettle(0));assertFalse(DebtCalculator.canSettle(1))}
}
