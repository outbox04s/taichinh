package vn.personalfinance.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    @Test fun `money preserves exact bigint-compatible amount`() {
        assertEquals(9_223_372_036_854_775_000L, Money(9_223_372_036_854_775_000L).amount)
    }
}
