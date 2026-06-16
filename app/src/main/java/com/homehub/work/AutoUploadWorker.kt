package com.homehub.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.homehub.core.ServiceLocator
import com.homehub.data.settings.ServiceId
import java.util.concurrent.TimeUnit

/**
 * Sichert Fotos (und optional Videos) zu Immich.
 * - Laeuft als Foreground-Worker mit Benachrichtigung + Fortschritt (auch im Hintergrund).
 * - Phase 1: SHA-1 berechnen und per Immich bulk-upload-check Duplikate erkennen.
 * - Phase 2: neue Medien hochladen.
 * Bereits vorhandene Medien werden dauerhaft gemerkt (SHA-Cache + uploadedMediaIds).
 */
class AutoUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo("Backup wird vorbereitet", 0, 0)

    override suspend fun doWork(): Result {
        val settings = ServiceLocator.settings
        if (!settings.get(ServiceId.IMMICH).isConfigured) return Result.success()

        val resolver = applicationContext.contentResolver
        val deviceId = Settings.Secure.getString(resolver, Settings.Secure.ANDROID_ID) ?: "homehub"
        val nowSeconds = System.currentTimeMillis() / 1000

        if (!hasMediaPermission()) {
            summary("Backup nicht moeglich", "HomeHub hat keinen Zugriff auf deine Fotos. Bitte in den App-Einstellungen 'Fotos und Videos' erlauben.")
            return Result.success()
        }

        setForeground(foregroundInfo("Medien werden gesucht", 0, 0))

        val items = com.homehub.data.local.LocalMedia.query(
            applicationContext,
            buckets = settings.autoUploadBuckets,
            includeVideos = settings.autoUploadVideos
        )
        val candidates = items.filter { it.mediaId !in settings.uploadedMediaIds }

        if (candidates.isEmpty()) {
            summary("Backup abgeschlossen", "Keine neuen Medien zu sichern (${items.size} geprueft).")
            settings.lastUploadTimestamp = nowSeconds * 1000
            return Result.success()
        }

        // ---- Phase 1: SHA-1 + Duplikatpruefung mit Fortschritt ----
        val withSha = ArrayList<Pair<com.homehub.data.local.LocalItem, String>>(candidates.size)
        candidates.forEachIndexed { i, item ->
            if (isStopped) return Result.retry()
            val sha = settings.cachedSha(item.mediaId)
                ?: com.homehub.data.local.LocalMedia.sha1(resolver, item.uri)
                    ?.also { settings.cacheSha(item.mediaId, it) }
            if (sha != null) withSha.add(item to sha)
            if (i % 5 == 0) setForeground(foregroundInfo("Pruefe Medien (Duplikate)", i + 1, candidates.size))
        }

        val duplicates = mutableSetOf<String>()
        withSha.chunked(100).forEach { batch ->
            if (isStopped) return Result.retry()
            runCatching {
                ServiceLocator.immich.api.bulkUploadCheck(
                    com.homehub.data.immich.UploadCheckRequest(
                        batch.map { (item, sha) -> com.homehub.data.immich.UploadCheckItem(item.mediaId, sha) }
                    )
                )
            }.onSuccess { resp ->
                resp.results.filter { it.action.equals("reject", true) }.forEach { duplicates += it.id }
            }
        }
        settings.markUploaded(duplicates) // dauerhaft als 'vorhanden' merken

        val toUpload = withSha.map { it.first }.filter { it.mediaId !in duplicates }

        if (toUpload.isEmpty()) {
            summary("Backup abgeschlossen", "Alle ${candidates.size} Medien waren bereits gesichert.")
            settings.lastUploadTimestamp = nowSeconds * 1000
            return Result.success()
        }

        // ---- Phase 2: Upload mit Fortschritt ----
        var ok = 0
        var failures = 0
        toUpload.forEachIndexed { i, item ->
            if (isStopped) return Result.retry()
            setForeground(foregroundInfo("Sichere Medien", i + 1, toUpload.size))
            val result = ServiceLocator.immich.uploadFromUri(
                resolver = resolver, uri = item.uri, fileName = item.name,
                createdAt = item.createdAtMs, deviceId = deviceId
            )
            if (result.isSuccess) { settings.markUploaded(listOf(item.mediaId)); ok++ } else failures++
        }

        return if (failures == 0) {
            settings.lastUploadTimestamp = nowSeconds * 1000
            summary("Backup abgeschlossen", "$ok Medien gesichert.")
            Result.success()
        } else {
            summary("Backup teilweise fehlgeschlagen", "$ok gesichert, $failures fehlgeschlagen - wird spaeter erneut versucht.")
            Result.retry()
        }
    }

    private fun hasMediaPermission(): Boolean {
        val perms = if (Build.VERSION.SDK_INT >= 33)
            listOf(android.Manifest.permission.READ_MEDIA_IMAGES, android.Manifest.permission.READ_MEDIA_VIDEO)
        else listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        return perms.any {
            androidx.core.content.ContextCompat.checkSelfPermission(applicationContext, it) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(NotificationChannel(CHANNEL, "Foto-Backup", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun foregroundInfo(text: String, current: Int, total: Int): ForegroundInfo {
        ensureChannel()
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setContentTitle("Immich-Backup")
            .setContentText(if (total > 0) "$text ($current/$total)" else text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setProgress(total.coerceAtLeast(0), current.coerceAtMost(total), total == 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIF_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, builder.build())
        }
    }

    private fun summary(title: String, text: String) {
        ensureChannel()
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(applicationContext).notify(SUMMARY_ID, n) }
    }

    companion object {
        private const val WORK_NAME = "homehub_auto_upload"
        private const val CHANNEL = "homehub_backup"
        private const val NOTIF_ID = 5120
        private const val SUMMARY_ID = 5121

        fun schedule(context: Context) {
            val allowMobile = ServiceLocator.settings.allowMobileData
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (allowMobile) NetworkType.CONNECTED else NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<AutoUploadWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
            )
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<AutoUploadWorker>().build()
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
