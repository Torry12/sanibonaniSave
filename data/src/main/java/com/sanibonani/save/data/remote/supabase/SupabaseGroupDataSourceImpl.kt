package com.sanibonani.save.data.remote.supabase

import com.sanibonani.save.data.datasource.RemoteGroupDataSource
import com.sanibonani.save.data.remote.PostgrestColumns
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.GroupSettings
import com.sanibonani.save.domain.model.AdminFeeState
import com.sanibonani.save.domain.model.Money
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

import com.sanibonani.save.domain.repository.system.DataConnectionProtocol
import com.sanibonani.save.domain.utils.safeRunCatching

@Singleton
class SupabaseGroupDataSourceImpl @Inject constructor(
    protocol: DataConnectionProtocol
) : BaseSupabaseDataSource(protocol), RemoteGroupDataSource {

    override suspend fun getPublicGroups(): Result<List<Group>> = safeRunCatching {
        safeRunCatching {
            client.postgrest["groups"].select(columns = Columns.raw(PostgrestColumns.GROUPS_SAFE)) {
                filter {
                    eq("is_public", true)
                    eq("registration_paid", true)
                }
            }.decodeList<Group>()
        }.getOrElse {
            client.postgrest["groups"].select {
                filter {
                    eq("is_public", true)
                    eq("registration_paid", true)
                }
            }.decodeList<Group>()
        }
    }

    override suspend fun getGroupById(id: String): Result<Group> = safeRunCatching {
        safeRunCatching {
            client.postgrest["groups"].select(columns = Columns.raw(PostgrestColumns.GROUPS_SAFE)) {
                filter { eq("id", id) }
            }.decodeSingle<Group>()
        }.getOrElse {
            client.postgrest["groups"].select {
                filter { eq("id", id) }
            }.decodeSingle<Group>()
        }
    }

    override suspend fun getGroupsByAdmin(adminId: String): Result<List<Group>> = safeRunCatching {
        safeRunCatching {
            client.postgrest["groups"].select(columns = Columns.raw(PostgrestColumns.GROUPS_SAFE)) {
                filter { eq("admin_user_id", adminId) }
            }.decodeList<Group>()
        }.getOrElse {
            client.postgrest["groups"].select {
                filter { eq("admin_user_id", adminId) }
            }.decodeList<Group>()
        }
    }

    override suspend fun createGroup(group: Group): Result<String> = safeRunCatching {
        val payload = buildJsonObject {
            group.id?.let { put("id", it) }
            put("name", group.name)
            put("type", group.type.name.lowercase())
            put("province", group.province)
            put("city", group.city)
            put("township", group.township)
            put("description", group.description)
            put("logo_emoji", group.logoEmoji)
            put("joining_fee", group.joiningFee.amount.toPlainString())
            put("monthly_contribution", group.monthlyContribution.amount.toPlainString())
            put("late_fee", group.lateFee.amount.toPlainString())
            put("late_fee_grace_days", group.lateFeeGraceDays)
            put("probation_months", group.probationMonths)
            put("payment_due_day", group.paymentDueDay)
            put("max_members", group.maxMembers)
            put("current_members", group.currentMembers)
            put("is_public", group.isPublic)
            put("allow_partial_payment", group.allowPartialPayment)
            put("auto_suspend_after", group.autoSuspendAfter)
            group.bankName?.let { put("bank_name", it) }
            group.accountNumber?.let { put("account_number", it) }
            group.branchCode?.let { put("branch_code", it) }
            put("account_type", group.accountType)
            put("balance", group.balance.amount.toPlainString())
            group.adminUserId?.let { put("admin_user_id", it) }
            put("fee_status", "pending_activation")
            put("registration_paid", false)
            group.latitude?.let { put("latitude", it) }
            group.longitude?.let { put("longitude", it) }
            group.geohash?.let { put("geohash", it) }
            group.createdAt?.let { put("created_at", it) }
            put("is_platform_suspended", false)
            put("goal_amount", group.goalAmount.amount.toPlainString())
            put("period_months", group.periodMonths)
            group.maxBeneficiaries?.let { put("max_beneficiaries", it) }
            group.beneficiaryIncreasePct?.let { put("beneficiary_increase_pct", it) }
        }

        val created = client.postgrest["groups"].insert(payload) {
            select(columns = Columns.raw(PostgrestColumns.GROUPS_SAFE))
        }.decodeSingle<Group>()
        created.id ?: throw Exception("Failed to get created group ID")
    }

    override suspend fun updateGroup(group: Group): Result<Unit> = safeRunCatching {
        client.postgrest["groups"].update(group) {
            filter { eq("id", group.id ?: throw IllegalStateException("Group ID is required for update")) }
        }
    }

    override suspend fun updateGroupSettings(groupId: String, settings: GroupSettings): Result<Unit> = safeRunCatching {
        val payload = buildJsonObject {
            put("joining_fee", Money(settings.joiningFee).amount.toPlainString())
            put("monthly_contribution", Money(settings.monthlyContribution).amount.toPlainString())
            put("late_fee", Money(settings.lateFee).amount.toPlainString())
            put("late_fee_grace_days", settings.lateFeeGraceDays.toIntOrNull() ?: 5)
            put("probation_months", settings.probationMonths.toIntOrNull() ?: 3)
            put("payment_due_day", settings.paymentDueDay.toIntOrNull() ?: 28)
            put("max_members", settings.maxMembers.toIntOrNull() ?: 50)
            put("allow_partial_payment", settings.allowPartialPayment)
            put("auto_suspend_after", settings.autoSuspendAfter.toIntOrNull() ?: 2)
            put("bank_name", settings.bankName)
            put("account_number", settings.accountNumber)
            put("branch_code", settings.branchCode)
            put("account_type", settings.accountType)
            put("goal_amount", Money(settings.goalAmount).amount.toPlainString())
            put("period_months", settings.periodMonths.toIntOrNull() ?: 12)
            settings.maxBeneficiaries.toIntOrNull()?.let { if (it > 0) put("max_beneficiaries", it) }
            settings.beneficiaryIncreasePct.toDoubleOrNull()?.let { if (it > 0.0) put("beneficiary_increase_pct", it) }
            put("rosca_rotation_method", settings.rotationMethod.name.lowercase())
        }
        client.postgrest["groups"].update(payload) { filter { eq("id", groupId) } }
    }

    override suspend fun updateGroupSettings(groupId: String, settings: Map<String, Any>): Result<Unit> = safeRunCatching {
        val payload = buildJsonObject {
            settings.forEach { (k, v) ->
                when (v) {
                    is String -> put(k, v)
                    is Number -> put(k, v)
                    is Boolean -> put(k, v)
                    else -> put(k, v.toString())
                }
            }
        }
        client.postgrest["groups"].update(payload) { filter { eq("id", groupId) } }
    }

    override suspend fun activateGroup(groupId: String, txId: String?): Result<Unit> = safeRunCatching {
        client.postgrest["groups"].update(buildJsonObject {
            put("registration_paid", true)
            put("fee_status", "paid")
            put("is_platform_suspended", false)
        }) { filter { eq("id", groupId) } }
    }

    override suspend fun updateFeeStatus(groupId: String, status: AdminFeeState): Result<Unit> = safeRunCatching {
        val statusStr = status.name.lowercase()
        client.postgrest["groups"].update(buildJsonObject { put("fee_status", statusStr) }) {
            filter { eq("id", groupId) }
        }
    }

    override suspend fun incrementGroupBalance(groupId: String, amount: Money, memberId: String?, transactionId: String?): Result<Money> = safeRunCatching {
        val rpcParams = buildJsonObject {
            put("p_group_id", groupId)
            put("p_amount", amount.amount.toPlainString())
            memberId?.let { put("p_member_id", it) }
            transactionId?.let { put("p_transaction_id", it) }
        }
        client.postgrest.rpc("increment_group_balance", rpcParams).decodeAs<Money>()
    }

    override suspend fun recordDisbursement(groupId: String, amount: Money, description: String, category: String, transactionId: String?, memberId: String?): Result<Money> = safeRunCatching {
        val rpcParams = buildJsonObject {
            put("p_group_id", groupId)
            put("p_amount", amount.amount.toPlainString())
            put("p_description", description)
            put("p_category", category)
            transactionId?.let { put("p_transaction_id", it) }
            memberId?.let { put("p_member_id", it) }
        }
        client.postgrest.rpc("record_disbursement_v1", rpcParams).decodeAs<Money>()
    }

    override suspend fun deleteGroup(groupId: String): Result<Unit> = safeRunCatching {
        client.postgrest["groups"].delete {
            filter { eq("id", groupId) }
        }
    }
}
