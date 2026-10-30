package com.sanibonani.save.domain.usecase.admin
import com.sanibonani.save.domain.model.FraudDetectionEvent
import com.sanibonani.save.domain.repository.admin.BehaviorTrackingRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * Streams the fraud events detected for one member.
 */
class ObserveFraudEventsByMemberUseCase @Inject constructor(
    private val behaviorTrackingRepository: BehaviorTrackingRepository
) {
    operator fun invoke(memberId: String): Flow<Result<List<FraudDetectionEvent>>> =
        behaviorTrackingRepository.observeFraudEventsByMember(memberId)
}
