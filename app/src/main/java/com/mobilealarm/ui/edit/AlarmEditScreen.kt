package com.mobilealarm.ui.edit

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobilealarm.domain.model.AlarmPlan
import com.mobilealarm.domain.model.DayMode
import com.mobilealarm.domain.model.RuleComparator
import com.mobilealarm.domain.model.WeatherGroup
import com.mobilealarm.domain.model.WeatherMetric
import com.mobilealarm.domain.model.WeatherRule
import com.mobilealarm.ui.appViewModel
import com.mobilealarm.ui.components.WheelTimePickerSheet
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(
    alarmId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onOpenCommute: () -> Unit,
) {
    val viewModel: AlarmEditViewModel = appViewModel(key = "alarm-edit-$alarmId") {
        AlarmEditViewModel(it, alarmId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showRingtonePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var editingRuleId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onSaved()
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.let {
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            viewModel.setRingtone(uri?.toString())
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            EditBottomBar(
                plan = state.preview,
                loading = state.loading,
                onSave = viewModel::save,
            )
        },
        containerColor = Paper,
    ) { padding ->
        if (state.loading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("正在翻页…", color = Ink3)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
        ) {
            item {
                EditHeader(
                    isNew = alarmId == 0L,
                    onBack = onBack,
                    onDelete = { showDeleteConfirm = true },
                )
            }
            item {
                TimeHero(
                    hour = state.hour,
                    minute = state.minute,
                    onClick = { showTimePicker = true },
                )
            }
            item {
                RepeatSection(
                    state = state,
                    onMode = viewModel::setDayMode,
                    onDatePicker = { showDatePicker = true },
                    onToggleDay = viewModel::toggleCustomDay,
                    onHolidays = viewModel::setObserveHolidays,
                )
            }
            item {
                LabelSection(value = state.label, onChange = viewModel::setLabel)
            }
            item {
                WeatherRulesSection(
                    rules = state.rules,
                    plan = state.preview,
                    onAdd = viewModel::addRule,
                    onEdit = { rule -> editingRuleId = rule.id },
                )
            }
            item {
                CommuteEntry(onOpenCommute = onOpenCommute)
            }
            item {
                RingSection(
                    snoozeMinutes = state.snoozeMinutes,
                    vibrate = state.vibrate,
                    ringtone = state.ringtone,
                    onSnooze = viewModel::setSnoozeMinutes,
                    onVibrate = viewModel::setVibrate,
                    onPickRingtone = { showRingtonePicker = true },
                )
            }
        }
    }

    if (showTimePicker) {
        WheelTimePickerSheet(
            initialHour = state.hour,
            initialMinute = state.minute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                viewModel.setTime(hour, minute)
                showTimePicker = false
            },
        )
    }

    editingRuleId?.let { id ->
        val rule = state.rules.firstOrNull { it.id == id }
        if (rule == null) {
            editingRuleId = null
        } else {
            RuleEditorSheet(
                index = state.rules.indexOfFirst { it.id == id } + 1,
                rule = rule,
                onUpdate = viewModel::updateRule,
                onMetricChange = { metric -> viewModel.changeMetric(rule, metric) },
                onRemove = {
                    viewModel.removeRule(id)
                    editingRuleId = null
                },
                onDismiss = { editingRuleId = null },
            )
        }
    }

    if (showDatePicker) {
        val today = LocalDate.now()
        val initialDate = state.oneTimeDate?.takeIf { !it.isBefore(today) } ?: today
        val todayUtcMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis >= todayUtcMillis
                }

                override fun isSelectableYear(year: Int): Boolean = year >= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            viewModel.setOneTimeDate(
                                Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                            )
                        }
                        showDatePicker = false
                    },
                ) { Text("确定", color = Vermilion) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消", color = Ink3) }
            },
        ) {
            DatePicker(
                state = datePickerState,
                showModeToggle = false,
                title = { Text("选择响铃日期", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
            )
        }
    }

    if (showRingtonePicker) {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择闹钟铃声")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                state.ringtone?.let { runCatching { Uri.parse(it) }.getOrNull() },
            )
        }
        LaunchedEffect(Unit) {
            showRingtonePicker = false
            runCatching { ringtoneLauncher.launch(intent) }
                .onFailure { snackbar.showSnackbar("无法打开系统铃声选择器") }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = PaperRaised,
            title = { Text("删除这个闹钟？", fontFamily = MaterialTheme.typography.headlineSmall.fontFamily) },
            text = { Text("删除后将取消已安排的下一次响铃，且无法恢复。", color = Ink2) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                }) {
                    Text("删除", color = Vermilion)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消", color = Ink3) }
            },
        )
    }
}

