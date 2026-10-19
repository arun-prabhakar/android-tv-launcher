package me.prince.launcher

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private const val TAG = "Launcher"
// Xiaomi TVs have a picture side panel (mode, backlight, OLED settings); everything else gets display settings
private const val XIAOMI_PICTURE_SETTINGS = "com.mitv.settings.PICTURE_SETTINGS"
// FHD Xiaomi panels ship with 4K density (320 on a 1920px screen = 960dp wide), so dp-sized tiles,
// icons and spacings render ~2x larger than the 4K canvas the layout was authored for. Rescaling the
// density gives a 1080p TV roughly a 1330dp canvas instead; raise/lower to taste.
private const val UI_DENSITY_SCALE = 0.72f

class MainActivity : ComponentActivity() {
    private var reloadKey by mutableIntStateOf(0)
    private var homePresses by mutableIntStateOf(0)
    private var resumes by mutableIntStateOf(0)

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            reloadKey++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Back on the home screen should do nothing, not exit the launcher
        onBackPressedDispatcher.addCallback(this) {}

        registerReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })

        setContent {
            val deviceDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(deviceDensity.density * UI_DENSITY_SCALE, deviceDensity.fontScale),
            ) {
                HomeScreen(
                    reloadKey = reloadKey,
                    homePresses = homePresses,
                    resumes = resumes,
                    onLaunch = { if (it.isInput) launchFresh(it.launchIntent) else launch(it.launchIntent) },
                    onAppInfo = { app ->
                        launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
                    },
                    onWifi = { launch(Intent(Settings.ACTION_WIFI_SETTINGS)) },
                    onSettings = { launch(Intent(Settings.ACTION_SETTINGS)) },
                    onPicture = { launch(pictureSettings()) },
                    onSound = { launch(Intent(Settings.ACTION_SOUND_SETTINGS)) },
                )
            }
        }
    }

    // Home pressed while already on the home screen: jump back to the first app
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homePresses++
    }

    override fun onResume() {
        super.onResume()
        resumes++
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }

    private fun pictureSettings(): Intent {
        val xiaomi = Intent(XIAOMI_PICTURE_SETTINGS)
        return if (packageManager.resolveActivity(xiaomi, 0) != null) xiaomi else Intent(Settings.ACTION_DISPLAY_SETTINGS)
    }

    // Live TV apps (HDMI inputs) and Stremio (title links) can ignore the link if they're already running,
    // so close the target's background process first. It's always in the background while we're Home.
    private fun launchFresh(intent: Intent) {
        packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName?.let {
            getSystemService(ActivityManager::class.java).killBackgroundProcesses(it)
        }
        launch(intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }

    private fun launch(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No activity for $intent", e)
        }
    }
}
