package com.mobilealarm.data.local

import com.mobilealarm.domain.model.Alarm
import com.mobilealarm.domain.model.DayMode
import com.mobilealarm.domain.model.WeatherRule
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun AlarmEntity.toDomain(): Alarm = Alarm(
    id = id,
    label = label,
    hour = hour.coerceIn(0, 23),
    minute = minute.coerceIn(0, 59),
    enabled = enabled,
    dayMode = runCatching { DayMode.valueOf(dayMode) }.getOrDefault(DayMode.WORKDAY),
    customDays = customDays.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet(),
    oneTimeDate = oneTimeDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    observeHolidays = observeHolidays,
    weatherRules = runCatching {
        json.decodeFromString(ListSerializer(WeatherRule.serializer()), weatherRulesJson)
    }.getOrDefault(emptyList()),
    vibrate = vibrate,
    snoozeMinutes = snoozeMinutes.coerceIn(1, 60),
    ringtone = ringtone,
)

fun Alarm.toEntity(): AlarmEntity = AlarmEntity(
    id = id,
    label = label,
    hour = hour,
    minute = minute,
    enabled = enabled,
    dayMode = dayMode.name,
    customDays = customDays.sorted().joinToString(","),
    oneTimeDate = oneTimeDate?.toString(),
    observeHolidays = observeHolidays,
    weatherRulesJson = json.encodeToString(ListSerializer(WeatherRule.serializer()), weatherRules),
    vibrate = vibrate,
    snoozeMinutes = snoozeMinutes,
    ringtone = ringtone,
)