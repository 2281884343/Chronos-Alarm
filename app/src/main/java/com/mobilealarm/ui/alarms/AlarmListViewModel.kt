package com.mobilealarm.ui.alarms

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mobilealarm.MobileAlarmApp
import com.mobilealarm.domain.model.Alarm
import com.mobilealarm.domain.model.AlarmPlan
import com.mobilealarm.domain.model.CommutePlan
import com.mobilealarm.domain.model.CommutePreview
import com.mobilealarm.domain.model.DayInfo
import com.mobilealarm.domain.model.WeatherBundle
import com.mobilealarm.domain.rule.AlarmTimeCalculator
import com.mobilealarm.domain.rule.CommuteCalculator
import com.mobilealarm.domain.holiday.WorkdayCalendar
import com.mobilealarm.util.SystemPermissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

data class PermissionState(
    val exactAlarm: Boolean = true,
    val notifications: Boolean = true,
)

data class AlarmListUiState(
    val loading: Boolean = true,
    val alarms: List<Alarm> = emptyList(),
    val plans: Map<Long, AlarmPlan> = emptyMap(),
    val today: DayInfo? = null,
    val weather: WeatherBundle? = null,
    val weatherEnabled: Boolean = true,
    val permissions: PermissionState = PermissionState(),
    val commutePlan: CommutePlan = CommutePlan(),
    val commutePreview: CommutePreview? = null,
) {
    val nextPlan: AlarmPlan? get() = plans.values.minByOrNull { it.adjusted }
    val enabledCount: Int get() = alarms.count { it.enabled }
}

private data class AlarmBase(
    val alarms: List<Alarm>,
    val plans: Map<Long, AlarmPlan>,
    val today: DayInfo?,
    val weather: WeatherBundle?,
    val weatherEnabled: Boolean,
    val calendar: WorkdayCalendar,
)

class AlarmListViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MobileAlarmApp).container
    private val permissions = MutableStateFlow(readPermissions())
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val base = combine(
        container.alarmRepository.alarms,
        container.holidayRepository.calendarFlow,
        container.weatherRepository.weatherFlow,
        container.settingsRepository.settings,
    ) { alarms, calendar, weather, settings ->
        val now = LocalDateTime.now()
        AlarmBase(
            alarms = alarms,
            plans = AlarmTimeCalculator.nextPlans(alarms, now, calendar, weather),
            today = calendar.info(LocalDate.now()),
            weather = weather,
            weatherEnabled = settings.weatherEnabled,
            calendar = calendar,
        )
    }

    val uiState: StateFlow<AlarmListUiState> = combine(
        base,
        permissions,
        container.commuteRepository.planFlow,
    ) { baseState, permissionState, savedCommutePlan ->
        val commutePlan = savedCommutePlan ?: CommutePlan()
        AlarmListUiState(
            loading = false,
            alarms = baseState.alarms,
            plans = baseState.plans,
            today = baseState.today,
            weather = baseState.weather,
            weatherEnabled = baseState.weatherEnabled,
            permissions = permissionState,
            commutePlan = commutePlan,
            commutePreview = CommuteCalculator.preview(
                commutePlan,
                LocalDateTime.now(),
                baseState.calendar,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AlarmListUiState(),
    )

    fun refreshPermissions() {
        permissions.value = readPermissions()
    }

    private fun readPermissions(): PermissionState = PermissionState(
        exactAlarm = SystemPermissions.canScheduleExactAlarms(getApplication()),
        notifications = SystemPermissions.notificationsEnabled(getApplication()),
    )

    fun setEnabled(alarm: Alarm, enabled: Boolean) {
        viewModelScope.launch {
            container.alarmRepository.setEnabled(alarm.id, enabled)
            if (enabled) {
                container.alarmScheduler.scheduleNext(alarm.copy(enabled = true))
            } else {
                container.alarmScheduler.cancel(alarm.id)
            }
        }
    }

    fun delete(alarmId: Long) {
        viewModelScope.launch {
            container.alarmScheduler.cancel(alarmId)
            container.alarmRepository.delete(alarmId)
        }
    }

    fun refreshWeather() {
        viewModelScope.launch {
            val result = container.weatherRepository.refresh()
            _message.value = result.message
            if (result.success) container.alarmScheduler.rescheduleAll()
        }
    }

    fun refreshCommute() {
        viewModelScope.launch {
            val result = container.commuteRepository.refreshRoute()
            _message.value = result.message
            if (result.success) container.commuteScheduler.scheduleNext()
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun updateMessage(text: String?) {
        _message.update { text }
    }
}