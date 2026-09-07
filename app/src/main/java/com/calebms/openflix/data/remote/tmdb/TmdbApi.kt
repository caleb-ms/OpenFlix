package com.calebms.openflix.data.remote.tmdb

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query



data class TmdbSearchResponse(
    val results: List<TmdbSearchResult>
)

data class TmdbSearchResult(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val overview: String?,
    val poster_path: String?,
    val backdrop_path: String?,
    val vote_average: Double?,
    val release_date: String? = null,
    val first_air_date: String? = null,
    val genre_ids: List<Int>? = null
)

data class TmdbSeasonDetailsResponse(
    val episodes: List<TmdbEpisodeDetail>
)

data class TmdbEpisodeDetail(
    val id: Int,
    val episode_number: Int,
    val name: String?,
    val overview: String?,
    val still_path: String?
)


interface TmdbApiService {


    @GET("3/search/movie")
    suspend fun searchMovie(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("year") year: Int? = null
    ): TmdbSearchResponse


    @GET("3/search/tv")
    suspend fun searchTvShow(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("first_air_date_year") year: Int? = null
    ): TmdbSearchResponse


    @GET("3/tv/{tv_id}/season/{season_number}")
    suspend fun getSeasonDetails(
        @Path("tv_id") tvId: Int,
        @Path("season_number") seasonNumber: Int,
        @Query("api_key") apiKey: String
    ): TmdbSeasonDetailsResponse
}