package com.sanibonani.save.data.repository

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.data.remote.PostgrestColumns
import com.sanibonani.save.data.utils.logAndGetMessage
import com.sanibonani.save.domain.event.EventBus
import com.sanibonani.save.domain.event.LedgerEntryCreatedEvent
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.GroupRepository
import com.sanibonani.save.domain.repository.StorageRepository
import com.sanibonani.save.domain.repository.SupabaseRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.LocalDateTime
import javax.inject.Inject

class GroupRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
    private val supabaseRepo: SupabaseRepository,
    private val db: SanibonaniDatabase,
    private val storageRepo: StorageRepository
) : BaseRepository("GroupRepository"), GroupRepository {

    override suspend fun uploadConstitution(
        groupId: String,
        fileBytes: ByteArray,
        fileName: String
    ): Result<String> = retryWithExponentialBackoff {
        runCatching {
            fun sanitizeFileName(input: String): String {
                val base = input
                    .substringAfterLast('\\')
                    .substringAfterLast('/')
                    .trim()

                val cleaned = base.replace(Regex("[^A-Za-z0-9._-]"), "_")
                return cleaned.ifBlank { "constitution_${System.currentTimeMillis()}.pdf" }
            }

            val safeName = sanitizeFileName(fileName)
            val timestamp = System.currentTimeMillis()
            val fileNameWithTimestamp = if (safeName.contains(".")) {
                val name = safeName.substringBeforeLast(".")
                val ext = safeName.substringAfterLast(".")
                "${name}_$timestamp.$ext"
            } else {
                "${safeName}_$timestamp"
            }

            val path = "$groupId/$fileNameWithTimestamp"

            AppLogger.d(tag, "📤 Uploading constitution to constitutions/$path (${fileBytes.size} bytes)")
            val uploadedPath = storageRepo.uploadFile("constitutions", path, fileBytes).getOrThrow()
            val url = storageRepo.getPublicUrl("constitutions", uploadedPath)

            // Update the group record with the new URL and reset status to PENDING
            supabase.from("groups").update(buildJsonObject {
                put("constitution_url", url)
                put("constitution_status", DocumentStatus.PENDING.name.lowercase())
            }) {
                filter { eq("id", groupId) }
            }

            // Update local Room cache
            db.groupDao().getGroupById(groupId)?.let { local ->
                db.groupDao().upsertGroup(local.copy(
                    constitutionUrl = url,
                    constitutionStatus = DocumentStatus.PENDING
                ))
            }

            url
        }
    }

    override fun getPublicGroups(): Flow<Result<List<Group>>> = observeAndSync(
        dbFlow = db.groupDao().observePublicGroups(),
        mapper = { it.toModel() },
        toEntity = { it.toEntity() },
        networkFetch = { 
            supabase.postgrest["groups"].select(columns = Columns.raw(PostgrestColumns.GROUPS_SAFE)) { 
                filter { 
                    eq("is_public", true)
                    eq("registration_paid", true)
                }
            }.decodeList<Group>() 
        },
        cacheSync = { list -> db.groupDao().syncPublicGroups(list) }
    )

    override suspend fun getGroupById(id: String): Result<Group> = retryWithExponentialBackoff {
        runCatching {
            val group = supabase.postgrest["groups"].select(columns = Columns.raw(PostgrestColumns.GROUPS_SAFE)) { 
                filter { eq("id", id) } 
            }.decodeSingle<Group>()
            db.groupDao().upsertGroup(group.toEntity())
            group
        }.recoverCatching { exception ->
            db.groupDao().getGroupById(id)?.toModel()
                ?: run {
                    AppLogger.e(tag, "Network + local fallback failed for group $id", exception)
                    throw exception
                }
        }
    }

    override fun observeGroup(groupId: String): Flow<Result<Group?>> = channelFlow {
        val dbJob = launch {
            observeAndSyncItem(
                dbFlow = db.groupDao().observeGroupById(groupId),
                mapper = { it.toModel() },
                toEntity = { it.toEntity() },
                networkFetch = {
                    supabase.postgrest["groups"].select(columns = Columns.raw(PostgrestColumns.GROUPS_SAFE)) {
                        filter { eq("id", groupId) }
                    }.decodeSingle<Group>()
                },
                cacheSync = { entity -> db.groupDao().upsertGroup(entity) }
            ).collect { send(it) }
        }

        val channel = supabase.realtime.channel("group_rt_$groupId")
        val changes = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "groups"
        }

        val rtJob = launch {
            changes.collect { update ->
                if (update.record["id"]?.jsonPrimitive?.content == groupId) {
                    getGroupById(groupId)
                }
            }
        }

        channel.subscribe()
        awaitClose {
            dbJob.cancel()
            rtJob.cancel()
            launch { channel.unsubscribe() }
        }
    }

    override fun observeGroupsByAdmin(adminId: String): Flow<Result<List<Group>>> = observeAndSync(
        dbFlow = db.groupDao().observeGroupsByAdmin(adminId),
        mapper = { it.toModel() },
        toEntity = { it.toEntity() },
        networkFetch = {
            supabase.postgrest["groups"].select(columns = Columns.raw(PostgrestColumns.GROUPS_SAFE)) {
                filter { eq("admin_user_id", adminId) }
            }.decodeList<Group>()
        },
        cacheSync = { list ->
            db.groupDao().upsertGroups(list)
        }
    )

    override suspend fun createGroup(
        group: Group
    ): Result<String> = runCatching {
        val finalGroup = group.copy(
            feeStatus = AdminFeeState.PENDING_ACTIVATION,
            registrationPaid = false,
            currentMembers = 0,
            balance = 0.0
        )

        val insertData = buildJsonObject {
            put("name", finalGroup.name)
            put("type", finalGroup.type.name.lowercase())
            put("province", finalGroup.province)
            put("city", finalGroup.city)
            put("township", finalGroup.township)
            put("description", finalGroup.description)
            put("logo_emoji", finalGroup.logoEmoji)
            put("joining_fee", finalGroup.joiningFee)
            put("monthly_contribution", finalGroup.monthlyContribution)
            put("late_fee", finalGroup.lateFee)
            put("late_fee_grace_days", finalGroup.lateFeeGraceDays)
            put("probation_months", finalGroup.probationMonths)
            put("payment_due_day", finalGroup.paymentDueDay)
            put("max_members", finalGroup.maxMembers)
            put("current_members", finalGroup.currentMembers)
            put("is_public", finalGroup.isPublic)
            put("allow_partial_payment", finalGroup.allowPartialPayment)
            put("auto_suspend_after", finalGroup.autoSuspendAfter)
            finalGroup.bankName?.let { put("bank_name", it) }
            finalGroup.accountNumber?.let { put("account_number", it) }
            finalGroup.branchCode?.let { put("branch_code", it) }
            put("account_type", finalGroup.accountType)
            put("balance", finalGroup.balance)
            finalGroup.adminUserId?.let { put("admin_user_id", it) }
            put("fee_status", "pending_activation")
            put("registration_paid", false)
            finalGroup.latitude?.let { put("latitude", it) }
            finalGroup.longitude?.let { put("longitude", it) }
            finalGroup.geohash?.let { put("geohash", it) }
            put("is_platform_suspended", false)
            put("goal_amount", finalGroup.goalAmount)
            put("period_months", finalGroup.periodMonths)
            finalGroup.maxBeneficiaries?.let { put("max_beneficiaries", it) }
            finalGroup.beneficiaryIncreasePct?.let { put("beneficiary_increase_pct", it) }
        }

        val created = supabase.postgrest["groups"].insert(insertData) {
            select(columns = Columns.raw(PostgrestColumns.GROUPS_SAFE))
        }.decodeSingle<Group>()
        val createdGroupId = created.id ?: throw Exception("Failed to get created group ID")
        
        try {
            val nowStr = LocalDateTime.now().toString()
            supabase.postgrest["platform_fees"].insert(PlatformFee(
                groupId = createdGroupId,
                feeType = "registration",
                amount = PlatformFees.REGISTRATION,
                status = AdminFeeState.DUE,
                dueDate = nowStr
            ))
        } catch (e: Exception) {
            val userMsg = e.logAndGetMessage(tag)
            AppLogger.w(tag, "Platform fee initialization failed: $userMsg")
        }

        try {
            db.groupDao().upsertGroup(created.toEntity())
            AppLogger.d(tag, "✅ Group saved to local database: $createdGroupId")
        } catch (e: Exception) {
            val userMsg = e.logAndGetMessage(tag)
            AppLogger.e(tag, "❌ Failed to save group to local database: $userMsg", e)
            throw IllegalStateException(userMsg)
        }
        createdGroupId
    }

    override suspend fun incrementGroupBalance(groupId: String, amount: Double): Result<Double> = retryWithExponentialBackoff {
        runCatching {
            val rpcParams = buildJsonObject {
                put("p_group_id", groupId)
                put("p_amount", amount)
            }
            val newBalance = supabase.postgrest.rpc("increment_group_balance", rpcParams)
                .decodeAs<Double>()
            
            // Update local Room cache
            db.groupDao().getGroupById(groupId)?.let { local ->
                db.groupDao().upsertGroup(local.copy(balance = newBalance))
            }

            // Emit event for real-time audit logs
            EventBus.emit(
                LedgerEntryCreatedEvent(
                    LedgerEntry(
                        groupId = groupId,
                        amount = amount,
                        balanceAfter = newBalance,
                        description = "Atomic balance update",
                        category = "adjustment",
                        transactionId = null
                    )
                )
            )
            
            newBalance
        }
    }

    override suspend fun recordDisbursement(
        groupId: String,
        amount: Double,
        description: String,
        category: String,
        transactionId: String?
    ): Result<Double> = retryWithExponentialBackoff {
        runCatching {
            val rpcParams = buildJsonObject {
                put("p_group_id", groupId)
                put("p_amount", amount)
                put("p_description", description)
                put("p_category", category)
                transactionId?.let { put("p_transaction_id", it) }
            }
            val newBalance = supabase.postgrest.rpc("record_disbursement_v1", rpcParams)
                .decodeAs<Double>()

            // Update local Room cache
            db.groupDao().getGroupById(groupId)?.let { local ->
                db.groupDao().upsertGroup(local.copy(balance = newBalance))
            }

            // Emit event for side effects (Audit logs)
            EventBus.emit(
                LedgerEntryCreatedEvent(
                    LedgerEntry(
                        groupId = groupId,
                        amount = -amount, // Outflow
                        balanceAfter = newBalance,
                        description = description,
                        category = category,
                        transactionId = transactionId
                    )
                )
            )

            newBalance
        }
    }

    override suspend fun updateGroupSettings(groupId: String, settings: GroupSettings): Result<Unit> = runCatching {
        val updateData = buildJsonObject {
            put("joining_fee", settings.joiningFee.toDoubleOrNull() ?: 0.0)
            put("monthly_contribution", settings.monthlyContribution.toDoubleOrNull() ?: 0.0)
            put("late_fee", settings.lateFee.toDoubleOrNull() ?: 0.0)
            put("late_fee_grace_days", settings.lateFeeGraceDays.toIntOrNull() ?: 5)
            put("probation_months", settings.probationMonths)
            put("payment_due_day", settings.paymentDueDay)
            put("max_members", settings.maxMembers.toIntOrNull() ?: 50)
            put("allow_partial_payment", settings.allowPartialPayment)
            put("auto_suspend_after", settings.autoSuspendAfter)
            put("bank_name", settings.bankName)
            put("account_number", settings.accountNumber)
            put("branch_code", settings.branchCode)
            put("account_type", settings.accountType)
            put("goal_amount", settings.goalAmount.toDoubleOrNull() ?: 10000.0)
            put("period_months", settings.periodMonths.toIntOrNull() ?: 12)
            settings.maxBeneficiaries.toIntOrNull()?.let { if (it > 0) put("max_beneficiaries", it) }
            settings.beneficiaryIncreasePct.toDoubleOrNull()?.let { if (it > 0.0) put("beneficiary_increase_pct", it) }
            // ROSCA — stored as lowercase snake_case to satisfy DB CHECK constraint
            put("rosca_rotation_method", settings.rotationMethod.name.lowercase())
        }

        supabase.postgrest["groups"].update(updateData) { filter { eq("id", groupId) } }
        getGroupById(groupId)
    }

    override suspend fun updateGroupSettings(groupId: String, settings: Map<String, Any>): Result<Unit> = runCatching {
        val jsonSettings = buildJsonObject {
            settings.forEach { (k, v) ->
                when (v) {
                    is String -> put(k, v)
                    is Number -> put(k, v)
                    is Boolean -> put(k, v)
                    else -> put(k, v.toString())
                }
            }
        }
        supabase.postgrest["groups"].update(jsonSettings) { filter { eq("id", groupId) } }
        getGroupById(groupId)
    }

    override fun observeGroupFeeStatus(groupId: String): Flow<AdminFeeState> = callbackFlow {
        val channel = supabase.realtime.channel("public:groups:id=eq.$groupId")
        val changes = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public")
        
        val job = launch {
            changes.collect { update ->
                val newStatus = update.record["fee_status"]?.jsonPrimitive?.content
                if (newStatus != null) {
                    val decoded = AdminFeeState.entries.find { 
                        it.name.equals(newStatus, true) || 
                        it.toString().lowercase() == newStatus.lowercase() 
                    } ?: AdminFeeState.DUE
                    trySend(decoded)
                }
            }
        }
        
        channel.subscribe()
        awaitClose { 
            job.cancel()
            launch { channel.unsubscribe() }
        }
    }

    override suspend fun activateGroup(groupId: String, txId: String?): Result<Unit> = runCatching {
        // 1. Mark group as active and registration paid in network
        supabase.postgrest["groups"].update(buildJsonObject {
            put("registration_paid", true)
            put("fee_status", "paid")
            put("is_platform_suspended", false)
        }) { filter { eq("id", groupId) } }
        
        // Refresh local cache IMMEDIATELY after update
        getGroupById(groupId)
        
        // 2. Process Platform Registration Fee (Group -> Platform)
        payPlatformFee(groupId, PlatformFees.REGISTRATION, "registration", txId)
            .onFailure { AppLogger.w(tag, "Platform fee payment failed during activation: ${it.message}") }

        val group = getGroupById(groupId).getOrThrow()
        
        // 3. Finalize Admin Member Registration & Credit their first contribution
        group.adminUserId?.let { adminId ->
            try {
                // Find the admin member record.
                // Use decodeList().firstOrNull() instead of decodeSingleOrNull() so that
                // environments returning a JSON array (e.g., mock engine in tests) are
                // handled correctly.  decodeSingleOrNull requires Access:vnd.pgrst.object
                // which the mock doesn't honour.
                val memberResult = supabase.postgrest["members"].select {
                    filter {
                        eq("group_id", groupId)
                        eq("user_id", adminId)
                    }
                }.decodeList<Member>().firstOrNull()

                memberResult?.let { adminMember ->
                    val isPending = adminMember.status == MemberStatus.PENDING_PAYMENT
                    val nowStr = LocalDateTime.now().toString()

                    // Check if admin already has a first contribution recorded
                    val existingFirstContrib = supabase.postgrest["contributions"].select {
                        filter {
                            eq("member_id", adminMember.id ?: "")
                            eq("group_id", groupId)
                            eq("status", "paid")
                            eq("type", "contribution")
                        }
                    }.decodeList<Contribution>()

                    // Credit the group creation fee as the admin's FIRST MONTHLY CONTRIBUTION
                    // This goes to the group balance and counts towards their contributions
                    if (existingFirstContrib.isEmpty() && group.monthlyContribution > 0) {
                        val monthlyAmount = group.monthlyContribution

                        // Use RPC for contribution - this adds to group balance atomically
                        val rpcParams = buildJsonObject {
                            put("p_member_id", adminMember.id ?: "")
                            put("p_group_id", groupId)
                            put("p_amount", monthlyAmount)
                            put("p_due_date", nowStr.substring(0, 10)) // Current date as due date
                            put("p_paid_at", nowStr)
                            put("p_status", "paid")
                            put("p_type", "contribution")
                            put("p_tx_id", txId ?: "first_contrib_auto_${System.currentTimeMillis()}")
                        }
                        supabase.postgrest.rpc("record_contribution_v1", rpcParams)

                        AppLogger.d(tag, "✅ Credited first monthly contribution of R$monthlyAmount for admin member")
                    }

                    // Also record platform registration fee as separate record (for audit purposes)
                    val registrationFee = PlatformFees.REGISTRATION
                    val existingRegContribs = supabase.postgrest["contributions"].select {
                        filter {
                            eq("member_id", adminMember.id ?: "")
                            eq("group_id", groupId)
                            eq("status", "paid")
                            eq("type", "registration_contribution")
                        }
                    }.decodeList<Contribution>()

                    if (existingRegContribs.isEmpty()) {
                        // Direct INSERT for registration contribution - does NOT add to group balance
                        // The registration fee is a platform fee, not a group contribution
                        val insertData = buildJsonObject {
                            put("member_id", adminMember.id ?: "")
                            put("group_id", groupId)
                            put("amount", registrationFee)
                            put("due_date", adminMember.joinedAt ?: nowStr)
                            put("paid_at", nowStr)
                            put("status", "paid")
                            put("type", "registration_contribution")
                            put("transaction_id", txId ?: "reg_auto_credit_${System.currentTimeMillis()}")
                        }
                        supabase.postgrest["contributions"].insert(insertData)
                    }
                    
                    // Credit joining fee if applicable - this DOES go to group balance
                    if (group.joiningFee > 0) {
                        val existingJoining = supabase.postgrest["contributions"].select {
                            filter {
                                eq("member_id", adminMember.id ?: "")
                                eq("group_id", groupId)
                                eq("status", "paid")
                                eq("type", "joining_fee")
                            }
                        }.decodeList<Contribution>()

                        if (existingJoining.isEmpty()) {
                            // Use RPC for joining fee - this adds to group balance
                            val rpcParams = buildJsonObject {
                                put("p_member_id", adminMember.id ?: "")
                                put("p_group_id", groupId)
                                put("p_amount", group.joiningFee)
                                put("p_due_date", adminMember.joinedAt ?: nowStr)
                                put("p_paid_at", nowStr)
                                put("p_status", "paid")
                                put("p_type", "joining_fee")
                                put("p_tx_id", "joining_auto_credit_${System.currentTimeMillis()}")
                            }
                            supabase.postgrest.rpc("record_contribution_v1", rpcParams)
                        }
                    }
                    
                    // Update admin member status to ACTIVE/PROBATION if they were pending
                    if (isPending) {
                        val nextStatus = if (group.probationMonths > 0) MemberStatus.PROBATION else MemberStatus.ACTIVE
                        supabase.postgrest["members"].update(buildJsonObject { 
                            put("status", nextStatus.name.lowercase())
                        }) {
                            filter { eq("id", adminMember.id ?: "") }
                        }
                        
                        // Sync local member cache
                        db.memberDao().upsertMember(adminMember.copy(status = nextStatus).toEntity())
                    }
                }
            } catch (e: Exception) {
                val userMsg = e.logAndGetMessage(tag)
                AppLogger.w(tag, "Failed to finalize admin member: $userMsg")
            }
        }

        // 4. Update and sync final group state locally
        val updatedGroup = getGroupById(groupId).getOrThrow()
        db.groupDao().upsertGroup(updatedGroup.toEntity())
    }

    override suspend fun updateFeeStatus(groupId: String, status: AdminFeeState): Result<Unit> = runCatching {
        val statusStr = when(status) {
            AdminFeeState.PAID -> "paid"
            AdminFeeState.DUE -> "due"
            AdminFeeState.OVERDUE -> "overdue"
            AdminFeeState.WARNING -> "warning"
            AdminFeeState.SUSPENDED -> "suspended"
            AdminFeeState.PENDING_ACTIVATION -> "pending_activation"
        }
        supabase.postgrest["groups"].update(buildJsonObject { put("fee_status", statusStr) }) {
            filter { eq("id", groupId) } 
        }
        
        val group = getGroupById(groupId).getOrThrow()
        db.groupDao().upsertGroup(group.copy(feeStatus = status).toEntity())
    }

    override suspend fun payPlatformFee(
        groupId: String,
        amount: Double,
        feeType: String,
        txId: String?
    ): Result<Unit> = retryWithExponentialBackoff {
        runCatching {
            val rpcParams = buildJsonObject {
                put("p_group_id", groupId)
                put("p_amount", amount)
                put("p_fee_type", feeType)
                txId?.let { put("p_tx_id", it) }
            }
            supabase.postgrest.rpc("pay_platform_fee_v1", rpcParams)
            
            // Refresh local group state since balance decreased
            getGroupById(groupId)
            Unit
        }
    }

    override suspend fun updateGroup(group: Group): Result<Unit> {
        return try {
            supabase.from("groups").update(group) {
                filter { eq("id", group.id ?: throw IllegalStateException("Group ID is required for update")) }
            }
            db.groupDao().upsertGroup(group.toEntity())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCurrentUserEmail(): String? = supabaseRepo.currentSessionEmail

    override suspend fun getGroupsByAdmin(adminId: String): Result<List<Group>> = runCatching {
        val groups = supabase.postgrest["groups"].select(columns = Columns.raw(PostgrestColumns.GROUPS_SAFE)) {
            filter { eq("admin_user_id", adminId) }
        }.decodeList<Group>()
        db.groupDao().upsertGroups(groups.map { it.toEntity() })
        groups
    }

}
