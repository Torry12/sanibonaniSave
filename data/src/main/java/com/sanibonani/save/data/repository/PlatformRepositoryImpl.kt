package com.sanibonani.save.data.repository

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.data.remote.PostgrestColumns
import com.sanibonani.save.data.utils.logAndGetMessage
import com.sanibonani.save.domain.model.ActuarialMetrics
import com.sanibonani.save.domain.model.AdminFeeState
import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.domain.model.AuditLog
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberBehaviorInsight
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.model.NotifChannel
import com.sanibonani.save.domain.model.NotifEvent
import com.sanibonani.save.domain.model.Payment
import com.sanibonani.save.domain.model.PlatformAnalytics
import com.sanibonani.save.domain.model.PlatformSummaryStats
import com.sanibonani.save.domain.repository.ActuarialRepository
import com.sanibonani.save.domain.repository.NotificationRepository
import com.sanibonani.save.domain.repository.PlatformRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class PlatformRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val db: SanibonaniDatabase,
    private val actuarialRepo: ActuarialRepository,
    private val notifRepo: NotificationRepository
) : BaseRepository("PlatformRepository"), PlatformRepository {

    override suspend fun getPlatformAnalytics(): Result<PlatformAnalytics> = retryWithExponentialBackoff {
        runCatching {
            // Fetch high-level summary from performance view (avoids member OOM)
            val summary: PlatformSummaryStats? = runCatching {
                supabase.postgrest["platform_summary_stats"].select().decodeSingle<PlatformSummaryStats>()
            }.getOrNull()

            val groups: List<Group> = runCatching {
                supabase.postgrest["groups"].select(columns = Columns.raw(PostgrestColumns.GROUPS_SAFE)).decodeList<Group>()
            }.recoverCatching {
                supabase.postgrest["groups"].select(columns = Columns.raw(PostgrestColumns.GROUPS_SAFE)) {
                    filter {
                        eq("is_public", true)
                        eq("registration_paid", true)
                    }
                }.decodeList<Group>()
            }.getOrElse { e ->
                val userMsg = e.logAndGetMessage(tag)
                AppLogger.e(tag, "Failed to fetch groups for analytics: $userMsg", e)
                throw IllegalStateException(userMsg)
            }

            val totalGroups = summary?.totalGroups ?: groups.size
            val totalMembers = summary?.totalMembers ?: groups.sumOf { it.currentMembers }
            val totalProvinces = summary?.totalProvinces ?: groups
                .mapNotNull { it.province?.trim()?.takeIf(String::isNotEmpty) }
                .distinct()
                .size
            val totalBalance = summary?.totalBalance ?: groups.sumOf { it.balance }
            val totalPlatformFees = summary?.platformRevenue ?: 0.0
            val averageRiskScore = summary?.averageRiskScore ?: 0.0

            PlatformAnalytics(
                totalGroups = totalGroups,
                totalMembers = totalMembers,
                totalProvinces = totalProvinces,
                totalBalance = totalBalance,
                totalPlatformFees = totalPlatformFees,
                averageRiskScore = averageRiskScore,
                groupTypeDistribution = groups.groupingBy { it.type.name }.eachCount(),
                provinceDistribution = groups.groupingBy { it.province }.eachCount()
            )
        }
    }

    override suspend fun getAllGroups(): Result<List<Group>> = retryWithExponentialBackoff {
        runCatching {
            val groups = supabase.postgrest["groups"].select(columns = Columns.raw(PostgrestColumns.GROUPS_SAFE)).decodeList<Group>()
            db.groupDao().upsertGroups(groups.map { it.toEntity() })
            groups
        }
    }

    override suspend fun updateGlobalFees(
        memberCharge: Double,
        registrationFee: Double,
        payoutFee: Double,
        whatsappFee: Double,
        lateFeePercent: Double,
        autoSuspensionDays: Int
    ): Result<Unit> = retryWithExponentialBackoff {
        runCatching {
            // Upsert monthly member fee (new canonical key)
            supabase.postgrest["platform_settings"].upsert(buildJsonObject {
                put("key", "monthly_member_fee")
                put("value", memberCharge)
            }) { onConflict = "key"; select() }
            
            // Keep legacy key in sync for backward compatibility.
            supabase.postgrest["platform_settings"].upsert(buildJsonObject {
                put("key", "monthly_per_member")
                put("value", memberCharge)
            }) { onConflict = "key"; select() }
            
            // Upsert registration_fee
            supabase.postgrest["platform_settings"].upsert(buildJsonObject {
                put("key", "registration_fee")
                put("value", registrationFee)
            }) { onConflict = "key"; select() }

            // New settings
            supabase.postgrest["platform_settings"].upsert(buildJsonObject {
                put("key", "payout_fee")
                put("value", payoutFee)
            }) { onConflict = "key"; select() }

            supabase.postgrest["platform_settings"].upsert(buildJsonObject {
                put("key", "whatsapp_fee")
                put("value", whatsappFee)
            }) { onConflict = "key"; select() }

            supabase.postgrest["platform_settings"].upsert(buildJsonObject {
                put("key", "late_fee_percent")
                put("value", lateFeePercent)
            }) { onConflict = "key"; select() }

            supabase.postgrest["platform_settings"].upsert(buildJsonObject {
                put("key", "auto_suspension_days")
                put("value", autoSuspensionDays.toDouble())
            }) { onConflict = "key"; select() }

            Unit
        }
    }

    override suspend fun getPlatformSettings(): Result<Map<String, Double>> = retryWithExponentialBackoff {
        runCatching {
            val settings = try {
                supabase.postgrest["platform_settings"].select().decodeList<PlatformSetting>()
            } catch (e: Exception) {
                val userMsg = e.logAndGetMessage(tag)
                AppLogger.w(tag, "Failed to fetch platform_settings: $userMsg")
                emptyList<PlatformSetting>()
            }
            val settingsMap = settings.associate { it.key to it.value }.toMutableMap()
            
            // Provide defaults if table is empty or missing keys
            if (!settingsMap.containsKey("registration_fee")) settingsMap["registration_fee"] = 700.0
            if (!settingsMap.containsKey("payout_fee")) settingsMap["payout_fee"] = 5.0
            if (!settingsMap.containsKey("whatsapp_fee")) settingsMap["whatsapp_fee"] = 0.50
            if (!settingsMap.containsKey("late_fee_percent")) settingsMap["late_fee_percent"] = 10.0
            if (!settingsMap.containsKey("auto_suspension_days")) settingsMap["auto_suspension_days"] = 30.0

            val monthlyMemberFee = settingsMap["monthly_member_fee"]
                ?: settingsMap["monthly_per_member"]
                ?: 10.0
            settingsMap["monthly_member_fee"] = monthlyMemberFee
            settingsMap["monthly_per_member"] = monthlyMemberFee
            settingsMap
        }
    }

    override suspend fun getPlatformPayments(): Result<List<Payment>> = retryWithExponentialBackoff {
        runCatching {
            supabase.postgrest["payments"].select {
                filter { eq("payment_type", "platform_fee") }
            }.decodeList<Payment>()
        }
    }

    override suspend fun unsuspendGroup(groupId: String): Result<Unit> = retryWithExponentialBackoff {
        runCatching {
            supabase.postgrest["groups"].update(buildJsonObject {
                put("is_platform_suspended", false)
                put("fee_status", AdminFeeState.PAID.name.lowercase())
            }) {
                filter { eq("id", groupId) }
            }
            
            notifRepo.sendNotification(AppNotification(
                groupId = groupId,
                message = "Platform Admin has lifted your suspension. Your group is now fully active.",
                triggerEvent = NotifEvent.GROUP_RESTORED
            ))
            Unit
        }
    }

    override suspend fun suspendGroup(groupId: String, reason: String): Result<Unit> = retryWithExponentialBackoff {
        runCatching {
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
            ))
            Unit
        }
    }

    override suspend fun getGroupMetrics(groupId: String): Result<ActuarialMetrics> = retryWithExponentialBackoff {
        actuarialRepo.computeMetrics(groupId)
    }

    override suspend fun logAuditEvent(auditLog: AuditLog): Result<Unit> = retryWithExponentialBackoff {
        runCatching {
            supabase.postgrest["audit_logs"].insert(auditLog) { select() }
            Unit
        }
    }

    override suspend fun broadcastPlatformMessage(message: String): Result<Unit> = retryWithExponentialBackoff {
        runCatching {
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
    }

    override suspend fun getAuditLogs(limit: Int): Result<List<AuditLog>> = retryWithExponentialBackoff {
        runCatching {
            supabase.postgrest["audit_logs"].select {
                order("created_at", order = Order.DESCENDING)
                limit(limit.toLong())
            }.decodeList<AuditLog>()
        }
    }

    override suspend fun getPlatformLedger(): Result<List<LedgerEntry>> = retryWithExponentialBackoff {
        runCatching {
            supabase.postgrest["platform_ledger"].select {
                order("created_at", order = Order.DESCENDING)
            }.decodeList<LedgerEntry>()
        }
    }

    override suspend fun getMemberBehaviorInsights(): Result<List<MemberBehaviorInsight>> = retryWithExponentialBackoff {
        runCatching {
            supabase.postgrest["platform_member_behavior_insights_v1"].select {
                order("overdue_loans", order = Order.DESCENDING)
                order("outstanding_amount", order = Order.DESCENDING)
            }.decodeList<MemberBehaviorInsight>()
        }
    }
}

@kotlinx.serialization.Serializable
data class PlatformSetting(val key: String, val value: Double)
