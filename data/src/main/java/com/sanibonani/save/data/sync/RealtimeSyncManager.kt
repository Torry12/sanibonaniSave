package com.sanibonani.save.data.sync

import com.sanibonani.save.data.local.SanibonaniDatabase
import com.sanibonani.save.data.logging.AppLogger
import com.sanibonani.save.data.repository.toEntity
import com.sanibonani.save.data.repository.toLedgerEntity
import com.sanibonani.save.domain.model.BeneficiaryPayoutClaim
import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.LoanRepayment
import com.sanibonani.save.domain.model.Payment
import com.sanibonani.save.domain.model.PayoutRequest
import com.sanibonani.save.domain.model.AppNotification
import com.sanibonani.save.domain.model.Beneficiary
import com.sanibonani.save.domain.model.LedgerEntry
import com.sanibonani.save.domain.model.MemberDocument
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealtimeSyncManager @Inject constructor(
    private val supabase: SupabaseClient,
    private val db: SanibonaniDatabase
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tag = "RealtimeSyncManager"

    fun start() {
        AppLogger.d(tag, "Starting realtime subscriptions for all entity types...")
        scope.launch { subscribeContributions() }
        scope.launch { subscribePayments() }
        scope.launch { subscribeLoans() }
        scope.launch { subscribeLoanRepayments() }
        scope.launch { subscribeLedger() }
        scope.launch { subscribeNotifications() }
        scope.launch { subscribePayouts() }
        scope.launch { subscribeBeneficiaries() }
        scope.launch { subscribeMemberDocuments() }
        scope.launch { subscribeBeneficiaryClaims() }
        AppLogger.d(tag, "All realtime subscriptions started")
    }

    fun stop() {
        scope.cancel()
        AppLogger.d(tag, "Realtime subscriptions stopped")
    }

    private suspend fun subscribeContributions() {
        try {
            val name = "rt_contributions"
            val channel = supabase.realtime.channel(name)
            val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") { table = "contributions" }
            val updates = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") { table = "contributions" }
            merge(inserts, updates).collect { action ->
                val groupId = action.record["group_id"]?.jsonPrimitive?.content ?: return@collect
                runCatching {
                    val data = supabase.postgrest["contributions"].select {
                        filter { eq("group_id", groupId) }
                    }.decodeList<Contribution>()
                    db.contributionDao().syncGroupContributions(groupId, data.map { it.toEntity() })
                }
            }
            channel.subscribe()
            AppLogger.d(tag, "Subscribed to contributions")
        } catch (e: Exception) {
            AppLogger.w(tag, "contributions subscription failed: ${e.message}")
        }
    }

    private suspend fun subscribePayments() {
        try {
            val name = "rt_payments"
            val channel = supabase.realtime.channel(name)
            val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") { table = "payments" }
            val updates = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") { table = "payments" }
            merge(inserts, updates).collect { action ->
                val groupId = action.record["group_id"]?.jsonPrimitive?.content ?: return@collect
                runCatching {
                    val data = supabase.postgrest["payments"].select {
                        filter { eq("group_id", groupId) }
                    }.decodeList<Payment>()
                    db.paymentDao().syncPayments(groupId, data.map { it.toEntity() })
                }
            }
            channel.subscribe()
            AppLogger.d(tag, "Subscribed to payments")
        } catch (e: Exception) {
            AppLogger.w(tag, "payments subscription failed: ${e.message}")
        }
    }

    private suspend fun subscribeLoans() {
        try {
            val name = "rt_loans"
            val channel = supabase.realtime.channel(name)
            val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") { table = "loans" }
            val updates = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") { table = "loans" }
            merge(inserts, updates).collect { action ->
                val loanId = action.record["id"]?.jsonPrimitive?.content ?: return@collect
                runCatching {
                    val loan = supabase.postgrest["loans"].select {
                        filter { eq("id", loanId) }
                    }.decodeSingle<Loan>()
                    db.loanDao().upsertLoan(loan.toEntity())
                }
            }
            channel.subscribe()
            AppLogger.d(tag, "Subscribed to loans")
        } catch (e: Exception) {
            AppLogger.w(tag, "loans subscription failed: ${e.message}")
        }
    }

    private suspend fun subscribeLoanRepayments() {
        try {
            val name = "rt_loan_repayments"
            val channel = supabase.realtime.channel(name)
            val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") { table = "loan_repayments" }
            val updates = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") { table = "loan_repayments" }
            merge(inserts, updates).collect { action ->
                val loanId = action.record["loan_id"]?.jsonPrimitive?.content ?: return@collect
                runCatching {
                    val data = supabase.postgrest["loan_repayments"].select {
                        filter { eq("loan_id", loanId) }
                    }.decodeList<LoanRepayment>()
                    db.loanDao().deleteRepaymentsByLoanId(loanId)
                    db.loanDao().upsertRepayments(data.map { it.toEntity() })
                }
            }
            channel.subscribe()
            AppLogger.d(tag, "Subscribed to loan_repayments")
        } catch (e: Exception) {
            AppLogger.w(tag, "loan_repayments subscription failed: ${e.message}")
        }
    }

    private suspend fun subscribeLedger() {
        try {
            val name = "rt_ledger"
            val channel = supabase.realtime.channel(name)
            val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") { table = "group_ledger" }
            val updates = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") { table = "group_ledger" }
            merge(inserts, updates).collect { action ->
                val groupId = action.record["group_id"]?.jsonPrimitive?.content ?: return@collect
                runCatching {
                    val data = supabase.postgrest["group_ledger"].select(columns = Columns.raw("*")) {
                        filter { eq("group_id", groupId) }
                    }.decodeList<LedgerEntry>()
                    db.ledgerDao().syncLedger(groupId, data.map { it.toLedgerEntity() })
                }
            }
            channel.subscribe()
            AppLogger.d(tag, "Subscribed to group_ledger")
        } catch (e: Exception) {
            AppLogger.w(tag, "group_ledger subscription failed: ${e.message}")
        }
    }

    private suspend fun subscribeNotifications() {
        try {
            val name = "rt_notifications"
            val channel = supabase.realtime.channel(name)
            val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") { table = "notifications" }
            val updates = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") { table = "notifications" }
            merge(inserts, updates).collect { action ->
                val groupId = action.record["group_id"]?.jsonPrimitive?.content ?: return@collect
                runCatching {
                    val data = supabase.postgrest["notifications"].select {
                        filter { eq("group_id", groupId) }
                    }.decodeList<AppNotification>()
                    db.notificationDao().syncNotifications(groupId, data.map { it.toEntity() })
                }
            }
            channel.subscribe()
            AppLogger.d(tag, "Subscribed to notifications")
        } catch (e: Exception) {
            AppLogger.w(tag, "notifications subscription failed: ${e.message}")
        }
    }

    private suspend fun subscribePayouts() {
        try {
            val name = "rt_payouts"
            val channel = supabase.realtime.channel(name)
            val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") { table = "payouts" }
            val updates = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") { table = "payouts" }
            merge(inserts, updates).collect { action ->
                val payoutId = action.record["id"]?.jsonPrimitive?.content ?: return@collect
                runCatching {
                    val payout = supabase.postgrest["payouts"].select {
                        filter { eq("id", payoutId) }
                    }.decodeSingle<PayoutRequest>()
                    db.payoutDao().upsertPayout(payout.toEntity())
                }
            }
            channel.subscribe()
            AppLogger.d(tag, "Subscribed to payouts")
        } catch (e: Exception) {
            AppLogger.w(tag, "payouts subscription failed: ${e.message}")
        }
    }

    private suspend fun subscribeBeneficiaries() {
        try {
            val name = "rt_beneficiaries"
            val channel = supabase.realtime.channel(name)
            val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") { table = "beneficiaries" }
            val updates = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") { table = "beneficiaries" }
            merge(inserts, updates).collect { action ->
                val memberId = action.record["member_id"]?.jsonPrimitive?.content ?: return@collect
                runCatching {
                    val data = supabase.postgrest["beneficiaries"].select {
                        filter { eq("member_id", memberId) }
                    }.decodeList<Beneficiary>()
                    db.beneficiaryDao().syncBeneficiaries(memberId, data.map { it.toEntity() })
                }
            }
            channel.subscribe()
            AppLogger.d(tag, "Subscribed to beneficiaries")
        } catch (e: Exception) {
            AppLogger.w(tag, "beneficiaries subscription failed: ${e.message}")
        }
    }

    private suspend fun subscribeMemberDocuments() {
        try {
            val name = "rt_member_documents"
            val channel = supabase.realtime.channel(name)
            val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") { table = "member_documents" }
            val updates = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") { table = "member_documents" }
            merge(inserts, updates).collect { action ->
                val memberId = action.record["member_id"]?.jsonPrimitive?.content ?: return@collect
                runCatching {
                    val data = supabase.postgrest["member_documents"].select {
                        filter { eq("member_id", memberId) }
                    }.decodeList<MemberDocument>()
                    db.memberDocumentDao().syncDocuments(memberId, data.map { it.toEntity() })
                }
            }
            channel.subscribe()
            AppLogger.d(tag, "Subscribed to member_documents")
        } catch (e: Exception) {
            AppLogger.w(tag, "member_documents subscription failed: ${e.message}")
        }
    }

    private suspend fun subscribeBeneficiaryClaims() {
        try {
            val name = "rt_burial_claims"
            val channel = supabase.realtime.channel(name)
            val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") { table = "beneficiary_payout_claims" }
            val updates = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") { table = "beneficiary_payout_claims" }
            merge(inserts, updates).collect { action ->
                val claimId = action.record["id"]?.jsonPrimitive?.content ?: return@collect
                runCatching {
                    val claim = supabase.postgrest["beneficiary_payout_claims"].select {
                        filter { eq("id", claimId) }
                    }.decodeSingle<BeneficiaryPayoutClaim>()
                    db.beneficiaryClaimDao().upsertClaim(claim.toEntity())
                }
            }
            channel.subscribe()
            AppLogger.d(tag, "Subscribed to beneficiary_payout_claims")
        } catch (e: Exception) {
            AppLogger.d(tag, "beneficiary_payout_claims table not available, trying burial_claims")
            subscribeLegacyBurialClaims()
        }
    }

    private suspend fun subscribeLegacyBurialClaims() {
        try {
            val name = "rt_burial_claims_legacy"
            val channel = supabase.realtime.channel(name)
            val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") { table = "burial_claims" }
            val updates = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") { table = "burial_claims" }
            merge(inserts, updates).collect { action ->
                val claimId = action.record["id"]?.jsonPrimitive?.content ?: return@collect
                runCatching {
                    val claim = supabase.postgrest["burial_claims"].select {
                        filter { eq("id", claimId) }
                    }.decodeSingle<BeneficiaryPayoutClaim>()
                    db.beneficiaryClaimDao().upsertClaim(claim.toEntity())
                }
            }
            channel.subscribe()
            AppLogger.d(tag, "Subscribed to burial_claims (legacy)")
        } catch (e: Exception) {
            AppLogger.w(tag, "burial_claims subscription also failed: ${e.message}")
        }
    }
}
