package com.example.ledger.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ledger.data.CategorySum
import com.example.ledger.data.MonthPoint
import com.example.ledger.ui.LedgerViewModel
import com.example.ledger.ui.MonthSwitcher
import com.example.ledger.ui.ScreenHeader
import com.example.ledger.ui.SectionTitle
import com.example.ledger.ui.theme.ChartPalette
import com.example.ledger.ui.theme.expenseColor
import com.example.ledger.ui.theme.incomeColor
import com.example.ledger.util.formatMoney
import kotlin.math.max

@Composable
fun StatsScreen(vm: LedgerViewModel) {
    val month by vm.month.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("統計")
        MonthSwitcher(month, vm::prevMonth, vm::nextMonth)

        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                StatCell("支出", formatMoney(stats.totals.expense), expenseColor(), Modifier.weight(1f))
                StatCell("收入", formatMoney(stats.totals.income), incomeColor(), Modifier.weight(1f))
                StatCell("結餘", formatMoney(stats.totals.balance), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            }
        }

        SectionTitle("本月支出分類")
        if (stats.expenseByCategory.isEmpty()) {
            Text(
                "這個月沒有支出",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                textAlign = TextAlign.Center,
            )
        } else {
            DonutChart(
                sums = stats.expenseByCategory,
                modifier = Modifier.fillMaxWidth().height(200.dp).padding(16.dp),
            )
            val total = stats.expenseByCategory.sumOf { it.total }
            stats.expenseByCategory.forEachIndexed { i, s ->
                CategoryLegendRow(s, total, ChartPalette[i % ChartPalette.size])
            }
        }

        SectionTitle("近 6 個月趨勢")
        TrendChart(
            points = stats.trend,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(expenseColor(), "支出")
            LegendDot(incomeColor(), "收入")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatCell(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun DonutChart(sums: List<CategorySum>, modifier: Modifier = Modifier) {
    val total = sums.sumOf { it.total }.toFloat()
    Canvas(modifier) {
        val diameter = minOf(size.width, size.height)
        val stroke = diameter * 0.22f
        val topLeft = Offset((size.width - diameter) / 2f + stroke / 2f, (size.height - diameter) / 2f + stroke / 2f)
        val arcSize = Size(diameter - stroke, diameter - stroke)
        var start = -90f
        sums.forEachIndexed { i, s ->
            val sweep = s.total / total * 360f
            drawArc(
                color = ChartPalette[i % ChartPalette.size],
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            start += sweep
        }
    }
}

@Composable
private fun CategoryLegendRow(s: CategorySum, total: Long, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text("${s.icon} ${s.name}", Modifier.weight(1f))
        Text(
            "%.0f%%".format(s.total * 100f / total),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(formatMoney(s.total), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun TrendChart(points: List<MonthPoint>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val peak = max(1L, points.maxOf { max(it.expense, it.income) }).toFloat()
    val barShape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
    Row(
        modifier = modifier.height(160.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        points.forEach { p ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.height(130.dp), verticalAlignment = Alignment.Bottom) {
                    Box(
                        Modifier.width(10.dp).fillMaxHeight(p.expense / peak)
                            .clip(barShape).background(expenseColor()),
                    )
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier.width(10.dp).fillMaxHeight(p.income / peak)
                            .clip(barShape).background(incomeColor()),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text("${p.month.monthValue}月", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
