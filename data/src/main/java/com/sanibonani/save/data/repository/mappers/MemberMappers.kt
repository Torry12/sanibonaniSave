package com.sanibonani.save.data.repository.mappers

import com.sanibonani.save.data.local.BeneficiaryEntity
import com.sanibonani.save.data.local.MemberDocumentEntity
import com.sanibonani.save.data.local.MemberEntity
import com.sanibonani.save.domain.model.*

fun Member.toEntity() = MemberEntity(
    id = id ?: "",
    groupId = groupId,
    userId = userId ?: "",
    memberKey = memberKey ?: "",
    fullName = fullName,
    idNumber = idNumber ?: "",
    phone = phone,
    email = email ?: "",
    street = street ?: "",
    suburb = suburb ?: "",
    city = city ?: "",
    province = province ?: "",
    status = status,
    joinedAt = joinedAt ?: "",
    probationEndAt = probationEndAt ?: "",
    profilePhotoUrl = profilePhotoUrl,
    document1Url = document1Url,
    document1Type = document1Type,
    document1Status = document1Status,
    document2Url = document2Url,
    document2Type = document2Type,
    document2Status = document2Status,
    document3Url = document3Url,
    document3Type = document3Type,
    document3Status = document3Status,
    document4Url = document4Url,
    document4Type = document4Type,
    document4Status = document4Status,
    document5Url = document5Url,
    document5Type = document5Type,
    document5Status = document5Status,
    contractUrl = contractUrl,
    contractStatus = contractStatus,

    bankName = bankName,
    accountNo = accountNo,
    branchCode = branchCode,

    beneficiaryCount = beneficiaryCount ?: 0,
    beneficiaryOver65Count = beneficiaryOver65Count ?: 0,
    monthlyContributionOverride = monthlyContributionOverride,

    roscaPosition = roscaPosition,

    totalContributions = totalContributions ?: 0,
    totalPaid = totalPaid,
    fcmToken = fcmToken,
    notificationPref = notificationPref,
    createdAt = createdAt,
)

fun MemberEntity.toModel() = Member(
    id = id,
    groupId = groupId,
    userId = userId,
    fullName = fullName,
    idNumber = idNumber,
    phone = phone,
    email = email,
    street = street,
    suburb = suburb,
    city = city,
    province = province,
    status = status,
    joinedAt = joinedAt,
    probationEndAt = probationEndAt,
    profilePhotoUrl = profilePhotoUrl,
    document1Url = document1Url,
    document1Type = document1Type,
    document1Status = document1Status,
    document2Url = document2Url,
    document2Type = document2Type,
    document2Status = document2Status,
    document3Url = document3Url,
    document3Type = document3Type,
    document3Status = document3Status,
    document4Url = document4Url,
    document4Type = document4Type,
    document4Status = document4Status,
    document5Url = document5Url,
    document5Type = document5Type,
    document5Status = document5Status,
    contractUrl = contractUrl,
    contractStatus = contractStatus,

    bankName = bankName,
    accountNo = accountNo,
    branchCode = branchCode,

    beneficiaryCount = beneficiaryCount,
    beneficiaryOver65Count = beneficiaryOver65Count,
    monthlyContributionOverride = monthlyContributionOverride,

    roscaPosition = roscaPosition,

    totalContributions = totalContributions,
    totalPaid = totalPaid,
    fcmToken = fcmToken,
    notificationPref = notificationPref,
    createdAt = createdAt,
    memberKey = memberKey,
    claims = emptyList() // Claims loaded separately or via join
)

fun Beneficiary.toEntity() = BeneficiaryEntity(
    groupId = groupId,
    memberId = memberId,
    id = id ?: "",
    fullName = fullName,
    idNumber = idNumber,
    relationship = relationship,
    dateOfBirth = dateOfBirth,
    isOver65 = isOver65,
    documentUrl = documentUrl,
    facePhotoUrl = facePhotoUrl,
    documentStatus = documentStatus.name.lowercase(),
    createdAt = createdAt,
)

fun BeneficiaryEntity.toModel() = Beneficiary(
    groupId = groupId,
    memberId = memberId,
    id = id,
    fullName = fullName,
    idNumber = idNumber,
    relationship = relationship,
    dateOfBirth = dateOfBirth,
    isOver65 = isOver65,
    documentUrl = documentUrl,
    facePhotoUrl = facePhotoUrl,
    documentStatus = DocumentStatus.entries.find { it.name.equals(documentStatus, ignoreCase = true) } ?: DocumentStatus.PENDING,
    createdAt = createdAt,
)

fun MemberDocument.toEntity() = MemberDocumentEntity(
    id = id ?: "",
    memberId = memberId,
    groupId = groupId,
    label = label,
    documentUrl = documentUrl,
    documentType = documentType,
    status = status,
    createdAt = createdAt,
)

fun MemberDocumentEntity.toModel() = MemberDocument(
    id = id,
    memberId = memberId,
    groupId = groupId,
    label = label,
    documentUrl = documentUrl,
    documentType = documentType,
    status = status,
    createdAt = createdAt,
)
