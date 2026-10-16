package com.calebms.openflix.data.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.calebms.openflix.data.local.entities.Profile

object AvatarUtils {
    val ALL_AVATARS = (1..16).map { "avatar-$it.svg" }

    fun getRandomAvatarName(): String {
        return ALL_AVATARS.random()
    }

    fun getAvatarName(profile: Profile): String {
        if (!profile.avatarName.isNullOrBlank()) {
            return profile.avatarName
        }
        val index = if (profile.id > 0) (profile.id - 1) % ALL_AVATARS.size else 0
        return ALL_AVATARS[index]
    }

    fun getAvatarAssetUrl(profile: Profile): String {
        return "file:///android_asset/${getAvatarName(profile)}"
    }

    fun getAvatarAssetUrl(avatarName: String): String {
        return "file:///android_asset/$avatarName"
    }

    @Composable
    fun rememberSvgImageLoader(): ImageLoader {
        val context = LocalContext.current
        return remember(context) {
            ImageLoader.Builder(context)
                .components {
                    add(SvgDecoder.Factory())
                }
                .build()
        }
    }
}
