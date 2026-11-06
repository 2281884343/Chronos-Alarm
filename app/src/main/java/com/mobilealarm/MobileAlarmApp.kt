package com.mobilealarm

import android.app.Application
import com.mobilealarm.alarm.AlarmNotifications
import com.mobilealarm.alarm.CommuteScheduler
import com.mobilealarm.data.AlarmRepository
import com.mobilealarm.data.commute.CommuteRepository
import com.mobilealarm.data.holiday.HolidayRepository
import com.mobilealarm.data.local.AlarmDatabase
import com.mobilealarm.data.prefs.SettingsRepository
import com.mobilealarm.data.remote.AmapApi
import com.mobilealarm.data.remote.OpenMeteoApi
import com.mobilealarm.data.weather.LocationProvider
import com.mobilealarm.data.weather.WeatherRepository
import com.mobilealarm.worker.BackgroundWork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Application) {
    val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository = SettingsRepository(appContext)
    private val database = AlarmDatabase.get(appContext)
    val alarmRepository = AlarmRepository(database.alarmDao())
    val holidayRepository = HolidayRepository(appContext, settingsRepository)
    val weatherRepository = WeatherRepository(settingsRepository, OpenMeteoApi())
    val locationProvider = LocationProvider(appContext)
    val commuteRepository = CommuteRepository(settingsRepository, AmapApi())
    val alarmScheduler = com.mobilealarm.alarm.AlarmScheduler(
        context = appContext,
        alarmRepository = alarmRepository,
        holidayRepository = holidayRepository,
        weatherRepository = weatherRepository,
        settingsRepository = settingsRepository,
    )
    val commuteScheduler = CommuteScheduler(
        context = appContext,
        commuteRepository = commuteRepository,
        holidayRepository = holidayRepository,
    )
}

class MobileAlarmApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AlarmNotifications.ensureChannel(this)
        com.mobilealarm.alarm.CommuteNotifications.ensureChannel(this)
        BackgroundWork.schedulePeriodic(this)
        container.appScope.launch {
            container.alarmScheduler.rescheduleAll()
            container.commuteRepository.refreshIfNeeded()
            container.commuteScheduler.scheduleNext()
            BackgroundWork.refreshWeatherIfStale(this@MobileAlarmApp)
        }
    }
}