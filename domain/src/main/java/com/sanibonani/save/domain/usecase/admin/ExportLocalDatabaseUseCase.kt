package com.sanibonani.save.domain.usecase.admin

import com.sanibonani.save.domain.repository.admin.PlatformRepository
import java.io.File
import javax.inject.Inject

/**
 * Dumps the local cache to a file the admin can hand over.
 *
 * [useOnline] chooses between the synced-on-cloud tier and the local tier, which is why the caller
 * has to state it rather than the screen guessing.
 */
class ExportLocalDatabaseUseCase @Inject constructor(
    private val platformRepository: PlatformRepository
) {
    suspend operator fun invoke(useOnline: Boolean): Result<File> =
        platformRepository.exportLocalDatabase(useOnline)
}
