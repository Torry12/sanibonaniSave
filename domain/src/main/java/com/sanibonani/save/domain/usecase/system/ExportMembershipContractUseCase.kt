package com.sanibonani.save.domain.usecase.system

import com.sanibonani.save.domain.model.Group
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.repository.system.ExportRepository
import java.io.File
import javax.inject.Inject

/**
 * The membership certificate for one member of one group.
 */
class ExportMembershipContractUseCase @Inject constructor(
    private val exportRepository: ExportRepository
) {
    suspend operator fun invoke(member: Member, group: Group): Result<File> =
        exportRepository.exportMembershipContract(member, group)
}
