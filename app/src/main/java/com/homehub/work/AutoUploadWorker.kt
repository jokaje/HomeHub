package com.homehub.work

import android.content.Context
import android.provider.MediaStore
import android.provider.Settings
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.homehub.core.ServiceLocator
import com.homehub.data.settings.ServiceId
import java.util.concurrent.TimeUnit

/**
 * Sichert Fotos (und optional Videos) automatisch zu Immich.
 * Läuft periodisch (alle 6 Stunden) – nur bei WLAN-Verbindung ("unmetered").
 * Duplikate werden zuverlässig per SHA-1-Prüfsumme + Immich
 * bulk-upload-check erkannt und übersprungen.
 */
class AutoUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = ServiceLocator.settings
        if (!settings.autoUploadEnabled) return Result.success()
        if (!settings.get(ServiceId.IMMICH).isConfigured) return Result.success()

        val resolver = applicationContext.contentResolver
        val deviceId = Settings.Secure.getString(resolver, Settings.Secure.ANDROID_ID) ?: "homehub"
        val nowSeconds = System.currentTimeMillis() / 1000

        // Medien aus den gewählten Ordnern lesen (gemeinsame Logik mit der Foto-Übersicht).
        // Kein Zeitstempel-Filter mehr nötig: die SHA-Prüfung unten ist die
        // zuverlässige Wahrheit darüber, was schon auf dem Server liegt.
        val items = com.homehub.data.local.LocalMedia.query(
            applicationContext,
            buckets = settings.autoUploadBuckets,
            includeVideos = settings.autoUploadVideos
        )

        // ---- Duplikatprüfung per SHA-1 (Immich bulk-upload-check) ----
        // Verhindert doppelte Uploads zuverlässig, auch wenn das Bild schon
        // über einen anderen Weg (Web, anderes Gerät) auf dem Server liegt.
        val candidates = items.filter { it.mediaId !in settings.uploadedMediaIds }
        val withSha = candidates.mapNotNull { item ->
            val sha = settings.cachedSha(item.mediaId)
                ?: com.homehub.data.local.LocalMedia.sha1(resolver, item.uri)
                    ?.also { settings.cacheSha(item.mediaId, it) }
            sha?.let { item to it }
        }

        val duplicates = mutableSetOf<String>()
        withSha.chunked(100).forEach { batch ->
            runCatching {
                com.homehub.core.ServiceLocator.immich.api.bulkUploadCheck(
                    com.homehub.data.immich.UploadCheckRequest(
                        batch.map { (item, sha) ->
                            com.homehub.data.immich.UploadCheckItem(item.mediaId, sha)
                        }
                    )
                )
            }.onSuccess { resp ->
                resp.results.filter { it.action.equals("reject", true) }
                    .forEach { duplicates += it.id }
            }
        }
        // Bereits vorhandene Medien merken, damit sie nie wieder geprüft werden
        settings.markUploaded(duplicates)

        val toUpload = withSha.map { it.first }.filter { it.mediaId !in duplicates }

        var failures = 0
        for (item in toUpload) {
            val result = ServiceLocator.immich.uploadFromUri(
                resolver = resolver,
                uri = item.uri,
                fileName = item.name,
                createdAt = item.createdAtMs,
                deviceId = deviceId
            )
            if (result.isSuccess) settings.markUploaded(listOf(item.mediaId))
            else failures++
        }

        return if (failures == 0) {
            settings.lastUploadTimestamp = nowSeconds * 1000
            Result.success()
        } else {
            // Beim nächsten Lauf erneut versuchen, Zeitstempel nicht vorziehen
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "homehub_auto_upload"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<AutoUploadWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
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
