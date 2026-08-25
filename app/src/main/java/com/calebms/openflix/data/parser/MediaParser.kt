package com.calebms.openflix.data.parser

data class ParsedMediaInfo(
    val cleanTitle: String,
    val isTvShow: Boolean,
    val releaseYear: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null
)

object MediaParser {

    // Matches standard TV show patterns: S01E02, s1e2, 1x02, etc.
    private val TV_SEASON_EPISODE_REGEX = Regex("(?i)[._ -]S(\\d{1,2})[._ -]?E(\\d{1,3})|[._ -](\\d{1,2})x(\\d{1,3})")

    // Matches 4-digit release years: (2023), 2024, etc.
    private val YEAR_REGEX = Regex("[._ -]\\(?((?:19|20)\\d{2})\\)?")

    // Common junk tags in torrent/rip filenames to clean out
    private val NOISE_REGEX = Regex("(?i)[._ -](1080p|720p|480p|2160p|4k|webrip|web-dl|bluray|x264|x265|hevc|aac|dts|amzn|nf|hdtv|repack).*")

    fun parse(fileName: String): ParsedMediaInfo {
        // Strip extension (e.g. .mp4, .mkv)
        val nameWithoutExt = fileName.substringBeforeLast(".")

        val tvMatch = TV_SEASON_EPISODE_REGEX.find(nameWithoutExt)

        return if (tvMatch != null) {
            // It's a TV Show episode
            val (season, episode) = if (tvMatch.groups[1] != null) {
                Pair(tvMatch.groups[1]?.value?.toIntOrNull() ?: 1, tvMatch.groups[2]?.value?.toIntOrNull() ?: 1)
            } else {
                Pair(tvMatch.groups[3]?.value?.toIntOrNull() ?: 1, tvMatch.groups[4]?.value?.toIntOrNull() ?: 1)
            }

            // Everything before the season/episode pattern is the show title
            val rawTitle = nameWithoutExt.substring(0, tvMatch.range.first)
            val cleanTitle = cleanUpTitle(rawTitle)

            ParsedMediaInfo(
                cleanTitle = cleanTitle,
                isTvShow = true,
                seasonNumber = season,
                episodeNumber = episode
            )
        } else {
            // It's a standalone Movie
            val yearMatch = YEAR_REGEX.find(nameWithoutExt)
            val year = yearMatch?.groups[1]?.value?.toIntOrNull()

            val rawTitle = if (yearMatch != null) {
                nameWithoutExt.substring(0, yearMatch.range.first)
            } else {
                NOISE_REGEX.replace(nameWithoutExt, "")
            }

            ParsedMediaInfo(
                cleanTitle = cleanUpTitle(rawTitle),
                isTvShow = false,
                releaseYear = year
            )
        }
    }

    private fun cleanUpTitle(title: String): String {
        return title
            .replace(".", " ")
            .replace("_", " ")
            .replace("-", " ")
            .trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
    }
}