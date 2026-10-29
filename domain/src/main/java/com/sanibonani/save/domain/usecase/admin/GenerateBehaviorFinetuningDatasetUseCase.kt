package com.sanibonani.save.domain.usecase.admin
import com.sanibonani.save.domain.model.SftTrainingExample
import com.sanibonani.save.domain.repository.admin.BehaviorTrackingRepository
import javax.inject.Inject

/**
 * Builds the supervised-fine-tuning dataset for a group from its members' behavior tracks.
 */
class GenerateBehaviorFinetuningDatasetUseCase @Inject constructor(
    private val behaviorTrackingRepository: BehaviorTrackingRepository
) {
    suspend operator fun invoke(groupId: String): Result<List<SftTrainingExample>> =
        behaviorTrackingRepository.generateFinetuningDataset(groupId)
}
