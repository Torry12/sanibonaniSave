package com.sanibonani.save.domain.service

import kotlinx.coroutines.flow.Flow

interface NetworkConnectivityService {
    val isOnline: Flow<Boolean>
    val currentStatus: Boolean
    val isAirplaneModeActive: Flow<Boolean>
}
