package com.homehub.playback

import android.content.Intent
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.android.gms.cast.framework.CastContext

/**
 * Besitzt den lokalen ExoPlayer und – falls Google Play Services verfügbar sind –
 * zusätzlich einen [CastPlayer]. Sobald eine Cast-Sitzung (Chromecast) verbunden
 * wird, übernimmt der CastPlayer; danach wieder der lokale Player.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private var castPlayer: CastPlayer? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        exoPlayer = player
        session = MediaSession.Builder(this, player).build()

        // Cast einrichten (nur wenn Play Services vorhanden – sonst überspringen)
        runCatching {
            val castContext = CastContext.getSharedInstance(this)
            val cp = CastPlayer(castContext, AudioMediaItemConverter())
            cp.setSessionAvailabilityListener(object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() { switchTo(cp) }
                override fun onCastSessionUnavailable() { exoPlayer?.let { switchTo(it) } }
            })
            castPlayer = cp
            if (cp.isCastSessionAvailable) switchTo(cp)
        }
    }

    /** Überträgt Warteschlange + Position auf den neuen Player und aktiviert ihn. */
    private fun switchTo(newPlayer: Player) {
        val current = session?.player ?: return
        if (current === newPlayer) return
        val items = (0 until current.mediaItemCount).map { current.getMediaItemAt(it) }
        val index = current.currentMediaItemIndex.coerceAtLeast(0)
        val pos = current.currentPosition.coerceAtLeast(0)
        val playWhenReady = current.playWhenReady
        if (items.isNotEmpty()) {
            newPlayer.setMediaItems(items, index, pos)
            newPlayer.prepare()
            newPlayer.playWhenReady = playWhenReady
        }
        current.stop()
        session?.player = newPlayer
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
        castPlayer = null
        session?.run {
            player.release()
            release()
        }
        session = null
        exoPlayer = null
        super.onDestroy()
    }
}
