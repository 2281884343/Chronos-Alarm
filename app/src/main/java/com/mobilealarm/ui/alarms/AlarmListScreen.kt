package com.mobilealarm.ui.alarms

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mobilealarm.domain.model.Alarm
import com.mobilealarm.domain.model.AlarmPlan
import com.mobilealarm.domain.model.DayKind
import com.mobilealarm.domain.model.WeatherBundle
import com.mobilealarm.domain.model.WeatherCodes
import com.mobilealarm.domain.model.WeatherGroup
import com.mobilealarm.ui.theme.Hairline
import com.mobilealarm.ui.theme.Ink
import com.mobilealarm.ui.theme.Ink2
import com.mobilealarm.ui.theme.Ink3
import com.mobilealarm.ui.theme.Ink4
import com.mobilealarm.ui.theme.Moss
import com.mobilealarm.ui.theme.Paper
import com.mobilealarm.ui.theme.PaperRaised
import com.mobilealarm.ui.theme.PaperSunken
import com.mobilealarm.ui.theme.Vermilion
import com.mobilealarm.ui.theme.VermilionWash
import com.mobilealarm.util.SystemPermissions
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AlarmListScreen(
    onEditAlarm: (Long) -> Unit,
    onOpenHoliday: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCommute: () -> Unit,
    viewModel: AlarmListViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val otherAlarms = state.alarms.filter { it.id != state.nextPlan?.alarm?.id }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissions()
        onPauseOrDispose { }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            HomeBottomBar(onCreate = { onEditAlarm(0L) })
        },
        containerColor = Paper,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 28.dp),
        ) {
            item {
                Masthead(
                    today = state.today?.date ?: LocalDate.now(),
                    todayKind = state.today?.kindLabel ?: "",
                    onOpenHoliday = onOpenHoliday,
                    onOpenSettings = onOpenSettings,
                )
            }
            item {
                HeroSection(
                    next = state.nextPlan,
                    loading = state.loading,
                    hasAlarms = state.alarms.isNotEmpty(),
                    onToggle = { enabled ->
                        state.nextPlan?.alarm?.let { viewModel.setEnabled(it, enabled) }
                    },
                    onOpen = { state.nextPlan?.alarm?.let { onEditAlarm(it.id) } },
                )
            }
            if (state.alarms.isNotEmpty()) {
                item {
                    TimelineSection(
                        next = state.nextPlan,
                        commuteEnabled = state.commutePlan.enabled,
                        commutePreview = state.commutePreview,
                    )
                }
                if (!state.permissions.exactAlarm || !state.permissions.notifications) {
                    item {
                        Column {
                            if (!state.permissions.exactAlarm) {
                                PermissionStrip(
                                    title = "精确闹钟权限未开启",
                                    text = "系统可能延迟响铃，建议允许“闹钟和提醒”。",
                                    action = "去开启",
                                    onAction = {
                                        SystemPermissions.exactAlarmSettingsIntent(context)?.let { intent ->
                                            runCatching { context.startActivity(intent) }
                                                .onFailure {
                                                    Toast.makeText(context, "请在系统设置中允许精确闹钟", Toast.LENGTH_LONG).show()
                                                }
                                        }
                                    },
                                )
                            }
                            if (!state.permissions.notifications) {
                                PermissionStrip(
                                    title = "通知权限未开启",
                                    text = "全屏提醒和响铃通知需要通知权限。",
                                    action = "去开启",
                                    onAction = {
                                        runCatching {
                                            context.startActivity(SystemPermissions.notificationSettingsIntent(context))
                                        }.onFailure {
                                            Toast.makeText(context, "请在系统设置中开启通知", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                if (otherAlarms.isNotEmpty()) {
                    item {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = Hairline, thickness = 1.dp)
                        }
                    }
                    items(otherAlarms, key = { it.id }) { alarm ->
                        AlarmRow(
                            alarm = alarm,
                            plan = state.plans[alarm.id],
                            onOpen = { onEditAlarm(alarm.id) },
                            onToggle = { enabled -> viewModel.setEnabled(alarm, enabled) },
                        )
                    }
                }
                item {
                    WeatherSection(
                        weather = state.weather,
                        enabled = state.weatherEnabled,
                        onRefresh = { viewModel.refreshWeather() },
                        onSettings = onOpenSettings,
                    )
                }
            }
        }
    }
}

@Composable
private fun Masthead(
    today: LocalDate,
    todayKind: String,
    onOpenHoliday: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "天时",
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, lineHeight = 32.sp),
                color = Ink,
                letterSpacing = 2.sp,
            )
            Text(
                text = buildString {
                    append("  ")
                    append(today.format(DateTimeFormatter.ofPattern("M月d日 EEE", Locale.CHINA)))
                    if (todayKind.isNotBlank()) {
                        append(" · ")
                        append(todayKind)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = Ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenHoliday, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Outlined.CalendarMonth, "节假日日历", tint = Ink2, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Outlined.Settings, "设置", tint = Ink2, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = Hairline, thickness = 1.dp)
    }
}

@Composable
private fun HeroSection(
    next: AlarmPlan?,
    loading: Boolean,
    hasAlarms: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(top = 24.dp)
            .clickable(enabled = next != null, onClick = onOpen),
    ) {
        if (next == null) {
            Text(
                text = when {
                    loading -> "正在读取…"
                    hasAlarms -> "没有启用的闹钟"
                    else -> "还没有闹钟"
                },
                style = MaterialTheme.typography.headlineMedium,
                color = Ink,
            )
            if (!loading) {
                Text(
                    text = if (hasAlarms) {
                        "打开下方任意闹钟，或新建一个。"
                    } else {
                        "先定一个起床时间，天气规则可以稍后再加。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink3,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("下一个闹钟")
                Spacer(Modifier.width(10.dp))
                Text(
                    text = next.dayInfo.kindLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (next.dayInfo.kind == DayKind.MAKEUP) Vermilion else Moss,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = next.timeText,
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 80.sp, lineHeight = 84.sp),
                    color = Ink,
                )
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = next.alarm.enabled,
                    onCheckedChange = onToggle,
                    colors = paperSwitchColors(),
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = buildString {
                        append(relativeDay(next.adjusted.toLocalDate()))
                        append(" · ")
                        append(next.adjusted.format(DateTimeFormatter.ofPattern("EEE", Locale.CHINA)))
                        if (next.alarm.dayMode == com.mobilealarm.domain.model.DayMode.ONCE) append(" · 仅一次")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink2,
                )
                Text("　·　${remainingText(next.adjusted)}", style = MaterialTheme.typography.bodySmall, color = Ink3)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = heroNote(next),
                style = MaterialTheme.typography.bodyMedium,
                color = if (next.offsetMinutes != 0) Vermilion else Ink2,
            )
        }
        Spacer(Modifier.height(22.dp))
        HorizontalDivider(color = Hairline, thickness = 1.dp)
    }
}

@Composable
private fun TimelineSection(
    next: AlarmPlan?,
    commuteEnabled: Boolean,
    commutePreview: com.mobilealarm.domain.model.CommutePreview?,
) {
    val nodes = buildList {
        next?.let {
            add(
                TimelineNode(
                    time = it.timeText,
                    label = relativeDay(it.adjusted.toLocalDate()),
                    title = "起床",
                    detail = buildString {
                        append(it.alarm.label.ifBlank { it.alarm.repeatSummary() })
                        if (it.offsetMinutes != 0) append(" · ${it.offsetLabel}")
                    },
                    accent = Vermilion,
                ),
            )
        }
        if (commuteEnabled) {
            add(
                TimelineNode(
                    time = commutePreview?.latestDepartureText ?: "—",
                    label = "出门",
                    title = commutePreview?.let { "${it.arrivalText} 到达" } ?: "耗时待计算",
                    detail = commutePreview?.let {
                        "${it.plan.mode.label} ${it.routeMinutes} 分 · 缓冲后最迟此刻出门"
                    } ?: "打开通勤提醒，选择出行方式",
                    accent = Moss,
                ),
            )
        }
    }
    if (nodes.isEmpty()) return
    Column(modifier = Modifier.padding(top = 18.dp)) {
        nodes.forEachIndexed { index, node ->
            TimelineRow(node = node, isLast = index == nodes.lastIndex)
        }
    }
}

private data class TimelineNode(
    val time: String,
    val label: String,
    val title: String,
    val detail: String,
    val accent: Color,
)

@Composable
private fun TimelineRow(node: TimelineNode, isLast: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        Column(modifier = Modifier.width(58.dp)) {
            Text(
                text = node.time,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                color = Ink,
            )
            Text(
                text = node.label,
                style = MaterialTheme.typography.labelSmall,
                color = Ink4,
                letterSpacing = 1.sp,
            )
        }
        Box(
            modifier = Modifier
                .width(22.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Hairline),
                )
            }
            Box(
                modifier = Modifier
                    .offset(y = 8.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(node.accent),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp, bottom = 22.dp),
        ) {
            Text(
                text = node.title,
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (node.detail.isNotBlank()) {
                Text(
                    text = node.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink3,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AlarmRow(
    alarm: Alarm,
    plan: AlarmPlan?,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.clickable(onClick = onOpen)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = alarm.timeText,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 32.sp, lineHeight = 36.sp),
                color = if (alarm.enabled) Ink else Ink4,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = alarm.label.ifBlank { alarm.repeatSummary() },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (alarm.enabled) Ink else Ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (plan != null && plan.offsetMinutes != 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = plan.offsetLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Vermilion,
                        )
                    }
                }
                if (alarm.label.isNotBlank()) {
                    Text(
                        text = alarm.repeatSummary(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = alarm.enabled,
                onCheckedChange = onToggle,
                colors = paperSwitchColors(),
            )
        }
        HorizontalDivider(color = Hairline, thickness = 1.dp)
    }
}

@Composable
private fun WeatherSection(
    weather: WeatherBundle?,
    enabled: Boolean,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        HorizontalDivider(color = Hairline, thickness = 1.dp)
        Row(
            modifier = Modifier.padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Eyebrow("天气")
            Spacer(Modifier.width(12.dp))
            Text(
                text = weather?.city ?: "未设置城市",
                style = MaterialTheme.typography.bodySmall,
                color = Ink3,
                modifier = Modifier.clickable(onClick = onSettings),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Outlined.Refresh, "刷新天气", tint = Ink3, modifier = Modifier.size(18.dp))
            }
        }
        Text(
            text = when {
                !enabled -> "天气联动已关闭。打开后，闹钟会按预报自动提前或延后。"
                weather == null -> "还没有天气数据。联网后刷新一次，就能看到未来 16 天预报。"
                else -> weatherSentence(weather)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) Ink2 else Ink3,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (!enabled || weather == null) {
            TextButton(onClick = if (enabled) onRefresh else onSettings, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(if (enabled) "立即获取  →" else "去设置开启  →", color = Vermilion)
            }
        }
    }
}

@Composable
private fun PermissionStrip(
    title: String,
    text: String,
    action: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .background(VermilionWash.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Ink)
            Text(text, style = MaterialTheme.typography.bodySmall, color = Ink3)
        }
        TextButton(onClick = onAction) { Text(action, color = Vermilion) }
    }
}

@Composable
private fun HomeBottomBar(onCreate: () -> Unit) {
    Surface(color = Paper) {
        Column {
            HorizontalDivider(color = Hairline, thickness = 1.dp)
            Box(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = onCreate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PaperSunken,
                        contentColor = Ink,
                    ),
                    border = BorderStroke(1.dp, Ink.copy(alpha = 0.36f)),
                    contentPadding = PaddingValues(horizontal = 18.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("新建闹钟", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun Eyebrow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 2.sp),
        color = Ink3,
    )
}

@Composable
private fun paperSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = PaperRaised,
    checkedTrackColor = Vermilion,
    checkedBorderColor = Vermilion,
    uncheckedThumbColor = PaperRaised,
    uncheckedTrackColor = PaperSunken,
    uncheckedBorderColor = Ink4.copy(alpha = 0.5f),
)

private fun heroNote(plan: AlarmPlan): String {
    val base = "基准 ${plan.baseTimeText}"
    if (plan.offsetMinutes == 0) {
        return "$base · 按基准时间"
    }
    val hint = when (plan.weather?.group) {
        WeatherGroup.RAIN -> "有雨"
        WeatherGroup.SNOW -> "有雪"
        WeatherGroup.STORM -> "有雷雨"
        WeatherGroup.FOG -> "有雾"
        else -> "天气条件"
    }
    return "$base · $hint，${plan.offsetLabel}"
}

private fun weatherSentence(weather: WeatherBundle): String {
    val today = LocalDate.now()
    val day = weather.dayFor(today.plusDays(1)) ?: weather.days.firstOrNull() ?: return "天气数据未更新"
    val label = WeatherCodes.label(day.code)
    val temps = when {
        day.tempMin != null && day.tempMax != null -> "%.0f°~%.0f°".format(day.tempMin, day.tempMax)
        day.tempMax != null -> "最高 %.0f°".format(day.tempMax)
        else -> ""
    }
    val peak = day.hourly.maxByOrNull { it.value.precipProbability ?: 0.0 }
    val rain = peak?.takeIf { (it.value.precipProbability ?: 0.0) >= 30.0 }?.let {
        "${it.key}:00 降水概率 ${it.value.precipProbability?.toInt()}%"
    }
    return buildString {
        append("明天 $label")
        if (temps.isNotBlank()) append(" $temps")
        rain?.let { append(" · $it") }
        if (day.precipProbMax != null) append(" · 全天最高 ${day.precipProbMax.toInt()}%")
    }
}

private fun remainingText(target: LocalDateTime): String {
    val duration = java.time.Duration.between(LocalDateTime.now(), target)
    if (duration.isNegative) return "即将响铃"
    val days = duration.toDays()
    val hours = duration.toHours() % 24
    val minutes = duration.toMinutes() % 60
    return when {
        days > 0 -> "${days} 天 ${hours} 小时"
        hours > 0 -> "${hours} 小时 ${minutes} 分"
        minutes > 0 -> "${minutes} 分钟"
        else -> "马上响铃"
    }
}

fun relativeDay(date: LocalDate, withTime: Boolean = true): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        today.plusDays(2) -> "后天"
        else -> date.format(DateTimeFormatter.ofPattern("M月d日 EEE", Locale.CHINA))
    }
}
