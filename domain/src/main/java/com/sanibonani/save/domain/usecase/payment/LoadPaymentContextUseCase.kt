package com.sanibonani.save.domain.usecase.payment

import com.sanibonani.save.domain.model.Contribution
import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Loan
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.PaymentDestination
import com.sanibonani.save.domain.model.PlatformConfig
import com.sanibonani.save.domain.repository.admin.PlatformConfigRepository
import com.sanibonani.save.domain.repository.auth.SupabaseRepository
import com.sanibonani.save.domain.repository.credit.LoanRepository
import com.sanibonani.save.domain.repository.equity.ContributionRepository
import com.sanibonani.save.domain.repository.group.GroupRepository
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.utils.PaymentCalculation
import com.sanibonani.save.domain.utils.PaymentCalculator
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * Resolves everything the payment screen needs before a member can pay: who is paying,
 * which group they belong to, what they owe, and which wallet the money belongs to.
 *
 * The decision of where a payment goes is billed as a fee rule, so the type-to-destination
 * mapping lives here with the rest of the payment rules.
 */
class LoadPaymentContextUseCase @Inject constructor(
    private val authRepository: SupabaseRepository,
    private val groupRepository: GroupRepository,
    private val memberRepository: MemberRepository,
    private val contributionRepository: ContributionRepository,
    private val loanRepository: LoanRepository,
    private val platformConfigRepository: PlatformConfigRepository,
    private val paymentCalculator: PaymentCalculator
) {
    sealed interface Outcome {
        /** The member or the group could not be resolved, so there is nothing to pay against. */
        data object Unavailable : Outcome

        data class Ready(val context: Context) : Outcome
    }

    data class Params(val userId: String, val groupId: String, val type: String)

    data class Context(
        val member: Member,
        val group: Group,
        val contributions: List<Contribution>,
        val loans: List<Loan>,
        val calculation: PaymentCalculation,
        val config: PlatformConfig?,
        val destination: PaymentDestination
    )

    fun currentUserId(): String? = authRepository.currentUserId

    operator fun invoke(params: Params): Flow<Outcome> = flow {
        val member = memberRepository.observeMemberByUserId(params.userId, params.groupId)
            .filter { it.getOrNull() != null }
            .first()
            .getOrNull()
            ?: run {
                emit(Outcome.Unavailable)
                return@flow
            }

        val group = groupRepository.getGroupById(params.groupId).getOrNull() ?: run {
            emit(Outcome.Unavailable)
            return@flow
        }

        val memberId = member.id ?: run {
            emit(Outcome.Unavailable)
            return@flow
        }

        val config = platformConfigRepository.current()
        val destination = destinationFor(params.type)

        emitAll(
            combine(
                contributionRepository.observeMemberContributions(memberId, params.groupId),
                loanRepository.getMemberLoans(memberId)
            ) { contributionsResult, loansResult ->
                val contributions = contributionsResult.getOrDefault(emptyList())
                val loans = loansResult.getOrDefault(emptyList())
                Outcome.Ready(
                    Context(
                        member = member,
                        group = group,
                        contributions = contributions,
                        loans = loans,
                        calculation = paymentCalculator.calculateStatus(group, member, contributions, loans),
                        config = config,
                        destination = destination
                    )
                )
            }
        )
    }

    private fun destinationFor(type: String): PaymentDestination = when (type.lowercase()) {
        "registration", "platform_fee", "joining_fee" -> PaymentDestination.PLATFORM
        else -> PaymentDestination.GROUP
    }
}
