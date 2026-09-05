package vn.personalfinance.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ExistingLoanScheduleTest {
    private val first=LocalDate.of(2026,1,20)

    @Test fun `September paid advances to October before on and after due day`() {
        listOf(1,6,20,30).forEach{day->
            assertEquals(ExistingLoanSchedule(3,LocalDate.of(2026,10,20)),existingLoanSchedule(first,12,LocalDate.of(2026,9,day),true))
        }
    }
    @Test fun `September unpaid remains September even before due day`() {
        listOf(1,6,20,30).forEach{day->
            assertEquals(ExistingLoanSchedule(4,LocalDate.of(2026,9,20)),existingLoanSchedule(first,12,LocalDate.of(2026,9,day),false))
        }
    }
    @Test fun `first installment in current month can be paid early`() {
        assertEquals(ExistingLoanSchedule(11,LocalDate.of(2026,2,20)),existingLoanSchedule(first,12,LocalDate.of(2026,1,1),true))
    }
    @Test fun `future loan does not skip first installment when checked`() {
        assertEquals(ExistingLoanSchedule(12,first),existingLoanSchedule(first,12,LocalDate.of(2025,12,31),true))
    }
    @Test fun `month end preserves original day across short months and year boundary`() {
        val end=LocalDate.of(2026,1,31)
        assertEquals(LocalDate.of(2026,2,28),existingLoanSchedule(end,24,LocalDate.of(2026,1,1),true).nextDue)
        assertEquals(LocalDate.of(2026,3,31),existingLoanSchedule(end,24,LocalDate.of(2026,2,1),true).nextDue)
        assertEquals(LocalDate.of(2027,1,31),existingLoanSchedule(end,24,LocalDate.of(2026,12,1),true).nextDue)
    }
    @Test fun `completed schedule never returns negative remaining count`() {
        assertEquals(0,existingLoanSchedule(first,9,LocalDate.of(2026,9,6),true).remaining)
        assertEquals(1,existingLoanSchedule(first,9,LocalDate.of(2026,9,6),false).remaining)
        assertEquals(0,existingLoanSchedule(first,9,LocalDate.of(2027,1,6),false).remaining)
    }
}
