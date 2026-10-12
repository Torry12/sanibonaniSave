package com.sanibonani.save.data.repository.mappers

import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.domain.model.Member
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central factory for creating JSON payloads for Supabase / PostgREST writes.
 * Encapsulates the mapping of domain models to database columns.
 * High-precision: Monetary values are serialized as plain strings.
 */
@Singleton
class DataPayloadFactory @Inject constructor() {

    fun createMemberPayload(member: Member): JsonObject {
        return buildJsonObject {
            member.id?.let { put("id", it) }
            put("group_id", member.groupId)
            member.userId?.let { put("user_id", it) }
            put("full_name", member.fullName)
            put("id_number", member.idNumber)
            put("phone", member.phone)
            put("email", member.email)
            put("street", member.street)
            put("suburb", member.suburb)
            put("city", member.city)
            put("province", member.province)
            put("notification_pref", member.notificationPref.name.lowercase())
            put("status", member.status.name.lowercase())
            member.joinedAt?.let { put("joined_at", it) }
            member.probationEndAt?.let { put("probation_end_at", it) }
            member.memberKey?.let { put("member_key", it) }
            member.profilePhotoUrl?.let { put("profile_photo_url", it) }
            put("total_contributions", member.totalContributions ?: 0)
            put("total_paid", member.totalPaid.amount.toPlainString())
            put("beneficiary_count", member.beneficiaryCount ?: 0)
            put("beneficiary_over_65_count", member.beneficiaryOver65Count ?: 0)
            member.monthlyContributionOverride?.let { put("monthly_contribution_override", it.amount.toPlainString()) }
        }
    }

    fun createNotificationPayload(notification: AppNotification): JsonObject {
        return buildJsonObject {
            notification.id?.let { put("id", it) }
            notification.groupId?.let { put("group_id", it) }
            notification.memberId?.let { put("member_id", it) }
            put("message", notification.message)
            put("channel", notification.channel.name.lowercase())
            put("trigger_event", notification.triggerEvent.name.lowercase())
        }
    }

    fun createLedgerPayload(entry: com.sanibonani.save.domain.model.LedgerEntry): JsonObject {
        return buildJsonObject {
            put("group_id", entry.groupId)
            put("amount", entry.amount.amount.toPlainString())
            put("balance_after", entry.balanceAfter.amount.toPlainString())
            put("description", entry.description)
            put("category", entry.category)
            entry.transactionId?.let { put("transaction_id", it) }
            put("is_platform", entry.isPlatform)
        }
    }

    fun createAuditLogPayload(log: com.sanibonani.save.domain.model.AuditLog): JsonObject {
        return buildJsonObject {
            put("action", log.action)
            put("target_group_id", log.targetGroupId ?: "")
            put("target_member_id", log.targetMemberId ?: "")
            log.details?.let { put("details", it) }
        }
    }

    fun createBankTransactionPayload(tx: com.sanibonani.save.domain.model.BankTransaction): JsonObject {
        return buildJsonObject {
            put("group_id", tx.groupId)
            put("bank_reference", tx.bankReference)
            put("amount", tx.amount.amount.toPlainString())
            put("transaction_date", tx.transactionDate)
            put("description", tx.description ?: "")
            put("status", tx.status.name.lowercase())
            put("reconciled_with", tx.reconciledWith ?: "")
        }
    }
}
