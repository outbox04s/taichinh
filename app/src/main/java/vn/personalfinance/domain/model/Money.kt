package vn.personalfinance.domain.model

@JvmInline
value class Money(val amount: Long) {
    init { require(amount != Long.MIN_VALUE) }
}
