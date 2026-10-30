package com.sanibonani.save.domain.usecase.auth

import com.sanibonani.save.domain.repository.system.DataConnectionProtocol
import javax.inject.Inject

/**
 * Rebuilds the remote and local connection handles, for the after-a-failure retry path.
 */
class RebuildConnectionsUseCase @Inject constructor(
    private val dataConnectionProtocol: DataConnectionProtocol
) {
    suspend operator fun invoke(): Result<Unit> = dataConnectionProtocol.rebuild()
}
