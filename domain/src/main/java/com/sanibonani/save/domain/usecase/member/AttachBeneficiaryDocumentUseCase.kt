package com.sanibonani.save.domain.usecase.member

import com.sanibonani.save.domain.model.Beneficiary
import com.sanibonani.save.domain.repository.member.BeneficiaryRepository
import com.sanibonani.save.domain.service.FileStorageService
import com.sanibonani.save.domain.utils.safeRunCatching
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Uploads a beneficiary's document and writes the returned link onto that beneficiary's row.
 *
 * The two steps are one operation: a file that stored but could not be attached to a row is
 * reported as a failure, so the screen never claims a document is on file when nothing points
 * at it.
 */
class AttachBeneficiaryDocumentUseCase @Inject constructor(
    private val beneficiaryRepository: BeneficiaryRepository,
    private val fileStorageService: FileStorageService
) {
    suspend operator fun invoke(
        knownBeneficiaries: List<Beneficiary>,
        memberId: String,
        beneficiaryId: String,
        fileName: String,
        data: ByteArray
    ): Result<Unit> {
        val extension = fileName.substringAfterLast(".", "pdf")

        return safeRunCatching {
            val url = fileStorageService
                .uploadBeneficiaryDocument(memberId, beneficiaryId, data, extension)
                .getOrThrow()
            val current = knownBeneficiaries.ifEmpty {
                beneficiaryRepository.observeBeneficiaries(memberId).first().getOrNull().orEmpty()
            }
            val target = current.find { it.id == beneficiaryId }
                ?: error("The document uploaded, but the beneficiary could not be found. Sync and try again.")
            beneficiaryRepository.updateBeneficiary(target.copy(documentUrl = url)).getOrThrow()
        }
    }
}
