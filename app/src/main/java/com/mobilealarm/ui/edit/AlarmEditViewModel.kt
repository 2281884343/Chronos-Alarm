package com.mobilealarm.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilealarm.AppContainer
import com.mobilealarm.domain.model.Alarm
import com.mobilealarm.domain.model.AlarmPlan
import com.mobilealarm.domain.model.DayMode
import com.mobilealarm.domain.model.RuleComparator
import com.mobilealarm.domain.model.WeatherGroup
import com.mobilealarm.domain.model.WeatherMetric
import com.mobilealarm.domain.model.WeatherRule
import com.mobilealarm.domain.rule.AlarmTimeCalculator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class AlarmEditUiState(
    val loading: Boolean = true,
    val enabled: Boolean = true,
    val hour: Int = 7,
    val minute: Int = 30,
    val label: String = "",
    val dayMode: DayMode = DayMode.WORKDAY,
    val customDays: Set<Int> = setOf(1, 2, 3, 4, 5),
    val oneTimeDate: LocalDate? = null,
    val observeHolidays: Boolean = true,
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = 10,
    val ringtone: String? = null,
    val rules: List<WeatherRule> = emptyList(),
    val preview: AlarmPlan? = null,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val error: String? = null,
) {
    val timeText: String get() = "%02d:%02d".format(hour, minute)
}

