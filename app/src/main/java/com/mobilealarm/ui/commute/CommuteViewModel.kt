package com.mobilealarm.ui.commute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilealarm.AppContainer
import com.mobilealarm.domain.model.CommuteMode
import com.mobilealarm.domain.model.CommutePlan
import com.mobilealarm.domain.model.CommutePlace
import com.mobilealarm.domain.model.CommutePreview
import com.mobilealarm.domain.rule.CommuteCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

data class CommuteUiState(
    val loading: Boolean = true,
    val plan: CommutePlan = CommutePlan(),
    val amapKey: String = "",
    val preview: CommutePreview? = null,
    val originQuery: String = "",
    val destinationQuery: String = "",
    val originResults: List<CommutePlace> = emptyList(),
    val destinationResults: List<CommutePlace> = emptyList(),
    val searchingOrigin: Boolean = false,
    val searchingDestination: Boolean = false,
    val refreshing: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
) {
    val canPlan: Boolean get() = plan.origin != null && plan.destination != null && amapKey.isNotBlank()
}

class CommuteViewModel(private val container: AppContainer) : ViewModel() {
    private val _uiState = MutableStateFlow(CommuteUiState())
    val uiState: StateFlow<CommuteUiState> = _uiState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        viewModelScope.launch {
            val settings = container.settingsRepository.current()
            val plan = container.commuteRepository.current() ?: CommutePlan()
            _uiState.value = CommuteUiState(
                loading = false,
                plan = plan,
                amapKey = settings.amapKey,
                originQuery = plan.origin?.name.orEmpty(),
                destinationQuery = plan.destination?.name.orEmpty(),
            )
            recalcPreview()
        }
    }

    fun updateKey(value: String) = _uiState.update { it.copy(amapKey = value.trim()) }

    fun updateOriginQuery(value: String) = _uiState.update {
        it.copy(originQuery = value, originResults = if (value.isBlank()) emptyList() else it.originResults)
    }

    fun updateDestinationQuery(value: String) = _uiState.update {
        it.copy(destinationQuery = value, destinationResults = if (value.isBlank()) emptyList() else it.destinationResults)
    }

    fun searchOrigin() {
        val query = _uiState.value.originQuery.trim()
        if (query.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(searchingOrigin = true) }
            runCatching {
                container.commuteRepository.searchPlaces(query, _uiState.value.amapKey)
            }
                .onSuccess { results ->
                    _uiState.update { it.copy(searchingOrigin = false, originResults = results) }
                    if (results.isEmpty()) _message.value = "没有找到「$query」，试试加上城市名"
                }
                .onFailure {
                    _uiState.update { s -> s.copy(searchingOrigin = false) }
                    _message.value = it.message ?: "搜索失败"
                }
        }
    }

    fun searchDestination() {
        val query = _uiState.value.destinationQuery.trim()
        if (query.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(searchingDestination = true) }
            runCatching {
                container.commuteRepository.searchPlaces(query, _uiState.value.amapKey)
            }
                .onSuccess { results ->
                    _uiState.update { it.copy(searchingDestination = false, destinationResults = results) }
                    if (results.isEmpty()) _message.value = "没有找到「$query」，试试加上城市名"
                }
                .onFailure {
                    _uiState.update { s -> s.copy(searchingDestination = false) }
                    _message.value = it.message ?: "搜索失败"
                }
        }
    }

    fun selectOrigin(place: CommutePlace) {
        _uiState.update {
            it.copy(
                originQuery = place.name,
                originResults = emptyList(),
                plan = it.plan.copy(origin = place).invalidatedRoute(),
            )
        }
        recalcPreview()
    }

    fun selectDestination(place: CommutePlace) {
        _uiState.update {
            it.copy(
                destinationQuery = place.name,
                destinationResults = emptyList(),
                plan = it.plan.copy(destination = place).invalidatedRoute(),
            )
        }
        recalcPreview()
    }

    fun setArrival(hour: Int, minute: Int) {
        _uiState.update { it.copy(plan = it.plan.copy(arrivalHour = hour, arrivalMinute = minute)) }
        recalcPreview()
    }

    fun setMode(mode: CommuteMode) {
        _uiState.update {
            if (it.plan.mode == mode) it
            else it.copy(plan = it.plan.copy(mode = mode).invalidatedRoute())
        }
        recalcPreview()
    }

    fun setBuffer(minutes: Int) {
        _uiState.update { it.copy(plan = it.plan.copy(bufferMinutes = minutes.coerceIn(0, 60))) }
        recalcPreview()
    }

    fun setEnabled(enabled: Boolean) {
        _uiState.update { it.copy(plan = it.plan.copy(enabled = enabled)) }
        recalcPreview()
    }

    fun setWorkdaysOnly(value: Boolean) {
        _uiState.update { it.copy(plan = it.plan.copy(workdaysOnly = value)) }
        recalcPreview()
    }

    fun updatePlan(transform: (CommutePlan) -> CommutePlan) {
        _uiState.update { it.copy(plan = transform(it.plan)) }
        recalcPreview()
    }

    fun refreshRoute() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.amapKey.isBlank()) {
                _message.value = "请先填写高德 Web 服务 Key"
                return@launch
            }
            if (!state.plan.isConfigured) {
                _message.value = "请先选择出发地和目的地"
                return@launch
            }
            _uiState.update { it.copy(refreshing = true) }
            container.settingsRepository.setAmapKey(state.amapKey)
            container.commuteRepository.save(state.plan)
            val result = container.commuteRepository.refreshRoute()
            _uiState.update {
                it.copy(
                    refreshing = false,
                    plan = result.plan ?: it.plan,
                )
            }
            _message.value = result.message
            recalcPreview()
            if (result.success) container.commuteScheduler.scheduleNext()
        }
    }

    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.plan.origin == null || state.plan.destination == null) {
                _message.value = "请先选择出发地和目的地"
                return@launch
            }
            val key = state.amapKey.trim()
            if (state.plan.enabled && key.isBlank()) {
                _message.value = "启用通勤提醒前，请先填写高德 Web 服务 Key"
                return@launch
            }
            _uiState.update { it.copy(saving = true) }
            container.settingsRepository.setAmapKey(key)
            var plan = state.plan
            container.commuteRepository.save(plan)
            var error: String? = null
            if (plan.enabled) {
                val result = container.commuteRepository.refreshRoute()
                result.plan?.let { plan = it }
                if (!result.success) error = result.message
            }
            container.commuteRepository.save(plan)
            if (plan.enabled && error == null) {
                container.commuteScheduler.scheduleNext()
            } else {
                container.commuteScheduler.cancel()
            }
            _uiState.update {
                it.copy(
                    saving = false,
                    plan = plan,
                    preview = null,
                    saved = error == null,
                )
            }
            recalcPreview()
            _message.value = error ?: "通勤提醒已保存"
        }
    }

    private fun recalcPreview() {
        viewModelScope.launch {
            val plan = _uiState.value.plan
            val calendar = container.holidayRepository.currentCalendar()
            val preview = withContext(Dispatchers.Default) {
                CommuteCalculator.preview(plan, LocalDateTime.now(), calendar)
            }
            _uiState.update { it.copy(preview = preview) }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun consumeSaved() {
        _uiState.update { it.copy(saved = false) }
    }

    private fun CommutePlan.invalidatedRoute(): CommutePlan = copy(
        routeMinutes = null,
        routeDistanceMeters = null,
        routeUpdatedAt = 0L,
        routeSummary = null,
    )
}