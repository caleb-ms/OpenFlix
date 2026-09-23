package com.calebms.openflix.data.parser

data class ParsedMediaInfo(
    val cleanTitle: String,
    val isTvShow: Boolean,
    val releaseYear: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val resolution: String? = null
)

object MediaParser {

    private val TV_SEASON_EPISODE_REGEX = Regex(
        "(?i)(?:s|season[._ -]?)(\\d{1,2})[._ -]*(?:e|ep|episode)[._ -]*(\\d{1,3})|(\\d{1,2})x(\\d{1,3})"
    )

    private val STANDALONE_EPISODE_REGEX = Regex("(?i)[._ -](?:e|ep|episode)[._ -]*(\\d{1,3})(?:[._ -]|$)")

    private val YEAR_REGEX = Regex("(?:[._ -]|^)\\(?((?:19|20)\\d{2})\\)?(?:[._ -]|$)")

    private val RESOLUTION_REGEX = Regex("(?i)[._ -](2160p|4k|1080p|1080i|720p|576p|480p|360p)(?:[._ -]|$)")

    private val NOISE_TAGS = listOf(
        "webrip", "web-dl", "webdl", "bluray", "blu-ray", "brrip", "bdrip", "dvdrip", "hdtv",
        "x264", "x265", "h264", "h265", "hevc", "avc", "xvid",
        "aac", "ac3", "eac3", "dts", "dts-hd", "truehd", "mp3", "flac",
        "10bit", "hdr", "hdr10", "dolby", "vision", "atmos",
        "amzn", "nf", "dsnp", "atvp", "repack", "proper", "rerip", "unrated", "directors.cut",
        "yify", "yts", "eztv", "rarbg", "galaxyrg"
    )

    private val NOISE_REGEX = Regex(
        "(?i)[._ -](${NOISE_TAGS.joinToString("|")})(?:[._ -]|$)"
    )

    fun parse(fileName: String): ParsedMediaInfo {
        val nameWithoutExt = fileName.substringBeforeLast(".")

        // 1. Check TV patterns
        var season: Int? = null
        var episode: Int? = null
        var tvMarkerIndex: Int? = null

        val tvMatch = TV_SEASON_EPISODE_REGEX.find(nameWithoutExt)
        if (tvMatch != null) {
            tvMarkerIndex = tvMatch.range.first
            if (tvMatch.groups[1] != null) {
                season = tvMatch.groups[1]?.value?.toIntOrNull()
                episode = tvMatch.groups[2]?.value?.toIntOrNull()
            } else {
                season = tvMatch.groups[3]?.value?.toIntOrNull()
                episode = tvMatch.groups[4]?.value?.toIntOrNull()
            }
        } else {
            val epMatch = STANDALONE_EPISODE_REGEX.find(nameWithoutExt)
            if (epMatch != null) {
                tvMarkerIndex = epMatch.range.first
                season = 1
                episode = epMatch.groups[1]?.value?.toIntOrNull()
            }
        }

        // 2. Extract resolution
        val resMatch = RESOLUTION_REGEX.find(nameWithoutExt)
        val resolution = resMatch?.groups[1]?.value?.uppercase()

        // 3. Find all noise markers
        val noiseMatch = NOISE_REGEX.find(nameWithoutExt)

        // 4. Resolve the Release Year correctly
        val allYears = YEAR_REGEX.findAll(nameWithoutExt).toList()

        // Filter out a year match if it sits at index 0 and other text follows (e.g., "2001 A Space Odyssey")
        val validYears = allYears.filter { match ->
            !(match.range.first == 0 && nameWithoutExt.length > 5)
        }

        // The actual release year is typically the last valid year before any noise or resolution tags
        val trueYearMatch = validYears.lastOrNull()
        val year = trueYearMatch?.groups[1]?.value?.toIntOrNull()

        // 5. Determine the title cutoff boundary
        val cutIndices = mutableListOf<Int>()
        tvMarkerIndex?.let { cutIndices.add(it) }
        trueYearMatch?.let { cutIndices.add(it.range.first) }

        // If resolution or noise exists and comes BEFORE a TV tag, it's a boundary
        resMatch?.range?.first?.let { idx ->
            if (tvMarkerIndex == null || idx < tvMarkerIndex) cutIndices.add(idx)
        }
        noiseMatch?.range?.first?.let { idx ->
            if (tvMarkerIndex == null || idx < tvMarkerIndex) cutIndices.add(idx)
        }

        val rawTitle = if (cutIndices.isNotEmpty()) {
            val minCut = cutIndices.minOrNull()!!
            if (minCut > 0) nameWithoutExt.substring(0, minCut) else nameWithoutExt
        } else {
            nameWithoutExt
        }

        val cleanTitle = cleanUpTitle(stripNoise(rawTitle))

        return ParsedMediaInfo(
            cleanTitle = cleanTitle,
            isTvShow = episode != null,
            releaseYear = year,
            seasonNumber = season,
            episodeNumber = episode,
            resolution = resolution
        )
    }

    private fun stripNoise(text: String): String {
        var result = text
        while (NOISE_REGEX.containsMatchIn(result)) {
            result = NOISE_REGEX.replace(result, " ")
        }
        return result
    }

    private fun cleanUpTitle(raw: String): String {
        return raw
            .replace(Regex("[\\[\\](){}]"), " ")
            .replace(Regex("[._ -]+"), " ")
            .trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
    }
}