class AlarmEditViewModel(
    private val container: AppContainer,
    private val alarmId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(AlarmEditUiState())
    val state: StateFlow<AlarmEditUiState> = _state
    private var previewJob: Job? = null

    init {
        viewModelScope.launch {
            val existing = if (alarmId == 0L) null else container.alarmRepository.get(alarmId)
            if (existing != null) {
                _state.value = AlarmEditUiState(
                    loading = false,
                    enabled = existing.enabled,
                    hour = existing.hour,
                    minute = existing.minute,
                    label = existing.label,
                    dayMode = existing.dayMode,
                    customDays = existing.customDays,
                    oneTimeDate = existing.oneTimeDate ?: if (existing.dayMode == DayMode.ONCE) {
                        defaultOneTimeDate(existing.hour, existing.minute)
                    } else {
                        null
                    },
                    observeHolidays = existing.observeHolidays,
                    vibrate = existing.vibrate,
                    snoozeMinutes = existing.snoozeMinutes,
                    ringtone = existing.ringtone,
                    rules = existing.weatherRules,
                )
            } else {
                _state.update { it.copy(loading = false) }
            }
            refreshPreview()
        }
    }

    fun setTime(hour: Int, minute: Int) = mutate { it.copy(hour = hour, minute = minute) }

    fun setLabel(label: String) = _state.update { it.copy(label = label.take(30)) }

    fun setDayMode(mode: DayMode) = mutate {
        it.copy(
            dayMode = mode,
            customDays = if (mode == DayMode.CUSTOM && it.customDays.isEmpty()) {
                setOf(1, 2, 3, 4, 5)
            } else {
                it.customDays
            },
            oneTimeDate = if (mode == DayMode.ONCE) {
                it.oneTimeDate ?: defaultOneTimeDate(it.hour, it.minute)
            } else {
                it.oneTimeDate
            },
        )
    }

    fun setOneTimeDate(date: LocalDate) = mutate { it.copy(oneTimeDate = date) }

    private fun defaultOneTimeDate(hour: Int, minute: Int): LocalDate {
        val now = LocalDateTime.now()
        val todayTarget = LocalDateTime.of(now.toLocalDate(), LocalTime.of(hour, minute))
        return if (todayTarget.isAfter(now)) now.toLocalDate() else now.toLocalDate().plusDays(1)
    }

    fun toggleCustomDay(day: Int) = mutate {
        val days = it.customDays.toMutableSet()
        if (!days.add(day)) days.remove(day)
        it.copy(customDays = days)
    }

    fun setObserveHolidays(value: Boolean) = mutate { it.copy(observeHolidays = value) }

    fun setVibrate(value: Boolean) = mutate { it.copy(vibrate = value) }

    fun setSnoozeMinutes(value: Int) = mutate { it.copy(snoozeMinutes = value) }

    fun setRingtone(uri: String?) = mutate { it.copy(ringtone = uri) }

    fun addRule() = mutate {
        it.copy(rules = it.rules + WeatherRule(metric = WeatherMetric.PRECIP_PROB, threshold = 60.0, offsetMinutes = -15))
    }

    fun updateRule(rule: WeatherRule) = mutate { current ->
        current.copy(rules = current.rules.map { if (it.id == rule.id) rule else it })
    }

    fun removeRule(id: String) = mutate { current ->
        current.copy(rules = current.rules.filterNot { it.id == id })
    }

    /**
     * 天气现象只支持“属于/不属于”，其它指标只支持数值比较，切换指标时顺手修正比较符。
     */
    fun changeMetric(rule: WeatherRule, metric: WeatherMetric): WeatherRule {
        val comparator = when {
            metric.isGroup && rule.comparator !in listOf(RuleComparator.IN, RuleComparator.NOT_IN) -> RuleComparator.IN
            !metric.isGroup && rule.comparator in listOf(RuleComparator.IN, RuleComparator.NOT_IN) -> RuleComparator.GTE
            else -> rule.comparator
        }
        val threshold = if (metric == WeatherMetric.WEATHER_GROUP) rule.threshold else when (metric) {
            WeatherMetric.PRECIP_PROB -> 60.0
            WeatherMetric.TEMPERATURE -> 25.0
            WeatherMetric.TEMP_MIN -> 5.0
            WeatherMetric.TEMP_MAX -> 30.0
            WeatherMetric.WIND_SPEED -> 30.0
            WeatherMetric.PRECIP_MM -> 5.0
            WeatherMetric.UV_INDEX -> 6.0
            WeatherMetric.WEATHER_GROUP -> rule.threshold
        }
        val groups = if (metric.isGroup) rule.groups.ifEmpty { listOf(WeatherGroup.RAIN) } else emptyList()
        val updated = rule.copy(metric = metric, comparator = comparator, threshold = threshold, groups = groups)
        updateRule(updated)
        return updated
    }

    private fun mutate(block: (AlarmEditUiState) -> AlarmEditUiState) {
        _state.update(block)
        refreshPreview()
    }

    private fun refreshPreview() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(120)
            val alarm = toAlarm()
            runCatching {
                val calendar = container.holidayRepository.currentCalendar()
                val settings = container.settingsRepository.current()
                val weather = if (settings.weatherEnabled) container.weatherRepository.current() else null
                AlarmTimeCalculator.nextPlan(alarm, LocalDateTime.now(), calendar, weather)
            }.onSuccess { plan ->
                _state.update { it.copy(preview = plan) }
            }
        }
    }

    fun save() {
        val current = _state.value
        if (current.dayMode == DayMode.CUSTOM && current.customDays.isEmpty()) {
            _state.update { it.copy(error = "自定义重复至少要选择一个星期") }
            return
        }
        if (current.dayMode == DayMode.ONCE && current.enabled) {
            val date = current.oneTimeDate
            if (date == null) {
                _state.update { it.copy(error = "请选择只响一次的日期") }
                return
            }
            val target = LocalDateTime.of(date, LocalTime.of(current.hour, current.minute))
            if (!target.isAfter(LocalDateTime.now())) {
                _state.update { it.copy(error = "这个时间已经过去了，请选择未来的日期或时间") }
                return
            }
        }
        val alarm = toAlarm()
        viewModelScope.launch {
            val now = LocalDateTime.now()
            if (alarm.dayMode == DayMode.ONCE && alarm.enabled) {
                val date = alarm.oneTimeDate
                val plan = if (date == null) {
                    null
                } else {
                    runCatching {
                        val calendar = container.holidayRepository.currentCalendar()
                        val settings = container.settingsRepository.current()
                        val weather = if (settings.weatherEnabled) container.weatherRepository.current() else null
                        AlarmTimeCalculator.planFor(alarm, date, calendar, weather)
                    }.getOrNull()
                }
                if (plan == null || !plan.adjusted.isAfter(now)) {
                    _state.update { it.copy(error = "这个时间已经过去了，请选择未来的日期或时间") }
                    return@launch
                }
            }
            runCatching {
                val id = container.alarmRepository.save(alarm)
                val saved = container.alarmRepository.get(id)
                if (saved != null) {
                    if (saved.enabled) {
                        container.alarmScheduler.scheduleNext(saved)
                    } else {
                        container.alarmScheduler.cancel(saved.id)
                    }
                }
            }.onSuccess {
                _state.update { it.copy(saved = true) }
            }.onFailure { error ->
                _state.update { it.copy(error = "保存失败：${error.message}") }
            }
        }
    }

    fun delete() {
        if (alarmId == 0L) return
        viewModelScope.launch {
            runCatching {
                container.alarmScheduler.cancel(alarmId)
                container.alarmRepository.delete(alarmId)
            }.onSuccess {
                _state.update { it.copy(deleted = true) }
            }.onFailure { error ->
                _state.update { it.copy(error = "删除失败：${error.message}") }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun toAlarm(): Alarm = Alarm(
        id = alarmId,
        label = _state.value.label.trim(),
        hour = _state.value.hour,
        minute = _state.value.minute,
        enabled = _state.value.enabled,
        dayMode = _state.value.dayMode,
        customDays = _state.value.customDays,
        oneTimeDate = _state.value.oneTimeDate,
        observeHolidays = _state.value.observeHolidays,
        weatherRules = _state.value.rules,
        vibrate = _state.value.vibrate,
        snoozeMinutes = _state.value.snoozeMinutes,
        ringtone = _state.value.ringtone,
    )
}