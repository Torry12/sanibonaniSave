package com.sanibonani.save.data.repository.mappers

import com.sanibonani.save.data.local.BeneficiaryClaimEntity
import com.sanibonani.save.domain.model.BeneficiaryPayoutClaim

// ─────────────────────────────────────────────────────────────────────────────
//  BURIAL CLAIM MAPPERS
// ─────────────────────────────────────────────────────────────────────────────

fun BeneficiaryPayoutClaim.toEntity() = BeneficiaryClaimEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    groupId = groupId,
    memberId = memberId,
    beneficiaryId = beneficiaryId,
    beneficiaryName = beneficiaryName,
    facePhotoUrl = facePhotoUrl,
    beneficiaryIdUrl = beneficiaryIdUrl,
    deathCertificateUrl = deathCertificateUrl,
    memberIdUrl = memberIdUrl,
    causeOfDeath = causeOfDeath,
    dateOfDeath = dateOfDeath,
    claimAmount = claimAmount,
    bankName = bankName,
    accountNo = accountNo,
    branchCode = branchCode,
    accountHolder = accountHolder,
    notes = notes,
    status = status,
    reviewedBy = reviewedBy,
    reviewedAt = reviewedAt,
    adminNotes = adminNotes,
    rejectionReason = rejectionReason,
    createdAt = createdAt,
)

fun BeneficiaryClaimEntity.toModel() = BeneficiaryPayoutClaim(
    id = id,
    groupId = groupId,
    memberId = memberId,
    beneficiaryId = beneficiaryId,
    beneficiaryName = beneficiaryName,
    facePhotoUrl = facePhotoUrl,
    beneficiaryIdUrl = beneficiaryIdUrl,
    deathCertificateUrl = deathCertificateUrl,
    memberIdUrl = memberIdUrl,
    causeOfDeath = causeOfDeath,
    dateOfDeath = dateOfDeath,
    claimAmount = claimAmount,
    bankName = bankName,
    accountNo = accountNo,
    branchCode = branchCode,
    accountHolder = accountHolder,
    notes = notes,
    status = status,
    reviewedBy = reviewedBy,
    reviewedAt = reviewedAt,
    adminNotes = adminNotes,
    rejectionReason = rejectionReason,
    createdAt = createdAt,
)
