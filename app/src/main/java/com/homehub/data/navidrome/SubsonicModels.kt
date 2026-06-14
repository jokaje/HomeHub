package com.homehub.data.navidrome

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Navidrome/Subsonic verpacken jede Antwort in {"subsonic-response": {...}}. */
@Serializable
data class SubsonicWrapper(
    @SerialName("subsonic-response") val response: SubsonicResponse = SubsonicResponse()
)

@Serializable
data class SubsonicResponse(
    val status: String = "",
    val version: String = "",
    val error: SubsonicError? = null,
    val albumList2: AlbumList2? = null,
    val album: Album? = null,
    val artists: ArtistsRoot? = null,
    val artist: Artist? = null,
    val playlists: Playlists? = null,
    val playlist: Playlist? = null,
    val searchResult3: SearchResult3? = null,
    val starred2: Starred2? = null
)

@Serializable
data class SubsonicError(val code: Int = 0, val message: String = "")

@Serializable
data class AlbumList2(val album: List<Album> = emptyList())

@Serializable
data class Album(
    val id: String = "",
    val name: String = "",
    val artist: String = "",
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val duration: Int = 0,
    val year: Int? = null,
    val genre: String? = null,
    val starred: String? = null,
    @SerialName("song") val songs: List<Song> = emptyList()
)

@Serializable
data class Song(
    val id: String = "",
    val parent: String? = null,
    val title: String = "",
    val album: String = "",
    val artist: String = "",
    val albumId: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val duration: Int = 0,
    val track: Int? = null,
    val year: Int? = null,
    val contentType: String? = null,
    val starred: String? = null
)

@Serializable
data class ArtistsRoot(val index: List<ArtistIndex> = emptyList())

@Serializable
data class ArtistIndex(val name: String = "", val artist: List<Artist> = emptyList())

@Serializable
data class Artist(
    val id: String = "",
    val name: String = "",
    val coverArt: String? = null,
    val albumCount: Int = 0,
    val starred: String? = null,
    @SerialName("album") val albums: List<Album> = emptyList()
)

@Serializable
data class Playlists(val playlist: List<Playlist> = emptyList())

@Serializable
data class Playlist(
    val id: String = "",
    val name: String = "",
    val songCount: Int = 0,
    val duration: Int = 0,
    val coverArt: String? = null,
    val comment: String? = null,
    @SerialName("entry") val entries: List<Song> = emptyList()
)

@Serializable
data class SearchResult3(
    val artist: List<Artist> = emptyList(),
    val album: List<Album> = emptyList(),
    val song: List<Song> = emptyList()
)

@Serializable
data class Starred2(
    val artist: List<Artist> = emptyList(),
    val album: List<Album> = emptyList(),
    val song: List<Song> = emptyList()
)
