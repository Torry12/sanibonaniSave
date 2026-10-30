package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.repository.admin.PlatformRepository
import javax.inject.Inject

/**
 * Recomputes the stored platform-wide metrics on the server.
 *
 * The read path serves the stored figures, so this is what makes them current again after bulk
 * changes; it costs a full scan and stays behind an explicit admin action.
 */
class ComputeSystemWideMetricsUseCase @Inject constructor(
    private val platformRepository: PlatformRepository
) {
    suspend operator fun invoke(): Result<Unit> = platformRepository.computeSystemWideMetrics()
}
