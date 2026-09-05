@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package vn.personalfinance.presentation.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.personalfinance.domain.FinanceCalculator
import vn.personalfinance.domain.RiskEngine
import vn.personalfinance.domain.RiskLevel
import vn.personalfinance.domain.model.*
import vn.personalfinance.domain.repository.IncomeSourceInput
import vn.personalfinance.presentation.*
import vn.personalfinance.presentation.components.glass.*
import vn.personalfinance.presentation.theme.LiquidGlassColors
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun OverviewScreen(
    state: FinanceUiState,
    onRetry: () -> Unit,
    onPeriod: (PeriodFilter) -> Unit,
    onAccount: (String?) -> Unit,
    onCustom: (LocalDate, LocalDate) -> Unit,
    onAddIncome: (IncomeSourceInput) -> Unit,
    onLinkIncome: (String, String, Long) -> Unit,
    onSettings: () -> Unit = {},
    onAddTransaction: () -> Unit = {},
    onAllTransactions: () -> Unit = {},
    onAllDebts: () -> Unit = {},
    onAllCategories: () -> Unit = {},
    onFixedExpenses: () -> Unit = {},
) {
    DashboardBackground {
        when {
            state.loading -> DashboardLoading()
            state.error != null -> DashboardError(state.error, onRetry)
            else -> DashboardContent(state, onPeriod, onAccount, onCustom, onAddIncome, onSettings, onAddTransaction, onAllTransactions, onAllDebts, onAllCategories, onFixedExpenses)
        }
    }
}

@Composable private fun DashboardBackground(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color.White, LiquidGlassColors.BackgroundSecondary, Color.White)))) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(LiquidGlassColors.BlueBright.copy(alpha = .12f), size.minDimension * .42f, Offset(size.width * .92f, size.height * .08f))
            drawCircle(LiquidGlassColors.Blue.copy(alpha = .08f), size.minDimension * .38f, Offset(size.width * .04f, size.height * .42f))
            drawCircle(LiquidGlassColors.BlueBright.copy(alpha = .07f), size.minDimension * .45f, Offset(size.width * .85f, size.height * .88f))
        }
        content()
    }
}

@Composable private fun DashboardContent(
    state: FinanceUiState,
    onPeriod: (PeriodFilter) -> Unit,
    onAccount: (String?) -> Unit,
    onCustom: (LocalDate, LocalDate) -> Unit,
    onAddIncome: (IncomeSourceInput) -> Unit,
    onSettings: () -> Unit,
    onAddTransaction: () -> Unit,
    onAllTransactions: () -> Unit,
    onAllDebts: () -> Unit,
    onAllCategories: () -> Unit = {},
    onFixedExpenses: () -> Unit = {},
) {
    val range = state.dateRange()
    val transactions = state.snapshot.transactions.filter { (state.accountId == null || it.accountId == state.accountId) && it.deletedAt == null }
    val flow = FinanceCalculator.cashFlow(transactions, range.first, range.second, VietnamZone)
    val assets = FinanceCalculator.totalAssets(state.snapshot.accounts.filter { state.accountId == null || it.id == state.accountId })
    val today = LocalDate.now(VietnamZone)
    val openDebts = state.snapshot.debts.filter { it.status != "paid" }
    val due30 = state.snapshot.installments.filter { it.status != "paid" && !it.dueDate.isBefore(today) && !it.dueDate.isAfter(today.plusDays(30)) }.sumOf { it.totalDue - it.paidAmount }
    val risk = RiskEngine.calculate(state.snapshot, today)
    var moneyVisible by rememberSaveable { mutableStateOf(true) }
    var customDates by remember { mutableStateOf(false) }
    var accountMenu by remember { mutableStateOf(false) }
    var incomeDialog by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 128.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { DashboardHeader(onSettings) }
        item {
            BalanceHeroCard(assets, moneyVisible, { moneyVisible = !moneyVisible }, state.snapshot.accounts.firstOrNull { it.id == state.accountId }?.name ?: "Tất cả tài khoản", { accountMenu = true })
            Box { DropdownMenu(accountMenu, { accountMenu = false }) { DropdownMenuItem({ Text("Tất cả tài khoản") }, { onAccount(null); accountMenu = false }); state.snapshot.accounts.forEach { account -> DropdownMenuItem({ Text(account.name) }, { onAccount(account.id); accountMenu = false }) } } }
        }
        item {
            GlassSegmentedControl(PeriodFilter.entries.filter { it != PeriodFilter.CUSTOM }.map { it to when (it) { PeriodFilter.WEEK -> "Tuần"; PeriodFilter.MONTH -> "Tháng"; PeriodFilter.QUARTER -> "Quý"; PeriodFilter.YEAR -> "Năm"; else -> "" } }, state.period.takeUnless { it == PeriodFilter.CUSTOM } ?: PeriodFilter.MONTH, {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onPeriod(it)
            }) { GlassIconButton(Icons.Rounded.CalendarMonth, "Chọn khoảng ngày", { customDates = true }) }
        }
        item { MetricGrid(flow.income, flow.expense, flow.net, due30, moneyVisible) }
        item { FinancialHealthCard(risk.level, risk.score, openDebts.sumOf { it.currentPrincipal }, state.snapshot.installments.filter { it.status != "paid" && !it.dueDate.isBefore(today) && !it.dueDate.isAfter(today.plusDays(7)) }.sumOf { it.totalDue - it.paidAmount }, risk.projectedCash30Days, onAllDebts) }
        item { CashFlowCard(transactions, range.first, range.second, flow.income, flow.expense, onAddTransaction) }
        item { ExpenseCard(transactions, state.snapshot.categories, range.first, range.second, onAllCategories) }
        item { RecentTransactionsCard(transactions, state.snapshot, onAllTransactions, onAddTransaction) }
        item { UpcomingDebtsCard(state.snapshot, today, onAllDebts) }
        item { OutlinedButton(onFixedExpenses, Modifier.fillMaxWidth()) { Text("Chi ph\u00ed c\u1ed1 \u0111\u1ecbnh h\u00e0ng th\u00e1ng") } }
        item { IncomeCard(state.snapshot, { incomeDialog = true }) }
    }
    if (customDates) CustomRangeDialog(state, { customDates = false }) { start, end -> onCustom(start, end); customDates = false }
    if (incomeDialog) AddIncomeDialog({ incomeDialog = false }) { onAddIncome(it); incomeDialog = false }
}

