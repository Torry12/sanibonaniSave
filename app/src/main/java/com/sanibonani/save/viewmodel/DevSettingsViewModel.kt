package com.sanibonani.save.viewmodel

import androidx.lifecycle.ViewModel
import com.sanibonani.save.data.utils.DevSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DevSettingsViewModel @Inject constructor(
    val devSettings: DevSettings
) : ViewModel()
