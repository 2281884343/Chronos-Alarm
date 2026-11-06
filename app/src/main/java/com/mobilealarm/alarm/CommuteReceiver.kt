package com.mobilealarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mobilealarm.MobileAlarmApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime

class CommuteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DISMISS -> {
                CommuteNotifications.cancel(context)
                return
            }
            ACTION_FIRE -> Unit
            else -> return
        }

        val app = context.applicationContext as? MobileAlarmApp ?: return
        val pending = goAsync()
        val route = intent.getIntExtra(EXTRA_ROUTE_MINUTES, 0)
        val arrivalHour = intent.getIntExtra(EXTRA_ARRIVAL_HOUR, 9)
        val arrivalMinute = intent.getIntExtra(EXTRA_ARRIVAL_MINUTE, 0)

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val plan = app.container.commuteRepository.current()
                if (plan != null) {
                    val minutes = route.takeIf { it > 0 } ?: plan.routeMinutes ?: 0
                    if (minutes > 0) {
                        val arrival = LocalDateTime.of(
                            LocalDateTime.now().toLocalDate(),
                            LocalTime.of(arrivalHour.coerceIn(0, 23), arrivalMinute.coerceIn(0, 59)),
                        )
                        val latest = arrival.minusMinutes(minutes.toLong())
                        CommuteNotifications.show(
                            context,
                            plan,
                            com.mobilealarm.domain.model.CommutePreview(
                                plan = plan,
                                arrival = arrival,
                                latestDeparture = latest,
                                remindAt = latest.minusMinutes(plan.bufferMinutes.toLong()),
                                routeMinutes = minutes,
                            ),
                        )
                    }
                }
                app.container.commuteScheduler.scheduleNext()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.mobilealarm.action.COMMUTE_FIRE"
        const val ACTION_DISMISS = "com.mobilealarm.action.COMMUTE_DISMISS"
        const val EXTRA_ROUTE_MINUTES = "route_minutes"
        const val EXTRA_ARRIVAL_HOUR = "arrival_hour"
        const val EXTRA_ARRIVAL_MINUTE = "arrival_minute"
        const val EXTRA_LATEST_DEPARTURE = "latest_departure"
    }
}