@Composable private fun DashboardHeader(onSettings: () -> Unit) {
    val hour = java.time.LocalTime.now(VietnamZone).hour
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(when (hour) { in 5..10 -> "CHÀO BUỔI SÁNG"; in 11..17 -> "CHÀO BUỔI CHIỀU"; else -> "CHÀO BUỔI TỐI" }, color = LiquidGlassColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium,maxLines=1); Text("TỔNG QUAN", color = LiquidGlassColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
        GlassIconButton(Icons.Rounded.NotificationsNone, "Thông báo", {})
        Spacer(Modifier.width(8.dp)); GlassIconButton(Icons.Rounded.Settings, "Mở cài đặt", onSettings)
    }
}

@Composable private fun BalanceHeroCard(value: Long, visible: Boolean, onVisibility: () -> Unit, account: String, onAccount: () -> Unit) {
    LiquidGlassSurface(Modifier.fillMaxWidth(), GlassLevel.Primary) {
        Canvas(Modifier.matchParentSize()) { drawCircle(Brush.radialGradient(listOf(LiquidGlassColors.BlueBright.copy(alpha = .20f), Color.Transparent)), size.minDimension * .75f, Offset(size.width, 0f)) }
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("TỔNG TÀI SẢN KHẢ DỤNG", Modifier.weight(1f), color = LiquidGlassColors.TextSecondary, fontWeight = FontWeight.SemiBold); IconButton(onVisibility) { Icon(if (visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, if (visible) "Ẩn số tiền" else "Hiện số tiền", tint = LiquidGlassColors.Blue) } }
            AnimatedContent(visible, label = "money") { shown -> Text(if (shown) value.toVnd() else "••••••••", color = LiquidGlassColors.TextPrimary, fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Row(verticalAlignment = Alignment.CenterVertically) { GlassChip(account, false, onAccount); if (value == 0L) Text("Thêm tài khoản đầu tiên", Modifier.padding(start = 12.dp), color = LiquidGlassColors.Mint, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable private fun MetricGrid(income: Long, expense: Long, net: Long, due: Long, visible: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("Tiền vào", income, Icons.Rounded.SouthWest, LiquidGlassColors.Mint, visible, Modifier.weight(1f)); MetricCard("Tiền ra", expense, Icons.Rounded.NorthEast, LiquidGlassColors.Coral, visible, Modifier.weight(1f)) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("Dòng tiền ròng", net, Icons.Rounded.ShowChart, LiquidGlassColors.Violet, visible, Modifier.weight(1f)); MetricCard("Phải trả 30 ngày", due, Icons.Rounded.Schedule, LiquidGlassColors.Amber, visible, Modifier.weight(1f)) }
    }
}

@Composable private fun MetricCard(label: String, value: Long, icon: ImageVector, color: Color, visible: Boolean, modifier: Modifier) {
    GlassCard(modifier.heightIn(min = 126.dp), GlassLevel.Tertiary) {
        Surface(shape = CircleShape, color = color.copy(alpha = .14f)) { Icon(icon, null, Modifier.padding(8.dp).size(20.dp), tint = color) }
        Spacer(Modifier.height(10.dp)); Text(label.uppercase(Locale.forLanguageTag("vi-VN")), color = LiquidGlassColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(if (visible) value.toVnd() else "••••••", color = LiquidGlassColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun FinancialHealthCard(level: RiskLevel, score: Int?, debt: Long, due7: Long, projected: Long, onDetails: () -> Unit) {
    val status = when (level) { RiskLevel.SAFE -> "An toàn"; RiskLevel.ATTENTION -> "Cần chú ý"; RiskLevel.DANGEROUS -> "Nguy hiểm"; RiskLevel.INSUFFICIENT_DATA -> "Chưa đủ dữ liệu" }
    val color = when (level) { RiskLevel.SAFE -> LiquidGlassColors.Mint; RiskLevel.DANGEROUS -> LiquidGlassColors.Coral; else -> LiquidGlassColors.Amber }
    GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("SỨC KHỎE TÀI CHÍNH", color = LiquidGlassColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); GlassStatusPill(status, color) }; Gauge(score ?: 0, color) }
        Spacer(Modifier.height(16.dp)); MoneyLine("Tổng dư nợ", debt); MoneyLine("Phải trả trong 7 ngày", due7); MoneyLine("Số dư dự kiến sau 30 ngày", projected)
        TextButton(onClick = onDetails, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) { Text("Xem phân tích", color = LiquidGlassColors.Mint); Icon(Icons.Rounded.ChevronRight, null, tint = LiquidGlassColors.Mint) }
    }
}

@Composable private fun Gauge(score: Int, color: Color) { Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) { Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Điểm sức khỏe $score trên 100" }) { drawArc(Color.White.copy(alpha = .1f), -90f, 360f, false, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round)); drawArc(color, -90f, score.coerceIn(0, 100) * 3.6f, false, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round)) }; Text(if (score == 0) "—" else "$score", color = LiquidGlassColors.TextPrimary, fontWeight = FontWeight.Bold) } }
@Composable private fun MoneyLine(label: String, value: Long) { Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = LiquidGlassColors.TextSecondary, fontSize = 14.sp); Text(value.toVnd(), color = LiquidGlassColors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1) } }

@Composable private fun CashFlowCard(transactions: List<Transaction>, start: LocalDate, end: LocalDate, income: Long, expense: Long, onAdd: () -> Unit) {
    val filtered = transactions.filter { it.status == TransactionStatus.CONFIRMED && it.type != TransactionType.TRANSFER && it.transactionAt.atZone(VietnamZone).toLocalDate() in start..end }
    GlassCard {
        SectionHeader("Dòng tiền"); Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) { Text("Thu ${income.toVnd()}", color = LiquidGlassColors.Mint, style = MaterialTheme.typography.labelMedium); Text("Chi ${expense.toVnd()}", color = LiquidGlassColors.Coral, style = MaterialTheme.typography.labelMedium) }
        if (filtered.isEmpty()) GlassEmptyState(Icons.Rounded.InsertChartOutlined, "Chưa có giao dịch trong kỳ", "Thêm giao dịch để bắt đầu theo dõi dòng tiền.", "Thêm giao dịch", onAdd)
        else FlowChart(filtered, start, end)
    }
}

@Composable private fun FlowChart(items: List<Transaction>, start: LocalDate, end: LocalDate) {
    val span = (end.toEpochDay() - start.toEpochDay()).coerceAtLeast(1).toInt(); val points = (0..minOf(span, 13)).map { start.plusDays((it.toLong() * span / minOf(span, 13).coerceAtLeast(1))) }
    val values = points.map { day -> items.filter { it.transactionAt.atZone(VietnamZone).toLocalDate() == day }.let { dayItems -> dayItems.filter { it.type == TransactionType.INCOME }.sumOf { it.amount } to dayItems.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount } } }
    val max = values.maxOf { maxOf(it.first, it.second) }.coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(220.dp).padding(top = 18.dp).semantics { contentDescription = "Biểu đồ dòng tiền trong kỳ" }) {
        repeat(4) { i -> val y = size.height * i / 3; drawLine(Color.White.copy(alpha = .06f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx()) }
        fun drawSeries(selector: (Pair<Long, Long>) -> Long, color: Color) {
            val step = size.width / (values.size - 1).coerceAtLeast(1)
            values.zipWithNext().forEachIndexed { index, pair ->
                val first = Offset(index * step, size.height - size.height * selector(pair.first) / max)
                val second = Offset((index + 1) * step, size.height - size.height * selector(pair.second) / max)
                drawLine(color, first, second, 3.dp.toPx(), StrokeCap.Round)
            }
        }
        drawSeries({ it.first }, LiquidGlassColors.Mint); drawSeries({ it.second }, LiquidGlassColors.Coral)
    }
}

@Composable private fun ExpenseCard(items: List<Transaction>, categories: List<Category>, start: LocalDate, end: LocalDate, onAll: () -> Unit) {
    val expenses = items.filter { it.type == TransactionType.EXPENSE && it.status == TransactionStatus.CONFIRMED && it.transactionAt.atZone(VietnamZone).toLocalDate() in start..end }
    val groups = expenses.groupBy { it.categoryId }.mapValues { it.value.sumOf(Transaction::amount) }.entries.sortedByDescending { it.value }; val total = groups.sumOf { it.value }
    GlassCard { SectionHeader("Cơ cấu chi tiêu", if (total > 0) "Xem tất cả" else null, onAll); if (total == 0L) GlassEmptyState(Icons.Rounded.DonutLarge, "Chưa có chi tiêu trong kỳ", "Cơ cấu danh mục sẽ xuất hiện tại đây.") else Row(verticalAlignment = Alignment.CenterVertically) { Donut(groups.take(5).map { it.value }, total); Column(Modifier.weight(1f).padding(start = 16.dp)) { groups.take(3).forEachIndexed { index, entry -> val colors = listOf(LiquidGlassColors.Mint, LiquidGlassColors.Violet, LiquidGlassColors.Coral); Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(colors[index])); Text(categories.firstOrNull { it.id == entry.key }?.name ?: "Chưa phân loại", Modifier.weight(1f).padding(start = 8.dp), color = LiquidGlassColors.TextSecondary, maxLines = 1); Text("${entry.value * 100 / total}%", color = LiquidGlassColors.TextPrimary) } } } } }
}
@Composable private fun Donut(values: List<Long>, total: Long) { Canvas(Modifier.size(104.dp).semantics { contentDescription = "Biểu đồ cơ cấu chi tiêu" }) { var start = -90f; val colors = listOf(LiquidGlassColors.Mint, LiquidGlassColors.Violet, LiquidGlassColors.Coral, LiquidGlassColors.Amber, LiquidGlassColors.Blue); values.forEachIndexed { i, value -> val sweep = value * 360f / total; drawArc(colors[i], start, sweep - 3f, false, style = Stroke(16.dp.toPx(), cap = StrokeCap.Round)); start += sweep } } }

