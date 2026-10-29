package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.model.GroupHealthScore
import com.sanibonani.save.domain.repository.admin.HealthScoreRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * A group's health score, re-emitted whenever it is recomputed.
 */
class ObserveGroupHealthScoreUseCase @Inject constructor(
    private val healthScoreRepository: HealthScoreRepository
) {
    operator fun invoke(groupId: String): Flow<Result<GroupHealthScore>> =
        healthScoreRepository.observeGroupHealthScore(groupId)
}
