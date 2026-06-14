package com.homehub.playback

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Hält eine MediaSession über den zentralen [MusicPlayer], damit die Musik im
 * Hintergrund weiterläuft und Benachrichtigungs-/Sperrbildschirm-Steuerung
 * funktioniert. Der eigentliche ExoPlayer gehört dem MusicPlayer.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        MusicPlayer.init(this)
        MusicPlayer.player?.let { player ->
            session = MediaSession.Builder(this, player).build()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        // Nur die Session freigeben – der Player gehört dem Singleton MusicPlayer.
        session?.release()
        session = null
        super.onDestroy()
    }
}
