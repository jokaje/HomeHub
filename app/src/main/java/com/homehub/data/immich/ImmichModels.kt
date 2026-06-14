package com.homehub.data.immich

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TimeBucket(
    val timeBucket: String, // z.B. "2025-08-01T00:00:00.000Z"
    val count: Int
)

@Serializable
data class Asset(
    val id: String,
    val type: String = "IMAGE", // IMAGE | VIDEO
    val originalFileName: String = "",
    val fileCreatedAt: String = "",
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val isTrashed: Boolean = false,
    // Neuere Immich-Versionen nutzen "visibility" statt "isArchived":
    // timeline | archive | hidden | locked
    val visibility: String? = null,
    // Immich liefert die Beschreibung je nach Version/Endpunkt auf oberster Ebene
    // ODER in exifInfo – beides wird für die #locked-Erkennung berücksichtigt.
    val description: String? = null,
    val duration: String? = null,
    val exifInfo: ExifInfo? = null,
    val people: List<Person> = emptyList()
) {
    val isVideo: Boolean get() = type.equals("VIDEO", true)
    val archivedEffective: Boolean get() = isArchived || visibility.equals("archive", true)

    /** Effektive Beschreibung aus allen möglichen Feldern. */
    val effectiveDescription: String?
        get() = description?.takeIf { it.isNotBlank() }
            ?: exifInfo?.description?.takeIf { it.isNotBlank() }
            ?: exifInfo?.imageDescription

    /** HomeHub-eigener gesperrter Ordner: Beschreibung mit "#locked".
     *  Solche Assets werden überall ausgeblendet außer im Gesperrt-Bereich. */
    val lockedCustom: Boolean
        get() = effectiveDescription?.contains("#locked", ignoreCase = true) == true
}

@Serializable
data class ExifInfo(
    val make: String? = null,
    val model: String? = null,
    val city: String? = null,
    val country: String? = null,
    val state: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val fNumber: Double? = null,
    val iso: Int? = null,
    val focalLength: Double? = null,
    val exposureTime: String? = null,
    val lensModel: String? = null,
    val description: String? = null,
    val imageDescription: String? = null,
    val dateTimeOriginal: String? = null,
    val fileSizeInByte: Long? = null,
    val exifImageWidth: Int? = null,
    val exifImageHeight: Int? = null
)

@Serializable
data class Album(
    val id: String,
    val albumName: String = "",
    val assetCount: Int = 0,
    val albumThumbnailAssetId: String? = null,
    val shared: Boolean = false,
    val assets: List<Asset> = emptyList()
)

@Serializable
data class Person(
    val id: String,
    val name: String = "",
    val birthDate: String? = null,
    val isHidden: Boolean = false
)

@Serializable
data class PeopleResponse(
    val people: List<Person> = emptyList(),
    val total: Int = 0
)

@Serializable
data class SearchResponse(val assets: SearchAssets = SearchAssets())

@Serializable
data class SearchAssets(
    val items: List<Asset> = emptyList(),
    val total: Int = 0,
    val nextPage: String? = null
)

@Serializable
data class MapMarker(
    val id: String,
    val lat: Double,
    val lon: Double,
    val city: String? = null,
    val country: String? = null
)

@Serializable
data class SharedLink(
    val id: String,
    val key: String,
    val type: String,
    val expiresAt: String? = null
)

@Serializable
data class ServerAbout(
    val version: String = "",
    @SerialName("versionUrl") val versionUrl: String? = null
)

@Serializable
data class BulkIds(val ids: List<String>, val force: Boolean? = null)

@Serializable
data class UpdateAssetRequest(
    val isFavorite: Boolean? = null,
    val isArchived: Boolean? = null,
    // Neuere Immich-Versionen archivieren über visibility ("archive"/"timeline")
    val visibility: String? = null,
    val description: String? = null
)

@Serializable
data class SmartSearchRequest(
    val query: String,
    val page: Int = 1,
    val size: Int = 100,
    val withExif: Boolean = true
)

@Serializable
data class MetadataSearchRequest(
    val isFavorite: Boolean? = null,
    val isArchived: Boolean? = null,
    val isTrashed: Boolean? = null,
    // WICHTIG: Ohne "withArchived: true" blendet die Immich-Suche archivierte
    // Assets KOMPLETT aus – egal welcher visibility-Filter gesetzt ist.
    // "visibility" filtert zusätzlich gezielt (archive/timeline/locked).
    val withArchived: Boolean? = null,
    val visibility: String? = null,
    val withDeleted: Boolean? = null,
    val personIds: List<String>? = null,
    val page: Int = 1,
    val size: Int = 200,
    val order: String = "desc",
    val withExif: Boolean = true
)

@Serializable
data class CreateSharedLinkRequest(
    val type: String, // "INDIVIDUAL" für Assets, "ALBUM" für Alben
    val assetIds: List<String>? = null,
    val albumId: String? = null,
    val allowDownload: Boolean = true,
    val showMetadata: Boolean = true
)

@Serializable
data class UploadCheckItem(val id: String, val checksum: String)

@Serializable
data class UploadCheckRequest(val assets: List<UploadCheckItem>)

@Serializable
data class UploadCheckResult(val id: String, val action: String, val reason: String? = null)

@Serializable
data class UploadCheckResponse(val results: List<UploadCheckResult> = emptyList())

@Serializable
data class UploadResponse(val id: String, val status: String = "")

@Serializable
data class UpdatePersonRequest(val name: String)

@Serializable
data class MemoryData(val year: Int? = null)

@Serializable
data class Memory(
    val id: String = "",
    val type: String = "",
    val data: MemoryData? = null,
    val assets: List<Asset> = emptyList()
)