@Composable private fun RecentTransactionsCard(items: List<Transaction>, snapshot: FinanceSnapshot, onAll: () -> Unit, onAdd: () -> Unit) {
    val recent = items.sortedByDescending { it.transactionAt }.take(5)
    GlassCard { SectionHeader("Giao dịch gần nhất", if (recent.isNotEmpty()) "Xem tất cả" else null, onAll); if (recent.isEmpty()) GlassEmptyState(Icons.Rounded.ReceiptLong, "Chưa có giao dịch", "Giao dịch mới nhất sẽ được hiển thị tại đây.", "Thêm giao dịch", onAdd) else recent.forEach { item -> DashboardTransactionRow(item, snapshot) } }
}
@Composable private fun DashboardTransactionRow(item: Transaction, snapshot: FinanceSnapshot) { Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) { val income = item.type == TransactionType.INCOME; Surface(shape = CircleShape, color = (if (income) LiquidGlassColors.Mint else LiquidGlassColors.Coral).copy(alpha = .12f)) { Icon(if (income) Icons.Rounded.SouthWest else Icons.Rounded.NorthEast, if (income) "Tiền vào" else "Tiền ra", Modifier.padding(9.dp).size(19.dp), tint = if (income) LiquidGlassColors.Mint else LiquidGlassColors.Coral) }; Column(Modifier.weight(1f).padding(horizontal = 10.dp)) { Text(item.description ?: snapshot.categories.firstOrNull { it.id == item.categoryId }?.name ?: "Giao dịch", color = LiquidGlassColors.TextPrimary, fontWeight = FontWeight.Medium, maxLines = 1); Text(item.transactionAt.atZone(VietnamZone).format(DateTimeFormatter.ofPattern("dd/MM • HH:mm")), color = LiquidGlassColors.TextSecondary, fontSize = 12.sp) }; Text((if (income) "+ " else "− ") + item.amount.toVnd(), color = LiquidGlassColors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1) } }

