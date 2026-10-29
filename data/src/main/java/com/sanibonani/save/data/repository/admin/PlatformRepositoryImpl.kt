package com.sanibonani.save.data.repository.admin

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.local.toEntity
import com.sanibonani.save.data.local.toModel
import com.sanibonani.save.data.remote.PostgrestColumns
import com.sanibonani.save.data.repository.BaseRepository
import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.ActuarialMetrics
import com.sanibonani.save.domain.model.AdminFeeState
import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.domain.model.DatabaseBackup
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.model.MemberBehaviorInsight
import com.sanibonani.save.domain.model.Money
import com.sanibonani.save.domain.model.NotifChannel
import com.sanibonani.save.domain.model.NotifEvent
import com.sanibonani.save.domain.model.Payment
import com.sanibonani.save.domain.model.PlatformAnalytics
import com.sanibonani.save.domain.model.PlatformAnalyticsSummary
import com.sanibonani.save.domain.model.PlatformConfig
import com.sanibonani.save.domain.model.PlatformFeeConfig
import com.sanibonani.save.domain.repository.admin.ActuarialRepository
import com.sanibonani.save.domain.repository.admin.PlatformRepository
import com.sanibonani.save.domain.repository.system.DataConnectionProtocol
import com.sanibonani.save.domain.repository.system.NotificationRepository
import com.sanibonani.save.domain.utils.logAndGetMessage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import java.io.File
import java.math.BigDecimal
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import com.sanibonani.save.domain.utils.safeRunCatching

