package com.byayzen

import com.fasterxml.jackson.annotation.JsonProperty

data class TokenResponse(
    @JsonProperty("access_token") val accesstoken: String?
)
data class TidalSearchResponseWrapper(
    @JsonProperty("albums") val albums: TidalSearchResponse?
)
data class TidalSearchResponse(
    @JsonProperty("items") val items: List<TidalItem>?
)

data class SimilarResponse(
    @JsonProperty("albums") val albums: List<TidalItem>?
)

data class TidalItem(
    @JsonProperty("id") val id: String?,
    @JsonProperty("title") val title: String?,
    @JsonProperty("cover") val cover: String?
)

data class EditorPick(
    @JsonProperty("id") val id: String?,
    @JsonProperty("title") val title: String?,
    @JsonProperty("cover") val cover: String?
)

data class AlbumDetails(
    @JsonProperty("id") val id: String?,
    @JsonProperty("title") val title: String?,
    @JsonProperty("cover") val cover: String?,
    @JsonProperty("releaseDate") val releasedate: String?,
    @JsonProperty("numberOfTracks") val numberoftracks: Int?,
    @JsonProperty("artist") val artist: ArtistSummary?,
    @JsonProperty("mediaMetadata") val mediametadata: MediaMetadata?
)

data class TrackListResponse(
    @JsonProperty("items") val items: List<TrackItem>?
)

data class TrackItem(
    @JsonProperty("id") val id: String,
    @JsonProperty("title") val title: String?,
    @JsonProperty("trackNumber") val trackNumber: Int?
)

data class ArtistSummary(
    @JsonProperty("name") val name: String?
)

data class MediaMetadata(
    @JsonProperty("tags") val tags: List<String>?
)

data class RecommendationResponse(
    @JsonProperty("data") val data: RecData?
)

data class RecData(
    @JsonProperty("items") val items: List<RecItem>?
)

data class RecItem(
    @JsonProperty("track") val track: RecTrack?
)

data class RecTrack(
    @JsonProperty("title") val title: String?,
    @JsonProperty("album") val album: RecAlbum?
)

data class RecAlbum(
    @JsonProperty("id") val id: String?,
    @JsonProperty("cover") val cover: String?
)

data class TidalManifestJsonResponse(
    @JsonProperty("data") val data: TidalDataWrapper?
)

data class TidalDataWrapper(
    @JsonProperty("data") val data: TidalInnerData?
)

data class TidalInnerData(
    @JsonProperty("attributes") val attributes: TidalAttributes?
)

data class TidalAttributes(
    @JsonProperty("uri") val uri: String?
)