@Composable private fun UpcomingDebtsCard(snapshot: FinanceSnapshot, today: LocalDate, onAll: () -> Unit) {
    val upcoming = snapshot.installments.filter { it.status != "paid" && !it.dueDate.isBefore(today) }.sortedBy { it.dueDate }.take(3)
    GlassCard { SectionHeader("Khoản nợ sắp đến hạn", if (upcoming.isNotEmpty()) "Xem tất cả" else null, onAll); if (upcoming.isEmpty()) Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.CheckCircle, null, tint = LiquidGlassColors.Mint); Text("Không có khoản nợ sắp đến hạn", Modifier.padding(start = 10.dp), color = LiquidGlassColors.TextSecondary) } else upcoming.forEach { installment -> val days = installment.dueDate.toEpochDay() - today.toEpochDay(); Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(snapshot.debts.firstOrNull { it.id == installment.debtId }?.name ?: "Khoản nợ", color = LiquidGlassColors.TextPrimary, fontWeight = FontWeight.Medium); Text("${installment.dueDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))} • còn $days ngày", color = LiquidGlassColors.TextSecondary, fontSize = 12.sp) }; Column(horizontalAlignment = Alignment.End) { Text((installment.totalDue - installment.paidAmount).toVnd(), color = LiquidGlassColors.TextPrimary, fontWeight = FontWeight.SemiBold); GlassStatusPill(if (days <= 7) "Sắp đến hạn" else "Đang theo dõi", if (days <= 7) LiquidGlassColors.Amber else LiquidGlassColors.Blue) } } } }
}

