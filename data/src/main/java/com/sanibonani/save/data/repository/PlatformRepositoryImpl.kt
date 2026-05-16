package com.sanibonani.save.data.repository

import com.sanibonani.save.domain.repository.*
import com.sanibonani.save.domain.model.PlatformAnalytics
import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.domain.model.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
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

            val members: List<Member> = if (supabase.auth.currentSessionOrNull() != null) {
                runCatching {
                    supabase.postgrest["members"].select().decodeList<Member>()
                }.getOrElse { e ->
                    // Members are often private; don't fail analytics for non-admins.
                    AppLogger.w(tag = "PlatformRepo", message = "Members not accessible: ${e.message}")
                    emptyList()
                }
            } else {
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
                AppLogger.w(tag, "Failed to fetch platform_settings: ${e.message}")
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
                order("created_at", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                limit(limit.toLong())
            }.decodeList<AuditLog>()
        }
    }

    override suspend fun getPlatformLedger(): Result<List<com.sanibonani.save.domain.model.LedgerEntry>> = retryWithExponentialBackoff {
        runCatching {
            supabase.postgrest["platform_ledger"].select {
                order("created_at", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            }.decodeList<com.sanibonani.save.domain.model.LedgerEntry>()
        }
    }

    override suspend fun getMemberBehaviorInsights(): Result<List<com.sanibonani.save.domain.model.MemberBehaviorInsight>> = retryWithExponentialBackoff {
        runCatching {
            supabase.postgrest["platform_member_behavior_insights_v1"].select {
                order("overdue_loans", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                order("outstanding_amount", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            }.decodeList<com.sanibonani.save.domain.model.MemberBehaviorInsight>()
        }
    }
}

@kotlinx.serialization.Serializable
data class PlatformSetting(val key: String, val value: Double)
