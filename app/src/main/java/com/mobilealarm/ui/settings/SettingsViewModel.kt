package com.mobilealarm.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilealarm.AppContainer
import com.mobilealarm.data.prefs.AppSettings
import com.mobilealarm.domain.model.CityOption
import com.mobilealarm.domain.model.WeatherBundle
import com.mobilealarm.util.SystemPermissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.TimeZone

data class SettingsUiState(
    val loading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val weather: WeatherBundle? = null,
    val weatherRefreshing: Boolean = false,
    val locating: Boolean = false,
    val searching: Boolean = false,
    val searchQuery: String = "",
    val cityResults: List<CityOption> = emptyList(),
    val message: String? = null,
    val exactAlarm: Boolean = true,
    val notifications: Boolean = true,
    val batteryOptimized: Boolean = true,
    val fullScreenAllowed: Boolean = true,
)

private data class SettingsExtra(
    val weatherRefreshing: Boolean = false,
    val locating: Boolean = false,
    val searching: Boolean = false,
    val searchQuery: String = "",
    val cityResults: List<CityOption> = emptyList(),
    val message: String? = null,
    val exactAlarm: Boolean = true,
    val notifications: Boolean = true,
    val batteryOptimized: Boolean = true,
    val fullScreenAllowed: Boolean = true,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val extra = MutableStateFlow(readPermissions())

    val uiState: StateFlow<SettingsUiState> = combine(
        container.settingsRepository.settings,
        container.weatherRepository.weatherFlow,
        extra,
    ) { settings, weather, extraState ->
        SettingsUiState(
            loading = false,
            settings = settings,
            weather = weather,
            weatherRefreshing = extraState.weatherRefreshing,
            locating = extraState.locating,
            searching = extraState.searching,
            searchQuery = extraState.searchQuery,
            cityResults = extraState.cityResults,
            message = extraState.message,
            exactAlarm = extraState.exactAlarm,
            notifications = extraState.notifications,
            batteryOptimized = extraState.batteryOptimized,
            fullScreenAllowed = extraState.fullScreenAllowed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    private fun readPermissions(): SettingsExtra {
        val context = container.appContext
        return SettingsExtra(
            exactAlarm = SystemPermissions.canScheduleExactAlarms(context),
            notifications = SystemPermissions.notificationsEnabled(context),
            batteryOptimized = SystemPermissions.isIgnoringBatteryOptimizations(context),
            fullScreenAllowed = SystemPermissions.canUseFullScreenIntent(context),
        )
    }

    fun refreshPermissions() {
        val latest = readPermissions()
        extra.update {
            it.copy(
                exactAlarm = latest.exactAlarm,
                notifications = latest.notifications,
                batteryOptimized = latest.batteryOptimized,
                fullScreenAllowed = latest.fullScreenAllowed,
            )
        }
    }

    fun setSearchQuery(text: String) {
        extra.update { it.copy(searchQuery = text) }
    }

    fun searchCity() {
        val query = extra.value.searchQuery.trim()
        if (query.isEmpty()) {
            extra.update { it.copy(message = "请输入城市名称，例如：上海、杭州、深圳") }
            return
        }
        viewModelScope.launch {
            extra.update { it.copy(searching = true, message = null) }
            val results = runCatching { container.weatherRepository.searchCity(query) }
                .getOrElse { error ->
                    extra.update { it.copy(searching = false, message = error.message ?: "搜索城市失败") }
                    return@launch
                }
            extra.update {
                it.copy(
                    searching = false,
                    cityResults = results,
                    message = if (results.isEmpty()) "没有找到“$query”，换个写法试试" else null,
                )
            }
        }
    }

    fun selectCity(city: CityOption) {
        viewModelScope.launch {
            container.settingsRepository.setCity(
                name = city.name,
                latitude = city.latitude,
                longitude = city.longitude,
                timezone = city.timezone ?: TimeZone.getDefault().id,
            )
            extra.update { it.copy(cityResults = emptyList(), searchQuery = "") }
            val weatherResult = refreshWeatherInternal()
            extra.update { it.copy(message = "已切换到 ${city.displayName}。$weatherResult") }
        }
    }

    fun hasLocationPermission(): Boolean = container.locationProvider.hasPermission()

    fun locate() {
        if (!container.locationProvider.hasPermission()) {
            extra.update { it.copy(message = "需要定位权限才能使用当前位置") }
            return
        }
        viewModelScope.launch {
            extra.update { it.copy(locating = true, message = null) }
            val location = container.locationProvider.currentLocation()
            if (location == null) {
                extra.update { it.copy(locating = false, message = "暂时拿不到定位，请手动搜索城市") }
                return@launch
            }
            val name = "当前位置"
            container.settingsRepository.setCity(
                name = name,
                latitude = location.latitude,
                longitude = location.longitude,
                timezone = TimeZone.getDefault().id,
            )
            container.settingsRepository.setAutoLocation(true)
            val weatherResult = refreshWeatherInternal()
            extra.update {
                it.copy(
                    locating = false,
                    message = "已定位到当前位置（%.4f, %.4f）。%s".format(
                        location.latitude,
                        location.longitude,
                        weatherResult,
                    ),
                )
            }
        }
    }

    fun disableAutoLocation() {
        viewModelScope.launch {
            container.settingsRepository.setAutoLocation(false)
            extra.update { it.copy(message = "已关闭自动定位，继续使用当前城市") }
        }
    }

    fun setWeatherEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setWeatherEnabled(enabled)
            container.alarmScheduler.rescheduleAll()
            extra.update {
                it.copy(message = if (enabled) "天气联动已开启" else "天气联动已关闭，所有闹钟按基准时间响铃")
            }
        }
    }

    fun refreshWeather() {
        if (extra.value.weatherRefreshing) return
        viewModelScope.launch {
            extra.update { it.copy(weatherRefreshing = true, message = null) }
            val message = refreshWeatherInternal()
            extra.update { it.copy(weatherRefreshing = false, message = message) }
        }
    }

    private suspend fun refreshWeatherInternal(): String {
        val result = container.weatherRepository.refresh()
        if (result.success) container.alarmScheduler.rescheduleAll()
        return result.message
    }

    fun consumeMessage() {
        extra.update { it.copy(message = null) }
    }
}