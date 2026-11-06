package com.mobilealarm.ui.holiday

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobilealarm.domain.holiday.WorkdayCalendar
import com.mobilealarm.domain.model.DayInfo
import com.mobilealarm.domain.model.DayKind
import com.mobilealarm.ui.appViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HolidayScreen(onBack: () -> Unit) {
    val viewModel: HolidayViewModel = appViewModel { HolidayViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("节假日与调休") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.refreshing) {
                        if (state.refreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Refresh, contentDescription = "更新节假日数据")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                YearMonthHeader(
                    year = state.year,
                    month = state.month,
                    years = (state.coveredYears + (state.year - 2..state.year + 2)).distinct().sorted(),
                    onYear = viewModel::setYear,
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
                )
            }
            item {
                MonthCalendar(
                    yearMonth = state.yearMonth,
                    calendar = state.calendar,
                    onDayClick = { viewModel.showOverrideDialog(it) },
                )
            }
            item {
                MonthSummary(state.yearMonth, state.calendar)
            }
            if (state.annualReminder) {
                item {
                    InfoBanner(
                        title = "新一年的安排可能尚未更新",
                        text = "国务院一般在 12 月发布下一年节假日安排。点右上角刷新，或到设置中检查更新。",
                    )
                }
            }
            item {
                DataSourceCard(
                    state = state,
                    onRefresh = viewModel::refresh,
                )
            }
            item {
                LegendCard()
            }
        }
    }

    val dialogDate = state.overrideDialogDate
    if (dialogDate != null && state.calendar != null) {
        OverrideDialog(
            date = dialogDate,
            info = state.calendar!!.info(dialogDate),
            onDismiss = { viewModel.showOverrideDialog(null) },
            onSelect = { workday -> viewModel.setOverride(dialogDate, workday) },
        )
    }
}

@Composable
private fun YearMonthHeader(
    year: Int,
    month: Int,
    years: List<Int>,
    onYear: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    var yearMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Box {
                TextButton(onClick = { yearMenu = true }) {
                    Text("$year 年", style = MaterialTheme.typography.titleLarge)
                }
                DropdownMenu(expanded = yearMenu, onDismissRequest = { yearMenu = false }) {
                    years.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text("$candidate 年") },
                            onClick = {
                                yearMenu = false
                                onYear(candidate)
                            },
                        )
                    }
                }
            }
        }
        Text(
            "$month 月",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onPrevious) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "上个月")
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = "下个月")
        }
    }
}

@Composable
private fun MonthCalendar(
    yearMonth: YearMonth,
    calendar: WorkdayCalendar?,
    onDayClick: (LocalDate) -> Unit,
) {
    val first = yearMonth.atDay(1)
    val leading = first.dayOfWeek.value - 1
    val padded = buildList<LocalDate?> {
        repeat(leading) { add(null) }
        for (day in 1..yearMonth.lengthOfMonth()) add(yearMonth.atDay(day))
        while (size % 7 != 0) add(null)
    }
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (label == "六" || label == "日") {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            padded.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        Box(modifier = Modifier.weight(1f)) {
                            if (date == null) {
                                Spacer(Modifier.aspectRatio(0.82f))
                            } else {
                                DayCell(
                                    date = date,
                                    info = calendar?.info(date),
                                    isToday = date == LocalDate.now(),
                                    onClick = { onDayClick(date) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    info: DayInfo?,
    isToday: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(11.dp)
    val background: Color = when (info?.kind) {
        DayKind.MAKEUP, DayKind.OVERRIDE_WORK -> MaterialTheme.colorScheme.tertiaryContainer
        DayKind.HOLIDAY, DayKind.OVERRIDE_REST -> MaterialTheme.colorScheme.errorContainer
        DayKind.WEEKEND -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        else -> Color.Transparent
    }
    val contentColor: Color = when (info?.kind) {
        DayKind.MAKEUP, DayKind.OVERRIDE_WORK -> MaterialTheme.colorScheme.onTertiaryContainer
        DayKind.HOLIDAY, DayKind.OVERRIDE_REST -> MaterialTheme.colorScheme.onErrorContainer
        DayKind.WEEKEND -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = Modifier
            .padding(1.5.dp)
            .fillMaxWidth()
            .aspectRatio(0.82f)
            .background(background, shape)
            .then(
                if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape) else Modifier,
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = contentColor,
        )
        if (info != null) {
            Text(
                text = info.shortLabel,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun MonthSummary(yearMonth: YearMonth, calendar: WorkdayCalendar?) {
    if (calendar == null) return
    var work = 0
    var rest = 0
    for (day in 1..yearMonth.lengthOfMonth()) {
        if (calendar.isWorkday(yearMonth.atDay(day))) work++ else rest++
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatChip("本月上班", "$work 天")
        StatChip("本月休息", "$rest 天")
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun InfoBanner(title: String, text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
}

@Composable
private fun DataSourceCard(state: HolidayUiState, onRefresh: () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.EventAvailable,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text("数据来源", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            if (state.sources.isEmpty()) {
                Text("暂无覆盖数据", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(
                    state.sources.entries.joinToString("、") { (year, source) -> "$year 年（$source）" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (state.lastUpdate > 0) {
                    "上次在线更新：" + Instant.ofEpochMilli(state.lastUpdate)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                } else {
                    "尚未在线更新，当前使用应用内置日历。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRefresh, enabled = !state.refreshing) {
                if (state.refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                } else {
                    Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (state.refreshing) "正在更新…" else "从在线数据源更新")
            }
            Text(
                "在线数据来自 holiday-cn（跟随国务院每年发布的放假安排）。点击任意日期可以手动把某天设为上班或休息。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegendCard() {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            LegendItem("班", "工作日/补班", MaterialTheme.colorScheme.tertiaryContainer)
            LegendItem("休", "周末/法定假日", MaterialTheme.colorScheme.errorContainer)
            LegendItem("今", "今天", Color.Transparent, bordered = true)
        }
    }
}

@Composable
private fun LegendItem(label: String, text: String, color: Color, bordered: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(color, CircleShape)
                .then(
                    if (bordered) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OverrideDialog(
    date: LocalDate,
    info: DayInfo,
    onDismiss: () -> Unit,
    onSelect: (Boolean?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)))
                Text(
                    "${info.kindLabel} · ${info.source}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column {
                Text("手动调整只影响这台手机上的闹钟判断。", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                OverrideOption("恢复法定日历", "删除手动调整，按官方数据判断") { onSelect(null) }
                OverrideOption("设为工作日", "这一天按上班处理") { onSelect(true) }
                OverrideOption("设为休息日", "这一天按休息处理") { onSelect(false) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun OverrideOption(title: String, subtitle: String, onClick: () -> Unit) {
    SurfaceOption(onClick = onClick) {
        Column(modifier = Modifier.padding(vertical = 9.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SurfaceOption(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
    ) {
        content()
    }
}