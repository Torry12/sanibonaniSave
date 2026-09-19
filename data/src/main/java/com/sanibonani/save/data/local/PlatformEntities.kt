package com.sanibonani.save.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Money

@Entity(tableName = "platform_fee_config", primaryKeys = ["groupType", "roscaCycle"])
data class PlatformFeeConfigEntity(
    val groupType: GroupType,
    val roscaCycle: Int,
    val registrationFee: Money,
    val monthlyFee: Money,
    val updatedAt: Long = System.currentTimeMillis()
)
