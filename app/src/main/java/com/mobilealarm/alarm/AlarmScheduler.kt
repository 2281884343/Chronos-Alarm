package com.mobilealarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mobilealarm.MainActivity
import com.mobilealarm.data.AlarmRepository
import com.mobilealarm.data.holiday.HolidayRepository
import com.mobilealarm.data.prefs.SettingsRepository
import com.mobilealarm.data.weather.WeatherRepository
import com.mobilealarm.domain.model.Alarm
import com.mobilealarm.domain.rule.AlarmTimeCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 每个闹钟只保留下一次待响铃的 PendingIntent。
 * 使用 setAlarmClock：系统会在状态栏显示下一个闹钟，且待机时也能准时唤醒。
 */
class AlarmScheduler(
    private val context: Context,
    private val alarmRepository: AlarmRepository,
    private val holidayRepository: HolidayRepository,
    private val weatherRepository: WeatherRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    suspend fun rescheduleAll() = withContext(Dispatchers.IO) {
        val alarms = alarmRepository.getAll().filter { it.enabled }
        val calendar = holidayRepository.currentCalendar()
        val settings = settingsRepository.current()
        val weather = if (settings.weatherEnabled) weatherRepository.current() else null
        val now = LocalDateTime.now()
        for (alarm in alarms) {
            val plan = AlarmTimeCalculator.nextPlan(alarm, now, calendar, weather) ?: continue
            scheduleAt(alarm.id, plan.adjusted, snooze = false)
        }
    }

    suspend fun scheduleNext(alarm: Alarm) = withContext(Dispatchers.IO) {
        if (!alarm.enabled) {
            cancel(alarm.id)
            return@withContext
        }
        val calendar = holidayRepository.currentCalendar()
        val settings = settingsRepository.current()
        val weather = if (settings.weatherEnabled) weatherRepository.current() else null
        val plan = AlarmTimeCalculator.nextPlan(alarm, LocalDateTime.now(), calendar, weather)
        if (plan == null) cancel(alarm.id) else scheduleAt(alarm.id, plan.adjusted, snooze = false)
    }

    fun scheduleSnooze(alarm: Alarm, minutes: Int = alarm.snoozeMinutes) {
        scheduleAt(alarm.id, LocalDateTime.now().plusMinutes(minutes.toLong()), snooze = true)
    }

    fun scheduleAt(alarmId: Long, dateTime: LocalDateTime, snooze: Boolean) {
        val triggerAt = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (triggerAt <= System.currentTimeMillis()) return
        val operation = alarmPendingIntent(alarmId, snooze)
        val showIntent = PendingIntent.getActivity(
            context,
            REQUEST_SHOW,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val info = AlarmManager.AlarmClockInfo(triggerAt, showIntent)
        try {
            alarmManager.setAlarmClock(info, operation)
        } catch (error: SecurityException) {
            // 没有精确闹钟权限时退化为允许待机唤醒的普通闹钟，仍然保证响铃。
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
    }

    fun cancel(alarmId: Long) {
        alarmManager.cancel(alarmPendingIntent(alarmId, snooze = false))
        alarmManager.cancel(alarmPendingIntent(alarmId, snooze = true))
    }

    private fun alarmPendingIntent(alarmId: Long, snooze: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE_ALARM
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_SNOOZE, snooze)
        }
        val requestCode = (alarmId.toInt() and 0x0FFFFFFF) * 2 + if (snooze) 1 else 0
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return alarmManager.canScheduleExactAlarms()
    }

    companion object {
        private const val REQUEST_SHOW = 90_001
    }
}