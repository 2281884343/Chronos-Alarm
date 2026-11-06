package com.mobilealarm.data.commute

import com.mobilealarm.data.prefs.SettingsRepository
import com.mobilealarm.data.remote.AmapApi
import com.mobilealarm.domain.model.CommuteMode
import com.mobilealarm.domain.model.CommutePlan
import com.mobilealarm.domain.model.CommutePlace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.LocalDateTime
import java.time.LocalTime

data class CommuteRefreshResult(
    val success: Boolean,
    val message: String,
    val plan: CommutePlan? = null,
)

class CommuteRepository(
    private val settings: SettingsRepository,
    private val api: AmapApi,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val planFlow: Flow<CommutePlan?> = settings.settings.map { s ->
        s.commuteJson?.let {
            runCatching { json.decodeFromString<CommutePlan>(it) }.getOrNull()
        }
    }

    suspend fun current(): CommutePlan? = planFlow.first()

    suspend fun setKey(key: String) = settings.setAmapKey(key)

    suspend fun save(plan: CommutePlan) {
        settings.saveCommuteJson(json.encodeToString(CommutePlan.serializer(), plan))
    }

    suspend fun searchPlaces(query: String, key: String? = null): List<CommutePlace> {
        val effectiveKey = key?.takeIf { it.isNotBlank() } ?: settings.current().amapKey
        val city = settings.current().cityName
        return api.searchPlaces(effectiveKey, query, city)
    }

    /**
     * 按当前通勤方式重新取一次耗时，并写回计划。
     * 失败时保留旧耗时，避免把已有提醒弄丢。
     */
    suspend fun refreshRoute(): CommuteRefreshResult {
        val current = current() ?: return CommuteRefreshResult(false, "还没有通勤计划")
        val origin = current.origin ?: return CommuteRefreshResult(false, "请先设置出发地")
        val destination = current.destination ?: return CommuteRefreshResult(false, "请先设置目的地")
        val prefs = settings.current()
        val city = destination.cityName ?: origin.cityName ?: prefs.cityName
        return try {
            val route = api.route(prefs.amapKey, origin, destination, current.mode, city)
            val updated = current.copy(
                routeMinutes = route.minutes,
                routeDistanceMeters = route.distanceMeters,
                routeUpdatedAt = System.currentTimeMillis(),
                routeSummary = route.summary,
            )
            save(updated)
            CommuteRefreshResult(
                success = true,
                message = "${current.mode.label}约 ${route.minutes} 分钟",
                plan = updated,
            )
        } catch (e: IOException) {
            CommuteRefreshResult(false, e.message ?: "路线获取失败")
        } catch (e: Exception) {
            CommuteRefreshResult(false, "路线获取失败：${e.message ?: "未知错误"}")
        }
    }

    /** 只在高德配额最值得花的时间窗内刷新，避免全天反复请求。 */
    suspend fun refreshIfNeeded(now: LocalDateTime = LocalDateTime.now()): CommuteRefreshResult? {
        val plan = current() ?: return null
        if (!plan.enabled || !plan.isConfigured) return null
        // 步行/骑行/电瓶车没有实时路况，反复请求只会浪费高德配额。
        if (plan.mode != CommuteMode.DRIVING && plan.mode != CommuteMode.TRANSIT) return null
        if (settings.current().amapKey.isBlank()) return null
        val arrival = LocalDateTime.of(now.toLocalDate(), LocalTime.of(plan.arrivalHour, plan.arrivalMinute))
        val inWindow = now.isAfter(arrival.minusHours(4)) && now.isBefore(arrival.plusHours(1))
        if (!inWindow) return null
        val stale = plan.routeUpdatedAt == 0L ||
            System.currentTimeMillis() - plan.routeUpdatedAt > 12 * 60 * 1000L
        if (!stale) return null
        return refreshRoute()
    }
}