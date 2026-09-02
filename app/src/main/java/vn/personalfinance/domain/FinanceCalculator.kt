package vn.personalfinance.domain

import vn.personalfinance.domain.model.*
import java.time.LocalDate
import java.time.ZoneId

data class CashFlow(val income: Long, val expense: Long) { val net: Long get() = income - expense }
data class BudgetUsage(val spent: Long, val limit: Long) { val percent: Int get() = if (limit <= 0) 0 else ((spent * 100) / limit).coerceAtMost(999).toInt() }

object FinanceCalculator {
    private fun Transaction.reportable() = deletedAt == null && status == TransactionStatus.CONFIRMED && type != TransactionType.TRANSFER
    fun cashFlow(items: List<Transaction>, start: LocalDate, end: LocalDate, zone: ZoneId): CashFlow {
        val filtered = items.filter { it.reportable() && !it.transactionAt.atZone(zone).toLocalDate().isBefore(start) && !it.transactionAt.atZone(zone).toLocalDate().isAfter(end) }
        return CashFlow(filtered.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }, filtered.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount })
    }
    fun totalAssets(accounts: List<FinancialAccount>) = accounts.filter { it.active }.sumOf { it.currentBalance }
    fun budgetUsage(budget: Budget, items: List<Transaction>, zone: ZoneId) = BudgetUsage(
        spent = items.filter { it.reportable() && it.type == TransactionType.EXPENSE && (budget.categoryId == null || it.categoryId == budget.categoryId) }
            .filter { !it.transactionAt.atZone(zone).toLocalDate().isBefore(budget.startDate) && !it.transactionAt.atZone(zone).toLocalDate().isAfter(budget.endDate) }.sumOf { it.amount },
        limit = budget.limitAmount,
    )
}
