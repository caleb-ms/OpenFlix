package com.calebms.openflix.ui.preview

import com.calebms.openflix.data.local.entities.MediaEpisode
import com.calebms.openflix.data.local.entities.MediaItem
import com.calebms.openflix.data.local.entities.Profile

object PreviewData {
    val sampleProfiles = listOf(
        Profile(id = 1, name = "Caleb", avatarColorHex = 0xFFE50914),
        Profile(id = 2, name = "Family", avatarColorHex = 0xFF0080FF),
        Profile(id = 3, name = "Kids", avatarColorHex = 0xFF2ECC71)
    )

    val sampleMovie = MediaItem(
        id = "movie_1",
        title = "Inception",
        type = "MOVIE",
        releaseYear = 2010,
        localUri = "file:///storage/emulated/0/Movies/inception.mp4",
        overview = "A thief who steals corporate secrets through the use of dream-sharing technology is given the inverse task of planting an idea into the mind of a C.E.O.",
        durationMs = 8880000L,
        voteAverage = 8.8,
        genres = "Action, Sci-Fi"
    )

    val sampleTvShow = MediaItem(
        id = "tv_1",
        title = "Stranger Things",
        type = "TV_SHOW",
        releaseYear = 2016,
        localUri = "file:///storage/emulated/0/TV/StrangerThings",
        overview = "When a young boy vanishes, a small town uncovers a mystery involving secret experiments, terrifying supernatural forces and one strange little girl.",
        voteAverage = 8.7,
        genres = "Drama, Fantasy, Horror"
    )

    val sampleMediaList = listOf(
        sampleMovie,
        sampleTvShow,
        MediaItem(
            id = "movie_2",
            title = "Interstellar",
            type = "MOVIE",
            releaseYear = 2014,
            localUri = "file:///storage/emulated/0/Movies/interstellar.mp4",
            overview = "When Earth becomes uninhabitable in the future, a farmer and ex-NASA pilot, Joseph Cooper, is tasked to pilot a spacecraft, along with a team of researchers, to find a new planet for humans.",
            durationMs = 10140000L,
            voteAverage = 8.6,
            genres = "Adventure, Drama, Sci-Fi"
        ),
        MediaItem(
            id = "movie_3",
            title = "The Dark Knight",
            type = "MOVIE",
            releaseYear = 2008,
            localUri = "file:///storage/emulated/0/Movies/dark_knight.mp4",
            overview = "When the menace known as the Joker wreaks havoc and chaos on the people of Gotham, Batman must accept one of the greatest psychological and physical tests of his ability to fight injustice.",
            durationMs = 9120000L,
            voteAverage = 9.0,
            genres = "Action, Crime, Drama"
        )
    )

    val sampleEpisodes = listOf(
        MediaEpisode(
            id = "ep_1",
            mediaItemId = "tv_1",
            seasonNumber = 1,
            episodeNumber = 1,
            episodeTitle = "Chapter One: The Vanishing of Will Byers",
            localFileUri = "file:///storage/emulated/0/TV/StrangerThings/S01E01.mp4",
            episodeOverview = "On his way home from a friend's house, young Will sees something terrifying. Nearby, a sinister secret lurks in the depths of a government lab.",
            durationMs = 2880000L
        ),
        MediaEpisode(
            id = "ep_2",
            mediaItemId = "tv_1",
            seasonNumber = 1,
            episodeNumber = 2,
            episodeTitle = "Chapter Two: The Weirdo on Maple Street",
            localFileUri = "file:///storage/emulated/0/TV/StrangerThings/S01E02.mp4",
            episodeOverview = "Lucas, Dustin and Mike try to talk to the girl they found in the woods. Hopper questions an anxious Joyce about a disturbing phone call.",
            durationMs = 3300000L
        )
    )
}
