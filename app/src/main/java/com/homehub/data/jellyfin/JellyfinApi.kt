package com.homehub.data.jellyfin

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface JellyfinApi {

    @POST("Users/AuthenticateByName")
    suspend fun authenticate(@Body body: AuthRequest): AuthResult

    /** Bibliotheken des Nutzers (Filme, Serien, …). */
    @GET("Users/{userId}/Views")
    suspend fun getViews(@Path("userId") userId: String): ItemsResult

    /** Items innerhalb eines Ordners/einer Bibliothek (optional nach Genre/Suche gefiltert). */
    @GET("Users/{userId}/Items")
    suspend fun getItems(
        @Path("userId") userId: String,
        @Query("ParentId") parentId: String? = null,
        @Query("IncludeItemTypes") includeItemTypes: String? = null,
        @Query("Recursive") recursive: Boolean = false,
        @Query("SortBy") sortBy: String = "SortName",
        @Query("SortOrder") sortOrder: String = "Ascending",
        @Query("Genres") genres: String? = null,
        @Query("SearchTerm") searchTerm: String? = null,
        @Query("Limit") limit: Int? = null,
        @Query("Fields") fields: String = "Overview,PrimaryImageAspectRatio"
    ): ItemsResult

    /** Vorhandene Genres (für Genre-Reihen wie 'Top Action"). */
    @GET("Genres")
    suspend fun getGenres(
        @Query("userId") userId: String,
        @Query("includeItemTypes") includeItemTypes: String = "Movie,Series",
        @Query("recursive") recursive: Boolean = true,
        @Query("sortBy") sortBy: String = "SortName",
        @Query("limit") limit: Int = 30
    ): ItemsResult

    /** Zuletzt hinzugefügt (optional pro Bibliothek). */
    @GET("Users/{userId}/Items/Latest")
    suspend fun getLatest(
        @Path("userId") userId: String,
        @Query("ParentId") parentId: String? = null,
        @Query("Limit") limit: Int = 16,
        @Query("Fields") fields: String = "Overview,PrimaryImageAspectRatio"
    ): List<JellyItem>

    /** Weiterschauen. */
    @GET("Users/{userId}/Items/Resume")
    suspend fun getResume(
        @Path("userId") userId: String,
        @Query("Limit") limit: Int = 16,
        @Query("Fields") fields: String = "Overview"
    ): ItemsResult

    /** Detail eines Items. */
    @GET("Users/{userId}/Items/{itemId}")
    suspend fun getItem(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String
    ): JellyItem

    /** Staffeln einer Serie. */
    @GET("Shows/{seriesId}/Seasons")
    suspend fun getSeasons(
        @Path("seriesId") seriesId: String,
        @Query("userId") userId: String,
        @Query("Fields") fields: String = "Overview,PrimaryImageAspectRatio"
    ): ItemsResult

    /** Episoden einer Staffel. */
    @GET("Shows/{seriesId}/Episodes")
    suspend fun getEpisodes(
        @Path("seriesId") seriesId: String,
        @Query("userId") userId: String,
        @Query("seasonId") seasonId: String? = null,
        @Query("Fields") fields: String = "Overview,PrimaryImageAspectRatio"
    ): ItemsResult

    /** Nächste zu schauende Episoden (über alle Serien). */
    @GET("Shows/NextUp")
    suspend fun getNextUp(
        @Query("userId") userId: String,
        @Query("Limit") limit: Int = 16,
        @Query("Fields") fields: String = "Overview,PrimaryImageAspectRatio"
    ): ItemsResult

    // Wiedergabe-Reporting (aktualisiert 'Weiterschauen" serverseitig)
    @POST("Sessions/Playing")
    suspend fun reportStart(@Body body: PlaybackStartInfo): Response<Unit>

    @POST("Sessions/Playing/Progress")
    suspend fun reportProgress(@Body body: PlaybackProgressInfo): Response<Unit>

    @POST("Sessions/Playing/Stopped")
    suspend fun reportStopped(@Body body: PlaybackStopInfo): Response<Unit>

    @POST("Users/{userId}/FavoriteItems/{itemId}")
    suspend fun addFavorite(@Path("userId") userId: String, @Path("itemId") itemId: String): Response<Unit>

    @DELETE("Users/{userId}/FavoriteItems/{itemId}")
    suspend fun removeFavorite(@Path("userId") userId: String, @Path("itemId") itemId: String): Response<Unit>

    @POST("Users/{userId}/PlayedItems/{itemId}")
    suspend fun markPlayed(@Path("userId") userId: String, @Path("itemId") itemId: String): Response<Unit>
}
