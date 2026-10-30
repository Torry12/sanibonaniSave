package com.sanibonani.save.features.common.presentation

import androidx.lifecycle.ViewModel
import com.sanibonani.save.domain.service.DevSettingsService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DevSettingsViewModel @Inject constructor(
    val devSettings: DevSettingsService
) : ViewModel()
