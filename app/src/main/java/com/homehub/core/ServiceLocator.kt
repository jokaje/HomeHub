package com.homehub.core

import android.annotation.SuppressLint
import android.content.Context
import com.homehub.data.comfy.ComfyClient
import com.homehub.data.hermes.HermesClient
import com.homehub.data.immich.ImmichRepository
import com.homehub.data.jellyfin.JellyfinDownloadRepository
import com.homehub.data.jellyfin.JellyfinRepository
import com.homehub.data.navidrome.MusicDownloadRepository
import com.homehub.data.navidrome.NavidromeRepository
import com.homehub.data.network.UrlResolver
import com.homehub.data.settings.SettingsRepository
import com.homehub.playback.MusicPlayer

@SuppressLint("StaticFieldLeak")
object ServiceLocator {
    lateinit var appContext: Context; private set
    lateinit var settings: SettingsRepository; private set
    lateinit var urls: UrlResolver; private set
    lateinit var immich: ImmichRepository; private set
    lateinit var hermes: HermesClient; private set
    lateinit var comfy: ComfyClient; private set
    lateinit var navidrome: NavidromeRepository; private set
    lateinit var musicDownloads: MusicDownloadRepository; private set
    lateinit var jellyfin: JellyfinRepository; private set
    lateinit var jellyfinDownloads: JellyfinDownloadRepository; private set

    fun init(context: Context) {
        appContext = context.applicationContext
        settings = SettingsRepository(appContext)
        urls = UrlResolver(settings)
        immich = ImmichRepository(settings, urls)
        hermes = HermesClient(settings, urls)
        comfy = ComfyClient(settings, urls)
        navidrome = NavidromeRepository(settings, urls)
        musicDownloads = MusicDownloadRepository(appContext, navidrome)
        jellyfin = JellyfinRepository(settings, urls)
        jellyfinDownloads = JellyfinDownloadRepository(appContext, jellyfin)
        // ExoPlayer auf dem Main-Thread mit App-Kontext erzeugen
        MusicPlayer.init(appContext)
    }
}