@Composable private fun IncomeCard(snapshot: FinanceSnapshot, onAdd: () -> Unit) { GlassCard { SectionHeader("Nguồn thu nhập", if (snapshot.incomeSources.isNotEmpty()) "Thêm" else null, onAdd); val source = snapshot.incomeSources.firstOrNull { it.active }; if (source == null) GlassEmptyState(Icons.Rounded.Payments, "Chưa thiết lập nguồn thu", "Theo dõi ngày nhận và số tiền dự kiến.", "Thiết lập nguồn thu", onAdd) else { MoneyLine(source.name, source.expectedAmount); Text("Ngày nhận dự kiến: ${source.nextExpectedDate?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) ?: "Chưa đặt"}", color = LiquidGlassColors.TextSecondary, fontSize = 13.sp); Spacer(Modifier.height(8.dp)); GlassStatusPill(if (snapshot.incomePayments.any { it.incomeSourceId == source.id && it.transactionId != null }) "Đã nhận" else "Chưa nhận", if (snapshot.incomePayments.any { it.incomeSourceId == source.id && it.transactionId != null }) LiquidGlassColors.Mint else LiquidGlassColors.Amber) } } }

@Composable private fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title.uppercase(Locale.forLanguageTag("vi-VN")), Modifier.weight(1f), color = LiquidGlassColors.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold); action?.let { TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(it, color = LiquidGlassColors.Blue) } } } }

