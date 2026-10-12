package com.sanibonani.save.data.local

import androidx.room.*
import com.sanibonani.save.domain.model.GroupType
import com.sanibonani.save.domain.model.Money
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "platform_fee_config", primaryKeys = ["groupType", "roscaCycle"])
data class PlatformFeeConfigEntity(
    val groupType: GroupType,
    val roscaCycle: Int,
    val registrationFee: Money,
    val monthlyFee: Money,
    @ColumnInfo(name = "shortfall_recovery_fee") val shortfallRecoveryFee: Money = Money("5.00"),
    @ColumnInfo(name = "updated_at") val updatedAt: String? = null
)

@Dao
interface PlatformFeeConfigDao {
    @Query("SELECT * FROM platform_fee_config")
    fun observeAll(): Flow<List<PlatformFeeConfigEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: PlatformFeeConfigEntity)

    @Query("DELETE FROM platform_fee_config")
    suspend fun clearAll()
}
