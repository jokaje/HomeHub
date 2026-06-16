package com.homehub.data.navidrome

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Subsonic-API (von Navidrome implementiert). Authentifizierungs-Parameter
 * (u, t, s, v, c, f) hängt ein Interceptor automatisch an jede Anfrage an.
 */
interface SubsonicApi {

    @GET("rest/ping.view")
    suspend fun ping(): SubsonicWrapper

    @GET("rest/getAlbumList2.view")
    suspend fun getAlbumList2(
        @Query("type") type: String = "newest",
        @Query("size") size: Int = 50,
        @Query("offset") offset: Int = 0
    ): SubsonicWrapper

    @GET("rest/getAlbum.view")
    suspend fun getAlbum(@Query("id") id: String): SubsonicWrapper

    @GET("rest/getArtists.view")
    suspend fun getArtists(): SubsonicWrapper

    @GET("rest/getArtist.view")
    suspend fun getArtist(@Query("id") id: String): SubsonicWrapper

    @GET("rest/getPlaylists.view")
    suspend fun getPlaylists(): SubsonicWrapper

    @GET("rest/getPlaylist.view")
    suspend fun getPlaylist(@Query("id") id: String): SubsonicWrapper

    @GET("rest/search3.view")
    suspend fun search3(
        @Query("query") query: String,
        @Query("artistCount") artistCount: Int = 20,
        @Query("albumCount") albumCount: Int = 20,
        @Query("songCount") songCount: Int = 40
    ): SubsonicWrapper

    @GET("rest/getStarred2.view")
    suspend fun getStarred2(): SubsonicWrapper

    @GET("rest/getRandomSongs.view")
    suspend fun getRandomSongs(@Query("size") size: Int = 50): SubsonicWrapper

    @GET("rest/createPlaylist.view")
    suspend fun createPlaylist(
        @Query("name") name: String,
        @Query("songId") songIds: List<String>? = null
    ): SubsonicWrapper

    @GET("rest/updatePlaylist.view")
    suspend fun updatePlaylist(
        @Query("playlistId") playlistId: String,
        @Query("songIdToAdd") songIdToAdd: List<String>? = null,
        @Query("songIndexToRemove") songIndexToRemove: List<Int>? = null
    ): SubsonicWrapper

    @GET("rest/deletePlaylist.view")
    suspend fun deletePlaylist(@Query("id") id: String): SubsonicWrapper

    @GET("rest/star.view")
    suspend fun star(@Query("id") id: String): SubsonicWrapper

    @GET("rest/unstar.view")
    suspend fun unstar(@Query("id") id: String): SubsonicWrapper
}
