package com.sanibonani.save.domain.usecase.group
import com.sanibonani.save.domain.model.*
import com.sanibonani.save.domain.repository.admin.*
import com.sanibonani.save.domain.repository.group.*
import com.sanibonani.save.domain.usecase.disbursement.ExecuteRoscaPayoutUseCase
import java.time.LocalDate
import javax.inject.Inject
import com.sanibonani.save.domain.utils.safeRunCatching
class AutomateGovernanceUseCase @Inject constructor(
    private val platformRepository: PlatformRepository,
    private val executeRoscaPayoutUseCase: ExecuteRoscaPayoutUseCase
) {
    suspend operator fun invoke(): Result<Unit> = safeRunCatching {
        val groups = platformRepository.getAllGroups().getOrThrow()
        val today = LocalDate.now()
        for (group in groups) {
            val groupId = group.id ?: continue
            if (group.type == GroupType.ROSCA) {
                executeRoscaPayoutUseCase(groupId)
            }
        }
    }
}