@Composable private fun CustomRangeDialog(state: FinanceUiState, onDismiss: () -> Unit, onApply: (LocalDate, LocalDate) -> Unit) { var start by remember { mutableStateOf(state.customStart?.toString() ?: LocalDate.now(VietnamZone).withDayOfMonth(1).toString()) }; var end by remember { mutableStateOf(state.customEnd?.toString() ?: LocalDate.now(VietnamZone).toString()) }; AlertDialog(onDismissRequest = onDismiss, title = { Text("Chọn khoảng thời gian") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { DatePickerField("Từ ngày", start) { start = it }; DatePickerField("Đến ngày", end) { end = it } } }, confirmButton = { Button({ runCatching { onApply(LocalDate.parse(start), LocalDate.parse(end)) } }) { Text("Áp dụng") } }, dismissButton = { TextButton(onDismiss) { Text("Hủy") } }) }
@Composable private fun AddIncomeDialog(onDismiss: () -> Unit, onSave: (IncomeSourceInput) -> Unit) { var name by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }; var day by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text("Thêm nguồn thu nhập") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(name, { name = it }, label = { Text("Tên nguồn") }); OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("Số tiền dự kiến") }); OutlinedTextField(day, { day = it.filter(Char::isDigit).take(2) }, label = { Text("Ngày nhận (1–31)") }) } }, confirmButton = { Button({ val value = amount.toLongOrNull(); val payDay = day.toIntOrNull(); if (name.isNotBlank() && value != null && value > 0 && payDay in 1..31) { val now = LocalDate.now(VietnamZone); onSave(IncomeSourceInput(name, "salary", value, payDay, "monthly", now.withDayOfMonth(minOf(payDay!!, now.lengthOfMonth())))) } }) { Text("Lưu") } }, dismissButton = { TextButton(onDismiss) { Text("Hủy") } }) }

@Composable private fun DashboardLoading() { LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { item { Spacer(Modifier.height(56.dp)); repeat(5) { LiquidGlassSurface(Modifier.fillMaxWidth().height(if (it == 0) 190.dp else 130.dp)) {}; Spacer(Modifier.height(16.dp)) } } } }
@Composable private fun DashboardError(message: String, onRetry: () -> Unit) { Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { GlassCard { GlassEmptyState(Icons.Rounded.CloudOff, "Không tải được dữ liệu", message, "Thử lại", onRetry) } } }

private fun sampleState(empty: Boolean = false) = FinanceUiState(loading = false, snapshot = if (empty) FinanceSnapshot() else FinanceSnapshot(accounts = listOf(FinancialAccount("1", "Tài khoản chính", "bank", 128_500_000)), categories = listOf(Category("food", "Ăn uống", TransactionType.EXPENSE)), transactions = listOf(Transaction("t1", "1", "food", TransactionType.EXPENSE, 250_000, Instant.now(), "Bữa tối", null, TransactionSource.MANUAL, TransactionStatus.CONFIRMED))))
@Preview(name = "Dashboard có dữ liệu", showBackground = true, backgroundColor = 0xFFF8FBFF) @Composable private fun DashboardDataPreview() { DashboardBackground { DashboardContent(sampleState(), {}, {}, { _, _ -> }, {}, {}, {}, {}, {}) } }
@Preview(name = "Dashboard trống", showBackground = true, backgroundColor = 0xFFF8FBFF) @Composable private fun DashboardEmptyPreview() { DashboardBackground { DashboardContent(sampleState(true), {}, {}, { _, _ -> }, {}, {}, {}, {}, {}) } }
@Preview(name = "Dashboard loading", showBackground = true, backgroundColor = 0xFFF8FBFF) @Composable private fun DashboardLoadingPreview() { DashboardBackground { DashboardLoading() } }
@Preview(name = "Dashboard lỗi", showBackground = true, backgroundColor = 0xFFF8FBFF) @Composable private fun DashboardErrorPreview() { DashboardBackground { DashboardError("Không thể kết nối. Vui lòng thử lại.", {}) } }
@Preview(name = "Màn hình nhỏ", widthDp = 320, heightDp = 700, showBackground = true, backgroundColor = 0xFFF8FBFF) @Composable private fun DashboardSmallPreview() { DashboardBackground { DashboardContent(sampleState(), {}, {}, { _, _ -> }, {}, {}, {}, {}, {}) } }
@Preview(name = "Font lớn", fontScale = 1.5f, widthDp = 390, heightDp = 844, showBackground = true, backgroundColor = 0xFFF8FBFF) @Composable private fun DashboardLargeFontPreview() { DashboardBackground { DashboardContent(sampleState(), {}, {}, { _, _ -> }, {}, {}, {}, {}, {}) } }
