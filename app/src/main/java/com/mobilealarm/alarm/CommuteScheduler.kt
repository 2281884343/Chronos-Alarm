package com.mobilealarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mobilealarm.MainActivity
import com.mobilealarm.data.commute.CommuteRepository
import com.mobilealarm.data.holiday.HolidayRepository
import com.mobilealarm.domain.model.CommutePreview
import com.mobilealarm.domain.rule.CommuteCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId

class CommuteScheduler(
    private val context: Context,
    private val commuteRepository: CommuteRepository,
    private val holidayRepository: HolidayRepository,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    suspend fun scheduleNext() = withContext(Dispatchers.IO) {
        val plan = commuteRepository.current()
        if (plan == null || !plan.enabled || !plan.isConfigured) {
            cancel()
            return@withContext
        }
        val calendar = holidayRepository.currentCalendar()
        val preview = CommuteCalculator.preview(plan, LocalDateTime.now(), calendar)
        if (preview == null) {
            cancel()
        } else {
            scheduleAt(preview)
        }
    }

    fun scheduleAt(preview: CommutePreview) {
        val trigger = preview.remindAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (trigger <= System.currentTimeMillis()) return
        val operation = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, CommuteReceiver::class.java).apply {
                action = CommuteReceiver.ACTION_FIRE
                putExtra(CommuteReceiver.EXTRA_ROUTE_MINUTES, preview.routeMinutes)
                putExtra(CommuteReceiver.EXTRA_ARRIVAL_HOUR, preview.arrival.hour)
                putExtra(CommuteReceiver.EXTRA_ARRIVAL_MINUTE, preview.arrival.minute)
                putExtra(CommuteReceiver.EXTRA_LATEST_DEPARTURE, preview.latestDepartureText)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, operation)
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, operation)
        }
    }

    fun cancel() {
        val operation = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, CommuteReceiver::class.java).setAction(CommuteReceiver.ACTION_FIRE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(operation)
    }

    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return alarmManager.canScheduleExactAlarms()
    }

    private fun showIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_SHOW,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val REQUEST_CODE = 0x0CC0_0010
        private const val REQUEST_SHOW = 0x0CC0_0011
    }
}