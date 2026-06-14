package com.homehub.data.local

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.security.MessageDigest

/** Ein lokales Foto/Video aus dem MediaStore. */
data class LocalItem(
    val mediaId: String,
    val uri: Uri,
    val name: String,
    val createdAtMs: Long,
    val isVideo: Boolean
)

object LocalMedia {

    /**
     * Liest Fotos (und optional Videos) aus den gewählten Geräte-Ordnern.
     * [buckets] leer = alle Ordner. [sinceSec] > 0 = nur neuere Aufnahmen.
     */
    fun query(
        context: Context,
        buckets: Set<String>,
        includeVideos: Boolean,
        sinceSec: Long = 0
    ): List<LocalItem> {
        val items = mutableListOf<LocalItem>()

        fun scan(collection: Uri, video: Boolean) {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_ADDED,
                "bucket_id"
            )
            var selection = "${MediaStore.MediaColumns.DATE_ADDED} > ?"
            val args = mutableListOf(sinceSec.toString())
            if (buckets.isNotEmpty()) {
                selection += " AND bucket_id IN (${buckets.joinToString(",") { "?" }})"
                args.addAll(buckets)
            }
            runCatching {
                context.contentResolver.query(
                    collection, projection, selection, args.toTypedArray(),
                    "${MediaStore.MediaColumns.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        items += LocalItem(
                            mediaId = "${if (video) "v" else "i"}-$id",
                            uri = ContentUris.withAppendedId(collection, id),
                            name = cursor.getString(nameCol) ?: "media_$id",
                            createdAtMs = cursor.getLong(dateCol) * 1000,
                            isVideo = video
                        )
                    }
                }
            } // ohne Medien-Berechtigung: leise leer lassen
        }

        scan(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, video = false)
        if (includeVideos) scan(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, video = true)
        return items.sortedByDescending { it.createdAtMs }
    }

    /**
     * SHA-1-Prüfsumme einer Datei (hex, klein geschrieben) – das Format,
     * mit dem Immichs bulk-upload-check Duplikate erkennt.
     */
    fun sha1(resolver: ContentResolver, uri: Uri): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-1")
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        } ?: return null
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