@Composable
private fun EditHeader(
    isNew: Boolean,
    onBack: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = Ink, modifier = Modifier.size(20.dp))
        }
        Text(
            text = if (isNew) "新建闹钟" else "编辑闹钟",
            style = MaterialTheme.typography.titleLarge,
            color = Ink,
            modifier = Modifier.padding(start = 6.dp),
        )
        Spacer(Modifier.weight(1f))
        if (!isNew) {
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Delete, "删除闹钟", tint = Ink3, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun TimeHero(hour: Int, minute: Int, onClick: () -> Unit) {
    val hour12 = ((hour + 11) % 12) + 1
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 14.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "%02d".format(hour12),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp, lineHeight = 68.sp),
            color = Ink,
        )
        Text(
            text = ":",
            style = MaterialTheme.typography.displayMedium.copy(fontSize = 46.sp),
            color = Ink3,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            text = "%02d".format(minute),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp, lineHeight = 68.sp),
            color = Ink,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = if (hour < 12) "上午" else "下午",
            style = MaterialTheme.typography.titleMedium,
            color = Ink2,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    HorizontalDivider(color = Hairline, thickness = 1.dp)
}

@Composable
private fun RepeatSection(
    state: AlarmEditUiState,
    onMode: (DayMode) -> Unit,
    onDatePicker: () -> Unit,
    onToggleDay: (Int) -> Unit,
    onHolidays: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 18.dp)) {
        SectionHeader("重复")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            DayMode.entries.forEach { mode ->
                Column(
                    modifier = Modifier
                        .clickable { onMode(mode) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.dayMode == mode) Vermilion else Ink2,
                        fontWeight = if (state.dayMode == mode) FontWeight.Medium else FontWeight.Normal,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(2.dp)
                            .background(if (state.dayMode == mode) Vermilion else Color.Transparent),
                    )
                }
            }
        }
        when (state.dayMode) {
            DayMode.ONCE -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(PaperSunken.copy(alpha = 0.7f))
                        .clickable(onClick = onDatePicker)
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.CalendarMonth, null, tint = Vermilion, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = state.oneTimeDate?.format(
                            DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA),
                        ) ?: "请选择日期",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink,
                        modifier = Modifier.weight(1f),
                    )
                    Text("改日期", style = MaterialTheme.typography.labelMedium, color = Vermilion)
                }
            }

            DayMode.CUSTOM -> {
                Spacer(Modifier.height(10.dp))
                WeekdayPicker(selected = state.customDays, onToggle = onToggleDay)
                Spacer(Modifier.height(8.dp))
                HolidaySwitch(checked = state.observeHolidays, onChange = onHolidays)
            }

            else -> {
                Spacer(Modifier.height(8.dp))
                HolidaySwitch(checked = state.observeHolidays, onChange = onHolidays)
            }
        }
    }
}

