package com.vizionconcepts.drive.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Registered in the manifest; its only job is to grant access to media sessions. */
class MediaListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        MediaHub.instance?.refresh()
    }
}

data class NowPlaying(
    val hasAccess: Boolean = false,
    val active: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val appPackage: String = "",
    val playing: Boolean = false,
    val art: Bitmap? = null,
    val durationMs: Long = 0,
    val positionMs: Long = 0,
)

class MediaHub(private val context: Context) {

    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    private val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val component = ComponentName(context, MediaListenerService::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private var registered = false

    init { instance = this }

    fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun accessIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        pick(list.orEmpty())
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = refresh()
    }

    fun refresh() {
        if (!hasAccess()) {
            _state.value = NowPlaying(hasAccess = false); return
        }
        runCatching {
            if (!registered) {
                msm.addOnActiveSessionsChangedListener(sessionsListener, component, main)
                registered = true
            }
            pick(msm.getActiveSessions(component))
        }.onFailure { _state.value = NowPlaying(hasAccess = false) }
    }

    private fun pick(list: List<MediaController>) {
        val best = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(controllerCallback)
            controller = best
            best?.registerCallback(controllerCallback, main)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        if (c == null) {
            _state.value = NowPlaying(hasAccess = true); return
        }
        val md = c.metadata
        val ps = c.playbackState
        _state.value = NowPlaying(
            hasAccess = true,
            active = true,
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artist = (md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty(),
            appPackage = c.packageName,
            playing = ps?.state == PlaybackState.STATE_PLAYING,
            art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART),
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0,
            positionMs = ps?.position ?: 0,
        )
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }
    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }

    companion object {
        @Volatile var instance: MediaHub? = null
            private set
    }
}
