package com.sanibonani.save.data.repository

import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.model.PlatformAnalytics
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.domain.model.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
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

    private val GROUP_COLUMNS_SAFE = "id,name,type,province,city,township,description,logo_emoji,joining_fee,monthly_contribution,late_fee,late_fee_grace_days,probation_months,payment_due_day,max_members,current_members,is_public,allow_partial_payment,auto_suspend_after,bank_name,account_number,branch_code,account_type,yoco_public_key,balance,admin_user_id,fee_status,registration_paid,latitude,longitude,geohash,created_at,is_platform_suspended"

    override suspend fun getPlatformAnalytics(): Result<PlatformAnalytics> = retryWithExponentialBackoff {
        runCatching {
            // NOTE:
            // - In production you likely want a single RPC for analytics.
            // - For anon users, some tables may be intentionally hidden by RLS.
            // - If table GRANTS are missing, PostgREST throws "permission denied".

            val groups: List<Group> = runCatching {
                // Prefer full dataset (platform admin / authenticated access)
                supabase.postgrest["groups"].select(columns = Columns.raw(GROUP_COLUMNS_SAFE)).decodeList<Group>()
            }.recoverCatching {
                // Fallback for anon: only discoverable groups (if policy allows)
                supabase.postgrest["groups"].select(columns = Columns.raw(GROUP_COLUMNS_SAFE)) {
                    filter {
                        eq("is_public", true)
                        eq("registration_paid", true)
                    }
                }.decodeList<Group>()
            }.getOrElse { e ->
                AppLogger.e(tag, "Failed to fetch groups for analytics", e)
                throw e
            }

            val members: List<Member> = runCatching {
                supabase.postgrest["members"].select().decodeList<Member>()
            }.getOrElse { e ->
                // Members are often private; don't fail analytics for anon.
                AppLogger.w(tag, "Members not accessible for analytics: ${e.message}")
                emptyList()
            }

            val totalGroups = groups.size
            // RLS can expose only a subset of member rows for non-platform users.
            // Use group-level counters as canonical platform totals and only use member rows as a lower-bound signal.
            val membersFromGroups = groups.sumOf { it.currentMembers }
            val membersFromRows = members
                .filter { it.status != MemberStatus.PENDING_PAYMENT }
                .size
            val totalMembers = maxOf(membersFromGroups, membersFromRows)
            val totalProvinces = groups
                .mapNotNull { it.province?.trim()?.takeIf(String::isNotEmpty) }
                .distinct()
                .size
            val totalBalance = groups.sumOf { it.balance }

            val totalPlatformFees = runCatching {
                // Sum platform fees from payments table (may be restricted)
                supabase.postgrest["payments"].select {
                    filter { eq("payment_type", "platform_fee") }
                }.decodeList<Payment>().sumOf { it.amount }
            }.getOrElse { 0.0 }

            withContext(Dispatchers.Default) {
                val averageRiskScore = if (members.isNotEmpty()) {
                    val scores: List<Double> = groups.map { group ->
                        val groupMembers = members.filter { it.groupId == group.id }
                        runCatching {
                            actuarialRepo.calculateMetrics(group, groupMembers).compositeRiskScore.toDouble()
                        }.getOrElse { 0.5 } // Default medium risk
                    }
                    if (scores.isNotEmpty()) scores.average() else 0.0
                } else {
                    // Avoid triggering extra network/RLS requirements for anon
                    0.0
                }

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
    }

    override suspend fun getAllGroups(): Result<List<Group>> = retryWithExponentialBackoff {
        runCatching {
            val groups = supabase.postgrest["groups"].select(columns = Columns.raw(GROUP_COLUMNS_SAFE)).decodeList<Group>()
            db.groupDao().upsertGroups(groups.map { it.toEntity() })
            groups
        }
    }

    override suspend fun updateGlobalFees(memberCharge: Double, registrationFee: Double): Result<Unit> = retryWithExponentialBackoff {
        runCatching {
            // Upsert monthly_per_member fee
            supabase.postgrest["platform_settings"].upsert(buildJsonObject {
                put("key", "monthly_per_member")
                put("value", memberCharge)
            }) {
                onConflict = "key"
                select()
            }
            // Upsert registration_fee
            supabase.postgrest["platform_settings"].upsert(buildJsonObject {
                put("key", "registration_fee")
                put("value", registrationFee)
            }) {
                onConflict = "key"
                select()
            }
            Unit
        }
    }

    override suspend fun getPlatformSettings(): Result<Map<String, Double>> = retryWithExponentialBackoff {
        runCatching {
            val settings = try {
                supabase.postgrest["platform_settings"].select().decodeList<PlatformSetting>()
            } catch (e: Exception) {
                AppLogger.w(tag, "Failed to fetch platform_settings: ${e.message}")
                emptyList<PlatformSetting>()
            }
            val settingsMap = settings.associate { it.key to it.value }.toMutableMap()
            // Provide defaults if table is empty or missing keys
            if (!settingsMap.containsKey("registration_fee")) settingsMap["registration_fee"] = 700.0
            if (!settingsMap.containsKey("monthly_per_member")) settingsMap["monthly_per_member"] = 10.0
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
}

@kotlinx.serialization.Serializable
data class PlatformSetting(val key: String, val value: Double)
