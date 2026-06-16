package com.homehub.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.homehub.core.ServiceLocator
import com.homehub.data.network.Http
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.RandomAccessFile

/**
 * Lädt ein Jellyfin-Video robust herunter:
 * - läuft als Foreground-Service (überlebt das Schließen der App),
 * - zeigt eine Benachrichtigung mit Fortschritt,
 * - setzt unterbrochene Downloads per HTTP-Range fort.
 */
class JellyfinDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun doWork(): Result {
        val itemId = inputData.getString(KEY_ID) ?: return Result.failure()
        val itemJson = inputData.getString(KEY_ITEM) ?: return Result.failure()
        val name = inputData.getString(KEY_NAME) ?: "Download"

        val repo = ServiceLocator.jellyfin
        val downloads = ServiceLocator.jellyfinDownloads
        repo.ensureAuth()
        val url = repo.downloadFileUrl(itemId) ?: return Result.retry()

        setForeground(foregroundInfo(name, 0))

        val target = downloads.videoTarget(itemId)
        val part = downloads.partTarget(itemId)

        val ok = runCatching {
            var existing = if (part.exists()) part.length() else 0L
            val reqBuilder = Request.Builder().url(url)
            if (existing > 0) reqBuilder.header("Range", "bytes=$existing-")
            Http.client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching false
                // Server ignoriert Range (200 statt 206) -> von vorne
                if (existing > 0 && resp.code == 200) { part.delete(); existing = 0 }
                val body = resp.body ?: return@runCatching false
                val totalRemaining = body.contentLength()
                val grandTotal = if (totalRemaining > 0) existing + totalRemaining else -1L

                RandomAccessFile(part, "rw").use { raf ->
                    raf.seek(existing)
                    body.byteStream().use { input ->
                        val buf = ByteArray(128 * 1024)
                        var downloaded = existing
                        var lastReport = 0L
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            if (isStopped) return@runCatching false
                            raf.write(buf, 0, read)
                            downloaded += read
                            if (grandTotal > 0) {
                                val frac = (downloaded.toFloat() / grandTotal).coerceIn(0f, 1f)
                                val now = System.currentTimeMillis()
                                if (now - lastReport > 500) {
                                    lastReport = now
                                    setProgress(workDataOf(KEY_PROGRESS to frac))
                                    runCatching { setForeground(foregroundInfo(name, (frac * 100).toInt())) }
                                }
                            }
                        }
                    }
                }
                if (part.renameTo(target)) true else { target.delete(); part.copyTo(target, overwrite = true); part.delete(); true }
            }
        }.getOrElse { false }

        if (!ok) return Result.retry()

        // Cover + Metadaten speichern
        runCatching {
            val item = json.decodeFromString<com.homehub.data.jellyfin.JellyItem>(itemJson)
            downloads.downloadPoster(item)
            downloads.markDownloaded(item)
        }
        return Result.success()
    }

    private fun foregroundInfo(name: String, percent: Int): ForegroundInfo {
        val ctx = applicationContext
        val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW)
            mgr.createNotificationChannel(channel)
        }
        val notification = androidx.core.app.NotificationCompat.Builder(ctx, CHANNEL)
            .setContentTitle("Lädt: $name")
            .setContentText("$percent %")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, percent, percent <= 0)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, notification)
        }
    }

    companion object {
        const val KEY_ID = "itemId"
        const val KEY_ITEM = "item"
        const val KEY_NAME = "name"
        const val KEY_PROGRESS = "p"
        const val TAG = "jellyfin_download"
        private const val CHANNEL = "homehub_downloads"
        private const val NOTIF_ID = 4711
        fun idTag(itemId: String) = "id:$itemId"
    }
}
