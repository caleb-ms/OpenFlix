package com.calebms.openflix.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calebms.openflix.data.local.AppDatabase
import com.calebms.openflix.data.local.entities.Profile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.calebms.openflix.data.utils.AvatarUtils

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val profileDao = db.profileDao()


    private val avatarColors = listOf(
        0xFFE50914,
        0xFF0071EB,
        0xFFF5B300,
        0xFF2E7D32,
        0xFF8E24AA
    )


    val allProfiles: StateFlow<List<Profile>> = profileDao.getAllProfiles()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun createProfile(name: String) {
        viewModelScope.launch {
            val currentProfiles = profileDao.getAllProfilesList()
            if (currentProfiles.size >= 5) return@launch
            val randomColor = avatarColors.random()
            val newProfile = Profile(
                name = name.trim(),
                avatarColorHex = randomColor,
                avatarName = AvatarUtils.getRandomAvatarName()
            )
            profileDao.insertProfile(newProfile)
        }
    }

    fun updateProfile(profile: Profile) {
        viewModelScope.launch {

            profileDao.insertProfile(profile)
        }
    }

    fun deleteProfile(profile: Profile) {
        viewModelScope.launch {
            profileDao.deleteProfile(profile)
        }
    }
}