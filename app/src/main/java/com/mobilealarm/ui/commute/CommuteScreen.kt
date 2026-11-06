package com.mobilealarm.ui.commute

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.ElectricBike
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobilealarm.domain.model.CommuteMode
import com.mobilealarm.domain.model.CommutePlan
import com.mobilealarm.domain.model.CommutePlace
import com.mobilealarm.ui.appViewModel
import com.mobilealarm.ui.components.WheelTimePickerSheet
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommuteScreen(
    onBack: () -> Unit,
    viewModel: CommuteViewModel = appViewModel { CommuteViewModel(it) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showTimePicker by remember { mutableStateOf(false) }
    var keyVisible by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            viewModel.consumeSaved()
            onBack()
        }
    }
    if (showTimePicker) {
        WheelTimePickerSheet(
            initialHour = state.plan.arrivalHour,
            initialMinute = state.plan.arrivalMinute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                viewModel.setArrival(hour, minute)
                showTimePicker = false
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("通勤提醒", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = !state.saving) {
                        if (state.saving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("保存")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 40.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { IntroCard() }
            item {
                KeyCard(
                    key = state.amapKey,
                    visible = keyVisible,
                    onToggleVisible = { keyVisible = !keyVisible },
                    onKeyChange = viewModel::updateKey,
                )
            }
            item {
                RouteCard(
                    state = state,
                    onOriginQuery = viewModel::updateOriginQuery,
                    onDestinationQuery = viewModel::updateDestinationQuery,
                    onSearchOrigin = viewModel::searchOrigin,
                    onSearchDestination = viewModel::searchDestination,
                    onSelectOrigin = viewModel::selectOrigin,
                    onSelectDestination = viewModel::selectDestination,
                )
            }
            item {
                ModeCard(
                    selected = state.plan.mode,
                    onSelect = viewModel::setMode,
                )
            }
            item {
                ScheduleCard(
                    plan = state.plan,
                    preview = state.preview,
                    refreshing = state.refreshing,
                    onPickTime = { showTimePicker = true },
                    onBuffer = viewModel::setBuffer,
                    onEnabled = viewModel::setEnabled,
                    onWorkdaysOnly = viewModel::setWorkdaysOnly,
                    onRefresh = viewModel::refreshRoute,
                )
            }
            item {
                PreviewCard(
                    plan = state.plan,
                    preview = state.preview,
                    refreshing = state.refreshing,
                )
            }
            item {
                Button(
                    onClick = viewModel::save,
                    enabled = !state.saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("保存通勤提醒", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item {
                Text(
                    text = "说明：系统按所选方式取耗时（开车含实时路况，公交含换乘接驳，电瓶车按骑行路线折算），" +
                        "再用「到达时间 − 耗时 − 缓冲」倒推提醒时间。临近出门的 4 小时内每 30 分钟自动刷新一次；" +
                        "进入待机或断网时可能略有延迟。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun IntroCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "先算路，再叫你出门",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "输入到达时间，系统按你选的出行方式倒推最迟出门时间；你再加一段 buffer，就能更从容。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FormulaChip("到达")
                Text("−", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f))
                FormulaChip("耗时")
                Text("−", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f))
                FormulaChip("缓冲")
                Text("=", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f))
                FormulaChip("提醒")
            }
        }
    }
}

@Composable
private fun FormulaChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.66f),
        shape = CircleShape,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun KeyCard(
    key: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    onKeyChange: (String) -> Unit,
) {
    val context = LocalContext.current
    SectionCard(
        title = "高德路线",
        subtitle = "五种出行方式共用一个「Web 服务」Key",
        icon = { Icon(Icons.Outlined.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
    ) {
        OutlinedTextField(
            value = key,
            onValueChange = onKeyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Web 服务 Key") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onToggleVisible) {
                    Icon(
                        if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (visible) "隐藏 Key" else "显示 Key",
                    )
                }
            },
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "个人开发者有免费额度，普通通勤足够用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://console.amap.com/dev/key/app")),
                    )
                }
            }) { Text("去申请") }
        }
    }
}

