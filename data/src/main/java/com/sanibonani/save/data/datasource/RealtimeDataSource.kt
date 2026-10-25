package com.sanibonani.save.data.datasource

import com.sanibonani.save.domain.model.AdminFeeState
import kotlinx.coroutines.flow.Flow

interface RealtimeDataSource {
    fun observeGroupFeeStatus(groupId: String): Flow<AdminFeeState>
}
