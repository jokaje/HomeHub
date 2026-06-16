package com.homehub.playback

import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.common.images.WebImage
import android.net.Uri

/**
 * Wandelt unsere Audio-[MediaItem]s in Cast-Queue-Items um. Der Standardkonverter
 * verlangt einen MIME-Typ, den wir nicht setzen – hier liefern wir audio/mpeg
 * (Navidrome streamt standardmäßig MP3) plus Titel/Künstler und Cover.
 */
class AudioMediaItemConverter : MediaItemConverter {

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
        val info = mediaQueueItem.media
        val uri = info?.contentId ?: ""
        return MediaItem.Builder().setUri(uri).build()
    }

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val cfg = mediaItem.localConfiguration
        val uri = cfg?.uri?.toString() ?: ""
        val md = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            mediaItem.mediaMetadata.title?.let { putString(MediaMetadata.KEY_TITLE, it.toString()) }
            mediaItem.mediaMetadata.artist?.let { putString(MediaMetadata.KEY_ARTIST, it.toString()) }
            mediaItem.mediaMetadata.albumTitle?.let { putString(MediaMetadata.KEY_ALBUM_TITLE, it.toString()) }
            mediaItem.mediaMetadata.artworkUri?.let { addImage(WebImage(Uri.parse(it.toString()))) }
        }
        val info = MediaInfo.Builder(uri)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType("audio/mpeg")
            .setMetadata(md)
            .build()
        return MediaQueueItem.Builder(info).build()
    }
}
