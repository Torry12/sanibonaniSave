package com.sanibonani.save.domain.usecase.system

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.repository.system.ExportRepository
import java.io.File
import javax.inject.Inject

/**
 * The group's constitution document.
 */
class ExportGroupConstitutionUseCase @Inject constructor(
    private val exportRepository: ExportRepository
) {
    suspend operator fun invoke(group: Group): Result<File> =
        exportRepository.exportGroupConstitution(group)
}
