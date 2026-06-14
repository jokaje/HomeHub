package com.homehub.data.immich

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Immich REST-API (getestet gegen Immich ~v1.120+).
 * Auth: Header "x-api-key: <API-Key>" (wird per Interceptor gesetzt).
 *
 * Hinweis: Immich entwickelt die API aktiv weiter. Sollte ein Endpunkt bei dir
 * 404 liefern, prüfe die API-Doku deiner Version unter {server}/api/docs
 * und passe nur die Pfade hier an – der Rest der App bleibt unverändert.
 */
interface ImmichApi {

    @GET("api/server/about")
    suspend fun serverAbout(): ServerAbout

    // ---- Timeline ----
    // Hinweis: Nur gesetzte Parameter werden gesendet (null = weggelassen),
    // da neuere Immich-Versionen unbekannte Parameter mit 400 ablehnen.
    @GET("api/timeline/buckets")
    suspend fun timelineBuckets(
        @Query("size") size: String = "MONTH",
        @Query("visibility") visibility: String? = null,
        @Query("isTrashed") isTrashed: Boolean? = null
    ): List<TimeBucket>

    @GET("api/timeline/bucket")
    suspend fun timelineBucket(
        @Query("timeBucket") timeBucket: String,
        @Query("size") size: String = "MONTH",
        @Query("visibility") visibility: String? = null,
        @Query("isTrashed") isTrashed: Boolean? = null
    ): List<Asset>

    // ---- Assets ----
    @GET("api/assets/{id}")
    suspend fun asset(@Path("id") id: String): Asset

    @PUT("api/assets/{id}")
    suspend fun updateAsset(@Path("id") id: String, @Body body: UpdateAssetRequest): Asset

    @HTTP(method = "DELETE", path = "api/assets", hasBody = true)
    suspend fun deleteAssets(@Body body: BulkIds)

    // ---- Alben ----
    @GET("api/albums")
    suspend fun albums(@Query("shared") shared: Boolean? = null): List<Album>

    @GET("api/albums/{id}")
    suspend fun album(@Path("id") id: String): Album

    // ---- Suche ----
    @POST("api/search/smart")
    suspend fun smartSearch(@Body body: SmartSearchRequest): SearchResponse

    @POST("api/search/metadata")
    suspend fun metadataSearch(@Body body: MetadataSearchRequest): SearchResponse

    // ---- Personen ----
    @GET("api/people")
    suspend fun people(@Query("withHidden") withHidden: Boolean = false): PeopleResponse

    @PUT("api/people/{id}")
    suspend fun updatePerson(@Path("id") id: String, @Body body: UpdatePersonRequest): Person

    // ---- Erinnerungen ("An diesem Tag") ----
    @GET("api/memories")
    suspend fun memories(): List<Memory>

    // ---- Karte ----
    @GET("api/map/markers")
    suspend fun mapMarkers(
        @Query("isArchived") isArchived: Boolean = false,
        @Query("isFavorite") isFavorite: Boolean? = null
    ): List<MapMarker>

    // ---- Papierkorb ----
    @POST("api/trash/restore/assets")
    suspend fun restoreAssets(@Body body: BulkIds)

    @POST("api/trash/empty")
    suspend fun emptyTrash()

    // ---- Teilen ----
    @POST("api/shared-links")
    suspend fun createSharedLink(@Body body: CreateSharedLinkRequest): SharedLink

    // ---- Upload ----
    @POST("api/assets/bulk-upload-check")
    suspend fun bulkUploadCheck(@Body body: UploadCheckRequest): UploadCheckResponse

    @Multipart
    @POST("api/assets")
    suspend fun upload(
        @Part assetData: MultipartBody.Part,
        @Part("deviceAssetId") deviceAssetId: okhttp3.RequestBody,
        @Part("deviceId") deviceId: okhttp3.RequestBody,
        @Part("fileCreatedAt") fileCreatedAt: okhttp3.RequestBody,
        @Part("fileModifiedAt") fileModifiedAt: okhttp3.RequestBody,
        @Part("isFavorite") isFavorite: okhttp3.RequestBody
    ): UploadResponse
}
