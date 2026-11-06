package com.mobilealarm.ui.holiday

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilealarm.AppContainer
import com.mobilealarm.domain.holiday.WorkdayCalendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class HolidayUiState(
    val loading: Boolean = true,
    val yearMonth: YearMonth = YearMonth.now(),
    val calendar: WorkdayCalendar? = null,
    val sources: Map<Int, String> = emptyMap(),
    val lastUpdate: Long = 0L,
    val refreshing: Boolean = false,
    val annualReminder: Boolean = false,
    val message: String? = null,
    val overrideDialogDate: LocalDate? = null,
) {
    val year: Int get() = yearMonth.year
    val month: Int get() = yearMonth.monthValue
    val coveredYears: List<Int> get() = calendar?.coveredYears ?: emptyList()
}

class HolidayViewModel(private val container: AppContainer) : ViewModel() {

    private val yearMonth = MutableStateFlow(YearMonth.now())
    private val extra = MutableStateFlow(HolidayExtra())

    private data class HolidayExtra(
        val sources: Map<Int, String> = emptyMap(),
        val lastUpdate: Long = 0L,
        val annualReminder: Boolean = false,
        val refreshing: Boolean = false,
        val message: String? = null,
        val dialogDate: LocalDate? = null,
    )

    private val _state = MutableStateFlow(HolidayUiState())
    val state: StateFlow<HolidayUiState> = _state

    init {
        viewModelScope.launch {
            combine(
                container.holidayRepository.calendarFlow,
                container.settingsRepository.settings,
                yearMonth,
                extra,
            ) { calendar, settings, ym, extraState ->
                HolidayUiState(
                    loading = false,
                    yearMonth = ym,
                    calendar = calendar,
                    sources = extraState.sources,
                    lastUpdate = if (settings.lastHolidayUpdate > 0) settings.lastHolidayUpdate else settings.lastHolidayCheck,
                    refreshing = extraState.refreshing,
                    annualReminder = extraState.annualReminder,
                    message = extraState.message,
                    overrideDialogDate = extraState.dialogDate,
                )
            }.collect { _state.value = it }
        }
        viewModelScope.launch { loadMeta() }
    }

    fun previousMonth() {
        yearMonth.update { it.minusMonths(1) }
    }

    fun nextMonth() {
        yearMonth.update { it.plusMonths(1) }
    }

    fun setYear(year: Int) {
        yearMonth.update { YearMonth.of(year, it.monthValue) }
    }

    fun showOverrideDialog(date: LocalDate?) {
        extra.update { it.copy(dialogDate = date) }
    }

    fun setOverride(date: LocalDate, workday: Boolean?) {
        viewModelScope.launch {
            container.holidayRepository.setOverride(date, workday)
            container.alarmScheduler.rescheduleAll()
            val text = when (workday) {
                true -> "已把 ${date} 设为工作日"
                false -> "已把 ${date} 设为休息日"
                null -> "已恢复 ${date} 的法定日历"
            }
            extra.update { it.copy(dialogDate = null, message = text) }
            loadMeta()
        }
    }

    fun refresh() {
        if (extra.value.refreshing) return
        viewModelScope.launch {
            extra.update { it.copy(refreshing = true, message = null) }
            val result = container.holidayRepository.refresh()
            if (result.success) container.alarmScheduler.rescheduleAll()
            extra.update { it.copy(refreshing = false, message = result.message) }
            loadMeta()
        }
    }

    fun consumeMessage() {
        extra.update { it.copy(message = null) }
    }

    private suspend fun loadMeta() {
        val sources = runCatching { container.holidayRepository.yearSources() }.getOrDefault(emptyMap())
        val annual = runCatching { container.holidayRepository.needsAnnualUpdate() }.getOrDefault(false)
        extra.update { it.copy(sources = sources, annualReminder = annual) }
    }
}