@Composable
private fun LabelSection(value: String, onChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(top = 20.dp)) {
        Text("备注", style = MaterialTheme.typography.labelSmall, color = Ink3, letterSpacing = 1.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = Ink),
            cursorBrush = SolidColor(Vermilion),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 4.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isBlank()) {
                        Text(
                            "起床、吃药、赶高铁…",
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink4,
                        )
                    }
                    inner()
                }
            },
        )
        HorizontalDivider(color = Hairline, thickness = 1.dp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun WeatherRulesSection(
    rules: List<WeatherRule>,
    plan: AlarmPlan?,
    onAdd: () -> Unit,
    onEdit: (WeatherRule) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 22.dp)) {
        SectionHeader("天气条件")
        rules.forEachIndexed { index, rule ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            RuleCard(
                index = index + 1,
                rule = rule,
                plan = plan,
                onClick = { onEdit(rule) },
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onAdd)
                .drawBehind {
                    drawRoundRect(
                        color = Ink.copy(alpha = 0.26f),
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 6.dp.toPx())),
                        ),
                        cornerRadius = CornerRadius(14.dp.toPx()),
                    )
                }
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Add, null, tint = Vermilion, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("再加一个条件", style = MaterialTheme.typography.bodyMedium, color = Vermilion)
            }
        }
    }
}

@Composable
private fun RuleCard(
    index: Int,
    rule: WeatherRule,
    plan: AlarmPlan?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PaperRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = "%02d".format(index),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                color = Ink4,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ruleCardTitle(rule, plan),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (rule.enabled) Ink else Ink4,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = rule.conditionText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Ink3,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                color = if (rule.offsetMinutes == 0) PaperSunken else VermilionWash,
                shape = RoundedCornerShape(20.dp),
            ) {
                Text(
                    text = rule.offsetLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (rule.offsetMinutes == 0) Ink3 else Vermilion,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun CommuteEntry(onOpenCommute: () -> Unit) {
    Column(modifier = Modifier.padding(top = 22.dp)) {
        SectionHeader("出门")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenCommute)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "通勤出门提醒",
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Outlined.ChevronRight, "打开通勤设置", tint = Ink4, modifier = Modifier.size(20.dp))
        }
        HorizontalDivider(color = Hairline, thickness = 1.dp)
    }
}

@Composable
private fun RingSection(
    snoozeMinutes: Int,
    vibrate: Boolean,
    ringtone: String?,
    onSnooze: (Int) -> Unit,
    onVibrate: (Boolean) -> Unit,
    onPickRingtone: () -> Unit,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(top = 22.dp)) {
        SectionHeader("响铃")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onPickRingtone)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.VolumeUp, null, tint = Ink2, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("铃声", style = MaterialTheme.typography.bodyMedium, color = Ink)
            Spacer(Modifier.weight(1f))
            Text(
                ringtoneTitle(context, ringtone),
                style = MaterialTheme.typography.bodySmall,
                color = Ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(150.dp),
                textAlign = TextAlign.End,
            )
            Icon(Icons.Outlined.ChevronRight, null, tint = Ink4, modifier = Modifier.size(18.dp))
        }
        HorizontalDivider(color = Hairline, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("稍后提醒", style = MaterialTheme.typography.bodyMedium, color = Ink)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(5, 10, 15, 20, 30).forEach { minutes ->
                    ChoicePill(
                        text = "$minutes 分",
                        selected = snoozeMinutes == minutes,
                        onClick = { onSnooze(minutes) },
                    )
                }
            }
        }
        HorizontalDivider(color = Hairline, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "响铃时震动",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = vibrate, onCheckedChange = onVibrate, colors = editSwitchColors())
        }
    }
}

