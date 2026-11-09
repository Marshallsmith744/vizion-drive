package com.vizionconcepts.drive.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Process
import android.os.UserHandle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppEntry(
    val label: String,
    val packageName: String,
    val component: String, // flattened ComponentName
    val icon: ImageBitmap,
)

class AppsRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = reload()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = reload()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = reload()
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
    }

    init {
        launcherApps.registerCallback(callback)
        reload()
    }

    fun reload() {
        scope.launch {
            val size = (64 * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
            val list = launcherApps.getActivityList(null, Process.myUserHandle())
                .filter { it.applicationInfo.packageName != context.packageName }
                .map { info ->
                    AppEntry(
                        label = info.label.toString(),
                        packageName = info.applicationInfo.packageName,
                        component = info.componentName.flattenToString(),
                        icon = info.getBadgedIcon(0).toBitmap(size).asImageBitmap(),
                    )
                }
                .sortedBy { it.label.lowercase() }
            _apps.value = list
        }
    }

    fun find(component: String): AppEntry? = _apps.value.firstOrNull { it.component == component }
    fun findPackage(pkg: String): AppEntry? = _apps.value.firstOrNull { it.packageName == pkg }

    fun launch(component: String): Boolean = runCatching {
        val cn = ComponentName.unflattenFromString(component) ?: return false
        launcherApps.startMainActivity(cn, Process.myUserHandle(), null, null)
        true
    }.getOrElse { false }

    fun launchPackage(pkg: String): Boolean = runCatching {
        val i = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrElse { false }

    /** Navigation-capable apps (anything that handles a geo: URI). */
    fun navigationApps(): List<AppEntry> {
        val handlers = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=petrol")), 0
        ).map { it.activityInfo.packageName }.toSet()
        return _apps.value.filter { it.packageName in handlers }.distinctBy { it.packageName }
    }

    private fun Drawable.toBitmap(size: Int): Bitmap {
        if (this is BitmapDrawable && bitmap != null) return Bitmap.createScaledBitmap(bitmap, size, size, true)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        setBounds(0, 0, size, size)
        draw(c)
        return bmp
    }
}

/** Plays alert tones, launches voice search, and changes volume with speed. */
class CarSystem(private val context: Context) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) }.getOrNull()
    private val _volume = MutableStateFlow(currentVolumeFraction())
    val volume: StateFlow<Float> = _volume.asStateFlow()
    private var lastAlert = 0L
    private var baseVolume: Int? = null
    private var lastBoost = 0

    fun alert(message: String) {
        val now = System.currentTimeMillis()
        if (now - lastAlert < 10_000) return // don't nag
        lastAlert = now
        tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun currentVolumeFraction(): Float {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    fun setVolumeFraction(f: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (f.coerceIn(0f, 1f) * max).toInt(), 0)
        baseVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC) - lastBoost
        _volume.value = currentVolumeFraction()
    }

    fun syncVolume() { _volume.value = currentVolumeFraction() }

    /**
     * Speed-adaptive volume: +1 step per 30 km/h above 40 km/h, max +4 steps,
     * relative to the level the driver last chose.
     */
    fun applySpeedVolume(kmh: Float) {
        val boost = SpeedVolume.stepsFor(kmh)
        if (boost == lastBoost) return
        val base = baseVolume ?: (audio.getStreamVolume(AudioManager.STREAM_MUSIC) - lastBoost).also { baseVolume = it }
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (base + boost).coerceIn(0, max), 0)
        lastBoost = boost
        _volume.value = currentVolumeFraction()
    }

    fun voiceAssistant(): Boolean = runCatching {
        context.startActivity(Intent(Intent.ACTION_VOICE_COMMAND).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.recoverCatching {
        context.startActivity(
            Intent(RecognizerIntent.ACTION_WEB_SEARCH).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ); true
    }.getOrElse { false }
}
