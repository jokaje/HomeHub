package com.homehub.data.jellyfin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthRequest(
    @SerialName("Username") val username: String,
    @SerialName("Pw") val pw: String
)

@Serializable
data class AuthResult(
    @SerialName("AccessToken") val accessToken: String = "",
    @SerialName("User") val user: JellyUser? = null
)

@Serializable
data class JellyUser(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = ""
)

@Serializable
data class ItemsResult(
    @SerialName("Items") val items: List<JellyItem> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int = 0
)

/** Universelles Jellyfin-Item: Bibliothek (View), Film, Serie, Staffel, Episode. */
@Serializable
data class JellyItem(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("Type") val type: String = "",
    @SerialName("CollectionType") val collectionType: String? = null,
    @SerialName("Overview") val overview: String? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("SeriesId") val seriesId: String? = null,
    @SerialName("MediaType") val mediaType: String? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String>? = null,
    @SerialName("BackdropImageTags") val backdropImageTags: List<String>? = null,
    @SerialName("UserData") val userData: UserData? = null
) {
    val isFolderLike: Boolean get() = type == "Series" || type == "Season" || collectionType != null
    val isPlayable: Boolean get() = type == "Movie" || type == "Episode" || mediaType == "Video"
    val primaryTag: String? get() = imageTags?.get("Primary")
    val runtimeMinutes: Int? get() = runTimeTicks?.let { (it / 600_000_000L).toInt() }
}

@Serializable
data class UserData(
    @SerialName("PlaybackPositionTicks") val playbackPositionTicks: Long = 0L,
    @SerialName("Played") val played: Boolean = false,
    @SerialName("IsFavorite") val isFavorite: Boolean = false,
    @SerialName("PlayedPercentage") val playedPercentage: Double? = null
)

@Serializable
data class PlaybackStartInfo(
    @SerialName("ItemId") val itemId: String,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null,
    @SerialName("PositionTicks") val positionTicks: Long = 0L
)

@Serializable
data class PlaybackProgressInfo(
    @SerialName("ItemId") val itemId: String,
    @SerialName("PositionTicks") val positionTicks: Long,
    @SerialName("IsPaused") val isPaused: Boolean = false
)

@Serializable
data class PlaybackStopInfo(
    @SerialName("ItemId") val itemId: String,
    @SerialName("PositionTicks") val positionTicks: Long
)