@Composable
private fun RouteCard(
    state: CommuteUiState,
    onOriginQuery: (String) -> Unit,
    onDestinationQuery: (String) -> Unit,
    onSearchOrigin: () -> Unit,
    onSearchDestination: () -> Unit,
    onSelectOrigin: (CommutePlace) -> Unit,
    onSelectDestination: (CommutePlace) -> Unit,
) {
    SectionCard(
        title = "路线",
        subtitle = "输入地点后点搜索，选择高德返回的 POI",
        icon = { Icon(Icons.Outlined.DirectionsWalk, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
    ) {
        PlaceField(
            label = "出发地",
            badge = "起",
            badgeColor = MaterialTheme.colorScheme.primary,
            value = state.originQuery,
            searching = state.searchingOrigin,
            onValueChange = onOriginQuery,
            onSearch = onSearchOrigin,
        )
        if (state.originResults.isNotEmpty()) {
            PlaceResults(state.originResults, onSelectOrigin)
        }
        Spacer(Modifier.height(10.dp))
        PlaceField(
            label = "目的地",
            badge = "终",
            badgeColor = MaterialTheme.colorScheme.secondary,
            value = state.destinationQuery,
            searching = state.searchingDestination,
            onValueChange = onDestinationQuery,
            onSearch = onSearchDestination,
        )
        if (state.destinationResults.isNotEmpty()) {
            PlaceResults(state.destinationResults, onSelectDestination)
        }
    }
}

@Composable
private fun ModeCard(
    selected: CommuteMode,
    onSelect: (CommuteMode) -> Unit,
) {
    SectionCard(
        title = "怎么去",
        subtitle = "不同方式，耗时差别很大",
        icon = { Icon(Icons.Outlined.DirectionsWalk, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            CommuteMode.entries.forEach { mode ->
                val active = mode == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (active) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                        )
                        .border(
                            width = 1.dp,
                            color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable { onSelect(mode) }
                        .padding(vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = modeIcon(mode),
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = mode.label,
                        fontSize = 10.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = modeNote(selected),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun modeIcon(mode: CommuteMode): ImageVector = when (mode) {
    CommuteMode.WALKING -> Icons.Outlined.DirectionsWalk
    CommuteMode.BICYCLING -> Icons.Outlined.DirectionsBike
    CommuteMode.EBIKE -> Icons.Outlined.ElectricBike
    CommuteMode.DRIVING -> Icons.Outlined.DirectionsCar
    CommuteMode.TRANSIT -> Icons.Outlined.DirectionsBus
}

private fun modeNote(mode: CommuteMode): String = when (mode) {
    CommuteMode.WALKING -> "按步行路线计算，适合 2 公里内；太远会明显偏长。"
    CommuteMode.BICYCLING -> "按高德骑行路线计算，已避开机动车道。"
    CommuteMode.EBIKE -> "按骑行路线 × 0.72 估算；实际受红绿灯、载人和限速影响。"
    CommuteMode.DRIVING -> "含当前路况与红绿灯，早晚高峰会明显变长。"
    CommuteMode.TRANSIT -> "含换乘、步行接驳和候车时间，按最快方案估算。"
}

@Composable
private fun PlaceField(
    label: String,
    badge: String,
    badgeColor: Color,
    value: String,
    searching: Boolean,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        leadingIcon = {
            Surface(color = badgeColor.copy(alpha = 0.14f), shape = CircleShape) {
                Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    Text(badge, color = badgeColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        trailingIcon = {
            if (searching) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Outlined.Search, contentDescription = "搜索$label")
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
    )
}

@Composable
private fun PlaceResults(results: List<CommutePlace>, onSelect: (CommutePlace) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column {
            results.take(5).forEachIndexed { index, place ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(place) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(place.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        place.address?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text("选择", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (index < minOf(results.size, 5) - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun ScheduleCard(
    plan: CommutePlan,
    preview: com.mobilealarm.domain.model.CommutePreview?,
    refreshing: Boolean,
    onPickTime: () -> Unit,
    onBuffer: (Int) -> Unit,
    onEnabled: (Boolean) -> Unit,
    onWorkdaysOnly: (Boolean) -> Unit,
    onRefresh: () -> Unit,
) {
    SectionCard(
        title = "时间",
        subtitle = "按${plan.mode.label}倒推",
        icon = {
            Icon(
                modeIcon(plan.mode),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPickTime)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("到达时间", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    plan.arrivalText,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text("修改", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("额外提前缓冲", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "在「最迟出门」基础上再提前 ${plan.bufferMinutes} 分钟",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                Text(
                    "+${plan.bufferMinutes} 分",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Slider(
            value = plan.bufferMinutes.toFloat(),
            onValueChange = { onBuffer(it.toInt()) },
            valueRange = 0f..60f,
            steps = 11,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        SettingRow("启用通勤提醒", "到提醒时间发横幅通知", plan.enabled, onEnabled)
        SettingRow("仅法定工作日", "自动跳过周末、节假日和调休", plan.workdaysOnly, onWorkdaysOnly)
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onRefresh,
            enabled = !refreshing,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            if (refreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text("正在计算路线…")
            } else {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("重新计算${plan.mode.label}耗时")
            }
        }
        if (preview == null && plan.isConfigured) {
            Text(
                "选择出行方式后点上方按钮，或保存时自动计算。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PreviewCard(
    plan: CommutePlan,
    preview: com.mobilealarm.domain.model.CommutePreview?,
    refreshing: Boolean,
) {
    if (preview == null) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(22.dp),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("还不能倒推", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "填写出发地、目的地和 Key，选择出行方式后点「重新计算耗时」，即可得到最迟出门时间。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    val scheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = scheme.primaryContainer),
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "建议出门",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${preview.arrivalText} 到达",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                preview.latestDepartureText,
                fontSize = 54.sp,
                fontWeight = FontWeight.Light,
                color = scheme.onPrimaryContainer,
            )
            Text(
                "最迟出门 · ${plan.mode.label}约 ${preview.routeMinutes} 分钟",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onPrimaryContainer.copy(alpha = 0.72f),
            )
            Spacer(Modifier.height(14.dp))
            Surface(color = scheme.surface.copy(alpha = 0.7f), shape = RoundedCornerShape(14.dp)) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row {
                        Text("提醒时间", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(
                            preview.remindText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row {
                        Text("含缓冲", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text("${plan.bufferMinutes} 分钟", style = MaterialTheme.typography.bodySmall, color = scheme.onSurface)
                    }
                    if (plan.routeDistanceMeters != null) {
                        Spacer(Modifier.height(4.dp))
                        Row {
                            Text("路程", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            Text(
                                "%.1f 公里".format(plan.routeDistanceMeters / 1000.0),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurface,
                            )
                        }
                    }
                }
            }
            plan.routeSummary?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.62f),
                )
            }
            plan.routeUpdatedAt.takeIf { it > 0 }?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${plan.mode.label}耗时更新于 " + Instant.ofEpochMilli(it)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("HH:mm")),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
        ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                    Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) { icon() }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}