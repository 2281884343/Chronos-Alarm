package com.mobilealarm

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mobilealarm.ui.alarms.AlarmListScreen
import com.mobilealarm.ui.commute.CommuteScreen
import com.mobilealarm.ui.edit.AlarmEditScreen
import com.mobilealarm.ui.holiday.HolidayScreen
import com.mobilealarm.ui.settings.SettingsScreen
import com.mobilealarm.ui.theme.MobileAlarmTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 纸面主题固定为浅色，不随系统深色模式切换图标颜色。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            MobileAlarmTheme {
                RequestNotificationPermissionOnce()
                MobileAlarmAppNav()
            }
        }
    }
}

/** Android 13+ 首次启动时请求一次通知权限；拒绝后由列表页的权限提示引导去设置。 */
@Composable
private fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val container = (context.applicationContext as MobileAlarmApp).container
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        val asked = container.settingsRepository.current().notificationsAsked
        if (!granted && !asked) {
            container.settingsRepository.setNotificationsAsked()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun MobileAlarmAppNav() {
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = "alarms",
        enterTransition = {
            fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 }
        },
        exitTransition = { fadeOut(tween(150)) },
        popEnterTransition = {
            fadeIn(tween(200)) + slideInHorizontally(tween(240)) { -it / 12 }
        },
        popExitTransition = {
            fadeOut(tween(150)) + slideOutHorizontally(tween(220)) { it / 12 }
        },
    ) {
        composable("alarms") {
            AlarmListScreen(
                onEditAlarm = { id -> nav.navigate("edit/$id") },
                onOpenHoliday = { nav.navigate("holiday") },
                onOpenSettings = { nav.navigate("settings") },
                onOpenCommute = { nav.navigate("commute") },
            )
        }
        composable(
            route = "edit/{alarmId}",
            arguments = listOf(navArgument("alarmId") { type = NavType.LongType; defaultValue = 0L }),
        ) { entry ->
            val alarmId = entry.arguments?.getLong("alarmId") ?: 0L
            AlarmEditScreen(
                alarmId = alarmId,
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack() },
                onOpenCommute = { nav.navigate("commute") },
            )
        }
        composable("holiday") {
            HolidayScreen(onBack = { nav.popBackStack() })
        }
        composable("commute") {
            CommuteScreen(onBack = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenHoliday = { nav.navigate("holiday") },
                onOpenCommute = { nav.navigate("commute") },
            )
        }
    }
}