package com.sanibonani.save.data.service

import com.sanibonani.save.domain.logging.AppLogger
import com.sanibonani.save.domain.model.Member
import com.sanibonani.save.domain.model.MemberStatus
import com.sanibonani.save.domain.repository.member.MemberRepository
import com.sanibonani.save.domain.service.AiService
import com.sanibonani.save.domain.service.FileStorageService
import com.sanibonani.save.domain.service.KycService
import com.sanibonani.save.domain.validation.InputValidator
import java.io.File
import javax.inject.Inject

class KycServiceImpl @Inject constructor(
    private val aiService: AiService,
    private val memberRepository: MemberRepository,
    private val fileStorageService: FileStorageService
) : KycService {

    override fun evaluateMemberIdentity(member: Member): Result<Unit> {
        val idNumber = member.idNumber
        if (!idNumber.isNullOrBlank()) {
            if (!InputValidator.isValidSAIdNumber(idNumber)) {
                return Result.failure(Exception("KYC Failure: Invalid SA ID Number provided."))
            }
        }
        return Result.success(Unit)
    }

    override suspend fun processKycAi(
        memberId: String,
        idFront: File,
        selfie: File
    ): Result<Unit> = runCatching {
        AppLogger.d("KycService", "Starting AI KYC for member: $memberId")

        val extraction = aiService.extractIdInfo(idFront).getOrThrow()
        if (extraction.confidenceScore < 0.7) {
            throw Exception("ID extraction confidence too low. Please retry with a clearer photo.")
        }

        val match = aiService.compareFaces(idFront, selfie).getOrThrow()
        if (!match.isMatch || match.confidenceScore < 0.7) {
            throw Exception("Facial recognition mismatch. Face on ID does not match selfie.")
        }

        val idUrl = fileStorageService.uploadMemberDocument(memberId, 1, idFront.readBytes(), "jpg").getOrThrow()
        val selfieUrl = fileStorageService.uploadMemberDocument(memberId, 0, selfie.readBytes(), "jpg").getOrThrow()

        memberRepository.updateMemberDocuments(
            memberId = memberId,
            doc1Url = idUrl,
            doc1Type = "id_front",
            profilePhotoUrl = selfieUrl
        ).getOrThrow()

        memberRepository.updateMemberStatus(memberId, MemberStatus.ACTIVE).getOrThrow()

        AppLogger.i("KycService", "AI KYC successful for member: $memberId")
    }
}