class PlatformRepositoryImpl @Inject constructor(
    private val protocol: DataConnectionProtocol,
    private val db: SanibonaniDatabase,
    private val actuarialRepo: ActuarialRepository,
    private val notifRepo: NotificationRepository,
) : BaseRepository("PlatformRepository"), PlatformRepository {

    private val supabase: SupabaseClient
        get() = protocol.getRemoteClient(SupabaseClient::class.java)
            ?: error("The app is not connected to the server yet.")

    override suspend fun getPlatformAnalytics(): Result<PlatformAnalytics> = retryWithExponentialBackoff {
        // Fetch high-level summary from performance view (avoids member OOM)
        val summary: PlatformAnalyticsSummary? = safeRunCatching {
            supabase.postgrest["platform_summary_stats"].select().decodeList<PlatformAnalyticsSummary>().firstOrNull()
        }.getOrNull()

        val groups: List<Group> = safeRunCatching {
            supabase.postgrest["groups"].select(columns = Columns.raw(PostgrestColumns.GROUPS_SAFE)).decodeList<Group>()
        }.recoverCatching {
            supabase.postgrest["groups"].select(columns = Columns.raw(PostgrestColumns.GROUPS_SAFE)) {
                filter {
                    eq("is_public", true)
                    eq("registration_paid", true)
                }
            }.decodeList<Group>()
        }.recoverCatching {
            db.groupDao().observeAllGroups().firstOrNull()?.map { it.toModel() } ?: emptyList()
        }.getOrElse { emptyList() }

        val totalGroups = summary?.totalGroups ?: groups.size
        val totalMembers = summary?.totalMembers ?: groups.sumOf { it.currentMembers }
        val totalProvinces = summary?.totalProvinces ?: groups
            .mapNotNull { it.province?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .size
        val totalBalance = summary?.totalBalance ?: Money(groups.map { it.balance.amount }.fold(
            BigDecimal.ZERO, BigDecimal::add))
        val summaryRevenue = summary?.platformRevenue ?: Money.ZERO
        val totalPlatformFees = if (summaryRevenue.isPositive) {
            summaryRevenue
        } else {
            // Fallback estimate: R10 per member per month for 6 months, plus an R150 group
            // registration fee at 80% capture.
            Money("10") * (totalMembers * 6) + Money("150").percent(80.0) * totalGroups
        }
        val averageRiskScore = summary?.averageRiskScore ?: 0.0

        PlatformAnalytics(
            totalGroups = totalGroups,
            totalMembers = totalMembers,
            totalProvinces = totalProvinces,
            totalBalance = totalBalance,
            totalPlatformFees = totalPlatformFees,
            averageRiskScore = averageRiskScore,
            groupTypeDistribution = groups.groupingBy { it.type.name }.eachCount(),
            provinceDistribution = groups.groupingBy { it.province.orEmpty() }.eachCount()
        )
    }

    override suspend fun getAllGroups(): Result<List<Group>> = retryWithExponentialBackoff {
        val groups = safeRunCatching {
            supabase.postgrest["groups"].select(columns = Columns.raw(PostgrestColumns.GROUPS_SAFE)).decodeList<Group>()
        }.recoverCatching {
            db.groupDao().observeAllGroups().firstOrNull()?.map { it.toModel() } ?: emptyList()
        }.getOrElse { emptyList() }

        if (groups.isNotEmpty()) {
            safeRunCatching { db.groupDao().upsertGroups(groups.map { it.toEntity() }) }
        }
        groups
    }

    override suspend fun updateGlobalFees(config: PlatformConfig): Result<Unit> = authoritativeCall("updateGlobalFees") {
        val allSettings = listOf(
            "monthly_member_fee" to config.monthlyMemberFee.amount.toPlainString(),
            "monthly_per_member" to config.monthlyMemberFee.amount.toPlainString(),
            "registration_fee" to config.registrationFee.amount.toPlainString(),
            "payout_fee" to config.payoutFee.amount.toPlainString(),
            "late_fee_percent" to config.lateFeePercent.amount.toPlainString(),
            "shortfall_recovery_fee" to config.shortfallRecoveryFee.amount.toPlainString(),
            "loan_interest_rate" to config.loanInterestRate.amount.toPlainString(),
            "loan_processing_fee" to config.loanProcessingFee.amount.toPlainString(),
            "group_suspension_lifting_fee" to config.groupSuspensionLiftingFee.amount.toPlainString(),
            "member_suspension_lifting_fee" to config.memberSuspensionLiftingFee.amount.toPlainString(),
            "auto_suspension_days" to config.autoSuspensionDays.toString(),
            "platform_bank_name" to config.bankName,
            "bank_name" to config.bankName,
            "platform_account_number" to config.accountNumber,
            "account_number" to config.accountNumber,
            "platform_branch_code" to config.branchCode,
            "branch_code" to config.branchCode,
            "platform_account_holder" to config.accountHolder,
            "account_holder" to config.accountHolder
        )

        allSettings.forEach { (key, value) ->
            supabase.postgrest["platform_settings"].upsert(buildJsonObject {
                put("key", key)
                put("value", value)
                put("value_text", value)
            }) { onConflict = "key"; select() }
        }
    }

    override suspend fun computeSystemWideMetrics(): Result<Unit> = authoritativeCall("computeSystemWideMetrics") {
        supabase.functions.invoke("compute-platform-metrics")
    }

    override suspend fun triggerCloudBackup(): Result<Unit> = authoritativeCall("triggerCloudBackup") {
        supabase.functions.invoke("trigger-backup")
    }

    override suspend fun getCloudBackups(): Result<List<DatabaseBackup>> = retryWithExponentialBackoff {
        // A dump is a storage object: `deleteCloudBackup` removes it from the `backups` bucket, and the
        // edge function that produces it writes there too. There is no `database_backups` table, so the
        // old read answered "no backups" for every project and, wrapped in getOrElse, never failed.
        supabase.storage["backups"]
            .list("")
            .sortedByDescending { file -> file.updatedAt }
            .map { file ->
                DatabaseBackup(
                    name = file.name,
                    size = file.metadata?.get("size")?.jsonPrimitive?.longOrNull ?: 0L,
                    lastModified = file.updatedAt?.toString().orEmpty()
                )
            }
    }

    override suspend fun deleteCloudBackup(name: String): Result<Unit> = authoritativeCall("deleteCloudBackup") {
        supabase.storage["backups"].delete(name)
    }

    override suspend fun repairGroupData(groupId: String): Result<Unit> = authoritativeCall("repairGroupData") {
        supabase.postgrest.rpc("recalculate_all_group_financials_v3", buildJsonObject { put("p_group_id", groupId) })
    }

    override suspend fun searchMembers(query: String): Result<List<MemberBehaviorInsight>> =
        retryWithExponentialBackoff {
            // `platform_member_behavior_insights_v1` was never a view this schema creates, so the read
            // could only ever 404 - and `getOrElse` turned that into an empty roster. The server has
            // had the answer all along in `search_members_v1`, which joins the behaviour track.
            supabase.postgrest.rpc(
                function = "search_members_v1",
                parameters = buildJsonObject { put("p_query", query) }
            ).decodeList<MemberBehaviorInsight>()
        }

    override suspend fun getGranularFeeConfigs(): Result<List<PlatformFeeConfig>> = retryWithExponentialBackoff {
        safeRunCatching {
            supabase.postgrest["platform_fee_config"].select().decodeList<PlatformFeeConfig>()
        }.getOrElse { emptyList() }
    }

    override suspend fun updateGranularFeeConfig(config: PlatformFeeConfig): Result<Unit> = authoritativeCall("updateGranularFeeConfig") {
        supabase.postgrest["platform_fee_config"].upsert(config) { select() }
    }

    override suspend fun exportLocalDatabase(useOnline: Boolean): Result<File> = safeRunCatching {
        val dbPath = safeRunCatching { db.openHelper.writableDatabase.path }.getOrNull()
        if (dbPath != null && File(dbPath).exists()) {
            val src = File(dbPath)
            val exportFile = File.createTempFile("sanibonani_${if (useOnline) "online" else "local"}_export_", ".db")
            src.copyTo(exportFile, overwrite = true)
            exportFile
        } else {
            File.createTempFile("db_export", ".db")
        }
    }

    override suspend fun getPlatformSettings(): Result<PlatformConfig> = retryWithExponentialBackoff {
        // A read that never reached the server stays a failure: an outage reported as a set of
        // fees would have the app bill its own fallbacks over the fee the admin published.
        val settings = supabase.postgrest["platform_settings"].select().decodeList<PlatformSetting>()

        val map = settings.associateBy { it.key }

        fun getSettingValue(vararg keys: String): String? {
            for (k in keys) {
                val s = map[k]
                val v = s?.valueText ?: s?.value
                if (!v.isNullOrBlank()) return v
            }
            return null
        }

        // PlatformConfig is the only list of fallbacks, so a key the server has never carried
        // resolves to the same amount as the rest of the app rather than a copy in this table.
        val defaults = PlatformConfig()
        fun moneySetting(default: Money, vararg keys: String): Money {
            val raw = getSettingValue(*keys) ?: return default
            return runCatching { Money(raw) }.getOrElse {
                AppLogger.w(tag, "Unparseable platform_settings fee; keeping the published default.")
                default
            }
        }

        PlatformConfig(
            monthlyMemberFee = moneySetting(defaults.monthlyMemberFee, "monthly_member_fee", "monthly_per_member"),
            registrationFee = moneySetting(defaults.registrationFee, "registration_fee"),
            payoutFee = moneySetting(defaults.payoutFee, "payout_fee"),
            lateFeePercent = moneySetting(defaults.lateFeePercent, "late_fee_percent"),
            shortfallRecoveryFee = moneySetting(defaults.shortfallRecoveryFee, "shortfall_recovery_fee"),
            loanInterestRate = moneySetting(defaults.loanInterestRate, "loan_interest_rate"),
            loanProcessingFee = moneySetting(defaults.loanProcessingFee, "loan_processing_fee"),
            groupSuspensionLiftingFee = moneySetting(defaults.groupSuspensionLiftingFee, "group_suspension_lifting_fee"),
            memberSuspensionLiftingFee = moneySetting(defaults.memberSuspensionLiftingFee, "member_suspension_lifting_fee"),
            autoSuspensionDays = getSettingValue("auto_suspension_days")?.toIntOrNull() ?: defaults.autoSuspensionDays,
            bankName = getSettingValue("platform_bank_name", "bank_name") ?: "FNB",
            accountNumber = getSettingValue("platform_account_number", "account_number") ?: "",
            branchCode = getSettingValue("platform_branch_code", "branch_code") ?: "",
            accountHolder = getSettingValue("platform_account_holder", "account_holder") ?: "Sanibonani Platform"
        )
    }

    override suspend fun unsuspendGroup(groupId: String): Result<Unit> {
        // The lifting fee is what the group is being charged to come back online, so an unreadable
        // settings table stops the action instead of running it on an amount nobody published.
        val config = getPlatformSettings().getOrElse { return Result.failure(it) }
        val liftingFee = config.groupSuspensionLiftingFee
        // One key for the whole action, and no retry around it: payments has no unique constraint,
        // so re-issuing an ambiguous timeout would post a second lifting fee the group never agreed to.
        val feeTxId = buildLiftingFeeTransactionId(groupId)

        return authoritativeCall("unsuspendGroup") {
            // The fee is recorded first. If the server refuses it the group stays suspended,
            // instead of coming back online with its revenue unaccounted for.
            supabase.postgrest["payments"].insert(liftingFeePayload(groupId, liftingFee, feeTxId))

            supabase.postgrest["groups"].update(buildJsonObject {
                put("is_platform_suspended", false)
                put("fee_status", AdminFeeState.PAID.name.lowercase())
            }) {
                filter { eq("id", groupId) }
            }

            notifRepo.sendNotification(AppNotification(
                groupId = groupId,
                message = "Platform Admin has lifted your suspension (Lifting Fee: ${liftingFee.format()}). Your group is now fully active.",
                triggerEvent = NotifEvent.GROUP_RESTORED
            )).onFailure { AppLogger.w(tag, "Group restoration notification failed: ${it.logAndGetMessage(tag)}") }
        }
    }

    override suspend fun suspendGroup(groupId: String, reason: String): Result<Unit> = authoritativeCall("suspendGroup") {
        supabase.postgrest["groups"].update(buildJsonObject {
            put("is_platform_suspended", true)
            put("fee_status", AdminFeeState.SUSPENDED.name.lowercase())
        }) {
            filter { eq("id", groupId) }
        }

        notifRepo.sendNotification(AppNotification(
            groupId = groupId,
            message = "Your group has been suspended by the Platform Admin. Reason: $reason",
            triggerEvent = NotifEvent.GROUP_SUSPENDED
        )).onFailure { AppLogger.w(tag, "Group suspension notification failed: ${it.logAndGetMessage(tag)}") }
    }

    override suspend fun getGroupMetrics(groupId: String): Result<ActuarialMetrics> = retryWithExponentialBackoff {
        actuarialRepo.computeMetrics(groupId).getOrThrow()
    }

    override suspend fun broadcastPlatformMessage(message: String): Result<Unit> = authoritativeCall("broadcastPlatformMessage") {
        val groups = getAllGroups().getOrThrow()
        groups.forEach { group ->
            notifRepo.sendNotification(AppNotification(
                groupId = group.id ?: return@forEach,
                message = "[SYSTEM BROADCAST]: $message",
                triggerEvent = NotifEvent.CUSTOM,
                channel = NotifChannel.BOTH
            ))
        }
    }

    override suspend fun getPlatformLedger(): Result<List<LedgerEntry>> = retryWithExponentialBackoff {
        safeRunCatching {
            supabase.postgrest["platform_ledger"].select {
                order("created_at", order = Order.DESCENDING)
            }.decodeList<LedgerEntry>()
        }.recoverCatching {
            db.ledgerDao().observePlatformLedger().firstOrNull()?.map { it.toModel() } ?: emptyList()
        }.getOrElse { emptyList() }
    }

    override suspend fun deleteGroupFromPlatform(groupId: String): Result<Unit> = authoritativeCall("deleteGroupFromPlatform") {
        safeRunCatching {
            supabase.postgrest["groups"].delete {
                filter { eq("id", groupId) }
            }
        }.getOrThrow()
        db.groupDao().deleteGroupById(groupId)
    }

    companion object {
        internal fun buildLiftingFeeTransactionId(groupId: String): String =
            "unsuspend_group_${groupId}_${System.currentTimeMillis()}"

        internal fun liftingFeePayload(groupId: String, amount: Money, transactionId: String): JsonObject =
            buildJsonObject {
                put("group_id", groupId)
                put("amount", amount.amount.toPlainString())
                put("payment_type", "platform_fee")
                put("payment_method", "bank")
                put("transaction_id", transactionId)
                put("status", "completed")
                put("description", "Group Suspension Lifting Fee (Platform Revenue)")
            }
    }
}

@Serializable
data class PlatformSetting(
    val key: String,
    val value: String? = null,
    @SerialName("value_text") val valueText: String? = null
)
