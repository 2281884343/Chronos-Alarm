package com.mobilealarm.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import com.mobilealarm.MobileAlarmApp
import java.util.concurrent.TimeUnit

class WeatherRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? MobileAlarmApp ?: return Result.success()
        val result = app.container.weatherRepository.refresh()
        app.container.alarmScheduler.rescheduleAll()
        return if (result.success) {
            Result.success()
        } else if (runAttemptCount < 2) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}

class HolidayUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? MobileAlarmApp ?: return Result.success()
        val result = app.container.holidayRepository.refresh()
        if (result.success) {
            app.container.alarmScheduler.rescheduleAll()
            app.container.commuteScheduler.scheduleNext()
        }
        return Result.success()
    }
}

class CommuteRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? MobileAlarmApp ?: return Result.success()
        app.container.commuteRepository.refreshIfNeeded()
        app.container.commuteScheduler.scheduleNext()
        return Result.success()
    }
}

object BackgroundWork {
    private const val WEATHER_PERIODIC = "weather-periodic"
    private const val HOLIDAY_PERIODIC = "holiday-periodic"
    private const val COMMUTE_PERIODIC = "commute-periodic"
    private const val WEATHER_NOW = "weather-now"

    private val network = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedulePeriodic(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniquePeriodicWork(
            WEATHER_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<WeatherRefreshWorker>(3, TimeUnit.HOURS)
                .setConstraints(network)
                .build(),
        )
        workManager.enqueueUniquePeriodicWork(
            HOLIDAY_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<HolidayUpdateWorker>(12, TimeUnit.HOURS)
                .setConstraints(network)
                .build(),
        )
        workManager.enqueueUniquePeriodicWork(
            COMMUTE_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<CommuteRefreshWorker>(30, TimeUnit.MINUTES)
                .setConstraints(network)
                .build(),
        )
    }

    suspend fun refreshWeatherIfStale(app: MobileAlarmApp) {
        val settings = app.container.settingsRepository.current()
        if (!settings.weatherEnabled) return
        if (System.currentTimeMillis() - settings.lastWeatherFetch < 3 * 60 * 60 * 1000L) return
        WorkManager.getInstance(app).enqueueUniqueWork(
            WEATHER_NOW,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<WeatherRefreshWorker>()
                .setConstraints(network)
                .build(),
        )
    }
}