@Composable
private fun EditBottomBar(plan: AlarmPlan?, loading: Boolean, onSave: () -> Unit) {
    Surface(color = Paper) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = Hairline, thickness = 1.dp)
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                if (loading) {
                    Text(
                        text = "正在计算下一次响铃…",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink3,
                    )
                } else if (plan != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${dayWord(plan.adjusted.toLocalDate())} ${plan.timeText} 响铃",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Ink,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (plan.offsetMinutes == 0) "按基准时间" else "比基准${plan.offsetLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (plan.offsetMinutes == 0) Ink3 else Vermilion,
                        )
                    }
                } else {
                    Text(
                        text = "这个时间已经过去，或未来没有符合条件的日期。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Vermilion,
                    )
                }
                Spacer(Modifier.height(9.dp))
                Button(
                    onClick = onSave,
                    enabled = !loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PaperSunken,
                        contentColor = Ink,
                    ),
                    border = BorderStroke(1.dp, Ink.copy(alpha = 0.44f)),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                ) {
                    Icon(Icons.Outlined.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("保存闹钟", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleEditorSheet(
    index: Int,
    rule: WeatherRule,
    onUpdate: (WeatherRule) -> Unit,
    onMetricChange: (WeatherMetric) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PaperRaised,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 3.dp)
                    .clip(CircleShape)
                    .background(Ink.copy(alpha = 0.25f)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "条件 %02d".format(index),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Ink,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Delete, "删除条件", tint = Vermilion, modifier = Modifier.size(20.dp))
                }
            }
            Text(
                "按闹钟响铃时刻的预报判断，多个条件同时命中时调整分钟数相加。",
                style = MaterialTheme.typography.bodySmall,
                color = Ink3,
            )
            Spacer(Modifier.height(16.dp))

            EnumDropdown(
                label = "判断什么",
                value = rule.metric.label,
                options = WeatherMetric.entries.map { it.label },
                modifier = Modifier.fillMaxWidth(),
                onSelect = { label ->
                    WeatherMetric.entries.firstOrNull { it.label == label }?.let(onMetricChange)
                },
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val comparators = if (rule.metric.isGroup) {
                    listOf(RuleComparator.IN, RuleComparator.NOT_IN)
                } else {
                    listOf(RuleComparator.GTE, RuleComparator.LTE)
                }
                EnumDropdown(
                    label = "关系",
                    value = rule.comparator.label,
                    options = comparators.map { it.label },
                    modifier = Modifier.width(104.dp),
                    onSelect = { label ->
                        comparators.firstOrNull { it.label == label }?.let { onUpdate(rule.copy(comparator = it)) }
                    },
                )
                if (!rule.metric.isGroup) {
                    var thresholdText by remember(rule.id, rule.metric) {
                        mutableStateOf(formatNumber(rule.threshold))
                    }
                    OutlinedTextField(
                        value = thresholdText,
                        onValueChange = { text ->
                            if (text.length <= 7) {
                                thresholdText = text
                                text.toDoubleOrNull()?.let { onUpdate(rule.copy(threshold = it)) }
                            }
                        },
                        label = {
                            Text("阈值" + rule.metric.unit.takeIf { it.isNotBlank() }?.let { "（$it）" }.orEmpty())
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }

            if (rule.metric.isGroup) {
                Spacer(Modifier.height(12.dp))
                Text("天气现象（可多选）", style = MaterialTheme.typography.labelMedium, color = Ink3)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WeatherGroup.entries.forEach { group ->
                        ChoicePill(
                            text = group.label,
                            selected = group in rule.groups,
                            onClick = {
                                val groups = rule.groups.toMutableList()
                                if (group in groups) groups.remove(group) else groups.add(group)
                                onUpdate(rule.copy(groups = groups))
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = Hairline, thickness = 1.dp)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("满足时", style = MaterialTheme.typography.bodyMedium, color = Ink2)
                Spacer(Modifier.weight(1f))
                Text(
                    rule.offsetLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (rule.offsetMinutes == 0) Ink3 else Vermilion,
                )
            }
            Slider(
                value = rule.offsetMinutes.toFloat(),
                onValueChange = { value ->
                    onUpdate(rule.copy(offsetMinutes = ((value / 5).roundToInt() * 5).coerceIn(-120, 120)))
                },
                valueRange = -120f..120f,
                steps = 47,
                colors = SliderDefaults.colors(
                    thumbColor = Vermilion,
                    activeTrackColor = Vermilion,
                    inactiveTrackColor = PaperSunken,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("提前 2 小时", style = MaterialTheme.typography.labelSmall, color = Ink4)
                Text("不调整", style = MaterialTheme.typography.labelSmall, color = Ink4)
                Text("延后 2 小时", style = MaterialTheme.typography.labelSmall, color = Ink4)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = rule.sentence(),
                style = MaterialTheme.typography.bodyMedium,
                color = Ink2,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PaperSunken.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PaperSunken,
                    contentColor = Ink,
                ),
                border = BorderStroke(1.dp, Ink.copy(alpha = 0.3f)),
            ) {
                Text("完成", fontSize = 15.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnumDropdown(
    label: String,
    value: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun WeekdayPicker(selected: Set<Int>, onToggle: (Int) -> Unit) {
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        labels.forEachIndexed { index, label ->
            val day = index + 1
            val isSelected = day in selected
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Vermilion else PaperSunken)
                    .clickable { onToggle(day) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (isSelected) Paper else Ink2,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun HolidaySwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "跟随法定节假日与调休",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onChange, colors = editSwitchColors())
    }
}

@Composable
private fun ChoicePill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Ink else PaperSunken)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Paper else Ink2,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 2.sp),
        color = Ink3,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun editSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = PaperRaised,
    checkedTrackColor = Vermilion,
    checkedBorderColor = Vermilion,
    uncheckedThumbColor = PaperRaised,
    uncheckedTrackColor = PaperSunken,
    uncheckedBorderColor = Ink4.copy(alpha = 0.5f),
)

private fun ruleCardTitle(rule: WeatherRule, plan: AlarmPlan?): String {
    val whenText = plan?.let { pil ->
        val date = pil.base.toLocalDate()
        val prefix = when {
            date == LocalDate.now() && pil.base.hour < 12 -> "今早"
            date == LocalDate.now() -> "今天"
            date == LocalDate.now().plusDays(1) && pil.base.hour < 12 -> "明早"
            date == LocalDate.now().plusDays(1) -> "明天"
            else -> date.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))
        }
        "$prefix ${"%02d:%02d".format(pil.base.hour, pil.base.minute)} "
    } ?: ""
    val condition = when (rule.metric) {
        WeatherMetric.WEATHER_GROUP -> {
            val names = rule.groups.map {
                when (it) {
                    WeatherGroup.CLEAR -> "晴天"
                    WeatherGroup.CLOUDY -> "多云"
                    WeatherGroup.FOG -> "有雾"
                    WeatherGroup.RAIN -> "下雨"
                    WeatherGroup.SNOW -> "下雪"
                    WeatherGroup.STORM -> "雷暴"
                }
            }
            if (names.isEmpty()) "天气现象" else names.joinToString("、")
        }
        WeatherMetric.PRECIP_PROB -> "降水概率 ${rule.comparator.label} ${formatNumber(rule.threshold)}%"
        WeatherMetric.TEMPERATURE -> "气温 ${rule.comparator.label} ${formatNumber(rule.threshold)}°C"
        WeatherMetric.TEMP_MIN -> "最低气温 ${rule.comparator.label} ${formatNumber(rule.threshold)}°C"
        WeatherMetric.TEMP_MAX -> "最高气温 ${rule.comparator.label} ${formatNumber(rule.threshold)}°C"
        WeatherMetric.WIND_SPEED -> "风速 ${rule.comparator.label} ${formatNumber(rule.threshold)} km/h"
        WeatherMetric.PRECIP_MM -> "降水量 ${rule.comparator.label} ${formatNumber(rule.threshold)} mm"
        WeatherMetric.UV_INDEX -> "紫外线 ${rule.comparator.label} ${formatNumber(rule.threshold)}"
    }
    return whenText + condition
}

private fun dayWord(date: LocalDate): String = when (date) {
    LocalDate.now() -> "今天"
    LocalDate.now().plusDays(1) -> "明天"
    LocalDate.now().plusDays(2) -> "后天"
    else -> date.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))
}

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

private fun ringtoneTitle(context: android.content.Context, uri: String?): String {
    if (uri.isNullOrBlank()) return "默认闹钟铃声"
    return runCatching {
        val parsed = Uri.parse(uri)
        RingtoneManager.getRingtone(context, parsed)?.getTitle(context)
    }.getOrNull() ?: "默认闹钟铃声"
}
