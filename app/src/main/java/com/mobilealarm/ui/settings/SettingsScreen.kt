package com.mobilealarm.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobilealarm.data.prefs.AppSettings
import com.mobilealarm.domain.model.CityOption
import com.mobilealarm.ui.appViewModel
import com.mobilealarm.util.SystemPermissions
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenHoliday: () -> Unit,
    onOpenCommute: () -> Unit,
) {
    val viewModel: SettingsViewModel = appViewModel { SettingsViewModel(it) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissions()
        onPauseOrDispose { }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) {
            viewModel.locate()
        } else {
            viewModel.locate()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
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
                start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                WeatherLocationCard(
                    state = state,
                    onQueryChange = viewModel::setSearchQuery,
                    onSearch = viewModel::searchCity,
                    onSelectCity = viewModel::selectCity,
                    onRefresh = viewModel::refreshWeather,
                    onWeatherEnabled = viewModel::setWeatherEnabled,
                    onAutoLocation = { enabled ->
                        if (!enabled) {
                            viewModel.disableAutoLocation()
                        } else if (viewModel.hasLocationPermission()) {
                            viewModel.locate()
                        } else {
                            locationLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        }
                    },
                )
            }
            item {
                HolidayDataCard(settings = state.settings, onOpenHoliday = onOpenHoliday)
            }
            item {
                CommuteEntryCard(onOpenCommute = onOpenCommute)
            }
            item {
                PermissionsCard(
                    state = state,
                    onExactAlarm = {
                        SystemPermissions.exactAlarmSettingsIntent(context)?.let { intent ->
                            runCatching { context.startActivity(intent) }
                                .onFailure { Toast.makeText(context, "请在系统设置中允许精确闹钟", Toast.LENGTH_LONG).show() }
                        }
                    },
                    onNotifications = {
                        runCatching { context.startActivity(SystemPermissions.notificationSettingsIntent(context)) }
                    },
                    onBattery = {
                        runCatching { context.startActivity(SystemPermissions.batteryOptimizationIntent(context)) }
                            .onFailure {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                }
                            }
                    },
                    onFullScreen = {
                        SystemPermissions.fullScreenIntentSettingsIntent(context)?.let { intent ->
                            runCatching { context.startActivity(intent) }
                        }
                    },
                )
            }
            item {
                AboutCard()
            }
        }
    }
}

@Composable
private fun WeatherLocationCard(
    state: SettingsUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectCity: (CityOption) -> Unit,
    onRefresh: () -> Unit,
    onWeatherEnabled: (Boolean) -> Unit,
    onAutoLocation: (Boolean) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("天气位置", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${state.settings.cityName} · %.2f, %.2f".format(
                            state.settings.latitude,
                            state.settings.longitude,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.weatherRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新天气")
                    }
                }
            }
            Text(
                text = state.weather?.let { bundle ->
                    "${bundle.days.size} 天预报 · " + (bundle.days.firstOrNull()?.let { day ->
                        "${day.tempMin?.let { "%.0f".format(it) } ?: "-"}° ~ ${day.tempMax?.let { "%.0f".format(it) } ?: "-"}°"
                    } ?: "")
                } ?: "暂无天气数据，联网后点刷新获取。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (state.settings.lastWeatherFetch > 0) {
                    "上次更新：" + Instant.ofEpochMilli(state.settings.lastWeatherFetch)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
                } else {
                    ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索城市，例如 杭州") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searching) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onSearch) {
                            Icon(Icons.Outlined.Search, contentDescription = "搜索")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            )
            state.cityResults.forEach { city ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectCity(city) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        city.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            SettingRow(
                title = "使用当前位置",
                subtitle = if (state.locating) "正在定位…" else "读取系统缓存定位，不引入额外定位服务",
                checked = state.settings.useAutoLocation,
                onCheckedChange = onAutoLocation,
            )
            SettingRow(
                title = "天气联动",
                subtitle = "关闭后忽略所有天气条件，按基准时间响铃",
                checked = state.settings.weatherEnabled,
                onCheckedChange = onWeatherEnabled,
            )
        }
    }
}

@Composable
private fun CommuteEntryCard(onOpenCommute: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenCommute),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.DirectionsCar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Text("通勤提醒", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun HolidayDataCard(settings: AppSettings, onOpenHoliday: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenHoliday),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("节假日与调休", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (settings.lastHolidayUpdate > 0) {
                        "已更新 · " + Instant.ofEpochMilli(settings.lastHolidayUpdate)
                            .atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
                    } else {
                        "内置 2024–2026 日历"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PermissionsCard(
    state: SettingsUiState,
    onExactAlarm: () -> Unit,
    onNotifications: () -> Unit,
    onBattery: () -> Unit,
    onFullScreen: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("权限与后台运行", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            PermissionRow(
                title = "精确闹钟",
                subtitle = "允许“闹钟和提醒”，保证准点响铃",
                granted = state.exactAlarm,
                actionLabel = "去开启",
                onAction = onExactAlarm,
            )
            PermissionRow(
                title = "通知",
                subtitle = "全屏提醒、响铃通知与操作按钮",
                granted = state.notifications,
                actionLabel = "去开启",
                onAction = onNotifications,
            )
            PermissionRow(
                title = "忽略电池优化",
                subtitle = "避免系统在待机时限制闹钟",
                granted = state.batteryOptimized,
                actionLabel = "去设置",
                onAction = onBattery,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                PermissionRow(
                    title = "全屏通知",
                    subtitle = "锁屏时直接显示闹钟响铃页面",
                    granted = state.fullScreenAllowed,
                    actionLabel = "去开启",
                    onAction = onFullScreen,
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (granted) {
            Text(
                "已开启",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        } else {
            TextButton(onClick = onAction) { Text(actionLabel) }
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
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AboutCard() {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Thermostat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("关于天气联动", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "天气来自 Open-Meteo；节假日来自 holiday-cn。多个条件叠加，总调整不超过 ±180 分钟。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}