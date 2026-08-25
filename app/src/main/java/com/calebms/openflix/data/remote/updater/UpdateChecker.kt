package com.calebms.openflix.data.remote.updater

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET


data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val fileSize: String,
    val forceUpdate: Boolean,
    val downloadUrl: String,
    val changelog: List<String>
)


interface UpdateApiService {
    @GET("openflix/version.json")
    suspend fun getLatestVersion(): AppUpdateInfo
}


sealed interface UpdateCheckState {
    object Idle : UpdateCheckState
    object Checking : UpdateCheckState
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckState
    object UpToDate : UpdateCheckState
    data class Error(val message: String) : UpdateCheckState
}


class UpdateManager(private val context: Context) {

    private val api: UpdateApiService = Retrofit.Builder()
        .baseUrl("https://cdn.calebms.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(UpdateApiService::class.java)


    suspend fun checkForUpdates(): UpdateCheckState = withContext(Dispatchers.IO) {
        try {
            val remoteInfo = api.getLatestVersion()
            val currentVersionCode = getCurrentVersionCode()

            if (remoteInfo.versionCode > currentVersionCode) {
                UpdateCheckState.UpdateAvailable(remoteInfo)
            } else {
                UpdateCheckState.UpToDate
            }
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to check for updates", e)
            UpdateCheckState.Error(e.localizedMessage ?: "Failed to reach update server")
        }
    }


    fun getCurrentVersionCode(): Long {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            1L
        }
    }


    fun getCurrentVersionName(): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
}