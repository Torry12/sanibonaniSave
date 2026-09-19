package com.sanibonani.save.data.repository.mappers

import com.sanibonani.save.data.local.*
import com.sanibonani.save.domain.model.*

fun GroupPoll.toEntity() = GroupPollEntity(
    id = id ?: "",
    groupId = groupId,
    createdByMemberId = createdByMemberId,
    title = title,
    description = description,
    type = type,
    status = status,
    effectStatus = effectStatus,
    effectData = effectData,
    allowMultipleChoice = allowMultipleChoice,
    startsAt = startsAt,
    endsAt = endsAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun GroupPollEntity.toModel() = GroupPoll(
    id = id,
    groupId = groupId,
    createdByMemberId = createdByMemberId,
    title = title,
    description = description,
    type = type,
    status = status,
    effectStatus = effectStatus,
    effectData = effectData,
    allowMultipleChoice = allowMultipleChoice,
    startsAt = startsAt,
    endsAt = endsAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun GroupPollOption.toEntity() = GroupPollOptionEntity(
    id = id ?: "",
    pollId = pollId,
    label = label,
    position = position,
    createdAt = createdAt
)

fun GroupPollOptionEntity.toModel() = GroupPollOption(
    id = id,
    pollId = pollId,
    label = label,
    position = position,
    createdAt = createdAt
)

fun GroupPollVote.toEntity() = GroupPollVoteEntity(
    id = id ?: "",
    pollId = pollId,
    optionId = optionId,
    memberId = memberId,
    groupId = groupId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun GroupPollVoteEntity.toModel() = GroupPollVote(
    id = id,
    pollId = pollId,
    optionId = optionId,
    memberId = memberId,
    groupId = groupId,
    createdAt = createdAt,
    updatedAt = updatedAt
)
